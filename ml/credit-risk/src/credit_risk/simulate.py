"""A simulated loan book: past applications with twelve months of account history and whether each loan was repaid.

Tameio is a fictional bank, so it has no loans that went bad to learn from. This builds them with pandas and numpy,
and says plainly how: the chance of default rises with the scorecard's four measures as a lender would expect, and
also with two things the scorecard does not look at (income that swings from month to month, and months spent in
the red). Whether the model finds those is the test. Nothing here is real data.
"""

import numpy as np
import pandas as pd

from credit_risk.features import from_history

ANNUAL_RATE = 0.07  # the rate the backend quotes (application.yml, tameio.lending.annual-rate)
TERMS = (12, 24, 36, 48, 60)

# The hidden rule behind the outcomes, on the measures in their own units
TRUE_RISK = {
    "intercept": -5.4,
    "repayment_ratio": 1.5,
    "loan_size": 0.6,
    "history_months": -0.035,
    "cash_cushion": -0.07,
    "inflow_volatility": 2.6,
    "negative_months": 2.4,
}


def months_of(base_inflow: float, volatility: float, margin: float, rng: np.random.Generator) -> pd.DataFrame:
    """Twelve months of money in and out: inflow around its base, outflow around a share of the base."""
    inflow = base_inflow * np.clip(1 + volatility * rng.standard_normal(12), 0.05, None)
    outflow = base_inflow * margin * np.clip(1 + 0.08 * rng.standard_normal(12), 0.3, None)
    return pd.DataFrame({"inflow": inflow.round(2), "outflow": outflow.round(2)})


def loan_book(n: int = 6000, seed: int = 11) -> pd.DataFrame:
    """One row per past application: its measures and `defaulted` (1 if the loan was not repaid)."""
    rng = np.random.default_rng(seed)
    rows = []
    for _ in range(n):
        base = float(rng.lognormal(9.3, 0.6))
        months = months_of(
            base_inflow=base,
            volatility=float(0.04 + 0.7 * rng.beta(2, 5)),
            margin=float(np.clip(rng.normal(0.82, 0.1), 0.45, 1.15)),
            rng=rng,
        )
        amount = round(base * 12 * float(rng.uniform(0.05, 1.3)), -2)
        term = int(rng.choice(TERMS))
        rate = ANNUAL_RATE / 12
        instalment = amount * rate / (1 - (1 + rate) ** -term)
        features = from_history(
            amount=amount,
            instalment=instalment,
            balance=base * float(rng.uniform(0.05, 4.0)),
            history_months=int(rng.integers(1, 61)),
            months=months,
        )
        rows.append(features.as_dict())
    book = pd.DataFrame(rows)
    logit = TRUE_RISK["intercept"] + sum(book[name] * weight for name, weight in TRUE_RISK.items() if name in book)
    book["defaulted"] = (rng.random(n) < 1 / (1 + np.exp(-logit))).astype(int)
    return book
