"""HTTP API of the document AI service.

    POST /v1/read   body: the file's bytes, Content-Type: application/pdf, image/jpeg or image/png
    GET  /health

The core sends a document and gets back its text, the fields found in it, the checks on those
fields and a suggested expense category. The service keeps nothing: it is stateless, so any number
of copies can run behind a load balancer.
"""

import logging
import os
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from document_ai.classifier import ExpenseClassifier
from document_ai.fields import ExtractedFields, Field, extract_fields
from document_ai.ocr import UnreadableDocument, read_text
from document_ai.schemas import (
    CategoryResult,
    CategorySuggestion,
    CheckResult,
    DateField,
    Fields,
    Health,
    ReadResult,
    TextField,
)

log = logging.getLogger("document_ai")

ACCEPTED_TYPES = {"application/pdf", "image/jpeg", "image/png"}
MAX_BYTES = 10 * 1024 * 1024
MODEL_PATH = Path(os.environ.get("DOCUMENT_AI_MODEL_PATH", "models/expense-classifier.joblib"))


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # Loaded once at start-up: a missing model stops the service instead of failing every request
    app.state.classifier = ExpenseClassifier.load(MODEL_PATH)
    log.info("Loaded %s", app.state.classifier.version)
    yield


app = FastAPI(title="Tameio document AI", version="0.1.0", lifespan=lifespan)


def get_classifier(request: Request) -> ExpenseClassifier:
    return request.app.state.classifier


def problem(status: int, title: str, detail: str) -> JSONResponse:
    """Errors as RFC 9457 problem details, the same shape the core API uses."""
    return JSONResponse(
        {"status": status, "title": title, "detail": detail}, status, media_type="application/problem+json"
    )


def _text_field(found: Field | None) -> TextField | None:
    return TextField(value=str(found.value), confidence=found.confidence) if found else None


def _fields(extracted: ExtractedFields) -> Fields:
    return Fields(
        issuer_afm=_text_field(extracted.issuer_afm),
        issue_date=DateField(value=extracted.issue_date.value, confidence=extracted.issue_date.confidence)
        if extracted.issue_date
        else None,
        net_amount=_text_field(extracted.net_amount),
        vat_amount=_text_field(extracted.vat_amount),
        total_amount=_text_field(extracted.total_amount),
    )


@app.get("/health")
def health(classifier: Annotated[ExpenseClassifier, Depends(get_classifier)]) -> Health:
    return Health(status="UP", model_version=classifier.version)


@app.post("/v1/read", response_model=ReadResult, response_model_by_alias=True)
async def read(
    request: Request,
    classifier: Annotated[ExpenseClassifier, Depends(get_classifier)],
    content_type: Annotated[str, Header()] = "",
):
    media_type = content_type.split(";")[0].strip().lower()
    if media_type not in ACCEPTED_TYPES:
        return problem(415, "Unsupported file type", "Send a PDF, JPEG or PNG file.")
    content = await request.body()
    if not content:
        return problem(400, "Invalid request", "The file is empty.")
    if len(content) > MAX_BYTES:
        return problem(413, "File too large", "Files are limited to 10 MB.")

    started = time.perf_counter()
    try:
        # OCR is CPU-bound and blocking: run it off the event loop so health checks still answer
        ocr = await run_in_threadpool(read_text, content, media_type)
    except UnreadableDocument as e:
        return problem(422, "Unreadable document", str(e))
    extracted = extract_fields(ocr.text)
    classification = classifier.classify(ocr.text)
    duration_ms = round((time.perf_counter() - started) * 1000)
    log.info("Read %s: %d pages by %s in %d ms", media_type, ocr.pages, ocr.method, duration_ms)

    return ReadResult(
        text=ocr.text,
        pages=ocr.pages,
        method=ocr.method,
        fields=_fields(extracted),
        checks=[CheckResult(name=c.name, passed=c.passed, detail=c.detail) for c in extracted.checks],
        category=CategoryResult(
            best=CategorySuggestion(category=classification.best.category, confidence=classification.best.confidence),
            alternatives=[
                CategorySuggestion(category=s.category, confidence=s.confidence) for s in classification.alternatives
            ],
            model_version=classification.model_version,
        ),
        duration_ms=duration_ms,
    )
