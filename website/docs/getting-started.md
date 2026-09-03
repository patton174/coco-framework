---
slug: /getting-started
sidebar_position: 2
title: 快速开始
---

# 快速开始

本页带你在 5 分钟内把 Coco Framework 接入一个 Spring Boot 应用。

## 前置要求

- JDK 17 及以上
- Maven 3.8.9 及以上

## 1. 引入依赖

业务应用使用 `coco-parent` 作为父 POM，并引入一个 starter：

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

:::info 版本
在 [Maven Central](https://central.sonatype.com/artifact/io.github.patton174/coco-framework) 查看最新发布版本，替换 `${coco.version}`。
:::

如果不想使用 `coco-parent` 作为父 POM，也可以通过 BOM 管理版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.patton174</groupId>
            <artifactId>coco-dependencies</artifactId>
            <version>${coco.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 2. 编写业务 Controller

业务 Controller 仍然是普通 Spring 代码——框架不引入新的编程模型：

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable String id) {
        return this.orderService.get(id);
    }
}
```

返回值会被框架的统一响应封装自动包裹为：

```json
{
  "success": true,
  "code": 0,
  "message": "",
  "data": { "id": "1", "amount": 100 }
}
```

## 3. 启动应用

```bash
mvn spring-boot:run
```

启动时控制台会打印 Coco 启动横幅，并列出已启用的功能。此时你已经获得：

- 统一响应封装
- 全局异常处理 + TraceId
- 一整套可通过配置启停的基础设施

## 4. 按需开启功能

大部分功能默认关闭或按依赖存在与否条件装配。通过 YAML 声明式开启，例如启用幂等：

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

## 下一步

- [特性开关](/feature-toggles) — 全局启停功能的两种方式
- [Web 运行时](/features/web-runtime) — 统一响应、异常处理、TraceId 的细节
- 按左侧目录浏览每个功能模块的接入方式
