package io.github.coco.feature.concurrencylimit.redis;

import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitAutoConfiguration;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Redis 并发许可存储自动配置。 */
@AutoConfiguration(before = CocoConcurrencyLimitAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "coco.concurrency-limit", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = CocoConcurrencyLimitRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoConcurrencyLimitRedisProperties.class)
public class CocoConcurrencyLimitRedisAutoConfiguration {
    /** 创建跨实例原子许可存储。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoConcurrencyLimitStore.class)
    public RedisCocoConcurrencyLimitStore cocoConcurrencyLimitRedisStore(RedisConnectionFactory factory,
            CocoConcurrencyLimitRedisProperties properties, Environment environment) {
        return new RedisCocoConcurrencyLimitStore(factory, properties, environment.getProperty("spring.application.name"));
    }
}
