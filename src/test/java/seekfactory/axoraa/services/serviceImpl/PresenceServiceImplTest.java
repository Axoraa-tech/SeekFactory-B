package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceServiceImplTest {

    private final PresenceServiceImpl presence = new PresenceServiceImpl();

    @Test
    void unknownUserIsOffline() {
        assertThat(presence.isOnline("u-1")).isFalse();
        assertThat(presence.isOnline(null)).isFalse();
    }

    @Test
    void activeUserIsOnlineUntilTheySignOut() {
        presence.touch("u-1");
        assertThat(presence.isOnline("u-1")).isTrue();
        assertThat(presence.lastSeen("u-1")).isNotNull();

        presence.markOffline("u-1");
        assertThat(presence.isOnline("u-1")).isFalse();
        assertThat(presence.lastSeen("u-1")).isNotNull();

        presence.touch("u-1");
        assertThat(presence.isOnline("u-1")).isTrue();
    }
}
