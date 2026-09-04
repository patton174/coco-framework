import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeDocPath, listDocsFromIndex, getDocFromIndex } from '../src/docs.mjs';
import { renderSnippet, mavenParentSnippet, mavenBomSnippet, gradleSnippet, GROUP_ID } from '../src/deps.mjs';
import { evaluateStatus, DEFAULT_STALE_DAYS } from '../src/index-status.mjs';

// ---- docs.mjs ----
const idx = [
  { id: 'features/tenant.md#1', docPath: 'features/tenant.md', title: '多租户', heading: '接入', text: 'second' },
  { id: 'features/tenant.md#0', docPath: 'features/tenant.md', title: '多租户', heading: '简介', text: 'first' },
  { id: 'overview.md#0', docPath: 'overview.md', title: '概览', heading: '概览', text: 'ov' },
];

test('normalizeDocPath adds .md, lowercases, trims slashes', () => {
  assert.equal(normalizeDocPath('features/Idempotency'), 'features/idempotency.md');
  assert.equal(normalizeDocPath('/overview.md/'), 'overview.md');
  assert.equal(normalizeDocPath('features\\tenant'), 'features/tenant.md');
});

test('listDocsFromIndex dedups by path, counts chunks, sorts', () => {
  const docs = listDocsFromIndex(idx);
  assert.equal(docs.length, 2);
  assert.equal(docs[0].docPath, 'features/tenant.md');
  assert.equal(docs[0].chunks, 2);
  assert.equal(docs[1].docPath, 'overview.md');
  assert.ok(docs[0].url.endsWith('features/tenant'));
});

test('getDocFromIndex reassembles chunks in id order with headings', () => {
  const doc = getDocFromIndex(idx, 'features/tenant');
  assert.ok(doc);
  assert.equal(doc.title, '多租户');
  // chunk #0 (first/简介) must precede chunk #1 (second/接入)
  assert.ok(doc.text.indexOf('first') < doc.text.indexOf('second'));
  assert.ok(doc.text.includes('## 简介'));
  assert.ok(doc.text.includes('## 接入'));
});

test('getDocFromIndex returns null for unknown path', () => {
  assert.equal(getDocFromIndex(idx, 'features/nope'), null);
});

// ---- deps.mjs ----
test('renderSnippet parent includes coco-parent and starter', () => {
  const s = mavenParentSnippet('2.0.2');
  assert.ok(s.includes('<artifactId>coco-parent</artifactId>'));
  assert.ok(s.includes('<version>2.0.2</version>'));
  assert.ok(s.includes('coco-spring-boot-starter'));
  assert.equal(renderSnippet('parent', '2.0.2'), s);
});

test('renderSnippet bom imports coco-dependencies with scope import', () => {
  const s = mavenBomSnippet('2.0.2');
  assert.ok(s.includes('coco-dependencies'));
  assert.ok(s.includes('<scope>import</scope>'));
  assert.ok(s.includes('<type>pom</type>'));
});

test('renderSnippet gradle uses platform()', () => {
  const s = gradleSnippet('2.0.2');
  assert.ok(s.includes(`platform("${GROUP_ID}:coco-dependencies:2.0.2")`));
});

test('renderSnippet throws on unknown style', () => {
  assert.throws(() => renderSnippet('sbt', '2.0.2'), /Unknown dependency style/);
});

// ---- index-status.mjs ----
test('evaluateStatus flags placeholder index as stale/unhealthy', () => {
  const st = evaluateStatus({ placeholder: true, chunkCount: 0 });
  assert.equal(st.healthy, false);
  assert.equal(st.stale, true);
  assert.match(st.recommendation, /build-index/);
});

test('evaluateStatus marks fresh populated index healthy', () => {
  const now = Date.parse('2026-09-03T00:00:00Z');
  const st = evaluateStatus(
    { placeholder: false, chunkCount: 202, builtAt: '2026-09-01T00:00:00Z', model: 'x' },
    { now },
  );
  assert.equal(st.healthy, true);
  assert.equal(st.stale, false);
  assert.equal(st.ageDays, 2);
});

test('evaluateStatus marks old index stale beyond threshold', () => {
  const now = Date.parse('2026-12-01T00:00:00Z');
  const st = evaluateStatus(
    { placeholder: false, chunkCount: 202, builtAt: '2026-09-01T00:00:00Z' },
    { now },
  );
  assert.ok(st.ageDays > DEFAULT_STALE_DAYS);
  assert.equal(st.stale, true);
  assert.equal(st.healthy, true); // populated but stale
});
