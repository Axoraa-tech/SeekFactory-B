// Isolated in-memory PostgreSQL only. Never uses connection URLs or credentials.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {render} from './render-reviewed-reconciliation.mjs';
const modulePath=process.env.PGLITE_MODULE;
const sql=s=>s.replace(/^\\set ON_ERROR_STOP on\n/m,'');
async function fixture(){
 const{PGlite}=await import(modulePath);const db=new PGlite();
 // PGlite has no network socket. Stub only inet_server_addr in this ephemeral DB.
 await db.exec(`CREATE OR REPLACE FUNCTION pg_catalog.inet_server_addr() RETURNS inet LANGUAGE sql AS $$ SELECT '127.0.0.1'::inet $$;
 CREATE TABLE categories(id text primary key,slug text unique,name text,parent_id text references categories(id),listing_count int,created_at timestamptz,updated_at timestamptz);
 CREATE TABLE products(id text primary key,category_id text references categories(id),name text,updated_at timestamptz);
 INSERT INTO categories VALUES('cat-root','root','Root',null,0,now(),now());
 INSERT INTO products VALUES('lgp-0123456789abcdef01234567','cat-root','Original', '2026-10-01T00:00:00Z');`);
 const row=(await db.query('select to_jsonb(p) row from products p')).rows[0].row;
 const address=(await db.query('select inet_server_addr()::text address')).rows[0].address;
 const oid=(await db.query("select oid::int from pg_database where datname=current_database()")).rows[0].oid;
 const plan={targetProjectRef:'isolated-test',targetCapturedAt:'2026-11-15T17:00:00Z',additions:[{id:'cat-legacy-one',slug:'sub-legacy-one',name:'Legacy child',parent_id:'cat-root'}],updates:[{id:row.id,expected_category_id:'cat-root',expected_updated_at:row.updated_at,category_id:'cat-legacy-one',expectedRow:row}]};
 const review={sourceClassificationReviewed:true,backupVerified:true,liveTargetVerified:true,target:{projectRef:'isolated-test',database:'postgres',databaseOid:oid,serverAddress:address},appliedAt:'2026-11-15T18:00:00Z'};
 return{db,plan,review,out:render(plan,review)};
}
for(const scenario of ['apply rollback','drift no timestamp','apply stale row','category collision','wrong target','repeat apply'])test('isolated PostgreSQL '+scenario,{skip:!modulePath},async()=>{
 const{db,plan,review,out}=await fixture();try{
 if(scenario==='apply stale row'){await db.exec("UPDATE products SET name='Edited'");await assert.rejects(db.exec(sql(out.apply)),/precondition/);await db.exec('ROLLBACK');assert.equal((await db.query('select count(*)::int n from categories')).rows[0].n,1);return;}
 if(scenario==='category collision'){await db.exec("INSERT INTO categories VALUES('cat-legacy-one','other','Other','cat-root',0,now(),now())");await assert.rejects(db.exec(sql(out.apply)),/conflict/);await db.exec('ROLLBACK');return;}
 if(scenario==='wrong target'){review.target.database='wrong';await assert.rejects(db.exec(sql(render(plan,review).apply)),/identity/);await db.exec('ROLLBACK');return;}
 await db.exec(sql(out.apply));assert.equal((await db.query('select category_id from products')).rows[0].category_id,'cat-legacy-one');
 if(scenario==='repeat apply'){await assert.rejects(db.exec(sql(out.apply)),/conflict/);await db.exec('ROLLBACK');return;}
 if(scenario==='drift no timestamp'){await db.exec("UPDATE products SET name='Later edit'");await assert.rejects(db.exec(sql(out.rollback)),/Rollback drift/);await db.exec('ROLLBACK');assert.equal((await db.query('select name from products')).rows[0].name,'Later edit');return;}
 await db.exec(sql(out.rollback));assert.equal((await db.query('select category_id from products')).rows[0].category_id,'cat-root');assert.equal((await db.query('select count(*)::int n from categories')).rows[0].n,2);
 }finally{await db.close();}
});
