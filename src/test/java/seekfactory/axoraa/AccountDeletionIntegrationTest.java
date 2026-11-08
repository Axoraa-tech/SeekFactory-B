package seekfactory.axoraa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Account deletion end to end: the real application on a disposable local PostgreSQL, driven
 * over HTTP. No shared database, .env or remote service is touched. The schema comes from
 * Hibernate as in LocalConcurrencyTest (the Flyway history does not replay on an empty database),
 * plus plan_payments, which has no entity, from its migration.
 */
class AccountDeletionIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir static Path temp;
    private static EmbeddedPostgres pg;
    private static ConfigurableApplicationContext context;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        pg = EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses", "127.0.0.1").start();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        SpringApplication app = new SpringApplication(AxoraaApplication.class);
        app.setEnvironment(environment);
        context = app.run("--spring.config.location=classpath:/local-load.yaml",
                "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres", "--spring.datasource.password=postgres",
                "--app.media.storage-dir=" + temp.resolve("media"));
        String planPayments = new String(AccountDeletionIntegrationTest.class
                .getResourceAsStream("/db/migration/V17__plan_payments.sql").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        context.getBean(JdbcTemplate.class).execute(planPayments);
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() throws Exception {
        if (context != null) context.close();
        if (pg != null) pg.close();
    }

    @Test
    void deletedBuyerIsAnonymisedSignedOutAndTheFactoryKeepsTheHistory() throws Exception {
        Account supplier = register("Supplier");
        Factory factory = approveFactoryWithProduct(supplier);
        Account buyer = register("Buyer");

        String conversationId = call("POST", "/api/v1/conversations", buyer.token,
                Map.of("manufacturerId", factory.id, "initialMessage", "Hello from the buyer")).path("data").path("id").asText();
        call("POST", "/api/v1/orders", buyer.token, Map.of("productSlug", factory.productSlug, "quantity", 3,
                "contactName", "Asha Buyer", "contactPhone", "+911234567890", "deliveryAddress", "1 Private Road"));
        call("POST", "/api/v1/products/" + factory.productId + "/like", buyer.token, Map.of());
        JdbcTemplate db = context.getBean(JdbcTemplate.class);
        db.update("""
                INSERT INTO plan_payments (id, user_id, account_type, buyer_plan, plan_name, region, currency, amount,
                    payer_name, payer_email, proof_data, proof_content_type, proof_size)
                VALUES (?, ?, 'BUYER', 'PRO', 'Pro', 'IN', 'INR', 999, 'Asha', ?, ?, 'image/png', 3)""",
                UUID.randomUUID().toString(), buyer.id, buyer.email, new byte[]{1, 2, 3});

        // Wrong password: nothing happens
        assertThat(status("POST", "/api/v1/account/delete", buyer.token, Map.of("password", "wrong-password"))).isEqualTo(400);
        assertThat(status("GET", "/api/v1/auth/me", buyer.token, null)).isEqualTo(200);

        assertThat(status("POST", "/api/v1/account/delete", buyer.token, Map.of("password", buyer.password))).isEqualTo(200);

        // The old 24-hour token and the password no longer work; the address can sign up again
        assertThat(status("GET", "/api/v1/auth/me", buyer.token, null)).isEqualTo(401);
        assertThat(status("POST", "/api/v1/auth/login", null, Map.of("email", buyer.email, "password", buyer.password))).isEqualTo(401);
        assertThat(status("POST", "/api/v1/auth/refresh", null, Map.of("refreshToken", buyer.refreshToken))).isEqualTo(401);

        // The factory still has the chat and the order, now from "Deleted user"
        JsonNode conversations = call("GET", "/api/v1/conversations", supplier.token, null).path("data");
        JsonNode chat = find(conversations, "id", conversationId);
        assertThat(chat.path("buyerName").asText()).isEqualTo("Deleted user");
        assertThat(chat.path("buyerCompany").isNull() || chat.path("buyerCompany").asText().isEmpty()
                || chat.path("buyerCompany").asText().equals("Deleted user")).isTrue();
        JsonNode messages = call("GET", "/api/v1/conversations/" + conversationId + "/messages", supplier.token, null).path("data");
        assertThat(messages.toString()).contains("Hello from the buyer");

        JsonNode orders = call("GET", "/api/v1/factory/orders", supplier.token, null).path("data");
        assertThat(orders.toString()).contains(factory.productName).contains("CANCELLED")
                .doesNotContain("Asha Buyer").doesNotContain("1 Private Road").doesNotContain(buyer.email);

        Map<String, Object> row = db.queryForMap("SELECT name, email, phone, password_hash, is_active, deleted_at FROM users WHERE id = ?", buyer.id);
        assertThat(row.get("name")).isEqualTo("Deleted user");
        assertThat((String) row.get("email")).endsWith("@deleted.seekfactory.invalid");
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("is_active")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM product_likes WHERE user_id = ?", Long.class, buyer.id)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM messages WHERE sender_id = ?", Long.class, buyer.id)).isEqualTo(1);
        Map<String, Object> payment = db.queryForMap(
                "SELECT status, payer_name, payer_email, proof_size, octet_length(proof_data) AS bytes, amount FROM plan_payments WHERE user_id = ?", buyer.id);
        assertThat(payment.get("status")).isEqualTo("REJECTED");
        assertThat(payment.get("payer_name")).isNull();
        assertThat(payment.get("payer_email")).isNull();
        assertThat(((Number) payment.get("bytes")).intValue()).isZero();
        assertThat(((Number) payment.get("amount")).intValue()).isEqualTo(999);

        // Same email can register a brand-new account
        assertThat(status("POST", "/api/v1/auth/register", null, Map.of("name", "Asha Again", "email", buyer.email,
                "password", buyer.password, "role", "Buyer"))).isBetween(200, 201);
    }

    @Test
    void deletedFactoryDisappearsForBuyersButTheirChatStays() throws Exception {
        Account supplier = register("Supplier");
        Factory factory = approveFactoryWithProduct(supplier);
        Account buyer = register("Buyer");
        String conversationId = call("POST", "/api/v1/conversations", buyer.token,
                Map.of("manufacturerId", factory.id, "initialMessage", "Is this in stock?")).path("data").path("id").asText();
        call("POST", "/api/v1/conversations/" + conversationId + "/messages", supplier.token,
                Map.of("messageText", "Yes, ready to ship"));
        assertThat(status("GET", "/api/v1/manufacturers/" + factory.slug, null, null)).isEqualTo(200);

        assertThat(status("POST", "/api/v1/account/delete", supplier.token, Map.of("password", supplier.password))).isEqualTo(200);

        assertThat(status("GET", "/api/v1/factory/profile", supplier.token, null)).isEqualTo(401);
        assertThat(status("GET", "/api/v1/manufacturers/" + factory.slug, null, null)).isEqualTo(404);
        assertThat(status("GET", "/api/v1/products/" + factory.productSlug, null, null)).isEqualTo(404);

        JsonNode chat = find(call("GET", "/api/v1/conversations", buyer.token, null).path("data"), "id", conversationId);
        assertThat(chat.isMissingNode()).isFalse();
        JsonNode messages = call("GET", "/api/v1/conversations/" + conversationId + "/messages", buyer.token, null).path("data");
        assertThat(messages.toString()).contains("Is this in stock?").contains("Yes, ready to ship");
    }

    @Test
    void accountWithoutPasswordConfirmsWithTheWordDelete() throws Exception {
        Account buyer = register("Buyer");
        context.getBean(JdbcTemplate.class).update("UPDATE users SET password_hash = NULL WHERE id = ?", buyer.id);
        assertThat(call("GET", "/api/v1/users/me", buyer.token, null).path("data").path("hasPassword").asBoolean(true)).isFalse();

        assertThat(status("POST", "/api/v1/account/delete", buyer.token, Map.of())).isEqualTo(400);
        assertThat(status("POST", "/api/v1/account/delete", buyer.token, Map.of("confirmation", "delete"))).isEqualTo(200);
        assertThat(status("GET", "/api/v1/auth/me", buyer.token, null)).isEqualTo(401);
    }

    // ─── Helpers ────────────────────────────────────────────────

    private record Account(String id, String email, String password, String token, String refreshToken) {}

    private record Factory(String id, String slug, String productId, String productSlug, String productName) {}

    private static Account register(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@deletion.invalid";
        String password = "Passw0rd!" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode data = call("POST", "/api/v1/auth/register", null, Map.of("name", "Test " + role, "email", email,
                "password", password, "role", role, "companyName", role + " Co", "phone", "+91" + (long) (Math.random() * 1e9)))
                .path("data");
        return new Account(data.path("userId").asText(), email, password,
                data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private static Factory approveFactoryWithProduct(Account supplier) {
        ManufacturerRepository manufacturers = context.getBean(ManufacturerRepository.class);
        Manufacturer m = manufacturers.findByUserId(supplier.id).orElseThrow();
        m.setVerified(true);
        manufacturers.save(m);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product p = context.getBean(ProductRepository.class).save(Product.builder()
                .manufacturer(m)
                .category(category())
                .slug("deletion-test-" + suffix).name("Deletion Test Tractor " + suffix)
                .imageUrl("/local-test.png").build());
        return new Factory(m.getId(), m.getSlug(), p.getId(), p.getSlug(), p.getName());
    }

    private static seekfactory.axoraa.entity.Category category() {
        CategoryRepository categories = context.getBean(CategoryRepository.class);
        return categories.findBySlug("deletion-test").orElseGet(() -> categories.save(
                seekfactory.axoraa.entity.Category.builder().slug("deletion-test").name("Deletion test").build()));
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) if (value.equals(n.path(field).asText())) return n;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int status(String method, String path, String token, Object body) throws Exception {
        return send(method, path, token, body).statusCode();
    }

    private static JsonNode call(String method, String path, String token, Object body) throws Exception {
        HttpResponse<String> res = send(method, path, token, body);
        assertThat(res.statusCode()).as(method + " " + path + " -> " + res.body()).isBetween(200, 299);
        return JSON.readTree(res.body());
    }
}
