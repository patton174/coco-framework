import type {ReactNode} from 'react';
import clsx from 'clsx';
import {STAGGER_MS} from '@site/src/components/HomeMotion/useHomeMotion';
import styles from './styles.module.css';

export type StoryArtKind = 'route' | 'connector' | 'stars';

/**
 * Timeline attributes read by useStory. A stroke starts `t` ms after the first element of its chapter
 * is revealed, takes `dur` ms and eases out (or runs linear when it must track something moving).
 * Elements that should appear when a stroke reaches them get a matching `data-order` (delay = order * STAGGER_MS).
 */
const beat = (t: number, dur = 600, linear = false) => ({'data-t': Math.round(t), 'data-dur': dur, ...(linear ? {'data-ease': 'linear'} : {})});

// Every drawable part carries pathLength="1": useStory reveals it by moving stroke-dashoffset from 1 to 0.

// ---- Chapter 3: a rising route under the four steps -------------------------------------------------------
// The steps grid uses a 3.5% column gap, so column centres sit at 11.1875% + 25.875% * i of the width
// (1200 units: 134.25 + 310.5 * i). Each step climbs 13.8% of a column (37.06 units); the figure pads its
// bottom by 4.5% (54 units), so the first step ends at y=106 and every stop sits 24 units below its step.
const ROUTE_MS = 900;
const ROUTE_FROM = 30;
const ROUTE_SPAN = 1140;
const stopX = (step: number): number => 134.25 + 310.5 * step;
/** When the (linear) route line passes each step, so the step's copy can appear exactly then. */
export const ROUTE_ARRIVALS_MS = [0, 1, 2, 3].map(step => ((stopX(step) - ROUTE_FROM) / ROUTE_SPAN) * ROUTE_MS);

function Route(): ReactNode {
  const stops = [0, 1, 2, 3].map(step => [stopX(step), 130 - 37.06 * step] as const);
  return (
    <>
      <path d="M30,142.4 L1170,6.3" pathLength={1} {...beat(0, ROUTE_MS, true)} />
      {stops.map(([x, y], step) => <circle key={x} cx={x} cy={y} r={7} pathLength={1} {...beat(ROUTE_ARRIVALS_MS[step], 320)} />)}
      <circle cx={stops[3][0]} cy={stops[3][1]} r={16} pathLength={1} {...beat(ROUTE_MS, 420)} />
    </>
  );
}

// ---- Chapter 4: Framework feeds Admin and Generate --------------------------------------------------------
// Percent coordinates inside a dedicated grid cell: Framework sits at its left edge (y 50%), the other two at
// 25% / 75%. Admin and Generate appear when the branches have arrived.
export const CONNECTOR_ARRIVAL_MS = 900;

function Connector(): ReactNode {
  return (
    <>
      <circle cx="0%" cy="50%" r={6} pathLength={1} {...beat(0, 300)} />
      <line x1="0%" y1="50%" x2="50%" y2="50%" pathLength={1} {...beat(200, 350)} />
      <line x1="50%" y1="50%" x2="50%" y2="25%" pathLength={1} {...beat(550, 220)} />
      <line x1="50%" y1="50%" x2="50%" y2="75%" pathLength={1} {...beat(550, 220)} />
      <line x1="50%" y1="25%" x2="100%" y2="25%" pathLength={1} {...beat(770, 280)} />
      <line x1="50%" y1="75%" x2="100%" y2="75%" pathLength={1} {...beat(770, 280)} />
      <circle cx="100%" cy="25%" r={6} pathLength={1} {...beat(1000, 260)} />
      <circle cx="100%" cy="75%" r={6} pathLength={1} {...beat(1000, 260)} />
    </>
  );
}

// ---- Chapter 5: a star with three ways to reach it -------------------------------------------------------
// The closing chapter asks for a GitHub star and offers three doors (discussions, issues, contributing). The star is
// drawn first, then each door as a line out to a node, in the order the links appear.
const STAR_CENTER = 300;
function starPath(outer: number, inner: number): string {
  const points: string[] = [];
  for (let vertex = 0; vertex < 10; vertex += 1) {
    const radius = vertex % 2 === 0 ? outer : inner;
    const angle = (-90 + vertex * 36) * (Math.PI / 180);
    points.push(`${(STAR_CENTER + Math.cos(angle) * radius).toFixed(1)},${(STAR_CENTER + Math.sin(angle) * radius).toFixed(1)}`);
  }
  return `M${points.join('L')}Z`;
}
const STAR = starPath(74, 30);
const DOORS = [[118, 168], [506, 214], [430, 500]] as const;
const FAINT = [[70, 380], [250, 530], [540, 380], [360, 80]] as const;

function Stars(): ReactNode {
  return (
    <>
      <path d={STAR} pathLength={1} {...beat(0, 800)} />
      <circle cx={STAR_CENTER} cy={STAR_CENTER} r={118} pathLength={1} {...beat(500, 700)} />
      {DOORS.map(([x, y], door) => {
        const angle = Math.atan2(y - STAR_CENTER, x - STAR_CENTER);
        const from = [STAR_CENTER + Math.cos(angle) * 118, STAR_CENTER + Math.sin(angle) * 118];
        return (
          <g key={door}>
            <line x1={from[0].toFixed(1)} y1={from[1].toFixed(1)} x2={x} y2={y} pathLength={1} {...beat(900 + door * 280, 420)} />
            <circle cx={x} cy={y} r={9} pathLength={1} {...beat(1250 + door * 280, 320)} />
            <circle cx={x} cy={y} r={3} pathLength={1} {...beat(1450 + door * 280, 200)} />
          </g>
        );
      })}
      {FAINT.map(([x, y], dot) => <path key={dot} d={`M${x - 6},${y}h12M${x},${y - 6}v12`} pathLength={1} {...beat(1900 + dot * 120, 250)} />)}
    </>
  );
}

const shapes: Record<StoryArtKind, () => ReactNode> = {route: Route, connector: Connector, stars: Stars};
const viewBoxes: Record<StoryArtKind, string | undefined> = {route: '0 0 1200 160', connector: undefined, stars: '0 0 600 600'};

/** A line drawing attached to one figure of a homepage chapter, drawn on a timeline (see useStory). */
export default function StoryArt({kind}: {kind: StoryArtKind}): ReactNode {
  const Shape = shapes[kind];
  return (
    <svg className={clsx(styles.ink, styles.art)} data-kind={kind} data-draw="" viewBox={viewBoxes[kind]} preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false">
      <Shape />
    </svg>
  );
}

/**
 * The spine of the capability list: this row's node, then a line down to the next row's node. It lives inside
 * the row, so the geometry follows the row's own height (see .spine). Each node is drawn as its row appears.
 */
export const rowDelay = (position: number): number => 200 + position * STAGGER_MS;
export function RowLine({position, last = false}: {position: number; last?: boolean}): ReactNode {
  const t = rowDelay(position);
  return (
    <svg className={clsx(styles.ink, styles.spine)} data-draw="" data-last={last || undefined} width={10} aria-hidden="true" focusable="false">
      {!last && <line x1={5} y1={9.5} x2={5} y2="100%" pathLength={1} {...beat(t + 150, 260)} />}
      <circle cx={5} cy={5} r={4.5} pathLength={1} {...beat(t, 320)} />
    </svg>
  );
}

/** The wall is laid course by course from the foundation: each layer starts this long after the one below it. */
export const tierDelay = (order: number): number => order * 280;

/**
 * A frame drawn as a stroke around its host (position: relative). `layer` sits on the host's edge; `frame` stands
 * 8px outside it, like a viewfinder, and is used on the hero's code panel.
 */
export function Outline({start, accent = false, frame = false}: {start: number; accent?: boolean; frame?: boolean}): ReactNode {
  return (
    <svg className={clsx(styles.ink, styles.outline, frame && styles.frame)} data-draw="" data-accent={accent || undefined} aria-hidden="true" focusable="false">
      <rect width="100%" height="100%" rx={frame ? 16 : 8} pathLength={1} {...beat(start, 650)} />
    </svg>
  );
}
