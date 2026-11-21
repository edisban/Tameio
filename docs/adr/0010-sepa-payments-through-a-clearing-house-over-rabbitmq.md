# 0010. SEPA payments through a clearing house, over RabbitMQ only

Status: accepted · 2026-11-21

## Context

Businesses must pay suppliers at other banks and receive money from them. Between banks, euro credit
transfers go through a clearing and settlement mechanism (for SEPA Credit Transfers, EBA STEP2 or a
national one such as DIAS in Greece). A payment is therefore not final when it leaves the payer's
account: it is submitted, cleared, then settled or returned with a reason code. Since October 2025
EU payment service providers must also offer Verification of Payee (VoP): before a payment, the payer
learns whether the name they typed matches the name on the payee's account.

There is no real clearing house to connect to, and the real one would be a separate system run by
someone else.

## Decision

- **A `clearing` module simulates the clearing house** and talks to the bank only through RabbitMQ,
  with JSON messages whose types each side declares for itself. No Java type is shared, so Spring
  Modulith sees no dependency either way, and the simulator can move to its own service unchanged.
- **Outgoing payment (pacs.008 in ISO 20022 terms).** The bank debits the payer and credits its
  internal SEPA_OUT account in one ledger entry, records a `payment` as PENDING, and publishes
  `SepaCreditTransferSubmitted` through the outbox (exchange `tameio.clearing`). The simulator answers
  a little later with a settlement report (pacs.002) on `tameio.banking`:
  - settled: SEPA_OUT → SETTLEMENT (the money leaves through the central bank), status SETTLED;
  - rejected with an ISO 20022 reason code (AC01 wrong account, AC04 closed account): the money goes
    back from SEPA_OUT to the payer, status RETURNED, and the statement shows the return.
  Reports are idempotent: a payment that is already final ignores a repeated report.
- **Incoming payment.** The simulator can deliver a credit transfer from another bank; the bank
  credits the account, or returns it (AC01 unknown account, AC04 closed or frozen).
- **Verification of Payee.** For Tameio IBANs the bank compares the name itself; for other banks it
  asks the clearing house with a request-reply message (RabbitMQ direct reply-to, 2 s timeout).
  Names are compared after removing accents, punctuation and legal forms (Α.Ε., Ε.Π.Ε., Ι.Κ.Ε. …):
  equal is MATCH, a small edit distance is CLOSE_MATCH (the actual name is shown), anything else is
  NO_MATCH, and no answer is NOT_POSSIBLE. The payment request repeats the check on the server; after
  NO_MATCH the payer must explicitly confirm, as the regulation allows.

## Consequences

- Payments have a real life cycle in the app and in the ledger, including returns.
- The simulator's deterministic rules (account numbers ending 0000 are closed, 9999 do not exist)
  let tests and demos produce every outcome.
- Messages are versioned only by their field names for now; a schema registry would come with Kafka.
