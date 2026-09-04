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
    void fixedWindowScriptContractCountsAtomicallyAndHidesLogicalKey() throws Exception {
        AtomicLong count = new AtomicLong();
        List<String> redisKeys = new CopyOnWriteArrayList<>();
        // Simulate the fixed-window Lua script: atomic increment up to the limit,
        // returning {allowed, remaining, resetAtMillis} the way the real script does.
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore((script, keys, arguments) -> {
            redisKeys.add(keys.get(0));
            long limit = Long.parseLong((String) arguments[0]);
            long resetAt = NOW.plusSeconds(30).toEpochMilli();
            while (true) {
                long current = count.get();
                if (current >= limit) {
                    return List.of(0L, 0L, resetAt);
                }
                if (count.compareAndSet(current, current + 1)) {
                    return List.of(1L, limit - (current + 1), resetAt);
                }
            }
        }, "test:rate:", CLOCK);
        CocoRateLimitPermit permit = new CocoRateLimitPermit(new CocoRateLimitKey("orders", "client-secret"),
                CocoRateLimitAlgorithm.FIXED_WINDOW, 10, 30);

        assertThat(runConcurrently(100, () -> store.acquire(permit).allowed()))
                .filteredOn(Boolean::booleanValue).hasSize(10);
        assertThat(count.get()).isEqualTo(10);
        assertThat(redisKeys).allMatch(key -> !key.contains("orders") && !key.contains("client-secret"));
        assertThat(RedisCocoRateLimitStore.fixedWindowScript().getScriptAsString())
                .contains("HGET", "PEXPIRE", "count >= limit");
    }

    @Test
    void eachAlgorithmSelectsItsOwnScript() {
        // Route the three algorithms and assert each hits a distinct, correct script.
        List<String> executed = new CopyOnWriteArrayList<>();
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore((script, keys, arguments) -> {
            executed.add(script.getScriptAsString());
            return List.of(1L, 5L, NOW.plusSeconds(10).toEpochMilli());
        }, "test:rate:", CLOCK);
        for (CocoRateLimitAlgorithm algorithm : CocoRateLimitAlgorithm.values()) {
            store.acquire(new CocoRateLimitPermit(new CocoRateLimitKey("orders", "client"), algorithm, 10, 10));
        }
        assertThat(executed).hasSize(3);
        assertThat(executed.get(0)).contains("count >= limit");            // fixed window
        assertThat(executed.get(1)).contains("prev * weight + cur");        // sliding window
        assertThat(executed.get(2)).contains("refillPerMs");                // token bucket
    }

    @Test
    void redisFailureIsRejectedClosed() {
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "test:rate:", CLOCK);
        CocoRateLimitDecision decision = store.acquire(new CocoRateLimitPermit(
                new CocoRateLimitKey("orders", "client"), CocoRateLimitAlgorithm.FIXED_WINDOW, 1, 60));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.capacityExhausted()).isTrue();
    }

    @Test
    void malformedScriptResultIsRejectedClosed() {
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore(
                (script, keys, arguments) -> List.of(1L), "test:rate:", CLOCK);
        assertThat(store.acquire(new CocoRateLimitPermit(new CocoRateLimitKey("orders", "client"),
                CocoRateLimitAlgorithm.SLIDING_WINDOW, 1, 60)).capacityExhausted()).isTrue();
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
