package seekfactory.axoraa.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;

/**
 * Central security configuration.
 *
 * Defines three access tiers:
 * 1. PUBLIC — No authentication needed (auth endpoints, feed, categories, public profiles)
 * 2. AUTHENTICATED — Any logged-in user (RFQ, messages, notifications, profile)
 * 3. ADMIN — Only ROLE_ADMIN users (user management, verifications)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity                    // Enables @PreAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS
                .cors(Customizer.withDefaults())
                // Disable CSRF (stateless JWT, no cookies for auth)
                .csrf(csrf -> csrf.disable())

                // Stateless session — no server-side session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Endpoint access rules
                .authorizeHttpRequests(auth -> auth
                        // ─── PUBLIC ENDPOINTS (no auth required) ──────────────
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/feed/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/manufacturers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reels/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/**").permitAll()

                        // Swagger / OpenAPI / Actuator
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // ─── ADMIN-ONLY ENDPOINTS ─────────────────────────────
                        .requestMatchers("/api/v1/admin/auth/login", "/api/v1/admin/auth/setup-password").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        // ─── ALL OTHER ENDPOINTS REQUIRE AUTHENTICATION ───────
                        .anyRequest().authenticated()
                )

                // Add JWT filter BEFORE Spring's default UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — industry standard for password hashing.
     * BCrypt automatically salts passwords and is resistant to rainbow table attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // strength = 12 rounds
    }
}