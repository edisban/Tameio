-- The repayment schedule of a disbursed loan, one row a month. A daily job marks each instalment as due
-- once its date has come.
CREATE TABLE loan_instalment (
    id                  UUID          PRIMARY KEY,
    loan_application_id UUID          NOT NULL REFERENCES loan_application (id),
    number              INT           NOT NULL,
    due_on              DATE          NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    became_due_at       TIMESTAMPTZ,
    version             BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    created_by          VARCHAR(64)   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_loan_instalment_number UNIQUE (loan_application_id, number),
    CONSTRAINT ck_loan_instalment_number CHECK (number > 0),
    CONSTRAINT ck_loan_instalment_amount CHECK (amount > 0),
    CONSTRAINT ck_loan_instalment_status CHECK (status IN ('SCHEDULED', 'DUE')),
    CONSTRAINT ck_loan_instalment_due CHECK (status <> 'DUE' OR became_due_at IS NOT NULL)
);

-- What the daily job looks for: scheduled instalments by date
CREATE INDEX ix_loan_instalment_scheduled ON loan_instalment (due_on) WHERE status = 'SCHEDULED';
