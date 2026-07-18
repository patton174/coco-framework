package io.github.coco.observability;

/**
 * Coco 可观测事件记录 SPI。
 * <p>
 * 参数故意只包含固定枚举，不允许将租户、用户、密钥、nonce、请求路径或任意属性作为指标标签。
 * </p>
 */
@FunctionalInterface
public interface CocoObservationRecorder {

    /**
     * 记录一个可安全聚合的观察事件。
     * @param kind 事件种类
     * @param outcome 事件结果
     */
    void record(CocoObservationKind kind, CocoObservationOutcome outcome);
}
