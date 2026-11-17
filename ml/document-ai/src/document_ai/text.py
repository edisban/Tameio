"""Text normalisation shared by field extraction and the classifier.

Greek receipts are mostly printed in capitals, often without accents, and OCR mixes up Greek and
Latin letters that look the same (Α/A, Ε/E, Ο/O). Comparing text in one normal form makes the
rest of the code independent of those differences.
"""

import unicodedata

# Latin capitals that OCR returns for their Greek look-alikes
_LATIN_TO_GREEK = str.maketrans("ABEZHIKMNOPTYX", "ΑΒΕΖΗΙΚΜΝΟΡΤΥΧ")


def greek_lookalikes(text: str) -> str:
    """Replaces Latin capitals with their Greek look-alikes, leaving everything else as it is.

    Used on regular expressions too, so a keyword such as TOTAL matches text that went through
    normalise(); lower-case escapes like \\s and \\d are not touched.
    """
    return text.translate(_LATIN_TO_GREEK)


# Symbols Tesseract returns for Greek letters it misreads: Φ often comes back as ® or Cyrillic Ф
_OCR_SYMBOLS = str.maketrans({"®": "Φ", "Ф": "Φ", "ф": "Φ", "©": "Θ", "€": "Ε"})


def strip_accents(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def clean_ocr_text(text: str) -> str:
    """Drops characters that are not valid Unicode, which Tesseract occasionally emits and JSON cannot carry."""
    return text.encode("utf-8", errors="replace").decode("utf-8")


def normalise(text: str) -> str:
    """Upper case, without accents, with Greek letters in place of Latin look-alikes and OCR symbols."""
    return greek_lookalikes(strip_accents(text.translate(_OCR_SYMBOLS)).upper())
