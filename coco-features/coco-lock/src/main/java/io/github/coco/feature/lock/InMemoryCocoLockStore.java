package io.github.coco.feature.lock;

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
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内 {@link CocoLockStore} 参考实现。
 * <p>它仅适合单实例或测试；多实例必须替换为共享 Store。容量计数在 key 计算中预留，以避免并发超限。</p>
 */
public final class InMemoryCocoLockStore implements CocoLockStore, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoLockStore.class);
    private final ConcurrentMap<String, Entry> entries;
    private final Clock clock;
    private final int maxEntries;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicInteger entryCount = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();

    /** 使用系统 UTC 时钟创建默认存储。 */
    public InMemoryCocoLockStore(CocoLockProperties properties) {
        this(properties, Clock.systemUTC(), true);
    }

    /** 使用指定时钟创建默认存储。 */
    public InMemoryCocoLockStore(CocoLockProperties properties, Clock clock, boolean backgroundCleanupEnabled) {
        this(properties, clock, backgroundCleanupEnabled, new ConcurrentHashMap<>());
    }

    InMemoryCocoLockStore(CocoLockProperties properties, Clock clock, boolean backgroundCleanupEnabled,
            ConcurrentMap<String, Entry> entries) {
        CocoLockProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.maxEntries = positive(checked.getMaxEntries(), "coco.lock.max-entries");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
        Duration interval = nonNegative(checked.getCleanupInterval(), "coco.lock.cleanup-interval");
        this.cleanupExecutor = backgroundCleanupEnabled && !interval.isZero()
                ? Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "coco-lock-cleanup");
                    thread.setDaemon(true);
                    return thread;
                }) : null;
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpired, interval.toMillis(), interval.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
        LOGGER.warn("Coco lock is using process-local storage; configure a shared CocoLockStore for multi-instance production deployments");
    }

    @Override
    public AcquireResult acquire(CocoLockLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        this.lifecycle.readLock().lock();
        try {
            Instant now = this.clock.instant();
            if (this.closed.get() || !lease.expiresAt().isAfter(now)) { return AcquireResult.UNAVAILABLE; }
            AcquireResult result = acquireCurrent(lease, now);
            if (result == AcquireResult.UNAVAILABLE) {
                cleanupExpired(now);
                result = acquireCurrent(lease, now);
            }
            if (this.closed.get() && result == AcquireResult.ACQUIRED) {
                removeOwned(lease.key(), lease.ownerToken());
                return AcquireResult.UNAVAILABLE;
            }
            return result;
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    private AcquireResult acquireCurrent(CocoLockLease lease, Instant now) {
        AtomicBoolean acquired = new AtomicBoolean();
        AtomicBoolean contended = new AtomicBoolean();
        this.entries.compute(lease.key(), (key, current) -> {
            if (current != null && current.expiresAt().isAfter(now)) {
                contended.set(true);
                return current;
            }
            if (current != null) {
                acquired.set(true);
                return Entry.from(lease);
            }
            if (!reserve()) { return null; }
            acquired.set(true);
            return Entry.from(lease);
        });
        if (acquired.get()) { return AcquireResult.ACQUIRED; }
        return contended.get() ? AcquireResult.CONTENDED : AcquireResult.UNAVAILABLE;
    }

    @Override
    public RenewResult renew(CocoLockLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        this.lifecycle.readLock().lock();
        try {
            Instant now = this.clock.instant();
            if (this.closed.get() || !lease.expiresAt().isAfter(now)) { return RenewResult.UNAVAILABLE; }
            AtomicBoolean renewed = new AtomicBoolean();
            AtomicBoolean removed = new AtomicBoolean();
            this.entries.compute(lease.key(), (key, current) -> {
                if (current == null) { return null; }
                if (!current.expiresAt().isAfter(now)) {
                    removed.set(true);
                    return null;
                }
                if (!current.ownerToken().equals(lease.ownerToken())) { return current; }
                renewed.set(true);
                return Entry.from(lease);
            });
            if (removed.get()) { this.entryCount.decrementAndGet(); }
            return renewed.get() ? RenewResult.RENEWED : RenewResult.NOT_OWNER;
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    @Override
    public boolean release(CocoLockLease lease) {
        if (lease == null) { return false; }
        this.lifecycle.readLock().lock();
        try {
            if (this.closed.get()) { return false; }
            return removeOwned(lease.key(), lease.ownerToken());
        }
        finally { this.lifecycle.readLock().unlock(); }
    }

    /** 主动清理过期租约。 */
    public void cleanupExpired() {
        this.lifecycle.readLock().lock();
        try { if (!this.closed.get()) { cleanupExpired(this.clock.instant()); } }
        finally { this.lifecycle.readLock().unlock(); }
    }

    private void cleanupExpired(Instant now) {
        for (Map.Entry<String, Entry> entry : this.entries.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(now)
                    && this.entries.remove(entry.getKey(), entry.getValue())) {
                this.entryCount.decrementAndGet();
            }
        }
    }

    private boolean removeOwned(String key, String ownerToken) {
        AtomicBoolean removed = new AtomicBoolean();
        this.entries.computeIfPresent(key, (ignored, current) -> {
            if (current.ownerToken().equals(ownerToken)) {
                removed.set(true);
                return null;
            }
            return current;
        });
        if (removed.get()) { this.entryCount.decrementAndGet(); }
        return removed.get();
    }

    private boolean reserve() {
        while (true) {
            int current = this.entryCount.get();
            if (current >= this.maxEntries) { return false; }
            if (this.entryCount.compareAndSet(current, current + 1)) { return true; }
        }
    }

    int size() { return this.entries.size(); }
    int entryCount() { return this.entryCount.get(); }
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

    private static Duration nonNegative(Duration value, String property) {
        value = Objects.requireNonNull(value, property + " must not be null");
        if (value.isNegative()) { throw new IllegalArgumentException(property + " must not be negative"); }
        return value;
    }

    record Entry(String ownerToken, Instant expiresAt) {
        static Entry from(CocoLockLease lease) { return new Entry(lease.ownerToken(), lease.expiresAt()); }
    }
}
