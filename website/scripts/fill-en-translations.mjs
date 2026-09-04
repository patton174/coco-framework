/**
 * Fills English messages into i18n/en/**.json.
 *
 * `docusaurus write-translations` seeds every `message` with the Chinese source
 * string, so a fresh key silently ships untranslated. This maps the Chinese
 * source text to its English wording and rewrites only the messages, leaving
 * keys and descriptions untouched. Re-running is safe and idempotent.
 *
 *   node scripts/fill-en-translations.mjs [--check]
 *
 * --check exits non-zero if anything is still untranslated, for CI.
 */
import {readFileSync, writeFileSync, existsSync} from 'node:fs';

const EN = {
  // navbar + footer column titles
  文档: 'Docs',
  技能: 'Skills',
  能力: 'Capabilities',
  资源: 'Resources',

  // footer items
  概览: 'Overview',
  快速开始: 'Getting Started',
  特性开关: 'Feature Toggles',
  功能总览: 'All Features',
  技能概览: 'Skills Overview',
  快捷安装: 'Quick Install',
  使用指南: 'Usage Guide',
  '核心 Web': 'Core Web',
  数据访问: 'Data Access',
  流控与可靠性: 'Rate Limiting & Reliability',
  对象存储: 'Object Storage',
  'npm · 技能包': 'npm · Skills Package',

  // doc sidebar categories
  多租户与权限: 'Multi-tenancy & Permissions',
  平台能力: 'Platform Capabilities',
};

const FILES = [
  'i18n/en/docusaurus-theme-classic/navbar.json',
  'i18n/en/docusaurus-theme-classic/footer.json',
  'i18n/en/docusaurus-plugin-content-docs/current.json',
];

/**
 * Keys left behind by earlier footer layouts. `write-translations` reports these
 * as unused but never deletes them, so they linger and confuse future edits.
 */
const STALE_KEYS = {
  'i18n/en/docusaurus-theme-classic/footer.json': ['link.title.更多'],
};

/**
 * The zh copyright is built with `new Date().getFullYear()` in
 * docusaurus.config.ts, but a translated message has to be a literal — so the
 * en copy would drift a year behind every January. Regenerate it here instead.
 */
const enCopyright = () =>
  `Copyright © ${new Date().getFullYear()} Coco Framework · Apache-2.0 · Built with Docusaurus.`;

const hasHan = (s) => /[一-鿿]/u.test(s);
const check = process.argv.includes('--check');
let changed = 0;
const untranslated = [];
const stale = [];

for (const file of FILES) {
  if (!existsSync(file)) {
    continue;
  }
  const json = JSON.parse(readFileSync(file, 'utf8'));
  let dirty = false;

  for (const key of STALE_KEYS[file] ?? []) {
    if (key in json) {
      stale.push(`${file}  ${key}`);
      delete json[key];
      dirty = true;
      changed++;
    }
  }

  if (json.copyright && json.copyright.message !== enCopyright()) {
    json.copyright.message = enCopyright();
    dirty = true;
    changed++;
  }

  for (const [key, entry] of Object.entries(json)) {
    // The Chinese source text is the trailing segment of the generated key,
    // e.g. "link.item.label.快速开始" or "sidebar.docsSidebar.category.平台能力".
    const source = key.slice(key.lastIndexOf('.') + 1);
    const translation = EN[source] ?? EN[entry.message];

    if (translation && entry.message !== translation) {
      entry.message = translation;
      dirty = true;
      changed++;
    } else if (!translation && hasHan(entry.message)) {
      untranslated.push(`${file}  ${key}`);
    }
  }

  if (dirty && !check) {
    writeFileSync(file, `${JSON.stringify(json, null, 2)}\n`, 'utf8');
  }
}

if (untranslated.length) {
  console.error(`Untranslated English messages (${untranslated.length}):`);
  untranslated.forEach((u) => console.error(`  ${u}`));
  console.error('Add the wording to EN in scripts/fill-en-translations.mjs.');
  process.exit(1);
}

if (check && changed > 0) {
  console.error(
    `${changed} English message(s) are stale or unfilled. ` +
      'Run: node scripts/fill-en-translations.mjs',
  );
  process.exit(1);
}

if (stale.length && !check) {
  console.log(`Removed ${stale.length} stale key(s):`);
  stale.forEach((s) => console.log(`  ${s}`));
}

console.log(
  check
    ? 'All English messages are translated and current.'
    : `Filled ${changed} English message(s); none left untranslated.`,
);
