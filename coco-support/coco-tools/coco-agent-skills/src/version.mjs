/**
 * Coco Framework 版本检测。
 * <p>
 * 从 Maven Central 元数据解析最新发布版本，并提供 semver 风格的版本比较。
 * pom 中的版本是 {@code 1.0.0-SNAPSHOT} 占位符，不可信；唯一可信来源是
 * {@code maven-metadata.xml}。
 * </p>
 * @author patton174
 * @since 0.1.0
 */

/** Maven Central 元数据地址（真值来源）。 */
export const MAVEN_METADATA_URL =
  'https://repo1.maven.org/maven2/io/github/patton174/coco-framework/maven-metadata.xml';

/**
 * 从 maven-metadata.xml 文本中解析版本信息。
 * <p>
 * 使用轻量正则解析，无需引入 XML 依赖。
 * </p>
 * @param {string} xml maven-metadata.xml 原始文本
 * @returns {{latest: string|null, release: string|null, versions: string[]}}
 */
export function parseMavenMetadata(xml) {
  const text = String(xml ?? '');
  const latest = matchTag(text, 'latest');
  const release = matchTag(text, 'release');
  const versions = [];
  const versionPattern = /<version>\s*([^<\s]+)\s*<\/version>/g;
  let match;
  while ((match = versionPattern.exec(text)) !== null) {
    versions.push(match[1]);
  }
  return { latest, release, versions };
}

function matchTag(text, tag) {
  const pattern = new RegExp(`<${tag}>\\s*([^<\\s]+)\\s*</${tag}>`);
  const match = pattern.exec(text);
  return match ? match[1] : null;
}

/**
 * 拉取并解析 Maven Central 最新版本信息。
 * <p>
 * 网络失败时抛出带有清晰信息的 Error，供调用方捕获。
 * </p>
 * @param {{url?: string, timeoutMs?: number, fetchImpl?: typeof fetch}} [options]
 * @returns {Promise<{latest: string|null, release: string|null, versions: string[]}>}
 */
export async function fetchLatestVersion(options = {}) {
  const url = options.url ?? MAVEN_METADATA_URL;
  const timeoutMs = options.timeoutMs ?? 15000;
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== 'function') {
    throw new Error('fetch is not available in this runtime (Node >=18 required).');
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let response;
  try {
    response = await fetchImpl(url, { signal: controller.signal });
  } catch (error) {
    throw new Error(`Failed to reach Maven Central (${url}): ${error.message}`);
  } finally {
    clearTimeout(timer);
  }
  if (!response.ok) {
    throw new Error(`Maven Central returned HTTP ${response.status} for ${url}.`);
  }
  const xml = await response.text();
  const parsed = parseMavenMetadata(xml);
  if (!parsed.latest && parsed.versions.length === 0) {
    throw new Error('Maven metadata parsed but contained no versions.');
  }
  return parsed;
}

/**
 * semver 风格的数字版本比较，正确处理 {@code 2.0.2} 与 {@code 2.0.10}。
 * <p>
 * 忽略前导 {@code v}，剥离 {@code -SNAPSHOT}/{@code -RC1} 等预发布后缀后按
 * 数字段逐位比较；带预发布后缀的版本被视为小于同版本号的正式版。
 * </p>
 * @param {string} a 左值
 * @param {string} b 右值
 * @returns {number} a<b 返回 -1，a>b 返回 1，相等返回 0
 */
export function compareVersions(a, b) {
  const pa = normalize(a);
  const pb = normalize(b);
  const len = Math.max(pa.nums.length, pb.nums.length);
  for (let i = 0; i < len; i += 1) {
    const na = pa.nums[i] ?? 0;
    const nb = pb.nums[i] ?? 0;
    if (na !== nb) {
      return na < nb ? -1 : 1;
    }
  }
  // 数字段相等时：无预发布后缀者更大。
  if (pa.prerelease === pb.prerelease) {
    return 0;
  }
  if (!pa.prerelease) {
    return 1;
  }
  if (!pb.prerelease) {
    return -1;
  }
  return pa.prerelease < pb.prerelease ? -1 : 1;
}

function normalize(value) {
  const raw = String(value ?? '').trim().replace(/^v/i, '');
  const [core, ...rest] = raw.split('-');
  const nums = core
    .split('.')
    .map((segment) => {
      const parsed = Number.parseInt(segment, 10);
      return Number.isNaN(parsed) ? 0 : parsed;
    });
  return { nums, prerelease: rest.join('-') };
}

/**
 * 将当前版本与最新版本比较。
 * @param {string} current 当前使用的版本
 * @param {{url?: string, fetchImpl?: typeof fetch}} [options]
 * @returns {Promise<{current: string, latest: string|null, release: string|null, versions: string[], updateAvailable: boolean}>}
 */
export async function checkVersion(current, options = {}) {
  const info = await fetchLatestVersion(options);
  const target = info.latest ?? info.release ?? info.versions[info.versions.length - 1] ?? null;
  const updateAvailable = Boolean(target) && compareVersions(current, target) < 0;
  return {
    current,
    latest: info.latest,
    release: info.release,
    versions: info.versions,
    updateAvailable,
  };
}
