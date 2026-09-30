package io.github.coco.feature.web.replay;

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

/** Redis Lua implementation of {@link CocoReplayStore}. */
public final class RedisCocoReplayStore implements CocoReplayStore {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
                return 1
            end
            return 0
            """);

    private final ScriptExecutor scriptExecutor;
    private final String keyPrefix;
    private final Clock clock;

    public RedisCocoReplayStore(StringRedisTemplate stringRedisTemplate, String keyPrefix) {
        this(stringRedisTemplate, keyPrefix, Clock.systemUTC());
    }

    RedisCocoReplayStore(StringRedisTemplate stringRedisTemplate, String keyPrefix, Clock clock) {
        this((script, keys, arguments) -> stringRedisTemplate.execute(script, keys, arguments), keyPrefix, clock);
    }

    RedisCocoReplayStore(ScriptExecutor scriptExecutor, String keyPrefix, Clock clock) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyPrefix = requirePrefix(keyPrefix);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt) {
        CocoReplayKey checkedKey = Objects.requireNonNull(key, "key must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        long ttlMillis = Duration.between(this.clock.instant(), checkedExpiresAt).toMillis();
        if (ttlMillis <= 0) {
            return false;
        }
        Long result = this.scriptExecutor.execute(RESERVE_SCRIPT, List.of(redisKey(checkedKey)),
                Long.toString(ttlMillis));
        return result != null && result == 1L;
    }

    static DefaultRedisScript<Long> reserveScript() {
        return RESERVE_SCRIPT;
    }

    private String redisKey(CocoReplayKey key) {
        return this.keyPrefix + digest(key.value());
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
