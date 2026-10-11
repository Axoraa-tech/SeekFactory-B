-- Seller-set stock indicator shown to buyers on product cards and detail pages.
ALTER TABLE products
    ADD COLUMN stock_status VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK';

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_status
        CHECK (stock_status IN ('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK'));
