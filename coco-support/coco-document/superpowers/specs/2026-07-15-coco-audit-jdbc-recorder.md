# Coco JDBC 审计记录器规格

## 目标

新增独立工件 `coco-audit-jdbc`，为 `coco-audit` 的 `CocoAuditRecorder` SPI 提供可选 JDBC 持久化适配器。该工件不加入 starter、`coco-features` 聚合、BOM 或任何共享 composition 文件；业务项目只有显式依赖该工件并设置 `coco.audit.jdbc.enabled=true` 才会启用。

该能力记录 Coco 已有的结构化审计事件，不引入用户、角色、组织、租户或业务审计模型。业务项目仍拥有表、迁移、数据源、连接池、事务和保留策略。

## 配置与装配

```yaml
coco:
  audit:
    jdbc:
      enabled: true
      schema: audit
      table-name: coco_audit_event
      batch-size: 100
```

- `enabled` 默认 `false`。
- `table-name` 默认 `coco_audit_event`，`schema` 可选。
- 两个标识符都只能是未引用的单段 SQL 标识符，语法为 `[A-Za-z_][A-Za-z0-9_]*`；空表名、引号、空格、点号、注释和 SQL 分隔符均被拒绝。schema 与表名由框架安全拼成唯一的两段表引用。
- 自动配置只在 Audit 功能有效、`JdbcOperations` 位于 classpath 且业务只提供一个候选（或明确 `@Primary`）时启用。
- 自动配置排序早于默认审计日志记录器。业务提供任意 `CocoAuditRecorder` Bean 时，JDBC 与默认日志记录器均回退，业务可完全替换实现。
- 记录器模块注册自己的中英文消息资源；运行时日志不记录事件正文、属性 JSON 或 SQL 参数。

## 表与数据语义

框架不自动执行 DDL。参考 DDL 位于工件的 `META-INF/coco/audit-jdbc-reference.sql`，业务必须使用自身的 Flyway、Liquibase 或迁移流程创建等价表并按目标数据库方言调整 CLOB/索引类型。

固定列为 `event_type`、`action`、`resource_type`、`resource_id`、`trace_id`、`actor`、`tenant_id`、`success`、`occurred_at_epoch_millis`、`attributes_json`。可选结构化文本字段为空时写入 SQL `NULL`；`attributes_json` 使用稳定排序的 JSON 对象，空属性写入 `{}`。所有值通过 `PreparedStatement` 绑定，表和 schema 之外的 SQL 不接受配置拼接。

## 批量、失败与事务

`record(event)` 始终走 JDBC 参数化批处理。实现公开 `recordBatch(events)`，按 `batch-size` 切分后依次调用 `JdbcOperations.batchUpdate`，用于需要批量写入的业务适配器。

记录器不会创建、提交、挂起或传播事务。调用线程存在 Spring 事务时，所有批次参与该事务；无事务时，每一成功批次按业务数据源的默认提交策略落库，后续批次失败不会回滚已提交批次。需要完整批次原子性的业务必须在自己的事务边界中调用 `recordBatch`。

数据库异常、JSON 序列化异常和写入计数异常不被记录器吞掉，交由已有 `CocoAuditErrorHandler` 与 `coco.audit.failure-policy` 决定忽略还是抛出。实现不使用异步队列或后台线程，因此不会把异步失败伪装为当前调用成功。关闭 Bean 只拒绝后续写入，不关闭业务数据源。

## 非目标

- 自动建表、迁移、删除、归档或分区审计表。
- 管理业务 DataSource、连接池、数据库账号、事务管理器或高可用。
- 绑定 Coco 到任何业务用户、身份、组织或租户模型。
- 承诺跨系统 exactly-once、审计保留合规或消息投递。
- 修改 starter、共享自动配置 composition 或 README。

## 验收

- H2 覆盖自动注入和替换回退、参数化结构化字段和 JSON、批处理、并发、超长与空字段、数据库失败、调用方回滚事务以及关闭后拒绝写入。
- 仅新增 `coco-features/coco-audit-jdbc`、本规格和测试，不修改共享 composition 文件或 README。
- 使用 JDK 21 执行独立模块测试及 SpotBugs、Checkstyle，并在源码变更后同步 CodeGraph（索引存在时）。
