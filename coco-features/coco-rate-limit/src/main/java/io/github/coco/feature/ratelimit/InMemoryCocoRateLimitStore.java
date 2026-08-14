package io.github.coco.feature.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内 Coco 限流参考存储。
 * <p>
 * 同一个键通过 {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} 原子计数；
 * 新键通过无锁容量预留保证活动键数不超过配置上限，因此不同键不会被同一个存储锁串行化。关闭流程使用共享生命周期门禁
 * 等待已经进入的请求完成，再清空全部状态。该实现的状态只存在于当前 JVM，启用后会输出多实例风险警告。
 * </p>
 */
public final class InMemoryCocoRateLimitStore implements CocoRateLimitStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoRateLimitStore.class);

    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();

    private static final long CLEANUP_TERMINATION_SECONDS = 5L;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final ConcurrentMap<CocoRateLimitKey, Bucket> entries;

    private final AtomicInteger activeEntryCount = new AtomicInteger();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);

    private final Lock lifecycleReadLock = this.lifecycleLock.readLock();

    private final Lock lifecycleWriteLock = this.lifecycleLock.writeLock();

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
        this(properties, clock, backgroundCleanupEnabled, new ConcurrentHashMap<>());
    }

    InMemoryCocoRateLimitStore(CocoRateLimitProperties properties, Clock clock, boolean backgroundCleanupEnabled,
            ConcurrentMap<CocoRateLimitKey, Bucket> entries) {
        CocoRateLimitProperties.InMemory inMemory = CocoRateLimitProperties.InMemory.copyOf(
                properties == null ? null : properties.getInMemory());
        this.maxEntries = positive(inMemory.getMaxEntries(), "coco.rate-limit.in-memory.max-entries");
        int cleanupIntervalSeconds = positive(inMemory.getCleanupIntervalSeconds(),
                "coco.rate-limit.in-memory.cleanup-interval-seconds");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
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
        if (this.closed.get()) {
            return unavailable(checkedPermit);
        }
        this.lifecycleReadLock.lock();
        try {
            Instant now = this.clock.instant();
            if (this.closed.get() || !checkedPermit.resetAt().isAfter(now)) {
                return unavailable(checkedPermit);
            }
            if (this.activeEntryCount.get() >= this.maxEntries) {
                removeExpired(now);
            }
            AtomicReference<CocoRateLimitDecision> decision = new AtomicReference<>();
            this.entries.compute(checkedPermit.key(),
                    (key, bucket) -> acquire(checkedPermit, now, bucket, decision));
            CocoRateLimitDecision resolved = decision.get();
            if (this.closed.get() || resolved == null) {
                return unavailable(checkedPermit);
            }
            return resolved;
        }
        finally {
            this.lifecycleReadLock.unlock();
        }
    }

    private Bucket acquire(CocoRateLimitPermit permit, Instant now, Bucket bucket,
            AtomicReference<CocoRateLimitDecision> decision) {
        if (this.closed.get()) {
            decision.set(unavailable(permit));
            return bucket;
        }
        if (bucket != null && bucket.resetAt().isAfter(now)) {
            long remaining = Math.max(0, permit.limit() - bucket.count());
            if (remaining == 0) {
                decision.set(new CocoRateLimitDecision(false, permit.limit(), 0, bucket.resetAt(), false));
                return bucket;
            }
            long updatedCount = bucket.count() + 1;
            decision.set(new CocoRateLimitDecision(true, permit.limit(), permit.limit() - updatedCount,
                    bucket.resetAt(), false));
            return new Bucket(updatedCount, bucket.resetAt());
        }
        boolean reserved = bucket == null;
        if (reserved && !reserveEntry()) {
            decision.set(unavailable(permit));
            return null;
        }
        if (this.closed.get()) {
            if (reserved) {
                releaseEntry();
            }
            decision.set(unavailable(permit));
            return bucket;
        }
        decision.set(new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1, permit.resetAt(), false));
        return new Bucket(1, permit.resetAt());
    }

    private boolean reserveEntry() {
        int current;
        do {
            current = this.activeEntryCount.get();
            if (current >= this.maxEntries) {
                return false;
            }
        }
        while (!this.activeEntryCount.compareAndSet(current, current + 1));
        return true;
    }

    private void releaseEntry() {
        this.activeEntryCount.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private void removeExpired(Instant now) {
        this.entries.forEach((key, bucket) -> {
            if (!bucket.resetAt().isAfter(now) && this.entries.remove(key, bucket)) {
                releaseEntry();
            }
        });
    }

    private void cleanupExpired() {
        if (this.closed.get()) {
            return;
        }
        this.lifecycleReadLock.lock();
        try {
            if (!this.closed.get()) {
                removeExpired(this.clock.instant());
            }
        }
        finally {
            this.lifecycleReadLock.unlock();
        }
    }

    int size() {
        return this.entries.size();
    }

    int activeEntryCount() {
        return this.activeEntryCount.get();
    }

    boolean isClosed() {
        return this.closed.get();
    }

    static void resetClusterWarningForTests() {
        CLUSTER_WARNING_LOGGED.set(false);
    }

    @Override
    public void close() {
        this.closed.set(true);
        this.lifecycleWriteLock.lock();
        try {
            this.entries.clear();
            this.activeEntryCount.set(0);
        }
        finally {
            this.lifecycleWriteLock.unlock();
        }
        if (this.cleanupExecutor != null) {
            shutdownCleanupExecutor(this.cleanupExecutor);
        }
    }

    static void shutdownCleanupExecutor(ScheduledExecutorService cleanupExecutor) {
        cleanupExecutor.shutdownNow();
        try {
            if (!cleanupExecutor.awaitTermination(CLEANUP_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Coco rate-limit cleanup executor did not terminate after close");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for Coco rate-limit cleanup executor to terminate", exception);
        }
    }

    private static CocoRateLimitDecision unavailable(CocoRateLimitPermit permit) {
        return new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), true);
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    record Bucket(long count, Instant resetAt) {
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
