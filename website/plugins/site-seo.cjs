const {writeFile} = require('node:fs/promises');
const path = require('node:path');

// Docusaurus already produces sitemap/canonical/hreflang. Derive robots from
// the actual build URL so GitHub Pages and the custom domain remain valid.
module.exports = function siteSeo() {
  return {
    name: 'coco-site-seo',
    async postBuild({outDir, siteConfig, i18n}) {
      if (i18n.currentLocale !== i18n.defaultLocale) {
        return;
      }
      const sitemap = new URL(`${siteConfig.baseUrl}sitemap.xml`, siteConfig.url);
      const extra = i18n.locales
        .filter(locale => locale !== i18n.defaultLocale)
        .map(locale => `Sitemap: ${new URL(`${siteConfig.baseUrl}${locale}/sitemap.xml`, siteConfig.url)}`)
        .join('\n');
      const lines = [
        'User-agent: *',
        'Allow: /',
        '',
        `Sitemap: ${sitemap}`,
      ];
      if (extra) {
        lines.push(extra);
      }
      lines.push('');
      await writeFile(path.join(outDir, 'robots.txt'), lines.join('\n'));
    },
  };
};
