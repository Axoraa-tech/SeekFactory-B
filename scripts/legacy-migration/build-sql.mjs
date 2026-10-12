// Step 3: generate the Flyway migration that loads the legacy catalog into the new schema.
//   suppliers -> users (ROLE_SUPPLIER, no password: they sign in via "forgot password" or Google)
//              + manufacturers (+ manufacturer_categories)
//   products  -> products + product_images (gallery), category mapped onto the V6 taxonomy,
//                prices converted to INR with the platform's exchange_rates setting
//   product videos -> reels linked to their product (reel_products)
//   blog posts -> blog_posts (new table)
// Every id is derived from the legacy Mongo _id, so the migration is idempotent and re-runnable.
import { readFileSync, writeFileSync } from "node:fs";
import { load, legacyPath, dataFile } from "./common.mjs";
import { mapCategory } from "./category-map.mjs";
import { parsePrice, moqText } from "./price.mjs";

const OUT = new URL("../../src/main/resources/db/migration/V19__migrate_legacy_catalog.sql", import.meta.url);
const media = JSON.parse(readFileSync(dataFile("media-map.json"), "utf8"));

const products = load("products");
const suppliers = load("suppliers");
const posts = load("posts");

// ---------- helpers ----------
const q = (v) => {
  if (v === null || v === undefined) return "NULL";
  if (typeof v === "number") return Number.isFinite(v) ? String(v) : "NULL";
  if (typeof v === "boolean") return v ? "TRUE" : "FALSE";
  return "'" + String(v).replace(/\u0000/g, "").replace(/'/g, "''") + "'";
};
const jsonb = (v) => q(JSON.stringify(v)) + "::jsonb";
const ts = (v) => (v ? q(new Date(v).toISOString()) + "::timestamptz" : "NOW()");
const cut = (s, n) => (s && s.length > n ? s.slice(0, n - 1).trimEnd() + "…" : s);
const clean = (s) => (typeof s === "string" ? s.replace(/\r\n/g, "\n").trim() || null : null);
const slugify = (s, id) =>
  (String(s || "item").toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 100) || "item") +
  "-" + id.slice(-6);
/** legacy media ref -> new "/api/v1/media/<key>" URL, or null if it never made it across */
const mediaUrl = (ref) => {
  const p = legacyPath(ref);
  return p && media[p]?.ok ? `/api/v1/media/${media[p].key}` : null;
};

const DIAL_CODES = { "+86": "China", "+91": "India", "+852": "Hong Kong", "+886": "Taiwan", "+81": "Japan", "+82": "South Korea",
  "+49": "Germany", "+39": "Italy", "+1": "United States", "+44": "United Kingdom", "+90": "Turkey", "+971": "United Arab Emirates",
  "+65": "Singapore", "+60": "Malaysia", "+66": "Thailand", "+84": "Vietnam", "+62": "Indonesia", "+7": "Russia" };
const countryFromPhone = (phone) => {
  const p = String(phone || "").replace(/[^\d+]/g, "");
  const code = Object.keys(DIAL_CODES).sort((a, b) => b.length - a.length).find((c) => p.startsWith(c));
  return code ? DIAL_CODES[code] : null;
};
const parseAddress = (a) => {
  try { return typeof a === "string" ? JSON.parse(a) : a || {}; } catch { return {}; }
};

const lines = [];
const out = (s) => lines.push(s);
const stats = {};

// ---------- suppliers ----------
const phoneCount = {};
suppliers.forEach((s) => { if (s.phone) phoneCount[s.phone] = (phoneCount[s.phone] || 0) + 1; });

const supplierRows = suppliers.map((s) => {
  const addr = parseAddress(s.address);
  const country = clean(addr.country) || countryFromPhone(s.phone) || "China";
  const location = [addr.city, addr.state].map(clean).filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(", ") || country;
  const company = clean(s.companyName) || clean(s.name) || s.email.split("@")[0];
  return [
    q(s._id), q(s.email.trim().toLowerCase()), q(phoneCount[s.phone] === 1 ? s.phone.trim() : null),
    q(cut(clean(s.name) || company, 255)), q(cut(company, 255)), q(mediaUrl(s.avatar)),
    q(cut(country, 100)), q(cut(location, 255)), q(!!s.isVerified), q(clean(s.businessType)),
    q(slugify(company, s._id)), ts(s.createdAt),
  ].join(", ");
});
stats.suppliers = supplierRows.length;

// ---------- products ----------
const productRows = [];
const imageRows = [];
const tierRows = [];
const reelRows = [];
const catUse = {};
for (const p of products) {
  const gallery = p.images.map(mediaUrl).filter(Boolean).slice(0, 8);
  if (!gallery.length) { (stats.skippedNoImage ??= []).push(p._id); continue; }
  const id = `lgp-${p._id}`;
  const price = parsePrice(p.priceRange);
  const moqN = Number(p.minOrderQuantity) > 0 ? Number(p.minOrderQuantity) : price.firstQty || 1;
  const categoryId = mapCategory(p.category, p.subcategory);
  catUse[categoryId] = (catUse[categoryId] || 0) + 1;

  const specs = {};
  if (clean(p.countryOfOrigin)) specs["Country of Origin"] = clean(p.countryOfOrigin);
  if (p.certifications?.length) specs["Certifications"] = p.certifications.join(", ");
  if (p.tags?.length) specs["Tags"] = p.tags.join(", ");

  productRows.push([
    q(id), q(slugify(p.name, p._id)), q(p.supplier?._id), q(categoryId), q(cut(clean(p.name), 255)),
    q(gallery[0]), q(clean(p.description) || clean(p.sort_description)), q(price.amount), q(price.currency),
    q(price.unit), q(cut(moqText(moqN, price.unit), 100)), jsonb(specs), q(p.isActive !== false),
    ts(p.createdAt), ts(p.updatedAt),
  ].join(", "));
  gallery.forEach((url, i) => imageRows.push(`${q(id)}, ${i}, ${q(url)}`));
  price.tiers.forEach((t) => tierRows.push(`${q(id)}, ${t.minQty}, ${t.amount}`));

  const video = mediaUrl(p.video);
  if (video) reelRows.push([q(`lgr-${p._id}`), q(id), q(cut(clean(p.name), 255)),
    q(cut(clean(p.sort_description) || clean(p.description), 1000)), q(gallery[0]), q(video), ts(p.createdAt)].join(", "));
}
stats.products = productRows.length;
stats.images = imageRows.length;
stats.priceTiers = tierRows.length;
stats.reels = reelRows.length;

// ---------- blog posts ----------
const rewriteMedia = (html) =>
  (html || "").replace(/(?:https?:\/\/[^"'\s)]+)?\/uploads\/[A-Za-z0-9._-]+/g, (m) => mediaUrl(m) ?? m);
const seenSlugs = new Set();
const postRows = posts.map((b) => {
  let slug = cut(b.slug || slugify(b.title, b._id), 240);
  if (seenSlugs.has(slug)) slug += "-" + b._id.slice(-6);
  seenSlugs.add(slug);
  const text = (b.content || "").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();
  return [
    q(`lgb-${b._id}`), q(slug), q(cut(clean(b.title), 255)),
    q(rewriteMedia(b.content)), q(cut(clean(b.metaDescription) || text, 300)),
    q(mediaUrl(b.image) || (b.image && !legacyPath(b.image) ? b.image : null)), q(clean(b.author) || "SeekFactory"),
    jsonb(b.tags || []), q(cut(clean(b.metaTitle), 255)), q(clean(b.metaDescription)), jsonb(b.metaKeywords || []),
    q(!!b.published), ts(b.createdAt), ts(b.updatedAt),
  ].join(", ");
});
stats.posts = postRows.length;

const values = (rows) => rows.map((r) => `  (${r})`).join(",\n");

// ---------- SQL ----------
out(`-- V19__migrate_legacy_catalog.sql
-- GENERATED by scripts/legacy-migration/build-sql.mjs from a snapshot of the legacy
-- (Azure/Mongo) SeekFactory site taken ${new Date().toISOString().slice(0, 10)}. Do not edit by hand: re-run the script.
--
-- Loads: ${stats.suppliers} suppliers (users + manufacturers), ${stats.products} products, ${stats.images} gallery images,
--        ${stats.priceTiers} price tiers, ${stats.reels} product-video reels, ${stats.posts} blog posts.
-- Media files referenced as /api/v1/media/<key> are produced by scripts/legacy-migration/media.mjs
-- into ./uploads (copy to the server, or set APP_MEDIA_MIGRATE_FROM_DIR to push them to the S3/OSS bucket).
--
-- Legacy rows use id prefixes lgu- (users), lgm- (manufacturers), lgp- (products), lgr- (reels), lgb- (blog posts).
-- Idempotent: re-running updates the same rows instead of duplicating them.
-- Rollback: DELETE FROM products WHERE id LIKE 'lgp-%'; DELETE FROM reels WHERE id LIKE 'lgr-%';
--           DELETE FROM manufacturers WHERE id LIKE 'lgm-%'; DELETE FROM users WHERE id LIKE 'lgu-%'; DROP TABLE blog_posts;
`);

out(`-- 1. Staging -----------------------------------------------------------------
DROP TABLE IF EXISTS pg_temp.stg_supplier;
CREATE TEMP TABLE stg_supplier (
    legacy_id TEXT PRIMARY KEY, email TEXT, phone TEXT, contact_name TEXT, company TEXT, logo_url TEXT,
    country TEXT, location TEXT, verified BOOLEAN, description TEXT, slug TEXT, created_at TIMESTAMPTZ
) ON COMMIT DROP;
INSERT INTO stg_supplier VALUES
${values(supplierRows)};

DROP TABLE IF EXISTS pg_temp.stg_product;
CREATE TEMP TABLE stg_product (
    id TEXT PRIMARY KEY, slug TEXT, supplier_legacy_id TEXT, category_id TEXT, name TEXT, image_url TEXT,
    description TEXT, amount NUMERIC, currency TEXT, unit TEXT, moq TEXT, specs JSONB, is_active BOOLEAN,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) ON COMMIT DROP;
INSERT INTO stg_product VALUES
${values(productRows)};

DROP TABLE IF EXISTS pg_temp.stg_image;
CREATE TEMP TABLE stg_image (product_id TEXT, position INT, image_url TEXT) ON COMMIT DROP;
INSERT INTO stg_image VALUES
${values(imageRows)};

DROP TABLE IF EXISTS pg_temp.stg_tier;
CREATE TEMP TABLE stg_tier (product_id TEXT, min_qty INT, amount NUMERIC) ON COMMIT DROP;
${tierRows.length ? `INSERT INTO stg_tier VALUES\n${values(tierRows)};` : ""}

-- INR per unit of each source currency, from the platform's own exchange_rates (1 INR = rate units)
DROP TABLE IF EXISTS pg_temp.stg_fx;
CREATE TEMP TABLE stg_fx (currency TEXT PRIMARY KEY, inr_per_unit NUMERIC) ON COMMIT DROP;
INSERT INTO stg_fx
SELECT c.currency,
       1 / COALESCE((SELECT NULLIF((value -> 'rates' ->> c.currency), '')::numeric
                     FROM platform_settings WHERE key = 'exchange_rates'), c.fallback_rate)
FROM (VALUES ('INR', 1::numeric), ('USD', 0.01149), ('CNY', 0.0833)) AS c(currency, fallback_rate);
`);

out(`-- 2. Supplier accounts -------------------------------------------------------
-- Reuse an existing account with the same email; otherwise create one without a password.
INSERT INTO users (id, email, phone, role, auth_provider, name, company_name, avatar_url, country,
                   is_active, email_verified, created_at, updated_at)
SELECT 'lgu-' || s.legacy_id, s.email,
       CASE WHEN EXISTS (SELECT 1 FROM users u WHERE u.phone = s.phone AND u.id <> 'lgu-' || s.legacy_id) THEN NULL ELSE s.phone END,
       'ROLE_SUPPLIER', 'LOCAL', s.contact_name, s.company, s.logo_url, s.country, TRUE, FALSE, s.created_at, NOW()
FROM stg_supplier s
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = s.email AND u.id <> 'lgu-' || s.legacy_id)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, company_name = EXCLUDED.company_name,
    avatar_url = COALESCE(users.avatar_url, EXCLUDED.avatar_url), country = EXCLUDED.country, updated_at = NOW();

DROP TABLE IF EXISTS pg_temp.stg_owner;
CREATE TEMP TABLE stg_owner ON COMMIT DROP AS
SELECT s.legacy_id, u.id AS user_id,
       COALESCE((SELECT m.id FROM manufacturers m WHERE m.user_id = u.id), 'lgm-' || s.legacy_id) AS manufacturer_id
FROM stg_supplier s
JOIN users u ON lower(u.email) = s.email;

UPDATE users u SET role = 'ROLE_SUPPLIER'
FROM stg_owner o WHERE u.id = o.user_id AND u.role = 'ROLE_BUYER';

INSERT INTO manufacturers (id, user_id, slug, name, logo_url, country, location, verified, verification_status,
                           description, created_at, updated_at)
SELECT o.manufacturer_id, o.user_id,
       CASE WHEN EXISTS (SELECT 1 FROM manufacturers m WHERE m.slug = s.slug AND m.id <> o.manufacturer_id)
            THEN s.slug || '-lg' ELSE s.slug END,
       s.company, s.logo_url, s.country, s.location, s.verified,
       CASE WHEN s.verified THEN 'APPROVED' ELSE 'PENDING' END, s.description, s.created_at, NOW()
FROM stg_supplier s JOIN stg_owner o USING (legacy_id)
WHERE o.manufacturer_id LIKE 'lgm-%'
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, logo_url = COALESCE(manufacturers.logo_url, EXCLUDED.logo_url),
    country = EXCLUDED.country, location = EXCLUDED.location, verified = EXCLUDED.verified,
    description = COALESCE(manufacturers.description, EXCLUDED.description), updated_at = NOW();
`);

out(`-- 3. Products ----------------------------------------------------------------
INSERT INTO products (id, slug, manufacturer_id, category_id, name, image_url, description, price_inr, unit, moq,
                      specs, is_active, listed, stock_status, price_tiers, created_at, updated_at)
SELECT p.id,
       CASE WHEN EXISTS (SELECT 1 FROM products x WHERE x.slug = p.slug AND x.id <> p.id) THEN p.slug || '-lg' ELSE p.slug END,
       o.manufacturer_id,
       COALESCE((SELECT c.id FROM categories c WHERE c.id = p.category_id), 'cat-other'),
       p.name, p.image_url, p.description,
       ROUND(p.amount * fx.inr_per_unit, 2), p.unit, p.moq, p.specs, p.is_active, TRUE, 'IN_STOCK',
       COALESCE((SELECT jsonb_agg(jsonb_build_object('minQty', t.min_qty, 'priceInr', ROUND(t.amount * fx.inr_per_unit, 2))
                                  ORDER BY t.min_qty)
                 FROM stg_tier t WHERE t.product_id = p.id), '[]'::jsonb),
       p.created_at, p.updated_at
FROM stg_product p
JOIN stg_owner o ON o.legacy_id = p.supplier_legacy_id
JOIN stg_fx fx ON fx.currency = p.currency
ON CONFLICT (id) DO UPDATE SET
    manufacturer_id = EXCLUDED.manufacturer_id, category_id = EXCLUDED.category_id, name = EXCLUDED.name,
    image_url = EXCLUDED.image_url, description = EXCLUDED.description, price_inr = EXCLUDED.price_inr,
    unit = EXCLUDED.unit, moq = EXCLUDED.moq, specs = EXCLUDED.specs, price_tiers = EXCLUDED.price_tiers,
    updated_at = NOW();

DELETE FROM product_images WHERE product_id IN (SELECT id FROM stg_product);
INSERT INTO product_images (product_id, position, image_url)
SELECT i.product_id, i.position, i.image_url
FROM stg_image i JOIN products p ON p.id = i.product_id;

-- Manufacturer <-> root category links, derived from what each supplier lists
INSERT INTO manufacturer_categories (manufacturer_id, category_id)
SELECT DISTINCT p.manufacturer_id, COALESCE(c.parent_id, c.id)
FROM products p JOIN categories c ON c.id = p.category_id
WHERE p.id LIKE 'lgp-%'
ON CONFLICT DO NOTHING;
`);

out(`-- 4. Product videos -> reels -------------------------------------------------
DROP TABLE IF EXISTS pg_temp.stg_reel;
CREATE TEMP TABLE stg_reel (id TEXT PRIMARY KEY, product_id TEXT, title TEXT, description TEXT,
                            poster_url TEXT, video_url TEXT, created_at TIMESTAMPTZ) ON COMMIT DROP;
${reelRows.length ? `INSERT INTO stg_reel VALUES\n${values(reelRows)};` : ""}

INSERT INTO reels (id, manufacturer_id, title, description, poster_url, video_url, created_at, updated_at)
SELECT r.id, p.manufacturer_id, r.title, r.description, r.poster_url, r.video_url, r.created_at, NOW()
FROM stg_reel r JOIN products p ON p.id = r.product_id
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description,
    poster_url = EXCLUDED.poster_url, video_url = EXCLUDED.video_url, updated_at = NOW();

INSERT INTO reel_products (reel_id, product_id)
SELECT r.id, r.product_id FROM stg_reel r JOIN reels x ON x.id = r.id
ON CONFLICT DO NOTHING;
`);

out(`-- 5. Blog posts --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blog_posts (
    id               VARCHAR(64) PRIMARY KEY,
    slug             VARCHAR(255) UNIQUE NOT NULL,
    title            VARCHAR(255) NOT NULL,
    content          TEXT NOT NULL,
    excerpt          VARCHAR(300),
    cover_image_url  TEXT,
    author           VARCHAR(255) NOT NULL DEFAULT 'SeekFactory',
    tags             JSONB NOT NULL DEFAULT '[]'::jsonb,
    meta_title       VARCHAR(255),
    meta_description TEXT,
    meta_keywords    JSONB NOT NULL DEFAULT '[]'::jsonb,
    published        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_blog_posts_published ON blog_posts (published, created_at DESC);

INSERT INTO blog_posts (id, slug, title, content, excerpt, cover_image_url, author, tags, meta_title,
                        meta_description, meta_keywords, published, created_at, updated_at)
VALUES
${values(postRows)}
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, excerpt = EXCLUDED.excerpt,
    cover_image_url = EXCLUDED.cover_image_url, tags = EXCLUDED.tags, meta_title = EXCLUDED.meta_title,
    meta_description = EXCLUDED.meta_description, meta_keywords = EXCLUDED.meta_keywords,
    published = EXCLUDED.published, updated_at = EXCLUDED.updated_at;
`);

out(`-- 6. Retire the V5 demo seed from the storefront (unlisted, not deleted) ------
UPDATE products SET listed = FALSE WHERE id IN ('prod-01', 'prod-02', 'prod-03', 'prod-04', 'prod-05', 'prod-06');
UPDATE reels    SET listed = FALSE WHERE id IN ('reel-01', 'reel-02', 'reel-03', 'reel-04');
`);

writeFileSync(OUT, lines.join("\n"));
writeFileSync(dataFile("category-usage.json"), JSON.stringify(catUse, null, 1));
console.log(JSON.stringify(stats));
console.log(`wrote ${OUT.pathname} (${(lines.join("\n").length / 1048576).toFixed(1)} MB)`);
