# Coco Sample Basic

这是 Coco Framework 的基础业务示例项目，用真实业务项目的方式接入 `coco-parent` 和 `coco-spring-boot-starter`。

示例只模拟商品查询、创建订单、订单查询和业务异常，不在业务代码里暴露框架探针接口。框架能力通过配置、异常码、国际化消息、统一响应、Trace 响应头和 Web 安全设施体现。

## 启动示例

先在仓库根目录构建框架，并为示例进程生成临时签名密钥和 AES key：

```powershell
mvn -DskipTests install
$env:SAMPLE_SIGNING_KEY = [Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:SAMPLE_ENCRYPTION_KEY = [Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
cd coco-samples\coco-sample-basic
mvn package
java -jar target\coco-sample-basic-0.0.1-SNAPSHOT.jar
```

普通业务接口无需密钥；签名和加密接口从 `SAMPLE_SIGNING_KEY`、`SAMPLE_ENCRYPTION_KEY`
读取材料。仓库不保存默认共享密钥。

安全能力按真实业务接口路径配置：

```text
POST /sample/secure/signature/orders
POST /sample/secure/replay/orders
POST /sample/secure/encryption/orders
```

业务系统接入时应使用自己的 `appId`、密钥管理方案和接口匹配规则，禁止把生产密钥写入配置文件或提交到版本库。

## Postman 导入

生成可导入的集合和环境变量：

```powershell
python scripts\generate_postman_import.py
```

生成器读取当前进程的 `SAMPLE_SIGNING_KEY` 和 `SAMPLE_ENCRYPTION_KEY`。未设置时会生成不含
secret、IV 和密文的安全模板；设置后会生成可直接配合当前示例进程使用的环境文件。包含
`signatureSecret` 的生成文件不得提交或共享。默认输出目录是已被 Git 忽略的 `target/postman`，
避免本地密钥写回仓库内受跟踪的模板文件。

加密请求生成依赖 Python `cryptography` 包，缺失时先安装：

```powershell
python -m pip install cryptography
```

生成文件：

```text
target\postman\coco-sample-basic.postman_collection.json
target\postman\coco-sample-basic.postman_environment.json
```

导入 Postman 后选择 `Coco Sample Basic Local` 环境，并确认其中的 `signatureSecret` 与示例进程一致。
仓库中的 `postman/` 仅保存安全字段为空的导入模板；签名脚本只从 Postman environment 读取
`signatureSecret`，不会把它复制到普通 collection variable。

如需改端口：

```powershell
python scripts\generate_postman_import.py --base-url http://localhost:18080
```

## 黑盒验证

打包后运行：

```powershell
python scripts\verify_business_flow.py --timeout-seconds 60
```

脚本会自动启动示例 jar，并在同一个应用实例中依次验证默认业务流、签名、防重放和加密流程。
