package io.github.coco.feature.lock;

import java.time.Clock;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;

/**
 * Coco 分布式锁自动配置。
 * <p>默认关闭；启用后，应用提供的 {@link CocoLockStore} Bean 优先于进程内参考实现。锁不改变事务边界，也不提供 exactly-once 保证。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoLockProperties.class)
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true")
public class CocoLockAutoConfiguration {
    /** 注册锁模块的国际化资源。 */
    @Bean
    @ConditionalOnMissingBean(name = "cocoLockMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoLockMessageBundleRegistrar() {
        return registry -> registry.add("coco-lock-messages");
    }

    /** 创建锁专用 UTC 时钟。 */
    @Bean("cocoLockClock")
    @ConditionalOnMissingBean(name = "cocoLockClock")
    public Clock cocoLockClock() { return Clock.systemUTC(); }

    /** 创建默认进程内 Store。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoLockStore.class)
    public CocoLockStore cocoLockStore(CocoLockProperties properties, @Qualifier("cocoLockClock") Clock clock) {
        return new InMemoryCocoLockStore(properties, clock, true);
    }

    /** 创建 Spring SpEL 锁键解析器。 */
    @Bean
    @ConditionalOnMissingBean
    public CocoLockKeyResolver cocoLockKeyResolver(CocoLockProperties properties) {
        return new DefaultCocoLockKeyResolver(properties);
    }

    /** 创建支持可重入和租约自动续期的锁管理器。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CocoLockManager cocoLockManager(CocoLockStore store, CocoLockProperties properties,
            @Qualifier("cocoLockClock") Clock clock) {
        return new DefaultCocoLockManager(store, properties, clock);
    }

    /** 创建同步方法锁拦截器。 */
    @Bean
    @ConditionalOnMissingBean
    public CocoLockAspect cocoLockAspect(CocoLockManager manager, CocoLockKeyResolver keyResolver,
            CocoLockProperties properties) {
        return new CocoLockAspect(manager, keyResolver, properties);
    }

    /** 注册可识别 JDK 接口代理注解的锁 Advisor。 */
    @Bean
    @ConditionalOnMissingBean(name = "cocoLockAdvisor")
    public DefaultPointcutAdvisor cocoLockAdvisor(CocoLockAspect aspect, CocoLockProperties properties) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(new CocoLockPointcut(), aspect);
        advisor.setOrder(properties.getAspectOrder());
        return advisor;
    }
}
