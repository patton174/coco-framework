---
title: 验证码
---

# 验证码

Coco 验证码（`coco-captcha`）提供与类型无关的验证码生成/校验 SPI：图形（IMAGE）、滑块（SLIDER）、短信码（SMS_CODE）。框架自带本地参考实现（JDK 内置 AWT 渲染图形、数字滑块、数字短信码）与进程内答案存储；生产可注册自定义生成器或共享存储替换。默认关闭，`coco.captcha.enabled=true` 开启。

## 功能简介

- **三种验证码类型**：`IMAGE` 下发 base64 PNG，`SLIDER` 下发轨道宽度按偏移容差校验，`SMS_CODE` 生成数字码（由业务经 `coco-notification` 下发，本模块不发送）。
- **答案不外泄**：`CocoCaptcha` 拆成客户端挑战与服务端答案两部分，响应只回 `ClientView`（无答案字段）。
- **单次核验**：`CocoCaptchaStore.consume` 取出即删——无论比对成败，一个 `captchaId` 只能核验一次，防止答案重放与单码爆破。
- **可替换存储**：默认 `InMemoryCocoCaptchaStore` 仅适合单实例；多实例应注册基于 Redis 等共享存储的 `CocoCaptchaStore`。
- **业务生成器优先**：注册自定义 `CocoCaptchaGenerator` 覆盖同类型参考实现。

## 如何启用接入

### 1. 打开开关

```yaml
coco:
  captcha:
    enabled: true
    ttl: 2m                # 验证码存活时间
    image-enabled: true
    slider-enabled: true
    sms-code-enabled: true
    image-length: 4        # 图形验证码字符数
    sms-code-length: 6     # 短信码位数
    slider-tolerance: 5    # 滑块偏移容差（像素）
```

### 2. 生成与校验

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
        // 返回 {captchaId, type, challenge}，challenge 是 data:image/png;base64,...
        return captchaService.generate(CocoCaptchaType.IMAGE);
    }

    @PostMapping("/verify")
    public boolean verify(@RequestParam String captchaId, @RequestParam String code) {
        return captchaService.verify(CocoCaptchaType.IMAGE, captchaId, code);
    }
}
```

### 3. 自定义生成器或存储

```java
@Bean
CocoCaptchaGenerator myImageGenerator() {
    return new MyImageGenerator();   // supportedType() 返回 IMAGE，覆盖参考实现
}

@Bean
CocoCaptchaStore redisCaptchaStore(StringRedisTemplate template) {
    return new RedisCocoCaptchaStore(template);   // 多实例共享
}
```

## 边界

- **不负责下发**：短信码由业务经 `coco-notification` 或自有渠道发送，本模块只生成与核验。
- **参考图形实现较弱**：`ImageCocoCaptchaGenerator` 只有基础干扰线，不抗 OCR；高安全场景请注册自定义生成器。
- **滑块不含拼图图像**：参考实现只做偏移容差校验，缺口拼图由业务生成器自行实现。
- **进程内存储不跨实例**：多实例部署务必替换 `CocoCaptchaStore`。
