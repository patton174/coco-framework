package io.github.coco.feature.idempotency;

import java.time.Clock;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis shared-store auto-configuration for idempotency. */
@AutoConfiguration(before = CocoIdempotencyAutoConfiguration.class)
@EnableConfigurationProperties(CocoIdempotencyProperties.class)
@ConditionalOnCocoFeature(CocoFeature.IDEMPOTENCY)
@ConditionalOnProperty(prefix = "coco.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.idempotency", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class CocoIdempotencyRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public CocoIdempotencyStore redisCocoIdempotencyStore(CocoIdempotencyProperties properties,
            StringRedisTemplate stringRedisTemplate, @Qualifier("cocoIdempotencyClock") Clock clock) {
        return new RedisCocoIdempotencyStore(stringRedisTemplate, properties.getRedis().getKeyPrefix(), clock);
    }
}
