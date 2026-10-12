import type {MouseEvent, ReactNode} from 'react';
import clsx from 'clsx';
import useIsBrowser from '@docusaurus/useIsBrowser';
import {translate} from '@docusaurus/Translate';
import type {Props} from '@theme/ColorModeToggle';
import type {ColorMode} from '@docusaurus/theme-common';
import Icon from '@site/src/components/Icon';
import styles from './styles.module.css';

function label(colorMode: ColorMode | null): string {
  switch (colorMode) {
    case null:
      return translate({
        message: 'system mode',
        id: 'theme.colorToggle.ariaLabel.mode.system',
        description: 'The name for the system color mode',
      });
    case 'light':
      return translate({
        message: 'light mode',
        id: 'theme.colorToggle.ariaLabel.mode.light',
        description: 'The name for the light color mode',
      });
    case 'dark':
      return translate({
        message: 'dark mode',
        id: 'theme.colorToggle.ariaLabel.mode.dark',
        description: 'The name for the dark color mode',
      });
    default:
      throw new Error(`unexpected color mode ${colorMode}`);
  }
}

function ariaLabel(colorMode: ColorMode | null): string {
  return translate(
    {
      message: 'Switch between dark and light mode (currently {mode})',
      id: 'theme.colorToggle.ariaLabel',
      description: 'The ARIA label for the color mode toggle',
    },
    {mode: label(colorMode)},
  );
}

type ViewTransitionDocument = Document & {
  startViewTransition?: (update: () => void) => {ready: Promise<void>; finished: Promise<void>};
};

/**
 * Switches the theme inside a View Transition: the new palette is revealed as a circle growing from
 * the toggle, instead of every surface snapping at once. Falls back to an instant switch when the
 * API is missing or the user prefers reduced motion.
 */
function switchTheme(button: HTMLElement, apply: () => void): void {
  const doc = document as ViewTransitionDocument;
  if (!doc.startViewTransition || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    apply();
    return;
  }
  const rect = button.getBoundingClientRect();
  const x = rect.left + rect.width / 2;
  const y = rect.top + rect.height / 2;
  const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y));
  const html = document.documentElement;
  html.setAttribute('data-theme-transition', '');
  // Docusaurus writes data-theme / data-theme-choice synchronously inside setColorMode, and the toggle
  // icons are driven by those attributes, so the DOM shows the new theme when the callback returns.
  const transition = doc.startViewTransition(apply);
  transition.ready.then(() => {
    html.animate(
      {clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`]},
      {duration: 620, easing: 'cubic-bezier(0.22, 1, 0.36, 1)', pseudoElement: '::view-transition-new(root)'},
    );
  }).catch(() => undefined);
  // A second click skips the running transition and rejects `finished` with AbortError; swallow it before
  // `finally`, which would otherwise re-throw it as an unhandled rejection.
  transition.finished.catch(() => undefined).finally(() => html.removeAttribute('data-theme-transition'));
}

export default function ColorModeToggle({
  className,
  buttonClassName,
  value,
  onChange,
}: Props): ReactNode {
  const isBrowser = useIsBrowser();
  const onClick = (event: MouseEvent<HTMLButtonElement>) => {
    // Two states, sun and moon: flip the theme that is actually showing (it may come from the system setting),
    // so one click always changes what the reader sees.
    const shown = document.documentElement.getAttribute('data-theme');
    const next: ColorMode = shown === 'dark' ? 'light' : 'dark';
    switchTheme(event.currentTarget, () => onChange(next));
  };
  return (
    <div className={clsx(styles.toggle, className)}>
      <button
        className={clsx(
          'clean-btn',
          styles.toggleButton,
          !isBrowser && styles.toggleButtonDisabled,
          buttonClassName,
        )}
        type="button"
        onClick={onClick}
        disabled={!isBrowser}
        title={label(value)}
        aria-label={ariaLabel(value)}>
        {/* The crescent's ink box is smaller than the sun's rays at equal size, so it is drawn larger. */}
        <Icon name="sun" size={23} className={clsx(styles.toggleIcon, styles.sunIcon)} />
        <Icon name="moon" size={25} className={clsx(styles.toggleIcon, styles.moonIcon)} />
      </button>
    </div>
  );
}
