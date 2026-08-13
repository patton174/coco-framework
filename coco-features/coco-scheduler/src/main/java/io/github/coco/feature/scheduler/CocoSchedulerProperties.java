package io.github.coco.feature.scheduler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 任务调度配置属性。
 */
@ConfigurationProperties("coco.scheduler")
public class CocoSchedulerProperties {

    private boolean enabled = true;
    private int poolSize = 1;
    private Duration shutdownAwait = Duration.ofSeconds(30);
    private Map<String, TaskProperties> tasks = new LinkedHashMap<>();

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPoolSize() { return this.poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    public Duration getShutdownAwait() { return this.shutdownAwait; }
    public void setShutdownAwait(Duration shutdownAwait) { this.shutdownAwait = shutdownAwait; }
    public Map<String, TaskProperties> getTasks() { return this.tasks; }
    public void setTasks(Map<String, TaskProperties> tasks) { this.tasks = tasks == null ? new LinkedHashMap<>() : tasks; }

    /** 单个任务配置。 */
    public static class TaskProperties {
        private String id;
        private ScheduleType type;
        private Duration initialDelay = Duration.ZERO;
        private Duration interval;
        private String cron;
        private String zone;
        private OverlapPolicy overlapPolicy = OverlapPolicy.SKIP;
        private RetryProperties retry = new RetryProperties();
        private Duration warningThreshold;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public ScheduleType getType() { return type; }
        public void setType(ScheduleType type) { this.type = type; }
        public Duration getInitialDelay() { return initialDelay; }
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public OverlapPolicy getOverlapPolicy() { return overlapPolicy; }
        public void setOverlapPolicy(OverlapPolicy overlapPolicy) { this.overlapPolicy = overlapPolicy; }
        public RetryProperties getRetry() { return retry; }
        public void setRetry(RetryProperties retry) { this.retry = retry; }
        public Duration getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(Duration warningThreshold) { this.warningThreshold = warningThreshold; }
    }

    /** 失败重试配置。 */
    public static class RetryProperties {
        private int maxAttempts = 1;
        private Duration backoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofMinutes(1);
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getBackoff() { return backoff; }
        public void setBackoff(Duration backoff) { this.backoff = backoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    }

    /** 调度类型。 */
    public enum ScheduleType { FIXED_DELAY, FIXED_RATE, CRON }
    /** 重叠处理策略。 */
    public enum OverlapPolicy { SKIP, QUEUE }
}
