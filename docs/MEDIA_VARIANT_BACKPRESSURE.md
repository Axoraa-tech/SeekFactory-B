# Bounded thumbnail-generation waiting

## Fix and request behavior

`MediaVariantService` permits two simultaneous image decodes per application instance.
Previously every additional cold-thumbnail request could wait indefinitely for a
permit. Requests now wait at most 250 ms **for the permit**, then use the existing
original-file fallback. This is not a 250 ms end-to-end timeout: storage lookups,
reads and image encoding still take their own time. Existing stored variants are
looked up before waiting, and cached variants avoid that lookup entirely.

The service now also validates the UUID/photo key and the 160/640/1280 width
allowlist internally, rather than trusting only the controller. A failure in the
initial variant-storage lookup now participates in the same five-minute fallback
backoff as later read/write failures. After waiting, requests recheck positive and
negative in-memory answers so an earlier completed request can avoid redundant work.
Interruption preserves the thread interrupt flag and only acquired permits are
released. The two-decode limit and the existing bounded result/failure maps remain.

## Bandwidth and cache trade-off

This protects request availability during thumbnail saturation, not image byte
count. A timed-out cold request can fetch a larger original. A transient timeout
is not memoized as a permanent missing thumbnail; later requests can generate it.
The controller's existing redirect cache can retain that original fallback for its
normal lifetime. Pre-warming variants remains preferable for predictable small
transfers. No API/session/private attachment caching was widened, no signed-link
TTL changed, and no media was made public by this patch.

## Delivery findings and remaining architecture work

A bounded live sampling on 14 November 2026 found the public API hostname in Azure
East Asia redirecting media to Azure Blob Storage. Sample video Range requests
returned 206. This is progressive MP4 playback, **not adaptive bitrate streaming**.
The samples did not expose a CDN media hostname; the Blob storage region and China
last-mile timings were not established. Do not describe the issue as a verified
US-East-only deployment or claim measured improvements in China.

Adaptive playback needs a rendition pipeline (multiple bitrate/resolution files),
a segmented manifest such as HLS, player bandwidth adaptation, and safe backfill
of existing videos. That is not implemented here. CDN/origin geography should be
benchmarked from representative China mobile networks before choosing a provider.
Generic rate limiting does not remove network latency; this patch applies narrow
backpressure to thumbnail generation, not an arbitrary public-media request limit.

## Validation

Nine MediaVariantService unit tests passed against the actual service, image
compressor and storage interface, including saturation, interruption, invalid
width/key calls, lookup-error backoff, existing variant reuse and smaller output.
They were compiled/run in an isolated Java 21 harness with real JUnit/Mockito and
Spring dependencies; the repository's declared production toolchain is Java 25.
The full Maven/Java 25 application build, HTTP integration and China-network
benchmarks were not run here. Before merge, run with the supported JDK:

```sh
bash mvnw -B -Dtest=MediaVariantServiceTest test
```

This change does not deploy, migrate product/category data, modify storage or
install a CDN. It does not constitute a full application security audit.
