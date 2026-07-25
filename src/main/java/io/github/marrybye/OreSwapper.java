package io.github.marrybye;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityXPOrb;
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
 */
public class OreSwapper {

    /** Ore-dictionary id of an ore block -> what its chunk drop looks like. Only ores with EnableRawOre are in here. */
    protected static final HashMap<Integer, DropStorage> dropMap = new HashMap<Integer, DropStorage>();
    /** Ore blocks matched by instance rather than by ore dictionary (see {@link Ore#vanillaBlocks}). */
    protected static final HashMap<Block, Ore> vanillaMap = new HashMap<Block, Ore>();

    private static boolean built;

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

            // Ores whose block drops a finished item (all pure vanilla ores, plus e.g. emerald) are matched by block
            // instance. This is independent of ore-dictionary matching, so an ore-dicted ore can use both paths.
            if (ore.vanillaBlocks != null) {
                for (Block block : ore.vanillaBlocks) {
                    if (block != null) vanillaMap.put(block, ore);
                }
            }

            // Pure vanilla ores have no ore-dictionary names to swap; everything else is also matched via the ore dict.
            if (ore.isVanilla) continue;

            for (String oreName : ore.oreNames) {
                addOre(oreName, ore);
            }
        }
    }

    public static void addOre(String oreName, Ore ore) {
        int oreID = OreDictionary.getOreID(oreName);

        int dropMin = ore.dropCount;
        int dropMax = Math.max(ore.dropCount, ore.dropCountMax);
        if (oreName.contains("Nether")) {
            dropMin *= 2;
            dropMax *= 2;
        }

        dropMap.put(oreID, new DropStorage(ore.meta, dropMin, dropMax, ore.xpDropMin, ore.xpDropMax));
    }

    /**
     * Replaces every matching ore drop in {@code drops} with this mod's chunks, in place.
     *
     * @return true when the list was changed, false when this block is none of our business (or its drops have already
     *         been converted by the other entry point).
     */
    public static boolean swapDrops(Block block, List<ItemStack> drops, int fortune, Random rand) {
        if (!built || block == null || drops == null || drops.isEmpty()) return false;
        // Our own ore blocks roll their chunk drops in BlockFortuneOre#getDrops; never touch them again.
        if (block instanceof BlockFortuneOre) return false;
        // Already converted (the mixin runs before the harvest event) - nothing left to do.
        if (containsChunk(drops)) return false;

        Ore vanillaOre = vanillaMap.get(block);
        if (vanillaOre != null) {
            int count = Drops.rollChunks(vanillaOre, fortune, rand);

            drops.clear();
            for (int i = 0; i < count; i++) drops.add(new ItemStack(FortuneOres.itemChunk, 1, vanillaOre.meta));
            return true;
        }

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
                for (int i = 0; i < count; i++) newDrops.add(new ItemStack(FortuneOres.itemChunk, 1, result.meta));
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
        boolean swapped = swapDrops(event.block, event.drops, event.fortuneLevel, event.world.rand);
        if (!swapped && !containsChunk(event.drops)) return;

        event.dropChance = 1.0f;

        // Vanilla ores keep vanilla's XP payout (BlockOre#getExpDrop); we only swapped their item drops for chunks.
        if (vanillaMap.containsKey(event.block)) return;

        spawnBonusXP(event);
    }

    /** Grants the ore's configured mining XP for ores that, unlike the vanilla ones, drop no XP on their own. */
    private void spawnBonusXP(HarvestDropsEvent event) {
        Item blockItem = Item.getItemFromBlock(event.block);
        if (blockItem == null) return;

        int blockOreDict = OreDictionary.getOreID(new ItemStack(blockItem, 1, event.blockMetadata));
        DropStorage xpDrops = dropMap.get(blockOreDict);

        if (xpDrops == null || xpDrops.xpMax <= 0) return;

        int countOrbs = Drops.randomInRange(xpDrops.xpMin, xpDrops.xpMax, event.world.rand);

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

        public int meta;
        public int countMin;
        public int countMax;
        public int xpMin;
        public int xpMax;

        public DropStorage(int oreMeta, int baseCountMin, int baseCountMax, int dropXPMin, int dropXPMax) {
            meta = oreMeta;
            countMin = baseCountMin;
            countMax = baseCountMax;
            xpMin = dropXPMin;
            xpMax = dropXPMax;
        }
    }

}
