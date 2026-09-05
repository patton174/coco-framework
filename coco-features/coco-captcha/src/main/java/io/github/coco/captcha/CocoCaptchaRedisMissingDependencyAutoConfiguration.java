package io.github.coco.captcha;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 验证码存储缺少运行时依赖时的失败关闭配置。
 * <p>
 * 显式要求 Redis 存储却没有 Spring Data Redis 时,启动即报错,而不是静默退回进程内存储 —— 后者在多实例
 * 部署下会让验证码校验随机失败,问题很难定位。
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
@AutoConfiguration(before = CocoCaptchaAutoConfiguration.class)
@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "coco.captcha", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.captcha", name = "store-type", havingValue = "redis")
public class CocoCaptchaRedisMissingDependencyAutoConfiguration {

    /**
     * 在业务未自备 Store 时给出明确的 Redis 依赖错误。
     * @return 不会正常返回,始终抛出异常
     */
    @Bean
    @ConditionalOnMissingBean(CocoCaptchaStore.class)
    CocoCaptchaStore missingRedisCocoCaptchaStore() {
        throw new BeanCreationException("coco.captcha.store-type=redis requires "
                + "org.springframework.data.redis.core.StringRedisTemplate on the runtime classpath");
    }
}
