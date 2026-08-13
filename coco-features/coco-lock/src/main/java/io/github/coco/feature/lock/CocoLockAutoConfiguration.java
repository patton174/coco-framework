package io.github.coco.feature.lock;

import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Coco 锁模块自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoLockProperties.class)
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoLockAutoConfiguration {

    /** @return 已校验的配置标记 */
    @Bean
    @ConditionalOnMissingBean(name = "cocoLockPropertiesValidation")
    public CocoLockPropertiesValidation cocoLockPropertiesValidation(CocoLockProperties properties) {
        properties.validate();
        return new CocoLockPropertiesValidation();
    }

    /** @return 默认本地锁管理器 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoLockManager.class)
    @ConditionalOnProperty(prefix = "coco.lock.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    public CocoLockManager localCocoLockManager(CocoLockProperties properties) {
        properties.validate();
        return new LocalCocoLockManager();
    }

    /** @return 锁 AOP 顾问 */
    @Bean
    @ConditionalOnBean(CocoLockManager.class)
    @ConditionalOnMissingBean(name = "cocoLockAdvisor")
    public DefaultPointcutAdvisor cocoLockAdvisor(CocoLockManager lockManager, CocoLockProperties properties) {
        Pointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return AnnotatedElementUtils.hasAnnotation(method, CocoLocked.class)
                        || AnnotatedElementUtils.hasAnnotation(targetClass, CocoLocked.class);
            }
        };
        return new DefaultPointcutAdvisor(pointcut, new CocoLockMethodInterceptor(lockManager, properties));
    }

    /** @return Advisor 自动代理创建器 */
    @Bean
    @ConditionalOnMissingBean(AbstractAdvisorAutoProxyCreator.class)
    public DefaultAdvisorAutoProxyCreator cocoLockAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }

    static final class CocoLockPropertiesValidation {
        private CocoLockPropertiesValidation() {
        }
    }
}
