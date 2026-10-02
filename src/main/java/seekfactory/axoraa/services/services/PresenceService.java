package seekfactory.axoraa.services.services;

import java.time.Instant;

/**
 * Who is online right now, for the green dot in chats. A user counts as online while their
 * signed-in pages keep calling the API (the web app sends a heartbeat every 30s) and until they
 * sign out.
 */
public interface PresenceService {

    /** Records activity for the user (called for every authenticated request). */
    void touch(String userId);

    /** Takes the user offline at once, e.g. on logout. */
    void markOffline(String userId);

    boolean isOnline(String userId);

    /** Last time the user was active, or null if unknown. */
    Instant lastSeen(String userId);
}
