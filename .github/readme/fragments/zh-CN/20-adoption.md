## 引入方式

业务应用使用 `coco-parent` 作为父 POM，并引入一个 starter。

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

可选功能通过配置声明启停：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

也可以通过 Java 配置声明：

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

功能选择优先使用 YAML 或 `@CocoFeatures`。旧的 `CocoConfigurer` Java 钩子仅保留兼容，已不再推荐。

需要保护写请求时，显式启用幂等功能，并在 Controller 类或方法上标注 `@CocoIdempotent`。客户端每次首次提交需携带 `Idempotency-Key`；仅正常完成的 `2xx/3xx` 会保留键到 TTL，同一键随后得到 `409`；任何异常或 `4xx/5xx` 都会释放租约以允许重试。

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

业务 Controller 仍然是普通 Spring 代码：

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

## 显式 CRUD 源码生成

需要标准 CRUD 脚手架时，使用独立的 [coco-generate](https://github.com/patton174/coco-generate)。它在开发期生成业务项目拥有的普通源码，不会成为应用运行时依赖。在业务项目根目录创建 `coco-generate.yml`：

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

`coco-generate` 默认写入 `src/main/java`，并拒绝覆盖已有文件。它会生成普通的 Controller、DTO、应用服务、领域仓储契约和 MyBatis-Plus 基础设施源码；生成后由业务项目继续维护，也不会在运行时自动暴露实体。已使用 `mvn coco:generate` 的 2.x 项目仍受框架兼容支持，但新能力和模板扩展仅在 `coco-generate` 演进。
