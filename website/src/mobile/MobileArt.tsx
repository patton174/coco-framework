import type {ReactNode} from 'react';
import styles from './home.module.css';

/**
 * Line art for the phone home page. All of it is decorative (aria-hidden) and is drawn in its final state, so
 * without JavaScript or with reduced motion it simply sits there. useMobileMotion finds the pieces through
 * data-m hooks: strokes carry pathLength=1 so a single dashoffset tween draws any shape.
 */
const f = (n: number) => n.toFixed(1);

function ticks(cx: number, cy: number, inner: number, outer: number, count: number, long = 6): string {
  const parts: string[] = [];
  for (let i = 0; i < count; i += 1) {
    const a = (i / count) * Math.PI * 2;
    const r0 = i % long === 0 ? inner - 6 : inner;
    parts.push(`M${f(cx + Math.cos(a) * r0)},${f(cy + Math.sin(a) * r0)}L${f(cx + Math.cos(a) * outer)},${f(cy + Math.sin(a) * outer)}`);
  }
  return parts.join('');
}

function polygon(cx: number, cy: number, r: number, sides: number, start = -90): string {
  const pts = Array.from({length: sides}, (_, i) => {
    const a = ((start + (i * 360) / sides) * Math.PI) / 180;
    return `${f(cx + Math.cos(a) * r)},${f(cy + Math.sin(a) * r)}`;
  });
  return `M${pts.join('L')}Z`;
}

/** Fixed page background: two rings at the lower left that drift up (and turn) as the page scrolls. */
export function BackdropArt(): ReactNode {
  return (
    <svg className={styles.backdrop} viewBox="0 0 375 700" preserveAspectRatio="xMinYMax slice" aria-hidden="true" focusable="false">
      <g data-m="bg-drift">
        <g data-m="bg-spin">
          <circle data-m="bg-stroke" cx={20} cy={600} r={112} pathLength={1} />
          <path data-m="bg-stroke" d={ticks(20, 600, 120, 130, 60)} pathLength={1} />
          <circle data-m="bg-stroke" cx={20} cy={600} r={70} pathLength={1} />
          <path data-m="bg-stroke" d={polygon(20, 600, 70, 6)} pathLength={1} />
        </g>
      </g>
    </svg>
  );
}

/** Hero dial: turns with the page and sits concentric with the bean mark. */
function HeroDial(): ReactNode {
  return (
    <svg className={styles.heroDial} viewBox="0 0 200 200" aria-hidden="true" focusable="false">
      <g data-m="hero-spin">
        <circle data-m="hero-ring" cx={100} cy={100} r={92} pathLength={1} />
        <path data-m="hero-ring" d={ticks(100, 100, 80, 92, 72, 6)} pathLength={1} />
        <path data-m="hero-ring" d={polygon(100, 100, 66, 6)} pathLength={1} />
      </g>
      <circle data-m="hero-ring" cx={100} cy={100} r={54} pathLength={1} />
    </svg>
  );
}

const STAND = 'translate(21.6 18.4) rotate(16)';
const LIE = 'translate(10 25.2) rotate(-8)';

/** The two-bean mark, drawn as outlines first and filled afterwards. Same geometry as scripts/generate-brand.py. */
function BeanArt(): ReactNode {
  return (
    <svg className={styles.beans} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <g data-m="hero-float">
        <g transform="translate(-1 -4.8) scale(1.1)">
          <ellipse data-m="hero-bean" className={styles.bean} rx={6.6} ry={10.2} transform={STAND} pathLength={1} />
          <ellipse data-m="hero-bean" className={styles.bean} rx={7.6} ry={4.9} transform={LIE} pathLength={1} />
          <path data-m="hero-crease" className={styles.crease} d="M-0.9,-8.4C-4.6,-3.4 4.6,3.4 0.9,8.4" transform={STAND} pathLength={1} />
          <path data-m="hero-crease" className={styles.crease} d="M-6.2,0.9C-2.6,-3.4 2.6,3.4 6.2,-0.9" transform={LIE} pathLength={1} />
        </g>
      </g>
    </svg>
  );
}

/** The hero figure: dial and beans share one box so they stay concentric at any width. */
export function HeroArt(): ReactNode {
  return (
    <div className={styles.heroArt} data-m="hero-art" aria-hidden="true">
      <HeroDial />
      <BeanArt />
    </div>
  );
}

/**
 * Capability dial: one node per capability, linked to the list below. The node of the capability that is
 * crossing the middle of the screen lights up, its spoke is drawn and the outer ring turns to it.
 */
export function DialArt({count}: {count: number}): ReactNode {
  const c = 60;
  const step = 360 / count;
  const at = (i: number, r: number) => {
    const a = ((-90 + i * step) * Math.PI) / 180;
    return [c + Math.cos(a) * r, c + Math.sin(a) * r] as const;
  };
  return (
    <svg className={styles.dial} viewBox="0 0 120 120" aria-hidden="true" focusable="false">
      <g data-m="dial-ring">
        <circle className={styles.dialRing} cx={c} cy={c} r={55} />
        <path className={styles.dialTicks} d={ticks(c, c, 50, 55, 36)} />
        <circle className={styles.dialMarker} cx={c} cy={c - 55} r={3.2} />
      </g>
      <path className={styles.dialHex} d={polygon(c, c, 38, count)} />
      {Array.from({length: count}, (_, i) => {
        const [x1, y1] = at(i, 11);
        const [x2, y2] = at(i, 33);
        const [nx, ny] = at(i, 38);
        return (
          <g key={i}>
            <line data-m="dial-spoke" className={styles.dialSpoke} x1={f(x1)} y1={f(y1)} x2={f(x2)} y2={f(y2)} />
            <circle data-m="dial-node" className={styles.dialNode} cx={f(nx)} cy={f(ny)} r={4.4} />
          </g>
        );
      })}
      <circle className={styles.dialCore} cx={c} cy={c} r={8} />
    </svg>
  );
}
