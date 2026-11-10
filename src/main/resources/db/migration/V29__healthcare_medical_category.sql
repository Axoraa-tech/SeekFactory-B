-- Healthcare / Medical: a top-level category manufacturers and products can select.
-- Additive only: existing categories (incl. "Test, Lab, Medical equipment") and their
-- products/manufacturer links are untouched. Idempotent, like V6.
-- Icon 'medical' is already supported by the web and mobile icon sets.

INSERT INTO categories (id, name, slug, icon, parent_id, listing_count, created_at, updated_at) VALUES
  ('cat-healthcare-medical', 'Healthcare / Medical', 'cat-healthcare-medical', 'medical', NULL, 0, NOW(), NOW()),
  ('cat-healthcare-medical-devices', 'Medical Devices & Equipment', 'sub-healthcare-medical-devices-equipment', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-diagnostic-imaging', 'Diagnostic & Imaging Equipment', 'sub-healthcare-diagnostic-imaging-equipment', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-hospital-furniture', 'Hospital Furniture & Beds', 'sub-healthcare-hospital-furniture-beds', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-consumables', 'Medical Consumables & Disposables', 'sub-healthcare-medical-consumables-disposables', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-dental', 'Dental Equipment', 'sub-healthcare-dental-equipment', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-pharma-machinery', 'Pharmaceutical Machinery', 'sub-healthcare-pharmaceutical-machinery', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW()),
  ('cat-healthcare-medical-rehab-mobility', 'Rehabilitation & Mobility Aids', 'sub-healthcare-rehabilitation-mobility-aids', 'medical', 'cat-healthcare-medical', 0, NOW(), NOW())
ON CONFLICT DO NOTHING;
