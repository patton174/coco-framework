package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class DefaultCocoLockManagerTest {

    @Test
    void supportsReentrancyAndOnlyOuterCloseReleases() throws Exception {
        CocoLockProperties properties = properties();
        try (InMemoryCocoLockStore store = new InMemoryCocoLockStore(properties, Clock.systemUTC(), false);
                DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle outer = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            CocoLockHandle nested = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            assertThat(nested.reentrant()).isTrue();
            outer.close();
            assertThat(acquireOnOtherThread(manager, "orders", Duration.ZERO)).isEqualTo(CocoLockStore.AcquireResult.CONTENDED);
            nested.close();
            assertThat(acquireOnOtherThread(manager, "orders", Duration.ZERO)).isEqualTo(CocoLockStore.AcquireResult.ACQUIRED);
        }
    }

    @Test
    void waitsThenTimesOutAndWatchdogRenews() throws Exception {
        CocoLockProperties properties = properties();
        properties.setWatchdogInterval(Duration.ofMillis(5));
        TrackingStore store = new TrackingStore();
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle first = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            assertThat(acquireOnOtherThread(manager, "orders", Duration.ofMillis(40))).isEqualTo(CocoLockStore.AcquireResult.CONTENDED);
            assertThat(waitFor(() -> store.renewed.get() > 0)).isTrue();
            first.close();
            assertThat(store.released.get()).isOne();
        }
    }

    private static CocoLockProperties properties() {
        CocoLockProperties properties = new CocoLockProperties();
        properties.setLease(Duration.ofMillis(80));
        properties.setPollInterval(Duration.ofMillis(5));
        properties.setWatchdogEnabled(true);
        return properties;
    }

    private static CocoLockRequest request(String key, Duration wait) {
        return new CocoLockRequest(key, Duration.ofMillis(80), wait, Duration.ofMillis(5));
    }

    private static CocoLockStore.AcquireResult acquireOnOtherThread(CocoLockManager manager, String key, Duration wait)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CocoLockStore.AcquireResult> future = executor.submit(() -> manager.tryAcquire(request(key, wait)).status());
            return future.get(5, TimeUnit.SECONDS);
        }
        finally { executor.shutdownNow(); }
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) { return true; }
            Thread.sleep(5);
        }
        return condition.getAsBoolean();
    }

    private static final class TrackingStore implements CocoLockStore {
        private final AtomicInteger renewed = new AtomicInteger();
        private final AtomicInteger released = new AtomicInteger();
        private volatile CocoLockLease lease;
        @Override public synchronized AcquireResult acquire(CocoLockLease candidate) {
            if (this.lease != null) { return AcquireResult.CONTENDED; }
            this.lease = candidate;
            return AcquireResult.ACQUIRED;
        }
        @Override public synchronized RenewResult renew(CocoLockLease candidate) {
            if (this.lease == null || !this.lease.ownerToken().equals(candidate.ownerToken())) { return RenewResult.NOT_OWNER; }
            this.lease = candidate;
            this.renewed.incrementAndGet();
            return RenewResult.RENEWED;
        }
        @Override public synchronized boolean release(CocoLockLease candidate) {
            if (this.lease == null || !this.lease.ownerToken().equals(candidate.ownerToken())) { return false; }
            this.lease = null;
            this.released.incrementAndGet();
            return true;
        }
    }
}
