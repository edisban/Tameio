# 0011. Loan review: an explicit state machine, maker-checker, and a points scorecard

Status: accepted · 2026-11-21 · builds on [0005](0005-human-decides-every-credit-decision.md)

## Context

A business loan goes through several hands before money moves: the business applies, an analyst
assesses, a manager decides, the bank pays out. Each step may happen only after the one before it, only
by the right person, and a bank needs four eyes on every decision. The people deciding need a risk
indication they can check and explain to the customer.

## Decision

- **State machine.** `LoanStatus` lists the allowed moves (DRAFT → SUBMITTED → IN_REVIEW → RECOMMENDED
  → APPROVED or DECLINED; APPROVED → DISBURSED) in one place. Every change of status goes through one
  method that refuses any other move with 409 Conflict. No status is ever set directly.
- **Maker-checker.** An analyst recommends, with reasons. A manager or administrator decides, with
  reasons, only if their approval limit covers the amount, and never the person who recommended. The
  rule is checked in the code (403 Forbidden) and again by a database constraint
  (`decided_by <> recommended_by`), so no code path can bypass it.
- **Concurrent decisions.** The application carries a `@Version`; two people acting at once on the same
  case get one success and one 409, never two decisions.
- **Disbursement through the ledger.** Paying out books LOANS → customer account through the existing
  `Ledger`, in the same transaction as the move to DISBURSED: the money and the status commit together.
- **A points scorecard, not a model.** Four measures (instalment against the monthly surplus, months of
  history, instalments covered by the balance, the loan against a year's inflow), each worth up to 25
  points by fixed tiers, add up to 0–100 and a band. Every factor is stored with what was measured, so
  the case shows why the score is what it is. A machine-learned model is a later idea; a scorecard is
  what many banks still start from, and a person can redo it on paper.
- **What the business sees.** Its application's status, the quoted instalment, the figures it was
  assessed on and the decision's reasons; not the score, which is the bank's working tool.

## Consequences

- Illegal steps and four-eyes violations are impossible, not just discouraged.
- The scorecard's tiers are judgement, not learned from data; they are easy to explain and to change.
- Application figures are a snapshot taken at submission, so the case shows what was actually assessed
  even if the account changes later.
