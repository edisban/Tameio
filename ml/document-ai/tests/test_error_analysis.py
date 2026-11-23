import json

import pandas as pd

from extraction.error_analysis import (
    accuracy_by,
    accuracy_by_field,
    category_confusion,
    kinds_of_mistake,
    markdown,
    mistakes,
    report,
    table,
    top_confusions,
)
from extraction.evaluate import predict, write_predictions
from training.dataset import Example


def receipt(id, shop, category, afm="094014201", text="ΑΦΜ 094014201"):
    expected = {
        "issuer_afm": afm,
        "issue_date": "2025-10-10",
        "net_amount": "10.00",
        "vat_amount": "2.40",
        "total_amount": "12.40",
        "category": category,
    }
    return Example(id=id, shop=shop, text=text, expected=expected)


TEST = [
    receipt("t1", "ΠΡΑΤΗΡΙΟ Α", "FUEL"),
    receipt("t2", "ΠΡΑΤΗΡΙΟ Α", "FUEL"),
    receipt("t3", "ΤΑΒΕΡΝΑ Β", "RESTAURANT", text="ΑΦΜ 0940142Ο1"),
    receipt("t4", "ΤΑΒΕΡΝΑ Β", "RESTAURANT"),
]


def answers():
    """t1 right; t2 called FUEL a TRANSPORT; t3 found no ΑΦΜ (OCR read 0 as Ο); t4 was not valid JSON."""
    right = [dict(e.expected) for e in TEST]
    right[1]["category"] = "TRANSPORT"
    right[2]["issuer_afm"] = None
    return [
        {"id": "t1", "found": right[0]},
        {"id": "t2", "found": right[1]},
        {"id": "t3", "found": right[2]},
        {"id": "t4", "found": None},
    ]


def test_a_field_is_right_only_when_it_matches_exactly():
    df = table(TEST, answers())

    assert df["ok_category"].tolist() == [True, False, True, False]
    assert df["ok_issuer_afm"].tolist() == [True, True, False, False]


def test_accuracy_by_field_is_the_share_of_receipts_with_it_right():
    accuracy = accuracy_by_field(table(TEST, answers()))

    assert accuracy["category"] == 0.5
    assert accuracy["issue_date"] == 0.75


def test_accuracy_by_category_counts_its_receipts_and_averages_each_field():
    by_category = accuracy_by(table(TEST, answers()), "category")

    assert by_category.loc["FUEL", "receipts"] == 2
    assert by_category.loc["FUEL", "category"] == 0.5
    assert by_category.loc["RESTAURANT", "issuer_afm"] == 0.0


def test_accuracy_by_shop_groups_the_receipts_of_each_shop():
    by_shop = accuracy_by(table(TEST, answers()), "shop")

    assert by_shop["receipts"].to_dict() == {"ΠΡΑΤΗΡΙΟ Α": 2, "ΤΑΒΕΡΝΑ Β": 2}


def test_the_confusion_table_counts_each_true_category_against_the_predicted_one():
    confusion = category_confusion(table(TEST, answers()))

    assert confusion.loc["FUEL", "TRANSPORT"] == 1
    assert confusion.loc["FUEL", "FUEL"] == 1
    assert confusion.loc["RESTAURANT", "(none)"] == 1


def test_only_wrong_pairs_count_as_confusions():
    worst = top_confusions(category_confusion(table(TEST, answers())))

    assert worst.to_dict() == {("FUEL", "TRANSPORT"): 1, ("RESTAURANT", "(none)"): 1}


def test_a_mistake_is_either_nothing_found_or_a_wrong_value():
    kinds = kinds_of_mistake(table(TEST, answers()))

    assert kinds.loc["issuer_afm"].to_dict() == {"wrong": 2, "found nothing": 2, "found a wrong value": 0}
    assert kinds.loc["category"].to_dict() == {"wrong": 2, "found nothing": 1, "found a wrong value": 1}


def test_mistakes_come_with_the_text_that_was_read():
    wrong = mistakes(table(TEST, answers()), "issuer_afm")

    assert wrong["id"].tolist() == ["t3", "t4"]
    assert wrong.iloc[0]["text"] == "ΑΦΜ 0940142Ο1"


def test_markdown_shows_shares_as_percentages_and_counts_as_they_are():
    df = pd.DataFrame({"receipts": [27], "category": [0.852]}, index=pd.Index(["RENT"], name="category"))

    assert markdown(df).splitlines()[2] == "| RENT | 27 | 85.2% |"


def test_the_report_has_every_section():
    text = report(table(TEST, answers()), "baseline")

    for heading in (
        "Accuracy by field",
        "Kinds of mistake",
        "by expense category",
        "by shop",
        "confused",
        "`issuer_afm`",
    ):
        assert heading in text
    assert "found `(nothing)`" in text


class Reader:
    model_name = "fake"
    hardware = "CPU"

    def __init__(self, answers):
        self._answers = iter(answers)

    def size_mb(self):
        return 0.0

    def read(self, text):
        return next(self._answers)


def test_predictions_are_saved_without_timings_so_the_file_does_not_change_between_machines(tmp_path):
    predictions = predict(Reader([TEST[0].expected, None]), TEST[:2])
    path = tmp_path / "predictions.jsonl"

    write_predictions(path, predictions)

    saved = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    assert saved == [{"id": "t1", "found": TEST[0].expected}, {"id": "t2", "found": None}]
