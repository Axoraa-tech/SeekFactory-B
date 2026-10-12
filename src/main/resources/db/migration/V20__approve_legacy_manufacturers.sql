-- V20__approve_legacy_manufacturers.sql
-- Suppliers migrated from the legacy site (V19) were all publicly listed there. The storefront only
-- shows products from verified manufacturers, so approve them to keep their 631 products visible.
UPDATE manufacturers
SET verified            = TRUE,
    verification_status = 'APPROVED',
    reviewed_at         = COALESCE(reviewed_at, NOW()),
    updated_at          = NOW()
WHERE id LIKE 'lgm-%'
  AND (verified = FALSE OR verification_status <> 'APPROVED');
