import type {ReactNode} from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Icon from '@site/src/components/Icon';
import {LineArrow} from '@site/src/components/Glyphs';
import {partnerLabels, partners, PARTNERS_AS_OF, type PartnerChapter} from '@site/src/data/partners';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

/** 17479 -> "1.7万" / "17.5k": a rounded figure reads as a snapshot, an exact one as a live claim. */
function formatStars(count: number, zh: boolean): string {
  if (zh) return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : `${(count / 1000).toFixed(1)}千`;
  return count >= 1000 ? `${(count / 1000).toFixed(1)}k` : String(count);
}

/**
 * A recommended open-source project, set in the page margin beside a chapter (wide screens only).
 *
 * Place it as a child of the chapter's container: the slot then spans exactly the height of the content next to
 * it, and the card stretches to fill that height (illustration above, text below). The whole card is one link,
 * labelled as a related project, with no tracking. The image is the project's own published artwork.
 */
export default function Partner({chapter}: {chapter: PartnerChapter}): ReactNode {
  const {i18n} = useDocusaurusContext();
  const zh = i18n.currentLocale !== 'en';
  const entry = partners.find(item => item.chapter === chapter);
  const imageSrc = useBaseUrl(`img/partners/${entry?.image ?? ''}`);
  if (!entry) return null;
  const label = partnerLabels[zh ? 'zh' : 'en'];
  const copy = entry[zh ? 'zh' : 'en'];
  return (
    <aside className={styles.slot} data-side={entry.side} aria-label={label.region}>
      <a className={styles.card} data-reveal="" data-partner={entry.name} href={entry.url} target="_blank" rel="noopener" data-glyph-host="">
        <span className={styles.visual}>
          <img className={styles.art} data-fit={entry.fit} src={imageSrc} alt="" loading="lazy" decoding="async" />
          <span className={styles.badge}>{label.badge}</span>
        </span>
        <span className={styles.body}>
          <span className={styles.kicker}>{label.kicker}</span>
          <strong className={styles.name}>{entry.name}</strong>
          <span className={styles.tagline}>{copy.tagline}</span>
          <span className={styles.why}>{copy.why}</span>
          <span className={styles.meta} title={`${label.stars} · ${label.asOf} ${PARTNERS_AS_OF}`}>
            <Icon name="star" size={15} className={styles.star} />
            <span className={styles.count}>{formatStars(entry.stars, zh)}</span>
            <span className={styles.license}>{entry.license}</span>
          </span>
          <span className={styles.go}><span>{label.view}</span><LineArrow size={11} /></span>
        </span>
      </a>
    </aside>
  );
}
