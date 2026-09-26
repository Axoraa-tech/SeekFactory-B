package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;
import seekfactory.axoraa.services.services.PlatformSettingsService;

import java.sql.Timestamp;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlatformSettingsServiceImpl implements PlatformSettingsService {

    private static final String FEED_SHOWCASE = "feed_showcase";

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
}
