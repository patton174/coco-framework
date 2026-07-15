package io.github.coco.observability;

/**
 * 异步日志溢出观察 SPI。
 * <p>
 * 该 SPI 只报告已确认丢弃的记录数，不暴露日志正文、异常、句柄名或其他可能高基数的信息。
 * </p>
 */
@FunctionalInterface
public interface CocoLogOverflowObservation {

    /**
     * 记录一个已确认的异步日志丢弃事件。
     */
    void recordDrop();
}
