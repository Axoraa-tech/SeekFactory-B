# Extended validation (2026-11-01)

Follow-up transport isolation and the actual underlying socket error are documented in
[BURST_DIAGNOSIS.md](BURST_DIAGNOSIS.md), with preserved controls and independent-client results.
The original failed burst rows below are retained. The diagnostic narrows the local failure to TCP
admission at Next before HTTP routing; it does not establish an Azure production capacity result.

The earlier unrestricted backend-only burst is not a production sizing result. This extension tests
the production Next.js proxy, Spring backend, disposable PostgreSQL, ffmpeg and synthetic client together.
See the accompanying bounded result files for actual results, including failures.

## Resource approximation

`scripts/Invoke-BoundedLocalTest.ps1` places the validation shell and its descendants in a Windows Job
Object with a 2,147,483,648-byte aggregate **committed-memory** limit. CPU hard caps use 500/10000 and
1000/10000 of a 20-logical-processor host: approximately one and two CPU equivalents. The JVM has a
640 MiB heap limit (618 MiB reported usable with SerialGC), 256 MiB metaspace limit and two advertised
processors; Next.js has a 384 MiB heap limit. The generator and Maven overhead are included in the job.
These are Windows quota approximations, not Linux RSS limits, Azure hardware, or B-series credit tests.

Both all-at-once 500-client bursts failed the unchanged error stop threshold:

| CPU approximation | Requests | Errors | p95 | Peak aggregate commit |
| --- | ---: | ---: | ---: | ---: |
| One CPU | 2,500 | 85 | 8,416 ms | 1,441,472,512 bytes |
| Two CPUs | 2,500 | 90 | 5,480 ms | 1,431,740,416 bytes |

The two-CPU errors were `ConnectException`s. No application HTTP error status was recorded in that
run. The one-CPU run did not record exception categories. These results establish a failure of the
tested end-to-end burst; they do not independently locate the bottleneck or prove an Azure VM's capacity.
Lower burst stages (25/100/250 clients) had zero request errors. Each failed burst stopped before its
sustained phase. A separate gradual-arrival scenario holds 500 SSE connections and ramps 500 mixed
clients over ten seconds, with a requested 180-second duration and one-second think time. Its result
must be read separately; a steady-state pass does not erase the failed burst.

That separate two-CPU steady run passed: 500 open SSE streams, 46,163 requests over181.320 seconds,
zero request errors, p95 1,763ms (41,556 HTTP200 and4,607 HTTP201). Peak job commit was1,955,274,752
bytes, about1.82 GiB. This leaves little margin against the job limit and excludes host OS memory;
it is not proof of a2 GiB Azure server or the cheaper one-vCPU budget configuration. There were
approximately255 completed requests/second, not500 requests/second.

The final one-CPU/two-GiB gradual-arrival repeat with the corrected proxy **failed** the existing
ten-second per-request latency threshold:500 idle SSE streams,2,248 completed HTTP requests over34.546s,
11 latency violations, p957,272ms. All completed statuses were expected (2,013 HTTP200,235 HTTP201);
the count is latency violations, not HTTP/transport errors. It stopped early instead of claiming a
three-minute pass. Peak job commit was1,467,957,248 bytes; CPU94.859s over114.207s including setup.
See `final-steady-one-cpu-results.txt` and its job file. This demonstrates inadequate performance for
this fixture under the one-CPU cap, even with gradual arrivals; it does not prove exact Azure capacity.
Do not approve the one-vCPU budget configuration for the requested500 workload on this evidence.

The final two-CPU/two-GiB control after the proxy correction passed:500 idle SSE streams,
44,401 requests over181.567s, zero errors/latency violations, p951,747ms (39,964 HTTP200,4,437 HTTP201).
Peak job commit was1,913,634,816 bytes (~1.78GiB), excluding the host OS; total CPU422.516s over252.895s
including setup and cleanup. See `final-steady-two-cpu-results.txt` and its job file. This repeat
supports the difference between the fixture's one- and two-CPU envelopes; it still does not establish
an Azure SKU, B-series credits, production data size or the monthly budget.

SSE connections in the load fixture are open idle streams, not 500 rendered browsers. The burst uses
authenticated feed reads and hot-reel reactions. The mixed scenario uses 75% feed, 15% sync reads,
5% message sends and 5% small PNG uploads. JWT validation is exercised; password-login, signup, and
token-refresh storms are not. The synthetic catalog is tiny, and most conversation histories are empty.
Real video upload/encoding is checked before load, not as 500 simultaneous encoders/viewers.

## Local media protocol checks

The HTTP checks upload a generated 1280x720 H.264 clip through the actual backend, await compression,
verify a 206/100-byte range response, immutable cache headers, allowed/blocked origins, range preflight,
and full-queue 429 with Retry-After. Backend CORS defaults now allow Range and expose the relevant media
headers.

`LocalS3ProtocolTest` drives the real AWS SDK against loopback S3rver, with a fresh disposable directory
and dummy S3RVER credentials. It verifies generated-video compression, SDK PUT/GET, range206, immutable
cache, allowed/blocked origins, range preflight, a two-second signed-link expiry returning403, and a
renewed link returning200. **S3rver does not verify SigV4 cryptographic signatures or enforce production
bucket ACLs.** This is protocol coverage, not R2/CDN security certification. MinIO's official Windows
binary endpoint returned410, so it was not used. No cloud bucket was accessed.

## R2 privacy configuration

The application stores `media/` and private chat attachments in one bucket. A plain R2 public bucket
domain can expose that bucket's contents. The new startup guard rejects a nonempty public-base URL
for an R2 endpoint. For this design, keep both r2.dev and public custom-domain access disabled, set
`APP_MEDIA_S3_PUBLIC_BASE_URL` empty, use region `auto`, and deliver through SDK-generated signed URLs.
Public CDN delivery requires a separate reviewed design protecting private attachments.

References: [R2 public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/) and
[presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/).

## Geography and remaining external validation

The current probe reported country IN and Cloudflare edge MAA. One ordinary HEAD to the existing public
`https://www.seekfactory.com` returned200 in719ms. This is one India-origin observation of the existing
site, not the proposed Azure/R2 stack and not a latency SLA. There is no available mainland-China probe,
selected staging Azure endpoint or isolated R2 account/bucket. China playback, actual R2 signature/ACL
enforcement, CDN cache behavior and real Azure credit/CPU/storage behavior remain untested.

No production load, cloud provisioning, shared migration, production deployment or merge occurred.

The long run exposed authorization errors on internal SSE completion dispatches after stream closure.
The fix permits only chat-stream ASYNC continuations; initial REQUEST authentication and conversation
membership remain required. Regression tests cover initial401, internal completion200 and other private
ASYNC paths401. The result files identify the resource run separately from this lifecycle regression.

The post-fix real500-stream open/close regression passed with1,364 requests and zero request errors;
the log contained no ERROR or AuthorizationDeniedException entries. All192 non-Docker backend
regression tests passed, including the R2 privacy guard and initial/async security-chain cases.

To reproduce the Windows resource fixture, compile tests first, build/copy the standalone web assets,
set the local tool paths listed in `scripts/Run-LocalStackFixture.ps1`, then invoke:

```powershell
.\scripts\Invoke-BoundedLocalTest.ps1 -CommandScript .\scripts\Run-LocalStackFixture.ps1 -MemoryMiB 2048 -CpuEquivalent 2
```

`LOCAL_TEST_BURST=true` selects the separate burst scenario; otherwise arrivals ramp gradually.
`LOCAL_TEST_SECONDS` defaults to180. Read the output files and exit code; a configured target is not
a passed result. The fixture uses loopback HTTP/1.1, disposable data, no email/bucket credentials and
a ten-minute JUnit timeout. The job cap changes only the test process and its descendants.
