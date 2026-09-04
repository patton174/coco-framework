/**
 * Coco 文档索引构建器（构建期运行）。
 * <p>
 * 读取中文（{@code website/docs}）与英文（{@code website/i18n/en/...}）两套
 * Markdown，剥离 frontmatter，按标题/段落切成 ~400-800 字符的块，用
 * {@link EMBEDDING_MODEL} 嵌入每个块，写出 {@code data/doc-index.json} 与
 * {@code data/doc-index.meta.json}。两种语言共用一个索引，每条带 {@code locale}。
 * </p>
 * <p>
 * 模型首次运行会下载（q8 量化权重，~95MB），可能较慢；网络失败时写出占位索引
 * 并给出提示。国内网络可设 {@code HF_ENDPOINT=https://hf-mirror.com} 加速。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

import { readFile, writeFile, mkdir, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative, join } from 'node:path';
import { execSync } from 'node:child_process';

import { EMBEDDING_MODEL, EMBEDDING_DIMENSION } from '../src/search.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const PKG_ROOT = resolve(HERE, '..');
const WEBSITE_ROOT = resolve(PKG_ROOT, '..', '..', '..', 'website');
/**
 * 每种语言的文档根目录。中文是 Docusaurus 默认语言，正文直接放 {@code docs/}；
 * 英文译文放在 {@code i18n/en/...} 下。两者进同一个索引（同模型、分数可比），
 * 每条 chunk 带 {@code locale} 区分。缺译的页面只会少一条英文记录，中文仍可命中。
 */
const DOC_SOURCES = [
  { locale: 'zh-Hans', root: resolve(WEBSITE_ROOT, 'docs') },
  {
    locale: 'en',
    root: resolve(
      WEBSITE_ROOT,
      'i18n',
      'en',
      'docusaurus-plugin-content-docs',
      'current',
    ),
  },
];
const DATA_DIR = resolve(PKG_ROOT, 'data');
const INDEX_PATH = join(DATA_DIR, 'doc-index.json');
const META_PATH = join(DATA_DIR, 'doc-index.meta.json');

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
      cwd: WEBSITE_ROOT,
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

  // The default locale must exist; a missing translation directory is only a
  // warning, so the index still builds on a checkout without translations.
  const sources = [];
  for (const source of DOC_SOURCES) {
    try {
      await stat(source.root);
      sources.push(source);
    } catch {
      if (source.locale === DOC_SOURCES[0].locale) {
        throw new Error(`Docs root not found at ${source.root}`);
      }
      console.error(
        `[build-index] WARNING: no docs for locale ${source.locale} at ${source.root}; skipping`,
      );
    }
  }

  const rawChunks = [];
  const perLocale = {};
  for (const { locale, root } of sources) {
    const files = await collectMarkdown(root);
    console.error(`[build-index] locale ${locale}: ${files.length} markdown files under ${root}`);
    let localeChunks = 0;
    for (const file of files) {
      const raw = await readFile(file, 'utf8');
      const { frontmatter, body } = stripFrontmatter(raw);
      const docPath = relative(root, file).replace(/\\/g, '/');
      const title = frontmatter.title || docPath.replace(/\.md$/i, '');
      const chunks = chunkBody(body, title);
      chunks.forEach((chunk, i) => {
        rawChunks.push({
          // docPath repeats across locales, so the locale has to be part of the
          // id or zh/en chunks of the same page would collide.
          id: `${locale}:${docPath}#${i}`,
          docPath,
          locale,
          title,
          heading: chunk.heading,
          text: chunk.text,
        });
        localeChunks += 1;
      });
    }
    perLocale[locale] = { files: files.length, chunks: localeChunks };
  }
  console.error(`[build-index] produced ${rawChunks.length} chunks total; embedding...`);

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
      embedded.push({
        ...chunk,
        // Full float64 serialises to ~20 chars per value ("0.051884498447179794"),
        // which is 5 MB of JSON for 508x512 values. Six decimals shifts cosine
        // similarity by ~2e-7 — far below any ranking effect — and cuts the
        // shipped index to roughly a third.
        embedding: Array.from(output.data, (v) => Number(v.toFixed(6))),
      });
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
    dimension: EMBEDDING_DIMENSION,
    chunkCount: embedded.length,
    totalChunksDiscovered: rawChunks.length,
    locales: perLocale,
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
