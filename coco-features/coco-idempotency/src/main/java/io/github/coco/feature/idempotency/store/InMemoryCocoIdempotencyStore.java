package io.github.coco.feature.idempotency.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 受容量和 TTL 限制的进程内幂等存储参考实现。
 * <p>该实现只保证单 JVM 内的状态一致性，不适用于多实例共享幂等。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class InMemoryCocoIdempotencyStore implements CocoIdempotencyStore {

    private final Object monitor = new Object();

    private final int maxEntries;

    private final Clock clock;

    private final Map<String, Entry> entries = new HashMap<>();

    private final ScheduledExecutorService cleanupExecutor;

    private boolean closed;

    /**
     * 创建进程内存储。
     * @param maxEntries 最大记录数
     * @param cleanupInterval 过期记录清理间隔
     * @param clock 存储时钟
     */
    public InMemoryCocoIdempotencyStore(int maxEntries, Duration cleanupInterval, Clock clock) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        Duration checkedInterval = Objects.requireNonNull(cleanupInterval, "cleanupInterval must not be null");
        if (checkedInterval.isZero() || checkedInterval.isNegative()) {
            throw new IllegalArgumentException("cleanupInterval must be positive");
        }
        this.maxEntries = maxEntries;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coco-idempotency-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpiredSafely,
                checkedInterval.toMillis(), checkedInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
        CocoIdempotencyRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!checkedExpiresAt.isAfter(checkedNow)) {
            throw new IllegalArgumentException("expiresAt must be after now");
        }
        synchronized (this.monitor) {
            requireOpen();
            Entry current = this.entries.get(checkedRequest.keyHash());
            if (current != null && !current.expiresAt().isAfter(checkedNow)) {
                this.entries.remove(checkedRequest.keyHash());
                current = null;
            }
            if (current != null) {
                if (!current.requestHash().equals(checkedRequest.requestHash())) {
                    return CocoIdempotencyAcquireResult.payloadMismatch();
                }
                if (current.response() != null) {
                    return CocoIdempotencyAcquireResult.replay(current.response());
                }
                return CocoIdempotencyAcquireResult.inProgress();
            }
            cleanupExpired(checkedNow);
            if (this.entries.size() >= this.maxEntries) {
                return CocoIdempotencyAcquireResult.capacityExceeded();
            }
            String ownerToken = UUID.randomUUID().toString();
            CocoIdempotencyLease lease = new CocoIdempotencyLease(checkedRequest, ownerToken, checkedExpiresAt);
            this.entries.put(checkedRequest.keyHash(), Entry.inProgress(lease));
            return CocoIdempotencyAcquireResult.acquired(lease);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        CocoIdempotencyStoredResponse checkedResponse = Objects.requireNonNull(response, "response must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        synchronized (this.monitor) {
            requireOpen();
            Entry current = this.entries.get(checkedLease.request().keyHash());
            if (!matchesActiveLease(current, checkedLease, checkedNow)) {
                removeExpiredLease(current, checkedLease, checkedNow);
                return false;
            }
            this.entries.put(checkedLease.request().keyHash(), current.complete(checkedResponse));
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean fail(CocoIdempotencyLease lease, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        synchronized (this.monitor) {
            requireOpen();
            Entry current = this.entries.get(checkedLease.request().keyHash());
            if (!matchesActiveLease(current, checkedLease, checkedNow)) {
                removeExpiredLease(current, checkedLease, checkedNow);
                return false;
            }
            this.entries.remove(checkedLease.request().keyHash());
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        synchronized (this.monitor) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.entries.clear();
        }
        this.cleanupExecutor.shutdownNow();
    }

    int activeEntries() {
        synchronized (this.monitor) {
            return this.entries.size();
        }
    }

    private void cleanupExpiredSafely() {
        synchronized (this.monitor) {
            if (!this.closed) {
                cleanupExpired(this.clock.instant());
            }
        }
    }

    private void cleanupExpired(Instant now) {
        Iterator<Entry> iterator = this.entries.values().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().expiresAt().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private void requireOpen() {
        if (this.closed) {
            throw new IllegalStateException("Coco idempotency store is closed");
        }
    }

    private void removeExpiredLease(Entry current, CocoIdempotencyLease lease, Instant now) {
        if (current != null && current.matches(lease) && !current.expiresAt().isAfter(now)) {
            this.entries.remove(lease.request().keyHash());
        }
    }

    private static boolean matchesActiveLease(Entry current, CocoIdempotencyLease lease, Instant now) {
        return current != null && current.response() == null && current.matches(lease)
                && current.expiresAt().isAfter(now);
    }

    private record Entry(String requestHash, String ownerToken, Instant expiresAt,
            CocoIdempotencyStoredResponse response) {

        private static Entry inProgress(CocoIdempotencyLease lease) {
            return new Entry(lease.request().requestHash(), lease.ownerToken(), lease.expiresAt(), null);
        }

        private Entry complete(CocoIdempotencyStoredResponse completedResponse) {
            return new Entry(this.requestHash, this.ownerToken, this.expiresAt, completedResponse);
        }

        private boolean matches(CocoIdempotencyLease lease) {
            return this.requestHash.equals(lease.request().requestHash())
                    && this.ownerToken.equals(lease.ownerToken())
                    && this.expiresAt.equals(lease.expiresAt());
        }
    }
}
