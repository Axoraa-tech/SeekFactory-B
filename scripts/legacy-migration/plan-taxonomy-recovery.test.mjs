import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { planRecovery, loadPlan } from './plan-taxonomy-recovery.mjs';
const fixture = () => ({
 taxonomy:[{category:'Agricultural Machinery',subcategory:'Agricultural Machinery Equipement'}],
 sourceProducts:[{_id:'original-id',category:'Agricultural Machinery',subcategory:'Agricultural Machinery Equipement'}],
 target:{categories:[{id:'cat-agriculture',slug:'cat-agriculture',name:'Agriculture',parent_id:null},{id:'cat-agriculture-tractors',slug:'sub-tractors',name:'Tractors',parent_id:'cat-agriculture'}],products:[{id:'lgp-original-id',category_id:'cat-agriculture'},{id:'new-product',category_id:'cat-agriculture-tractors'}]},
 parents:[{legacyCategory:'Agricultural Machinery',targetParentId:'cat-agriculture'}],
});
test('additive exact-source plan retains new taxonomy and new products',()=>{const f=fixture(), before=structuredClone(f),p=planRecovery(f);assert.equal(p.readyForReview,true);assert.equal(p.proposedCategoryAdditions.length,1);assert.equal(p.proposedProductUpdates.length,1);assert.equal(p.proposedProductUpdates[0].expectedCategoryId,'cat-agriculture');assert.deepEqual(f,before);assert.equal(p.appliesChanges,false);});
test('deterministic output',()=>assert.deepEqual(planRecovery(fixture()),planRecovery(fixture())));
test('idempotent after applying a hypothetical reviewed plan',()=>{const f=fixture(),p=planRecovery(f);f.target.categories.push(...p.proposedCategoryAdditions);f.target.products[0].category_id=p.proposedProductUpdates[0].categoryId;const again=planRecovery(f);assert.equal(again.readyForReview,true);assert.equal(again.proposedCategoryAdditions.length,0);assert.equal(again.proposedProductUpdates.length,0);assert.equal(again.unchangedProductIds.length,1);});
test('reuse exact existing child without altering it',()=>{const f=fixture();f.target.categories.push({id:'old-child',slug:'old-child',name:f.taxonomy[0].subcategory,parent_id:'cat-agriculture'});assert.equal(planRecovery(f).proposedProductUpdates[0].categoryId,'old-child');});
test('reject guessed current mapping',()=>{const f=fixture();f.target.products[0].category_id='cat-agriculture-tractors';assert.equal(planRecovery(f).readyForReview,false);});
test('unknown legacy pair never uses root fallback',()=>{const f=fixture();f.taxonomy[0].subcategory=f.sourceProducts[0].subcategory='Unknown type';assert.equal(planRecovery(f).blockers[0].reason,'current_assignment_not_proven_original_import');});
test('missing source pair blocked',()=>{const f=fixture();f.sourceProducts[0].subcategory='Not in source taxonomy';assert.equal(planRecovery(f).readyForReview,false);});
test('missing legacy target blocked',()=>{const f=fixture();f.target.products.shift();assert.equal(planRecovery(f).blockers[0].reason,'source_product_missing_from_target');});
test('incomplete source snapshot blocked',()=>{const f=fixture();f.target.products.push({id:'lgp-not-in-snapshot',category_id:'cat-agriculture'});assert.equal(planRecovery(f).blockers[0].reason,'target_legacy_product_missing_from_source');});
test('missing parent mapping blocked',()=>{const f=fixture();f.parents=[];assert.equal(planRecovery(f).readyForReview,false);});
test('parent must be existing root',()=>{const f=fixture();f.parents[0].targetParentId='cat-agriculture-tractors';assert.throws(()=>planRecovery(f),/existing retained root/);});
test('ambiguous existing child blocked',()=>{const f=fixture();for(const id of ['a','b'])f.target.categories.push({id,slug:id,parent_id:'cat-agriculture',name:f.taxonomy[0].subcategory});assert.equal(planRecovery(f).blockers[0].reason,'ambiguous_existing_child');});
test('duplicate source identity rejected',()=>{const f=fixture();f.sourceProducts.push({...f.sourceProducts[0]});assert.throws(()=>planRecovery(f),/duplicate source product/);});
test('duplicate taxonomy pair rejected',()=>{const f=fixture();f.taxonomy.push({...f.taxonomy[0]});assert.throws(()=>planRecovery(f),/duplicate source pair/);});
test('identifier collision blocked',()=>{const f=fixture(),id=planRecovery(f).proposedCategoryAdditions[0].id;f.target.categories.push({id,slug:'different',name:'Different',parent_id:'cat-agriculture'});assert.equal(planRecovery(f).blockers[0].reason,'generated_identifier_collision');});
test('hash-bound snapshots load and tampering is rejected',()=>{const dir=mkdtempSync(join(tmpdir(),'taxonomy-plan-'));try{const f=fixture(),m={schemaVersion:1,targetEnvironment:'synthetic-test'};for(const [key,value]of Object.entries(f)){const bytes=JSON.stringify(value);writeFileSync(join(dir,key+'.json'),bytes);m[key]={path:key+'.json',sha256:createHash('sha256').update(bytes).digest('hex'),capturedAt:'2026-11-14T12:00:00Z',provenance:'Synthetic fixture, not recovered production data'};}writeFileSync(join(dir,'manifest.json'),JSON.stringify(m));assert.equal(loadPlan(join(dir,'manifest.json')).readyForReview,true);writeFileSync(join(dir,'taxonomy.json'),'[]');assert.throws(()=>loadPlan(join(dir,'manifest.json')),/hash mismatch/);}finally{rmSync(dir,{recursive:true});}});
test('empty source taxonomy rejected',()=>{const f=fixture();f.taxonomy=[];assert.throws(()=>planRecovery(f),/Missing source taxonomy/);});

test('shared child identity does not depend on legacy pair order',()=>{const f=fixture();f.taxonomy.push({category:'Other old root',subcategory:f.taxonomy[0].subcategory});f.parents.push({legacyCategory:'Other old root',targetParentId:'cat-agriculture'});const a=planRecovery(f);f.taxonomy.reverse();const b=planRecovery(f);assert.deepEqual(a.proposedCategoryAdditions,b.proposedCategoryAdditions);assert.deepEqual(a.proposedProductUpdates,b.proposedProductUpdates);});
