<div align="center">

# Coco Framework

<p>
  <strong>面向 Spring Boot Web 服务的高约定快速开发框架，用于构建可生产落地的 Java 服务。</strong>
</p>

<p>
  <a href="./README.md">English</a>
  ·
  <a href="./README_CN.md">简体中文</a>
</p>

<p>
  <img src="https://img.shields.io/badge/Java-17+-f89820?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4.1"/>
  <img src="https://img.shields.io/badge/Maven-3.8.9-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven 3.8.9"/>
  <img src="https://img.shields.io/badge/License-Apache%202.0-4b5563?style=for-the-badge&logo=apache&logoColor=white" alt="Apache 2.0"/>
</p>

<p>
  <a href="#引入方式">引入方式</a>
  ·
  <a href="#能力范围">能力范围</a>
  ·
  <a href="#生产-sql-防护">SQL 防护</a>
  ·
  <a href="#边界">边界</a>
  ·
  <a href="#扩展边界">扩展边界</a>
  ·
  <a href="#星星趋势">星星趋势</a>
  ·
  <a href="#贡献者">贡献者</a>
</p>

</div>

---

## 概览

Coco Framework 帮助团队快速搭建 Spring Boot Web 服务：框架提供高约定、可替换的黑盒基础设施，业务侧继续使用普通 Java/Spring 编程模型。

它适用于 SaaS 系统、内部服务、管理后台、集成服务和通用 Web API。它不是零代码业务运行时，也不会强制所有项目使用同一套用户、角色、菜单、组织或租户模型。

> 基础设施默认自动化；业务代码保持显式、可生成、由用户持有。

## 引入方式

业务应用使用 `coco-parent` 作为父 POM，并引入一个 starter。

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>${coco.version}</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

可选功能通过配置声明启停：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

也可以通过 Java 配置声明：

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

功能选择优先使用 YAML 或 `@CocoFeatures`。旧的 `CocoConfigurer` Java 钩子仅保留兼容，已不再推荐。

业务 Controller 仍然是普通 Spring 代码：

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    OrderResponse create(@RequestBody CreateOrderRequest request) {
        return this.orderService.create(request);
    }
}
```

## 生产 SQL 防护

Coco 默认不启用 MyBatis-Plus SQL 防护，避免首次接入时破坏已有维护 SQL。生产服务建议先回放或审查业务 SQL，再显式启用：

```yaml
coco:
  mybatis-plus:
    sql-guard:
      block-attack-enabled: true
      illegal-sql-enabled: true
```

启用后，MyBatis-Plus 可能拦截一些业务上合法但需要改写、复核或仅对受控维护语句显式忽略的 SQL：

- 没有有效 `WHERE` 的 `UPDATE` / `DELETE`，或包含 `1 = 1` 等恒真条件的批量写语句。
- 启用 `IllegalSQLInnerInterceptor` 后，没有 `WHERE` 的 `SELECT` / `UPDATE` / `DELETE`。
- `WHERE` 中使用 `OR`、`!=`、被检查列一侧使用函数，或被解析器识别为子查询形态。
- 谓词或 JOIN 条件中首个被检查字段没有命中索引元数据。
- JSQLParser 防护器无法稳定验证的复杂 JOIN、带 schema 的表名、数据库方言 SQL 或动态生成 SQL。

## 能力范围

<table>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Web-Servlet%20Runtime-2563eb?style=flat-square" alt="Web"/></p>
      <strong>Web 运行时</strong><br/>
      统一响应、异常响应、链路标识、请求上下文、访问日志、请求签名、请求加密和防重放。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Security-Context%20Foundation-7c3aed?style=flat-square" alt="Security"/></p>
      <strong>安全基础</strong><br/>
      安全上下文门面、解析 SPI、Web 上下文桥接、可信请求头适配、断言工具和上下文传播原语。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Data-MyBatis--Plus-0891b2?style=flat-square" alt="Data"/></p>
      <strong>数据集成</strong><br/>
      MyBatis-Plus 拦截器组装、分页、SQL 防护、租户 SQL 隔离和数据权限 SQL 条件。
    </td>
  </tr>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Config-Feature%20Control-f97316?style=flat-square" alt="Feature Control"/></p>
      <strong>功能控制</strong><br/>
      父 POM、BOM、单 starter、声明式功能选择、依赖感知的功能计划和运行时功能条件。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Audit-Event%20Pipeline-16a34a?style=flat-square" alt="Audit"/></p>
      <strong>审计流水线</strong><br/>
      审计事件模型、记录器 SPI、发布器、失败策略和访问日志到审计事件的适配器。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Codegen-SPI%20Boundary-475569?style=flat-square" alt="Codegen"/></p>
      <strong>代码生成边界</strong><br/>
      面向显式源码脚手架的生成器 SPI；隐藏式运行时 CRUD Controller 明确不在范围内。
    </td>
  </tr>
</table>

## 边界

<table>
  <thead>
    <tr>
      <th width="50%">Coco 负责封装</th>
      <th width="50%">业务应用负责</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>starter 装配和自动配置组合</td>
      <td>领域模型和 API 语义</td>
    </tr>
    <tr>
      <td>功能启停、依赖传播和运行时功能门控</td>
      <td>Controller 形态和服务编排</td>
    </tr>
    <tr>
      <td>统一响应、类型化异常、国际化、链路上下文和访问日志</td>
      <td>事务边界和自定义持久化设计</td>
    </tr>
    <tr>
      <td>请求签名、请求加密、防重放、安全上下文生命周期桥接、审计钩子、租户 SQL 和数据权限 SQL</td>
      <td>认证提供方、用户模型、组织模型、角色模型和生成后的 CRUD 代码</td>
    </tr>
  </tbody>
</table>

CRUD 应该走代码生成，而不是运行时暴露实体。生成后的代码应当是可读的 Java 源码，业务项目可以保留、修改、删除或替换。

## 扩展边界

<table>
  <thead>
    <tr>
      <th width="25%">领域</th>
      <th width="38%">已交付边界</th>
      <th width="37%">业务应用或路线图负责</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Security</td>
      <td>上下文门面、解析 SPI、Servlet 上下文桥接、可信请求头适配、断言工具和上下文传播原语。</td>
      <td>认证提供方、RBAC/ABAC 模型、会话、令牌和用户存储。</td>
    </tr>
    <tr>
      <td>Audit</td>
      <td>事件契约、发布器、记录器 SPI、失败策略和访问日志适配器。</td>
      <td>数据库落库、MQ 投递、合规报表和保留策略。</td>
    </tr>
    <tr>
      <td>OpenAPI</td>
      <td>元数据提供器、配置边界，以及业务项目已引入 SpringDoc 时的可选元数据适配器。</td>
      <td>文档渲染、UI 集成和接口级文档策略。</td>
    </tr>
    <tr>
      <td>Codegen</td>
      <td>生成器 SPI 和配置边界。</td>
      <td>模板、项目脚手架规则和生成后的 CRUD 源码归属。</td>
    </tr>
  </tbody>
</table>

## 运行形态

```mermaid
flowchart LR
    app["业务应用"] --> parent["coco-parent"]
    app --> starter["coco-spring-boot-starter"]
    starter --> config["coco-config"]
    config --> runtime["coco-feature-runtime"]
    runtime --> web["Web 运行时"]
    runtime --> security["安全基础"]
    runtime --> data["数据集成"]
    web --> business["普通 Spring 业务代码"]
    security --> business
    data --> business
```

## 星标历史

<!-- COCO_STATS_START -->
<table>
  <tr>
    <td align="center"><strong>1</strong><br/>星标</td>
    <td align="center"><strong>0</strong><br/>派生</td>
    <td align="center"><strong>1</strong><br/>贡献者</td>
    <td align="center"><a href="https://github.com/patton174/coco-framework">更新时间: 2026-07-09</a></td>
  </tr>
</table>
<!-- COCO_STATS_END -->

<a href="https://www.star-history.com/?repos=patton174%2Fcoco-framework&type=date&legend=bottom-right">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=patton174/coco-framework&type=date&theme=dark&legend=bottom-right&sealed_token=WZtqAVEpmYHgLl3AUpfxFV4e_emJFt7fNK_ep9JrVVZ-tZvSoWbTwOEfvg8WIg0WEiosjWjZYSnF9DgC86cCiKp4iJ1uqirVm49z4-xECDHKRBogVqDokZF1cp6b00IInXU9FOcrhqR1nhcwP0t2KQhtRQAFe07t-K4PpUO7ERUjlhS6iRI1085j31pQ"/>
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=patton174/coco-framework&type=date&legend=bottom-right&sealed_token=WZtqAVEpmYHgLl3AUpfxFV4e_emJFt7fNK_ep9JrVVZ-tZvSoWbTwOEfvg8WIg0WEiosjWjZYSnF9DgC86cCiKp4iJ1uqirVm49z4-xECDHKRBogVqDokZF1cp6b00IInXU9FOcrhqR1nhcwP0t2KQhtRQAFe07t-K4PpUO7ERUjlhS6iRI1085j31pQ"/>
    <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=patton174/coco-framework&type=date&legend=bottom-right&sealed_token=WZtqAVEpmYHgLl3AUpfxFV4e_emJFt7fNK_ep9JrVVZ-tZvSoWbTwOEfvg8WIg0WEiosjWjZYSnF9DgC86cCiKp4iJ1uqirVm49z4-xECDHKRBogVqDokZF1cp6b00IInXU9FOcrhqR1nhcwP0t2KQhtRQAFe07t-K4PpUO7ERUjlhS6iRI1085j31pQ"/>
  </picture>
</a>

## 贡献者

<!-- COCO_CONTRIBUTORS_START -->
<table>
  <tr>
    <td align="center">
      <a href="https://github.com/patton174">
        <img src="https://avatars.githubusercontent.com/u/151543933?v=4&s=96" width="48" height="48" alt="patton174"/><br/>
        <sub>patton174</sub>
      </a>
    </td>
  </tr>
</table>
<p><a href="https://github.com/patton174/coco-framework/graphs/contributors">查看全部贡献者</a></p>
<!-- COCO_CONTRIBUTORS_END -->

<sub>星星和贡献者区域由 GitHub Actions 自动刷新。见 `.github/workflows/update-readme-insights.yml` 和 `tools/docs/update-readme-insights.mjs`。</sub>

## 许可证

Apache License 2.0.
