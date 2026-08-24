package io.github.coco.feature.web.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class RedisCocoReplayStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scriptContractAllowsOnlyOneActiveReservationAndHashesNonce() throws Exception {
        AtomicBoolean reserved = new AtomicBoolean();
        List<String> redisKeys = new CopyOnWriteArrayList<>();
        RedisCocoReplayStore store = new RedisCocoReplayStore((script, keys, arguments) -> {
            redisKeys.add(keys.get(0));
            return reserved.compareAndSet(false, true) ? 1L : 0L;
        }, "test:replay:", CLOCK);
        CocoReplayKey key = new CocoReplayKey("app", "key", "123", "nonce-secret", "POST", "/orders");

        assertThat(runConcurrently(50, () -> store.reserve(key, NOW.plusSeconds(30))))
                .filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(store.reserve(key, NOW)).isFalse();
        assertThat(redisKeys).allMatch(redisKey -> !redisKey.contains("nonce-secret") && !redisKey.contains("/orders"));
        assertThat(RedisCocoReplayStore.reserveScript().getScriptAsString()).contains("SET", "NX", "PX");
    }

    @Test
    void redisFailurePropagatesToTheFilterForFailClosedHandling() {
        RedisCocoReplayStore store = new RedisCocoReplayStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "test:replay:", CLOCK);
        CocoReplayKey key = new CocoReplayKey("app", null, "123", "nonce-secret", "POST", "/orders");

        assertThatThrownBy(() -> store.reserve(key, NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class).hasMessage("redis unavailable");
    }

    private static List<Boolean> runConcurrently(int calls, Callable<Boolean> callable) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
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
