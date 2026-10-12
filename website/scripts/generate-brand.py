"""Generate the Coco brand assets: two coffee beans, one standing and one lying beside it.

Run: python scripts/generate-brand.py --font C:/Windows/Fonts/msyhbd.ttc [--serif C:/Windows/Fonts/georgia.ttf]

Every asset is derived from the single mark definition below, so the SVG tiles, monochrome variants, PNG sizes,
favicon.ico, wordmarks and share cards cannot drift apart. Fonts are used locally for rasterization only.
"""
import argparse
from html import escape
from math import cos, radians, sin
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument("--font", required=True, help="Path to a Chinese-capable font")
parser.add_argument("--serif", default="C:/Windows/Fonts/georgia.ttf", help="Latin serif for the wordmark")
parser.add_argument("--headline", default="少写基础设施，多写业务。")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / "static" / "img"
out = root / "brand"
out.mkdir(exist_ok=True)
cream, ink, accent, muted, tint = "#faf6f0", "#2b1d16", "#a5432a", "#6a5042", "#f1c4b3"

# Mark geometry in a 32 x 32 box: (cx, cy, rx, ry, rotation) and the crease as a cubic curve in bean space.
STAND = (21.6, 18.4, 6.6, 10.2, 16)
LIE = (10.0, 25.2, 7.6, 4.9, -8)
CREASE_STAND = ((-0.9, -8.4), (-4.6, -3.4), (4.6, 3.4), (0.9, 8.4), 1.7)
CREASE_LIE = ((-6.2, 0.9), (-2.6, -3.4), (2.6, 3.4), (6.2, -0.9), 1.6)
FIT_SHIFT, FIT_SCALE = (-1.0, -4.8), 1.1  # centres the pair in the box


def to_box(bean, x, y, shrink, scale):
    cx, cy, _, _, rot = bean
    r = radians(rot)
    px, py = cx + x * cos(r) - y * sin(r), cy + x * sin(r) + y * cos(r)
    px = ((px * FIT_SCALE + FIT_SHIFT[0]) - 16) * shrink + 16
    py = ((py * FIT_SCALE + FIT_SHIFT[1]) - 16) * shrink + 16
    return px * scale, py * scale


def bean_outline(bean, shrink, scale, steps=96):
    rx, ry = bean[2], bean[3]
    return [to_box(bean, rx * cos(radians(i * 360 / steps)), ry * sin(radians(i * 360 / steps)), shrink, scale) for i in range(steps)]


def crease_path(bean, crease, shrink, scale, steps=48):
    p0, p1, p2, p3, width = crease
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
        pts.append(to_box(bean, x, y, shrink, scale))
    return pts, width * FIT_SCALE * shrink * scale


def draw_beans(size, stand_fill, lie_fill, shrink=1.0):
    """Supersampled 4x. Creases are cut out of the beans through an alpha mask so the ground shows through."""
    ss = 4
    scale = size * ss / 32
    layer = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon(bean_outline(STAND, shrink, scale), fill=stand_fill)
    d.polygon(bean_outline(LIE, shrink, scale), fill=lie_fill)
    mask = Image.new("L", layer.size, 255)
    md = ImageDraw.Draw(mask)
    for bean, crease in ((STAND, CREASE_STAND), (LIE, CREASE_LIE)):
        pts, width = crease_path(bean, crease, shrink, scale)
        md.line(pts, fill=0, width=max(1, round(width)), joint="curve")
        for x, y in (pts[0], pts[-1]):
            md.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=0)
    alpha = Image.composite(layer.getchannel("A"), Image.new("L", layer.size, 0), mask)
    layer.putalpha(alpha)
    return layer.resize((size, size), Image.Resampling.LANCZOS)


def mark(size=512, bg=accent, stand=cream, lie=tint, radius=7, shrink=0.84):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if bg:
        ImageDraw.Draw(im).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius * size / 32, fill=bg)
    im.alpha_composite(draw_beans(size, stand, lie, shrink))
    return im


# ---- Vector -----------------------------------------------------------------------------------------------
MASK = (
    '<defs><mask id="cut" maskUnits="userSpaceOnUse" x="0" y="0" width="32" height="32"><rect width="32" height="32" fill="#fff"/>'
    '<g transform="translate(21.6 18.4) rotate(16)"><path d="M-0.9,-8.4 C-4.6,-3.4 4.6,3.4 0.9,8.4" fill="none" stroke="#000" stroke-width="1.7" stroke-linecap="round"/></g>'
    '<g transform="translate(10 25.2) rotate(-8)"><path d="M-6.2,0.9 C-2.6,-3.4 2.6,3.4 6.2,-0.9" fill="none" stroke="#000" stroke-width="1.6" stroke-linecap="round"/></g></mask></defs>'
)


def beans_svg(stand, lie, shrink=1.0, mask_id="cut"):
    t = f"translate(16 16) scale({shrink}) translate(-16 -16) translate(-1 -4.8) scale(1.1)"
    return (
        MASK.replace('id="cut"', f'id="{mask_id}"')
        + f'<g transform="{t}"><g mask="url(#{mask_id})">'
        f'<ellipse {stand} rx="6.6" ry="10.2" transform="translate(21.6 18.4) rotate(16)"/>'
        f'<ellipse {lie} rx="7.6" ry="4.9" transform="translate(10 25.2) rotate(-8)"/></g></g>'
    )


def fill(color):
    return f'fill="{color}"'


def svg(inner, w=32, h=32, vb="0 0 32 32"):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}"><title>Coco Framework</title>{inner}</svg>\n'


def tile(bg, stand, lie):
    return f'<rect width="32" height="32" rx="7" fill="{bg}"/>' + beans_svg(fill(stand), fill(lie), 0.84)


def write(path, text):
    path.write_text(text, encoding="utf-8")


write(root / "logo.svg", svg(tile(accent, cream, tint)))
write(out / "logo.svg", svg(tile(accent, cream, tint)))
write(out / "mark-clay.svg", svg(tile(accent, cream, tint)))
write(out / "mark-clay-dark.svg", svg(tile("#d97757", "#1f1e1d", "#5a2a1a")))
write(
    out / "favicon.svg",
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><title>Coco Framework</title>'
    "<style>.s{fill:#a5432a}.l{fill:#c96442}@media (prefers-color-scheme:dark){.s{fill:#e38c6c}.l{fill:#f0a88c}}</style>"
    + beans_svg('class="s"', 'class="l"')
    + "</svg>\n",
)
for name, color in [("white", "#ffffff"), ("black", "#000000"), ("primary", accent)]:
    write(out / f"logo-{name}.svg", svg(beans_svg(fill(color), fill(color))))

# ---- Raster -----------------------------------------------------------------------------------------------
for size in [16, 32, 48, 64, 128, 180, 256, 512]:
    mark(size).save(out / f"logo-{size}.png")
mark(180, radius=0).convert("RGB").save(out / "apple-touch-icon.png")
mark(512).save(out / "logo-mark.png")
mark(256).save(root / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])


def serif(size):
    return ImageFont.truetype(args.serif, size)


def sans(size):
    return ImageFont.truetype(args.font, size)


def wordmark(name, w, h, mark_x, mark_y, mark_size, text_x, text_y, text_size, dark):
    bg, fg = ("#1f1e1d", "#faf9f5") if dark else (cream, ink)
    im = Image.new("RGBA", (w, h), bg)
    im.alpha_composite(mark(mark_size), (mark_x, mark_y))
    ImageDraw.Draw(im).text((text_x, text_y), "Coco Framework", font=serif(text_size), fill=fg)
    im.save(out / f"{name}.png")


wordmark("logo-horizontal", 860, 180, 14, 10, 160, 200, 52, 64, False)
wordmark("logo-horizontal-light", 860, 180, 14, 10, 160, 200, 52, 64, False)
wordmark("logo-horizontal-dark", 860, 180, 14, 10, 160, 200, 52, 64, True)
wordmark("logo-stacked", 640, 400, 240, 20, 160, 118, 250, 64, False)

for name, w, h, mx, my, msize, tx, ty, ts in [
    ("logo-horizontal", 860, 180, 14, 10, 160, 200, 52, 64),
    ("logo-stacked", 640, 400, 240, 20, 160, 118, 250, 64),
]:
    inner = svg(tile(accent, cream, tint)).split("</title>")[1].rsplit("</svg>", 1)[0]
    write(
        out / f"{name}.svg",
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="{w}" height="{h}" fill="{cream}"/>'
        f'<g transform="translate({mx} {my}) scale({msize / 32})">{inner}</g>'
        f'<text x="{tx}" y="{ty + ts}" font-family="Newsreader Variable, Georgia, serif" font-size="{ts}" font-weight="500" fill="{ink}">Coco Framework</text></svg>\n',
    )

for name, w, h in [("social-card", 1200, 630), ("github-social-preview", 1280, 640)]:
    im = Image.new("RGBA", (w, h), cream)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 14, h), fill=accent)
    d.line((64, h - 122, w - 64, h - 122), fill="#d9c7b9", width=2)
    im.alpha_composite(mark(104), (60, 58))
    d.text((190, 80), "COCO / FRAMEWORK", fill=accent, font=sans(25))
    d.text((64, 170), "Coco Framework", fill=ink, font=serif(84))
    d.text((66, 306), args.headline, fill=accent, font=sans(43))
    d.text((66, 387), "Spring Boot  /  One starter  /  Plain Java", fill=muted, font=sans(24))
    d.text((66, h - 89), "17 feature IDs on main", fill=ink, font=sans(22))
    d.text((433, h - 89), "Apache-2.0", fill=ink, font=sans(22))
    d.text((w - 332, h - 89), "cocoframework.dev", fill=accent, font=sans(22))
    im.convert("RGB").save(out / f"{name}.png", optimize=True)
    inner = svg(tile(accent, cream, tint)).split("</title>")[1].rsplit("</svg>", 1)[0]
    write(
        out / f"{name}.svg",
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="{w}" height="{h}" fill="{cream}"/><rect width="14" height="{h}" fill="{accent}"/>'
        f'<g transform="translate(60 58) scale(3.25)">{inner}</g>'
        f'<g font-family="Microsoft YaHei, sans-serif"><text x="190" y="115" font-size="25" fill="{accent}">COCO / FRAMEWORK</text>'
        f'<text x="64" y="250" font-size="84" font-family="Newsreader Variable, Georgia, serif" font-weight="500" fill="{ink}">Coco Framework</text>'
        f'<text x="66" y="355" font-size="43" fill="{accent}">{escape(args.headline)}</text>'
        f'<text x="66" y="422" font-size="24" fill="{muted}">Spring Boot / One starter / Plain Java</text>'
        f'<text x="66" y="{h - 55}" font-size="22" fill="{ink}">17 feature IDs on main · Apache-2.0 · cocoframework.dev</text></g></svg>\n',
    )
print(f"Brand assets generated in {out}")
