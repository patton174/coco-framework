import type {ReactNode, SVGProps} from 'react';

/**
 * One hairline icon set for the whole site (desktop chrome and the mobile shell).
 * 24px grid, 1.5px stroke, round caps and joins, `currentColor`: the same drawing language as the
 * hairline arrows in src/components/Glyphs. Decorative by default (aria-hidden); pass `title` to name one.
 */
export type IconName =
  | 'home' | 'book' | 'spark' | 'history' | 'dots' | 'list' | 'share' | 'sun' | 'moon' | 'auto'
  | 'globe' | 'github' | 'chevron' | 'close' | 'arrow-left' | 'arrow-right' | 'desktop'
  | 'check' | 'copy' | 'link' | 'external' | 'menu' | 'star';

const shapes: Record<IconName, ReactNode> = {
  home: <path d="M4 11.2 12 4l8 7.2V19a1.5 1.5 0 0 1-1.5 1.5H15v-5.5H9v5.5H5.5A1.5 1.5 0 0 1 4 19z" />,
  book: <path d="M12 6.2C10.4 4.8 8 4.3 4.5 4.3v12.9c3.5 0 5.9.5 7.5 1.9 1.6-1.4 4-1.9 7.5-1.9V4.3c-3.5 0-5.9.5-7.5 1.9zM12 6.2v12.9" />,
  spark: <path d="M11 3.5c.6 4.4 2.3 6.1 6.7 6.7-4.4.6-6.1 2.3-6.7 6.7-.6-4.4-2.3-6.1-6.7-6.7C8.7 9.6 10.4 7.9 11 3.5zM18.2 15.6c.2 1.5.8 2.1 2.3 2.3-1.5.2-2.1.8-2.3 2.3-.2-1.5-.8-2.1-2.3-2.3 1.5-.2 2.1-.8 2.3-2.3z" />,
  history: <path d="M4.2 12a7.8 7.8 0 1 0 2.4-5.6M4 4.2v3.9h3.9M12 8v4.4l2.9 1.7" />,
  dots: (
    <>
      <circle cx="5.5" cy="12" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="18.5" cy="12" r="1.2" fill="currentColor" stroke="none" />
    </>
  ),
  list: <path d="M9.5 6.5h10M9.5 12h10M9.5 17.5h10M4.8 6.5h.01M4.8 12h.01M4.8 17.5h.01" />,
  share: <path d="M12 14.5V4m0 0L8.3 7.7M12 4l3.7 3.7M6.5 11.5V18A1.5 1.5 0 0 0 8 19.5h8a1.5 1.5 0 0 0 1.5-1.5v-6.5" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="3.8" />
      <path d="M12 3.5v1.8M12 18.7v1.8M3.5 12h1.8M18.7 12h1.8M6 6l1.3 1.3M16.7 16.7 18 18M18 6l-1.3 1.3M7.3 16.7 6 18" />
    </>
  ),
  moon: <path d="M19.5 14.2A7.8 7.8 0 0 1 9.8 4.5a7.8 7.8 0 1 0 9.7 9.7z" />,
  auto: (
    <>
      <circle cx="12" cy="12" r="7.8" />
      <path d="M12 4.2a7.8 7.8 0 0 0 0 15.6z" fill="currentColor" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17M12 3.5c2.4 2.3 3.6 5.1 3.6 8.5s-1.2 6.2-3.6 8.5c-2.4-2.3-3.6-5.1-3.6-8.5S9.6 5.8 12 3.5zM5 7.6c2 1.1 4.4 1.6 7 1.6s5-.5 7-1.6M5 16.4c2-1.1 4.4-1.6 7-1.6s5 .5 7 1.6" />
    </>
  ),
  github: (
    <path
      fill="currentColor"
      stroke="none"
      d="M12 2.4a9.7 9.7 0 0 0-3.07 18.9c.49.09.66-.21.66-.47v-1.66c-2.7.59-3.27-1.3-3.27-1.3-.44-1.12-1.08-1.42-1.08-1.42-.88-.6.07-.59.07-.59.98.07 1.49 1 1.49 1 .87 1.49 2.28 1.06 2.83.81.09-.63.34-1.06.62-1.3-2.16-.24-4.42-1.08-4.42-4.8 0-1.06.38-1.93 1-2.6-.1-.25-.43-1.23.1-2.57 0 0 .82-.26 2.67 1a9.2 9.2 0 0 1 4.86 0c1.85-1.26 2.67-1 2.67-1 .53 1.34.2 2.32.1 2.57.62.67 1 1.54 1 2.6 0 3.73-2.27 4.55-4.43 4.79.35.3.66.89.66 1.8v2.67c0 .26.17.57.67.47A9.7 9.7 0 0 0 12 2.4z"
    />
  ),
  chevron: <path d="M9 5.5 15.5 12 9 18.5" />,
  close: <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />,
  'arrow-left': <path d="M19 12H5.5M5.5 12l5.5-5.5M5.5 12l5.5 5.5" />,
  'arrow-right': <path d="M5 12h13.5M18.5 12 13 6.5M18.5 12 13 17.5" />,
  desktop: <path d="M3.5 6A1.5 1.5 0 0 1 5 4.5h14A1.5 1.5 0 0 1 20.5 6v8.5A1.5 1.5 0 0 1 19 16H5a1.5 1.5 0 0 1-1.5-1.5zM8.5 19.5h7M12 16v3.5" />,
  check: <path d="M5 12.5 9.8 17.3 19 7.2" />,
  copy: <path d="M9 9h9.5a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1H10a1 1 0 0 1-1-1zM5.5 15V5a1 1 0 0 1 1-1H15" />,
  link: <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />,
  menu: <path d="M4.5 8h15M4.5 16h10" />,
  star: <path d="M12 3.6l2.5 5.3 5.8.8-4.2 4 1 5.8L12 16.7l-5.1 2.8 1-5.8-4.2-4 5.8-.8z" />,
  external: <path d="M10 5.5H6A1.5 1.5 0 0 0 4.5 7v11A1.5 1.5 0 0 0 6 19.5h11a1.5 1.5 0 0 0 1.5-1.5v-4M13.5 4.5h6v6M19.5 4.5 11 13" />,
};

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  size?: number;
  title?: string;
}

export default function Icon({name, size = 20, title, className, ...rest}: IconProps): ReactNode {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      focusable="false"
      {...rest}>
      {title ? <title>{title}</title> : null}
      {shapes[name]}
    </svg>
  );
}
