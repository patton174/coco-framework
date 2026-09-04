---
title: Captcha
---

# Captcha

Coco captcha (`coco-captcha`) provides a type-agnostic generate/verify SPI: image (IMAGE), slider (SLIDER), and SMS code (SMS_CODE). The framework ships local reference implementations (JDK-native AWT image rendering, a numeric slider, a numeric SMS code) plus an in-process answer store; production can register custom generators or a shared store. Off by default — enable with `coco.captcha.enabled=true`.

## Overview

- **Three captcha types**: `IMAGE` returns a base64 PNG, `SLIDER` returns a track width verified by offset tolerance, `SMS_CODE` generates a numeric code (delivered by the application via `coco-notification`; this module does not send).
- **Answer never leaks**: `CocoCaptcha` splits into a client-facing challenge and a server-only answer; the response returns only `ClientView` (no answer field).
- **Single-use verification**: `CocoCaptchaStore.consume` removes on read — a `captchaId` verifies at most once regardless of match result, preventing answer replay and single-code brute force.
- **Pluggable store**: the default `InMemoryCocoCaptchaStore` fits a single instance only; a multi-instance deployment should register a `CocoCaptchaStore` backed by shared storage such as Redis.
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

### 3. Custom generator or store

```java
@Bean
CocoCaptchaGenerator myImageGenerator() {
    return new MyImageGenerator();   // supportedType() returns IMAGE, overriding the reference
}

@Bean
CocoCaptchaStore redisCaptchaStore(StringRedisTemplate template) {
    return new RedisCocoCaptchaStore(template);   // shared across instances
}
```

## Boundaries

- **Does not deliver**: SMS codes are sent by the application via `coco-notification` or its own channel; this module only generates and verifies.
- **Reference image is weak**: `ImageCocoCaptchaGenerator` has only basic interference lines and is not OCR-resistant; register a custom generator for high-security scenarios.
- **Slider has no puzzle image**: the reference implementation only does offset-tolerance verification; puzzle-gap rendering is left to a business generator.
- **In-process store is not shared**: replace `CocoCaptchaStore` for multi-instance deployments.
