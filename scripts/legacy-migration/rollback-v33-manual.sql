-- MANUAL ONLY. Never place in Flyway; never invoke automatically on deployment.
-- Review and verify the target and backup before execution. Contains COMMIT.
-- Leaves 14 added categories and audit rows intact; no delete of later references.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL standard_conforming_strings=on;
SET LOCAL search_path=pg_catalog,public;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
LOCK TABLE public.categories, public.products IN SHARE ROW EXCLUSIVE MODE;
DO $rollback$
DECLARE n integer;
BEGIN
 IF (SELECT count(*) FROM seekfactory_migration_audit.legacy_subcategories_v33)<>164 THEN RAISE EXCEPTION 'V33 backup incomplete'; END IF;
 IF EXISTS(SELECT 1 FROM seekfactory_migration_audit.legacy_subcategories_v33 a LEFT JOIN public.products p ON p.id=a.product_id WHERE p.id IS NULL OR to_jsonb(p) IS DISTINCT FROM a.after_row)
 THEN RAISE EXCEPTION 'V33 rollback drift; refusing to overwrite later edits'; END IF;
 UPDATE public.products p SET category_id=a.before_row->>'category_id',updated_at=(a.before_row->>'updated_at')::timestamptz
 FROM seekfactory_migration_audit.legacy_subcategories_v33 a WHERE p.id=a.product_id AND to_jsonb(p)=a.after_row;
 GET DIAGNOSTICS n=ROW_COUNT;
 IF n<>164 THEN RAISE EXCEPTION 'V33 rollback affected count mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM seekfactory_migration_audit.legacy_subcategories_v33 a JOIN public.products p ON p.id=a.product_id WHERE to_jsonb(p) IS DISTINCT FROM a.before_row)
 THEN RAISE EXCEPTION 'V33 rollback trigger/field mismatch'; END IF;
END $rollback$;
COMMIT;
