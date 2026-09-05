---
title: Captcha
---

# Captcha

Coco captcha (`coco-captcha`) provides a type-agnostic generate/verify SPI: image (IMAGE), slider (SLIDER), and SMS code (SMS_CODE). The framework ships local reference implementations (JDK-native AWT image rendering, a numeric slider, a numeric SMS code) plus an in-process answer store; production can register custom generators or a shared store. Off by default — enable with `coco.captcha.enabled=true`.

## Overview

- **Three captcha types**: `IMAGE` returns a base64 PNG, `SLIDER` returns a track width verified by offset tolerance, `SMS_CODE` generates a numeric code (delivered by the application via `coco-notification`; this module does not send).
- **Answer never leaks**: `CocoCaptcha` splits into a client-facing challenge and a server-only answer; the response returns only `ClientView` (no answer field).
- **Single-use verification**: `CocoCaptchaStore.consume` removes on read — a `captchaId` verifies at most once regardless of match result, preventing answer replay and single-code brute force.
- **Pluggable store**: the default `InMemoryCocoCaptchaStore` fits a single instance only; switch `store-type` to `redis` for the built-in atomic Lua implementation, or supply your own bean to back it with different shared storage.
- **Business generator wins**: register a custom `CocoCaptchaGenerator` to override the reference implementation for its type.

## How to Enable

### 1. Turn it on

```yaml
coco:
  captcha:
    enabled: true
    ttl: 2m                # captcha time-to-live
    image-enabled: true
    slider-enabled: true
    sms-code-enabled: true
    image-length: 4        # image captcha character count
    sms-code-length: 6     # SMS code digit count
    slider-tolerance: 5    # slider offset tolerance (pixels)
```

### 2. Generate and verify

```java
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final CocoCaptchaService captchaService;

    public CaptchaController(CocoCaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/image")
    public CocoCaptcha.ClientView image() {
        // Returns {captchaId, type, challenge}; challenge is data:image/png;base64,...
        return captchaService.generate(CocoCaptchaType.IMAGE);
    }

    @PostMapping("/verify")
    public boolean verify(@RequestParam String captchaId, @RequestParam String code) {
        return captchaService.verify(CocoCaptchaType.IMAGE, captchaId, code);
    }
}
```

### 3. Switch to the Redis store for clustered deployments

`InMemoryCocoCaptchaStore` keeps answers in the current JVM only. Across instances a captcha generated on A cannot be verified on B, so verification always fails — and it fails as "wrong captcha", which makes it easy to misdiagnose.

For a cluster, declare `store-type`:

```yaml
coco:
  captcha:
    enabled: true
    store-type: redis             # default is in-memory
    redis:
      key-prefix: "coco:captcha:"  # optional
```

Then add Spring Data Redis:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`RedisCocoCaptchaStore` folds `GET` + `DEL` into one Lua script so the single-use guarantee holds under concurrency — two round trips would let two concurrent requests read the same answer. Keys are SHA-256 digests of the `captchaId`, so the raw identifier never enters the Redis keyspace.

:::tip[Fails loudly when the dependency is missing]
If `store-type: redis` is set but Spring Data Redis is absent from the classpath, startup fails with the reason and **does not silently fall back to the in-process store**. A silent fallback would make captchas fail at random behind a load balancer while everything looked healthy.
:::

**With multiple `StringRedisTemplate` beans**, name one via `coco.captcha.redis.template-bean-name` or mark the target `@Primary`; otherwise startup fails and lists the candidates rather than picking one arbitrarily.

When Redis is unavailable, `consume` throws rather than degrading to `null`. `null` already means "expired or absent" and renders to the user as a wrong captcha, so reusing it for an outage would report a correct answer as wrong and hide the outage from error rates.

### 4. Custom generator or store

```java
@Bean
CocoCaptchaGenerator myImageGenerator() {
    return new MyImageGenerator();   // supportedType() returns IMAGE, overriding the reference
}

@Bean
CocoCaptchaStore myCaptchaStore() {
    return new MyOwnCaptchaStore(/* ... */);   // applies under either store-type
}
```

## Boundaries

- **Does not deliver**: SMS codes are sent by the application via `coco-notification` or its own channel; this module only generates and verifies.
- **Reference image is weak**: `ImageCocoCaptchaGenerator` has only basic interference lines and is not OCR-resistant; register a custom generator for high-security scenarios.
- **Slider has no puzzle image**: the reference implementation only does offset-tolerance verification; puzzle-gap rendering is left to a business generator.
- **Default store is not shared**: multi-instance deployments must switch to `store-type: redis` or supply their own shared-storage implementation.
