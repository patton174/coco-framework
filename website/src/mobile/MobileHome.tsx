import {useRef, type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Icon from '@site/src/components/Icon';
import {landing} from '@site/src/data/landing';
import MobileShell from './MobileShell';
import {BackdropArt, DialArt, HeroArt} from './MobileArt';
import {useMobile} from './useMobile';
import {useMobileMotion} from './useMobileMotion';
import styles from './styles.module.css';
import home from './home.module.css';

/**
 * Mobile home: the pitch, the four ways in and the six capability areas, over a line-art backdrop.
 * Content is plain and complete in the markup; useMobileMotion only animates it (see the hook for the gate).
 */
export default function MobileHome(): ReactNode {
  const {t, locale} = useMobile();
  const copy = landing[locale];
  const rootRef = useRef<HTMLDivElement>(null);
  useMobileMotion(rootRef);
  const entries = [
    {to: '/m/getting-started', icon: 'arrow-right', title: t.start, hint: copy.setupLink},
    {to: '/m/overview', icon: 'book', title: t.docs, hint: t.allDocs},
    {to: '/m/feature-toggles', icon: 'list', title: copy.featurePlanLink, hint: t.explore},
    {to: '/m/skills', icon: 'spark', title: t.skills, hint: copy.features[5][1]},
  ] as const;

  return (
    <MobileShell title={locale === 'en' ? 'Coco Framework - Spring Boot infrastructure' : 'Coco Framework - Spring Boot 基础设施框架'} description={copy.description} canonicalPath="/">
      <div className={home.page} ref={rootRef}>
        <BackdropArt />
        <div className={home.content}>
          <section className={home.hero} aria-labelledby="m-title">
            <HeroArt />
            <a className={home.release} data-m="hero-line" data-mr="" href="https://github.com/patton174/coco-framework/releases/tag/v2.0.3">{copy.release}</a>
            <h1 id="m-title" data-m="hero-line" data-mr="">Coco Framework</h1>
            <p className={home.positioning} data-m="hero-line" data-mr="">{copy.positioning}</p>
            <p className={home.intro} data-m="hero-line" data-mr="">{copy.intro}</p>
            <Link className={home.cta} data-m="hero-line" data-mr="" to="/m/getting-started">
              <span>{t.start}</span>
              <Icon name="arrow-right" size={16} />
            </Link>
          </section>

          <div className={home.rule} data-m="facts-rule" />
          <ul className={home.facts} aria-label={t.facts}>
            {copy.stats.map(([value, label]) => (
              <li key={label} data-m="fact" data-mr="">
                <strong data-count={/^\d+$/.test(value) ? value : undefined}>{value}</strong>
                <span>{label}</span>
              </li>
            ))}
          </ul>

          <ul className={home.entries}>
            {entries.map(entry => (
              <li key={entry.to} data-m="entry" data-mr="">
                <Link to={entry.to} className={home.entry}>
                  <Icon name={entry.icon} size={18} />
                  <span><strong>{entry.title}</strong><small>{entry.hint}</small></span>
                  <Icon name="chevron" size={16} />
                </Link>
              </li>
            ))}
          </ul>

          <h2 className={home.heading} data-m="caps-heading" data-mr="">{copy.featuresTitle}</h2>
          <div className={home.capsWrap}>
            <div className={home.dialSlot}><DialArt count={copy.features.length} /></div>
            <span className={home.rail} aria-hidden="true"><span data-m="rail" /></span>
            <ul className={home.caps} data-m="caps">
              {copy.features.map(([name, value, , path]) => (
                <li key={path} data-m="cap" data-mr="">
                  <Link to={`/m${path}`}>
                    <strong>{name}</strong>
                    <span>{value}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </MobileShell>
  );
}
