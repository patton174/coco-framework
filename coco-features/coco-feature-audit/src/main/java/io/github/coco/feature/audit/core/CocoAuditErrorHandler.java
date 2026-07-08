package io.github.coco.feature.audit.core;

/**
 * Coco 审计记录失败处理器。
 * <p>
 * 当某个 {@link CocoAuditRecorder} 写入失败时，发布器通过该接口决定忽略失败、抛出失败或执行业务侧自定义补偿逻辑。
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
@FunctionalInterface
public interface CocoAuditErrorHandler {

    /**
     * <p>
     * 处理审计记录失败。
     * </p>
     * @param event 审计事件
     * @param recorder 失败的审计记录器
     * @param failure 记录失败异常
     */
    void handle(CocoAuditEvent event, CocoAuditRecorder recorder, RuntimeException failure);
}
