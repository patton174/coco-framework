package io.github.coco.feature.web.replay;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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

    private final Map<String, Set<String>> reservedKeysByAppId = new HashMap<>();

    private final NavigableMap<Instant, Set<String>> reservedKeysByExpiration = new TreeMap<>();

    private final ReentrantLock reservationLock = new ReentrantLock();

    private final long cleanupIntervalSeconds;

    private final int maxEntries;

    private final int maxEntriesPerAppId;

    private final Clock clock;

    private final ScheduledExecutorService cleanupExecutor;

    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicLong capacityRejections = new AtomicLong();

    private long targetedCleanupInspections;

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
            ensureOpen();
            Reservation current = this.reservedKeys.get(storageKey);
            if (current != null) {
                if (current.expiresAt().isAfter(now)) {
                    return false;
                }
                rejected = replaceExpiredReservationLocked(storageKey, current, checkedExpiresAt, appId, now);
                if (rejected == null) {
                    return true;
                }
            }
            else {
                CocoReplayCapacityExceededException capacityFailure = capacityFailure(appId);
                if (capacityFailure != null) {
                    cleanupCapacityLocked(appId, capacityFailure.scope(), now);
                    capacityFailure = capacityFailure(appId);
                }
                if (capacityFailure != null) {
                    rejected = capacityFailure;
                }
                else {
                    addReservationLocked(storageKey, new Reservation(checkedExpiresAt, appId));
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
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        if (this.cleanupExecutor != null) {
            this.cleanupExecutor.shutdownNow();
        }
        this.reservationLock.lock();
        try {
            this.reservedKeys.clear();
            this.reservedKeyCountsByAppId.clear();
            this.reservedKeysByAppId.clear();
            this.reservedKeysByExpiration.clear();
        }
        finally {
            this.reservationLock.unlock();
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

    int indexedKeyCountForAppId(String appId) {
        String normalizedAppId = normalizeCapacitySubject(appId);
        this.reservationLock.lock();
        try {
            Set<String> storageKeys = this.reservedKeysByAppId.get(normalizedAppId);
            return storageKeys == null ? 0 : storageKeys.size();
        }
        finally {
            this.reservationLock.unlock();
        }
    }

    int expirationIndexKeyCount() {
        this.reservationLock.lock();
        try {
            return this.reservedKeysByExpiration.values().stream().mapToInt(Set::size).sum();
        }
        finally {
            this.reservationLock.unlock();
        }
    }

    long targetedCleanupInspectionCount() {
        this.reservationLock.lock();
        try {
            return this.targetedCleanupInspections;
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
            Reservation current, Instant expiresAt, String appId, Instant now) {
        if (Objects.equals(current.appId(), appId)) {
            replaceReservationExpirationLocked(storageKey, current, expiresAt);
            return null;
        }
        cleanupExpiredKeysForAppIdLocked(appId, now);
        if (this.reservedKeyCountsByAppId.getOrDefault(appId, 0) >= this.maxEntriesPerAppId) {
            return new CocoReplayCapacityExceededException(
                    CocoReplayCapacityExceededException.Scope.APP_ID, this.maxEntriesPerAppId);
        }
        migrateReservationLocked(storageKey, current, new Reservation(expiresAt, appId));
        return null;
    }

    private void cleanupCapacityLocked(String appId, CocoReplayCapacityExceededException.Scope scope, Instant now) {
        if (scope == CocoReplayCapacityExceededException.Scope.APP_ID) {
            cleanupExpiredKeysForAppIdLocked(appId, now);
        }
        else {
            cleanupExpiredKeysLocked(now);
        }
    }

    private int cleanupExpiredKeysForAppIdLocked(String appId, Instant now) {
        Set<String> storageKeys = this.reservedKeysByAppId.get(appId);
        if (storageKeys == null || storageKeys.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<String> iterator = storageKeys.iterator();
        while (iterator.hasNext()) {
            String storageKey = iterator.next();
            this.targetedCleanupInspections++;
            Reservation reservation = requireIndexedReservation(storageKey, appId);
            if (!reservation.expiresAt().isAfter(now)) {
                iterator.remove();
                this.reservedKeys.remove(storageKey);
                removeExpirationIndexLocked(storageKey, reservation.expiresAt());
                decrementAppIdCount(appId);
                removed++;
            }
        }
        if (storageKeys.isEmpty()) {
            this.reservedKeysByAppId.remove(appId);
        }
        return removed;
    }

    private static String normalizeCapacitySubject(String capacitySubject) {
        return capacitySubject == null || capacitySubject.isBlank() ? null : capacitySubject.trim();
    }

    private void incrementAppIdCount(String appId) {
        this.reservedKeyCountsByAppId.merge(appId, 1, Integer::sum);
    }

    private void addReservationLocked(String storageKey, Reservation reservation) {
        if (this.reservedKeys.putIfAbsent(storageKey, reservation) != null) {
            throw new IllegalStateException("Coco replay reservation already exists");
        }
        addAppIdIndexLocked(storageKey, reservation.appId());
        addExpirationIndexLocked(storageKey, reservation.expiresAt());
        incrementAppIdCount(reservation.appId());
    }

    private void replaceReservationExpirationLocked(String storageKey, Reservation current, Instant expiresAt) {
        removeExpirationIndexLocked(storageKey, current.expiresAt());
        Reservation replacement = new Reservation(expiresAt, current.appId());
        this.reservedKeys.put(storageKey, replacement);
        addExpirationIndexLocked(storageKey, replacement.expiresAt());
    }

    private void migrateReservationLocked(String storageKey, Reservation current, Reservation replacement) {
        removeAppIdIndexLocked(storageKey, current.appId());
        removeExpirationIndexLocked(storageKey, current.expiresAt());
        decrementAppIdCount(current.appId());
        this.reservedKeys.put(storageKey, replacement);
        addAppIdIndexLocked(storageKey, replacement.appId());
        addExpirationIndexLocked(storageKey, replacement.expiresAt());
        incrementAppIdCount(replacement.appId());
    }

    private void addAppIdIndexLocked(String storageKey, String appId) {
        if (!this.reservedKeysByAppId.computeIfAbsent(appId, ignored -> new HashSet<>()).add(storageKey)) {
            throw new IllegalStateException("Coco replay appId index already contains reservation");
        }
    }

    private void removeAppIdIndexLocked(String storageKey, String appId) {
        Set<String> storageKeys = this.reservedKeysByAppId.get(appId);
        if (storageKeys == null || !storageKeys.remove(storageKey)) {
            throw new IllegalStateException("Coco replay appId index is inconsistent");
        }
        if (storageKeys.isEmpty()) {
            this.reservedKeysByAppId.remove(appId);
        }
    }

    private void addExpirationIndexLocked(String storageKey, Instant expiresAt) {
        if (!this.reservedKeysByExpiration.computeIfAbsent(expiresAt, ignored -> new HashSet<>()).add(storageKey)) {
            throw new IllegalStateException("Coco replay expiration index already contains reservation");
        }
    }

    private void removeExpirationIndexLocked(String storageKey, Instant expiresAt) {
        Set<String> storageKeys = this.reservedKeysByExpiration.get(expiresAt);
        if (storageKeys == null || !storageKeys.remove(storageKey)) {
            throw new IllegalStateException("Coco replay expiration index is inconsistent");
        }
        if (storageKeys.isEmpty()) {
            this.reservedKeysByExpiration.remove(expiresAt);
        }
    }

    private Reservation requireIndexedReservation(String storageKey, String appId) {
        Reservation reservation = this.reservedKeys.get(storageKey);
        if (reservation == null || !Objects.equals(reservation.appId(), appId)) {
            throw new IllegalStateException("Coco replay appId index is inconsistent");
        }
        return reservation;
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Coco replay store is closed");
        }
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
        Map.Entry<Instant, Set<String>> entry = this.reservedKeysByExpiration.firstEntry();
        while (entry != null && !entry.getKey().isAfter(now)) {
            Instant expiresAt = entry.getKey();
            Set<String> storageKeys = this.reservedKeysByExpiration.pollFirstEntry().getValue();
            for (String storageKey : storageKeys) {
                Reservation reservation = this.reservedKeys.remove(storageKey);
                if (reservation == null || !reservation.expiresAt().equals(expiresAt)) {
                    throw new IllegalStateException("Coco replay expiration index is inconsistent");
                }
                removeAppIdIndexLocked(storageKey, reservation.appId());
                decrementAppIdCount(reservation.appId());
                removed++;
            }
            entry = this.reservedKeysByExpiration.firstEntry();
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
