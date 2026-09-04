/**
 * Coco 文档检索(整页 / 列表)。
 * <p>
 * 语义检索只返回片段;agent 常需要整页内容来完整配置某个能力。本模块直接从
 * 已构建的索引重建整页文本(npm 包只分发索引,不分发源 Markdown),并提供
 * 文档清单发现能力。纯函数、不触网、不下载模型。
 * </p>
 * @author patton174
 * @since 0.2.0
 */

import { loadIndex, docPathToUrl } from './search.mjs';

/**
 * 规范化文档路径,便于宽松匹配。
 * <p>去首尾斜杠、反斜杠转正斜杠、补 {@code .md} 后缀、小写。</p>
 * @param {string} value 输入路径,如 {@code features/idempotency} 或 {@code idempotency.md}
 * @returns {string}
 */
export function normalizeDocPath(value) {
  let p = String(value ?? '')
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '')
    .trim()
    .toLowerCase();
  if (p && !p.endsWith('.md')) {
    p += '.md';
  }
  return p;
}

/**
 * 从索引中列出全部文档(去重),按路径排序。
 * @param {Array<object>} index 索引条目
 * @returns {Array<{docPath:string, title:string, url:string, chunks:number}>}
 */
export function listDocsFromIndex(index) {
  const byPath = new Map();
  for (const entry of index) {
    const key = entry.docPath;
    if (!byPath.has(key)) {
      byPath.set(key, { docPath: key, title: entry.title, url: docPathToUrl(key), chunks: 0 });
    }
    byPath.get(key).chunks += 1;
  }
  return [...byPath.values()].sort((a, b) => a.docPath.localeCompare(b.docPath));
}

/**
 * 从索引重建整页文档(按 chunk id 的序号排序拼接)。
 * @param {Array<object>} index 索引条目
 * @param {string} docPath 文档路径(宽松匹配)
 * @returns {{docPath:string, title:string, url:string, text:string}|null} 未命中返回 null
 */
export function getDocFromIndex(index, docPath) {
  const target = normalizeDocPath(docPath);
  const matched = index.filter((e) => String(e.docPath).toLowerCase() === target);
  if (!matched.length) {
    return null;
  }
  const chunkOrder = (id) => {
    const hash = String(id).lastIndexOf('#');
    const n = hash === -1 ? NaN : Number.parseInt(String(id).slice(hash + 1), 10);
    return Number.isNaN(n) ? 0 : n;
  };
  matched.sort((a, b) => chunkOrder(a.id) - chunkOrder(b.id));
  const parts = [];
  let lastHeading = null;
  for (const entry of matched) {
    if (entry.heading && entry.heading !== lastHeading) {
      parts.push(`## ${entry.heading}`);
      lastHeading = entry.heading;
    }
    parts.push(entry.text);
  }
  return {
    docPath: matched[0].docPath,
    title: matched[0].title,
    url: docPathToUrl(matched[0].docPath),
    text: parts.join('\n\n'),
  };
}

/**
 * 列出全部文档(自动加载默认索引)。
 * @param {{indexPath?:string, index?:Array<object>}} [options]
 * @returns {Promise<Array<{docPath:string,title:string,url:string,chunks:number}>>}
 */
export async function listDocs(options = {}) {
  const index = options.index ?? (await loadIndex(options.indexPath));
  return listDocsFromIndex(index);
}

/**
 * 获取整页文档(自动加载默认索引)。
 * @param {string} docPath 文档路径
 * @param {{indexPath?:string, index?:Array<object>}} [options]
 * @returns {Promise<{docPath:string,title:string,url:string,text:string}|null>}
 */
export async function getDoc(docPath, options = {}) {
  const index = options.index ?? (await loadIndex(options.indexPath));
  return getDocFromIndex(index, docPath);
}
