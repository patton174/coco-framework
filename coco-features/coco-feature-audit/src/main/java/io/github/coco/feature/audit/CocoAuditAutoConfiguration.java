package io.github.coco.feature.audit;

import java.util.Collection;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.common.logging.core.CocoLogHandle;
import io.github.coco.common.logging.core.CocoLogHandleRegistrar;
import io.github.coco.common.logging.core.CocoLogManager;
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
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = CocoCommonLoggingAutoConfiguration.class)
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
    public CocoAuditPublisher cocoAuditPublisher(Collection<CocoAuditRecorder> auditRecorders,
            CocoAuditErrorHandler errorHandler) {
        return new CompositeCocoAuditPublisher(auditRecorders, errorHandler);
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
}
