# Workbook category additions — prepared, not applied

This standalone SQL candidate adds exactly 32 subcategories listed in Sheet1 but
absent from the read-only DEV snapshot on 2026-11-15. Sheet1 contains 219 children;
Sheet2 contains 187, all matching the current category tree. Existing Healthcare
and all other product lines remain untouched. This is workbook coverage repair,
not proof of restoration of the complete old Mongo taxonomy.

Source: SEEKFACTORY MACHINERY EQUIPMENT PRODUCT LIST NAMES.xlsx
SHA-256: fd455c84756163ce0de5b32ecf16a6ab920bbc4e9f45fed9d50d86ce0a6fbf6c
`expected-additions.json` preserves each workbook parent, exact child label,
Sheet1 cell, raw value including count suffix, and proposed target identifiers.
Counts printed in the workbook are not actual SeekFactory inventory counts and
are NOT imported. New listing_count values are zero; icon inherits its parent.
IDs/slugs use a deterministic SHA-256 prefix over retained parent ID + exact name.

## Scope and safeguards

- Adds rows only. No UPDATE, DELETE, product creation/reassignment, manufacturer
  links, schema changes, category replacement, or deployment settings.
- Validates exact parent ID, name and root status. Missing/changed parents abort.
- Existing expected IDs are no-ops only if name, slug and parent match exactly.
- Conflicting ID, slug or an exact child name under a different ID aborts;
  the operator must reconcile that situation rather than create duplicates.
- Existing categories, counts, icons, timestamps and noncategory tables survive.
- A category-table write lock prevents check/insert races. The entire DO block
  is atomic: even a late conflict rolls back earlier inserts in that statement.
- Intentional hierarchy additions are independent of legacy product mapping.
  Root-assigned legacy products remain root-assigned until original mappings
  are independently verified. Empty new subcategories are expected initially.

## Validation and incorporation

Run only the disposable local test harness:
`npm ci --ignore-scripts && npm test`

The test imports PGlite, builds an isolated category schema from the included sanitized category-only
read-only target snapshot (`category-fixture.json`) and uses synthetic product/link rows. It never
connects to the remote database. Parent/category name and child-name comparisons
are exact; no case folding or fuzzy match is applied.

The candidate is integrated as V32__add_missing_workbook_categories.sql.
The tests read that actual migration file rather than a duplicated SQL copy.
Recheck the migration version against the target branch before merging.
Review the exact target and obtain its current backup before execution.
The normal transactional Flyway deployment will acquire a category write lock.
Do not merge/deploy this draft until database execution is separately authorized.

The accompanying service fix gives all-category and root-category caches independent
atomic value/timestamp entries. Refreshing one no longer keeps stale data in the
other alive indefinitely. CategoryCacheIsolationTest covers both directions and
normal fresh-cache reuse. It does not implement immediate invalidation: up to the
existing ten-minute TTL remains expected on a running instance.

No product mappings are guessed or changed. Adding empty children will not
reassign the 214 legacy products previously verified on root categories.
Original Mongo taxonomy and product-to-subcategory mappings are still required
for that separate recovery.
