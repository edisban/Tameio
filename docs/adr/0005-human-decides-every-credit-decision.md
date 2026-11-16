# 0005. A person decides every credit decision

Status: accepted · 2026-11-16

## Context

Creditworthiness assessment is a high-risk use of AI under the EU AI Act. A score that cannot be explained,
or that decides on its own, would be unacceptable to a bank, and a decision a customer cannot understand
is unfair to them.

## Decision

- The risk model recommends; it never approves or declines. Every decision is made and signed by a credit
  analyst, and above the analyst's approval limit also by a manager (maker-checker).
- Every score is shown with the factors that raised or lowered it (SHAP values).
- Each assessment records the model version, its inputs and its output, in the audit log.
- The credit model is checked before release for unequal outcomes between groups such as region and
  sector, and the results are published in its model card.
- The customer is told the main reasons for a decision.
- The assistants can explain and help, but cannot make or promise a decision.

## Consequences

- Decisions stay accountable and explainable, as regulators and banks require.
- Decisions take a person's time; the AI's job is to make that time short, not to remove it.
