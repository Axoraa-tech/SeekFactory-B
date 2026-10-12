// Step 1: snapshot the legacy (Azure/Mongo) SeekFactory API into data/*.json.
// Read-only: only GET requests against the old public API.
import { mkdir, writeFile } from "node:fs/promises";
import { LEGACY_ORIGIN } from "./common.mjs";

const API = `${LEGACY_ORIGIN}/api`;

const ENDPOINTS = {
  products: "/products",
  categories: "/admin/categories",
  suppliers: "/auth/suppliers",
  posts: "/admin/posts",
  homepage: "/admin/homepage",
};

async function get(path) {
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetch(API + path, { signal: AbortSignal.timeout(120_000) });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (e) {
      if (attempt >= 3) throw new Error(`GET ${path} failed: ${e.message}`);
    }
  }
}

await mkdir(new URL("./data/", import.meta.url), { recursive: true });
for (const [name, path] of Object.entries(ENDPOINTS)) {
  const body = await get(path);
  await writeFile(new URL(`./data/${name}.json`, import.meta.url), JSON.stringify(body, null, 1));
  const rows = Array.isArray(body) ? body : body.data;
  console.log(`${name}: ${Array.isArray(rows) ? rows.length + " rows" : "object"}`);
}
