/**
 * Coco 文档语义检索（运行时）。
 * <p>
 * 加载构建期生成的 {@code data/doc-index.json}，用与构建期相同的本地嵌入模型
 * （{@link EMBEDDING_MODEL}，{@link EMBEDDING_DIMENSION} 维）对查询做向量化，
 * 按余弦相似度排序。索引同时收录中英文档，每条带 {@code locale} 字段，可按需过滤。
 * 模型在首次 {@link embedQuery} 时懒加载，避免仅做 {@link loadIndex} 时就下载模型。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));

/** 默认索引文件路径。 */
export const DEFAULT_INDEX_PATH = resolve(HERE, '..', 'data', 'doc-index.json');
/**
 * 嵌入模型标识（构建期与运行时必须一致）。
 * <p>
 * 用中英双语模型而非 {@code all-MiniLM-L6-v2}：文档 95% 是中文，纯英文模型
 * 对中文查询召回为 0（实测「如何开启分布式锁」正确文档排第 26 位，返回的全是
 * 「概览 › 边界」这类泛化段落）。bge-small-zh-v1.5 在中英查询上均为 4/4 命中
 * 首位，故单一索引即可同时服务两种语言，无需按语言分库。
 * </p>
 */
export const EMBEDDING_MODEL = 'Xenova/bge-small-zh-v1.5';
/** 嵌入向量维度（随模型变化，供构建期与状态检查校验）。 */
export const EMBEDDING_DIMENSION = 512;
/** 文档站点基础地址。 */
export const DOCS_BASE_URL = 'https://patton174.github.io/coco-framework/';
/**
 * 支持的文档语言，首项为站点默认语言（URL 不带前缀）。
 * <p>
 * 声明在 {@link docPathToUrl} 之前：{@code const} 不提升，而该函数的默认参数
 * 引用了本常量。
 * </p>
 */
export const LOCALES = ['zh-Hans', 'en'];

let extractorPromise = null;

/**
 * 懒加载 feature-extraction 管道（首次调用才下载模型）。
 * @returns {Promise<Function>} transformers pipeline
 */
export async function getExtractor() {
  if (!extractorPromise) {
    extractorPromise = (async () => {
      const { pipeline } = await import('@huggingface/transformers');
      // Pin the quantised (q8) weights explicitly. transformers v2 defaulted to
      // q8 (~23MB); v3+ defaults to full fp32 (~90MB) and would silently produce
      // different vectors from the committed index. Build-time and query-time
      // both route through here, so this keeps the two provably consistent.
      return pipeline('feature-extraction', EMBEDDING_MODEL, { dtype: 'q8' });
    })();
  }
  return extractorPromise;
}

/**
 * 用嵌入模型对单段文本做向量化（mean-pooling + normalize）。
 * @param {string} text 待嵌入文本
 * @returns {Promise<number[]>} {@link EMBEDDING_DIMENSION} 维向量
 */
export async function embedQuery(text) {
  const extractor = await getExtractor();
  const output = await extractor(String(text ?? ''), { pooling: 'mean', normalize: true });
  return Array.from(output.data);
}

/**
 * 加载文档索引。
 * @param {string} [indexPath] 索引文件路径
 * @returns {Promise<Array<{id:string, docPath:string, title:string, heading:string, text:string, embedding:number[]}>>}
 */
export async function loadIndex(indexPath = DEFAULT_INDEX_PATH) {
  const raw = await readFile(indexPath, 'utf8');
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error(`Index at ${indexPath} is not an array.`);
  }
  return parsed;
}

/**
 * 余弦相似度（两个向量均假定已归一化，但仍按通用公式计算以稳健）。
 * @param {number[]} a 向量 a
 * @param {number[]} b 向量 b
 * @returns {number} 相似度 [-1, 1]
 */
export function cosineSimilarity(a, b) {
  let dot = 0;
  let normA = 0;
  let normB = 0;
  const len = Math.min(a.length, b.length);
  for (let i = 0; i < len; i += 1) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  if (normA === 0 || normB === 0) {
    return 0;
  }
  return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

/**
 * 将文档路径转换为文档站点 URL。
 * <p>
 * 站点 {@code routeBasePath} 为 {@code /}，slug 与文件名一致，因此
 * {@code features/idempotency.md} → {@code .../features/idempotency}。
 * 非默认语言在站点上带语言前缀（英文为 {@code /en/}），默认语言不带。
 * </p>
 * @param {string} docPath 相对文档路径，如 {@code features/idempotency.md}
 * @param {string} [locale] 文档语言，默认为站点默认语言
 * @returns {string} 文档 URL
 */
export function docPathToUrl(docPath, locale = LOCALES[0]) {
  const route = String(docPath ?? '')
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\.md$/i, '');
  const prefix = locale && locale !== LOCALES[0] ? `${locale}/` : '';
  return `${DOCS_BASE_URL}${prefix}${route}`;
}

/**
 * 在给定索引内对查询向量排序（纯函数，便于测试，不触网、不下载模型）。
 * <p>
 * 中英文档共用一个索引、同一个模型，因此分数天然可比，不需要跨模型归一化。
 * 传 {@code locale} 只是把候选集缩到该语言；不传则由语义自行决定，通常会返回
 * 与提问语言一致的段落。
 * </p>
 * @param {number[]} queryEmbedding 查询向量
 * @param {Array<object>} index 索引条目
 * @param {number} [topK] 返回条数
 * @param {{locale?: string}} [options] {@code locale} 限定语言（{@link LOCALES}）
 * @returns {Array<{score:number, title:string, heading:string, docPath:string, locale:string, text:string, url:string}>}
 */
export function rankIndex(queryEmbedding, index, topK = 5, options = {}) {
  const { locale } = options;
  // Entries predating the bilingual index carry no locale; treat them as the
  // default locale so an old index still answers a locale-filtered query.
  const localeOf = (entry) => entry.locale ?? LOCALES[0];
  const candidates = locale ? index.filter((entry) => localeOf(entry) === locale) : index;

  const scored = candidates.map((entry) => ({
    score: cosineSimilarity(queryEmbedding, entry.embedding ?? []),
    title: entry.title,
    heading: entry.heading,
    docPath: entry.docPath,
    locale: localeOf(entry),
    text: entry.text,
    url: docPathToUrl(entry.docPath, localeOf(entry)),
  }));
  scored.sort((left, right) => right.score - left.score);
  return scored.slice(0, Math.max(0, topK));
}

/**
 * 语义检索：向量化查询并在索引中排序返回。
 * @param {string} query 查询词
 * @param {number} [topK] 返回条数
 * @param {{indexPath?: string, index?: Array<object>, locale?: string}} [options]
 *        {@code locale} 限定只在该语言的文档内检索（{@link LOCALES}）
 * @returns {Promise<Array<{score:number, title:string, heading:string, docPath:string, locale:string, text:string, url:string}>>}
 */
export async function search(query, topK = 5, options = {}) {
  const index = options.index ?? (await loadIndex(options.indexPath));
  const queryEmbedding = await embedQuery(query);
  return rankIndex(queryEmbedding, index, topK, { locale: options.locale });
}
