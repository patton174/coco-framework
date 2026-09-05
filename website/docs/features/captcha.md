---
title: 验证码
---

# 验证码

Coco 验证码（`coco-captcha`）提供与类型无关的验证码生成/校验 SPI：图形（IMAGE）、滑块（SLIDER）、短信码（SMS_CODE）。框架自带本地参考实现（JDK 内置 AWT 渲染图形、数字滑块、数字短信码）与进程内答案存储；生产可注册自定义生成器或共享存储替换。默认关闭，`coco.captcha.enabled=true` 开启。

## 功能简介

- **三种验证码类型**：`IMAGE` 下发 base64 PNG，`SLIDER` 下发轨道宽度按偏移容差校验，`SMS_CODE` 生成数字码（由业务经 `coco-notification` 下发，本模块不发送）。
- **答案不外泄**：`CocoCaptcha` 拆成客户端挑战与服务端答案两部分，响应只回 `ClientView`（无答案字段）。
- **单次核验**：`CocoCaptchaStore.consume` 取出即删——无论比对成败，一个 `captchaId` 只能核验一次，防止答案重放与单码爆破。
- **可替换存储**：默认 `InMemoryCocoCaptchaStore` 仅适合单实例；多实例把 `store-type` 切到 `redis` 即用内置的 Lua 原子实现，也可提供自定义 Bean 接入其它共享存储。
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

### 3. 集群部署切换到 Redis 存储

`InMemoryCocoCaptchaStore` 的答案只存在于当前 JVM。多实例部署下验证码在 A 实例生成、B 实例校验时取不到答案，核验必然失败——而且表现为"验证码错误"，排查起来很隐蔽。

集群环境改用内置的 Redis 存储，只需声明 `store-type`：

```yaml
coco:
  captcha:
    enabled: true
    store-type: redis             # 默认 in-memory
    redis:
      key-prefix: "coco:captcha:"  # 可选
```

再引入 Spring Data Redis：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`RedisCocoCaptchaStore` 用 Lua 脚本把 `GET` + `DEL` 合成一步，保证单次核验语义在并发下依然成立——分成两次往返会让两个并发请求读到同一个答案。键名对 `captchaId` 做 SHA-256 摘要，标识不会明文进入 Redis keyspace。

:::tip[依赖缺失时明确失败]
若设了 `store-type: redis` 但 classpath 上没有 Spring Data Redis，启动会直接失败并说明原因，**不会静默回落到进程内存储**。静默回落会让验证码在负载均衡后随机失败，却看起来一切正常。
:::

**多个 `StringRedisTemplate` Bean 时**，用 `coco.captcha.redis.template-bean-name` 显式指定，或把目标 Bean 标记 `@Primary`；否则启动失败并列出候选，不会随机挑一个。

Redis 不可用时 `consume` 会直接抛出异常，不会退化成返回 `null`——`null` 的语义是"已过期或不存在"，会被渲染成"验证码错误"，用它兼表故障等于把正确答案判成错误，还会让故障从错误率里消失。

### 4. 自定义生成器或存储

```java
@Bean
CocoCaptchaGenerator myImageGenerator() {
    return new MyImageGenerator();   // supportedType() 返回 IMAGE，覆盖参考实现
}

@Bean
CocoCaptchaStore myCaptchaStore() {
    return new MyOwnCaptchaStore(/* ... */);   // 两种 store-type 下都生效
}
```

## 边界

- **不负责下发**：短信码由业务经 `coco-notification` 或自有渠道发送，本模块只生成与核验。
- **参考图形实现较弱**：`ImageCocoCaptchaGenerator` 只有基础干扰线，不抗 OCR；高安全场景请注册自定义生成器。
- **滑块不含拼图图像**：参考实现只做偏移容差校验，缺口拼图由业务生成器自行实现。
- **默认存储不跨实例**：多实例部署务必切到 `store-type: redis` 或自备共享存储实现。
