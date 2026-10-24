# Public media browser caching

The S3 public media route previously returned `302 Cache-Control: no-cache`.
Without a public base URL, `publicUrl` creates a fresh signed URL per request.
That changes the browser's object cache key even though the UUID media key and
bytes have not changed. Final public object uploads already carry
`public, max-age=31536000, immutable`; pending video originals carry `no-cache`.

Public image/video redirects now use
`max-age=300, must-revalidate, private` at the default ten-minute signature TTL.
For shorter signatures, max-age is limited to TTL minus 30 seconds, floored at
zero. A configured public base URL uses 300 seconds. No object metadata request
is added. Unknown storage implementations default to zero.

Processing videos and document redirects still use `no-cache`. Redirects are
never immutable. Finalization replaces the pending video at the same key, so
the pending response must keep revalidating. Normal replacement uploads use new
UUIDs and therefore new browser cache keys. Local body delivery is unchanged.
The public route is independent of viewer identity; this does not cache API
data, authenticated pages, sessions, private attachments, or payment proofs.
Private signed attachment redirects still check participation before signing
and return `no-store`.

Frontend inspection at dev f9151a7: marketplace images/video load directly;
Next image remotePatterns is empty and the optimizer only handles local brand
assets. API and session `no-store` behavior is unchanged. No frontend PR is
needed. The backend controller does not add ETag or Last-Modified headers to
redirects; storage-origin validators and cache metadata are unchanged.

## Validation and limits

- Spring MVC with the real security filter chain verifies actual redirect/body
  headers, guest/authenticated public access, processing-to-final transition,
  document exclusion, zero lifetime, replacement location and private/session
  rejection with `no-store`. A separate participant regression verifies private
  redirects remain `no-store` and another user is rejected before signing.
- S3 tests verify 600-second TTL -> 300-second browser lifetime, 90 -> 60,
  30/10 -> zero, and no new bucket HEAD/GET requests. Existing original-document
  upload tests remain intact.
- Isolated headless Chrome 155 HTTP fixture (synthetic localhost headers, not a
  live Java/Backblaze measurement): three visits with changing signed targets
  made 3 redirects + 3 image transfers before, versus 1 + 1 after. Processing
  remained 3 + 3. Replacement URLs and expired redirects refreshed; a separate
  browser context fetched independently. No existing browser tabs were used.
- Offline Maven regression: 160 tests passed excluding AxoraaApplicationTests
  (Docker unavailable), VideoTranscoderTest (ffmpeg unavailable), and
  LocalMediaStorageServiceTest#videoServesOriginalWhileProcessingThenTheCompressedFile
  (ffmpeg unavailable). Full attempts exposed these environment limitations.
- Final focused rerun: 27 cache/private/S3 tests passed, including the final
  processing-to-final assertion. Offline `mvn -DskipTests package` passed.
- Live origin Cache-Control/ETag/Last-Modified and real repeat downloads remain
  unverified: the supplied Backblaze daily 1 GB download / 2,500 Class B caps are
  exhausted until 05:30 IST October 25, 2026 (00:00 UTC). No Backblaze requests,
  metadata probes, retries or bulk downloads were made. Render service URL
  discovery was stopped because no workspace is selected; no service settings
  were changed.

After reset and deployment to an authorized dev environment, verify one final
public image and one final video (including Range) in a fresh browser context:
inspect the origin headers, compare first/repeat network transfer counts,
replace with a new UUID and verify fresh bytes, then verify a pending video
revalidates through finalization. Confirm guest/participant/nonparticipant
private attachment behavior. This draft does not merge or deploy the change.

The redirect lifetime reduces churn within five minutes, rather than providing
permanent offline media. Previously uploaded objects with missing cache metadata
may still revalidate. Signed links can stop working sooner if credentials are
revoked or temporary credentials expire; this change does not extend access.
Already cached public bytes cannot be immediately recalled (the existing
immutable object policy already has this property).
