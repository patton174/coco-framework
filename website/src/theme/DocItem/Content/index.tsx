import type {ReactNode} from 'react';
import Content from '@theme-original/DocItem/Content';
import type ContentType from '@theme/DocItem/Content';
import type {WrapperProps} from '@docusaurus/types';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import styles from './styles.module.css';
import {useDocMotion} from './useDocMotion';

type Props = WrapperProps<typeof ContentType>;

// Page actions (copy as Markdown, raw source, edit) live on the breadcrumb row: src/theme/DocBreadcrumbs.
export default function ContentWrapper(props: Props): ReactNode {
  const {metadata} = useDoc();
  const {contentRef, progressRef} = useDocMotion(metadata.permalink);
  return (
    <>
      <div className={styles.readingTrack} aria-hidden="true">
        <div ref={progressRef} className={styles.readingProgress} />
      </div>
      <div ref={contentRef} className={styles.document}><Content {...props} /></div>
    </>
  );
}
