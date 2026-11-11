# One optional product video

Products now expose `videoUrl` independently of Seeks/Reels. Product photos (1–8) and
cover-photo behavior are unchanged. No Seek is created or changed when a product is saved.

## Write contract

Use the existing seller upload endpoint `POST /api/v1/factory/media`, multipart
`file` and `kind=video`. Keep the returned `url` and send it as `videoUrl` in the
existing product create or partial-update JSON. Upload limits, accepted formats,
compression, storage redirects, and Range playback are the existing media pipeline.

- Create: omitted, null, or blank `videoUrl` means no video.
- Update: omitted or null preserves the stored video (legacy clients are safe).
- Update: `videoUrl: ""` explicitly removes the video.
- A nonblank string replaces the one existing video. Arrays are not supported.
- URLs are trimmed. Supported references are UUID upload paths under
  `/api/v1/media/` or absolute HTTP(S) URLs, with MP4, WebM, or MOV path extensions.
  Absolute links can include query parameters. Unsafe schemes, malformed URLs,
  credentials in URLs, non-upload relative paths, and non-video extensions are rejected
  with HTTP 400. URL validation does not fetch remote content or establish its existence.

`ProductResponse.videoUrl` is available in seller lists/create/update responses and
buyer catalog/detail/related/feed-embedded products, including cached response copies.
A missing video is null (or omitted where configured to omit null response fields).

## Storage and rollout

Flyway `V31__product_video.sql` adds nullable `products.video_url`. Existing rows stay
null; it does not backfill from Seeks. Deploy the backend/migration before clients
that write the field. Use the canonical upload response URL in clients.

Clearing/replacing a product video unlinks it without deleting the media, since other
content may use it. Deleting a Seek retains its media when a product references the
same URL or canonical media key (including absolute API/CDN aliases), including paused
or soft-deleted products. Encoded uploaded keys are normalized before saving.

## Validation

New unit coverage: `ProductVideoTest`, `ProductVideoRetentionTest`, `ProductVideoMigrationTest`, and the video
mapping case in `ProductLikesMapperTest`. Run with the project's JDK 25 toolchain:

```sh
bash mvnw -B -Dtest=ProductVideoTest,ProductVideoRetentionTest,ProductLikesMapperTest,FactoryServiceImplCatalogTest,ProductVideoMigrationTest test
```

Use local/disposable test databases only for broader integration runs.
