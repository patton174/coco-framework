package io.github.coco.scheduling;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Coco 动态任务定义。
 * <p>
 * 一个定义只能声明 Cron、固定延迟或固定频率中的一种触发方式。该约束由调度器在注册时严格校验。
 * </p>
 *
 * @since 1.0.0
 */
public final class CocoTaskDefinition {

    private final String name;
    private final Runnable task;
    private final String cron;
    private final Duration fixedDelay;
    private final Duration fixedRate;
    private final ZoneId zone;
    private final Duration initialDelay;
    private final CocoTaskOverlapPolicy overlapPolicy;
    private final boolean enabled;

    private CocoTaskDefinition(Builder builder) {
        this.name = normalize(builder.name);
        this.task = builder.task;
        this.cron = normalize(builder.cron);
        this.fixedDelay = builder.fixedDelay;
        this.fixedRate = builder.fixedRate;
        this.zone = builder.zone == null ? ZoneId.systemDefault() : builder.zone;
        this.initialDelay = builder.initialDelay == null ? Duration.ZERO : builder.initialDelay;
        this.overlapPolicy = builder.overlapPolicy == null ? CocoTaskOverlapPolicy.SKIP : builder.overlapPolicy;
        this.enabled = builder.enabled;
    }

    /**
     * 创建任务定义构建器。
     *
     * @param name 稳定任务名称
     * @param task 任务逻辑
     * @return 构建器
     */
    public static Builder builder(String name, Runnable task) {
        return new Builder(name, task);
    }

    public String getName() {
        return this.name;
    }

    public Runnable getTask() {
        return this.task;
    }

    public String getCron() {
        return this.cron;
    }

    public Duration getFixedDelay() {
        return this.fixedDelay;
    }

    public Duration getFixedRate() {
        return this.fixedRate;
    }

    public ZoneId getZone() {
        return this.zone;
    }

    public Duration getInitialDelay() {
        return this.initialDelay;
    }

    public CocoTaskOverlapPolicy getOverlapPolicy() {
        return this.overlapPolicy;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Coco 任务定义构建器。
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private final String name;
        private final Runnable task;
        private String cron;
        private Duration fixedDelay;
        private Duration fixedRate;
        private ZoneId zone;
        private Duration initialDelay = Duration.ZERO;
        private CocoTaskOverlapPolicy overlapPolicy = CocoTaskOverlapPolicy.SKIP;
        private boolean enabled = true;

        private Builder(String name, Runnable task) {
            this.name = name;
            this.task = task;
        }

        public Builder cron(String cron) {
            this.cron = cron;
            return this;
        }

        public Builder fixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
            return this;
        }

        public Builder fixedRate(Duration fixedRate) {
            this.fixedRate = fixedRate;
            return this;
        }

        public Builder zone(ZoneId zone) {
            this.zone = zone;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder overlapPolicy(CocoTaskOverlapPolicy overlapPolicy) {
            this.overlapPolicy = overlapPolicy;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public CocoTaskDefinition build() {
            return new CocoTaskDefinition(this);
        }
    }
}
