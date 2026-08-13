package io.github.coco.feature.audit;

import java.util.Collection;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.logging.core.CocoLogHandle;
import io.github.coco.logging.core.CocoLogHandleRegistrar;
import io.github.coco.logging.core.CocoLogManager;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditFormatter;
import io.github.coco.feature.audit.core.CocoAuditErrorHandler;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.CompositeCocoAuditPublisher;
import io.github.coco.feature.audit.core.DefaultCocoAuditFormatter;
import io.github.coco.feature.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.feature.audit.core.PolicyCocoAuditErrorHandler;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 审计功能自动配置。
 * <p>
 * 负责为审计功能模块注册国际化消息资源、默认日志记录器、审计发布器和访问日志审计适配器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = CocoCommonLoggingAutoConfiguration.class,
        afterName = "io.github.coco.feature.lock.CocoLockAutoConfiguration")
@ConditionalOnCocoFeature(CocoFeature.AUDIT)
@EnableConfigurationProperties(CocoAuditProperties.class)
public class CocoAuditAutoConfiguration {

    /**
     * <p>
     * 注册审计功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoAuditMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoAuditMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-audit-messages");
    }

    /**
     * <p>
     * 注册独立审计日志句柄。
     * </p>
     * @param properties 审计配置属性
     * @return 日志句柄注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoAuditLogHandleRegistrar")
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.logging", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoLogHandleRegistrar cocoAuditLogHandleRegistrar(CocoAuditProperties properties) {
        CocoAuditProperties.LoggingProperties logging = properties.getLogging();
        return registry -> registry.register(CocoLogHandle.of(LoggingCocoAuditRecorder.LOG_HANDLE,
                logging.getLoggerName(), logging.getLevel()));
    }

    /**
     * <p>
     * 创建默认审计事件格式化器。
     * </p>
     * @return 审计事件格式化器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoAuditFormatter cocoAuditFormatter() {
        return new DefaultCocoAuditFormatter();
    }

    /**
     * <p>
     * 创建默认审计日志记录器。
     * </p>
     * @param properties 审计配置属性
     * @param formatter 审计事件格式化器
     * @param logManager Coco 日志管理器
     * @return 审计记录器
     */
    @Bean
    @ConditionalOnBean(CocoLogManager.class)
    @ConditionalOnMissingBean(CocoAuditRecorder.class)
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.logging", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoAuditRecorder cocoAuditRecorder(CocoAuditProperties properties, CocoAuditFormatter formatter,
            CocoLogManager logManager) {
        return new LoggingCocoAuditRecorder(properties.getLogging(), formatter, logManager);
    }

    /**
     * <p>
     * 创建审计记录失败处理器。
     * </p>
     * @param properties 审计配置属性
     * @return 审计记录失败处理器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoAuditErrorHandler cocoAuditErrorHandler(CocoAuditProperties properties) {
        return new PolicyCocoAuditErrorHandler(properties.getFailurePolicy());
    }

    /**
     * <p>
     * 创建审计事件发布器。
     * </p>
     * @param auditRecorders 审计记录器集合
     * @param errorHandler 审计记录失败处理器
     * @return 审计事件发布器
     */
    @Bean
    @ConditionalOnBean(CocoAuditRecorder.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.async", name = "enabled", havingValue = "false", matchIfMissing = true)
    public CocoAuditPublisher cocoAuditPublisher(Collection<CocoAuditRecorder> auditRecorders,
            CocoAuditErrorHandler errorHandler) {
        return new CompositeCocoAuditPublisher(auditRecorders, errorHandler);
    }

    @Bean(name = "cocoAuditPublisher", destroyMethod = "close")
    @ConditionalOnBean(CocoAuditRecorder.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.async", name = "enabled", havingValue = "true")
    CocoAuditPublisher asyncCocoAuditPublisher(Collection<CocoAuditRecorder> auditRecorders,
            CocoAuditErrorHandler errorHandler, CocoAuditProperties properties) {
        CocoAuditProperties.AsyncProperties async = properties.getAsync();
        return new AsyncCocoAuditPublisher(new CompositeCocoAuditPublisher(auditRecorders, errorHandler),
                async.getQueueCapacity(), async.getShutdownTimeout(), properties.getFailurePolicy());
    }

    /**
     * <p>
     * 创建访问日志审计适配器。
     * </p>
     * @param auditPublisher 审计事件发布器
     * @return 访问日志记录器
     */
    @Bean
    @ConditionalOnBean(CocoAuditPublisher.class)
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.access-log", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoAccessLogRecorder cocoAccessLogAuditRecorder(CocoAuditPublisher auditPublisher) {
        return new CocoAccessLogAuditRecorder(auditPublisher);
    }

    /**
     * <p>
     * 创建声明式业务审计事件工厂。
     * </p>
     * @return 默认声明式审计事件工厂
     */
    @Bean
    @ConditionalOnBean(CocoAuditPublisher.class)
    @ConditionalOnMissingBean(CocoAuditEventFactory.class)
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoAuditEventFactory cocoAuditEventFactory() {
        return new DefaultCocoAuditEventFactory();
    }

    /**
     * <p>
     * 创建 {@link CocoAudited} AOP 顾问。方法注解优先于目标类型和接口类型注解。
     * </p>
     * @param auditPublisher 审计事件发布器
     * @param eventFactory 审计事件工厂
     * @return 声明式审计顾问
     */
    @Bean(name = "cocoAuditAdvisor")
    @ConditionalOnBean(CocoAuditPublisher.class)
    @ConditionalOnMissingBean(name = "cocoAuditAdvisor")
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DefaultPointcutAdvisor cocoAuditAdvisor(CocoAuditPublisher auditPublisher,
            CocoAuditEventFactory eventFactory) {
        Pointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                java.lang.reflect.Method specificMethod = org.springframework.core.BridgeMethodResolver.findBridgedMethod(
                        AopUtils.getMostSpecificMethod(method, targetClass));
                return CocoAuditMethodInterceptor.findAnnotation(specificMethod, method, targetClass) != null;
            }
        };
        return new DefaultPointcutAdvisor(pointcut, new CocoAuditMethodInterceptor(auditPublisher, eventFactory));
    }

    /**
     * <p>
     * 注册基于 Advisor 的自动代理创建器。已有的 Spring 或其他 Coco 模块代理创建器会被复用。
     * </p>
     * @return Advisor 自动代理创建器
     */
    @Bean(name = "cocoAuditAutoProxyCreator")
    @ConditionalOnMissingBean(AbstractAdvisorAutoProxyCreator.class)
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DefaultAdvisorAutoProxyCreator cocoAuditAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }
}
