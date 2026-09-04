---
slug: /skills/usage
sidebar_position: 3
title: 使用指南
---

# 使用指南

技能提供 7 个 MCP 工具(Agent 运行时调用)和一组 CLI 命令(人工/CI 使用)。

## MCP 工具

MCP 服务器名为 `coco`,通过 stdio 暴露以下工具:

| 工具 | 入参 | 返回 |
|------|------|------|
| `coco_search_docs` | `{ query, topK?, locale? }` | 语义检索,返回排序后的文档片段 + 标题 + 语言 + 文档站 URL |
| `coco_get_doc` | `{ docPath }` | 单页文档全文(如 `features/idempotency`) |
| `coco_list_docs` | — | 全部文档页(路径、标题、URL) |
| `coco_dependency_snippet` | `{ style?, version? }` | Maven/Gradle 依赖片段,版本自动填最新发布版 |
| `coco_get_latest_version` | — | Maven Central 最新发布版本 |
| `coco_check_version` | `{ current }` | 当前版本与最新版比对,是否需升级 |
| `coco_index_status` | — | 文档索引新鲜度与重建建议 |

### 典型编排

配置某个能力时,Agent 的理想调用链:

1. `coco_search_docs` 定位相关文档 →
2. `coco_get_doc` 拉取整页 →
3. `coco_dependency_snippet` 生成构建依赖。

版本号永远通过工具解析,不手写。

## CLI 命令

无需 MCP 也可直接命令行使用:

```bash
# 语义检索(中英文都可以问)
npx @patton174/coco-agent-skills search "如何开启幂等" --topK 5
npx @patton174/coco-agent-skills search "how to enable idempotency"

# 只要某种语言的结果
npx @patton174/coco-agent-skills search "幂等" --locale zh-Hans

# 列出全部文档页 / 打印整页
npx @patton174/coco-agent-skills list
npx @patton174/coco-agent-skills doc features/idempotency

# 依赖片段(版本自动填最新)
npx @patton174/coco-agent-skills deps --style bom

# 索引新鲜度
npx @patton174/coco-agent-skills status

# 版本检测(可与当前版本比对)
npx @patton174/coco-agent-skills version --current 2.0.1
```

## 依赖片段样式

`deps --style` 支持三种接入方式:

| 样式 | 说明 |
|------|------|
| `parent` | 继承 `coco-parent` 父 POM |
| `bom` | 导入 `coco-dependencies` BOM |
| `gradle` | Gradle 依赖声明 |

## 版本真值

框架 pom 里的版本是 `1.0.0-SNAPSHOT` 占位符,**不可信**。唯一可信来源是 Maven Central 的 `maven-metadata.xml`——所有版本类工具都以此为准。

## 索引更新

文档索引在包构建期预生成(嵌入向量随包分发)。`coco_index_status` / `status` 命令报告索引对应的文档 commit 与构建时间;当框架文档更新后,重新构建索引即可刷新。
