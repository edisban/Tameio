# 0009. Core banking on a double-entry ledger

Status: accepted · 2026-11-21 · changes a non-goal of the [design](../design.md)

## Context

The design left core banking out: accounts and payments were to be mocked. Tameio is meant to show
how a bank's systems are built, and a business that keeps its books and borrows here should also hold
its money here: receive a loan into an account, pay suppliers, see every movement. That needs real
accounts and a ledger that never loses or invents a cent, even with many requests at once.

## Decision

A `banking` module with a double-entry ledger in PostgreSQL:

- **Accounts.** Each business can open current accounts with a Greek IBAN (`GR`, two check digits,
  bank code `999` of Tameio, the fictional bank this app now is, branch `0001`, a 16-digit account number from a
  sequence), validated with the ISO 13616 mod-97 check. The bank's own internal accounts (settlement
  with the central bank, SEPA clearing, loans, income) live in the same table.
- **Journal entries and postings.** Every movement of money is one journal entry with two or more
  postings whose signed amounts add up to zero. A deferred constraint trigger checks this at commit,
  so an unbalanced entry cannot be stored, whatever the application does. Entries and postings are
  append-only: triggers refuse updates and deletes, and a mistake is corrected by a reversing entry.
- **Balances.** Each account keeps its balance, updated in the same transaction as its postings, and
  each posting records the balance after it, so a statement needs no recalculation. A customer
  account cannot go below zero (checked by the ledger and by a database constraint).
- **Concurrency.** A transfer locks the accounts it touches with `SELECT … FOR UPDATE`, always in
  ascending id order, so two transfers between the same accounts in opposite directions wait for each
  other instead of deadlocking.
- **Idempotency.** Creating a transfer requires an `Idempotency-Key` header. The key and a hash of
  the request are stored with the entry (unique per user), so a retried request returns the original
  transfer instead of paying twice, and a reused key with a different request is refused.
- **One way to move money.** Only the `Ledger` class books entries; every feature (transfers, SEPA,
  loans, fees) goes through it.
- **Trial balance.** Because every entry sums to zero, the sum of all balances is always zero. The back
  office shows this, with the bank's internal accounts and total customer deposits.

## Consequences

- Money is conserved by construction and checked by the database, not only by code.
- Statements, the business overview and later the bank's analytics read postings directly.
- Locking serialises transfers on the same account, which is what a bank wants; throughput across
  different accounts is unaffected.
- Signed amounts are used instead of separate debit and credit columns: simpler queries, the same
  guarantees. A customer account's balance is what the bank owes that customer.
