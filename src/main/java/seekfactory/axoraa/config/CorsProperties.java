package seekfactory.axoraa.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe, validated configuration properties for CORS.
 * Binds directly to the "app.cors" section in YAML.
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
@Validated
@Getter
@Setter
public class CorsProperties {

    /**
     * List of allowed origin patterns (e.g., http://localhost:3000, https://*.yourdomain.com).
     */
    @NotEmpty(message = "CORS allowed-origin-patterns must contain at least one origin")
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
    ));

    /**
     * List of allowed HTTP methods.
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * List of allowed HTTP headers.
     */
    private List<String> allowedHeaders = List.of(
            "Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"
    );

    /**
     * List of exposed HTTP response headers.
     */
    private List<String> exposedHeaders = List.of("Authorization");

    /**
     * Whether credentials (cookies, auth headers) are supported.
     */
    private boolean allowCredentials = true;

    /**
     * Max age in seconds for CORS preflight cache.
     */
    private long maxAge = 3600L;
}