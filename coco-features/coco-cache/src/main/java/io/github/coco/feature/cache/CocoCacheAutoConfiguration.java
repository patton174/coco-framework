package io.github.coco.feature.cache;

import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Coco 本地缓存自动配置。
 * <p>
 * 默认启用 Spring Cache 注解支持；业务方声明 {@link CacheManager} 后，本配置不再提供默认缓存管理器。Caffeine
 * 存在时使用带大小和写入后过期策略的实现，否则使用 {@link ConcurrentMapCacheManager}。
 * </p>
 */
@AutoConfiguration
@EnableCaching
@EnableConfigurationProperties(CocoCacheProperties.class)
@ConditionalOnProperty(prefix = "coco.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoCacheAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(name = "cacheResolver")
    static class CacheResolverConfiguration {

        @Bean
        @ConditionalOnMissingBean(CachingConfigurer.class)
        CachingConfigurer cocoCacheResolverConfigurer(@Qualifier("cacheResolver") CacheResolver cacheResolver) {
            return new CacheResolverCachingConfigurer(cacheResolver);
        }

        private static final class CacheResolverCachingConfigurer implements CachingConfigurer {

            private final CacheResolver cacheResolver;

            private CacheResolverCachingConfigurer(CacheResolver cacheResolver) {
                this.cacheResolver = cacheResolver;
            }

            @Override
            public CacheResolver cacheResolver() {
                return this.cacheResolver;
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Caffeine.class)
    static class CaffeineCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(value = CacheManager.class, name = "cacheResolver")
        CaffeineCacheManager cocoCacheManager(CocoCacheProperties properties) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder();
            if (properties.getMaximumSize() != null) {
                builder.maximumSize(properties.getMaximumSize());
            }
            if (properties.getExpireAfterWrite() != null) {
                builder.expireAfterWrite(properties.getExpireAfterWrite());
            }
            CaffeineCacheManager cacheManager = new CaffeineCacheManager();
            cacheManager.setCaffeine(builder);
            cacheManager.setAllowNullValues(properties.isAllowNullValues());
            configureCacheNames(cacheManager, properties.getCacheNames());
            return cacheManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Caffeine")
    static class ConcurrentMapCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(value = CacheManager.class, name = "cacheResolver")
        ConcurrentMapCacheManager cocoCacheManager(CocoCacheProperties properties, Environment environment) {
            rejectCaffeineOnlyProperties(environment);
            ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
            cacheManager.setAllowNullValues(properties.isAllowNullValues());
            configureCacheNames(cacheManager, properties.getCacheNames());
            return cacheManager;
        }
    }

    private static void configureCacheNames(ConcurrentMapCacheManager cacheManager, List<String> cacheNames) {
        if (!cacheNames.isEmpty()) {
            cacheManager.setCacheNames(cacheNames);
        }
    }

    private static void configureCacheNames(CaffeineCacheManager cacheManager, List<String> cacheNames) {
        if (!cacheNames.isEmpty()) {
            cacheManager.setCacheNames(cacheNames);
        }
    }

    private static void rejectCaffeineOnlyProperties(Environment environment) {
        if (environment.containsProperty("coco.cache.maximum-size")
                || environment.containsProperty("coco.cache.expire-after-write")) {
            throw new IllegalStateException("coco.cache.maximum-size and coco.cache.expire-after-write require "
                    + "com.github.ben-manes.caffeine:caffeine on the runtime classpath.");
        }
    }
}
