# Coco Framework 边界

## 定位

Coco Framework 是面向 Spring Boot Web 服务的高约定基础框架。它负责把多个业务应用都会重复实现的服务器基础设施收敛为稳定依赖、自动配置、构建期 feature 计划和明确的扩展点。

框架不会接管业务模型、接口语义、查询设计或事务边界。业务项目仍然使用普通 Java、Spring Boot 和 Maven 代码，并对最终提交的源码和运行行为负责。

## 框架负责

- 通过 `coco-parent`、`coco-dependencies` 和 `coco-spring-boot-starter` 提供统一的版本、构建和依赖入口。
- 维护稳定 API、上下文、异常、国际化、日志和 feature 元数据。
- 提供 Spring Boot 自动配置、feature 计划、运行时条件和应用启动集成。
- 提供 Web 响应与异常、请求上下文、访问日志、签名、加密和防重放基础设施。
- 提供安全上下文、审计、MyBatis-Plus、租户 SQL、数据权限 SQL 和 OpenAPI 适配能力。
- 在构建期生成 feature 清单，并从 Spring Boot 包中裁剪已禁用 feature 的制品。
- 通过 Bean、属性或小型 SPI 暴露真正需要替换的集成点。

## 业务应用负责

- 领域模型、Controller、应用服务、DTO、仓储接口和业务校验。
- 认证协议、用户与组织模型、角色与权限存储，以及租户主数据。
- 自定义 SQL、复杂查询、数据库迁移、事务和一致性策略。
- MQ、审计持久化、合规报表、监控告警和生产部署拓扑。
- 审阅并维护所有业务源码，包括由开发期工具生成后提交到业务仓库的源码。

框架不会根据实体在运行时自动暴露 CRUD API，也不会强制所有应用采用同一套用户、角色、菜单、组织或租户模型。

## 仓库边界

| 仓库 | 职责 | 依赖方向 |
| --- | --- | --- |
| [coco-framework](https://github.com/patton174/coco-framework) | 独立的 Web 服务器基础框架 | 不依赖 Admin 或 Generate |
| [coco-admin](https://github.com/patton174/coco-admin) | 基于框架实现的 ERP 产品和业务模块 | 运行时依赖 Framework |
| [coco-generate](https://github.com/patton174/coco-generate) | 开发期源码生成器和模板平台 | 可以面向 Framework 契约生成源码 |

`coco-admin` 可以在开发阶段使用 `coco-generate`。生成文件经审阅后归 Admin 或其他业务仓库所有，业务运行时不依赖 Generate。

当前 Framework 仓库不维护业务样例应用，也不提供框架内代码生成 feature 或 `coco:generate` goal。真实产品用法由 `coco-admin` 展示，源码生成能力由 `coco-generate` 演进。

## 依赖约束

1. `coco-foundation` 下的稳定契约和通用基础设施不得依赖具体 feature。
2. `coco-spring-boot-autoconfigure` 负责 Spring 集成和运行时 feature 计划，不承载业务功能。
3. `coco-features` 下的模块各自拥有具体能力，不把实现迁入 starter。
4. `coco-spring-boot-starter` 只组合正常应用依赖，不成为实现模块。
5. `coco-build` 只影响依赖管理和构建生命周期，不成为业务运行时依赖。
6. `coco-support` 提供测试支持，不应成为生产代码的隐式依赖。

详细目录和制品清单见 [模块布局](./module-layout.md)，feature 从声明到打包和运行时生效的过程见 [Feature 生命周期](./feature-lifecycle.md)。
