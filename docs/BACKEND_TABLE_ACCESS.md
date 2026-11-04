# Backend-only table access

V26 enables PostgreSQL row-level security on `public.product_likes` and on
`public.blog_posts` when that optional table exists. It revokes all table privileges
from `PUBLIC`, `anon`, and `authenticated` (the latter roles only when present).
There are intentionally no client-facing RLS policies. Requests must go through
the backend's existing authentication and authorization.

The migration does not force RLS, change ownership, or change direct grants to
`postgres` / `service_role`. The reviewed DEV backend uses `postgres` with
BYPASSRLS. A backend using a non-owner, non-BYPASSRLS role needs a separate access
review before this migration is deployed. RLS bypass does not replace SQL grants.

`product_likes` is required by V23, which is unchanged. `blog_posts` is present in
DEV but has no creation migration in this repository, so V26 skips it on fresh
installations. Any future migration that creates that table must secure it at
creation time. Its external ownership and application usage remain unverified.
This change does not claim a blog HTTP endpoint was tested.

## Isolated regression tests

With the repository's Java 25 JDK and Maven wrapper, run:

```sh
sh mvnw -Dtest=BackendTableAccessMigrationTest test
```

The tests reuse the existing embedded-Postgres dependency and start disposable
loopback-only databases. They do not start Spring or read `.env`, application
configuration, or remote database credentials. They exercise the real V23 and
V26 SQL resources, not a copied migration:

- Both tables present: revoke all table privileges for API roles and PUBLIC,
  reject SELECT/INSERT/UPDATE/DELETE/TRUNCATE, preserve postgres and service_role
  CRUD access, and verify repeat application is safe.
- Fresh database without Supabase roles or `blog_posts`: migrate successfully,
  preserve backend CRUD, and remove PUBLIC read access.
- Defense in depth: after deliberately regranting API CRUD privileges in the
  disposable fixture, RLS hides existing rows and rejects inserts.

These are focused SQL regression tests, not a full historical Flyway-chain or
end-to-end application test. The synthetic blog fixture tests access controls
without assuming the structure or application behavior of the external table.

## DEV verification and rollout boundary

The equivalent DEV-only hotfix was applied separately on 2026-11-04. Catalog
checks showed RLS enabled on both tables, no effective table/column privileges
for `anon` or `authenticated`, and unchanged postgres/service_role privileges.
Read-only EXPLAIN checks returned SQLSTATE 42501 for both client roles on both
tables and succeeded for both backend roles. These live checks are separate from
the repository test run.

This PR targets `dev`. Creating it does not merge or deploy it, and production
was not changed. Review the backend database role and any direct client usage
before any future deployment to another environment.
