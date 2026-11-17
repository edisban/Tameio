"""Trains the expense classifier and measures it, together with field extraction.

    python -m training.train_classifier --out models

Writes models/expense-classifier.joblib and models/metrics.json. The Docker build runs this, so
every image carries a model trained from the code in the same commit.
"""

import argparse
import json
import random
from datetime import UTC, datetime
from pathlib import Path

from sklearn.metrics import accuracy_score, classification_report, f1_score

from document_ai.classifier import ExpenseClassifier, build_pipeline
from document_ai.fields import extract_fields
from training.receipts import CATEGORIES, Receipt, generate

MODEL_VERSION = "0.1.0"
DATASET = "synthetic-receipts-v1"
SEED = 42


def split_by_shop(receipts: list[Receipt], seed: int = SEED) -> tuple[list[Receipt], list[Receipt]]:
    """Two shops of every category go to the test set only, so the test measures unseen shops."""
    rng = random.Random(seed)
    held_out = {shop for spec in CATEGORIES.values() for shop in rng.sample(spec["shops"], 2)}
    train = [r for r in receipts if r.shop not in held_out]
    test = [r for r in receipts if r.shop in held_out]
    return train, test


def evaluate_classifier(train: list[Receipt], test: list[Receipt]) -> dict:
    pipeline = build_pipeline(SEED).fit([r.text for r in train], [r.category for r in train])
    predicted = pipeline.predict([r.text for r in test])
    actual = [r.category for r in test]
    return {
        "train_size": len(train),
        "test_size": len(test),
        "accuracy": round(accuracy_score(actual, predicted), 4),
        "macro_f1": round(f1_score(actual, predicted, average="macro"), 4),
        "per_category": {
            category: {
                "precision": round(s["precision"], 3),
                "recall": round(s["recall"], 3),
                "f1": round(s["f1-score"], 3),
            }
            for category, s in classification_report(actual, predicted, output_dict=True, zero_division=0).items()
            if category in CATEGORIES
        },
    }


def evaluate_extraction(receipts: list[Receipt]) -> dict:
    """Share of receipts where each field came out exactly right."""
    hits = {"issuer_afm": 0, "issue_date": 0, "net_amount": 0, "vat_amount": 0, "total_amount": 0}
    for receipt in receipts:
        fields = extract_fields(receipt.text)
        expected = {
            "issuer_afm": receipt.afm,
            "issue_date": receipt.issue_date,
            "net_amount": receipt.net,
            "vat_amount": receipt.vat,
            "total_amount": receipt.total,
        }
        for name, value in expected.items():
            found = getattr(fields, name)
            hits[name] += found is not None and found.value == value
    return {name: round(count / len(receipts), 4) for name, count in hits.items()}


def train(out: Path, per_category: int = 250) -> dict:
    # Trained on noisier text than a clean scan, so it holds up on photos
    receipts = generate(per_category, seed=SEED, noise=0.04)
    train_set, test_set = split_by_shop(receipts)
    metrics = {
        "model": "expense-classifier",
        "version": MODEL_VERSION,
        "dataset": DATASET,
        "trained_at": datetime.now(UTC).isoformat(timespec="seconds"),
        "classifier": evaluate_classifier(train_set, test_set),
        "extraction": {
            "clean": evaluate_extraction(generate(40, seed=SEED + 1, noise=0.0)),
            "with_ocr_noise": evaluate_extraction(generate(40, seed=SEED + 2, noise=0.02)),
        },
    }

    # The shipped model learns from every shop; the numbers above come from the held-out run
    final = build_pipeline(SEED).fit([r.text for r in receipts], [r.category for r in receipts])
    ExpenseClassifier(final, {k: metrics[k] for k in ("version", "dataset", "trained_at")}).save(
        out / "expense-classifier.joblib"
    )
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("models"))
    metrics = train(parser.parse_args().out)
    print(
        json.dumps(
            {
                "classifier": {k: metrics["classifier"][k] for k in ("accuracy", "macro_f1")},
                "extraction": metrics["extraction"],
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
