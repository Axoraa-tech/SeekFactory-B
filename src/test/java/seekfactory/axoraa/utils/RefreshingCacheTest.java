package seekfactory.axoraa.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshingCacheTest {

    @Test
    void servesFreshValuesWithoutReloading() {
        RefreshingCache<String, Integer> cache = new RefreshingCache<>(60_000, 120_000, 10);
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(1);
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(1);
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void servesTheStaleValueWhileOneBackgroundRefreshRuns() throws InterruptedException {
        RefreshingCache<String, Integer> cache = new RefreshingCache<>(0, 60_000, 10);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch refreshed = new CountDownLatch(1);
        assertThat(cache.get("k", () -> 1)).isEqualTo(1);

        // Expired: the old value comes back immediately; the slow rebuild happens once, behind it
        for (int i = 0; i < 5; i++) {
            assertThat(cache.get("k", () -> {
                loads.incrementAndGet();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                refreshed.countDown();
                return 2;
            })).isEqualTo(1);
        }
        release.countDown();
        assertThat(refreshed.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.get("k", () -> 3)).isIn(2, 3);
    }

    @Test
    void loadsInTheRequestWhenMissingOrTooOld() {
        RefreshingCache<String, Integer> cache = new RefreshingCache<>(0, 0, 10);
        assertThat(cache.get("k", () -> 1)).isEqualTo(1);
        assertThat(cache.get("k", () -> 2)).isEqualTo(2);
    }

    @Test
    void markStaleServesTheOldValueOnceAndRebuildsBehindIt() throws InterruptedException {
        RefreshingCache<String, Integer> cache = new RefreshingCache<>(60_000, 120_000, 10);
        cache.get("k", () -> 1);
        cache.markStale();
        assertThat(cache.get("k", () -> 2)).isEqualTo(1);
        Thread.sleep(100);
        assertThat(cache.get("k", () -> 3)).isEqualTo(2);
    }

    @Test
    void aFailedRefreshKeepsTheOldValue() throws InterruptedException {
        RefreshingCache<String, Integer> cache = new RefreshingCache<>(0, 60_000, 10);
        cache.get("k", () -> 1);
        assertThat(cache.get("k", () -> { throw new IllegalStateException("db down"); })).isEqualTo(1);
        Thread.sleep(100);
        assertThat(cache.get("k", () -> 1)).isEqualTo(1);
    }
}
