import type {ReactNode} from 'react';
import Head from '@docusaurus/Head';
import {PageMetadata} from '@docusaurus/theme-common';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';

/** Doc permalink without the site and locale base ("/en/features/audit" -> "features/audit"). */
function useDocRelativePath(permalink: string): string {
  const {i18n, siteConfig} = useDocusaurusContext();
  const localeBase = useBaseUrl('/');
  const rest = permalink.startsWith(localeBase) ? permalink.slice(localeBase.length) : permalink.replace(siteConfig.baseUrl, '');
  return rest.replace(/^\/+|\/+$/g, '') || (i18n.currentLocale ? '' : '');
}

export default function DocItemMetadata(): ReactNode {
  const {metadata, frontMatter, assets} = useDoc();
  const {i18n, siteConfig} = useDocusaurusContext();
  // Separate-URLs mobile annotation: this page's phone twin lives under /m/ (see plugins/mobile-routes.cjs).
  const phonePath = useBaseUrl(`/m/${useDocRelativePath(metadata.permalink)}`);
  const phoneUrl = `${siteConfig.url}${phonePath}`;
  const description = metadata.description || (i18n.currentLocale === 'en'
    ? `${metadata.title}: configuration and integration guide for Coco Framework on Spring Boot.`
    : `${metadata.title}：Coco Framework 的配置、接入与扩展指南，适用于 Spring Boot 应用。`);
  const en = i18n.currentLocale === 'en';
  // The visible heading stays `title`; the <title>/og:title may carry the words people search for (`seo_title`).
  const custom = (frontMatter as {seo_title?: unknown}).seo_title;
  const seoTitle = typeof custom === 'string' ? custom : metadata.title;
  // One generated share card per page and locale (scripts/generate-social-cards.py); falls back to the site card.
  const card = `img/social/${en ? 'en' : 'zh'}/${metadata.id}.png`;
  // Absolute, locale-aware URL of this page; Docusaurus's own BreadcrumbList covers the trail.
  const pageUrl = `${siteConfig.url}${metadata.permalink}`;
  const article = {
    '@context': 'https://schema.org',
    '@type': 'TechArticle',
    headline: metadata.title,
    description,
    url: pageUrl,
    mainEntityOfPage: pageUrl,
    inLanguage: en ? 'en' : 'zh-Hans',
    isPartOf: {'@type': 'WebSite', name: 'Coco Framework', url: `${siteConfig.url}${siteConfig.baseUrl}`},
    about: {'@type': 'SoftwareApplication', name: 'Coco Framework', url: 'https://github.com/patton174/coco-framework'},
    author: {'@type': 'Organization', name: 'Coco Framework', url: 'https://github.com/patton174/coco-framework'},
  };
  return <>
    <PageMetadata title={seoTitle} description={description}
      keywords={frontMatter.keywords ?? ['Spring Boot', 'Coco Framework', metadata.title]}
      image={assets.image ?? frontMatter.image ?? card} />
    <Head>
      <meta name="twitter:title" content={`${seoTitle} · Coco Framework`} />
      <meta name="twitter:description" content={description} />
      <link rel="alternate" media="only screen and (max-width: 640px)" href={phoneUrl} />
      <script type="application/ld+json">{JSON.stringify(article)}</script>
    </Head>
  </>;
}
