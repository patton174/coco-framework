## 快速开始

**1. 加依赖。** 需要 Java 17+、Maven 3.8.9+ 和 Spring Boot 4.1（不支持 Boot 3.x）。用 `coco-parent` 做父 POM，再加一个 Starter。

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>3.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

**2. 写一个普通 Controller。** 里面没有任何 Coco 专属代码。

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable String id) {
        return orderService.get(id);
    }
}
```

**3. 调用它。** 返回值会被自动包装，响应头里带上 `X-Trace-Id`。

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": { "id": "1", "amount": 100 }
}
```

Controller 抛异常时，返回同样的结构，`"success": false`，并带上对应的 HTTP 状态码。

**4. 开关功能。** 有些功能（比如限流）默认关闭，需要设置 `coco.<功能>.enabled: true` 才生效。想让某个模块完全不参与装配，写进 `coco.features.disabled`（也可以用 `@CocoFeatures`）：

```yaml
coco:
  rate-limit:
    enabled: true      # 默认关闭的功能，需要主动开启
  features:
    disabled:          # 这些模块完全不装配
      - mybatis-plus
      - tenant
```

更多：[快速开始](https://cocoframework.dev/getting-started) · [特性开关](https://cocoframework.dev/feature-toggles)
