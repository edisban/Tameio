import json

import numpy as np
import pandas as pd
import pytest
from sklearn.model_selection import train_test_split

from credit_risk.features import CAPS, FEATURES, Features, from_history
from credit_risk.golden import GOLDEN_FILE, build, probability
from credit_risk.scorecard import score
from credit_risk.simulate import loan_book
from credit_risk.train import MODEL_FILE, evaluate, export


def months(inflow: list[float], outflow: list[float]) -> pd.DataFrame:
    return pd.DataFrame({"inflow": inflow, "outflow": outflow})


def test_the_measures_come_from_the_months() -> None:
    measures = from_history(12000, 500, 3000, 20, months([1000, 3000] * 6, [1500] * 12))

    assert measures.repayment_ratio == pytest.approx(1.0)  # 500 of a 500 surplus
    assert measures.loan_size == pytest.approx(0.5)  # 12000 of 24000 a year
    assert measures.cash_cushion == pytest.approx(6.0)
    assert measures.inflow_volatility == pytest.approx(0.5)  # sd 1000 over a mean of 2000
    assert measures.negative_months == pytest.approx(0.5)  # every other month


def test_no_surplus_or_no_inflow_gives_the_worst_values() -> None:
    measures = from_history(5000, 230, 100, 2, months([0, 0], [300, 250]))

    assert measures.repayment_ratio == CAPS["repayment_ratio"]
    assert measures.loan_size == CAPS["loan_size"]
    assert measures.inflow_volatility == CAPS["inflow_volatility"]
    assert measures.negative_months == 1.0


def test_the_scorecard_matches_the_backends_tiers() -> None:
    best = Features(0.10, 0.20, 24, 8, 0.1, 0.0)
    middle = Features(0.30, 0.40, 8, 4, 0.1, 0.0)
    worst = Features(0.80, 2.00, 1, 0.5, 0.1, 0.0)

    assert score(best) == 100
    assert score(middle) == 18 + 15 + 15 + 15
    assert score(worst) == 0


@pytest.fixture(scope="module")
def evaluated() -> tuple[object, dict]:
    book = loan_book()
    train, test = train_test_split(book, test_size=0.3, random_state=7, stratify=book["defaulted"])
    pipeline, metrics, _, _ = evaluate(train, test)
    return pipeline, metrics


def test_the_model_beats_the_scorecard_on_loans_it_has_not_seen(evaluated: tuple[object, dict]) -> None:
    _, metrics = evaluated
    assert metrics["auc"]["model"] > metrics["auc"]["scorecard"] + 0.03
    assert metrics["ks"]["model"] > metrics["ks"]["scorecard"]
    assert metrics["brier"]["model"] < metrics["brier"]["baseRate"]
    # A simulated small-business book: a default rate a lender would recognise
    assert 0.05 < metrics["defaultRate"] < 0.15


def test_its_probabilities_are_calibrated(evaluated: tuple[object, dict]) -> None:
    _, metrics = evaluated
    for group in metrics["calibration"]:
        assert abs(group["predicted"] - group["actual"]) < 0.06


def test_the_exported_numbers_give_what_scikit_learn_gives(evaluated: tuple[object, dict]) -> None:
    pipeline, metrics = evaluated
    model = export(pipeline, metrics)
    book = loan_book(n=50, seed=99)

    by_hand = [probability(model, row)[0] for row in book[FEATURES].to_dict("records")]
    assert np.allclose(by_hand, pipeline.predict_proba(book[FEATURES])[:, 1], atol=1e-6)


def test_the_shared_examples_are_up_to_date() -> None:
    # Regenerate with `python -m credit_risk.golden` after retraining, so the backend test checks the new model
    written = json.loads(GOLDEN_FILE.read_text(encoding="utf-8"))
    assert written["modelVersion"] == json.loads(MODEL_FILE.read_text(encoding="utf-8"))["version"]
    assert written["cases"] == build()
