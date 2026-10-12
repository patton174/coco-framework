import type {ComponentProps, ReactNode} from 'react';
import {useMemo} from 'react';
import Link from '@docusaurus/Link';
import {MDXProvider} from '@mdx-js/react';
import MDXComponents from '@theme/MDXComponents';
import {DocProvider} from '@docusaurus/plugin-content-docs/client';
import type {PropDocContent} from '@docusaurus/plugin-content-docs';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Icon from '@site/src/components/Icon';
import MobileShell from './MobileShell';
import {useMobile} from './useMobile';
import styles from './styles.module.css';

/**
 * Links inside MDX are compiled against the desktop site. On the mobile site an internal link must stay on
 * the mobile site, so it is rewritten to its /m/ twin (only for documents that have one).
 */
function useMobileLink() {
  const {docs} = useMobile();
  const {siteConfig, i18n} = useDocusaurusContext();
  const siteBase = siteConfig.baseUrl.replace(/\/+$/, '');
  const localeBase = `${siteBase}${i18n.currentLocale === i18n.defaultLocale ? '' : `/${i18n.currentLocale}`}`;
  return useMemo(() => {
    const byDesktop = new Map(docs.map(doc => [doc.desktopPath, doc.mobilePath]));
    return function MobileLink({href, children, ...rest}: ComponentProps<'a'>): ReactNode {
      if (href && href.startsWith('/')) {
        const [pathAndQuery, hash = ''] = href.split('#');
        const local = pathAndQuery.startsWith(localeBase) ? pathAndQuery.slice(localeBase.length) || '/' : pathAndQuery;
        const target = byDesktop.get(local.replace(/\/+$/, '') || '/');
        if (target) return <Link to={`${target}${hash ? `#${hash}` : ''}`} {...rest}>{children}</Link>;
      }
      return (MDXComponents.a as (props: ComponentProps<'a'>) => ReactNode)({href, children, ...rest});
    };
  }, [docs, localeBase]);
}

interface Props { content: PropDocContent }

/** Route component for /m/<doc>: the doc's own compiled MDX, inside the mobile shell. */
export default function MobileDocRoute({content: MDXContent}: Props): ReactNode {
  const {docs, t} = useMobile();
  const MobileLink = useMobileLink();
  const {metadata, frontMatter, toc} = MDXContent;
  const entry = docs.find(doc => doc.id === metadata.id);
  const components = useMemo(() => ({...MDXComponents, a: MobileLink}), [MobileLink]);
  const chapters = useMemo(
    () => toc.filter(item => item.level <= 3).map(item => ({id: item.id, value: item.value.replace(/<[^>]+>/g, ''), level: item.level})),
    [toc],
  );
  const description = (frontMatter.description as string | undefined) ?? metadata.description;

  return (
    <DocProvider content={MDXContent}>
      <MobileShell
        document
        title={metadata.title}
        description={description}
        toc={chapters}
        desktopPath={entry?.desktopPath}
        canonicalPath={entry?.desktopPath}
        prev={entry?.previous}
        next={entry?.next}>
        <article className={styles.article}>
          <div className="markdown">
            <MDXProvider components={components}>
              <MDXContent />
            </MDXProvider>
          </div>
          <nav className={styles.pager} aria-label={t.chapters}>
            {entry?.previous ? (
              <Link to={entry.previous.mobilePath} className={styles.pagerLink} data-dir="prev">
                <Icon name="arrow-left" size={18} />
                <span><small>{t.prev}</small>{entry.previous.title}</span>
              </Link>
            ) : <span />}
            {entry?.next ? (
              <Link to={entry.next.mobilePath} className={styles.pagerLink} data-dir="next">
                <span><small>{t.next}</small>{entry.next.title}</span>
                <Icon name="arrow-right" size={18} />
              </Link>
            ) : <span />}
          </nav>
          <p className={styles.swipeHint}>{t.swipe}</p>
        </article>
      </MobileShell>
    </DocProvider>
  );
}
