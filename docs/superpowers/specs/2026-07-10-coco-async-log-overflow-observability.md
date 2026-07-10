# Coco 异步日志溢出可观测性规格

## 问题

`coco-common-logging` 默认启用 `AsyncCocoLogSink`，使用容量为 1024 的进程内有界队列降低业务线程的日志等待时间。当前实现已经保证携带异常的记录和 `ERROR` 始终同步写出，并在队列满时同步写出 `WARN`；但 `TRACE`、`DEBUG` 和 `INFO` 被拒绝时会直接丢弃，没有累计计数、回调或受控诊断。

这会让业务在过载时只能看到日志缺口，无法判断是日志级别过滤、下游输出失败还是异步队列溢出。默认结构化审计日志也使用 `INFO` 路径，因此必须让允许丢弃的边界可观察，但 Coco 不能因此承诺可靠日志投递、持久化队列或观测平台能力。

## 目标

- 保持有界队列和调用线程非阻塞，继续允许队列满时丢弃 `TRACE`、`DEBUG` 和 `INFO`。
- 为每条实际丢弃的记录维护准确、单调递增的累计计数。
- 提供小型函数式监听 SPI，业务可以桥接 Micrometer、告警或其他观测系统，而 common logging 不强依赖这些系统。
- 默认监听器通过独立 SLF4J logger 输出聚合 WARN，第一次丢弃立即报告，之后只在累计数达到 2 的幂次时报告，避免过载时产生告警风暴。
- 保持 `WARN` 队列溢出时同步写出，保持 `ERROR` 和携带异常的记录始终同步写出。
- 保留 `AsyncCocoLogSink(CocoLogSink, int)` 构造器兼容性。
- 隔离业务自定义监听器异常，日志溢出诊断不得反向使业务请求失败。

## 公共契约

新增 `CocoAsyncLogDropListener` 函数式接口：

```java
void onDropped(CocoLogLevel level, String handleName, long totalDropped);
```

- `level` 是被丢弃记录的级别，只会是当前允许丢弃的低等级日志。
- `handleName` 是日志句柄名称，不传递日志正文或异常，避免扩大敏感信息暴露。
- `totalDropped` 是当前 `AsyncCocoLogSink` 实例自启动以来的累计实际丢弃数，从 1 开始并保持单调递增。
- 回调在提交日志的调用线程中执行，业务实现必须快速、非阻塞；框架会隔离其运行时异常。
- 业务定义唯一 `CocoAsyncLogDropListener` Bean 时，默认 SLF4J 监听器回退；没有自定义 Bean 时自动使用默认监听器。

`AsyncCocoLogSink` 新增接受监听器的构造器，并暴露只读 `droppedRecordCount()`。现有两参数构造器自动使用默认 SLF4J 监听器，不改变直接创建时的可观测性。

## 默认诊断

默认 `Slf4jCocoAsyncLogDropListener` 直接调用 SLF4J，不经过 `CocoLogManager` 或 `AsyncCocoLogSink`，避免诊断再次进入已满队列形成递归。

报告条件为累计丢弃数 `1, 2, 4, 8, 16, ...`。WARN 至少包含：

- 当前累计丢弃数；
- 最近一条被丢弃记录的级别；
- 最近一条被丢弃记录的日志句柄。

该策略提供随压力增长的持续信号，同时把诊断量限制在对数级。业务替换监听器后，可以自行选择指标、采样或告警策略。

## 生命周期与并发语义

- 丢弃计数只在 `ArrayBlockingQueue.offer()` 失败且记录没有同步兜底时增加。
- 队列满时同步写出的 `WARN` 不计入 dropped；始终同步写出的 `ERROR` 或携带异常记录也不计入 dropped。
- `close()` 后提交的记录继续沿用现有同步写出行为，不产生虚假丢弃计数或通知。
- 计数使用并发安全原语，多个调用线程同时溢出时不得丢失增量；监听器看到的累计数必须唯一且单调。
- 监听器抛出的运行时异常被隔离，不能中断 `log()`，也不能停止后台 writer。

## 模块边界

- 实现仅位于 `coco-common-logging`，不向 starter、Web、Audit 或其他 feature 模块移动行为。
- 不新增功能标识，不改变 feature 解析和包裁剪。
- 不新增配置项；现有 `coco.logging.async.enabled` 和 `queue-capacity` 继续决定是否使用异步队列及容量。
- 自定义 `CocoLogSink` 仍然完全替换默认 SLF4J/异步输出链路。

## 非目标

- 不保证日志不丢失、至少一次、恰好一次或跨进程可靠投递。
- 不引入持久化队列、数据库、MQ、Redis、磁盘 spool 或远程日志客户端。
- 不强依赖 Micrometer、Actuator 或特定告警平台。
- 不修改日志正文、脱敏、保留期限、合规归档或审计持久化策略。
- 不借本次改动重构整个 common logging API，也不处理 delegate 异常导致 writer 退出等相邻问题。

## 验收

- 队列满时，`TRACE`、`DEBUG` 和 `INFO` 的每条实际拒绝记录各计数并通知一次。
- 队列满时，`WARN` 同步写出且不计入 dropped。
- `ERROR` 和携带异常的记录始终同步写出且不进入 drop 通知。
- 并发溢出时最终计数准确，监听累计数不重复。
- 自定义监听器抛异常时 `log()` 不抛出，后台 writer 仍能继续排空。
- 默认监听器只在首次及 2 的幂次累计数输出 WARN，且不经过 Coco 异步日志链路。
- 关闭后写入保持同步回退，不产生 drop 通知。
- 自定义 `CocoAsyncLogDropListener` Bean 替换默认监听器；关闭 async 或自定义 `CocoLogSink` 时不使用溢出链路。
- 中英文 README 和审计路线图与实现一致。
- `mvn -B -pl :coco-common-logging -am verify`、`mvn -B verify`、`git diff --check` 通过。
- 源码变更后执行 `codegraph sync .` 并确认 `codegraph status .` 为最新。
