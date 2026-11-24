-- Admin-chosen position of a category among its siblings (0 = first). Null = not placed yet:
-- such categories follow the placed ones, A-Z, with "Other" last (the website's default order).
ALTER TABLE categories ADD COLUMN IF NOT EXISTS display_order INTEGER;
