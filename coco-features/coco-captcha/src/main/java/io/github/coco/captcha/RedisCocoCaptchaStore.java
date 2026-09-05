package io.github.coco.captcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的验证码答案存储。
 * <p>
 * 多实例部署时替代进程内实现:验证码在任一实例生成、任一实例校验都能命中。
 * {@link #consume} 用 Lua 脚本做 GET+DEL 原子取用,保证同一验证码不会被两个并发请求同时消费。
 * 键名对 captchaId 做 SHA-256 摘要,避免标识明文进入 Redis。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-captcha}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
public final class RedisCocoCaptchaStore implements CocoCaptchaStore {

    private static final DefaultRedisScript<String> STORE_SCRIPT = storeScript();

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = consumeScript();

    private final ScriptExecutor scriptExecutor;

    private final String keyPrefix;

    /**
     * 使用 StringRedisTemplate 创建 Redis 验证码存储。
     * @param stringRedisTemplate Redis 模板
     * @param keyPrefix 键前缀
     */
    public RedisCocoCaptchaStore(StringRedisTemplate stringRedisTemplate, String keyPrefix) {
        this(executorOf(stringRedisTemplate), keyPrefix);
    }

    RedisCocoCaptchaStore(ScriptExecutor scriptExecutor, String keyPrefix) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor");
        this.keyPrefix = requirePrefix(keyPrefix);
    }

    @Override
    public void store(String captchaId, String answer, Duration ttl) {
        Objects.requireNonNull(captchaId, "captchaId");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        scriptExecutor.execute(STORE_SCRIPT, List.of(redisKey(captchaId)),
                answer, Long.toString(ttl.toMillis()));
    }

    @Override
    public String consume(String captchaId) {
        Objects.requireNonNull(captchaId, "captchaId");
        // Redis failures propagate deliberately. null is already taken: it means "expired or
        // absent", which verify() renders to the user as a wrong captcha. Reusing it for an
        // outage would report a correct answer as wrong and hide the outage from error rates.
        // store() throws on the generate path anyway, so a user cannot reach verification
        // during an outage — swallowing here would buy nothing.
        return scriptExecutor.execute(CONSUME_SCRIPT, List.of(redisKey(captchaId)));
    }

    private String redisKey(String captchaId) {
        return keyPrefix + digest(captchaId);
    }

    // Hash the id so a raw captchaId never lands in Redis keyspace (visible via
    // KEYS/SCAN/slowlog). SHA-256 keeps the mapping stable across instances.
    private static String digest(String captchaId) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(captchaId.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String requirePrefix(String keyPrefix) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        return keyPrefix;
    }

    private static ScriptExecutor executorOf(StringRedisTemplate stringRedisTemplate) {
        Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        return (script, keys, arguments) -> stringRedisTemplate.execute(script, keys, (Object[]) arguments);
    }

    static DefaultRedisScript<String> storeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);
        script.setScriptText("""
                redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[2]))
                return 'OK'
                """);
        return script;
    }

    static DefaultRedisScript<String> consumeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);
        // GET and DEL must be one atomic step. A read-then-delete round trip lets two
        // concurrent verifications both read the same answer before either deletes it,
        // which breaks the single-use guarantee the SPI promises.
        script.setScriptText("""
                local answer = redis.call('GET', KEYS[1])
                if answer then
                    redis.call('DEL', KEYS[1])
                end
                return answer
                """);
        return script;
    }

    /**
     * 脚本执行入口。
     * <p>
     * 抽出这层是为了让本类在没有真实 Redis 的情况下也能单测。
     * </p>
     * @author patton174
     * @since 2.1.0
     */
    @FunctionalInterface
    interface ScriptExecutor {

        /**
         * 执行 Lua 脚本。
         * @param script 待执行脚本
         * @param keys 键列表
         * @param arguments 脚本参数
         * @return 脚本返回值
         */
        String execute(DefaultRedisScript<String> script, List<String> keys, String... arguments);
    }
}
