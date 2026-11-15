package seekfactory.axoraa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable local PostgreSQL only; never loads Spring or environment database credentials. */
class ProductLimitMigrationTest {

    private static int limitOf(Statement sql, String id) throws SQLException {
        try (var row = sql.executeQuery("SELECT product_limit FROM manufacturers WHERE id='" + id + "'")) {
            assertTrue(row.next());
            return row.getInt(1);
        }
    }

    @Test void defaultsToThreeAndGrandfathersFactoriesAlreadyAboveIt() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0)
                .setServerConfig("listen_addresses", "127.0.0.1")
                .setServerConfig("unix_socket_directories", "").start();
             var db = pg.getPostgresDatabase().getConnection();
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE manufacturers (id varchar(64) PRIMARY KEY, name text NOT NULL)");
            sql.execute("CREATE TABLE products (id varchar(64) PRIMARY KEY, manufacturer_id varchar(64) NOT NULL"
                    + " REFERENCES manufacturers(id), is_active boolean NOT NULL DEFAULT true)");
            sql.execute("INSERT INTO manufacturers VALUES ('small', 'Small'), ('big', 'Big'), ('deleted', 'Deleted')");
            sql.execute("INSERT INTO products VALUES ('s1','small',true), ('s2','small',true)");
            for (int i = 0; i < 6; i++) sql.execute("INSERT INTO products VALUES ('b" + i + "','big',true)");
            // Soft-deleted products do not count towards the limit
            for (int i = 0; i < 5; i++) sql.execute("INSERT INTO products VALUES ('d" + i + "','deleted',false)");

            try (var resource = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V34__manufacturer_product_limit_and_profile_video.sql")) {
                assertNotNull(resource);
                sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }

            assertEquals(3, limitOf(sql, "small"));
            assertEquals(6, limitOf(sql, "big"));
            assertEquals(3, limitOf(sql, "deleted"));
            try (var row = sql.executeQuery("SELECT count(*) FROM products")) {
                assertTrue(row.next());
                assertEquals(13, row.getInt(1)); // nothing removed
            }

            sql.execute("INSERT INTO manufacturers (id, name) VALUES ('new', 'New')");
            assertEquals(3, limitOf(sql, "new"));
            sql.execute("UPDATE manufacturers SET profile_video_url='/api/v1/media/a.mp4' WHERE id='new'");

            sql.execute("INSERT INTO product_limit_requests (id, manufacturer_id, current_count, current_limit,"
                    + " requested_limit) VALUES ('r1', 'small', 2, 3, 10)");
            try (var row = sql.executeQuery("SELECT status FROM product_limit_requests WHERE id='r1'")) {
                assertTrue(row.next());
                assertEquals("PENDING", row.getString(1));
            }
            // At most one open request per factory, but history may hold many handled ones
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO product_limit_requests"
                    + " (id, manufacturer_id, current_count, current_limit, requested_limit) VALUES ('r2','small',2,3,5)"));
            sql.execute("UPDATE product_limit_requests SET status='APPROVED' WHERE id='r1'");
            sql.execute("INSERT INTO product_limit_requests (id, manufacturer_id, current_count, current_limit,"
                    + " requested_limit) VALUES ('r3', 'small', 2, 10, 20)");
            assertThrows(SQLException.class, () -> sql.execute("UPDATE product_limit_requests SET status='PAID' WHERE id='r3'"));
        }
    }
}
