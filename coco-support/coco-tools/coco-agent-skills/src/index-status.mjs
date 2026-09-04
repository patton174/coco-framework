/**
 * Coco 文档索引状态与陈旧度检测。
 * <p>
 * 读取 {@code data/doc-index.meta.json},报告索引是否为占位、构建时间、对应
 * 文档 commit,并按可配置的时效阈值判断是否建议重建(支持"自动更新"诉求:
 * agent / CI 可据此决定是否触发 {@code build-index})。纯读取,不触网。
 * </p>
 * @author patton174
 * @since 0.2.0
 */

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));

/** 默认元数据路径。 */
export const DEFAULT_META_PATH = resolve(HERE, '..', 'data', 'doc-index.meta.json');
/** 默认陈旧阈值(天):超过则建议重建。 */
export const DEFAULT_STALE_DAYS = 30;

/**
 * 基于元数据对象计算状态(纯函数,便于测试)。
 * @param {object} meta 元数据
 * @param {{now?: number, staleDays?: number}} [options]
 * @returns {{placeholder:boolean, chunkCount:number, builtAt:string|null, ageDays:number|null, stale:boolean, healthy:boolean, model:string|null, docsCommit:string|null, recommendation:string}}
 */
export function evaluateStatus(meta, options = {}) {
  const now = options.now ?? Date.now();
  const staleDays = options.staleDays ?? DEFAULT_STALE_DAYS;
  const placeholder = Boolean(meta?.placeholder);
  const chunkCount = Number(meta?.chunkCount ?? 0);
  const builtAt = meta?.builtAt ?? null;
  let ageDays = null;
  if (builtAt) {
    const built = Date.parse(builtAt);
    if (!Number.isNaN(built)) {
      ageDays = Math.max(0, Math.round((now - built) / 86400000));
    }
  }
  const stale = placeholder || chunkCount === 0 || (ageDays !== null && ageDays > staleDays);
  const healthy = !placeholder && chunkCount > 0;
  let recommendation;
  if (placeholder || chunkCount === 0) {
    recommendation = 'Index is empty/placeholder — run `npm run build-index` (needs network to download the embedding model).';
  } else if (stale) {
    recommendation = `Index is ${ageDays} days old (> ${staleDays}). Consider rebuilding with \`npm run build-index\` to pick up doc changes.`;
  } else {
    recommendation = 'Index is healthy and current.';
  }
  return {
    placeholder,
    chunkCount,
    builtAt,
    ageDays,
    stale,
    healthy,
    model: meta?.model ?? null,
    docsCommit: meta?.frameworkDocsCommit ?? null,
    recommendation,
  };
}

/**
 * 读取元数据并计算状态。
 * @param {{metaPath?: string, now?: number, staleDays?: number}} [options]
 * @returns {Promise<object>}
 */
export async function indexStatus(options = {}) {
  const metaPath = options.metaPath ?? DEFAULT_META_PATH;
  let meta;
  try {
    meta = JSON.parse(await readFile(metaPath, 'utf8'));
  } catch (error) {
    return {
      placeholder: true,
      chunkCount: 0,
      builtAt: null,
      ageDays: null,
      stale: true,
      healthy: false,
      model: null,
      docsCommit: null,
      recommendation: `No index metadata at ${metaPath} (${error.message}). Run \`npm run build-index\`.`,
    };
  }
  return evaluateStatus(meta, options);
}
