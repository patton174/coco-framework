# Coco Framework 模块 / 分包 / 代码耦合审计报告

- 审计日期：2026-07-09
- 审计范围：28 个 Maven 模块、主要 Java 包结构、`coco-feature-web` 子包内聚度、starter 装配边界
- 审计方法：Maven 依赖关系、Java import 关系、fan-in / fan-out、god class / god package、Tarjan SCC、AGENTS.md 架构规则对照

## 结论

Coco Framework 的模块层依赖方向整体干净：未发现 `coco-common-*` 反向依赖 `coco-feature-*`，也未发现模块级循环依赖。真正的耦合压力集中在两个位置：

- `coco-spring-boot-starter` 默认装入较多 feature artifact，适合快速接入，但按需装配边界需要后续治理。
- `coco-feature-web` 内部存在明显的入口类和请求上下文包聚合问题；其中 `CocoWebAutoConfiguration` 已在 PR8 拆分，`web.security.metadata` 命名冲突已在 PR9 收敛为 `web.request.metadata`。

## 当前状态快照

| 编号 | 主题 | 状态 | 当前处理 |
| --- | --- | --- | --- |
| M1 | starter 装包过重 | adjusted | 暂不破坏单 starter 体验，后续评估 core/web starter 分层。 |
| M2 | tenant / data-permission 直接依赖 MyBatis-Plus | deferred | 先保留 MP 实现边界，v1.1 再评估策略 SPI 上提。 |
| M3 | security 与 web 请求安全管线无桥接 | adjusted | README 已改为上下文门面，不宣称完整认证授权产品。 |
| M4 | `CocoWebAutoConfiguration` 过大 | done | 已拆分为 context、body、trace、signature、encryption、replay、response、exception、i18n 等子配置。 |
| M5 | `CocoRequestContext` 过大 | deferred | 属于公共上下文模型演进，不抢安全修复窗口。 |
| M6 | web 多个 `*Properties` 过大 | deferred | 后续按配置 POJO 与 provider/resolver 逻辑拆分治理。 |
| M7 | `web.context` god package | deferred | 等自动配置拆分稳定后再分包。 |
| M8 | 部分 web 子包抽象度较低 | accepted | 先记录为有意识固化点，只有真实替换需求才新增 SPI。 |
| M9 | `web.security.metadata` 命名冲突 | done | 已改为 `io.github.coco.feature.web.request.metadata`。 |
| M10 | `CocoRequestContextAttributes` 过大 | deferred | 与 M5 合并处理。 |
| M11 | common logging 抽象层复用率低 | deferred | 先保留，等 audit 和 web 使用形态稳定后复核。 |
| M12 | web 请求安全元数据与 security 模块语义重叠 | done | 通过 M9 命名收敛拆开请求级元数据与用户级安全上下文。 |
| M13 | Maven plugin 依赖 feature registry | deferred | 元数据仍保持声明式，后续再评估纯数据契约是否上提。 |

## 模块级依赖判断

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 模块级循环依赖 | 0 | Tarjan SCC 未发现模块环。 |
| `coco-common-*` 依赖 `coco-feature-*` | 0 | 保持 common 独立性。 |
| feature 间依赖 | 少量必要依赖 | 主要集中在 runtime、registry、MyBatis-Plus、tenant、data-permission。 |
| starter 行为 | 有边界压力 | starter 当前偏向“一依赖可用”，后续可提供更细分入口但不应破坏默认体验。 |
| API 稳定性 | 基本清晰 | `coco-api-core` 放公共契约，后续需要收敛低价值 API。 |

## `coco-feature-web` 子包判断

| 子域 | 风险 | 判断 |
| --- | --- | --- |
| root auto-configuration | 高，已修 | 原入口类承担过多 bean 装配；已拆为多个小配置类。 |
| `web.context` | 高，待修 | 请求上下文、参数、快照、canonicalization、target resolver 仍在同一大域内。 |
| `web.request.metadata` | 中，已修 | 原 `web.security.metadata` 容易与 `coco-feature-security` 混淆；包名已收敛。 |
| `web.signature` / `web.encryption` / `web.replay` | 中 | 三个请求安全管线依赖上下文、请求元数据和异常写出，后续需要更细粒度测试。 |
| `web.exception` | 中 | 暂时以框架固化行为为主，不主动制造 SPI。 |
| `web.response` | 低 | 统一响应包装边界清晰，继续保持对下载、流式响应等场景的跳过策略。 |

## M9 命名收敛说明

原包名语义：`web.security.metadata`

该包表达的是请求级元数据：请求是否签名、是否加密、是否携带时间戳和 nonce、哪些请求需要纳入 canonical form。它不是用户认证授权模型。

新包名：

```text
io.github.coco.feature.web.request.metadata
```

这样可以把两个概念拆开：

- `coco-feature-web.request.metadata`：请求安全管线需要的请求元数据。
- `coco-feature-security`：用户级 principal、role、permission 和安全上下文门面。

## 后续建议

1. 先完成 PR9 文档承诺收敛，确保 README 不把 security、audit、openapi、codegen 描述成超出现状的完整产品。
2. 再处理 PR10 API/SPI 收敛，评估 `CocoConfigurer`、`CocoFeatureRegistry`、`DefaultCocoFeatureRegistry` 是否需要内部化或废弃迁移。
3. `web.context` 分包应单独开 PR，避免与自动配置拆分、包名迁移、API 收敛混在一起。
4. starter 拆分只应在有明确业务使用场景后执行；当前仍优先保留一 starter 快速接入体验。

## 验收建议

```powershell
mvn -pl :coco-feature-web -am test
mvn -pl :coco-feature-security -am test
git diff --check
codegraph sync .
```
