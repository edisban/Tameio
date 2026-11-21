from itertools import count

from extraction import prompt
from extraction.evaluate import check, merge, score, table
from extraction.schema import parse_reply
from training.dataset import FIELDS, Example

GOLDEN = {
    "issuer_afm": "094014201",
    "issue_date": "2025-10-10",
    "net_amount": "134.02",
    "vat_amount": "32.16",
    "total_amount": "166.18",
    "category": "TRANSPORT",
}
RECEIPT = Example(id="test-0000", shop="ΚΤΕΛ", text="ΣΥΝΟΛΟ 166,18", expected=GOLDEN)


class FakeReader:
    name = "fake"
    model_name = "fake model"
    hardware = "CPU"

    def __init__(self, answers):
        self._answers = iter(answers)

    def size_mb(self):
        return 1.0

    def read(self, text):
        return next(self._answers)


def metrics(accuracy, dataset="field-extraction-v1"):
    return {
        "dataset": dataset,
        "variants": {
            "baseline": {
                "field_accuracy": dict.fromkeys(FIELDS, accuracy),
                "all_fields_correct": accuracy,
                "invalid_json_rate": 0.0,
                "latency_ms": {"mean": 2.0, "p50": 2.0, "p95": 3.0},
                "model_size_mb": 1.2,
                "hardware": "CPU",
            }
        },
    }


def test_a_reply_wrapped_in_code_fences_is_read():
    reply = "```json\n" + prompt.answer(GOLDEN) + "\n```"

    assert parse_reply(reply).as_text() == GOLDEN


def test_a_reply_that_is_not_json_is_invalid():
    assert parse_reply("The total is 166.18 euro.") is None


def test_a_reply_with_an_eight_digit_afm_is_invalid():
    assert parse_reply(prompt.answer({**GOLDEN, "issuer_afm": "09401420"})) is None


def test_a_reply_with_an_unknown_category_is_invalid():
    assert parse_reply(prompt.answer({**GOLDEN, "category": "COFFEE"})) is None


def test_a_reply_with_a_key_the_schema_does_not_know_is_invalid():
    assert parse_reply(prompt.answer({**GOLDEN, "currency": "EUR"})) is None


def test_a_field_the_model_could_not_find_may_be_null():
    assert parse_reply(prompt.answer({**GOLDEN, "net_amount": None})).as_text()["net_amount"] is None


def test_the_training_target_is_a_valid_reply():
    target = prompt.training_example(RECEIPT)["completion"][0]["content"]

    assert parse_reply(target).as_text() == GOLDEN


def test_an_invalid_reply_misses_every_field_and_counts_as_invalid_json():
    reader = FakeReader([GOLDEN, None])

    result = score(reader, [RECEIPT, RECEIPT], clock=count().__next__)

    assert result["field_accuracy"] == dict.fromkeys(FIELDS, 0.5)
    assert result["all_fields_correct"] == 0.5
    assert result["invalid_json_rate"] == 0.5


def test_one_wrong_field_costs_only_that_field():
    reader = FakeReader([{**GOLDEN, "vat_amount": "32.15"}])

    result = score(reader, [RECEIPT], clock=count().__next__)

    assert result["field_accuracy"]["vat_amount"] == 0.0
    assert result["field_accuracy"]["total_amount"] == 1.0
    assert result["all_fields_correct"] == 0.0


def test_new_results_keep_the_variants_that_were_not_run():
    existing = merge(None, {"prompted": {"model": "llm"}})

    merged = merge(existing, {"baseline": {"model": "rules"}})

    assert list(merged["variants"]) == ["baseline", "prompted"]


def test_results_from_another_dataset_version_are_dropped():
    merged = merge({"dataset": "field-extraction-v0", "variants": {"prompted": {}}}, {"baseline": {}})

    assert list(merged["variants"]) == ["baseline"]


def test_the_check_reports_a_changed_score_but_not_a_changed_latency():
    fresh = metrics(0.9)["variants"]
    fresh["baseline"]["latency_ms"]["p50"] = 50.0

    assert check(fresh, metrics(0.9)) == []
    assert check(metrics(0.8)["variants"], metrics(0.9)) != []


def test_the_check_allows_one_receipt_read_differently_on_another_machine():
    assert check(metrics(0.896)["variants"], metrics(0.9)) == []


def test_the_comparison_has_a_row_per_variant():
    lines = table(metrics(0.9)).splitlines()

    assert any(line.startswith("| baseline | 90.0% |") for line in lines)
