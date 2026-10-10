import {useEffect, useLayoutEffect, type RefObject} from 'react';

const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;
/** Gap between staggered blocks; line art timelines (StoryArt) are written against it. */
export const STAGGER_MS = 130;
const STAGGER_MAX_MS = 780;
const COUNT_MS = 1200;
const COUNT_DELAY_MS = 180;

/**
 * Homepage scroll reveals and stat count-up.
 *
 * The head script in docusaurus.config.ts sets html[data-coco-motion] before first paint; CSS hides
 * [data-reveal] blocks only under that attribute, so without JavaScript or with reduced motion every
 * block renders in its final state. This hook marks the gate "armed" (cancelling the 4s safety timer),
 * then reveals blocks as they enter the viewport by setting data-in. Any failure removes the gate.
 */
export function useHomeMotion(rootRef: RefObject<HTMLElement | null>): void {
  useIsomorphicLayoutEffect(() => {
    const root = rootRef.current;
    const html = document.documentElement;
    if (!root) return undefined;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (reduce.matches || !('IntersectionObserver' in window)) {
      html.removeAttribute('data-coco-motion');
      return undefined;
    }
    // Initial load: animate only if the head script armed the gate and its safety timer has not fired.
    // Client-side navigation (already hydrated): arm before paint so blocks start from their hidden state.
    if (!html.hasAttribute('data-coco-motion') && html.dataset.hasHydrated !== 'true') return undefined;

    const frames = new Set<number>();
    const counters = new Map<HTMLElement, string>();
    let observer: IntersectionObserver | undefined;

    const finishCount = (el: HTMLElement) => {
      const final = counters.get(el);
      if (final === undefined) return;
      el.textContent = final;
      el.style.minWidth = '';
      counters.delete(el);
    };
    const count = (el: HTMLElement, delay: number) => {
      const final = el.dataset.count ?? '';
      const target = Number(final);
      if (!Number.isFinite(target) || counters.has(el)) return;
      counters.set(el, final);
      // Hold the final width so changing digits never shift neighbouring text.
      el.style.minWidth = `${el.getBoundingClientRect().width}px`;
      el.textContent = '0';
      const start = performance.now() + delay;
      const tick = (now: number) => {
        const progress = Math.min(Math.max((now - start) / COUNT_MS, 0), 1);
        el.textContent = String(Math.round(target * (1 - (1 - progress) ** 3)));
        if (progress < 1) frames.add(requestAnimationFrame(tick));
        else finishCount(el);
      };
      frames.add(requestAnimationFrame(tick));
    };
    const reveal = (el: HTMLElement, order: number, instant = false) => {
      if (el.hasAttribute('data-in')) return;
      const explicit = el.dataset.order;
      // data-delay (ms) pins a block to a moment of its chapter's line-art timeline.
      const pinned = el.dataset.delay;
      const delay = instant ? 0 : pinned !== undefined ? Number(pinned) : Math.min((explicit === undefined ? order : Number(explicit)) * STAGGER_MS, STAGGER_MAX_MS);
      el.style.setProperty('--reveal-delay', `${delay}ms`);
      if (instant) el.style.transition = 'none';
      el.setAttribute('data-in', '');
      if (!instant) el.querySelectorAll<HTMLElement>('[data-count]').forEach(item => count(item, delay + COUNT_DELAY_MS));
    };
    const revealAll = () => {
      observer?.disconnect();
      root.querySelectorAll<HTMLElement>('[data-reveal]').forEach(el => reveal(el, 0, true));
      counters.forEach((_, el) => finishCount(el));
      frames.forEach(cancelAnimationFrame);
      frames.clear();
    };
    // Keyboard focus must never land on a block that is still transparent.
    const onFocus = (event: FocusEvent) => {
      const block = (event.target as HTMLElement | null)?.closest<HTMLElement>('[data-reveal]');
      if (block) reveal(block, 0, true);
    };
    const onPreference = () => { if (reduce.matches) revealAll(); };

    try {
      html.setAttribute('data-coco-motion', 'armed');
      observer = new IntersectionObserver(entries => {
        let order = 0;
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          observer?.unobserve(entry.target);
          reveal(entry.target as HTMLElement, order++);
        }
      }, {rootMargin: '0px 0px -6% 0px', threshold: 0.12});
      const blocks = Array.from(root.querySelectorAll<HTMLElement>('[data-reveal]'));
      // Everything already on the first screen reveals now, even if it sits inside the observer's bottom margin.
      const firstScreen = blocks.filter(el => el.getBoundingClientRect().top < window.innerHeight);
      blocks.filter(el => !firstScreen.includes(el)).forEach(el => observer?.observe(el));
      firstScreen.forEach((el, order) => reveal(el, order));
      root.addEventListener('focusin', onFocus);
      reduce.addEventListener('change', onPreference);
      window.addEventListener('beforeprint', revealAll);
    } catch {
      html.removeAttribute('data-coco-motion');
      revealAll();
    }

    return () => {
      observer?.disconnect();
      frames.forEach(cancelAnimationFrame);
      frames.clear();
      counters.forEach((_, el) => finishCount(el));
      root.removeEventListener('focusin', onFocus);
      reduce.removeEventListener('change', onPreference);
      window.removeEventListener('beforeprint', revealAll);
    };
  }, [rootRef]);
}
