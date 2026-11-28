# 0011. The credit model runs in the core, from exported coefficients

Status: accepted · 2026-11-28

## Context

The credit model is trained in Python (pandas, scikit-learn) and its estimate is needed where applications are
assessed, in the Spring Boot core. It could be served by a Python service the core calls, or carried into the
core as a file.

## Decision

- The model is a logistic regression, so it is fully described by its coefficients, the standardisation of each
  measure and the caps. Training exports these as JSON (`lending/credit-model.json`), and the core computes the
  probability itself.
- The measures are computed in Java the same way as in Python. Examples written by the Python package are checked
  by a backend test to six decimals, so the two cannot drift apart unnoticed.
- The estimate is stored with each application together with the model version and each measure's part, so a
  decision can be traced back to the exact model that informed it.
- The estimate sits beside the scorecard; the scorecard stays the bank's score, and a person decides (ADR 0005).

## Consequences

- No extra service, network call or failure mode on the assessment path; scoring takes microseconds.
- A model that is not a linear formula (gradient boosting, a neural network) could not be carried this way; it
  would need ONNX or a service, decided then.
- Retraining means a new JSON file in a release, reviewed like code, rather than a model swapped at runtime.
