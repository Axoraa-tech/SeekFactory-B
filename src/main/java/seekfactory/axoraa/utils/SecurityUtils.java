package seekfactory.axoraa.utils;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import seekfactory.axoraa.exceptions.UnauthorizedException;

import java.util.Optional;

/**
 * Utility to extract the current authenticated user's info from the SecurityContext.
 * Used by service methods that need to know "who is making this request?"
 */
public final class SecurityUtils {

    private SecurityUtils() {} // Utility class — no instantiation

    /**
     * Returns the current authenticated user's ID.
     * Throws UnauthorizedException if no authentication is present.
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return (String) authentication.getPrincipal();
    }

    /**
     * Returns the signed-in user's ID, or empty for guests on public endpoints.
     * (Spring marks anonymous requests "authenticated" with principal "anonymousUser",
     * so getCurrentUserId() cannot be used to detect guests.)
     */
    public static Optional<String> findCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof String userId)) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    /**
     * Returns the current user's role (e.g., "ROLE_BUYER").
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}