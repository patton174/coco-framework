# @patton174/coco-agent-skills

Let AI coding agents use the [Coco Framework](https://patton174.github.io/coco-framework/) — semantic documentation search, latest-version detection from Maven Central, and one-command MCP install for popular agents.

Coco is a high-convention framework for Spring Boot Web services (Java 17+, Spring Boot 4.1, group `io.github.patton174`). This package gives an agent three abilities: search the docs by meaning, know the newest released version, and get itself wired in as an MCP server.

## Requirements

- Node.js >= 20

## Install deps

```bash
npm install
```

The first `build-index` (or first `search`) downloads the local embedding model `Xenova/bge-small-zh-v1.5` (q8 weights, ~95 MB) to your Transformers.js cache. No API keys, no network at query time once cached. Behind a slow link to huggingface.co, set `HF_ENDPOINT=https://hf-mirror.com` to pull from a mirror.

The model is bilingual by design. The docs are ~95% Chinese, and an English-only model scored 0/4 on Chinese queries in testing (asking 「如何开启分布式锁」 ranked the correct page 26th and returned generic overview prose instead). `bge-small-zh-v1.5` hits rank 1 on both Chinese and English queries, so one index serves both languages.

## MCP tools

The MCP server (`src/mcp-server.mjs`, stdio) exposes:

| Tool | Input | Returns |
|------|-------|---------|
| `coco_search_docs` | `{ query: string, topK?: number, locale?: "zh-Hans" \| "en" }` | Ranked doc snippets with title, heading, doc path, locale, and a doc-site URL. Ask in either language; pass `locale` only to pin the result language. |
| `coco_get_doc` | `{ docPath: string }` | Full text of one doc page, reassembled from the index. |
| `coco_list_docs` | — | Every doc page (path, title, URL). |
| `coco_dependency_snippet` | `{ style?: parent\|bom\|gradle, version? }` | Ready-to-paste build snippet with the latest version auto-filled. |
| `coco_get_latest_version` | — | `{ latest, release, all: string[] }` from Maven Central. |
| `coco_check_version` | `{ current: string }` | Whether an update exists and the newest version. |
| `coco_index_status` | — | Index freshness: chunk count, age, rebuild recommendation. |

Run it directly:

```bash
npm run mcp
```

## CLI

```bash
# semantic search over the docs
node bin/cli.mjs search "how do I enable idempotency" --topK 5
node bin/cli.mjs search "怎么开启幂等" --topK 3
node bin/cli.mjs search "idempotency" --locale en    # pin the result language

# list all doc pages, or print one in full
node bin/cli.mjs list
node bin/cli.mjs doc features/idempotency

# dependency snippet with the latest version auto-filled
node bin/cli.mjs deps --style bom

# index freshness (exit 1 if unhealthy — useful in CI)
node bin/cli.mjs status

# latest version, optionally compared to yours
node bin/cli.mjs version --current 2.0.1

# wire up an agent
node bin/cli.mjs install --agent <claude|codex|cursor|generic|all> [--target <dir>]
```

### install — what gets written

Writes are idempotent (existing config is merged, not clobbered).

- `--agent claude` — merges `mcpServers.coco` into `~/.claude.json` (or a project `.mcp.json` when `--target` is given).
- `--agent cursor` — writes `.cursor/mcp.json` and `.cursor/rules/coco-framework.mdc` (in `--target` or the current dir).
- `--agent codex` — appends an `[mcp_servers.coco]` block to `~/.codex/config.toml`.
- `--agent generic` — writes a project `.mcp.json` (the emerging cross-agent standard, picked up by Tongyi Lingma / Trae / CodeBuddy) plus an `AGENTS.md` skill fragment.
- Every non-generic install also drops the cross-agent `.mcp.json` + `AGENTS.md` so Chinese agents can pick it up too.
- `--agent all` — runs claude, codex, cursor, and generic.

The generated server command is `node <abs-path>/src/mcp-server.mjs`. Restart your agent afterward.

## Skill files

Static, human-readable guidance lives under `skills/` and is copied by `install`:

- `skills/claude/SKILL.md` — Claude Code skill doc.
- `skills/cursor/coco-framework.mdc` — Cursor rules file.
- `skills/agents/AGENTS.md` — generic cross-agent fragment.

## Rebuilding the docs index

The search index is committed at `data/doc-index.json` (with `data/doc-index.meta.json`). Rebuild it after the framework docs change:

```bash
npm run build-index
```

It reads every Markdown file for both locales — Chinese from `../../../website/docs` and English from `../../../website/i18n/en/docusaurus-plugin-content-docs/current` — strips frontmatter, chunks by heading/paragraph (~400–800 chars), embeds each chunk with `Xenova/bge-small-zh-v1.5` (mean-pooling + L2 normalize), and writes 512-dim vectors. Both languages land in one index; every entry carries a `locale` field, and chunk ids are prefixed with it (`en:features/lock.md#0`) so the two versions of a page don't collide. A missing translation directory is a warning, not an error. If the model download fails, it writes a clearly-marked empty placeholder index and sets `placeholder: true` in the meta so CI can rebuild.

## Tests

```bash
npm test
```

Tests run fully offline: `compareVersions`/metadata parsing are pure, and search-ranking tests inject fake embeddings so no model is downloaded.
