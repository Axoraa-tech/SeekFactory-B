// All database writes are to ephemeral in-memory PGlite, never a URL.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const modulePath=process.env.PGLITE_MODULE;
const migration=fs.readFileSync(new URL('../../src/main/resources/db/migration/V33__restore_reviewed_legacy_subcategories.sql',import.meta.url),'utf8');
const rollback=fs.readFileSync(new URL('rollback-v33-manual.sql',import.meta.url),'utf8').replace(/^\\set ON_ERROR_STOP on\n/m,'');
async function fixture(){
 const {PGlite}=await import(modulePath);const db=new PGlite();
 await db.exec(`CREATE TABLE categories(id text primary key,slug text unique,name text,parent_id text,listing_count int,created_at timestamptz,updated_at timestamptz);
 CREATE TABLE products(id text primary key,category_id text,name text,updated_at timestamptz);`);
 // Derive expected input from the exact committed migration, not a second mapping.
 const children=migration.match(/INSERT INTO sf_v33_children VALUES\n([\s\S]*?);/)[1];
 const products=migration.match(/INSERT INTO sf_v33_products VALUES\n([\s\S]*?);/)[1];
 await db.exec('CREATE TABLE seed_children(id text,slug text,name text,parent_id text); INSERT INTO seed_children VALUES '+children+'; CREATE TABLE seed_products(id text,old_category text,old_updated_at timestamptz,new_category text); INSERT INTO seed_products VALUES '+products+';');
 if(process.env.PRIVATE_TARGET_SNAPSHOT){
  const t=JSON.parse(fs.readFileSync(process.env.PRIVATE_TARGET_SNAPSHOT));
  for(const c of t.categories)await db.query('INSERT INTO categories(id,slug,name,parent_id) VALUES($1,$2,$3,$4)',[c.id,c.slug,c.name,c.parent_id]);
  for(const p of t.products)await db.query('INSERT INTO products VALUES($1,$2,$3,$4)',[p.id,p.category_id,'Synthetic preserved field',p.updated_at]);
 }else{
  await db.exec("INSERT INTO categories(id,slug,name) SELECT DISTINCT parent_id,parent_id,parent_id FROM seed_children; INSERT INTO products SELECT id,old_category,'Synthetic preserved field',old_updated_at FROM seed_products; INSERT INTO categories(id,slug,name,parent_id) SELECT 'keep-child','keep-child','Keep',parent_id FROM seed_children LIMIT 1; INSERT INTO products VALUES('keep-current-child','keep-child','Untouched',now()),('new-product','keep-child','New',now());");
 }
 const before=(await db.query('SELECT to_jsonb(p) row FROM products p ORDER BY id')).rows.map(x=>x.row);
 const categoryCount=(await db.query('SELECT count(*)::int n FROM categories')).rows[0].n;
 const id=(await db.query('SELECT id FROM seed_products LIMIT 1')).rows[0].id;
 return{db,before,id,categoryCount};
}
for(const scenario of ['apply replay rollback','stale timestamp','existing child','missing product','category collision','rollback drift without timestamp','trigger change','replay drift'])test('auto migration '+scenario,{skip:!modulePath},async()=>{
 const{db,before,id,categoryCount}=await fixture();
 try{
  if(scenario==='stale timestamp')await db.query("UPDATE products SET updated_at=now() WHERE id=$1",[id]);
  if(scenario==='existing child')await db.query("UPDATE products SET category_id='already-reviewed-child' WHERE id=$1",[id]);
  if(scenario==='missing product')await db.query('DELETE FROM products WHERE id=$1',[id]);
  if(scenario==='category collision')await db.exec('INSERT INTO categories(id,slug,name,parent_id) SELECT * FROM seed_children LIMIT 1');
  if(scenario==='trigger change')await db.exec(`CREATE FUNCTION mutate() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.name:='bad trigger'; RETURN NEW; END $$; CREATE TRIGGER mutation BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION mutate();`);
  if(['stale timestamp','existing child','missing product','category collision','trigger change'].includes(scenario)){
   await assert.rejects(db.exec('BEGIN;'+migration+'COMMIT;'),/V33/);await db.exec('ROLLBACK');
   assert.equal((await db.query("SELECT to_regclass('seekfactory_migration_audit.legacy_subcategories_v33') value")).rows[0].value,null);return;
  }
  await db.exec('BEGIN;'+migration+'COMMIT;');
  const after=(await db.query('SELECT to_jsonb(p) row FROM products p ORDER BY id')).rows.map(x=>x.row);
  assert.equal(after.filter((p,i)=>p.category_id!==before[i].category_id).length,164);
  assert.equal((await db.query('SELECT count(*)::int n FROM categories')).rows[0].n,categoryCount+14);
  const changedIds=new Set((await db.query('SELECT id FROM seed_products')).rows.map(x=>x.id));
  for(let i=0;i<after.length;i++)if(!changedIds.has(after[i].id))assert.deepEqual(after[i],before[i]);
  assert.equal((await db.query('SELECT count(*)::int n FROM seekfactory_migration_audit.legacy_subcategories_v33')).rows[0].n,164);
  for(let i=0;i<after.length;i++)assert.equal(after[i].name,before[i].name);
  if(scenario.includes('drift')){
   await db.query("UPDATE products SET name='New edit' WHERE id=$1",[id]);
   await assert.rejects(db.exec(scenario==='replay drift'?'BEGIN;'+migration+'COMMIT;':rollback),/drift/);await db.exec('ROLLBACK');return;
  }
  await db.exec('BEGIN;'+migration+'COMMIT;'); // Exact replay is a no-op.
  assert.deepEqual((await db.query('SELECT to_jsonb(p) row FROM products p ORDER BY id')).rows.map(x=>x.row),after);
  await db.exec(rollback);
  assert.deepEqual((await db.query('SELECT to_jsonb(p) row FROM products p ORDER BY id')).rows.map(x=>x.row),before);
 }finally{await db.close();}
});
