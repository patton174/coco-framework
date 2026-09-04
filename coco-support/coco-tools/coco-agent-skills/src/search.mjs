/**
 * Coco 文档语义检索（运行时）。
 * <p>
 * 加载构建期生成的 {@code data/doc-index.json}，用与构建期相同的本地嵌入模型
 * （{@code Xenova/all-MiniLM-L6-v2}，384 维）对查询做向量化，按余弦相似度排序。
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
/** 嵌入模型标识（构建期与运行时必须一致）。 */
export const EMBEDDING_MODEL = 'Xenova/all-MiniLM-L6-v2';
/** 文档站点基础地址。 */
export const DOCS_BASE_URL = 'https://patton174.github.io/coco-framework/';

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
 * @returns {Promise<number[]>} 384 维向量
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
 * </p>
 * @param {string} docPath 相对文档路径，如 {@code features/idempotency.md}
 * @returns {string} 文档 URL
 */
export function docPathToUrl(docPath) {
  const route = String(docPath ?? '')
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\.md$/i, '');
  return `${DOCS_BASE_URL}${route}`;
}

/**
 * 在给定索引内对查询向量排序（纯函数，便于测试，不触网、不下载模型）。
 * @param {number[]} queryEmbedding 查询向量
 * @param {Array<object>} index 索引条目
 * @param {number} [topK] 返回条数
 * @returns {Array<{score:number, title:string, heading:string, docPath:string, text:string, url:string}>}
 */
export function rankIndex(queryEmbedding, index, topK = 5) {
  const scored = index.map((entry) => ({
    score: cosineSimilarity(queryEmbedding, entry.embedding ?? []),
    title: entry.title,
    heading: entry.heading,
    docPath: entry.docPath,
    text: entry.text,
    url: docPathToUrl(entry.docPath),
  }));
  scored.sort((left, right) => right.score - left.score);
  return scored.slice(0, Math.max(0, topK));
}

/**
 * 语义检索：向量化查询并在索引中排序返回。
 * @param {string} query 查询词
 * @param {number} [topK] 返回条数
 * @param {{indexPath?: string, index?: Array<object>}} [options]
 * @returns {Promise<Array<{score:number, title:string, heading:string, docPath:string, text:string, url:string}>>}
 */
export async function search(query, topK = 5, options = {}) {
  const index = options.index ?? (await loadIndex(options.indexPath));
  const queryEmbedding = await embedQuery(query);
  return rankIndex(queryEmbedding, index, topK);
}
