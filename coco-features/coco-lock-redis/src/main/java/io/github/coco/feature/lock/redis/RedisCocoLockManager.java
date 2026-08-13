package io.github.coco.feature.lock.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.feature.lock.CocoLock;
import io.github.coco.feature.lock.CocoLockException;
import io.github.coco.feature.lock.CocoLockManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.types.Expiration;

/**
 * 基于 Spring Data Redis 的 Coco 分布式锁管理器。
 * <p>
 * 通过 {@code SET NX PX} 原子获取锁，并使用 Lua 比对 owner token 后删除。不提供看门狗续租，租期完全由调用方声明。
 * </p>
 */
public final class RedisCocoLockManager implements CocoLockManager {

    private static final byte[] RELEASE_SCRIPT = ("if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end").getBytes(StandardCharsets.UTF_8);

    private final RedisConnectionFactory connectionFactory;

    private final String keyPrefix;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param connectionFactory Redis 连接工厂
     * @param keyPrefix 安全的应用隔离前缀
     */
    public RedisCocoLockManager(RedisConnectionFactory connectionFactory, String keyPrefix) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    @Override
    public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
        validate(key, waitTime, leaseTime);
        ensureOpen();
        long leaseMillis = leaseMillis(leaseTime);
        long deadline = deadline(waitTime);
        byte[] redisKey = redisKey(key);
        byte[] ownerToken = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        do {
            ensureOpen();
            if (setIfAbsent(redisKey, ownerToken, leaseMillis)) {
                Instant acquiredAt = Instant.now();
                return Optional.of(new RedisCocoLock(key, redisKey, ownerToken, acquiredAt,
                        acquiredAt.plus(leaseTime)));
            }
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            pause(deadline);
        }
        while (true);
    }

    @Override
    public void close() {
        this.closed.set(true);
    }

    static long leaseMillis(Duration leaseTime) {
        try {
            long millis = leaseTime.toMillis();
            if (millis <= 0) {
                throw new IllegalArgumentException("Coco Redis lock leaseTime must be at least one millisecond");
            }
            return millis;
        }
        catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Coco Redis lock leaseTime is too large", ex);
        }
    }

    private boolean setIfAbsent(byte[] key, byte[] token, long leaseMillis) {
        try (RedisConnection connection = requireConnection(this.connectionFactory.getConnection())) {
            return Boolean.TRUE.equals(connection.stringCommands().set(key, token,
                    Expiration.milliseconds(leaseMillis), SetOption.SET_IF_ABSENT));
        }
        catch (DataAccessException ex) {
            throw new CocoLockException("Redis failure while acquiring Coco lock", ex);
        }
    }

    private void release(byte[] key, byte[] token) {
        try (RedisConnection connection = requireConnection(this.connectionFactory.getConnection())) {
            connection.scriptingCommands().eval(RELEASE_SCRIPT, ReturnType.INTEGER, 1, key, token);
        }
        catch (DataAccessException ex) {
            throw new CocoLockException("Redis failure while releasing Coco lock", ex);
        }
    }

    private byte[] redisKey(String businessKey) {
        return (this.keyPrefix + sha256(businessKey)).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static RedisConnection requireConnection(RedisConnection connection) {
        if (connection == null) {
            throw new CocoLockException("Redis connection factory returned no connection");
        }
        return connection;
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Coco lock manager is closed");
        }
    }

    private static void validate(String key, Duration waitTime, Duration leaseTime) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Coco lock key must not be blank");
        }
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock waitTime must not be negative");
        }
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock leaseTime must be positive");
        }
        leaseMillis(leaseTime);
    }

    private static long deadline(Duration waitTime) {
        try {
            return Math.addExact(System.nanoTime(), waitTime.toNanos());
        }
        catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static void pause(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(10L, Math.max(1L, remaining / 1_000_000L)));
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CocoLockException("Interrupted while acquiring Redis Coco lock", ex);
        }
    }

    private final class RedisCocoLock implements CocoLock {
        private final String key;
        private final byte[] redisKey;
        private final byte[] token;
        private final Instant acquiredAt;
        private final Instant expiresAt;
        private final Thread owner = Thread.currentThread();
        private final AtomicBoolean released = new AtomicBoolean();

        private RedisCocoLock(String key, byte[] redisKey, byte[] token, Instant acquiredAt, Instant expiresAt) {
            this.key = key;
            this.redisKey = redisKey;
            this.token = token;
            this.acquiredAt = acquiredAt;
            this.expiresAt = expiresAt;
        }

        @Override public String key() { return this.key; }
        @Override public Instant acquiredAt() { return this.acquiredAt; }
        @Override public Instant expiresAt() { return this.expiresAt; }

        @Override
        @SuppressFBWarnings(value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
                justification = "CocoLock requires cross-thread release and Redis failures to remain visible to callers.")
        public void close() {
            if (this.released.get()) {
                return;
            }
            if (Thread.currentThread() != this.owner) {
                throw new IllegalStateException("Coco lock must be released by its acquiring thread");
            }
            if (this.released.compareAndSet(false, true)) {
                try {
                    release(this.redisKey, this.token);
                }
                catch (RuntimeException ex) {
                    this.released.set(false);
                    throw ex;
                }
            }
        }
    }
}
