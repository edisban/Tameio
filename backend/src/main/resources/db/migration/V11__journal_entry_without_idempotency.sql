-- No request books money with an idempotency key, so the columns stay empty: they go.
ALTER TABLE journal_entry
    DROP CONSTRAINT uq_journal_entry_idempotency,
    DROP COLUMN idempotency_key,
    DROP COLUMN request_hash;
