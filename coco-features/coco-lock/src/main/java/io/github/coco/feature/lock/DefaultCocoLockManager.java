package io.github.coco.feature.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认锁管理器，提供有限等待、同线程重入和租约看门狗。 */
public final class DefaultCocoLockManager implements CocoLockManager, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCocoLockManager.class);
    private final CocoLockStore store;
    private final CocoLockProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService watchdogExecutor;
    private final ThreadLocal<Map<String, HeldLock>> heldLocks = ThreadLocal.withInitial(HashMap::new);
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 创建管理器。 */
    public DefaultCocoLockManager(CocoLockStore store, CocoLockProperties properties, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.watchdogExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coco-lock-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CocoLockResult tryAcquire(CocoLockRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (this.closed.get()) { return new CocoLockResult(CocoLockStore.AcquireResult.UNAVAILABLE, null); }
        Map<String, HeldLock> currentLocks = this.heldLocks.get();
        HeldLock existing = currentLocks.get(request.key());
        if (existing != null && !existing.closed.get()) {
            existing.reentrancy.incrementAndGet();
            return new CocoLockResult(CocoLockStore.AcquireResult.ACQUIRED, new Handle(existing, true));
        }
        long deadline = deadline(request.waitDuration());
        do {
            CocoLockLease lease = new CocoLockLease(request.key(), UUID.randomUUID().toString(),
                    this.clock.instant().plus(request.lease()));
            CocoLockStore.AcquireResult result = this.store.acquire(lease);
            if (result == CocoLockStore.AcquireResult.ACQUIRED) {
                HeldLock held = new HeldLock(lease, request.lease(), Thread.currentThread());
                currentLocks.put(request.key(), held);
                scheduleWatchdog(held);
                return new CocoLockResult(result, new Handle(held, false));
            }
            if (result == CocoLockStore.AcquireResult.UNAVAILABLE || System.nanoTime() >= deadline) {
                return new CocoLockResult(result, null);
            }
            sleepUntilNextPoll(request.pollInterval(), deadline);
        }
        while (true);
    }

    private void scheduleWatchdog(HeldLock held) {
        if (!this.properties.isWatchdogEnabled()) { return; }
        Duration configured = positive(this.properties.getWatchdogInterval(), "coco.lock.watchdog-interval");
        long leaseMillis = Math.max(1L, held.leaseDuration.toMillis());
        long periodMillis = Math.max(1L, Math.min(configured.toMillis(), Math.max(1L, leaseMillis / 3L)));
        held.watchdog = this.watchdogExecutor.scheduleWithFixedDelay(() -> renew(held), periodMillis, periodMillis,
                TimeUnit.MILLISECONDS);
    }

    private void renew(HeldLock held) {
        if (this.closed.get() || held.closed.get()) { return; }
        CocoLockLease renewal = new CocoLockLease(held.lease.get().key(), held.lease.get().ownerToken(),
                this.clock.instant().plus(held.leaseDuration));
        CocoLockStore.RenewResult result = this.store.renew(renewal);
        if (result == CocoLockStore.RenewResult.RENEWED) {
            held.lease.set(renewal);
            return;
        }
        if (held.warnedLost.compareAndSet(false, true)) {
            LOGGER.warn("Coco lock lease was not renewed; keyHash={}, result={}",
                    Integer.toUnsignedString(renewal.key().hashCode(), 16), result);
        }
    }

    private void release(HeldLock held) {
        if (held.ownerThread != Thread.currentThread()) {
            throw new IllegalStateException("CocoLockHandle must be closed by its acquiring thread");
        }
        if (held.reentrancy.decrementAndGet() != 0) { return; }
        if (!held.closed.compareAndSet(false, true)) { return; }
        if (held.watchdog != null) { held.watchdog.cancel(false); }
        this.store.release(held.lease.get());
        Map<String, HeldLock> currentLocks = this.heldLocks.get();
        currentLocks.remove(held.lease.get().key(), held);
        if (currentLocks.isEmpty()) { this.heldLocks.remove(); }
    }

    private static long deadline(Duration wait) {
        long nanos = wait.toNanos();
        long now = System.nanoTime();
        return nanos >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + nanos;
    }

    private static void sleepUntilNextPoll(Duration pollInterval, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) { return; }
        long nanos = Math.min(pollInterval.toNanos(), remaining);
        try { TimeUnit.NANOSECONDS.sleep(nanos); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw CocoLockErrorCode.INTERRUPTED.system();
        }
    }

    private static Duration positive(Duration value, String property) {
        value = Objects.requireNonNull(value, property + " must not be null");
        if (value.isZero() || value.isNegative()) { throw new IllegalArgumentException(property + " must be positive"); }
        return value;
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) { this.watchdogExecutor.shutdownNow(); }
    }

    private final class Handle implements CocoLockHandle {
        private final HeldLock held;
        private final boolean reentrant;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Handle(HeldLock held, boolean reentrant) { this.held = held; this.reentrant = reentrant; }
        @Override public CocoLockLease lease() { return this.held.lease.get(); }
        @Override public boolean reentrant() { return this.reentrant; }
        @Override public void close() { if (this.closed.compareAndSet(false, true)) { release(this.held); } }
    }

    private static final class HeldLock {
        private final AtomicReference<CocoLockLease> lease;
        private final Duration leaseDuration;
        private final Thread ownerThread;
        private final AtomicInteger reentrancy = new AtomicInteger(1);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean warnedLost = new AtomicBoolean();
        private volatile ScheduledFuture<?> watchdog;
        private HeldLock(CocoLockLease lease, Duration leaseDuration, Thread ownerThread) {
            this.lease = new AtomicReference<>(lease);
            this.leaseDuration = leaseDuration;
            this.ownerThread = ownerThread;
        }
    }
}
