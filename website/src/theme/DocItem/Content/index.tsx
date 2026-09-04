import type {ReactNode} from 'react';
import {useState, useCallback} from 'react';
import Content from '@theme-original/DocItem/Content';
import type ContentType from '@theme/DocItem/Content';
import type {WrapperProps} from '@docusaurus/types';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import Translate from '@docusaurus/Translate';
import styles from './styles.module.css';

type Props = WrapperProps<typeof ContentType>;

// metadata.editUrl is `string | null | undefined` — null when a doc opts out of
// editing via frontmatter, undefined when the plugin has no editUrl configured.
function rawUrlFromEditUrl(editUrl: string | null | undefined): string | null {
  if (!editUrl) return null;
  // https://github.com/OWNER/REPO/tree/BRANCH/path  ->  raw.githubusercontent.com/OWNER/REPO/BRANCH/path
  const m = editUrl.match(
    /^https:\/\/github\.com\/([^/]+)\/([^/]+)\/(?:tree|blob)\/([^/]+)\/(.+)$/,
  );
  if (!m) return null;
  const [, owner, repo, branch, path] = m;
  return `https://raw.githubusercontent.com/${owner}/${repo}/${branch}/${path}`;
}

export default function ContentWrapper(props: Props): ReactNode {
  const {metadata} = useDoc();
  const rawUrl = rawUrlFromEditUrl(metadata.editUrl);
  const [state, setState] = useState<'idle' | 'copied' | 'error'>('idle');

  const onCopy = useCallback(async () => {
    if (!rawUrl) return;
    try {
      const res = await fetch(rawUrl);
      if (!res.ok) throw new Error(String(res.status));
      const text = await res.text();
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('error');
    }
    setTimeout(() => setState('idle'), 2200);
  }, [rawUrl]);

  return (
    <>
      {rawUrl && (
        <div className={styles.copyBar}>
          <button
            type="button"
            className={styles.copyBtn}
            data-state={state}
            onClick={onCopy}
            aria-label="Copy this page as Markdown">
            <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"
              fill="none" stroke="currentColor" strokeWidth="2"
              strokeLinecap="round" strokeLinejoin="round">
              {state === 'copied' ? (
                <path d="M20 6 9 17l-5-5" />
              ) : (
                <>
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </>
              )}
            </svg>
            <span>
              {state === 'copied' ? (
                <Translate id="doc.copyMarkdown.done">已复制 Markdown</Translate>
              ) : state === 'error' ? (
                <Translate id="doc.copyMarkdown.error">复制失败</Translate>
              ) : (
                <Translate id="doc.copyMarkdown.idle">复制为 Markdown</Translate>
              )}
            </span>
          </button>
        </div>
      )}
      <Content {...props} />
    </>
  );
}
