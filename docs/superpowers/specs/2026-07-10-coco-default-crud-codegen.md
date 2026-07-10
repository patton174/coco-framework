# Coco 默认 CRUD 源码生成规格

## 目标

`coco-feature-codegen` 应从只有 SPI 和 No-Op 默认实现，演进为一个可直接使用、同时可替换的源码生成基础设施。

默认能力面向常规 Spring Boot Web 业务项目：开发者显式执行一次 Maven goal，从一份小型 YAML 规格生成可读 Java 源码。生成后代码完全属于业务项目，可以继续修改、删除或替换。

本能力不在应用运行期扫描实体，不自动暴露 Controller，也不接管业务模型、API 语义、自定义查询或事务设计。

## 使用入口

业务项目继承 `coco-parent` 后，在项目根目录创建 `coco-codegen.yml`，然后显式执行：

```powershell
mvn coco:generate
```

该 goal 不绑定 Maven 默认生命周期。是否生成、何时生成，由业务开发者决定。

可选参数：

- `-Dcoco.codegen.spec=<path>`：规格文件，默认 `${project.basedir}/coco-codegen.yml`。
- `-Dcoco.codegen.outputDirectory=<path>`：输出根目录，默认 `${project.build.sourceDirectory}`。
- `-Dcoco.codegen.overwrite=true`：允许覆盖已有文件，默认 `false`。
- `-Dcoco.codegen.dryRun=true`：只计算和打印输出文件，不写磁盘。
- `-Dcoco.codegen.templateLocation=<location>`：替换模板根位置。
- `-Dcoco.codegen.encoding=<charset>`：规格、模板和输出编码，默认 `UTF-8`。

## YAML 契约

```yaml
base-package: com.example.catalog
resources:
  - name: Product
    table: catalog_product
    api-path: /products
    id:
      name: id
      column: id
      type: Long
      strategy: AUTO
    fields:
      - name: sku
        column: sku
        type: String
        required: true
      - name: name
        column: name
        type: String
        required: true
      - name: unitPrice
        column: unit_price
        type: BigDecimal
        required: true
```

契约规则：

- `base-package`、`resources`、`name`、`table`、`id` 和 `fields` 为必填项。
- `api-path` 缺失时可以由资源名生成稳定默认值，但业务方可以显式覆盖。
- 字段名必须是合法 Java 标识符，列名、表名和 API path 必须通过各自的安全格式校验。
- Java 类型支持常用简单类型和合法全限定类型；模板负责生成确定的 import 集合。
- ID strategy 与 MyBatis-Plus `IdType` 对齐，非法值立即失败。
- 未知配置键、重复资源、重复字段、ID 与普通字段重名、空列表和错误数据类型必须立即失败，不能静默忽略。

## 默认生成结果

内置 `crud` 模板组至少生成以下普通业务源码：

```text
<base-package>/domain/<resource>/<Resource>.java
<base-package>/domain/<resource>/<Resource>Repository.java
<base-package>/application/<resource>/<Resource>ApplicationService.java
<base-package>/infrastructure/<resource>/<Resource>Entity.java
<base-package>/infrastructure/<resource>/<Resource>Mapper.java
<base-package>/infrastructure/<resource>/MybatisPlus<Resource>Repository.java
<base-package>/interfaces/rest/<resource>/<Resource>Controller.java
<base-package>/interfaces/rest/<resource>/dto/Create<Resource>Request.java
<base-package>/interfaces/rest/<resource>/dto/Update<Resource>Request.java
<base-package>/interfaces/rest/<resource>/dto/<Resource>Response.java
```

生成代码遵循这些约束：

- Controller 使用显式 DTO，不直接把持久化实体暴露为 API。
- 列表接口默认使用页码和单页上限，不生成无界整表查询。
- 应用服务声明清晰的读写事务边界。
- 资源不存在和分页参数非法使用 Coco 类型化异常，不退化为统一 500。
- domain repository 是普通 Java 契约，MyBatis-Plus 细节留在 infrastructure。
- tenant 和 data-permission 仍由已有 MyBatis-Plus 拦截器处理；模板不强制某个租户或权限字段。
- 默认 CRUD 只是可编辑起点，不隐藏自定义查询、业务校验和错误码设计。

## 生成器架构

- `CocoCodeGenerator` 继续作为可替换 SPI。
- 默认 bean 改为真实模板生成器，读取 `coco.codegen.templates.location` 与 `encoding`。
- 公共 CRUD 规格 API 负责归一化名称、类型、字段和模板模型，再转换为 `CocoCodegenRequest`。
- 模板组通过 manifest 声明模板资源和输出路径；业务项目可以替换模板根位置，或直接提供自定义 `CocoCodeGenerator` bean。
- `CocoGeneratedFileWriter` 只负责显式落盘，不在生成器内部产生隐藏副作用。

## 写入安全

- 所有生成路径必须是规范化相对路径，禁止绝对路径、盘符、空路径和 `..` 越界。
- 写入前必须完成全部路径、重复输出和文件碰撞预检。
- 默认有任意目标文件已存在时整批失败，不能写一半后才报错。
- 只有显式 `overwrite=true` 才允许覆盖。
- `dryRun` 不创建目录或文件。
- 模板组不存在、manifest 非法、模板缺失、模板渲染失败和编码非法都应给出可定位错误。

## 明确非目标

- 运行时 auto-CRUD 或实体自动暴露。
- 数据库反向工程和 JDBC 元数据扫描。
- 数据库迁移脚本生成。
- 强制用户、角色、菜单、组织或 SaaS 租户领域模型。
- 自动覆盖业务方已经修改的源码。
- 在核心框架中固化某个认证、审计持久化或 OpenAPI UI 产品。

## 验收

- 默认自动配置不再注册 No-Op 生成器。
- 内置 YAML 示例可以通过 `mvn coco:generate` 生成完整文件集。
- 生成源码在 Java 17 目标下可以编译。
- 自定义生成器 bean 和自定义模板位置仍可替换默认实现。
- dry-run、默认拒绝覆盖、显式覆盖、路径越界、重复输出、未知模板组和非法 YAML 都有回归测试。
- `mvn -B -pl :coco-feature-codegen,:coco-maven-plugin -am verify` 通过。
- 源码变更后执行 `codegraph sync .`，并保持 `git diff --check` 干净。
