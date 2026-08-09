package io.github.coco.feature.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内 Coco 限流参考存储。
 * <p>
 * 所有窗口计数、容量判断、过期回收和写入都由同一个短时状态锁保护，作为一个原子存储操作完成；
 * 该实现的状态只存在于当前 JVM，启用后会输出多实例风险警告。
 * </p>
 */
public final class InMemoryCocoRateLimitStore implements CocoRateLimitStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoRateLimitStore.class);

    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<CocoRateLimitKey, Bucket> entries = new ConcurrentHashMap<>();

    /**
     * 保护整个内存存储操作，确保计数、容量判断和 TTL 写入不会被拆成多个可观察步骤。
     */
    private final ReentrantLock stateLock = new ReentrantLock();

    private final int maxEntries;

    private final Clock clock;

    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 创建进程内限流参考存储。
     * @param properties 限流配置
     */
    public InMemoryCocoRateLimitStore(CocoRateLimitProperties properties) {
        this(properties, Clock.systemUTC(), true);
    }

    InMemoryCocoRateLimitStore(CocoRateLimitProperties properties, Clock clock, boolean backgroundCleanupEnabled) {
        CocoRateLimitProperties.InMemory inMemory = CocoRateLimitProperties.InMemory.copyOf(
                properties == null ? null : properties.getInMemory());
        this.maxEntries = positive(inMemory.getMaxEntries(), "coco.rate-limit.in-memory.max-entries");
        int cleanupIntervalSeconds = positive(inMemory.getCleanupIntervalSeconds(),
                "coco.rate-limit.in-memory.cleanup-interval-seconds");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.cleanupExecutor = backgroundCleanupEnabled
                ? Executors.newSingleThreadScheduledExecutor(new CleanupThreadFactory())
                : null;
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpired, cleanupIntervalSeconds,
                    cleanupIntervalSeconds, TimeUnit.SECONDS);
        }
        if (CLUSTER_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco rate-limit is using process-local storage; configure a shared CocoRateLimitStore for multi-instance production deployments");
        }
    }

    @Override
    public CocoRateLimitDecision acquire(CocoRateLimitPermit permit) {
        CocoRateLimitPermit checkedPermit = Objects.requireNonNull(permit, "permit must not be null");
        this.stateLock.lock();
        try {
            Instant now = this.clock.instant();
            if (this.closed.get() || !checkedPermit.resetAt().isAfter(now)) {
                return new CocoRateLimitDecision(false, checkedPermit.limit(), 0, checkedPermit.resetAt(), true);
            }
            removeExpired(now);
            return acquireNew(checkedPermit, now);
        }
        finally {
            this.stateLock.unlock();
        }
    }

    private CocoRateLimitDecision acquireNew(CocoRateLimitPermit permit, Instant now) {
        Bucket existing = this.entries.get(permit.key());
        if (existing != null && existing.resetAt().isAfter(now)) {
            return acquireExisting(permit, existing);
        }
        if (this.entries.size() >= this.maxEntries) {
            return new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), true);
        }
        this.entries.put(permit.key(), new Bucket(1, permit.resetAt()));
        return new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1, permit.resetAt(), false);
    }

    private CocoRateLimitDecision acquireExisting(CocoRateLimitPermit permit, Bucket bucket) {
        long remaining = Math.max(0, permit.limit() - bucket.count());
        if (remaining == 0) {
            return new CocoRateLimitDecision(false, permit.limit(), 0, bucket.resetAt(), false);
        }
        long updatedCount = bucket.count() + 1;
        this.entries.put(permit.key(), new Bucket(updatedCount, bucket.resetAt()));
        return new CocoRateLimitDecision(true, permit.limit(), permit.limit() - updatedCount,
                bucket.resetAt(), false);
    }

    private void removeExpired(Instant now) {
        this.entries.entrySet().removeIf(entry -> !entry.getValue().resetAt().isAfter(now));
    }

    private void cleanupExpired() {
        if (this.closed.get() || !this.stateLock.tryLock()) {
            return;
        }
        try {
            if (!this.closed.get()) {
                removeExpired(this.clock.instant());
            }
        }
        finally {
            this.stateLock.unlock();
        }
    }

    int size() {
        return this.entries.size();
    }

    static void resetClusterWarningForTests() {
        CLUSTER_WARNING_LOGGED.set(false);
    }

    @Override
    public void close() {
        this.stateLock.lock();
        try {
            if (this.closed.compareAndSet(false, true)) {
                this.entries.clear();
            }
        }
        finally {
            this.stateLock.unlock();
        }
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.shutdownNow();
            try {
                if (!this.cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Coco rate-limit cleanup executor did not terminate after close");
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for Coco rate-limit cleanup executor to terminate",
                        exception);
            }
        }
    }

    boolean cleanupExecutorTerminated() {
        return this.cleanupExecutor == null || this.cleanupExecutor.isTerminated();
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record Bucket(long count, Instant resetAt) {
    }

    private static final class CleanupThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "coco-rate-limit-cleanup");
            thread.setDaemon(true);
            return thread;
        }
    }
}
