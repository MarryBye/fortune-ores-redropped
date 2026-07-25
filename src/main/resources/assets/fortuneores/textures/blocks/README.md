# Ore block textures

16×16 PNG block textures live here. File names **must** match exactly (the block registers icons by
`fortuneores:<name>`). Missing files won't crash the game — the block simply shows the pink/black "missing texture"
until you add them.

There are now **four host variants** per material, one for each terrain the ore can generate in:

| Host | Dimension | File name |
| ---- | --------- | --------- |
| Stone | Overworld | `ore_<mat>.png` |
| Deepslate | Overworld (deep) | `deepslate_ore_<mat>.png` |
| Netherrack | Nether | `netherrack_ore_<mat>.png` |
| End stone | End | `endstone_ore_<mat>.png` |

Materials (`<mat>`): `coal`, `diamond`, `redstone`, `lapis`, `emerald`, `ruby`, `topaz`, `sapphire`, `iron`, `gold`,
`malachite`, `tanzanite`, `shadow`, `draconium`.

## Don't hand-draw all four — generate them

Draw the ore bits **once** on a transparent background, then run the texture builder to composite them onto each
host background. See [`tools/README.md`](../../../../../../../tools/README.md):

```
python tools/build_textures.py
```

Tip: a stone ore texture is usually a stone background with coloured speckles; the other hosts are the same speckles
on a darker deepslate / red netherrack / pale end-stone background — exactly what the builder produces.
