-- V3__admin_and_pricing.sql
-- 1. Add TOTP fields to users table for Admin 2FA
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN is_totp_enabled BOOLEAN DEFAULT FALSE NOT NULL;

-- 2. Create subscription_plans table for Factory Pricing
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    price_usd DECIMAL(10, 2) NOT NULL,
    features_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Link manufacturers to subscription plans
ALTER TABLE manufacturers ADD COLUMN subscription_id UUID;
ALTER TABLE manufacturers ADD CONSTRAINT fk_manufacturer_subscription FOREIGN KEY (subscription_id) REFERENCES subscription_plans (id) ON DELETE SET NULL;

-- Insert default subscription plans
INSERT INTO subscription_plans (name, price_usd, features_json) VALUES
('Free Tier', 0.00, '{"rfq_limit": 5, "featured": false}'),
('Pro Tier', 99.00, '{"rfq_limit": 50, "featured": true}'),
('Enterprise Tier', 299.00, '{"rfq_limit": -1, "featured": true, "dedicated_support": true}');
