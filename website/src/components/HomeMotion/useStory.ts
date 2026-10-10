import {useEffect, useLayoutEffect, type RefObject} from 'react';

const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;
const clamp = (value: number): number => Math.min(1, Math.max(0, value));
const easeOut = (progress: number): number => 1 - (1 - progress) ** 3;

interface Stroke { el: SVGElement; t: number; dur: number; linear: boolean }
interface Chapter { strokes: Stroke[]; played: boolean }

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
 * - Each chapter's line art is drawn on a timeline. It starts the moment the chapter's first block is revealed
 *   (useHomeMotion sets data-in) and every stroke carries its own start time (data-t), so the copy that
 *   shares a data-order delay appears exactly when the line reaches it.
 *
 * Both only run while the motion gate is armed; otherwise the rail and the art render complete.
 */
export function useStory(rootRef: RefObject<HTMLElement | null>): void {
  useIsomorphicLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    const html = document.documentElement;
    const nodes = Array.from(root.querySelectorAll<HTMLElement>('[data-node]'));
    const chapters = new Map<Element, Chapter>();
    root.querySelectorAll<HTMLElement>('section, main > header').forEach(section => {
      const strokes = Array.from(section.querySelectorAll<SVGElement>('[data-draw] [data-t]')).map(el => ({
        el,
        t: Number(el.dataset.t) || 0,
        dur: Number(el.dataset.dur) || 600,
        linear: el.dataset.ease === 'linear',
      }));
      if (strokes.length) chapters.set(section, {strokes, played: false});
    });
    const frames = new Set<number>();
    let railTop = 0;
    let railHeight = 1;
    let nodeY: number[] = [];
    let scrollFrame = 0;

    const finish = (chapter: Chapter) => chapter.strokes.forEach(({el}) => { el.style.strokeDashoffset = '0'; });
    const play = (chapter: Chapter) => {
      if (chapter.played) return;
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
      chapters.forEach(chapter => { chapter.played = true; finish(chapter); });
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
    // A chapter that was already revealed before this effect ran (client navigation, tall screens).
    chapters.forEach((chapter, section) => { if (section.querySelector('[data-in]')) play(chapter); });

    const update = () => {
      scrollFrame = 0;
      const armed = html.getAttribute('data-coco-motion') === 'armed';
      const line = window.innerHeight * 0.6 - root.getBoundingClientRect().top;
      root.style.setProperty('--rail-p', (armed ? clamp((line - railTop) / railHeight) : 1).toFixed(4));
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
    window.addEventListener('scroll', schedule, {passive: true});
    window.addEventListener('resize', measure);
    window.addEventListener('beforeprint', completeAll);
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure);
    observer?.observe(root);
    void document.fonts?.ready.then(measure);

    return () => {
      if (scrollFrame) cancelAnimationFrame(scrollFrame);
      frames.forEach(cancelAnimationFrame);
      frames.clear();
      watcher.disconnect();
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', measure);
      window.removeEventListener('beforeprint', completeAll);
      observer?.disconnect();
      chapters.forEach(chapter => chapter.strokes.forEach(({el}) => { el.style.strokeDashoffset = ''; }));
    };
  }, [rootRef]);
}
