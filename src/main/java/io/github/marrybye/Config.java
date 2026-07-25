package io.github.marrybye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

public class Config {

    /**
     * Ores fully replaced out of the box (both EnableRawOre and EnableOreGen on): the vanilla Minecraft ores, the
     * metals
     * a modpack's progression usually runs on, and the materials this mod ships tuned world-gen and biome placement for
     * rather than merely recognising - the two Applied Energistics quartzes, the six Thaumcraft infused ores, the two
     * Tinkers' Nether metals and the gems. Every other ore ships off so the player opts in to exactly what they want
     * instead of switching off dozens of variants; an ore whose providing mod is absent simply generates this mod's own
     * block, which still smelts into whatever the pack does have. Matched case-insensitively against {@link Ore#name},
     * and each ore's vein shape, biomes and drop range live next to its declaration in {@code FortuneOres#setupOres}.
     */
    private static final Set<String> DEFAULT_REPLACE = new HashSet<String>(
        Arrays.asList(
            // Vanilla and the industrial backbone - no biome restriction, every pack needs these everywhere.
            "coal",
            "iron",
            "copper",
            "tin",
            "gold",
            "lapis",
            "redstone",
            "diamond",
            "quartz",
            "aluminum",
            "osmium",
            // Applied Energistics.
            "certusquartz",
            "chargedcertusquartz",
            // High tier gates.
            "draconium",
            "iridium",
            "dilithium",
            // Tinkers' Construct Nether metals.
            "cobalt",
            "ardite",
            // Gems, spread across the biomes they thematically belong to.
            "emerald",
            "ruby",
            "sapphire",
            "peridot",
            "topaz",
            "malachite",
            "tanzanite",
            "amber",
            "cinnabar",
            "rutile",
            // Thaumcraft infused ores, one biome theme per primal aspect.
            "infusedair",
            "infusedfire",
            "infusedwater",
            "infusedearth",
            "infusedorder",
            "infusedentropy"));

    /**
     * Ores that default to EnableRawOre only: mined for chunks wherever their own mod puts them, but neither generated
     * nor suppressed by us. Nothing ships this way at the moment - it is the middle setting a player picks per ore when
     * they want another mod's world generation left intact.
     */
    private static final Set<String> DEFAULT_RAW_ONLY = new HashSet<String>();

    public Config(Configuration config) {
        config.load();

        FortuneOres.allowProcessing = config.get("AAAGeneral", "AllowProcessing", true)
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
                + "first one that exists is used. If none is present, only stone-hosted ores generate.")
            .getString();
        FortuneOres.deepslateMaxY = config.get(
            "AAAGeneral",
            "DeepslateMaxY",
            22,
            "Overworld height at or below which ores generate as their deepslate variant; above it they use the stone "
                + "variant. Matches where the deepslate layer begins (default 22). Has no effect in the Nether or End, "
                + "which only ever get their own netherrack/end-stone variants.")
            .getInt();

        readForeignOreBlocks(config);

        // Every ore's drop/smelt AND world-gen settings live together in its own config category (the ore name).
        for (Ore ore : FortuneOres.oreStorage) {
            String cat = ore.name;
            // Defaults: the ores in DEFAULT_REPLACE are fully replaced, everything else stays off until enabled.
            String key = ore.name.toLowerCase();
            boolean defaultReplace = DEFAULT_REPLACE.contains(key);
            boolean defaultRaw = defaultReplace || DEFAULT_RAW_ONLY.contains(key);
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
                        + "other mods' ores in the world while still harvesting chunks from them.")
                .getBoolean(defaultRaw);
            ore.enableOreGen = config.get(
                cat,
                "EnableOreGen",
                defaultReplace,
                "Generate this mod's own " + ore.name
                    + " ore block in the world AND suppress the same ore type from Minecraft and every other mod (full "
                    + "replacement). Leave off to keep foreign "
                    + ore.name
                    + " ore generation untouched.")
                .getBoolean(defaultReplace);
            // Derived: the chunk is 'active' (registered, ore-dicted and smeltable) when either switch is on.
            ore.enabled = ore.enableRawOre || ore.enableOreGen;
            // BaseDrop is the minimum chunk drop; BaseDropMax adds an upper bound for a random range (e.g. 4-8 for
            // redstone/lapis). SmeltCount multiplies the furnace output per chunk. Defaults come from setupOres().
            ore.dropCount = config.get(cat, "BaseDrop", ore.dropCount)
                .getInt();
            ore.dropCountMax = config.get(cat, "BaseDropMax", Math.max(ore.dropCount, ore.dropCountMax))
                .getInt();
            ore.smeltCount = config.get(cat, "SmeltCount", ore.smeltCount)
                .getInt();

            // World generation shape. These knobs only matter when EnableOreGen is on. Defaults are seeded per rarity
            // (see OreRarity) and can all be overridden here.
            ore.minY = config.get(cat, "OreMinY", ore.minY)
                .getInt();
            ore.maxY = config.get(cat, "OreMaxY", ore.maxY)
                .getInt();
            ore.veinSize = config.get(
                cat,
                "OreVeinSize",
                ore.veinSize,
                "Upper bound on one " + ore.name
                    + " vein. It feeds the same ellipsoid vanilla uses, so it is a bound rather than an exact count: "
                    + "12 lands roughly 6-12 blocks, 6 lands 3-6 and 3 lands 1-2.")
                .getInt();
            ore.veinsPerChunk = config.get(cat, "OreVeinsPerChunk", ore.veinsPerChunk, "Vein attempts per chunk.")
                .getInt();
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
                    "Generate " + ore.name + " ore in the Overworld (and modded " + "stone/deepslate dimensions).")
                .getBoolean(true);
            ore.spawnNether = config
                .get(cat, "SpawnInNether", true, "Generate " + ore.name + " ore in the Nether (netherrack).")
                .getBoolean(true);
            ore.spawnEnd = config.get(cat, "SpawnInEnd", true, "Generate " + ore.name + " ore in the End (end stone).")
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
