package io.github.coco.captcha;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 验证码服务:按类型路由到生成器,生成时把答案存入 {@link CocoCaptchaStore},核验时取出并交回生成器比对。
 * <p>
 * 构造时把注入的生成器按 {@link CocoCaptchaGenerator#supportedType()} 建索引;同类型多个时后者覆盖前者
 * 并告警。生成会自动分配 {@code captchaId},答案按配置 TTL 存入 store。核验是单次的:{@code store.consume}
 * 取出即删,无论比对成败该验证码都作废,防止重放。
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
public final class CocoCaptchaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoCaptchaService.class);

    private final Map<CocoCaptchaType, CocoCaptchaGenerator> generators =
            new EnumMap<>(CocoCaptchaType.class);

    private final CocoCaptchaStore store;

    private final Duration ttl;

    /**
     * 创建验证码服务。
     * @param generators 已注册的生成器
     * @param store 答案存储
     * @param ttl 验证码存活时间
     */
    public CocoCaptchaService(List<CocoCaptchaGenerator> generators, CocoCaptchaStore store, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        for (CocoCaptchaGenerator generator : Objects.requireNonNull(generators, "generators must not be null")) {
            CocoCaptchaGenerator previous = this.generators.put(generator.supportedType(), generator);
            if (previous != null) {
                LOGGER.warn("Multiple Coco captcha generators for type {}; {} overrides {}",
                        generator.supportedType(), generator.getClass().getName(), previous.getClass().getName());
            }
        }
    }

    /**
     * 生成一个指定类型的验证码,答案存入 store。
     * @param type 验证码类型
     * @return 客户端视图(不含答案);无对应生成器时抛出 {@link IllegalArgumentException}
     */
    public CocoCaptcha.ClientView generate(CocoCaptchaType type) {
        Objects.requireNonNull(type, "type must not be null");
        CocoCaptchaGenerator generator = this.generators.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("no captcha generator registered for type " + type);
        }
        CocoCaptcha captcha = generator.generate(UUID.randomUUID().toString());
        this.store.store(captcha.captchaId(), captcha.answer(), this.ttl);
        return captcha.toClientView();
    }

    /**
     * 核验一次提交(单次消费)。
     * @param type 验证码类型
     * @param captchaId 生成时下发的标识
     * @param submitted 客户端提交的值
     * @return 通过返回 {@code true};过期、不存在、类型无生成器或不匹配均为 {@code false}
     */
    public boolean verify(CocoCaptchaType type, String captchaId, String submitted) {
        Objects.requireNonNull(type, "type must not be null");
        CocoCaptchaGenerator generator = this.generators.get(type);
        if (generator == null) {
            LOGGER.warn("No Coco captcha generator registered for type {}; rejecting verification", type);
            return false;
        }
        String answer = this.store.consume(captchaId);
        if (answer == null) {
            return false;
        }
        return generator.matches(submitted, answer);
    }

    /**
     * 是否存在支持某类型的生成器。
     * @param type 验证码类型
     * @return 存在返回 {@code true}
     */
    public boolean supports(CocoCaptchaType type) {
        return this.generators.containsKey(type);
    }
}
