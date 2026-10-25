package seekfactory.axoraa.services.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-off background job: makes the small photo copies for every product, factory logo/cover and
 * seek poster already in the database, so no buyer waits for the first copy. Off by default;
 * turn on with APP_MEDIA_WARM_VARIANTS=true for one start (e.g. after a deploy or import).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.media.warm-variants", havingValue = "true")
public class MediaVariantWarmer implements ApplicationRunner {

    private static final Pattern KEY = Pattern.compile(
            "/api/v1/media/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg))$");

    private final JdbcTemplate jdbcTemplate;
    private final MediaVariantService variants;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("media-variant-warmer").start(this::warm);
    }

    private void warm() {
        Set<String> keys = new LinkedHashSet<>();
        String sql = """
                SELECT image_url FROM products WHERE is_active = TRUE
                UNION SELECT pi.image_url FROM product_images pi JOIN products p ON p.id = pi.product_id WHERE p.is_active = TRUE
                UNION SELECT logo_url FROM manufacturers
                UNION SELECT cover_url FROM manufacturers
                UNION SELECT poster_url FROM reels
                """;
        jdbcTemplate.query(sql, rs -> {
            String url = rs.getString(1);
            if (url == null) return;
            Matcher m = KEY.matcher(url);
            if (m.find()) keys.add(m.group(1));
        });
        log.info("Making small photo copies for {} images", keys.size());
        int done = 0;
        long started = System.currentTimeMillis();
        for (String key : keys) {
            for (int width : MediaVariantService.WIDTHS) variants.variantFor(key, width);
            if (++done % 200 == 0) log.info("Photo copies: {}/{}", done, keys.size());
        }
        log.info("Photo copies ready for {} images in {} s", keys.size(), (System.currentTimeMillis() - started) / 1000);
    }
}
