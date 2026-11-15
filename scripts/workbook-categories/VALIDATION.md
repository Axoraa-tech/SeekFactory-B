# Validation — 2026-11-15

Base: dev 0d67ed2bc3b653b43039fc30733faa02267feebc (merged PR #61).

- `npm ci --ignore-scripts && npm test`: 8 passed, 0 failed, against the actual
  V32 file in disposable PGlite. No environment database connection.
- CategoryCacheIsolationTest: 3 passed, 0 failed in a targeted Java/JUnit harness
  compiling the actual service, entities and repository with cached dependencies.
  Mockito's default inline self-attach was unsupported in this environment;
  the harness used mock-maker-subclass. No production/source configuration changed.
- `git diff --check`: passed.
- Independent read-only review: additive SQL and conflict safeguards checked.

Limits: not a full Maven application test or real-environment Flyway execution.
No original product-category mapping is established by these tests. No database
migration, merge or deployment was performed. The existing Azure deployment
workflow is manual-only; it has not been invoked.
