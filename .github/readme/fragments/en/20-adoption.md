## Quick start

**1. Add the dependency.** Requires Java 17+, Maven 3.8.9+ and Spring Boot 4.1 (Boot 3.x is not supported). Use `coco-parent` as your parent POM, then add one starter.

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

**2. Write a normal controller.** Nothing Coco-specific in it.

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

**3. Call it.** The result is wrapped for you, and the response carries an `X-Trace-Id` header.

```json
{
  "success": true,
  "code": 200,
  "message": "Operation succeeded.",
  "data": { "id": "1", "amount": 100 }
}
```

If the controller throws, you get the same shape with `"success": false` and a matching HTTP status.

**4. Turn features on or off.** Some features, such as rate limiting, stay off until you set `coco.<feature>.enabled: true`. To keep a module out of the app completely, list it under `coco.features.disabled` (or use `@CocoFeatures`):

```yaml
coco:
  rate-limit:
    enabled: true      # opt in to a feature that is off by default
  features:
    disabled:          # leave these modules out completely
      - mybatis-plus
      - tenant
```

More: [Getting started](https://cocoframework.dev/en/getting-started) · [Feature toggles](https://cocoframework.dev/en/feature-toggles)
