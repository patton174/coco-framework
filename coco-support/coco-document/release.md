# Coco Framework 发布说明

## 发布目标

Coco Framework 使用 Maven Central Portal 发布正式版本。发布链路由 Maven release profile 和 GitHub Actions Release 工作流共同完成：

- Maven 负责生成可消费的 POM、sources、javadoc 和 GPG 签名。
- Central Publishing Maven Plugin 负责在 `mvn deploy` 时上传 bundle 到 Central Portal。
- GitHub Actions 负责注入 Central Token 与 GPG 私钥，避免在仓库中保存敏感信息。

## 必需配置

发布前需要在 GitHub 仓库的 `coco-spring` Environment 中配置以下 Secrets：

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

这一步会生成 `.flattened-pom.xml` 并安装到本地仓库，主要用于验证 `${revision}` 已被解析，样例项目可以像外部业务项目一样消费框架依赖。

验证真实业务接入示例时执行：

```bash
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
python coco-samples/coco-sample-basic/scripts/verify_business_flow.py
```

该命令会像外部业务项目一样只声明 `coco-spring-boot-starter`，并生成 Spring Boot 可执行包；`coco.features.disabled`
中关闭的功能模块会从最终 `BOOT-INF/lib` 和 Spring Boot 索引文件中裁剪掉。Python 脚本会启动最终
Spring Boot jar，并通过商品查询、创建订单、查询订单和库存不足场景执行黑盒 HTTP 验证。

需要检查 release profile 产物时，可以执行：

```bash
mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify
```

本地没有 GPG 私钥时使用 `-Dgpg.skip=true`，正式发布不能跳过签名。

## GitHub Actions 发布

发布只能从受保护分支的最新 `main` 提交手动执行。进入 GitHub Actions 的 `Release`
工作流，选择 `main`，填写版本号（例如 `2.0.1`）；版本号留空时，工作流会根据现有
正式标签生成下一个 patch 版本。

默认 `autoPublish=false`。工作流上传并等待 Central 校验后，维护者需要在本次工作流仍
处于运行状态时进入 Portal 确认发布；工作流会继续等待到状态变为 `PUBLISHED`。选择
`autoPublish=true` 时，Central 校验通过后自动发布。两种模式都只有在制品已公开发布
后才会进入标签 job。

Release 工作流会在跨平台测试通过后再次确认提交仍是最新 `main`，然后才进入
`coco-spring` 环境。该环境只接受 `main`，禁用管理员绕过，并要求维护者批准后才提供
Central 与 GPG 密钥。Maven Central 发布成功后，工作流会为已验证的 dispatch SHA
创建对应的 `v*` 标签；即使 Central 处理期间 `main` 又有新提交，标签也必须指向实际
发布的精确提交。不要手工创建或推送发布标签；仓库规则集禁止已有发布标签被更新或
删除。

## 样例模块

`coco-samples/coco-sample-basic` 只用于本地和 CI 验证，不作为正式框架组件发布到 Maven Central。
