from training.dataset import DATA_ROOT, FIELDS, SPLITS, VERSION, build, load
from training.receipts import CATEGORIES


def test_no_shop_appears_in_two_splits():
    shops = {split: {e.shop for e in load(split)} for split in SPLITS}

    assert not shops["train"] & shops["test"]
    assert not shops["train"] & shops["validation"]
    assert not shops["validation"] & shops["test"]


def test_every_category_is_in_the_golden_test_set():
    categories = {e.expected["category"] for e in load("test")}

    assert categories == set(CATEGORIES)


def test_every_golden_receipt_has_every_field():
    for example in load("test"):
        assert set(example.expected) == set(FIELDS)
        assert all(example.expected.values())


def test_the_committed_dataset_is_exactly_what_the_generator_produces(tmp_path):
    build(tmp_path)

    for name in [*(f"{split}.jsonl" for split in SPLITS), "manifest.json"]:
        assert (tmp_path / VERSION / name).read_bytes() == (DATA_ROOT / VERSION / name).read_bytes(), name
