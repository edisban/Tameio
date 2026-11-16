-- Businesses (tenants) and who may act for them. Users themselves live in Keycloak;
-- here they are referenced by their stable subject id.

CREATE TABLE business (
    id          UUID PRIMARY KEY,
    afm         VARCHAR(9)   NOT NULL,
    name        VARCHAR(200) NOT NULL,
    version     BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(64)  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_business_afm UNIQUE (afm),
    CONSTRAINT ck_business_afm_digits CHECK (afm ~ '^[0-9]{9}$')
);

CREATE TABLE membership (
    id           UUID PRIMARY KEY,
    business_id  UUID        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id      VARCHAR(64) NOT NULL,
    role         VARCHAR(20) NOT NULL,
    version      BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   VARCHAR(64) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_membership_business_user UNIQUE (business_id, user_id),
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'ACCOUNTANT'))
);

-- "Which businesses can this user act for?" is asked on almost every request
CREATE INDEX ix_membership_user ON membership (user_id);
