import type {ReactNode} from 'react';
import DocBreadcrumbs from '@theme-original/DocBreadcrumbs';
import type DocBreadcrumbsType from '@theme/DocBreadcrumbs';
import type {WrapperProps} from '@docusaurus/types';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import PageActions from '@site/src/components/PageActions';

type Props = WrapperProps<typeof DocBreadcrumbsType>;

// The breadcrumb row doubles as the page toolbar: trail on the left, page actions on the right.
export default function DocBreadcrumbsWrapper(props: Props): ReactNode {
  const {metadata} = useDoc();
  return (
    <div className="doc-headbar">
      <DocBreadcrumbs {...props} />
      <PageActions editUrl={metadata.editUrl} />
    </div>
  );
}
