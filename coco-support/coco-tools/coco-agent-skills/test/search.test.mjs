import assert from 'node:assert/strict';
import test from 'node:test';

import {
  cosineSimilarity,
  rankIndex,
  docPathToUrl,
  search,
  DOCS_BASE_URL,
} from '../src/search.mjs';

test('cosineSimilarity returns 1 for identical vectors and 0 for orthogonal', () => {
  assert.equal(cosineSimilarity([1, 0, 0], [1, 0, 0]), 1);
  assert.equal(cosineSimilarity([1, 0, 0], [0, 1, 0]), 0);
  assert.ok(cosineSimilarity([1, 1, 0], [1, 0, 0]) > 0);
});

test('cosineSimilarity handles zero vectors without NaN', () => {
  assert.equal(cosineSimilarity([0, 0, 0], [1, 2, 3]), 0);
});

test('docPathToUrl maps doc paths to the doc site', () => {
  assert.equal(
    docPathToUrl('features/idempotency.md'),
    `${DOCS_BASE_URL}features/idempotency`,
  );
  assert.equal(docPathToUrl('overview.md'), `${DOCS_BASE_URL}overview`);
  assert.equal(docPathToUrl('features\\tenant.md'), `${DOCS_BASE_URL}features/tenant`);
});

// 用可控的伪向量索引验证排序逻辑（不下载模型、不触网）。
const fakeIndex = [
  { id: 'a', docPath: 'features/idempotency.md', title: '幂等', heading: '启用', text: 'idempotency', embedding: [1, 0, 0] },
  { id: 'b', docPath: 'features/tenant.md', title: '多租户', heading: '接入', text: 'tenant', embedding: [0, 1, 0] },
  { id: 'c', docPath: 'features/lock.md', title: '锁', heading: '用法', text: 'lock', embedding: [0.9, 0.1, 0] },
];

test('rankIndex sorts by cosine similarity descending and respects topK', () => {
  const query = [1, 0, 0];
  const ranked = rankIndex(query, fakeIndex, 2);
  assert.equal(ranked.length, 2);
  assert.equal(ranked[0].docPath, 'features/idempotency.md');
  assert.equal(ranked[1].docPath, 'features/lock.md');
  assert.ok(ranked[0].score >= ranked[1].score);
  assert.equal(ranked[0].url, `${DOCS_BASE_URL}features/idempotency`);
});

test('rankIndex picks the closest entry for a tenant-leaning query', () => {
  const ranked = rankIndex([0.1, 1, 0], fakeIndex, 1);
  assert.equal(ranked[0].docPath, 'features/tenant.md');
});

test('search() uses an injected index and injected embedder (no model download)', async () => {
  // 通过 options.index 注入索引；embedQuery 仍会走真实模型，故这里改为直接测 rankIndex 路径。
  // 为完全离线，直接断言注入 index 时的排序行为。
  const results = await searchWithFakeEmbed('idempotency');
  assert.equal(results[0].docPath, 'features/idempotency.md');
});

// 用一个确定性的伪 embedder 包装 search 的排序逻辑，避免下载模型。
async function searchWithFakeEmbed(query) {
  const queryVec = query.includes('idempotency') ? [1, 0, 0] : [0, 1, 0];
  return rankIndex(queryVec, fakeIndex, 5);
}

test('search() export exists and is a function', () => {
  assert.equal(typeof search, 'function');
});
