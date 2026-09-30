import type {ReactNode} from 'react';
import Head from '@docusaurus/Head';
import {PageMetadata} from '@docusaurus/theme-common';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

export default function DocItemMetadata(): ReactNode {
  const {metadata, frontMatter, assets} = useDoc();
  const {i18n} = useDocusaurusContext();
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
    </Head>
  </>;
}
