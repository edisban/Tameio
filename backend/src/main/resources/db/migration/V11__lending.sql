-- Loan applications and their review: who recommended, who decided, and the risk score shown to them.

CREATE TABLE loan_application (
    id                       UUID           PRIMARY KEY,
    business_id              UUID           NOT NULL REFERENCES business (id),
    business_name            VARCHAR(200)   NOT NULL,
    -- The account the loan is paid into; by id, the banking module owns accounts
    account_id               UUID           NOT NULL,
    amount                   NUMERIC(14, 2) NOT NULL,
    term_months              INT            NOT NULL,
    purpose                  VARCHAR(30)    NOT NULL,
    description              VARCHAR(500),
    annual_rate              NUMERIC(6, 4)  NOT NULL,
    monthly_instalment       NUMERIC(14, 2) NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    -- The business's figures when it applied, so the case shows what was assessed
    average_monthly_inflow   NUMERIC(14, 2) NOT NULL,
    average_monthly_outflow  NUMERIC(14, 2) NOT NULL,
    average_monthly_expenses NUMERIC(14, 2) NOT NULL,
    balance                  NUMERIC(14, 2) NOT NULL,
    months_of_history        INT            NOT NULL,
    risk_score               INT            NOT NULL,
    risk_band                VARCHAR(10)    NOT NULL,
    submitted_at             TIMESTAMPTZ,
    reviewer_id              VARCHAR(64),
    reviewer_name            VARCHAR(200),
    review_started_at        TIMESTAMPTZ,
    recommendation           VARCHAR(10),
    recommendation_note      VARCHAR(1000),
    recommended_by           VARCHAR(64),
    recommended_by_name      VARCHAR(200),
    recommended_at           TIMESTAMPTZ,
    decision_note            VARCHAR(1000),
    decided_by               VARCHAR(64),
    decided_by_name          VARCHAR(200),
    decided_at               TIMESTAMPTZ,
    disbursement_entry_id    UUID,
    disbursed_at             TIMESTAMPTZ,
    version                  BIGINT         NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL,
    created_by               VARCHAR(64)    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_loan_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'RECOMMENDED', 'APPROVED', 'DECLINED', 'DISBURSED')),
    CONSTRAINT ck_loan_amount CHECK (amount > 0),
    CONSTRAINT ck_loan_term CHECK (term_months > 0),
    CONSTRAINT ck_loan_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_loan_recommendation CHECK (recommendation IN ('APPROVE', 'DECLINE')),
    -- Maker-checker, enforced by the database as well as the code: one person cannot do both
    CONSTRAINT ck_loan_four_eyes CHECK (decided_by IS NULL OR decided_by <> recommended_by),
    CONSTRAINT ck_loan_disbursed CHECK (status <> 'DISBURSED' OR disbursement_entry_id IS NOT NULL)
);

-- A business's applications, and the back office's queue by status
CREATE INDEX ix_loan_application_business ON loan_application (business_id, created_at DESC);
CREATE INDEX ix_loan_application_status ON loan_application (status, created_at);

-- The factors behind each risk score, in the order the scorecard adds them
CREATE TABLE loan_risk_factor (
    loan_application_id UUID          NOT NULL REFERENCES loan_application (id) ON DELETE CASCADE,
    position            INT           NOT NULL,
    code                VARCHAR(30)   NOT NULL,
    measured            NUMERIC(14, 4),
    points              INT           NOT NULL,
    max_points          INT           NOT NULL,
    PRIMARY KEY (loan_application_id, position),
    CONSTRAINT ck_loan_risk_factor_points CHECK (points BETWEEN 0 AND max_points)
);
