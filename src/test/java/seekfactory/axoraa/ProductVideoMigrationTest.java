package seekfactory.axoraa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable local PostgreSQL only; never loads Spring or environment database credentials. */
class ProductVideoMigrationTest {
    @Test void additiveMigrationPreservesLegacyProductsAndStoresReplacesClearsVideo() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0)
                .setServerConfig("listen_addresses", "127.0.0.1")
                .setServerConfig("unix_socket_directories", "").start();
             var db = pg.getPostgresDatabase().getConnection();
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE products (id text PRIMARY KEY, name text NOT NULL)");
            sql.execute("INSERT INTO products VALUES ('legacy', 'Original')");
            try (var resource = getClass().getClassLoader().getResourceAsStream("db/migration/V31__product_video.sql")) {
                assertNotNull(resource);
                sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (var row = sql.executeQuery("SELECT name, video_url FROM products WHERE id='legacy'")) {
                assertTrue(row.next());
                assertEquals("Original", row.getString(1));
                assertNull(row.getString(2));
            }
            sql.execute("INSERT INTO products (id, name, video_url) VALUES ('new', 'New', '/api/v1/media/a.mp4')");
            sql.execute("UPDATE products SET video_url='/api/v1/media/b.mp4' WHERE id='new'");
            sql.execute("UPDATE products SET name='Renamed' WHERE id='new'");
            try (var row = sql.executeQuery("SELECT video_url FROM products WHERE id='new'")) {
                assertTrue(row.next());
                assertEquals("/api/v1/media/b.mp4", row.getString(1));
            }
            sql.execute("UPDATE products SET video_url=NULL WHERE id='new'");
            try (var row = sql.executeQuery("SELECT video_url FROM products WHERE id='new'")) {
                assertTrue(row.next());
                assertNull(row.getString(1));
            }
        }
    }
}
