"""Generate one 1200x630 share card per documentation page, per locale.

Run: python scripts/generate-social-cards.py --font C:/Windows/Fonts/msyhbd.ttc [--serif C:/Windows/Fonts/georgia.ttf]

Titles and descriptions come from each page's front matter (zh) and its i18n copy (en), so a card cannot drift from
the page it previews. Output: static/img/social/<locale>/<doc path>.png, referenced by src/theme/DocItem/Metadata.
Fonts are used locally for rasterization only; the PNGs are committed.
"""
import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument("--font", required=True, help="Path to a Chinese-capable font")
parser.add_argument("--serif", default="C:/Windows/Fonts/georgia.ttf", help="Latin serif for English titles")
args = parser.parse_args()

site = Path(__file__).resolve().parents[1]
mark = Image.open(site / "static/img/brand/logo-256.png").convert("RGBA").resize((104, 104), Image.LANCZOS)
cream, ink, accent, muted, rule = "#faf6f0", "#2b1d16", "#a5432a", "#6a5042", "#d9c7b9"
W, H = 1200, 630
SOURCES = {"zh": site / "docs", "en": site / "i18n/en/docusaurus-plugin-content-docs/current"}


def front_matter(path):
    text = path.read_text(encoding="utf-8")
    match = re.match(r"---\r?\n(.*?)\r?\n---", text, re.S)
    block = match.group(1) if match else ""

    def field(name):
        found = re.search(rf'^{name}:\s*"?(.*?)"?\s*$', block, re.M)
        return found.group(1) if found else ""

    return field("title"), field("description"), field("slug")


def wrap(draw, text, font, width):
    """Greedy wrap that breaks between CJK characters and between Latin words."""
    lines, line = [], ""
    for token in re.findall(r"[\u3000-\u9fff\uff00-\uffef]|\S+\s*", text):
        if line and draw.textlength(line + token, font=font) > width:
            lines.append(line.rstrip())
            line = ""
        line += token
    if line:
        lines.append(line.rstrip())
    return lines


def card(locale, title, description):
    im = Image.new("RGB", (W, H), cream)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 14, H), fill=accent)
    im.paste(mark, (60, 58), mark)
    label = ImageFont.truetype(args.font, 25)
    d.text((190, 80), "COCO / FRAMEWORK", fill=accent, font=label)
    face = args.serif if locale == "en" else args.font
    size = 68 if locale == "en" else 64
    title_font = ImageFont.truetype(face, size)
    lines = wrap(d, title, title_font, W - 150)
    if len(lines) > 1:  # a two-line title gets a smaller face so the description keeps three lines above the rule
        size = 54 if locale == "en" else 52
        title_font = ImageFont.truetype(face, size)
        lines = wrap(d, title, title_font, W - 150)
    body = ImageFont.truetype(args.font, 28 if len(lines) > 1 else 30)
    y = 190
    for line in lines[:2]:
        d.text((64, y), line, fill=ink, font=title_font)
        y += round(size * 1.3)
    y += 14
    for line in wrap(d, description, body, W - 150)[:3]:
        d.text((66, y), line, fill=muted, font=body)
        y += 42
    d.line((64, H - 98, W - 64, H - 98), fill=rule, width=2)
    foot = ImageFont.truetype(args.font, 24)
    d.text((66, H - 70), "Spring Boot  /  One starter  /  Plain Java", fill=muted, font=foot)
    url = "cocoframework.dev"
    d.text((W - 64 - d.textlength(url, font=foot), H - 70), url, fill=accent, font=foot)
    return im.quantize(colors=96, method=Image.MEDIANCUT, dither=Image.NONE)


count = 0
for locale, root in SOURCES.items():
    for source in sorted(root.rglob("*.md")):
        title, description, _ = front_matter(source)
        if not title or not description:
            description = description or title
        target = site / "static/img/social" / locale / source.relative_to(root).with_suffix(".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        card(locale, title, description).save(target, optimize=True)
        count += 1
print(f"{count} social cards in {site / 'static/img/social'}")
