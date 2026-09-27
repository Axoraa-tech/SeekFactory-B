-- Seller catalogue editing and richer factory profiles.
--
-- `listed` is the seller's pause switch: an unlisted product or seek stays in the
-- seller hub but is hidden from buyers. It is separate from `is_active`, which
-- remains the soft-delete flag.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS listed         BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS datasheet_url  TEXT,
    ADD COLUMN IF NOT EXISTS datasheet_name VARCHAR(255);

ALTER TABLE reels
    ADD COLUMN IF NOT EXISTS listed BOOLEAN NOT NULL DEFAULT TRUE;

-- Product gallery. products.image_url stays the cover (first image) so every
-- existing reader keeps working.
CREATE TABLE IF NOT EXISTS product_images (
    product_id VARCHAR(64) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position   INTEGER     NOT NULL,
    image_url  TEXT        NOT NULL,
    PRIMARY KEY (product_id, position)
);

INSERT INTO product_images (product_id, position, image_url)
SELECT id, 0, image_url FROM products
WHERE image_url IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE manufacturers
    ADD COLUMN IF NOT EXISTS website_url      TEXT,
    ADD COLUMN IF NOT EXISTS annual_turnover  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS production_lines INTEGER;
