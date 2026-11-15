# Manual, reviewed category reconciliation

This tool is **offline only**. It writes SQL files, never connects to a database,
and is outside Flyway. Do not copy outputs into `db/migration` or deployment hooks.
No customer snapshots, IDs, credentials or generated SQL belong in Git.

1. Capture old product IDs and exact category/subcategory strings read-only.
   Classify by stable IDs, never names. Legacy updates may have left stale
   subcategory strings: source presence does not prove accurate classification.
2. Capture a fresh target snapshot and keep existing child assignments/new
   products unchanged. A conservative plan has `targetProjectRef`, `targetCapturedAt`, `additions`
   (`id`, `slug`, `name`, `parent_id`) and `updates` (`id`, `expected_category_id`,
   `expected_updated_at`, `category_id`, `expectedRow`). `expectedRow` must be the complete product row from `to_jsonb(products)`;
   partial rows fail closed at execution. Keep this private. Only root-to-child changes are supported.
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
   and a unique `appliedAt` ISO timestamp after the snapshot. Flags must be literal JSON booleans `true`, never strings. The project label
   must exactly match `plan.targetProjectRef`; it is emitted as an operator-verified
   label, not verified by SQL. Database OID/address guards cannot establish the
   Supabase project identity. Boolean flags are operator attestations, not automated proof; never fabricate them.
5. Run `node render-reviewed-reconciliation.mjs plan.json review.json output`.
   Review both generated SQL files. They contain **COMMIT** and execute only when
   deliberately submitted by an authorized operator. Run via `psql` (ON_ERROR_STOP).
   Set appropriate maintenance windows; category locking may briefly block writes.
6. Apply is one transaction: target identity, category collisions, root parentage,
   exact product ID/current category/updated_at, full row equality (excluding timestamp, checked separately) and affected row count are checked.
   Any mismatch aborts all changes. Repeating apply aborts rather than duplicating.
   Verify counts, product browsing, preserved data and target identity after commit.
7. Rollback compares the full expected post-apply row plus the apply timestamp and
   refuses edits even when a writer failed to bump `updated_at`.
   It restores original category and timestamp, retaining added categories to avoid
   deleting later references. If any record drifted, do not force rollback; review
   and recover from the verified backup. No other product fields are modified.

Tests: `node --test render-reviewed-reconciliation.test.mjs`.
These are offline renderer/validation tests, not a production database rehearsal.
A realistic isolated database apply/rollback rehearsal remains required before use.

Isolated execution test: set `PGLITE_MODULE` to the installed PGlite module URL
and run `node --test render-reviewed-reconciliation*.test.mjs`. PGlite instances
are ephemeral and do not connect remotely. Only its socket-address builtin is
stubbed (PGlite has no network socket); the generated SQL executes unchanged
apart from the psql-specific `\\set` directive. Tests cover successful apply and
rollback, stale rows without timestamp changes, category collision, incorrect
target database and repeated apply. These are not evidence about production
triggers, actual connection identity, backups or classification correctness.

## Current delivery scope: local test only

The current implementation and tests are local-only. The latest requested future
workflow is automatic reconciliation on production deployment, but that behavior
is NOT implemented or safe to enable yet: exact classifications, target identity,
backup and rollback must first be independently verified. No merge, deployment
or remote database run is authorized as part of this PR preparation.
The historical field name `liveTargetVerified` is an operator attestation in the
renderer, **not permission to use production**. In tests it refers only to the
ephemeral fixture. Do not mark it true for a real target during this task.

`reconciliation.synthetic-example.mjs` provides a fully invented one-product,
one-child plan, six held/ambiguous cases and a deliberately unapproved review
template. It is not a customer migration or evidence that historical labels are
correct. A product ID match cannot resolve stale classification. Keep all
unresolved cases out of `updates`; never fuzzy-match names or overwrite an
existing child assignment.

### Reproduce locally

From this directory:
```sh
node --test render-reviewed-reconciliation.test.mjs
# Use an already installed @electric-sql/pglite module:
PGLITE_MODULE=file:///absolute/path/to/@electric-sql/pglite/dist/index.js \
  node --test render-reviewed-reconciliation*.test.mjs
```

The integration suite creates only in-memory synthetic tables, derives their
database OID/address, executes generated SQL, checks outcomes and closes them.
It needs no database URL, service key, production export or network connection.
Without PGLITE_MODULE the integration cases are skipped; a skipped run is not
execution validation. The unapproved example intentionally fails rendering.

For a future separately authorized non-production review, write exact reviewed
plan bytes to a private JSON file and obtain its SHA-256 (for example
`sha256sum plan.json`). Place that digest in the private review manifest only
after independent classification, snapshot, identity and backup verification.
Run the renderer command above to create SQL for inspection only. Never copy
synthetic identity values into a real review. SQL contains COMMIT; do not submit
it to any database under the current local-test-only instruction.

### Future automatic deployment approach: blocked, not implemented

Only a separately reviewed, exact and semantically validated assignment set can
be considered for a versioned deployment migration. Ambiguous records must remain
excluded. Do not commit customer snapshots or a real assignment dataset without
explicit authorization. Deployment execution would need a verified pre-deploy
backup, bounded transactional updates, row drift guards, controlled locking,
post-deploy verification and a tested rollback procedure. Startup execution can
block application availability or fail deployment on drift; adding a Flyway file
is not merely shipping a tool. Never turn the synthetic example into an automatic
seed or bypass a denied target review. This PR adds no Flyway, CI or startup hook.
