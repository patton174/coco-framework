package io.github.coco.scheduling;

import java.time.Duration;

import org.springframework.scheduling.support.CronExpression;

final class CocoTaskDefinitionValidator {

    private final CocoSchedulingMessageResolver messages;

    CocoTaskDefinitionValidator(CocoSchedulingMessageResolver messages) {
        this.messages = messages;
    }

    CocoTaskScheduleType validate(CocoTaskDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw this.messages.error(CocoSchedulingMessage.TASK_NAME_REQUIRED);
        }
        if (definition.getTask() == null) {
            throw this.messages.error(CocoSchedulingMessage.TASK_REQUIRED, definition.getName());
        }
        int scheduleCount = (definition.getCron() == null ? 0 : 1)
                + (definition.getFixedDelay() == null ? 0 : 1)
                + (definition.getFixedRate() == null ? 0 : 1);
        if (scheduleCount != 1) {
            throw this.messages.error(CocoSchedulingMessage.SCHEDULE_EXACTLY_ONE, definition.getName());
        }
        if (definition.getInitialDelay().isNegative()) {
            throw this.messages.error(CocoSchedulingMessage.INITIAL_DELAY_NEGATIVE, definition.getName());
        }
        if (definition.getCron() != null) {
            try {
                CronExpression.parse(definition.getCron());
            }
            catch (IllegalArgumentException exception) {
                throw this.messages.error(CocoSchedulingMessage.CRON_INVALID, definition.getName());
            }
            return CocoTaskScheduleType.CRON;
        }
        Duration interval = definition.getFixedDelay() == null ? definition.getFixedRate() : definition.getFixedDelay();
        if (interval.isNegative() || interval.isZero()) {
            throw this.messages.error(CocoSchedulingMessage.SCHEDULE_DURATION_POSITIVE, definition.getName());
        }
        return definition.getFixedDelay() == null ? CocoTaskScheduleType.FIXED_RATE : CocoTaskScheduleType.FIXED_DELAY;
    }

    void validateProperties(CocoSchedulingProperties properties) {
        if (properties.getPoolSize() < 1) {
            throw this.messages.error(CocoSchedulingMessage.POOL_SIZE_INVALID);
        }
        Duration awaitTermination = properties.getShutdown().getAwaitTermination();
        if (awaitTermination == null || awaitTermination.isNegative()) {
            throw this.messages.error(CocoSchedulingMessage.AWAIT_TERMINATION_NEGATIVE);
        }
    }

    CocoSchedulingException error(CocoSchedulingMessage message, Object... args) {
        return this.messages.error(message, args);
    }
}
