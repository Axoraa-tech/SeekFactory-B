import {test} from 'node:test';
import assert from 'node:assert/strict';
import {render} from './render-reviewed-reconciliation.mjs';
const fixture=()=>({plan:{targetProjectRef:'verified-project',targetCapturedAt:'2026-11-15T17:00:00Z',additions:[{id:'cat-legacy-abc',slug:'sub-legacy-abc',name:"Original's label",parent_id:'cat-agriculture'}],updates:[{id:'lgp-0123456789abcdef01234567',expected_category_id:'cat-agriculture',category_id:'cat-legacy-abc',expected_updated_at:'2026-10-01T00:00:00Z',expectedRow:{id:'lgp-0123456789abcdef01234567',category_id:'cat-agriculture',name:'Preserved name'}}]},review:{sourceClassificationReviewed:true,backupVerified:true,liveTargetVerified:true,target:{projectRef:'verified-project',database:'postgres',databaseOid:12,serverAddress:'127.0.0.1'},appliedAt:'2026-11-15T18:00:00Z'}});
test('renders guarded transactional apply and drift-safe rollback, no delete',()=>{const {plan,review}=fixture(),out=render(plan,review);assert.match(out.apply,/BEGIN;/);assert.match(out.apply,/Original''s label/);assert.match(out.apply,/Only root assignments/);assert.match(out.apply,/updated_at=/);assert.match(out.rollback,/Rollback drift/);assert.doesNotMatch(out.rollback,/DELETE FROM/);});
for(const field of ['sourceClassificationReviewed','backupVerified','liveTargetVerified'])test('requires '+field,()=>{const{plan,review}=fixture();review[field]=false;assert.throws(()=>render(plan,review));});
test('rejects duplicate products',()=>{const{plan,review}=fixture();plan.updates.push(plan.updates[0]);assert.throws(()=>render(plan,review));});
test('rejects nonlegacy IDs',()=>{const{plan,review}=fixture();plan.updates[0].id='new-id';assert.throws(()=>render(plan,review));});
test('rejects old apply timestamp',()=>{const{plan,review}=fixture();review.appliedAt='2026-01-01T00:00:00Z';assert.throws(()=>render(plan,review));});
test('rejects dollar-block injection',()=>{const{plan,review}=fixture();plan.additions[0].name='$apply$; DROP TABLE products;';assert.throws(()=>render(plan,review));});
test('rejects missing target identity',()=>{const{plan,review}=fixture();delete review.target.databaseOid;assert.throws(()=>render(plan,review));});

for(const field of ['sourceClassificationReviewed','backupVerified','liveTargetVerified'])for(const value of ['false','true',1,{},[]])test(`rejects nonboolean ${field}=${JSON.stringify(value)}`,()=>{const{plan,review}=fixture();review[field]=value;assert.throws(()=>render(plan,review));});
test('binds project label to reviewed plan and emits limits',()=>{const{plan,review}=fixture();const sql=render(plan,review).apply;assert.match(sql,/Operator-verified project label: verified-project/);assert.match(sql,/SQL cannot verify/);review.target.projectRef='different';assert.throws(()=>render(plan,review));});
test('rejects missing plan project label',()=>{const{plan,review}=fixture();delete plan.targetProjectRef;assert.throws(()=>render(plan,review));});
test('rejects project comment injection',()=>{const{plan,review}=fixture();plan.targetProjectRef=review.target.projectRef='x\nCOMMIT;';assert.throws(()=>render(plan,review));});

test('full-row apply and rollback guards do not rely only on updated_at',()=>{const{plan,review}=fixture();const out=render(plan,review);assert.match(out.apply,/to_jsonb\(public.products\)/);assert.match(out.rollback,/to_jsonb\(public.products\)/);assert.match(out.rollback,/Preserved name/);});
test('requires complete expected row input',()=>{const{plan,review}=fixture();delete plan.updates[0].expectedRow;assert.throws(()=>render(plan,review));});
test('expected row must match ID and old category',()=>{const{plan,review}=fixture();plan.updates[0].expectedRow.category_id='wrong';assert.throws(()=>render(plan,review));});
