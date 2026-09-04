package io.github.coco.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

/**
 * Coco 缓存自动配置。
 * <p>
 * {@code coco.cache.enabled=true} 时装配 {@link CocoCacheManager} 并开启 Spring 缓存注解
 * （{@code @Cacheable} 等）。L2 存储与失效广播发布器通过 {@link ObjectProvider} 可选注入：
 * {@link CocoCacheStoreType#LOCAL} 拓扑下二者缺省为 {@code null}，退化为纯本地缓存；
 * {@link CocoCacheStoreType#TWO_LEVEL} 拓扑下由 {@link CocoCacheRedisAutoConfiguration} 提供。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoCacheProperties.class)
@ConditionalOnProperty(prefix = "coco.cache", name = "enabled", havingValue = "true")
@EnableCaching
public class CocoCacheAutoConfiguration {

    /**
     * 创建缓存管理器。
     * @param properties 缓存配置
     * @param l2 可选的 L2 存储
     * @param publisher 可选的失效广播发布器
     * @return 缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public CocoCacheManager cocoCacheManager(CocoCacheProperties properties,
            ObjectProvider<CocoCacheL2Store> l2, ObjectProvider<CocoCacheInvalidationPublisher> publisher) {
        return new CocoCacheManager(properties, l2.getIfAvailable(), publisher.getIfAvailable());
    }
}
