"""Generate Coco brand PNG/ICO assets using Pillow.

Run: python scripts/generate-brand.py --font C:/Windows/Fonts/msyhbd.ttc
Font is used locally for rasterization; font files are not redistributed.
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from html import escape

parser = argparse.ArgumentParser()
parser.add_argument("--font", required=True, help="Path to a Chinese-capable font")
parser.add_argument("--headline", default="少写基础设施，多写业务。")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / "static" / "img"
out = root / "brand"
out.mkdir(exist_ok=True)
cream, ink, accent, muted = "#faf6f0", "#2b1d16", "#b4441f", "#6a5042"

def font(size):
    return ImageFont.truetype(args.font, size)

def mark(size=512):
    im = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(im)
    p = size / 32
    d.rounded_rectangle((2*p, 2*p, 30*p, 30*p), radius=9*p, fill=accent)
    d.arc((9*p, 9*p, 23*p, 23*p), 45, 315, fill=cream, width=round(3*p))
    d.ellipse((14*p, 14*p, 18*p, 18*p), fill=cream)
    return im

svg_body = '<rect x="2" y="2" width="28" height="28" rx="9" fill="#b4441f"/><path d="M21 11a7.07 7.07 0 1 0 0 10" fill="none" stroke="#faf6f0" stroke-width="3" stroke-linecap="round"/><circle cx="16" cy="16" r="2" fill="#faf6f0"/>'
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><title>Coco Framework</title>{svg_body}</svg>\n'
(root / "logo.svg").write_text(svg, encoding="utf-8")
(out / "logo.svg").write_text(svg, encoding="utf-8")
for name, color in [("white", "#ffffff"), ("black", "#000000"), ("primary", accent)]:
    mono = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><title>Coco Framework</title><path d="M24 8a11.3 11.3 0 1 0 0 16" fill="none" stroke="{color}" stroke-width="4" stroke-linecap="round"/><circle cx="16" cy="16" r="3" fill="{color}"/></svg>\n'
    (out / f"logo-{name}.svg").write_text(mono, encoding="utf-8")
for size in [16, 32, 48, 64, 128, 180, 256, 512]:
    mark(1024).resize((size, size), Image.Resampling.LANCZOS).save(out / f"logo-{size}.png")
mark(1024).resize((180, 180), Image.Resampling.LANCZOS).save(out / "apple-touch-icon.png")
mark(256).save(root / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
for name, w, h, x, y, tx, ty, ts in [
    ("logo-horizontal", 860, 180, 10, 10, 200, 54, 48),
    ("logo-stacked", 640, 400, 240, 20, 79, 238, 42),
]:
    im = Image.new("RGBA", (w, h), cream)
    im.alpha_composite(mark(160), (x, y))
    ImageDraw.Draw(im).text((tx, ty), "Coco Framework", font=font(ts), fill=ink)
    im.save(out / f"{name}.png")
    vector = f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="{w}" height="{h}" fill="{cream}"/><g transform="translate({x} {y}) scale(5)">{svg_body}</g><text x="{tx}" y="{ty+ts}" font-family="Bricolage Grotesque Variable, sans-serif" font-size="{ts}" font-weight="700" fill="{ink}">Coco Framework</text></svg>\n'
    (out / f"{name}.svg").write_text(vector, encoding="utf-8")

for name, w, h in [("social-card", 1200, 630), ("github-social-preview", 1280, 640)]:
    im = Image.new("RGB", (w, h), cream)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 14, h), fill=accent)
    d.line((64, h-122, w-64, h-122), fill="#d9c7b9", width=2)
    im.paste(mark(104), (60, 58), mark(104))
    d.text((190, 80), "COCO / FRAMEWORK", fill=accent, font=font(25))
    d.text((64, 190), "Coco Framework", fill=ink, font=font(73))
    d.text((66, 306), args.headline, fill=accent, font=font(43))
    d.text((66, 387), "Spring Boot  /  One starter  /  Plain Java", fill=muted, font=font(24))
    d.text((66, h-89), "17 feature IDs on main", fill=ink, font=font(22))
    d.text((433, h-89), "Apache-2.0", fill=ink, font=font(22))
    d.text((w-332, h-89), "cocoframwork.dev", fill=accent, font=font(22))
    im.save(out / f"{name}.png", optimize=True)
    (out / f"{name}.svg").write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="{w}" height="{h}" fill="{cream}"/><rect width="14" height="{h}" fill="{accent}"/><g transform="translate(60 58) scale(3.25)">{svg_body}</g><g font-family="Bricolage Grotesque Variable, Microsoft YaHei, sans-serif"><text x="190" y="115" font-size="25" fill="{accent}">COCO / FRAMEWORK</text><text x="64" y="270" font-size="73" font-weight="700" fill="{ink}">Coco Framework</text><text x="66" y="355" font-size="43" fill="{accent}">{escape(args.headline)}</text><text x="66" y="422" font-size="24" fill="{muted}">Spring Boot / One starter / Plain Java</text><text x="66" y="{h-55}" font-size="22" fill="{ink}">17 feature IDs on main · Apache-2.0 · cocoframwork.dev</text></g></svg>\n', encoding="utf-8")
print(f"Brand assets generated in {out}")
