package seekfactory.axoraa.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


/**
 * Enables JPA Auditing so that @CreatedDate and @LastModifiedDate
 * are automatically populated by Spring Data.
 */

@Configuration
@EnableJpaAuditing
public class AuditingConfig {
}
