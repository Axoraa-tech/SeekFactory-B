package seekfactory.axoraa.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Small in-memory cache for public lists that are costly to build and identical for everyone
 * (all approved factories, a category's products). Stale-while-revalidate: once a value is older
 * than {@code freshMillis} it is still served, while one background rebuild replaces it, so no
 * request waits for a rebuild after the first load. Values older than {@code maxStaleMillis} (or
 * missing) are built in the request itself.
 *
 * The loader runs on a background thread for rebuilds: give it everything it needs (e.g. its
 * own transaction), not the caller's.
 */
@Slf4j
public final class RefreshingCache<K, V> {

    private record Entry<V>(V value, long loadedAt) {}

    private final long freshMillis;
    private final long maxStaleMillis;
    private final int maxEntries;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Set<K> refreshing = ConcurrentHashMap.newKeySet();
    /** Bumped by markStale: a build that started before a change must not count as fresh. */
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();

    public RefreshingCache(long freshMillis, long maxStaleMillis, int maxEntries) {
        this.freshMillis = freshMillis;
        this.maxStaleMillis = maxStaleMillis;
        this.maxEntries = maxEntries;
    }

    public V get(K key, Supplier<V> loader) {
        Entry<V> entry = entries.get(key);
        long age = entry == null ? Long.MAX_VALUE : System.currentTimeMillis() - entry.loadedAt();
        if (age < freshMillis) return entry.value();
        if (age < maxStaleMillis) {
            if (refreshing.add(key)) {
                long started = generation.get();
                Thread.ofVirtual().name("cache-refresh").start(() -> {
                    try {
                        put(key, loader.get(), started);
                    } catch (RuntimeException e) {
                        // Keep serving the previous value; the next request tries again
                        log.warn("Background cache refresh failed for {}: {}", key, e.getMessage());
                    } finally {
                        refreshing.remove(key);
                    }
                });
            }
            return entry.value();
        }
        long started = generation.get();
        V value = loader.get();
        put(key, value, started);
        return value;
    }

    /** Forget everything, e.g. after a change that must show immediately. */
    public void clear() {
        entries.clear();
    }

    /**
     * Treat every value as out of date: the next request still gets it instantly but starts a
     * background rebuild, so a change shows up within a request or two without anyone waiting.
     */
    public void markStale() {
        generation.incrementAndGet();
        long stale = System.currentTimeMillis() - freshMillis;
        entries.replaceAll((key, entry) -> new Entry<>(entry.value(), Math.min(entry.loadedAt(), stale)));
    }

    private void put(K key, V value, long startedAtGeneration) {
        if (entries.size() >= maxEntries && !entries.containsKey(key)) entries.clear();
        long now = System.currentTimeMillis();
        // Built from data read before the latest change: keep it, but rebuild on next use
        long loadedAt = generation.get() == startedAtGeneration ? now : now - freshMillis;
        entries.put(key, new Entry<>(value, loadedAt));
    }
}
