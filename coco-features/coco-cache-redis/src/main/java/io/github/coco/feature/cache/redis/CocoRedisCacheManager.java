package io.github.coco.feature.cache.redis;

import java.util.Map;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

/** 创建使用安全 null marker 的 Redis Cache。 */
final class CocoRedisCacheManager extends RedisCacheManager {

    CocoRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultConfiguration,
            boolean allowRuntimeCacheCreation,
            Map<String, RedisCacheConfiguration> initialConfigurations) {
        super(cacheWriter, defaultConfiguration, allowRuntimeCacheCreation, initialConfigurations);
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration configuration) {
        CocoRedisCacheNamespaceValidator.validate(name, "Redis cache name");
        return new CocoRedisCache(name, getCacheWriter(), configuration);
    }
}
