package seekfactory.axoraa.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter — runs ONCE per request.
 *
 * Workflow:
 * 1. Extract Bearer token from Authorization header
 * 2. Validate the token (signature + expiry)
 * 3. Extract user ID and role from token claims
 * 4. Set authentication in SecurityContext so controllers can use @AuthenticationPrincipal
 *
 * If no token is present, the request proceeds unauthenticated (anonymous).
 * If token is invalid/expired, the request proceeds unauthenticated (Spring Security will reject if endpoint requires auth).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final PresenceService presenceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromHeader(request);

        if (StringUtils.hasText(token)
                && (jwtTokenProvider.isValidAccessToken(token)
                    || (isUploadRequest(request) && jwtTokenProvider.isValidUploadToken(token)))) {
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            // Create authentication object with the user's granted authority
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,                                          // principal = userId
                            null,                                            // credentials (not needed with JWT)
                            List.of(new SimpleGrantedAuthority(role))        // authorities
                    );

            // Set in SecurityContext — available via SecurityContextHolder throughout the request
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Any signed-in call (including the web app's 30s heartbeat) keeps the user "online"
            presenceService.touch(userId);
        }

        filterChain.doFilter(request, response);
    }

    /** The only requests an upload pass (see JwtTokenProvider#generateUploadToken) may authorise. */
    private static boolean isUploadRequest(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return false;
        String path = request.getRequestURI();
        return "/api/v1/factory/media".equals(path) || "/api/v1/media".equals(path);
    }

    /**
     * Extracts the JWT from the "Authorization: Bearer <token>" header.
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}