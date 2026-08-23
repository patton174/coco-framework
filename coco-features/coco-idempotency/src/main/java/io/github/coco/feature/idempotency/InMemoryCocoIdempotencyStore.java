package io.github.coco.feature.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 进程内幂等租约参考存储。 */
public final class InMemoryCocoIdempotencyStore implements CocoIdempotencyStore, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoIdempotencyStore.class);
    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();
    private final ConcurrentMap<CocoIdempotencyKey, Entry> entries;
    private final Clock clock;
    private final int maxEntries;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicInteger entryCount = new AtomicInteger();
    private final AtomicBoolean capacityCleanupRunning = new AtomicBoolean();
    private final AtomicLong nextCapacityCleanupAtMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicInteger capacityCleanupRuns = new AtomicInteger();
    private final long capacityCleanupCooldownMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();

    /** 创建默认进程内存储。 */
    public InMemoryCocoIdempotencyStore(CocoIdempotencyProperties properties) {
        this(properties, Clock.systemUTC(), true);
    }

    InMemoryCocoIdempotencyStore(CocoIdempotencyProperties properties, Clock clock, boolean backgroundCleanupEnabled) {
        this(properties, clock, backgroundCleanupEnabled, new ConcurrentHashMap<>());
    }

    InMemoryCocoIdempotencyStore(CocoIdempotencyProperties properties, Clock clock, boolean backgroundCleanupEnabled,
            ConcurrentMap<CocoIdempotencyKey, Entry> entries) {
        CocoIdempotencyProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.maxEntries = positive(checked.getMaxEntries(), "coco.idempotency.max-entries");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
        Duration interval = checked.getCleanupInterval();
        if (interval == null || interval.isNegative()) {
            throw new IllegalArgumentException("coco.idempotency.cleanup-interval must not be negative");
        }
        this.capacityCleanupCooldownMillis = Math.max(1L, interval.isZero() ? 1_000L : interval.toMillis());
        this.cleanupExecutor = backgroundCleanupEnabled && !interval.isZero() ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "coco-idempotency-cleanup");
            thread.setDaemon(true);
            return thread;
        }) : null;
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpired, interval.toMillis(), interval.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
        if (CLUSTER_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco idempotency is using process-local storage; configure a shared CocoIdempotencyStore for multi-instance production deployments");
        }
    }

    @Override
    public AcquireResult acquire(CocoIdempotencyLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        this.lifecycle.readLock().lock();
        try {
            Instant now = this.clock.instant();
            if (this.closed.get() || !lease.expiresAt().isAfter(now)) { return AcquireResult.UNAVAILABLE; }
            AcquireResult result = acquireCurrent(lease, now);
            if (result == AcquireResult.UNAVAILABLE && cleanupExpiredAtCapacity(now)) {
                result = acquireCurrent(lease, now);
            }
            if (this.closed.get() && result == AcquireResult.ACQUIRED) {
                removeCurrent(lease.key(), new Entry(lease.ownerToken(), lease.expiresAt()));
                return AcquireResult.UNAVAILABLE;
            }
            return result;
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    private AcquireResult acquireCurrent(CocoIdempotencyLease lease, Instant now) {
        AtomicBoolean acquired = new AtomicBoolean();
        AtomicBoolean duplicate = new AtomicBoolean();
        this.entries.compute(lease.key(), (key, current) -> {
            if (current != null && current.expiresAt().isAfter(now)) {
                duplicate.set(true);
                return current;
            }
            if (current != null) {
                acquired.set(true);
                return new Entry(lease.ownerToken(), lease.expiresAt());
            }
            if (!reserveWithoutCleanup()) { return null; }
            acquired.set(true);
            return new Entry(lease.ownerToken(), lease.expiresAt());
        });
        if (acquired.get()) { return AcquireResult.ACQUIRED; }
        return duplicate.get() ? AcquireResult.DUPLICATE : AcquireResult.UNAVAILABLE;
    }

    private boolean reserveWithoutCleanup() {
        while (true) {
            int current = this.entryCount.get();
            if (current >= this.maxEntries) { return false; }
            if (this.entryCount.compareAndSet(current, current + 1)) { return true; }
        }
    }

    @Override
    public void release(CocoIdempotencyLease lease) {
        if (lease == null || this.closed.get()) { return; }
        this.lifecycle.readLock().lock();
        try {
            if (!this.closed.get()) { removeCurrent(lease.key(), new Entry(lease.ownerToken(), lease.expiresAt())); }
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    /** 清理过期租约。 */
    public void cleanupExpired() {
        this.lifecycle.readLock().lock();
        try {
            if (!this.closed.get()) { cleanupExpired(this.clock.instant()); }
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    private boolean cleanupExpiredAtCapacity(Instant now) {
        long currentMillis = now.toEpochMilli();
        long nextAttempt = this.nextCapacityCleanupAtMillis.get();
        if (currentMillis < nextAttempt
                || !this.nextCapacityCleanupAtMillis.compareAndSet(nextAttempt,
                        saturatingAdd(currentMillis, this.capacityCleanupCooldownMillis))) {
            return false;
        }
        if (!this.capacityCleanupRunning.compareAndSet(false, true)) { return false; }
        try {
            this.capacityCleanupRuns.incrementAndGet();
            cleanupExpired(now);
            return true;
        }
        finally { this.capacityCleanupRunning.set(false); }
    }

    private void cleanupExpired(Instant now) {
        for (Map.Entry<CocoIdempotencyKey, Entry> entry : this.entries.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(now)) { removeCurrent(entry.getKey(), entry.getValue()); }
        }
    }

    private void removeCurrent(CocoIdempotencyKey key, Entry entry) {
        if (this.entries.remove(key, entry)) { this.entryCount.decrementAndGet(); }
    }

    int size() { return this.entries.size(); }

    int entryCount() { return this.entryCount.get(); }

    int capacityCleanupRuns() { return this.capacityCleanupRuns.get(); }

    boolean isClosed() { return this.closed.get(); }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) { return; }
        this.lifecycle.writeLock().lock();
        try {
            if (this.cleanupExecutor != null) { this.cleanupExecutor.shutdownNow(); }
            this.entries.clear();
            this.entryCount.set(0);
        }
        finally { this.lifecycle.writeLock().unlock(); }
    }

    private static int positive(int value, String property) {
        if (value < 1) { throw new IllegalArgumentException(property + " must be positive"); }
        return value;
    }

    private static long saturatingAdd(long value, long delta) {
        return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }

    record Entry(String ownerToken, Instant expiresAt) { }
}
