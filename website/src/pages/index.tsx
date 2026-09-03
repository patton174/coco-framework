import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className={clsx('container', styles.heroInner)}>
        <span className={styles.badge}>
          <span className={styles.badgeDot} />
          Spring Boot 4.1 · Java 17+
        </span>
        <Heading as="h1" className={styles.heroTitle}>
          {siteConfig.title}
        </Heading>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className={clsx('button button--primary button--lg', styles.primaryBtn)}
            to="/getting-started">
            快速开始 →
          </Link>
          <Link
            className={clsx('button button--outline button--lg', styles.ghostBtn)}
            to="/overview">
            了解框架
          </Link>
        </div>
        <div className={styles.install}>
          <div className={styles.installBar}>
            <span className={styles.dot} />
            <span className={styles.dot} />
            <span className={styles.dot} />
            <span className={styles.installLabel}>pom.xml</span>
          </div>
          <pre>
{`<dependency>
  <groupId>`}<span className={styles.tok}>io.github.patton174</span>{`</groupId>
  <artifactId>`}<span className={styles.tok}>coco-spring-boot-starter</span>{`</artifactId>
</dependency>`}
          </pre>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="高约定的 Spring Boot Web 服务端框架，快速构建生产可用的 Java 服务">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
