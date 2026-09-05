---
title: Messaging and Events
---

# Messaging and Events

Coco messaging (`coco-messaging`) provides an in-process publish/subscribe boundary: publish with `CocoMessagePublisher`, subscribe by declaring `@CocoMessageListener`, and let configuration decide delivery mode, failure handling, and shutdown behaviour.

It binds the `coco.messaging` namespace and is **disabled by default** — set `coco.messaging.enabled=true` explicitly.

:::tip[An explicit scope]
This module is a **local event bus** plus a replaceable transport SPI, not an MQ integration. Persistence, cross-process delivery, retries, dead-letter queues, transactional messaging, and exactly-once are **all out of scope** — when you need them, implement `CocoMessageTransport` against RabbitMQ / Kafka / RocketMQ without touching business code.
:::

## What it does

- **Publish/subscribe**: `CocoMessagePublisher.publish(topic, payload)` publishes; `@CocoMessageListener(topic = "...")` subscribes, with `order` controlling sequence within a topic.
- **Sync or async delivery**: `delivery-mode` decides whether handlers run on the calling thread or via a bounded queue.
- **Failure policy**: when a subscriber throws, either fail fast or log and continue.
- **No-subscriber policy**: publishing to a topic nobody listens on can be ignored or raised as an error — the latter surfaces typos early.
- **Shutdown policy**: on application shutdown, queued async messages can be drained or discarded.
- **Replaceable transport**: `CocoMessageTransport` is an SPI. The default `LocalCocoMessageTransport` is process-local; supply your own Bean to route through external middleware.

## How to enable

### 1. Turn it on

```yaml
coco:
  messaging:
    enabled: true
    delivery-mode: sync              # sync | async, default sync
    failure-policy: fail-fast        # fail-fast | log-and-continue
    no-subscriber-policy: ignore     # ignore | fail
    async:
      queue-capacity: 1024
      shutdown-await: 5s
      shutdown-policy: drain         # drain | discard
```

### 2. Publish

```java
@Service
class OrderService {

    private final CocoMessagePublisher publisher;

    OrderService(CocoMessagePublisher publisher) {
        this.publisher = publisher;
    }

    void create(CreateOrderRequest request) {
        Order order = this.orderRepository.save(request.toOrder());
        this.publisher.publish("order.created", order.id());
    }
}
```

### 3. Subscribe

```java
@Component
class OrderCreatedListeners {

    @CocoMessageListener(topic = "order.created")
    void sendConfirmation(Long orderId) {
        this.notificationService.notifyCustomer(orderId);
    }

    @CocoMessageListener(topic = "order.created", order = 10)
    void updateStatistics(Long orderId) {
        this.statisticsService.increment(orderId);
    }
}
```

Lower `order` runs first. In sync mode the handler runs on the publisher's thread, so it falls inside the publisher's transaction boundary.

## Key configuration

Prefix `coco.messaging`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable the messaging module |
| `delivery-mode` | enum | `sync` | `sync` handles on the calling thread; `async` queues the message |
| `failure-policy` | enum | `fail-fast` | `fail-fast` rethrows to the publisher; `log-and-continue` logs and notifies remaining subscribers |
| `no-subscriber-policy` | enum | `ignore` | Ignore or raise when a topic has no subscribers |
| `async.queue-capacity` | int | `1024` | Async queue capacity |
| `async.shutdown-await` | Duration | `5s` | How long shutdown waits for the queue |
| `async.shutdown-policy` | enum | `drain` | Drain remaining messages after the wait, or discard them |

## Choosing a delivery mode

| | `sync` | `async` |
|---|---|---|
| Handler thread | Publisher's thread | Queue worker thread |
| Transaction | Inside the publisher's transaction | Outside it |
| Publisher sees failures | Yes (with `fail-fast`) | No |
| When the queue is full | N/A | Backpressure policy applies |

In sync mode handlers run inside the publisher's transaction, which means a failing handler rolls back the business transaction. That may be what you want (strong consistency) or not (a failed notification should not roll back the order). For the latter, use async mode or catch exceptions inside the handler.

## Routing through external middleware

Implement `CocoMessageTransport` and register it as a Bean to replace the process-local transport. Publishing and subscribing code stays unchanged:

```java
@Bean
public CocoMessageTransport cocoMessageTransport(RabbitTemplate template) {
    return new RabbitCocoMessageTransport(template);
}
```

The SPI describes only publish and subscribe. Persistence, retries, dead-letter queues, ordering, and exactly-once are decisions your implementation makes — the framework does not pretend to provide those guarantees.

## Boundaries

- **In-process only**: the default transport's subscriptions exist in the current JVM, so instances don't see each other's messages. Cross-instance delivery needs a custom transport.
- **No persistence**: queue contents are lost on restart. Do not rely on the default transport when you need delivery guarantees.
- **Async escapes the transaction**: async delivery is not guaranteed to happen after the publisher's transaction commits, so it cannot implement "reliable delivery inside a transaction". For eventual consistency, combine a pattern such as a local message table with your own transport.
- **No built-in retry**: `failure-policy` only decides whether remaining subscribers are still notified; it never redelivers.
