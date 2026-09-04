package io.github.coco.feature.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis Lua {@link CocoLockStore} 实现。 */
public final class RedisCocoLockStore implements CocoLockStore {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = script("""
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
                return 1
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """);

    private final ScriptExecutor scriptExecutor;
    private final String keyPrefix;
    private final Clock clock;

    /** 使用 StringRedisTemplate 创建 Redis 锁存储。 */
    public RedisCocoLockStore(StringRedisTemplate stringRedisTemplate, String keyPrefix, Clock clock) {
        this((script, keys, arguments) -> stringRedisTemplate.execute(script, keys, arguments), keyPrefix, clock);
    }

    RedisCocoLockStore(ScriptExecutor scriptExecutor, String keyPrefix, Clock clock) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyPrefix = requirePrefix(keyPrefix);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public AcquireResult acquire(CocoLockLease lease) {
        CocoLockLease checked = Objects.requireNonNull(lease, "lease must not be null");
        long ttlMillis = ttlMillis(checked);
        if (ttlMillis <= 0) {
            return AcquireResult.UNAVAILABLE;
        }
        try {
            Long result = this.scriptExecutor.execute(ACQUIRE_SCRIPT, List.of(redisKey(checked.key())),
                    checked.ownerToken(), Long.toString(ttlMillis));
            if (result == null) {
                return AcquireResult.UNAVAILABLE;
            }
            return result == 1L ? AcquireResult.ACQUIRED : AcquireResult.CONTENDED;
        }
        catch (RuntimeException exception) {
            return AcquireResult.UNAVAILABLE;
        }
    }

    @Override
    public RenewResult renew(CocoLockLease lease) {
        CocoLockLease checked = Objects.requireNonNull(lease, "lease must not be null");
        long ttlMillis = ttlMillis(checked);
        if (ttlMillis <= 0) {
            return RenewResult.NOT_OWNER;
        }
        try {
            Long result = this.scriptExecutor.execute(RENEW_SCRIPT, List.of(redisKey(checked.key())),
                    checked.ownerToken(), Long.toString(ttlMillis));
            if (result == null) {
                return RenewResult.UNAVAILABLE;
            }
            return result == 1L ? RenewResult.RENEWED : RenewResult.NOT_OWNER;
        }
        catch (RuntimeException exception) {
            return RenewResult.UNAVAILABLE;
        }
    }

    @Override
    public boolean release(CocoLockLease lease) {
        if (lease == null) {
            return false;
        }
        try {
            Long result = this.scriptExecutor.execute(RELEASE_SCRIPT, List.of(redisKey(lease.key())), lease.ownerToken());
            return result != null && result == 1L;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    static DefaultRedisScript<Long> acquireScript() { return ACQUIRE_SCRIPT; }
    static DefaultRedisScript<Long> renewScript() { return RENEW_SCRIPT; }
    static DefaultRedisScript<Long> releaseScript() { return RELEASE_SCRIPT; }

    private long ttlMillis(CocoLockLease lease) {
        return Duration.between(this.clock.instant(), lease.expiresAt()).toMillis();
    }

    private String redisKey(String logicalKey) {
        return this.keyPrefix + digest(logicalKey);
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requirePrefix(String keyPrefix) {
        String value = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        return value;
    }

    @FunctionalInterface
    interface ScriptExecutor {
        Long execute(DefaultRedisScript<Long> script, List<String> keys, Object... arguments);
    }
}
