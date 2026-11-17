from datetime import date
from decimal import Decimal

import pytest

from document_ai.afm import is_valid_afm
from document_ai.fields import extract_fields, parse_amount
from document_ai.text import clean_ocr_text, normalise

GREEK_RECEIPT = """
ΚΑΦΕ ΤΟ ΣΤΕΚΙ
ΕΡΜΟΥ 12, ΑΘΗΝΑ
Α.Φ.Μ.: 094014201 ΔΟΥ Α' ΑΘΗΝΩΝ
ΑΠΟΔΕΙΞΗ ΛΙΑΝΙΚΗΣ ΠΩΛΗΣΗΣ
ΗΜΕΡΟΜΗΝΙΑ: 17/11/2026 10:42
ΦΡΕΝΤΟ ΕΣΠΡΕΣΟ 2 X 3,50 7,00
ΤΥΡΟΠΙΤΑ 1 X 2,80 2,80
ΚΑΘΑΡΗ ΑΞΙΑ 8,67
ΦΠΑ 13% 1,13
ΣΥΝΟΛΟ 9,80
ΜΕΤΡΗΤΑ
"""


class TestAfm:
    @pytest.mark.parametrize("afm", ["094014201", "090000045", "123456783"])
    def test_accepts_valid_numbers(self, afm):
        assert is_valid_afm(afm)

    @pytest.mark.parametrize("afm", ["123456789", "000000000", "09401420", "0940142011", "09401420A"])
    def test_rejects_invalid_numbers(self, afm):
        assert not is_valid_afm(afm)


class TestNormalise:
    def test_removes_accents_and_upper_cases(self):
        assert normalise("Απόδειξη") == "ΑΠΟΔΕΙΞΗ"

    def test_turns_latin_look_alikes_into_greek(self):
        # "ΣYNOΛO" as OCR often returns it, with Latin Y, N and O
        assert normalise("ΣYNOΛO") == normalise("ΣΥΝΟΛΟ")

    def test_reads_tesseracts_symbol_for_phi_as_phi(self):
        # Seen on a real photo: "Α.Φ.Μ." came back as "A.®.M."
        assert normalise("A.®.M.") == "Α.Φ.Μ."

    def test_drops_characters_json_cannot_carry(self):
        assert clean_ocr_text("ΣΥΝΟΛΟ \udc9d 5,00") == "ΣΥΝΟΛΟ ? 5,00"

    def test_finds_the_afm_keyword_in_ocr_output(self):
        fields = extract_fields("A.®.M.: 094014201 ΔΟΥ ΙΓ’ ΑΘΗΝΩΝ")

        assert fields.issuer_afm.confidence == 0.95


class TestParseAmount:
    @pytest.mark.parametrize(
        ("text", "expected"),
        [
            ("9,80", "9.80"),
            ("1.234,56", "1234.56"),
            ("1,234.56", "1234.56"),
            ("12.50", "12.50"),
            ("1.234.567,00", "1234567.00"),
        ],
    )
    def test_reads_greek_and_english_formats(self, text, expected):
        assert parse_amount(text) == Decimal(expected)


class TestExtractFields:
    def test_reads_every_field_of_a_greek_receipt(self):
        fields = extract_fields(GREEK_RECEIPT)

        assert fields.issuer_afm.value == "094014201"
        assert fields.issuer_afm.confidence > 0.9
        assert fields.issue_date.value == date(2026, 11, 17)
        assert fields.net_amount.value == Decimal("8.67")
        assert fields.vat_amount.value == Decimal("1.13")
        assert fields.total_amount.value == Decimal("9.80")
        assert all(check.passed for check in fields.checks)

    def test_reads_lower_case_text_with_accents(self):
        fields = extract_fields(GREEK_RECEIPT.lower().replace("συνολο", "σύνολο"))

        assert fields.total_amount.value == Decimal("9.80")
        assert fields.issuer_afm.value == "094014201"

    def test_works_out_the_net_amount_when_only_total_and_vat_are_printed(self):
        text = "ΑΦΜ 094014201\nΦΠΑ 24% 19,72\nΠΛΗΡΩΤΕΟ ΠΟΣΟ 101,88"

        fields = extract_fields(text)

        assert fields.net_amount.value == Decimal("82.16")
        # Worked out, not read, so less certain
        assert fields.net_amount.confidence < fields.total_amount.confidence

    def test_does_not_take_a_word_ending_in_net_for_the_net_amount(self):
        # Regression: "INTERNET" was read as a "NET" line
        text = "ΑΦΜ 094014201\nΣΥΝΔΕΣΗ INTERNET VDSL 3 X 33,96 101,88\nΦΠΑ 24% 19,72\nΣΥΝΟΛΟ 101,88"

        assert extract_fields(text).net_amount.value == Decimal("82.16")

    def test_does_not_take_the_vat_subtotal_for_the_total(self):
        text = "ΚΑΘΑΡΗ ΑΞΙΑ 100,00\nΣΥΝΟΛΟ ΦΠΑ 24,00\nΣΥΝΟΛΟ ΜΕ ΦΠΑ 124,00"

        fields = extract_fields(text)

        assert fields.total_amount.value == Decimal("124.00")
        assert fields.vat_amount.value == Decimal("24.00")

    def test_does_not_take_a_date_or_a_percentage_for_an_amount(self):
        text = "ΣΥΝΟΛΟ 17.11.26 ΦΠΑ 24,00% 12,40"

        assert extract_fields(text).total_amount.value == Decimal("12.40")

    def test_flags_amounts_that_do_not_add_up(self):
        text = "ΑΦΜ 094014201\nΚΑΘΑΡΗ ΑΞΙΑ 10,00\nΦΠΑ 24% 2,40\nΣΥΝΟΛΟ 15,00"

        checks = {check.name: check.passed for check in extract_fields(text).checks}

        assert checks["AMOUNTS_ADD_UP"] is False

    def test_flags_an_unknown_vat_rate(self):
        text = "ΚΑΘΑΡΗ ΑΞΙΑ 10,00\nΦΠΑ 3,00\nΣΥΝΟΛΟ 13,00"

        checks = {check.name: check.passed for check in extract_fields(text).checks}

        assert checks["VAT_RATE_KNOWN"] is False

    def test_skips_an_invalid_afm_and_an_impossible_date(self):
        text = "ΑΦΜ 123456789\nΗΜΕΡΟΜΗΝΙΑ 31/02/2026\n05/03/2026\nΣΥΝΟΛΟ 5,00"

        fields = extract_fields(text)

        assert fields.issuer_afm is None
        assert fields.issue_date.value == date(2026, 3, 5)
        assert {"ISSUER_AFM_FOUND"} <= {check.name for check in fields.checks if not check.passed}

    def test_finds_nothing_in_text_without_a_receipt(self):
        fields = extract_fields("Αγαπητέ πελάτη, σας ευχαριστούμε.")

        assert fields.total_amount is None
        assert fields.issuer_afm is None
