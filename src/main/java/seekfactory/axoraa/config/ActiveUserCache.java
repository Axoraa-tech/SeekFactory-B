package seekfactory.axoraa.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Answers "is this user still active?" for every authenticated request without a database
 * round trip each time: an active answer is remembered for {@link #TTL_MS}. Access tokens are
 * valid for 24 hours, so without this a deleted or deactivated account would keep working
 * until its token expired. {@link #evict} makes a deletion take effect at once on this
 * instance; other instances catch up within the TTL.
 */
@Component
public class ActiveUserCache {

    static final long TTL_MS = 30_000;
    private static final int MAX_ENTRIES = 50_000;

    private final Predicate<String> isActiveInDb;
    private final Map<String, Long> activeUntil = new ConcurrentHashMap<>();

    @Autowired
    public ActiveUserCache(UserRepository userRepository) {
        this(userRepository::existsByIdAndIsActiveTrue);
    }

    /** For tests: decide activity with the given lookup. */
    public ActiveUserCache(Predicate<String> isActiveInDb) {
        this.isActiveInDb = isActiveInDb;
    }

    public boolean isActive(String userId) {
        if (userId == null) return false;
        long now = System.currentTimeMillis();
        Long until = activeUntil.get(userId);
        if (until != null && until > now) return true;
        boolean active = isActiveInDb.test(userId);
        if (active) {
            if (activeUntil.size() >= MAX_ENTRIES) activeUntil.clear();  // crude bound; refills lazily
            activeUntil.put(userId, now + TTL_MS);
        } else {
            activeUntil.remove(userId);
        }
        return active;
    }

    /** Forget a user so the next request re-checks the database (call after deactivating). */
    public void evict(String userId) {
        if (userId != null) activeUntil.remove(userId);
    }
}
