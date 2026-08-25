package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RedisCocoRateLimitStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scriptContractEnforcesWindowBoundaryAndDoesNotExposeLogicalKey() throws Exception {
        AtomicLong count = new AtomicLong();
        List<String> redisKeys = new CopyOnWriteArrayList<>();
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore((script, keys, arguments) -> {
            redisKeys.add(keys.get(0));
            long limit = Long.parseLong((String) arguments[0]);
            while (true) {
                long current = count.get();
                if (current >= limit) { return -current; }
                if (count.compareAndSet(current, current + 1)) { return current + 1; }
            }
        }, "test:rate:", CLOCK);
        CocoRateLimitPermit permit = new CocoRateLimitPermit(new CocoRateLimitKey("orders", "client-secret"), 10,
                NOW.plusSeconds(30));

        assertThat(runConcurrently(100, () -> store.acquire(permit).allowed()))
                .filteredOn(Boolean::booleanValue).hasSize(10);
        assertThat(count.get()).isEqualTo(10);
        assertThat(redisKeys).allMatch(key -> !key.contains("orders") && !key.contains("client-secret"));
        assertThat(RedisCocoRateLimitStore.acquireScript().getScriptAsString())
                .contains("INCR", "PEXPIRE", "current >= limit");
    }

    @Test
    void expirationAndRedisFailureAreRejectedClosed() {
        CocoRateLimitPermit expired = new CocoRateLimitPermit(new CocoRateLimitKey("orders", "client"), 1, NOW);
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "test:rate:", CLOCK);

        assertThat(store.acquire(expired).capacityExhausted()).isTrue();
        assertThat(store.acquire(new CocoRateLimitPermit(expired.key(), 1, NOW.plusSeconds(1))).capacityExhausted())
                .isTrue();
    }

    private static List<Boolean> runConcurrently(int calls, Callable<Boolean> callable) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(calls, 16)));
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < calls; index++) { tasks.add(callable); }
            return executor.invokeAll(tasks).stream().map(result -> {
                try { return result.get(); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
        }
        finally { executor.shutdownNow(); }
    }
}
