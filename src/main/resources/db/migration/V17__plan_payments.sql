-- V17__plan_payments.sql
-- Payment requests for paid plans. A buyer (or, later, a manufacturer) pays outside the app,
-- uploads the payment screenshot or invoice, and an admin approves it, which activates the plan.

CREATE TABLE plan_payments (
    id                   VARCHAR(64) PRIMARY KEY,
    user_id              VARCHAR(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_type         VARCHAR(16) NOT NULL CHECK (account_type IN ('BUYER', 'MANUFACTURER')),

    -- What is being bought. Buyers use buyer_plan; manufacturers use subscription_plan_id.
    buyer_plan           VARCHAR(32),
    manufacturer_id      VARCHAR(64) REFERENCES manufacturers (id) ON DELETE SET NULL,
    -- No foreign key: manufacturer payments are not built yet and the id type differs between environments
    subscription_plan_id VARCHAR(64),
    plan_name            VARCHAR(100) NOT NULL,

    -- Price snapshot at submission, so later price edits never change an open request
    region               VARCHAR(16)   NOT NULL,
    currency             VARCHAR(8)    NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    payer_reference      VARCHAR(120),

    -- Payer details as they were when the payment was submitted
    payer_name           VARCHAR(255),
    payer_email          VARCHAR(255),
    payer_phone          VARCHAR(50),
    payer_company        VARCHAR(255),
    payer_country        VARCHAR(100),
    payer_address        TEXT,

    -- The proof lives in the database so it is private and survives redeploys
    proof_data           BYTEA        NOT NULL,
    proof_content_type   VARCHAR(100) NOT NULL,
    proof_filename       VARCHAR(255),
    proof_size           INT          NOT NULL,

    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by          VARCHAR(64),
    reviewed_at          TIMESTAMP WITH TIME ZONE,
    rejection_reason     TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_plan_payments_status_created ON plan_payments (status, created_at DESC);
CREATE INDEX idx_plan_payments_user ON plan_payments (user_id);

-- One open request per account: resubmit only after the previous one is decided
CREATE UNIQUE INDEX uq_plan_payments_one_pending ON plan_payments (user_id) WHERE status = 'PENDING';
