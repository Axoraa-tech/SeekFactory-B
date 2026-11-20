# Azure Blob: permanent public media links

Public photos and videos are served from permanent addresses instead of a redirect to an expiring signed
link. Every request then needs one host fewer, and browsers (and any CDN later) keep a single copy per
file. Chat and RFQ attachments move to a separate private container and keep their short signed links.

Both settings are off by default; leaving them unset keeps the original behaviour.

| Setting | Value | Effect |
|---|---|---|
| `APP_MEDIA_AZURE_PRIVATE_CONTAINER` | `private` | New attachments go to this container. Attachments stored earlier under `media/private/…` are still read from there. |
| `APP_MEDIA_AZURE_PUBLIC_BASE_URL` | `https://<account>.blob.core.windows.net/media` | `/api/v1/media/<key>` redirects to `<base>/media/<key>` (and copies to `<base>/media/variants/<name>`), cached publicly for a day. |

The website uses the same address in `NEXT_PUBLIC_MEDIA_BASE_URL`, so photos and videos load directly.

## Order (each step keeps the site working)

1. **Create the private container.** Storage account → Containers → `+ Container`, name `private`,
   anonymous access **Private**.
2. **Deploy this code**, then set `APP_MEDIA_AZURE_PRIVATE_CONTAINER=private` and restart. Startup logs
   `attachments in private`. From now on new attachments are written there.
3. **Copy the existing attachments** into the private container (keeps the originals until checked):
   ```
   azcopy copy "https://<account>.blob.core.windows.net/media/private/*?<SAS>" "https://<account>.blob.core.windows.net/private/?<SAS>" --recursive
   ```
   Then open a few old chat attachments in the app. Only after that, delete `media/private/` so the public
   container holds no private files.
4. **Allow anonymous reads of public files.** Storage account → Settings → Configuration →
   *Allow Blob anonymous access* = Enabled. Container `media` → *Change access level* → **Blob (anonymous read
   access for blobs only)** — files can be read by exact name, the container cannot be listed.
5. **CORS** (needed for adaptive streaming in the browser). Storage account → Resource sharing (CORS) →
   Blob service: allowed origins `https://seekfactory.com,https://www.seekfactory.com` (plus the
   azurewebsites web host), methods `GET,HEAD,OPTIONS`, allowed headers `*`, exposed headers
   `Content-Length,Content-Range,Accept-Ranges`, max age `86400`.
6. Set `APP_MEDIA_AZURE_PUBLIC_BASE_URL=https://<account>.blob.core.windows.net/media` and restart. Check:
   `curl -I https://<api>/api/v1/media/<key>` → `302`, `Location` without `sig=`, `Cache-Control: max-age=86400, public`.
7. Website: set `NEXT_PUBLIC_MEDIA_BASE_URL` to the same value and redeploy the web app.

## Rollback

Unset `APP_MEDIA_AZURE_PUBLIC_BASE_URL` (signed links again) and `NEXT_PUBLIC_MEDIA_BASE_URL`, redeploy.
Container access can go back to *Private* afterwards.

## CDN later

Put a CDN in front of the `media` container and set both base URLs to its address (for example
`https://media.seekfactory.com`). Setting them back to the Blob address turns it off.
