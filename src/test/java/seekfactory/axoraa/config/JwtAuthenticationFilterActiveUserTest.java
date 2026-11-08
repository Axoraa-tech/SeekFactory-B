package seekfactory.axoraa.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** A valid 24-hour token must stop working as soon as its account is deleted or deactivated. */
class JwtAuthenticationFilterActiveUserTest {

    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final AtomicBoolean activeInDb = new AtomicBoolean(true);
    private final AtomicInteger dbLookups = new AtomicInteger();
    private final ActiveUserCache cache = new ActiveUserCache(id -> {
        dbLookups.incrementAndGet();
        return activeInDb.get();
    });
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, presence, cache);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUserIsAuthenticatedAndTheLookupIsCached() throws Exception {
        givenValidToken();

        assertThat(authenticate()).isTrue();
        assertThat(authenticate()).isTrue();

        assertThat(dbLookups.get()).isEqualTo(1);
        verify(presence, times(2)).touch("u-1");
    }

    @Test
    void deletedUsersTokenIsIgnoredOnceEvicted() throws Exception {
        givenValidToken();
        assertThat(authenticate()).isTrue();

        activeInDb.set(false);
        cache.evict("u-1");  // what AccountDeletionServiceImpl does

        assertThat(authenticate()).isFalse();
        verify(presence, times(1)).touch("u-1");
    }

    @Test
    void inactiveUserIsNeverCachedAsActive() throws Exception {
        givenValidToken();
        activeInDb.set(false);

        assertThat(authenticate()).isFalse();
        assertThat(authenticate()).isFalse();
        assertThat(dbLookups.get()).isEqualTo(2);
    }

    private void givenValidToken() {
        when(tokens.isValidAccessToken("tok")).thenReturn(true);
        when(tokens.getUserIdFromToken("tok")).thenReturn("u-1");
        when(tokens.getRoleFromToken("tok")).thenReturn("ROLE_BUYER");
    }

    private boolean authenticate() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer tok");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }
}
