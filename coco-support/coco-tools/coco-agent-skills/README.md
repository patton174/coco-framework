# @patton174/coco-agent-skills

Let AI coding agents use the [Coco Framework](https://patton174.github.io/coco-framework/) — semantic documentation search, latest-version detection from Maven Central, and one-command MCP install for popular agents.

Coco is a high-convention framework for Spring Boot Web services (Java 17+, Spring Boot 4.1, group `io.github.patton174`). This package gives an agent three abilities: search the docs by meaning, know the newest released version, and get itself wired in as an MCP server.

**📖 Full documentation: [Agent skills](https://patton174.github.io/coco-framework/skills)** — [install guide](https://patton174.github.io/coco-framework/skills/install) · [tool and CLI reference](https://patton174.github.io/coco-framework/skills/usage)

## Quick start

Requires Node.js >= 20.

```bash
# wire up your agent (claude | codex | cursor | generic | all)
npx @patton174/coco-agent-skills install --agent claude

# or use the CLI directly
npx @patton174/coco-agent-skills search "how do I enable idempotency"
npx @patton174/coco-agent-skills search "如何开启幂等"
```

The MCP server exposes 7 tools over stdio: `coco_search_docs`, `coco_get_doc`, `coco_list_docs`, `coco_dependency_snippet`, `coco_get_latest_version`, `coco_check_version`, `coco_index_status`. See the [usage guide](https://patton174.github.io/coco-framework/skills/usage) for inputs and returns.

## Embedding model

The first search downloads `Xenova/bge-small-zh-v1.5` (q8 weights, ~95 MB) to your Transformers.js cache. No API keys, and no network at query time once cached. Behind a slow link to huggingface.co, set `HF_ENDPOINT=https://hf-mirror.com`.

The model is bilingual by design. The docs are ~95% Chinese, and an English-only model scored 0/4 on Chinese queries in testing — asking 「如何开启分布式锁」 ranked the correct page 26th and returned generic overview prose instead. `bge-small-zh-v1.5` hits rank 1 on both Chinese and English queries, so one index serves both languages.

---

## Development

Everything below is for working on this package itself.

### Layout

- `src/` — MCP server (`mcp-server.mjs`) and the modules behind each tool.
- `bin/cli.mjs` — CLI entry point.
- `data/` — the committed search index and its metadata.
- `skills/` — static agent guidance copied by `install`: `claude/SKILL.md`, `cursor/coco-framework.mdc`, `agents/AGENTS.md`.

```bash
npm install
npm run mcp     # run the MCP server directly
```

### Rebuilding the docs index

The index is committed at `data/doc-index.json` (with `data/doc-index.meta.json`). Rebuild it after the framework docs change:

```bash
npm run build-index
```

It reads every Markdown file for both locales — Chinese from `../../../website/docs` and English from `../../../website/i18n/en/docusaurus-plugin-content-docs/current` — strips frontmatter, chunks by heading/paragraph (~400–800 chars), embeds each chunk (mean-pooling + L2 normalize), and writes 512-dim vectors rounded to 6 decimals.

Both languages land in one index. Every entry carries a `locale`, and chunk ids are prefixed with it (`en:features/lock.md#0`) so the two versions of a page don't collide. A missing translation directory is a warning, not an error. If the model download fails, the script writes a clearly-marked empty placeholder index and sets `placeholder: true` in the meta so CI can rebuild.

### Tests

```bash
npm test
```

Tests run fully offline: version comparison and metadata parsing are pure, and search-ranking tests inject fake embeddings so no model is downloaded.
