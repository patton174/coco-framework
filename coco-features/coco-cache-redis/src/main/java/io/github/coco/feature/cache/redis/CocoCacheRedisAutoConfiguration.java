package io.github.coco.feature.cache.redis;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.feature.cache.CocoCacheAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Coco Redis Spring Cache 自动配置。
 * <p>
 * 本配置不创建 Redis 连接，也不提供缓存注解拦截器。应用必须提供
 * {@link RedisConnectionFactory}；应用已有 {@link CacheManager} 或名为
 * {@code cacheResolver} 的 Bean 时，本配置完全回退。
 * </p>
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class, before = CocoCacheAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = CocoCacheRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoCacheRedisProperties.class)
public class CocoCacheRedisAutoConfiguration {

    /**
     * 创建 Redis 缓存管理器。
     * <p>
     * 当业务方声明 {@link RedisCacheConfiguration} 时，该配置直接作为默认 Redis
     * 缓存配置使用，Coco 的 TTL、序列化器和前缀默认值不再参与组合。
     * </p>
     * @param connectionFactory 应用提供的 Redis 连接工厂
     * @param properties Coco Redis 缓存属性
     * @param customConfiguration 业务方可选的完整 Redis 缓存配置
     * @return Redis 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean(value = CacheManager.class, name = "cacheResolver")
    public RedisCacheManager cocoRedisCacheManager(RedisConnectionFactory connectionFactory,
            CocoCacheRedisProperties properties, ObjectProvider<RedisCacheConfiguration> customConfigurations,
            Environment environment) {
        rejectIgnoredKeyPrefix(properties, environment);
        RedisCacheConfiguration customConfiguration = customConfigurations.getIfAvailable();
        RedisCacheConfiguration configuration = customConfiguration == null
                ? defaultCacheConfiguration(properties) : customConfiguration;
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration);
        if (!properties.getCacheNames().isEmpty()) {
            builder.initialCacheNames(cacheNames(properties)).disableCreateOnMissingCache();
        }
        return builder.build();
    }

    private static RedisCacheConfiguration defaultCacheConfiguration(CocoCacheRedisProperties properties) {
        @SuppressWarnings("removal")
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer))
                .entryTtl(properties.getTimeToLive());
        if (!properties.isAllowNullValues()) {
            configuration = configuration.disableCachingNullValues();
        }
        if (properties.isUseKeyPrefix()) {
            String keyPrefix = properties.getKeyPrefix();
            configuration = configuration.computePrefixWith(cacheName -> keyPrefix + cacheName + "::");
        }
        else {
            configuration = configuration.disableKeyPrefix();
        }
        return configuration;
    }

    private static Set<String> cacheNames(CocoCacheRedisProperties properties) {
        return new LinkedHashSet<>(properties.getCacheNames());
    }

    private static void rejectIgnoredKeyPrefix(CocoCacheRedisProperties properties, Environment environment) {
        if (!properties.isUseKeyPrefix()
                && environment.containsProperty(CocoCacheRedisProperties.PROPERTY_PREFIX + ".key-prefix")) {
            throw new IllegalStateException("coco.cache.redis.key-prefix cannot be set when "
                    + "coco.cache.redis.use-key-prefix=false");
        }
    }
}
