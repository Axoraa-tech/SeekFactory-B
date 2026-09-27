-- V10__order_requests.sql
-- Buyer order requests (no payment: SeekFactory connects buyer and seller, the seller
-- tracks the deal status), plus the incoterm a seller quotes on an RFQ.
-- Additive only.

CREATE TABLE order_requests (
    id VARCHAR(64) PRIMARY KEY,
    reference_number VARCHAR(32) NOT NULL UNIQUE,
    -- product may be removed later; the snapshot columns keep the order readable
    product_id VARCHAR(64) REFERENCES products(id) ON DELETE SET NULL,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    buyer_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_name VARCHAR(255) NOT NULL,
    product_slug VARCHAR(255),
    product_image_url TEXT,
    unit_price_inr NUMERIC(14, 2),
    unit VARCHAR(32),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    buyer_note TEXT,
    seller_note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_requests_status CHECK (
        status IN ('PENDING', 'CONTACTED', 'NEGOTIATING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX idx_order_requests_mfr_created ON order_requests (manufacturer_id, created_at DESC);
CREATE INDEX idx_order_requests_buyer_created ON order_requests (buyer_id, created_at DESC);

-- Seller quotations can now carry trade terms (FOB, CIF, EXW, ...)
ALTER TABLE rfq_quotes ADD COLUMN incoterm VARCHAR(32);
