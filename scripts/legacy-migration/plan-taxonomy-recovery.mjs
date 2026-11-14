// Offline review planner only. Never connects to a database or executes SQL.
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { CATEGORY_MAP } from './category-map.mjs';

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const pairKey = (a, b) => JSON.stringify([a, b]);
const nonempty = x => typeof x === 'string' && x.trim().length > 0;
const rows = value => Array.isArray(value) ? value : value?.data;
function unique(items, key, label) {
  const result = new Map();
  for (const item of items) {
    const k = key(item);
    if (!k || result.has(k)) throw new Error(`Invalid or duplicate ${label}: ${k}`);
    result.set(k, item);
  }
  return result;
}

/**
 * taxonomy is an explicitly normalized, source-verified list of old pairs, NOT
 * category-map.mjs. parents is an explicit approved old-root -> retained-root map.
 * Snapshots must be complete and captured before edits; hashes bind the report.
 */
export function planRecovery({ taxonomy, sourceProducts, target, parents }) {
  if (!Array.isArray(taxonomy) || !taxonomy.length || !Array.isArray(parents)) throw new Error('Missing source taxonomy or explicit parent mapping');
  const source = rows(sourceProducts);
  if (!Array.isArray(source) || !source.length || !Array.isArray(target?.categories) || !Array.isArray(target?.products)) throw new Error('Missing source/target snapshots');
  const categories = unique(target.categories, c => c.id, 'target category ID');
  unique(target.categories, c => c.slug, 'target category slug');
  const current = unique(target.products, p => p.id, 'target product ID');
  unique(source, p => p._id, 'source product ID');
  const rootMap = unique(parents, p => p.legacyCategory, 'legacy parent');
  for (const [name, p] of rootMap) {
    const root = categories.get(p.targetParentId);
    if (!nonempty(name) || !root || root.parent_id !== null) throw new Error(`Parent mapping must name an existing retained root: ${name}`);
  }
  const pairs = unique(taxonomy, p => {
    if (!nonempty(p.category) || !nonempty(p.subcategory)) throw new Error('Source pair names must be nonempty');
    return pairKey(p.category, p.subcategory);
  }, 'source pair');
  const blockers = [], additions = [], links = new Map(), updates = [], unchanged = [];
  for (const [key, pair] of pairs) {
    const parent = rootMap.get(pair.category)?.targetParentId;
    if (!parent) { blockers.push({ reason: 'unmapped_legacy_parent', pair }); continue; }
    // Exact name and parent only. No case folding, spelling fixes, fuzzy matches.
    const matches = target.categories.filter(c => c.parent_id === parent && c.name === pair.subcategory);
    if (matches.length > 1) { blockers.push({ reason: 'ambiguous_existing_child', pair }); continue; }
    const id = `cat-legacy-${sha256(pairKey(parent, pair.subcategory)).slice(0, 32)}`;
    const slug = `sub-legacy-${sha256(pairKey(parent, pair.subcategory)).slice(0, 32)}`;
    const found = matches[0];
    if (found) { links.set(key, found.id); continue; }
    if (categories.has(id) || target.categories.some(c => c.slug === slug)) {
      blockers.push({ reason: 'generated_identifier_collision', pair }); continue;
    }
    // Multiple legacy roots deliberately mapped to one new root may share a child.
    const planned = additions.find(c => c.parent_id === parent && c.name === pair.subcategory);
    if (planned) { links.set(key, planned.id); continue; }
    additions.push({ id, slug, name: pair.subcategory, parent_id: parent });
    links.set(key, id);
  }
  const sourceIds = new Set(source.map(p => `lgp-${p._id}`));
  for (const p of target.products) {
    if (p.id.startsWith('lgp-') && !sourceIds.has(p.id)) blockers.push({ reason: 'target_legacy_product_missing_from_source', productId: p.id });
  }
  for (const p of source) {
    const id = `lgp-${p._id}`, existing = current.get(id), key = pairKey(p.category, p.subcategory);
    if (!existing) { blockers.push({ reason: 'source_product_missing_from_target', productId: id }); continue; }
    if (!pairs.has(key) || !links.has(key)) { blockers.push({ reason: 'source_pair_missing_or_unresolved', productId: id, category: p.category, subcategory: p.subcategory }); continue; }
    const destination = links.get(key);
    if (existing.category_id === destination) { unchanged.push(id); continue; }
    // Detect changes since V19. Unknown pairs never use ROOT_FALLBACK.
    const imported = CATEGORY_MAP[`${p.category} > ${p.subcategory}`];
    if (!imported || existing.category_id !== imported || !categories.has(imported)) {
      blockers.push({ reason: 'current_assignment_not_proven_original_import', productId: id, currentCategoryId: existing.category_id }); continue;
    }
    updates.push({ productId: id, expectedCategoryId: existing.category_id, categoryId: destination, sourcePair: { category: p.category, subcategory: p.subcategory } });
  }
  return {
    schemaVersion: 1, readyForReview: blockers.length === 0, appliesChanges: false,
    counts: { sourceProducts: source.length, targetLegacyProducts: target.products.filter(p => p.id.startsWith('lgp-')).length, sourcePairs: pairs.size, proposedCategoryAdditions: additions.length, proposedProductUpdates: updates.length, unchanged: unchanged.length, blockers: blockers.length },
    blockers, proposedCategoryAdditions: additions, proposedProductUpdates: updates, unchangedProductIds: unchanged,
    protections: ['No category removal or replacement', 'No non-legacy product changes', 'No database connection, SQL generation or application', 'Target snapshot must be rechecked before a separately reviewed apply'],
  };
}

export function loadPlan(manifestPath) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  if (manifest.schemaVersion !== 1 || !nonempty(manifest.targetEnvironment)) throw new Error('Invalid manifest version/target');
  const inputs = {};
  for (const key of ['taxonomy', 'sourceProducts', 'target', 'parents']) {
    const ref = manifest[key];
    if (!ref || !nonempty(ref.path) || !/^[a-f0-9]{64}$/.test(ref.sha256) || !nonempty(ref.provenance) || !Number.isFinite(Date.parse(ref.capturedAt))) throw new Error(`Missing provenance/hash/time: ${key}`);
    const bytes = readFileSync(new URL(ref.path, pathToFileURL(manifestPath)));
    if (sha256(bytes) !== ref.sha256) throw new Error(`Input hash mismatch: ${key}`);
    inputs[key] = JSON.parse(bytes);
  }
  return { ...planRecovery(inputs), evidence: manifest };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.length !== 4) throw new Error('Usage: node plan-taxonomy-recovery.mjs /absolute/manifest.json /output/plan.json');
  const plan = loadPlan(process.argv[2]);
  writeFileSync(process.argv[3], JSON.stringify(plan, null, 2) + '\n', { flag: 'wx' });
  console.log(JSON.stringify(plan.counts));
  if (!plan.readyForReview) process.exitCode = 2;
}
