package io.github.coco.audit;

import io.github.coco.logging.CocoLogLevel;
import io.github.coco.audit.core.CocoAuditFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 审计功能配置属性。
 * <p>
 * 绑定 {@code coco.audit} 命名空间，控制审计事件基础设施、默认日志记录器、记录失败策略和访问日志审计适配器。
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
@ConfigurationProperties(prefix = "coco.audit")
public class CocoAuditProperties {

    private boolean enabled = true;

    private CocoAuditFailurePolicy failurePolicy = CocoAuditFailurePolicy.IGNORE;

    @NestedConfigurationProperty
    private LoggingProperties logging = new LoggingProperties();

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
     * 返回默认审计日志配置。
     * </p>
     * @return 默认审计日志配置
     */
    public LoggingProperties getLogging() {
        return this.logging;
    }

    /**
     * <p>
     * 设置默认审计日志配置。
     * </p>
     * @param logging 默认审计日志配置
     */
    public void setLogging(LoggingProperties logging) {
        this.logging = logging == null ? new LoggingProperties() : logging;
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
     * Coco 默认审计日志配置。
     * <p>
     * 控制默认日志记录器的启用状态、隔离 logger 名称和输出级别。
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
    public static class LoggingProperties {

        /**
         * 默认审计日志 logger 名称。
         */
        public static final String DEFAULT_LOGGER_NAME = "io.github.coco.audit";

        private boolean enabled = true;

        private String loggerName = DEFAULT_LOGGER_NAME;

        private CocoLogLevel level = CocoLogLevel.INFO;

        /**
         * <p>
         * 返回是否启用默认审计日志记录器。
         * </p>
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return this.enabled;
        }

        /**
         * <p>
         * 设置是否启用默认审计日志记录器。
         * </p>
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * <p>
         * 返回审计日志 logger 名称。
         * </p>
         * @return logger 名称
         */
        public String getLoggerName() {
            return this.loggerName;
        }

        /**
         * <p>
         * 设置审计日志 logger 名称。
         * </p>
         * @param loggerName logger 名称
         */
        public void setLoggerName(String loggerName) {
            this.loggerName = loggerName == null || loggerName.isBlank()
                    ? DEFAULT_LOGGER_NAME
                    : loggerName.trim();
        }

        /**
         * <p>
         * 返回审计日志输出级别。
         * </p>
         * @return 输出级别
         */
        public CocoLogLevel getLevel() {
            return this.level;
        }

        /**
         * <p>
         * 设置审计日志输出级别。
         * </p>
         * @param level 输出级别
         */
        public void setLevel(CocoLogLevel level) {
            this.level = level == null ? CocoLogLevel.INFO : level;
        }
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
     *   <li>模块：{@code coco-audit}</li>
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
