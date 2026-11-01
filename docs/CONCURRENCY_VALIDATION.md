# Concurrent chat and media processing

This branch adds PostgreSQL LISTEN/NOTIFY fan-out between backend instances. Message writes, revisions,
and `pg_notify` share the sending transaction: rollback sends no notification. Notifications contain
conversation/message IDs only. Each listener loads the committed message in a short read transaction,
releases the database connection, and then sends SSE. The database is the source of truth; notifications
are wake-ups, not a durable queue.

The web companion consumes `/conversations/{id}/messages/sync?after=N`. Each conversation has a
serialized revision counter. Sends, edits and deletes stamp the message with that revision. Sync returns
up to 100 changed messages in revision order with `cursor` and `hasMore`. Clients retry unchanged cursors
after failures, reconcile on INIT/reconnect and every 15 seconds while visible, and reject stale revisions.
Existing messages remain available through the existing history endpoint.

Send clients may supply `clientMessageId` (1-64 URL-safe characters). Reusing it within the same
conversation and sender returns the original message without incrementing unread counts again.
Older clients may omit it. A conversation row lock serializes sends and explicit read acknowledgments;
stream registration alone no longer marks a message read. Buyer row locks serialize creation of the
same buyer/factory conversation. Reel likes/saves and comment counters use row locks as well.

## Configuration and rollout requirements

- Review and apply migration V25 through the normal deployment process before the companion web
  change. This task has not applied migrations to any shared database or deployed either application.
- LISTEN needs a direct PostgreSQL connection or **session** pooling. Transaction pooling cannot retain
  the LISTEN session. If the application datasource uses transaction pooling, set
  `APP_CHAT_LISTENER_URL` to a direct/session JDBC URL, with `APP_CHAT_LISTENER_USERNAME` and
  `APP_CHAT_LISTENER_PASSWORD` as needed. They default to the datasource credentials. Keep credentials
  in the deployment secret store.
- The listener reserves one connection per backend instance. When using the application pool, this is
  included in its configured pool size (default 20); a separately configured listener adds one connection.
  Include every instance in the database connection budget.
- No new paid broker/service is required. Existing database connections, CPU, storage and network still
  consume hosting resources. This change does not establish a monthly cost or a resource size.
- Listener reconnect uses a one-second delay. Event delivery has two workers and a 256-event queue.
  If delivery is saturated, hints can be dropped; database sync recovers the stored changes. Monitor
  disconnect and queue-full warnings. Configure the reverse proxy to stream SSE without buffering and
  allow idle periods longer than the 15-second heartbeat.
- SSE admission is capped at 1,024 streams per process and 20 per conversation. These are protective
  limits, not measured capacity promises. Excess admission receives HTTP 429 with Retry-After.

## Media behavior

Video processing admits at most eight jobs, including the running job, per process. Admission happens
before writing a local work copy. It accounts for three times the input bytes across admitted jobs and
leaves 256 MiB free-disk headroom. Full admission returns HTTP 429 instead of growing an unbounded queue.
The original is still served while encoding. Pending files are scanned every minute, with an in-process
set preventing duplicate submissions; interrupted/finalization-failed work can be retried. The work
directory must survive restarts to preserve retries. This remains a local worker, not a distributed job
queue. Separate instances need separate work directories; do not share a pending directory between them.

## Validation and limits

Run `mvn -Dtest=*Test -Dseekfactory.localLoad=true test` with Java 25. Set `FFMPEG_PATH` to a local
ffmpeg executable to include real encoding tests. `*Test` deliberately excludes the existing
Docker-dependent `AxoraaApplicationTests`; Docker was unavailable on the validation machine.

The opt-in test starts disposable PostgreSQL 14.22 on loopback, removes system/environment configuration
sources, loads only `local-load.yaml`, uses temporary local media, disables Flyway/demo seeding, and
does not configure SMTP or a bucket. Test users have `.invalid` addresses. No production credentials
or endpoints are used. A separate disposable-PostgreSQL test validates V25 and committed/rolled-back
notification delivery to two listeners.

The HTTP workload checks parallel retry deduplication, concurrent unread increments, sync pagination,
edit/delete recovery, and reaction counts. Burst ramps use 25/100/250/500 synthetic clients. A further
20-second 500-client mix uses approximately 75% feed reads, 15% sync, 5% sends and 5% small PNG uploads,
with one second of think time. Tests stop on request errors or latency exceeding ten seconds. The
committed `local-load-results.txt` records the actual run.

These are short local tests against a tiny synthetic catalog, with server and generator sharing a laptop.
They do not certify 500 production users, a 2 GiB VM, Next.js server memory, CDN throughput, large video
uploads, or India/China latency. Heap samples are not process RSS or total hosting RAM. Bucket policy,
range/signed-URL behavior on the chosen provider, cache hit rate, egress, backups and tax remain deployment
validation/cost inputs. The existing initial full-history endpoint remains unpaginated, and online
presence remains in-process; those limitations need follow-up before claiming unrestricted growth.
