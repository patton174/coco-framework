import type {ClientModule} from '@docusaurus/types';

/*
 * Page-to-page feedback for client-side navigation (navbar tabs, footer links, homepage CTAs).
 * Without it the new page simply replaces the old one in a single frame.
 *
 * - Initial load: nothing animates (the first paint is the LCP).
 * - Doc to doc: handled by DocItem/Content (useDocMotion), so the sidebar and TOC stay still.
 * - Into the homepage: the hero has its own staged entrance.
 * - Hash-only changes: nothing.
 * Opacity only: a transform on .main-wrapper would re-anchor the fixed reading-progress bar.
 */
let previousWasDoc = false;

const isDocPage = () => document.querySelector('.theme-doc-markdown') !== null;

const routeTransition: ClientModule = {
  onRouteDidUpdate({location, previousLocation}) {
    const doc = isDocPage();
    const wasDoc = previousWasDoc;
    previousWasDoc = doc;
    if (!previousLocation || previousLocation.pathname === location.pathname) return;
    if ((doc && wasDoc) || document.querySelector('[data-coco-home]')) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    document.querySelector<HTMLElement>('.main-wrapper')?.animate(
      [{opacity: 0}, {opacity: 1}],
      {duration: 360, easing: 'cubic-bezier(0.22, 1, 0.36, 1)'},
    );
  },
};

export default routeTransition;
