"""Builds the Google Play graphics from raw emulator screenshots.

Usage: python make_graphics.py <raw screenshot dir> <output dir>
Needs Pillow. Fonts come from macOS (/System/Library/Fonts/Supplemental).

Outputs:
  icon-512.png             512x512 store icon (redrawn from the adaptive icon vectors)
  feature-graphic.png      1024x500
  phone-N-<name>.png       1080x1920 captioned screenshots (Play needs the long side <= 2x the short side)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_DIR = Path("/System/Library/Fonts/Supplemental")
BOLD = str(FONT_DIR / "Arial Bold.ttf")
REGULAR = str(FONT_DIR / "Arial.ttf")

BG_TOP = (0x34, 0x2A, 0x25)
BG_BOTTOM = (0x1A, 0x15, 0x12)
TEXT = (0xF3, 0xEC, 0xE7)
TEXT_SECONDARY = (0xAB, 0xA1, 0x9C)
ACCENT = (0xF3, 0xB4, 0x64)
TERRACOTTA = (0xD9, 0x6B, 0x43)
TERRACOTTA_LIGHT = (0xE8, 0x8A, 0x63)

# (raw file, output name, caption) in store order
SCREENS = [
    ("raw_player.png", "player", "Pick up right where\nyou left off"),
    ("raw_shelf.png", "bookshelf", "Your whole library\nat a glance"),
    ("raw_sleep.png", "sleep-timer", "A sleep timer that knows\nwhere you drifted off"),
    ("raw_bookmarks.png", "bookmarks", "Bookmarks with notes,\nsearchable and shareable"),
    ("raw_chapters.png", "chapters", "Jump to any chapter"),
    ("raw_details.png", "details", "Tidy up titles, authors\nand narrators"),
    ("raw_player_dark.png", "dark", "Easy on the eyes\nat bedtime"),
    ("raw_settings.png", "settings", "Tuned to how you listen"),
]


def gradient(size, top, bottom, diagonal=False):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / w + y / h) / 2) if diagonal else y / h
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
    return img


def cubic(p0, p1, p2, p3, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        pts.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    return pts


def draw_glyph(draw, origin, scale):
    """Draws ic_launcher_foreground (108x108 viewport) at origin with the given scale."""
    ox, oy = origin

    def m(pts):
        return [(ox + x * scale, oy + y * scale) for x, y in pts]

    left = [(53, 49)] + cubic((53, 49), (47.5, 45.5), (39.5, 44.5), (32, 46))[1:] + [(32, 69)] \
        + cubic((32, 69), (39.5, 67.5), (47.5, 68.5), (53, 72))[1:]
    right = [(55, 49)] + cubic((55, 49), (60.5, 45.5), (68.5, 44.5), (76, 46))[1:] + [(76, 69)] \
        + cubic((76, 69), (68.5, 67.5), (60.5, 68.5), (55, 72))[1:]
    draw.polygon(m(left), fill=TERRACOTTA)
    draw.polygon(m(right), fill=TERRACOTTA_LIGHT)

    base = cubic((31, 73), (40, 71), (48, 72), (54, 76)) + cubic((54, 76), (60, 72), (68, 71), (77, 73))[1:]
    width = 2.5 * scale
    draw.line(m(base), fill=TERRACOTTA, width=round(width), joint="curve")
    for x, y in (base[0], base[-1]):
        cx, cy = ox + x * scale, oy + y * scale
        draw.ellipse([cx - width / 2, cy - width / 2, cx + width / 2, cy + width / 2], fill=TERRACOTTA)

    bar = 3.5 * scale
    for x, top in ((42, 35), (48, 30), (54, 25), (60, 30), (66, 35)):
        x0, y0, y1 = ox + x * scale, oy + top * scale, oy + 40 * scale
        draw.rounded_rectangle([x0 - bar / 2, y0 - bar / 2, x0 + bar / 2, y1 + bar / 2], radius=bar / 2, fill=ACCENT)


def supersampled(size, painter, factor=4):
    big = painter((size[0] * factor, size[1] * factor), factor)
    return big.resize(size, Image.LANCZOS)


def make_icon(out):
    def paint(size, f):
        img = gradient(size, BG_TOP, BG_BOTTOM, diagonal=True)
        # Crop the 108dp adaptive canvas to its central 72dp so the glyph fills the store icon
        scale = size[0] / 72
        draw_glyph(ImageDraw.Draw(img), (-18 * scale, -18 * scale), scale)
        return img

    supersampled((512, 512), paint).save(out / "icon-512.png", optimize=True)


def make_feature(out):
    def paint(size, f):
        img = gradient(size, BG_TOP, BG_BOTTOM, diagonal=True)
        d = ImageDraw.Draw(img)
        # Glyph content spans x 31..77 and y 25..76 of the 108 viewport
        scale = 6 * f
        draw_glyph(d, (80 * f - 31 * scale, size[1] / 2 - 50.5 * scale), scale)
        tx = 440 * f
        d.text((tx, 170 * f), "Better Audiobook", font=ImageFont.truetype(BOLD, 58 * f), fill=TEXT)
        d.text((tx, 238 * f), "Player", font=ImageFont.truetype(BOLD, 58 * f), fill=TEXT)
        d.text((tx, 322 * f), "Your audiobooks. Your phone.\nNo account, no ads.", font=ImageFont.truetype(REGULAR, 28 * f),
               fill=TEXT_SECONDARY, spacing=10 * f)
        return img

    supersampled((1024, 500), paint, factor=2).save(out / "feature-graphic.png", optimize=True)


def make_screens(raw, out):
    W, H = 1080, 1920
    caption_font = ImageFont.truetype(BOLD, 64)
    for i, (name, slug, caption) in enumerate(SCREENS, start=1):
        src = raw / name
        if not src.exists():
            print(f"skip {name} (missing)")
            continue
        canvas = gradient((W, H), BG_TOP, BG_BOTTOM)
        d = ImageDraw.Draw(canvas)
        d.multiline_text((W / 2, 150), caption, font=caption_font, fill=TEXT, anchor="mm", align="center", spacing=16)

        shot = Image.open(src).convert("RGB")
        top = 300
        sh = H - top - 60
        sw = round(shot.width * sh / shot.height)
        shot = shot.resize((sw, sh), Image.LANCZOS)
        radius = 44
        mask = Image.new("L", (sw, sh), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], radius=radius, fill=255)

        x = (W - sw) // 2
        shadow = Image.new("L", (W, H), 0)
        ImageDraw.Draw(shadow).rounded_rectangle([x, top + 12, x + sw, top + sh + 12], radius=radius, fill=150)
        canvas.paste((0, 0, 0), (0, 0), shadow.filter(ImageFilter.GaussianBlur(24)))
        canvas.paste(shot, (x, top), mask)
        ImageDraw.Draw(canvas).rounded_rectangle([x, top, x + sw - 1, top + sh - 1], radius=radius, outline=(0x5A, 0x4E, 0x48), width=3)
        canvas.save(out / f"phone-{i}-{slug}.png", optimize=True)


def main():
    raw, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    make_icon(out)
    make_feature(out)
    make_screens(raw, out)
    print("\n".join(sorted(p.name for p in out.iterdir())))


if __name__ == "__main__":
    main()
