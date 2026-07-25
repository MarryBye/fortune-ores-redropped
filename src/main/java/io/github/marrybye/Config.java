package io.github.marrybye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

public class Config {

    /**
     * Ores fully replaced out of the box (both EnableRawOre and EnableOreGen on): the vanilla Minecraft ores. Every
     * other ore ships off so the player opts in to exactly what they want instead of switching off dozens of variants.
     * Matched case-insensitively against {@link Ore#name}.
     */
    private static final Set<String> DEFAULT_REPLACE = new HashSet<String>(
        Arrays.asList("copper", "iron", "gold", "coal", "diamond", "redstone", "lapis", "quartz"));

    /**
     * Ores that additionally default to EnableRawOre only (chunk when mined, but no world-gen or suppression). Emerald
     * lives here: vanilla emerald ore keeps generating in its normal rare spots and simply drops the emerald chunk,
     * instead of us flooding the world with emerald veins. Turning its EnableOreGen on does now replace it for real -
     * StrictOreGenSuppression reaches the hills biome that places it without firing the ore-gen event.
     */
    private static final Set<String> DEFAULT_RAW_ONLY = new HashSet<String>(Arrays.asList("emerald"));

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
            // Defaults: vanilla ores are fully replaced, emerald is raw-only, everything else is off until enabled.
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
            ore.veinSize = config.get(cat, "OreVeinSize", ore.veinSize)
                .getInt();
            ore.veinsPerChunk = config.get(cat, "OreVeinsPerChunk", ore.veinsPerChunk)
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
        }

        if (config.hasChanged()) {
            config.save();
        }
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
