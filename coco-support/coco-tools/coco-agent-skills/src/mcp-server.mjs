/**
 * Coco Agent Skills 的 MCP 服务器（stdio）。
 * <p>
 * 暴露三个工具：{@code coco_search_docs}（语义检索文档）、
 * {@code coco_get_latest_version}（拉取 Maven Central 最新版本）、
 * {@code coco_check_version}（比较当前版本与最新版本）。
 * </p>
 * <p>
 * 所有工具都做了健壮的错误处理：单个工具出错时返回 {@code isError} 结果，
 * 不会让整个服务器崩溃。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

import { readFile } from 'node:fs/promises';
import { realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

import { search } from './search.mjs';
import { fetchLatestVersion, checkVersion } from './version.mjs';
import { getDoc, listDocs } from './docs.mjs';
import { dependencySnippet } from './deps.mjs';
import { indexStatus } from './index-status.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));

async function readPackageVersion() {
  try {
    const pkg = JSON.parse(await readFile(resolve(HERE, '..', 'package.json'), 'utf8'));
    return pkg.version ?? '0.0.0';
  } catch {
    return '0.0.0';
  }
}

/** 把任意值转为工具文本结果。 */
function textResult(text) {
  return { content: [{ type: 'text', text: String(text) }] };
}

/** 把错误转为工具错误结果（不抛出）。 */
function errorResult(message) {
  return { content: [{ type: 'text', text: `Error: ${message}` }], isError: true };
}

/**
 * 格式化检索结果为可读文本。
 * @param {Array<object>} results 检索结果
 * @returns {string}
 */
function formatSearchResults(results) {
  if (!results.length) {
    return 'No matching documentation found. The index may be empty; run `npm run build-index`.';
  }
  return results
    .map((r, i) => {
      const score = r.score.toFixed(3);
      const snippet = r.text.length > 500 ? `${r.text.slice(0, 500)}…` : r.text;
      return [
        `#${i + 1} [${score}] ${r.title} › ${r.heading}`,
        `doc: ${r.docPath}`,
        `url: ${r.url}`,
        '',
        snippet,
      ].join('\n');
    })
    .join('\n\n---\n\n');
}

/**
 * 构建已注册工具的 MCP 服务器实例（便于测试）。
 * @returns {Promise<McpServer>}
 */
export async function createServer() {
  const version = await readPackageVersion();
  const server = new McpServer({ name: 'coco-agent-skills', version });

  server.registerTool(
    'coco_search_docs',
    {
      title: 'Search Coco Framework docs',
      description:
        'Semantic search over the Coco Framework documentation. Returns ranked snippets with doc paths and doc-site URLs. Use this to answer how to enable/configure a feature (idempotency, tenant, rate-limit, storage, etc.).',
      inputSchema: {
        query: z.string().describe('Natural-language question or keywords'),
        topK: z.number().int().min(1).max(20).optional().describe('Number of results (default 5)'),
      },
    },
    async ({ query, topK }) => {
      try {
        const results = await search(query, topK ?? 5);
        return textResult(formatSearchResults(results));
      } catch (error) {
        return errorResult(`search failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_get_latest_version',
    {
      title: 'Get latest Coco Framework version',
      description:
        'Fetch the latest released version of Coco Framework from Maven Central. Returns latest, release, and all published versions.',
      inputSchema: {},
    },
    async () => {
      try {
        const info = await fetchLatestVersion();
        return textResult(
          JSON.stringify(
            { latest: info.latest, release: info.release, all: info.versions },
            null,
            2,
          ),
        );
      } catch (error) {
        return errorResult(`version lookup failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_check_version',
    {
      title: 'Check for a Coco Framework update',
      description:
        'Compare a current Coco Framework version against the latest on Maven Central. Returns whether an update exists and the newest version.',
      inputSchema: {
        current: z.string().describe('The Coco Framework version currently in use, e.g. 2.0.1'),
      },
    },
    async ({ current }) => {
      try {
        const result = await checkVersion(current);
        return textResult(
          JSON.stringify(
            {
              current: result.current,
              latest: result.latest,
              updateAvailable: result.updateAvailable,
              newest: result.latest ?? result.release,
            },
            null,
            2,
          ),
        );
      } catch (error) {
        return errorResult(`version check failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_get_doc',
    {
      title: 'Get a full Coco doc page',
      description:
        'Return the full text of a single Coco documentation page by path (e.g. "features/idempotency" or "getting-started"). Use after coco_search_docs when you need the complete page, not just a snippet.',
      inputSchema: {
        docPath: z.string().describe('Doc path, e.g. features/tenant or overview'),
      },
    },
    async ({ docPath }) => {
      try {
        const doc = await getDoc(docPath);
        if (!doc) {
          return errorResult(`No doc found for "${docPath}". Use coco_list_docs to see available paths.`);
        }
        return textResult(`# ${doc.title}\n${doc.url}\n\n${doc.text}`);
      } catch (error) {
        return errorResult(`get doc failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_list_docs',
    {
      title: 'List all Coco doc pages',
      description:
        'List every Coco documentation page (path, title, doc-site URL). Use to discover what documentation exists before searching or fetching.',
      inputSchema: {},
    },
    async () => {
      try {
        const docs = await listDocs();
        if (!docs.length) {
          return errorResult('Index is empty; run `npm run build-index`.');
        }
        const lines = docs.map((d) => `- ${d.docPath}  —  ${d.title}\n  ${d.url}`);
        return textResult(lines.join('\n'));
      } catch (error) {
        return errorResult(`list docs failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_dependency_snippet',
    {
      title: 'Generate a Coco dependency snippet',
      description:
        'Generate a ready-to-paste Maven/Gradle dependency snippet for adding Coco Framework, with the version auto-filled from the latest Maven Central release. style: parent (inherit coco-parent), bom (import coco-dependencies), or gradle.',
      inputSchema: {
        style: z.enum(['parent', 'bom', 'gradle']).optional().describe('Integration style (default parent)'),
        version: z.string().optional().describe('Pin a specific version; omit to use the latest release'),
      },
    },
    async ({ style, version }) => {
      try {
        const result = await dependencySnippet({ style, version });
        const note = result.resolvedFromCentral
          ? `# version ${result.version} (latest from Maven Central)`
          : `# version ${result.version} (pinned)`;
        return textResult(`${note}\n\n${result.snippet}`);
      } catch (error) {
        return errorResult(`dependency snippet failed: ${error.message}`);
      }
    },
  );

  server.registerTool(
    'coco_index_status',
    {
      title: 'Report doc-index freshness',
      description:
        'Report the semantic-search index status: chunk count, build time, age in days, and whether a rebuild is recommended. Use to decide if docs are stale and the index should be regenerated.',
      inputSchema: {},
    },
    async () => {
      try {
        const status = await indexStatus();
        return textResult(JSON.stringify(status, null, 2));
      } catch (error) {
        return errorResult(`index status failed: ${error.message}`);
      }
    },
  );

  return server;
}

async function main() {
  const server = await createServer();
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // 保持进程存活；stdio 传输负责读写。
  process.stderr.write('coco-agent-skills MCP server running on stdio\n');
}

// Decide whether this module is the entry point node was told to run, resolving
// BOTH sides to real filesystem paths in the SAME form before comparing.
// Under nvm-for-windows the global bin sits behind a symlink (…/nodejs →
// …/nvm/vX): Node resolves ESM import.meta.url to the real path but leaves
// process.argv[1] as the symlink, so a raw href compare is always false and the
// server never starts. Normalising a file: URL to a path, then realpathSync
// (with the raw path as fallback when it can't be stat'd), keeps the two
// operands in one representation, so the compare can't drift path-vs-URL.
function toRealPath(value) {
  const asPath = value.startsWith('file:') ? fileURLToPath(value) : value;
  try {
    return realpathSync(asPath);
  } catch {
    return asPath;
  }
}

function isInvokedDirectly() {
  const entry = process.argv[1];
  if (!entry) {
    return false;
  }
  return toRealPath(import.meta.url) === toRealPath(entry);
}

if (isInvokedDirectly()) {
  main().catch((error) => {
    process.stderr.write(`fatal: ${error.stack ?? error.message}\n`);
    process.exitCode = 1;
  });
}
