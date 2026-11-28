"""Shared examples that keep the backend's Java equal to this package.

    python -m credit_risk.golden

A handful of applications, each with its twelve months of history, written with the measures and the probability
this package computes from the exported model. A backend test reads the same file and must get the same numbers,
so the two cannot drift apart without a test failing.
"""

import json
import math
from typing import Any

import pandas as pd

from credit_risk.features import from_history
from credit_risk.train import MODEL_FILE, ROOT

GOLDEN_FILE = ROOT / "backend/src/test/resources/lending/credit-golden.json"

CASES: list[dict[str, Any]] = [
    {"name": "steady and well covered", "amount": 20000, "instalment": 620, "balance": 18000, "historyMonths": 40,
     "inflow": [12000] * 12, "outflow": [9000] * 12},
    {"name": "seasonal, with lean months", "amount": 30000, "instalment": 930, "balance": 2500, "historyMonths": 14,
     "inflow": [4000, 3500, 5000, 9000, 15000, 22000, 26000, 25000, 14000, 7000, 4500, 4000],
     "outflow": [8000, 8000, 8500, 9000, 11000, 14000, 16000, 16000, 11000, 9000, 8000, 8000]},
    {"name": "spends more than it earns", "amount": 15000, "instalment": 460, "balance": 900, "historyMonths": 8,
     "inflow": [6000] * 12, "outflow": [6500] * 12},
    {"name": "new account, few months", "amount": 8000, "instalment": 360, "balance": 4000, "historyMonths": 3,
     "inflow": [7000, 7500, 6800], "outflow": [5000, 5200, 4900]},
    {"name": "large loan for its size", "amount": 120000, "instalment": 2380, "balance": 30000, "historyMonths": 60,
     "inflow": [20000, 21000, 19000, 22000, 20500, 21500, 20000, 19500, 22500, 21000, 20000, 20800],
     "outflow": [16000] * 12},
    {"name": "no money coming in", "amount": 5000, "instalment": 230, "balance": 100, "historyMonths": 2,
     "inflow": [0, 0], "outflow": [300, 250]},
]  # fmt: skip


def probability(model: dict[str, Any], measures: dict[str, float]) -> tuple[float, dict[str, float]]:
    """The model's estimate from its exported numbers, the way the backend works it out, with each measure's part."""
    parts = {}
    for feature in model["features"]:
        value = min(max(measures[feature["name"]], 0.0), feature["cap"])
        parts[feature["name"]] = feature["coefficient"] * (value - feature["mean"]) / feature["scale"]
    logit = model["intercept"] + sum(parts.values())
    return 1 / (1 + math.exp(-logit)), parts


def build() -> list[dict[str, Any]]:
    model = json.loads(MODEL_FILE.read_text(encoding="utf-8"))
    cases = []
    for case in CASES:
        months = pd.DataFrame({"inflow": case["inflow"], "outflow": case["outflow"]})
        measures = from_history(case["amount"], case["instalment"], case["balance"], case["historyMonths"], months)
        pd_value, parts = probability(model, measures.as_dict())
        cases.append({
            **case,
            "expected": {
                "measures": measures.as_dict(),
                "probability": round(pd_value, 6),
                "contributions": {name: round(part, 6) for name, part in parts.items()},
            },
        })  # fmt: skip
    return cases


def main() -> None:
    GOLDEN_FILE.parent.mkdir(parents=True, exist_ok=True)
    GOLDEN_FILE.write_text(json.dumps({"modelVersion": json.loads(MODEL_FILE.read_text())["version"],
                                       "cases": build()}, indent=2) + "\n", encoding="utf-8")  # fmt: skip
    print(f"Wrote {GOLDEN_FILE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
