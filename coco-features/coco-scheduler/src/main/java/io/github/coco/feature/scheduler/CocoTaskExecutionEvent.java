package io.github.coco.feature.scheduler;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务执行观测事件。
 * @param taskId 任务标识
 * @param executionId 执行标识
 * @param scheduledAt 计划触发时间
 * @param startedAt 实际开始时间
 * @param attempt 当前尝试次数
 */
public record CocoTaskExecutionEvent(String taskId, UUID executionId, Instant scheduledAt, Instant startedAt,
        int attempt) {

    /**
     * 创建执行观测事件。
     */
    public CocoTaskExecutionEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }
}
