import type {ReactNode} from 'react';
import {useEffect, useId, useRef} from 'react';
import clsx from 'clsx';
import Icon from '@site/src/components/Icon';
import styles from './styles.module.css';

interface Props {
  open: boolean;
  title: string;
  closeLabel: string;
  onClose: () => void;
  /** Taller panel for long menus. */
  tall?: boolean;
  children: ReactNode;
}

const focusable = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Bottom sheet: slides up over a scrim, closes on scrim tap, Escape, or a downward drag on its handle.
 * Closed sheets are inert, so they take no focus and no screen-reader attention. While open, the page behind
 * does not scroll (overscroll-behavior) and focus stays inside.
 */
export default function Sheet({open, title, closeLabel, onClose, tall = false, children}: Props): ReactNode {
  const id = useId();
  const panel = useRef<HTMLDivElement>(null);
  const drag = useRef<{y: number; dy: number} | null>(null);

  useEffect(() => {
    if (!open) return undefined;
    const previous = document.activeElement as HTMLElement | null;
    const node = panel.current;
    node?.querySelector<HTMLElement>(focusable)?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { onClose(); return; }
      if (event.key !== 'Tab' || !node) return;
      const items = Array.from(node.querySelectorAll<HTMLElement>(focusable));
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    document.documentElement.setAttribute('data-sheet-open', '');
    return () => {
      document.removeEventListener('keydown', onKey);
      document.documentElement.removeAttribute('data-sheet-open');
      previous?.focus?.();
    };
  }, [open, onClose]);

  const onPointerDown = (event: React.PointerEvent) => {
    drag.current = {y: event.clientY, dy: 0};
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: React.PointerEvent) => {
    if (!drag.current || !panel.current) return;
    drag.current.dy = Math.max(0, event.clientY - drag.current.y);
    panel.current.style.transform = `translate3d(0, ${drag.current.dy}px, 0)`;
    panel.current.style.transition = 'none';
  };
  const onPointerEnd = () => {
    const state = drag.current;
    drag.current = null;
    if (!panel.current || !state) return;
    panel.current.style.transition = '';
    panel.current.style.transform = '';
    if (state.dy > 90) onClose();
  };

  return (
    <div className={clsx(styles.sheetRoot, open && styles.sheetOpen)} inert={!open}>
      <div className={styles.scrim} onClick={onClose} aria-hidden="true" />
      <div ref={panel} className={clsx(styles.sheet, tall && styles.sheetTall)} role="dialog" aria-modal="true" aria-labelledby={id}>
        <div
          className={styles.grip}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerEnd}
          onPointerCancel={onPointerEnd}>
          <span aria-hidden="true" />
        </div>
        <div className={styles.sheetHead}>
          <h2 id={id}>{title}</h2>
          <button type="button" className={styles.iconButton} onClick={onClose} aria-label={closeLabel}>
            <Icon name="close" />
          </button>
        </div>
        <div className={styles.sheetBody}>{children}</div>
      </div>
    </div>
  );
}
