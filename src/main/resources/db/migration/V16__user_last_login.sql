-- When the user last signed in. A null value means the account has never signed in, which
-- the frontend uses to send a supplier's first session to the product catalog.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;

-- Existing accounts have been used already (signing up also signs in): only accounts created
-- from now on get the first-login experience.
UPDATE users
SET last_login_at = created_at
WHERE last_login_at IS NULL;
