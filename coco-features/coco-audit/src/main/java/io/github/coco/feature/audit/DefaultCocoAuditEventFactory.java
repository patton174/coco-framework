package io.github.coco.feature.audit;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.audit.core.CocoAuditEvent;

/**
 * 声明式审计事件的默认工厂。
 * <p>
 * 只使用注解中的静态声明、调用成功状态、TraceId、异常类名和耗时，不读取业务输入或输出。
 * </p>
 */
final class DefaultCocoAuditEventFactory implements CocoAuditEventFactory {

    @Override
    public CocoAuditEvent create(CocoAuditInvocation invocation) {
        CocoAudited audited = invocation.annotation();
        CocoAuditEvent.Builder builder = CocoAuditEvent.builder(CocoAuditInvocation.requireType(audited))
                .action(CocoAuditInvocation.normalize(audited.action()))
                .resourceType(CocoAuditInvocation.normalize(audited.resourceType()))
                .resourceId(CocoAuditInvocation.normalize(audited.resourceId()))
                .success(invocation.success())
                .occurredAt(invocation.completedAt())
                .attribute("durationMillis", invocation.durationMillis());
        CocoTraceContext.currentTraceId().ifPresent(builder::traceId);
        if (invocation.exceptionType() != null) {
            builder.attribute("exceptionType", invocation.exceptionType());
        }
        return builder.build();
    }
}
