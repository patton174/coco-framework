package io.github.coco.feature.ratelimit.redis;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.ratelimit.CocoRateLimitAutoConfiguration;
import io.github.coco.feature.ratelimit.CocoRateLimitStore;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Coco Redis 限流存储自动配置。
 * <p>
 * 仅当 Coco 限流已启用且应用提供 {@link RedisConnectionFactory} 时注册 Redis store；业务自定义的
 * {@link CocoRateLimitStore} 始终优先。
 * </p>
 */
@AutoConfiguration(before = CocoRateLimitAutoConfiguration.class,
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = "coco.rate-limit.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoRateLimitRedisProperties.class)
public class CocoRateLimitRedisAutoConfiguration {

    /**
     * 创建 Redis 固定窗口限流存储。
     * @param connectionFactory Redis 连接工厂
     * @param properties Redis 限流存储配置
     * @return Redis 限流存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoRateLimitStore.class)
    public CocoRateLimitStore cocoRateLimitRedisStore(RedisConnectionFactory connectionFactory,
            CocoRateLimitRedisProperties properties) {
        return new RedisCocoRateLimitStore(connectionFactory, properties);
    }
}
