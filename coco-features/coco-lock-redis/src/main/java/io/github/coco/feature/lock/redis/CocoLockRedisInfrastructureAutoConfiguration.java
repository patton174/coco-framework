package io.github.coco.feature.lock.redis;

import io.github.coco.feature.lock.CocoLockAutoConfiguration;
import io.github.coco.feature.lock.CocoLockManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 锁基础设施的启动期校验。
 * <p>
 * 本自动配置不引用 Spring Data Redis 类型，因此在 Redis API 缺失时仍能给出 fail-fast 结果。
 * 业务显式提供 {@link CocoLockManager} 时完全回退。
 * </p>
 */
@AutoConfiguration(after = CocoLockAutoConfiguration.class,
        afterName = "io.github.coco.feature.lock.redis.CocoLockRedisAutoConfiguration")
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = CocoLockRedisProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
public class CocoLockRedisInfrastructureAutoConfiguration {

    /**
     * Redis 适配器已显式启用但最终未注册锁管理器时终止启动。
     * @return 不会正常返回
     */
    @Bean
    @ConditionalOnMissingBean(CocoLockManager.class)
    RedisInfrastructureValidation cocoLockRedisInfrastructureValidation() {
        throw new IllegalStateException("coco.lock.redis.enabled=true requires Spring Data Redis and a "
                + "RedisConnectionFactory unless a CocoLockManager is provided");
    }

    static final class RedisInfrastructureValidation {
        private RedisInfrastructureValidation() {
        }
    }
}
