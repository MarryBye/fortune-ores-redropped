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
- Ore dictionary mirroring happens in `OreDictHandler.Handle(OreRegisterEvent)`; it maps registered ore names back to `Ore` instances and registers chunk stacks.
- Drop replacement lives in the static `OreSwapper.swapDrops(...)`; silk touch is preserved and XP is spawned from the matched ore entry.
- An ore block is recognised by the mined block itself (instance + metadata), not by what it drops, so ores dropping a finished item (Thaumcraft's amber, Biomes O' Plenty's gems) are swapped too. The table is built from the ore dictionary, `Ore#vanillaBlocks` and `Ore#foreignBlockIds`; matching the dropped stack through the ore dictionary remains a fallback.
- `Ore#foreignBlockIds` holds `"modid:block[:meta]"` ids for ores the ore dictionary cannot describe, resolved in `FortuneOres.resolveForeignBlocks()` as a soft dependency and overridable through the `ForeignOreBlocks` config list.
- What a chunk smelts into is resolved in `FortuneOres.resolveSmeltResult`: the ore's explicit smelt names first, then its name and aliases with the `ingot` > `gem` > `dust` prefixes.
- It has two entry points: the `MixinBlock` injection into `Block#getDrops` (covers machine miners such as Mekanism's Digital Miner, which never fire a harvest event) and `OreSwapper.SwapOres(HarvestDropsEvent)` (fallback for blocks that override `getDrops`, plus the bonus XP). Both are idempotent - whichever runs first wins.
- `allowProcessing` in `Config` changes whether ore chunks are registered as ores or as `dust*` entries.
- `ItemChunk` expects `nextMeta` and `oreStorage` to stay in sync; add new ores by appending to `setupOres()` rather than reordering existing entries.
- Ore aliases are encoded directly in `setupOres()` with extra names like `Aluminium`, `Mythril`, and `Titanium`.
- Item textures live under `src/main/resources/assets/fortuneores/textures/items/` and are named after the ore key in lowercase; `mysteriouschunk.png` is the fallback.

## Integration notes
- Access transformers are supported via `gradle.properties`, but the README warns they can break source attachment in IntelliJ.
- Mixins are enabled (`usesMixins = true`, `mixinsPackage = mixin`), which makes UniMixins a required runtime dependency. New mixin classes must be listed in `src/main/resources/mixins.fortuneores.json`.
- Foreign ore generation is suppressed in two layers: `OreGenSuppressor.onGenerateMinable` (the ore-gen event) and, when `StrictOreGenSuppression` is on, `MixinWorld` denying the placement outright inside the window that `MixinChunkProviderServer` opens around chunk population. The strict layer only acts during world generation and only when the ore replaces a non-air block.
- If the Forge deobfuscator asks for an MCP conf directory, use `%USERPROFILE%/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf` on Windows.

