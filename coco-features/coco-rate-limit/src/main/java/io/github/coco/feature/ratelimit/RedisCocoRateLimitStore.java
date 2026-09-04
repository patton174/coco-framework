package io.github.coco.feature.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Lua 实现的 {@link CocoRateLimitStore}。
 * <p>
 * 每种算法一段 Lua 脚本,在 Redis 服务端原子执行:读取-判定-写回不可被并发穿插,
 * 计数用 Redis 服务器时间({@code TIME})而非应用时钟,避免多实例时钟漂移影响窗口边界。
 * 脚本统一返回三元组 {@code {allowed, remaining, resetAtMillis}}。
 * </p>
 */
public final class RedisCocoRateLimitStore implements CocoRateLimitStore {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> FIXED_WINDOW_SCRIPT = script("""
            local limit = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local windowStart = math.floor(nowMs / windowMs) * windowMs
            local resetAt = windowStart + windowMs
            local count = 0
            if redis.call('HGET', KEYS[1], 'ws') == tostring(windowStart) then
                count = tonumber(redis.call('HGET', KEYS[1], 'c') or '0')
            end
            if count >= limit then
                return {0, 0, resetAt}
            end
            count = count + 1
            redis.call('HSET', KEYS[1], 'ws', windowStart, 'c', count)
            redis.call('PEXPIRE', KEYS[1], windowMs)
            return {1, limit - count, resetAt}
            """);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = script("""
            local limit = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local windowStart = math.floor(nowMs / windowMs) * windowMs
            local resetAt = windowStart + windowMs
            local storedWs = tonumber(redis.call('HGET', KEYS[1], 'ws') or '-1')
            local cur = 0
            local prev = 0
            if storedWs == windowStart then
                cur = tonumber(redis.call('HGET', KEYS[1], 'c') or '0')
                prev = tonumber(redis.call('HGET', KEYS[1], 'p') or '0')
            elseif storedWs == windowStart - windowMs then
                prev = tonumber(redis.call('HGET', KEYS[1], 'c') or '0')
            end
            local weight = (windowMs - (nowMs - windowStart)) / windowMs
            if weight < 0 then weight = 0 end
            local estimated = prev * weight + cur
            if estimated + 1 > limit then
                return {0, 0, resetAt}
            end
            cur = cur + 1
            redis.call('HSET', KEYS[1], 'ws', windowStart, 'c', cur, 'p', prev)
            redis.call('PEXPIRE', KEYS[1], windowMs * 2)
            local remaining = limit - math.ceil(estimated + 1)
            if remaining < 0 then remaining = 0 end
            return {1, remaining, resetAt}
            """);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = script("""
            local limit = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local refillPerMs = limit / windowMs
            local t = redis.call('TIME')
            local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local tokens = limit
            local last = tonumber(redis.call('HGET', KEYS[1], 'ts') or '-1')
            if last >= 0 then
                local stored = tonumber(redis.call('HGET', KEYS[1], 'tk') or '0')
                local refilled = math.max(0, nowMs - last) * refillPerMs
                tokens = math.min(limit, stored + refilled)
            end
            if tokens < 1 then
                local waitMs = math.ceil((1 - tokens) / refillPerMs)
                redis.call('HSET', KEYS[1], 'ts', nowMs, 'tk', tostring(tokens))
                redis.call('PEXPIRE', KEYS[1], windowMs)
                return {0, 0, nowMs + waitMs}
            end
            tokens = tokens - 1
            redis.call('HSET', KEYS[1], 'ts', nowMs, 'tk', tostring(tokens))
            redis.call('PEXPIRE', KEYS[1], windowMs)
            local fullMs = math.ceil((limit - tokens) / refillPerMs)
            return {1, math.floor(tokens), nowMs + fullMs}
            """);

    private final ScriptExecutor scriptExecutor;
    private final String keyPrefix;
    private final Clock clock;

    /**
     * 创建 Redis 限流存储。
     * @param stringRedisTemplate Redis 模板
     * @param keyPrefix 键前缀
     * @param clock 时钟(仅用于不可用兜底的 resetAt)
     */
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
        DefaultRedisScript<List> script = switch (checked.algorithm()) {
            case FIXED_WINDOW -> FIXED_WINDOW_SCRIPT;
            case SLIDING_WINDOW -> SLIDING_WINDOW_SCRIPT;
            case TOKEN_BUCKET -> TOKEN_BUCKET_SCRIPT;
        };
        try {
            List<?> result = this.scriptExecutor.execute(script, List.of(redisKey(checked.key())),
                    Long.toString(checked.limit()), Long.toString(checked.windowSeconds() * 1000L));
            if (result == null || result.size() < 3) {
                return unavailable(checked);
            }
            long allowed = toLong(result.get(0));
            long remaining = Math.max(0L, Math.min(checked.limit(), toLong(result.get(1))));
            Instant resetAt = Instant.ofEpochMilli(toLong(result.get(2)));
            return new CocoRateLimitDecision(allowed == 1L, checked.limit(), remaining, resetAt, false);
        }
        catch (RuntimeException exception) {
            return unavailable(checked);
        }
    }

    private CocoRateLimitDecision unavailable(CocoRateLimitPermit permit) {
        long windowMillis = permit.windowSeconds() * 1000L;
        long nowMillis = this.clock.instant().toEpochMilli();
        long resetAt = Math.floorDiv(nowMillis, windowMillis) * windowMillis + windowMillis;
        return new CocoRateLimitDecision(false, permit.limit(), 0, Instant.ofEpochMilli(resetAt), true);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String redisKey(CocoRateLimitKey key) {
        return this.keyPrefix + digest(key.routeId().length() + ":" + key.routeId()
                + key.subject().length() + ":" + key.subject());
    }

    static DefaultRedisScript<List> fixedWindowScript() {
        return FIXED_WINDOW_SCRIPT;
    }

    static DefaultRedisScript<List> slidingWindowScript() {
        return SLIDING_WINDOW_SCRIPT;
    }

    static DefaultRedisScript<List> tokenBucketScript() {
        return TOKEN_BUCKET_SCRIPT;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> script(String source) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(List.class);
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
        @SuppressWarnings("rawtypes")
        List execute(DefaultRedisScript<List> script, List<String> keys, Object... arguments);
    }
}
