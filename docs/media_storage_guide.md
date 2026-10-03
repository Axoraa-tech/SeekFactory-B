# Media storage: Alibaba OSS + CDN

By default, uploads (product photos, seek videos, datasheets, chat attachments) are saved on the
backend server's disk in `uploads/`. To keep the server stateless and serve media from a CDN, the
backend can store everything in an **S3-compatible bucket** instead. Production target: **Alibaba
Cloud OSS, Singapore (ap-southeast-1), behind Alibaba Cloud CDN**. AWS S3, Tencent COS and
Cloudflare R2 use the same settings with a different endpoint.

The switch is configuration only:

| | Local (default) | Bucket |
|---|---|---|
| Setting | `APP_MEDIA_STORAGE=local` (or unset) | `APP_MEDIA_STORAGE=s3` |
| Public photos / videos | served by the API | loaded straight from the CDN |
| Chat attachments | streamed by the API | API checks the user, then redirects to a signed link (10 min) |
| Stored in the DB | `/api/v1/media/<key>` | the same, so **no database migration** |

## How it works

- **Object keys.** Public files are `media/<uuid>.<ext>`, private chat files `private/<conversationId>/<uuid>.<ext>`.
  Names are random, so public URLs cannot be guessed from anything else.
- **The bucket stays private.** The CDN reads it through private-bucket origin access. Chat files are
  never on the CDN; they are only reachable with a short-lived signed link handed out after the
  participant check.
- **Photos** are compressed before upload, and cached for a year (`immutable`).
- **Videos** are uploaded as-is first (`no-cache`), so the seek works immediately. ffmpeg then
  compresses a local copy in `APP_MEDIA_S3_WORK_DIR` and replaces the object with the smaller MP4
  (`immutable`). Unfinished work is resumed after a restart.
- **Deleting a seek** removes its video from the bucket, unless another seek uses the same file.
  Thumbnails are kept, since they are often product photos.
- **Old links keep working.** `GET /api/v1/media/<key>` answers with a redirect to the CDN.

## 1. Create the bucket (OSS console)

1. **Region:** Singapore (ap-southeast-1).
2. **Name:** e.g. `seekfactory-media`. **Storage class:** Standard. **ACL:** **Private**.
3. Optional: turn on server-side encryption (OSS-managed keys) and versioning.
4. No CORS rule is needed: browsers load media through plain `<img>` / `<video>` tags and redirects.
   (Browser-direct uploads, a possible later step, would need one.)

## 2. Create a RAM user with least privilege

1. RAM console: create a user for **programmatic access only** and save its AccessKey ID and secret.
2. Attach a custom policy limited to this bucket:

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:GetObject", "oss:DeleteObject"],
      "Resource": ["acs:oss:*:*:seekfactory-media/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["oss:GetBucketInfo", "oss:ListObjects"],
      "Resource": ["acs:oss:*:*:seekfactory-media"]
    }
  ]
}
```

Never use the root account's keys. Rotate the key if it is ever exposed.

## 3. Put the CDN in front (Alibaba Cloud CDN)

1. **Add a domain:** e.g. `media.seekfactory.com`.
   - **Acceleration region:** with *Outside mainland China* or *Global (excluding mainland)*, no ICP filing
     is needed.
   - Accelerating inside mainland China (for suppliers there) **requires an ICP filing** for the domain.
2. **Origin:** the OSS bucket. Turn on **private bucket origin access** (阿里云OSS私有Bucket回源) so
   the CDN can read the private bucket.
3. **HTTPS:** upload a certificate or use a free one. Turn on *Force HTTPS* and HTTP/2.
4. **Caching:**
   - Let **the origin's Cache-Control take priority**, so videos marked `no-cache` while compressing are not cached.
   - Turn on **range origin fetch** (Range回源) so video seeking does not pull whole files.
5. **DNS:** add the CNAME that the CDN console gives you.

## 4. Configure the backend

Add these to the backend's environment (see `.env.example`):

```bash
APP_MEDIA_STORAGE=s3
APP_MEDIA_S3_ENDPOINT=https://s3.oss-ap-southeast-1.aliyuncs.com
APP_MEDIA_S3_REGION=aws-global
APP_MEDIA_S3_BUCKET=seekfactory-media
APP_MEDIA_S3_ACCESS_KEY=<RAM AccessKey ID>
APP_MEDIA_S3_SECRET_KEY=<RAM AccessKey secret>
APP_MEDIA_S3_PATH_STYLE=false
APP_MEDIA_S3_PUBLIC_BASE_URL=https://media.seekfactory.com
# optional
APP_MEDIA_S3_SIGNED_URL_TTL=10m
APP_MEDIA_S3_WORK_DIR=/var/lib/seekfactory/media-work
```

- The server **refuses to start** if the bucket is unreachable with these settings, so mistakes
  show up at deploy time and not at the first upload.
- Keep `APP_MEDIA_FFMPEG_PATH` set as today; video compression still runs on the backend.
- If more than one backend instance runs, give each one a persistent work directory: a video is
  compressed by the instance that received it.

## 5. Configure the frontend

Set this before building:

```bash
NEXT_PUBLIC_MEDIA_BASE_URL=https://media.seekfactory.com
```

It is a build-time variable, so **rebuild and redeploy** after changing it. Without it, media still
works: it loads through the API, which redirects to the bucket.

## 6. Move existing files (one time)

1. Set `APP_MEDIA_MIGRATE_FROM_DIR=uploads` (the current local folder) and start the backend.
2. Wait for the log line: `Media migration finished: N uploaded, N already in the bucket, 0 failed`.
3. Remove the setting.

The migration is safe to re-run, because it skips files already in the bucket. Local files are not
deleted; remove `uploads/` yourself once everything checks out.

## 7. Check it works

- [ ] Upload a product photo: it appears under `media/` in the bucket, and the page loads it from
      `https://media.seekfactory.com/media/...`.
- [ ] Upload a seek video: it plays immediately. A few minutes later the object is smaller and its
      Cache-Control is `immutable`.
- [ ] Send a PDF in chat: opening it redirects to an `...aliyuncs.com/private/...?...Signature...`
      link that stops working after 10 minutes.
- [ ] Opening `https://<bucket>.oss-ap-southeast-1.aliyuncs.com/private/...` without a signature returns 403.
- [ ] Delete a seek: its video disappears from `media/`.

## Rolling back

Set `APP_MEDIA_STORAGE=local` (or remove it) and unset `NEXT_PUBLIC_MEDIA_BASE_URL`. Files uploaded
while the bucket was active exist only in the bucket, so copy them back into `uploads/` first
(e.g. with `ossutil cp -r oss://seekfactory-media/media/ uploads/`).

## Local testing

Any S3-compatible test server works, for example `moto_server` (Python: `pip install "moto[server]"`).
Set `APP_MEDIA_S3_ENDPOINT=http://127.0.0.1:9000`, `APP_MEDIA_S3_PATH_STYLE=true`, any keys, and
create the bucket first. MinIO's free community builds are no longer distributed.
