import {useMemo} from 'react';
import {usePluginData} from '@docusaurus/useGlobalData';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {stringsFor, type Strings} from './strings';

export interface DocLink { title: string; mobilePath: string }
export interface MobileDoc {
  id: string;
  rel: string;
  title: string;
  sidebar: string | null;
  desktopPath: string;
  mobilePath: string;
  previous: DocLink | null;
  next: DocLink | null;
}
export type NavItem =
  | {type: 'doc'; title: string; mobilePath: string}
  | {type: 'category'; title: string; mobilePath: string | null; items: NavItem[]};
export interface MobileData { docs: MobileDoc[]; nav: {id: string; items: NavItem[]}[] }

export interface Mobile extends MobileData {
  locale: 'zh' | 'en';
  t: Strings;
  /** The doc (if any) that owns this mobile path. */
  docFor: (mobilePath: string) => MobileDoc | undefined;
}

/** Plugin data (coco-mobile-routes) plus the locale strings, memoised for the shell. */
export function useMobile(): Mobile {
  const data = usePluginData('coco-mobile-routes') as MobileData | undefined;
  const {i18n} = useDocusaurusContext();
  return useMemo(() => {
    const docs = data?.docs ?? [];
    const locale = i18n.currentLocale === 'en' ? 'en' : 'zh';
    return {
      docs,
      nav: data?.nav ?? [],
      locale,
      t: stringsFor(locale),
      docFor: (mobilePath: string) => docs.find(doc => doc.mobilePath === mobilePath.replace(/\/+$/, '')),
    };
  }, [data, i18n.currentLocale]);
}

/** The section tab a document belongs to. */
export type Section = 'home' | 'docs' | 'skills' | 'releases';
export const sectionOf = (doc: MobileDoc | undefined): Section => {
  if (!doc) return 'home';
  if (doc.sidebar === 'skillsSidebar') return 'skills';
  if (doc.sidebar === 'releasesSidebar') return 'releases';
  return 'docs';
};
