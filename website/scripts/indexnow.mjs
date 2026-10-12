// Notify IndexNow search engines (Bing, Yandex, Naver, Seznam, ...) about the pages of a finished build.
// Run after a release has been published:  node scripts/indexnow.mjs [--dry-run]
// The key is public by design: the protocol proves ownership by the same key file being served from the site root.
import {readdirSync, readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const build = fileURLToPath(new URL('../build/', import.meta.url));
const host = 'cocoframework.dev';
const keyFile = readdirSync(build).find(name => /^[0-9a-f]{32}\.txt$/.test(name));
if (!keyFile) throw new Error('IndexNow key file missing from build/ (static/<key>.txt)');
const key = keyFile.slice(0, -4);
if (readFileSync(build + keyFile, 'utf8').trim() !== key) throw new Error('IndexNow key file content must equal its name');

const urls = ['sitemap.xml', 'en/sitemap.xml']
  .flatMap(file => [...readFileSync(build + file, 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]))
  .filter(url => new URL(url).host === host);
if (!urls.length) throw new Error('no sitemap URLs for ' + host + ' (build with COCO_SITE_URL=https://' + host + ')');

if (process.argv.includes('--dry-run')) {
  console.log(`dry run: ${urls.length} URLs, key ${key}`);
  process.exit(0);
}
const res = await fetch('https://api.indexnow.org/indexnow', {
  method: 'POST',
  headers: {'Content-Type': 'application/json; charset=utf-8'},
  body: JSON.stringify({host, key, keyLocation: `https://${host}/${keyFile}`, urlList: urls}),
});
console.log(`IndexNow: HTTP ${res.status} for ${urls.length} URLs`);
if (res.status !== 200 && res.status !== 202) process.exit(1);
