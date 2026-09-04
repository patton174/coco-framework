/**
 * Coco 依赖坐标片段生成。
 * <p>
 * 为 agent 生成即用型的 Maven / Gradle 依赖片段,版本号自动填入 Maven Central
 * 最新发布版(而非 pom 里的 SNAPSHOT 占位)。支持两种接入方式:
 * parent(继承 coco-parent)与 bom(导入 coco-dependencies)。
 * </p>
 * @author patton174
 * @since 0.2.0
 */

import { fetchLatestVersion } from './version.mjs';

/** 框架 groupId。 */
export const GROUP_ID = 'io.github.patton174';

/**
 * 生成 Maven 片段(parent 方式)。
 * @param {string} version 版本号
 * @returns {string}
 */
export function mavenParentSnippet(version) {
  return [
    '<parent>',
    `  <groupId>${GROUP_ID}</groupId>`,
    '  <artifactId>coco-parent</artifactId>',
    `  <version>${version}</version>`,
    '  <relativePath/>',
    '</parent>',
    '',
    '<dependencies>',
    '  <dependency>',
    `    <groupId>${GROUP_ID}</groupId>`,
    '    <artifactId>coco-spring-boot-starter</artifactId>',
    '  </dependency>',
    '</dependencies>',
  ].join('\n');
}

/**
 * 生成 Maven 片段(BOM 导入方式,不继承 coco-parent)。
 * @param {string} version 版本号
 * @returns {string}
 */
export function mavenBomSnippet(version) {
  return [
    '<dependencyManagement>',
    '  <dependencies>',
    '    <dependency>',
    `      <groupId>${GROUP_ID}</groupId>`,
    '      <artifactId>coco-dependencies</artifactId>',
    `      <version>${version}</version>`,
    '      <type>pom</type>',
    '      <scope>import</scope>',
    '    </dependency>',
    '  </dependencies>',
    '</dependencyManagement>',
    '',
    '<dependencies>',
    '  <dependency>',
    `    <groupId>${GROUP_ID}</groupId>`,
    '    <artifactId>coco-spring-boot-starter</artifactId>',
    '  </dependency>',
    '</dependencies>',
  ].join('\n');
}

/**
 * 生成 Gradle 片段(BOM 平台方式)。
 * @param {string} version 版本号
 * @returns {string}
 */
export function gradleSnippet(version) {
  return [
    'dependencies {',
    `    implementation(platform("${GROUP_ID}:coco-dependencies:${version}"))`,
    `    implementation("${GROUP_ID}:coco-spring-boot-starter")`,
    '}',
  ].join('\n');
}

/**
 * 生成依赖片段(自动填最新版本)。
 * @param {{style?: 'parent'|'bom'|'gradle', version?: string, fetchImpl?: typeof fetch, url?: string}} [options]
 * @returns {Promise<{version:string, style:string, snippet:string, resolvedFromCentral:boolean}>}
 */
export async function dependencySnippet(options = {}) {
  const style = options.style ?? 'parent';
  let version = options.version ?? null;
  let resolvedFromCentral = false;
  if (!version) {
    const info = await fetchLatestVersion({ fetchImpl: options.fetchImpl, url: options.url });
    version = info.latest ?? info.release ?? info.versions[info.versions.length - 1];
    resolvedFromCentral = true;
  }
  if (!version) {
    throw new Error('Could not resolve a Coco Framework version.');
  }
  const snippet = renderSnippet(style, version);
  return { version, style, snippet, resolvedFromCentral };
}

/**
 * 按 style 渲染片段(纯函数,便于测试)。
 * @param {'parent'|'bom'|'gradle'} style 接入方式
 * @param {string} version 版本号
 * @returns {string}
 */
export function renderSnippet(style, version) {
  switch (style) {
    case 'parent':
      return mavenParentSnippet(version);
    case 'bom':
      return mavenBomSnippet(version);
    case 'gradle':
      return gradleSnippet(version);
    default:
      throw new Error(`Unknown dependency style: ${style} (expected parent|bom|gradle)`);
  }
}
