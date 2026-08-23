package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

class InMemoryCocoIdempotencyStoreTest {
    @Test
    void sameKeyCompetitionHasExactlyOneWinnerAfterCommonStart() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(10), clock, false)) {
            CyclicBarrier start = new CyclicBarrier(64);
            List<Future<CocoIdempotencyStore.AcquireResult>> results = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                int owner = index;
                results.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return store.acquire(lease("owner-" + owner, "orders", clock, 60));
                }));
            }
            assertThat(results.stream().map(this::result)
                    .filter(value -> value == CocoIdempotencyStore.AcquireResult.ACQUIRED)).hasSize(1);
            assertThat(store.size()).isOne();
        }
        finally { executor.shutdownNow(); }
    }

    @Test
    void differentKeysNeverExceedStrictCapacityUnderConcurrentArrival() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(8), clock, false)) {
            CyclicBarrier start = new CyclicBarrier(32);
            List<Future<CocoIdempotencyStore.AcquireResult>> results = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                int key = index;
                results.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return store.acquire(lease("owner-" + key, "operation-" + key, clock, 60));
                }));
            }
            assertThat(results.stream().map(this::result)
                    .filter(value -> value == CocoIdempotencyStore.AcquireResult.ACQUIRED)).hasSize(8);
            assertThat(store.size()).isEqualTo(8);
        }
        finally { executor.shutdownNow(); }
    }

    @Test
    void closeAndAcquireUseOneLifecycleProtocol() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        BlockingMap entries = new BlockingMap();
        InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(2), clock, false, entries);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CocoIdempotencyStore.AcquireResult> acquire = executor.submit(
                    () -> store.acquire(lease("owner", "orders", clock, 60)));
            assertThat(entries.computeEntered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch closeStarted = new CountDownLatch(1);
            Future<?> close = executor.submit(() -> { closeStarted.countDown(); store.close(); });
            assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            entries.allowCompute.countDown();
            assertThat(result(acquire)).isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
            close.get(5, TimeUnit.SECONDS);
            assertThat(store.acquire(lease("after-close", "other", clock, 60)))
                    .isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
        }
        finally { executor.shutdownNow(); store.close(); }
    }

    @Test
    void expirationReleaseAndOldOwnerProtectionAllowSafeRetry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(1), clock, false)) {
            CocoIdempotencyLease first = lease("old", "orders", clock, 5);
            assertThat(store.acquire(first)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            store.release(first);
            assertThat(store.acquire(lease("retry", "orders", clock, 5)))
                    .isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            clock.advanceSeconds(5);
            CocoIdempotencyLease replacement = lease("new", "orders", clock, 5);
            assertThat(store.acquire(replacement)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            store.release(first);
            assertThat(store.acquire(lease("later", "orders", clock, 5)))
                    .isEqualTo(CocoIdempotencyStore.AcquireResult.DUPLICATE);
        }
    }

    @Test
    void capacityFailsClosedAndKeysNeverExposeRawValues() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        CocoIdempotencyKey key = CocoIdempotencyKey.fromRawKey("default", "POST", "orders", "raw-secret");
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(1), clock, false)) {
            assertThat(store.acquire(new CocoIdempotencyLease(key, "one", clock.instant().plusSeconds(60))))
                    .isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            assertThat(store.acquire(lease("two", "other", clock, 60)))
                    .isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
            assertThat(key.toString()).doesNotContain("raw-secret");
            assertThat(key.keyDigest()).doesNotContain("raw-secret");
            assertThat(new CocoIdempotencyKeyException().getMessage()).doesNotContain("raw-secret");
        }
    }

    @Test
    void cleanupRemovesExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(2), clock, false)) {
            assertThat(store.acquire(lease("one", "orders", clock, 1))).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            clock.advanceSeconds(1);
            store.cleanupExpired();
            assertThat(store.size()).isZero();
        }
    }

    private CocoIdempotencyStore.AcquireResult result(Future<CocoIdempotencyStore.AcquireResult> future) {
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static CocoIdempotencyProperties properties(int entries) {
        CocoIdempotencyProperties result = new CocoIdempotencyProperties();
        result.setMaxEntries(entries);
        return result;
    }

    private static CocoIdempotencyLease lease(String owner, String operation, Clock clock, long seconds) {
        return new CocoIdempotencyLease(CocoIdempotencyKey.fromRawKey("default", "POST", operation,
                "request-key-" + operation), owner, clock.instant().plusSeconds(seconds));
    }

    private static final class BlockingMap extends ConcurrentHashMap<CocoIdempotencyKey, InMemoryCocoIdempotencyStore.Entry> {
        private final CountDownLatch computeEntered = new CountDownLatch(1);
        private final CountDownLatch allowCompute = new CountDownLatch(1);
        @Override
        public InMemoryCocoIdempotencyStore.Entry compute(CocoIdempotencyKey key,
                BiFunction<? super CocoIdempotencyKey, ? super InMemoryCocoIdempotencyStore.Entry,
                        ? extends InMemoryCocoIdempotencyStore.Entry> function) {
            this.computeEntered.countDown();
            try {
                if (!this.allowCompute.await(5, TimeUnit.SECONDS)) { throw new AssertionError("compute was not released"); }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return super.compute(key, function);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return this.instant.get(); }
        private void advanceSeconds(long seconds) { this.instant.updateAndGet(value -> value.plusSeconds(seconds)); }
    }
}
