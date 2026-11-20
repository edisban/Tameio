-- What document AI reads from each document, and the outbox that sends reading jobs to RabbitMQ.

ALTER TABLE document DROP CONSTRAINT ck_document_status;
ALTER TABLE document
    ADD CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED', 'READING', 'READ', 'FAILED')),
    ADD COLUMN read_attempts                 INT           NOT NULL DEFAULT 0,
    ADD COLUMN read_failure                  VARCHAR(20),
    ADD COLUMN read_failure_detail           VARCHAR(500),
    ADD COLUMN read_at                       TIMESTAMPTZ,
    ADD COLUMN issuer_afm                    VARCHAR(9),
    ADD COLUMN issuer_afm_confidence         DOUBLE PRECISION,
    ADD COLUMN issue_date                    DATE,
    ADD COLUMN issue_date_confidence         DOUBLE PRECISION,
    -- Exact decimals: money is never a float
    ADD COLUMN net_amount                    NUMERIC(14, 2),
    ADD COLUMN net_amount_confidence         DOUBLE PRECISION,
    ADD COLUMN vat_amount                    NUMERIC(14, 2),
    ADD COLUMN vat_amount_confidence         DOUBLE PRECISION,
    ADD COLUMN total_amount                  NUMERIC(14, 2),
    ADD COLUMN total_amount_confidence       DOUBLE PRECISION,
    ADD COLUMN suggested_category            VARCHAR(40),
    ADD COLUMN suggested_category_confidence DOUBLE PRECISION,
    ADD COLUMN model_version                 VARCHAR(100),
    ADD COLUMN page_count                    INT,
    ADD COLUMN ocr_method                    VARCHAR(20),
    ADD COLUMN ocr_text                      TEXT,
    ADD CONSTRAINT ck_document_read_failure CHECK (read_failure IN ('UNREADABLE', 'REJECTED', 'GAVE_UP')),
    -- A failed document always says why
    ADD CONSTRAINT ck_document_failed_has_reason CHECK (status <> 'FAILED' OR read_failure IS NOT NULL);

-- The checks document AI ran on the fields, in the order it ran them
CREATE TABLE document_check (
    document_id UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    position    INT          NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    passed      BOOLEAN      NOT NULL,
    detail      VARCHAR(300) NOT NULL,
    PRIMARY KEY (document_id, position)
);

-- Spring Modulith's event publication registry (the transactional outbox), as its JDBC module
-- expects it (schema v2). An event is stored with the transaction that published it and marked
-- complete once it has been sent to RabbitMQ; incomplete ones are sent again after a restart.
CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
