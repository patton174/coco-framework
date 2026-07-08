package io.github.coco.feature.audit;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.NoOpCocoAuditRecorder;
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
 * 负责为审计功能模块注册国际化消息资源、审计记录器 SPI 和访问日志审计适配器。
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
     * 创建默认审计记录器。
     * </p>
     * @return 审计记录器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoAuditRecorder cocoAuditRecorder() {
        return new NoOpCocoAuditRecorder();
    }

    /**
     * <p>
     * 创建访问日志审计适配器。
     * </p>
     * @param auditRecorder 审计记录器
     * @return 访问日志记录器
     */
    @Bean
    @ConditionalOnBean(CocoAuditRecorder.class)
    @ConditionalOnProperty(prefix = "coco.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "coco.audit.access-log", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoAccessLogRecorder cocoAccessLogAuditRecorder(CocoAuditRecorder auditRecorder) {
        return new CocoAccessLogAuditRecorder(auditRecorder);
    }
}
