package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin dashboard leaves the V5 demo seed out of its figures by id prefix. These tests keep that
 * filter in step with the seed file and prove it can never hide a real (UUID) row.
 */
class AdminDashboardDemoFilterTest {

    private static final Pattern DEMO = Pattern.compile(AdminDashboardServiceImpl.DEMO_ID_REGEX);

    /** Tables the dashboard counts, so every seeded row in them must be recognised as demo. */
    private static final Set<String> FILTERED_TABLES = Set.of(
            "users", "manufacturers", "products", "reels", "comments",
            "rfqs", "rfq_quotes", "conversations", "messages", "notifications");

    @Test
    void recognisesEverySeededRowInTheTablesTheDashboardCounts() throws IOException {
        String seed = readSeed();
        Matcher inserts = Pattern.compile("INSERT INTO (\\w+)[^;]*?VALUES(.*?);\\s*(?=\\n\\S|$)", Pattern.DOTALL).matcher(seed);

        List<String> seededIds = new ArrayList<>();
        while (inserts.find()) {
            if (!FILTERED_TABLES.contains(inserts.group(1))) continue;
            Matcher row = Pattern.compile("^\\(\\s*'([^']+)'", Pattern.MULTILINE).matcher(inserts.group(2));
            while (row.find()) seededIds.add(row.group(1));
        }

        // Sanity: the parser found the seed at all, otherwise the assertion below proves nothing
        assertThat(seededIds).hasSizeGreaterThan(20);
        assertThat(seededIds).allSatisfy(id ->
                assertThat(DEMO.matcher(id).find()).as("seeded id %s must match the demo filter", id).isTrue());
    }

    @Test
    void neverHidesARealRow() {
        for (int i = 0; i < 5_000; i++) {
            String uuid = UUID.randomUUID().toString();
            assertThat(DEMO.matcher(uuid).find()).as(uuid).isFalse();
        }
    }

    private static String readSeed() throws IOException {
        try (InputStream in = AdminDashboardDemoFilterTest.class.getResourceAsStream("/db/migration/V5__seed_demo_data.sql")) {
            assertThat(in).as("V5 seed migration on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
