import {useEffect, useRef, type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Head from '@docusaurus/Head';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import {ArrowRight, Blocks, Database, FileBox, Fingerprint, Gauge, Globe, Layers, Repeat2, Settings2, ShieldCheck} from 'lucide-react';
import {landing} from '@site/src/data/landing';
import styles from './index.module.css';

const starter = `<dependency>
  <groupId>io.github.patton174</groupId>
  <artifactId>coco-spring-boot-starter</artifactId>
</dependency>`;
const assembly = `@Configuration
class WebInfrastructure {
  // ResponseBodyAdvice
  // RestControllerAdvice
  // TraceId filter
  // Access-log filter
  // Context propagation
}`;
const featureIcons = [Globe, Database, Gauge, FileBox, Fingerprint, Settings2];
const painIcons = [Repeat2, Settings2, Layers, ShieldCheck];

// Observe individual items rather than a tall section: mobile cards enter at
// their own scroll position. Server-rendered content never starts hidden.
function Reveal({children, className = ''}: {children: ReactNode; className?: string}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const root = ref.current;
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!root || !window.IntersectionObserver || !Element.prototype.animate || preference.matches) return;
    const animations = new Set<Animation>();
    const targets = root.querySelectorAll<HTMLElement>(
      `:scope > h2, :scope > .${styles.sectionIntro}, article, ol > li, :scope > div > h2`,
    );
    const observer = new IntersectionObserver(entries => {
      let order = 0;
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        observer.unobserve(entry.target);
        if (preference.matches) continue;
        const target = entry.target as HTMLElement;
        // A link focused from the keyboard must remain visible immediately.
        if (target.contains(document.activeElement)) continue;
        const animation = target.animate([
          {opacity: 0, transform: 'translateY(22px)'},
          {opacity: 1, transform: 'translateY(0)'},
        ], {
          duration: 640, delay: Math.min(order++ * 75, 225),
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)', fill: 'backwards',
        });
        animations.add(animation);
        animation.onfinish = () => animations.delete(animation);
        animation.oncancel = () => animations.delete(animation);
      }
    }, {threshold: 0.12, rootMargin: '0px 0px -24px 0px'});
    targets.forEach(target => observer.observe(target));
    const finish = () => {
      animations.forEach(animation => animation.cancel());
      animations.clear();
    };
    const onPreference = () => { if (preference.matches) finish(); };
    root.addEventListener('focusin', finish);
    preference.addEventListener('change', onPreference);
    return () => {
      observer.disconnect();
      finish();
      root.removeEventListener('focusin', finish);
      preference.removeEventListener('change', onPreference);
    };
  }, []);
  return <div ref={ref} className={className}>{children}</div>;
}

function Metric({value}: {value: string}) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const target = ref.current;
    const number = Number(value);
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!target || !Number.isFinite(number) || number <= 1 || preference.matches || !window.IntersectionObserver) return;
    let frame = 0;
    const finish = () => {
      cancelAnimationFrame(frame);
      target.textContent = value;
    };
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      observer.disconnect();
      if (preference.matches) return;
      const start = performance.now();
      const tick = (now: number) => {
        const progress = Math.min((now - start) / 1100, 1);
        target.textContent = String(Math.round(number * (1 - Math.pow(1 - progress, 3))));
        if (progress < 1) frame = requestAnimationFrame(tick);
      };
      frame = requestAnimationFrame(tick);
    }, {threshold: 0.8});
    observer.observe(target);
    const onPreference = () => { if (preference.matches) finish(); };
    preference.addEventListener('change', onPreference);
    return () => {
      observer.disconnect();
      finish();
      preference.removeEventListener('change', onPreference);
    };
  }, [value]);
  return <><span className={styles.screenReader}>{value}</span><span ref={ref} aria-hidden="true" className={styles.metric}>{value}</span></>;
}

export default function Home(): ReactNode {
  const {i18n, siteConfig} = useDocusaurusContext();
  const en = i18n.currentLocale === 'en';
  const t = landing[en ? 'en' : 'zh'];
  const logo = useBaseUrl('/img/brand/logo.svg');
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
      <main className={styles.home}>
        <header className={styles.hero}>
          <div className="container">
            <div className={styles.heroGrid}>
              <div>
                <a className={styles.release} href="https://github.com/patton174/coco-framework/releases/tag/v2.0.2">
                  <span />{t.release}<ArrowRight size={15} aria-hidden="true" />
                </a>
                <div className={styles.brand}><img src={logo} width="54" height="54" alt="" /><span>COCO / FRAMEWORK</span></div>
                <h1>Coco Framework</h1>
                <p className={styles.slogan}>{t.slogan}</p>
                <p className={styles.intro}>{t.intro}</p>
                <div className={styles.actions}>
                  <Link className="button button--primary button--lg" to="/getting-started">{t.start}<ArrowRight size={18} aria-hidden="true" /></Link>
                  <a className={styles.textLink} href="#capabilities">{t.explore} ↓</a>
                </div>
              </div>
              <div className={styles.editor}>
                <div className={styles.editorBar}><span className={styles.lights}>● ● ●</span><span>pom.xml</span><span>JAVA / MAVEN</span></div>
                <div className={styles.editorIntro}><Blocks size={20} aria-hidden="true" />{en ? 'One starter. Your own business code.' : '一个 Starter，业务代码依然由你掌控。'}</div>
                <CodeBlock language="xml">{starter}</CodeBlock>
                <div className={styles.editorNote}>↳ {en ? 'Requires coco-parent or the Coco BOM.' : '配合 coco-parent 或 Coco BOM 使用。'} <Link to="/getting-started">{en ? 'Full setup' : '完整接入步骤'} →</Link></div>
                <div className={styles.request}><span>GET /hello</span><strong>200 OK</strong></div>
                <p className={styles.responseNote}>{en ? 'Responses · Exceptions · TraceId' : '统一响应 · 全局异常 · TraceId'}</p>
              </div>
            </div>
            <dl className={styles.stats}>{t.stats.map(([value, label]) => <div key={label}><dt><Metric value={value} /></dt><dd>{label}</dd></div>)}</dl>
            <p className={styles.versionNote}>{t.versionNote}</p>
          </div>
        </header>
        <section className={styles.section}>
          <Reveal className="container">
            <span className={styles.eyebrow}>01 / WHY COCO</span><h2>{t.painTitle}</h2><p className={styles.sectionIntro}>{t.painIntro}</p>
            <div className={styles.painGrid}>{t.pains.map(([title, text], i) => {const Icon = painIcons[i]; return <article className={styles.pain} key={title}><Icon aria-hidden="true" size={24} /><h3>{title}</h3><p>{text}</p></article>;})}</div>
          </Reveal>
        </section>
        <section id="capabilities" className={`${styles.section} ${styles.tinted}`}>
          <Reveal className="container">
            <span className={styles.eyebrow}>02 / CAPABILITIES</span><h2>{t.featuresTitle}</h2><p className={styles.sectionIntro}>{t.featuresIntro}</p>
            <div className={styles.featureGrid}>{t.features.map(([title, value, items, path], i) => {const Icon = featureIcons[i]; return <article className={styles.feature} key={title}><Icon size={25} aria-hidden="true" /><h3><Link to={path}>{title}<ArrowRight size={17} aria-hidden="true" /></Link></h3><p>{value}</p><ul>{items.map(item => <li key={item}>{item}</li>)}</ul></article>;})}</div>
          </Reveal>
        </section>
        <section className={styles.section}>
          <Reveal className="container">
            <span className={styles.eyebrow}>03 / LESS ASSEMBLY</span><h2>{t.compareTitle}</h2>
            <div className={styles.compare}>
              <article><h3>{t.before}</h3><p>{t.beforeText}</p><CodeBlock language="java">{assembly}</CodeBlock></article>
              <article><h3>{t.after}</h3><p>{t.afterText}</p><CodeBlock language="xml">{starter}</CodeBlock></article>
            </div>
            <p className={styles.callout}>{t.scopeNote}</p>
          </Reveal>
        </section>
        <section className={`${styles.section} ${styles.tinted}`}>
          <Reveal className={`container ${styles.architecture}`}>
            <div><span className={styles.eyebrow}>04 / ARCHITECTURE</span><h2>{t.architectureTitle}</h2><p className={styles.sectionIntro}>{t.scopeNote}</p><Link className={styles.textLink} to="/overview">{en ? 'Explore the design' : '了解设计边界'} →</Link></div>
            <ol className={styles.layers}>{t.layers.map(([name, desc], i) => <li key={name}><span>0{i + 1}</span><div><strong>{name}</strong><p>{desc}</p></div><span aria-hidden="true">{i < 3 ? '↓' : '●'}</span></li>)}</ol>
          </Reveal>
        </section>
        <section className={styles.section}>
          <Reveal className="container">
            <span className={styles.eyebrow}>05 / ECOSYSTEM</span><h2>{t.ecosystemTitle}</h2>
            <div className={styles.ecosystem}>{t.ecosystem.map(([name, desc, repo], i) => <article key={name}><span className={styles.eyebrow}>COCO / 0{i + 1}</span><h3><a href={`https://github.com/patton174/${repo}`}>{name} ↗</a></h3><p>{desc}</p></article>)}</div>
            <p className={styles.versionNote}>{t.dependency}</p>
          </Reveal>
        </section>
        <section className={`${styles.section} ${styles.tinted}`}>
          <Reveal className="container">
            <span className={styles.eyebrow}>06 / GET STARTED</span><h2>{t.quickTitle}</h2>
            <ol className={styles.steps}>{t.steps.map(([title, desc], i) => <li key={title}><span>0{i + 1}</span><h3>{title}</h3><p>{desc}</p></li>)}</ol>
            <Link className="button button--primary button--lg" to="/getting-started">{en ? 'Follow the getting-started guide' : '跟着指南完成第一个接口'} →</Link>
          </Reveal>
        </section>
        <section className={`${styles.section} ${styles.community}`}>
          <Reveal className="container">
            <span className={styles.eyebrow}>BUILT IN THE OPEN</span><h2>{t.communityTitle}</h2><p className={styles.sectionIntro}>{t.communityText}</p>
            <div className={styles.actions}>{['discussions', 'issues', 'blob/main/CONTRIBUTING.md'].map((path, i) => <a key={path} className="button button--secondary" href={`https://github.com/patton174/coco-framework/${path}`}>{t.community[i]} ↗</a>)}</div>
            <p className={styles.star}>{t.star}</p>
          </Reveal>
        </section>
      </main>
    </Layout>
  );
}
