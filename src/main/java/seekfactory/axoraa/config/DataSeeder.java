package seekfactory.axoraa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        String rootToken = "00000000-0000-0000-0000-000000000000";
        String email = "info.axoraa@gmail.com";
        
        // Check if token exists
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_invitations WHERE token = ?", 
                Integer.class, 
                rootToken
        );

        if (count == null || count == 0) {
            log.info("Seeding root admin invitation token...");
            jdbcTemplate.update(
                    "INSERT INTO admin_invitations (id, email, token, expires_at, is_used, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID(), email, rootToken, LocalDateTime.now().plusDays(30), false, LocalDateTime.now()
            );
            log.info("Root admin invitation token seeded successfully.");
        } else {
            jdbcTemplate.update("UPDATE admin_invitations SET is_used = false, expires_at = ? WHERE token = ?", LocalDateTime.now().plusDays(30), rootToken);
            log.info("Reset root admin invitation token to unused.");
        }
        
        // Always delete any stale admin user from previous test runs so setup can succeed
        jdbcTemplate.update("DELETE FROM users WHERE email = ? AND role = 'ROLE_ADMIN'", email);
        log.info("Cleaned up stale admin user.");
    }
}
