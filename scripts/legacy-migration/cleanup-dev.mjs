// Removes demo/test content so only the migrated legacy catalog remains (DEV database).
//   - every product and reel that did not come from the legacy migration (lgp-/lgr-)
//   - V5 demo seed accounts and automated test accounts (their manufacturers cascade)
//   - the V5 fake root categories (cat-cnc, cat-diecasting, ...) and their children
// Admin accounts and real team accounts are kept. Every affected row is backed up to
// data/cleanup-backup-<timestamp>.json first. Usage: node cleanup-dev.mjs [--commit]
import { writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dataFile } from "./common.mjs";

const connect = createRequire(import.meta.url)("./db.cjs");
const commit = process.argv.includes("--commit");

const TEST_USERS = `
  (id IN ('usr-supp-01','usr-supp-02','usr-supp-03','usr-supp-04','usr-buyer-01','usr-buyer-guest')
   OR email ~* '^(e2e\\.|first\\.).*@example\\.com$'
   OR email IN ('admin@demo.com','vendor@demo.com','test@gmail.com','qwer@gmail.com','qwerty@gmail.com')
   OR (email IS NULL AND name ~ '^User [0-9]{4}$'))
  AND role <> 'ROLE_ADMIN' AND id NOT LIKE 'lgu-%'`;
const FAKE_CATS = `('cat-cnc','cat-diecasting','cat-optics','cat-automation','cat-moulds','cat-sheetmetal')`;

const c = await connect("DEV");
const rows = async (sql) => (await c.query(sql)).rows;

const backup = {
  users: await rows(`SELECT * FROM users WHERE ${TEST_USERS}`),
  manufacturers: await rows(`SELECT * FROM manufacturers WHERE user_id IN (SELECT id FROM users WHERE ${TEST_USERS})`),
  products: await rows(`SELECT * FROM products WHERE id NOT LIKE 'lgp-%'`),
  product_images: await rows(`SELECT * FROM product_images WHERE product_id NOT LIKE 'lgp-%'`),
  reels: await rows(`SELECT * FROM reels WHERE id NOT LIKE 'lgr-%'`),
  categories: await rows(`SELECT * FROM categories WHERE id IN ${FAKE_CATS} OR parent_id IN ${FAKE_CATS}`),
};
const file = dataFile(`cleanup-backup-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
writeFileSync(file, JSON.stringify(backup, null, 1));
console.log("backed up:", Object.fromEntries(Object.entries(backup).map(([k, v]) => [k, v.length])), "->", file.pathname);

try {
  await c.query("BEGIN");
  const del = async (label, sql) => console.log(label, (await c.query(sql)).rowCount);
  await del("reels deleted:", `DELETE FROM reels WHERE id NOT LIKE 'lgr-%'`);
  await del("products deleted:", `DELETE FROM products WHERE id NOT LIKE 'lgp-%'`);
  await del("test users deleted:", `DELETE FROM users WHERE ${TEST_USERS}`);
  await del("orphan manufacturers deleted:",
    `DELETE FROM manufacturers m WHERE m.id NOT LIKE 'lgm-%'
       AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = m.user_id)`);
  await del("fake subcategories deleted:", `DELETE FROM categories WHERE parent_id IN ${FAKE_CATS}`);
  await del("fake categories deleted:", `DELETE FROM categories WHERE id IN ${FAKE_CATS}`);
  console.log((await rows(`SELECT
    (SELECT count(*) FROM products) products, (SELECT count(*) FROM products WHERE id NOT LIKE 'lgp-%') non_legacy_products,
    (SELECT count(*) FROM reels) reels, (SELECT count(*) FROM manufacturers) manufacturers,
    (SELECT count(*) FROM users) users, (SELECT count(*) FROM categories) categories`))[0]);
  await c.query(commit ? "COMMIT" : "ROLLBACK");
  console.log(commit ? "COMMITTED" : "dry run - rolled back (pass --commit to apply)");
} catch (e) {
  await c.query("ROLLBACK");
  console.error("FAILED, rolled back:", e.message, e.detail || "");
  process.exitCode = 1;
} finally {
  await c.end();
}
