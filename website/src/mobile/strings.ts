export type Locale = 'zh' | 'en';

/**
 * Chrome strings of the mobile shell. Kept in code (like data/landing.ts) rather than in Docusaurus
 * translation files: the shell is a fixed, small vocabulary, and a missing key cannot ship untranslated.
 */
const zh = {
    home: '首页', docs: '文档', skills: '技能', releases: '更新', more: '更多',
    toc: '目录', onPage: '本页', chapters: '章节', close: '关闭', back: '返回',
    appearance: '外观', light: '浅色', dark: '深色', auto: '跟随系统',
    language: '语言', otherLanguage: 'English',
    desktop: '桌面版', desktopHint: '在电脑版布局中查看本页',
    share: '分享本页', copied: '链接已复制', shareFailed: '无法分享',
    prev: '上一篇', next: '下一篇', swipe: '左右滑动可翻页',
    github: 'GitHub 仓库', menuTitle: '本页导航', moreTitle: '更多',
    stable: '稳定版', start: '快速开始', explore: '功能导览', allDocs: '全部文档',
    samples: '接入示例', facts: '项目基本信息', readGuide: '阅读指南',
    skip: '跳到正文', progress: '阅读进度', section: '所属章节',
} as const;

export type Strings = { -readonly [K in keyof typeof zh]: string };

const en: Strings = {
    home: 'Home', docs: 'Docs', skills: 'Skills', releases: 'Releases', more: 'More',
    toc: 'Contents', onPage: 'On this page', chapters: 'Sections', close: 'Close', back: 'Back',
    appearance: 'Appearance', light: 'Light', dark: 'Dark', auto: 'System',
    language: 'Language', otherLanguage: '简体中文',
    desktop: 'Desktop site', desktopHint: 'View this page in the desktop layout',
    share: 'Share this page', copied: 'Link copied', shareFailed: 'Could not share',
    prev: 'Previous', next: 'Next', swipe: 'Swipe left or right to turn pages',
    github: 'GitHub repository', menuTitle: 'Page navigation', moreTitle: 'More',
    stable: 'Stable', start: 'Get started', explore: 'Explore features', allDocs: 'All docs',
    samples: 'Setup samples', facts: 'Project facts', readGuide: 'Read the guide',
    skip: 'Skip to content', progress: 'Reading progress', section: 'Section',
};

export const stringsFor = (locale: string): Strings => (locale === 'en' ? en : zh);
