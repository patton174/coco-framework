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
  return <>
    <PageMetadata title={metadata.title} description={description}
      keywords={frontMatter.keywords ?? ['Spring Boot', 'Coco Framework', metadata.title]}
      image={assets.image ?? frontMatter.image} />
    <Head>
      <meta name="twitter:title" content={`${metadata.title} · Coco Framework`} />
      <meta name="twitter:description" content={description} />
      <link rel="alternate" media="only screen and (max-width: 640px)" href={phoneUrl} />
    </Head>
  </>;
}
