---
title: 通知
---

# 通知

Coco 通知（`coco-notification`）提供渠道无关的通知发送 SPI：短信（SMS）、邮件（EMAIL）、站内信（IN_APP）。框架只带本地参考实现（日志渠道、进程内站内信），对接阿里云/腾讯云短信、SMTP 邮件等**由业务自行引入 SDK** 后实现 `CocoNotificationChannel` 并注册为 Bean——与对象存储一致的纯 SPI 策略，框架不绑定任何云厂商依赖。

它绑定 `coco.notification` 命名空间，**默认关闭**，需显式打开 `coco.notification.enabled=true`。

## 功能简介

- **按类型路由**：`CocoNotificationService` 把通知按 `channelType` 路由到对应渠道；发到未注册类型时回失败结果而非抛异常。
- **业务渠道优先**：业务注册的 `CocoNotificationChannel` 优先；参考实现只补齐业务未覆盖的类型，不会覆盖它们。
- **发送不抛异常**：单条发送失败回 `success=false` 的 `CocoNotificationResult`，便于多渠道并发时按渠道聚合成败，不被首个异常打断。
- **纯 SPI**：框架不引入任何云厂商 SDK；真实渠道由业务实现接口后注册。

## 如何启用接入

### 1. 打开开关

```yaml
coco:
  notification:
    enabled: true
    logging-fallback: true    # 为未实现的 SMS/EMAIL 注册日志参考渠道（开发用）
    in-memory-in-app: true    # 注册进程内站内信参考渠道
```

### 2. 发送通知

```java
@Service
public class SignupService {

    private final CocoNotificationService notifications;

    public SignupService(CocoNotificationService notifications) {
        this.notifications = notifications;
    }

    public void welcome(String phone) {
        CocoNotificationResult result = notifications.send(
                CocoNotification.of(CocoNotificationChannelType.SMS, phone, "欢迎注册"));
        if (!result.success()) {
            // 按 result.detail() 处理失败
        }
    }
}
```

### 3. 接入真实渠道（业务侧引入 SDK）

```java
@Component
public class AliyunSmsChannel implements CocoNotificationChannel {

    @Override
    public CocoNotificationChannelType supportedType() {
        return CocoNotificationChannelType.SMS;
    }

    @Override
    public CocoNotificationResult send(CocoNotification notification) {
        try {
            String bizId = aliyunClient.send(notification.recipient(),
                    notification.attributes().get("templateCode"), notification.content());
            return CocoNotificationResult.success(CocoNotificationChannelType.SMS, bizId);
        }
        catch (RuntimeException exception) {
            return CocoNotificationResult.failure(CocoNotificationChannelType.SMS, exception.getMessage());
        }
    }
}
```

注册后，`AliyunSmsChannel` 自动接管 SMS 类型，日志参考渠道不再对 SMS 生效。

:::tip[参考实现的边界]
日志渠道只记日志、不真正外发，生产务必用真实渠道替换（可关 `logging-fallback` 以免误以为发出去了）。进程内站内信真能用，但状态不跨实例、不持久化，多实例生产应换成落库/落 Redis 的实现。
:::

## 关键配置项

前缀 `coco.notification`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用通知。 |
| `logging-fallback` | boolean | `true` | 为业务未覆盖的 SMS/EMAIL 注册日志参考渠道。 |
| `in-memory-in-app` | boolean | `true` | 注册进程内站内信参考渠道。 |

## 边界注意事项

- **纯 SPI，不带云 SDK**：框架不依赖任何短信/邮件厂商。真实发送由业务实现 `CocoNotificationChannel`。
- **参考实现仅供开发**：日志渠道不外发；进程内站内信不跨实例、不持久化。
- **发送失败不抛异常**：以 `CocoNotificationResult.success()` 判定，调用方需自行处理失败分支。
- **每类型一个渠道**：同一 `channelType` 注册多个渠道时后者覆盖前者并告警。
