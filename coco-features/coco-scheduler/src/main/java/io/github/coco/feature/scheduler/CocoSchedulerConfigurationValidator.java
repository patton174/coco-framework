package io.github.coco.feature.scheduler;

import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.scheduling.support.CronExpression;

final class CocoSchedulerConfigurationValidator {
    private CocoSchedulerConfigurationValidator() { }
    static void validate(CocoSchedulerProperties properties) {
        if (properties.getPoolSize() < 1) throw new IllegalStateException("coco.scheduler.pool-size must be greater than zero");
        positive(properties.getShutdownAwait(), "coco.scheduler.shutdown-await");
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, CocoSchedulerProperties.TaskProperties> entry : properties.getTasks().entrySet()) {
            String key = entry.getKey(); CocoSchedulerProperties.TaskProperties task = entry.getValue();
            if (task == null) throw new IllegalStateException("coco.scheduler.tasks." + key + " must not be null");
            String id = blank(task.getId()) ? key : task.getId().trim();
            if (blank(id) || !ids.add(id)) throw new IllegalStateException("Coco scheduler task id must be nonblank and unique: " + id);
            if (task.getType() == null) throw new IllegalStateException("Task " + id + " must declare type");
            if (task.getInitialDelay() == null || task.getInitialDelay().isNegative()) throw new IllegalStateException("Task " + id + " initial-delay must not be negative");
            if (task.getOverlapPolicy() == null) throw new IllegalStateException("Task " + id + " overlap-policy must be set");
            if (task.getWarningThreshold() != null) positive(task.getWarningThreshold(), "Task " + id + " warning-threshold");
            CocoSchedulerProperties.RetryProperties retry = task.getRetry();
            if (retry == null || retry.getMaxAttempts() < 1) throw new IllegalStateException("Task " + id + " retry.max-attempts must be greater than zero");
            positive(retry.getBackoff(), "Task " + id + " retry.backoff"); positive(retry.getMaxBackoff(), "Task " + id + " retry.max-backoff");
            if (retry.getBackoff().compareTo(retry.getMaxBackoff()) > 0) throw new IllegalStateException("Task " + id + " retry.backoff must not exceed retry.max-backoff");
            if (task.getType() == CocoSchedulerProperties.ScheduleType.CRON) {
                if (!blank(task.getInterval())) throw new IllegalStateException("Cron task " + id + " must not declare interval");
                if (blank(task.getCron()) || !CronExpression.isValidExpression(task.getCron())) throw new IllegalStateException("Cron task " + id + " must declare a valid cron expression");
                if (!blank(task.getZone())) { try { ZoneId.of(task.getZone()); } catch (RuntimeException ex) { throw new IllegalStateException("Cron task " + id + " has invalid zone", ex); } }
            } else {
                if (!blank(task.getCron()) || !blank(task.getZone())) throw new IllegalStateException("Interval task " + id + " must not declare cron or zone");
                positive(task.getInterval(), "Task " + id + " interval");
            }
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean blank(Duration value) { return value == null; }
    private static void positive(Duration value, String name) { if (value == null || value.isZero() || value.isNegative()) throw new IllegalStateException(name + " must be greater than zero"); }
}
