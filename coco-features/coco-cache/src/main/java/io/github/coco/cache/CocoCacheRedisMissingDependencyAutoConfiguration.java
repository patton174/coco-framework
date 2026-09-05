package io.github.coco.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * {@code store-type=two-level} 但 classpath 没有 Spring Data Redis 时,显式失败而非静默回落。
 * <p>
 * 缓存的静默回落不像锁那样直接导致数据错误,但用户既然配了 two-level 就是要跨实例一致;
 * 悄悄退回纯本地会让多实例读到各自的陈旧副本,且毫无提示。fail-closed 让配置意图与运行行为一致。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "coco.cache", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.cache", name = "store-type", havingValue = "two-level")
@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
public class CocoCacheRedisMissingDependencyAutoConfiguration {

    /**
     * 装配期抛出,指明缺失的依赖。
     * @return 永不返回
     */
    @Bean
    public Object cocoCacheRedisDependencyGuard() {
        throw new IllegalStateException("coco.cache.store-type=two-level requires "
                + "org.springframework.data.redis.core.StringRedisTemplate on the runtime classpath");
    }
}
