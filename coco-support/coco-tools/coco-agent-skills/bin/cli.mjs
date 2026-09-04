#!/usr/bin/env node
/**
 * Coco Agent Skills CLI。
 * <p>
 * 提供三条命令：{@code search}（语义检索文档）、{@code version}（查询/比较
 * Maven Central 最新版本）、{@code install}（为目标 AI Agent 写入 MCP 配置与技能文件）。
 * </p>
 * <p>
 * 仅依赖 Node 内置模块（fs/path/url）；codex 的 TOML 块为手写最小追加。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync, readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import { homedir } from 'node:os';

const HERE = dirname(fileURLToPath(import.meta.url));
const PKG_ROOT = resolve(HERE, '..');

const NODE_BIN = process.execPath;
const MCP_ENTRY = resolve(PKG_ROOT, 'src', 'mcp-server.mjs');

function pkgVersion() {
  try {
    return JSON.parse(readFileSync(resolve(PKG_ROOT, 'package.json'), 'utf8')).version ?? '0.0.0';
  } catch {
    return '0.0.0';
  }
}

/** 解析 `--flag value` 与 `--flag=value` 形式的参数。 */
function parseFlags(args) {
  const flags = {};
  const positional = [];
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg.startsWith('--')) {
      const eq = arg.indexOf('=');
      if (eq !== -1) {
        flags[arg.slice(2, eq)] = arg.slice(eq + 1);
      } else {
        const next = args[i + 1];
        if (next && !next.startsWith('--')) {
          flags[arg.slice(2)] = next;
          i += 1;
        } else {
          flags[arg.slice(2)] = true;
        }
      }
    } else {
      positional.push(arg);
    }
  }
  return { flags, positional };
}

async function readJsonIfExists(path) {
  if (!existsSync(path)) {
    return {};
  }
  try {
    const raw = await readFile(path, 'utf8');
    return raw.trim() ? JSON.parse(raw) : {};
  } catch (error) {
    throw new Error(`Existing config at ${path} is not valid JSON: ${error.message}`);
  }
}

async function writeJson(path, data) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(data, null, 2)}\n`);
}

/** MCP 服务器条目（command/args 形式，跨 Agent 通用）。 */
function mcpServerEntry() {
  return { command: NODE_BIN, args: [MCP_ENTRY] };
}

// ---- command: search ----
async function cmdSearch(query, flags) {
  if (!query) {
    process.stderr.write('Usage: coco-agent-skills search <query> [--topK N]\n');
    process.exitCode = 1;
    return;
  }
  const { search } = await import('../src/search.mjs');
  const topK = flags.topK ? Number.parseInt(flags.topK, 10) : 5;
  const results = await search(query, topK);
  if (!results.length) {
    process.stdout.write('No results. Index may be empty; run `npm run build-index`.\n');
    return;
  }
  for (const [i, r] of results.entries()) {
    const snippet = r.text.length > 300 ? `${r.text.slice(0, 300)}…` : r.text;
    process.stdout.write(
      `#${i + 1} [${r.score.toFixed(3)}] ${r.title} › ${r.heading}\n` +
        `  ${r.url}\n` +
        `  ${snippet.replace(/\n+/g, ' ')}\n\n`,
    );
  }
}

// ---- command: version ----
async function cmdVersion(flags) {
  const { fetchLatestVersion, compareVersions } = await import('../src/version.mjs');
  try {
    const info = await fetchLatestVersion();
    process.stdout.write(`latest:  ${info.latest}\n`);
    process.stdout.write(`release: ${info.release}\n`);
    process.stdout.write(`all:     ${info.versions.join(', ')}\n`);
    const current = typeof flags.current === 'string' ? flags.current : null;
    if (current) {
      const target = info.latest ?? info.release;
      const cmp = compareVersions(current, target);
      if (cmp < 0) {
        process.stdout.write(`\nUpdate available: ${current} → ${target}\n`);
      } else if (cmp === 0) {
        process.stdout.write(`\nUp to date (${current}).\n`);
      } else {
        process.stdout.write(`\nCurrent (${current}) is newer than published ${target}.\n`);
      }
    }
  } catch (error) {
    process.stderr.write(`Failed to fetch version: ${error.message}\n`);
    process.exitCode = 1;
  }
}

// ---- command: list ----
async function cmdList() {
  const { listDocs } = await import('../src/docs.mjs');
  const docs = await listDocs();
  if (!docs.length) {
    process.stdout.write('No docs indexed. Run `npm run build-index`.\n');
    return;
  }
  for (const d of docs) {
    process.stdout.write(`${d.docPath}  —  ${d.title}\n  ${d.url}\n`);
  }
}

// ---- command: doc ----
async function cmdDoc(docPath) {
  if (!docPath) {
    process.stderr.write('Usage: coco-agent-skills doc <path>   e.g. features/idempotency\n');
    process.exitCode = 1;
    return;
  }
  const { getDoc } = await import('../src/docs.mjs');
  const doc = await getDoc(docPath);
  if (!doc) {
    process.stderr.write(`No doc found for "${docPath}". Run \`coco-agent-skills list\` to see paths.\n`);
    process.exitCode = 1;
    return;
  }
  process.stdout.write(`# ${doc.title}\n${doc.url}\n\n${doc.text}\n`);
}

// ---- command: deps ----
async function cmdDeps(flags) {
  const { dependencySnippet } = await import('../src/deps.mjs');
  const style = typeof flags.style === 'string' ? flags.style : 'parent';
  const version = typeof flags.version === 'string' ? flags.version : undefined;
  try {
    const result = await dependencySnippet({ style, version });
    const note = result.resolvedFromCentral
      ? `<!-- version ${result.version} (latest from Maven Central) -->`
      : `<!-- version ${result.version} (pinned) -->`;
    process.stdout.write(`${note}\n${result.snippet}\n`);
  } catch (error) {
    process.stderr.write(`Failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}

// ---- command: status ----
async function cmdStatus() {
  const { indexStatus } = await import('../src/index-status.mjs');
  const status = await indexStatus();
  process.stdout.write(`${JSON.stringify(status, null, 2)}\n`);
  if (!status.healthy) {
    process.exitCode = 1;
  }
}

// ---- install helpers ----
const written = [];
function recordWrite(path, note) {
  written.push({ path, note });
}

async function copySkillFile(relSource, destPath) {
  const source = resolve(PKG_ROOT, relSource);
  const content = await readFile(source, 'utf8');
  await mkdir(dirname(destPath), { recursive: true });
  await writeFile(destPath, content);
  recordWrite(destPath, `copied from ${relSource}`);
}

// Claude Code: 合并 mcpServers 到用户 ~/.claude.json 或项目 .mcp.json。
async function installClaude(targetDir) {
  if (targetDir) {
    const path = join(targetDir, '.mcp.json');
    const config = await readJsonIfExists(path);
    config.mcpServers = config.mcpServers ?? {};
    config.mcpServers.coco = mcpServerEntry();
    await writeJson(path, config);
    recordWrite(path, 'merged mcpServers.coco (project-local)');
    await copySkillFile(
      join('skills', 'claude', 'SKILL.md'),
      join(targetDir, '.claude', 'skills', 'coco-framework', 'SKILL.md'),
    );
  } else {
    const path = join(homedir(), '.claude.json');
    const config = await readJsonIfExists(path);
    config.mcpServers = config.mcpServers ?? {};
    config.mcpServers.coco = mcpServerEntry();
    await writeJson(path, config);
    recordWrite(path, 'merged mcpServers.coco (user global)');
  }
}

// Cursor: .cursor/mcp.json + 规则文件。
async function installCursor(targetDir) {
  const base = targetDir ?? process.cwd();
  const path = join(base, '.cursor', 'mcp.json');
  const config = await readJsonIfExists(path);
  config.mcpServers = config.mcpServers ?? {};
  config.mcpServers.coco = mcpServerEntry();
  await writeJson(path, config);
  recordWrite(path, 'merged mcpServers.coco');
  await copySkillFile(
    join('skills', 'cursor', 'coco-framework.mdc'),
    join(base, '.cursor', 'rules', 'coco-framework.mdc'),
  );
}

// Codex: 追加 [mcp_servers.coco] TOML 块到 ~/.codex/config.toml（幂等）。
async function installCodex() {
  const path = join(homedir(), '.codex', 'config.toml');
  let existing = existsSync(path) ? await readFile(path, 'utf8') : '';
  if (/^\s*\[mcp_servers\.coco\]/m.test(existing)) {
    recordWrite(path, 'already contains [mcp_servers.coco]; left unchanged');
    return;
  }
  const argsToml = JSON.stringify([MCP_ENTRY]);
  const block =
    `\n[mcp_servers.coco]\n` +
    `command = ${JSON.stringify(NODE_BIN)}\n` +
    `args = ${argsToml}\n`;
  if (existing && !existing.endsWith('\n')) {
    existing += '\n';
  }
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, existing + block);
  recordWrite(path, 'appended [mcp_servers.coco] block');
}

// 中文/跨 Agent 通用：AGENTS.md 片段 + .mcp.json（Tongyi Lingma / Trae / CodeBuddy）。
async function installGeneric(targetDir) {
  const base = targetDir ?? process.cwd();
  const mcpPath = join(base, '.mcp.json');
  const config = await readJsonIfExists(mcpPath);
  config.mcpServers = config.mcpServers ?? {};
  config.mcpServers.coco = mcpServerEntry();
  await writeJson(mcpPath, config);
  recordWrite(mcpPath, 'merged mcpServers.coco (cross-agent .mcp.json)');
  await copySkillFile(join('skills', 'agents', 'AGENTS.md'), join(base, 'AGENTS.md'));
}

async function cmdInstall(flags) {
  const agent = typeof flags.agent === 'string' ? flags.agent.toLowerCase() : null;
  const targetDir = typeof flags.target === 'string' ? resolve(flags.target) : null;
  if (!agent) {
    process.stderr.write(
      'Usage: coco-agent-skills install --agent <claude|codex|cursor|generic|all> [--target <dir>]\n',
    );
    process.exitCode = 1;
    return;
  }
  written.length = 0;
  const agents = agent === 'all' ? ['claude', 'codex', 'cursor', 'generic'] : [agent];
  for (const a of agents) {
    switch (a) {
      case 'claude':
        await installClaude(targetDir);
        break;
      case 'cursor':
        await installCursor(targetDir);
        break;
      case 'codex':
        await installCodex();
        break;
      case 'generic':
      case 'lingma':
      case 'trae':
      case 'codebuddy':
        await installGeneric(targetDir);
        break;
      default:
        process.stderr.write(`Unknown agent: ${a}\n`);
        process.exitCode = 1;
        return;
    }
  }
  // 中文 Agent 兼容：为 claude/cursor/codex 也补写通用 .mcp.json + AGENTS.md（幂等合并）。
  if (agent !== 'generic' && agent !== 'all') {
    await installGeneric(targetDir);
  }
  process.stdout.write('Wrote:\n');
  for (const w of written) {
    process.stdout.write(`  ${w.path}\n    (${w.note})\n`);
  }
  process.stdout.write(
    `\nMCP server command: ${NODE_BIN} ${MCP_ENTRY}\nRestart your agent to pick up the new server.\n`,
  );
}

function printHelp() {
  process.stdout.write(`coco-agent-skills v${pkgVersion()} — let AI coding agents use the Coco Framework.

Usage:
  coco-agent-skills <command> [options]

Commands:
  search <query> [--topK N]        Semantic search over Coco Framework docs.
  list                             List all indexed doc pages (path, title, URL).
  doc <path>                       Print a full doc page, e.g. features/idempotency.
  deps [--style parent|bom|gradle] [--version X]
                                   Print a dependency snippet with the latest version filled in.
  status                           Report doc-index freshness (exit 1 if unhealthy).
  version [--current X]            Print latest Maven Central version; compare if --current given.
  install --agent <name> [--target <dir>]
                                   Write MCP config + skill files for an agent.
                                   agents: claude | codex | cursor | generic | all
  --help, -h                       Show this help.

Examples:
  coco-agent-skills search "how do I enable idempotency"
  coco-agent-skills doc features/idempotency
  coco-agent-skills deps --style bom
  coco-agent-skills status
  coco-agent-skills version --current 2.0.1
  coco-agent-skills install --agent claude
  coco-agent-skills install --agent cursor --target ./my-project
`);
}

async function main() {
  const argv = process.argv.slice(2);
  if (argv.length === 0 || argv.includes('--help') || argv.includes('-h')) {
    printHelp();
    return;
  }
  const [command, ...rest] = argv;
  const { flags, positional } = parseFlags(rest);
  switch (command) {
    case 'search':
      await cmdSearch(positional.join(' '), flags);
      break;
    case 'list':
      await cmdList();
      break;
    case 'doc':
      await cmdDoc(positional.join(' '));
      break;
    case 'deps':
      await cmdDeps(flags);
      break;
    case 'status':
      await cmdStatus();
      break;
    case 'version':
      await cmdVersion(flags);
      break;
    case 'install':
      await cmdInstall(flags);
      break;
    default:
      process.stderr.write(`Unknown command: ${command}\n\n`);
      printHelp();
      process.exitCode = 1;
  }
}

// Compare resolved real paths, not raw URL strings. Under nvm-for-windows the
// global bin lives behind a symlink (…/nodejs → …/nvm/vX): Node resolves ESM
// import.meta.url to the real path but leaves process.argv[1] as the symlink,
// so a plain href compare is always false and main() never runs. realpathSync
// collapses both to the same target; fall back to the raw compare if either
// path can't be stat'd (e.g. piped from stdin).
function isInvokedDirectly() {
  const entry = process.argv[1];
  if (!entry) {
    return false;
  }
  try {
    return realpathSync(fileURLToPath(import.meta.url)) === realpathSync(entry);
  } catch {
    return import.meta.url === pathToFileURL(entry).href;
  }
}

if (isInvokedDirectly()) {
  main().catch((error) => {
    process.stderr.write(`${error.stack ?? error.message}\n`);
    process.exitCode = 1;
  });
}

export { parseFlags, mcpServerEntry, cmdInstall };
