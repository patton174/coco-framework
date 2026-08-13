package io.github.coco.feature.ratelimit.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.ratelimit.CocoRateLimitDecision;
import io.github.coco.feature.ratelimit.CocoRateLimitKey;
import io.github.coco.feature.ratelimit.CocoRateLimitPermit;
import io.github.coco.feature.ratelimit.CocoRateLimitStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.ScriptExecutor;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 基于 Redis Lua 的 Coco 固定窗口限流存储。
 * <p>
 * 每次占用通过一个 Redis 脚本原子完成计数、上限判断和绝对过期时间写入。连接失败、脚本错误和无法识别的
 * 返回值均向上抛出，交由 Coco 限流请求执行器按 unavailable 语义失败关闭。
 * </p>
 */
public final class RedisCocoRateLimitStore implements CocoRateLimitStore, AutoCloseable {

    private static final String SCRIPT_PATH = "io/github/coco/feature/ratelimit/redis/fixed-window.lua";

    private static final DefaultRedisScript<String> ACQUIRE_SCRIPT = acquireScript();

    private final ScriptExecutor<String> scriptExecutor;

    private final String keyPrefix;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 Redis 固定窗口限流存储。
     * @param connectionFactory Redis 连接工厂
     * @param properties Redis 限流存储配置
     */
    public RedisCocoRateLimitStore(RedisConnectionFactory connectionFactory,
            CocoRateLimitRedisProperties properties) {
        this(scriptExecutor(connectionFactory), properties);
    }

    RedisCocoRateLimitStore(ScriptExecutor<String> scriptExecutor, CocoRateLimitRedisProperties properties) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        String configuredPrefix = CocoRateLimitRedisProperties.DEFAULT_KEY_PREFIX;
        if (properties != null && properties.getKeyPrefix() != null) {
            configuredPrefix = properties.getKeyPrefix();
        }
        this.keyPrefix = normalizePrefix(configuredPrefix);
    }

    @Override
    public CocoRateLimitDecision acquire(CocoRateLimitPermit permit) {
        CocoRateLimitPermit checkedPermit = Objects.requireNonNull(permit, "permit must not be null");
        ensureOpen();

        long resetAtEpochMillis;
        try {
            resetAtEpochMillis = checkedPermit.resetAt().toEpochMilli();
        }
        catch (ArithmeticException exception) {
            throw new IllegalArgumentException("resetAt must be representable as epoch milliseconds", exception);
        }
        if (resetAtEpochMillis <= 0) {
            throw new IllegalArgumentException("resetAt must be after the Unix epoch");
        }

        String key = redisKey(checkedPermit.key(), resetAtEpochMillis);
        String result = this.scriptExecutor.execute(ACQUIRE_SCRIPT, List.of(key),
                Long.toString(checkedPermit.limit()), Long.toString(resetAtEpochMillis));
        return parseDecision(result, checkedPermit);
    }

    /**
     * 关闭存储并拒绝后续占用。
     */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private String redisKey(CocoRateLimitKey key, long resetAtEpochMillis) {
        return this.keyPrefix + "{" + digest(key) + "}:" + resetAtEpochMillis;
    }

    private static String digest(CocoRateLimitKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(key.routeId().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.subject().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static CocoRateLimitDecision parseDecision(String result, CocoRateLimitPermit permit) {
        if (result == null || result.length() < 3 || result.charAt(1) != ':'
                || result.indexOf(':', 2) >= 0) {
            throw invalidResult();
        }
        String remainingValue = result.substring(2);
        if (!isCanonicalDecimal(remainingValue)) {
            throw invalidResult();
        }

        long remaining;
        try {
            remaining = Long.parseLong(remainingValue);
        }
        catch (NumberFormatException exception) {
            throw invalidResult(exception);
        }
        if (remaining < 0 || remaining > permit.limit()) {
            throw invalidResult();
        }

        return switch (result.charAt(0)) {
            case '1' -> {
                if (remaining >= permit.limit()) {
                    throw invalidResult();
                }
                yield new CocoRateLimitDecision(true, permit.limit(), remaining, permit.resetAt(), false);
            }
            case '0' -> {
                if (remaining != 0) {
                    throw invalidResult();
                }
                yield new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), false);
            }
            case 'E' -> {
                if (remaining != 0) {
                    throw invalidResult();
                }
                yield unavailable(permit);
            }
            default -> throw invalidResult();
        };
    }

    private static boolean isCanonicalDecimal(String value) {
        if (value.isEmpty() || value.length() > 1 && value.charAt(0) == '0') {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    private static CocoRateLimitDecision unavailable(CocoRateLimitPermit permit) {
        return new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), true);
    }

    private static String normalizePrefix(String configuredPrefix) {
        String prefix = Objects.requireNonNull(configuredPrefix, "coco.rate-limit.redis.key-prefix must not be null")
                .trim();
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("coco.rate-limit.redis.key-prefix must not be blank");
        }
        for (int index = 0; index < prefix.length(); index++) {
            char value = prefix.charAt(index);
            if (value == '{' || value == '}' || Character.isWhitespace(value) || Character.isISOControl(value)) {
                throw new IllegalArgumentException(
                        "coco.rate-limit.redis.key-prefix must not contain braces, whitespace, or control characters");
            }
        }
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private static ScriptExecutor<String> scriptExecutor(RedisConnectionFactory connectionFactory) {
        return new ClosingRedisScriptExecutor(
                Objects.requireNonNull(connectionFactory, "connectionFactory must not be null"));
    }

    private static DefaultRedisScript<String> acquireScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_PATH, RedisCocoRateLimitStore.class.getClassLoader()));
        script.setResultType(String.class);
        script.afterPropertiesSet();
        return script;
    }

    private static IllegalStateException invalidResult() {
        return new IllegalStateException("Redis rate-limit script returned an invalid result");
    }

    private static IllegalStateException invalidResult(Exception cause) {
        return new IllegalStateException("Redis rate-limit script returned an invalid result", cause);
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Redis rate-limit store is closed");
        }
    }

    private static RedisConnection requireConnection(RedisConnection connection) {
        if (connection == null) {
            throw new IllegalStateException("Redis connection factory returned no connection");
        }
        return connection;
    }

    private static final class ClosingRedisScriptExecutor implements ScriptExecutor<String> {

        private final RedisConnectionFactory connectionFactory;

        private ClosingRedisScriptExecutor(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... arguments) {
            if (script.getResultType() != String.class || keys.size() != 1 || arguments.length != 2) {
                throw new IllegalArgumentException("Redis rate-limit script invocation is invalid");
            }
            byte[] result;
            try (RedisConnection connection = requireConnection(this.connectionFactory.getConnection())) {
                result = connection.scriptingCommands().eval(script.getScriptAsString().getBytes(StandardCharsets.UTF_8),
                        ReturnType.VALUE, 1, keys.get(0).getBytes(StandardCharsets.UTF_8),
                        String.valueOf(arguments[0]).getBytes(StandardCharsets.UTF_8),
                        String.valueOf(arguments[1]).getBytes(StandardCharsets.UTF_8));
            }
            return result == null ? null : script.getResultType().cast(new String(result, StandardCharsets.UTF_8));
        }

        @Override
        public <T> T execute(RedisScript<T> script, RedisSerializer<?> argumentsSerializer,
                RedisSerializer<T> resultSerializer, List<String> keys, Object... arguments) {
            return execute(script, keys, arguments);
        }
    }
}
