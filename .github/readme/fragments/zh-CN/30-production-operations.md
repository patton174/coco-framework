## 生产 SQL 防护

Coco 默认不启用 MyBatis-Plus SQL 防护，避免首次接入时破坏已有维护 SQL。生产服务建议先回放或审查业务 SQL，再显式启用：

```yaml
coco:
  mybatis-plus:
    sql-guard:
      block-attack-enabled: true
      illegal-sql-enabled: true
```

启用后，MyBatis-Plus 可能拦截一些业务上合法但需要改写、复核或仅对受控维护语句显式忽略的 SQL：

- 没有有效 `WHERE` 的 `UPDATE` / `DELETE`，或包含 `1 = 1` 等恒真条件的批量写语句。
- 启用 `IllegalSQLInnerInterceptor` 后，没有 `WHERE` 的 `SELECT` / `UPDATE` / `DELETE`。
- `WHERE` 中使用 `OR`、`!=`、被检查列一侧使用函数，或被解析器识别为子查询形态。
- 谓词或 JOIN 条件中首个被检查字段没有命中索引元数据。
- JSQLParser 防护器无法稳定验证的复杂 JOIN、带 schema 的表名、数据库方言 SQL 或动态生成 SQL。

## 集群防重放

默认 `InMemoryCocoReplayStore` 明确是进程内实现，适合单实例和本地开发；集群服务必须使用共享存储。业务应用已经提供 Spring `JdbcOperations` 时，可以显式启用内置 JDBC 参考实现：

```yaml
coco:
  web:
    replay:
      store-type: jdbc
      jdbc:
        table-name: coco_replay_key
```

Coco 不自动执行数据库迁移。请通过业务项目已有的迁移流程创建等价结构，并按目标数据库方言调整以下基准 DDL：

```sql
CREATE TABLE coco_replay_key (
    replay_key_hash VARCHAR(64) NOT NULL,
    expires_at_epoch_millis BIGINT NOT NULL,
    PRIMARY KEY (replay_key_hash)
);
CREATE INDEX idx_coco_replay_key_expires_at
    ON coco_replay_key (expires_at_epoch_millis);
```

唯一键负责跨实例原子占用，Coco 只保存 SHA-256 摘要，并在后台清理过期记录。占用路径的数据库异常会让受保护请求失败关闭；异步清理失败只记录并重试。Servlet Filter 会在通常的 Controller 事务边界之前占用 key。表结构迁移、数据库可用性、集群时钟同步、直接调用时的事务使用和 exactly-once 副作用仍由业务应用负责。存在多个 `JdbcOperations` Bean 时，应给目标候选标记 `@Primary`，或提供自定义 `CocoReplayStore` 替换两种内置实现。

## 异步日志背压

Coco 日志默认使用有界异步队列。`ERROR` 和携带异常的记录始终同步写出；队列满时，`WARN` 也会回退为同步输出。被拒绝的 `TRACE`、`DEBUG` 和 `INFO` 仍允许丢弃，使队列提交不会等待可用容量。

每条实际丢弃都会增加进程内累计计数，每条非重入丢弃会通知 `CocoAsyncLogDropListener`。默认监听器直接通过 SLF4J 在首次丢弃以及累计数达到 2 的幂次时输出 WARN，既提供低噪声过载信号，也不会把诊断再次送入已满的 Coco 队列。业务可以用一个 Bean 替换：

```java
@Bean
CocoAsyncLogDropListener cocoAsyncLogDropListener(MeterRegistry registry) {
    Counter counter = registry.counter("coco.logging.async.dropped");
    return (level, handleName, totalDropped) -> counter.increment();
}
```

回调只接收级别、日志句柄名称和累计数，不暴露日志正文或异常。它在提交线程执行，业务实现必须快速并避免主动阻塞；并发回调会获得唯一累计值，但不保证跨线程顺序，监听器重入会被抑制且嵌套丢弃仍会计数。该机制只提供过载可观测性，不保证日志可靠交付；需要强交付保证的业务应替换 `CocoLogSink` 或审计记录器。
