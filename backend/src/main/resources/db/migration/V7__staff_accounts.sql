-- Bank staff: disabling accounts, ending sessions, temporary passwords and approval limits.

ALTER TABLE user_account
    ADD COLUMN active               BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Every session token carries the version it was issued with; raising it ends them all
    ADD COLUMN session_version      BIGINT         NOT NULL DEFAULT 0,
    ADD COLUMN must_change_password BOOLEAN        NOT NULL DEFAULT FALSE,
    ADD COLUMN approval_limit       NUMERIC(14, 2),
    ADD CONSTRAINT ck_user_account_approval_limit CHECK (approval_limit IS NULL OR approval_limit >= 0),
    -- Only bank staff can approve anything
    ADD CONSTRAINT ck_user_account_limit_for_staff CHECK (approval_limit IS NULL OR role IN ('MANAGER', 'ADMIN'));

-- The staff list
CREATE INDEX ix_user_account_staff ON user_account (role, full_name) WHERE role <> 'BUSINESS';
