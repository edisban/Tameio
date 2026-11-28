-- The credit model's estimate next to the scorecard: the chance of default, the model version that gave it, and
-- how much each measure moved it. Applications assessed before the model existed have none.
ALTER TABLE loan_application
    ADD COLUMN default_probability  NUMERIC(5, 4),
    ADD COLUMN credit_model_version VARCHAR(20),
    ADD CONSTRAINT ck_loan_default_probability CHECK (default_probability BETWEEN 0 AND 1);

CREATE TABLE loan_credit_factor (
    loan_application_id UUID           NOT NULL REFERENCES loan_application (id) ON DELETE CASCADE,
    position            INT            NOT NULL,
    measure             VARCHAR(30)    NOT NULL,
    measured            NUMERIC(14, 6) NOT NULL,
    contribution        NUMERIC(10, 4) NOT NULL,
    PRIMARY KEY (loan_application_id, position)
);
