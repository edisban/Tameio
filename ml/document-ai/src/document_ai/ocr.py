"""Turns a PDF or an image into text.

A PDF made by software already contains its text, which is exact and instant to read. Only a scan or
a photo needs OCR, done here with Tesseract in Greek and English.
"""

import io
from dataclasses import dataclass
from enum import StrEnum

import pypdfium2 as pdfium
import pytesseract
from PIL import Image, ImageOps

from document_ai.text import clean_ocr_text

MAX_PAGES = 5
# A PDF page with less text than this is treated as a scan
MIN_TEXT_LAYER_CHARS = 20
# 300 dpi is what Tesseract is tuned for; PDF pages are measured at 72 points per inch
RENDER_SCALE = 300 / 72
# --psm 4: a single column of text of variable sizes, the shape of a till receipt
TESSERACT_CONFIG = "--psm 4"
TESSERACT_LANGUAGES = "ell+eng"


class Method(StrEnum):
    PDF_TEXT = "PDF_TEXT"
    OCR = "OCR"


@dataclass(frozen=True)
class OcrResult:
    text: str
    pages: int
    method: Method


class UnreadableDocument(ValueError):
    """The bytes are not a PDF or an image this service can open."""


def _ocr(image: Image.Image) -> str:
    # Photos from phones carry their rotation in EXIF; grayscale helps Tesseract with coloured paper
    upright = ImageOps.exif_transpose(image).convert("L")
    return clean_ocr_text(pytesseract.image_to_string(upright, lang=TESSERACT_LANGUAGES, config=TESSERACT_CONFIG))


def _read_pdf(content: bytes) -> OcrResult:
    try:
        pdf = pdfium.PdfDocument(content)
    except pdfium.PdfiumError as e:
        raise UnreadableDocument("Not a readable PDF") from e
    try:
        pages = [pdf[i] for i in range(min(len(pdf), MAX_PAGES))]
        texts = [page.get_textpage().get_text_range() for page in pages]
        if sum(len(t.strip()) for t in texts) >= MIN_TEXT_LAYER_CHARS * len(pages):
            return OcrResult("\n".join(texts), len(pages), Method.PDF_TEXT)
        scanned = [_ocr(page.render(scale=RENDER_SCALE).to_pil()) for page in pages]
        return OcrResult("\n".join(scanned), len(pages), Method.OCR)
    finally:
        pdf.close()


def read_text(content: bytes, media_type: str) -> OcrResult:
    if media_type == "application/pdf":
        return _read_pdf(content)
    try:
        image = Image.open(io.BytesIO(content))
        image.load()
    except OSError as e:
        raise UnreadableDocument("Not a readable image") from e
    return OcrResult(_ocr(image), 1, Method.OCR)
