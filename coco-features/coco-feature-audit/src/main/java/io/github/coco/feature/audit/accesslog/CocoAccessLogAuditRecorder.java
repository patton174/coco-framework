package io.github.coco.feature.audit.accesslog;

import java.time.Instant;
import java.util.Objects;

import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;

/**
 * Coco 访问日志审计适配器。
 * <p>
 * 将 Web 模块发布的访问日志事件转换为审计事件。该适配器只负责语义转换，不负责审计事件的最终存储。
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
public final class CocoAccessLogAuditRecorder implements CocoAccessLogRecorder {

    /**
     * 访问日志审计事件类型。
     */
    public static final String EVENT_TYPE = "access-log";

    private static final String RESOURCE_TYPE = "http-request";

    private final CocoAuditPublisher auditPublisher;

    /**
     * <p>
     * 创建访问日志审计适配器。
     * </p>
     * @param auditRecorder 审计记录器
     */
    public CocoAccessLogAuditRecorder(CocoAuditRecorder auditRecorder) {
        this(adapt(auditRecorder));
    }

    /**
     * <p>
     * 创建访问日志审计适配器。
     * </p>
     * @param auditPublisher 审计事件发布器
     */
    public CocoAccessLogAuditRecorder(CocoAuditPublisher auditPublisher) {
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAccessLog accessLog) {
        CocoAccessLog checkedAccessLog = Objects.requireNonNull(accessLog, "accessLog must not be null");
        CocoAuditEvent.Builder builder = CocoAuditEvent.builder(EVENT_TYPE)
                .traceId(checkedAccessLog.traceId())
                .action(checkedAccessLog.method().orElse(null))
                .resourceType(RESOURCE_TYPE)
                .resourceId(checkedAccessLog.path().orElse(null))
                .success(checkedAccessLog.success())
                .occurredAt(Instant.now())
                .attribute("status", checkedAccessLog.status())
                .attribute("durationMillis", checkedAccessLog.durationMillis());
        checkedAccessLog.clientIp().ifPresent(clientIp -> builder.attribute("clientIp", clientIp));
        checkedAccessLog.clientIpSource().ifPresent(source -> builder.attribute("clientIpSource", source));
        checkedAccessLog.userAgent().ifPresent(userAgent -> builder.attribute("userAgent", userAgent));
        checkedAccessLog.contentType().ifPresent(contentType -> builder.attribute("contentType", contentType));
        checkedAccessLog.exceptionType().ifPresent(exceptionType -> builder.attribute("exceptionType", exceptionType));
        checkedAccessLog.browserFingerprint().ifPresent(fingerprint -> builder.attribute("browserFingerprint", fingerprint));
        this.auditPublisher.publish(builder.build());
    }

    private static CocoAuditPublisher adapt(CocoAuditRecorder auditRecorder) {
        CocoAuditRecorder checkedAuditRecorder = Objects.requireNonNull(auditRecorder,
                "auditRecorder must not be null");
        return checkedAuditRecorder::record;
    }
}
