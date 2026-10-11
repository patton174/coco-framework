import type {CSSProperties, ReactNode} from 'react';
import {useRef} from 'react';
import Link from '@docusaurus/Link';
import Head from '@docusaurus/Head';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import {Database, FileBox, Fingerprint, Gauge, Globe, Settings2} from 'lucide-react';
import {landing} from '@site/src/data/landing';
import {useHomeMotion} from '@site/src/components/HomeMotion/useHomeMotion';
import {useStory} from '@site/src/components/HomeMotion/useStory';
import StoryArt, {Outline, RowLine, ROUTE_ARRIVALS_MS, CONNECTOR_ARRIVAL_MS, rowDelay, tierDelay} from '@site/src/components/StoryArt';
import {CornerArrow, LineArrow} from '@site/src/components/Glyphs';
import StarterTabs from '@site/src/components/StarterTabs';
import {ChapterArt, PageBackdrop} from '@site/src/components/PageArt';
import Partner from '@site/src/components/Partner';
import styles from './index.module.css';

const featureIcons = [Globe, Database, Gauge, FileBox, Fingerprint, Settings2];
const index = (value: number) => String(value + 1).padStart(2, '0');

// Values with a single integer (17, Java 17+) count up; the accessible text is always the final value.
function Stat({value}: {value: string}): ReactNode {
  const match = /^(\D*)(\d+)(\D*)$/.exec(value);
  if (!match || Number(match[2]) < 2) return value;
  const [, before, number, after] = match;
  return (
    <>
      <span aria-hidden="true">{before}<span className={styles.count} data-count={number}>{number}</span>{after}</span>
      <span className={styles.srOnly}>{value}</span>
    </>
  );
}

export default function Home(): ReactNode {
  const {i18n, siteConfig} = useDocusaurusContext();
  const rootRef = useRef<HTMLElement>(null);
  useHomeMotion(rootRef);
  useStory(rootRef);
  const en = i18n.currentLocale === 'en';
  const t = landing[en ? 'en' : 'zh'];
  const title = en ? 'Coco Framework - Spring Boot infrastructure' : 'Coco Framework - Spring Boot 基础设施框架';
  const url = `${siteConfig.url}${siteConfig.baseUrl}${en ? 'en/' : ''}`;
  const schema = {
    '@context': 'https://schema.org',
    '@type': ['SoftwareApplication', 'SoftwareSourceCode'],
    name: 'Coco Framework', description: t.description, url,
    applicationCategory: 'DeveloperApplication', operatingSystem: 'JVM',
    programmingLanguage: 'Java', license: 'https://www.apache.org/licenses/LICENSE-2.0',
    codeRepository: 'https://github.com/patton174/coco-framework',
    isAccessibleForFree: true,
  };

  return (
    <Layout title={title} description={t.description}>
      <Head>
        <title>{title}</title>
        <meta property="og:title" content={title} />
        <meta name="twitter:title" content={title} />
        <meta name="twitter:description" content={t.description} />
        <script type="application/ld+json">{JSON.stringify(schema)}</script>
      </Head>
      <main ref={rootRef} className={styles.home} data-coco-home="">
        <PageBackdrop />
        <span className={styles.railInk} data-rail-ink="" aria-hidden="true" />
        <header className={styles.hero}>
          <div className={`${styles.container} ${styles.heroGrid}`}>
            <div className={styles.copy}>
              <a className={styles.release} data-node="" href="https://github.com/patton174/coco-framework/releases/tag/v2.0.3">
                {t.release}<CornerArrow size={12} />
              </a>
              <h1 className={styles.title}><span className={styles.word}>Coco</span> <span className={styles.word}>Framework</span></h1>
              <p className={styles.positioning}>{t.positioning}</p>
              <p className={styles.intro}>{t.intro}</p>
              <div className={styles.actions}>
                <Link className={`button button--primary ${styles.primary}`} to="/getting-started">
                  <span>{t.start}</span><LineArrow size={13} />
                </Link>
                <a className={styles.outline} href="#capabilities">
                  <span>{t.explore}</span><LineArrow size={13} direction="down" />
                </a>
              </div>
            </div>
            <div className={styles.aside}>
              <StarterTabs label={t.samplesLabel} samples={t.samples} />
              <Outline frame start={1100} />
            </div>
            <ul className={styles.facts} aria-label={t.factsLabel} data-reveal="line">
              {t.stats.map(([value, label]) => (
                <li key={label} data-reveal=""><strong><Stat value={value} /></strong><span className={styles.factLabel}>{label}</span></li>
              ))}
            </ul>
          </div>
        </header>

        <section id="capabilities" className={styles.section} aria-labelledby="capabilities-title">
          <ChapterArt kind="modules" />
          <div className={`${styles.container} ${styles.split}`}>
            <Partner chapter="capabilities" />
            <div className={styles.lede} data-reveal="">
              <p className={styles.eyebrow} data-node="" aria-hidden="true">01 / Capabilities</p>
              <h2 id="capabilities-title">{t.featuresTitle}</h2>
              <p className={styles.lead}>{t.featuresIntro}</p>
              <Link className={styles.textLink} to="/feature-toggles"><span>{t.featurePlanLink}</span><LineArrow size={11} /></Link>
            </div>
            <ol className={styles.index}>
              {t.features.map(([name, value, items, path], position) => {
                const Icon = featureIcons[position];
                return (
                  <li className={styles.cap} key={path} data-reveal="" data-at={rowDelay(position)} data-glyph-host="">
                    <RowLine position={position} last={position === t.features.length - 1} />
                    <span className={styles.capIndex} aria-hidden="true">{index(position)}</span>
                    <div className={styles.capBody}>
                      <h3><Link to={path}><span>{name}</span><LineArrow size={12} /></Link></h3>
                      <p>{value}</p>
                      <ul>{items.map(item => <li key={item}>{item}</li>)}</ul>
                    </div>
                    <Icon className={styles.capIcon} size={22} aria-hidden="true" />
                  </li>
                );
              })}
            </ol>
          </div>
        </section>

        <section id="architecture" className={styles.section} aria-labelledby="architecture-title">
          <ChapterArt kind="planes" />
          <div className={`${styles.container} ${styles.split} ${styles.splitCenter}`}>
            <Partner chapter="architecture" />
            <div className={styles.lede} data-reveal="">
              <p className={styles.eyebrow} data-node="" aria-hidden="true">02 / Architecture</p>
              <h2 id="architecture-title">{t.architectureTitle}</h2>
              <p className={styles.sectionIntro}>{t.pains[3][1]}</p>
              <p className={styles.scope}>{t.scopeNote}</p>
              <Link className={styles.textLink} to="/overview"><span>{t.architectureLink}</span><LineArrow size={11} /></Link>
            </div>
            <ol className={styles.tiers}>
              {t.layers.map(([name, description], position) => (
                // Business code sits on top; each layer below is wider, ending in the shared foundation.
                <li className={styles.tier} key={name} data-tier={position} data-reveal="stack" data-at={tierDelay(t.layers.length - 1 - position)}>
                  <Outline start={tierDelay(t.layers.length - 1 - position)} accent={position === t.layers.length - 1} />
                  <strong>{name}</strong>
                  <p>{description}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="getting-started" className={styles.section} aria-labelledby="getting-started-title">
          <div className={`${styles.container} ${styles.stairs}`}>
            <Partner chapter="getting-started" />
            <div className={styles.lede} data-reveal="">
              <p className={styles.eyebrow} data-node="" aria-hidden="true">03 / Getting started</p>
              <h2 id="getting-started-title">{t.quickTitle}</h2>
              <Link className={styles.textLink} to="/getting-started"><span>{t.guideLink}</span><LineArrow size={11} /></Link>
            </div>
            <div className={styles.track}>
              <StoryArt kind="route" />
              <ol className={styles.steps}>
              {t.steps.map(([name, description], position) => (
                <li key={name} data-reveal="" data-at={Math.round(ROUTE_ARRIVALS_MS[position])}><span className={styles.stepNumber} aria-hidden="true">{index(position)}</span><h3>{name}</h3><p>{description}</p></li>
              ))}
              </ol>
            </div>
          </div>
        </section>

        <section id="ecosystem" className={styles.section} aria-labelledby="ecosystem-title">
          <ChapterArt kind="system" />
          <div className={styles.container}>
            <Partner chapter="ecosystem" />
            <div className={styles.sectionHead} data-reveal="">
              <p className={styles.eyebrow} data-node="" aria-hidden="true">04 / Ecosystem</p>
              <h2 id="ecosystem-title">{t.ecosystemTitle}</h2>
              <p className={styles.lead}>{t.dependency}</p>
            </div>
            <div className={styles.repos}>
              <StoryArt kind="connector" />
              {t.ecosystem.map(([name, description, repo], position) => (
                <article key={repo} className={position === 0 ? `${styles.repo} ${styles.repoFeatured}` : styles.repo} data-reveal="" data-at={position === 0 ? 0 : CONNECTOR_ARRIVAL_MS + (position - 1) * 140} data-glyph-host="">
                  <h3><a href={`https://github.com/patton174/${repo}`}>{name}</a></h3>
                  <p>{description}</p>
                  <span className={styles.repoSlug}>{repo}<CornerArrow size={14} /></span>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="community" className={styles.section} aria-labelledby="community-title">
          <div className={styles.container}>
            <StoryArt kind="stars" />
            <div className={styles.closing} data-reveal="">
              <p className={styles.eyebrow} data-node="" aria-hidden="true">05 / Community</p>
              <h2 id="community-title">{t.communityTitle}</h2>
              <p>{t.communityText}</p>
            </div>
            <ul className={styles.communityLinks} data-reveal="">
              {['discussions', 'issues', 'blob/main/CONTRIBUTING.md'].map((path, position) => (
                <li key={path} style={{'--i': position} as CSSProperties}>
                  <a className={styles.textLink} href={`https://github.com/patton174/coco-framework/${path}`}><span>{t.community[position]}</span><CornerArrow size={14} /></a>
                </li>
              ))}
            </ul>
            <p className={styles.star} data-reveal="">{t.star}</p>
          </div>
        </section>
      </main>
    </Layout>
  );
}
