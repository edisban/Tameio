"""Retrieval over the bank's documents: the R in RAG.

The Markdown files in `knowledge/` are split into passages, one per section (LangChain's Markdown splitter), and
each is turned into a vector by a small multilingual embedding model that runs as ONNX on the CPU (fastembed). A
question is embedded the same way and the passages closest to it by cosine similarity are returned with their source,
so the answer can cite them. A few dozen passages fit in memory; a vector database would earn its place only with
many more.
"""

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Protocol

import numpy as np
from langchain_text_splitters import MarkdownHeaderTextSplitter

KNOWLEDGE = Path(__file__).parent / "knowledge"
EMBEDDING_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
TOP_K = 3


class Embedder(Protocol):
    def embed(self, texts: list[str]) -> np.ndarray: ...


class FastEmbedder:
    """The ONNX embedding model; downloaded once and cached."""

    def __init__(self, model: str = EMBEDDING_MODEL) -> None:
        from fastembed import TextEmbedding

        self.model = TextEmbedding(model)

    def embed(self, texts: list[str]) -> np.ndarray:
        return np.array(list(self.model.embed(texts)), dtype=np.float32)


@dataclass(frozen=True)
class Passage:
    source: str
    section: str
    text: str
    score: float = 0.0

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


def passages(folder: Path = KNOWLEDGE) -> list[Passage]:
    """One passage per `##` section, split by LangChain's Markdown splitter; the document's `#` title and the
    section's title travel with it."""
    splitter = MarkdownHeaderTextSplitter([("#", "title"), ("##", "section")])
    found = []
    for path in sorted(folder.glob("*.md")):
        for part in splitter.split_text(path.read_text(encoding="utf-8")):
            if "section" in part.metadata:
                found.append(Passage(path.name, f"{part.metadata['title']} · {part.metadata['section']}",
                                     " ".join(part.page_content.split())))  # fmt: skip
    return found


class KnowledgeBase:
    def __init__(self, embedder: Embedder, folder: Path = KNOWLEDGE) -> None:
        self.embedder = embedder
        self.passages = passages(folder)
        self.vectors = self._normalised(embedder.embed([f"{p.section}. {p.text}" for p in self.passages]))

    @staticmethod
    def _normalised(vectors: np.ndarray) -> np.ndarray:
        return vectors / np.clip(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-12, None)

    def search(self, query: str, k: int = TOP_K) -> list[Passage]:
        if not query.strip():
            return []
        scores = self.vectors @ self._normalised(self.embedder.embed([query]))[0]
        best = np.argsort(-scores)[:k]
        return [Passage(**{**asdict(self.passages[i]), "score": round(float(scores[i]), 4)}) for i in best]
