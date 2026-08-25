package io.github.coco.feature.idempotency;

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

/** Redis Lua implementation of {@link CocoIdempotencyStore}. */
public final class RedisCocoIdempotencyStore implements CocoIdempotencyStore {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = script("""
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
                return 1
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

    public RedisCocoIdempotencyStore(StringRedisTemplate stringRedisTemplate, String keyPrefix, Clock clock) {
        this((script, keys, arguments) -> stringRedisTemplate.execute(script, keys, arguments), keyPrefix, clock);
    }

    RedisCocoIdempotencyStore(ScriptExecutor scriptExecutor, String keyPrefix, Clock clock) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyPrefix = requirePrefix(keyPrefix);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public AcquireResult acquire(CocoIdempotencyLease lease) {
        CocoIdempotencyLease checked = Objects.requireNonNull(lease, "lease must not be null");
        long ttlMillis = Duration.between(this.clock.instant(), checked.expiresAt()).toMillis();
        if (ttlMillis <= 0) {
            return AcquireResult.UNAVAILABLE;
        }
        try {
            Long result = this.scriptExecutor.execute(ACQUIRE_SCRIPT, List.of(redisKey(checked.key())),
                    checked.ownerToken(), Long.toString(ttlMillis));
            if (result == null) {
                return AcquireResult.UNAVAILABLE;
            }
            return result == 1L ? AcquireResult.ACQUIRED : AcquireResult.DUPLICATE;
        }
        catch (RuntimeException exception) {
            return AcquireResult.UNAVAILABLE;
        }
    }

    @Override
    public void release(CocoIdempotencyLease lease) {
        if (lease == null) {
            return;
        }
        try {
            this.scriptExecutor.execute(RELEASE_SCRIPT, List.of(redisKey(lease.key())), lease.ownerToken());
        }
        catch (RuntimeException ignored) {
            // Retaining the lease until its TTL expires is the fail-closed outcome.
        }
    }

    static DefaultRedisScript<Long> acquireScript() { return ACQUIRE_SCRIPT; }
    static DefaultRedisScript<Long> releaseScript() { return RELEASE_SCRIPT; }

    private String redisKey(CocoIdempotencyKey key) {
        String material = key.namespace().length() + ":" + key.namespace()
                + key.method().length() + ":" + key.method()
                + key.operationId().length() + ":" + key.operationId()
                + key.keyDigest();
        return this.keyPrefix + digest(material);
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
        if (value.isEmpty()) { throw new IllegalArgumentException("keyPrefix must not be blank"); }
        return value;
    }

    @FunctionalInterface
    interface ScriptExecutor {
        Long execute(DefaultRedisScript<Long> script, List<String> keys, Object... arguments);
    }
}
