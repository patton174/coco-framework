---
slug: /skills
sidebar_position: 1
title: 技能概览
---

# Agent 技能

**让 AI 编码助手直接会用 Coco Framework。**

`@patton174/coco-agent-skills` 是一个零依赖的 Node 包,把框架文档、版本信息和接入能力封装成 AI Agent(Claude Code、Codex、Cursor 及国产 Agent)可直接调用的技能。Agent 不再需要你手动粘贴文档——它能语义检索、拉取整页、生成依赖片段、检测版本。

:::tip[一句话价值]
把"查文档、找版本号、写依赖"这些重复动作,变成 Agent 的一次工具调用。
:::

## 能力总览

| 能力 | 说明 |
|------|------|
| **语义检索** | 基于本地嵌入(all-MiniLM-L6-v2,384 维)对全部文档做向量检索,离线可用、无需 API key |
| **整页获取** | 检索命中后拉取整页文档,供 Agent 完整配置某个能力 |
| **文档发现** | 列出全部文档页(路径、标题、URL) |
| **依赖片段** | 生成 Maven/Gradle 依赖片段,版本号自动填 Maven Central 最新发布版 |
| **版本检测** | 从 Maven Central 拉取最新版本,与当前版本比对,判断是否需要升级 |
| **索引新鲜度** | 报告文档索引的构建时间与陈旧度,支撑自动更新 |
| **一键安装** | 为 Claude / Codex / Cursor / 通用 Agent 写入 MCP 配置与技能文件 |

## 工作方式

技能以两种形态交付,可同时使用:

- **MCP 服务器** — 通过 stdio 暴露 7 个工具,Agent 运行时动态调用。
- **静态技能文件** — 为各家 Agent 写入约定文件(Claude 的 `SKILL.md`、Cursor 的 `.cursor/rules`、通用 `AGENTS.md`),让 Agent 无需运行时也能理解框架。

## 下一步

- [快捷安装](/skills/install) — 一条命令接入你的 Agent
- [使用指南](/skills/usage) — 7 个 MCP 工具与 CLI 命令详解
