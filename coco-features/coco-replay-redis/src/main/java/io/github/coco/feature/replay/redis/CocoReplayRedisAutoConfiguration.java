package io.github.coco.feature.replay.redis;

import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.replay.CocoReplayStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 防重放存储自动配置。
 * <p>
 * 只有应用显式启用且 Spring Data Redis 在类路径中时才注册。业务项目自行提供
 * {@link CocoReplayStore} 时，此配置会完全回退。
 * </p>
 */
@AutoConfiguration(before = CocoWebAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(CocoReplayRedisProperties.class)
@ConditionalOnProperty(prefix = "coco.web.replay.redis", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoReplayRedisAutoConfiguration {

    /**
     * 创建 Redis 防重放共享存储。
     * @param connectionFactory Redis 连接工厂
     * @return Redis 防重放共享存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoReplayStore.class)
    public RedisCocoReplayStore redisCocoReplayStore(RedisConnectionFactory connectionFactory) {
        return new RedisCocoReplayStore(connectionFactory);
    }
}
