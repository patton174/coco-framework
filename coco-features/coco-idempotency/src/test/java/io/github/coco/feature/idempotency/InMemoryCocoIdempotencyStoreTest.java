package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class InMemoryCocoIdempotencyStoreTest {
    @Test
    void concurrentRequestsHaveExactlyOneWinner() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(10), clock, false)) {
            var executor = Executors.newFixedThreadPool(16);
            try {
                List<Callable<CocoIdempotencyStore.AcquireResult>> tasks = new ArrayList<>();
                for (int index = 0; index < 64; index++) {
                    int owner = index;
                    tasks.add(() -> store.acquire(lease("owner-" + owner, clock)));
                }
                List<Future<CocoIdempotencyStore.AcquireResult>> results = executor.invokeAll(tasks);
                assertThat(results.stream().map(this::result).filter(value -> value == CocoIdempotencyStore.AcquireResult.ACQUIRED)).hasSize(1);
                assertThat(store.size()).isOne();
            } finally { executor.shutdownNow(); }
        }
    }

    @Test
    void expirationAndFailureReleaseAllowRetryAndOldOwnerCannotDeleteNewLease() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(1), clock, false)) {
            CocoIdempotencyLease first = lease("old", clock, 5);
            assertThat(store.acquire(first)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            store.release(first);
            assertThat(store.acquire(lease("retry", clock, 5))).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            clock.advanceSeconds(5);
            CocoIdempotencyLease replacement = lease("new", clock, 5);
            assertThat(store.acquire(replacement)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            store.release(first);
            assertThat(store.acquire(lease("later", clock, 5))).isEqualTo(CocoIdempotencyStore.AcquireResult.DUPLICATE);
        }
    }

    @Test
    void capacityFailsClosedWithoutEvictingActiveLeaseAndOnlyStoresDigest() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(1), clock, false)) {
            CocoIdempotencyLease first = lease("one", clock);
            CocoIdempotencyLease second = new CocoIdempotencyLease(new CocoIdempotencyKey("default", "POST", "/two", "another-digest"), "two", clock.instant().plusSeconds(60));
            assertThat(store.acquire(first)).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
            assertThat(store.acquire(second)).isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
            assertThat(store.acquire(lease("three", clock))).isEqualTo(CocoIdempotencyStore.AcquireResult.DUPLICATE);
            assertThat(first.key().toString()).doesNotContain("raw-secret");
        }
    }

    @Test
    void cleanupAndCloseClearExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(properties(2), clock, false);
        assertThat(store.acquire(lease("one", clock, 1))).isEqualTo(CocoIdempotencyStore.AcquireResult.ACQUIRED);
        clock.advanceSeconds(1);
        store.cleanupExpired();
        assertThat(store.size()).isZero();
        store.close();
        assertThat(store.acquire(lease("two", clock))).isEqualTo(CocoIdempotencyStore.AcquireResult.UNAVAILABLE);
    }

    private CocoIdempotencyStore.AcquireResult result(Future<CocoIdempotencyStore.AcquireResult> future) {
        try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static CocoIdempotencyProperties properties(int entries) { CocoIdempotencyProperties result = new CocoIdempotencyProperties(); result.setMaxEntries(entries); return result; }
    private static CocoIdempotencyLease lease(String owner, Clock clock) { return lease(owner, clock, 60); }
    private static CocoIdempotencyLease lease(String owner, Clock clock, long seconds) { return new CocoIdempotencyLease(new CocoIdempotencyKey("default", "POST", "/orders", "digest"), owner, clock.instant().plusSeconds(seconds)); }
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return this.instant.get(); }
        private void advanceSeconds(long seconds) { this.instant.updateAndGet(value -> value.plusSeconds(seconds)); }
    }
}
