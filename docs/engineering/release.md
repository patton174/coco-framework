# Coco Framework 发布说明

## 发布目标

Coco Framework 使用 Maven Central Portal 发布正式版本。发布链路由 Maven release profile 和 GitHub Actions Release 工作流共同完成：

- Maven 负责生成可消费的 POM、sources、javadoc 和 GPG 签名。
- Central Publishing Maven Plugin 负责在 `mvn deploy` 时上传 bundle 到 Central Portal。
- GitHub Actions 负责注入 Central Token 与 GPG 私钥，避免在仓库中保存敏感信息。

## 必需配置

发布前需要在 GitHub 仓库 `Settings -> Secrets and variables -> Actions` 中配置以下 Secrets：

- `CENTRAL_USERNAME`：Central Portal User Token 的用户名。
- `CENTRAL_PASSWORD`：Central Portal User Token 的密码。
- `GPG_PRIVATE_KEY`：ASCII-armored GPG 私钥。
- `GPG_PASSPHRASE`：GPG 私钥密码。

Central Portal 中还需要完成 `io.github.patton174` 命名空间验证，否则上传会被拒绝。

## 本地验证

发布前建议先执行：

```bash
mvn -B install
```

这一步会生成 `.flattened-pom.xml` 并安装到本地仓库，主要用于验证 `${revision}` 已被解析，面向业务应用发布的 POM 可以从本地仓库正常解析全部 Framework 制品。

针对单 starter 接入执行聚焦验证：

```bash
mvn -B -ntp -pl :coco-spring-boot-starter -am verify
```

该命令会构建 starter 的全部上游模块，并运行 starter 的 Servlet 应用启动与自动配置集成测试。根 reactor 中的 Maven plugin 测试继续验证 feature 清单、依赖组合、Spring Boot 包裁剪和索引同步；Framework 仓库不再依赖业务样例应用作为发布门禁。

需要检查 release profile 产物时，可以执行：

```bash
mvn -B -Prelease "-Drevision=1.0.0" "-Dgpg.skip=true" -DskipTests verify
```

本地没有 GPG 私钥时使用 `-Dgpg.skip=true`，正式发布不能跳过签名。

## GitHub Actions 发布

手动发布时进入 GitHub Actions 的 `Release` 工作流，填写版本号，例如 `1.0.0`。

默认 `autoPublish=false`，表示上传到 Central Portal 并通过校验后，需要在 Portal 页面人工确认发布。确认链路稳定后，可以在手动触发时选择自动发布。

也可以推送 tag 触发：

```bash
git tag v1.0.0
git push origin v1.0.0
```

tag 触发默认不会自动发布，仍会停留在 Central Portal 等待人工确认。

## 发布制品

正式制品按 `coco-build`、`coco-foundation`、`coco-spring`、`coco-features` 和 `coco-support` 分组。当前坐标及 1.x 迁移关系见 [模块布局](../architecture/module-layout.md)。

业务产品和源码生成工具不随 Framework 发布：真实产品接入位于 [coco-admin](https://github.com/patton174/coco-admin)，开发期生成能力位于 [coco-generate](https://github.com/patton174/coco-generate)。
