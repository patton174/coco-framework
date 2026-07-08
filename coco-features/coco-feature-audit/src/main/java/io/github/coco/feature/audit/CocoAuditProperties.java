package io.github.coco.feature.audit;

import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 审计功能配置属性。
 * <p>
 * 绑定 {@code coco.audit} 命名空间，控制审计事件基础设施、记录失败策略和访问日志审计适配器。
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
@ConfigurationProperties(prefix = "coco.audit")
public class CocoAuditProperties {

    private boolean enabled = true;

    private CocoAuditFailurePolicy failurePolicy = CocoAuditFailurePolicy.IGNORE;

    @NestedConfigurationProperty
    private AccessLogProperties accessLog = new AccessLogProperties();

    /**
     * <p>
     * 返回是否启用审计基础设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用审计基础设施。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回审计记录失败策略。
     * </p>
     * @return 审计记录失败策略
     */
    public CocoAuditFailurePolicy getFailurePolicy() {
        return this.failurePolicy;
    }

    /**
     * <p>
     * 设置审计记录失败策略。
     * </p>
     * @param failurePolicy 审计记录失败策略
     */
    public void setFailurePolicy(CocoAuditFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy == null ? CocoAuditFailurePolicy.IGNORE : failurePolicy;
    }

    /**
     * <p>
     * 返回访问日志审计适配配置。
     * </p>
     * @return 访问日志审计适配配置
     */
    public AccessLogProperties getAccessLog() {
        return this.accessLog;
    }

    /**
     * <p>
     * 设置访问日志审计适配配置。
     * </p>
     * @param accessLog 访问日志审计适配配置
     */
    public void setAccessLog(AccessLogProperties accessLog) {
        this.accessLog = accessLog == null ? new AccessLogProperties() : accessLog;
    }

    /**
     * Coco 访问日志审计适配配置。
     * <p>
     * 控制是否把 Web 访问日志转换为审计事件。
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
    public static class AccessLogProperties {

        private boolean enabled = true;

        /**
         * <p>
         * 返回是否把访问日志转换为审计事件。
         * </p>
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return this.enabled;
        }

        /**
         * <p>
         * 设置是否把访问日志转换为审计事件。
         * </p>
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
