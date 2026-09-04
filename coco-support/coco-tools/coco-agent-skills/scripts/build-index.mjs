/**
 * Coco 文档索引构建器（构建期运行）。
 * <p>
 * 读取 {@code website/docs} 下全部 Markdown，剥离 frontmatter，按标题/段落切成
 * ~400-800 字符的块，用 {@code Xenova/all-MiniLM-L6-v2} 嵌入每个块，写出
 * {@code data/doc-index.json} 与 {@code data/doc-index.meta.json}。
 * </p>
 * <p>
 * 模型首次运行会下载（~23MB），可能较慢；网络失败时写出占位索引并给出提示。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

import { readFile, writeFile, mkdir, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative, join } from 'node:path';
import { execSync } from 'node:child_process';

import { EMBEDDING_MODEL } from '../src/search.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const PKG_ROOT = resolve(HERE, '..');
const DOCS_ROOT = resolve(PKG_ROOT, '..', '..', '..', 'website', 'docs');
const DATA_DIR = resolve(PKG_ROOT, 'data');
const INDEX_PATH = join(DATA_DIR, 'doc-index.json');
const META_PATH = join(DATA_DIR, 'doc-index.meta.json');
const DIMENSION = 384;

/**
 * 递归收集目录下所有 .md 文件。
 * @param {string} dir 根目录
 * @returns {Promise<string[]>} 绝对路径列表
 */
async function collectMarkdown(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectMarkdown(full)));
    } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) {
      files.push(full);
    }
  }
  return files.sort();
}

/**
 * 剥离 YAML frontmatter，返回 {frontmatter, body}。
 * @param {string} raw 原始文件内容
 */
function stripFrontmatter(raw) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(raw);
  if (!match) {
    return { frontmatter: {}, body: raw };
  }
  const frontmatter = {};
  for (const line of match[1].split(/\r?\n/)) {
    const kv = /^([A-Za-z0-9_-]+):\s*(.*)$/.exec(line);
    if (kv) {
      frontmatter[kv[1]] = kv[2].replace(/^["']|["']$/g, '').trim();
    }
  }
  return { frontmatter, body: raw.slice(match[0].length) };
}

/**
 * 将文档正文按标题分段，再把过长段落切成 ~400-800 字符的块。
 * @param {string} body 去除 frontmatter 的正文
 * @param {string} docTitle 文档标题
 * @returns {Array<{heading:string, text:string}>}
 */
function chunkBody(body, docTitle) {
  const lines = body.split(/\r?\n/);
  const sections = [];
  let currentHeading = docTitle;
  let buffer = [];

  const flush = () => {
    const text = buffer.join('\n').trim();
    if (text) {
      sections.push({ heading: currentHeading, text });
    }
    buffer = [];
  };

  for (const line of lines) {
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      flush();
      currentHeading = heading[2].trim();
    } else {
      buffer.push(line);
    }
  }
  flush();

  // 二次切分：把过长 section 按段落聚合到 ~400-800 字符的块。
  const chunks = [];
  for (const section of sections) {
    const paragraphs = section.text.split(/\r?\n\s*\r?\n/).map((p) => p.trim()).filter(Boolean);
    let acc = '';
    for (const paragraph of paragraphs) {
      if (acc && acc.length + paragraph.length + 2 > 800) {
        chunks.push({ heading: section.heading, text: acc });
        acc = paragraph;
      } else {
        acc = acc ? `${acc}\n\n${paragraph}` : paragraph;
      }
      // 单段就已很长时，硬切成 ~800 字符窗口。
      while (acc.length > 900) {
        chunks.push({ heading: section.heading, text: acc.slice(0, 800) });
        acc = acc.slice(800);
      }
    }
    if (acc.trim()) {
      chunks.push({ heading: section.heading, text: acc.trim() });
    }
  }
  return chunks.filter((c) => c.text.length >= 20);
}

/**
 * 尝试读取 website docs 的 git commit（可选，失败忽略）。
 * @returns {string|undefined}
 */
function tryGitCommit() {
  try {
    return execSync('git rev-parse --short HEAD', {
      cwd: DOCS_ROOT,
      stdio: ['ignore', 'pipe', 'ignore'],
    })
      .toString()
      .trim();
  } catch {
    return undefined;
  }
}

async function main() {
  await mkdir(DATA_DIR, { recursive: true });
  try {
    await stat(DOCS_ROOT);
  } catch {
    throw new Error(`Docs root not found at ${DOCS_ROOT}`);
  }

  const files = await collectMarkdown(DOCS_ROOT);
  console.error(`[build-index] found ${files.length} markdown files under ${DOCS_ROOT}`);

  const rawChunks = [];
  for (const file of files) {
    const raw = await readFile(file, 'utf8');
    const { frontmatter, body } = stripFrontmatter(raw);
    const docPath = relative(DOCS_ROOT, file).replace(/\\/g, '/');
    const title = frontmatter.title || docPath.replace(/\.md$/i, '');
    const chunks = chunkBody(body, title);
    chunks.forEach((chunk, i) => {
      rawChunks.push({
        id: `${docPath}#${i}`,
        docPath,
        title,
        heading: chunk.heading,
        text: chunk.text,
      });
    });
  }
  console.error(`[build-index] produced ${rawChunks.length} chunks; embedding...`);

  const commit = tryGitCommit();
  let embedded;
  let fellBack = false;
  try {
    const { getExtractor } = await import('../src/search.mjs');
    const extractor = await getExtractor();
    embedded = [];
    for (let i = 0; i < rawChunks.length; i += 1) {
      const chunk = rawChunks[i];
      const output = await extractor(chunk.text, { pooling: 'mean', normalize: true });
      embedded.push({ ...chunk, embedding: Array.from(output.data) });
      if ((i + 1) % 10 === 0 || i + 1 === rawChunks.length) {
        console.error(`[build-index] embedded ${i + 1}/${rawChunks.length}`);
      }
    }
  } catch (error) {
    fellBack = true;
    console.error(`[build-index] EMBEDDING FAILED: ${error.message}`);
    console.error('[build-index] writing PLACEHOLDER index; rebuild in CI with network access.');
    embedded = [];
  }

  await writeFile(INDEX_PATH, JSON.stringify(embedded, null, 0));
  const meta = {
    model: EMBEDDING_MODEL,
    dimension: DIMENSION,
    chunkCount: embedded.length,
    totalChunksDiscovered: rawChunks.length,
    builtAt: new Date().toISOString(),
    frameworkDocsCommit: commit,
    placeholder: fellBack,
    note: fellBack
      ? 'Embedding model unavailable at build time. Index is empty; run `npm run build-index` in an environment with network access to populate it.'
      : undefined,
  };
  await writeFile(META_PATH, JSON.stringify(meta, null, 2));
  console.error(`[build-index] wrote ${INDEX_PATH} (${embedded.length} vectors) and ${META_PATH}`);
  if (fellBack) {
    process.exitCode = 0; // 不视为致命：占位索引已写出。
  }
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
