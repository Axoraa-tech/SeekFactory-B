// Step 2: download every legacy media file (product images/videos, supplier avatars, blog images)
// into the backend's local media folder (SeekFactory-B/uploads) under keys the backend accepts.
// Images are normalised like ImageCompressor does on upload (max 2048px); avif becomes webp.
// Re-runnable: files already on disk are skipped. Writes data/media-map.json.
import { existsSync, readFileSync } from "node:fs";
import { mkdir, writeFile, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import sharp from "sharp";
import { LEGACY_ORIGIN, load, legacyPath, mediaKey, targetExt, dataFile } from "./common.mjs";

const UPLOADS = fileURLToPath(new URL("../../uploads/", import.meta.url));
const MAX_DIM = 2048;
const CONCURRENCY = 8;

const refs = new Set();
const add = (ref) => { const p = legacyPath(ref); if (p) refs.add(p); };
for (const p of load("products")) { p.images.forEach(add); add(p.video); }
for (const s of load("suppliers")) add(s.avatar);
for (const post of load("posts")) {
  add(post.image);
  for (const m of (post.content || "").matchAll(/(?:https?:\/\/[^"'\s)]+)?\/uploads\/[A-Za-z0-9._-]+/g)) add(m[0]);
}

const mapFile = dataFile("media-map.json");
const map = existsSync(mapFile) ? JSON.parse(readFileSync(mapFile, "utf8")) : {};
await mkdir(UPLOADS, { recursive: true });

async function transfer(path) {
  const ext = targetExt(path);
  const key = mediaKey(path, ext);
  const target = UPLOADS + key;
  if (existsSync(target)) return { key, ok: true, bytes: (await stat(target)).size };

  let buf;
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetch(`${LEGACY_ORIGIN}/api${path}`, { signal: AbortSignal.timeout(180_000) });
      if (res.status === 404) return { key, ok: false, error: "404 on legacy server" };
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      buf = Buffer.from(await res.arrayBuffer());
      break;
    } catch (e) {
      if (attempt >= 4) return { key, ok: false, error: e.message };
    }
  }

  if (["png", "jpg", "webp"].includes(ext)) {
    try {
      let img = sharp(buf, { failOn: "none" }).rotate().resize(MAX_DIM, MAX_DIM, { fit: "inside", withoutEnlargement: true });
      img = ext === "png" ? img.png({ compressionLevel: 9 }) : ext === "jpg" ? img.jpeg({ quality: 85, mozjpeg: true }) : img.webp({ quality: 85 });
      const out = await img.toBuffer();
      // keep the original when re-encoding would make it bigger (already-optimised files)
      if (out.length < buf.length || path.toLowerCase().endsWith(".avif")) buf = out;
    } catch (e) {
      return { key, ok: false, error: "unreadable image: " + e.message };
    }
  }
  await writeFile(target, buf);
  return { key, ok: true, bytes: buf.length };
}

const queue = [...refs];
let done = 0;
await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
  while (queue.length) {
    const path = queue.shift();
    map[path] = await transfer(path);
    if (++done % 100 === 0) {
      console.log(`${done}/${refs.size}`);
      await writeFile(mapFile, JSON.stringify(map, null, 1));
    }
  }
}));
await writeFile(mapFile, JSON.stringify(map, null, 1));

const failed = Object.entries(map).filter(([, v]) => !v.ok);
const mb = Object.values(map).reduce((a, v) => a + (v.bytes || 0), 0) / 1048576;
console.log(`media: ${refs.size} referenced, ${refs.size - failed.length} stored (${mb.toFixed(0)} MB), ${failed.length} failed`);
failed.forEach(([p, v]) => console.log(`  FAILED ${p}: ${v.error}`));
