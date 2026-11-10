-- Optional upper bound for a product's price. price_inr stays the single price / range
-- minimum, so every existing row and API consumer keeps working; NULL means "single price".
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS price_max_inr NUMERIC(12, 2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_price_max_inr') THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_price_max_inr
            CHECK (price_max_inr IS NULL OR (price_max_inr >= 0 AND price_max_inr >= price_inr));
    END IF;
END $$;
