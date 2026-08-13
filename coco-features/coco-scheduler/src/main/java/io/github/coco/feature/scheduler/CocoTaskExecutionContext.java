package io.github.coco.feature.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单次任务执行的不可变上下文。
 * @param executionId 执行标识
 * @param scheduledAt 触发计划时间
 * @param startedAt 实际开始时间
 * @param attempt 当前尝试次数，从 1 开始
 * @param clock 执行使用的时钟
 */
public record CocoTaskExecutionContext(UUID executionId, Instant scheduledAt, Instant startedAt, int attempt,
        Clock clock) {

    /**
     * 创建执行上下文。
     */
    public CocoTaskExecutionContext {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }
    }
}
