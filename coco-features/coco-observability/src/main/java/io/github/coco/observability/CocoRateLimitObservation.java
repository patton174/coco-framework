package io.github.coco.observability;

/**
 * 限流决策观察 SPI。
 * <p>
 * Coco 基线没有统一限流事件源。限流实现可调用本 SPI 发布允许或拒绝结果，且不得传递主体、租户、用户或路由。
 * </p>
 */
@FunctionalInterface
public interface CocoRateLimitObservation {

    /**
     * 记录限流决策。
     * @param outcome 只能使用 {@link CocoObservationOutcome#ALLOWED} 或
     * {@link CocoObservationOutcome#REJECTED}
     */
    void record(CocoObservationOutcome outcome);
}
