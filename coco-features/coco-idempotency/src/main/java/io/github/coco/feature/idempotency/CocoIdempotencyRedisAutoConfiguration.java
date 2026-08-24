package io.github.coco.feature.idempotency;

import java.time.Clock;
import java.util.Arrays;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis shared-store auto-configuration for idempotency. */
@AutoConfiguration(before = CocoIdempotencyAutoConfiguration.class)
@EnableConfigurationProperties(CocoIdempotencyProperties.class)
@ConditionalOnCocoFeature(CocoFeature.IDEMPOTENCY)
@ConditionalOnProperty(prefix = "coco.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.idempotency", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
public class CocoIdempotencyRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public CocoIdempotencyStore redisCocoIdempotencyStore(CocoIdempotencyProperties properties,
            ConfigurableListableBeanFactory beanFactory, @Qualifier("cocoIdempotencyClock") Clock clock) {
        return new RedisCocoIdempotencyStore(resolve(beanFactory, properties.getRedis().getTemplateBeanName(),
                "coco.idempotency.redis.template-bean-name"), properties.getRedis().getKeyPrefix(), clock);
    }

    private static StringRedisTemplate resolve(ConfigurableListableBeanFactory factory, String configuredName, String key) {
        if (configuredName != null) {
            if (!factory.containsBean(configuredName) || !factory.isTypeMatch(configuredName, StringRedisTemplate.class)) { throw new BeanCreationException(key + " references StringRedisTemplate bean '" + configuredName + "' that does not exist or has the wrong type"); }
            return factory.getBean(configuredName, StringRedisTemplate.class);
        }
        String[] names = factory.getBeanNamesForType(StringRedisTemplate.class, false, false);
        if (names.length == 1) { return factory.getBean(names[0], StringRedisTemplate.class); }
        String[] primary = Arrays.stream(names).filter(name -> factory.containsBeanDefinition(name) && factory.getBeanDefinition(name).isPrimary()).toArray(String[]::new);
        if (primary.length == 1) { return factory.getBean(primary[0], StringRedisTemplate.class); }
        throw new BeanCreationException(key + " requires one StringRedisTemplate or one @Primary candidate; candidates=" + Arrays.toString(names) + "; configure " + key);
    }
}
