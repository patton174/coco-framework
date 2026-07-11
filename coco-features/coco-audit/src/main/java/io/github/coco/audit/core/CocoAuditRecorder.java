package io.github.coco.audit.core;

/**
 * Coco 审计记录器。
 * <p>
 * 审计模块只向该接口提交结构化审计事件，具体写入日志、数据库、消息队列或外部审计系统由实现方决定。
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
public interface CocoAuditRecorder {

    /**
     * <p>
     * 记录审计事件。
     * </p>
     * @param event 审计事件
     */
    void record(CocoAuditEvent event);
}
