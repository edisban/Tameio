"""The six measures the model reads, from what the bank knows when a business applies.

Four are the scorecard's own (repayment capacity, loan size, track record, cash cushion). Two it does not have:
how much the money coming in swings from month to month, and how many of the last months the business spent
more than it took in. Both are worked out with pandas from the last twelve months of the account.

The backend computes the same measures in Java (CreditFeatures); a shared file of examples keeps the two equal.
"""

from dataclasses import asdict, dataclass

import pandas as pd

# A measure beyond its cap says nothing more: 2x the surplus is as bad as 10x, and the cap keeps an extreme
# value from dominating the model. The same caps are written into the exported model for the backend.
CAPS = {
    "repayment_ratio": 2.0,
    "loan_size": 3.0,
    "history_months": 36.0,
    "cash_cushion": 24.0,
    "inflow_volatility": 2.0,
    "negative_months": 1.0,
}
FEATURES = list(CAPS)


@dataclass(frozen=True)
class Features:
    repayment_ratio: float
    """The monthly instalment as a share of the monthly surplus (inflow minus outflow)."""
    loan_size: float
    """The loan as a share of a year's inflow."""
    history_months: float
    """Months since the account's first movement."""
    cash_cushion: float
    """How many instalments today's balance would cover."""
    inflow_volatility: float
    """Standard deviation of the monthly inflow over its mean (coefficient of variation)."""
    negative_months: float
    """Share of the months in which more went out than came in."""

    def as_dict(self) -> dict[str, float]:
        return asdict(self)


def from_history(
    amount: float, instalment: float, balance: float, history_months: int, months: pd.DataFrame
) -> Features:
    """`months` has one row per month (the last twelve at most) with columns inflow and outflow."""
    inflow = months["inflow"].astype(float)
    outflow = months["outflow"].astype(float)
    mean_in = float(inflow.mean()) if len(inflow) else 0.0
    surplus = mean_in - (float(outflow.mean()) if len(outflow) else 0.0)
    values = {
        # With no surplus there is nothing to repay from: the worst value
        "repayment_ratio": instalment / surplus if surplus > 0 else CAPS["repayment_ratio"],
        "loan_size": amount / (12 * mean_in) if mean_in > 0 else CAPS["loan_size"],
        "history_months": float(history_months),
        "cash_cushion": balance / instalment if instalment > 0 else CAPS["cash_cushion"],
        # Population standard deviation, as the backend computes it
        "inflow_volatility": float(inflow.std(ddof=0)) / mean_in if mean_in > 0 else CAPS["inflow_volatility"],
        "negative_months": float((outflow > inflow).mean()) if len(inflow) else CAPS["negative_months"],
    }
    return Features(**{name: round(min(max(value, 0.0), CAPS[name]), 6) for name, value in values.items()})


def annuity(amount: float, annual_rate: float, term_months: int) -> float:
    """The fixed monthly instalment of a loan (the formula the backend quotes with)."""
    rate = annual_rate / 12
    return amount * rate / (1 - (1 + rate) ** -term_months)
