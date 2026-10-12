import type {KeyboardEvent as ReactKeyboardEvent, ReactNode} from 'react';
import {useCallback, useEffect, useId, useRef, useState} from 'react';
import {translate} from '@docusaurus/Translate';
import {AlertCircle, Check, ChevronDown, Copy, FileText, LoaderCircle, PenLine} from 'lucide-react';
import {CornerArrow} from '@site/src/components/Glyphs';
import styles from './styles.module.css';

type CopyState = 'idle' | 'copying' | 'copied' | 'error';
const STATES: readonly CopyState[] = ['idle', 'copying', 'copied', 'error'];
const ICONS = {idle: Copy, copying: LoaderCircle, copied: Check, error: AlertCircle} as const;

// Some docs opt out of editing, so no raw Markdown action is available.
export function rawUrlFromEditUrl(editUrl: string | null | undefined): string | null {
  if (!editUrl) return null;
  const match = editUrl.match(/^https:\/\/github\.com\/([^/]+)\/([^/]+)\/(?:tree|blob)\/([^/]+)\/(.+)$/);
  if (!match) return null;
  const [, owner, repo, branch, path] = match;
  return `https://raw.githubusercontent.com/${owner}/${repo}/${branch}/${path}`;
}

function usePrevious<T>(value: T): T | undefined {
  const ref = useRef<T | undefined>(undefined);
  useEffect(() => { ref.current = value; }, [value]);
  return ref.current;
}

/**
 * Page actions shown on the breadcrumb row of every doc: copy the page as Markdown (split button),
 * plus a menu with the raw source and the GitHub editor. Labels and icons of every state share one
 * grid cell, so the button never changes width; the outgoing state leaves upwards while the next
 * one arrives from below. The result is announced through a persistent polite live region.
 */
export default function PageActions({editUrl}: {editUrl: string | null | undefined}): ReactNode {
  const rawUrl = rawUrlFromEditUrl(editUrl);
  const menuId = useId();
  const [state, setState] = useState<CopyState>('idle');
  const previous = usePrevious(state);
  const [open, setOpen] = useState(false);
  const requestRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setState('idle');
    setOpen(false);
    return () => {
      requestRef.current?.abort();
      requestRef.current = null;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [rawUrl]);

  const items = () => Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
  const close = useCallback((restoreFocus: boolean) => {
    setOpen(false);
    if (restoreFocus) triggerRef.current?.focus();
  }, []);

  // Dismiss on outside pointer or focus leaving the control.
  useEffect(() => {
    if (!open) return undefined;
    const onPointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) close(false);
    };
    document.addEventListener('pointerdown', onPointer);
    return () => document.removeEventListener('pointerdown', onPointer);
  }, [open, close]);

  const onCopy = useCallback(async () => {
    if (!rawUrl || requestRef.current) return;
    if (timerRef.current) clearTimeout(timerRef.current);
    const controller = new AbortController();
    requestRef.current = controller;
    setState('copying');
    try {
      const response = await fetch(rawUrl, {signal: controller.signal});
      if (!response.ok) throw new Error(String(response.status));
      const text = await response.text();
      if (controller.signal.aborted) return;
      await navigator.clipboard.writeText(text);
      if (!controller.signal.aborted) setState('copied');
    } catch {
      if (!controller.signal.aborted) setState('error');
    } finally {
      if (requestRef.current === controller) requestRef.current = null;
      if (!controller.signal.aborted) {
        timerRef.current = setTimeout(() => setState('idle'), 2200);
      }
    }
  }, [rawUrl]);

  if (!rawUrl) return null;

  const labels: Record<CopyState, string> = {
    idle: translate({id: 'doc.copyMarkdown.idle', message: '复制为 Markdown'}),
    copying: translate({id: 'doc.copyMarkdown.loading', message: '正在复制 Markdown'}),
    copied: translate({id: 'doc.copyMarkdown.done', message: '已复制 Markdown'}),
    error: translate({id: 'doc.copyMarkdown.error', message: '复制失败'}),
  };
  const moreLabel = translate({id: 'doc.pageActions.more', message: '更多页面操作'});
  const position = (target: CopyState) => target === state ? 'current' : target === previous ? 'leaving' : 'waiting';

  const openMenu = (focus: 'first' | 'last' | null) => {
    setOpen(true);
    if (focus) requestAnimationFrame(() => { const list = items(); (focus === 'first' ? list[0] : list[list.length - 1])?.focus(); });
  };
  const onTriggerKey = (event: ReactKeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      openMenu(event.key === 'ArrowDown' ? 'first' : 'last');
    }
  };
  const onMenuKey = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    const list = items();
    const index = list.indexOf(document.activeElement as HTMLElement);
    const move = (next: number) => { event.preventDefault(); list[(next + list.length) % list.length]?.focus(); };
    if (event.key === 'ArrowDown') move(index + 1);
    else if (event.key === 'ArrowUp') move(index - 1);
    else if (event.key === 'Home') move(0);
    else if (event.key === 'End') move(list.length - 1);
    else if (event.key === 'Escape') { event.preventDefault(); close(true); }
    else if (event.key === 'Tab') close(false);
  };

  return (
    <div ref={rootRef} className={styles.root} data-open={open ? '' : undefined}>
      <div className={styles.split}>
        <button
          type="button"
          className={styles.copy}
          data-state={state}
          onClick={onCopy}
          disabled={state === 'copying'}
          aria-busy={state === 'copying'}
          aria-label={labels[state]}>
          <span className={styles.stack} aria-hidden="true">
            {STATES.map(target => {
              const Icon = ICONS[target];
              return <Icon key={target} size={15} strokeWidth={1.75} className={styles.icon} data-state={target} data-position={position(target)} />;
            })}
          </span>
          <span className={`${styles.stack} ${styles.labels}`} aria-hidden="true">
            {STATES.map(target => <span key={target} className={styles.label} data-position={position(target)}>{labels[target]}</span>)}
          </span>
        </button>
        <button
          ref={triggerRef}
          type="button"
          className={styles.trigger}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-controls={menuId}
          aria-label={moreLabel}
          title={moreLabel}
          onClick={() => (open ? close(false) : openMenu(null))}
          onKeyDown={onTriggerKey}>
          <ChevronDown size={15} strokeWidth={1.75} aria-hidden="true" className={styles.chevron} />
        </button>
      </div>

      <div ref={menuRef} id={menuId} className={styles.menu} role="menu" aria-label={moreLabel} onKeyDown={onMenuKey} inert={!open}>
        <button type="button" role="menuitem" className={styles.item} onClick={() => { close(true); void onCopy(); }}>
          <Copy size={16} strokeWidth={1.75} aria-hidden="true" className={styles.itemIcon} />
          <span className={styles.itemText}>
            <span className={styles.itemTitle}>{labels.idle}</span>
            <span className={styles.itemHint}>{translate({id: 'doc.pageActions.copyHint', message: '粘贴给 AI 助手或笔记'})}</span>
          </span>
        </button>
        <a role="menuitem" className={styles.item} href={rawUrl} target="_blank" rel="noopener noreferrer" onClick={() => close(false)}>
          <FileText size={16} strokeWidth={1.75} aria-hidden="true" className={styles.itemIcon} />
          <span className={styles.itemText}>
            <span className={styles.itemTitle}>{translate({id: 'doc.pageActions.viewRaw', message: '查看 Markdown 原文'})}</span>
            <span className={styles.itemHint}>{translate({id: 'doc.pageActions.viewRawHint', message: '在新标签页打开源文件'})}</span>
          </span>
          <CornerArrow size={12} className={styles.itemGlyph} />
        </a>
        {editUrl && (
          <a role="menuitem" className={styles.item} href={editUrl} target="_blank" rel="noopener noreferrer" onClick={() => close(false)}>
            <PenLine size={16} strokeWidth={1.75} aria-hidden="true" className={styles.itemIcon} />
            <span className={styles.itemText}>
              <span className={styles.itemTitle}>{translate({id: 'doc.pageActions.edit', message: '在 GitHub 上编辑'})}</span>
              <span className={styles.itemHint}>{translate({id: 'doc.pageActions.editHint', message: '提交修改建议'})}</span>
            </span>
            <CornerArrow size={12} className={styles.itemGlyph} />
          </a>
        )}
      </div>

      <span className={styles.status} role="status" aria-live="polite" aria-atomic="true">
        {state === 'idle' ? '' : labels[state]}
      </span>
    </div>
  );
}
