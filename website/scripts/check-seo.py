"""Audit generated HTML, sitemaps, and local resources. Python standard library only."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse, unquote
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "build"
errors = []

class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.meta, self.links, self.resources, self.schemas = {}, [], [], []
        self.h1 = 0
        self.in_schema = False
        self.schema = ""
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "meta":
            key = a.get("name", a.get("property"))
            if key:
                self.meta.setdefault(key, []).append(a.get("content", ""))
        if tag == "link":
            self.links.append(a)
            if a.get("rel") in ["stylesheet", "icon", "apple-touch-icon"]:
                self.resources.append(a.get("href", ""))
        if tag in ["script", "img"] and "src" in a:
            self.resources.append(a["src"])
        if tag == "h1":
            self.h1 += 1
        if tag == "script" and a.get("type") == "application/ld+json":
            self.in_schema, self.schema = True, ""
    def handle_data(self, data):
        if self.in_schema:
            self.schema += data
    def handle_endtag(self, tag):
        if tag == "script" and self.in_schema:
            self.schemas.append(json.loads(self.schema))
            self.in_schema = False

pages = [p for p in root.rglob("*.html") if p.name != "404.html"]
canonicals = set()
for p in pages:
    page = Page()
    page.feed(p.read_text(encoding="utf-8"))
    def check(ok, message):
        if not ok:
            errors.append(f"{p.relative_to(root)}: {message}")
    check(page.h1 == 1, f"expected one h1, got {page.h1}")
    for key in ["description", "og:title", "og:description", "og:image", "og:url", "og:type", "og:site_name", "twitter:card", "twitter:title", "twitter:description", "twitter:image", "keywords"]:
        check(len(page.meta.get(key, [])) == 1 and bool(page.meta[key][0].strip()), f"missing/duplicate {key}")
    canonical = [a["href"] for a in page.links if a.get("rel") == "canonical"]
    check(len(canonical) == 1, "canonical missing/duplicate")
    if len(canonical) == 1:
        check(canonical[0] not in canonicals, "non-unique canonical")
        check(canonical[0].startswith("https://cocoframwork.dev/"), "wrong canonical host")
        canonicals.add(canonical[0])
    languages = {a.get("hreflang") for a in page.links if a.get("rel") == "alternate"}
    check({"en", "zh-Hans"}.issubset(languages), "missing language alternates")
    for resource in page.resources:
        if resource.startswith("/"):
            check((root / unquote(urlparse(resource).path.lstrip("/"))).is_file(), f"missing resource {resource}")
        elif resource.startswith("http"):
            check(False, f"external page-load dependency {resource}")
    if p.relative_to(root).as_posix() in ["index.html", "en/index.html"]:
        check(any("SoftwareApplication" in s.get("@type", []) and s.get("programmingLanguage") == "Java" for s in page.schemas), "missing software schema")
for sitemap in [root / "sitemap.xml", root / "en/sitemap.xml"]:
    doc = ET.parse(sitemap)
    for node in doc.findall(".//{*}loc"):
        if node.text not in canonicals:
            errors.append(f"sitemap URL missing canonical: {node.text}")
robots = (root / "robots.txt").read_text()
for url in ["https://cocoframwork.dev/sitemap.xml", "https://cocoframwork.dev/en/sitemap.xml"]:
    if f"Sitemap: {url}" not in robots:
        errors.append(f"robots missing {url}")
print(json.dumps({"pages": len(pages), "uniqueCanonicals": len(canonicals), "errors": errors}, ensure_ascii=False, indent=2))
raise SystemExit(bool(errors))
