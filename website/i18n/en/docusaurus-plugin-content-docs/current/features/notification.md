---
title: Notification
---

# Notification

Coco notification (`coco-notification`) provides a channel-agnostic sending SPI: SMS, email, and in-app. The framework ships only local reference implementations (a logging channel, an in-memory in-app inbox); integrating Aliyun/Tencent SMS, SMTP email, and so on is done by **the application bringing its own SDK**, implementing `CocoNotificationChannel`, and registering it as a bean — the same pure-SPI strategy as object storage, so the framework binds no cloud-vendor dependency.

It binds the `coco.notification` namespace and is **disabled by default**; turn it on with `coco.notification.enabled=true`.

## Overview

- **Type-based routing**: `CocoNotificationService` routes a notification to the channel registered for its `channelType`; an unregistered type returns a failure result rather than throwing.
- **Business channels win**: a business-registered `CocoNotificationChannel` takes precedence; reference implementations only fill types the business has not covered, never overriding them.
- **Sending never throws**: a failed send returns a `success=false` `CocoNotificationResult`, so multi-channel fan-out can aggregate outcomes per channel instead of being interrupted by the first exception.
- **Pure SPI**: no cloud-vendor SDK is pulled in; real channels are implemented by the application.

## How to Enable

### 1. Turn on the switch

```yaml
coco:
  notification:
    enabled: true
    logging-fallback: true    # register a logging reference channel for unimplemented SMS/EMAIL (dev)
    in-memory-in-app: true    # register the in-process in-app reference channel
```

### 2. Send a notification

```java
@Service
public class SignupService {

    private final CocoNotificationService notifications;

    public SignupService(CocoNotificationService notifications) {
        this.notifications = notifications;
    }

    public void welcome(String phone) {
        CocoNotificationResult result = notifications.send(
                CocoNotification.of(CocoNotificationChannelType.SMS, phone, "Welcome"));
        if (!result.success()) {
            // handle failure via result.detail()
        }
    }
}
```

### 3. Wire a real channel (the application brings the SDK)

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

Once registered, `AliyunSmsChannel` takes over the SMS type and the logging reference channel no longer applies to SMS.

:::tip[Reference-implementation boundaries]
The logging channel only logs — it does not actually send, so production must replace it with a real channel (disable `logging-fallback` to avoid the false impression that messages went out). The in-memory in-app inbox does work, but its state is not shared across instances and is not persisted; a multi-instance production deployment should replace it with a store-backed or Redis-backed implementation.
:::

## Key Configuration

Prefix `coco.notification`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether notifications are enabled. |
| `logging-fallback` | boolean | `true` | Register a logging reference channel for SMS/EMAIL types the business has not covered. |
| `in-memory-in-app` | boolean | `true` | Register the in-process in-app reference channel. |

## Boundary Notes

- **Pure SPI, no cloud SDK**: the framework depends on no SMS/email vendor. Real delivery is implemented by the application via `CocoNotificationChannel`.
- **Reference implementations are for development**: the logging channel does not send; the in-memory in-app inbox is neither cross-instance nor persistent.
- **Sending does not throw**: decide on `CocoNotificationResult.success()`; callers must handle the failure branch themselves.
- **One channel per type**: registering multiple channels for the same `channelType` lets the last one win, with a warning.
