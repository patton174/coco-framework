## Install

Use `coco-parent` as the application parent and add the single starter dependency.

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>${coco.version}</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Optional feature selection remains declarative:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

Or Java-based:

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

Prefer YAML or `@CocoFeatures` for feature selection. The older `CocoConfigurer` Java hook is kept for compatibility but is deprecated.

To protect a write request, explicitly enable idempotency and annotate the Controller class or method with `@CocoIdempotent`. Clients send an `Idempotency-Key` for the first submission; only a normally completed `2xx/3xx` response retains the key for its TTL and returns `409` on repeats; an exception or any `4xx/5xx` releases the lease for retry.

```yaml
coco:
  idempotency:
    enabled: true
```

```java
@PostMapping
@CocoIdempotent(namespace = "orders")
OrderResponse create(@RequestBody CreateOrderRequest request) {
    return this.orderService.create(request);
}
```

Business controllers remain ordinary Spring code:

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    OrderResponse create(@RequestBody CreateOrderRequest request) {
        return this.orderService.create(request);
    }
}
```

## Explicit CRUD Source Generation

When a project needs standard CRUD scaffolding, use the standalone [coco-generate](https://github.com/patton174/coco-generate). It generates business-owned ordinary source during development and is not an application runtime dependency. Add `coco-generate.yml` at the project root:

```yaml
base-package: com.example.catalog
resources:
  - name: Product
    table: catalog_product
    api-path: /products
    id: { name: id, column: id, type: Long, strategy: AUTO }
    fields:
      - { name: sku, column: sku, type: String, required: true }
      - { name: unitPrice, column: unit_price, type: BigDecimal, required: true }
```

`coco-generate` writes to `src/main/java` by default and refuses to overwrite existing files. It produces ordinary Controller, DTO, application-service, domain-repository, and MyBatis-Plus infrastructure source owned by the business project, and never exposes entities automatically at runtime. Existing 2.x projects using `mvn coco:generate` remain supported as a framework compatibility surface, but new capabilities and template extensions evolve only in `coco-generate`.
