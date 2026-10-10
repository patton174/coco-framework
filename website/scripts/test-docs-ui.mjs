import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import postcss from 'postcss';
import {parse} from '@babel/parser';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const css = postcss.parse(read('src/css/custom.css'));
const tokens = selector => {
  const values = {};
  css.walkRules(selector, rule => rule.walkDecls(decl => {values[decl.prop] = decl.value;}));
  return values;
};
const light = tokens(':root');
const dark = {...light, ...tokens("[data-theme='dark']")};
const luminance = hex => {
  const channels = hex.slice(1).match(/../g).map(value => parseInt(value, 16) / 255)
    .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
};
const contrast = (a, b) => {
  const x = luminance(a), y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

test('light and dark themes expose all shared docs tokens', () => {
  const names = ['canvas', 'surface', 'surface-muted', 'text', 'text-muted', 'border',
    'accent', 'code-bg', 'code-border', 'focus', 'radius-sm', 'radius-md', 'shadow-subtle'];
  for (const theme of [light, dark]) {
    for (const name of names) assert.ok(theme[`--coco-${name}`], `missing --coco-${name}`);
    for (let i = 1; i <= 8; i++) assert.equal(theme[`--coco-space-${i}`], `${i * 4}px`);
    assert.equal(theme['--coco-radius-md'], '8px');
    assert.equal(theme['--ifm-background-color'], 'var(--coco-canvas)');
  }
});

test('theme text, links and focus indicators meet AA contrast', () => {
  for (const theme of [light, dark]) {
    for (const background of ['canvas', 'surface', 'surface-muted', 'code-bg']) {
      for (const foreground of ['text', 'text-muted', 'accent']) {
        assert.ok(contrast(theme[`--coco-${foreground}`], theme[`--coco-${background}`]) >= 4.5,
          `${foreground}/${background} must meet 4.5:1`);
      }
      assert.ok(contrast(theme['--coco-focus'], theme[`--coco-${background}`]) >= 3);
    }
  }
});

test('docs prose has bounded reading width and internally scrolling tables', () => {
  let bounded = false, scrolling = false;
  css.walkRules('.markdown', rule => rule.walkDecls('max-width', decl => {bounded = /^(6[5-9]|7[0-5])ch$/.test(decl.value);}));
  css.walkRules('.markdown table', rule => rule.walkDecls('overflow-x', decl => {scrolling ||= decl.value === 'auto';}));
  assert.ok(bounded, 'prose maximum must be 65-75ch');
  assert.ok(scrolling, 'tables must scroll inside their own container');
  assert.doesNotMatch(read('src/css/custom.css'), /background-clip:\s*text|feTurbulence/);
});

test('all Prism syntax colors meet AA on the shared code background', () => {
  for (const theme of [light, dark]) {
    for (const name of ['keyword', 'string', 'function', 'literal', 'type']) {
      const color = theme[`--coco-code-${name}`];
      assert.ok(color, `missing syntax token: ${name}`);
      assert.ok(contrast(color, theme['--coco-code-bg']) >= 4.5, `${name} syntax must meet 4.5:1`);
      assert.ok(read('docusaurus.config.ts').includes(`var(--coco-code-${name})`), `Prism must consume ${name}`);
    }
  }
});

test('warm paper palette: heading, clay, buttons and highlighted lines stay readable and teal is gone', () => {
  for (const theme of [light, dark]) {
    for (const name of ['heading', 'clay', 'rule-strong', 'button-bg', 'button-text', 'button-hover', 'code-highlight']) {
      assert.match(theme[`--coco-${name}`] ?? '', /^#[0-9a-f]{6}$/i, `missing hex token --coco-${name}`);
    }
    for (const background of ['canvas', 'surface', 'surface-muted', 'code-bg']) {
      assert.ok(contrast(theme['--coco-heading'], theme[`--coco-${background}`]) >= 4.5, `heading/${background}`);
    }
    for (const background of ['button-bg', 'button-hover']) {
      assert.ok(contrast(theme['--coco-button-text'], theme[`--coco-${background}`]) >= 4.5, `button-text/${background}`);
    }
    for (const name of ['clay', 'rule-strong']) assert.ok(contrast(theme[`--coco-${name}`], theme['--coco-canvas']) >= 3, `${name}/canvas`);
    for (const name of ['text', 'text-muted', 'code-keyword', 'code-string', 'code-function', 'code-literal', 'code-type']) {
      assert.ok(contrast(theme[`--coco-${name}`], theme['--coco-code-highlight']) >= 4.5, `${name} on highlighted line`);
    }
  }
  const teal = /0f766e|5eead4|0d6962|0c625c|0a514b|10837a|118a80|139d92|3ee5cb|2ee3c6|19c5aa|7eefdd|8ef1e1|bef7ef|08746f/i;
  assert.doesNotMatch(read('src/css/custom.css'), teal);
  assert.doesNotMatch(read('src/pages/index.module.css'), teal);
});

test('translated labels never use tracked uppercase mono and the theme toggle does not lift', () => {
  const sheet = read('src/css/custom.css');
  const rootRules = [...css.nodes].filter(node => node.type === 'rule' && !node.selector.includes(':lang(en)'));
  for (const rule of rootRules.filter(node => /footer__title|pagination-nav__sublabel|admonitionHeading/.test(node.selector))) {
    rule.walkDecls('text-transform', decl => assert.equal(decl.value, 'none', `${rule.selector} must not uppercase CJK labels`));
    rule.walkDecls('font', decl => assert.doesNotMatch(decl.value, /mono/, `${rule.selector} must not use mono by default`));
  }
  assert.match(sheet, /html:lang\(en\) \.footer__title/);
  const toggle = read('src/theme/ColorModeToggle/styles.module.css');
  assert.doesNotMatch(toggle, /translateY|transform|border-radius:\s*(9|\d\d)px/);
});

const home = parse(read('src/pages/index.tsx'), {sourceType: 'module', plugins: ['typescript', 'jsx']});
const jsx = [];
const visit = node => {
  if (!node || typeof node !== 'object') return;
  if (node.type === 'JSXOpeningElement') jsx.push(node);
  for (const value of Object.values(node)) {
    if (Array.isArray(value)) value.forEach(visit);
    else if (value && typeof value === 'object') visit(value);
  }
};
visit(home);
const attribute = (node, name) => node.attributes.find(attr => attr.type === 'JSXAttribute' && attr.name.name === name)?.value;

test('homepage preserves public anchors in the agreed reading order and has one H1', () => {
  const sections = jsx.filter(node => node.name.name === 'section');
  const anchors = sections.map(node => attribute(node, 'id')?.value).filter(Boolean);
  assert.deepEqual(anchors, ['capabilities', 'architecture', 'getting-started', 'ecosystem', 'community']);
  assert.equal(jsx.filter(node => node.name.name === 'h1').length, 1);
});

// Ruling 2026-10-09: the user asked for homepage motion ("没有任何动画效果"), overruling the earlier
// "no metric animation / no block reveals" contract. Motion must stay JS-gated and reduced-motion safe.
test('homepage motion exists, is gated behind a pre-paint JS flag and is disabled under reduced motion', () => {
  const page = read('src/pages/index.tsx');
  const sheet = read('src/pages/index.module.css');
  const hook = read('src/components/HomeMotion/useHomeMotion.ts');
  const config = read('docusaurus.config.ts');
  assert.match(page, /useHomeMotion\(/);
  assert.match(page, /data-reveal/);
  assert.match(page, /data-count=/);
  assert.match(hook, /IntersectionObserver/);
  assert.match(hook, /requestAnimationFrame/);
  // The gate is set by an inline head script, skips reduced motion and removes itself if the hook never arms.
  assert.match(config, /tagName: 'script'/);
  assert.match(config, /prefers-reduced-motion: reduce/);
  assert.match(config, /setTimeout\(function\(\)\{if\(d\.getAttribute\('data-coco-motion'\)!=='armed'\)d\.removeAttribute\('data-coco-motion'\)/);
  assert.match(hook, /prefers-reduced-motion: reduce/);
  assert.match(hook, /setAttribute\('data-coco-motion', 'armed'\)/);
  assert.match(hook, /catch \{\s*html\.removeAttribute\('data-coco-motion'\)/);
  const module = postcss.parse(sheet);
  let gated = 0;
  module.walkRules(rule => {
    if (rule.parent.type === 'atrule' && rule.parent.name === 'keyframes') return;
    const animates = rule.nodes.some(decl => decl.type === 'decl' && (decl.prop === 'animation' || (decl.prop === 'opacity' && decl.value === '0')));
    if (!animates) return;
    gated++;
    assert.match(rule.selector, /html\[data-coco-motion\]/, `${rule.selector} must be JS-gated`);
    assert.equal(rule.parent.type === 'atrule' && rule.parent.params, '(prefers-reduced-motion: no-preference)', `${rule.selector} must respect reduced motion`);
  });
  assert.ok(gated >= 10, 'hero entrance and scroll reveals are present');
  // Only compositor-friendly properties animate.
  module.walkAtRules('keyframes', frames => frames.walkDecls(decl => assert.match(decl.prop, /^(opacity|transform|clip-path)$/, `@keyframes ${frames.params}: ${decl.prop}`)));
  module.walkAtRules('media', media => {
    if (media.params !== '(prefers-reduced-motion: no-preference)') return;
    media.walkDecls('transition', decl => {
      for (const part of decl.value.split(/,(?![^(]*\))/)) assert.match(part.trim(), /^(opacity|transform|clip-path|none)\b/, `transition: ${part}`);
    });
  });
  assert.doesNotMatch(sheet, /--coco-cream|--coco-espresso|linear-gradient|letter-spacing:\s*-|cubic-bezier\([^)]*-|elastic|bounce/);
  assert.match(read('src/css/custom.css'), /@media \(prefers-reduced-motion: reduce\) \{[\s\S]*?animation: none !important; transition: none !important;/);
});

test('layout tokens follow viewport width and height and the hero fills the first screen', () => {
  assert.match(light['--coco-gutter'], /^clamp\([^)]*vw[^)]*\)$/);
  assert.equal(light['--coco-container'], 'min(var(--coco-container-max), 100% - 2 * var(--coco-gutter))');
  assert.match(light['--coco-viewport-h'], /^calc\(100s?vh - var\(--ifm-navbar-height\)\)$/);
  for (const step of ['--coco-step-2', '--coco-step-3', '--coco-step-hero']) {
    assert.match(light[step], /^max\(.+min\(.+vw.+vh.+\)\)$/, `${step} must be capped by both width and height`);
  }
  assert.match(light['--coco-section-space'], /vw.*vh/);
  const module = postcss.parse(read('src/pages/index.module.css'));
  const decl = (selector, prop) => {
    let value;
    module.walkRules(selector, rule => rule.walkDecls(prop, item => {value ??= item.value;}));
    return value;
  };
  assert.equal(decl('.container', 'width'), 'var(--coco-container)');
  assert.match(decl('.hero', 'min-height'), /var\(--coco-viewport-h\)/);
  assert.match(decl('.title', 'font-size'), /var\(--coco-step-hero\)/);
  let twoColumn = false;
  module.walkAtRules('media', media => {
    if (!media.params.includes('(min-width: 1024px)')) return;
    media.walkRules('.heroGrid', rule => rule.walkDecls('grid-template-columns', item => {twoColumn ||= item.value.split(/\s(?![^(]*\))/).length === 2;}));
  });
  assert.ok(twoColumn, 'hero uses two columns from 1024px');
  const sheet = read('src/css/custom.css');
  assert.match(sheet, /\[class\^='sidebarViewport_'\][^}]*100dvh/);
  assert.match(sheet, /\.theme-doc-toc-desktop[^}]*100dvh - var\(--ifm-navbar-height\)/);
});

test('initial hydration does not fade the largest content elements', () => {
  const motion = read('src/theme/DocItem/Content/useDocMotion.ts');
  assert.match(motion, /previousRoute/);
  assert.match(motion, /if \(routeChanged\) gsap\.fromTo\(root/);
  assert.doesNotMatch(read('src/pages/index.module.css'), /\.entered\s*\{\s*animation:/);
  // The hero entrance is CSS from first paint; hydration never re-hides it, and no motion library ships on the homepage.
  const hook = read('src/components/HomeMotion/useHomeMotion.ts');
  assert.doesNotMatch(hook, /gsap|\.title|\.word|\.intro/);
  assert.doesNotMatch(read('src/pages/index.tsx'), /from 'gsap'|@gsap\/react/);
});

test('both landing locales have identical field shapes and preserve the six feature destinations', () => {
  const ast = parse(read('src/data/landing.ts'), {sourceType: 'module', plugins: ['typescript']});
  const initializer = ast.program.body.find(node => node.type === 'ExportNamedDeclaration').declaration.declarations[0].init;
  const literal = node => {
    if (node.type === 'TSAsExpression' || node.type === 'TSSatisfiesExpression') return literal(node.expression);
    if (node.type === 'ObjectExpression') return Object.fromEntries(node.properties.map(prop => [prop.key.name ?? prop.key.value, literal(prop.value)]));
    if (node.type === 'ArrayExpression') return node.elements.map(literal);
    if (node.type === 'StringLiteral') return node.value;
    throw new Error(`unexpected landing value: ${node.type}`);
  };
  const {zh, en} = literal(initializer);
  const shape = value => Array.isArray(value) ? value.map(shape) : typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, shape(entry)])) : typeof value;
  assert.equal(JSON.stringify(shape(zh)), JSON.stringify(shape(en)));
  const paths = ['/features/web-runtime', '/features/mybatis-plus', '/features/rate-limit', '/features/storage', '/features/audit', '/feature-toggles'];
  for (const locale of [zh, en]) assert.equal(JSON.stringify(locale.features.map(feature => feature[3])), JSON.stringify(paths));
});

// The copy control moved from DocItem/Content to the breadcrumb toolbar (PageActions); same contract.
test('Markdown copy feedback is announced, localized and does not bounce', () => {
  const source = read('src/components/PageActions/index.tsx');
  assert.match(read('src/theme/DocBreadcrumbs/index.tsx'), /<PageActions /);
  const ast = parse(source, {sourceType: 'module', plugins: ['typescript', 'jsx']});
  const elements = [];
  const collect = node => {
    if (!node || typeof node !== 'object') return;
    if (node.type === 'JSXOpeningElement') elements.push(node);
    for (const value of Object.values(node)) {
      if (Array.isArray(value)) value.forEach(collect);
      else if (value && typeof value === 'object') collect(value);
    }
  };
  collect(ast);
  assert.ok(elements.some(node => attribute(node, 'aria-live')?.value === 'polite'), 'copy result needs a persistent live region');
  assert.ok(elements.some(node => node.name.name === 'button' && attribute(node, 'disabled')), 'in-flight copy must prevent duplicate requests');
  assert.ok(elements.some(node => node.name.name === 'button' && attribute(node, 'aria-label')?.type === 'JSXExpressionContainer'), 'button name must use its localized label');
  assert.doesNotMatch(source, /gsap|<svg/);
});
