package io.github.coco.feature.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class CocoTaskSchedulerLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);

    @Test void registersFixedDelayFixedRateAndCron() {
        ManualTaskScheduler scheduler = new ManualTaskScheduler();
        CocoSchedulerProperties properties = properties("delay", CocoSchedulerProperties.ScheduleType.FIXED_DELAY);
        properties.getTasks().put("rate", task("rate", CocoSchedulerProperties.ScheduleType.FIXED_RATE));
        CocoSchedulerProperties.TaskProperties cron = task("cron", CocoSchedulerProperties.ScheduleType.CRON); cron.setInterval(null); cron.setCron("0 * * * * *"); properties.getTasks().put("cron", cron);
        CocoTaskSchedulerLifecycle lifecycle = lifecycle(scheduler, properties, List.of(taskBean("delay", c -> { }), taskBean("rate", c -> { }), taskBean("cron", c -> { })));
        lifecycle.start();
        assertThat(scheduler.fixedDelay).hasSize(1); assertThat(scheduler.fixedRate).hasSize(1); assertThat(scheduler.cron).hasSize(1);
    }
    @Test void rejectsInvalidConfigurationAndMissingOrDuplicateTaskBeans() {
        CocoSchedulerProperties invalid = properties("task", CocoSchedulerProperties.ScheduleType.FIXED_DELAY); invalid.getTasks().get("task").setCron("0 * * * * *");
        assertThatThrownBy(() -> lifecycle(new ManualTaskScheduler(), invalid, List.of(taskBean("task", c -> { }))).start()).hasMessageContaining("must not declare cron");
        assertThatThrownBy(() -> lifecycle(new ManualTaskScheduler(), properties("missing", CocoSchedulerProperties.ScheduleType.FIXED_DELAY), List.of()).start()).hasMessageContaining("No CocoScheduledTask Bean");
        assertThatThrownBy(() -> lifecycle(new ManualTaskScheduler(), properties("same", CocoSchedulerProperties.ScheduleType.FIXED_DELAY), List.of(taskBean("same", c -> { }), taskBean("same", c -> { })))).hasMessageContaining("unique");
    }
    @Test void skipsOrQueuesOneBoundedFollowUpExecution() {
        ManualTaskScheduler scheduler = new ManualTaskScheduler(); AtomicInteger calls = new AtomicInteger(); CountDownLatch started = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        CocoScheduledTask blocking = taskBean("task", context -> { calls.incrementAndGet(); started.countDown(); release.await(); });
        CocoSchedulerProperties properties = properties("task", CocoSchedulerProperties.ScheduleType.FIXED_RATE); properties.getTasks().get("task").setOverlapPolicy(CocoSchedulerProperties.OverlapPolicy.QUEUE);
        CocoTaskSchedulerLifecycle lifecycle = lifecycle(scheduler, properties, List.of(blocking)); lifecycle.start();
        Thread first = new Thread(() -> scheduler.fixedRate.get(0).run()); first.start(); await(started); scheduler.fixedRate.get(0).run(); scheduler.fixedRate.get(0).run(); release.countDown(); join(first); await(() -> calls.get() == 2);
        assertThat(calls).hasValue(2);
    }
    @Test void retriesNormalFailuresButNotInterruptedExceptionOrError() {
        ManualTaskScheduler scheduler = new ManualTaskScheduler(); AtomicInteger attempts = new AtomicInteger();
        CocoSchedulerProperties properties = properties("task", CocoSchedulerProperties.ScheduleType.FIXED_DELAY); properties.getTasks().get("task").getRetry().setMaxAttempts(3);
        CocoTaskSchedulerLifecycle lifecycle = lifecycle(scheduler, properties, List.of(taskBean("task", c -> { if (attempts.incrementAndGet() < 3) throw new IllegalStateException("retry"); })));
        lifecycle.start(); scheduler.fixedDelay.get(0).run(); scheduler.runAllOneShot(); assertThat(attempts).hasValue(3);
        AtomicInteger interrupted = new AtomicInteger(); ManualTaskScheduler interruptedScheduler = new ManualTaskScheduler(); CocoTaskSchedulerLifecycle interruptedLifecycle = lifecycle(interruptedScheduler, properties("interrupt", CocoSchedulerProperties.ScheduleType.FIXED_DELAY), List.of(taskBean("interrupt", c -> { interrupted.incrementAndGet(); throw new InterruptedException(); }))); interruptedLifecycle.start();
        interruptedScheduler.fixedDelay.get(0).run(); assertThat(interrupted).hasValue(1); assertThat(Thread.interrupted()).isTrue();
    }
    @Test void emitsEventsAndListenerFailuresDoNotChangeTaskFailure() {
        ManualTaskScheduler scheduler = new ManualTaskScheduler(); List<CocoTaskExecutionEvent> events = new ArrayList<>();
        CocoTaskExecutionListener listener = new CocoTaskExecutionListener() { @Override public void onStarted(CocoTaskExecutionEvent event) { events.add(event); } @Override public void onFailed(CocoTaskExecutionEvent event, Throwable failure) { events.add(event); } };
        CocoTaskSchedulerLifecycle lifecycle = new CocoTaskSchedulerLifecycle(scheduler, properties("task", CocoSchedulerProperties.ScheduleType.FIXED_DELAY), List.of(taskBean("task", c -> { throw new IllegalStateException("original"); })), List.of(listener, new CocoTaskExecutionListener() { @Override public void onFailed(CocoTaskExecutionEvent event, Throwable failure) { throw new IllegalStateException("listener"); } }), CLOCK);
        lifecycle.start(); scheduler.fixedDelay.get(0).run(); assertThat(events).hasSize(2); assertThat(events.get(0).executionId()).isEqualTo(events.get(1).executionId()); assertThat(events.get(0).attempt()).isEqualTo(1);
    }
    private static CocoTaskSchedulerLifecycle lifecycle(ManualTaskScheduler scheduler, CocoSchedulerProperties properties, Collection<CocoScheduledTask> tasks) { return new CocoTaskSchedulerLifecycle(scheduler, properties, tasks, List.of(), CLOCK); }
    private static CocoSchedulerProperties properties(String id, CocoSchedulerProperties.ScheduleType type) { CocoSchedulerProperties p=new CocoSchedulerProperties(); p.getTasks().put(id, task(id,type)); return p; }
    private static CocoSchedulerProperties.TaskProperties task(String id, CocoSchedulerProperties.ScheduleType type) { CocoSchedulerProperties.TaskProperties t=new CocoSchedulerProperties.TaskProperties(); t.setId(id); t.setType(type); t.setInterval(Duration.ofSeconds(1)); return t; }
    private static CocoScheduledTask taskBean(String id, ThrowingTask task) { return new CocoScheduledTask() { public String taskId() { return id; } public void execute(CocoTaskExecutionContext context) throws Exception { task.execute(context); } }; }
    private static void await(CountDownLatch latch) { try { assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); } }
    private static void await(java.util.function.BooleanSupplier condition) { for (int i=0;i<100000&&!condition.getAsBoolean();i++) Thread.onSpinWait(); assertThat(condition.getAsBoolean()).isTrue(); }
    private static void join(Thread thread) { try { thread.join(1000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); } }
    @FunctionalInterface interface ThrowingTask { void execute(CocoTaskExecutionContext context) throws Exception; }
    static final class ManualTaskScheduler implements TaskScheduler {
        final List<Runnable> fixedDelay=new ArrayList<>(), fixedRate=new ArrayList<>(), cron=new ArrayList<>(), oneShot=new ArrayList<>();
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { cron.add(task); return future(); }
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) { oneShot.add(task); return future(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) { fixedRate.add(task); return future(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) { fixedRate.add(task); return future(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) { fixedDelay.add(task); return future(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) { fixedDelay.add(task); return future(); }
        void runAllOneShot() { while(!oneShot.isEmpty()) oneShot.remove(0).run(); }
        private ScheduledFuture<?> future() { return new CompletedFuture(); }
    }
    static final class CompletedFuture implements ScheduledFuture<Object> { public long getDelay(TimeUnit unit){return 0;} public int compareTo(Delayed o){return 0;} public boolean cancel(boolean b){return true;} public boolean isCancelled(){return false;} public boolean isDone(){return false;} public Object get(){return null;} public Object get(long t,TimeUnit u){return null;} }
}
