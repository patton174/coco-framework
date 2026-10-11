import {useEffect, useRef, useState, type ReactNode} from 'react';
import {rowDelay} from '@site/src/components/StoryArt';
import {orbitPoint, type OrbitSpec} from './orbit';
import styles from './styles.module.css';

/**
 * Background line art for the homepage. Every chapter owns one motif that sits behind its content, drawn from that
 * chapter's own subject, and a single thread runs down the whole page through all of them.
 *
 * - Strokes carry data-t / data-dur like the rest of the story art: useStory draws them as the chapter scrolls
 *   in and runs them back when scrolling up.
 * - Scroll linkage is declared with data attributes and applied by useStory straight onto the element
 *   (data-spin, data-orbit, data-shift), never through inherited CSS variables: a variable written on an ancestor
 *   invalidates the style of the whole page on every scroll frame.
 *
 * All of it is decorative (aria-hidden) and hidden below 997px, where the chapters stack and there is no margin.
 */
const beat = (t: number, dur = 600) => ({'data-t': t, 'data-dur': dur});
const point = (x: number, y: number) => `${x.toFixed(1)},${y.toFixed(1)}`;

// ---- Capabilities: six modules around the Starter -------------------------------------------------------------
// Each node stands for one row of the capability list and is drawn at the moment that row appears (rowDelay).
const CENTER = 300;
const NODE_R = 170;
const CORE_R = 34;
const NODE_SIZE = 11;
const nodeAngle = (module: number) => ((-90 + module * 60) * Math.PI) / 180;
const nodeAt = (module: number, radius: number) => [CENTER + Math.cos(nodeAngle(module)) * radius, CENTER + Math.sin(nodeAngle(module)) * radius] as const;
const MODULES = [0, 1, 2, 3, 4, 5];
const HEXAGON = `M${MODULES.map(module => point(...nodeAt(module, NODE_R))).join('L')}Z`;
const SPOKES = MODULES.map(module => `M${point(...nodeAt(module, CORE_R))}L${point(...nodeAt(module, NODE_R - NODE_SIZE))}`).join('');
function dialTicks(): string {
  const parts: string[] = [];
  for (let tick = 0; tick < 72; tick += 1) {
    const angle = (tick / 72) * Math.PI * 2;
    const inner = tick % 6 === 0 ? 258 : 268;
    parts.push(`M${point(CENTER + Math.cos(angle) * inner, CENTER + Math.sin(angle) * inner)}L${point(CENTER + Math.cos(angle) * 284, CENTER + Math.sin(angle) * 284)}`);
  }
  return parts.join('');
}
const DIAL = dialTicks();

function Modules(): ReactNode {
  return (
    <svg className={styles.modules} data-draw="" viewBox="0 0 600 600" aria-hidden="true" focusable="false">
      <g data-spin="-90" data-cx={CENTER} data-cy={CENTER}>
        <circle cx={CENTER} cy={CENTER} r={244} pathLength={1} {...beat(0, 600)} />
        <path d={DIAL} pathLength={1} {...beat(100, 800)} />
      </g>
      <path d={HEXAGON} pathLength={1} {...beat(rowDelay(0) - 50, 760)} />
      <path d={SPOKES} pathLength={1} {...beat(rowDelay(0) - 80, 800)} />
      <circle cx={CENTER} cy={CENTER} r={CORE_R} pathLength={1} {...beat(0, 450)} />
      <circle cx={CENTER} cy={CENTER} r={10} pathLength={1} {...beat(250, 300)} />
      {MODULES.map(module => {
        const [x, y] = nodeAt(module, NODE_R);
        return (
          <g key={module}>
            <circle cx={x} cy={y} r={NODE_SIZE} pathLength={1} {...beat(rowDelay(module), 350)} />
            <circle cx={x} cy={y} r={4} pathLength={1} {...beat(rowDelay(module) + 150, 250)} />
          </g>
        );
      })}
    </svg>
  );
}

// ---- Architecture: four layers, as wide as the layers they stand behind ---------------------------------------
// Business code is the narrow top course and Foundation the widest; they open up as the chapter passes.
const HALF_WIDTHS = [0.7, 0.8, 0.9, 1].map(share => 196 * share);
const SPREAD = [-70, -24, 24, 70];
const plane = (half: number) => `M200,4 L${200 + half},82 L200,160 L${200 - half},82 Z`;
const fold = (half: number) => `M${200 - half},82 L200,160 L${200 + half},82`;

function Planes(): ReactNode {
  return (
    <svg className={styles.planes} data-draw="" viewBox="0 0 400 420" aria-hidden="true" focusable="false">
      {SPREAD.map((spread, position) => (
        <g key={spread} data-shift={spread} data-rest={position * 74} transform={`translate(0 ${position * 74})`}>
          <path d={plane(HALF_WIDTHS[position])} pathLength={1} {...beat((SPREAD.length - 1 - position) * 280, 650)} />
          <path d={fold(HALF_WIDTHS[position])} pathLength={1} className={styles.fold} {...beat((SPREAD.length - 1 - position) * 280 + 250, 400)} />
        </g>
      ))}
    </svg>
  );
}

// ---- Ecosystem: Framework at the centre, Admin tied to it at run time, Generate only passing by ---------------
// Admin runs on a tight orbit and stays joined to Framework by a tether (a runtime dependency). Generate runs on
// a wide, loose orbit with no tether: it only touches your source while you develop.
const ADMIN: OrbitSpec = [CENTER, CENTER, 140, 78, -24, 40, 0.5];
const GENERATE: OrbitSpec = [CENTER, CENTER, 255, 118, 26, 215, -0.25];

function System(): ReactNode {
  const admin = orbitPoint(ADMIN, 0);
  const generate = orbitPoint(GENERATE, 0);
  return (
    <svg className={styles.system} data-draw="" viewBox="0 0 600 600" aria-hidden="true" focusable="false">
      <ellipse className={styles.loose} cx={CENTER} cy={CENTER} rx={GENERATE[2]} ry={GENERATE[3]} transform={`rotate(${GENERATE[4]} ${CENTER} ${CENTER})`} pathLength={1} {...beat(150, 900)} />
      <ellipse cx={CENTER} cy={CENTER} rx={ADMIN[2]} ry={ADMIN[3]} transform={`rotate(${ADMIN[4]} ${CENTER} ${CENTER})`} pathLength={1} {...beat(0, 800)} />
      <circle cx={CENTER} cy={CENTER} r={30} pathLength={1} {...beat(0, 500)} />
      <circle cx={CENTER} cy={CENTER} r={11} pathLength={1} {...beat(200, 300)} />
      <line data-follow="admin" x1={CENTER} y1={CENTER} x2={admin.x} y2={admin.y} pathLength={1} {...beat(600, 400)} />
      <g data-orbit={ADMIN.join(',')} data-id="admin" transform={`translate(${admin.x.toFixed(1)} ${admin.y.toFixed(1)})`}>
        <rect x={-8} y={-8} width={16} height={16} rx={3} pathLength={1} {...beat(500, 400)} />
      </g>
      <g data-orbit={GENERATE.join(',')} transform={`translate(${generate.x.toFixed(1)} ${generate.y.toFixed(1)})`}>
        <rect x={-8} y={-8} width={16} height={16} transform="rotate(45)" pathLength={1} {...beat(750, 400)} />
        <circle r={2.5} pathLength={1} {...beat(950, 200)} />
      </g>
    </svg>
  );
}

const motifs = {modules: Modules, planes: Planes, system: System};

/** One chapter's motif. Place it as the first child of the chapter's section. */
export function ChapterArt({kind}: {kind: keyof typeof motifs}): ReactNode {
  const Motif = motifs[kind];
  return (
    <div className={styles.stage} data-kind={kind} aria-hidden="true">
      <Motif />
    </div>
  );
}

/**
 * The page-wide layer: paper grain over everything, and one thread that winds from the first screen to the last,
 * switching sides at each chapter so it meets that chapter's motif at the page margin. The path is built from the
 * measured chapter positions (so it follows the real layout at any width) and drawn by the page position (--page-p).
 */
const SIDES = [0.965, 0.035, 0.965, 0.035, 0.965, 0.035];

function threadPath(page: HTMLElement): string {
  const host = page.parentElement;
  if (!host) return '';
  const width = host.clientWidth;
  const top = host.getBoundingClientRect().top;
  const marks = Array.from(host.querySelectorAll<HTMLElement>(':scope > header, :scope > section')).map(mark => {
    const box = mark.getBoundingClientRect();
    return {top: box.top - top, bottom: box.bottom - top, height: box.height};
  });
  if (marks.length < 2) return '';
  const x = (position: number) => ((SIDES[position] ?? 0.5) * width).toFixed(1);
  // Runs down the page margin, and changes sides only in the empty band around each chapter boundary,
  // so the line never crosses copy.
  const start = marks[0].bottom - marks[0].height * 0.22;
  let d = `M${x(0)},${start.toFixed(1)}`;
  for (let index = 1; index < marks.length; index += 1) {
    const from = marks[index - 1];
    const to = marks[index];
    const yA = Math.max(start, to.top - from.height * 0.1);
    const yB = to.top + to.height * 0.1;
    const mid = (yA + yB) / 2;
    d += ` L${x(index - 1)},${yA.toFixed(1)} C${x(index - 1)},${mid.toFixed(1)} ${x(index)},${mid.toFixed(1)} ${x(index)},${yB.toFixed(1)}`;
  }
  d += ` L${x(marks.length - 1)},${(marks[marks.length - 1].bottom - 24).toFixed(1)}`;
  return d;
}

export function PageBackdrop(): ReactNode {
  const ref = useRef<HTMLDivElement>(null);
  const [d, setD] = useState('');
  useEffect(() => {
    const page = ref.current;
    const host = page?.parentElement;
    if (!page || !host) return undefined;
    const measure = () => setD(threadPath(page));
    measure();
    window.addEventListener('resize', measure);
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure);
    observer?.observe(host);
    void document.fonts?.ready.then(measure);
    return () => {
      window.removeEventListener('resize', measure);
      observer?.disconnect();
    };
  }, []);
  return (
    <div ref={ref} className={styles.page} aria-hidden="true">
      {d && (
        <svg className={styles.thread} focusable="false">
          <path d={d} pathLength={1} className={styles.threadTrack} />
          <path d={d} pathLength={1} className={styles.threadInk} data-thread-ink="" />
        </svg>
      )}
      <span className={styles.grain} />
    </div>
  );
}
