package io.github.coco.feature.lock.redis;

import io.github.coco.feature.lock.CocoLockAutoConfiguration;
import io.github.coco.feature.lock.CocoLockManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Redis 分布式锁自动配置。 */
@AutoConfiguration(before = CocoLockAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = CocoLockRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoLockRedisProperties.class)
public class CocoLockRedisAutoConfiguration {
    /** @return Redis 锁管理器 */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(CocoLockManager.class)
    public CocoLockManager redisCocoLockManager(RedisConnectionFactory connectionFactory, CocoLockRedisProperties properties, Environment environment) {
        return new RedisCocoLockManager(connectionFactory, properties.resolveKeyPrefix(environment.getProperty("spring.application.name")));
    }
}
