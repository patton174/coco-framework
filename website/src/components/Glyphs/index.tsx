import type {ReactNode} from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

/*
 * Hairline arrows that replace the stock icon-set arrows.
 * They are decorative (aria-hidden) and react to the hover/focus of the nearest link, button or
 * [data-glyph-host] ancestor: the line arrow stretches toward its destination, the corner arrow
 * leaves through the top-right edge while a copy slides in from the bottom-left.
 */
interface GlyphProps {
  /** Rendered height in px (the line arrow is 1.75x as wide). */
  size?: number;
  className?: string;
}

export function LineArrow({size = 14, direction = 'right', className}: GlyphProps & {direction?: 'right' | 'down'}): ReactNode {
  if (direction === 'down') {
    return (
      <svg className={clsx(styles.glyph, styles.down, className)} width={size} height={size * 1.75} viewBox="0 0 16 28" aria-hidden="true" focusable="false">
        <path className={styles.tail} d="M8 3V19" />
        <path className={styles.head} d="M3.5 14.5L8 19L12.5 14.5" />
      </svg>
    );
  }
  return (
    <svg className={clsx(styles.glyph, styles.line, className)} width={size * 1.75} height={size} viewBox="0 0 28 16" aria-hidden="true" focusable="false">
      <path className={styles.tail} d="M3 8H19" />
      <path className={styles.head} d="M14.5 3.5L19 8L14.5 12.5" />
    </svg>
  );
}

export function CornerArrow({size = 14, className}: GlyphProps): ReactNode {
  const arrow = 'M4.5 11.5L11.5 4.5M6.5 4.5H11.5V9.5';
  return (
    <svg className={clsx(styles.glyph, styles.corner, className)} width={size} height={size} viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path className={styles.exit} d={arrow} />
      <path className={styles.enter} d={arrow} />
    </svg>
  );
}
