package seekfactory.axoraa.services.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.config.OneTimeJobs;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-off background job: makes the small photo copies for every product, factory logo/cover and
 * seek poster already in the database, under the names browsers load directly from storage
 * (see MediaVariantService#ensureDirectCopy), so no buyer waits for the first copy.
 *
 * <p>Runs by itself once, a few minutes after the first start that has this version, and records that in
 * the database ({@link OneTimeJobs}); new photos get their copies at upload. {@code app.media.warm-variants}:
 * {@code auto} (default, once), {@code true} (every start, e.g. after a bulk import), {@code false} (never).
 */
@Slf4j
@Component
public class MediaVariantWarmer implements ApplicationRunner {

    static final String JOB = "photo-direct-copies";
    /** Raise to make every existing photo's copies again (e.g. new widths). */
    static final int VERSION = 1;
    /** Let the API finish starting and serve its first visitors before the job takes CPU. */
    static final long START_DELAY_MS = 3 * 60_000;

    private static final Pattern KEY = Pattern.compile(
            "/api/v1/media/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg))$");

    static final long PAUSE_BETWEEN_PHOTOS_MS = 300;

    private final JdbcTemplate jdbcTemplate;
    private final MediaVariantService variants;
    private final OneTimeJobs jobs;
    private final String mode;

    public MediaVariantWarmer(JdbcTemplate jdbcTemplate, MediaVariantService variants, OneTimeJobs jobs,
                              @Value("${app.media.warm-variants:auto}") String mode) {
        this.jdbcTemplate = jdbcTemplate;
        this.variants = variants;
        this.jobs = jobs;
        this.mode = mode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!shouldRun()) return;
        Thread.ofVirtual().name("media-variant-warmer").start(() -> {
            try {
                Thread.sleep(START_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (warm()) jobs.markDone(JOB, VERSION);
            } catch (RuntimeException e) {
                log.warn("Photo copies stopped, will retry on the next start: {}", e.getMessage());
            }
        });
    }

    boolean shouldRun() {
        return switch (mode) {
            case "false", "off", "never" -> false;
            case "true", "always" -> true;
            default -> jobs.isPending(JOB, VERSION);
        };
    }

    /** True when every photo was gone through (copies that failed are made later, when first viewed). */
    boolean warm() {
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
            for (int width : MediaVariantService.WIDTHS) variants.ensureDirectCopy(key, width);
            if (++done % 200 == 0) log.info("Photo copies: {}/{}", done, keys.size());
            // Decoding a large photo takes the server's only CPU core: leave room for visitors' requests
            try {
                Thread.sleep(PAUSE_BETWEEN_PHOTOS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.info("Photo copies ready for {} images in {} s", keys.size(), (System.currentTimeMillis() - started) / 1000);
        return true;
    }
}
