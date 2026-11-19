-- People who sign in to Tameio. Passwords are stored only as one-way hashes.
-- membership.user_id holds a user_account id as text, as it did for the earlier identity provider.

CREATE TABLE user_account (
    id               UUID PRIMARY KEY,
    -- Stored in lower case, so "Emma@Example.com" and "emma@example.com" are the same account
    email            VARCHAR(254) NOT NULL,
    -- "{argon2}$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>", about 115 characters
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(200) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    failed_sign_ins  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    version          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(64)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT ck_user_account_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_user_account_role CHECK (role IN ('BUSINESS', 'ANALYST', 'MANAGER', 'ADMIN'))
);
