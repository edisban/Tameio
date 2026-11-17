"""Finds the issuer's ΑΦΜ, the date and the amounts in the text of a Greek receipt or invoice.

Rules, not a model: receipts follow a few well-known layouts, and rules are fast, explainable and
easy to test. Each field carries a confidence, so the review screen can point a person to the
fields worth checking. The YOLO phase will add where on the page each field was found.
"""

import re
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal, InvalidOperation

from document_ai.afm import is_valid_afm
from document_ai.text import greek_lookalikes, normalise

# Greek VAT rates: standard, reduced and super-reduced, and the lower rates on some islands
VAT_RATES = (Decimal("0.24"), Decimal("0.13"), Decimal("0.06"), Decimal("0.17"), Decimal("0.09"), Decimal("0.04"))
# Rounding on a receipt is per line, so the parts may differ from the total by a few cents
TOLERANCE = Decimal("0.02")


def _keyword(pattern: str) -> re.Pattern[str]:
    """A pattern for text that went through normalise(): Latin capitals become their Greek look-alikes."""
    return re.compile(greek_lookalikes(pattern))


_AFM_KEYWORD = _keyword(r"Α\.?\s?Φ\.?\s?Μ|AFM|VAT|EL(?=\d{9})")
_NINE_DIGITS = re.compile(r"(?<!\d)(\d{9})(?!\d)")
_DATE = re.compile(r"(?<!\d)(\d{1,2})\s?[/.\-]\s?(\d{1,2})\s?[/.\-]\s?(\d{4}|\d{2})(?!\d)")
_DATE_KEYWORD = _keyword(r"ΗΜΕΡΟΜΗΝΙΑ|ΗΜ/ΝΙΑ|ΗΜΕΡ\.|ΗΜ\.?\s?ΕΚΔ|DATE")
# 1.234,56 · 1234,56 · 12.50 · 1,234.56: digits, optional thousands groups, two decimals. Not part of a
# longer number such as a date (17.11.26) and not a percentage (24,00%).
_AMOUNT = re.compile(r"(?<![\d.,])(\d{1,3}(?:[.,]\d{3})+[.,]\d{2}|\d+[.,]\d{2})(?![\d%]|[.,]\d)")

_TOTAL_KEYWORDS = (
    _keyword(r"ΓΕΝΙΚΟ\s+ΣΥΝΟΛΟ|ΠΛΗΡΩΤΕΟ|ΤΕΛΙΚΟ\s+ΣΥΝΟΛΟ"),
    _keyword(r"ΣΥΝΟΛΟ|ΣΥΝΟΛΙΚΟ|TOTAL"),
)
_NET_KEYWORD = _keyword(r"ΚΑΘΑΡΗ\s+ΑΞΙΑ|ΚΑΘΑΡΟ|ΑΞΙΑ\s+ΠΡΟ\s+Φ\.?Π\.?Α|ΥΠΟΣΥΝΟΛΟ|\bNET\b|SUBTOTAL")
_VAT_KEYWORD = _keyword(r"Φ\.?\s?Π\.?\s?Α|VAT")
# A line giving the VAT, as opposed to a total that includes it ("ΣΥΝΟΛΟ ΜΕ ΦΠΑ")
_VAT_LINE = _keyword(r"^\s*(?:Φ\.?\s?Π\.?\s?Α|VAT)|ΣΥΝΟΛΟ\s+Φ\.?\s?Π\.?\s?Α|TOTAL\s+VAT")
# "Total including VAT" mentions VAT but gives the total
_INCLUDING_VAT = _keyword(r"(?:ΜΕ|ΣΥΝ|ΠΕΡΙΛ\w*)\s+Φ\.?\s?Π\.?\s?Α|INCL")
# Lines about the issuer's tax number mention VAT too, and carry a nine-digit number
_AFM_LINE = _keyword(r"Α\.?\s?Φ\.?\s?Μ|AFM|\d{9}")


@dataclass(frozen=True)
class Field[T]:
    value: T
    confidence: float


@dataclass(frozen=True)
class Check:
    name: str
    passed: bool
    detail: str


@dataclass
class ExtractedFields:
    issuer_afm: Field[str] | None = None
    issue_date: Field[date] | None = None
    net_amount: Field[Decimal] | None = None
    vat_amount: Field[Decimal] | None = None
    total_amount: Field[Decimal] | None = None
    checks: list[Check] = field(default_factory=list)


def parse_amount(text: str) -> Decimal | None:
    """Reads an amount written the Greek way (1.234,56) or the English way (1,234.56)."""
    cleaned = text.replace(" ", "")
    # Whichever separator comes last is the decimal one; the other groups thousands
    if cleaned.rfind(",") > cleaned.rfind("."):
        cleaned = cleaned.replace(".", "").replace(",", ".")
    else:
        cleaned = cleaned.replace(",", "")
    try:
        return Decimal(cleaned).quantize(Decimal("0.01"))
    except InvalidOperation:
        return None


def _amounts(line: str) -> list[Decimal]:
    return [a for a in (parse_amount(m) for m in _AMOUNT.findall(line)) if a is not None]


def find_afm(lines: list[str]) -> Field[str] | None:
    """The first valid ΑΦΜ, which on a receipt is the issuer's. One next to the word ΑΦΜ is surer."""
    fallback = None
    for line in lines:
        compact = re.sub(r"(?<=\d)\s(?=\d)", "", line)
        for match in _NINE_DIGITS.finditer(compact):
            candidate = match.group(1)
            if not is_valid_afm(candidate):
                continue
            if _AFM_KEYWORD.search(compact[: match.start()]):
                return Field(candidate, 0.95)
            fallback = fallback or Field(candidate, 0.6)
    return fallback


def find_date(lines: list[str]) -> Field[date] | None:
    """The issue date: one on a line that says so if there is one, otherwise the first real date."""
    fallback = None
    for line in lines:
        for day, month, year in _DATE.findall(line):
            full_year = int(year) + 2000 if len(year) == 2 else int(year)
            try:
                found = date(full_year, int(month), int(day))
            except ValueError:
                continue
            if not 2000 <= found.year <= 2100:
                continue
            if _DATE_KEYWORD.search(line):
                return Field(found, 0.95)
            fallback = fallback or Field(found, 0.75)
    return fallback


def _last_amount_on_keyword_line(
    lines: list[str], keyword: re.Pattern[str], exclude: tuple[re.Pattern[str], ...] = ()
) -> Decimal | None:
    """The amount on the last line with this keyword: totals are printed after the items."""
    for line in reversed(lines):
        if keyword.search(line) and not any(pattern.search(line) for pattern in exclude):
            amounts = _amounts(line)
            if amounts:
                return amounts[-1]
    return None


def find_amounts(lines: list[str]) -> tuple[Field[Decimal] | None, Field[Decimal] | None, Field[Decimal] | None]:
    total = None
    # "ΣΥΝΟΛΟ ΦΠΑ" and "ΚΑΘΑΡΟ ΣΥΝΟΛΟ" are parts, not the total; the stronger keywords are tried first
    for confidence, keyword in zip((0.9, 0.8), _TOTAL_KEYWORDS, strict=True):
        amount = _last_amount_on_keyword_line(lines, keyword, exclude=(_NET_KEYWORD, _VAT_LINE))
        if amount is not None:
            total = Field(amount, confidence)
            break
    net = _last_amount_on_keyword_line(lines, _NET_KEYWORD)
    vat = _last_amount_on_keyword_line(lines, _VAT_KEYWORD, exclude=(_AFM_LINE, _INCLUDING_VAT, _TOTAL_KEYWORDS[0]))
    return (
        Field(net, 0.85) if net is not None else None,
        Field(vat, 0.85) if vat is not None else None,
        total,
    )


def _checks(fields: ExtractedFields) -> list[Check]:
    checks = []
    if fields.issuer_afm is None:
        checks.append(Check("ISSUER_AFM_FOUND", False, "No valid ΑΦΜ found"))
    net, vat, total = (f.value if f else None for f in (fields.net_amount, fields.vat_amount, fields.total_amount))
    if net is not None and vat is not None and total is not None:
        difference = abs(net + vat - total)
        checks.append(Check("AMOUNTS_ADD_UP", difference <= TOLERANCE, f"{net} + {vat} = {net + vat}, total {total}"))
    if net and vat is not None:
        rate = vat / net
        nearest = min(VAT_RATES, key=lambda r: abs(r - rate))
        checks.append(Check("VAT_RATE_KNOWN", abs(nearest - rate) <= Decimal("0.005"), f"VAT is {rate:.1%} of net"))
    if total is None:
        checks.append(Check("TOTAL_FOUND", False, "No total found"))
    return checks


def extract_fields(text: str) -> ExtractedFields:
    lines = [normalise(line) for line in text.splitlines() if line.strip()]
    fields = ExtractedFields(issuer_afm=find_afm(lines), issue_date=find_date(lines))
    fields.net_amount, fields.vat_amount, fields.total_amount = find_amounts(lines)

    # Work out a missing part from the other two, and say that it was worked out
    net, vat, total = fields.net_amount, fields.vat_amount, fields.total_amount
    if total and vat and not net:
        fields.net_amount = Field(total.value - vat.value, 0.7)
    elif total and net and not vat:
        fields.vat_amount = Field(total.value - net.value, 0.7)
    elif net and vat and not total:
        fields.total_amount = Field(net.value + vat.value, 0.7)

    fields.checks = _checks(fields)
    return fields
