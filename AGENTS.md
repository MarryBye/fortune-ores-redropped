# AGENTS.md

## Project at a glance
- This is a Forge 1.7.10 mod: `FortuneOres`.
- Main entrypoint: `src/main/java/io/github/marrybye/FortuneOres.java` (root package `io.github.marrybye`, mixins in `io.github.marrybye.mixin`).
- The mod id stays `FortuneOres` and the asset domain stays `assets/fortuneores/`; both are baked into existing worlds, so do not rename them along with the package.
- Mod flow is split by lifecycle: `preInit()` creates `Config`, the creative tab, and `ItemChunk`; `init()` wires ore dictionary registration and `OreSwapper`; `postInit()` adds smelting.
- Ore behavior is data-driven through `Ore` objects in `FortuneOres.setupOres()` and metadata order matters.

## Build and dev workflow
- Do not edit the root `build.gradle.kts` for normal changes; it only applies `com.gtnewhorizons.gtnhconvention`.
- Put dependency additions in `dependencies.gradle` and repository additions in `repositories.gradle`.
- Use `gradle.properties` for project identity and build flags (`modId`, `modGroup`, `usesMixins`, `accessTransformersFile`, etc.).
- Use `addon.gradle[.kts]`, `addon.late.gradle[.kts]`, or `addon[.late].local.gradle[.kts]` for extra build logic or local-only tweaks.
- First setup / migration: `./gradlew setupDecompWorkspace`.
- Routine validation: `./gradlew build`.
- IDE run target: `./gradlew runClient --username=Developer` (username can be overridden per run).
- CI-style validation mentioned in docs: `./gradlew clean setupCIWorkspace`.

## Mod-specific conventions
- `src/main/resources/mcmod.info` is still used for metadata; keep it aligned with `modId`, `modName`, and `minecraftVersion` when renaming.
- Ore dictionary mirroring happens in `OreDictHandler.Handle(OreRegisterEvent)`; it maps registered ore names back to `Ore` instances and registers chunk stacks. Register through `FortuneOres.registerOreOnce` rather than `OreDictionary.registerOre` - the chunks are reached by two paths and Forge does not deduplicate.
- Every ore must have at least one `Ore#oreNames` entry (`addOreName`), including the `addVanillaOre` ones: those names are what `FortuneOres.addBlockOreDicting()` registers the generated ore blocks under, and what `OreGenSuppressor` matches modded ores by. `addBlockOreDicting` logs a `severe` line for any enabled ore that has none.
- The deepslate host's blocks are additionally registered as `oreDeepslate*` (Et Futurum Requiem's naming) on top of the plain `ore*` name.
- Drop replacement lives in the static `OreSwapper.swapDrops(...)`; silk touch is preserved and XP is spawned from the matched ore entry.
- An ore block is recognised by the mined block itself (instance + metadata), not by what it drops, so ores dropping a finished item (Thaumcraft's amber, Biomes O' Plenty's gems) are swapped too. The table is built from the ore dictionary, `Ore#vanillaBlocks` and `Ore#foreignBlockIds`; matching the dropped stack through the ore dictionary remains a fallback.
- `Ore#foreignBlockIds` holds `"modid:block[:meta]"` ids for ores the ore dictionary cannot describe, resolved in `FortuneOres.resolveForeignBlocks()` as a soft dependency and overridable through the `ForeignOreBlocks` config list.
- What a chunk smelts into is resolved in `FortuneOres.resolveSmeltResult`: the ore's explicit smelt names first, then its name and aliases with the `ingot` > `gem` > `dust` prefixes.
- It has two entry points: the `MixinBlock` injection into `Block#getDrops` (covers machine miners such as Mekanism's Digital Miner, which never fire a harvest event) and `OreSwapper.SwapOres(HarvestDropsEvent)` (fallback for blocks that override `getDrops`, plus the bonus XP). Both are idempotent - whichever runs first wins.
- `allowProcessing` in `Config` changes whether ore chunks are registered as ores or as `dust*` entries.
- Ore-dictionary names may be shared: `addAlias` can hand one ore another's name (Rutile carries `oreTitanium`). `Ore#isAlias` marks those, and `OreSwapper` claims own names before aliases so the ore owning a name keeps the mined blocks registered under it.
- `ItemChunk` expects `nextMeta` and `oreStorage` to stay in sync; add new ores by appending to `setupOres()` rather than reordering existing entries.
- World-gen shape per ore: height (`OreMinY`/`OreMaxY`, with `NetherMinY`/`EndMinY`/... overrides), `OreVeinSize`, `OreVeinsPerChunk` and `OreVeinChance` - the percentage is the rarity knob, since dozens of ores can be enabled at once and stacking veins per chunk carpets the world. Seed defaults from `OreRarity` and override per ore with `setVeins`/`setVeinChance`/`setBiomes`/`setHarvestLevel`/`setNetherHeight`/`setEndHeight` next to the ore's declaration.
- Biome restrictions are parsed by `BiomeFilter` from each ore's `Biomes` list (`type:MOUNTAIN`, a biome name, `id:35`, `!` to exclude). An ore meant to appear in the End needs `setEndHeight(40, 75)` - the islands float there, so a normal Overworld band never finds end stone.
- Ore aliases are encoded directly in `setupOres()` with extra names like `Aluminium`, `Mythril`, and `Titanium`.
- Item textures live under `src/main/resources/assets/fortuneores/textures/items/` and are named after the ore key in lowercase; `mysteriouschunk.png` is the fallback.

## Integration notes
- Ore processing lives in `MachineCompat`, called from `postInit` (`TechModIntegration` / `MachineOutputMultiplier` in the config). It registers chunk and ore block -> dust in every supported machine that is installed - Mekanism's Enrichment Chamber, Thermal Expansion's Pulverizer, IC2's Macerator, Immersive Engineering's Crusher - all through reflection plus `Loader.isModLoaded`, so no tech mod is a build dependency. Add a machine by writing another `Machine` implementation with a `create()` that returns null when its mod is absent.
- Those mods only build ore recipes for a hard-coded material list of their own, which is why ore-dicting the blocks as `oreIron` is enough for iron but leaves the other hundred materials unprocessable; `MachineCompat` is what closes that gap. Mods reading ore-dictionary *names* at recipe time (GregTech, and IC2/IE for their own materials) need nothing from us. Ender IO's SAG Mill and AE2's grindstone are still uncovered - both take recipes from their own files rather than an API.
- What a recipe pays out is `MachineCompat#resolveOutput`: `dust*`, then the ore's explicit smelt names, then `gem*`/`crystal*`, and the chunk itself when the pack has none of those. Never `ingot*` - that would skip the smelting step the machines are built around.
- Access transformers are supported via `gradle.properties`, but the README warns they can break source attachment in IntelliJ.
- Mixins are enabled (`usesMixins = true`, `mixinsPackage = mixin`), which makes UniMixins a required runtime dependency. New mixin classes must be listed in `src/main/resources/mixins.fortuneores.json`.
- Foreign ore generation is suppressed in two layers: `OreGenSuppressor.onGenerateMinable` (the ore-gen event) and, when `StrictOreGenSuppression` is on, `MixinWorld` denying the placement outright inside the window that `MixinChunkProviderServer` opens around chunk population. The strict layer only acts during world generation and only when the ore replaces a non-air block.
- If the Forge deobfuscator asks for an MCP conf directory, use `%USERPROFILE%/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf` on Windows.

