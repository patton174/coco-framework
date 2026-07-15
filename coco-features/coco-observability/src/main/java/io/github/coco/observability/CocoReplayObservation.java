package io.github.coco.observability;

/**
 * 防重放结果观察 SPI。
 * <p>
 * 基线防重放存储没有结果发布 hook。实现防重放存储或过滤器的应用可在完成 reserve 后调用本 SPI，
 * 且不得传递 replay key、nonce 或请求路径。
 * </p>
 */
@FunctionalInterface
public interface CocoReplayObservation {

    /**
     * 记录防重放结果。
     * @param outcome 固定结果枚举
     */
    void record(CocoObservationOutcome outcome);
}
