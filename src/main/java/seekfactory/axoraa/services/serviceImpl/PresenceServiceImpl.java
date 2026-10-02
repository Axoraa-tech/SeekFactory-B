package seekfactory.axoraa.services.serviceImpl;

import org.springframework.stereotype.Service;
import seekfactory.axoraa.services.services.PresenceService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence: enough for a single backend instance. With several instances behind a load
 * balancer this moves to a shared store (Redis) so every instance sees the same last-seen times.
 */
@Service
public class PresenceServiceImpl implements PresenceService {

    /** Three missed heartbeats (sent every 30s) and the user shows as offline. */
    static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);

    /** Skip rewriting the map on every request of a busy page. */
    private static final Duration TOUCH_GRANULARITY = Duration.ofSeconds(5);

    /** Stale entries are swept once the map grows past this. */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** Last activity, and whether the user has signed out since. */
    private record Seen(Instant at, boolean signedOut) {}

    private final Map<String, Seen> lastSeen = new ConcurrentHashMap<>();

    @Override
    public void touch(String userId) {
        if (userId == null) return;
        Instant now = Instant.now();
        Seen previous = lastSeen.get(userId);
        if (previous != null && !previous.signedOut() && previous.at().plus(TOUCH_GRANULARITY).isAfter(now)) return;
        lastSeen.put(userId, new Seen(now, false));
        if (lastSeen.size() > SWEEP_THRESHOLD) {
            Instant cutoff = now.minus(ONLINE_WINDOW);
            lastSeen.values().removeIf(seen -> seen.at().isBefore(cutoff));
        }
    }

    @Override
    public void markOffline(String userId) {
        // Keep the time so the chat can still say "last seen just now"
        if (userId != null) lastSeen.put(userId, new Seen(Instant.now(), true));
    }

    @Override
    public boolean isOnline(String userId) {
        Seen seen = userId == null ? null : lastSeen.get(userId);
        return seen != null && !seen.signedOut() && seen.at().plus(ONLINE_WINDOW).isAfter(Instant.now());
    }

    @Override
    public Instant lastSeen(String userId) {
        Seen seen = userId == null ? null : lastSeen.get(userId);
        return seen == null ? null : seen.at();
    }
}
