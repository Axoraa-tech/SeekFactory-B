-- Seller-uploaded certificate documents shown on the factory profile
-- (title, issuer, number, dates, image). Stored as JSON: they are only ever
-- read and written as a whole list with the profile.

ALTER TABLE manufacturers
    ADD COLUMN IF NOT EXISTS certificates JSONB NOT NULL DEFAULT '[]'::jsonb;
