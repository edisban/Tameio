"""Variant 1, the baseline: the service's own rules for the fields and TF-IDF for the category."""

import io
import os

import joblib

from document_ai.classifier import build_pipeline
from document_ai.fields import extract_fields
from training.dataset import Example


class Baseline:
    name = "baseline"
    model_name = "rules + TF-IDF and logistic regression"
    hardware = f"CPU, {os.cpu_count()} threads"

    def __init__(self, train: list[Example]):
        # Trained on the train split only, like the fine-tune, so the test shops are new to both
        self._pipeline = build_pipeline().fit([e.text for e in train], [e.expected["category"] for e in train])

    def size_mb(self) -> float:
        buffer = io.BytesIO()
        joblib.dump(self._pipeline, buffer)
        return buffer.tell() / 1_000_000

    def read(self, text: str) -> dict[str, str | None]:
        fields = extract_fields(text)
        return {
            "issuer_afm": fields.issuer_afm.value if fields.issuer_afm else None,
            "issue_date": fields.issue_date.value.isoformat() if fields.issue_date else None,
            "net_amount": f"{fields.net_amount.value:.2f}" if fields.net_amount else None,
            "vat_amount": f"{fields.vat_amount.value:.2f}" if fields.vat_amount else None,
            "total_amount": f"{fields.total_amount.value:.2f}" if fields.total_amount else None,
            "category": str(self._pipeline.predict([text])[0]),
        }
