package io.github.coco.feature.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

/** 管理 Coco 配置任务的注册、执行和关闭。 */
public final class CocoTaskSchedulerLifecycle implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(CocoTaskSchedulerLifecycle.class);
    private final TaskScheduler scheduler;
    private final CocoSchedulerProperties properties;
    private final Map<String, CocoScheduledTask> tasks;
    private final List<CocoTaskExecutionListener> listeners;
    private final Clock clock;
    private final Map<String, TaskRunner> runners = new HashMap<>();
    private final List<ScheduledFuture<?>> registrations = new ArrayList<>();
    private volatile boolean running;

    public CocoTaskSchedulerLifecycle(TaskScheduler scheduler, CocoSchedulerProperties properties,
            Collection<CocoScheduledTask> taskBeans, Collection<CocoTaskExecutionListener> listeners, Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.listeners = List.copyOf(listeners); this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tasks = taskMap(taskBeans);
    }
    @Override public synchronized void start() {
        if (this.running || !this.properties.isEnabled()) return;
        CocoSchedulerConfigurationValidator.validate(this.properties);
        for (Map.Entry<String, CocoSchedulerProperties.TaskProperties> entry : this.properties.getTasks().entrySet()) {
            String id = resolvedId(entry); CocoScheduledTask task = this.tasks.get(id);
            if (task == null) throw new IllegalStateException("No CocoScheduledTask Bean found for configured task id: " + id);
            TaskRunner runner = new TaskRunner(id, task, entry.getValue()); this.runners.put(id, runner);
            this.registrations.add(register(runner, entry.getValue()));
        }
        this.running = true;
    }
    @Override public synchronized void stop() {
        if (!this.running) return;
        this.running = false; this.registrations.forEach(future -> future.cancel(false));
        long deadline = System.nanoTime() + this.properties.getShutdownAwait().toNanos();
        for (TaskRunner runner : this.runners.values()) runner.stop();
        for (TaskRunner runner : this.runners.values()) runner.awaitStopped(deadline);
        this.registrations.clear(); this.runners.clear();
    }
    @Override public void stop(Runnable callback) { try { stop(); } finally { callback.run(); } }
    @Override public boolean isRunning() { return this.running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    private ScheduledFuture<?> register(TaskRunner runner, CocoSchedulerProperties.TaskProperties task) {
        Runnable trigger = () -> runner.trigger(this.clock.instant());
        Instant startTime = this.clock.instant().plus(task.getInitialDelay());
        if (task.getType() == CocoSchedulerProperties.ScheduleType.FIXED_DELAY) return this.scheduler.scheduleWithFixedDelay(trigger, startTime, task.getInterval());
        if (task.getType() == CocoSchedulerProperties.ScheduleType.FIXED_RATE) return this.scheduler.scheduleAtFixedRate(trigger, startTime, task.getInterval());
        Trigger cron = new CronTrigger(task.getCron(), task.getZone() == null || task.getZone().isBlank() ? this.clock.getZone() : java.time.ZoneId.of(task.getZone()));
        return this.scheduler.schedule(trigger, cron);
    }
    private Map<String, CocoScheduledTask> taskMap(Collection<CocoScheduledTask> beans) {
        Map<String, CocoScheduledTask> result = new HashMap<>();
        for (CocoScheduledTask task : beans) {
            String id = task.taskId();
            if (id == null || id.isBlank() || result.putIfAbsent(id, task) != null) throw new IllegalStateException("CocoScheduledTask Bean ids must be nonblank and unique: " + id);
        } return result;
    }
    private static String resolvedId(Map.Entry<String, CocoSchedulerProperties.TaskProperties> entry) { return entry.getValue().getId() == null || entry.getValue().getId().isBlank() ? entry.getKey() : entry.getValue().getId().trim(); }
    private final class TaskRunner {
        private final String id; private final CocoScheduledTask task; private final CocoSchedulerProperties.TaskProperties config;
        private final AtomicBoolean active = new AtomicBoolean(); private final AtomicBoolean queued = new AtomicBoolean(); private volatile boolean accepting = true;
        TaskRunner(String id, CocoScheduledTask task, CocoSchedulerProperties.TaskProperties config) { this.id=id; this.task=task; this.config=config; }
        void trigger(Instant scheduledAt) {
            if (!accepting) return;
            if (!active.compareAndSet(false, true)) {
                if (config.getOverlapPolicy() == CocoSchedulerProperties.OverlapPolicy.QUEUE) queued.set(true); else notifySkipped(id);
                return;
            } run(scheduledAt, UUID.randomUUID(), 1);
        }
        void run(Instant scheduledAt, UUID executionId, int attempt) {
            if (!accepting) {
                complete();
                return;
            }
            Instant startedAt = clock.instant(); CocoTaskExecutionContext context = new CocoTaskExecutionContext(executionId, scheduledAt, startedAt, attempt, clock);
            CocoTaskExecutionEvent event = event(id, context); notifyStarted(event);
            try {
                task.execute(context); Duration duration = Duration.between(startedAt, clock.instant()); notifySucceeded(event, duration);
                if (config.getWarningThreshold() != null && duration.compareTo(config.getWarningThreshold()) > 0) LOGGER.warn("Coco task {} exceeded warning-threshold: duration={}", id, duration);
                complete();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); notifyFailed(event, ex); complete();
            } catch (Error error) { notifyFailed(event, error); complete(); throw error;
            } catch (Exception ex) {
                notifyFailed(event, ex);
                if (attempt < config.getRetry().getMaxAttempts() && accepting) {
                    Duration delay = retryDelay(attempt); notifyRetry(event, delay, ex); scheduler.schedule(() -> run(scheduledAt, executionId, attempt + 1), clock.instant().plus(delay));
                } else complete();
            }
        }
        void complete() { active.set(false); if (accepting && queued.compareAndSet(true, false)) trigger(clock.instant()); }
        void stop() { accepting = false; }
        void awaitStopped(long deadline) { while (active.get() && System.nanoTime() < deadline) { Thread.onSpinWait(); } }
        Duration retryDelay(int previousAttempt) { Duration delay=config.getRetry().getBackoff(); for (int i=1;i<previousAttempt && delay.compareTo(config.getRetry().getMaxBackoff())<0;i++) delay=delay.multipliedBy(2); return delay.compareTo(config.getRetry().getMaxBackoff())>0 ? config.getRetry().getMaxBackoff() : delay; }
    }
    private CocoTaskExecutionEvent event(String id, CocoTaskExecutionContext c) { return new CocoTaskExecutionEvent(id,c.executionId(),c.scheduledAt(),c.startedAt(),c.attempt()); }
    private void notifyStarted(CocoTaskExecutionEvent e) { listeners.forEach(l -> safely(() -> l.onStarted(e))); }
    private void notifySucceeded(CocoTaskExecutionEvent e, Duration d) { listeners.forEach(l -> safely(() -> l.onSucceeded(e,d))); }
    private void notifyFailed(CocoTaskExecutionEvent e, Throwable t) { listeners.forEach(l -> safely(() -> l.onFailed(e,t))); }
    private void notifyRetry(CocoTaskExecutionEvent e, Duration d, Throwable t) { listeners.forEach(l -> safely(() -> l.onRetryScheduled(e,d,t))); }
    private void notifySkipped(String id) { listeners.forEach(l -> safely(() -> l.onSkipped(id))); }
    private void safely(Runnable action) { try { action.run(); } catch (RuntimeException ex) { LOGGER.warn("Coco task execution listener failed", ex); } }
}
