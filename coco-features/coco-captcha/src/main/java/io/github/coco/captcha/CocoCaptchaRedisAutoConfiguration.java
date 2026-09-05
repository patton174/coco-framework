package io.github.coco.captcha;

import java.util.Arrays;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Coco 验证码 Redis 共享存储自动配置。
 * <p>
 * 仅在 {@code coco.captcha.store-type=redis} 且类路径上有 Spring Data Redis 时装配。排在
 * {@link CocoCaptchaAutoConfiguration} 之前,让主配置的 {@code @ConditionalOnMissingBean} 自动退让。
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
@EnableConfigurationProperties(CocoCaptchaProperties.class)
@ConditionalOnProperty(prefix = "coco.captcha", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.captcha", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
public class CocoCaptchaRedisAutoConfiguration {

    /**
     * 创建 Redis 验证码答案存储。
     * @param properties 验证码配置
     * @param beanFactory 用于解析 StringRedisTemplate 的容器
     * @return Redis 答案存储
     */
    @Bean
    @ConditionalOnMissingBean(CocoCaptchaStore.class)
    public CocoCaptchaStore redisCocoCaptchaStore(CocoCaptchaProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        StringRedisTemplate template = resolve(beanFactory, properties.getRedis().getTemplateBeanName(),
                "coco.captcha.redis.template-bean-name");
        return new RedisCocoCaptchaStore(template, properties.getRedis().getKeyPrefix());
    }

    // Mirrors the resolution rule the lock/idempotency modules use: an explicit bean name wins,
    // otherwise a sole candidate or a sole @Primary one. Ambiguity fails loudly rather than
    // silently binding captcha answers to whichever template happens to be first.
    private static StringRedisTemplate resolve(ConfigurableListableBeanFactory factory, String configuredName,
            String key) {
        if (configuredName != null) {
            if (!factory.containsBean(configuredName)
                    || !factory.isTypeMatch(configuredName, StringRedisTemplate.class)) {
                throw new BeanCreationException(key + " references StringRedisTemplate bean '" + configuredName
                        + "' that does not exist or has the wrong type");
            }
            return factory.getBean(configuredName, StringRedisTemplate.class);
        }
        String[] names = factory.getBeanNamesForType(StringRedisTemplate.class, false, false);
        if (names.length == 1) {
            return factory.getBean(names[0], StringRedisTemplate.class);
        }
        String[] primary = Arrays.stream(names)
                .filter(name -> factory.containsBeanDefinition(name) && factory.getBeanDefinition(name).isPrimary())
                .toArray(String[]::new);
        if (primary.length == 1) {
            return factory.getBean(primary[0], StringRedisTemplate.class);
        }
        throw new BeanCreationException(key + " requires one StringRedisTemplate or one @Primary candidate; candidates="
                + Arrays.toString(names) + "; configure " + key);
    }
}
