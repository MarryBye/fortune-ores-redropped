# Texture builder — `build_textures.py`

Fortune Ores now generates its world-gen ores in **every dimension**, and each ore shows the right background for
where it spawns: stone / deepslate in the Overworld, netherrack in the Nether, end stone in the End. That means four
block textures per material. Instead of drawing all of them by hand, you draw the ore bits **once** and let this
script paint them onto each background.

## What you provide

```
tools/texture_input/
    backgrounds/
        stone.png        <- Overworld stone background      -> ore_<mat>.png
        deepslate.png    <- Overworld deepslate background  -> deepslate_ore_<mat>.png
        netherrack.png   <- Nether background               -> netherrack_ore_<mat>.png
        endstone.png     <- End background                  -> endstone_ore_<mat>.png
    overlays/
        coal.png         <- just the ore speckles, TRANSPARENT background
        iron.png
        diamond.png
        ... one PNG per material ...
```

- **Backgrounds** are the plain 16×16 host blocks (opaque). Provide as many as you have — a missing background just
  skips that dimension's variant.
- **Overlays** are the coloured ore bits ("камешки") on a fully transparent background, one file per material. The
  file name (without `.png`) is the material name.

Every registered ore now generates its own block, so an overlay's file name must match the ore's **texture stem** —
i.e. the same file name it already uses in `assets/fortuneores/textures/items/` (e.g. `copper.png`, `certus_quartz.png`,
`crimson_iron.png`, `mangnanese.png`). There are ~105 of them; supply overlays only for the ores you want to restyle,
the rest keep their existing (placeholder) block texture.

## How to run

Double-click **`build_textures.bat`** (it installs Pillow the first time, then runs the script), or from the repo
root:

```
python tools/build_textures.py
```

Each overlay is composited onto every background and written straight into
`src/main/resources/assets/fortuneores/textures/blocks/` with the exact names the blocks register. Existing files
are overwritten; nothing else is touched.

Useful flags:

```
python tools/build_textures.py --dry-run          # show what would be written, write nothing
python tools/build_textures.py --input <folder>   # use a different input folder
python tools/build_textures.py --out <folder>     # write somewhere else
python tools/build_textures.py --size 32          # output at a higher resolution
```

## Generated overlays — `make_shard_textures.py`

The six Thaumcraft infused ores are the same crystal cluster in six aspect colours, so their art is generated
instead of drawn. `python tools/make_shard_textures.py` writes the item icons straight into
`assets/fortuneores/textures/items/` and their overlays into `texture_input/overlays/` (plus the four backgrounds);
run `build_textures.py` afterwards to turn those overlays into the block variants. Retint `ASPECTS` or reshape
`SHARDS` in the script and every icon and block follows.

## Per-ore dimension control

Where each ore may spawn is set in the mod config (`config/FortuneOres.cfg`), per material, under its own category:

```
SpawnInOverworld=true
SpawnInNether=true
SpawnInEnd=true
```

All default to `true` (every ore in every dimension); turn one off to stop that ore generating there.
