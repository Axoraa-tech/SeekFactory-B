-- PREPARED ONLY. Reserve an unused Flyway version before incorporation.
-- Add precisely the 32 Sheet1-only workbook children; no product reassignment.
-- Source workbook SHA256: fd455c84756163ce0de5b32ecf16a6ab920bbc4e9f45fed9d50d86ce0a6fbf6c
-- Listing counts in the workbook are NOT SeekFactory inventory counts.
-- One atomic DO statement; any conflict aborts every addition in this statement.
DO $workbook_categories$
DECLARE
  wanted RECORD;
  retained_parent RECORD;
  current_child RECORD;
BEGIN
  -- Serialize category writers for parent/conflict checks and additions.
  LOCK TABLE categories IN SHARE ROW EXCLUSIVE MODE;
  FOR wanted IN
    SELECT * FROM (VALUES
    ('cat-workbook-b407fa728da22bf62d983971', 'sub-workbook-b407fa728da22bf62d983971', 'Harvester Headers', 'cat-agriculture', 'Agriculture'),
    ('cat-workbook-fe9cec481a9123de3ba7537d', 'sub-workbook-fe9cec481a9123de3ba7537d', 'Greenhouse, Horticulture, Nursery Equipment', 'cat-agriculture', 'Agriculture'),
    ('cat-workbook-a28439d2cfcda233755b90b2', 'sub-workbook-a28439d2cfcda233755b90b2', 'Coffee Machines', 'cat-food-beverage-processing', 'Food & Beverage Processing'),
    ('cat-workbook-39aadf06d97676df5e9b5c27', 'sub-workbook-39aadf06d97676df5e9b5c27', 'Dish Washing Machines', 'cat-food-beverage-processing', 'Food & Beverage Processing'),
    ('cat-workbook-4bb71b78a35a8495f4367e57', 'sub-workbook-4bb71b78a35a8495f4367e57', 'Reach Stackers & Container Handlers', 'cat-material-handling', 'Material handling'),
    ('cat-workbook-2379568dfabb50573ee4592e', 'sub-workbook-2379568dfabb50573ee4592e', 'Vacuum Lifts, Sheet Metal Lifters', 'cat-material-handling', 'Material handling'),
    ('cat-workbook-b4c6e404cc766c67d8713b49', 'sub-workbook-b4c6e404cc766c67d8713b49', 'Wire Bonders', 'cat-semiconductors', 'Semiconductors'),
    ('cat-workbook-71e758af1d212fd8462a4f0c', 'sub-workbook-71e758af1d212fd8462a4f0c', 'Reflow Ovens', 'cat-semiconductors', 'Semiconductors'),
    ('cat-workbook-52a8fa97716f421af9cef2bd', 'sub-workbook-52a8fa97716f421af9cef2bd', 'Passenger Ships', 'cat-boats-marine-equipment', 'Boats & Marine Equipment'),
    ('cat-workbook-f6f8ca3e42f6218cbb184556', 'sub-workbook-f6f8ca3e42f6218cbb184556', 'Knuckleboom Loaders', 'cat-forestry', 'Forestry'),
    ('cat-workbook-6806be01e96ce86c01327260', 'sub-workbook-6806be01e96ce86c01327260', 'Yarders', 'cat-forestry', 'Forestry'),
    ('cat-workbook-987de94ffb8370e773a59c7b', 'sub-workbook-987de94ffb8370e773a59c7b', 'Triplex Pumps', 'cat-oil-gas-mining', 'Oil, Gas & Mining'),
    ('cat-workbook-718b13842e3db2fb8202fcad', 'sub-workbook-718b13842e3db2fb8202fcad', 'Stacking Conveyors', 'cat-oil-gas-mining', 'Oil, Gas & Mining'),
    ('cat-workbook-5d096daf777697846c0109b7', 'sub-workbook-5d096daf777697846c0109b7', 'Materials Testing', 'cat-test-lab-medical-equipment', 'Test, Lab, Medical equipment'),
    ('cat-workbook-e413cf5f1b0b815db4efcf9f', 'sub-workbook-e413cf5f1b0b815db4efcf9f', 'Chromatography', 'cat-test-lab-medical-equipment', 'Test, Lab, Medical equipment'),
    ('cat-workbook-096de9fd645e9e10c221cac3', 'sub-workbook-096de9fd645e9e10c221cac3', 'Mortisers and Tenoners', 'cat-woodworking', 'Woodworking'),
    ('cat-workbook-c279514b2cd847934a4cb669', 'sub-workbook-c279514b2cd847934a4cb669', 'Chainsaws', 'cat-woodworking', 'Woodworking'),
    ('cat-workbook-f26f06db3023a2d40a83b11c', 'sub-workbook-f26f06db3023a2d40a83b11c', 'Compactors', 'cat-construction', 'Construction'),
    ('cat-workbook-83e4aa04fc8c848777b2380b', 'sub-workbook-83e4aa04fc8c848777b2380b', 'Concrete Batching Plants', 'cat-construction', 'Construction'),
    ('cat-workbook-7c7e0b912d34456f324745ae', 'sub-workbook-7c7e0b912d34456f324745ae', 'IT Infrastructure', 'cat-industrial-automation', 'Industrial Automation'),
    ('cat-workbook-af267fd5a12bcb94f486810b', 'sub-workbook-af267fd5a12bcb94f486810b', 'Temperature Controllers', 'cat-industrial-automation', 'Industrial Automation'),
    ('cat-workbook-cae39a6a5ceee43408e514f5', 'sub-workbook-cae39a6a5ceee43408e514f5', '3D Printers', 'cat-printing', 'Printing'),
    ('cat-workbook-0920e3495e068aea3e6142e7', 'sub-workbook-0920e3495e068aea3e6142e7', 'Book Binding', 'cat-printing', 'Printing'),
    ('cat-workbook-dcf528b4577cd1b7e35c2d79', 'sub-workbook-dcf528b4577cd1b7e35c2d79', 'Leather', 'cat-textile-and-leather-manufacturing', 'Textile and leather manufacturing'),
    ('cat-workbook-7c698b457ad00111c4b2ac2c', 'sub-workbook-7c698b457ad00111c4b2ac2c', 'Medical Masks & Respiratory Protection Manufacturing', 'cat-textile-and-leather-manufacturing', 'Textile and leather manufacturing'),
    ('cat-workbook-d8e708dcdd733f0721ead6d9', 'sub-workbook-d8e708dcdd733f0721ead6d9', 'Cogeneration Plants', 'cat-energy', 'Energy'),
    ('cat-workbook-916daf2218e0fcbc6d9f3e0b', 'sub-workbook-916daf2218e0fcbc6d9f3e0b', 'Press Brakes', 'cat-machine-tools', 'Machine Tools'),
    ('cat-workbook-ee03c8e224756797245e2b9b', 'sub-workbook-ee03c8e224756797245e2b9b', 'Shearing Equipment', 'cat-machine-tools', 'Machine Tools'),
    ('cat-workbook-123c6bb6f8f56224ce4dfd5f', 'sub-workbook-123c6bb6f8f56224ce4dfd5f', 'Foundry & Casting', 'cat-processing', 'Processing'),
    ('cat-workbook-d8d80892ea7dc07eb6986a1e', 'sub-workbook-d8d80892ea7dc07eb6986a1e', 'Papermaking', 'cat-processing', 'Processing'),
    ('cat-workbook-fd934fbb0e14bc74073bbcce', 'sub-workbook-fd934fbb0e14bc74073bbcce', 'Concrete Mixers', 'cat-transportation-trailers', 'Transportation & Trailers'),
    ('cat-workbook-d932bcf17fe3ad65f257d172', 'sub-workbook-d932bcf17fe3ad65f257d172', 'Flatbed Trucks', 'cat-transportation-trailers', 'Transportation & Trailers')
    ) AS additions(id, slug, name, parent_id, parent_name)
  LOOP
    SELECT * INTO retained_parent FROM categories WHERE id = wanted.parent_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Missing expected parent %', wanted.parent_id;
    END IF;
    IF retained_parent.parent_id IS NOT NULL OR retained_parent.name IS DISTINCT FROM wanted.parent_name THEN
      RAISE EXCEPTION 'Expected parent changed: %', wanted.parent_id;
    END IF;
    IF EXISTS (SELECT 1 FROM categories WHERE parent_id = wanted.parent_id AND name = wanted.name AND id <> wanted.id) THEN
      RAISE EXCEPTION 'Child already exists with another ID: % / %', wanted.parent_id, wanted.name;
    END IF;
    SELECT * INTO current_child FROM categories WHERE id = wanted.id;
    IF FOUND THEN
      IF current_child.slug IS DISTINCT FROM wanted.slug
          OR current_child.name IS DISTINCT FROM wanted.name
          OR current_child.parent_id IS DISTINCT FROM wanted.parent_id THEN
        RAISE EXCEPTION 'Category ID conflicts with workbook definition: %', wanted.id;
      END IF;
      -- Existing exact rows remain untouched, including counts/icons/timestamps.
    ELSE
      IF EXISTS (SELECT 1 FROM categories WHERE slug = wanted.slug) THEN
        RAISE EXCEPTION 'Category slug conflict: %', wanted.slug;
      END IF;
      IF EXISTS (SELECT 1 FROM categories WHERE parent_id = wanted.parent_id AND name = wanted.name) THEN
        RAISE EXCEPTION 'Child already exists with another ID: % / %', wanted.parent_id, wanted.name;
      END IF;
      INSERT INTO categories (id, slug, name, icon, parent_id, listing_count)
      VALUES (wanted.id, wanted.slug, wanted.name, retained_parent.icon, wanted.parent_id, 0);
    END IF;
  END LOOP;
END
$workbook_categories$;
