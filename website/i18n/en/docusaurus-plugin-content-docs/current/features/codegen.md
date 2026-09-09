---
title: Code Generation
description: Coco Framework 3.0.0 removes built-in code generation; CRUD source generation is owned by the standalone coco-generate tool.
---

# Code Generation

**Coco Framework no longer ships built-in code generation as of 3.0.0.** The `coco-feature-codegen` module, the `CocoFeature.CODEGEN` feature identifier, and the `coco:generate` goal of `coco-maven-plugin` have been removed. CRUD scaffolding is provided by the standalone development-time tool [coco-generate](https://github.com/patton174/coco-generate), which is independent of the framework: it does not depend on framework modules, and business applications gain no runtime dependency from using it.

This page is kept so that links from older documentation and the README keep working, and to explain how to migrate.

## Why it was removed

- Code generation is a development-time concern and should not participate in runtime auto-configuration or package pruning as a feature.
- Generated output is ordinary source owned by the business project; that ownership boundary is stated more clearly in the coco-generate product specification.
- The framework keeps infrastructure only, following the [boundary and design philosophy](../overview.md).

## Migrating from `coco:generate`

How the old Maven goal parameters map to the coco-generate CLI:

| Old `coco:generate` parameter | coco-generate equivalent |
| --- | --- |
| `coco.codegen.spec` (default `coco-codegen.yml`) | `coco-generate.yml` in the project root; the legacy file name `coco-codegen.yml` is still recognized and the YAML structure is unchanged |
| `coco.codegen.outputDirectory` (default `src/main/java`) | Always writes to `<project>/src/main/java` |
| `coco.codegen.dryRun=true` | `coco-generate plan <project>`: prints the generation plan without writing files |
| `coco.codegen.overwrite=true` | No equivalent. coco-generate only performs `CREATE_NEW` writes; existing files are reported as conflicts and never overwritten globally |
| `coco.codegen.templateLocation` | External template roots are not supported yet; only the bundled `crud` templates are used |
| `coco.codegen.encoding` | Always UTF-8 |

Minimal example:

```bash
java -jar coco-generate.jar plan ./my-service
java -jar coco-generate.jar generate ./my-service
```

The bundled `crud` templates are byte-for-byte identical to the 2.x framework templates, so the generated Controller, DTO, application service, domain repository, and MyBatis-Plus infrastructure sources keep the same semantics.

## Upgrade checklist

1. Remove any explicit dependency on `io.github.patton174:coco-feature-codegen` from `pom.xml` (projects that only depend on `coco-spring-boot-starter` need no change).
2. Remove invocations of the `coco:generate` goal and use the coco-generate CLI instead.
3. Remove `codegen` from `coco.features.disabled`, `coco.features.enabled`, and `@CocoFeatures`. A leftover `codegen` entry is rejected by `coco:features` at build time with a hint pointing to coco-generate; the build fails until it is removed.
4. Remove `coco.codegen.*` properties; they are no longer read.
5. Stop referencing types under the `io.github.coco.feature.codegen` package (`CocoCodeGenerator`, `CocoCrudSpec`, `CocoGeneratedFileWriter`, and so on). Describe CRUD specifications with coco-generate directly instead.

See the [coco-generate repository](https://github.com/patton174/coco-generate) and its product boundary specification for details.
