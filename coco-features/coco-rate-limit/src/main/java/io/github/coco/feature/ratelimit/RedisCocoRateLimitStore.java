package io.github.coco.feature.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis Lua implementation of {@link CocoRateLimitStore}. */
public final class RedisCocoRateLimitStore implements CocoRateLimitStore {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = script("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if current >= limit then
                return -current
            end
            current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return current
            """);

    private final ScriptExecutor scriptExecutor;
    private final String keyPrefix;
    private final Clock clock;

    public RedisCocoRateLimitStore(StringRedisTemplate stringRedisTemplate, String keyPrefix, Clock clock) {
        this((script, keys, arguments) -> stringRedisTemplate.execute(script, keys, arguments), keyPrefix, clock);
    }

    RedisCocoRateLimitStore(ScriptExecutor scriptExecutor, String keyPrefix, Clock clock) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyPrefix = requirePrefix(keyPrefix);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public CocoRateLimitDecision acquire(CocoRateLimitPermit permit) {
        CocoRateLimitPermit checked = Objects.requireNonNull(permit, "permit must not be null");
        long ttlMillis = Duration.between(this.clock.instant(), checked.resetAt()).toMillis();
        if (ttlMillis <= 0) {
            return unavailable(checked);
        }
        try {
            Long result = this.scriptExecutor.execute(ACQUIRE_SCRIPT, List.of(redisKey(checked.key())),
                    Long.toString(checked.limit()), Long.toString(ttlMillis));
            if (result == null || result == Long.MIN_VALUE) {
                return unavailable(checked);
            }
            long count = Math.abs(result);
            long remaining = Math.max(0L, checked.limit() - count);
            return new CocoRateLimitDecision(result > 0, checked.limit(), remaining, checked.resetAt(), false);
        }
        catch (RuntimeException exception) {
            return unavailable(checked);
        }
    }

    static DefaultRedisScript<Long> acquireScript() {
        return ACQUIRE_SCRIPT;
    }

    private String redisKey(CocoRateLimitKey key) {
        return this.keyPrefix + digest(key.routeId().length() + ":" + key.routeId()
                + key.subject().length() + ":" + key.subject());
    }

    private static CocoRateLimitDecision unavailable(CocoRateLimitPermit permit) {
        return new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), true);
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
