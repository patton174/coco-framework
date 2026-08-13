package io.github.coco.feature.cache.redis;

import java.util.Arrays;

import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

/** 避免 Spring Data Redis 的 JDK 序列化 null marker。 */
final class CocoRedisCache extends RedisCache {

    private static final byte[] NULL_VALUE = new byte[] { 0, 'C', 'O', 'C', 'O', '-', 'N', 'U', 'L', 'L' };

    CocoRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration configuration) {
        super(name, cacheWriter, configuration);
    }

    @Override
    protected byte[] serializeCacheValue(Object value) {
        if (value == NullValue.INSTANCE) {
            return NULL_VALUE.clone();
        }
        return super.serializeCacheValue(value);
    }

    @Override
    protected Object deserializeCacheValue(byte[] value) {
        if (Arrays.equals(NULL_VALUE, value)) {
            return NullValue.INSTANCE;
        }
        return super.deserializeCacheValue(value);
    }
}
