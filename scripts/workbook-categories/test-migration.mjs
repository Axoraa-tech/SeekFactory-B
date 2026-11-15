import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
const sql=readFileSync(new URL('../../src/main/resources/db/migration/V32__add_missing_workbook_categories.sql',import.meta.url),'utf8');
const expected=JSON.parse(readFileSync(new URL('./expected-additions.json',import.meta.url),'utf8'));
const categories=JSON.parse(readFileSync(new URL('./category-fixture.json',import.meta.url),'utf8'));
async function fixture(){
 const db=new PGlite();
 await db.exec(`CREATE TABLE categories(id varchar(64) PRIMARY KEY,slug varchar(128) UNIQUE NOT NULL,name varchar(255) NOT NULL,icon varchar(64) NOT NULL DEFAULT 'other',parent_id varchar(64) REFERENCES categories(id),listing_count integer NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());CREATE TABLE products(id text PRIMARY KEY,category_id varchar(64) REFERENCES categories(id),name text);CREATE TABLE manufacturer_categories(manufacturer_id text,category_id varchar(64) REFERENCES categories(id));`);
 for(const c of [...categories.filter(c=>c.parent_id===null),...categories.filter(c=>c.parent_id!==null)])await db.query('INSERT INTO categories(id,slug,name,parent_id,listing_count) VALUES($1,$2,$3,$4,7)',[c.id,c.slug,c.name,c.parent_id]);
 await db.exec("INSERT INTO products VALUES('lgp-original','cat-agriculture','Preserve me'),('new-product','cat-healthcare-medical','Keep new line');INSERT INTO manufacturer_categories VALUES('factory','cat-agriculture');");
 return db;
}
async function snapshot(db){return (await db.query('SELECT row_to_json(c) data FROM categories c ORDER BY id')).rows;}
test('exactly32 additions; existing category rows, products and manufacturer links untouched; rerun no-op',async()=>{const db=await fixture();try{const before=await snapshot(db),products=(await db.query('SELECT * FROM products ORDER BY id')).rows,links=(await db.query('SELECT * FROM manufacturer_categories')).rows;await db.exec(sql);const after=await snapshot(db);assert.equal(after.length,before.length+32);for(const row of before)assert.deepEqual(after.find(x=>x.data.id===row.data.id),row);assert.deepEqual((await db.query('SELECT * FROM products ORDER BY id')).rows,products);assert.deepEqual((await db.query('SELECT * FROM manufacturer_categories')).rows,links);for(const p of expected){const c=after.find(x=>x.data.id===p.id).data;assert.equal(c.name,p.subcategory);assert.equal(c.parent_id,p.parent_id);assert.equal(c.slug,p.slug);assert.equal(c.icon,after.find(x=>x.data.id===p.parent_id).data.icon);assert.equal(c.listing_count,0);}await db.exec(sql);assert.deepEqual(await snapshot(db),after);}finally{await db.close();}});
for(const [label,mutate] of [
 ['missing parent',async db=>{const p=expected.at(-1);await db.query('UPDATE categories SET parent_id=NULL WHERE parent_id=$1',[p.parent_id]);await db.query('DELETE FROM categories WHERE id=$1',[p.parent_id]);}],
 ['renamed parent',async db=>db.query('UPDATE categories SET name=$1 WHERE id=$2',['Changed',expected.at(-1).parent_id])],
 ['reparented parent',async db=>db.query('UPDATE categories SET parent_id=$1 WHERE id=$2',['cat-agriculture',expected.at(-1).parent_id])],
 ['conflicting ID',async db=>{const p=expected.at(-1);await db.query('INSERT INTO categories(id,slug,name,parent_id) VALUES($1,$2,$3,$4)',[p.id,'occupied','Conflict',p.parent_id]);}],
 ['conflicting slug',async db=>{const p=expected.at(-1);await db.query('INSERT INTO categories(id,slug,name,parent_id) VALUES($1,$2,$3,$4)',['other-id',p.slug,'Conflict',p.parent_id]);}],
 ['same name different ID',async db=>{const p=expected.at(-1);await db.query('INSERT INTO categories(id,slug,name,parent_id) VALUES($1,$2,$3,$4)',['other-id','other-slug',p.subcategory,p.parent_id]);}],
])test(label+' aborts all additions atomically',async()=>{const db=await fixture();try{await mutate(db);const before=await snapshot(db);await assert.rejects(db.exec(sql));assert.deepEqual(await snapshot(db),before);}finally{await db.close();}});
test('existing exact expected row preserved',async()=>{const db=await fixture();try{const p=expected[0];await db.query('INSERT INTO categories(id,slug,name,parent_id,listing_count,icon) VALUES($1,$2,$3,$4,123,$5)',[p.id,p.slug,p.subcategory,p.parent_id,'custom']);const before=(await db.query('SELECT * FROM categories WHERE id=$1',[p.id])).rows;await db.exec(sql);assert.deepEqual((await db.query('SELECT * FROM categories WHERE id=$1',[p.id])).rows,before);assert.equal((await snapshot(db)).length,categories.length+32);}finally{await db.close();}});
