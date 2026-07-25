package io.github.marrybye;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Turns the drops of a mined foreign ore block (vanilla or from another mod) into this mod's chunks.
 *
 * <p>
 * The swap itself lives in the static {@link #swapDrops} because it has to run from two places:
 *
 * <ul>
 * <li>{@link io.github.marrybye.mixin.MixinBlock}, injected into {@code Block.getDrops} - the method
 * <em>every</em> drop path funnels through, including the machine miners (Mekanism's Digital Miner, quarries, ...) that
 * collect a block's drops themselves and never fire a Forge harvest event;</li>
 * <li>the {@link HarvestDropsEvent} handler below, which still covers the blocks whose class overrides
 * {@code getDrops} wholesale (the mixin only applies to {@code Block}'s own implementation) and which is where the
 * mod's extra mining XP is granted.</li>
 * </ul>
 *
 * <p>
 * Both entry points are idempotent: whichever runs first converts the drops, and the other one sees chunks already in
 * the list and leaves them alone.
 *
 * <p>
 * An ore block is recognised by <em>the block that was mined</em> (its instance plus its metadata), not by what it
 * drops, so what it would have dropped does not matter: an ore handing out a finished item (Thaumcraft's amber-bearing
 * stone drops plain amber) is swapped just like one dropping an ore-dicted ItemBlock. The blocks worth swapping are
 * collected from the ore dictionary, from {@link Ore#vanillaBlocks} and from {@link Ore#foreignBlockIds}. Matching the
 * dropped stack through the ore dictionary is kept as a fallback for drops that are ore blocks in their own right.
 */
public class OreSwapper {

    /** Ore-dictionary id of an ore block -> what its chunk drop looks like. Only ores with EnableRawOre are in here. */
    protected static final HashMap<Integer, DropStorage> dropMap = new HashMap<Integer, DropStorage>();
    /**
     * The mined block itself -> what its chunk drop looks like, one entry per metadata value (null = not ours). This is
     * the primary match: it works no matter what the block drops, which is what catches the ores that hand out a
     * finished item instead of an ore-dicted ItemBlock (Thaumcraft's amber-bearing stone drops plain amber, ...).
     */
    protected static final HashMap<Block, DropStorage[]> blockMap = new HashMap<Block, DropStorage[]>();

    private static boolean built;
    private static volatile boolean blockMapBuilt;

    public OreSwapper() {
        build();
    }

    /**
     * Builds the lookup tables from the (config-populated) ore list. Idempotent; runs from {@link FortuneOres#init}
     * before anything can break a block.
     */
    public static void build() {
        if (built) return;
        built = true;

        for (Ore ore : FortuneOres.oreStorage) {
            // Swapping mined ore drops to chunks is exactly the "raw ore" feature, gated per ore by EnableRawOre.
            if (!ore.enableRawOre) continue;

            // Pure vanilla ores have no ore-dictionary names to swap; everything else is also matched via the ore dict.
            if (ore.isVanilla) continue;

            for (String oreName : ore.oreNames) {
                addOre(oreName, ore);
            }
        }
    }

    public static void addOre(String oreName, Ore ore) {
        dropMap.put(OreDictionary.getOreID(oreName), storageFor(ore, oreName.contains("Nether")));
    }

    /** How many chunks one mined block of {@code ore} yields; the legacy "oreNether*" names pay out double. */
    private static DropStorage storageFor(Ore ore, boolean nether) {
        int dropMin = ore.dropCount;
        int dropMax = Math.max(ore.dropCount, ore.dropCountMax);
        if (nether) {
            dropMin *= 2;
            dropMax *= 2;
        }
        return new DropStorage(ore, dropMin, dropMax);
    }

    /**
     * Builds the mined-block lookup table. Deferred to the first mined block rather than done in {@code init} so that
     * every mod has finished registering its ore-dictionary entries by then - the same reason
     * {@link OreGenSuppressor#buildBlockTable()} defers its own table.
     */
    private static synchronized void buildBlockMap() {
        if (blockMapBuilt) return;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.enableRawOre) continue;

            // Vanilla ore blocks (coal, diamond, ...) are not ore-dicted, and lit redstone ore is a block of its own.
            if (ore.vanillaBlocks != null) {
                for (Block block : ore.vanillaBlocks) markBlock(block, OreDictionary.WILDCARD_VALUE, ore, false);
            }
            if (ore.isVanilla) continue;

            // Every block registered under one of the ore's ore-dictionary names, metadata included.
            for (String oreName : ore.oreNames) {
                boolean nether = oreName.contains("Nether");
                for (ItemStack stack : OreDictionary.getOres(oreName)) {
                    if (stack == null || stack.getItem() == null) continue;
                    markBlock(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage(), ore, nether);
                }
            }

            // Ores their own mod never puts in the ore dictionary, addressed by registry id instead.
            for (ForeignOreBlock foreign : ore.foreignBlocks) markBlock(foreign.block, foreign.meta, ore, false);
        }

        blockMapBuilt = true;
    }

    private static void markBlock(Block block, int meta, Ore ore, boolean nether) {
        if (block == null || block == Blocks.air) return;
        // Our own ore blocks carry the very same ore-dictionary names and roll their own drops in
        // BlockFortuneOre#getDrops - never swap those.
        if (block instanceof BlockFortuneOre) return;

        DropStorage[] metas = blockMap.get(block);
        if (metas == null) {
            metas = new DropStorage[16];
            blockMap.put(block, metas);
        }

        DropStorage entry = storageFor(ore, nether);
        if (meta == OreDictionary.WILDCARD_VALUE) {
            // A wildcard registration claims only the metadata values no ore has claimed by number.
            for (int i = 0; i < metas.length; i++) {
                if (metas[i] == null) metas[i] = entry;
            }
        } else if (meta >= 0 && meta < metas.length) {
            metas[meta] = entry;
        }
    }

    /** The chunk drop registered for the mined block at this metadata, or null when the block is none of ours. */
    private static DropStorage lookupBlock(Block block, int metadata) {
        DropStorage[] metas = blockMap.get(block);
        if (metas == null || metadata < 0 || metadata >= metas.length) return null;
        return metas[metadata];
    }

    /**
     * Replaces every matching ore drop in {@code drops} with this mod's chunks, in place.
     *
     * @return true when the list was changed, false when this block is none of our business (or its drops have already
     *         been converted by the other entry point).
     */
    public static boolean swapDrops(Block block, int metadata, List<ItemStack> drops, int fortune, Random rand) {
        if (!built || block == null || drops == null || drops.isEmpty()) return false;
        // Our own ore blocks roll their chunk drops in BlockFortuneOre#getDrops; never touch them again.
        if (block instanceof BlockFortuneOre) return false;

        if (!blockMapBuilt) buildBlockMap();

        // Already converted (the mixin runs before the harvest event) - nothing left to do.
        if (containsChunk(drops)) return false;

        // Primary match: the block that was mined. Whatever it would have dropped is replaced wholesale, so this also
        // covers the ores that drop a finished item (Thaumcraft amber, vanilla coal/diamond/redstone/lapis, ...).
        DropStorage blockEntry = lookupBlock(block, metadata);
        if (blockEntry != null) {
            int count = Drops
                .applyFortune(Drops.randomInRange(blockEntry.countMin, blockEntry.countMax, rand), fortune, rand);

            drops.clear();
            for (int i = 0; i < count; i++) drops.add(new ItemStack(FortuneOres.itemChunk, 1, blockEntry.ore.meta));
            return true;
        }

        // Fallback: the drop is an ore block itself (a machine handing out ore blocks, a block dropping a different
        // ore than it is). Matched through the ore dictionary of the dropped stack.
        ArrayList<ItemStack> newDrops = new ArrayList<ItemStack>(drops.size());
        boolean modified = false;

        // Only ItemBlocks can be an ore block's drop; anything else (a finished item, a bonus drop) passes through.
        for (ItemStack drop : drops) {
            DropStorage result = (drop != null && drop.getItem() instanceof ItemBlock) ? lookup(drop) : null;

            if (result == null) {
                if (drop != null) newDrops.add(drop);
                continue;
            }

            modified = true;
            // A drop of N ore blocks (some machines hand out stacks) yields N separate chunk rolls.
            for (int stack = 0; stack < Math.max(1, drop.stackSize); ++stack) {
                int count = Drops
                    .applyFortune(Drops.randomInRange(result.countMin, result.countMax, rand), fortune, rand);
                for (int i = 0; i < count; i++) newDrops.add(new ItemStack(FortuneOres.itemChunk, 1, result.ore.meta));
            }
        }

        if (!modified) return false;

        drops.clear();
        drops.addAll(newDrops);
        return true;
    }

    /** The chunk drop registered for the first ore-dictionary name of {@code stack} we know about, or null. */
    private static DropStorage lookup(ItemStack stack) {
        int[] oreIDs = OreDictionary.getOreIDs(stack);
        for (int i = 0; i < oreIDs.length; i++) {
            DropStorage result = dropMap.get(oreIDs[i]);
            if (result != null) return result;
        }
        return null;
    }

    private static boolean containsChunk(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop != null && drop.getItem() == FortuneOres.itemChunk) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void OreDictTooltip(ItemTooltipEvent event) {
        if (!event.showAdvancedItemTooltips) return;

        int[] oreIDs = OreDictionary.getOreIDs(event.itemStack);

        if (oreIDs.length <= 0) return;

        for (int i = 0; i < oreIDs.length; ++i) event.toolTip.add(OreDictionary.getOreName(oreIDs[i]));
    }

    @SubscribeEvent
    public void SwapOres(HarvestDropsEvent event) {
        if (event.isSilkTouching) return;
        if (event.drops.isEmpty()) return;
        // Our own blocks pay out their own drops and XP (BlockFortuneOre#getDrops / #getExpDrop).
        if (event.block instanceof BlockFortuneOre) return;

        // `swapped` is false when the mixin already converted the drops in Block#getDrops; the block is still one of
        // ours in that case, so the chunks must drop unconditionally and the XP below is still owed.
        boolean swapped = swapDrops(
            event.block,
            event.blockMetadata,
            event.drops,
            event.fortuneLevel,
            event.world.rand);
        if (!swapped && !containsChunk(event.drops)) return;

        event.dropChance = 1.0f;

        spawnBonusXP(event, oreOf(event.block, event.blockMetadata));
    }

    /** The ore a mined block belongs to, whichever of the two matching paths knows it, or null. */
    private static Ore oreOf(Block block, int metadata) {
        DropStorage entry = lookupBlock(block, metadata);
        if (entry != null) return entry.ore;

        Item blockItem = Item.getItemFromBlock(block);
        if (blockItem == null) return null;

        entry = dropMap.get(OreDictionary.getOreID(new ItemStack(blockItem, 1, metadata)));
        return entry != null ? entry.ore : null;
    }

    /** Grants the ore's configured mining XP for ores that, unlike the vanilla ones, drop no XP on their own. */
    private void spawnBonusXP(HarvestDropsEvent event, Ore ore) {
        if (ore == null) return;
        // Vanilla ores keep vanilla's XP payout (BlockOre#getExpDrop); we only swapped their item drops for chunks.
        if (ore.isVanilla) return;
        if (ore.xpDropMax <= 0) return;
        // Same for any other ore block that pays XP by itself (vanilla emerald, Thaumcraft's ores): topping it up
        // would hand out both payouts for one block.
        if (event.block.getExpDrop(event.world, event.blockMetadata, event.fortuneLevel) > 0) return;

        int countOrbs = Drops.randomInRange(ore.xpDropMin, ore.xpDropMax, event.world.rand);

        for (int i = 0; i < countOrbs; ++i) {
            event.world.spawnEntityInWorld(
                new EntityXPOrb(
                    event.world,
                    (double) event.x + 0.5D,
                    (double) event.y + 0.5D,
                    (double) event.z + 0.5D,
                    1));
        }
    }

    protected static class DropStorage {

        public final Ore ore;
        public final int countMin;
        public final int countMax;

        public DropStorage(Ore ore, int baseCountMin, int baseCountMax) {
            this.ore = ore;
            countMin = baseCountMin;
            countMax = baseCountMax;
        }
    }

}
