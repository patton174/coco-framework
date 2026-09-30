import {useEffect, useRef} from 'react';

/** Readable SSR first; motion decorates blocks once, never gates their visibility. */
export function useDocMotion(route: string) {
  const contentRef = useRef<HTMLDivElement>(null);
  const progressRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const root = contentRef.current;
    const progress = progressRef.current;
    if (!root) return;
    let frame = 0;
    const updateProgress = () => {
      frame = 0;
      if (!progress) return;
      const rect = root.getBoundingClientRect();
      const navbarHeight = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ifm-navbar-height')) || 60;
      // The navbar token may be rem; use the actual rendered navbar height.
      const offset = document.querySelector('.navbar')?.getBoundingClientRect().height ?? navbarHeight;
      const length = rect.height - window.innerHeight + offset;
      const amount = length <= 0 ? 1 : Math.min(1, Math.max(0, (offset - rect.top) / length));
      progress.style.transform = `scaleX(${amount})`;
    };
    const scheduleProgress = () => { if (!frame) frame = requestAnimationFrame(updateProgress); };
    window.addEventListener('scroll', scheduleProgress, {passive: true});
    window.addEventListener('resize', scheduleProgress);
    const resize = window.ResizeObserver ? new ResizeObserver(scheduleProgress) : undefined;
    resize?.observe(root);
    updateProgress();

    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const animations = new Set<Animation>();
    let skipUntil = location.hash ? performance.now() + 1000 : 0;
    const cancel = () => {
      animations.forEach(animation => animation.cancel());
      animations.clear();
    };
    const onJump = () => { skipUntil = performance.now() + 1000; cancel(); };
    const onPreference = () => { if (preference.matches) cancel(); };
    const animate = (target: HTMLElement, order: number, initial: boolean) => {
      if (preference.matches || performance.now() < skipUntil || target.contains(document.activeElement)) return;
      const heading = /^H[123]$/.test(target.tagName);
      const animation = target.animate([
        {transform: `translateY(${heading ? 14 : 9}px)`, opacity: initial ? 1 : heading ? .45 : .72},
        {transform: 'translateY(0)', opacity: 1},
      ], {
        duration: heading ? 540 : 440,
        delay: Math.min(order * 55, 165),
        easing: 'cubic-bezier(.22,1,.36,1)', fill: 'backwards',
      });
      animations.add(animation);
      animation.onfinish = () => animations.delete(animation);
      animation.oncancel = () => animations.delete(animation);
    };

    let observer: IntersectionObserver | undefined;
    if (typeof window.IntersectionObserver === 'function' && typeof Element.prototype.animate === 'function') {
      observer = new IntersectionObserver(entries => {
        let order = 0;
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          observer?.unobserve(entry.target);
          animate(entry.target as HTMLElement, order++, false);
        }
      }, {threshold: 0, rootMargin: '0px 0px -32px 0px'});
      const markdown = root.querySelector('.markdown');
      const targets = markdown?.querySelectorAll<HTMLElement>(
        ':scope > h1, :scope > header > h1, :scope > h2, :scope > h3, :scope > p, :scope > ul, :scope > ol, :scope > table, :scope > blockquote, :scope > .theme-code-block, :scope > .theme-admonition',
      );
      let initialOrder = 0;
      targets?.forEach(target => {
        const rect = target.getBoundingClientRect();
        if (rect.top < innerHeight && rect.bottom > 0) {
          // Only headings move on entry. Already-readable body copy stays still.
          if (/^H[123]$/.test(target.tagName)) animate(target, initialOrder++, true);
        } else observer?.observe(target);
      });
    }
    root.addEventListener('focusin', onJump);
    root.addEventListener('pointerdown', cancel);
    root.addEventListener('beforematch', onJump);
    window.addEventListener('hashchange', onJump);
    preference.addEventListener('change', onPreference);
    return () => {
      cancelAnimationFrame(frame);
      cancel();
      observer?.disconnect();
      resize?.disconnect();
      window.removeEventListener('scroll', scheduleProgress);
      window.removeEventListener('resize', scheduleProgress);
      window.removeEventListener('hashchange', onJump);
      root.removeEventListener('focusin', onJump);
      root.removeEventListener('pointerdown', cancel);
      root.removeEventListener('beforematch', onJump);
      preference.removeEventListener('change', onPreference);
    };
  }, [route]);
  return {contentRef, progressRef};
}
