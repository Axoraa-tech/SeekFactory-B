import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";

export const LEGACY_ORIGIN = "https://backend-seekfactory-gag3cyhtfphpcxg0.eastasia-01.azurewebsites.net";

export const dataFile = (name) => new URL(`./data/${name}`, import.meta.url);
export const load = (name) => {
  const body = JSON.parse(readFileSync(dataFile(`${name}.json`), "utf8"));
  return Array.isArray(body) ? body : body.data;
};

/** Normalises any legacy media reference to its "/uploads/<file>" path, or null if it is not legacy media. */
export function legacyPath(ref) {
  if (!ref || typeof ref !== "string") return null;
  const m = ref.match(/\/uploads\/([A-Za-z0-9._-]+)$/);
  return m ? `/uploads/${m[1]}` : null;
}

/**
 * Deterministic media key matching the backend's MediaTypes.KEY_PATTERN (uuid.ext).
 * Same legacy file -> same key on every run, so re-running never duplicates media.
 */
export function mediaKey(path, ext) {
  const h = createHash("sha1").update("seekfactory-legacy:" + path).digest("hex");
  const uuid = `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-${((parseInt(h[16], 16) & 3) | 8).toString(16)}${h.slice(17, 20)}-${h.slice(20, 32)}`;
  return `${uuid}.${ext}`;
}

/** Legacy extension -> extension the new backend accepts. avif is converted to webp. */
export function targetExt(path) {
  const ext = path.split(".").pop().toLowerCase();
  if (ext === "jpeg") return "jpg";
  if (ext === "avif") return "webp";
  return ext;
}
