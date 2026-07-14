package io.github.coco.feature.replay.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.web.replay.CocoReplayKey;
import io.github.coco.feature.web.replay.CocoReplayStore;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.types.Expiration;

/**
 * 基于 Redis 的 Coco Web 防重放共享存储。
 * <p>
 * 每次占用在同一 Redis 连接上先读取服务端时间，再以 {@code SET ... PX ... NX} 原子写入。
 * Redis 键只包含固定命名空间和防重放材料的 SHA-256 摘要，避免写入 appId、keyId 或 nonce 原文。
 * </p>
 */
public final class RedisCocoReplayStore implements CocoReplayStore, AutoCloseable {

    private static final String KEY_NAMESPACE = "coco:replay:";

    private static final byte[] RESERVED_VALUE = { 1 };

    private final RedisConnectionFactory connectionFactory;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 Redis 防重放存储。
     * @param connectionFactory Redis 连接工厂
     */
    public RedisCocoReplayStore(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt) {
        CocoReplayKey checkedKey = Objects.requireNonNull(key, "key must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        ensureOpen();
        RedisConnection connection = requireConnection(this.connectionFactory.getConnection());
        try {
            long nowMillis = requireServerTime(connection);
            long ttlMillis = Math.subtractExact(checkedExpiresAt.toEpochMilli(), nowMillis);
            if (ttlMillis <= 0) {
                return false;
            }
            Boolean reserved = connection.stringCommands().set(redisKey(checkedKey), RESERVED_VALUE,
                    SetCondition.ifAbsent(), Expiration.milliseconds(ttlMillis));
            if (reserved == null) {
                throw new IllegalStateException("Redis replay reservation returned no result");
            }
            return reserved;
        }
        finally {
            connection.close();
        }
    }

    /**
     * 关闭存储并拒绝后续占用。
     */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private static RedisConnection requireConnection(RedisConnection connection) {
        if (connection == null) {
            throw new IllegalStateException("Redis connection factory returned no connection");
        }
        return connection;
    }

    private static long requireServerTime(RedisConnection connection) {
        Long serverTime = connection.serverCommands().time(TimeUnit.MILLISECONDS);
        if (serverTime == null) {
            throw new IllegalStateException("Redis server returned no current time");
        }
        return serverTime;
    }

    private static byte[] redisKey(CocoReplayKey key) {
        return (KEY_NAMESPACE + sha256(key.value())).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Redis replay store is closed");
        }
    }
}
