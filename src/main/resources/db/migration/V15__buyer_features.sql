-- V15__buyer_features.sql
-- Buyer-side data that the web app previously faked on the client:
-- follows, saved products, cart, order request details, buyer plans, profile fields,
-- product price tiers and exchange rates.

-- 1. Buyers following manufacturers (drives the "Following" feed tab)
CREATE TABLE IF NOT EXISTS manufacturer_follows (
    id              VARCHAR(64) PRIMARY KEY,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers (id) ON DELETE CASCADE,
    user_id         VARCHAR(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (manufacturer_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_manufacturer_follows_user_id ON manufacturer_follows (user_id);

-- Follower counts were seeded as static numbers; from now on they track real follows
UPDATE manufacturers m
SET follower_count = (SELECT COUNT(*) FROM manufacturer_follows f WHERE f.manufacturer_id = m.id);

-- 2. Saved products (saved seeks already live in reel_saves)
CREATE TABLE IF NOT EXISTS product_saves (
    id         VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    user_id    VARCHAR(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_product_saves_user_id ON product_saves (user_id);

-- 3. Buyer profile fields shown on the profile page
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tax_id     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS address    TEXT,
    ADD COLUMN IF NOT EXISTS buyer_plan VARCHAR(32) NOT NULL DEFAULT 'FREE';

-- 4. Optional bulk price tiers: [{"minQty": 10, "priceInr": 1200.00}, ...]
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS price_tiers JSONB NOT NULL DEFAULT '[]';

-- 5. Cart
CREATE TABLE IF NOT EXISTS cart_items (
    id         VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, product_id)
);

-- 6. Order requests (see V10__order_requests) gain the buyer-side details: where the request
--    came from (a product page, the cart or an accepted RFQ quote), the delivery contact and,
--    for quotes, the agreed total in the quote's currency. Still no payment is taken.
ALTER TABLE order_requests
    ADD COLUMN IF NOT EXISTS source           VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN IF NOT EXISTS rfq_quote_id     VARCHAR(64) REFERENCES rfq_quotes (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS currency         VARCHAR(3)  NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS quoted_total     NUMERIC(16, 2),
    ADD COLUMN IF NOT EXISTS contact_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_phone    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS delivery_address TEXT,
    ADD COLUMN IF NOT EXISTS cancel_reason    TEXT;

ALTER TABLE order_requests
    ADD CONSTRAINT chk_order_requests_source CHECK (source IN ('DIRECT', 'CART', 'RFQ_QUOTE'));

-- An accepted quote turns into exactly one order request
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_requests_rfq_quote
    ON order_requests (rfq_quote_id) WHERE rfq_quote_id IS NOT NULL;

-- 7. Buyer membership plans (manufacturer plans live in subscription_plans)
CREATE TABLE IF NOT EXISTS buyer_plans (
    code          VARCHAR(32) PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    price_inr     NUMERIC(10, 2) NOT NULL,
    price_cny     NUMERIC(10, 2) NOT NULL,
    features_json JSONB NOT NULL DEFAULT '[]',
    sort_order    INT NOT NULL DEFAULT 0
);

INSERT INTO buyer_plans (code, name, price_inr, price_cny, features_json, sort_order) VALUES
('FREE', 'Free', 0.00, 0.00, '["Browse seeks and products", "Post RFQs", "Chat with manufacturers"]', 0),
('PRO', 'Pro', 1.00, 10.00, '["Unlimited RFQs and quotes", "Full verified factory profiles", "Priority support"]', 1),
('ENTERPRISE', 'Enterprise', 10.00, 50.00, '["Everything in Pro", "Dedicated sourcing manager", "Factory audit reports"]', 2)
ON CONFLICT (code) DO NOTHING;

-- 8. Currency conversion used for display (1 INR = rate units). Admin-editable later.
INSERT INTO platform_settings (key, value)
VALUES ('exchange_rates', '{"base": "INR", "rates": {"INR": 1.0, "USD": 0.01149, "EUR": 0.01053, "GBP": 0.00893, "CNY": 0.0833, "JPY": 1.724, "AED": 0.0422}}')
ON CONFLICT (key) DO NOTHING;
