/**
 * Related open-source projects shown in the margins of the homepage chapters (see components/Partner).
 *
 * Each entry sits beside the chapter it is relevant to, so the link is editorial and on-topic rather than a
 * banner. Everything here is public information; none of these projects sponsors or endorses Coco, and the card
 * says so by calling them "related projects" and linking to the project itself.
 *
 * Star counts are a snapshot (GitHub API, 2026-10-11) shown rounded and labelled as such in the tooltip. Refresh
 * them when the entries are next reviewed; they are context, not a claim that must stay live.
 */
export const PARTNERS_AS_OF = '2026-10-11';

export type PartnerChapter = 'capabilities' | 'architecture' | 'getting-started' | 'ecosystem';

interface PartnerCopy { tagline: string; why: string }
export interface Partner {
  chapter: PartnerChapter;
  /** The project's own published artwork, in static/img/partners (see the README there for sources). */
  image: string;
  /** `contain` keeps a diagram or mascot whole; `cover` fills the frame with a screenshot. */
  fit: 'contain' | 'cover';
  /** Which page margin the card stands in. It is always the side the page thread is not on. */
  side: 'left' | 'right';
  name: string;
  url: string;
  stars: number;
  license: string;
  zh: PartnerCopy;
  en: PartnerCopy;
}

/** Card labels per locale. */
export const partnerLabels = {
  zh: {badge: '热门推荐', kicker: '相关项目', stars: 'GitHub Stars', view: '查看项目', asOf: '截至', region: '相关开源项目'},
  en: {badge: 'Featured', kicker: 'Related project', stars: 'GitHub stars', view: 'View project', asOf: 'as of', region: 'Related open-source projects'},
} as const;

export const partners: readonly Partner[] = [
  {
    chapter: 'capabilities', image: 'mybatis-plus.webp', fit: 'contain', side: 'right',
    name: 'MyBatis-Plus', url: 'https://github.com/baomidou/mybatis-plus', stars: 17479, license: 'Apache-2.0',
    zh: {tagline: 'MyBatis 增强工具包', why: 'Coco 的分页、租户隔离与数据权限都接入在它的拦截器之上。'},
    en: {tagline: 'MyBatis, enhanced', why: 'Coco’s paging, tenant isolation and data permissions plug into its interceptors.'},
  },
  {
    chapter: 'architecture', image: 'springblade.webp', fit: 'contain', side: 'left',
    name: 'SpringBlade', url: 'https://github.com/chillzhuang/SpringBlade', stars: 6945, license: 'Apache-2.0',
    zh: {tagline: 'SaaS 多租户微服务平台', why: '基于 Spring Boot 与 Spring Cloud，适合参考完整的分层与二次开发方式。'},
    en: {tagline: 'Multi-tenant SaaS platform', why: 'Built on Spring Boot and Spring Cloud; a full reference for layering and extension.'},
  },
  {
    chapter: 'getting-started', image: 'jhipster.webp', fit: 'contain', side: 'right',
    name: 'JHipster', url: 'https://github.com/jhipster/generator-jhipster', stars: 22462, license: 'Apache-2.0',
    zh: {tagline: '应用与微服务生成器', why: '快速生成 Spring Boot 工程与微服务架构，想从脚手架起步时值得一看。'},
    en: {tagline: 'App and microservice generator', why: 'Quickly generates Spring Boot apps and microservice architectures; worth a look when you start from scaffolding.'},
  },
  {
    chapter: 'ecosystem', image: 'ruoyi-vue-pro.webp', fit: 'cover', side: 'left',
    name: 'ruoyi-vue-pro', url: 'https://github.com/YunaiV/ruoyi-vue-pro', stars: 39644, license: 'MIT',
    zh: {tagline: '开箱即用的后台管理系统', why: '多租户、工作流、ERP 与 CRM 一应俱全，常见的二次开发起点。'},
    en: {tagline: 'Batteries-included admin system', why: 'Multi-tenancy, workflows, ERP and CRM in one codebase; a common base for custom builds.'},
  },
];
