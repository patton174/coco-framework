---
slug: /skills/install
sidebar_position: 2
title: Quick Install
---

# Quick Install

One command writes the MCP config and skill files for your AI agent.

## Requirements

- Node.js ≥ 20
- The target agent installed (Claude Code / Codex / Cursor, etc.)

## Install command

```bash
npx @patton174/coco-agent-skills install --agent <target>
```

Values for `--agent`:

| Value | Target agent | What gets written |
|-------|--------------|-------------------|
| `claude` | Claude Code | `.mcp.json` + `.claude/skills/coco-framework/SKILL.md` |
| `codex` | Codex CLI | `.codex/config.toml` (appends an MCP block) |
| `cursor` | Cursor | `.cursor/mcp.json` + `.cursor/rules/coco-framework.mdc` |
| `generic` | Generic agents | `AGENTS.md` (skill section) |
| `all` | All of the above | Everything |

:::tip[Non-destructive writes]
The installer only appends or creates — it never overwrites your existing config blocks. The Codex TOML block is appended idempotently.
:::

## Choosing a target directory

Writes go to the current directory by default. Use `--target` to point at a project root:

```bash
npx @patton174/coco-agent-skills install --agent cursor --target ./my-service
```

## After installing

- **Claude Code**: restart, and the `coco` MCP server loads with its 7 tools available.
- **Cursor**: confirm the MCP server is enabled in settings.
- **Codex**: `.codex/config.toml` is read on next launch.

The first call to a search tool downloads the local embedding model (q8 weights, ~95 MB); everything after that works offline.

:::tip[Slow connection to huggingface.co]
If the model download is slow or fails, point it at a mirror first:

```bash
export HF_ENDPOINT=https://hf-mirror.com
```
:::

## Next

- [Usage guide](/skills/usage) — the tools and commands in detail
