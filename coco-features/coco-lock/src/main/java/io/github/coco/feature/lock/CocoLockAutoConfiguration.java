package io.github.coco.feature.lock;

import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Coco 锁模块自动配置。
 * <p>
 * 模块独立加载，不参与标准 CocoFeature 或单一 Starter 组合。业务应用可通过声明 {@link CocoLockManager} Bean
 * 覆盖默认实现。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoLockProperties.class)
@ConditionalOnProperty(prefix = "coco.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoLockAutoConfiguration {

    /**
     * 在注册默认实现或业务覆盖实现前校验配置边界。
     * @param properties 锁配置
     * @return 已完成校验的标记 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoLockPropertiesValidation")
    public CocoLockPropertiesValidation cocoLockPropertiesValidation(CocoLockProperties properties) {
        properties.validate();
        return new CocoLockPropertiesValidation();
    }

    /**
     * 创建默认本地锁管理器。
     * @param properties 锁配置
     * @return 本地锁管理器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoLockManager.class)
    @ConditionalOnProperty(prefix = "coco.lock", name = "type", havingValue = "local", matchIfMissing = true)
    public CocoLockManager localCocoLockManager(CocoLockProperties properties) {
        properties.validate();
        return new LocalCocoLockManager();
    }

    /**
     * Redis 类可用时的 Redis 锁自动配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnProperty(prefix = "coco.lock", name = "type", havingValue = "redis")
    static class RedisConfiguration {

        /**
         * 创建 Redis 锁管理器。缺少连接工厂时不注册该 Bean，外层 fail-fast 配置会给出明确错误。
         * @param connectionFactory Redis 连接工厂
         * @param properties 锁配置
         * @return Redis 锁管理器
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean(CocoLockManager.class)
        CocoLockManager redisCocoLockManager(RedisConnectionFactory connectionFactory, CocoLockProperties properties) {
            properties.validate();
            return new RedisCocoLockManager(connectionFactory, properties.getKeyPrefix());
        }
    }

    /**
     * Redis 类型配置的缺失依赖检查。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "coco.lock", name = "type", havingValue = "redis")
    static class RedisRequiredConfiguration {

        /**
         * 当未提供 Redis 连接工厂时在启动期失败，禁止回退本地实现。
         * @param properties 锁配置
         * @return 启动期校验器
         */
        @Bean
        @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "cocoLockRedisValidation",
                type = "org.springframework.data.redis.connection.RedisConnectionFactory")
        RedisValidation cocoLockRedisValidation(CocoLockProperties properties) {
            properties.validate();
            throw new IllegalStateException("coco.lock.type=redis requires a RedisConnectionFactory");
        }
    }

    /**
     * Redis 驱动不在类路径时的启动期校验。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "coco.lock", name = "type", havingValue = "redis")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass(
            "org.springframework.data.redis.connection.RedisConnectionFactory")
    static class MissingRedisDriverConfiguration {

        /**
         * Redis 类型要求 Spring Data Redis 位于运行期类路径。
         * @param properties 锁配置
         * @return 不会正常返回
         */
        @Bean
        RedisValidation cocoLockMissingRedisDriverValidation(CocoLockProperties properties) {
            properties.validate();
            throw new IllegalStateException("coco.lock.type=redis requires Spring Data Redis on the classpath");
        }
    }

    /**
     * 创建 {@link CocoLocked} AOP 顾问。
     * @param lockManager 锁管理器
     * @param properties 锁配置
     * @return 锁 AOP 顾问
     */
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

    /**
     * 注册基于 Advisor 的自动代理创建器，使独立引入模块的 Spring Bean 方法也能被 {@link CocoLocked} 拦截。
     * @return Advisor 自动代理创建器
     */
    @Bean
    @ConditionalOnMissingBean(AbstractAdvisorAutoProxyCreator.class)
    public DefaultAdvisorAutoProxyCreator cocoLockAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }

    /**
     * Redis 配置启动期标记类型。
     */
    static final class RedisValidation {
        private RedisValidation() {
        }
    }

    /**
     * 锁属性启动期校验标记类型。
     */
    static final class CocoLockPropertiesValidation {
        private CocoLockPropertiesValidation() {
        }
    }
}
