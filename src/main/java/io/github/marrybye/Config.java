package io.github.marrybye;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

/**
 * Reads the config file into the ore catalogue.
 *
 * <p>
 * Out of the box every ore is on as a <em>raw ore</em> ({@code EnableRawOre}) and none of them generates
 * ({@code EnableOreGen}): mining any mod's copper ore hands out this mod's copper chunk, while the world keeps exactly
 * the ore generation the pack already had. World generation is what a player opts into per ore. That default is only
 * as broad as it looks because it is paired with {@code RequireRegisteredMaterial}: an ore whose material nothing in
 * the pack provides is dropped entirely, so "every ore on" really means "every ore the pack can do something with".
 */
public class Config {

    public Config(Configuration config) {
        config.load();

        FortuneOres.allowProcessing = config.get("AAAGeneral", "AllowProcessing", true)
            .getBoolean(true);

        FortuneOres.requireRegisteredMaterial = config.get(
            "AAAGeneral",
            "RequireRegisteredMaterial",
            true,
            "Drop an ore entirely unless the pack provides its material - an ingot, gem, dust or the ore's own smelting "
                + "target somewhere in the ore dictionary. Such an ore gets no chunk name, no icon, no ore dictionary "
                + "entry, no recipe, no creative tab entry and no world generation, and mining another mod's ore of "
                + "that type is left alone: a chunk that cannot be smelted into anything is a dead end, and handing "
                + "one out in place of a working ore drop would break the mod that ore came from. This is what keeps "
                + "'every ore enabled' cheap - a plain Forge pack ends up with the vanilla materials and nothing else. "
                + "Turn off only if you want the chunks of materials your pack does not have.")
            .getBoolean(true);

        // ---- World generation master switches ----------------------------------------------------------------------
        // Foreign ore generation is no longer cancelled by one global switch: each ore's own EnableOreGen toggle
        // (below)
        // both generates this mod's ore block and suppresses the matching foreign ore type, so replacement is per-ore.
        FortuneOres.strictOreGenSuppression = config.get(
            "AAAGeneral",
            "StrictOreGenSuppression",
            true,
            "Also deny foreign ore blocks at placement level while a chunk generates, for every ore whose EnableOreGen "
                + "is on. The ore-gen event only reaches generators that fire it; this catches the rest - vanilla "
                + "emerald (placed straight from the hills biome) and mods that run their own world generator. Only "
                + "applies during world generation and only where the ore would replace an existing block, so placing "
                + "an ore block by hand and ore inside generated structures are left alone. Turn off to fall back to "
                + "event-only suppression.")
            .getBoolean(true);
        FortuneOres.deepslateBlockId = config.get(
            "AAAGeneral",
            "DeepslateBlockId",
            "etfuturum:deepslate",
            "Registry id (modid:name) of the deepslate block that hosts the deepslate ore variants (Et Futurum Requiem). "
                + "You may list several comma-separated candidates (e.g. 'etfuturum:deepslate,quark:deepslate'); the "
                + "first one that exists is used. If no listed mod is installed, the deepslate ore blocks are not "
                + "registered at all and only stone-hosted ores generate; leave the value empty to skip them outright.")
            .getString();
        FortuneOres.deepslateMaxY = config.get(
            "AAAGeneral",
            "DeepslateMaxY",
            22,
            "Overworld height at or below which ores generate as their deepslate variant; above it they use the stone "
                + "variant. Matches where the deepslate layer begins (default 22). Has no effect in the Nether or End, "
                + "which only ever get their own netherrack/end-stone variants.")
            .getInt();

        // ---- Tech mod ore processing -------------------------------------------------------------------------------
        FortuneOres.techModIntegration = config.get(
            "AAAGeneral",
            "TechModIntegration",
            true,
            "Register the ore chunks and the ore blocks in the ore-processing machines of every supported tech mod "
                + "that is installed: Mekanism's Enrichment Chamber, Thermal Expansion's Pulverizer, IC2's Macerator "
                + "and Immersive Engineering's Crusher. Those mods only build ore recipes for a hard-coded list of "
                + "materials of their own, so without this only a handful of ores (iron, gold, copper, ...) can be "
                + "doubled and the rest are furnace-only. A machine that can be asked is never given a recipe for an "
                + "input it already handles; Mekanism cannot be asked, but keys its recipes by input and so ends up "
                + "with exactly one either way. Turn off to leave every machine as its own mod set it up.")
            .getBoolean(true);
        FortuneOres.machineOutputMultiplier = clampMultiplier(
            config.get(
                "AAAGeneral",
                "MachineOutputMultiplier",
                2,
                "How many outputs one ore chunk is worth in those machines (1-64). 2 is the ore doubling all of them "
                    + "are built around; 1 turns the machines into an alternative to the furnace rather than a "
                    + "profit. An ore block (silk touch) pays this times its BaseDrop, so processing the chunks a "
                    + "mined block drops is never worse than processing the block - that route still gets Fortune.")
                .getInt());
        FortuneOres.tinkersIntegration = config.get(
            "AAAGeneral",
            "TinkersSmelteryIntegration",
            true,
            "Let the ore chunks and the ore blocks melt in Tinkers' Construct's smeltery, into the molten fluid of "
                + "their own material. Tinkers only sets melting up for its own material list, so without this every "
                + "chunk outside iron/gold/copper/tin/... is not a smeltery input at all. The fluid is whatever the "
                + "pack registered for the material (iron.molten, molten.iron, ...), the melting point is the one "
                + "Tinkers itself uses for it, and an input Tinkers already melts is left untouched. A chunk is worth "
                + "MachineOutputMultiplier ingots of fluid, an ore block that times its BaseDrop. Materials with no "
                + "molten form in the pack (most gems) are simply skipped. Has no effect without Tinkers' Construct.")
            .getBoolean(true);
        FortuneOres.thaumcraftIntegration = config.get(
            "AAAGeneral",
            "ThaumcraftAspectIntegration",
            true,
            "Give the ore chunks and the ore blocks Thaumcraft aspects, so they can be scanned, put into a crucible "
                + "and spent on an infusion like any other ore. Thaumcraft works aspects out from an item's crafting "
                + "recipe and a chunk has none, so without this a chunk is aspect-less no matter what it is made of. "
                + "The aspects are those of what the chunk smelts into plus terra for the rock, falling back to "
                + "metallum/vitreus for a material Thaumcraft does not know. Has no effect without Thaumcraft.")
            .getBoolean(true);
        FortuneOres.oreDictTooltips = config.get(
            "AAAGeneral",
            "OreDictionaryTooltips",
            false,
            "Add an item's ore dictionary names to its advanced tooltip (F3+H). This is a debugging aid and it applies "
                + "to every item in the pack, not only this mod's, which is why it is off by default - NEI and most "
                + "inspection mods already show the same information on request. Turn on to see at a glance which "
                + "names an ore chunk carries.")
            .getBoolean(false);

        readForeignOreBlocks(config);

        // Every ore's drop/smelt AND world-gen settings live together in its own config category (the ore name).
        for (Ore ore : FortuneOres.oreStorage) {
            String cat = ore.name;
            // Defaults: every ore is a raw ore, nothing generates. RequireRegisteredMaterial then throws out whatever
            // the pack has no material for, which is what keeps the wide default from registering a hundred unusable
            // materials.
            boolean defaultRaw = true;
            boolean defaultReplace = false;
            // Two independent switches decide how this ore is handled:
            // - EnableRawOre: mining a matching ore block (this mod's, vanilla, or another mod's) drops this mod's
            // chunk. Turn this on to keep foreign ores in the world but harvest chunks from them.
            // - EnableOreGen: this mod generates its own ore block AND the same ore type from Minecraft/other mods is
            // suppressed (full replacement).
            ore.enableRawOre = config
                .get(
                    cat,
                    "EnableRawOre",
                    defaultRaw,
                    "Turn a mined " + ore.name
                        + " ore block (this mod's, vanilla, or from another mod) into this mod's chunk. Enable to keep "
                        + "other mods' ores in the world while still harvesting chunks from them. With this and "
                        + "EnableOreGen both off the ore leaves the game entirely: its chunk gets no ore dictionary "
                        + "entry, no smelting recipe, no machine recipe, no icon and no place in the creative tab.")
                .getBoolean(defaultRaw);
            ore.enableOreGen = config.get(
                cat,
                "EnableOreGen",
                defaultReplace,
                "Generate this mod's own " + ore.name
                    + " ore block in the world AND suppress the same ore type from Minecraft and every other mod (full "
                    + "replacement). Leave off to keep foreign "
                    + ore.name
                    + " ore generation untouched - the ore block is then not registered at all, and neither are the "
                    + "host variants (stone/deepslate/netherrack/end stone) no enabled ore generates in. Ore blocks "
                    + "already generated in an existing world disappear once their block is no longer registered.")
                .getBoolean(defaultReplace);
            // Derived: the chunk is 'active' (registered, ore-dicted and smeltable) when either switch is on.
            ore.enabled = ore.enableRawOre || ore.enableOreGen;
            // BaseDrop is the minimum chunk drop; BaseDropMax adds an upper bound for a random range (e.g. 4-8 for
            // redstone/lapis). SmeltCount multiplies the furnace output per chunk. Defaults come from setupOres().
            ore.dropCount = Math.max(
                0,
                config.get(cat, "BaseDrop", ore.dropCount)
                    .getInt());
            // Never below BaseDrop: the drop roll is a uniform range, and an inverted one would silently pay out the
            // minimum for ever.
            ore.dropCountMax = Math.max(
                ore.dropCount,
                config.get(cat, "BaseDropMax", Math.max(ore.dropCount, ore.dropCountMax))
                    .getInt());
            // A furnace recipe has to hand out at least one item, or there is no recipe.
            ore.smeltCount = Math.max(
                1,
                config.get(cat, "SmeltCount", ore.smeltCount)
                    .getInt());

            // World generation shape. These knobs only matter when EnableOreGen is on. Defaults are seeded per rarity
            // (see OreRarity) and can all be overridden here.
            ore.minY = clampHeight(
                config.get(cat, "OreMinY", ore.minY)
                    .getInt());
            ore.maxY = Math.max(
                ore.minY,
                clampHeight(
                    config.get(cat, "OreMaxY", ore.maxY)
                        .getInt()));
            // The vein shape divides by this, so zero would place a vein of NaN-sized nothing.
            ore.veinSize = Math.max(
                1,
                config.get(
                    cat,
                    "OreVeinSize",
                    ore.veinSize,
                    "Upper bound on one " + ore.name
                        + " vein. It feeds the same ellipsoid vanilla uses, so it is a bound rather than an exact count: "
                        + "12 lands roughly 6-12 blocks, 6 lands 3-6 and 3 lands 1-2.")
                    .getInt());
            ore.veinsPerChunk = Math.max(
                0,
                config.get(cat, "OreVeinsPerChunk", ore.veinsPerChunk, "Vein attempts per chunk.")
                    .getInt());
            ore.veinChance = clampPercent(
                config.get(
                    cat,
                    "OreVeinChance",
                    ore.veinChance,
                    "Percent chance that each attempt actually places a vein (1-100). This is how a rare ore stays rare "
                        + "without turning OreVeinsPerChunk down to zero: 25 with one attempt per chunk is a vein about "
                        + "every fourth chunk.")
                    .getInt());
            ore.harvestLevel = config.get(
                cat,
                "OreHarvestLevel",
                ore.harvestLevel,
                "Pickaxe tier needed to harvest the generated " + ore.name
                    + " ore block: 0 = wooden/golden, 1 = stone, 2 = iron, 3 = diamond. Mods that show a harvest level "
                    + "in a tooltip (WAILA) may number the tiers from 1, in which case the value shown there is this "
                    + "one plus one.")
                .getInt();
            // Per-dimension toggles. All default on, so every ore generates everywhere until the player narrows it
            // down. The Overworld toggle also governs modded stone/deepslate dimensions.
            ore.spawnOverworld = config
                .get(
                    cat,
                    "SpawnInOverworld",
                    true,
                    "Generate " + ore.name
                        + " ore in the Overworld (and modded stone/deepslate dimensions). Switched off for every ore, "
                        + "the stone and deepslate ore blocks are not registered at all.")
                .getBoolean(true);
            ore.spawnNether = config
                .get(
                    cat,
                    "SpawnInNether",
                    true,
                    "Generate " + ore.name
                        + " ore in the Nether (netherrack). Switched off for every ore, the netherrack ore blocks are "
                        + "not registered at all.")
                .getBoolean(true);
            ore.spawnEnd = config
                .get(
                    cat,
                    "SpawnInEnd",
                    true,
                    "Generate " + ore.name
                        + " ore in the End (end stone). Switched off for every ore, the end-stone ore blocks are not "
                        + "registered at all.")
                .getBoolean(true);
            ore.dimensionIds = config.get(
                cat,
                "DimensionIds",
                ore.dimensionIds,
                "Exact dimension ids " + ore.name
                    + " ore may generate in (0 = Overworld, -1 = Nether, 1 = End, anything else = a modded dimension). "
                    + "Leave empty to use the three SpawnIn* switches instead, which is what covers 'every modded "
                    + "stone dimension' with one toggle; a non-empty list is an exact whitelist and overrides them.")
                .getIntList();
            // Height overrides for the Nether and the End, which are solid 128-block masses rather than a thin
            // overworld crust; -1 keeps OreMinY/OreMaxY.
            ore.netherMinY = config
                .get(cat, "NetherMinY", ore.netherMinY, "Nether-only OreMinY override; -1 uses OreMinY.")
                .getInt();
            ore.netherMaxY = config
                .get(cat, "NetherMaxY", ore.netherMaxY, "Nether-only OreMaxY override; -1 uses OreMaxY.")
                .getInt();
            ore.endMinY = config.get(cat, "EndMinY", ore.endMinY, "End-only OreMinY override; -1 uses OreMinY.")
                .getInt();
            ore.endMaxY = config.get(cat, "EndMaxY", ore.endMaxY, "End-only OreMaxY override; -1 uses OreMaxY.")
                .getInt();
            // Biome restriction. Note that suppression of the foreign ore this one replaces is not biome-restricted -
            // EnableOreGen replaces that ore everywhere, so narrowing the biomes down makes the ore genuinely scarcer.
            ore.biomeRules = config.get(
                cat,
                "Biomes",
                ore.biomeRules,
                "Biomes " + ore.name
                    + " ore may generate in, one rule per line; empty means every biome. A rule is a Forge biome type "
                    + "('type:MOUNTAIN', which also covers modded biomes tagged with it), a biome name ('Extreme "
                    + "Hills', matched ignoring case/spaces/underscores) or a raw id ('id:35'). Prefix a rule with '!' "
                    + "to exclude instead, e.g. '!type:OCEAN'; exclusions win over inclusions, and a list of only "
                    + "exclusions means everywhere but those. Types available in 1.7.10: HOT, COLD, SPARSE, DENSE, WET, "
                    + "DRY, SAVANNA, CONIFEROUS, JUNGLE, SPOOKY, DEAD, LUSH, NETHER, END, MUSHROOM, MAGICAL, OCEAN, "
                    + "RIVER, WATER, MESA, FOREST, PLAINS, MOUNTAIN, HILLS, SWAMP, SANDY, SNOWY, WASTELAND, BEACH.")
                .getStringList();
            ore.biomes = BiomeFilter.parse(ore.name, ore.biomeRules);
        }

        if (config.hasChanged()) {
            config.save();
        }
    }

    /** A machine output has to fit in one stack, and 0 would register recipes that hand out nothing. */
    private static int clampMultiplier(int value) {
        if (value < 1) return 1;
        if (value > 64) return 64;
        return value;
    }

    /** Keeps a height inside the world; the generator reads nothing outside it and would place nothing there. */
    private static int clampHeight(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }

    /** Keeps a percentage knob usable: 0 or less would silence the ore entirely, which is what EnableOreGen is for. */
    private static int clampPercent(int value) {
        if (value < 1) return 1;
        if (value > 100) return 100;
        return value;
    }

    /**
     * Reads the {@code OreName=modid:block[:meta]} list that assigns another mod's ore block to one of this mod's
     * ores. It exists for the mods the ore dictionary cannot describe: ores that are never registered in it, and ores
     * sharing one block with unrelated ones, where only certain metadata values may be swapped. The list in the config
     * file replaces the built-in one wholesale, so an entry can be removed by deleting its line.
     */
    private void readForeignOreBlocks(Configuration config) {
        List<String> builtIn = new ArrayList<String>();
        for (Ore ore : FortuneOres.oreStorage) {
            for (String blockId : ore.foreignBlockIds) builtIn.add(ore.name + "=" + blockId);
        }

        String[] configured = config.get(
            "AAAGeneral",
            "ForeignOreBlocks",
            builtIn.toArray(new String[builtIn.size()]),
            "Ore blocks from other mods that this mod should handle as one of its own ores, one 'OreName=modid:block' "
                + "or 'OreName=modid:block:meta' per line. Only needed for ores the ore dictionary does not describe - "
                + "everything registered as oreCopper, oreIron and so on is already found automatically. Give the "
                + "metadata when one block holds several unrelated ores (Thaumcraft's blockCustomOre: 0 is cinnabar, "
                + "1-6 are the infused ores, 7 is amber); leave it out to match every metadata value. Entries whose "
                + "mod is not installed are ignored, and an entry is removed by deleting its line.")
            .getStringList();

        for (Ore ore : FortuneOres.oreStorage) ore.foreignBlockIds.clear();

        for (String entry : configured) {
            if (entry == null) continue;

            int sep = entry.indexOf('=');
            if (sep <= 0) continue;

            Ore ore = FortuneOres.getOre(
                entry.substring(0, sep)
                    .trim());
            if (ore == null) continue;

            ore.addForeignBlockId(
                entry.substring(sep + 1)
                    .trim());
        }
    }
}
