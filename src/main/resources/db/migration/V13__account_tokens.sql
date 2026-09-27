-- Password reset and email verification.
--
-- Only a SHA-256 hash of each token is stored, so a database read does not
-- yield usable reset links. Tokens are single-use and expire.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS account_tokens (
    id         VARCHAR(64)              PRIMARY KEY,
    user_id    VARCHAR(64)              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(32)              NOT NULL,
    token_hash VARCHAR(64)              NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_tokens_purpose CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'))
);

CREATE INDEX IF NOT EXISTS idx_account_tokens_user_purpose ON account_tokens (user_id, purpose);
