import type {KeyboardEvent, ReactNode} from 'react';
import {useId, useRef, useState} from 'react';
import clsx from 'clsx';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

// Samples mirror the getting-started guide (docs/getting-started.md), shortened to fit the first screen.
// Every sample has exactly four lines, so the panel never shows an empty strip under a shorter one.
const SAMPLES: readonly {language: string; code: string}[] = [
  {language: 'xml', code: `<dependency>
  <groupId>io.github.patton174</groupId>
  <artifactId>coco-spring-boot-starter</artifactId>
</dependency>`},
  {language: 'yaml', code: `# features are opt-in
coco:
  idempotency:
    enabled: true`},
  {language: 'java', code: `@CocoIdempotent(namespace = "orders")
OrderResponse create(@RequestBody CreateOrder cmd) {
  return orders.create(cmd);
}`},
];

interface Props {
  /** Accessible name of the tab list. */
  label: string;
  /** [file name, caption] per sample, in SAMPLES order. */
  samples: readonly (readonly [string, string])[];
  className?: string;
}

/**
 * A small editor window: file tabs (with the language on the right), the code, and a one-line caption
 * as its status bar. All panes share one grid cell, so the panel keeps the height of the tallest sample
 * and switching never moves the hero. Inactive panes are inert and hidden; the underline hands over from
 * the previous tab (data-from) and the new pane's lines are re-inked.
 */
export default function StarterTabs({label, samples, className}: Props): ReactNode {
  const id = useId();
  const [active, setActive] = useState(0);
  const [from, setFrom] = useState<'left' | 'right' | null>(null);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);

  const select = (next: number, focus = false) => {
    if (focus) tabs.current[next]?.focus();
    if (next === active) return;
    setFrom(next > active ? 'left' : 'right');
    setActive(next);
  };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const last = SAMPLES.length - 1;
    const keys: Record<string, number> = {
      ArrowRight: active === last ? 0 : active + 1,
      ArrowLeft: active === 0 ? last : active - 1,
      Home: 0,
      End: last,
    };
    if (!(event.key in keys)) return;
    event.preventDefault();
    select(keys[event.key], true);
  };

  return (
    <div className={clsx(styles.root, className)}>
      <div className={styles.panel} data-from={from ?? undefined}>
        <div className={styles.bar}>
          <div className={styles.tabs} role="tablist" aria-label={label} onKeyDown={onKeyDown}>
            {SAMPLES.map((_, position) => (
              <button
                key={samples[position][0]}
                ref={element => { tabs.current[position] = element; }}
                id={`${id}-tab-${position}`}
                className={styles.tab}
                type="button"
                role="tab"
                aria-selected={position === active}
                aria-controls={`${id}-pane-${position}`}
                tabIndex={position === active ? 0 : -1}
                onClick={() => select(position)}>
                {samples[position][0]}
              </button>
            ))}
          </div>
          <span className={styles.lang} aria-hidden="true">{SAMPLES[active].language}</span>
        </div>
        <div className={styles.panes}>
          {SAMPLES.map(({language, code}, position) => (
            <div
              key={language}
              id={`${id}-pane-${position}`}
              className={styles.pane}
              role="tabpanel"
              aria-labelledby={`${id}-tab-${position}`}
              inert={position !== active}
              data-switched={from && position === active ? '' : undefined}>
              <CodeBlock language={language}>{code}</CodeBlock>
            </div>
          ))}
        </div>
        {/* Keyed so the caption re-enters with the pane it describes. */}
        <p key={active} className={styles.note} data-switched={from ? '' : undefined}>{samples[active][1]}</p>
      </div>
    </div>
  );
}
