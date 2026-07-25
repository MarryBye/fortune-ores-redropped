#!/usr/bin/env python3
"""Draw the Thaumcraft infused-shard textures: one item shard cluster per aspect, plus the
matching block overlay and the four host backgrounds build_textures.py composites them onto.

The six shards differ only in colour, so they are generated rather than drawn by hand - retint
ASPECTS or reshape SHARDS and every icon and block variant follows. Run this first, then
build_textures.py to turn the overlays into the stone/deepslate/netherrack/end-stone blocks:

    python tools/make_shard_textures.py
    python tools/build_textures.py

Requires Pillow:  pip install pillow
"""

import os
import sys
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(REPO, "src", "main", "resources", "assets", "fortuneores", "textures", "items")
TOOLS_IN = os.path.join(REPO, "tools", "texture_input")
OVERLAYS = os.path.join(TOOLS_IN, "overlays")
BACKGROUNDS = os.path.join(TOOLS_IN, "backgrounds")

# The flat host colours the existing generated block textures already use.
HOST_BG = {
    "stone": (127, 127, 127, 255),
    "deepslate": (72, 72, 78, 255),
    "netherrack": (94, 32, 32, 255),
    "endstone": (219, 222, 171, 255),
}

# Thaumcraft 4 primal aspect colours. Perditio's 0x404040 is lifted a little so the outline
# ramp below it still reads against deepslate and netherrack.
ASPECTS = {
    "infused_air": (0xFF, 0xF0, 0x6E),
    "infused_fire": (0xFF, 0x66, 0x1E),
    "infused_water": (0x3C, 0xD4, 0xFC),
    "infused_earth": (0x56, 0xC0, 0x00),
    "infused_order": (0xC6, 0xC5, 0xEA),
    "infused_entropy": (0x74, 0x74, 0x82),
}


def lerp(c, target, t):
    return tuple(int(round(c[i] + (target[i] - c[i]) * t)) for i in range(3))


def ramp(base):
    """Highlight / light / mid / dark / outline, in the order the crystal shading uses."""
    white = (255, 255, 255)
    black = (0, 0, 0)
    return {
        "H": lerp(base, white, 0.55) + (255,),
        "L": lerp(base, white, 0.25) + (255,),
        "M": base + (255,),
        "D": lerp(base, black, 0.30) + (255,),
        "O": lerp(base, black, 0.70) + (255,),
    }


def crystal(cx, top, height, width, lean=0.0):
    """Rasterise one shard as the elongated hexagon a Thaumcraft crystal reads as: a pointed
    tip, straight faces, a pointed foot. Returns {(x, y): shade}.

    The faces are shaded across the crystal - lit on the left, in shadow on the right - which
    is what turns a flat silhouette into something faceted.
    """
    body = {}
    taper = min(2, height // 3)
    for i in range(height):
        # Full width per row: grow into the body at the tip, shrink again at the foot.
        if i < taper:
            w = 1 + (width - 1) * (i + 1) // (taper + 1)
        elif i >= height - taper:
            w = 1 + (width - 1) * (height - i) // (taper + 1)
        else:
            w = width
        w = max(1, w)

        y = top + i
        centre = cx + lean * i
        x0 = int(round(centre - (w - 1) / 2.0))
        for off in range(w):
            x = x0 + off
            if w == 1:
                shade = "L"
            elif w == 2:
                # Too narrow for a shadow face; two flat tones keep it from reading as a pill.
                shade = "H" if off == 0 else "M"
            elif off == 0:
                shade = "H"
            elif off == w - 1:
                shade = "D"
            elif off == 1 and w >= 4:
                shade = "L"
            else:
                shade = "M"
            body[(x, y)] = shade
    return body


def outline(cells, size):
    """One-pixel dark border hugging the drawn shape - the pixel-art staple that stops the
    crystal dissolving into a light background (end stone) or a dark one (deepslate)."""
    edge = {}
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                p = (x + dx, y + dy)
                if 0 <= p[0] < size and 0 <= p[1] < size and p not in cells:
                    edge[p] = "O"
    return edge


def render(shards, colours, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    body = {}
    # Drawn back to front, so a later crystal overlaps the one behind it.
    for spec in shards:
        body.update(crystal(*spec))
    body = {p: s for p, s in body.items() if 0 <= p[0] < size and 0 <= p[1] < size}
    for p, s in outline(set(body), size).items():
        px[p] = colours[s]
    for p, s in body.items():
        px[p] = colours[s]
    return img


# (centre x, top y, height, width, lean)
# A tall shard flanked by two shorter ones - what a handful of shards reads as at 16x16. The
# same cluster is the block's ore patch: every other material in the mod ships the identical
# art as item icon and block overlay, so the shards keep that convention.
SHARDS = [
    (4, 6, 8, 3, 0.0),
    (11, 7, 7, 3, 0.0),
    (8, 2, 12, 4, 0.0),
]


def main():
    os.makedirs(OVERLAYS, exist_ok=True)
    os.makedirs(BACKGROUNDS, exist_ok=True)

    for host, colour in HOST_BG.items():
        Image.new("RGBA", (16, 16), colour).save(os.path.join(BACKGROUNDS, host + ".png"))
        print("background %s.png" % host)

    for name, base in ASPECTS.items():
        art = render(SHARDS, ramp(base))
        art.save(os.path.join(ITEMS, name + ".png"))
        art.save(os.path.join(OVERLAYS, name + ".png"))
        print("item + overlay %s.png" % name)


if __name__ == "__main__":
    sys.exit(main())
