package io.github.coco.feature.ratelimit;

import java.time.Clock;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis shared-store auto-configuration for rate limiting. */
@AutoConfiguration(before = CocoRateLimitAutoConfiguration.class)
@EnableConfigurationProperties(CocoRateLimitProperties.class)
@ConditionalOnCocoFeature(CocoFeature.RATE_LIMIT)
@ConditionalOnProperty(prefix = "coco.rate-limit", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.rate-limit", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnSingleCandidate(StringRedisTemplate.class)
public class CocoRateLimitRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CocoRateLimitStore.class)
    public CocoRateLimitStore redisCocoRateLimitStore(CocoRateLimitProperties properties,
            StringRedisTemplate stringRedisTemplate, @Qualifier("cocoRateLimitClock") Clock clock) {
        return new RedisCocoRateLimitStore(stringRedisTemplate, properties.getRedis().getKeyPrefix(), clock);
    }
}
