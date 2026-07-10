package io.github.coco.sample.full.interfaces.rest;

import java.time.Instant;
import java.util.Map;

import io.github.coco.feature.audit.core.CocoAuditEvent;

/**
 * 示例审计事件响应。
 *
 * @param type 事件类型
 * @param action 动作
 * @param resourceType 资源类型
 * @param traceId TraceId
 * @param actor 操作者
 * @param tenantId 租户标识
 * @param success 是否成功
 * @param occurredAt 发生时间
 * @param attributes 扩展属性
 * @author patton174
 * @since 1.0.0
 */
public record SampleAuditResponse(
        String type,
        String action,
        String resourceType,
        String traceId,
        String actor,
        String tenantId,
        boolean success,
        Instant occurredAt,
        Map<String, Object> attributes) {

    public static SampleAuditResponse from(CocoAuditEvent event) {
        return new SampleAuditResponse(
                event.type(),
                event.action().orElse(null),
                event.resourceType().orElse(null),
                event.traceId().orElse(null),
                event.actor().orElse(null),
                event.tenantId().orElse(null),
                event.success(),
                event.occurredAt(),
                event.attributes());
    }
}
