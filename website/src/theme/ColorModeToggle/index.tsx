import type {ReactNode} from 'react';
import clsx from 'clsx';
import useIsBrowser from '@docusaurus/useIsBrowser';
import {translate} from '@docusaurus/Translate';
import type {Props} from '@theme/ColorModeToggle';
import type {ColorMode} from '@docusaurus/theme-common';
import {Sun, Moon, MonitorSmartphone} from 'lucide-react';
import styles from './styles.module.css';

function getNextColorMode(
  colorMode: ColorMode | null,
  respectPrefersColorScheme: boolean,
): ColorMode | null {
  if (!respectPrefersColorScheme) {
    return colorMode === 'dark' ? 'light' : 'dark';
  }
  switch (colorMode) {
    case null:
      return 'light';
    case 'light':
      return 'dark';
    case 'dark':
      return null;
    default:
      throw new Error(`unexpected color mode ${colorMode}`);
  }
}

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

export default function ColorModeToggle({
  className,
  buttonClassName,
  respectPrefersColorScheme,
  value,
  onChange,
}: Props): ReactNode {
  const isBrowser = useIsBrowser();
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
        onClick={() => onChange(getNextColorMode(value, respectPrefersColorScheme))}
        disabled={!isBrowser}
        title={label(value)}
        aria-label={ariaLabel(value)}>
        <Sun aria-hidden size={18} strokeWidth={2.1}
          className={clsx(styles.toggleIcon, styles.lightToggleIcon)} />
        <Moon aria-hidden size={18} strokeWidth={2.1}
          className={clsx(styles.toggleIcon, styles.darkToggleIcon)} />
        <MonitorSmartphone aria-hidden size={18} strokeWidth={2.1}
          className={clsx(styles.toggleIcon, styles.systemToggleIcon)} />
      </button>
    </div>
  );
}
