package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class RedisCocoIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scriptContractSupportsSingleOwnerAndOwnerSafeRelease() throws Exception {
        AtomicReference<String> owner = new AtomicReference<>();
        List<String> redisKeys = new CopyOnWriteArrayList<>();
        RedisCocoIdempotencyStore store = new RedisCocoIdempotencyStore((script, keys, arguments) -> {
            redisKeys.add(keys.get(0));
            String requestedOwner = (String) arguments[0];
            if (script == RedisCocoIdempotencyStore.acquireScript()) {
                return owner.compareAndSet(null, requestedOwner) ? 1L : 0L;
            }
            return owner.compareAndSet(requestedOwner, null) ? 1L : 0L;
        }, "test:idempotency:", CLOCK);
        CocoIdempotencyKey key = CocoIdempotencyKey.fromRawKey("orders", "POST", "create", "request-secret");
        CocoIdempotencyLease first = new CocoIdempotencyLease(key, "owner-one", NOW.plusSeconds(30));
        CocoIdempotencyLease second = new CocoIdempotencyLease(key, "owner-two", NOW.plusSeconds(30));

        assertThat(runConcurrently(50, () -> store.acquire(first) == CocoIdempotencyStore.AcquireResult.ACQUIRED))
                .filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(store.acquire(second)).isEqualTo(CocoIdempotencyStore.AcquireResult.DUPLICATE);
        store.release(second);
        assertThat(store.acquire(second)).isEqualTo(CocoIdempotencyStore.AcquireResult.DUPLICATE);
        store.release(first);
        assertThat(store.acquire(second)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
        assertThat(redisKeys).allMatch(redisKey -> !redisKey.contains("request-secret") && !redisKey.contains("orders"));
        assertThat(RedisCocoIdempotencyStore.acquireScript().getScriptAsString()).contains("SET", "NX", "PX");
        assertThat(RedisCocoIdempotencyStore.releaseScript().getScriptAsString()).contains("GET", "DEL");
    }

    @Test
    void expirationAndRedisFailureAreUnavailable() {
        CocoIdempotencyKey key = CocoIdempotencyKey.fromRawKey("orders", "POST", "create", "request-secret");
        RedisCocoIdempotencyStore store = new RedisCocoIdempotencyStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "test:idempotency:", CLOCK);

        assertThat(store.acquire(new CocoIdempotencyLease(key, "owner", NOW)))
                .isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
        assertThat(store.acquire(new CocoIdempotencyLease(key, "owner", NOW.plusSeconds(1))))
                .isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
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
