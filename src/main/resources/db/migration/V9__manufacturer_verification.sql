-- Manufacturer admin review.
--
-- Until now every supplier was created with verified = true, so no approval
-- queue existed. This adds the review lifecycle around that flag.
--
-- Existing manufacturers are grandfathered as APPROVED: they were already live
-- to buyers, and retro-hiding them would pull working factories off the platform.
-- Only accounts created from here on start as PENDING.

ALTER TABLE manufacturers
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS reviewed_by         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reviewed_at         TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rejection_reason    TEXT,
    ADD COLUMN IF NOT EXISTS submitted_at        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS company_reg_number  VARCHAR(120),
    ADD COLUMN IF NOT EXISTS tax_id              VARCHAR(120),
    ADD COLUMN IF NOT EXISTS registration_date   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS factory_address     TEXT;

-- Grandfather every row that exists today
UPDATE manufacturers
SET verification_status = CASE WHEN verified THEN 'APPROVED' ELSE 'PENDING' END
WHERE verification_status IS NULL;

ALTER TABLE manufacturers
    ALTER COLUMN verification_status SET NOT NULL,
    ALTER COLUMN verification_status SET DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_manufacturers_verification_status
    ON manufacturers (verification_status);

-- Certifications the factory claims at submission (ISO 9001, CE, RoHS, …).
-- Documents are not stored: the platform has no file-upload pipeline yet.
CREATE TABLE IF NOT EXISTS manufacturer_certifications (
    manufacturer_id VARCHAR(64)  NOT NULL REFERENCES manufacturers (id) ON DELETE CASCADE,
    certification   VARCHAR(120) NOT NULL,
    PRIMARY KEY (manufacturer_id, certification)
);
