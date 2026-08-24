package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(store.renewed.await(2, TimeUnit.SECONDS)).isTrue();
            first.close();
            assertThat(store.released.get()).isOne();
        }
    }

    @Test
    void failedRenewalMarksLostBeforeBusinessCanReturnAndRejectsReentrancy() throws Exception {
        CocoLockProperties properties = properties();
        properties.setWatchdogInterval(Duration.ofMillis(1));
        BarrierStore store = new BarrierStore(CocoLockStore.RenewResult.NOT_OWNER);
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            assertThat(store.renewEntered.await(2, TimeUnit.SECONDS)).isTrue();
            store.allowRenew.countDown();
            assertThat(awaitLost(handle)).isTrue();
            assertThat(manager.tryAcquire(request("orders", Duration.ZERO)).status())
                    .isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
            assertThatThrownBy(handle::close).isInstanceOf(CocoLockException.class);
            assertThat(store.released.get()).isZero();
        }
    }

    @Test
    void managerCloseMarksActiveLocksLostAndStopsTheirRenewal() {
        CocoLockProperties properties = properties();
        properties.setWatchdogEnabled(false);
        BarrierStore store = new BarrierStore(CocoLockStore.RenewResult.RENEWED);
        DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC());
        CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
        manager.close();
        assertThat(handle.lost()).isTrue();
        assertThat(manager.tryAcquire(request("orders", Duration.ZERO)).status())
                .isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
        assertThatThrownBy(handle::close).isInstanceOf(CocoLockException.class);
        assertThat(store.released.get()).isZero();
    }

    @Test
    void renewalExceptionAlsoMarksLockLost() throws Exception {
        CocoLockProperties properties = properties();
        properties.setWatchdogInterval(Duration.ofMillis(1));
        BarrierStore store = new BarrierStore(new IllegalStateException("renew failure"));
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            assertThat(store.renewEntered.await(2, TimeUnit.SECONDS)).isTrue();
            store.allowRenew.countDown();
            assertThat(awaitLost(handle)).isTrue();
            assertThatThrownBy(handle::close).isInstanceOf(CocoLockException.class);
        }
    }

    @Test
    void unavailableRenewalAlsoMarksLockLost() throws Exception {
        CocoLockProperties properties = properties();
        properties.setWatchdogInterval(Duration.ofMillis(1));
        BarrierStore store = new BarrierStore(CocoLockStore.RenewResult.UNAVAILABLE);
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();
            assertThat(store.renewEntered.await(2, TimeUnit.SECONDS)).isTrue();
            store.allowRenew.countDown();
            assertThat(awaitLost(handle)).isTrue();
            assertThatThrownBy(handle::close).isInstanceOf(CocoLockException.class);
        }
    }

    @Test
    void duplicateCloseIsIdempotentAfterSuccessfulOuterRelease() {
        CocoLockProperties properties = properties();
        properties.setWatchdogEnabled(false);
        TrackingStore store = new TrackingStore();
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();

            handle.close();
            handle.close();

            assertThat(store.released.get()).isOne();
        }
    }

    @Test
    void rejectedStoreReleaseMarksTheHandleLostAndPropagatesLockException() {
        CocoLockProperties properties = properties();
        properties.setWatchdogEnabled(false);
        ReleaseFailureStore store = new ReleaseFailureStore();
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, properties, Clock.systemUTC())) {
            CocoLockHandle handle = manager.tryAcquire(request("orders", Duration.ZERO)).handle();

            assertThatThrownBy(handle::close).isInstanceOf(CocoLockException.class);
            assertThat(handle.lost()).isTrue();
            assertThat(store.released.get()).isOne();
            handle.close();
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

    private static boolean awaitLost(CocoLockHandle handle) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (handle.lost()) { return true; }
            Thread.onSpinWait();
        }
        return handle.lost();
    }

    private static final class TrackingStore implements CocoLockStore {
        private final CountDownLatch renewed = new CountDownLatch(1);
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
            this.renewed.countDown();
            return RenewResult.RENEWED;
        }
        @Override public synchronized boolean release(CocoLockLease candidate) {
            if (this.lease == null || !this.lease.ownerToken().equals(candidate.ownerToken())) { return false; }
            this.lease = null;
            this.released.incrementAndGet();
            return true;
        }
    }

    private static final class BarrierStore implements CocoLockStore {
        private final CocoLockStore.RenewResult renewResult;
        private final RuntimeException renewalFailure;
        private final CountDownLatch renewEntered = new CountDownLatch(1);
        private final CountDownLatch allowRenew = new CountDownLatch(1);
        private final AtomicInteger released = new AtomicInteger();
        private volatile CocoLockLease lease;
        private BarrierStore(CocoLockStore.RenewResult renewResult) {
            this.renewResult = renewResult;
            this.renewalFailure = null;
        }
        private BarrierStore(RuntimeException renewalFailure) {
            this.renewResult = null;
            this.renewalFailure = renewalFailure;
        }
        @Override public synchronized AcquireResult acquire(CocoLockLease candidate) {
            if (this.lease != null) { return AcquireResult.CONTENDED; }
            this.lease = candidate;
            return AcquireResult.ACQUIRED;
        }
        @Override public RenewResult renew(CocoLockLease candidate) {
            this.renewEntered.countDown();
            try {
                if (!this.allowRenew.await(2, TimeUnit.SECONDS)) { throw new AssertionError("renewal was not released"); }
            }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
            if (this.renewalFailure != null) { throw this.renewalFailure; }
            return this.renewResult;
        }
        @Override public synchronized boolean release(CocoLockLease candidate) {
            this.released.incrementAndGet();
            this.lease = null;
            return true;
        }
    }

    private static final class ReleaseFailureStore implements CocoLockStore {
        private final AtomicInteger released = new AtomicInteger();
        @Override public AcquireResult acquire(CocoLockLease candidate) { return AcquireResult.ACQUIRED; }
        @Override public RenewResult renew(CocoLockLease candidate) { return RenewResult.RENEWED; }
        @Override public boolean release(CocoLockLease candidate) {
            this.released.incrementAndGet();
            return false;
        }
    }
}
