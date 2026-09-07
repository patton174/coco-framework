import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'Coco Framework',
  titleDelimiter: '·',
  tagline: '少写基础设施，多写业务。',
  favicon: 'img/brand/logo.svg',

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
  plugins: ['./plugins/site-seo.cjs'],
  headTags: [
    {tagName: 'link', attributes: {rel: 'apple-touch-icon', href: `${process.env.COCO_BASE_URL || '/coco-framework/'}img/brand/apple-touch-icon.png`}},
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
        },
        blog: false,
        sitemap: {changefreq: 'weekly', priority: 0.5, ignorePatterns: ['**/404.html']},
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/brand/social-card.png',
    metadata: [
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
      title: 'Coco Framework',
      logo: {
        alt: 'Coco Framework Logo',
        src: 'img/brand/logo.svg',
        width: 32,
        height: 32,
      },
      items: [
        {to: '/releases', label: '更新日志', position: 'left'},
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
      theme: {...prismThemes.github, styles: [...prismThemes.github.styles, {types: ['comment'], style: {color: '#626258'}}]},
      darkTheme: {...prismThemes.dracula, styles: [...prismThemes.dracula.styles, {types: ['comment'], style: {color: '#a5afd0'}}]},
      additionalLanguages: ['java', 'yaml', 'properties', 'bash', 'json', 'sql'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
