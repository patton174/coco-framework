---
title: Audit Pipeline
---

# Audit Pipeline

The Coco audit pipeline (`coco-feature-audit`) converges the "audit-worthy actions" produced by the framework and the business into structured audit events, then distributes them to one or more sinks through a unified publisher. Event sources depend only on the publisher and are unaware of the specific sinks, so modules such as Web, Security, and Tenant stay decoupled from the final storage implementation. The module binds the `coco.audit` namespace, is enabled by default, and participates in auto-configuration as a Coco Feature (`CocoFeature.AUDIT`).

## Feature overview

The pipeline is made up of four kinds of roles:

- **`CocoAuditEvent`**: the immutable audit event model. It carries only audit semantics and is not responsible for logging, database writes, or message delivery.
- **`CocoAuditPublisher`**: the audit event publisher, responsible for dispatching events to one or more `CocoAuditRecorder`s. The default implementation `CompositeCocoAuditPublisher` aggregates all recorders.
- **`CocoAuditRecorder`**: the audit recorder SPI. Whether to write to logs, a database, a message queue, or an external audit system is up to the implementer. The default `LoggingCocoAuditRecorder` writes events to a dedicated log handle.
- **`CocoAccessLogAuditRecorder`**: the access log adapter. It converts access log events published by the Web module into audit events, performing only semantic conversion and taking no responsibility for final storage.

### Audit event fields

`CocoAuditEvent` is built via `CocoAuditEvent.builder(type)`, where `type` is required and the rest of the fields are optional:

| Field | Type | Description |
| --- | --- | --- |
| `type` | `String` | Event type, required and must not be empty |
| `action` | `Optional<String>` | Audit action |
| `resourceType` | `Optional<String>` | Resource type |
| `resourceId` | `Optional<String>` | Resource identifier |
| `traceId` | `Optional<String>` | Trace identifier |
| `actor` | `Optional<String>` | Operator identifier |
| `tenantId` | `Optional<String>` | Tenant identifier |
| `success` | `boolean` | Whether the action succeeded, defaults to `true` |
| `occurredAt` | `Instant` | When the event occurred; when unset, `Instant.now()` is used |
| `attributes` | `Map<String, Object>` | Extension attributes; entries with a null key or value are dropped, and the map is immutable after building |

## How to enable and integrate

The module is enabled by default and works without any extra switch. The default log recorder writes audit events to a dedicated log handle; the `logger` name defaults to `io.github.coco.audit` and the level defaults to `INFO`. To publish custom audit events, just inject `CocoAuditPublisher`:

```java
@Service
public class OrderService {

    private final CocoAuditPublisher auditPublisher;

    public OrderService(CocoAuditPublisher auditPublisher) {
        this.auditPublisher = auditPublisher;
    }

    public void cancel(String orderId, String operator) {
        // ... business processing
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

To persist audit events to a database or message queue, just declare your own `CocoAuditRecorder` bean. It will be automatically included in the dispatch chain by `CompositeCocoAuditPublisher`, coexisting with the default log recorder:

```java
@Component
public class JdbcAuditRecorder implements CocoAuditRecorder {

    @Override
    public void record(CocoAuditEvent event) {
        // write to the audit table / deliver to a message queue
    }
}
```

## Usage example

The access log audit adapter is enabled by default (`coco.audit.access-log.enabled=true`) and requires a `CocoAuditPublisher` to be present in the container. Once enabled, every access log line from the Web module is converted into an audit event with `type=access-log`: `action` takes the HTTP method, `resourceType` is `http-request`, `resourceId` takes the request path, `success` reuses the access log's success flag, and the status code, latency, client IP, User-Agent, content type, exception type, browser fingerprint, and so on are written into `attributes`.

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

## Failure policy

How a recorder throwing an exception is handled is decided by `CocoAuditFailurePolicy`, configured via `coco.audit.failure-policy`:

| Value | Description |
| --- | --- |
| `IGNORE` (default) | Ignore a single recorder failure and continue dispatching to subsequent recorders |
| `THROW` | Throw the recorder failure exception immediately |

The default `IGNORE` guarantees that an audit persistence failure does not affect the main business flow; for scenarios with strong requirements on audit completeness you can switch to `THROW`, but the business side needs to evaluate the impact of a failure on the main flow.

## Key configuration properties

Bound under the prefix `coco.audit` (corresponding to `CocoAuditProperties`):

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.audit.enabled` | `boolean` | `true` | Whether to enable the audit infrastructure |
| `coco.audit.failure-policy` | `CocoAuditFailurePolicy` | `IGNORE` | Recorder failure policy: `IGNORE` / `THROW` |
| `coco.audit.logging.enabled` | `boolean` | `true` | Whether to enable the default audit log recorder |
| `coco.audit.logging.logger-name` | `String` | `io.github.coco.audit` | The audit log logger name; falls back to the default when empty |
| `coco.audit.logging.level` | `CocoLogLevel` | `INFO` | The audit log output level |
| `coco.audit.access-log.enabled` | `boolean` | `true` | Whether to convert Web access logs into audit events |

## Boundaries and caveats

- The default audit log recorder (`LoggingCocoAuditRecorder`) depends on a `CocoLogManager` being present in the container; if the logging infrastructure is not assembled, the default recorder is not created, and if there is also no business-custom `CocoAuditRecorder`, the publisher is likewise not created.
- The access log adapter depends on the Web access log capability being enabled and publishing `CocoAccessLog`; when Web access logging is not enabled, no `access-log` audit event is produced.
- Entries in `attributes` with a `null` key or value are silently dropped, and an empty `type` throws `IllegalArgumentException`, so take care when building audit events.
