package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.settings.ExchangeRatesResponse;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;
import seekfactory.axoraa.services.services.PlatformSettingsService;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlatformSettingsServiceImpl implements PlatformSettingsService {

    private static final String FEED_SHOWCASE = "feed_showcase";
    private static final String EXCHANGE_RATES = "exchange_rates";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public FeedShowcaseSettings getFeedShowcase() {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT s.value->>'mode' AS mode,
                           coalesce((s.value->>'autoplay')::boolean, true)    AS autoplay,
                           coalesce((s.value->>'showProfile')::boolean, true) AS show_profile,
                           coalesce((s.value->>'showPhotos')::boolean, true)  AS show_photos,
                           s.updated_at, u.name AS updated_by_name
                    FROM platform_settings s LEFT JOIN users u ON u.id = s.updated_by
                    WHERE s.key = ?
                    """, (rs, i) -> {
                Timestamp ts = rs.getTimestamp("updated_at");
                String mode = rs.getString("mode");
                return new FeedShowcaseSettings(
                        FeedShowcaseSettings.Mode.parseOrDefault(mode),
                        rs.getBoolean("autoplay"), rs.getBoolean("show_profile"), rs.getBoolean("show_photos"),
                        ts == null ? null : ts.toInstant(), rs.getString("updated_by_name"));
            }, FEED_SHOWCASE);
        } catch (EmptyResultDataAccessException e) {
            return FeedShowcaseSettings.defaults();
        }
    }

    @Override
    public FeedShowcaseSettings updateFeedShowcase(FeedShowcaseSettings settings, String adminId) {
        jdbcTemplate.update("""
                INSERT INTO platform_settings (key, value, updated_by, updated_at)
                VALUES (?, jsonb_build_object('mode', ?::text, 'autoplay', ?, 'showProfile', ?, 'showPhotos', ?), ?, now())
                ON CONFLICT (key) DO UPDATE
                  SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()
                """,
                FEED_SHOWCASE, settings.mode().name(), settings.autoplay(), settings.showProfile(),
                settings.showPhotos(), adminId);
        log.info("Admin {} set feed showcase to {}", adminId, settings);
        return getFeedShowcase();
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangeRatesResponse getExchangeRates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT r.key AS code, r.value::double precision AS rate
                FROM platform_settings s, jsonb_each_text(s.value->'rates') r
                WHERE s.key = ?
                ORDER BY r.key
                """, rs -> {
            rates.put(rs.getString("code"), rs.getDouble("rate"));
        }, EXCHANGE_RATES);
        String base = jdbcTemplate.query("SELECT value->>'base' FROM platform_settings WHERE key = ?",
                rs -> rs.next() ? rs.getString(1) : null, EXCHANGE_RATES);
        return ExchangeRatesResponse.builder()
                .base(base != null ? base : "INR")
                .rates(rates)
                .build();
    }
}
