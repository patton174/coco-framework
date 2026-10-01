package io.github.coco.feature.web;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.RedisCocoReplayStore;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis shared-store auto-configuration for Web replay protection. */
@AutoConfiguration(before = CocoWebAutoConfiguration.class)
@EnableConfigurationProperties(CocoWebProperties.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = "coco.web.replay", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
public class CocoWebRedisReplayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CocoReplayStore.class)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoReplayStore redisCocoReplayStore(CocoWebProperties properties, ConfigurableListableBeanFactory beanFactory) {
        return new RedisCocoReplayStore(resolve(beanFactory, properties.getReplay().getRedis().getTemplateBeanName(),
                "coco.web.replay.redis.template-bean-name"), properties.getReplay().getRedis().getKeyPrefix());
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
