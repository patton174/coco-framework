---
slug: /skills/install
sidebar_position: 2
title: 快捷安装
---

# 快捷安装

一条命令为你的 AI Agent 写入 MCP 配置与技能文件。

## 前置要求

- Node.js ≥ 20
- 目标 Agent 已安装(Claude Code / Codex / Cursor 等)

## 安装命令

```bash
npx @patton174/coco-agent-skills install --agent <目标>
```

`--agent` 可选值:

| 值 | 目标 Agent | 写入内容 |
|------|-----------|---------|
| `claude` | Claude Code | `.mcp.json` + `.claude/skills/coco-framework/SKILL.md` |
| `codex` | Codex CLI | `.codex/config.toml`(追加 MCP 块) |
| `cursor` | Cursor | `.cursor/mcp.json` + `.cursor/rules/coco-framework.mdc` |
| `generic` | 通用/国产 Agent | `AGENTS.md`(技能片段) |
| `all` | 以上全部 | 全部写入 |

:::tip[非破坏式写入]
安装器只追加或创建,不覆盖你已有的配置块;codex 的 TOML 块为幂等追加。
:::

## 指定目标目录

默认写入当前目录,可用 `--target` 指定项目根:

```bash
npx @patton174/coco-agent-skills install --agent cursor --target ./my-service
```

## 安装后

- **Claude Code**:重启后 `coco` MCP 服务器自动加载,7 个工具可用。
- **Cursor**:在设置里确认 MCP 服务器已启用。
- **Codex**:下次启动读取 `.codex/config.toml`。

首次调用检索类工具时会下载本地嵌入模型(q8 量化权重,约 95MB),之后离线可用。

:::tip[国内网络]
若直连 huggingface.co 缓慢或失败,设置镜像后再调用:

```bash
export HF_ENDPOINT=https://hf-mirror.com
```
:::

## 下一步

- [使用指南](/skills/usage) — 工具与命令详解
