package io.github.marrybye;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public class Ore {

    public String name;
    public String texture;
    public int meta;
    /** Derived: {@code enableRawOre || enableOreGen}. Gates chunk registration, ore-dicting and smelting. */
    public boolean enabled;

    // ---- Handling switches ---------------------------------------------------------------------------------------
    /** Swap the drops of a matching mined ore block (this mod's, vanilla, or another mod's) to this ore's chunk. */
    public boolean enableRawOre = true;
    /** Generate this mod's own ore block AND suppress the same ore type from Minecraft and every other mod. */
    public boolean enableOreGen = true;

    // ---- World generation (every ore generates its own block when {@link #enableOreGen} is on) --------------------
    public int minY = 4;
    public int maxY = 32;
    public int veinSize = 6;
    public int veinsPerChunk = 3;
    /** Per-dimension toggles: Overworld also covers modded stone/deepslate dimensions; Nether = -1; End = 1. */
    public boolean spawnOverworld = true;
    public boolean spawnNether = true;
    public boolean spawnEnd = true;
    /** Pickaxe tier required to harvest the generated ore block (0 = wood, 1 = stone, 2 = iron, 3 = diamond). */
    public int harvestLevel = 1;

    /** Lower bound of chunks dropped when the ore is mined (before Fortune). */
    public int dropCount;
    /** Upper bound of chunks dropped when the ore is mined. When {@code > dropCount} the amount is rolled uniformly. */
    public int dropCountMax;
    /** How many result items a single chunk yields when smelted in a furnace. */
    public int smeltCount;

    public float xpSmelt;
    public int xpDropMin;
    public int xpDropMax;

    public boolean oreDicted;

    /**
     * Vanilla ores (coal, diamond, redstone, lapis) drop a finished item instead of an ore-dicted ItemBlock, so they
     * cannot be matched through the ore dictionary like metals. Instead they are matched directly against these blocks
     * in {@link OreSwapper}, and their chunk is smelted into {@link #vanillaSmeltResult}.
     */
    public boolean isVanilla;
    public Block[] vanillaBlocks;
    public ItemStack vanillaSmeltResult;

    /**
     * Foreign ore blocks declared as {@code "modid:name[:meta]"} for mods that keep their ore out of the ore
     * dictionary, or whose ore block drops a finished item. Resolved into {@link #foreignBlocks} in
     * {@link FortuneOres#init}.
     */
    public ArrayList<String> foreignBlockIds;
    /** The entries of {@link #foreignBlockIds} whose mod is actually installed; empty when none is. */
    public ArrayList<ForeignOreBlock> foreignBlocks;

    public ArrayList<String> oreNames;
    /**
     * Explicit smelting targets (e.g. {@code quicksilver} for Cinnabar), checked before anything derived from the
     * ore's own name, in the order they were added.
     */
    public ArrayList<String> smeltNames;
    /**
     * The ore's name plus its aliases. Each is tried with the {@code ingot} / {@code gem} / {@code dust} prefixes, in
     * that priority order, to find what a chunk smelts into (see {@code FortuneOres#resolveSmeltResult}).
     */
    public ArrayList<String> smeltBases;

    public Ore(String oreName, int oreMeta) {
        name = oreName;
        texture = oreName.toLowerCase();
        meta = oreMeta;

        dropCount = 1;
        dropCountMax = 1;
        smeltCount = 1;

        xpSmelt = 0;
        xpDropMin = 0;
        xpDropMax = 0;

        oreNames = new ArrayList<>();
        smeltNames = new ArrayList<>();
        smeltBases = new ArrayList<>();
        foreignBlockIds = new ArrayList<>();
        foreignBlocks = new ArrayList<>();
    }

    public Ore(String oreName, int oreMeta, int droppedXPMin, int droppedXPMax, float xpSmelt) {
        this(oreName, oreMeta);
        this.xpSmelt = xpSmelt;
        xpDropMin = droppedXPMin;
        xpDropMax = droppedXPMax;
    }

    public void addOreName(String name) {
        oreNames.add("ore" + name);
        oreNames.add("oreNether" + name);
    }

    public void addSmeltName(String name) {
        smeltNames.add(name);
    }

    /** Adds a name (the ore's own or an alias) that the ingot/gem/dust smelting lookup should try. */
    public void addSmeltBase(String name) {
        smeltBases.add(name);
    }

    public void addForeignBlockId(String id) {
        foreignBlockIds.add(id);
    }
}
