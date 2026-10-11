import {useEffect, useLayoutEffect, type RefObject} from 'react';
import {gsap} from 'gsap';
import {ScrollTrigger} from 'gsap/ScrollTrigger';

const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;
const q = (root: Element, key: string) => Array.from(root.querySelectorAll<SVGElement | HTMLElement>(`[data-m="${key}"]`));
const DRAW = {strokeDasharray: 1, strokeDashoffset: 1};
const SETTLED = 'strokeDasharray,strokeDashoffset';

/**
 * Motion for the phone home page (GSAP + ScrollTrigger).
 *
 * Gate: the head script in docusaurus.config.ts sets html[data-coco-motion] before first paint and
 * home.module.css hides the [data-mr] blocks only under it. This hook flips the gate to "armed" and then
 * owns the reveal, so without JavaScript or with reduced motion the page renders in its final state.
 * If anything throws, the gate is removed and everything is shown.
 *
 * Linkage (everything is driven by one scroll position, nothing runs on its own clock except the bean float):
 * - the hero dial turns and fades with the page while the beans drift against it,
 * - the fixed background rings drift and turn over the whole page height,
 * - a rail beside the capability list fills as you read it, the capability crossing the middle of the screen
 *   lights its row, its node on the sticky dial and turns the dial's marker to it.
 */
export function useMobileMotion(rootRef: RefObject<HTMLElement | null>): void {
  useIsomorphicLayoutEffect(() => {
    const root = rootRef.current;
    const html = document.documentElement;
    if (!root) return undefined;
    // Arming here (before paint) also covers client-side navigation, where the head script's gate may be long gone.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      html.removeAttribute('data-coco-motion');
      return undefined;
    }
    gsap.registerPlugin(ScrollTrigger);
    html.setAttribute('data-coco-motion', 'armed');
    const ctx = gsap.context(() => {
      try {
        build(root);
      } catch {
        html.removeAttribute('data-coco-motion');
        gsap.set(root.querySelectorAll('[data-mr]'), {clearProps: 'all'});
      }
    }, root);
    const refresh = window.setTimeout(() => ScrollTrigger.refresh(), 300);
    const fonts = (document as Document & {fonts?: {ready: Promise<unknown>}}).fonts;
    fonts?.ready.then(() => ScrollTrigger.refresh()).catch(() => undefined);
    return () => {
      window.clearTimeout(refresh);
      ctx.revert();
    };
  }, [rootRef]);
}

function build(root: HTMLElement): void {
  const reveal = (key: string) => q(root, key);

  // ---- Entrance --------------------------------------------------------------------------------------------
  const intro = gsap.timeline({defaults: {ease: 'power3.out'}});
  const rings = reveal('hero-ring');
  const beans = reveal('hero-bean');
  const creases = reveal('hero-crease');
  const bgStrokes = reveal('bg-stroke');
  gsap.set([...rings, ...beans, ...creases, ...bgStrokes], DRAW);
  gsap.set(beans, {fillOpacity: 0});
  gsap.set(reveal('hero-art'), {autoAlpha: 1});
  intro
    .to(bgStrokes, {strokeDashoffset: 0, duration: 1.6, stagger: 0.12, ease: 'power2.inOut'}, 0)
    .to(rings, {strokeDashoffset: 0, duration: 1.3, stagger: 0.14, ease: 'power2.inOut'}, 0.05)
    .to(beans, {strokeDashoffset: 0, duration: 0.9, stagger: 0.12, ease: 'power2.inOut'}, 0.25)
    .to(creases, {strokeDashoffset: 0, duration: 0.6, stagger: 0.1}, 0.85)
    .to(beans, {fillOpacity: 1, duration: 0.7, stagger: 0.1, ease: 'power2.out'}, 0.9)
    .fromTo(reveal('hero-float'), {scale: 0.86, transformOrigin: '50% 50%'}, {scale: 1, duration: 1.1, ease: 'back.out(1.6)'}, 0.3)
    .fromTo(reveal('hero-line'), {autoAlpha: 0, y: 26}, {autoAlpha: 1, y: 0, duration: 0.75, stagger: 0.09, clearProps: 'transform'}, 0.15)
    .add(() => {
      gsap.set([...rings, ...beans, ...creases, ...bgStrokes], {clearProps: SETTLED});
    });

  // Idle: the beans breathe, the only motion that is not tied to scroll.
  gsap.to(reveal('hero-float'), {y: -0.6, duration: 2.6, ease: 'sine.inOut', yoyo: true, repeat: -1});

  // ---- Page-long scroll linkage ----------------------------------------------------------------------------
  const page = {trigger: root, start: 'top top', end: 'bottom bottom', scrub: 0.6, invalidateOnRefresh: true};
  const heroTrigger = {trigger: reveal('hero-art')[0] ?? root, start: 'top 20%', end: 'bottom -80%', scrub: 0.5};
  gsap.to(reveal('hero-spin'), {rotation: 140, transformOrigin: '50% 50%', ease: 'none', scrollTrigger: heroTrigger});
  gsap.to(reveal('hero-art'), {yPercent: 18, scale: 0.86, opacity: 0.25, ease: 'none', scrollTrigger: heroTrigger});
  gsap.to(reveal('hero-float'), {rotation: -14, transformOrigin: '50% 50%', ease: 'none', scrollTrigger: heroTrigger});
  gsap.to(reveal('bg-drift'), {yPercent: -22, ease: 'none', scrollTrigger: page});
  gsap.to(reveal('bg-spin'), {rotation: 200, transformOrigin: '50% 50%', ease: 'none', scrollTrigger: page});

  // ---- Stats: count up and rise as they enter ----------------------------------------------------------------
  const facts = reveal('fact');
  if (facts.length) {
    gsap.set(facts, {autoAlpha: 0, y: 18});
    ScrollTrigger.batch(facts, {
      start: 'top 92%',
      once: true,
      onEnter: batch => {
        gsap.to(batch, {autoAlpha: 1, y: 0, duration: 0.7, stagger: 0.1, ease: 'power3.out', clearProps: 'transform'});
        batch.forEach(item => {
          const el = (item as HTMLElement).querySelector<HTMLElement>('[data-count]');
          const target = Number(el?.dataset.count);
          if (!el || !Number.isFinite(target)) return;
          el.style.minWidth = `${el.getBoundingClientRect().width}px`;
          const counter = {value: 0};
          gsap.to(counter, {
            value: target, duration: 1.1, ease: 'power2.out', delay: 0.15,
            onUpdate: () => { el.textContent = String(Math.round(counter.value)); },
            onComplete: () => { el.textContent = String(target); el.style.minWidth = ''; },
          });
        });
      },
    });
    gsap.set(reveal('facts-rule'), {scaleX: 0, transformOrigin: '0 50%'});
    gsap.to(reveal('facts-rule'), {scaleX: 1, duration: 1, ease: 'power3.out', scrollTrigger: {trigger: reveal('facts-rule')[0], start: 'top 95%', once: true}});
  }

  // ---- Entries: slide in from the edge, alternating ------------------------------------------------------------
  const entries = reveal('entry');
  if (entries.length) {
    gsap.set(entries, {autoAlpha: 0, x: (i: number) => (i % 2 ? 28 : -28)});
    ScrollTrigger.batch(entries, {
      start: 'top 94%',
      once: true,
      onEnter: batch => gsap.to(batch, {autoAlpha: 1, x: 0, duration: 0.65, stagger: 0.09, ease: 'power3.out', clearProps: 'transform'}),
    });
  }

  // ---- Capabilities: rail, rows and the sticky dial move together ------------------------------------------------
  const caps = reveal('cap') as HTMLElement[];
  const list = reveal('caps')[0];
  const nodes = reveal('dial-node');
  const spokes = reveal('dial-spoke');
  const ring = reveal('dial-ring')[0];
  const heading = reveal('caps-heading');
  if (heading.length) {
    gsap.set(heading, {autoAlpha: 0, y: 16});
    gsap.to(heading, {autoAlpha: 1, y: 0, duration: 0.7, ease: 'power3.out', clearProps: 'transform', scrollTrigger: {trigger: heading[0], start: 'top 92%', once: true}});
  }
  // One trigger drives the rail and the lit row. Progress runs from "list top at 62% of the screen" to "list
  // bottom at the screen's bottom edge", which a short page can always reach, so the last row gets its turn too.
  const span = {trigger: list, start: 'top 62%', end: 'bottom bottom', invalidateOnRefresh: true};
  const step = caps.length ? 360 / caps.length : 0;
  let active = -1;
  const light = (index: number) => {
    if (index === active) return;
    active = index;
    caps.forEach((cap, i) => (i === index ? cap.setAttribute('data-on', '') : cap.removeAttribute('data-on')));
    nodes.forEach((node, i) => (i === index ? node.setAttribute('data-on', '') : node.removeAttribute('data-on')));
    spokes.forEach((spoke, i) => (i === index ? spoke.setAttribute('data-on', '') : spoke.removeAttribute('data-on')));
    if (ring && index >= 0) gsap.to(ring, {rotation: index * step, transformOrigin: '50% 50%', duration: 0.7, ease: 'back.out(1.5)', overwrite: 'auto'});
  };
  if (list && caps.length) {
    gsap.set(reveal('rail'), {scaleY: 0, transformOrigin: '50% 0'});
    ScrollTrigger.create({
      ...span,
      onUpdate: self => {
        gsap.set(reveal('rail'), {scaleY: self.progress});
        light(self.progress <= 0 ? -1 : Math.min(caps.length - 1, Math.floor(self.progress * caps.length)));
      },
    });
  }
  caps.forEach((cap, i) => {
    gsap.set(cap, {autoAlpha: 0, y: 22});
    gsap.to(cap, {autoAlpha: 1, y: 0, duration: 0.6, ease: 'power3.out', clearProps: 'transform', scrollTrigger: {trigger: cap, start: 'top 95%', once: true}});
  });
}
