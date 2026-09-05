package io.github.coco.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

class CocoTwoLevelCacheTest {

    @Test
    void localOnlyCacheStoresAndEvictsWithoutL2OrBroadcast() {
        CocoTwoLevelCache cache = cache(null, null);
        cache.put("k", "v");
        assertThat(cache.get("k").get()).isEqualTo("v");
        cache.evict("k");
        assertThat(cache.get("k")).isNull();
    }

    @Test
    void readFallsThroughToL2AndBackfillsL1() {
        RecordingL2 l2 = new RecordingL2();
        l2.data.put("orders:k", "from-l2");
        CocoTwoLevelCache cache = cache(l2, null);
        // First read misses L1, hits L2.
        assertThat(cache.get("k").get()).isEqualTo("from-l2");
        // L2 read count stays at 1 on the next read: L1 was backfilled.
        cache.get("k");
        assertThat(l2.getCount.get()).isEqualTo(1);
    }

    @Test
    void nullValuesAreCachedForPenetrationProtection() {
        RecordingL2 l2 = new RecordingL2();
        CocoTwoLevelCache cache = cache(l2, null);
        AtomicInteger loads = new AtomicInteger();
        // Loader returns null; with allowNullValues the miss is cached in both tiers.
        String first = cache.get("missing", () -> { loads.incrementAndGet(); return null; });
        assertThat(first).isNull();
        String second = cache.get("missing", () -> { loads.incrementAndGet(); return "should-not-run"; });
        assertThat(second).isNull();
        assertThat(loads.get()).isEqualTo(1);
        assertThat(l2.data).containsKey("orders:missing");
    }

    @Test
    void loaderRunsAtMostOncePerKeyUnderConcurrency() throws Exception {
        CocoTwoLevelCache cache = cache(new RecordingL2(), null);
        AtomicInteger loads = new AtomicInteger();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 32; i++) {
            Thread t = new Thread(() -> cache.get("hot", () -> {
                loads.incrementAndGet();
                Thread.sleep(20);
                return "value";
            }));
            threads.add(t);
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }
        // Single-flight: Caffeine runs the mapping function once even under 32-way contention.
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void remoteInvalidationClearsOnlyL1AndNeverTouchesL2() {
        RecordingL2 l2 = new RecordingL2();
        CocoTwoLevelCache cache = cache(l2, new RecordingPublisher("other-instance"));
        cache.put("k", "v");
        int evictsBefore = l2.evictCount.get();
        cache.onRemoteInvalidation("k");
        // L1 gone, L2 untouched by the remote signal.
        @SuppressWarnings("unchecked")
        var l1 = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        assertThat(l1.asMap()).doesNotContainKey("k");
        assertThat(l2.evictCount.get()).isEqualTo(evictsBefore);
    }

    @Test
    void encodeDecodeRoundTripsIncludingNullKeyAndPipeInKey() {
        CocoCacheInvalidationMessage clear = new CocoCacheInvalidationMessage("orders", null, "src");
        assertThat(RedisCocoCacheInvalidationPublisher.decode(RedisCocoCacheInvalidationPublisher.encode(clear)))
                .isEqualTo(clear);
        CocoCacheInvalidationMessage piped = new CocoCacheInvalidationMessage("orders", "a|b|c", "src");
        assertThat(RedisCocoCacheInvalidationPublisher.decode(RedisCocoCacheInvalidationPublisher.encode(piped)))
                .isEqualTo(piped);
    }

    private static CocoTwoLevelCache cache(CocoCacheL2Store l2, CocoCacheInvalidationPublisher publisher) {
        return new CocoTwoLevelCache("orders", Caffeine.newBuilder().maximumSize(1000).build(),
                l2, publisher, true, 60_000L, 30_000L);
    }

    private static final class RecordingL2 implements CocoCacheL2Store {
        private final ConcurrentMap<String, Object> data = new ConcurrentHashMap<>();
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger evictCount = new AtomicInteger();

        @Override
        public Entry get(String cacheName, String key) {
            this.getCount.incrementAndGet();
            String k = cacheName + ":" + key;
            return this.data.containsKey(k) ? Entry.hit(unwrap(this.data.get(k))) : Entry.miss();
        }

        @Override
        public void put(String cacheName, String key, Object value, long ttlMillis) {
            this.data.put(cacheName + ":" + key, value == null ? NULL : value);
        }

        @Override
        public void evict(String cacheName, String key) {
            this.evictCount.incrementAndGet();
            this.data.remove(cacheName + ":" + key);
        }

        @Override
        public void clear(String cacheName) {
            this.data.keySet().removeIf(k -> k.startsWith(cacheName + ":"));
        }

        private static final Object NULL = new Object();

        private static Object unwrap(Object value) {
            return value == NULL ? null : value;
        }
    }

    private record RecordingPublisher(String sourceId) implements CocoCacheInvalidationPublisher {
        @Override
        public void publish(CocoCacheInvalidationMessage message) {
            // no-op for tests
        }
    }
}
