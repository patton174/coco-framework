## 安装

用 `coco-parent` 作为应用父 POM，再加一个 starter 依赖。

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>2.0.2</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

接入到此结束。统一响应、全局异常处理、TraceId 链路默认开启；业务 Controller 保持普通 Spring 代码。

能力通过 YAML 或 `@CocoFeatures` 声明式启停：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
```

**→ [快速开始](https://cocoframwork.dev/getting-started)** 完整走一遍第一个服务。
**→ [特性开关](https://cocoframwork.dev/feature-toggles)** 列出全部开关及默认值。
