import {useEffect, useLayoutEffect, useRef} from 'react';
import {useGSAP} from '@gsap/react';
import {gsap} from 'gsap';
import {ScrollTrigger} from 'gsap/ScrollTrigger';

const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;
// Figures that settle in as they scroll into view. Running text and headings are never hidden.
const BLOCKS = ':scope .markdown > :is(.theme-code-block, table, .theme-admonition, blockquote, figure, img, details, .tabs-container)';
const STAGGER_MS = 70;

/**
 * Doc motion: reading progress, a short settle of the article on client-side navigation, and
 * scroll reveals for figures (code, tables, callouts).
 *
 * Reveals are armed after hydration: html[data-coco-doc-motion] is set only once every figure already
 * on screen is marked data-in, so nothing visible is ever hidden, and without JavaScript or with
 * reduced motion the attribute never exists.
 */
// Module scope, not a ref: DocItem remounts on every doc route, so a ref would always start empty and
// the doc-to-doc transition would never run. Null until the first doc has rendered (hydration).
const previousRoute: {current: string | null} = {current: null};

export function useDocMotion(route: string) {
  const contentRef = useRef<HTMLDivElement>(null);
  const progressRef = useRef<HTMLDivElement>(null);

  useGSAP(() => {
    const root = contentRef.current;
    const progress = progressRef.current;
    if (!root) return;
    // The route transition is feedback for client-side navigation only; the first mount must not touch the LCP content.
    const routeChanged = previousRoute.current !== null && previousRoute.current !== route;
    previousRoute.current = route;
    gsap.registerPlugin(ScrollTrigger);
    const media = gsap.matchMedia();
    media.add({
      reduce: '(prefers-reduced-motion: reduce)',
      regular: '(prefers-reduced-motion: no-preference)',
    }, context => {
      if (context.conditions?.reduce) {
        if (progress) gsap.set(progress, {scaleX: 1});
        return;
      }
      if (progress) {
        gsap.fromTo(progress, {scaleX: 0}, {
          scaleX: 1, ease: 'none',
          scrollTrigger: {
            trigger: root,
            start: () => `top top+=${parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ifm-navbar-height')) * parseFloat(getComputedStyle(document.documentElement).fontSize)}`,
            end: 'clamp(bottom bottom)',
            scrub: 0.15,
            invalidateOnRefresh: true,
          },
        });
      }
      if (routeChanged) gsap.fromTo(root, {opacity: 0, y: 10}, {opacity: 1, y: 0, duration: 0.45, ease: 'power3.out', clearProps: 'transform,opacity'});
      const timer = setTimeout(() => ScrollTrigger.refresh(), 150);
      return () => clearTimeout(timer);
    });
    return () => media.revert();
  }, {scope: contentRef, dependencies: [route], revertOnUpdate: true});

  // Figure reveals (runs before paint on every route).
  useIsomorphicLayoutEffect(() => {
    const root = contentRef.current;
    const html = document.documentElement;
    if (!root || !('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      html.removeAttribute('data-coco-doc-motion');
      return undefined;
    }
    const blocks = Array.from(root.querySelectorAll<HTMLElement>(BLOCKS));
    const show = (el: HTMLElement) => el.setAttribute('data-in', '');
    const observer = new IntersectionObserver(entries => {
      let order = 0;
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        observer.unobserve(entry.target);
        const el = entry.target as HTMLElement;
        el.style.setProperty('--reveal-delay', `${Math.min(order++ * STAGGER_MS, 280)}ms`);
        show(el);
      }
    // No negative margin and a threshold of 0: a block counts as soon as one pixel is visible, so a figure
    // that sits at the very end of the page (inside the last 8% of the viewport) is still revealed.
    }, {rootMargin: '0px', threshold: 0});
    for (const el of blocks) {
      if (el.getBoundingClientRect().top < window.innerHeight) show(el);
      else observer.observe(el);
    }
    // Focus or find-in-page must never land in a transparent block; printing shows everything.
    const revealAll = () => blocks.forEach(show);
    // Safety net: once the page is scrolled to its end, nothing below may stay transparent.
    const onScroll = () => {
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) revealAll();
    };
    const onFocus = (event: FocusEvent) => {
      const block = (event.target as HTMLElement | null)?.closest<HTMLElement>('[data-reveal-block]');
      if (block) show(block);
    };
    blocks.forEach(el => el.setAttribute('data-reveal-block', ''));
    html.setAttribute('data-coco-doc-motion', '');
    root.addEventListener('focusin', onFocus);
    window.addEventListener('beforeprint', revealAll);
    window.addEventListener('scroll', onScroll, {passive: true});
    return () => {
      observer.disconnect();
      root.removeEventListener('focusin', onFocus);
      window.removeEventListener('beforeprint', revealAll);
      window.removeEventListener('scroll', onScroll);
      html.removeAttribute('data-coco-doc-motion');
    };
  }, [route]);

  return {contentRef, progressRef};
}
