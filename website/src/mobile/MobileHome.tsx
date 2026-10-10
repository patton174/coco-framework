import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Icon from '@site/src/components/Icon';
import {landing} from '@site/src/data/landing';
import MobileShell from './MobileShell';
import {useMobile} from './useMobile';
import styles from './styles.module.css';

/** Mobile home: one thumb-sized screen of the pitch, the four ways in, and the six capability areas. */
export default function MobileHome(): ReactNode {
  const {t, locale} = useMobile();
  const copy = landing[locale];
  const entries = [
    {to: '/m/getting-started', icon: 'arrow-right', title: t.start, hint: copy.setupLink},
    {to: '/m/overview', icon: 'book', title: t.docs, hint: t.allDocs},
    {to: '/m/feature-toggles', icon: 'list', title: copy.featurePlanLink, hint: t.explore},
    {to: '/m/skills', icon: 'spark', title: t.skills, hint: copy.features[5][1]},
  ] as const;

  return (
    <MobileShell title={locale === 'en' ? 'Coco Framework - Spring Boot infrastructure' : 'Coco Framework - Spring Boot 基础设施框架'} description={copy.description} canonicalPath="/">
      <div className={styles.home}>
        <section className={styles.hero} aria-labelledby="m-title">
          <a className={styles.release} href="https://github.com/patton174/coco-framework/releases/tag/v2.0.3">{copy.release}</a>
          <h1 id="m-title">Coco Framework</h1>
          <p className={styles.positioning}>{copy.positioning}</p>
          <p className={styles.intro}>{copy.intro}</p>
          <Link className={styles.cta} to="/m/getting-started">
            <span>{t.start}</span>
            <Icon name="arrow-right" size={18} />
          </Link>
        </section>

        <ul className={styles.facts} aria-label={t.facts}>
          {copy.stats.map(([value, label]) => (
            <li key={label}><strong>{value}</strong><span>{label}</span></li>
          ))}
        </ul>

        <ul className={styles.entries}>
          {entries.map(entry => (
            <li key={entry.to}>
              <Link to={entry.to} className={styles.entry}>
                <Icon name={entry.icon} size={22} />
                <span><strong>{entry.title}</strong><small>{entry.hint}</small></span>
                <Icon name="chevron" size={18} />
              </Link>
            </li>
          ))}
        </ul>

        <h2 className={styles.homeHeading}>{copy.featuresTitle}</h2>
        <ul className={styles.caps}>
          {copy.features.map(([name, value, , path]) => (
            <li key={path}>
              <Link to={`/m${path}`}>
                <strong>{name}</strong>
                <span>{value}</span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </MobileShell>
  );
}
