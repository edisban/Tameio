-- The expense book: one entry per confirmed document, owned by the accounting module.

CREATE TABLE expense (
    id           UUID           PRIMARY KEY,
    business_id  UUID           NOT NULL REFERENCES business (id),
    -- No foreign key to document: modules share ids, never tables
    document_id  UUID           NOT NULL,
    supplier_afm VARCHAR(9)     NOT NULL,
    issue_date   DATE           NOT NULL,
    net_amount   NUMERIC(14, 2) NOT NULL,
    vat_amount   NUMERIC(14, 2) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    category     VARCHAR(40)    NOT NULL,
    version      BIGINT         NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    created_by   VARCHAR(64)    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    -- One entry per document: a second review updates it
    CONSTRAINT uq_expense_document UNIQUE (document_id),
    CONSTRAINT ck_expense_amounts CHECK (net_amount >= 0 AND vat_amount >= 0 AND net_amount + vat_amount = total_amount)
);

-- Every query is one business over a date range
CREATE INDEX ix_expense_business_issue_date ON expense (business_id, issue_date);
