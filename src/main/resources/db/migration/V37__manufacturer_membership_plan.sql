-- Factory membership plan (BASIC, PREMIUM, VIP, STRATEGIC), set when an admin approves a plan payment.
ALTER TABLE manufacturers ADD COLUMN IF NOT EXISTS membership_plan VARCHAR(32) NOT NULL DEFAULT 'BASIC';
ALTER TABLE manufacturers DROP CONSTRAINT IF EXISTS chk_manufacturers_membership_plan;
ALTER TABLE manufacturers ADD CONSTRAINT chk_manufacturers_membership_plan
    CHECK (membership_plan IN ('BASIC', 'PREMIUM', 'VIP', 'STRATEGIC'));

-- Which factory plan a payment is for (buyer payments keep using buyer_plan)
ALTER TABLE plan_payments ADD COLUMN IF NOT EXISTS manufacturer_plan VARCHAR(32);
