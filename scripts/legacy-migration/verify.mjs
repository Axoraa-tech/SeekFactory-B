// Runs V19 against a database inside a transaction and ROLLS BACK (default), reporting what it would load.
// Usage: node verify.mjs [DEV|PROD]   (credentials from SeekFactory-B/.env, never printed)
import "dotenv/config";
import { readFileSync } from "node:fs";
import pg from "pg";
import dotenv from "dotenv";

dotenv.config({ path: new URL("../../.env", import.meta.url) });
const env = (process.argv[2] || "DEV").toUpperCase();
const url = new URL(process.env[`${env}_DB_URL`].replace(/^jdbc:/, ""));
const client = new pg.Client({
  host: url.hostname, port: +url.port, database: url.pathname.slice(1),
  user: process.env[`${env}_DB_USERNAME`], password: process.env[`${env}_DB_PASSWORD`],
  ssl: { rejectUnauthorized: false },
});
const sql = readFileSync(new URL("../../src/main/resources/db/migration/V19__migrate_legacy_catalog.sql", import.meta.url), "utf8");

await client.connect();
const one = async (s) => (await client.query(s)).rows[0];
try {
  await client.query("BEGIN");
  const t0 = Date.now();
  await client.query(sql);
  console.log(`V19 executed OK on ${env} in ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  console.log(await one(`SELECT
    (SELECT count(*) FROM users WHERE id LIKE 'lgu-%') new_users,
    (SELECT count(*) FROM manufacturers WHERE id LIKE 'lgm-%') new_manufacturers,
    (SELECT count(*) FROM products WHERE id LIKE 'lgp-%') products,
    (SELECT count(*) FROM product_images WHERE product_id LIKE 'lgp-%') images,
    (SELECT count(*) FROM products WHERE id LIKE 'lgp-%' AND jsonb_array_length(price_tiers) > 0) tiered,
    (SELECT count(*) FROM products WHERE id LIKE 'lgp-%' AND category_id = 'cat-other') in_other,
    (SELECT count(*) FROM reels WHERE id LIKE 'lgr-%') reels,
    (SELECT count(*) FROM blog_posts) blog_posts`));
  console.log(await one(`SELECT name, price_inr, unit, moq, category_id, price_tiers, specs
    FROM products WHERE id = 'lgp-6ac79bad91ca6b010c2bdefd'`));
  console.log("idempotency: re-running...");
  await client.query(sql);
  console.log(await one(`SELECT (SELECT count(*) FROM products WHERE id LIKE 'lgp-%') products,
    (SELECT count(*) FROM product_images WHERE product_id LIKE 'lgp-%') images`));
} finally {
  await client.query(process.argv.includes("--commit") ? "COMMIT" : "ROLLBACK");
  console.log(process.argv.includes("--commit") ? "COMMITTED" : "rolled back - nothing was written");
  await client.end();
}
