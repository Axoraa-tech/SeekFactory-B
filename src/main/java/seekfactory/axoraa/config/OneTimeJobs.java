package seekfactory.axoraa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Remembers in the database which one-time background jobs have finished, so a job runs by itself on the
 * first start after a deploy that needs it and never again, with no setting to add and later remove.
 *
 * <p>Each job has a name and a version: raising the version in code makes it run once more (e.g. when what
 * it produces changes). Stored as {@code platform_settings} rows {@code job:<name>} = {@code {"version": n}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneTimeJobs {

    private static final String PREFIX = "job:";

    private final JdbcTemplate jdbcTemplate;

    /** True when {@code job} has not completed {@code version} (or a later one) yet. */
    public boolean isPending(String job, int version) {
        Integer done = jdbcTemplate.query(
                "SELECT (value->>'version')::int FROM platform_settings WHERE key = ?",
                rs -> rs.next() ? (Integer) rs.getObject(1) : null, PREFIX + job);
        return done == null || done < version;
    }

    /** Records that {@code job} finished {@code version}. */
    public void markDone(String job, int version) {
        jdbcTemplate.update("""
                INSERT INTO platform_settings (key, value, updated_at)
                VALUES (?, jsonb_build_object('version', ?), now())
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """, PREFIX + job, version);
        log.info("One-time job '{}' v{} done", job, version);
    }
}
