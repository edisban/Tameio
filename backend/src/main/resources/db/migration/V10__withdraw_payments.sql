-- Payments (transfers, SEPA, Verification of Payee) were withdrawn for now (ADR 0010); their record
-- goes too. The money they moved stays in the ledger: journal entries and postings are append-only,
-- and every balance is still the sum of its postings. The code is kept at the git tag with-payments.

DROP TABLE payment;
