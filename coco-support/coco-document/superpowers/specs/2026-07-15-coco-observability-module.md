# Coco Optional Observability Module

## Scope

`coco-features/coco-observability` is an independent optional adapter. It is intentionally not added to the shared
reactor, BOM, starter, standard feature metadata, or README. Applications opt in by declaring the module directly.

The module compiles against Micrometer, Actuator, audit, Web replay, logging, and feature-model APIs as optional
dependencies. Its auto-configuration activates only when `MeterRegistry` is on the classpath and
`coco.observability.enabled` is not false. An application without Micrometer receives no auto-configured beans,
metrics, endpoint types, or runtime probes from this module.

## Signals

The metric names and tags are fixed:

| Signal | Metric | Tags |
| --- | --- | --- |
| Audit event business result | `coco.audit.events` | `outcome=success|failure` |
| Replay adapter result | `coco.replay.reservations` | `outcome=accepted|duplicate|capacity_exceeded|error` |
| Rate-limit adapter result | `coco.rate_limit.decisions` | `outcome=allowed|rejected` |
| Confirmed async log drop | `coco.logging.dropped` | `outcome=dropped` |

No metric carries tenant ID, user or actor ID, credential/key material, nonce, replay key, request path, resource ID,
exception text, log handle, log body, or arbitrary audit attributes. The default tag set is one bounded enum value.

Audit uses the existing `CocoAuditRecorder` composition point. The observability recorder is ordered after Coco audit
auto-configuration so the standard audit recorder remains available; the composite publisher sees both recorders.
Async logging uses the existing `CocoAsyncLogDropListener` hook and preserves the normal SLF4J overflow diagnostic.

There is no replay result hook and no rate-limiter event source in the baseline. The module therefore provides
`CocoReplayObservation` and `CocoRateLimitObservation` small adapter SPIs. A replay store/filter or rate limiter must
explicitly call these SPI beans with a fixed outcome. The module does not infer a reservation or decision and does not
manufacture measurements when the producer has not reported an event.

## Actuator Status

When Actuator types are available, the `cocoobservability` read endpoint and the info contributor receive a safe summary
containing only startup state and feature-plan availability plus enabled/disabled counts. It does not list request context
or identifiers. Spring Boot 4.1 no longer exposes the former `HealthIndicator` API from `spring-boot-actuator`, so this
module uses an Actuator `@Endpoint` instead of pretending to contribute to the standard health group. Applications can
replace the status source through `CocoObservabilityStatusProvider`, feature-plan source through
`CocoFeaturePlanStatusContributor`, or either endpoint by declaring the corresponding bean name:

- `cocoObservabilityHealthEndpoint`
- `cocoObservabilityInfoContributor`

## Configuration And Replacement

- `coco.observability.enabled=false` disables every adapter.
- `coco.observability.metrics.enabled=false` disables all metric binders.
- `coco.observability.metrics.audit-enabled`, `replay-enabled`, `rate-limit-enabled`, and `log-overflow-enabled`
  control each binder independently.
- `coco.observability.health.enabled=false` and `coco.observability.info.enabled=false` disable their endpoints.
- A user supplied `CocoObservationRecorder`, observation SPI, `CocoAsyncLogDropListener`, status provider, or named
  endpoint bean replaces the module default for that contract.

The module does not add automatic HTTP endpoint exposure or management security rules. Operators continue to control
Actuator endpoint exposure and access using Spring Boot configuration.
