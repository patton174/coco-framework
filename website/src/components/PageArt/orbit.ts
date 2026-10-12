/**
 * A body travelling on a tilted ellipse. The spec travels in a data attribute ("cx,cy,rx,ry,tilt,start,turns") so the
 * markup (first paint, no JavaScript) and the scroll driver (useStory) compute the very same point.
 *
 * `start` is the angle in degrees at rest; `turns` is how many revolutions one unit of chapter position adds.
 */
export type OrbitSpec = readonly [cx: number, cy: number, rx: number, ry: number, tilt: number, start: number, turns: number];

export function orbitPoint([cx, cy, rx, ry, tilt, start, turns]: OrbitSpec, position: number): {x: number; y: number} {
  const angle = ((start + position * turns * 360) * Math.PI) / 180;
  const lean = (tilt * Math.PI) / 180;
  const along = rx * Math.cos(angle);
  const across = ry * Math.sin(angle);
  return {
    x: cx + along * Math.cos(lean) - across * Math.sin(lean),
    y: cy + along * Math.sin(lean) + across * Math.cos(lean),
  };
}

export function parseOrbit(value: string | undefined): OrbitSpec | undefined {
  if (!value) return undefined;
  const numbers = value.split(',').map(Number);
  return numbers.length === 7 && numbers.every(Number.isFinite) ? (numbers as unknown as OrbitSpec) : undefined;
}
