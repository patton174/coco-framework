## 安装

用 `coco-parent` 作为应用父 POM，再加一个 starter 依赖。

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

接入到此结束。统一响应、全局异常处理、TraceId 链路默认开启；业务 Controller 保持普通 Spring 代码。

能力通过 YAML 或 `@CocoFeatures` 声明式启停：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
```

**→ [快速开始](https://patton174.github.io/coco-framework/getting-started)** 完整走一遍第一个服务。
**→ [特性开关](https://patton174.github.io/coco-framework/feature-toggles)** 列出全部开关及默认值。

## CRUD 源码生成

标准 CRUD 脚手架由独立工具 [coco-generate](https://github.com/patton174/coco-generate) 提供。它在开发期生成业务持有的普通源码——Controller、DTO、应用服务、领域仓储、MyBatis-Plus 基础设施——**不是**应用运行时依赖。默认写入 `src/main/java` 且拒绝覆盖已有文件，因此运行时不会自动暴露实体。

**→ [代码生成](https://patton174.github.io/coco-framework/features/codegen)** 讲解配置格式与模板。
