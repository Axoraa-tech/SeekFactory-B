// Entirely invented test data. No customer IDs, snapshots or real taxonomy.
export const plan = {
  targetProjectRef: 'isolated-test',
  targetCapturedAt: '2026-11-15T17:00:00Z',
  additions: [{id:'cat-legacy-example',slug:'sub-legacy-example',name:'Synthetic pumps',parent_id:'cat-example'}],
  updates: [{
    id:'lgp-000000000000000000000001',
    expected_category_id:'cat-example',
    expected_updated_at:'2026-10-01T00:00:00Z',
    category_id:'cat-legacy-example',
    expectedRow:{id:'lgp-000000000000000000000001',category_id:'cat-example',name:'Synthetic pump',updated_at:'2026-10-01T00:00:00Z'}
  }]
};
// Informational exclusions. Never copy these into plan.updates.
// Stable IDs only identify records, not the correctness of their classification.
export const held = [
  {case:'two children share a label',reason:'Ambiguous destination; require exact parent and unique child.'},
  {case:'legacy label conflicts with current child',reason:'Preserve current child; source may be stale.'},
  {case:'source pair absent from taxonomy',reason:'Unsupported classification; manual evidence required.'},
  {case:'source ID absent from target',reason:'Unmatched product; do not invent or create a product.'},
  {case:'current product has no legacy ID',reason:'Preserve new data unchanged.'},
  {case:'target changed after snapshot',reason:'Refresh and review; never override row preconditions.'}
];
// Deliberately not approved. These flags MUST NOT be set from a template.
export const reviewTemplate = {
  planSha256:'REPLACE_WITH_HASH_OF_EXACT_PRIVATE_PLAN_BYTES',
  sourceClassificationReviewed:false,backupVerified:false,liveTargetVerified:false,
  target:{projectRef:'isolated-test',database:'postgres',databaseOid:0,serverAddress:'127.0.0.1'},
  appliedAt:'2026-11-15T18:00:00Z'
};
