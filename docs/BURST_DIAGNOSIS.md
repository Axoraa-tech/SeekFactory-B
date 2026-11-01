# Local burst transport diagnosis (2026-11-01)

The original 85/2,500 and90/2,500 connection failures remain recorded in the bounded result files.
They are failed end-to-end tests, not a production capacity measurement. Controlled comparisons use
the same disposable stack, Windows aggregate2GiB/two-CPU-equivalent job, synthetic500 actors and five
requests per actor. No cloud endpoint or production data is used.

## Controls and independent client

The Java25.0.4.1 HTTP/1.1 generator completed all2,500 requests against both a trivial Node HTTP server
and Spring directly. Against Next, it produced ConnectException with a ClosedChannelException root,
including with a newly created client. The same failure also occurs on Next's static `/robots.txt`
(1,265/2,500 failures in the recorded control), which bypasses the API proxy, Spring and PostgreSQL.
Thus the backend connection pool, database transaction locks and media pipeline cannot explain that
static-route reproduction. There were no corresponding Next application error logs or HTTP5xx statuses.

A separate Node/Undici6 transport, capped at500 active connections, completed the actual authenticated
Next-proxy feed/reaction mix three times:2,500 HTTP200 each, zero transport errors, p952,118ms,1,823ms
and1,949ms.
It consumes every body and adds no application retry loop. Its connection management differs from
Java's; these passes do not erase the Java failures or establish universal client compatibility.
Peak aggregate job commit for those comparison runs was1,673,170,944 and1,686,409,216 bytes.

A final diagnostic disabled Java's built-in connect retry **only to expose the original exception**,
not to obtain a passing capacity result. The static-route failures then reported
`ConnectException: Connection refused: getsockopt` (1,198/2,500); the fresh-client API case reported
992/2,500 refusals. The shared-client API case happened to complete2,500/2,500. This local Next TCP
admission failure is sensitive to client connection scheduling. It precedes HTTP routing and is not
an authentication, SQL pool timeout or application HTTP500. Windows listener/backlog or event-loop
admission pressure is a plausible explanation, not an established unique root cause. No OS socket
limits or Next package internals were changed. The Java retry path obscured the original refusal as
a closed-channel error; the preserved traces and local JDK source establish that retry sequence.

## Corrected proxy defect

The web proxy forwarded Tomcat's `Connection`/`Keep-Alive` headers to the independent browser hop.
It now strips standard hop-by-hop headers and every header named by `Connection` in both directions.
The route regression preserves206 range/cache delivery and removes transport metadata/backend cookies.
All154 web tests, TypeScript and the standalone build pass; production npm audit reports zero findings.
The Java connection failures still reproduce after this correctness fix, so it is not presented as
the cause or cure of those failures. No timeout, authentication requirement or failure threshold was
relaxed to obtain a pass.

## Reproduction and interpretation

Compile tests and build/copy the standalone web assets as in EXTENDED_VALIDATION.md. Set
`LOCAL_DIAGNOSE_BURST=true` and `LOCAL_UNDICI_MODULE` to a separately installed test-only `undici@6`
directory, then run `Run-LocalStackFixture.ps1` through the bounded wrapper. The diagnostic records all
outcomes; its exit0 means collection completed, not that every scenario passed. Read the result rows.
The independent generator is `scripts/local-node-burst.cjs`; a loopback guard rejects external targets.
Undici and the local media tools are not production application dependencies.

The pre-fix comparison's independent generator did not run because a newly installed tool version
required a Node zstd API absent from local Node23.5. That run exited1; its Java rows remain useful and
are preserved in `burst-diagnostic-before.txt`. The independent client was rerun with compatible
Undici6. The later result files contain the actual completed independent measurements.

These are client/host-specific transport observations. An actual Linux reverse-proxy/Azure staging
test remains required. Do not size a2GiB Azure host or promise500 production users from these results.
Until staging demonstrates the chosen workload, use admission/arrival controls and a small pilot;
the existing lower25/100/250 Java burst stages passed, but are not a production user allowance.
Stage the real reverse proxy with persistent upstream connections, SSE buffering disabled and explicit
connection/arrival admission limits; verify rejection/reconnect behavior under the same abrupt burst.
Do not apply arbitrary kernel/backlog or timeout tuning to production based on this Windows fixture.

The separate final one-CPU/2GiB gradual-arrival test also failed for a different reason:11 requests
exceeded ten seconds, after2,248 completed HTTP200/201 requests over34.546s with500 idle SSE streams.
This is a latency/capacity gate even when arrival bursts are avoided. No exception/HTTP failure was
recorded in that run. See EXTENDED_VALIDATION.md and the final steady result/job files.
