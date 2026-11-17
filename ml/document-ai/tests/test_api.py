from pathlib import Path

import pytest

from document_ai import main
from document_ai.ocr import Method, OcrResult, UnreadableDocument

FIXTURES = Path(__file__).parent / "fixtures"

RECEIPT_TEXT = """ΠΡΑΤΗΡΙΟ ΚΑΥΣΙΜΩΝ ΚΩΣΤΑ
Α.Φ.Μ.: 094014201
ΗΜΕΡΟΜΗΝΙΑ: 17/11/2026
ΑΜΟΛΥΒΔΗ 95 30 Χ 1,85 55,50
ΚΑΘΑΡΗ ΑΞΙΑ 44,76
ΦΠΑ 24% 10,74
ΣΥΝΟΛΟ 55,50"""


@pytest.fixture
def fake_ocr(monkeypatch):
    """Skips Tesseract: these tests are about the API, the OCR has its own tests."""
    monkeypatch.setattr(main, "read_text", lambda content, media_type: OcrResult(RECEIPT_TEXT, 1, Method.OCR))


def test_reads_a_receipt(client, fake_ocr):
    response = client.post("/v1/read", content=b"\xff\xd8\xff fake jpeg", headers={"Content-Type": "image/jpeg"})

    assert response.status_code == 200
    body = response.json()
    assert body["fields"]["issuerAfm"] == {"value": "094014201", "confidence": 0.95}
    assert body["fields"]["issueDate"]["value"] == "2026-11-17"
    # Amounts travel as strings, so no decimal is lost to floating point
    assert body["fields"]["totalAmount"]["value"] == "55.50"
    assert body["category"]["best"]["category"] == "FUEL"
    assert body["category"]["modelVersion"] == "expense-classifier:test"
    assert {check["name"] for check in body["checks"]} == {"AMOUNTS_ADD_UP", "VAT_RATE_KNOWN"}
    assert all(check["passed"] for check in body["checks"])


def test_reads_the_text_layer_of_a_real_pdf_without_ocr(client):
    response = client.post(
        "/v1/read",
        content=(FIXTURES / "receipt-text-layer.pdf").read_bytes(),
        headers={"Content-Type": "application/pdf"},
    )

    assert response.status_code == 200
    assert response.json()["method"] == "PDF_TEXT"
    assert response.json()["fields"]["totalAmount"]["value"] == "4.00"


def test_refuses_other_file_types(client):
    response = client.post("/v1/read", content=b"<html>", headers={"Content-Type": "text/html"})

    assert response.status_code == 415
    assert response.headers["content-type"] == "application/problem+json"


def test_refuses_an_empty_body(client):
    assert client.post("/v1/read", content=b"", headers={"Content-Type": "application/pdf"}).status_code == 400


def test_says_when_a_document_cannot_be_opened(client, monkeypatch):
    def broken(content, media_type):
        raise UnreadableDocument("Not a readable PDF")

    monkeypatch.setattr(main, "read_text", broken)

    response = client.post("/v1/read", content=b"%PDF-broken", headers={"Content-Type": "application/pdf"})

    assert response.status_code == 422
    assert response.json()["detail"] == "Not a readable PDF"


def test_health_reports_the_model_in_use(client):
    assert client.get("/health").json() == {"status": "UP", "modelVersion": "expense-classifier:test"}
