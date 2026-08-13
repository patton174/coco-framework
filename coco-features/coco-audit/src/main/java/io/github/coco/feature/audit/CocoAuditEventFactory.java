package io.github.coco.feature.audit;

import io.github.coco.feature.audit.core.CocoAuditEvent;

/**
 * 将声明式审计调用转换为审计事件的替换点。
 * <p>
 * 业务应用可从自身受控上下文补充操作者、租户或动态资源标识，但实现不应从 invocation 提取参数、返回值或异常内容。
 * </p>
 * @author patton174
 * @since 2.0.3
 */
@FunctionalInterface
public interface CocoAuditEventFactory {

    /**
     * 创建待发布的审计事件。
     * @param invocation 安全的调用描述
     * @return 审计事件
     */
    CocoAuditEvent create(CocoAuditInvocation invocation);
}
