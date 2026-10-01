package io.github.coco.feature.audit.core;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 组合式 Coco 审计事件发布器。
 * <p>
 * 将单个审计事件按顺序分发给所有可用的 {@link CocoAuditRecorder}，并把单个记录器失败交给
 * {@link CocoAuditErrorHandler} 处理。
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
public final class CompositeCocoAuditPublisher implements CocoAuditPublisher {

    private final List<CocoAuditRecorder> auditRecorders;

    private final CocoAuditErrorHandler errorHandler;

    /**
     * <p>
     * 创建组合式审计事件发布器。
     * </p>
     * @param auditRecorders 审计记录器集合
     * @param errorHandler 审计记录失败处理器
     */
    public CompositeCocoAuditPublisher(Collection<CocoAuditRecorder> auditRecorders,
            CocoAuditErrorHandler errorHandler) {
        this.auditRecorders = auditRecorders == null ? List.of() : auditRecorders.stream()
                .filter(Objects::nonNull)
                .toList();
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(CocoAuditEvent event) {
        CocoAuditEvent checkedEvent = Objects.requireNonNull(event, "event must not be null");
        for (CocoAuditRecorder recorder : this.auditRecorders) {
            try {
                recorder.record(checkedEvent);
            }
            catch (RuntimeException ex) {
                this.errorHandler.handle(checkedEvent, recorder, ex);
            }
        }
    }
}
