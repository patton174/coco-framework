---
slug: /getting-started
sidebar_position: 2
title: Getting Started
---

# Getting Started

This page walks you through integrating Coco Framework into a Spring Boot application in 5 minutes.

## Prerequisites

- JDK 17 or later
- Maven 3.8.9 or later

## 1. Add the dependency

A business application uses `coco-parent` as its parent POM and adds a starter:

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

:::info[Version]
Check the latest release on [Maven Central](https://central.sonatype.com/artifact/io.github.patton174/coco-framework) and replace `${coco.version}`.
:::

If you would rather not use `coco-parent` as your parent POM, you can also manage versions through the BOM:

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

## 2. Write a business Controller

A business Controller is still plain Spring code — the framework introduces no new programming model:

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

The return value is automatically wrapped by the framework's unified response wrapper into:

```json
{
  "success": true,
  "code": 0,
  "message": "",
  "data": { "id": "1", "amount": 100 }
}
```

## 3. Start the application

```bash
mvn spring-boot:run
```

At startup the console prints the Coco startup banner and lists the enabled features. At this point you already have:

- Unified response wrapping
- Global exception handling + TraceId
- A full set of infrastructure that can be started and stopped via configuration

## 4. Enable features on demand

Most features are disabled by default, or conditionally assembled based on whether a dependency is present. Enable them declaratively via YAML — for example, to enable idempotency:

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

## Next steps

- [Feature toggles](/feature-toggles) — two ways to globally start and stop features
- [Web runtime](/features/web-runtime) — the details of unified responses, exception handling, and TraceId
- Browse the integration approach for each feature module via the left-hand table of contents
