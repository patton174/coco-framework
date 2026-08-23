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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 进程内幂等租约参考存储。 */
public final class InMemoryCocoIdempotencyStore implements CocoIdempotencyStore, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoIdempotencyStore.class);
    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();
    private final ConcurrentMap<CocoIdempotencyKey, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxEntries;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 创建默认进程内存储。 */
    public InMemoryCocoIdempotencyStore(CocoIdempotencyProperties properties) {
        this(properties, Clock.systemUTC(), true);
    }

    InMemoryCocoIdempotencyStore(CocoIdempotencyProperties properties, Clock clock, boolean backgroundCleanupEnabled) {
        CocoIdempotencyProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.maxEntries = positive(checked.getMaxEntries(), "coco.idempotency.max-entries");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        Duration interval = checked.getCleanupInterval();
        if (interval == null || interval.isNegative()) { throw new IllegalArgumentException("coco.idempotency.cleanup-interval must not be negative"); }
        this.cleanupExecutor = backgroundCleanupEnabled && !interval.isZero() ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "coco-idempotency-cleanup"); thread.setDaemon(true); return thread;
        }) : null;
        if (this.cleanupExecutor != null) { this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpired, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS); }
        if (CLUSTER_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco idempotency is using process-local storage; configure a shared CocoIdempotencyStore for multi-instance production deployments");
        }
    }

    @Override
    public AcquireResult acquire(CocoIdempotencyLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        if (this.closed.get() || !lease.expiresAt().isAfter(this.clock.instant())) { return AcquireResult.UNAVAILABLE; }
        synchronized (this.entries) {
            Instant now = this.clock.instant();
            if (this.closed.get() || !lease.expiresAt().isAfter(now)) { return AcquireResult.UNAVAILABLE; }
            cleanupExpired(now);
            Entry current = this.entries.get(lease.key());
            if (current != null && current.expiresAt().isAfter(now)) { return AcquireResult.DUPLICATE; }
            if (current == null && this.entries.size() >= this.maxEntries) { return AcquireResult.UNAVAILABLE; }
            this.entries.put(lease.key(), new Entry(lease.ownerToken(), lease.expiresAt()));
            return AcquireResult.ACQUIRED;
        }
    }

    @Override
    public void release(CocoIdempotencyLease lease) {
        if (lease == null || this.closed.get()) { return; }
        this.entries.remove(lease.key(), new Entry(lease.ownerToken(), lease.expiresAt()));
    }

    /** 清理过期租约。 */
    public void cleanupExpired() { cleanupExpired(this.clock.instant()); }

    private void cleanupExpired(Instant now) {
        for (Map.Entry<CocoIdempotencyKey, Entry> entry : this.entries.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(now)) { this.entries.remove(entry.getKey(), entry.getValue()); }
        }
    }

    int size() { return this.entries.size(); }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            if (this.cleanupExecutor != null) { this.cleanupExecutor.shutdownNow(); }
            this.entries.clear();
        }
    }

    private static int positive(int value, String property) { if (value < 1) { throw new IllegalArgumentException(property + " must be positive"); } return value; }
    private record Entry(String ownerToken, Instant expiresAt) { }
}
