---
title: 审计管道
---

# 审计管道

Coco 审计管道（`coco-feature-audit`）把框架和业务产生的“具有审计意义的动作”收敛成结构化的审计事件，再通过统一的发布器分发到一个或多个落地端。事件来源只依赖发布器、不感知具体落地端，因此 Web、Security、Tenant 等模块与最终存储实现保持解耦。模块绑定 `coco.audit` 命名空间，默认启用，并作为一个 Coco Feature（`CocoFeature.AUDIT`）参与自动装配。

## 功能简介

管道由四类角色构成：

- **`CocoAuditEvent`**：不可变的审计事件模型，只承载审计语义，不负责日志打印、数据库写入或消息投递。
- **`CocoAuditPublisher`**：审计事件发布器，负责把事件分发给一个或多个 `CocoAuditRecorder`。默认实现 `CompositeCocoAuditPublisher` 聚合所有记录器。
- **`CocoAuditRecorder`**：审计记录器 SPI，具体写入日志、数据库、消息队列或外部审计系统由实现方决定。默认提供把事件写入独立日志句柄的 `LoggingCocoAuditRecorder`。
- **`CocoAccessLogAuditRecorder`**：访问日志适配器，把 Web 模块发布的访问日志事件转换成审计事件，只做语义转换、不负责最终存储。

### 审计事件字段

`CocoAuditEvent` 通过 `CocoAuditEvent.builder(type)` 构建，`type` 必填，其余字段可选：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | `String` | 事件类型，必填且不可为空 |
| `action` | `Optional<String>` | 审计动作 |
| `resourceType` | `Optional<String>` | 资源类型 |
| `resourceId` | `Optional<String>` | 资源标识 |
| `traceId` | `Optional<String>` | 链路标识 |
| `actor` | `Optional<String>` | 操作者标识 |
| `tenantId` | `Optional<String>` | 租户标识 |
| `success` | `boolean` | 动作是否成功，默认 `true` |
| `occurredAt` | `Instant` | 事件发生时间，未设置时取 `Instant.now()` |
| `attributes` | `Map<String, Object>` | 扩展属性，键或值为空的条目会被丢弃，构建后不可变 |

## 如何启用接入

模块默认启用，无需额外开关即可工作。默认日志记录器会把审计事件写入独立的日志句柄，`logger` 名称默认为 `io.github.coco.audit`，级别默认 `INFO`。要发布自定义审计事件，注入 `CocoAuditPublisher` 即可：

```java
@Service
public class OrderService {

    private final CocoAuditPublisher auditPublisher;

    public OrderService(CocoAuditPublisher auditPublisher) {
        this.auditPublisher = auditPublisher;
    }

    public void cancel(String orderId, String operator) {
        // ... 业务处理
        auditPublisher.publish(CocoAuditEvent.builder("order")
                .action("cancel")
                .resourceType("order")
                .resourceId(orderId)
                .actor(operator)
                .success(true)
                .attribute("reason", "user-request")
                .build());
    }
}
```

要把审计事件落地到数据库或消息队列，声明自己的 `CocoAuditRecorder` Bean 即可，它会被 `CompositeCocoAuditPublisher` 自动纳入分发链，与默认日志记录器并存：

```java
@Component
public class JdbcAuditRecorder implements CocoAuditRecorder {

    @Override
    public void record(CocoAuditEvent event) {
        // 写入审计表 / 投递到消息队列
    }
}
```

## 使用示例

访问日志审计适配默认开启（`coco.audit.access-log.enabled=true`），且需要容器中存在 `CocoAuditPublisher`。开启后，Web 模块每条访问日志都会被转换成 `type=access-log` 的审计事件：`action` 取 HTTP 方法，`resourceType` 为 `http-request`，`resourceId` 取请求路径，`success` 沿用访问日志的成功标记，并把状态码、耗时、客户端 IP、User-Agent、内容类型、异常类型、浏览器指纹等写入 `attributes`。

```yaml
coco:
  audit:
    enabled: true
    failure-policy: IGNORE
    logging:
      enabled: true
      logger-name: io.github.coco.audit
      level: INFO
    access-log:
      enabled: true
```

## 失败策略

单个记录器抛异常时的处理方式由 `CocoAuditFailurePolicy` 决定，通过 `coco.audit.failure-policy` 配置：

| 取值 | 说明 |
| --- | --- |
| `IGNORE`（默认） | 忽略单个记录器失败，继续分发给后续记录器 |
| `THROW` | 立即抛出记录器失败异常 |

默认 `IGNORE` 保证审计落地失败不影响主业务流程；对审计完整性要求强的场景可切换为 `THROW`，但需要业务方评估失败对主流程的影响。

## 关键配置项

绑定前缀 `coco.audit`（对应 `CocoAuditProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.audit.enabled` | `boolean` | `true` | 是否启用审计基础设施 |
| `coco.audit.failure-policy` | `CocoAuditFailurePolicy` | `IGNORE` | 记录器失败策略：`IGNORE` / `THROW` |
| `coco.audit.logging.enabled` | `boolean` | `true` | 是否启用默认审计日志记录器 |
| `coco.audit.logging.logger-name` | `String` | `io.github.coco.audit` | 审计日志 logger 名称，为空时回退默认值 |
| `coco.audit.logging.level` | `CocoLogLevel` | `INFO` | 审计日志输出级别 |
| `coco.audit.access-log.enabled` | `boolean` | `true` | 是否把 Web 访问日志转换为审计事件 |

## 边界注意事项

- 默认审计日志记录器（`LoggingCocoAuditRecorder`）依赖容器中存在 `CocoLogManager`；若日志基础设施未装配，默认记录器不会创建，此时如果也没有业务自定义 `CocoAuditRecorder`，发布器同样不会创建。
- 访问日志适配依赖 Web 访问日志能力已开启并发布 `CocoAccessLog`；未启用 Web 访问日志时不会产生 `access-log` 审计事件。
- `attributes` 中键或值为 `null` 的条目会被静默丢弃，`type` 为空会抛 `IllegalArgumentException`，构建审计事件时需注意。
