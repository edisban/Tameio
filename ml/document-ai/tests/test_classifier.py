"""The model's quality gate: CI fails if a change to the code or the data makes it worse."""

import pytest

from document_ai.classifier import ExpenseClassifier
from training.receipts import generate
from training.train_classifier import evaluate_classifier, evaluate_extraction, split_by_shop

# Floors a little under today's scores, so random noise does not fail a build but a real drop does
MIN_MACRO_F1_UNSEEN_SHOPS = 0.82
MIN_FIELD_ACCURACY_CLEAN = 0.98
MIN_FIELD_ACCURACY_NOISY = 0.85


def test_classifier_generalises_to_shops_it_never_saw():
    receipts = generate(250, seed=42, noise=0.04)
    train, test = split_by_shop(receipts)

    assert not {r.shop for r in train} & {r.shop for r in test}
    assert evaluate_classifier(train, test)["macro_f1"] >= MIN_MACRO_F1_UNSEEN_SHOPS


@pytest.mark.parametrize(("noise", "floor"), [(0.0, MIN_FIELD_ACCURACY_CLEAN), (0.02, MIN_FIELD_ACCURACY_NOISY)])
def test_field_extraction_is_accurate_on_known_receipts(noise, floor):
    accuracy = evaluate_extraction(generate(40, seed=99, noise=noise))

    assert min(accuracy.values()) >= floor, accuracy


@pytest.mark.parametrize(
    ("text", "category"),
    [
        ("ΠΡΑΤΗΡΙΟ ΚΑΥΣΙΜΩΝ ΚΩΣΤΑ\nΑΜΟΛΥΒΔΗ 95 30 Χ 1,85 55,50\nΣΥΝΟΛΟ 55,50", "FUEL"),
        ("ΤΑΒΕΡΝΑ Ο ΓΙΑΝΝΗΣ\nΣΟΥΒΛΑΚΙ 3 Χ 3,50 10,50\nΜΠΥΡΑ 2 Χ 4,00 8,00", "RESTAURANT"),
        ("ΛΟΓΙΣΤΙΚΟ ΓΡΑΦΕΙΟ ΜΑΡΚΟΥ\nΑΜΟΙΒΗ ΛΟΓΙΣΤΙΚΗΣ ΥΠΟΣΤΗΡΙΞΗΣ ΝΟΕΜΒΡΙΟΥ 150,00", "PROFESSIONAL_SERVICES"),
    ],
)
def test_suggests_the_obvious_category(classifier: ExpenseClassifier, text, category):
    result = classifier.classify(text)

    assert result.best.category == category
    assert len(result.alternatives) == 2
    assert result.best.confidence >= result.alternatives[0].confidence


def test_saves_and_loads_the_model_with_its_version(classifier: ExpenseClassifier, tmp_path):
    path = tmp_path / "model.joblib"
    classifier.save(path)

    loaded = ExpenseClassifier.load(path)

    assert loaded.version == "expense-classifier:test"
    assert loaded.classify("ΑΜΟΛΥΒΔΗ 95").best == classifier.classify("ΑΜΟΛΥΒΔΗ 95").best
