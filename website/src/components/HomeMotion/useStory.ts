import {useEffect, useLayoutEffect, type RefObject} from 'react';
import {orbitPoint, parseOrbit, type OrbitSpec} from '@site/src/components/PageArt/orbit';

const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;
const clamp = (value: number): number => Math.min(1, Math.max(0, value));
const easeOut = (progress: number): number => 1 - (1 - progress) ** 3;
/** Time constant of the catch-up that smooths the scroll-driven drawing (ms). */
const SETTLE_MS = 110;

interface Stroke { el: SVGElement; t: number; dur: number; linear: boolean }
/** A block that appears when the scrubbed line has reached `at` ms of its chapter's timeline. */
interface Cue { el: HTMLElement; at: number }
/** Parts of a chapter's background motif that follow the scroll position (see PageArt). */
interface Motion {
  spins: {el: SVGGElement; degrees: number; cx: number; cy: number}[];
  shifts: {el: SVGGElement; rest: number; reach: number}[];
  orbits: {el: SVGGElement; spec: OrbitSpec; tether?: SVGLineElement | null}[];
}
interface Chapter {
  motion: Motion;
  strokes: Stroke[];
  cues: Cue[];
  /** Length of the chapter's timeline: the moment its last stroke ends. */
  total: number;
  /** Chapters below the first screen are drawn by the scroll position; the hero frame plays on a clock. */
  scrub: boolean;
  /** The figures the drawing belongs to; their box decides how far along the scroll is. */
  stage: HTMLElement[];
  target: number;
  shown: number;
  seen: boolean;
  played: boolean;
}

// Layout position inside the story box. offsetTop/offsetLeft ignore transforms, so a half-revealed block measures true.
function place(el: HTMLElement, root: HTMLElement): {x: number; y: number} {
  let x = 0;
  let y = 0;
  let node: HTMLElement | null = el;
  while (node && node !== root) {
    x += node.offsetLeft;
    y += node.offsetTop;
    node = node.offsetParent as HTMLElement | null;
  }
  return {x, y};
}

/**
 * Storytelling for the homepage.
 *
 * - A rail in the left margin grows with the scroll position and lights one node per chapter, starting at
 *   the first screen. It is scrubbed directly by scroll (no easing).
 * - Each chapter's line art is one timeline (every stroke carries its own start time, data-t). Below the first
 *   screen the timeline is scrubbed by the scroll position: the drawing advances as the figure travels up the
 *   screen and runs back when scrolling up, with a short catch-up so it never jumps. Blocks marked data-at
 *   appear when the line reaches that moment. The hero's frame plays on a clock once it is revealed.
 *
 * Both only run while the motion gate is armed; otherwise the rail and the art render complete.
 */
export function useStory(rootRef: RefObject<HTMLElement | null>): void {
  useIsomorphicLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    const html = document.documentElement;
    const nodes = Array.from(root.querySelectorAll<HTMLElement>('[data-node]'));
    const railInk = root.querySelector<HTMLElement>('[data-rail-ink]');
    const chapters = new Map<Element, Chapter>();
    root.querySelectorAll<HTMLElement>('section, main > header').forEach(section => {
      const strokes = Array.from(section.querySelectorAll<SVGElement>('[data-draw] [data-t]')).map(el => ({
        el,
        t: Number(el.dataset.t) || 0,
        dur: Number(el.dataset.dur) || 600,
        linear: el.dataset.ease === 'linear',
      }));
      if (!strokes.length) return;
      const cues = Array.from(section.querySelectorAll<HTMLElement>('[data-at]')).map(el => ({el, at: Number(el.dataset.at) || 0}));
      const drawings = Array.from(section.querySelectorAll<SVGElement>('[data-draw]'));
      const stage = Array.from(new Set(drawings.map(svg => svg.parentElement).filter((node): node is HTMLElement => !!node)));
      const motion: Motion = {
        spins: Array.from(section.querySelectorAll<SVGGElement>('[data-spin]')).map(el => ({el, degrees: Number(el.dataset.spin) || 0, cx: Number(el.dataset.cx) || 0, cy: Number(el.dataset.cy) || 0})),
        shifts: Array.from(section.querySelectorAll<SVGGElement>('[data-shift]')).map(el => ({el, rest: Number(el.dataset.rest) || 0, reach: Number(el.dataset.shift) || 0})),
        orbits: Array.from(section.querySelectorAll<SVGGElement>('[data-orbit]')).flatMap(el => {
          const spec = parseOrbit(el.dataset.orbit);
          return spec ? [{el, spec, tether: el.dataset.id ? section.querySelector<SVGLineElement>(`[data-follow="${el.dataset.id}"]`) : null}] : [];
        }),
      };
      chapters.set(section, {
        motion, strokes, cues, stage,
        total: Math.max(1, ...strokes.map(stroke => stroke.t + stroke.dur)),
        scrub: section.tagName === 'SECTION',
        target: 0, shown: 0, seen: false, played: false,
      });
    });
    const frames = new Set<number>();
    let railTop = 0;
    let railHeight = 1;
    let nodeY: number[] = [];
    let scrollFrame = 0;
    let settleFrame = 0;
    let settleAt = 0;

    const finish = (chapter: Chapter) => {
      chapter.strokes.forEach(({el}) => { el.style.strokeDashoffset = '0'; });
      chapter.cues.forEach(({el}) => el.setAttribute('data-in', ''));
    };
    // Where the scrub stands, 0 to 1, from the position of the figure on the screen.
    const progressOf = (chapter: Chapter): number => {
      const view = window.innerHeight;
      let top = Infinity;
      let bottom = -Infinity;
      for (const el of chapter.stage) {
        const box = el.getBoundingClientRect();
        if (!box.height) continue;
        top = Math.min(top, box.top);
        bottom = Math.max(bottom, box.bottom);
      }
      if (top === Infinity) return 0;
      // Measured at the figure's centre: it starts to draw as the figure rises through the bottom of the screen and
      // is finished when the chapter sits aligned on its own screen (centre a little below the middle).
      return clamp((view * 0.92 - (top + bottom) / 2) / (view * 0.38));
    };
    // Strokes follow the scrub both ways; a cued block, once shown, stays (its reveal is a transition, not a scrub).
    const paint = (chapter: Chapter) => {
      const clock = chapter.shown * chapter.total;
      for (const stroke of chapter.strokes) stroke.el.style.strokeDashoffset = (1 - clamp((clock - stroke.t) / stroke.dur)).toFixed(3);
      if (chapter.shown <= 0) return;
      for (const {el, at} of chapter.cues) {
        if (clock < at || el.hasAttribute('data-in')) continue;
        el.style.setProperty('--reveal-delay', '0ms');
        el.setAttribute('data-in', '');
      }
    };
    // Frame-rate independent catch-up toward the scroll position.
    const settle = (now: number) => {
      settleFrame = 0;
      const gain = 1 - Math.exp(-Math.max(0, now - settleAt) / SETTLE_MS);
      settleAt = now;
      let more = false;
      chapters.forEach(chapter => {
        if (!chapter.scrub) return;
        const gap = chapter.target - chapter.shown;
        if (Math.abs(gap) > 0.0005) { chapter.shown += gap * gain; more = true; } else chapter.shown = chapter.target;
        paint(chapter);
      });
      if (more) settleFrame = requestAnimationFrame(settle);
    };
    const wake = () => {
      if (settleFrame) return;
      settleAt = performance.now();
      settleFrame = requestAnimationFrame(settle);
    };

    // The hero's frame is not scrolled into view: it plays once its block is revealed (useHomeMotion sets data-in).
    const play = (chapter: Chapter) => {
      if (chapter.played || chapter.scrub) return;
      chapter.played = true;
      if (html.getAttribute('data-coco-motion') !== 'armed') { finish(chapter); return; }
      const begin = performance.now();
      const tick = (now: number) => {
        let running = false;
        for (const stroke of chapter.strokes) {
          const raw = clamp((now - begin - stroke.t) / stroke.dur);
          stroke.el.style.strokeDashoffset = (1 - (stroke.linear ? raw : easeOut(raw))).toFixed(3);
          if (raw < 1) running = true;
        }
        if (running) frames.add(requestAnimationFrame(tick));
      };
      frames.add(requestAnimationFrame(tick));
    };
    const completeAll = () => {
      frames.forEach(cancelAnimationFrame);
      frames.clear();
      if (settleFrame) cancelAnimationFrame(settleFrame);
      settleFrame = 0;
      chapters.forEach(chapter => {
        chapter.played = true;
        chapter.seen = true;
        chapter.shown = 1;
        chapter.target = 1;
        finish(chapter);
      });
    };

    const watcher = new MutationObserver(records => {
      for (const record of records) {
        const target = record.target as HTMLElement;
        if (!target.hasAttribute('data-in')) continue;
        const section = target.closest('section, header');
        const chapter = section ? chapters.get(section) : undefined;
        if (chapter) play(chapter);
      }
    });
    watcher.observe(root, {attributes: true, attributeFilter: ['data-in'], subtree: true});
    // A hero that was already revealed before this effect ran (client navigation).
    chapters.forEach((chapter, section) => { if (!chapter.scrub && section.querySelector('[data-in]')) play(chapter); });

    const update = () => {
      scrollFrame = 0;
      const armed = html.getAttribute('data-coco-motion') === 'armed';
      if (armed) {
        chapters.forEach(chapter => {
          if (!chapter.scrub) return;
          chapter.target = progressOf(chapter);
          // Arriving below a chapter (reload mid-page, anchor jump): show its drawing finished instead of replaying it.
          if (!chapter.seen) {
            chapter.seen = true;
            if (chapter.target >= 1) chapter.shown = 1;
          }
        });
        wake();
      }
      const line = window.innerHeight * 0.6 - root.getBoundingClientRect().top;
      // Scroll linkage of the page art, written straight onto the SVG parts that move (not onto an ancestor as an
      // inherited variable: that would invalidate the style of the whole page on every scroll frame).
      const view = window.innerHeight;
      chapters.forEach((chapter, section) => {
        const {spins, shifts, orbits} = chapter.motion;
        if (!spins.length && !shifts.length && !orbits.length) return;
        const box = section.getBoundingClientRect();
        // -1 while the chapter is a screen below, 0 when it is centred, +1 a screen above.
        const position = Math.max(-1.5, Math.min(1.5, ((box.top + box.bottom) / 2 - view / 2) / view));
        for (const {el, degrees, cx, cy} of spins) el.setAttribute('transform', `rotate(${(position * degrees).toFixed(2)} ${cx} ${cy})`);
        for (const {el, rest, reach} of shifts) el.setAttribute('transform', `translate(0 ${(rest + position * reach).toFixed(2)})`);
        for (const {el, spec, tether} of orbits) {
          const at = orbitPoint(spec, -position);
          el.setAttribute('transform', `translate(${at.x.toFixed(1)} ${at.y.toFixed(1)})`);
          if (tether) { tether.setAttribute('x2', at.x.toFixed(1)); tether.setAttribute('y2', at.y.toFixed(1)); }
        }
      });
      const ink = root.querySelector<SVGElement>('[data-thread-ink]');
      if (ink) ink.style.strokeDashoffset = (armed ? 1 - clamp(line / Math.max(1, root.offsetHeight)) : 0).toFixed(4);
      if (railInk) railInk.style.transform = `scaleY(${(armed ? clamp((line - railTop) / railHeight) : 1).toFixed(4)})`;
      nodes.forEach((node, position) => node.toggleAttribute('data-lit', !armed || nodeY[position] <= line));
    };
    const schedule = () => { if (!scrollFrame) scrollFrame = requestAnimationFrame(update); };

    const measure = () => {
      if (nodes.length < 2) return;
      const spots = nodes.map(node => ({...place(node, root), height: node.offsetHeight}));
      nodeY = spots.map(spot => spot.y + spot.height / 2);
      railTop = nodeY[0];
      railHeight = Math.max(1, nodeY[nodeY.length - 1] - nodeY[0]);
      root.style.setProperty('--rail-x', `${spots[0].x - 24}px`);
      root.style.setProperty('--rail-top', `${railTop}px`);
      root.style.setProperty('--rail-h', `${railHeight}px`);
      root.setAttribute('data-rail', '');
      update();
    };

    measure();
    update();
    window.addEventListener('scroll', schedule, {passive: true});
    window.addEventListener('resize', measure);
    window.addEventListener('beforeprint', completeAll);
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure);
    observer?.observe(root);
    void document.fonts?.ready.then(measure);

    return () => {
      if (scrollFrame) cancelAnimationFrame(scrollFrame);
      if (settleFrame) cancelAnimationFrame(settleFrame);
      frames.forEach(cancelAnimationFrame);
      frames.clear();
      watcher.disconnect();
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', measure);
      window.removeEventListener('beforeprint', completeAll);
      observer?.disconnect();
      if (railInk) railInk.style.transform = '';
      chapters.forEach(chapter => {
        chapter.strokes.forEach(({el}) => { el.style.strokeDashoffset = ''; });
        chapter.cues.forEach(({el}) => { el.removeAttribute('data-in'); el.style.removeProperty('--reveal-delay'); });
      });
    };
  }, [rootRef]);
}
