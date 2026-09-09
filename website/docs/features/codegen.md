---
title: 代码生成
description: Coco Framework 3.0.0 起移除内置代码生成；CRUD 源码生成由独立工具 coco-generate 承担。
---

# 代码生成

**Coco Framework 3.0.0 起不再内置代码生成。** `coco-feature-codegen` 模块、`CocoFeature.CODEGEN` 功能标识以及 `coco-maven-plugin` 的 `coco:generate` goal 均已移除。CRUD 脚手架由独立的开发期工具 [coco-generate](https://github.com/patton174/coco-generate) 提供，它与框架保持独立：不依赖框架模块，业务应用也不会因此增加运行时依赖。

这一页保留下来，是为了让旧文档与 README 中的链接继续可用，并说明如何迁移。

## 为什么移除

- 代码生成是开发期能力，不应作为运行时特性参与自动装配和包裁剪。
- 生成结果是业务项目自己的普通源码，归属边界在 coco-generate 的产品规格中更清晰。
- 框架自身只保留基础设施，遵循[边界与设计哲学](../overview.md)。

## 从 `coco:generate` 迁移

旧的 Maven goal 参数与 coco-generate CLI 的对应关系：

| 旧 `coco:generate` 参数 | coco-generate 对应方式 |
| --- | --- |
| `coco.codegen.spec`（默认 `coco-codegen.yml`） | 项目根目录的 `coco-generate.yml`；旧文件名 `coco-codegen.yml` 仍可直接识别，YAML 结构不变 |
| `coco.codegen.outputDirectory`（默认 `src/main/java`） | 固定写入 `<项目目录>/src/main/java` |
| `coco.codegen.dryRun=true` | `coco-generate plan <项目目录>`：只打印生成计划，不写文件 |
| `coco.codegen.overwrite=true` | 无对应。coco-generate 只允许 `CREATE_NEW`，已有文件一律作为冲突报告，不做全局覆盖 |
| `coco.codegen.templateLocation` | 暂不支持外部模板根；当前只使用内置 `crud` 模板 |
| `coco.codegen.encoding` | 固定 UTF-8 |

最小示例：

```bash
java -jar coco-generate.jar plan ./my-service
java -jar coco-generate.jar generate ./my-service
```

内置 `crud` 模板与 2.x 框架内置模板逐字节一致，生成的 Controller、DTO、应用服务、领域仓储和 MyBatis-Plus 基础设施源码保持相同语义。

## 升级检查

1. 从 `pom.xml` 中移除对 `io.github.patton174:coco-feature-codegen` 的显式依赖（只依赖 `coco-spring-boot-starter` 的项目无需改动）。
2. 删除对 `coco:generate` goal 的调用，改用 coco-generate CLI。
3. 从 `coco.features.disabled`、`coco.features.enabled` 与 `@CocoFeatures` 中删除 `codegen`。残留的 `codegen` 会在构建期被 `coco:features` 拒绝，并给出指向 coco-generate 的提示；不删除则构建失败。
4. 删除 `coco.codegen.*` 配置项，它们不再被读取。
5. 不再引用 `io.github.coco.feature.codegen` 包下的类型（`CocoCodeGenerator`、`CocoCrudSpec`、`CocoGeneratedFileWriter` 等）。需要在代码中描述 CRUD 规格时，请直接使用 coco-generate。

更多说明见 [coco-generate 仓库](https://github.com/patton174/coco-generate) 与其产品边界规格。
