package io.github.coco.feature.idempotency.redis;

import io.github.coco.feature.idempotency.CocoIdempotencyAutoConfiguration;
import io.github.coco.feature.idempotency.CocoIdempotencyFeature;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.api.feature.CocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 幂等共享存储自动配置。
 *
 * <p>Redis 连接工厂是启用该适配器后的必需依赖；用户提供的 {@link CocoIdempotencyStore}
 * 优先于本适配器。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoIdempotencyAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = CocoIdempotencyFeature.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = CocoIdempotencyRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoIdempotencyRedisProperties.class)
public class CocoIdempotencyRedisAutoConfiguration {

    /**
     * 创建 Redis 幂等存储。
     *
     * @param connectionFactory Redis 连接工厂；缺失时启动失败
     * @param properties Redis 适配器配置
     * @return Redis 幂等存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public RedisCocoIdempotencyStore redisCocoIdempotencyStore(RedisConnectionFactory connectionFactory,
            CocoIdempotencyRedisProperties properties) {
        return new RedisCocoIdempotencyStore(connectionFactory, properties);
    }
}
