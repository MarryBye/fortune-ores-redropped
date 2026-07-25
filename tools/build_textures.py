#!/usr/bin/env python3
"""Assemble Fortune Ores block textures by compositing ore "speckle" overlays onto host backgrounds.

You provide, in an input folder:

    texture_input/
        backgrounds/
            stone.png        <- overworld stone background
            deepslate.png    <- overworld deepslate background
            netherrack.png   <- Nether background
            endstone.png     <- End background
        overlays/
            coal.png         <- the ore bits ("cameshki") on a TRANSPARENT background
            iron.png
            ...one file per material...

For every overlay this script lays it over each background and writes the finished 16x16 block texture
straight into the mod's texture folder with the exact name the block registers, e.g.

    coal.png  ->  ore_coal.png, deepslate_ore_coal.png, netherrack_ore_coal.png, endstone_ore_coal.png

Missing a background simply skips that host; missing an overlay just means that ore keeps whatever texture
is already on disk. Nothing is deleted.

Usage (from the repo root):

    python tools/build_textures.py
    python tools/build_textures.py --input tools/texture_input --out src/main/resources/assets/fortuneores/textures/blocks

Requires Pillow:  pip install pillow
"""

import argparse
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit(
        "This script needs the Pillow imaging library.\n"
        "Install it with:  python -m pip install pillow\n"
        "(or just run build_textures.bat, which installs it for you)"
    )

# Host background file name -> icon/file infix the block registers with. Must match OreHost.infix in the mod.
HOSTS = {
    "stone": "ore",
    "deepslate": "deepslate_ore",
    "netherrack": "netherrack_ore",
    "endstone": "endstone_ore",
}

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_INPUT = os.path.join(REPO_ROOT, "tools", "texture_input")
DEFAULT_OUT = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "fortuneores", "textures", "blocks"
)


def load_rgba(path):
    return Image.open(path).convert("RGBA")


def find_backgrounds(bg_dir):
    """Return {host_name: loaded background image} for every background file that exists."""
    backgrounds = {}
    for host_name in HOSTS:
        path = os.path.join(bg_dir, host_name + ".png")
        if os.path.isfile(path):
            backgrounds[host_name] = load_rgba(path)
    return backgrounds


def find_overlays(overlay_dir):
    """Return [(material, path)] for every PNG in the overlays folder (stem lowercased = material name)."""
    overlays = []
    if not os.path.isdir(overlay_dir):
        return overlays
    for name in sorted(os.listdir(overlay_dir)):
        if not name.lower().endswith(".png"):
            continue
        material = os.path.splitext(name)[0].lower()
        overlays.append((material, os.path.join(overlay_dir, name)))
    return overlays


def composite(background, overlay, size):
    """Overlay the speckles onto a copy of the background, both normalised to `size`x`size`, nearest-neighbour."""
    bg = background if background.size == (size, size) else background.resize((size, size), Image.NEAREST)
    ov = overlay if overlay.size == (size, size) else overlay.resize((size, size), Image.NEAREST)
    out = bg.copy()
    out.alpha_composite(ov)
    return out


def main():
    parser = argparse.ArgumentParser(description="Composite ore overlays onto host backgrounds.")
    parser.add_argument("--input", default=DEFAULT_INPUT,
                        help="Input folder containing backgrounds/ and overlays/ (default: tools/texture_input)")
    parser.add_argument("--out", default=DEFAULT_OUT,
                        help="Output folder for the finished block textures (default: the mod's textures/blocks)")
    parser.add_argument("--size", type=int, default=16, help="Output texture size in pixels (default: 16)")
    parser.add_argument("--dry-run", action="store_true", help="List what would be written without writing anything")
    args = parser.parse_args()

    bg_dir = os.path.join(args.input, "backgrounds")
    overlay_dir = os.path.join(args.input, "overlays")

    backgrounds = find_backgrounds(bg_dir)
    if not backgrounds:
        sys.exit(
            "No background images found in %s\n"
            "Add stone.png / deepslate.png / netherrack.png / endstone.png there." % bg_dir
        )

    overlays = find_overlays(overlay_dir)
    if not overlays:
        sys.exit(
            "No overlay images found in %s\n"
            "Add one PNG per material (e.g. coal.png) with a transparent background." % overlay_dir
        )

    print("Backgrounds: " + ", ".join(sorted(backgrounds)))
    missing_bg = [h for h in HOSTS if h not in backgrounds]
    if missing_bg:
        print("  (skipping absent backgrounds: %s)" % ", ".join(missing_bg))

    if not args.dry_run:
        os.makedirs(args.out, exist_ok=True)

    written = 0
    for material, overlay_path in overlays:
        overlay = load_rgba(overlay_path)
        print(material)
        for host_name, background in backgrounds.items():
            infix = HOSTS[host_name]
            out_name = "%s_%s.png" % (infix, material)
            out_path = os.path.join(args.out, out_name)
            if args.dry_run:
                print("    would write %s" % out_name)
                continue
            composite(background, overlay, args.size).save(out_path)
            print("    wrote %s" % out_name)
            written += 1

    print("\nDone. %d texture%s %s." % (
        written, "" if written == 1 else "s", "would be written" if args.dry_run else "written to %s" % args.out))


if __name__ == "__main__":
    main()
