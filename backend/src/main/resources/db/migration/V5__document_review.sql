-- The values a person confirms for a document, and where they differ from what document AI read.

ALTER TABLE document DROP CONSTRAINT ck_document_status;
ALTER TABLE document
    ADD CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED', 'READING', 'READ', 'FAILED', 'REVIEWED')),
    ADD COLUMN reviewed_at            TIMESTAMPTZ,
    ADD COLUMN reviewed_by            VARCHAR(64),
    ADD COLUMN confirmed_issuer_afm   VARCHAR(9),
    ADD COLUMN confirmed_issue_date   DATE,
    ADD COLUMN confirmed_net_amount   NUMERIC(14, 2),
    ADD COLUMN confirmed_vat_amount   NUMERIC(14, 2),
    ADD COLUMN confirmed_total_amount NUMERIC(14, 2),
    ADD COLUMN confirmed_category     VARCHAR(40),
    -- A reviewed document has every confirmed value, and they add up
    ADD CONSTRAINT ck_document_review_complete CHECK (
        status <> 'REVIEWED' OR (
            reviewed_at IS NOT NULL AND confirmed_issuer_afm IS NOT NULL AND confirmed_issue_date IS NOT NULL
            AND confirmed_category IS NOT NULL
            AND confirmed_net_amount + confirmed_vat_amount = confirmed_total_amount));

-- Training data: what document AI said and what the person corrected it to, with the model that said it
CREATE TABLE document_correction (
    document_id   UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    position      INT          NOT NULL,
    field         VARCHAR(30)  NOT NULL,
    suggested     VARCHAR(100),
    corrected     VARCHAR(100) NOT NULL,
    model_version VARCHAR(100),
    PRIMARY KEY (document_id, position)
);

-- Reviewed documents of a business by issue date: the books read them this way
CREATE INDEX ix_document_business_issue_date ON document (business_id, confirmed_issue_date) WHERE status = 'REVIEWED';
