-- Product engagement is separate from seek/reel engagement. No seeded counts.
CREATE TABLE product_likes (
    id         VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    user_id    VARCHAR(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_likes_product_user UNIQUE (product_id, user_id)
);
-- The unique index covers per-product counts; this index covers viewer lookups and user deletion.
CREATE INDEX idx_product_likes_user_id ON product_likes (user_id);
