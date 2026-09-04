---
title: 消息与事件
---

# 消息与事件

Coco 消息（`coco-messaging`）提供进程内的发布/订阅边界：业务用 `CocoMessagePublisher` 发消息，用 `@CocoMessageListener` 声明订阅，投递方式、失败处理和关闭行为都由配置决定。

它绑定 `coco.messaging` 命名空间，**默认关闭**，需显式打开 `coco.messaging.enabled=true`。

:::tip[明确的能力边界]
这个模块是**本地事件总线**加一层可替换的传输 SPI，不是 MQ 集成。持久化、跨进程投递、重试、死信队列、事务消息和 exactly-once **都不在范围内** —— 需要这些能力时，实现 `CocoMessageTransport` 接入 RabbitMQ / Kafka / RocketMQ，业务代码不用改。
:::

## 功能简介

- **发布/订阅**：`CocoMessagePublisher.publish(topic, payload)` 发布，`@CocoMessageListener(topic = "...")` 订阅，支持 `order` 控制同一主题内的处理顺序。
- **同步或异步投递**：`delivery-mode` 决定在调用线程处理还是交给有界队列异步处理。
- **失败策略**：订阅者抛异常时，可选择快速失败或记录后继续。
- **无订阅者策略**：发到没人订阅的主题时，可选择忽略或报错——后者能及早暴露主题拼写错误。
- **关闭策略**：应用关闭时，异步队列里的消息可选择排空或丢弃。
- **可替换传输**：`CocoMessageTransport` 是 SPI，默认 `LocalCocoMessageTransport` 仅进程内有效；提供自定义 Bean 即可换成外部消息中间件。

## 如何启用接入

### 1. 打开开关

```yaml
coco:
  messaging:
    enabled: true
    delivery-mode: sync              # sync | async，默认 sync
    failure-policy: fail-fast        # fail-fast | log-and-continue
    no-subscriber-policy: ignore     # ignore | fail
    async:
      queue-capacity: 1024
      shutdown-await: 5s
      shutdown-policy: drain         # drain | discard
```

### 2. 发布消息

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

### 3. 订阅消息

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

`order` 小的先执行。同步模式下，监听方法在发布者线程内运行，因此会落在发布方的事务边界内。

## 关键配置项

前缀 `coco.messaging`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用消息模块 |
| `delivery-mode` | enum | `sync` | `sync` 在调用线程处理；`async` 投入有界队列 |
| `failure-policy` | enum | `fail-fast` | `fail-fast` 向发布者抛出；`log-and-continue` 记录后继续通知其余订阅者 |
| `no-subscriber-policy` | enum | `ignore` | 主题无订阅者时忽略或报错 |
| `async.queue-capacity` | int | `1024` | 异步队列容量 |
| `async.shutdown-await` | Duration | `5s` | 关闭时等待队列处理的时长 |
| `async.shutdown-policy` | enum | `drain` | 超时后排空剩余消息或直接丢弃 |

## 投递模式的取舍

| | `sync` | `async` |
|---|---|---|
| 执行线程 | 发布者线程 | 队列工作线程 |
| 事务 | 落在发布者事务内 | 脱离发布者事务 |
| 发布者感知失败 | 能（`fail-fast` 时抛出） | 不能 |
| 队列满时 | 不涉及 | 按背压策略处理 |

同步模式下监听器在发布者的事务里执行，意味着监听器失败会回滚业务事务 —— 这既可能是你想要的（强一致），也可能不是（通知失败不该回滚订单）。需要后者时用异步模式，或在监听器内自行捕获异常。

## 接入外部消息中间件

实现 `CocoMessageTransport` 并注册为 Bean，即可替换默认的进程内传输，业务侧的发布和订阅代码不用改：

```java
@Bean
public CocoMessageTransport cocoMessageTransport(RabbitTemplate template) {
    return new RabbitCocoMessageTransport(template);
}
```

SPI 只描述发布与订阅两个动作。持久化、重试、死信队列、顺序保证和 exactly-once 由你的实现决定 —— 框架不假装提供这些保证。

## 边界注意事项

- **仅进程内**：默认实现的订阅关系只在当前 JVM 有效，多实例部署下各实例互不感知。跨实例投递需要自定义传输实现。
- **不持久化**：进程重启后队列内容丢失。需要投递保证时不要依赖默认实现。
- **异步模式脱离事务**：异步投递发生在发布者事务提交之后没有保证，因此不能用它实现"事务内可靠投递"。需要最终一致性时，配合本地消息表等模式，由你的传输实现负责。
- **无内建重试**：`failure-policy` 只决定失败后是否继续通知其余订阅者，不会重投。
