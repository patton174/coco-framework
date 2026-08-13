package io.github.coco.feature.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 本地缓存自动配置测试。
 */
class CocoCacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCacheAutoConfiguration.class));

    @Test
    void doesNotRegisterWhenDisabled() {
        this.contextRunner.withPropertyValues("coco.cache.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CacheManager.class);
            assertThat(context).doesNotHaveBean(CacheOperationSource.class);
        });
    }

    @Test
    void backsOffWhenApplicationProvidesCacheManager() {
        this.contextRunner.withUserConfiguration(CustomCacheManagerConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            assertThat(context).hasSingleBean(CacheOperationSource.class);
        });
    }

    @Test
    void backsOffWhenApplicationProvidesCacheResolver() {
        this.contextRunner.withUserConfiguration(CustomCacheResolverConfiguration.class).run(context ->
                assertThat(context).doesNotHaveBean(CacheManager.class));
        this.contextRunner.withClassLoader(new FilteredClassLoader(Caffeine.class))
                .withUserConfiguration(CustomCacheResolverConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));
    }

    @Test
    void createsConcurrentMapCacheManagerWithoutCaffeine() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(Caffeine.class)).run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            assertThat(context.getBean(CacheManager.class).getCache("dynamic")).isNotNull();
        });
    }

    @Test
    void createsCaffeineCacheManagerWithConfiguredPolicies() {
        this.contextRunner.withPropertyValues(
                "coco.cache.cache-names=orders,products",
                "coco.cache.maximum-size=123",
                "coco.cache.expire-after-write=30s",
                "coco.cache.allow-null-values=false")
                .run(context -> {
                    CaffeineCacheManager manager = context.getBean(CaffeineCacheManager.class);

                    assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("orders", "products");
                    assertThat(manager.getCache("unknown")).isNull();
                    CaffeineCache cache = (CaffeineCache) manager.getCache("orders");
                    @SuppressWarnings("unchecked")
                    Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();
                    assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(123L);
                    assertThat(nativeCache.policy().expireAfterWrite().orElseThrow()
                            .getExpiresAfter(TimeUnit.SECONDS)).isEqualTo(30L);
                    assertThat(cache.isAllowNullValues()).isFalse();
                    assertThatThrownBy(() -> cache.put("null", null))
                            .isInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void rejectsCaffeineOnlyPropertiesWithoutCaffeine() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(Caffeine.class))
                .withPropertyValues("coco.cache.maximum-size=10")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("require com.github.ben-manes.caffeine:caffeine"));
    }

    @Test
    void rejectsInvalidMaximumSize() {
        this.contextRunner.withPropertyValues("coco.cache.maximum-size=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("coco.cache.maximum-size"));
    }

    @Test
    void rejectsInvalidExpireAfterWrite() {
        this.contextRunner.withPropertyValues("coco.cache.expire-after-write=0s")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("coco.cache.expire-after-write"));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCacheManagerConfiguration {

        @Bean
        CacheManager applicationCacheManager() {
            return new ConcurrentMapCacheManager("application");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCacheResolverConfiguration {

        @Bean("cacheResolver")
        SimpleCacheResolver cacheResolver() {
            return new SimpleCacheResolver(new ConcurrentMapCacheManager());
        }
    }
}
