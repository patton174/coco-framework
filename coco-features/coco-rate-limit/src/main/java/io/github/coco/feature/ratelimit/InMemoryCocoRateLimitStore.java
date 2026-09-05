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
            if (this.closed.get()) {
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
        // A never-before-seen key needs a capacity slot before it can create state.
        // Existing keys already hold one, so only reserve on the null->present edge.
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
        Bucket updated = switch (permit.algorithm()) {
            case FIXED_WINDOW -> acquireFixedWindow(permit, now, bucket, decision);
            case SLIDING_WINDOW -> acquireSlidingWindow(permit, now, bucket, decision);
            case TOKEN_BUCKET -> acquireTokenBucket(permit, now, bucket, decision);
        };
        // If a freshly reserved key ends up with no stored state, hand the slot back.
        if (reserved && updated == null) {
            releaseEntry();
        }
        return updated;
    }

    private static Bucket acquireFixedWindow(CocoRateLimitPermit permit, Instant now, Bucket bucket,
            AtomicReference<CocoRateLimitDecision> decision) {
        Instant resetAt = fixedWindowResetAt(now, permit.windowSeconds());
        long count = bucket != null && bucket.expiresAt().isAfter(now) ? bucket.count() : 0;
        if (count >= permit.limit()) {
            decision.set(new CocoRateLimitDecision(false, permit.limit(), 0, resetAt, false));
            return new Bucket(count, 0, resetAt);
        }
        long updatedCount = count + 1;
        decision.set(new CocoRateLimitDecision(true, permit.limit(), permit.limit() - updatedCount, resetAt, false));
        return new Bucket(updatedCount, 0, resetAt);
    }

    private static Bucket acquireSlidingWindow(CocoRateLimitPermit permit, Instant now, Bucket bucket,
            AtomicReference<CocoRateLimitDecision> decision) {
        long windowSeconds = permit.windowSeconds();
        long windowStart = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        long currentCount = 0;
        long previousCount = 0;
        if (bucket != null) {
            if (bucket.windowStart() == windowStart) {
                currentCount = bucket.count();
                previousCount = bucket.previousCount();
            }
            else if (bucket.windowStart() == windowStart - windowSeconds) {
                // The new request falls in the window immediately after the stored one,
                // so the stored current-count becomes the previous-window count.
                previousCount = bucket.count();
            }
            // Older than one window back: both counts are already 0 (a full reset).
        }
        // Weight the previous window by the fraction of it still overlapping the
        // trailing `windowSeconds` that end at `now`. This is the sliding-window
        // counter approximation: cheap (two counters) yet free of the fixed-window
        // 2x boundary burst.
        double elapsed = now.getEpochSecond() - windowStart + now.getNano() / 1_000_000_000.0;
        double previousWeight = Math.max(0.0, (windowSeconds - elapsed) / windowSeconds);
        double estimated = previousCount * previousWeight + currentCount;
        Instant resetAt = Instant.ofEpochSecond(windowStart + windowSeconds);
        Instant expiresAt = Instant.ofEpochSecond(windowStart + 2 * windowSeconds);
        if (estimated + 1 > permit.limit()) {
            decision.set(new CocoRateLimitDecision(false, permit.limit(), 0, resetAt, false));
            return new Bucket(currentCount, previousCount, windowStart, 0.0, expiresAt);
        }
        long updatedCount = currentCount + 1;
        long remaining = Math.max(0, permit.limit() - (long) Math.ceil(estimated + 1));
        decision.set(new CocoRateLimitDecision(true, permit.limit(), remaining, resetAt, false));
        return new Bucket(updatedCount, previousCount, windowStart, 0.0, expiresAt);
    }

    private static Bucket acquireTokenBucket(CocoRateLimitPermit permit, Instant now, Bucket bucket,
            AtomicReference<CocoRateLimitDecision> decision) {
        double refillPerSecond = (double) permit.limit() / permit.windowSeconds();
        double nowSeconds = now.getEpochSecond() + now.getNano() / 1_000_000_000.0;
        double tokens = permit.limit();
        if (bucket != null) {
            double lastSeconds = bucket.windowStart() + bucket.previousCount() / 1_000_000_000.0;
            double refilled = Math.max(0.0, nowSeconds - lastSeconds) * refillPerSecond;
            tokens = Math.min(permit.limit(), bucket.tokens() + refilled);
        }
        Bucket stamped = tokenBucketState(now, tokens);
        if (tokens < 1.0) {
            // Time until the next whole token, expressed as a reset instant.
            long waitMillis = (long) Math.ceil((1.0 - tokens) / refillPerSecond * 1000.0);
            Instant resetAt = now.plusMillis(Math.max(1, waitMillis));
            decision.set(new CocoRateLimitDecision(false, permit.limit(), 0, resetAt, false));
            return stamped;
        }
        double remainingTokens = tokens - 1.0;
        long fullMillis = (long) Math.ceil((permit.limit() - remainingTokens) / refillPerSecond * 1000.0);
        Instant resetAt = now.plusMillis(Math.max(1, fullMillis));
        decision.set(new CocoRateLimitDecision(true, permit.limit(), (long) Math.floor(remainingTokens), resetAt,
                false));
        return tokenBucketState(now, remainingTokens);
    }

    // Token-bucket state reuses the Bucket record: windowStart holds the last-refill
    // epoch second and previousCount holds its nanosecond remainder, so the pair is a
    // full-precision timestamp without widening the record.
    private static Bucket tokenBucketState(Instant now, double tokens) {
        long expireSeconds = now.getEpochSecond() + Math.max(1L, (long) Math.ceil(tokens)) + 1L;
        return new Bucket(0, now.getNano(), now.getEpochSecond(), tokens, Instant.ofEpochSecond(expireSeconds));
    }

    static Instant fixedWindowResetAt(Instant now, long windowSeconds) {
        long windowStart = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(windowStart + windowSeconds);
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
            if (!bucket.expiresAt().isAfter(now) && this.entries.remove(key, bucket)) {
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

    private CocoRateLimitDecision unavailable(CocoRateLimitPermit permit) {
        // No stored window to derive a reset from on the unavailable path, so report
        // the fixed-window boundary as a stable, algorithm-agnostic hint.
        return new CocoRateLimitDecision(false, permit.limit(), 0,
                fixedWindowResetAt(this.clock.instant(), permit.windowSeconds()), true);
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * 单个键的限流状态,三种算法复用同一 record。
     * <p>
     * 各算法只用其中相关字段:固定窗口用 {@code count};滑动窗口另用 {@code previousCount}
     * 与 {@code windowStart};令牌桶用 {@code tokens},并把上次补充时间戳拆进 {@code windowStart}
     * (整秒)与 {@code previousCount}(纳秒余数)以免加宽 record。{@code expiresAt} 供后台清理统一使用。
     * </p>
     */
    record Bucket(long count, long previousCount, long windowStart, double tokens, Instant expiresAt) {

        /** 固定窗口便捷构造:仅计数与到期时间。 */
        Bucket(long count, long unused, Instant expiresAt) {
            this(count, 0, 0, 0.0, expiresAt);
        }
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
