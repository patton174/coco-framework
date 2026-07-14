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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内 Coco 限流参考存储。
 * <p>
 * 同一个键通过 {@link ConcurrentHashMap#computeIfPresent(Object, java.util.function.BiFunction)} 原子计数；
 * 新键创建时使用短时容量锁，保证活动键数不超过配置上限。该实现的状态只存在于当前 JVM，启用后会输出多实例风险警告。
 * </p>
 */
public final class InMemoryCocoRateLimitStore implements CocoRateLimitStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoRateLimitStore.class);

    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<CocoRateLimitKey, Bucket> entries = new ConcurrentHashMap<>();

    private final ReentrantLock capacityLock = new ReentrantLock();

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
            this.cleanupExecutor.scheduleWithFixedDelay(() -> removeExpired(this.clock.instant()), cleanupIntervalSeconds,
                    cleanupIntervalSeconds, TimeUnit.SECONDS);
        }
        if (CLUSTER_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco rate-limit is using process-local storage; configure a shared CocoRateLimitStore for multi-instance production deployments");
        }
    }

    @Override
    public CocoRateLimitDecision acquire(CocoRateLimitPermit permit) {
        CocoRateLimitPermit checkedPermit = Objects.requireNonNull(permit, "permit must not be null");
        Instant now = this.clock.instant();
        if (this.closed.get() || !checkedPermit.resetAt().isAfter(now)) {
            return new CocoRateLimitDecision(false, checkedPermit.limit(), 0, checkedPermit.resetAt(), true);
        }
        CocoRateLimitDecision existingDecision = acquireExisting(checkedPermit, now);
        if (existingDecision != null) {
            return existingDecision;
        }
        return acquireNew(checkedPermit, now);
    }

    private CocoRateLimitDecision acquireExisting(CocoRateLimitPermit permit, Instant now) {
        AtomicReference<CocoRateLimitDecision> decision = new AtomicReference<>();
        this.entries.computeIfPresent(permit.key(), (key, bucket) -> {
            if (!bucket.resetAt().isAfter(now)) {
                return null;
            }
            long remaining = Math.max(0, permit.limit() - bucket.count());
            if (remaining == 0) {
                decision.set(new CocoRateLimitDecision(false, permit.limit(), 0, bucket.resetAt(), false));
                return bucket;
            }
            long updatedCount = bucket.count() + 1;
            decision.set(new CocoRateLimitDecision(true, permit.limit(), permit.limit() - updatedCount,
                    bucket.resetAt(), false));
            return new Bucket(updatedCount, bucket.resetAt());
        });
        return decision.get();
    }

    private CocoRateLimitDecision acquireNew(CocoRateLimitPermit permit, Instant now) {
        this.capacityLock.lock();
        try {
            removeExpired(now);
            Bucket existing = this.entries.get(permit.key());
            if (existing != null && existing.resetAt().isAfter(now)) {
                return acquireExisting(permit, now);
            }
            if (this.entries.size() >= this.maxEntries) {
                return new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), true);
            }
            this.entries.put(permit.key(), new Bucket(1, permit.resetAt()));
            return new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1, permit.resetAt(), false);
        }
        finally {
            this.capacityLock.unlock();
        }
    }

    private void removeExpired(Instant now) {
        this.entries.entrySet().removeIf(entry -> !entry.getValue().resetAt().isAfter(now));
    }

    int size() {
        return this.entries.size();
    }

    static void resetClusterWarningForTests() {
        CLUSTER_WARNING_LOGGED.set(false);
    }

    @Override
    public void close() {
        this.closed.set(true);
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.shutdownNow();
        }
        this.entries.clear();
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
