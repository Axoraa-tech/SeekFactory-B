-- These tables are backend-only: Spring JPA uses postgres/BYPASSRLS.
-- Do not FORCE RLS or change postgres/service_role ownership or direct grants.
-- blog_posts exists in DEV but is not created by this repository's migrations.
DO $migration$
DECLARE
    table_name text;
    role_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['product_likes', 'blog_posts'] LOOP
        IF to_regclass(format('public.%I', table_name)) IS NULL THEN
            IF table_name = 'product_likes' THEN
                RAISE EXCEPTION 'Required table public.product_likes is missing';
            END IF;
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM PUBLIC', table_name);
        FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
                EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM %I',
                               table_name, role_name);
            END IF;
        END LOOP;
    END LOOP;
END
$migration$;
