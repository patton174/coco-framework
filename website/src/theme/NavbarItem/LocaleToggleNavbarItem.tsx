import React, {type ReactNode} from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useAlternatePageUtils} from '@docusaurus/theme-common/internal';
import {useHistorySelector} from '@docusaurus/theme-common';
import Link from '@docusaurus/Link';
import {Languages} from 'lucide-react';

/**
 * Icon-only locale switch: a single click flips between the two configured
 * locales. Uses lucide's translate glyph to match the sibling theme toggle;
 * the target language is carried by the tooltip and aria-label rather than
 * visible text, so the control stays the same 32px square as its neighbours.
 *
 * Registered as `custom-localeToggle` via src/theme/NavbarItem/ComponentTypes.
 *
 * The `pathname://` prefix plus `autoAddBaseUrl: false` is the same trick the
 * stock LocaleDropdownNavbarItem uses: locales are separate builds, so the
 * switch has to be a real navigation, not a client-side route change.
 */
export default function LocaleToggleNavbarItem({
  mobile,
  className,
}: {
  mobile?: boolean;
  className?: string;
}): ReactNode {
  const {
    i18n: {currentLocale, locales, localeConfigs},
  } = useDocusaurusContext();
  const alternatePageUtils = useAlternatePageUtils();
  const search = useHistorySelector((history) => history.location.search);
  const hash = useHistorySelector((history) => history.location.hash);

  // Only meaningful as a toggle when there are exactly two locales.
  const target = locales.find((locale) => locale !== currentLocale);
  if (!target) {
    return null;
  }

  const targetLabel = localeConfigs[target]?.label ?? target;
  const to = `pathname://${alternatePageUtils.createUrl({
    locale: target,
    fullyQualified: false,
  })}${search}${hash}`;

  // `docusaurus start` compiles ONE locale, so in dev the other locale's routes
  // don't exist and switching dead-ends on the 404 page. Flag it in the tooltip
  // instead of letting it look broken. Production serves both, so this is
  // compiled out of the production bundle entirely.
  const devOnlyHint =
    process.env.NODE_ENV === 'development'
      ? ` (dev: run "npm run start:${target}" — the dev server only builds one locale)`
      : '';

  return (
    <Link
      to={to}
      target="_self"
      autoAddBaseUrl={false}
      lang={localeConfigs[target]?.htmlLang ?? target}
      aria-label={`Switch to ${targetLabel}`}
      title={`${targetLabel}${devOnlyHint}`}
      className={
        mobile
          ? `menu__link navbar-locale-toggle--mobile ${className ?? ''}`
          : `navbar__item navbar__link navbar-locale-toggle ${className ?? ''}`
      }>
      <Languages
        size={19}
        strokeWidth={2.1}
        aria-hidden="true"
        className="navbar-locale-toggle__icon"
      />
      {mobile && <span>{targetLabel}</span>}
    </Link>
  );
}
