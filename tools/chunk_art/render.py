# -*- coding: utf-8 -*-
"""Renders the ore-chunk item textures from the silhouette library in forms.py.

The point of the generator is consistency: one lighting model, one tone ramp
recipe and one outline rule for every chunk, so 100+ items read as a single set
the way the modern (JAPPA) vanilla item textures do, while the silhouette - which
is what a player actually recognises at 16x16 - stays unique per ore.

    python tools/chunk_art/render.py            # write the textures
    python tools/chunk_art/render.py --sheet    # also write a contact sheet
"""

import colorsys
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from forms import FORMS
from palette import ORES

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "fortuneores", "textures", "items")

BODY_CHARS = "#+,^-o*"
STONE_CHARS = "o"
ACCENT_CHARS = "*"

# Rock the ore sits in, for the forms that show a matrix rather than a clean lump.
STONE_BASE = "#8B8B8B"


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def ramp(base_hex, style):
    """Six tones, darkest first: outline, deep shadow, shadow, base, light, specular.

    Shadows shift towards blue and gain saturation, highlights shift towards yellow
    and lose it - the standard pixel-art hue ramp, which is what keeps a 16x16 item
    from looking like one flat colour with a black edge on it.
    """
    r, g, b = (c / 255.0 for c in hex_rgb(base_hex))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    # A near-black material would ramp into an unreadable silhouette, so it is lifted
    # first and its contrast is taken from the spread below rather than from the base.
    l = max(l, 0.22)

    spread = {
        "gem": (0.34, 0.56, 0.78, 1.00, 1.30, 1.62),
        "crystal": (0.34, 0.56, 0.78, 1.00, 1.28, 1.58),
        "glow": (0.42, 0.62, 0.82, 1.00, 1.24, 1.50),
        "metal": (0.32, 0.54, 0.77, 1.00, 1.24, 1.50),
        "rock": (0.36, 0.58, 0.80, 1.00, 1.18, 1.36),
        "organic": (0.34, 0.57, 0.79, 1.00, 1.20, 1.42),
    }[style]

    tones = []
    for i, factor in enumerate(spread):
        if factor <= 1.0:
            nl = l * factor
        else:
            nl = l + (1.0 - l) * (factor - 1.0)
        nl = min(0.97, max(0.045, nl))
        # Saturation rises into the shadows and falls out of the highlights.
        ns = min(1.0, s * (1.10 - 0.13 * i))
        nh = (h + (i - 3) * 0.007) % 1.0
        nr, ng, nb = colorsys.hls_to_rgb(nh, nl, ns)
        tones.append((int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255))))
    return tones


def jitter(name, x, y):
    """Deterministic per-pixel noise, so tone bands break up instead of banding."""
    h = hash((name, x, y, 0x9E3779B9)) & 0xFFFF
    return (h / 65535.0 - 0.5) * 0.16


def body_mask(rows):
    return [[rows[y][x] in BODY_CHARS for x in range(16)] for y in range(16)]


def depth_map(mask):
    """How many erosion steps a pixel survives; 0 on the silhouette border."""
    depth = [[0] * 16 for _ in range(16)]
    current = [row[:] for row in mask]
    step = 0
    while any(any(row) for row in current):
        nxt = [[False] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                if not current[y][x]:
                    continue
                inner = True
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < 16 and 0 <= ny < 16) or not current[ny][nx]:
                        inner = False
                        break
                if inner:
                    nxt[y][x] = True
                    depth[y][x] = step + 1
        current = nxt
        step += 1
        if step > 16:
            break
    return depth


def neighbours(mask, x, y):
    count = 0
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if 0 <= nx < 16 and 0 <= ny < 16 and mask[ny][nx]:
            count += 1
    return count


def render(name, form_name, base_hex, style, accent_hex=None):
    flip = form_name.endswith("^")
    rows = FORMS[form_name.rstrip("^")]
    if flip:
        rows = [r[::-1] for r in rows]

    mask = body_mask(rows)
    depth = depth_map(mask)
    tones = ramp(base_hex, style)
    stone = ramp(STONE_BASE, "rock")
    accent = ramp(accent_hex or base_hex, "glow")

    xs = [x for y in range(16) for x in range(16) if mask[y][x]]
    ys = [y for y in range(16) for x in range(16) if mask[y][x]]
    if not xs:
        raise ValueError("empty form " + form_name)
    cx, cy = (min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0
    rx = max(1.0, (max(xs) - min(xs)) / 2.0)
    ry = max(1.0, (max(ys) - min(ys)) / 2.0)

    # Gems and crystals read as translucent, so they keep more light in the lower
    # half; metal and rock fall off the way an opaque solid does.
    contrast = {"gem": 1.25, "crystal": 1.20, "glow": 0.85, "metal": 1.0, "rock": 0.88, "organic": 0.95}[style]
    lift = {"gem": 0.16, "crystal": 0.12, "glow": 0.22, "metal": 0.0, "rock": -0.04, "organic": 0.0}[style]

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    for y in range(16):
        for x in range(16):
            ch = rows[y][x]
            if ch not in BODY_CHARS:
                continue

            palette = stone if ch in STONE_CHARS else (accent if ch in ACCENT_CHARS else tones)

            light = (-(x - cx) / rx - (y - cy) / ry) / 2.0
            light = light * contrast + lift + jitter(name, x, y)
            d = depth[y][x]

            n = neighbours(mask, x, y)
            if n < 4:
                # The silhouette edge is the darkest tone, inset rather than grown, so a
                # 16x16 form never spills outside its cell. A feature only one or two
                # pixels wide is all edge, though - outlining it on every side would turn
                # a needle or a star point into a black stick, so those keep the shadow
                # tones and stay readable as part of the material.
                if n >= 3:
                    index = 0
                else:
                    index = 1 if light < 0.0 else 2
                px[x, y] = palette[index] + (255,)
                continue

            if ch == "-":
                px[x, y] = palette[1] + (255,)
                continue

            if light >= 0.52 and d >= 2:
                index = 5
            elif light >= 0.16:
                index = 4
            elif light > -0.22:
                index = 3
            elif light > -0.56:
                index = 2
            else:
                index = 1

            if ch == "+":
                index = min(5, index + 1)
            elif ch == ",":
                index = max(1, index - 1)
            elif ch == "^":
                index = 5
            elif ch == "*":
                index = 5 if d >= 1 else 4

            px[x, y] = palette[index] + (255,)

    return img


def main():
    written = []
    for name, (form_name, base_hex, style, accent) in ORES.items():
        if form_name.rstrip("^") not in FORMS:
            raise KeyError("%s: unknown form %r" % (name, form_name))
        img = render(name, form_name, base_hex, style, accent)
        path = os.path.join(OUT_DIR, name + ".png")
        img.save(path)
        written.append(name)

    print("rendered %d chunk textures" % len(written))

    used = {}
    for name, (form_name, _, _, _) in ORES.items():
        used.setdefault(form_name, []).append(name)
    clashes = {k: v for k, v in used.items() if len(v) > 1}
    if clashes:
        print("WARNING - forms used more than once:")
        for k, v in sorted(clashes.items()):
            print("   %-22s %s" % (k, ", ".join(v)))
    else:
        print("every ore has a silhouette of its own")

    if "--sheet" in sys.argv:
        sheet(written)


def sheet(names):
    from PIL import ImageDraw

    scale, cols = 6, 10
    cw, chh = 16 * scale, 16 * scale + 14
    rows = (len(names) + cols - 1) // cols
    out = Image.new("RGB", (cols * cw, rows * chh), (72, 72, 80))
    draw = ImageDraw.Draw(out)
    for i, name in enumerate(names):
        im = Image.open(os.path.join(OUT_DIR, name + ".png")).convert("RGBA")
        im = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        bg = Image.new("RGBA", im.size, (140, 140, 148, 255))
        bg.alpha_composite(im)
        x, y = (i % cols) * cw, (i // cols) * chh
        out.paste(bg.convert("RGB"), (x, y))
        draw.text((x + 2, y + 16 * scale + 2), name[:16], fill=(255, 255, 255))
    path = os.path.join(os.environ.get("CHUNK_ART_SHEET", ROOT), "chunk_sheet.png")
    out.save(path)
    print("sheet:", path)


if __name__ == "__main__":
    main()
