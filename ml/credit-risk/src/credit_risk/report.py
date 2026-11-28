"""The figures for the model card, and a local experiment log.

Three figures with matplotlib and seaborn: the ROC curves of the model and the scorecard, the calibration (predicted
against actual default rate), and the weight of each measure. Each training run is recorded with MLflow in a local
database next to this package: its settings, its scores, the figures and the model itself, as a registered version.
Nothing is served from MLflow; the backend reads the exported JSON.
"""

import os
from pathlib import Path
from typing import Any

import pandas as pd
from sklearn.metrics import roc_curve
from sklearn.pipeline import Pipeline

from credit_risk.features import FEATURES

PACKAGE = Path(__file__).resolve().parents[2]
TRACKING_URI = f"sqlite:///{(PACKAGE / 'mlflow.db').as_posix()}"
REGISTERED_NAME = "tameio-credit-risk"


def figures(test: pd.DataFrame, predicted: Any, points: pd.Series, pipeline: Pipeline, out: Path) -> list[Path]:
    import matplotlib

    matplotlib.use("Agg")  # files only, no window
    import matplotlib.pyplot as plt
    import seaborn as sns

    sns.set_theme(style="whitegrid")
    out.mkdir(parents=True, exist_ok=True)
    paths = []

    fig, ax = plt.subplots(figsize=(5, 5))
    for label, risk in (("Model", predicted), ("Scorecard", -points)):
        fpr, tpr, _ = roc_curve(test["defaulted"], risk)
        ax.plot(fpr, tpr, label=label)
    ax.plot([0, 1], [0, 1], linestyle="--", color="grey", linewidth=1)
    ax.set(xlabel="Good loans flagged (false positive rate)", ylabel="Bad loans caught (true positive rate)",
           title="ROC on applications not trained on")  # fmt: skip
    ax.legend()
    paths.append(_save(fig, out / "roc.png"))

    groups = pd.DataFrame({"predicted": predicted, "actual": test["defaulted"].to_numpy()})
    groups["group"] = pd.qcut(groups["predicted"], 10, labels=False, duplicates="drop")
    table = groups.groupby("group")[["predicted", "actual"]].mean()
    fig, ax = plt.subplots(figsize=(5, 5))
    ax.plot([0, table.max().max()], [0, table.max().max()], linestyle="--", color="grey", linewidth=1)
    sns.scatterplot(data=table, x="predicted", y="actual", ax=ax, s=60)
    ax.set(xlabel="Predicted default rate", ylabel="Actual default rate", title="Calibration, by tenths")
    paths.append(_save(fig, out / "calibration.png"))

    weights = pd.DataFrame({"measure": FEATURES, "weight": pipeline.named_steps["model"].coef_[0]})
    fig, ax = plt.subplots(figsize=(6, 3.5))
    # Red raises the estimate of default, blue lowers it
    weights["effect"] = weights["weight"].map(lambda w: "raises risk" if w > 0 else "lowers risk")
    sns.barplot(data=weights, y="measure", x="weight", ax=ax, hue="effect",
                palette={"raises risk": "#c0504d", "lowers risk": "#4f81bd"})  # fmt: skip
    ax.set(xlabel="Weight per standard deviation (log-odds of default)", ylabel="", title="What moves the estimate")
    paths.append(_save(fig, out / "weights.png"))
    return paths


def _save(fig: Any, path: Path) -> Path:
    fig.tight_layout()
    fig.savefig(path, dpi=120)
    fig.clf()
    return path


def track(pipeline: Pipeline, params: dict[str, Any], metrics: dict[str, Any], artifacts: list[Path]) -> str:
    """Records the run in the local MLflow and registers the model; returns its registry version."""
    os.environ.setdefault("MLFLOW_DISABLE_AGENT_HINT", "1")
    import mlflow
    import mlflow.sklearn

    mlflow.set_tracking_uri(TRACKING_URI)
    # The files go next to the database, not wherever the command happens to run
    if mlflow.get_experiment_by_name("credit-risk") is None:
        mlflow.create_experiment("credit-risk", artifact_location=(PACKAGE / "mlruns").as_uri())
    mlflow.set_experiment("credit-risk")
    with mlflow.start_run(run_name="logistic regression"):
        mlflow.log_params(params)
        mlflow.log_metrics({
            "auc_model": metrics["auc"]["model"], "auc_scorecard": metrics["auc"]["scorecard"],
            "ks_model": metrics["ks"]["model"], "ks_scorecard": metrics["ks"]["scorecard"],
            "brier_model": metrics["brier"]["model"], "brier_base_rate": metrics["brier"]["baseRate"],
        })  # fmt: skip
        for path in artifacts:
            mlflow.log_artifact(str(path), artifact_path="figures")
        info = mlflow.sklearn.log_model(pipeline, name="model", registered_model_name=REGISTERED_NAME)
    return str(info.registered_model_version)
