package io.github.coco.audit.core;

import java.util.Objects;

/**
 * 基于配置策略的 Coco 审计记录失败处理器。
 * <p>
 * 默认忽略单个记录器失败，业务侧可以通过 {@code coco.audit.failure-policy=throw} 切换为失败即抛出。
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
public final class PolicyCocoAuditErrorHandler implements CocoAuditErrorHandler {

    private final CocoAuditFailurePolicy failurePolicy;

    /**
     * <p>
     * 创建审计记录失败处理器。
     * </p>
     * @param failurePolicy 审计记录失败策略
     */
    public PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy == null ? CocoAuditFailurePolicy.IGNORE : failurePolicy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(CocoAuditEvent event, CocoAuditRecorder recorder, RuntimeException failure) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(recorder, "recorder must not be null");
        RuntimeException checkedFailure = Objects.requireNonNull(failure, "failure must not be null");
        if (this.failurePolicy == CocoAuditFailurePolicy.THROW) {
            throw checkedFailure;
        }
    }
}
