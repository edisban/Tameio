"""The field-extraction dataset: synthetic receipts with their true fields, split by shop, as JSONL.

    python -m training.dataset

Writes data/field-extraction/<version>/{train,validation,test}.jsonl and a manifest with the counts,
the shops in each split and a hash of every file. The files are committed, so every variant is
measured on exactly the same receipts; a test checks the generator still produces them byte for byte.

The test split is the golden set: every test receipt carries the fields a correct reader must return.
"""

import argparse
import hashlib
import json
import random
from dataclasses import dataclass
from pathlib import Path

from training.receipts import CATEGORIES, Receipt, generate

VERSION = "v1"
SEED = 2026
PER_CATEGORY = 80
# The noise of a decent phone photo: a few letters per receipt read wrong
NOISE = 0.02
SPLITS = ("train", "validation", "test")
# Shops per category kept out of training; the rest of the category's shops train
TEST_SHOPS = 2
VALIDATION_SHOPS = 1
FIELDS = ("issuer_afm", "issue_date", "net_amount", "vat_amount", "total_amount", "category")
DATA_ROOT = Path("data/field-extraction")


@dataclass(frozen=True)
class Example:
    id: str
    shop: str
    text: str
    expected: dict[str, str]


def assign_shops(seed: int = SEED) -> dict[str, str]:
    """Each shop goes to exactly one split, so a test receipt always comes from a shop never trained on."""
    rng = random.Random(seed)
    split_of = {}
    for spec in CATEGORIES.values():
        shops = sorted(spec["shops"])
        rng.shuffle(shops)
        for position, shop in enumerate(shops):
            if position < TEST_SHOPS:
                split_of[shop] = "test"
            elif position < TEST_SHOPS + VALIDATION_SHOPS:
                split_of[shop] = "validation"
            else:
                split_of[shop] = "train"
    return split_of


def expected_fields(receipt: Receipt) -> dict[str, str]:
    """The answer, as text: amounts with two decimals and dates as ISO, so a match is a plain comparison."""
    return {
        "issuer_afm": receipt.afm,
        "issue_date": receipt.issue_date.isoformat(),
        "net_amount": f"{receipt.net:.2f}",
        "vat_amount": f"{receipt.vat:.2f}",
        "total_amount": f"{receipt.total:.2f}",
        "category": receipt.category,
    }


def build(root: Path = DATA_ROOT) -> dict:
    split_of = assign_shops()
    rows: dict[str, list[dict]] = {split: [] for split in SPLITS}
    for receipt in generate(PER_CATEGORY, seed=SEED, noise=NOISE):
        split = split_of[receipt.shop]
        rows[split].append(
            {
                "id": f"{split}-{len(rows[split]):04d}",
                "shop": receipt.shop,
                "text": receipt.text,
                "expected": expected_fields(receipt),
            }
        )

    out = root / VERSION
    out.mkdir(parents=True, exist_ok=True)
    files = {}
    for split, items in rows.items():
        content = "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in items)
        (out / f"{split}.jsonl").write_text(content, encoding="utf-8", newline="\n")
        files[split] = {
            "receipts": len(items),
            "shops": sorted({item["shop"] for item in items}),
            "sha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
        }
    manifest = {
        "name": "field-extraction",
        "version": VERSION,
        "generator": "training/receipts.py",
        "seed": SEED,
        "per_category": PER_CATEGORY,
        "ocr_noise": NOISE,
        "fields": list(FIELDS),
        "splits": files,
    }
    (out / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n"
    )
    return manifest


def load(split: str, root: Path = DATA_ROOT) -> list[Example]:
    lines = (root / VERSION / f"{split}.jsonl").read_text(encoding="utf-8").splitlines()
    return [Example(**json.loads(line)) for line in lines]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=DATA_ROOT)
    manifest = build(parser.parse_args().root)
    print(json.dumps({split: info["receipts"] for split, info in manifest["splits"].items()}))


if __name__ == "__main__":
    main()
