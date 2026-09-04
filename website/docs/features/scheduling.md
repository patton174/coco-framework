---
title: 动态定时任务
---

# 动态定时任务

Coco 调度（`coco-scheduling`）在 Spring `TaskScheduler` 之上提供两种能力：一是用 `@CocoScheduled` 声明方法级定时任务，二是通过 `CocoTaskScheduler` 编程式地在运行时动态注册、替换、取消任务。相比原生 `@Scheduled`，它额外提供稳定任务名、重叠执行策略、执行结果观测和统一的关闭生命周期管理。

调度绑定 `coco.scheduling` 命名空间。与限流、幂等、锁不同，调度**默认开启**（`coco.scheduling.enabled` 默认 `true`，`matchIfMissing = true`），不依赖 Web 运行时。

## 功能简介

- **注解式声明**：`@CocoScheduled` 支持 Cron、固定延迟（fixedDelay）、固定频率（fixedRate）三种触发方式，三者必须且只能指定一个。
- **动态本地任务调度**：通过 `CocoTaskScheduler` / `CocoTaskRegistry` 在运行时 `register`、`replace`、`cancel` 任务，并查询状态。任务在本地调度器上执行（本地，不含跨实例分片）。
- **重叠执行策略**：`CocoTaskOverlapPolicy` 控制上一次执行未结束时新触发的行为，默认 `SKIP`（跳过）。
- **执行观测**：`CocoTaskExecutionObserver` SPI 接收任务开始、成功、失败、跳过等事件，事件只携带治理元数据，不含业务参数或异常文本。
- **稳定任务名与状态快照**：每个任务有稳定名称，可查询最近一次执行结果、耗时、TraceId 等状态。
- **可替换组件**：`TaskScheduler`、`CocoTaskExecutionGuard`、`CocoTaskScheduler` 均可由业务方提供 Bean 覆盖。

## 如何启用接入

调度默认开启，通常无需额外配置即可使用。如需关闭，可设置 `coco.scheduling.enabled=false` 或通过特性开关禁用 `scheduling`。

### 1. （可选）调整调度配置

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

### 2. 用注解声明任务

```java
@Component
public class ReportJobs {

    @CocoScheduled(name = "daily-report", cron = "0 0 2 * * *", zone = "Asia/Shanghai")
    public void dailyReport() {
        // 每天 02:00（上海时区）执行
    }

    @CocoScheduled(fixedDelay = "10s", initialDelay = "5s",
            overlapPolicy = CocoTaskOverlapPolicy.SKIP)
    public void pollQueue() {
        // 上次执行结束后再等 10 秒触发；重叠则跳过
    }
}
```

未显式指定 `name` 时使用 `beanName#methodName` 作为稳定名。`fixedDelay`、`fixedRate`、`initialDelay` 使用 Spring `DurationStyle` 文本，如 `10s` 或 `PT10S`。`enabled = false` 时任务不注册到底层调度器。

### 3. 编程式动态注册

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
        this.scheduler.register(definition);       // 同名已存在则拒绝
    }

    public void reschedule(CocoTaskDefinition definition) {
        this.scheduler.replace(definition);        // 原子替换并取消旧 future
    }

    public boolean stop(String name) {
        return this.scheduler.cancel(name);        // 取消并移除
    }

    public List<CocoTaskStatus> overview() {
        return this.scheduler.list();              // 全部任务状态快照
    }
}
```

一个任务定义只能声明 Cron、固定延迟或固定频率中的一种，注册时严格校验。`register` 遇同名任务拒绝；`replace` 原子替换并取消旧任务的 future。

## 使用示例

### 观测任务执行

实现 `CocoTaskExecutionObserver` 并注册为 Bean 即可接收执行事件：

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

观察器抛出的异常会被调度器隔离，不会终止后续任务调度。`CocoTaskExecutionOutcome` 取值：`NONE`、`STARTED`、`SUCCEEDED`、`FAILED`、`SKIPPED`。

### 重叠策略

| 策略 | 行为 |
|------|------|
| `SKIP`（默认） | 上一次执行尚未结束时，跳过新的触发，并产生 `SKIPPED` 事件。 |
| `ALLOW` | 允许同名任务的多个执行并发重叠。 |

`SKIP` 由 `CocoTaskExecutionGuard` 保证。默认实现是进程内互斥，只在单实例内跳过重叠；如需跨实例的"同一时刻只有一个实例执行"，业务方可将 guard 替换为基于分布式锁的适配实现。

## 关键配置项

前缀 `coco.scheduling`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用调度模块（`matchIfMissing = true`，默认开启）。 |
| `pool-size` | int | `1` | 默认 `ThreadPoolTaskScheduler` 线程池大小。 |
| `thread-name-prefix` | string | `coco-scheduling-` | 调度线程名前缀。 |
| `shutdown.await-termination` | Duration | `30s` | 关闭时等待在执行任务完成的最长时间。 |
| `shutdown.interrupt` | boolean | `false` | 关闭时是否中断正在执行的任务（`false` 表示等待其完成）。 |

`@CocoScheduled` 注解属性：`name`、`cron`、`fixedDelay`、`fixedRate`、`zone`、`initialDelay`、`overlapPolicy`（默认 `SKIP`）、`enabled`（默认 `true`）。

## 边界注意事项

- **本地调度，非集群分片**：任务在本地 `TaskScheduler` 上运行。多实例部署时每个实例都会各自触发同名任务；需要"全局单次执行"时，请把 `CocoTaskExecutionGuard` 替换为分布式实现，或结合分布式锁自行控制。
- **三选一触发方式**：Cron、固定延迟、固定频率必须且只能指定其一，无论注解还是 `CocoTaskDefinition`，违反约束会在注册/校验时报错。
- **默认 SKIP 只在进程内生效**：默认 guard 是进程内互斥，无法阻止不同实例的重叠。
- **注册名唯一**：`register` 遇同名任务拒绝；替换请用 `replace`。
- **事件不含业务数据**：执行事件与状态快照刻意不携带方法参数、Runnable、异常消息，仅暴露治理所需元数据，避免通过治理接口泄露业务数据。
- **关闭行为受配置影响**：`shutdown.interrupt=false` 时会等待在执行任务至多 `await-termination`；设置为 `true` 会尝试中断，长任务需自行处理中断响应。

