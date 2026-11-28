"""Trains the credit model, checks it against the scorecard, and exports it for the backend.

    python -m credit_risk.train

A logistic regression on the six measures, standardised. It is scored on applications it did not learn from, next
to the scorecard: AUC (how well each ranks the loans that went bad above those that did not), the Brier score and
a calibration table (do 10% predictions go bad about 10% of the time). It is exported only if its AUC beats the
scorecard's; the export is the coefficients in JSON, so the backend computes the same probability in Java with no
Python service to call, and can show how much each measure moved it.
"""

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from scipy.stats import ks_2samp
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import brier_score_loss, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from credit_risk.features import CAPS, FEATURES, Features
from credit_risk.scorecard import score
from credit_risk.simulate import loan_book

ROOT = Path(__file__).resolve().parents[4]
MODEL_FILE = ROOT / "backend/src/main/resources/lending/credit-model.json"
RESULTS = Path(__file__).resolve().parents[2] / "results"
VERSION = "1"


def fit(book: pd.DataFrame) -> Pipeline:
    pipeline = Pipeline([("scale", StandardScaler()), ("model", LogisticRegression(C=1.0, max_iter=1000))])
    pipeline.fit(book[FEATURES], book["defaulted"])
    return pipeline


def calibration(actual: pd.Series, predicted: np.ndarray, bins: int = 5) -> list[dict[str, float]]:
    """Applications in groups by predicted probability: the average prediction and the share that went bad."""
    frame = pd.DataFrame({"actual": actual.to_numpy(), "predicted": predicted})
    frame["group"] = pd.qcut(frame["predicted"], bins, labels=False, duplicates="drop")
    table = frame.groupby("group").agg(predicted=("predicted", "mean"), actual=("actual", "mean"), n=("actual", "size"))
    return [{k: round(float(v), 4) for k, v in row.items()} for row in table.to_dict("records")]


def ks(actual: pd.Series, risk: Any) -> float:
    """Kolmogorov-Smirnov: the widest gap between how bad and good loans spread over the score (0 none, 1 perfect)."""
    risk = np.asarray(risk, dtype=float)
    return round(float(ks_2samp(risk[actual.to_numpy() == 1], risk[actual.to_numpy() == 0]).statistic), 4)


def evaluate(train: pd.DataFrame, test: pd.DataFrame) -> tuple[Pipeline, dict[str, Any]]:
    pipeline = fit(train)
    predicted = pipeline.predict_proba(test[FEATURES])[:, 1]
    points = test.apply(lambda row: score(Features(**row[FEATURES].to_dict())), axis=1)
    metrics = {
        "applications": {"train": len(train), "test": len(test)},
        "defaultRate": round(float(test["defaulted"].mean()), 4),
        # Fewer points mean more risk, so the scorecard ranks by the negated points
        "auc": {
            "model": round(float(roc_auc_score(test["defaulted"], predicted)), 4),
            "scorecard": round(float(roc_auc_score(test["defaulted"], -points)), 4),
        },
        # The usual separation figure in credit scoring, next to AUC
        "ks": {
            "model": ks(test["defaulted"], predicted),
            "scorecard": ks(test["defaulted"], -points),
        },
        "brier": {
            "model": round(float(brier_score_loss(test["defaulted"], predicted)), 4),
            # Predicting the average rate for everyone: what a useless model would score
            "baseRate": round(
                float(brier_score_loss(test["defaulted"], np.full(len(test), train["defaulted"].mean()))), 4
            ),
        },
        "calibration": calibration(test["defaulted"], predicted),
    }
    return pipeline, metrics, predicted, points


def export(pipeline: Pipeline, metrics: dict[str, Any]) -> dict[str, Any]:
    scaler: StandardScaler = pipeline.named_steps["scale"]
    model: LogisticRegression = pipeline.named_steps["model"]
    return {
        "model": "logistic-regression",
        "version": VERSION,
        "trainedOn": "simulated loan book (ml/credit-risk/src/credit_risk/simulate.py), not real loans",
        "intercept": round(float(model.intercept_[0]), 8),
        "features": [
            {
                "name": name,
                "cap": CAPS[name],
                "mean": round(float(scaler.mean_[i]), 8),
                "scale": round(float(scaler.scale_[i]), 8),
                "coefficient": round(float(model.coef_[0][i]), 8),
            }
            for i, name in enumerate(FEATURES)
        ],
        "metrics": {key: metrics[key] for key in ("auc", "ks", "brier", "defaultRate")},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail if the model does not beat the scorecard")
    parser.add_argument("--report", action="store_true", help="draw the figures and record the run in MLflow")
    args = parser.parse_args()

    book = loan_book()
    train, test = train_test_split(book, test_size=0.3, random_state=7, stratify=book["defaulted"])
    pipeline, metrics, predicted, points = evaluate(train, test)
    if args.report:
        from credit_risk.report import figures, track

        drawn = figures(test, predicted, points, pipeline, RESULTS / "figures")
        params = {"C": 1.0, "features": ",".join(FEATURES), "applications": len(book), "seed": 11}
        print(f"Recorded in MLflow as {track(pipeline, params, metrics, drawn)}")
    RESULTS.mkdir(exist_ok=True)
    (RESULTS / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metrics["auc"]), json.dumps(metrics["ks"]), json.dumps(metrics["brier"]))
    if metrics["auc"]["model"] <= metrics["auc"]["scorecard"]:
        raise SystemExit("The model does not beat the scorecard: not exported")
    if not args.check:
        MODEL_FILE.parent.mkdir(parents=True, exist_ok=True)
        MODEL_FILE.write_text(json.dumps(export(pipeline, metrics), indent=2) + "\n", encoding="utf-8")
        print(f"Exported to {MODEL_FILE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
