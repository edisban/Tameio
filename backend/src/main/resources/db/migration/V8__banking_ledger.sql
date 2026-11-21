-- Core banking: accounts and a double-entry ledger (ADR 0009).

-- The 16-digit account number inside each IBAN the bank issues
CREATE SEQUENCE bank_account_number_seq START WITH 1000001;

CREATE TABLE bank_account (
    id          UUID           PRIMARY KEY,
    kind        VARCHAR(20)    NOT NULL,
    -- A customer account belongs to a business; the bank's internal accounts have a code instead
    business_id UUID           REFERENCES business (id),
    code        VARCHAR(40),
    iban        VARCHAR(34),
    -- The holder's registered name when the account was opened, as printed on statements
    holder_name VARCHAR(200),
    name        VARCHAR(100)   NOT NULL,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    -- What the bank owes the holder; kept in step with postings in the same transaction
    balance     NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status      VARCHAR(20)    NOT NULL,
    status_reason VARCHAR(200),
    version     BIGINT         NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    created_by  VARCHAR(64)    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_bank_account_iban UNIQUE (iban),
    CONSTRAINT uq_bank_account_code UNIQUE (code),
    CONSTRAINT ck_bank_account_kind CHECK (kind IN ('CUSTOMER', 'INTERNAL')),
    CONSTRAINT ck_bank_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_bank_account_currency CHECK (currency = 'EUR'),
    -- A customer account has a holder and an IBAN, and is never overdrawn
    CONSTRAINT ck_bank_account_customer CHECK (
        kind <> 'CUSTOMER' OR (business_id IS NOT NULL AND iban IS NOT NULL AND holder_name IS NOT NULL AND code IS NULL AND balance >= 0)),
    CONSTRAINT ck_bank_account_internal CHECK (kind <> 'INTERNAL' OR (code IS NOT NULL AND business_id IS NULL))
);

CREATE INDEX ix_bank_account_business ON bank_account (business_id) WHERE business_id IS NOT NULL;

-- One movement of money. Append-only.
CREATE TABLE journal_entry (
    id              UUID         PRIMARY KEY,
    kind            VARCHAR(30)  NOT NULL,
    description     VARCHAR(200) NOT NULL,
    -- The payment reference the payer typed, shown to both sides
    reference       VARCHAR(140),
    initiated_by    VARCHAR(64)  NOT NULL,
    -- Retrying a request with the same key returns this entry instead of moving the money again
    idempotency_key VARCHAR(100),
    request_hash    VARCHAR(64),
    booked_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_journal_entry_idempotency UNIQUE (initiated_by, idempotency_key)
);

-- The legs of an entry: signed amounts that add up to zero. Append-only.
CREATE TABLE posting (
    id                UUID           PRIMARY KEY,
    journal_entry_id  UUID           NOT NULL REFERENCES journal_entry (id),
    position          INT            NOT NULL,
    account_id        UUID           NOT NULL REFERENCES bank_account (id),
    amount            NUMERIC(18, 2) NOT NULL,
    balance_after     NUMERIC(18, 2) NOT NULL,
    counterparty_name VARCHAR(140),
    counterparty_iban VARCHAR(34),
    booked_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_posting_position UNIQUE (journal_entry_id, position),
    CONSTRAINT ck_posting_amount CHECK (amount <> 0)
);

-- An account's statement, newest first
CREATE INDEX ix_posting_account_booked ON posting (account_id, booked_at DESC, id);

-- Double entry, enforced by the database: when the transaction commits, every entry it touched must
-- have at least two postings adding up to zero
CREATE FUNCTION journal_entry_must_balance() RETURNS trigger AS $$
DECLARE
    total NUMERIC;
    legs  INT;
BEGIN
    SELECT COALESCE(SUM(amount), 0), COUNT(*) INTO total, legs FROM posting WHERE journal_entry_id = NEW.journal_entry_id;
    IF legs < 2 OR total <> 0 THEN
        RAISE EXCEPTION 'Journal entry % does not balance: % legs, total %', NEW.journal_entry_id, legs, total
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER posting_entry_balances
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION journal_entry_must_balance();

-- The ledger is a record of what happened: rows are never changed or removed, only reversed
CREATE FUNCTION ledger_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: correct it with a reversing entry', TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER journal_entry_append_only BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER posting_append_only BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

-- The bank's own accounts
INSERT INTO bank_account (id, kind, code, name, status, version, created_at, created_by, updated_at) VALUES
    ('01900000-0000-7000-8000-000000000001', 'INTERNAL', 'SETTLEMENT', 'Settlement with the Bank of Greece (TARGET2)', 'ACTIVE', 0, now(), 'system', now()),
    ('01900000-0000-7000-8000-000000000002', 'INTERNAL', 'SEPA_OUT', 'SEPA outgoing clearing', 'ACTIVE', 0, now(), 'system', now()),
    ('01900000-0000-7000-8000-000000000003', 'INTERNAL', 'SEPA_IN', 'SEPA incoming clearing', 'ACTIVE', 0, now(), 'system', now()),
    ('01900000-0000-7000-8000-000000000004', 'INTERNAL', 'LOANS', 'Loans to customers', 'ACTIVE', 0, now(), 'system', now()),
    ('01900000-0000-7000-8000-000000000005', 'INTERNAL', 'INTEREST_INCOME', 'Interest income', 'ACTIVE', 0, now(), 'system', now()),
    ('01900000-0000-7000-8000-000000000006', 'INTERNAL', 'FEE_INCOME', 'Fee income', 'ACTIVE', 0, now(), 'system', now());
