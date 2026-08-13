package io.github.coco.feature.scheduler;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认 SLF4J 任务执行日志监听器。 */
final class Slf4jCocoTaskExecutionListener implements CocoTaskExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("io.github.coco.scheduler");
    @Override public void onStarted(CocoTaskExecutionEvent event) { LOGGER.debug("Coco task started: taskId={}, executionId={}, attempt={}", event.taskId(), event.executionId(), event.attempt()); }
    @Override public void onSucceeded(CocoTaskExecutionEvent event, Duration duration) { LOGGER.info("Coco task succeeded: taskId={}, executionId={}, attempt={}, duration={}", event.taskId(), event.executionId(), event.attempt(), duration); }
    @Override public void onFailed(CocoTaskExecutionEvent event, Throwable failure) { LOGGER.warn("Coco task failed: taskId={}, executionId={}, attempt={}", event.taskId(), event.executionId(), event.attempt(), failure); }
    @Override public void onRetryScheduled(CocoTaskExecutionEvent event, Duration delay, Throwable failure) { LOGGER.info("Coco task retry scheduled: taskId={}, executionId={}, attempt={}, delay={}", event.taskId(), event.executionId(), event.attempt(), delay); }
    @Override public void onSkipped(String taskId) { LOGGER.warn("Coco task trigger skipped due to overlap: taskId={}", taskId); }
}
