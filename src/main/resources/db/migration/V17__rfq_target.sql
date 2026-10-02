-- An RFQ can be sent to one factory about one of its products, optionally from the seek (video)
-- the buyer was watching. Without a manufacturer the RFQ goes to every factory in its category.

ALTER TABLE rfqs
    ADD COLUMN IF NOT EXISTS manufacturer_id VARCHAR(64) REFERENCES manufacturers(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS product_id VARCHAR(64) REFERENCES products(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS source_reel_id VARCHAR(64) REFERENCES reels(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_rfqs_manufacturer_id ON rfqs(manufacturer_id);
