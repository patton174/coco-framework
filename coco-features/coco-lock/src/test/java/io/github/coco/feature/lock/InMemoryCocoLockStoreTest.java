package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

class InMemoryCocoLockStoreTest {

    @Test
    void sameKeyHasOneWinnerAndCapacityIsStrict() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try (InMemoryCocoLockStore store = new InMemoryCocoLockStore(properties(8), clock, false)) {
            CountDownLatch ready = new CountDownLatch(32);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CocoLockStore.AcquireResult>> results = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                int owner = index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return store.acquire(lease("same", "owner-" + owner, clock, 60));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(results.stream().map(this::get).filter(result -> result == CocoLockStore.AcquireResult.ACQUIRED))
                    .hasSize(1);
            assertThat(store.size()).isOne();
            for (int index = 0; index < 20; index++) {
                store.acquire(lease("key-" + index, "other-" + index, clock, 60));
            }
            assertThat(store.size()).isEqualTo(8);
            assertThat(store.entryCount()).isEqualTo(8);
        }
        finally { executor.shutdownNow(); }
    }

    @Test
    void expiryAllowsTakeoverAndOldOwnerCannotReleaseOrRenew() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        try (InMemoryCocoLockStore store = new InMemoryCocoLockStore(properties(2), clock, false)) {
            CocoLockLease oldLease = lease("orders", "old", clock, 1);
            assertThat(store.acquire(oldLease)).isEqualTo(CocoLockStore.AcquireResult.ACQUIRED);
            clock.advanceSeconds(1);
            CocoLockLease newLease = lease("orders", "new", clock, 60);
            assertThat(store.acquire(newLease)).isEqualTo(CocoLockStore.AcquireResult.ACQUIRED);
            assertThat(store.release(oldLease)).isFalse();
            assertThat(store.renew(lease("orders", "old", clock, 60))).isEqualTo(CocoLockStore.RenewResult.NOT_OWNER);
            assertThat(store.acquire(lease("orders", "later", clock, 60))).isEqualTo(CocoLockStore.AcquireResult.CONTENDED);
            assertThat(store.release(newLease)).isTrue();
        }
    }

    @Test
    void closeAndAcquireShareOneLifecycleProtocol() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        BlockingMap entries = new BlockingMap();
        InMemoryCocoLockStore store = new InMemoryCocoLockStore(properties(2), clock, false, entries);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CocoLockStore.AcquireResult> acquire = executor.submit(() -> store.acquire(lease("orders", "owner", clock, 60)));
            assertThat(entries.computeEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> close = executor.submit(store::close);
            assertThat(awaitClosed(store)).isTrue();
            entries.allowCompute.countDown();
            assertThat(get(acquire)).isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
            close.get(5, TimeUnit.SECONDS);
            assertThat(store.acquire(lease("other", "after", clock, 60))).isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
        }
        finally { executor.shutdownNow(); store.close(); }
    }

    private CocoLockStore.AcquireResult get(Future<CocoLockStore.AcquireResult> future) {
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static boolean awaitClosed(InMemoryCocoLockStore store) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!store.isClosed() && System.nanoTime() < deadline) { Thread.onSpinWait(); }
        return store.isClosed();
    }

    private static CocoLockProperties properties(int maxEntries) {
        CocoLockProperties properties = new CocoLockProperties();
        properties.setMaxEntries(maxEntries);
        return properties;
    }

    private static CocoLockLease lease(String key, String owner, Clock clock, long seconds) {
        return new CocoLockLease(key, owner, clock.instant().plusSeconds(seconds));
    }

    private static final class BlockingMap extends ConcurrentHashMap<String, InMemoryCocoLockStore.Entry> {
        private final CountDownLatch computeEntered = new CountDownLatch(1);
        private final CountDownLatch allowCompute = new CountDownLatch(1);
        @Override
        public InMemoryCocoLockStore.Entry compute(String key,
                BiFunction<? super String, ? super InMemoryCocoLockStore.Entry,
                        ? extends InMemoryCocoLockStore.Entry> function) {
            this.computeEntered.countDown();
            try { assertThat(this.allowCompute.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
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
