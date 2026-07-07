# Coco Sample Basic

这是 Coco Framework 的基础业务示例项目，用真实业务项目的方式接入 `coco-parent` 和 `coco-spring-boot-starter`。

示例只模拟商品查询、创建订单、订单查询和业务异常，不在业务代码里暴露框架探针接口。框架能力通过配置、异常码、国际化消息、统一响应、Trace 响应头和 Web 安全设施体现。

## 启动示例

先在仓库根目录构建框架，再进入示例项目打包：

```powershell
mvn -DskipTests install
cd coco-samples\coco-sample-basic
mvn package
java -jar target\coco-sample-basic-0.0.1-SNAPSHOT.jar
```

默认启动后即可验证业务接口、统一响应、异常国际化、访问日志、签名、防重放和加密。

安全能力按真实业务接口路径配置：

```text
POST /sample/secure/signature/orders
POST /sample/secure/replay/orders
POST /sample/secure/encryption/orders
```

业务系统接入时应使用自己的 `appId`、密钥和接口匹配规则。

## Postman 导入

生成可导入的集合和环境变量：

```powershell
python scripts\generate_postman_import.py
```

加密请求生成依赖 Python `cryptography` 包，缺失时先安装：

```powershell
python -m pip install cryptography
```

生成文件：

```text
postman\coco-sample-basic.postman_collection.json
postman\coco-sample-basic.postman_environment.json
```

导入 Postman 后选择 `Coco Sample Basic Local` 环境。示例应用默认启动后即可直接运行完整集合。

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
