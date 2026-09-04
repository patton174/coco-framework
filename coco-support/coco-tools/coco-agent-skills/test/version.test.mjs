import assert from 'node:assert/strict';
import test from 'node:test';

import {
  compareVersions,
  parseMavenMetadata,
  fetchLatestVersion,
  checkVersion,
} from '../src/version.mjs';

test('compareVersions orders numeric segments, not lexically', () => {
  assert.equal(compareVersions('2.0.2', '2.0.10'), -1);
  assert.equal(compareVersions('2.0.10', '2.0.2'), 1);
  assert.equal(compareVersions('2.0.2', '2.0.2'), 0);
});

test('compareVersions handles major/minor differences', () => {
  assert.equal(compareVersions('1.9.9', '2.0.0'), -1);
  assert.equal(compareVersions('2.1.0', '2.0.9'), 1);
  assert.equal(compareVersions('1.0.0', '1.0.1'), -1);
});

test('compareVersions tolerates v prefix and uneven lengths', () => {
  assert.equal(compareVersions('v2.0.2', '2.0.2'), 0);
  assert.equal(compareVersions('2.0', '2.0.0'), 0);
  assert.equal(compareVersions('2.0.0', '2.0'), 0);
});

test('compareVersions treats prerelease as lower than release', () => {
  assert.equal(compareVersions('2.0.2-SNAPSHOT', '2.0.2'), -1);
  assert.equal(compareVersions('2.0.2', '2.0.2-RC1'), 1);
  assert.equal(compareVersions('2.0.2-RC1', '2.0.2-RC2'), -1);
});

test('compareVersions sorts a full list correctly', () => {
  const list = ['2.0.10', '1.0.0', '2.0.2', '1.0.10', '1.0.2'];
  const sorted = [...list].sort(compareVersions);
  assert.deepEqual(sorted, ['1.0.0', '1.0.2', '1.0.10', '2.0.2', '2.0.10']);
});

test('parseMavenMetadata extracts latest, release, and versions', () => {
  const xml = `<?xml version="1.0"?>
<metadata>
  <versioning>
    <latest>2.0.2</latest>
    <release>2.0.2</release>
    <versions>
      <version>1.0.0</version>
      <version>2.0.1</version>
      <version>2.0.2</version>
    </versions>
  </versioning>
</metadata>`;
  const parsed = parseMavenMetadata(xml);
  assert.equal(parsed.latest, '2.0.2');
  assert.equal(parsed.release, '2.0.2');
  assert.deepEqual(parsed.versions, ['1.0.0', '2.0.1', '2.0.2']);
});

test('fetchLatestVersion parses an injected fetch (no network)', async () => {
  const fakeFetch = async () => ({
    ok: true,
    status: 200,
    async text() {
      return '<metadata><versioning><latest>3.1.4</latest><release>3.1.4</release><versions><version>3.0.0</version><version>3.1.4</version></versions></versioning></metadata>';
    },
  });
  const info = await fetchLatestVersion({ fetchImpl: fakeFetch });
  assert.equal(info.latest, '3.1.4');
  assert.deepEqual(info.versions, ['3.0.0', '3.1.4']);
});

test('fetchLatestVersion throws a clear error on network failure', async () => {
  const failing = async () => {
    throw new Error('ENOTFOUND');
  };
  await assert.rejects(
    () => fetchLatestVersion({ fetchImpl: failing }),
    /Failed to reach Maven Central/,
  );
});

test('checkVersion reports update availability against injected latest', async () => {
  const fakeFetch = async () => ({
    ok: true,
    status: 200,
    async text() {
      return '<metadata><versioning><latest>2.0.2</latest><release>2.0.2</release><versions><version>2.0.1</version><version>2.0.2</version></versions></versioning></metadata>';
    },
  });
  const behind = await checkVersion('2.0.1', { fetchImpl: fakeFetch });
  assert.equal(behind.updateAvailable, true);
  const current = await checkVersion('2.0.2', { fetchImpl: fakeFetch });
  assert.equal(current.updateAvailable, false);
});
