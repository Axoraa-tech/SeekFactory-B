// Step 4: copy the migrated media (SeekFactory-B/uploads) into the production bucket, exactly the way
// S3MediaStorageService stores uploads: object key "media/<key>", same content type, immutable caching.
// Skips objects already in the bucket, so it is safe to re-run.
//
// Credentials come from the environment (never hard-coded):
//   B2_KEY_ID, B2_APP_KEY                      Backblaze application key (needs write access)
//   B2_BUCKET   (default seekfactory-media-seeks)
//   B2_ENDPOINT (default https://s3.eu-central-003.backblazeb2.com)
//   B2_REGION   (default eu-central-003)
// Usage: node upload-b2.mjs [--dry-run]
import { readdirSync, statSync, createReadStream } from "node:fs";
import { fileURLToPath } from "node:url";
import { S3Client, PutObjectCommand, HeadObjectCommand } from "@aws-sdk/client-s3";

const UPLOADS = fileURLToPath(new URL("../../uploads/", import.meta.url));
const BUCKET = process.env.B2_BUCKET || "seekfactory-media-seeks";
const KEY_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg|webp|gif|mp4|webm|mov|pdf)$/;
const TYPES = { png: "image/png", jpg: "image/jpeg", webp: "image/webp", gif: "image/gif", mp4: "video/mp4",
  webm: "video/webm", mov: "video/quicktime", pdf: "application/pdf" };
const dryRun = process.argv.includes("--dry-run");

if (!process.env.B2_KEY_ID || !process.env.B2_APP_KEY) {
  console.error("Set B2_KEY_ID and B2_APP_KEY first.");
  process.exit(1);
}
const s3 = new S3Client({
  endpoint: process.env.B2_ENDPOINT || "https://s3.eu-central-003.backblazeb2.com",
  region: process.env.B2_REGION || "eu-central-003",
  credentials: { accessKeyId: process.env.B2_KEY_ID, secretAccessKey: process.env.B2_APP_KEY },
});

const files = readdirSync(UPLOADS).filter((f) => KEY_PATTERN.test(f));
let uploaded = 0, skipped = 0, bytes = 0;
const failed = [];

async function exists(Key) {
  try { await s3.send(new HeadObjectCommand({ Bucket: BUCKET, Key })); return true; }
  catch (e) { if (e.$metadata?.httpStatusCode === 404) return false; throw e; }
}

const queue = [...files];
await Promise.all(Array.from({ length: 8 }, async () => {
  while (queue.length) {
    const name = queue.shift();
    const Key = `media/${name}`;
    try {
      // HEAD is a Class B transaction on B2 (2,500/day free); only check when explicitly asked
      if (process.argv.includes("--skip-existing") && await exists(Key)) { skipped++; continue; }
      const size = statSync(UPLOADS + name).size;
      if (!dryRun) {
        await s3.send(new PutObjectCommand({
          Bucket: BUCKET, Key, Body: createReadStream(UPLOADS + name), ContentLength: size,
          ContentType: TYPES[name.split(".").pop()], CacheControl: "public, max-age=31536000, immutable",
        }));
      }
      uploaded++; bytes += size;
      if ((uploaded + skipped) % 100 === 0) console.log(`${uploaded + skipped}/${files.length}`);
    } catch (e) {
      failed.push(`${name}: ${e.name} ${e.message}`);
    }
  }
}));

console.log(`${dryRun ? "[dry run] would upload" : "uploaded"} ${uploaded} (${(bytes / 1048576).toFixed(0)} MB), ` +
  `already in bucket ${skipped}, failed ${failed.length} of ${files.length}`);
failed.slice(0, 20).forEach((f) => console.log("  FAILED " + f));
if (failed.length) process.exitCode = 1;
