---
slug: /skills/usage
sidebar_position: 3
title: Usage Guide
---

# Usage Guide

The skills provide 7 MCP tools (called by the agent at runtime) and a set of CLI commands (for humans and CI).

## MCP tools

The MCP server is named `coco` and exposes these tools over stdio:

| Tool | Input | Returns |
|------|-------|---------|
| `coco_search_docs` | `{ query, topK?, locale? }` | Ranked doc snippets with title, heading, locale, and doc-site URL |
| `coco_get_doc` | `{ docPath }` | The full text of one page (e.g. `features/idempotency`) |
| `coco_list_docs` | — | Every doc page (path, title, URL) |
| `coco_dependency_snippet` | `{ style?, version? }` | A Maven/Gradle snippet with the latest release filled in |
| `coco_get_latest_version` | — | The latest release on Maven Central |
| `coco_check_version` | `{ current }` | Compares your version against the latest and flags upgrades |
| `coco_index_status` | — | Doc-index freshness and a rebuild recommendation |

### Typical sequence

When configuring a capability, the ideal chain is:

1. `coco_search_docs` to locate the relevant page →
2. `coco_get_doc` to pull it in full →
3. `coco_dependency_snippet` to generate the build dependency.

Versions are always resolved through a tool, never hand-written.

## CLI commands

Everything works from the command line without MCP:

```bash
# semantic search (ask in either language)
npx @patton174/coco-agent-skills search "how to enable idempotency" --topK 5
npx @patton174/coco-agent-skills search "如何开启幂等"

# restrict results to one language
npx @patton174/coco-agent-skills search "idempotency" --locale en

# list every doc page / print one in full
npx @patton174/coco-agent-skills list
npx @patton174/coco-agent-skills doc features/idempotency

# dependency snippet (version auto-filled)
npx @patton174/coco-agent-skills deps --style bom

# index freshness (exit 1 if unhealthy — useful in CI)
npx @patton174/coco-agent-skills status

# version check, optionally compared to yours
npx @patton174/coco-agent-skills version --current 2.0.1
```

## Dependency snippet styles

`deps --style` supports three integration shapes:

| Style | Description |
|-------|-------------|
| `parent` | Inherit the `coco-parent` parent POM |
| `bom` | Import the `coco-dependencies` BOM |
| `gradle` | Gradle dependency declaration |

## Source of version truth

The version in the framework's own POM is a `1.0.0-SNAPSHOT` placeholder and is **not trustworthy**. The only reliable source is `maven-metadata.xml` on Maven Central — every version tool reads from there.

## Index updates

The doc index is generated at package build time and ships with the embedding vectors included. `coco_index_status` (or the `status` command) reports the docs commit and build time behind the index; rebuild it to pick up documentation changes.
