package io.github.coco.feature.web.replay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 进程内 Coco Web 防重放存储。
 * <p>
 * 使用内存映射保存已占用的防重放键，适合单进程应用和本地开发；集群部署时应由业务项目替换为分布式存储实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class InMemoryCocoReplayStore implements CocoReplayStore {

    private final ConcurrentMap<String, Instant> reservedKeys = new ConcurrentHashMap<>();

    private final Duration cleanupInterval;

    private final Clock clock;

    private final AtomicReference<Instant> nextCleanupAt;

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
        CocoReplayProperties replayProperties = properties == null ? new CocoReplayProperties() : properties;
        this.cleanupInterval = Duration.ofSeconds(replayProperties.getCleanupIntervalSeconds());
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.nextCleanupAt = new AtomicReference<>(this.clock.instant().plus(this.cleanupInterval));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt) {
        CocoReplayKey checkedKey = Objects.requireNonNull(key, "key must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Instant now = this.clock.instant();
        cleanupIfNeeded(now);
        AtomicBoolean reserved = new AtomicBoolean(false);
        this.reservedKeys.compute(checkedKey.value(), (ignored, currentExpiresAt) -> {
            if (currentExpiresAt == null || !currentExpiresAt.isAfter(now)) {
                reserved.set(true);
                return checkedExpiresAt;
            }
            return currentExpiresAt;
        });
        return reserved.get();
    }

    private void cleanupIfNeeded(Instant now) {
        Instant currentNextCleanupAt = this.nextCleanupAt.get();
        if (now.isBefore(currentNextCleanupAt)) {
            return;
        }
        Instant nextCleanup = now.plus(this.cleanupInterval);
        if (this.nextCleanupAt.compareAndSet(currentNextCleanupAt, nextCleanup)) {
            this.reservedKeys.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        }
    }
}
