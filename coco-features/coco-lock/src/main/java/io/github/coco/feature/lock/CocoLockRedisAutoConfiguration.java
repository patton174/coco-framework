package io.github.coco.feature.lock;

import java.time.Clock;
import java.util.Arrays;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Coco 锁 Redis 共享存储自动配置。 */
@AutoConfiguration(before = CocoLockAutoConfiguration.class)
@EnableConfigurationProperties(CocoLockProperties.class)
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.lock", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
public class CocoLockRedisAutoConfiguration {

    /** 创建 Redis 锁存储。 */
    @Bean
    @ConditionalOnMissingBean(CocoLockStore.class)
    public CocoLockStore redisCocoLockStore(CocoLockProperties properties, ConfigurableListableBeanFactory beanFactory,
            @Qualifier("cocoLockClock") Clock clock) {
        return new RedisCocoLockStore(resolve(beanFactory, properties.getRedis().getTemplateBeanName(),
                "coco.lock.redis.template-bean-name"), properties.getRedis().getKeyPrefix(), clock);
    }

    private static StringRedisTemplate resolve(ConfigurableListableBeanFactory factory, String configuredName, String key) {
        if (configuredName != null) {
            if (!factory.containsBean(configuredName) || !factory.isTypeMatch(configuredName, StringRedisTemplate.class)) {
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
