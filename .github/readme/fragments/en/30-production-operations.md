## Production SQL Guard

Coco keeps MyBatis-Plus SQL guard disabled by default so first adoption does not break existing maintenance SQL. For production services, replay or review application SQL first, then enable the guard explicitly:

```yaml
coco:
  mybatis-plus:
    sql-guard:
      block-attack-enabled: true
      illegal-sql-enabled: true
```

When enabled, MyBatis-Plus may reject legitimate SQL that should be rewritten, reviewed, or explicitly ignored only for controlled maintenance statements:

- `UPDATE` or `DELETE` without a selective `WHERE`, or with tautological conditions such as `1 = 1`.
- `SELECT`, `UPDATE`, or `DELETE` without `WHERE` when `IllegalSQLInnerInterceptor` is enabled.
- predicates using `OR`, `!=`, functions on the checked column side, or parser-detected subquery patterns.
- predicates or join conditions whose first checked column is not covered by index metadata.
- complex join, schema-qualified, vendor-specific, or dynamically generated SQL that the JSQLParser-based guard cannot validate reliably.

## Cluster Replay Protection

The default `InMemoryCocoReplayStore` is intentionally process-local. It is suitable for one application instance and local development, but clustered services must use a shared store. When the application already provides Spring `JdbcOperations`, select the built-in JDBC reference implementation explicitly:

```yaml
coco:
  web:
    replay:
      store-type: jdbc
      jdbc:
        table-name: coco_replay_key
```

Coco does not execute database migrations. Create the equivalent structure through the application's existing migration process, adapting this baseline DDL to the selected database:

```sql
CREATE TABLE coco_replay_key (
    replay_key_hash VARCHAR(64) NOT NULL,
    expires_at_epoch_millis BIGINT NOT NULL,
    PRIMARY KEY (replay_key_hash)
);
CREATE INDEX idx_coco_replay_key_expires_at
    ON coco_replay_key (expires_at_epoch_millis);
```

The unique key provides cross-instance atomic reservation, while Coco stores only a SHA-256 digest and cleans expired rows in the background. Reservation-path database failures fail protected requests closed; asynchronous cleanup failures are logged and retried. The Servlet filter reserves before normal Controller transaction boundaries. Schema lifecycle, database availability, clock synchronization, direct-call transaction use, and exactly-once side effects remain application responsibilities. With multiple `JdbcOperations` Beans, mark the intended candidate `@Primary` or provide a custom `CocoReplayStore`, which still replaces both built-in stores.

## Async Logging Backpressure

Coco logging uses a bounded asynchronous queue by default. `ERROR` records and records carrying an exception are always written synchronously; when the queue is full, `WARN` also falls back to synchronous output. Rejected `TRACE`, `DEBUG`, and `INFO` records remain intentionally droppable so queue submission never waits for capacity.

Every actual drop increments an in-process counter, while each non-reentrant drop notifies `CocoAsyncLogDropListener`. The default listener writes a direct SLF4J warning for the first drop and then at power-of-two totals, providing a low-noise overload signal without feeding diagnostics back into the full Coco queue. Applications can replace it with one Bean:

```java
@Bean
CocoAsyncLogDropListener cocoAsyncLogDropListener(MeterRegistry registry) {
    Counter counter = registry.counter("coco.logging.async.dropped");
    return (level, handleName, totalDropped) -> counter.increment();
}
```

The callback receives only the level, log handle name, and cumulative count; message text and exceptions are not exposed. It runs on the submitting thread and must remain fast and avoid blocking. Concurrent callbacks receive unique cumulative values but are not ordered across threads; listener re-entry is suppressed while nested drops are still counted. This mechanism is overload observability, not durable log delivery; applications requiring delivery guarantees should provide their own `CocoLogSink` or audit recorder.
