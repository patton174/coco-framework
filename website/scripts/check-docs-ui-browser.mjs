import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import path from 'node:path';

// QA dependencies are optional and are never bundled into the website.
// COCO_PLAYWRIGHT_ROOT: folder whose node_modules contains playwright; COCO_QA_ROOT: folder with axe-core.
const runtime = createRequire(path.resolve(process.env.COCO_PLAYWRIGHT_ROOT || '.', 'package.json'));
const qa = createRequire(path.resolve(process.env.COCO_QA_ROOT || '.', 'package.json'));
const {chromium} = runtime('playwright');
const axePath = qa.resolve('axe-core/axe.min.js');
const base = process.env.COCO_QA_URL || 'http://127.0.0.1:3000/coco-framework/';
const output = path.resolve(process.env.COCO_QA_OUTPUT || '../.superpowers/docs-ui-qa');
await mkdir(output, {recursive: true});
// Real, classic scrollbars (headless hides them by default): the site reserves a 12px scrollbar gutter, and the
// overflow check must measure what a Windows user gets. With hidden scrollbars the gutter stays empty and
// scrollWidth - clientWidth reads -12 even though nothing overflows.
const browser = await chromium.launch({channel: process.env.COCO_BROWSER || 'chrome', headless: true, ignoreDefaultArgs: ['--hide-scrollbars']});
const report = {cases: [], interactions: [], motion: {}, failures: []};
const size = (width, height) => ({width, height});
const homeSizes = [size(2560, 1440), size(1920, 1080), size(1440, 900), size(1280, 720), size(1024, 768),
  size(1024, 500), size(768, 1024), size(844, 390), size(390, 844), size(360, 640)];
const docSizes = [size(1920, 1080), size(1440, 900), size(1024, 768), size(390, 844)];
const docRoutes = ['getting-started', 'features/web-runtime'];
const fullShots = new Set(['1920x1080', '1440x900', '390x844']);
const label = ({width, height}) => `${width}x${height}`;
// Mirrors the hero media query: two columns from 1024px wide, or short landscape screens from 720px.
const twoColumn = ({width, height}) => width >= 1024 || (width >= 720 && height <= 600 && width > height);
const desktop = sizes => sizes.width >= 997;

// Review-safe copies: viewport captures scaled so neither side exceeds 1600px (image viewers reject > 2000px).
const reviewDir = path.join(output, 'review');
await mkdir(reviewDir, {recursive: true});
const REVIEW_MAX = 1600;
const reviewHome = new Set(['1920x1080', '1440x900', '1024x500', '390x844', '844x390', '2560x1440']);
const wantsReview = (lang, theme, viewport) => reviewHome.has(label(viewport))
  && (theme === 'light' ? lang === 'zh' || ['1920x1080', '1440x900', '390x844'].includes(label(viewport))
    : ['1440x900', '390x844'].includes(label(viewport)));
let reviewPage;
async function reviewCopy(buffer, file) {
  reviewPage ??= await (await browser.newContext({viewport: size(REVIEW_MAX, REVIEW_MAX)})).newPage();
  await reviewPage.setContent('<style>html,body{margin:0}img{display:block}</style><img alt="">');
  await reviewPage.evaluate(async ([src, max]) => {
    const img = document.querySelector('img');
    img.src = src;
    await img.decode();
    const scale = Math.min(1, max / img.naturalWidth, max / img.naturalHeight);
    img.style.width = `${Math.floor(img.naturalWidth * scale)}px`;
    img.style.height = `${Math.floor(img.naturalHeight * scale)}px`;
  }, [`data:image/png;base64,${buffer.toString('base64')}`, REVIEW_MAX]);
  await reviewPage.locator('img').screenshot({path: path.join(reviewDir, file)});
}

async function check(name, run) {
  try {
    const result = await run();
    console.log(`PASS ${name}`);
    return result;
  } catch (error) {
    report.failures.push({name, message: error.message});
    console.error(`FAIL ${name}: ${error.message}`);
  }
}

async function ready(page, route = '') {
  await page.goto(new URL(route, base).href, {waitUntil: 'networkidle'});
  await page.evaluate(() => document.fonts.ready);
}

// Wait until no finite Web Animation (CSS animation/transition) is still running.
async function settle(page, timeout = 6000) {
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'
    || a.effect?.getComputedTiming().iterations === Infinity), null, {timeout});
}

// Scroll through the page so every reveal block enters the viewport, then return to the top.
async function revealByScrolling(page) {
  await page.evaluate(async () => {
    const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const step = Math.max(200, innerHeight * 0.7);
    for (let y = 0; y < document.documentElement.scrollHeight; y += step) {
      window.scrollTo({top: y, behavior: 'instant'});
      await pause(70);
    }
    window.scrollTo({top: document.documentElement.scrollHeight, behavior: 'instant'});
    await pause(120);
    window.scrollTo({top: 0, behavior: 'instant'});
  });
  await page.waitForFunction(() => [...document.querySelectorAll('[data-reveal]')].every((el) => el.hasAttribute('data-in')
    || !document.documentElement.hasAttribute('data-coco-motion')), null, {timeout: 4000});
  await settle(page);
}

async function axe(page) {
  await page.addScriptTag({path: axePath});
  return page.evaluate(async () => {
    const result = await window.axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa']}});
    return result.violations.filter((v) => ['serious', 'critical'].includes(v.impact)).map((v) => ({
      id: v.id, impact: v.impact, nodes: v.nodes.map((n) => ({target: n.target, summary: n.failureSummary})),
    }));
  });
}

const revealState = (page) => page.evaluate(() => [...document.querySelectorAll('[data-reveal]')].map((el) => ({
  in: el.hasAttribute('data-in'), opacity: getComputedStyle(el).opacity, transform: getComputedStyle(el).transform,
})));

async function homeLayout(page) {
  return page.evaluate(() => {
    const rect = (el) => {
      const b = el.getBoundingClientRect();
      return {top: b.top, bottom: b.bottom, left: b.left, right: b.right, width: b.width, height: b.height};
    };
    const root = document.documentElement;
    const main = document.querySelector('main');
    const hero = main.querySelector('header');
    const grid = hero.firstElementChild;
    const [copy, aside, facts] = [...grid.children].map(rect);
    const parts = [copy, aside, facts];
    const title = main.querySelector('h1');
    const titleStyle = getComputedStyle(title);
    const actions = [...main.querySelectorAll('header a.button, header a[href="#capabilities"]')].map((a) => ({
      href: a.getAttribute('href'), ...rect(a), scrollWidth: a.scrollWidth, clientWidth: a.clientWidth,
    }));
    return {
      overflow: root.scrollWidth - root.clientWidth,
      theme: root.dataset.theme,
      innerHeight,
      nav: document.querySelector('.navbar').getBoundingClientRect().height,
      hero: rect(hero),
      heroClipped: hero.scrollHeight - hero.clientHeight,
      container: rect(grid),
      copy, aside, facts,
      content: {left: Math.min(...parts.map((p) => p.left)), right: Math.max(...parts.map((p) => p.right)), bottom: Math.max(...parts.map((p) => p.bottom))},
      title: {size: parseFloat(titleStyle.fontSize), lines: Math.round(title.getBoundingClientRect().height / parseFloat(titleStyle.lineHeight))},
      body: parseFloat(getComputedStyle(main.querySelector('header p:not([class*="positioning"])')).fontSize),
      actions,
      headings: [...main.querySelectorAll('h1,h2,h3,h4,h5,h6')].map((h) => Number(h.tagName[1])),
      missingImages: [...document.images].filter((img) => !img.complete || !img.naturalWidth).map((img) => img.src),
      scrollContainers: [...main.querySelectorAll('pre,table')].map((el) => ({
        tag: el.tagName, overflow: getComputedStyle(el).overflowX, width: el.clientWidth, scrollWidth: el.scrollWidth,
      })),
    };
  });
}

async function docLayout(page) {
  return page.evaluate(() => {
    const root = document.documentElement;
    const main = document.querySelector('main');
    const prose = document.querySelector('.markdown');
    const style = getComputedStyle(prose);
    const ctx = document.createElement('canvas').getContext('2d');
    ctx.font = `${style.fontSize} ${style.fontFamily}`;
    const box = (selector) => {
      const el = document.querySelector(selector);
      if (!el || !el.offsetParent) return null;
      const b = el.getBoundingClientRect();
      return {top: b.top, bottom: b.bottom, left: b.left, right: b.right, width: b.width, maxHeight: getComputedStyle(el).maxHeight};
    };
    return {
      overflow: root.scrollWidth - root.clientWidth,
      theme: root.dataset.theme,
      clientWidth: root.clientWidth,
      innerHeight,
      nav: document.querySelector('.navbar').getBoundingClientRect().height,
      headings: [...main.querySelectorAll('h1,h2,h3,h4,h5,h6')].map((h) => Number(h.tagName[1])),
      maxCh: parseFloat(style.maxWidth) / ctx.measureText('0').width,
      proseWidth: prose.getBoundingClientRect().width,
      fontSize: parseFloat(style.fontSize),
      sidebar: box('.theme-doc-sidebar-container [class^="sidebarViewport_"]'),
      toc: box('.theme-doc-toc-desktop'),
      frame: box('[class^="docRoot_"]'),
      missingImages: [...document.images].filter((img) => !img.complete || !img.naturalWidth).map((img) => img.src),
      scrollContainers: [...main.querySelectorAll('pre,table')].map((el) => ({
        tag: el.tagName, overflow: getComputedStyle(el).overflowX, width: el.clientWidth, scrollWidth: el.scrollWidth,
      })),
    };
  });
}

function commonAsserts(layout, theme) {
  assert.equal(layout.overflow, 0, 'page-level horizontal overflow');
  assert.equal(layout.theme, theme);
  assert.equal(layout.headings.filter((n) => n === 1).length, 1, 'exactly one H1');
  assert.ok(layout.headings.every((n, i) => i === 0 || n <= layout.headings[i - 1] + 1), 'heading levels must not skip');
  assert.deepEqual(layout.missingImages, [], 'images render');
  assert.ok(layout.scrollContainers.every((el) => el.scrollWidth <= el.width || ['auto', 'scroll'].includes(el.overflow)), 'long code/tables scroll internally');
}

try {
  const sizes = [...new Map([...homeSizes, ...docSizes].map((s) => [label(s), s])).values()];
  for (const locale of ['', 'en/']) {
    for (const theme of ['light', 'dark']) {
      for (const viewport of sizes) {
        const routes = [...(homeSizes.some((s) => label(s) === label(viewport)) ? [''] : []),
          ...(docSizes.some((s) => label(s) === label(viewport)) ? docRoutes : [])];
        const context = await browser.newContext({viewport, colorScheme: theme});
        const page = await context.newPage();
        for (const route of routes) {
          const lang = locale ? 'en' : 'zh';
          const name = `${lang}/${route || 'home'} ${theme} ${label(viewport)}`;
          const errors = [];
          const onError = (error) => errors.push(error.message);
          page.on('pageerror', onError);
          await check(name, async () => {
            await ready(page, locale + route);
            await settle(page);
            if (!route) {
              const layout = await homeLayout(page);
              commonAsserts(layout, theme);
              const available = layout.innerHeight - layout.nav;
              assert.ok(layout.hero.height >= available * 0.9 - 1, `hero ${layout.hero.height}px < 90% of ${available}px`);
              assert.ok(layout.heroClipped <= 1, `hero content clipped by ${layout.heroClipped}px`);
              assert.equal(layout.actions.length, 2);
              assert.ok(layout.actions.every((a) => a.bottom <= layout.innerHeight), 'both entry actions in first viewport');
              assert.ok(layout.actions.every((a) => a.scrollWidth <= a.clientWidth + 1), 'CTA text fits');
              if (twoColumn(viewport)) {
                assert.ok(layout.aside.left >= layout.copy.right, 'copy and code panel sit side by side');
                assert.ok(layout.content.bottom <= layout.innerHeight + 1, `hero content ends at ${Math.round(layout.content.bottom)}px, below the ${layout.innerHeight}px first screen`);
              } else {
                assert.ok(layout.aside.top >= Math.max(...layout.actions.map((a) => a.bottom)), 'code panel follows the CTAs');
              }
              if (viewport.width >= 1024) {
                const span = (layout.content.right - layout.content.left) / layout.container.width;
                assert.ok(span >= 0.8, `hero content spans ${(span * 100).toFixed(0)}% of the container`);
              }
              const firstScreen = await page.evaluate(() => [...document.querySelectorAll('[data-reveal]')]
                .filter((el) => el.getBoundingClientRect().top < innerHeight)
                .map((el) => ({in: el.hasAttribute('data-in'), opacity: getComputedStyle(el).opacity})));
              assert.ok(firstScreen.every((r) => r.in && r.opacity === '1'), 'blocks on the first screen are revealed without scrolling');
              const firstShot = await page.screenshot({path: path.join(output, `${lang}-home-${theme}-${label(viewport)}.png`)});
              if (wantsReview(lang, theme, viewport)) await reviewCopy(firstShot, `${lang}-home-${theme}-${label(viewport)}.png`);
              await revealByScrolling(page);
              const reveals = await revealState(page);
              assert.ok(reveals.length >= 20, 'homepage has reveal blocks');
              assert.ok(reveals.every((r) => r.opacity === '1' && r.transform === 'none'), 'every block ends in its final state');
              const a11y = await axe(page);
              assert.equal(a11y.length, 0, JSON.stringify(a11y));
              if (fullShots.has(label(viewport))) {
                await page.screenshot({path: path.join(output, `${lang}-home-${theme}-${label(viewport)}-full.png`), fullPage: true});
              }
              report.cases.push({name, layout, reveals: reveals.length, a11y});
            } else {
              const layout = await docLayout(page);
              commonAsserts(layout, theme);
              assert.ok(layout.maxCh >= 65 && layout.maxCh <= 75, `prose width: ${layout.maxCh}ch`);
              if (desktop(viewport)) {
                assert.ok(layout.sidebar && layout.sidebar.bottom <= layout.innerHeight + 1, 'sidebar fits the viewport height');
                assert.ok(layout.toc && layout.toc.top >= layout.nav - 1 && layout.toc.bottom <= layout.innerHeight + 1, 'TOC fits below the navbar');
              }
              if (viewport.width >= 1920) {
                const left = layout.frame.left, right = layout.clientWidth - layout.frame.right;
                assert.ok(Math.abs(left - right) <= 2, `doc frame centred (${left} / ${right})`);
              }
              const a11y = await axe(page);
              assert.equal(a11y.length, 0, JSON.stringify(a11y));
              if (route === 'getting-started') {
                const docShot = await page.screenshot({path: path.join(output, `${lang}-getting-started-${theme}-${label(viewport)}.png`)});
                if (lang === 'zh' && ['1920x1080', '390x844'].includes(label(viewport))) await reviewCopy(docShot, `zh-getting-started-${theme}-${label(viewport)}.png`);
              }
              report.cases.push({name, layout, a11y});
            }
            assert.deepEqual(errors, [], 'no client errors');
          });
          page.off('pageerror', onError);
        }
        await context.close();
      }
    }
  }

  await check('homepage motion: staged hero entrance, count-up, scroll reveals, CLS', async () => {
    const context = await browser.newContext({viewport: size(1440, 900)});
    const page = await context.newPage();
    await page.addInitScript(() => {
      window.__countLog = [];
      window.__cls = 0;
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) if (!entry.hadRecentInput) window.__cls += entry.value;
      }).observe({type: 'layout-shift', buffered: true});
      document.addEventListener('DOMContentLoaded', () => {
        const target = document.querySelector('[data-count]');
        if (target) new MutationObserver(() => window.__countLog.push(target.textContent)).observe(target, {childList: true, characterData: true, subtree: true});
      });
    });
    const sample = () => page.evaluate(() => {
      const animations = document.getAnimations();
      const starts = animations.map((a) => a.startTime).filter((t) => t !== null);
      const word = document.querySelector('main h1 > span');
      const line = document.querySelector('main header .token-line');
      return {
        time: document.timeline.currentTime,
        firstStart: starts.length ? Math.min(...starts) : null,
        gate: document.documentElement.getAttribute('data-coco-motion'),
        running: animations.filter((a) => a.playState === 'running').map((a) => a.animationName || a.transitionProperty),
        wordOpacity: Number(getComputedStyle(word).opacity),
        wordTransform: getComputedStyle(word).transform,
        lineClip: line ? getComputedStyle(line).clipPath : null,
      };
    });
    await page.goto(base, {waitUntil: 'domcontentloaded'});
    const frames = [];
    let origin = null;
    for (const at of [0, 300, 1200]) {
      if (origin !== null) await page.waitForFunction(([o, ms]) => document.timeline.currentTime - o >= ms, [origin, at]);
      const state = await sample();
      origin ??= state.firstStart ?? state.time;
      const file = `motion-hero-${at}ms.png`;
      await reviewCopy(await page.screenshot({path: path.join(output, file)}), file);
      frames.push({at, elapsed: Math.round(state.time - origin), file, ...state});
    }
    report.motion.hero = frames;
    assert.ok(frames[0].gate !== null, 'motion gate set before first paint');
    assert.ok(frames[0].running.length >= 5, `hero entrance running at ~0ms: ${frames[0].running}`);
    assert.ok(frames[0].wordOpacity < 1, 'headline starts below full opacity');
    assert.ok(frames[1].running.length > 0, 'still animating at ~300ms');
    await page.waitForLoadState('networkidle');
    await settle(page);
    const final = await sample();
    assert.equal(final.wordOpacity, 1);
    assert.equal(final.wordTransform, 'none');
    assert.equal(final.gate, 'armed', 'hook armed the gate after hydration');
    const counts = await page.evaluate(() => window.__countLog);
    report.motion.countLog = counts;
    assert.ok(counts.some((v) => Number(v) < 17), `stat counted up: ${counts.join(',')}`);
    assert.equal(counts.at(-1), '17');

    const sectionFrames = [];
    await page.evaluate(() => document.getElementById('architecture').scrollIntoView({behavior: 'instant'}));
    for (const at of [150, 1400]) {
      await page.waitForTimeout(at - (sectionFrames.at(-1)?.at ?? 0));
      const state = await page.evaluate(() => ({
        layers: [...document.querySelectorAll('#architecture li[data-reveal]')].map((li) => ({
          in: li.hasAttribute('data-in'), opacity: Number(getComputedStyle(li).opacity), transform: getComputedStyle(li).transform,
        })),
        running: document.getAnimations().filter((a) => a.playState === 'running').length,
      }));
      const file = `motion-architecture-${at}ms.png`;
      await reviewCopy(await page.screenshot({path: path.join(output, file)}), file);
      sectionFrames.push({at, file, ...state});
    }
    report.motion.architecture = sectionFrames;
    assert.ok(sectionFrames[0].layers.every((l) => l.in), 'layers revealed on entering the viewport');
    assert.ok(sectionFrames[0].running > 0 && sectionFrames[0].layers.some((l) => l.opacity < 1), 'layers are mid-transition at ~150ms');
    assert.ok(sectionFrames[1].layers.every((l) => l.opacity === 1 && l.transform === 'none'), 'layers settle');
    await page.evaluate(() => document.getElementById('getting-started').scrollIntoView({behavior: 'instant'}));
    await page.waitForTimeout(250);
    await reviewCopy(await page.screenshot({path: path.join(output, 'motion-steps-250ms.png')}), 'motion-steps-250ms.png');
    await revealByScrolling(page);
    const cls = await page.evaluate(() => window.__cls);
    report.motion.cls = cls;
    assert.ok(cls <= 0.05, `CLS ${cls}`);

    // Client-side navigation back to the homepage replays the entrance without a visible flash.
    await ready(page, 'getting-started');
    await page.locator('.navbar__brand').click();
    await page.waitForFunction(() => document.querySelector('main header h1'));
    const spa = await sample();
    report.motion.spa = spa;
    assert.equal(spa.gate, 'armed');
    assert.ok(spa.running.length > 0, 'entrance replays after client-side navigation');
    await settle(page);
    await revealByScrolling(page);
    assert.ok((await revealState(page)).every((r) => r.opacity === '1'), 'all blocks visible after client-side navigation');
    await context.close();
    report.interactions.push('hero entrance frames 0/300/1200ms, count-up, architecture stacking, steps, CLS, SPA replay');
  });

  await check('SSR without JavaScript shows final state', async () => {
    for (const viewport of [size(1440, 900), size(390, 844)]) {
      const context = await browser.newContext({viewport, javaScriptEnabled: false});
      const page = await context.newPage();
      for (const route of ['', 'en/', 'getting-started', 'en/getting-started']) {
        await page.goto(new URL(route, base).href);
        await page.locator('h1').waitFor({state: 'visible'});
        assert.equal(await page.locator('h1').evaluate((h) => getComputedStyle(h).opacity), '1');
        assert.equal(await page.locator('main').evaluate((h) => getComputedStyle(h).visibility), 'visible');
        assert.equal(await page.evaluate(() => document.documentElement.hasAttribute('data-coco-motion')), false);
        if (!route || route === 'en/') {
          const reveals = await revealState(page);
          assert.ok(reveals.length >= 20 && reveals.every((r) => r.opacity === '1' && r.transform === 'none'), 'no-JS blocks visible');
          assert.ok(await page.locator('main h1 > span').evaluateAll((spans) => spans.every((s) => getComputedStyle(s).opacity === '1' && getComputedStyle(s).animationName === 'none')));
          await reviewCopy(await page.screenshot(), `nojs-${route ? 'en' : 'zh'}-home-${label(viewport)}.png`);
          if (viewport.width === 1440) await page.screenshot({path: path.join(output, `nojs-${route ? 'en' : 'zh'}-home-1440x900-full.png`), fullPage: true});
        }
      }
      await context.close();
    }
    report.interactions.push('SSR visible without JS at desktop and mobile, both locales, no motion gate');
  });

  await check('motion safety net reveals content when the app bundle never runs', async () => {
    const context = await browser.newContext({viewport: size(1440, 900)});
    const page = await context.newPage();
    await page.route(/\/assets\/js\//, (route) => route.abort());
    await page.goto(base, {waitUntil: 'domcontentloaded'});
    assert.equal(await page.evaluate(() => document.documentElement.getAttribute('data-coco-motion')), '');
    await page.waitForTimeout(4400);
    assert.equal(await page.evaluate(() => document.documentElement.hasAttribute('data-coco-motion')), false, 'gate removed by the 4s timer');
    const reveals = await revealState(page);
    assert.ok(reveals.every((r) => r.opacity === '1'), 'blocks visible after the safety timeout');
    await context.close();
    report.interactions.push('blocked JS bundle: 4s safety timer removes the gate and every block is visible');
  });

  await check('keyboard copy success, failure, loading and cancellation', async () => {
    const context = await browser.newContext({viewport: size(1440, 900), permissions: ['clipboard-read', 'clipboard-write']});
    const page = await context.newPage();
    await ready(page, 'getting-started');
    const copy = page.getByRole('button', {name: /Markdown/});
    let reached = false;
    for (let i = 0; i < 100; i++) {
      await page.keyboard.press('Tab');
      if (await copy.evaluate((button) => button === document.activeElement)) {reached = true; break;}
    }
    assert.ok(reached, 'Markdown button is reachable by Tab');
    assert.ok(await copy.evaluate((button) => getComputedStyle(button).outlineStyle !== 'none'), 'visible keyboard focus');
    await page.screenshot({path: path.join(output, 'markdown-keyboard-focus.png')});
    await page.route('https://raw.githubusercontent.com/**', (route) => route.fulfill({status: 200, body: '# Coco QA\n'}));
    await page.keyboard.press('Enter');
    await page.getByRole('status').filter({hasText: /Markdown/}).waitFor({state: 'attached'});
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('\u5df2\u590d\u5236'));
    assert.equal((await page.evaluate(() => navigator.clipboard.readText())).replaceAll('\r\n', '\n'), '# Coco QA\n');
    await page.waitForTimeout(2300);
    await page.unrouteAll();
    await page.route('https://raw.githubusercontent.com/**', (route) => route.fulfill({status: 500, body: 'failed'}));
    await copy.click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('\u590d\u5236\u5931\u8d25'));
    await page.waitForTimeout(2300);
    await page.unrouteAll();
    await page.route('https://raw.githubusercontent.com/**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 700));
      await route.fulfill({status: 200, body: '# Slow request\n'}).catch(() => {});
    });
    await copy.click();
    assert.equal(await copy.isDisabled(), true, 'in-flight requests are disabled');
    assert.equal(await copy.getAttribute('aria-busy'), 'true');
    await page.locator('.pagination-nav__link--next').click();
    await page.waitForTimeout(900);
    assert.equal(await page.getByRole('status').innerText(), '', 'old request does not update the new route');
    await context.close();
    report.interactions.push('Tab/Enter, focus ring, clipboard success/error, loading, route cancellation');
  });

  await check('mobile drawer, TOC, theme, locale and anchors', async () => {
    const mobile = size(390, 844);
    const context = await browser.newContext({viewport: mobile});
    const page = await context.newPage();
    await ready(page, 'getting-started');
    await page.locator('.navbar__toggle').click();
    await page.locator('.navbar-sidebar').waitFor({state: 'visible'});
    // The open drawer locks page scrolling, so its scrollbar disappears while the reserved gutter stays (that is
    // what keeps the page from shifting sideways): the content may be narrower than the client area, never wider.
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth) <= 0, 'open drawer must not overflow horizontally');
    await page.locator('.navbar-sidebar__close').click();
    const toc = page.locator('[class*="tocCollapsibleButton"]');
    await toc.click();
    await page.locator('[class*="tocCollapsibleContent"] a').first().waitFor({state: 'visible'});
    await toc.click();
    await ready(page);
    await page.locator('a[href="#capabilities"]').click();
    await page.waitForFunction(() => location.hash === '#capabilities');
    for (const id of ['architecture', 'getting-started', 'ecosystem', 'community']) {
      await page.goto(new URL(`#${id}`, base).href);
      await page.waitForFunction((target) => {
        const top = document.getElementById(target).getBoundingClientRect().top;
        return top >= 0 && top < innerHeight;
      }, id);
      const top = await page.locator(`#${id}`).evaluate((el) => el.getBoundingClientRect().top);
      assert.ok(top >= 0 && top < mobile.height, `anchor ${id} visible`);
    }
    await ready(page);
    await page.locator('.navbar__toggle').click();
    const theme = page.locator('.navbar-sidebar button[class*="toggleButton"]');
    // The toggle switches inside a View Transition, so the attribute changes a frame after the click:
    // wait for each step's circular reveal to finish before reading it again.
    for (let i = 0; i < 3 && await page.locator('html').getAttribute('data-theme') !== 'dark'; i++) {
      await theme.click();
      await page.waitForFunction(() => !document.documentElement.hasAttribute('data-theme-transition'), null, {timeout: 3000});
    }
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
    await page.locator('.navbar-sidebar__close').click();
    await page.locator('.navbar-locale-toggle').click();
    // The static server 302-redirects /en/ to /en (trailingSlash: false): compare without the trailing slash.
    const rootPath = new URL(base).pathname.replace(/\/$/, '');
    await page.waitForURL((url) => url.pathname.replace(/\/$/, '') === `${rootPath}/en`);
    assert.equal(await page.locator('html').getAttribute('lang'), 'en');
    await page.locator('.navbar-locale-toggle').click();
    await page.waitForURL((url) => url.pathname.replace(/\/$/, '') === rootPath);
    await context.close();
    report.interactions.push('mobile drawer/TOC, theme and locale switches, five homepage anchors');
  });

  await check('keyboard navigation covers the document controls', async () => {
    const context = await browser.newContext({viewport: size(1440, 900)});
    const page = await context.newPage();
    await ready(page, 'getting-started');
    const seen = new Set();
    for (let i = 0; i < 140; i++) {
      await page.keyboard.press('Tab');
      const active = await page.evaluate(() => {
        const el = document.activeElement;
        if (!el.matches('a,button')) return null;
        const style = getComputedStyle(el);
        return {
          focus: style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) >= 1,
          name: el.getAttribute('aria-label') || el.textContent.trim() || el.getAttribute('title') || el.querySelector('img')?.alt,
          kinds: [
            el.closest('.navbar') && 'navbar', el.matches('.navbar-locale-toggle') && 'locale',
            el.matches('.navbar-github-link') && 'github',
            el.closest('.theme-doc-sidebar-container') && 'sidebar',
            el.closest('.table-of-contents') && 'toc',
            el.closest('.theme-code-block') && 'code-copy',
            el.getAttribute('aria-label')?.includes('Markdown') && 'markdown-copy',
            el.closest('.pagination-nav') && 'pagination',
          ].filter(Boolean),
        };
      });
      if (!active) continue;
      assert.ok(active.name, 'interactive element has an accessible name');
      assert.ok(active.focus, `visible focus: ${active.name}`);
      active.kinds.forEach((kind) => seen.add(kind));
      if (seen.size === 8) break;
    }
    assert.deepEqual([...seen].sort(), ['navbar', 'locale', 'github', 'sidebar', 'toc', 'code-copy', 'markdown-copy', 'pagination'].sort());
    await context.close();
    report.interactions.push('keyboard reaches navbar/locale/GitHub/sidebar/TOC/code-copy/Markdown/pagination with visible focus');
  });

  await check('keyboard focus inside a hidden homepage block reveals it immediately', async () => {
    const context = await browser.newContext({viewport: size(1440, 900)});
    const page = await context.newPage();
    await ready(page);
    const link = page.locator('#ecosystem h3 a').first();
    assert.equal(await link.evaluate((a) => a.closest('[data-reveal]').hasAttribute('data-in')), false, 'block starts hidden below the fold');
    await link.focus();
    await page.waitForTimeout(50);
    assert.equal(await link.evaluate((a) => getComputedStyle(a.closest('[data-reveal]')).opacity), '1');
    await context.close();
    report.interactions.push('focusing a link in an unrevealed block shows it instantly');
  });

  await check('reduced motion shows the final state immediately', async () => {
    const context = await browser.newContext({viewport: size(1440, 900), reducedMotion: 'reduce'});
    const page = await context.newPage();
    await page.goto(base, {waitUntil: 'domcontentloaded'});
    const early = await page.evaluate(() => ({
      gate: document.documentElement.hasAttribute('data-coco-motion'),
      animations: document.getAnimations().length,
      word: getComputedStyle(document.querySelector('main h1 > span')).opacity,
    }));
    assert.deepEqual(early, {gate: false, animations: 0, word: '1'});
    await page.waitForLoadState('networkidle');
    await reviewCopy(await page.screenshot({path: path.join(output, 'reduced-motion-home-1440x900.png')}), 'reduced-motion-home-1440x900.png');
    const entryAnimation = await page.locator('main header').evaluate((el) => getComputedStyle(el).animationName);
    assert.equal(entryAnimation, 'none');
    const reveals = await revealState(page);
    assert.ok(reveals.every((r) => r.opacity === '1' && r.transform === 'none' && !r.in), 'blocks final without scrolling or JS reveal');
    assert.equal(await page.locator('[data-count]').first().textContent(), '17', 'no count-up');
    assert.equal(await page.evaluate(() => document.getAnimations().length), 0);
    await page.screenshot({path: path.join(output, 'reduced-motion-home-1440x900-full.png'), fullPage: true});
    await ready(page, 'getting-started');
    const state = await page.locator('.markdown').evaluate((el) => ({opacity: getComputedStyle(el).opacity, transform: getComputedStyle(el).transform}));
    assert.deepEqual(state, {opacity: '1', transform: 'none'});
    await page.emulateMedia({reducedMotion: 'no-preference'});
    await page.emulateMedia({reducedMotion: 'reduce'});
    assert.equal(await page.locator('.markdown').evaluate((el) => getComputedStyle(el).opacity), '1');
    await context.close();
    report.interactions.push('reduced motion: no gate, no animations, no count-up, blocks final immediately; docs readable');
  });
} finally {
  await browser.close();
  await writeFile(path.join(output, 'browser-report.json'), JSON.stringify(report, null, 2));
}
const expected = homeSizes.length * 4 + docSizes.length * docRoutes.length * 4;
console.log(`${report.cases.length}/${expected} layout/a11y cases; ${report.interactions.length}/8 interaction checks; ${report.failures.length} failures`);
if (report.failures.length) process.exitCode = 1;
