package io.github.coco.feature.web.replay;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内 Coco Web 防重放存储。
 * <p>
 * 使用有界内存映射保存已占用的防重放键，并同时执行全局和 appId 隔离容量限制。
 * 适合单进程应用和本地开发；集群部署时应由业务项目替换为分布式存储实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class InMemoryCocoReplayStore implements CocoReplayStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoReplayStore.class);

    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private final Map<String, Reservation> reservedKeys = new HashMap<>();

    private final Map<String, Integer> reservedKeyCountsByAppId = new HashMap<>();

    private final ReentrantLock reservationLock = new ReentrantLock();

    private final long cleanupIntervalSeconds;

    private final int maxEntries;

    private final int maxEntriesPerAppId;

    private final Clock clock;

    private final ScheduledExecutorService cleanupExecutor;

    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicLong capacityRejections = new AtomicLong();

    /**
     * <p>
     * 创建进程内防重放存储。
     * </p>
     * @param properties 防重放配置属性
     */
    public InMemoryCocoReplayStore(CocoReplayProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * <p>
     * 创建进程内防重放存储。
     * </p>
     * @param properties 防重放配置属性
     * @param clock 时钟
     */
    public InMemoryCocoReplayStore(CocoReplayProperties properties, Clock clock) {
        this(properties, clock, true);
    }

    InMemoryCocoReplayStore(CocoReplayProperties properties, Clock clock, boolean backgroundCleanupEnabled) {
        CocoReplayProperties replayProperties = properties == null ? new CocoReplayProperties() : properties;
        this.cleanupIntervalSeconds = replayProperties.getCleanupIntervalSeconds();
        this.maxEntries = replayProperties.getInMemory().getMaxEntries();
        this.maxEntriesPerAppId = replayProperties.getInMemory().getMaxEntriesPerAppId();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.cleanupExecutor = backgroundCleanupEnabled
                ? Executors.newSingleThreadScheduledExecutor(new CleanupThreadFactory())
                : null;
        warnClusterDeploymentRisk();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt) {
        return reserve(key, expiresAt, key == null ? null : key.appId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt, String capacitySubject) {
        CocoReplayKey checkedKey = Objects.requireNonNull(key, "key must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        startCleanupTaskIfNecessary();
        Instant now = this.clock.instant();
        String storageKey = checkedKey.value();
        String appId = normalizeCapacitySubject(capacitySubject);
        CocoReplayCapacityExceededException rejected = null;
        this.reservationLock.lock();
        try {
            Reservation current = this.reservedKeys.get(storageKey);
            if (current != null) {
                if (current.expiresAt().isAfter(now)) {
                    return false;
                }
                rejected = replaceExpiredReservationLocked(storageKey, current, checkedExpiresAt, appId);
                if (rejected == null) {
                    return true;
                }
            }
            else {
                CocoReplayCapacityExceededException capacityFailure = capacityFailure(appId);
                if (capacityFailure != null) {
                    cleanupExpiredKeysLocked(now);
                    capacityFailure = capacityFailure(appId);
                }
                if (capacityFailure != null) {
                    rejected = capacityFailure;
                }
                else {
                    this.reservedKeys.put(storageKey, new Reservation(checkedExpiresAt, appId));
                    incrementAppIdCount(appId);
                    return true;
                }
            }
        }
        finally {
            this.reservationLock.unlock();
        }
        throw recordCapacityRejection(rejected);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (this.cleanupExecutor != null && this.closed.compareAndSet(false, true)) {
            this.cleanupExecutor.shutdownNow();
        }
    }

    int cleanupExpiredKeys() {
        Instant now = this.clock.instant();
        this.reservationLock.lock();
        try {
            return cleanupExpiredKeysLocked(now);
        }
        finally {
            this.reservationLock.unlock();
        }
    }

    int reservedKeyCount() {
        this.reservationLock.lock();
        try {
            return this.reservedKeys.size();
        }
        finally {
            this.reservationLock.unlock();
        }
    }

    int reservedKeyCountForAppId(String appId) {
        String normalizedAppId = normalizeCapacitySubject(appId);
        this.reservationLock.lock();
        try {
            return this.reservedKeyCountsByAppId.getOrDefault(normalizedAppId, 0);
        }
        finally {
            this.reservationLock.unlock();
        }
    }

    private CocoReplayCapacityExceededException capacityFailure(String appId) {
        if (this.reservedKeys.size() >= this.maxEntries) {
            return new CocoReplayCapacityExceededException(
                    CocoReplayCapacityExceededException.Scope.GLOBAL, this.maxEntries);
        }
        if (this.reservedKeyCountsByAppId.getOrDefault(appId, 0) >= this.maxEntriesPerAppId) {
            return new CocoReplayCapacityExceededException(
                    CocoReplayCapacityExceededException.Scope.APP_ID, this.maxEntriesPerAppId);
        }
        return null;
    }

    private CocoReplayCapacityExceededException replaceExpiredReservationLocked(String storageKey,
            Reservation current, Instant expiresAt, String appId) {
        if (Objects.equals(current.appId(), appId)) {
            this.reservedKeys.put(storageKey, new Reservation(expiresAt, appId));
            return null;
        }
        if (this.reservedKeyCountsByAppId.getOrDefault(appId, 0) >= this.maxEntriesPerAppId) {
            return new CocoReplayCapacityExceededException(
                    CocoReplayCapacityExceededException.Scope.APP_ID, this.maxEntriesPerAppId);
        }
        decrementAppIdCount(current.appId());
        incrementAppIdCount(appId);
        this.reservedKeys.put(storageKey, new Reservation(expiresAt, appId));
        return null;
    }

    private static String normalizeCapacitySubject(String capacitySubject) {
        return capacitySubject == null || capacitySubject.isBlank() ? null : capacitySubject.trim();
    }

    private void incrementAppIdCount(String appId) {
        this.reservedKeyCountsByAppId.merge(appId, 1, Integer::sum);
    }

    private CocoReplayCapacityExceededException recordCapacityRejection(
            CocoReplayCapacityExceededException exception) {
        long totalRejections = this.capacityRejections.incrementAndGet();
        if ((totalRejections & (totalRejections - 1)) == 0) {
            LOGGER.warn("Coco replay capacity exhausted: scope={}, capacity={}, totalRejections={}",
                    exception.scope().id(), exception.capacity(), totalRejections);
        }
        return exception;
    }

    private int cleanupExpiredKeysLocked(Instant now) {
        int removed = 0;
        Iterator<Map.Entry<String, Reservation>> iterator = this.reservedKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Reservation reservation = iterator.next().getValue();
            if (!reservation.expiresAt().isAfter(now)) {
                iterator.remove();
                decrementAppIdCount(reservation.appId());
                removed++;
            }
        }
        return removed;
    }

    private void decrementAppIdCount(String appId) {
        Integer current = this.reservedKeyCountsByAppId.get(appId);
        if (current == null || current <= 0) {
            throw new IllegalStateException("Coco replay appId capacity accounting is inconsistent");
        }
        if (current == 1) {
            this.reservedKeyCountsByAppId.remove(appId);
        }
        else {
            this.reservedKeyCountsByAppId.put(appId, current - 1);
        }
    }

    private void startCleanupTaskIfNecessary() {
        if (this.cleanupExecutor == null || this.closed.get()
                || !this.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpiredKeysSafely,
                this.cleanupIntervalSeconds, this.cleanupIntervalSeconds, TimeUnit.SECONDS);
    }

    private void cleanupExpiredKeysSafely() {
        try {
            cleanupExpiredKeys();
        }
        catch (RuntimeException ex) {
            LOGGER.warn("Coco replay cleanup failed; expired replay keys will be retried later.", ex);
        }
    }

    private static void warnClusterDeploymentRisk() {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco replay uses process-local InMemoryCocoReplayStore; replace CocoReplayStore "
                    + "with a shared implementation for clustered deployments.");
        }
    }

    private static final class CleanupThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "coco-replay-cleanup");
            thread.setDaemon(true);
            return thread;
        }
    }

    private record Reservation(Instant expiresAt, String appId) {
    }
}
