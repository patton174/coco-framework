# @patton174/coco-agent-skills

Let AI coding agents use the [Coco Framework](https://patton174.github.io/coco-framework/) — semantic documentation search, latest-version detection from Maven Central, and one-command MCP install for popular agents.

Coco is a high-convention framework for Spring Boot Web services (Java 17+, Spring Boot 4.1, group `io.github.patton174`). This package gives an agent three abilities: search the docs by meaning, know the newest released version, and get itself wired in as an MCP server.

## Requirements

- Node.js >= 20

## Install deps

```bash
npm install
```

The first `build-index` (or first `search`) downloads the local embedding model `Xenova/all-MiniLM-L6-v2` (~23 MB) to your Transformers.js cache. No API keys, no network at query time once cached.

## MCP tools

The MCP server (`src/mcp-server.mjs`, stdio) exposes:

| Tool | Input | Returns |
|------|-------|---------|
| `coco_search_docs` | `{ query: string, topK?: number }` | Ranked doc snippets with title, heading, doc path, and a doc-site URL. |
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

It reads every Markdown file under `../../../website/docs`, strips frontmatter, chunks by heading/paragraph (~400–800 chars), embeds each chunk with `Xenova/all-MiniLM-L6-v2` (mean-pooling + L2 normalize), and writes 384-dim vectors. If the model download fails, it writes a clearly-marked empty placeholder index and sets `placeholder: true` in the meta so CI can rebuild.

## Tests

```bash
npm test
```

Tests run fully offline: `compareVersions`/metadata parsing are pure, and search-ranking tests inject fake embeddings so no model is downloaded.
