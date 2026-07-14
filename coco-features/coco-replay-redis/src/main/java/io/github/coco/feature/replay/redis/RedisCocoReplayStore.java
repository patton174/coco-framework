package io.github.coco.feature.replay.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.web.replay.CocoReplayKey;
import io.github.coco.feature.web.replay.CocoReplayStore;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;

/**
 * 基于 Redis 的 Coco Web 防重放共享存储。
 * <p>
 * 每次占用通过单键 Lua 脚本在目标 Redis 节点读取服务端时间并执行 {@code SET ... PX ... NX}。
 * Redis 键只包含固定命名空间和防重放材料的 SHA-256 摘要，避免写入 appId、keyId 或 nonce 原文。
 * </p>
 */
public final class RedisCocoReplayStore implements CocoReplayStore, AutoCloseable {

    private static final String KEY_NAMESPACE = "coco:replay:";

    private static final byte[] RESERVED_VALUE = { 1 };

    /*
     * Decimal-string subtraction retains the full signed-long Redis PX range without Lua
     * floating-point precision loss.
     */
    private static final byte[] RESERVE_SCRIPT = """
            redis.replicate_commands()
            local deadline = ARGV[1]
            if string.sub(deadline, 1, 1) == '-' then return 0 end
            local time = redis.call('TIME')
            local now = time[1] .. string.format('%03d', math.floor(tonumber(time[2]) / 1000))
            deadline = string.gsub(deadline, '^0+', '')
            now = string.gsub(now, '^0+', '')
            if deadline == '' then deadline = '0' end
            if now == '' then now = '0' end
            if #deadline < #now or (#deadline == #now and deadline <= now) then return 0 end
            local borrow = 0
            local ttl = ''
            for index = #deadline, 1, -1 do
              local digit = string.byte(deadline, index) - 48 - borrow
              local nowIndex = #now - (#deadline - index)
              local nowDigit = nowIndex > 0 and string.byte(now, nowIndex) - 48 or 0
              if digit < nowDigit then digit = digit + 10; borrow = 1 else borrow = 0 end
              ttl = string.char(digit - nowDigit + 48) .. ttl
            end
            ttl = string.gsub(ttl, '^0+', '')
            if ttl == '' or borrow ~= 0 then return 0 end
            if redis.call('SET', KEYS[1], ARGV[2], 'NX', 'PX', ttl) then return 1 end
            return 0
            """.getBytes(StandardCharsets.UTF_8);

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
            Long reserved = connection.scriptingCommands().eval(RESERVE_SCRIPT, ReturnType.INTEGER, 1,
                    redisKey(checkedKey), Long.toString(checkedExpiresAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8),
                    RESERVED_VALUE);
            if (reserved == null || (reserved != 0 && reserved != 1)) {
                throw new IllegalStateException("Redis replay reservation returned no result");
            }
            return reserved == 1;
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
