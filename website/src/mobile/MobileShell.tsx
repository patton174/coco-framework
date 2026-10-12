import type {ReactNode} from 'react';
import {useCallback, useEffect, useRef, useState} from 'react';
import Link from '@docusaurus/Link';
import Head from '@docusaurus/Head';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useColorMode} from '@docusaurus/theme-common';
import LayoutProvider from '@theme/Layout/Provider';
import {useLocation} from '@docusaurus/router';
import clsx from 'clsx';
import Icon from '@site/src/components/Icon';
import Sheet from './Sheet';
import {sectionOf, useMobile, useSiteBase, type NavItem, type Section} from './useMobile';
import styles from './styles.module.css';

declare global {
  interface Window {
    __cocoView?: {
      pref: () => string | null;
      set: (value: string | null) => void;
      toDesktop: (path: string) => string | null;
    };
  }
}

const THEME_COLORS = {light: '#f5f0e8', dark: '#1f1e1d'} as const;

function Tree({items, current, depth = 0}: {items: NavItem[]; current: string; depth?: number}): ReactNode {
  return (
    <ul className={styles.tree} data-depth={depth}>
      {items.map(item => {
        if (item.type === 'doc') {
          return (
            <li key={item.mobilePath}>
              <Link to={item.mobilePath} className={clsx(styles.treeLink, item.mobilePath === current && styles.treeActive)} aria-current={item.mobilePath === current ? 'page' : undefined}>
                {item.title}
              </Link>
            </li>
          );
        }
        const open = item.items.some(child => child.type === 'doc' && child.mobilePath === current) || item.mobilePath === current;
        return (
          <li key={item.title}>
            <details open={open} className={styles.group}>
              <summary>
                <span>{item.title}</span>
                <Icon name="chevron" size={16} className={styles.groupChevron} />
              </summary>
              <Tree items={item.items} current={current} depth={depth + 1} />
            </details>
          </li>
        );
      })}
    </ul>
  );
}

interface ShellProps {
  children: ReactNode;
  /** Chapters of the page, shown in the contents sheet. */
  toc?: {id: string; value: string; level: number}[];
  /** Raw desktop path of this page, for the desktop switch. */
  desktopPath?: string;
  title?: string;
  description?: string;
  /** Where this page lives on the desktop site (canonical). */
  canonicalPath?: string;
  /** Reading progress ring + prev/next swipe are only meaningful on documents. */
  document?: boolean;
  prev?: {title: string; mobilePath: string} | null;
  next?: {title: string; mobilePath: string} | null;
}

/**
 * The mobile app shell: slim top bar and one bottom sheet for navigation and preferences. It replaces
 * Docusaurus's own Layout entirely on /m/ routes, so no desktop navbar, drawer or footer is rendered or hydrated.
 */
function Shell({children, toc = [], desktopPath, title, description, canonicalPath, document: isDoc = false, prev, next}: ShellProps): ReactNode {
  const {t, nav, docFor, locale} = useMobile();
  const {siteConfig} = useDocusaurusContext();
  const siteBase = useSiteBase();
  const {colorMode, setColorMode, colorModeChoice} = useColorMode();
  const location = useLocation();
  const [sheet, setSheet] = useState<'toc' | 'menu' | null>(null);
  const [toast, setToast] = useState('');
  const ringRef = useRef<SVGCircleElement>(null);
  const [activeId, setActiveId] = useState('');
  const bar = useRef<HTMLDivElement>(null);
  const current = location.pathname.replace(/\/+$/, '');

  // Plugin data uses base-relative paths (/m/overview); location.pathname carries the site and locale base.
  const localeBase = useBaseUrl('/').replace(/\/+$/, '');
  const markSrc = useBaseUrl(colorMode === 'dark' ? 'img/brand/mark-clay-dark.svg' : 'img/brand/mark-clay.svg');
  const here = current.startsWith(localeBase) ? current.slice(localeBase.length) || '/' : current;
  const doc = docFor(here);
  const section: Section = here === '/m' ? 'home' : sectionOf(doc);
  const tabs: {key: Section; icon: Parameters<typeof Icon>[0]['name']; label: string; to: string}[] = [
    {key: 'home', icon: 'home', label: t.home, to: '/m'},
    {key: 'docs', icon: 'book', label: t.docs, to: '/m/overview'},
    {key: 'skills', icon: 'spark', label: t.skills, to: '/m/skills'},
    {key: 'releases', icon: 'history', label: t.releases, to: '/m/releases'},
  ];
  const sidebarId = section === 'skills' ? 'skillsSidebar' : section === 'releases' ? 'releasesSidebar' : 'docsSidebar';
  const tree = nav.find(entry => entry.id === sidebarId)?.items ?? [];

  const close = useCallback(() => setSheet(null), []);
  useEffect(() => { setSheet(null); }, [location.pathname]);

  // Keep the browser UI colour in step with the page, so the status bar blends with the top bar.
  const themeColor = THEME_COLORS[colorMode];

  // Reading progress and the chapter in view. The ring is written straight to the DOM: a React state here would
  // re-render the whole shell (menu tree, contents sheet) on every scroll frame. Reads come before the write so a
  // frame never forces a second layout.
  useEffect(() => {
    if (!isDoc) return undefined;
    let frame = 0;
    const circumference = 2 * Math.PI * 9;
    const headings = toc.map(entry => window.document.getElementById(entry.id)).filter((node): node is HTMLElement => !!node);
    const update = () => {
      frame = 0;
      const root = window.document.documentElement;
      const max = root.scrollHeight - window.innerHeight;
      const progress = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
      let active = '';
      for (const heading of headings) {
        if (heading.getBoundingClientRect().top <= 96) active = heading.id; else break;
      }
      if (ringRef.current) ringRef.current.style.strokeDashoffset = String(circumference * (1 - progress));
      setActiveId(active);
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    update();
    window.addEventListener('scroll', schedule, {passive: true});
    window.addEventListener('resize', schedule);
    return () => {
      if (frame) cancelAnimationFrame(frame);
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
    };
  }, [isDoc, toc, location.pathname]);

  // Hide the top bar while reading down, bring it back on the way up (and always near the top).
  useEffect(() => {
    let last = window.scrollY;
    const onScroll = () => {
      const y = window.scrollY;
      const node = bar.current;
      if (node) {
        if (y < 120 || y < last - 4) delete node.dataset.hidden;
        else if (y > last + 4) node.dataset.hidden = '';
      }
      last = y;
    };
    window.addEventListener('scroll', onScroll, {passive: true});
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const flash = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 1800);
  };
  const share = async () => {
    const url = window.location.href;
    try {
      if (navigator.share) { await navigator.share({title: title ?? siteConfig.title, url}); return; }
      await navigator.clipboard.writeText(url);
      flash(t.copied);
    } catch (error) {
      if ((error as DOMException)?.name !== 'AbortError') flash(t.shareFailed);
    }
  };

  const goDesktop = () => {
    window.__cocoView?.set('desktop');
    const target = window.__cocoView?.toDesktop(window.location.pathname) ?? desktopPath ?? '/';
    window.location.assign(`${target}${window.location.hash}`);
  };
  const otherLocale = locale === 'en' ? '' : '/en';
  const switchLocale = () => {
    const withoutBase = window.location.pathname.slice(siteBase.length);
    const stripped = locale === 'en' ? withoutBase.replace(/^\/en(?=\/|$)/, '') : withoutBase;
    window.location.assign(`${siteBase}${otherLocale}${stripped || '/'}${window.location.hash}`);
  };

  // Swipe left/right to turn pages: horizontal intent only, never from code blocks or tables.
  const touch = useRef<{x: number; y: number; ok: boolean} | null>(null);
  const onTouchStart = (event: React.TouchEvent) => {
    const target = event.target as HTMLElement;
    const blocked = !!target.closest('pre, table, [data-no-swipe]');
    touch.current = {x: event.touches[0].clientX, y: event.touches[0].clientY, ok: !blocked};
  };
  const onTouchEnd = (event: React.TouchEvent) => {
    const start = touch.current;
    touch.current = null;
    if (!start?.ok || !isDoc) return;
    const dx = event.changedTouches[0].clientX - start.x;
    const dy = event.changedTouches[0].clientY - start.y;
    if (Math.abs(dx) < 90 || Math.abs(dx) < Math.abs(dy) * 2) return;
    const target = dx < 0 ? next : prev;
    if (target) window.location.assign(`${localeBase}${target.mobilePath}`);
  };

  const canonical = canonicalPath ? `${siteConfig.url}${localeBase}${canonicalPath === '/' ? '/' : canonicalPath}` : undefined;
  const ring = 2 * Math.PI * 9;

  return (
    <div className={styles.shell} onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
      <Head>
        <meta name="robots" content="noindex, follow" />
        <meta name="theme-color" content={themeColor} />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="format-detection" content="telephone=no" />
        {title ? <title>{title}</title> : null}
        {description ? <meta name="description" content={description} /> : null}
        {canonical ? <link rel="canonical" href={canonical} /> : null}
      </Head>
      <a className={styles.skip} href="#m-content">{t.skip}</a>

      <header className={styles.bar} ref={bar}>
        <Link to="/m" className={styles.brand} aria-label="Coco Framework">
          <img src={markSrc} alt="" width={26} height={26} />
          <span>Coco</span>
        </Link>
        <div className={styles.barTitle} aria-hidden="true">{isDoc ? title : ''}</div>
        <div className={styles.barActions}>
          {isDoc && toc.length > 0 ? (
            <button type="button" className={styles.iconButton} onClick={() => setSheet('toc')} aria-label={t.toc} aria-haspopup="dialog">
              <Icon name="list" />
              <svg className={styles.ring} viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <circle cx="12" cy="12" r="9" className={styles.ringValue} ref={ringRef} style={{strokeDasharray: ring, strokeDashoffset: ring}} />
              </svg>
            </button>
          ) : null}
          <button type="button" className={styles.iconButton} onClick={() => setSheet('menu')} aria-label={t.menu} aria-haspopup="dialog">
            <Icon name="menu" />
          </button>
        </div>
      </header>

      <main id="m-content" className={styles.main}>{children}</main>

      <Sheet open={sheet === 'toc'} title={t.onPage} closeLabel={t.close} onClose={close}>
        <ul className={styles.toc}>
          {toc.map(entry => (
            <li key={entry.id} data-level={entry.level}>
              <a href={`#${entry.id}`} className={clsx(activeId === entry.id && styles.tocActive)} onClick={close}>{entry.value}</a>
            </li>
          ))}
        </ul>
      </Sheet>

      <Sheet open={sheet === 'menu'} title={t.menu} closeLabel={t.close} onClose={close} tall>
        <nav className={styles.sections} aria-label={t.sections}>
          {tabs.map(tab => (
            <Link key={tab.key} to={tab.to} className={clsx(styles.sectionLink, section === tab.key && styles.sectionActive)} aria-current={section === tab.key ? 'page' : undefined}>
              <Icon name={tab.icon} size={22} />
              <span>{tab.label}</span>
              <Icon name="arrow-right" size={16} />
            </Link>
          ))}
        </nav>
        {section !== 'home' && tree.length > 0 ? (
          <>
            <h3 className={styles.menuLabel}>{t.chapters}</h3>
            <Tree items={tree} current={here} />
          </>
        ) : null}
        <h3 className={styles.menuLabel}>{t.settings}</h3>
        <div className={styles.more}>
          <div className={styles.segment} role="group" aria-label={t.appearance}>
            {([['light', 'sun', t.light], ['dark', 'moon', t.dark], [null, 'auto', t.auto]] as const).map(([value, icon, label]) => (
              <button key={String(value)} type="button" aria-pressed={colorModeChoice === value} onClick={() => setColorMode(value)}>
                <Icon name={icon} size={18} /><span>{label}</span>
              </button>
            ))}
          </div>
          <button type="button" className={styles.row} onClick={switchLocale}>
            <Icon name="globe" /><span>{t.language}</span><em>{t.otherLanguage}</em>
          </button>
          {isDoc ? (
            <button type="button" className={styles.row} onClick={() => { close(); void share(); }}>
              <Icon name="share" /><span>{t.share}</span>
            </button>
          ) : null}
          <button type="button" className={styles.row} onClick={goDesktop}>
            <Icon name="desktop" /><span>{t.desktop}</span><em>{t.desktopHint}</em>
          </button>
          <a className={styles.row} href="https://github.com/patton174/coco-framework">
            <Icon name="github" /><span>{t.github}</span><Icon name="external" size={16} />
          </a>
        </div>
      </Sheet>

      <div className={clsx(styles.toast, toast && styles.toastShown)} role="status" aria-live="polite">{toast}</div>
    </div>
  );
}

/** Providers only (colour mode, scroll control): none of the desktop navbar, drawer or footer is rendered. */
export default function MobileShell(props: ShellProps): ReactNode {
  return (
    <LayoutProvider>
      <Shell {...props} />
    </LayoutProvider>
  );
}
