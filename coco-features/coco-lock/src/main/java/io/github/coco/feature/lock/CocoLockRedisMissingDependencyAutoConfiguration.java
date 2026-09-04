package io.github.coco.feature.lock;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Redis 存储缺少运行时依赖时的失败关闭配置。 */
@AutoConfiguration(before = CocoLockAutoConfiguration.class)
@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.lock", name = "store-type", havingValue = "redis")
public class CocoLockRedisMissingDependencyAutoConfiguration {

    /** 在没有应用自定义 Store 时给出明确的 Redis 依赖错误。 */
    @Bean
    @ConditionalOnMissingBean(CocoLockStore.class)
    CocoLockStore missingRedisCocoLockStore() {
        throw new BeanCreationException("coco.lock.store-type=redis requires "
                + "org.springframework.data.redis.core.StringRedisTemplate on the runtime classpath");
    }
}
