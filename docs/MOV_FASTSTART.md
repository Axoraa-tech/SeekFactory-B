# Existing MOV playback startup

The production homepage inspected on 18 November 2026 contained eight MOV seek URLs before its MP4 entries. The optional `VideoOptimizer` previously matched only MP4 keys, so existing MOV gallery videos could never receive its fast-start optimization.

This change includes MOV keys and moves their movie index ahead of media data using a lossless QuickTime remux. It preserves the key, MOV container, audio/video streams and quality. MP4 compression behavior is unchanged. MOV bitrate/codec conversion is deliberately out of scope; fast-start alone cannot cure a slow connection or an unsupported codec.

## Operational boundary

The job remains OFF by default. This PR does not enable it, deploy, modify production media, or change the database. Merging the code alone does not optimize existing files. Before a separately approved maintenance run, verify a restorable blob backup, adequate temporary storage, ffmpeg availability and the exact affected files. Run only with the existing opt-in `APP_MEDIA_OPTIMIZE_VIDEOS=true`, then disable it after completion. The existing optimizer replaces media at the same keys, so browser/CDN copies already cached may persist.

## Verification

Static review and `git diff --check` only. No build, tests, manual browser QA or CI manually invoked, respecting the owner's instruction. The added key regression tests are NOT RUN. Before enabling the job, verify a sample MOV retains its streams and QuickTime container with `moov` before `mdat`, and measure playback on the affected device/network.

The reported 30-second delay was not reproduced: read-only media requests from the investigation environment timed out at 20 seconds. This is not proof of an Azure outage. The homepage was accessible; blob redirects, byte ranges, codec compatibility and actual device time-to-first-frame remain unverified.
