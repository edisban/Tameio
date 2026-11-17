import io
import shutil
from decimal import Decimal
from pathlib import Path

import pypdfium2 as pdfium
import pytest

from document_ai.fields import extract_fields
from document_ai.ocr import Method, UnreadableDocument, read_text

FIXTURES = Path(__file__).parent / "fixtures"


def test_uses_the_text_layer_of_a_digital_pdf():
    result = read_text((FIXTURES / "receipt-text-layer.pdf").read_bytes(), "application/pdf")

    assert result.method == Method.PDF_TEXT
    assert "Total EUR" in result.text


def test_rejects_bytes_that_are_not_a_pdf():
    with pytest.raises(UnreadableDocument):
        read_text(b"%PDF-but not really", "application/pdf")


def test_rejects_bytes_that_are_not_an_image():
    with pytest.raises(UnreadableDocument):
        read_text(b"\x89PNG but not really", "image/png")


@pytest.mark.ocr
@pytest.mark.skipif(shutil.which("tesseract") is None, reason="Tesseract is installed in the Docker image and CI")
def test_reads_a_photographed_receipt_with_tesseract():
    # The same receipt as an image, as a phone photo would be, so only OCR can read it
    pdf = pdfium.PdfDocument((FIXTURES / "receipt-text-layer.pdf").read_bytes())
    image = pdf[0].render(scale=300 / 72).to_pil()
    png = io.BytesIO()
    image.save(png, format="PNG")

    result = read_text(png.getvalue(), "image/png")

    assert result.method == Method.OCR
    assert "Receipt" in result.text
    assert extract_fields(result.text).total_amount.value == Decimal("4.00")
