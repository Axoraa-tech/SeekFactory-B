# Automatic historical subcategory restoration (V33)

**This PR now changes deployment behavior.** Once reviewed, merged and deployed
to an application running Flyway, V33 executes automatically in its PostgreSQL
migration transaction. Preparing this PR did not merge, deploy or access a remote
database. Do not bypass any denied target review.

## Exact scope and accepted uncertainty
The owner approved restoring these historical labels after disclosure that the
legacy updateProduct handler could leave stale subcategories. This is a historical
restoration, NOT independent proof that every product's classification is correct.
The migration includes only 164 explicit stable product IDs, their observed root
category/timestamp preconditions and 14 additive child categories. Existing child
assignments, 50 unsupported historical pairs, unmatched products and new products
are not changed. Current root parents are retained. No deletion or fuzzy matching.
Only the minimum approved mapping dataset is committed, not full snapshots,
product details, passwords, URLs or connection credentials.

## Deployment gates and failure behavior
V33 was unused in dev at preparation (latest V32); recheck before merging.
The preconditions come from a historical DEV snapshot, not a verified production
mirror. A target lacking any exact ID/root/timestamp will FAIL and abort migration.
An existing destination ID/slug/name conflict also fails. Do not remove guards to
make deployment pass. Refresh evidence and review a new plan instead.
This intentionally can block application startup. The migration checks dataset
identity, not provider project identity. The deployment owner must verify the
actual target and full recoverable backup before deployment; no live verification
or production rehearsal is claimed.

It locks categories and products briefly (5s lock timeout, 60s statement timeout),
preflights all rows, inserts 14 children and changes precisely164 category IDs.
It preserves unrelated product fields; unexpected triggers abort the transaction.
A private audit schema captures complete before/after rows at execution.
Audit records are not a substitute for an independently recoverable DB backup.
Ensure the migration role can create this schema/table; failure aborts safely.
Exact replay is a no-op; changed post-state rejects replay. Flyway normally runs
a version only once. Do not edit the SQL/checksum after a successful deployment.

## Rollback
scripts/legacy-migration/rollback-v33-manual.sql is manual, NOT a Flyway migration.
Verify target and backup, inspect the SQL, then an authorized operator can run
through psql. It contains COMMIT. It requires all164 full post-state rows unchanged
even if later writers did not bump updated_at. Any drift aborts all rollback.
It restores prior category and timestamp, validates full restored rows and keeps
added categories and audit entries to avoid deleting subsequent references.
After manual rollback Flyway still records V33 applied: do not repair/delete its
history or auto-reapply. A future forward fix needs its own reviewed version.

## Reproduce isolated tests
Use an already installed PGlite module; no remote database URL is used:
```sh
PGLITE_MODULE=file:///absolute/path/to/@electric-sql/pglite/dist/index.js \
  node --test scripts/legacy-migration/auto-reconciliation.test.mjs
```
Tests execute the exact committed SQL in ephemeral PostgreSQL with reduced tables,
derived164 input rows and synthetic preserved fields. They cover apply, replay,
rollback, missing/stale/root-to-child protection, collision, trigger mutation,
rollback and replay drift. Optional PRIVATE_TARGET_SNAPSHOT points to a private
minimal local snapshot for a634-row rehearsal; never upload it. Without
PGLITE_MODULE tests are skipped, not passed execution validation.
These tests do not establish production schema/trigger equivalence or semantics.

Initial-apply drift limitation: source snapshot contains category/timestamp only.
A later writer that changes other fields without bumping updated_at cannot be
detected before assignment. Those fields are preserved, but classification may be
stale. Full-row guards apply to post-state replay/rollback, not historical initial
apply. The owner accepted historical-label uncertainty; this is not a guarantee
of no semantic mismatches. Review deployment-time evidence before release.
