# Coco 默认审计日志规格

## 目标

`coco-feature-audit` 已经具备审计事件、发布器、记录器 SPI、失败策略和访问日志适配器，但默认
`NoOpCocoAuditRecorder` 会在业务未提供自定义记录器时静默丢弃全部事件。

默认实现应改为结构化日志记录器，使单 starter 项目启用 Audit 后立即获得可观测的审计输出，同时继续把
数据库持久化、MQ 投递、保留期限和合规策略留给业务项目。

## 默认行为

- 默认 `CocoAuditRecorder` 使用 Coco 日志基础设施输出单行结构化 JSON。
- 默认 logger 为 `io.github.coco.audit`，默认级别为 `INFO`。
- 访问日志审计适配器继续把 Web 访问事件发布到同一审计管道。
- `NoOpCocoAuditRecorder` 继续保留，供测试或业务显式关闭输出时使用，但不再作为默认 bean。
- 默认日志记录器不得引入数据库表、消息中间件、业务用户模型或额外运行时 JSON 依赖。
- 默认记录器提供的是可观测性输出，不是可靠存储。Coco 全局异步日志开启且队列满载时，`INFO` 审计记录可能被丢弃；框架会累计并通知该溢出，但需要强交付保证的项目仍必须替换 `CocoAuditRecorder`。

## 配置

在现有 `coco.audit` 命名空间下增加：

```yaml
coco:
  audit:
    logging:
      enabled: true
      logger-name: io.github.coco.audit
      level: INFO
```

- `enabled=false` 只关闭框架默认日志记录器，不阻止业务自定义 `CocoAuditRecorder` 工作。
- `level=OFF` 保留审计管道但不输出默认审计日志。
- `logger-name` 必须归一化空白值，空值回退默认 logger。

## 结构化事件

默认 Formatter 固定输出以下字段顺序：

1. `type`
2. `action`
3. `resourceType`
4. `resourceId`
5. `traceId`
6. `actor`
7. `tenantId`
8. `success`
9. `occurredAt`
10. `attributes`

约束：

- 可选字段缺失时输出 JSON `null`，保持结构稳定。
- `occurredAt` 使用 ISO-8601 字符串。
- `attributes` 按 key 排序，避免同一事件因 Map 顺序不同产生不稳定日志。
- 字符串中的引号、反斜线、换行、回车、制表符和其他控制字符必须转义，防止日志注入。
- Number 与 Boolean 保留 JSON 标量；其他属性值安全转换为字符串。

## SPI 与覆盖

- 新增小型 `CocoAuditFormatter` SPI，业务可以只替换日志格式，不必重写发布管道。
- 业务提供任意 `CocoAuditRecorder` bean 时，框架默认日志记录器回退。
- 业务提供 `CocoAuditFormatter` bean 时，默认日志记录器使用该 Formatter。
- 审计日志使用独立 Coco log handle，不直接依赖具体 SLF4J API。
- `CocoAuditFailurePolicy` 继续决定 Formatter 或 Recorder 失败后是忽略还是抛出。

## 明确非目标

- 数据库审计表及自动建表。
- 分布式消息投递和 exactly-once 语义。
- 审计日志防篡改、签名、归档和保留策略。
- 默认日志记录器的持久化、强交付或合规保证。
- 用户、角色、组织、菜单或租户业务模型。
- 自动推断哪些业务方法必须审计。

## 验收

- 默认自动配置不再注册 `NoOpCocoAuditRecorder`。
- 发布审计事件会写入独立 audit log handle，logger 名和级别可配置。
- 默认 Formatter 输出稳定、合法、单行 JSON，并覆盖控制字符转义和属性排序。
- `coco.audit.logging.enabled=false` 时不注册默认记录器；自定义记录器仍可建立发布管道。
- 自定义 `CocoAuditRecorder` 和 `CocoAuditFormatter` 都能替换默认实现。
- 配置元数据覆盖新增属性和日志级别提示。
- `mvn -B -pl :coco-feature-audit -am verify` 与全量 `mvn -B verify` 通过。
- 源码变更后执行 `codegraph sync .`，并保持 `git diff --check` 干净。
