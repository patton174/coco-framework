package io.github.coco.feature.lock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.connection.ReturnType;

/**
 * 基于 Spring Data Redis 的可选 Coco 分布式锁管理器。
 * <p>
 * 每次获取和释放各使用一个 Redis 连接。释放时通过 Lua 脚本比对随机 owner token，只有当前句柄仍是 Redis
 * 锁拥有者时才删除键；不提供自动续期。
 * </p>
 */
public final class RedisCocoLockManager implements CocoLockManager {

    private static final byte[] RELEASE_SCRIPT = ("if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end").getBytes(StandardCharsets.UTF_8);

    private final RedisConnectionFactory connectionFactory;

    private final String keyPrefix;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 Redis Coco 锁管理器。
     * @param connectionFactory Redis 连接工厂
     * @param keyPrefix Redis 锁键前缀
     */
    public RedisCocoLockManager(RedisConnectionFactory connectionFactory, String keyPrefix) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        this.keyPrefix = keyPrefix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
        validateRequest(key, waitTime, leaseTime);
        if (this.closed.get()) {
            throw new IllegalStateException("Coco lock manager is closed");
        }
        long leaseMillis = redisLeaseMillis(leaseTime);
        long deadline = deadlineNanos(waitTime);
        String redisKey = this.keyPrefix + key;
        byte[] redisKeyBytes = redisKey.getBytes(StandardCharsets.UTF_8);
        String ownerToken = UUID.randomUUID().toString();
        byte[] ownerTokenBytes = ownerToken.getBytes(StandardCharsets.UTF_8);
        do {
            if (this.closed.get()) {
                throw new IllegalStateException("Coco lock manager is closed");
            }
            if (setIfAbsent(redisKeyBytes, ownerTokenBytes, leaseMillis)) {
                Instant acquiredAt = Instant.now();
                return Optional.of(new RedisCocoLock(key, redisKeyBytes, ownerTokenBytes, acquiredAt,
                        acquiredAt.plus(leaseTime)));
            }
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            pauseUntil(deadline);
        }
        while (true);
    }

    /**
     * 将租期转换为 Redis 所需的正毫秒数。
     * @param leaseTime 租期
     * @return 正毫秒数
     */
    static long redisLeaseMillis(Duration leaseTime) {
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock leaseTime must be positive");
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private boolean setIfAbsent(byte[] key, byte[] value, long leaseMillis) {
        try (RedisConnection connection = this.connectionFactory.getConnection()) {
            return Boolean.TRUE.equals(connection.stringCommands().set(key, value,
                    Expiration.milliseconds(leaseMillis), SetOption.SET_IF_ABSENT));
        }
        catch (DataAccessException ex) {
            throw new CocoLockException("Redis failure while acquiring Coco lock", ex);
        }
    }

    private void release(byte[] key, byte[] ownerToken) {
        try (RedisConnection connection = this.connectionFactory.getConnection()) {
            connection.scriptingCommands().eval(RELEASE_SCRIPT, ReturnType.INTEGER, 1, key, ownerToken);
        }
        catch (DataAccessException ex) {
            throw new CocoLockException("Redis failure while releasing Coco lock", ex);
        }
    }

    private static void validateRequest(String key, Duration waitTime, Duration leaseTime) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Coco lock key must not be blank");
        }
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock waitTime must not be negative");
        }
        redisLeaseMillis(leaseTime);
    }

    private static long deadlineNanos(Duration waitTime) {
        try {
            return Math.addExact(System.nanoTime(), waitTime.toNanos());
        }
        catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static void pauseUntil(long deadline) {
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

        private final byte[] ownerToken;

        private final Instant acquiredAt;

        private final Instant expiresAt;

        private final Thread owner = Thread.currentThread();

        private final AtomicBoolean released = new AtomicBoolean();

        private RedisCocoLock(String key, byte[] redisKey, byte[] ownerToken, Instant acquiredAt, Instant expiresAt) {
            this.key = key;
            this.redisKey = redisKey;
            this.ownerToken = ownerToken;
            this.acquiredAt = acquiredAt;
            this.expiresAt = expiresAt;
        }

        @Override
        public String key() {
            return this.key;
        }

        @Override
        public Instant acquiredAt() {
            return this.acquiredAt;
        }

        @Override
        public Instant expiresAt() {
            return this.expiresAt;
        }

        @Override
        public void close() {
            if (this.released.get()) {
                return;
            }
            if (Thread.currentThread() != this.owner) {
                throw new IllegalStateException("Coco lock must be released by its acquiring thread");
            }
            if (this.released.compareAndSet(false, true)) {
                boolean releasedSuccessfully = false;
                try {
                    release(this.redisKey, this.ownerToken);
                    releasedSuccessfully = true;
                }
                finally {
                    if (!releasedSuccessfully) {
                        this.released.set(false);
                    }
                }
            }
        }
    }
}
