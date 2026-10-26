<!-- Generated from .github/readme/manifest.json. Edit the source fragments, then run: node .github/readme/scripts/render.mjs --write -->

<h1><picture><source media="(prefers-color-scheme: dark)" srcset="website/static/img/brand/logo-primary-dark.svg"><img src="website/static/img/brand/logo-primary.svg" height="56" align="absmiddle" alt="Coco Framework 标志"></picture>&nbsp; Coco Framework</h1>

**一个 Spring Boot Starter，替你写掉后端里那些重复的活。** 加一个依赖，不用再自己写统一响应、异常处理和请求追踪。

[**快速开始**](https://cocoframework.dev/getting-started) &nbsp;·&nbsp; [文档](https://cocoframework.dev/) &nbsp;·&nbsp; [Discussions](https://github.com/patton174/coco-framework/discussions) &nbsp;·&nbsp; [English](README.md)

![Build](https://github.com/patton174/coco-framework/actions/workflows/ci.yml/badge.svg) ![Release](https://img.shields.io/github/v/release/patton174/coco-framework) ![License](https://img.shields.io/badge/License-Apache--2.0-b4441f) ![Java](https://img.shields.io/badge/Java-17%2B-b4441f) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F) ![Maven](https://img.shields.io/badge/Maven-3.8.9%2B-C71A36)

---

## Coco 是什么

大多数 Spring Boot 服务最后都会写出同一套胶水代码。Coco 把它一次做好，放进一个 Starter：

- **统一的响应格式。** Controller 直接返回普通对象，Coco 负责包装。
- **异常自动变成规范响应。** 一个全局处理器，HTTP 状态码正确。
- **每个请求都有 TraceId。** 通过 `X-Trace-Id` 请求头读取或回写。
- **常用功能随时可开。** 限流、幂等、分布式锁、缓存、验证码、多租户等，需要时在 YAML 里打开。
- **业务代码仍是普通 Spring。** Coco 不替你决定用户、角色和权限模型。

## 快速开始

**1. 加依赖。** 需要 Java 17+、Maven 3.8.9+ 和 Spring Boot 4.1（不支持 Boot 3.x）。用 `coco-parent` 做父 POM，再加一个 Starter。

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>3.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

**2. 写一个普通 Controller。** 里面没有任何 Coco 专属代码。

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable String id) {
        return orderService.get(id);
    }
}
```

**3. 调用它。** 返回值会被自动包装，响应头里带上 `X-Trace-Id`。

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": { "id": "1", "amount": 100 }
}
```

Controller 抛异常时，返回同样的结构，`"success": false`，并带上对应的 HTTP 状态码。

**4. 开关功能。** 有些功能（比如限流）默认关闭，需要设置 `coco.<功能>.enabled: true` 才生效。想让某个模块完全不参与装配，写进 `coco.features.disabled`（也可以用 `@CocoFeatures`）：

```yaml
coco:
  rate-limit:
    enabled: true      # 默认关闭的功能，需要主动开启
  features:
    disabled:          # 这些模块完全不装配
      - mybatis-plus
      - tenant
```

更多：[快速开始](https://cocoframework.dev/getting-started) · [特性开关](https://cocoframework.dev/feature-toggles)

## 上生产前

有些默认值是为了第一次跑起来方便，不是为集群准备的。请检查这三项：

| 功能 | 默认 | 你需要做什么 |
|---|---|---|
| **SQL 防护** | 关闭 | 先检查你的 SQL，再开启 `block-attack` / `illegal-sql`。它可能拒绝一些它无法校验的合法语句。[文档](https://cocoframework.dev/features/mybatis-plus) |
| **防重放** | 内存存储，仅单进程有效 | 多实例部署时，换成 JDBC 存储或你自己的实现。Coco 不会建表，表结构由你管理。[文档](https://cocoframework.dev/features/request-security) |
| **异步日志** | 有界队列；错误日志始终同步写入 | 把 `CocoAsyncLogDropListener` 接到你的监控，就能看到被丢弃的日志。它反映过载情况，不保证日志一定送达。[文档](https://cocoframework.dev/features/infra) |

另外要注意：只有统一响应、异常处理和 TraceId 是零配置可用的。其他功能大多需要手动开启，有些还需要 Redis 或数据库。每个功能页都写明了默认值。

## 功能一览

| 领域 | 内容 |
|---|---|
| Web | 统一响应、全局异常、TraceId、访问日志、CORS、安全响应头、字段级校验错误 |
| 请求安全 | 签名、加密、防重放 |
| 数据 | MyBatis-Plus、分页、租户 SQL 隔离、数据权限过滤 |
| 流量控制 | 限流（滑动窗口、令牌桶）、幂等、分布式锁、定时调度 |
| 平台 | 文件存储、本地与 Redis 缓存、消息、通知渠道、验证码（图形、滑块、短信） |
| 可观测 | 审计事件、OpenAPI 元数据 |

以上功能都已包含在最新版本 **v3.0.0** 中。

## Coco 不做什么

Coco 只管基础设施。下面这些由你的应用负责：

- 业务模型和 API 设计
- 登录与认证
- 用户、角色、组织模型
- 数据库表结构和迁移

所有扩展点都是普通的 Spring `@Bean`，任何默认实现都可以替换成你自己的。需要 CRUD 时，代码生成会产出普通 Java 源码，归你所有，运行时不会把实体直接暴露成接口。更多见[概览](https://cocoframework.dev/overview)。

## 端到端验收

业务和 HTTP 验收测试在 [`coco-admin/framework-acceptance`](https://github.com/patton174/coco-admin/tree/main/framework-acceptance)：

- **Basic**（无数据库）：响应、异常、i18n、Trace、签名、加密、防重放。
- **Full**（H2 + MyBatis-Plus）：安全断言、租户 SQL 隔离、数据权限过滤、审计事件。

## 相关项目

| 项目 | 说明 |
|---|---|
| [**Coco Framework**](https://github.com/patton174/coco-framework) | 本仓库：Spring Boot Starter |
| [**Coco Admin**](https://github.com/patton174/coco-admin) | 基于框架构建的 ERP 产品 |
| [**Coco Generate**](https://github.com/patton174/coco-generate) | 源码生成器与模板 |

依赖方向是单向的：Admin 使用 Framework，Framework 不依赖另外两个。生成出来的代码归你的应用所有，不会增加运行时依赖。

## 社区

提问和想法：[Discussions](https://github.com/patton174/coco-framework/discussions)。想参与贡献：[CONTRIBUTING](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)。发现安全漏洞：[安全策略](https://github.com/patton174/coco-framework/security/policy)。

<details>
<summary>项目数据与贡献者</summary>

<br/>

<!-- COCO_STATS_START -->
<table>
  <tr>
    <td align="center"><strong>4</strong><br/>星标</td>
    <td align="center"><strong>1</strong><br/>派生</td>
    <td align="center"><strong>1</strong><br/>贡献者</td>
    <td align="center"><a href="https://github.com/patton174/coco-framework">更新时间: 2026-10-26</a></td>
  </tr>
</table>
<!-- COCO_STATS_END -->

<!-- COCO_CONTRIBUTORS_START -->
<table>
  <tr>
    <td align="center">
      <a href="https://github.com/patton174">
        <img src="https://wsrv.nl/?url=github.com%2Fpatton174.png&w=96&h=96&fit=cover&mask=circle" width="48" height="48" alt="patton174"/><br/>
        <sub>patton174</sub>
      </a>
    </td>
  </tr>
</table>
<p><a href="https://github.com/patton174/coco-framework/graphs/contributors">查看全部贡献者</a></p>
<!-- COCO_CONTRIBUTORS_END -->

</details>

## 许可证

Apache License 2.0.
