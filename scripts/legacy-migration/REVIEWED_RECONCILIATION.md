# Manual, reviewed category reconciliation

This tool is **offline only**. It writes SQL files, never connects to a database,
and is outside Flyway. Do not copy outputs into `db/migration` or deployment hooks.
No customer snapshots, IDs, credentials or generated SQL belong in Git.

1. Capture old product IDs and exact category/subcategory strings read-only.
   Classify by stable IDs, never names. Legacy updates may have left stale
   subcategory strings: source presence does not prove accurate classification.
2. Capture a fresh target snapshot and keep existing child assignments/new
   products unchanged. A conservative plan has `targetCapturedAt`, `additions`
   (`id`, `slug`, `name`, `parent_id`) and `updates` (`id`, `expected_category_id`,
   `expected_updated_at`, `category_id`). Only root-to-child changes are supported.
   Every destination must remain under that product's existing root. Ambiguous,
   unmatched, stale or semantically questionable records remain held for review.
3. Obtain an independently verified full affected-row backup and test its recovery
   procedure. Verify the actual target project/connection independently; DEV
   branch names do not identify a live database. Obtain current database name,
   database OID and server IP via read-only SQL. Host/IP/OID may change, requiring
   a fresh review. These SQL guards complement, not replace, connection verification.
4. Recheck source and target for drift. Review every proposed classification and
   the exact plan hash. Create a private review JSON with `planSha256`,
   `sourceClassificationReviewed:true`, `backupVerified:true`,
   `liveTargetVerified:true`, `target:{projectRef,database,databaseOid,serverAddress}`
   and a unique `appliedAt` ISO timestamp after the snapshot. Boolean flags are
   operator attestations, not automated proof; never fabricate them.
5. Run `node render-reviewed-reconciliation.mjs plan.json review.json output`.
   Review both generated SQL files. They contain **COMMIT** and execute only when
   deliberately submitted by an authorized operator. Run via `psql` (ON_ERROR_STOP).
   Set appropriate maintenance windows; category locking may briefly block writes.
6. Apply is one transaction: target identity, category collisions, root parentage,
   exact product ID/current category/updated_at, and affected row count are checked.
   Any mismatch aborts all changes. Repeating apply aborts rather than duplicating.
   Verify counts, product browsing, preserved data and target identity after commit.
7. Rollback compares destination plus the apply timestamp and refuses newer edits.
   It restores original category and timestamp, retaining added categories to avoid
   deleting later references. If any record drifted, do not force rollback; review
   and recover from the verified backup. No other product fields are modified.

Tests: `node --test render-reviewed-reconciliation.test.mjs`.
These are offline renderer/validation tests, not a production database rehearsal.
A realistic isolated database apply/rollback rehearsal remains required before use.
