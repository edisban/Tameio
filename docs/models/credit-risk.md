# Model card: credit risk estimate

The chance that a business loan is not repaid, worked out from what the bank knows when the business applies,
and shown to the analyst beside the scorecard.

| | |
| --- | --- |
| Task | An application's account history in, the probability of default and each measure's part in it out |
| In production | **Beside the scorecard, not instead of it.** The scorecard stays the bank's score; the estimate is a second view on the case page, and a person decides ([ADR 0005](../adr/0005-human-decides-every-credit-decision.md), [ADR 0011](../adr/0011-credit-model-as-exported-coefficients.md)) |
| Model | Logistic regression (scikit-learn) on six standardised measures, version `1` |
| Code | `ml/credit-risk/` (training), `backend/.../lending/CreditModel.java` and `CreditFeatures.java` (scoring) |
| Results | [`ml/credit-risk/results/metrics.json`](../../ml/credit-risk/results/metrics.json) and the [figures](../../ml/credit-risk/results/figures/) |

## Data

Tameio is a fictional bank, so it has no loans that went bad. The training data is a **simulated loan book**
(`simulate.py`): 6,000 past applications, each with twelve months of money in and out generated with pandas
and numpy, a loan amount and term, and an outcome. The chance of default follows a rule written in the code:
it rises as a lender would expect with the scorecard's four measures, and also with two the scorecard does not
use. About 9% of the loans default, a rate a small-business lender would recognise.

Because the rule behind the outcomes is known, the question the model answers here is narrow and honest: can it
recover patterns the scorecard misses? It says nothing about real borrowers.

## Measures

| Measure | What it is | In the scorecard |
| --- | --- | --- |
| `repayment_ratio` | The monthly instalment as a share of the monthly surplus | yes |
| `loan_size` | The loan as a share of a year's inflow | yes |
| `history_months` | Months since the account's first movement | yes |
| `cash_cushion` | Instalments today's balance would cover | yes |
| `inflow_volatility` | Standard deviation of the monthly inflow over its mean | no |
| `negative_months` | Share of months in which more went out than came in | no |

Each measure is capped (a repayment ratio of 2 is as bad as 10), so an extreme value cannot dominate.
The Python package and the backend compute them the same way; a backend test reads examples the package wrote
(`credit-golden.json`) and must match every measure, contribution and probability to six decimals.

## Results

On 1,800 applications the model did not learn from:

| | Model | Scorecard |
| --- | --- | --- |
| AUC | **0.814** | 0.735 |
| KS | **0.492** | 0.379 |
| Brier score | 0.074 (predicting the average for all: 0.086) | – |

AUC is the chance that a loan that went bad is ranked above one that did not; KS (from scipy) is the widest gap
between how the two groups spread over the score, the usual separation figure in credit scoring. The scorecard
gives points, not probabilities, so only its ranking is compared.

Calibration, in fifths by predicted probability:

| Predicted | Actual |
| --- | --- |
| 0.2% | 0.8% |
| 1.3% | 1.9% |
| 4.6% | 5.0% |
| 11.1% | 12.2% |
| 29.5% | 27.2% |

The model is exported only if its AUC beats the scorecard's; CI runs that check on every change.

## Explanation

The case page shows, for each measure, how far it moved the estimate from an average applicant: the coefficient
times the standardised value, in log-odds. For a linear model this is exactly its SHAP value, so the explanation
is the model, not an approximation of it.

## Limits

- **Simulated data.** The coefficients reflect the simulation's rule, not real defaults; with real outcomes the
  same code would be retrained and the gate would decide again.
- **No fairness measurement yet.** The data has no groups such as region or sector, so unequal outcomes between
  them cannot be measured; the model uses no personal attributes. ADR 0005 asks for this check before the model
  could decide anything, which it does not.
- **Linear.** Interactions (volatility hurting more when the cushion is thin) are not captured; a gradient
  boosting model would have to beat this one to replace it.

## Reproducing

```bash
cd ml/credit-risk
pip install -e ".[dev,report]"
python -m credit_risk.train --report   # trains, checks the gate, draws the figures, logs the run to a local MLflow
python -m credit_risk.golden           # rewrites the examples the backend test checks against
mlflow ui --backend-store-uri sqlite:///mlflow.db   # the runs and registered versions, on http://localhost:5000
```
