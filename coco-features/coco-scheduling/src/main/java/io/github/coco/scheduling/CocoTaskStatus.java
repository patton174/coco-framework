package io.github.coco.scheduling;

import java.time.Duration;
import java.time.Instant;

/**
 * Coco 任务的运行状态快照。
 * <p>
 * 状态不包含任务 {@link Runnable}、注解参数或异常消息，避免通过治理接口暴露业务数据。
 * </p>
 *
 * @param name 稳定任务名称
 * @param scheduleType 触发方式
 * @param overlapPolicy 重叠策略
 * @param enabled 是否启用
 * @param scheduled 是否已向底层调度器提交
 * @param outcome 最近结果
 * @param lastStartedAt 最近开始时间
 * @param lastCompletedAt 最近结束时间
 * @param lastDuration 最近耗时
 * @param traceId 最近执行的 TraceId
 * @since 1.0.0
 */
public record CocoTaskStatus(String name, CocoTaskScheduleType scheduleType, CocoTaskOverlapPolicy overlapPolicy,
        boolean enabled, boolean scheduled, CocoTaskExecutionOutcome outcome, Instant lastStartedAt,
        Instant lastCompletedAt, Duration lastDuration, String traceId) {

    static CocoTaskStatus initial(CocoTaskDefinition definition, CocoTaskScheduleType scheduleType,
            boolean scheduled) {
        return new CocoTaskStatus(definition.getName(), scheduleType, definition.getOverlapPolicy(),
                definition.isEnabled(), scheduled, CocoTaskExecutionOutcome.NONE, null, null, null, null);
    }
}
