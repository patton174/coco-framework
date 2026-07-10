# Coco Audit 功能独立性规格

## 问题

`coco-feature-audit` 的实际依赖只有通用 i18n、通用日志和功能运行时。审计事件、发布器、默认结构化日志和 Recorder SPI 均不需要 Servlet Web 或 MyBatis-Plus。

标准功能图仍把 Audit 声明为同时依赖 `WEB` 和 `MYBATIS_PLUS`，导致业务项目关闭 Web 或数据库集成时，构建期清单和运行期功能计划会连带禁用 Audit，并可能从最终包中裁掉 `coco-feature-audit`。

这与 Coco 面向通用 Web 服务器和 Spring Boot 服务的定位冲突，也把未来可能存在的数据库审计适配器错误提升成了核心依赖。

## 目标

- Audit 作为独立基础设施能力，不依赖 `WEB`、`MYBATIS_PLUS`、租户或业务安全模型。
- 无数据库 Web 服务仍可获得审计事件发布和默认结构化日志。
- 非 Web Spring Boot 服务可以显式发布审计事件。
- Web 访问日志只是一种可选事件来源，不决定 Audit 功能是否存在。
- 未来数据库表变更审计通过可选 Recorder、Publisher 适配器或独立功能模块组合。

## 功能图

`StandardCocoFeatures` 中 Audit 定义的依赖集合必须为空：

```text
AUDIT -> []
```

保持不变的依赖包括：

- Tenant 和 Data Permission 继续依赖 MyBatis-Plus 与 Security。
- OpenAPI 继续依赖 Web 与 Security。
- Codegen 当前继续依赖 MyBatis-Plus，因为内置 CRUD 模板直接生成 MyBatis-Plus 代码。

## 运行时组合

- `CocoAuditAutoConfiguration` 继续通过 `@ConditionalOnCocoFeature(AUDIT)` 激活。
- 默认 Recorder 继续依赖通用 `CocoLogManager`，不直接依赖 Web 或 MyBatis-Plus。
- `CocoAccessLogAuditRecorder` 继续作为 `CocoAccessLogRecorder` Bean 注册；没有 Web 请求生产访问日志时，它不会产生额外行为。
- 自定义 `CocoAuditRecorder` 和 `CocoAuditFormatter` 覆盖规则保持不变。

## 构建期行为

- 禁用 `mybatis-plus` 时，Audit 仍在最终功能计划和 `META-INF/coco/features.json` 中保持启用。
- 禁用 `web` 时，Audit 仍保持启用。
- Maven 插件仍为启用的 Audit 添加 `coco-feature-audit` 运行时依赖。
- 禁用 Audit 本身时，现有裁剪行为保持不变。

## 兼容性

`CocoFeatureManifest` 保存的是构建期已经解析完成的启用状态，运行时不会根据新版依赖图重算旧清单。因此升级后必须重新执行 `coco:features` 并重新打包；旧产物中已经被连带关闭的 Audit 不会仅靠替换运行时 jar 自动恢复。

本次依赖图修正不改变 manifest schema，重新生成清单即可获得新语义。

## 非目标

- 在本批次实现数据库表变更审计。
- 修改 Tenant、Data Permission、OpenAPI 或 Codegen 的依赖关系。
- 自动推断业务方法是否需要审计。
- 改变 Audit 默认日志的可靠性边界。

## 验收

- 功能元数据断言 Audit 依赖为空。
- 关闭 MyBatis-Plus 后，Audit 不出现在 `disabledByDependencyFeatures` 中。
- 关闭 Web 后，Audit 仍启用。
- Maven 功能装配测试证明关闭 MyBatis-Plus 后仍生成并保留 Audit 依赖。
- Audit 模块依赖树不包含 Servlet Web、MyBatis 或 MyBatis-Plus 产物。
- 聚焦验证和全量 `mvn -B verify` 通过。
- 源码变更后执行 `codegraph sync .`，并保持 `git diff --check` 干净。
