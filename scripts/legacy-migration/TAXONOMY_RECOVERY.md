# Legacy subcategory recovery: preparation only

The legacy import mapped original category/subcategory pairs onto the V6 tree.
Some pairs became root assignments, so those products cannot appear when a child
category is selected. Restoring old subcategories must be **additive**: retain all
current product lines, categories, IDs and non-legacy assignments.

## Status and source gate

The original October 12 extraction (`data/categories.json` and
`data/products.json`) is not in the repository; `data/` is gitignored. V19 and
`category-map.mjs` are lossy outputs, not substitutes for the original source.
The known mappings are not a complete category list. This PR restores no data
and contains no Flyway migration or database write path.

Obtain the original extraction or an authenticated legacy database export. Keep
raw source files unchanged and outside git. Independently verify normalization
of the source taxonomy into exact pairs; do not derive the taxonomy from product
names or the lossy V6 map. A hash proves input integrity, not source authenticity
or completeness. A reviewer must establish those separately.

## Inputs

The planner uses a manifest with `schemaVersion: 1`, an explicit
`targetEnvironment` and four file references named `taxonomy`, `sourceProducts`,
`target`, and `parents`. Each reference requires `path`, lowercase hex `sha256`,
ISO `capturedAt` and a meaningful `provenance` identifying the original source
and, for normalized files, the normalization/review record. Paths resolve from
the manifest's directory. Do not include secrets or public signed URLs.

- `taxonomy`: array of `{category, subcategory}` pairs verified against the
  **complete original taxonomy**, including categories that have no products.
  Preserve exact source spellings. Every pair must be unique.
- `sourceProducts`: original `/api/products` array, or `{data: [...]}`, with
  `_id`, `category` and `subcategory` on every product. Full source inventory is
  required; missing source/target products block readiness rather than silently
  dropping records. Known excluded records need separate reviewed handling.
- `target`: consistent read-only snapshot `{categories: [...], products: [...]}`.
  Categories use `id`, `slug`, `name`, `parent_id` (roots have null parent).
  Products use `id`, `category_id`. Include complete inventories, not just
  visible/listed records. Avoid unrelated private product/customer fields.
- `parents`: reviewed array of `{legacyCategory, targetParentId}`. Each target
  must be an existing retained root. This mapping is explicit, never guessed.

## Run and inspect

```sh
node --test scripts/legacy-migration/plan-taxonomy-recovery.test.mjs
node scripts/legacy-migration/plan-taxonomy-recovery.mjs /absolute/manifest.json /absolute/new-plan.json
```

The output file must not already exist. A plan with blockers exits with status
2. Proposed additions/updates remain diagnostic suggestions when blocked, not
an approved executable batch. `readyForReview` means only the planner found no
known mismatch; it does **not** authorize application or establish source
completeness. There is deliberately no SQL generation, network or database
connection, automatic deployment, product creation, or category deletion.

## Rules and eventual apply gate

- Match exact original product `_id` to `lgp-<id>`, not titles or slugs.
- Match exact source category/subcategory pairs; no fuzzy matching, fallback or
  spelling correction. Unknown pairs and changed current assignments block.
- Reuse an exact child under the explicitly selected root, otherwise propose a
  deterministic new child. Keep current V6 rows untouched.
- Existing legacy products may only be proposed for a move when their current
  assignment still equals the explicit V19 pair mapping. Ambiguous/currently
  edited products require separate review, not force updates.
- Before a separate apply implementation: establish the exact destination,
  freshly back up current categories and affected assignments, recheck all
  source/target hashes and row preconditions, and review every proposed change.
  Apply new categories before links in one transaction with compare-and-set
  checks, aborting on any conflict. Preserve all other fields. Verify counts,
  parent/child integrity, product visibility and navigation in the actual client
  after deployment. Do not rerun V19: it updates unrelated product content.
- Tests here use synthetic fixtures only. They do not establish a recovered
  taxonomy, completed migration, or live website correctness.
