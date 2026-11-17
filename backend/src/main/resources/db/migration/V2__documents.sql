-- Documents a business uploads. The file itself is in object storage under storage_key.

CREATE TABLE document (
    id           UUID PRIMARY KEY,
    -- No cascade: deleting a business must first deal with its stored files
    business_id  UUID         NOT NULL REFERENCES business (id),
    filename     VARCHAR(255) NOT NULL,
    media_type   VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    storage_key  VARCHAR(300) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_document_storage_key UNIQUE (storage_key),
    -- The same file cannot be uploaded twice to the same business
    CONSTRAINT uq_document_business_sha256 UNIQUE (business_id, sha256),
    CONSTRAINT ck_document_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED'))
);

-- The document list: one business, newest first
CREATE INDEX ix_document_business_created ON document (business_id, created_at DESC);
