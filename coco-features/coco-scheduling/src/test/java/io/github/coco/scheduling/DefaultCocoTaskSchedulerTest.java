package io.github.coco.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronTrigger;

class DefaultCocoTaskSchedulerTest {

    @AfterEach
    void clearTraceContext() {
        CocoTraceContext.clear();
    }

    @Test
    void schedulesCronAndFixedPeriodsAndKeepsDisabledDefinitionVisible() {
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        DefaultCocoTaskScheduler scheduler = scheduler(springScheduler, new InMemoryCocoTaskExecutionGuard(), List.of());

        scheduler.register(CocoTaskDefinition.builder("cron", () -> { }).cron("0 * * * * *").build());
        scheduler.register(CocoTaskDefinition.builder("delay", () -> { }).fixedDelay(Duration.ofSeconds(2))
                .initialDelay(Duration.ofSeconds(1)).build());
        scheduler.register(CocoTaskDefinition.builder("rate", () -> { }).fixedRate(Duration.ofSeconds(3)).build());
        scheduler.register(CocoTaskDefinition.builder("disabled", () -> { }).fixedRate(Duration.ofSeconds(3))
                .enabled(false).build());

        assertThat(springScheduler.entries()).hasSize(3);
        assertThat(springScheduler.entries().get(0).trigger()).isInstanceOf(CronTrigger.class);
        assertThat(springScheduler.entries().get(1).type()).isEqualTo("fixed-delay");
        assertThat(springScheduler.entries().get(1).startTime()).isEqualTo(springScheduler.getClock().instant().plusSeconds(1));
        assertThat(springScheduler.entries().get(2).type()).isEqualTo("fixed-rate");
        assertThat(scheduler.status("disabled")).hasValueSatisfying(status -> assertThat(status.scheduled()).isFalse());
    }

    @Test
    void rejectsMutuallyExclusiveAndInvalidPeriods() {
        DefaultCocoTaskScheduler scheduler = scheduler(new ManualTaskScheduler(), new InMemoryCocoTaskExecutionGuard(), List.of());

        assertThatThrownBy(() -> scheduler.register(CocoTaskDefinition.builder("both", () -> { })
                .cron("0 * * * * *").fixedRate(Duration.ofSeconds(1)).build()))
                .isInstanceOf(CocoSchedulingException.class)
                .hasFieldOrPropertyWithValue("code", "coco.scheduling.schedule-exactly-one");
        assertThatThrownBy(() -> scheduler.register(CocoTaskDefinition.builder("zero", () -> { })
                .fixedDelay(Duration.ZERO).build()))
                .isInstanceOf(CocoSchedulingException.class)
                .hasFieldOrPropertyWithValue("code", "coco.scheduling.schedule-duration-positive");
    }

    @Test
    void replacesAndCancelsTaskAtomically() {
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        DefaultCocoTaskScheduler scheduler = scheduler(springScheduler, new InMemoryCocoTaskExecutionGuard(), List.of());
        AtomicInteger executions = new AtomicInteger();

        scheduler.register(CocoTaskDefinition.builder("refresh", () -> executions.addAndGet(1))
                .fixedRate(Duration.ofSeconds(1)).build());
        ManualTaskScheduler.Entry first = springScheduler.latest();
        scheduler.replace(CocoTaskDefinition.builder("refresh", () -> executions.addAndGet(10))
                .fixedRate(Duration.ofSeconds(1)).build());
        ManualTaskScheduler.Entry replacement = springScheduler.latest();

        assertThat(first.future().cancelled()).isTrue();
        replacement.run();
        assertThat(executions).hasValue(10);
        assertThat(scheduler.cancel("refresh")).isTrue();
        assertThat(replacement.future().cancelled()).isTrue();
        assertThat(scheduler.status("refresh")).isEmpty();
    }

    @Test
    void appliesSkipAndAllowPoliciesWithoutLeakingTaskStateToObserver() {
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        CocoTaskExecutionGuard rejectingGuard = new CocoTaskExecutionGuard() {
            @Override
            public boolean tryAcquire(String taskName) {
                return false;
            }

            @Override
            public void release(String taskName) {
            }
        };
        List<CocoTaskExecutionEvent> events = new ArrayList<>();
        DefaultCocoTaskScheduler scheduler = scheduler(springScheduler, rejectingGuard, List.of(events::add));
        AtomicInteger skippedTask = new AtomicInteger();
        AtomicInteger allowedTask = new AtomicInteger();

        scheduler.register(CocoTaskDefinition.builder("skip", skippedTask::incrementAndGet)
                .fixedRate(Duration.ofSeconds(1)).build());
        springScheduler.latest().run();
        scheduler.register(CocoTaskDefinition.builder("allow", allowedTask::incrementAndGet)
                .fixedRate(Duration.ofSeconds(1)).overlapPolicy(CocoTaskOverlapPolicy.ALLOW).build());
        springScheduler.latest().run();

        assertThat(skippedTask).hasValue(0);
        assertThat(allowedTask).hasValue(1);
        assertThat(events).extracting(CocoTaskExecutionEvent::outcome)
                .containsExactly(CocoTaskExecutionOutcome.SKIPPED, CocoTaskExecutionOutcome.STARTED,
                        CocoTaskExecutionOutcome.SUCCEEDED);
        assertThat(events).allSatisfy(event -> assertThat(event.failureType()).isNull());
    }

    @Test
    void restoresTraceNotifiesObserverAndContinuesAfterTaskFailure() {
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        List<CocoTaskExecutionEvent> events = new ArrayList<>();
        DefaultCocoTaskScheduler scheduler = scheduler(springScheduler, new InMemoryCocoTaskExecutionGuard(),
                List.of(events::add));
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger executions = new AtomicInteger();
        AtomicBoolean traceRestored = new AtomicBoolean();
        CocoTraceContext.setTraceId("trace-registration");

        scheduler.register(CocoTaskDefinition.builder("trace", () -> {
            traceRestored.set(CocoTraceContext.currentTraceId().filter("trace-registration"::equals).isPresent());
            executions.incrementAndGet();
            springScheduler.advance(Duration.ofSeconds(2));
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("business-secret-must-not-publish");
            }
        }).fixedRate(Duration.ofSeconds(1)).build());
        CocoTraceContext.clear();

        springScheduler.latest().run();
        springScheduler.latest().run();

        assertThat(executions).hasValue(2);
        assertThat(traceRestored).isTrue();
        assertThat(CocoTraceContext.currentTraceId()).isEmpty();
        assertThat(events).extracting(CocoTaskExecutionEvent::outcome).containsExactly(
                CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.FAILED,
                CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.SUCCEEDED);
        assertThat(events.get(1).failureType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(events.get(1).duration()).isEqualTo(Duration.ofSeconds(2));
        assertThat(events.get(1).toString()).doesNotContain("business-secret-must-not-publish");
    }

    @Test
    void closesByCancellingFuturesWithConfiguredInterruption() {
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        CocoSchedulingProperties properties = new CocoSchedulingProperties();
        properties.getShutdown().setInterrupt(true);
        DefaultCocoTaskScheduler scheduler = scheduler(springScheduler, new InMemoryCocoTaskExecutionGuard(), List.of(),
                properties);
        scheduler.register(CocoTaskDefinition.builder("close", () -> { }).fixedRate(Duration.ofSeconds(1)).build());
        ManualTaskScheduler.Entry entry = springScheduler.latest();

        scheduler.close();

        assertThat(entry.future().cancelled()).isTrue();
        assertThat(entry.future().interrupt()).isTrue();
        assertThatThrownBy(() -> scheduler.register(CocoTaskDefinition.builder("after-close", () -> { })
                .fixedRate(Duration.ofSeconds(1)).build())).isInstanceOf(CocoSchedulingException.class);
    }

    @Test
    void providesChineseSchedulingValidationMessages() {
        ResourceBundle messages = ResourceBundle.getBundle("coco-scheduling-messages", Locale.SIMPLIFIED_CHINESE);

        assertThat(messages.getString("coco.scheduling.schedule-exactly-one"))
                .isEqualTo("任务“{0}”必须且只能配置 cron、固定延迟或固定频率中的一种。");
    }

    private DefaultCocoTaskScheduler scheduler(ManualTaskScheduler springScheduler, CocoTaskExecutionGuard guard,
            List<CocoTaskExecutionObserver> observers) {
        return scheduler(springScheduler, guard, observers, new CocoSchedulingProperties());
    }

    private DefaultCocoTaskScheduler scheduler(ManualTaskScheduler springScheduler, CocoTaskExecutionGuard guard,
            List<CocoTaskExecutionObserver> observers, CocoSchedulingProperties properties) {
        CocoTaskDefinitionValidator validator = new CocoTaskDefinitionValidator(new CocoSchedulingMessageResolver(null));
        return new DefaultCocoTaskScheduler(springScheduler, guard, observers, validator, properties.getShutdown());
    }
}
