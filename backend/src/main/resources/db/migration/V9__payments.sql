-- Payments: transfers within the bank and SEPA credit transfers to and from other banks (ADR 0010).

CREATE TABLE payment (
    id                  UUID           PRIMARY KEY,
    business_id         UUID           NOT NULL REFERENCES business (id),
    account_id          UUID           NOT NULL REFERENCES bank_account (id),
    direction           VARCHAR(10)    NOT NULL,
    scheme              VARCHAR(10)    NOT NULL,
    -- The ISO 20022 end-to-end id that follows the payment through clearing
    end_to_end_id       VARCHAR(35)    NOT NULL,
    amount              NUMERIC(18, 2) NOT NULL,
    counterparty_iban   VARCHAR(34)    NOT NULL,
    counterparty_name   VARCHAR(140)   NOT NULL,
    reference           VARCHAR(140),
    status              VARCHAR(10)    NOT NULL,
    -- ISO 20022 reason code of a rejection or return, such as AC04 (closed account)
    reason_code         VARCHAR(4),
    reason              VARCHAR(200),
    -- Verification of Payee: what the check said about the name the payer typed
    payee_check         VARCHAR(20),
    booking_entry_id    UUID           REFERENCES journal_entry (id),
    final_entry_id      UUID           REFERENCES journal_entry (id),
    version             BIGINT         NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          VARCHAR(64)    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_payment_end_to_end UNIQUE (direction, end_to_end_id),
    CONSTRAINT uq_payment_booking_entry UNIQUE (booking_entry_id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_direction CHECK (direction IN ('OUTGOING', 'INCOMING')),
    CONSTRAINT ck_payment_scheme CHECK (scheme IN ('INTERNAL', 'SEPA')),
    CONSTRAINT ck_payment_status CHECK (status IN ('COMPLETED', 'PENDING', 'SETTLED', 'RETURNED')),
    CONSTRAINT ck_payment_payee_check CHECK (payee_check IN ('MATCH', 'CLOSE_MATCH', 'NO_MATCH', 'NOT_POSSIBLE')),
    -- A returned payment always says why
    CONSTRAINT ck_payment_returned_reason CHECK (status <> 'RETURNED' OR reason_code IS NOT NULL)
);

-- An account's payments, newest first
CREATE INDEX ix_payment_account_created ON payment (account_id, created_at DESC);
-- Payments still in clearing
CREATE INDEX ix_payment_pending ON payment (created_at) WHERE status = 'PENDING';
