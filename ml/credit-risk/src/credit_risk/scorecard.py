"""The backend's scorecard (lending/Scorecard.java), in Python: the baseline the model has to beat.

Four measures, up to 25 points each by fixed tiers, 0 to 100 in all; the higher, the lower the risk.
"""

from credit_risk.features import Features

# (limit, points): the first tier the measure falls within gives the points
REPAYMENT = ((0.20, 25), (0.35, 18), (0.50, 10))  # at most
SIZE = ((0.25, 25), (0.50, 15), (1.00, 8))  # at most
HISTORY = ((12, 25), (6, 15), (3, 8))  # at least
CUSHION = ((6, 25), (3, 15), (1, 8))  # at least


def _at_most(value: float, tiers: tuple[tuple[float, int], ...]) -> int:
    return next((points for limit, points in tiers if value <= limit), 0)


def _at_least(value: float, tiers: tuple[tuple[float, int], ...]) -> int:
    return next((points for limit, points in tiers if value >= limit), 0)


def score(features: Features) -> int:
    """The scorecard's points. No surplus or no inflow gives a capped measure, which earns nothing, as in Java."""
    return (
        _at_most(features.repayment_ratio, REPAYMENT)
        + _at_most(features.loan_size, SIZE)
        + _at_least(features.history_months, HISTORY)
        + _at_least(features.cash_cushion, CUSHION)
    )
