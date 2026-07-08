package io.github.coco.feature.audit.core;

/**
 * Coco 审计记录失败策略。
 * <p>
 * 定义默认审计发布器遇到单个记录器失败时的处理方式。
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
public enum CocoAuditFailurePolicy {

    /**
     * 忽略单个记录器失败，继续分发给后续记录器。
     */
    IGNORE,

    /**
     * 立即抛出记录器失败异常。
     */
    THROW
}
