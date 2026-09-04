---
title: 代码生成
---

# 代码生成

Coco 代码生成（`coco-feature-codegen`）是一套基于模板的 CRUD 脚手架生成能力。它把“一个业务资源”的描述（包名、资源名、数据表、字段）交给 FreeMarker 模板渲染成可继续维护的普通源码，**不注册任何运行时动态 CRUD 行为**。它是开发期能力，生成结果是业务项目自己的源文件。模块绑定 `coco.codegen` 命名空间，默认启用，作为 Coco Feature（`CocoFeature.CODEGEN`）参与自动装配，并在 MyBatis-Plus 自动配置之后加载（内置 CRUD 模板生成的仓储、Mapper 依赖 MyBatis-Plus）。

## 功能简介

- **`CocoCodeGenerator`**：代码生成器 SPI，入参 `CocoCodegenRequest`（模板组、目标包名、扩展上下文），返回 `CocoCodegenResult`（内存中的生成文件集合）。默认实现是 `FreeMarkerCocoCodeGenerator`。
- **`CocoCrudSpec`**：默认 CRUD 生成规格，描述单个业务资源并在进入模板前归一化、校验包名、资源名、表名、字段和 Java 类型，再通过 `toRequest()` 转成内置 `crud` 模板组请求。
- **`FreeMarkerCocoCodeGenerator`**：模板引擎实现，从模板根目录读取每个模板组的 `<group>/manifest.properties` 声明的模板资源与输出路径，**只返回内存文件结果，不隐式创建目录或写盘**。

## 如何启用接入

模块默认启用。开启后框架注册两个 Bean：`CocoCodeGenerator`（默认 `FreeMarkerCocoCodeGenerator`，读取 `coco.codegen.templates.location` 与 `encoding`）和 `CocoGeneratedFileWriter`（显式落盘时使用）。注入生成器即可：

```java
@Component
public class CrudScaffolder {

    private final CocoCodeGenerator codeGenerator;
    private final CocoGeneratedFileWriter fileWriter;

    public CrudScaffolder(CocoCodeGenerator codeGenerator, CocoGeneratedFileWriter fileWriter) {
        this.codeGenerator = codeGenerator;
        this.fileWriter = fileWriter;
    }
}
```

## 使用示例

用 `CocoCrudSpec` 描述一个资源，转成请求交给生成器；生成结果是内存文件，是否落盘由调用方**显式决定**：

```java
CocoCrudSpec spec = CocoCrudSpec.builder("com.example.order", "Order", "t_order")
        .id("id", "id", Long.class, CocoCrudIdStrategy.AUTO)
        .field("orderNo", "order_no", String.class, true)
        .field("amount", "amount", java.math.BigDecimal.class, true)
        .field("remark", "remark", String.class, false)
        .apiPath("/orders")   // 省略时按资源名推导，如 Order -> /orders
        .build();

CocoCodegenResult result = codeGenerator.generate(spec.toRequest());

for (CocoGeneratedFile file : result.files()) {
    System.out.println(file.path());   // 相对输出路径
    // 需要落盘时再调用 fileWriter 写入目标目录
}
```

`CocoCrudSpec` 在构建阶段做了大量安全校验：包名与字段名必须是合法 Java 标识符且不能是关键字；表名、列名必须匹配安全 SQL 标识符；`apiPath` 必须是安全段组成的绝对路径；主键类型不允许为基本类型；字段名、列名不允许重复；资源名和字段类型不得与模板内置生成类型（如 `Controller`、`Mapper`、`Service` 等）冲突。这些校验保证渲染出的源码可编译、无注入风险。

## 模板机制

`FreeMarkerCocoCodeGenerator` 通过模板组组织模板。每个模板组在模板根目录下有一份 `<group>/manifest.properties`，声明模板数量、每个模板的源文件与输出路径：

```properties
group=crud
template.count=2
template.0.source=Entity.java.ftl
template.0.output=${basePackagePath}/entity/${resourceName}Entity.java
template.1.source=Controller.java.ftl
template.1.output=${basePackagePath}/web/${resourceName}Controller.java
```

- 模板路径经过归一化校验，拒绝绝对路径、盘符前缀、`.`/`..` 段等穿越尝试；`file:` / 普通路径根目录下的模板读取还会校验目标路径不逃逸出模板根。
- 输出路径本身也是 FreeMarker 表达式，渲染后经 `CocoGeneratedPathValidator.normalizeRelativePath` 归一化；同一模板组产出重复输出路径会报错。
- 模板模型保留字段 `_coco`、`templateGroup`、`targetPackage` 不允许被请求属性覆盖。
- FreeMarker 配置为严格模式（`RETHROW_HANDLER`、禁用 `localizedLookup`、禁止空循环变量回退），模板错误会直接抛 `CocoCodegenException`。

业务方可通过 `coco.codegen.templates.location` 指向自己的模板根目录，替换或扩展内置 `crud` 模板组。模板位置支持 `classpath:`、`file:` 和普通文件路径三种前缀。

## 关键配置项

绑定前缀 `coco.codegen`（对应 `CocoCodegenProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.codegen.enabled` | `boolean` | `true` | 是否启用代码生成基础设施 |
| `coco.codegen.templates.location` | `String` | `classpath:/coco/codegen/templates` | FreeMarker 模板根位置，支持 `classpath:` / `file:` / 普通路径 |
| `coco.codegen.templates.encoding` | `String` | `UTF-8` | 模板文件编码 |

## 边界注意事项

- 这是**开发期能力**：生成的是业务项目可继续维护的普通源码，不会在运行时注册动态 CRUD 行为，也不读取数据库元数据。
- 内置 `crud` 模板生成的仓储、Mapper 依赖 MyBatis-Plus，因此自动配置声明在 `CocoMybatisPlusAutoConfiguration` 之后；使用内置模板的项目需具备 MyBatis-Plus。
- 生成器只计算文件，**不隐式写盘**。是否落盘、落到哪个目录由调用方通过 `CocoGeneratedFileWriter` 显式控制，避免覆盖既有源码。
- `CocoCrudSpec` 的严格校验意味着不合规的包名、表名、字段名会在构建期直接抛异常，而不是产出无法编译的代码。
