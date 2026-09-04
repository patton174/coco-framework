import ComponentTypes from '@theme-original/NavbarItem/ComponentTypes';
// Relative, not '@theme/...': the alias only resolves components the theme
// declares, and this one is ours.
import LocaleToggleNavbarItem from './LocaleToggleNavbarItem';

/**
 * Docusaurus resolves navbar item `type: 'custom-x'` to the `custom-x` key
 * here, so this is the supported way to add a navbar item type without
 * rewriting the stock ones.
 */
export default {
  ...ComponentTypes,
  'custom-localeToggle': LocaleToggleNavbarItem,
};
