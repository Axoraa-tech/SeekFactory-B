package seekfactory.axoraa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable local PostgreSQL only. Does not load Spring, .env or remote credentials. */
class BackendTableAccessMigrationTest {
    private static final String MIGRATION = "db/migration/V26__restrict_direct_table_access.sql";

    @Test
    void existingTablesDenyApiRolesAndPreserveBackendAccess() throws Exception {
        try (var pg = localPostgres(); var db = pg.getPostgresDatabase().getConnection()) {
            fixture(db, true, true);
            migrate(db);
            migrate(db); // Also safe after the equivalent DEV hotfix has already run.
            for (String table : new String[]{"product_likes", "blog_posts"}) {
                assertRls(db, table);
                for (String role : new String[]{"anon", "authenticated", "public_probe"}) {
                    for (String privilege : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE",
                            "TRUNCATE", "REFERENCES", "TRIGGER"}) {
                        assertFalse(flag(db, "SELECT has_table_privilege('" + role + "', 'public."
                                + table + "', '" + privilege + "')"), role + ": " + privilege);
                    }
                    sql(db, "SET ROLE " + role);
                    try {
                        denied(db, "SELECT * FROM public." + table);
                        denied(db, "INSERT INTO public." + table + " (id) VALUES ('blocked')");
                        denied(db, "UPDATE public." + table + " SET id = id");
                        denied(db, "DELETE FROM public." + table);
                        denied(db, "TRUNCATE public." + table);
                    } finally {
                        sql(db, "RESET ROLE");
                    }
                }
                backendCrud(db, table); // postgres table owner / superuser
                sql(db, "SET ROLE service_role");
                try {
                    backendCrud(db, table); // Non-owner BYPASSRLS role, with explicit grants.
                } finally {
                    sql(db, "RESET ROLE");
                }
            }
        }
    }

    @Test
    void freshDatabaseWithoutSupabaseRolesOrOptionalBlogTableWorks() throws Exception {
        try (var pg = localPostgres(); var db = pg.getPostgresDatabase().getConnection()) {
            fixture(db, false, false);
            migrate(db);
            assertRls(db, "product_likes");
            assertFalse(flag(db, "SELECT to_regclass('public.blog_posts') IS NOT NULL"));
            assertFalse(flag(db, "SELECT has_table_privilege('public_probe', 'public.product_likes', 'SELECT')"));
            backendCrud(db, "product_likes");
        }
    }

    @Test
    void rlsStillDeniesRowsIfApiCrudPrivilegesAreAccidentallyRegranted() throws Exception {
        try (var pg = localPostgres(); var db = pg.getPostgresDatabase().getConnection()) {
            fixture(db, true, true);
            migrate(db);
            for (String table : new String[]{"product_likes", "blog_posts"}) {
                sql(db, "GRANT SELECT, INSERT, UPDATE, DELETE ON public." + table + " TO anon, authenticated");
                for (String role : new String[]{"anon", "authenticated"}) {
                    sql(db, "SET ROLE " + role);
                    try {
                        assertFalse(flag(db, "SELECT EXISTS (SELECT 1 FROM public." + table + ")"));
                        denied(db, "INSERT INTO public." + table + " (id) VALUES ('blocked')");
                        assertEquals(0, update(db, "UPDATE public." + table + " SET id = id"));
                        assertEquals(0, update(db, "DELETE FROM public." + table));
                    } finally {
                        sql(db, "RESET ROLE");
                    }
                }
            }
        }
    }

    private static EmbeddedPostgres localPostgres() throws Exception {
        return EmbeddedPostgres.builder().setPort(0)
                .setServerConfig("listen_addresses", "127.0.0.1").start();
    }

    private static void fixture(Connection db, boolean blog, boolean apiRoles) throws Exception {
        sql(db, "CREATE ROLE public_probe NOLOGIN");
        sql(db, "CREATE TABLE public.products (id varchar(64) PRIMARY KEY)");
        sql(db, "CREATE TABLE public.users (id varchar(64) PRIMARY KEY)");
        resource(db, "db/migration/V23__product_likes.sql");
        sql(db, "INSERT INTO public.products VALUES ('product')");
        sql(db, "INSERT INTO public.users VALUES ('user')");
        sql(db, "ALTER TABLE public.product_likes ALTER COLUMN product_id SET DEFAULT 'product'");
        sql(db, "ALTER TABLE public.product_likes ALTER COLUMN user_id SET DEFAULT 'user'");
        if (blog) sql(db, "CREATE TABLE public.blog_posts (id varchar(64) PRIMARY KEY)");
        if (apiRoles) {
            sql(db, "CREATE ROLE anon NOLOGIN");
            sql(db, "CREATE ROLE authenticated NOLOGIN");
            sql(db, "CREATE ROLE service_role NOLOGIN BYPASSRLS");
        }
        for (String table : blog ? new String[]{"product_likes", "blog_posts"} : new String[]{"product_likes"}) {
            sql(db, "GRANT ALL ON public." + table + " TO PUBLIC");
            if (apiRoles) sql(db, "GRANT ALL ON public." + table + " TO anon, authenticated, service_role");
        }
        // Seed both tables so a zero-row result proves RLS filtered existing rows.
        sql(db, "INSERT INTO public.product_likes (id) VALUES ('seed')");
        if (blog) sql(db, "INSERT INTO public.blog_posts (id) VALUES ('seed')");
    }

    private static void backendCrud(Connection db, String table) throws Exception {
        // Reuse the existing product/user pair without violating V23's unique constraint.
        assertEquals(1, update(db, "DELETE FROM public." + table + " WHERE id = 'seed'"));
        assertEquals(1, update(db, "INSERT INTO public." + table + " (id) VALUES ('backend')"));
        assertTrue(flag(db, "SELECT EXISTS (SELECT 1 FROM public." + table + " WHERE id = 'backend')"));
        assertEquals(1, update(db, "UPDATE public." + table + " SET id = 'seed' WHERE id = 'backend'"));
    }

    private static void assertRls(Connection db, String table) throws Exception {
        assertTrue(flag(db, "SELECT relrowsecurity AND NOT relforcerowsecurity FROM pg_class WHERE oid = 'public."
                + table + "'::regclass"));
    }

    private static void migrate(Connection db) throws Exception { resource(db, MIGRATION); }

    private static void resource(Connection db, String path) throws Exception {
        try (var input = BackendTableAccessMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            sql(db, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void denied(Connection db, String query) {
        SQLException error = assertThrows(SQLException.class, () -> sql(db, query));
        assertEquals("42501", error.getSQLState(), query);
    }

    private static void sql(Connection db, String query) throws SQLException {
        try (var statement = db.createStatement()) { statement.execute(query); }
    }

    private static int update(Connection db, String query) throws SQLException {
        try (var statement = db.createStatement()) { return statement.executeUpdate(query); }
    }

    private static boolean flag(Connection db, String query) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next(), query);
            return rows.getBoolean(1);
        }
    }
}
