-- Manufacturer product allowance, a single factory/chairman profile video, and manual limit-upgrade requests.

-- New factories may list three products. Existing factories keep everything they already list:
-- anyone above three is grandfathered at their current live count so no product is hidden or removed.
ALTER TABLE manufacturers ADD COLUMN product_limit INTEGER NOT NULL DEFAULT 3;
UPDATE manufacturers m
SET product_limit = live.cnt
FROM (SELECT manufacturer_id, COUNT(*) AS cnt FROM products WHERE is_active = TRUE GROUP BY manufacturer_id) live
WHERE live.manufacturer_id = m.id AND live.cnt > 3;
ALTER TABLE manufacturers ADD CONSTRAINT chk_manufacturers_product_limit CHECK (product_limit >= 0);

ALTER TABLE manufacturers ADD COLUMN profile_video_url TEXT;

-- Handled manually by an admin; no payment is taken or recorded here.
CREATE TABLE product_limit_requests (
    id              VARCHAR(64) PRIMARY KEY,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers (id) ON DELETE CASCADE,
    current_count   INTEGER     NOT NULL,
    current_limit   INTEGER     NOT NULL,
    requested_limit INTEGER     NOT NULL CHECK (requested_limit > 0),
    message         TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_note      TEXT,
    reviewed_by     VARCHAR(64),
    reviewed_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_product_limit_requests_status_created ON product_limit_requests (status, created_at DESC);
CREATE INDEX idx_product_limit_requests_manufacturer ON product_limit_requests (manufacturer_id, created_at DESC);
-- At most one open request per factory.
CREATE UNIQUE INDEX uq_product_limit_requests_pending ON product_limit_requests (manufacturer_id) WHERE status = 'PENDING';
