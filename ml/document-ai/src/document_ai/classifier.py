"""Suggests an expense category for a receipt from its text.

A baseline on purpose: character n-grams with TF-IDF and logistic regression. It trains in seconds,
copes with OCR typos (n-grams overlap even when a letter is wrong) and its probabilities are honest
enough to show as confidence. Later models have to beat its numbers to replace it.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from document_ai.text import normalise

MODEL_NAME = "expense-classifier"


@dataclass(frozen=True)
class Suggestion:
    category: str
    confidence: float


@dataclass(frozen=True)
class Classification:
    best: Suggestion
    alternatives: list[Suggestion]
    model_version: str


def build_pipeline(seed: int = 42) -> Pipeline:
    return Pipeline(
        [
            (
                "features",
                TfidfVectorizer(
                    preprocessor=normalise, analyzer="char_wb", ngram_range=(2, 5), sublinear_tf=True, min_df=2
                ),
            ),
            ("model", LogisticRegression(max_iter=2000, C=4.0, class_weight="balanced", random_state=seed)),
        ]
    )


class ExpenseClassifier:
    """A trained pipeline together with what it is: its version and how well it scored."""

    def __init__(self, pipeline: Pipeline, metadata: dict[str, Any]):
        self._pipeline = pipeline
        self.metadata = metadata

    @property
    def version(self) -> str:
        return f"{MODEL_NAME}:{self.metadata['version']}"

    def classify(self, text: str, alternatives: int = 2) -> Classification:
        probabilities = self._pipeline.predict_proba([text])[0]
        ranked = sorted(
            zip(self._pipeline.classes_, probabilities, strict=True), key=lambda pair: pair[1], reverse=True
        )
        suggestions = [Suggestion(str(category), round(float(p), 4)) for category, p in ranked[: alternatives + 1]]
        return Classification(suggestions[0], suggestions[1:], self.version)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump({"pipeline": self._pipeline, "metadata": self.metadata}, path)

    @classmethod
    def load(cls, path: Path) -> "ExpenseClassifier":
        # Only ever loads the file this service's own build produced: joblib must not open untrusted files
        stored = joblib.load(path)
        return cls(stored["pipeline"], stored["metadata"])
