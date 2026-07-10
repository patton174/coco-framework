package io.github.coco.feature.audit.core;

import java.util.Objects;

import io.github.coco.common.logging.core.CocoLogLevel;
import io.github.coco.common.logging.core.CocoLogManager;
import io.github.coco.feature.audit.CocoAuditProperties;

/**
 * 基于 Coco 日志管理器的审计记录器。
 * <p>
 * 将格式化后的审计事件写入独立的 {@value #LOG_HANDLE} 日志句柄，logger 和级别由
 * {@code coco.audit.logging} 配置控制。
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
public final class LoggingCocoAuditRecorder implements CocoAuditRecorder {

    /**
     * 审计日志句柄名称。
     */
    public static final String LOG_HANDLE = "audit";

    private final CocoAuditProperties.LoggingProperties properties;

    private final CocoAuditFormatter formatter;

    private final CocoLogManager logManager;

    /**
     * <p>
     * 创建审计日志记录器。
     * </p>
     * @param properties 审计日志配置
     * @param formatter 审计事件格式化器
     * @param logManager Coco 日志管理器
     */
    public LoggingCocoAuditRecorder(CocoAuditProperties.LoggingProperties properties,
            CocoAuditFormatter formatter, CocoLogManager logManager) {
        this.properties = properties == null ? new CocoAuditProperties.LoggingProperties() : properties;
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
        this.logManager = Objects.requireNonNull(logManager, "logManager must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAuditEvent event) {
        CocoAuditEvent checkedEvent = Objects.requireNonNull(event, "event must not be null");
        CocoLogLevel level = this.properties.getLevel();
        if (!this.properties.isEnabled() || !level.enabled()) {
            return;
        }
        String message = this.formatter.format(checkedEvent);
        if (message == null || message.isBlank()) {
            return;
        }
        this.logManager.log(LOG_HANDLE, level, message, null);
    }
}
