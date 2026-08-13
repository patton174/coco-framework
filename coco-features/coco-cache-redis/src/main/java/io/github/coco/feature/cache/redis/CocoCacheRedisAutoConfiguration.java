package io.github.coco.feature.cache.redis;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.coco.feature.cache.CocoCacheAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Coco Redis Spring Cache 自动配置。
 * <p>
 * 本配置不创建 Redis 连接，也不提供缓存注解拦截器。应用必须提供
 * {@link RedisConnectionFactory}；应用已有 {@link CacheManager} 或名为
 * {@code cacheResolver} 的 Bean 时，本配置完全回退。
 * </p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        before = CocoCacheAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = CocoCacheRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoCacheRedisProperties.class)
public class CocoCacheRedisAutoConfiguration {

    /** 业务覆盖完整 Redis 缓存配置时必须使用的 Bean 名称。 */
    public static final String CACHE_CONFIGURATION_BEAN_NAME = "cocoRedisCacheConfiguration";

    private static final String APPLICATION_NAME_PROPERTY = "spring.application.name";

    /**
     * 创建安全的默认 Redis 缓存配置。
     * <p>
     * 默认值序列化只保留 JSON scalar、collection 和 map 结构，不启用 Jackson default typing。
     * 任意 DTO 需要保持原类型时，业务方应以 {@link #CACHE_CONFIGURATION_BEAN_NAME} 覆盖完整配置。
     * </p>
     * @param properties Coco Redis 缓存属性
     * @param environment 应用环境
     * @return 默认 Redis 缓存配置
     */
    @Bean(CACHE_CONFIGURATION_BEAN_NAME)
    @ConditionalOnMissingBean(value = CacheManager.class,
            name = { "cacheResolver", CACHE_CONFIGURATION_BEAN_NAME })
    public RedisCacheConfiguration cocoRedisCacheConfiguration(CocoCacheRedisProperties properties,
            Environment environment) {
        validateDefaultProperties(properties, environment);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJacksonJsonRedisSerializer(JsonMapper.builder().build())))
                .entryTtl(properties.getTimeToLive());
        if (!properties.isAllowNullValues()) {
            configuration = configuration.disableCachingNullValues();
        }
        if (properties.isUseKeyPrefix()) {
            String keyPrefix = resolveKeyPrefix(properties, environment);
            configuration = configuration.computePrefixWith(cacheName -> keyPrefix + cacheName + "::");
        }
        else {
            configuration = configuration.disableKeyPrefix();
        }
        return configuration;
    }

    /**
     * 创建 Redis 缓存管理器。
     * @param connectionFactory 应用提供的 Redis 连接工厂
     * @param properties Coco Redis 缓存属性
     * @param cacheConfiguration 唯一命名的完整 Redis 缓存配置
     * @return Redis 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean(value = CacheManager.class, name = "cacheResolver")
    public RedisCacheManager cocoRedisCacheManager(RedisConnectionFactory connectionFactory,
            CocoCacheRedisProperties properties,
            @Qualifier(CACHE_CONFIGURATION_BEAN_NAME) RedisCacheConfiguration cacheConfiguration) {
        Set<String> cacheNames = CocoRedisCacheNamespaceValidator.validateCacheNames(properties.getCacheNames());
        Map<String, RedisCacheConfiguration> initialConfigurations = new LinkedHashMap<>();
        for (String cacheName : cacheNames) {
            initialConfigurations.put(cacheName, cacheConfiguration);
        }
        return new CocoRedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                cacheConfiguration, cacheNames.isEmpty(), initialConfigurations);
    }

    private static void validateDefaultProperties(CocoCacheRedisProperties properties, Environment environment) {
        Duration timeToLive = properties.getTimeToLive();
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalStateException("coco.cache.redis.time-to-live must be positive");
        }
        if (!properties.isUseKeyPrefix()) {
            if (environment.containsProperty(CocoCacheRedisProperties.PROPERTY_PREFIX + ".key-prefix")) {
                throw new IllegalStateException("coco.cache.redis.key-prefix cannot be set when "
                        + "coco.cache.redis.use-key-prefix=false");
            }
            return;
        }
        resolveKeyPrefix(properties, environment);
    }

    private static String resolveKeyPrefix(CocoCacheRedisProperties properties, Environment environment) {
        String configuredPrefix = properties.getKeyPrefix();
        if (configuredPrefix != null) {
            CocoRedisCacheNamespaceValidator.validate(configuredPrefix, "coco.cache.redis.key-prefix");
            return configuredPrefix;
        }
        String applicationName = environment.getProperty(APPLICATION_NAME_PROPERTY);
        CocoRedisCacheNamespaceValidator.validate(applicationName, APPLICATION_NAME_PROPERTY);
        return "coco:" + applicationName + ":";
    }
}
