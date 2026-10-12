/**
 * Mobile site, server half: routes, navigation data and the device gate.
 *
 * Phones get their own pages under /m/ (and /en/m/). There is no second docs plugin and no copy of the
 * content: for every document the docs plugin loaded, this plugin registers one more route that renders the
 * very same compiled MDX module inside the mobile shell (src/mobile). The chrome, navigation and gestures are
 * different components, not the desktop ones squeezed by breakpoints.
 *
 * It also exports the navigation tree and the desktop/mobile path pairs as plugin global data and injects
 * the device-detection script that sends phones to /m/ and everyone else back (see buildGate).
 *
 * SEO follows Google's "separate URLs" pattern: desktop pages declare rel=alternate for small screens, mobile
 * pages declare rel=canonical to the desktop page and noindex, /m/ is left out of the sitemap, and crawlers
 * are never redirected.
 */

const PLUGIN_NAME = 'coco-mobile-routes';

function stripBases(pathname, siteBase, locale, defaultLocale) {
  let rest = pathname.replace(/^\/+/, '');
  const site = siteBase.replace(/^\/+|\/+$/g, '');
  if (site && (rest === site || rest.startsWith(`${site}/`))) {
    rest = rest.slice(site.length).replace(/^\/+/, '');
  }
  if (locale !== defaultLocale && (rest === locale || rest.startsWith(`${locale}/`))) {
    rest = rest.slice(locale.length).replace(/^\/+/, '');
  }
  return rest.replace(/\/+$/, '');
}

function buildGate({localeBase, docs}) {
  // ES5 on purpose: this runs inline in <head>, before the bundle, on every device.
  // An explicit choice (?view=mobile|desktop, remembered in localStorage) always wins; crawlers and
  // automation are never redirected otherwise.
  return `(function(){try{
var LB=${JSON.stringify(localeBase)},DOCS=${JSON.stringify(docs)},w=window,loc=w.location,doc=document;
function store(){try{return w.localStorage}catch(e){return null}}
function pref(){var s=store();try{return s&&s.getItem('coco-view')}catch(e){return null}}
function setPref(v){var s=store();try{if(!s)return;if(v)s.setItem('coco-view',v);else s.removeItem('coco-view')}catch(e){}}
var q=/[?&]view=(mobile|desktop)(&|$)/.exec(loc.search);
if(q)setPref(q[1]);
function bot(){var n=navigator;return !!n.webdriver||/bot|crawl|spider|slurp|lighthouse|headless|prerender|facebookexternalhit/i.test(n.userAgent||'')}
function phone(){
var n=navigator,u=n.userAgentData;
if(u&&u.mobile===true)return true;
if(!w.matchMedia)return false;
var s=w.screen;
return Math.min(s.width,s.height)<700&&w.matchMedia('(pointer: coarse)').matches&&w.matchMedia('(hover: none)').matches}
function rel(p){
if(p.indexOf(LB)===0)p=p.slice(LB.length);else if(p+'/'===LB)p='';
return p.replace(/^\\/+|\\/+$/g,'').replace(/\\.html$/,'')}
function isMobilePath(p){var r=rel(p);return r==='m'||r.indexOf('m/')===0}
function toMobile(p){var r=rel(p);if(isMobilePath(p))return null;if(r==='')return LB+'m';return DOCS.indexOf(r)>-1?LB+'m/'+r:null}
function toDesktop(p){var r=rel(p);if(r==='m')return LB;if(r.indexOf('m/')===0)return LB+r.slice(2);return null}
function wantsMobile(){var v=pref();if(v==='mobile')return true;if(v==='desktop')return false;if(bot())return null;return phone()}
function decide(p){
var m=wantsMobile();
if(m===null)return null;
return isMobilePath(p)?(m?null:toDesktop(p)):(m?toMobile(p):null)}
w.__cocoView={decide:decide,pref:pref,set:setPref,toMobile:toMobile,toDesktop:toDesktop,phone:phone};
if(isMobilePath(loc.pathname)){
doc.documentElement.setAttribute('data-coco-shell','mobile');
var mv=doc.querySelector('meta[name=viewport]');
if(mv)mv.setAttribute('content','width=device-width, initial-scale=1, viewport-fit=cover')}
var target=decide(loc.pathname);
if(target){
var search=loc.search.replace(/([?&])view=(mobile|desktop)&?/,'$1').replace(/[?&]$/,'');
loc.replace(target+search+loc.hash)}
}catch(e){}})();`;
}

module.exports = function mobileRoutes(context) {
  const {siteConfig, baseUrl: localeBase, i18n} = context;
  let docPaths = [];

  return {
    name: PLUGIN_NAME,

    async allContentLoaded({allContent, actions}) {
      const docsContent = allContent['docusaurus-plugin-content-docs']?.default;
      const version = docsContent?.loadedVersions?.find(v => v.isLast) ?? docsContent?.loadedVersions?.[0];
      if (!version) {
        return;
      }
      const rel = permalink => stripBases(permalink, siteConfig.baseUrl, i18n.currentLocale, i18n.defaultLocale);
      // Paths without base URL are for <Link to>, which adds the localized base itself; routes need the full path.
      const mobilePath = relative => (relative ? `/m/${relative}` : '/m');
      const desktopPath = relative => `/${relative}`;
      const routePath = relative => `${localeBase.replace(/\/+$/, '')}${mobilePath(relative)}`;

      const docs = version.docs.map(doc => {
        const relative = rel(doc.permalink);
        const link = permalink => (permalink ? {title: null, mobilePath: mobilePath(rel(permalink))} : null);
        return {
          id: doc.id,
          rel: relative,
          title: doc.title,
          sidebar: doc.sidebar ?? null,
          desktopPath: desktopPath(relative),
          mobilePath: mobilePath(relative),
          previous: doc.previous ? {...link(doc.previous.permalink), title: doc.previous.title} : null,
          next: doc.next ? {...link(doc.next.permalink), title: doc.next.title} : null,
        };
      });
      const byId = new Map(docs.map(doc => [doc.id, doc]));

      const convert = item => {
        if (item.type === 'doc' || item.type === 'ref') {
          const doc = byId.get(item.id);
          return doc ? {type: 'doc', title: item.label ?? doc.title, mobilePath: doc.mobilePath} : null;
        }
        if (item.type === 'category') {
          const linked = item.link?.type === 'doc' ? byId.get(item.link.id) : null;
          const items = (item.items ?? []).map(convert).filter(Boolean);
          return {type: 'category', title: item.label, mobilePath: linked ? linked.mobilePath : null, items};
        }
        return null;
      };
      const nav = Object.entries(version.sidebars ?? {}).map(([id, items]) => ({
        id,
        items: (items ?? []).map(convert).filter(Boolean),
      }));

      // One mobile route per document, rendering the same compiled MDX module as the desktop route.
      for (const doc of version.docs) {
        actions.addRoute({
          path: routePath(rel(doc.permalink)),
          component: '@site/src/mobile/MobileDocRoute',
          exact: true,
          modules: {content: doc.source},
        });
      }
      actions.addRoute({path: routePath(''), component: '@site/src/mobile/MobileHome', exact: true});

      actions.setGlobalData({docs, nav});
      docPaths = docs.map(doc => doc.rel);
    },

    injectHtmlTags() {
      return {
        headTags: [{tagName: 'script', innerHTML: buildGate({localeBase, docs: docPaths})}],
      };
    },
  };
};

module.exports.PLUGIN_NAME = PLUGIN_NAME;
