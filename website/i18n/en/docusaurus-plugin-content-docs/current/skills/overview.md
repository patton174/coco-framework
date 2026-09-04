---
slug: /skills
sidebar_position: 1
title: Skills Overview
---

# Agent Skills

**Teach AI coding assistants to use Coco Framework directly.**

`@patton174/coco-agent-skills` is a Node package that turns the framework's documentation, version data, and integration steps into skills an AI agent (Claude Code, Codex, Cursor, and other agents) can call. You no longer paste docs by hand — the agent searches semantically, pulls whole pages, generates dependency snippets, and checks versions on its own.

:::tip[The value in one line]
Turn "look up the docs, find the version, write the dependency" into a single tool call.
:::

## Capabilities

| Capability | What it does |
|------------|--------------|
| **Semantic search** | Vector search across all docs using a local embedding model (bge-small-zh-v1.5, 512-dim). Bilingual, works offline, no API key |
| **Full page fetch** | Pull a complete page after a search hit, so the agent has everything needed to configure a feature |
| **Doc discovery** | List every doc page (path, title, URL) |
| **Dependency snippets** | Generate Maven/Gradle snippets with the version filled in from Maven Central |
| **Version checks** | Fetch the latest release from Maven Central and compare it against yours |
| **Index freshness** | Report when the doc index was built and how stale it is, so updates can be automated |
| **One-command install** | Write MCP config and skill files for Claude / Codex / Cursor / generic agents |

## Bilingual search

The index holds both the Chinese docs and their English translations, embedded with one bilingual model. That means:

- **Ask in either language** — 「如何开启分布式锁」 and `how to enable distributed lock` both land on the right page.
- **Answers come back in the language you asked in** — an English question returns the English page (its URL carries the `/en/` prefix), not a Chinese paragraph.
- **Pin a language when you need to** — pass `locale` (`zh-Hans` or `en`) to restrict results.

:::tip[Why not an English-only model]
The docs are ~95% Chinese. With an English-only embedding model, Chinese queries barely retrieved the right page at all — asking 「如何开启分布式锁」 ranked the correct doc 26th. A bilingual model ranks the right page first in both languages.
:::

## How it works

The skills ship in two forms, usable together:

- **MCP server** — exposes 7 tools over stdio for the agent to call at runtime.
- **Static skill files** — writes each agent's conventional file (`SKILL.md` for Claude, `.cursor/rules` for Cursor, a generic `AGENTS.md`) so the agent understands the framework even without the server running.

## Next

- [Quick install](/skills/install) — one command to wire up your agent
- [Usage guide](/skills/usage) — the 7 MCP tools and the CLI commands
