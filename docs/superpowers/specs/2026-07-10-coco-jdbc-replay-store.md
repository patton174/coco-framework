# Coco JDBC 防重放存储规格

## 问题

`coco-feature-web` 当前默认注册 `InMemoryCocoReplayStore`。该实现能在单进程内原子占用防重放键，但不同应用实例之间没有共享状态；集群部署如果没有业务自定义 `CocoReplayStore`，同一请求可以分别在多个实例上通过。

现有启动 WARN 和 SPI 已经避免静默误用，但业务项目仍需从零实现共享存储。审计路线图要求提供 JDBC 参考实现，同时 Coco 不能因此接管业务数据库迁移、事务边界或数据库运维。

## 目标

- 默认行为保持不变，单实例和本地开发继续使用进程内存储。
- 业务项目显式选择 JDBC 后，使用现有 `JdbcOperations` 获得跨实例共享的防重放键占用。
- 使用数据库唯一键保证并发请求中最多一个调用占用成功。
- 过期键可以再次占用，并由后台任务定期清理。
- 防重放键只保存 SHA-256 摘要，不把 appId、keyId、timestamp、nonce、方法或路径原文写入数据库。
- `reserve()` 路径数据库不可用或 SQL 执行失败时向上抛出异常，使受保护请求失败关闭，不绕过防重放校验。
- 保留 `CocoReplayStore` SPI；Redis、专用网关或其他存储仍可通过业务 Bean 完全替换。

## 配置入口

默认配置：

```yaml
coco:
  web:
    replay:
      store-type: in-memory
```

显式启用 JDBC：

```yaml
coco:
  web:
    replay:
      store-type: jdbc
      cleanup-interval-seconds: 60
      jdbc:
        table-name: coco_replay_key
```

- `store-type` 只支持 `in-memory` 和 `jdbc`，默认 `in-memory`。
- `jdbc.table-name` 默认 `coco_replay_key`，只支持未引用的普通表名或单层 schema-qualified 表名，精确语法为 `[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?`。
- JDBC 自动配置只在 replay 已启用且业务项目存在唯一 `JdbcOperations` 候选时生效；多个候选必须由业务标记 `@Primary` 或直接提供自定义 `CocoReplayStore`。
- 框架不自动创建 DataSource，也不猜测多数据源中的业务库。
- 业务项目提供自定义 `CocoReplayStore` Bean 时，所有默认存储实现都回退。
- 显式选择 JDBC、启用 replay 但缺少 `JdbcOperations` 或存储 Bean 时，应用必须启动失败，不能静默退回单机内存。
- replay 已关闭时不要求 JDBC 基础设施存在。

## 表契约

框架不自动执行 DDL。业务项目应通过 Flyway、Liquibase 或现有迁移流程创建等价结构；以下是需要按数据库方言调整的基准示例：

```sql
CREATE TABLE coco_replay_key (
    replay_key_hash VARCHAR(64) NOT NULL,
    expires_at_epoch_millis BIGINT NOT NULL,
    PRIMARY KEY (replay_key_hash)
);

CREATE INDEX idx_coco_replay_key_expires_at
    ON coco_replay_key (expires_at_epoch_millis);
```

固定列语义：

- `replay_key_hash`：`CocoReplayKey.value()` 的小写 SHA-256 十六进制摘要。
- `expires_at_epoch_millis`：服务端计算的绝对过期时间，UTC epoch milliseconds。
- 主键或唯一约束是原子占用契约的一部分，不允许省略。
- 过期时间索引用于后台清理，生产环境应保留。

## 占用语义

1. 先按摘要执行条件更新：仅当现有记录 `expires_at_epoch_millis <= now` 时，把过期时间更新为本次请求的过期时间。
2. 条件更新影响一行时，表示调用方原子接管了已过期记录，返回 `true`。
3. 条件更新没有影响行时尝试插入摘要和过期时间；不存在记录时插入成功并返回 `true`。
4. 插入发生唯一键冲突时，表示另一调用方已经持有有效占用或赢得了并发竞争，返回 `false`。
5. 后台清理与占用竞争时，条件更新和插入仍以数据库单条语句及唯一键决定结果，不使用 check-then-insert。
6. 非唯一键冲突、连接失败、超时和其他数据库异常不转换为“重复请求”，而是原样向上抛出。

自动配置的 Store 由 Servlet 防重放过滤器调用，发生在 Controller 和通常的业务 `@Transactional` 边界之前。框架不创建事务管理器，也不主动开启事务。直接调用 `JdbcCocoReplayStore` 时，`JdbcOperations` 会遵循调用线程已有事务；业务不得把需要独立保留的 replay 占用放进随后会回滚的业务事务。

该契约只保证 replay key 的共享原子占用，不保证业务写入与防重放占用处于同一事务，也不提供业务请求的 exactly-once 语义。

## 清理语义

- JDBC store 在第一次 `reserve()` 后懒启动守护线程。
- 按 `coco.web.replay.cleanup-interval-seconds` 删除已过期记录。
- 多实例同时清理是幂等的，不依赖分布式锁。
- 异步清理失败只记录 WARN，并在后续周期重试；它不会伪装成当前请求的 `reserve()` 失败。
- Spring 销毁 Bean 时关闭清理线程。
- 高吞吐、分区表、归档或专用保留策略属于业务数据库运维范围；业务可替换默认 Store。

## 模块与依赖边界

- 实现位于 `coco-feature-web` 的 replay 子域，不向 starter 添加行为。
- `spring-jdbc` 与 `spring-boot-jdbc` 都以可选依赖接入，只为 JDBC 适配器和类型安全自动配置排序提供编译契约；默认 Web 接入不因此要求 DataSource。
- 测试使用 H2 验证真实唯一约束、并发和清理行为，H2 不进入运行时依赖。
- 不新增独立标准功能标识；JDBC 是 Web replay 的可选存储策略，不是新的业务功能。

## 非目标

- 自动创建或迁移数据库表。
- 管理 DataSource、连接池、数据库账号或数据库高可用。
- 把 replay 占用并入业务事务。
- 提供 Redis、Redisson、MQ 或网关实现。
- 保证请求副作用 exactly-once。
- 为所有数据库方言提供专用 DDL 和性能参数。

## 验收

- 默认自动配置仍创建 `InMemoryCocoReplayStore`。
- `store-type=jdbc`、replay 已启用且存在唯一 `JdbcOperations` 候选时创建 `JdbcCocoReplayStore`。
- 自定义 `CocoReplayStore` 时默认内存和 JDBC 实现都回退。
- 显式 JDBC 配置在 Servlet replay 已启用但缺少唯一 JDBC 候选时启动失败，不回退内存；replay 关闭时不要求 JDBC 基础设施。
- H2 测试覆盖首次占用、有效重复、过期重占用、并发竞争、过期清理、摘要存储和非法表名。
- 配置元数据、中英文 README、框架边界规格和审计路线图与实现一致。
- 聚焦验证和全量 `mvn -B verify` 通过。
- 源码变更后执行 `codegraph sync .`，并保持 `git diff --check` 干净。
