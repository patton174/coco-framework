package io.github.coco.feature.audit.core;

/**
 * Coco 审计事件发布器。
 * <p>
 * 发布器负责接收框架内部产生的审计事件，并将事件分发给一个或多个 {@link CocoAuditRecorder}。
 * 事件来源只依赖发布器，不直接感知具体落地端，从而保持 Web、Security、Tenant 等模块与存储实现解耦。
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
@FunctionalInterface
public interface CocoAuditPublisher {

    /**
     * <p>
     * 发布审计事件。
     * </p>
     * @param event 审计事件
     */
    void publish(CocoAuditEvent event);
}
