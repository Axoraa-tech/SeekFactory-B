package seekfactory.axoraa.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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

                // Missing or expired token → 401 (not Spring's default 403) so clients can refresh
                // or send the user to sign in; a signed-in user without the role still gets 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // Endpoint access rules
                .authorizeHttpRequests(auth -> auth
                        // ─── PUBLIC ENDPOINTS (no auth required) ──────────────
                        // Error forwards keep the original status (403 stays 403, not 401)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Keep-alive pinger (prod exposes only "health", without details)
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/feed", "/api/v1/feed/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/manufacturers", "/api/v1/manufacturers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reels/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()
                        // View tracking counts guests too (deduped server-side)
                        .requestMatchers(HttpMethod.POST, "/api/v1/feed/*/view", "/api/v1/products/*/view").permitAll()
                        // Buyer discovery: search, buyer plan prices and share counts work for guests too
                        .requestMatchers(HttpMethod.GET, "/api/v1/search", "/api/v1/pricing/buyer-plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/feed/*/share").permitAll()

                        // Swagger / OpenAPI / Actuator
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // ─── ADMIN-ONLY ENDPOINTS ─────────────────────────────
                        .requestMatchers("/api/v1/admin/auth/login", "/api/v1/admin/auth/setup-password").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        // ─── SUPPLIER-ONLY ENDPOINTS ──────────────────────────
                        // Without this, any logged-in buyer hitting /factory/** was
                        // auto-provisioned a manufacturer profile.
                        .requestMatchers("/api/v1/factory/**").hasAuthority("ROLE_SUPPLIER")

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