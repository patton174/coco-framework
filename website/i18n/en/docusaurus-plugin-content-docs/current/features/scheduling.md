---
title: Dynamic Scheduled Tasks
---

# Dynamic Scheduled Tasks

Coco Scheduling (`coco-scheduling`) provides two capabilities on top of Spring's `TaskScheduler`: declaring method-level scheduled tasks with `@CocoScheduled`, and programmatically registering, replacing, and cancelling tasks at runtime via `CocoTaskScheduler`. Compared with the native `@Scheduled`, it additionally provides stable task names, overlapping-execution policies, execution-result observation, and unified shutdown lifecycle management.

Scheduling is bound to the `coco.scheduling` namespace. Unlike rate limiting, idempotency, and locking, scheduling is **enabled by default** (`coco.scheduling.enabled` defaults to `true`, with `matchIfMissing = true`) and does not depend on the Web runtime.

## Overview

- **Annotation-based declaration**: `@CocoScheduled` supports three trigger types — Cron, fixed delay (fixedDelay), and fixed rate (fixedRate) — exactly one of which must be specified.
- **Dynamic local task scheduling**: use `CocoTaskScheduler` / `CocoTaskRegistry` to `register`, `replace`, and `cancel` tasks at runtime and query their status. Tasks execute on the local scheduler (local, without cross-instance sharding).
- **Overlapping-execution policy**: `CocoTaskOverlapPolicy` controls the behavior when a new trigger fires before the previous execution has finished; the default is `SKIP`.
- **Execution observation**: the `CocoTaskExecutionObserver` SPI receives task started, succeeded, failed, and skipped events; the events carry only governance metadata, not business parameters or exception text.
- **Stable task names and status snapshots**: each task has a stable name, and you can query the most recent execution result, duration, TraceId, and other status.
- **Replaceable components**: `TaskScheduler`, `CocoTaskExecutionGuard`, and `CocoTaskScheduler` can all be overridden by beans provided by the business.

## How to Enable and Integrate

Scheduling is enabled by default and can typically be used without any extra configuration. To disable it, set `coco.scheduling.enabled=false` or disable `scheduling` via the feature toggle.

### 1. (Optional) Adjust the Scheduling Configuration

```yaml
coco:
  scheduling:
    enabled: true
    pool-size: 4
    thread-name-prefix: "coco-scheduling-"
    shutdown:
      await-termination: 30s
      interrupt: false
```

### 2. Declare Tasks with Annotations

```java
@Component
public class ReportJobs {

    @CocoScheduled(name = "daily-report", cron = "0 0 2 * * *", zone = "Asia/Shanghai")
    public void dailyReport() {
        // Runs daily at 02:00 (Shanghai time zone)
    }

    @CocoScheduled(fixedDelay = "10s", initialDelay = "5s",
            overlapPolicy = CocoTaskOverlapPolicy.SKIP)
    public void pollQueue() {
        // Triggers 10 seconds after the previous execution finishes; skips on overlap
    }
}
```

When `name` is not explicitly specified, `beanName#methodName` is used as the stable name. `fixedDelay`, `fixedRate`, and `initialDelay` use Spring `DurationStyle` text, such as `10s` or `PT10S`. When `enabled = false`, the task is not registered with the underlying scheduler.

### 3. Programmatic Dynamic Registration

```java
@Service
public class TaskAdminService {

    private final CocoTaskScheduler scheduler;

    public TaskAdminService(CocoTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleCleanup() {
        CocoTaskDefinition definition = CocoTaskDefinition
                .builder("temp-cleanup", () -> cleanupTempFiles())
                .fixedRate(Duration.ofMinutes(5))
                .initialDelay(Duration.ofSeconds(30))
                .overlapPolicy(CocoTaskOverlapPolicy.SKIP)
                .build();
        this.scheduler.register(definition);       // Rejected if a task with the same name already exists
    }

    public void reschedule(CocoTaskDefinition definition) {
        this.scheduler.replace(definition);        // Atomically replaces and cancels the old future
    }

    public boolean stop(String name) {
        return this.scheduler.cancel(name);        // Cancels and removes
    }

    public List<CocoTaskStatus> overview() {
        return this.scheduler.list();              // Status snapshot of all tasks
    }
}
```

A single task definition may declare only one of Cron, fixed delay, or fixed rate, strictly validated at registration time. `register` rejects a task with a duplicate name; `replace` atomically replaces it and cancels the old task's future.

## Usage Examples

### Observing Task Execution

Implement `CocoTaskExecutionObserver` and register it as a bean to receive execution events:

```java
@Component
public class LoggingTaskObserver implements CocoTaskExecutionObserver {

    @Override
    public void onExecution(CocoTaskExecutionEvent event) {
        // event.taskName() / outcome() / duration() / traceId() / failureType()
        log.info("task={} outcome={} durationMs={} traceId={}",
                event.taskName(), event.outcome(),
                event.duration().toMillis(), event.traceId());
    }
}
```

Exceptions thrown by an observer are isolated by the scheduler and will not terminate subsequent task scheduling. `CocoTaskExecutionOutcome` values are: `NONE`, `STARTED`, `SUCCEEDED`, `FAILED`, `SKIPPED`.

### Overlapping Policy

| Policy | Behavior |
|------|------|
| `SKIP` (default) | When the previous execution has not yet finished, the new trigger is skipped and a `SKIPPED` event is produced. |
| `ALLOW` | Allows multiple executions of a task with the same name to overlap concurrently. |

`SKIP` is guaranteed by `CocoTaskExecutionGuard`. The default implementation is an in-process mutex that only skips overlaps within a single instance; for cross-instance "only one instance executes at the same moment", the business can replace the guard with an adapter implementation based on a distributed lock.

## Key Configuration Items

Prefix `coco.scheduling`.

| Configuration Item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `true` | Whether to enable the scheduling module (`matchIfMissing = true`, enabled by default). |
| `pool-size` | int | `1` | Thread pool size of the default `ThreadPoolTaskScheduler`. |
| `thread-name-prefix` | string | `coco-scheduling-` | Prefix for scheduling thread names. |
| `shutdown.await-termination` | Duration | `30s` | Maximum time to wait for in-flight tasks to complete during shutdown. |
| `shutdown.interrupt` | boolean | `false` | Whether to interrupt executing tasks during shutdown (`false` means wait for them to finish). |

`@CocoScheduled` annotation attributes: `name`, `cron`, `fixedDelay`, `fixedRate`, `zone`, `initialDelay`, `overlapPolicy` (default `SKIP`), and `enabled` (default `true`).

## Boundary Notes

- **Local scheduling, not cluster sharding**: tasks run on the local `TaskScheduler`. In a multi-instance deployment, each instance triggers the same-named task on its own; when you need "globally single execution", replace `CocoTaskExecutionGuard` with a distributed implementation, or control it yourself with a distributed lock.
- **One of three trigger types**: exactly one of Cron, fixed delay, or fixed rate must be specified, whether via the annotation or `CocoTaskDefinition`; violating this constraint raises an error at registration/validation time.
- **Default SKIP only takes effect in-process**: the default guard is an in-process mutex and cannot prevent overlaps across different instances.
- **Registration name is unique**: `register` rejects a task with a duplicate name; use `replace` to replace.
- **Events carry no business data**: execution events and status snapshots deliberately do not carry method parameters, the Runnable, or exception messages, exposing only the metadata required for governance to avoid leaking business data through governance interfaces.
- **Shutdown behavior is affected by configuration**: when `shutdown.interrupt=false`, it waits for in-flight tasks up to `await-termination`; setting it to `true` attempts to interrupt, so long-running tasks must handle interruption themselves.