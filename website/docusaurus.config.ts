import type {PrismTheme} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

// CSS variables keep syntax colors in sync with both surface themes, including SSR.
const codeTheme: PrismTheme = {
  plain: {color: 'var(--coco-text)', backgroundColor: 'var(--coco-code-bg)'},
  styles: [
    {types: ['comment', 'prolog', 'doctype', 'cdata', 'namespace'], style: {color: 'var(--coco-text-muted)'}},
    {types: ['string', 'attr-value', 'inserted'], style: {color: 'var(--coco-code-string)'}},
    {types: ['atrule', 'keyword', 'attr-name', 'selector'], style: {color: 'var(--coco-code-keyword)'}},
    {types: ['function', 'function-variable', 'deleted', 'tag'], style: {color: 'var(--coco-code-function)'}},
    {types: ['entity', 'url', 'symbol', 'number', 'boolean', 'variable', 'constant', 'property', 'regex'], style: {color: 'var(--coco-code-literal)'}},
    {types: ['class-name', 'builtin'], style: {color: 'var(--coco-code-type)'}},
  ],
};

const homeMotionGate = `(function(){var d=document.documentElement;try{if(!('IntersectionObserver' in window)||window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;d.setAttribute('data-coco-motion','');setTimeout(function(){if(d.getAttribute('data-coco-motion')!=='armed')d.removeAttribute('data-coco-motion');},4000);}catch(e){d.removeAttribute('data-coco-motion');}})();`;

const config: Config = {
  title: 'Coco Framework',
  titleDelimiter: '·',
  tagline: '少写基础设施，多写业务。',
  // The tab icon is the two-bean mark on a transparent ground (no tile), recoloured for dark browser chrome.
  favicon: 'img/brand/favicon.svg',

  future: {
    v4: true,
  },

  url: process.env.COCO_SITE_URL || 'https://patton174.github.io',
  baseUrl: process.env.COCO_BASE_URL || '/coco-framework/',

  organizationName: 'patton174',
  projectName: 'coco-framework',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  plugins: ['./plugins/site-seo.cjs', './plugins/mobile-routes.cjs'],
  clientModules: ['./src/clientModules/routeTransition.ts'],
  headTags: [
    {tagName: 'link', attributes: {rel: 'apple-touch-icon', href: `${process.env.COCO_BASE_URL || '/coco-framework/'}img/brand/apple-touch-icon.png`}},
    // Motion gate, set before first paint: CSS only hides or animates content under html[data-coco-motion].
    // No JS or reduced motion: attribute never set, content renders in its final state. Safety net: if the
    // homepage motion hook has not armed (attribute value "armed") within 4s, the attribute is removed.
    {tagName: 'script', attributes: {}, innerHTML: homeMotionGate},
  ],

  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans', 'en'],
    localeConfigs: {
      'zh-Hans': {label: '简体中文', htmlLang: 'zh-Hans'},
      en: {label: 'English', htmlLang: 'en'},
    },
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: '/',
          editUrl:
            'https://github.com/patton174/coco-framework/tree/main/website/',
          // Git commit time per document; feeds the sitemap <lastmod>. CI must check out full history.
          showLastUpdateTime: true,
        },
        blog: false,
        // /m/ is the phone shell of the same documents: it is noindex, canonical to the desktop page, and kept out of the sitemap.
        sitemap: {lastmod: 'date', changefreq: 'weekly', priority: 0.5, ignorePatterns: ['**/404.html', '**/m/**', '**/m']},
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/brand/social-card.png',
    metadata: [
      // Search Console ownership (HTML tag method). Built into every deployment, so the GitHub Pages URL prefix
      // and the custom domain verify with the same token. Not a secret; it only proves control of the site.
      {name: 'google-site-verification', content: 't5EgGuNWZ2_VdsT5X_pp0RWlqX2OqJ2vcy3zYHcOoPE'},
      {property: 'og:type', content: 'website'},
      {property: 'og:site_name', content: 'Coco Framework'},
      {property: 'og:image:width', content: '1200'},
      {property: 'og:image:height', content: '630'},
      {property: 'og:image:alt', content: 'Coco Framework — Less infrastructure. More product.'},
      {name: 'keywords', content: 'Spring Boot, Java framework, Spring Boot starter, distributed lock, rate limiting, idempotency, multi-tenant, data permissions, audit logging'},
    ],
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      // The brand is live serif text only (no mark beside it): crisp at any scale and set in the same face as
      // the headings. The mark itself lives in the favicon, social cards and the mobile shell.
      title: 'Coco Framework',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: '文档',
        },
        {
          type: 'docSidebar',
          sidebarId: 'skillsSidebar',
          position: 'left',
          label: '技能',
        },
        {
          type: 'docSidebar',
          sidebarId: 'releasesSidebar',
          position: 'left',
          label: '更新日志',
        },
        {
          // Icon-only 文A / A文 switch — see src/theme/NavbarItem/ComponentTypes.
          type: 'custom-localeToggle',
          position: 'right',
        },
        {
          href: 'https://github.com/patton174/coco-framework',
          position: 'right',
          className: 'navbar-github-link',
          'aria-label': 'GitHub repository',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '文档',
          items: [
            {label: '概览', to: '/overview'},
            {label: '快速开始', to: '/getting-started'},
            {label: '特性开关', to: '/feature-toggles'},
            {label: '功能总览', to: '/features/web-runtime'},
          ],
        },
        {
          title: '技能',
          items: [
            {label: '技能概览', to: '/skills'},
            {label: '快捷安装', to: '/skills/install'},
            {label: '使用指南', to: '/skills/usage'},
          ],
        },
        {
          title: '能力',
          items: [
            {label: '核心 Web', to: '/features/web-runtime'},
            {label: '数据访问', to: '/features/mybatis-plus'},
            {label: '流控与可靠性', to: '/features/rate-limit'},
            {label: '对象存储', to: '/features/storage'},
          ],
        },
        {
          title: '资源',
          items: [
            {label: 'GitHub', href: 'https://github.com/patton174/coco-framework'},
            {label: 'Maven Central', href: 'https://central.sonatype.com/artifact/io.github.patton174/coco-framework'},
            {label: 'npm · 技能包', href: 'https://www.npmjs.com/package/@patton174/coco-agent-skills'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Coco Framework · Apache-2.0 · Built with Docusaurus.`,
    },
    prism: {
      theme: codeTheme,
      darkTheme: codeTheme,
      additionalLanguages: ['java', 'yaml', 'properties', 'bash', 'json', 'sql'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
