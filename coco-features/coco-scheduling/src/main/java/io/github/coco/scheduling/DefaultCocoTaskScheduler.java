package io.github.coco.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.trace.CocoTraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 基于 Spring {@link TaskScheduler} 的默认 Coco 任务调度器。
 *
 * @since 1.0.0
 */
public final class DefaultCocoTaskScheduler implements CocoTaskScheduler {

    private static final Log LOGGER = LogFactory.getLog(DefaultCocoTaskScheduler.class);

    private final Object monitor = new Object();
    private final TaskScheduler taskScheduler;
    private final CocoTaskExecutionGuard guard;
    private final List<CocoTaskExecutionObserver> observers;
    private final CocoTaskDefinitionValidator validator;
    private final CocoSchedulingProperties.ShutdownProperties shutdown;
    private final ConcurrentHashMap<String, Registration> registrations = new ConcurrentHashMap<>();
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private volatile boolean closed;

    DefaultCocoTaskScheduler(TaskScheduler taskScheduler, CocoTaskExecutionGuard guard,
            Collection<CocoTaskExecutionObserver> observers, CocoTaskDefinitionValidator validator,
            CocoSchedulingProperties.ShutdownProperties shutdown) {
        this.taskScheduler = taskScheduler;
        this.guard = guard;
        this.observers = List.copyOf(observers);
        this.validator = validator;
        this.shutdown = shutdown;
    }

    @Override
    public void register(CocoTaskDefinition definition) {
        register(definition, false);
    }

    @Override
    public void replace(CocoTaskDefinition definition) {
        register(definition, true);
    }

    @Override
    public boolean cancel(String name) {
        if (name == null || name.isBlank()) {
            throw this.validator.error(CocoSchedulingMessage.TASK_NAME_REQUIRED);
        }
        synchronized (this.monitor) {
            Registration registration = this.registrations.remove(name.trim());
            if (registration == null) {
                return false;
            }
            registration.cancel(this.shutdown.isInterrupt());
            return true;
        }
    }

    @Override
    public List<CocoTaskStatus> list() {
        List<CocoTaskStatus> statuses = new ArrayList<>();
        for (Registration registration : this.registrations.values()) {
            statuses.add(registration.status.get());
        }
        statuses.sort(Comparator.comparing(CocoTaskStatus::name));
        return List.copyOf(statuses);
    }

    @Override
    public Optional<CocoTaskStatus> status(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Registration registration = this.registrations.get(name.trim());
        return registration == null ? Optional.empty() : Optional.of(registration.status.get());
    }

    @Override
    public void close() {
        List<Registration> registrations;
        synchronized (this.monitor) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            registrations = List.copyOf(this.registrations.values());
            this.registrations.clear();
            for (Registration registration : registrations) {
                registration.cancel(this.shutdown.isInterrupt());
            }
        }
        awaitActiveExecutions();
    }

    private void register(CocoTaskDefinition definition, boolean replace) {
        CocoTaskScheduleType scheduleType = this.validator.validate(definition);
        synchronized (this.monitor) {
            ensureOpen();
            Registration current = this.registrations.get(definition.getName());
            if (!replace && current != null) {
                throw this.validator.error(CocoSchedulingMessage.TASK_EXISTS, definition.getName());
            }
            if (replace && current == null) {
                throw this.validator.error(CocoSchedulingMessage.TASK_NOT_FOUND, definition.getName());
            }
            Registration next = new Registration(definition, scheduleType, CocoTraceContext.capture());
            next.future = definition.isEnabled() ? schedule(next) : null;
            this.registrations.put(definition.getName(), next);
            if (current != null) {
                current.cancel(this.shutdown.isInterrupt());
            }
        }
    }

    private ScheduledFuture<?> schedule(Registration registration) {
        ScheduledFuture<?> future;
        if (registration.scheduleType == CocoTaskScheduleType.CRON) {
            future = this.taskScheduler.schedule(() -> execute(registration),
                    new CronTrigger(registration.definition.getCron(), registration.definition.getZone()));
        }
        else if (registration.scheduleType == CocoTaskScheduleType.FIXED_DELAY) {
            future = registration.definition.getInitialDelay().isZero()
                    ? this.taskScheduler.scheduleWithFixedDelay(() -> execute(registration), registration.definition.getFixedDelay())
                    : this.taskScheduler.scheduleWithFixedDelay(() -> execute(registration),
                            Instant.now(this.taskScheduler.getClock()).plus(registration.definition.getInitialDelay()),
                            registration.definition.getFixedDelay());
        }
        else {
            future = registration.definition.getInitialDelay().isZero()
                    ? this.taskScheduler.scheduleAtFixedRate(() -> execute(registration), registration.definition.getFixedRate())
                    : this.taskScheduler.scheduleAtFixedRate(() -> execute(registration),
                            Instant.now(this.taskScheduler.getClock()).plus(registration.definition.getInitialDelay()),
                            registration.definition.getFixedRate());
        }
        if (future == null) {
            throw this.validator.error(CocoSchedulingMessage.SCHEDULER_REJECTED, registration.definition.getName());
        }
        return future;
    }

    private void execute(Registration registration) {
        if (!reserveExecution(registration)) {
            return;
        }
        try {
            try (CocoContextScope ignored = CocoTraceContext.restore(registration.traceContext)) {
                String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
                boolean guarded = false;
                Throwable failure = null;
                if (registration.definition.getOverlapPolicy() == CocoTaskOverlapPolicy.SKIP) {
                    try {
                        guarded = this.guard.tryAcquire(registration.definition.getName());
                    }
                    catch (Throwable exception) {
                        failure = exception;
                    }
                    if (failure == null && !guarded) {
                        Instant now = Instant.now(this.taskScheduler.getClock());
                        update(registration, CocoTaskExecutionOutcome.SKIPPED, null, now, Duration.ZERO, traceId);
                        publish(new CocoTaskExecutionEvent(registration.definition.getName(), CocoTaskExecutionOutcome.SKIPPED,
                                now, Duration.ZERO, traceId, null));
                        return;
                    }
                }

                Instant started = Instant.now(this.taskScheduler.getClock());
                update(registration, CocoTaskExecutionOutcome.STARTED, started, null, null, traceId);
                publish(new CocoTaskExecutionEvent(registration.definition.getName(), CocoTaskExecutionOutcome.STARTED,
                        started, Duration.ZERO, traceId, null));
                if (failure == null) {
                    try {
                        registration.definition.getTask().run();
                    }
                    catch (Throwable exception) {
                        failure = exception;
                    }
                }
                if (guarded) {
                    try {
                        this.guard.release(registration.definition.getName());
                    }
                    catch (Throwable exception) {
                        if (failure == null) {
                            failure = exception;
                        }
                        else {
                            LOGGER.error("Coco scheduled task guard release failed: name="
                                    + registration.definition.getName() + ", failureType="
                                    + exception.getClass().getName());
                        }
                    }
                }
                if (failure == null) {
                    complete(registration, CocoTaskExecutionOutcome.SUCCEEDED, started, traceId, null);
                }
                else {
                    complete(registration, CocoTaskExecutionOutcome.FAILED, started, traceId,
                            failure.getClass().getName());
                    LOGGER.error("Coco scheduled task failed: name=" + registration.definition.getName()
                            + ", failureType=" + failure.getClass().getName());
                }
            }
        }
        finally {
            releaseExecutionReservation();
        }
    }

    private boolean reserveExecution(Registration registration) {
        synchronized (this.monitor) {
            if (this.closed || this.registrations.get(registration.definition.getName()) != registration) {
                return false;
            }
            this.activeExecutions.incrementAndGet();
            return true;
        }
    }

    private void releaseExecutionReservation() {
        if (this.activeExecutions.decrementAndGet() == 0) {
            synchronized (this.monitor) {
                this.monitor.notifyAll();
            }
        }
    }

    private void complete(Registration registration, CocoTaskExecutionOutcome outcome, Instant started, String traceId,
            String failureType) {
        Instant completed = Instant.now(this.taskScheduler.getClock());
        Duration duration = Duration.between(started, completed);
        update(registration, outcome, started, completed, duration, traceId);
        publish(new CocoTaskExecutionEvent(registration.definition.getName(), outcome, completed, duration, traceId,
                failureType));
    }

    private void update(Registration registration, CocoTaskExecutionOutcome outcome, Instant started, Instant completed,
            Duration duration, String traceId) {
        registration.status.updateAndGet(current -> new CocoTaskStatus(current.name(), current.scheduleType(),
                current.overlapPolicy(), current.enabled(), current.scheduled(), outcome,
                started == null ? current.lastStartedAt() : started,
                completed == null ? current.lastCompletedAt() : completed,
                duration == null ? current.lastDuration() : duration, traceId));
    }

    private void publish(CocoTaskExecutionEvent event) {
        for (CocoTaskExecutionObserver observer : this.observers) {
            try {
                observer.onExecution(event);
            }
            catch (Throwable exception) {
                LOGGER.warn("Coco task execution observer failed: observer=" + observer.getClass().getName());
            }
        }
    }

    private void ensureOpen() {
        if (this.closed) {
            throw this.validator.error(CocoSchedulingMessage.SCHEDULER_CLOSED);
        }
    }

    private void awaitActiveExecutions() {
        Duration wait = this.shutdown.getAwaitTermination();
        long remainingNanos = wait.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (this.monitor) {
            while (this.activeExecutions.get() > 0 && remainingNanos > 0) {
                try {
                    long millis = remainingNanos / 1_000_000;
                    int nanos = (int) (remainingNanos % 1_000_000);
                    this.monitor.wait(millis, nanos);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                remainingNanos = deadline - System.nanoTime();
            }
        }
    }

    private static final class Registration {

        private final CocoTaskDefinition definition;
        private final CocoTaskScheduleType scheduleType;
        private final CocoContextSnapshot traceContext;
        private final AtomicReference<CocoTaskStatus> status;
        private volatile ScheduledFuture<?> future;

        private Registration(CocoTaskDefinition definition, CocoTaskScheduleType scheduleType,
                CocoContextSnapshot traceContext) {
            this.definition = definition;
            this.scheduleType = scheduleType;
            this.traceContext = traceContext;
            this.status = new AtomicReference<>(CocoTaskStatus.initial(definition, scheduleType, definition.isEnabled()));
        }

        private void cancel(boolean interrupt) {
            if (this.future != null) {
                this.future.cancel(interrupt);
            }
        }
    }
}
