package io.github.coco.scheduling;

import java.time.Duration;
import java.time.Instant;

/**
 * Coco 任务执行事件。
 * <p>
 * 事件仅携带任务治理所需的元数据，不携带业务方法参数和异常文本。
 * </p>
 *
 * @param taskName 稳定任务名称
 * @param outcome 执行阶段或结果
 * @param occurredAt 事件发生时间
 * @param duration 执行耗时；开始和跳过事件为零
 * @param traceId 本次执行 TraceId
 * @param failureType 失败异常类型；非失败事件为空
 * @since 1.0.0
 */
public record CocoTaskExecutionEvent(String taskName, CocoTaskExecutionOutcome outcome, Instant occurredAt,
        Duration duration, String traceId, String failureType) {
}
