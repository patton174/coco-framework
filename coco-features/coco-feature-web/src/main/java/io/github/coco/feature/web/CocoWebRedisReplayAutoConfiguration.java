package io.github.coco.feature.web;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.RedisCocoReplayStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis shared-store auto-configuration for Web replay protection. */
@AutoConfiguration(before = CocoWebAutoConfiguration.class)
@EnableConfigurationProperties(CocoWebProperties.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = "coco.web.replay", name = "store-type", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class CocoWebRedisReplayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CocoReplayStore.class)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoReplayStore redisCocoReplayStore(CocoWebProperties properties, StringRedisTemplate stringRedisTemplate) {
        return new RedisCocoReplayStore(stringRedisTemplate, properties.getReplay().getRedis().getKeyPrefix());
    }
}
