"""Where a field-extraction variant goes wrong, with pandas: by field, category and shop, which categories it
confuses, and examples of its mistakes with the OCR text it read.

    python -m extraction.error_analysis                 # the baseline
    python -m extraction.error_analysis --variant prompted

Reads results/field-extraction/predictions-<variant>.jsonl, which extraction.evaluate writes, and the golden test
set; writes results/field-extraction/errors-<variant>.md (errors.md for the baseline).
"""

import argparse
import json
from pathlib import Path

import pandas as pd

from extraction.evaluate import RESULTS
from training.dataset import DATA_ROOT, FIELDS, Example, load

EXAMPLES = 10
# Fields whose mistakes are shown with the text, because a person can see at once why they went wrong
SHOWN_FIELDS = ("issuer_afm", "issue_date")
SNIPPET_LINES = 6


def table(test: list[Example], predictions: list[dict]) -> pd.DataFrame:
    """One row per receipt: its shop and text, and for each field what was expected, what was found and whether
    they match. A reply that was not valid JSON found nothing, so every field of it is wrong."""
    found_by_id = {p["id"]: p["found"] or {} for p in predictions}
    rows = []
    for example in test:
        found = found_by_id.get(example.id, {})
        row = {"id": example.id, "shop": example.shop, "text": example.text, "category": example.expected["category"]}
        for field in FIELDS:
            row[f"expected_{field}"] = example.expected[field]
            row[f"found_{field}"] = found.get(field)
            row[f"ok_{field}"] = found.get(field) == example.expected[field]
        rows.append(row)
    return pd.DataFrame(rows)


def ok_columns() -> list[str]:
    return [f"ok_{field}" for field in FIELDS]


def accuracy_by_field(df: pd.DataFrame) -> pd.Series:
    """The share of receipts with each field right: the mean of a True/False column."""
    return df[ok_columns()].mean().rename(lambda column: column.removeprefix("ok_"))


def kinds_of_mistake(df: pd.DataFrame) -> pd.DataFrame:
    """For each field, how many receipts got it wrong, and of those how many found nothing at all and how many
    found a wrong value. A rule that finds nothing is safe (a person fills it in); a wrong value can slip through."""
    rows = {}
    for field in FIELDS:
        wrong = df.loc[~df[f"ok_{field}"], f"found_{field}"]
        rows[field] = {
            "wrong": len(wrong),
            "found nothing": int(wrong.isna().sum()),
            "found a wrong value": int(wrong.notna().sum()),
        }
    result = pd.DataFrame.from_dict(rows, orient="index")
    result.index.name = "field"
    return result


def accuracy_by(df: pd.DataFrame, column: str) -> pd.DataFrame:
    """The same, for each value of a column (the category, the shop), with how many receipts each has."""
    grouped = df.groupby(column)
    result = grouped[ok_columns()].mean().rename(columns=lambda c: c.removeprefix("ok_"))
    result.insert(0, "receipts", grouped.size())
    return result.sort_values("receipts", ascending=False)


def category_confusion(df: pd.DataFrame) -> pd.DataFrame:
    """Rows: the true category; columns: what was predicted. The diagonal is right, everything else a confusion."""
    predicted = df["found_category"].fillna("(none)")
    return pd.crosstab(df["expected_category"], predicted, rownames=["expected"], colnames=["predicted"])


def top_confusions(confusion: pd.DataFrame, limit: int = 5) -> pd.Series:
    """The most frequent wrong (expected, predicted) pairs, largest first."""
    pairs = confusion.stack()
    wrong = pairs[[expected != predicted for expected, predicted in pairs.index]]
    return wrong[wrong > 0].sort_values(ascending=False).head(limit)


def mistakes(df: pd.DataFrame, field: str, limit: int = EXAMPLES) -> pd.DataFrame:
    """Receipts with this field wrong, with what was expected, what was found and the text."""
    wrong = df.loc[~df[f"ok_{field}"], ["id", "shop", f"expected_{field}", f"found_{field}", "text"]]
    return wrong.head(limit)


def markdown(df: pd.DataFrame, percent: bool = True) -> str:
    """A pandas table as markdown: numbers between 0 and 1 as percentages, everything else as it is."""

    def cell(value) -> str:
        if isinstance(value, float) and percent:
            return f"{value:.1%}"
        return str(value).replace("|", "\\|")

    header = [df.index.name or "", *map(str, df.columns)]
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    # itertuples keeps each column's type; iterrows would turn a row of counts and shares into floats
    for index, row in zip(df.index, df.itertuples(index=False), strict=True):
        lines.append("| " + " | ".join([str(index), *(cell(value) for value in row)]) + " |")
    return "\n".join(lines)


def shown(value) -> str:
    """A found value, or "(nothing)" where none was found: pandas holds that as a missing value."""
    return "(nothing)" if pd.isna(value) else str(value)


def snippet(text: str) -> str:
    """The first lines of the OCR text, enough to see the ΑΦΜ and the date."""
    return "\n".join(text.splitlines()[:SNIPPET_LINES])


def report(df: pd.DataFrame, variant: str) -> str:
    by_field = accuracy_by_field(df).to_frame("accuracy")
    by_field.index.name = "field"
    confusion = category_confusion(df)
    worst_pairs = top_confusions(confusion).rename("receipts").to_frame()
    worst_pairs.index = [f"{expected} → {predicted}" for expected, predicted in worst_pairs.index]
    worst_pairs.index.name = "expected → predicted"
    parts = [
        f"# Field extraction errors: {variant}",
        f"{len(df)} receipts of the golden test set, from shops no model trained on. "
        "Written by `python -m extraction.error_analysis`.",
        "## Accuracy by field",
        markdown(by_field),
        "## Kinds of mistake",
        "Finding nothing is the safe mistake: the review screen asks a person. A wrong value can slip through.",
        markdown(kinds_of_mistake(df), percent=False),
        "## Accuracy by expense category",
        markdown(accuracy_by(df, "category")),
        "## Accuracy by shop",
        markdown(accuracy_by(df, "shop")),
        "## Which categories are confused",
        "Rows are the true category, columns the predicted one; the diagonal is right.",
        markdown(confusion, percent=False),
        "The most frequent confusions:",
        markdown(worst_pairs, percent=False),
    ]
    for field in SHOWN_FIELDS:
        examples = mistakes(df, field)
        parts.append(f"## Mistakes in `{field}` ({int((~df[f'ok_{field}']).sum())} in all, the first {len(examples)})")
        for _, row in examples.iterrows():
            parts.append(
                f"**{row['id']}**, {row['shop']}: expected `{row[f'expected_{field}']}`, "
                f"found `{shown(row[f'found_{field}'])}`\n\n```\n{snippet(row['text'])}\n```"
            )
    return "\n\n".join(parts) + "\n"


def read_predictions(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--variant", default="baseline")
    parser.add_argument("--results", type=Path, default=RESULTS)
    parser.add_argument("--data", type=Path, default=DATA_ROOT)
    args = parser.parse_args()

    df = table(load("test", args.data), read_predictions(args.results / f"predictions-{args.variant}.jsonl"))
    name = "errors.md" if args.variant == "baseline" else f"errors-{args.variant}.md"
    (args.results / name).write_text(report(df, args.variant), encoding="utf-8", newline="\n")
    print(accuracy_by_field(df).round(3).to_string())


if __name__ == "__main__":
    main()
