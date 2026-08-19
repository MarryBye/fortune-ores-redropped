package io.github.marrybye;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;

/**
 * Thaumcraft aspects for the ore chunks and the generated ore blocks.
 *
 * <p>
 * Thaumcraft derives an item's aspects from its crafting recipe, and an ore chunk has none - it is a world drop. Left
 * alone it therefore ends up with no aspects at all, which means it cannot be scanned, cannot go into a crucible and
 * cannot pay for an infusion, while the very same material dug out of Thaumcraft's own ore can do all three. This
 * class closes that gap the way Thaumcraft describes an ore itself: the aspects of what the chunk smelts into, plus
 * {@code terra} for the rock it came out of.
 *
 * <p>
 * Everything is reflective and guarded by {@link Loader#isModLoaded}, so Thaumcraft stays a soft dependency; a
 * material whose smelted form Thaumcraft knows nothing about falls back to the aspects any ore has - {@code terra}
 * with {@code metallum} for a metal or {@code vitreus} for a gem.
 */
public final class ThaumcraftCompat {

    private ThaumcraftCompat() {}

    private static final String MODID = "Thaumcraft";

    /** How much {@code terra} the surrounding rock is worth; Thaumcraft's own ores sit in this range. */
    private static final int ROCK_ASPECT = 1;

    /** Registers the aspects of every enabled ore's chunk and ore blocks. Called from {@link FortuneOres#postInit}. */
    public static void register() {
        if (!FortuneOres.thaumcraftIntegration) return;
        if (!Loader.isModLoaded(MODID)) return;

        Aspects aspects = Aspects.create();
        if (aspects == null) {
            FMLLog.warning(
                "[FortuneOres] Thaumcraft is installed but its aspect API could not be reached; the ore chunks stay "
                    + "without aspects.");
            return;
        }

        int chunks = 0;
        int blocks = 0;
        int derived = 0;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.isActive()) continue;

            ItemStack smeltResult = FortuneOres.resolveSmeltResult(ore);
            Object list = smeltResult != null ? aspects.of(smeltResult) : null;
            if (list != null) {
                derived++;
            } else {
                list = aspects.fallbackFor(ore);
            }
            if (list == null) continue;

            Object chunkAspects = aspects.plusEarth(list, Math.max(1, ore.smeltCount));
            if (chunkAspects == null) continue;

            if (aspects.assign(new ItemStack(FortuneOres.itemChunk, 1, ore.meta), chunkAspects)) chunks++;

            // A silk-touched ore block holds everything the chunks it would have dropped hold.
            Object blockAspects = aspects.plusEarth(list, Math.max(1, ore.dropCount) * Math.max(1, ore.smeltCount));
            if (blockAspects == null) continue;

            int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
            for (OreHost host : OreHost.values()) {
                Block block = host.blockFor(ore);
                if (block == null) continue;

                if (aspects.assign(new ItemStack(block, 1, offset), blockAspects)) blocks++;
            }
        }

        FMLLog.info(
            "[FortuneOres] Thaumcraft: aspects registered for %d ore chunks (%d taken from the smelted material) and "
                + "%d ore blocks.",
            chunks,
            derived,
            blocks);
    }

    // ---- Thaumcraft's aspect API, reached reflectively ------------------------------------------------------------

    /** The handful of Thaumcraft entry points this needs, resolved once. */
    private static final class Aspects {

        private final Constructor<?> newList;
        private final Method add;
        private final Method getAspects;
        private final Method getAmount;
        private final Method registerObjectTag;
        /** {@code ThaumcraftCraftingManager.getObjectTags}; absent on a build that moved it, which is not fatal. */
        private final Method objectTags;
        private final Object earth;
        private final Object metal;
        private final Object crystal;

        private Aspects(Constructor<?> newList, Method add, Method getAspects, Method getAmount,
            Method registerObjectTag, Method objectTags, Object earth, Object metal, Object crystal) {
            this.newList = newList;
            this.add = add;
            this.getAspects = getAspects;
            this.getAmount = getAmount;
            this.registerObjectTag = registerObjectTag;
            this.objectTags = objectTags;
            this.earth = earth;
            this.metal = metal;
            this.crystal = crystal;
        }

        static Aspects create() {
            try {
                Class<?> listClass = Class.forName("thaumcraft.api.aspects.AspectList");
                Class<?> aspectClass = Class.forName("thaumcraft.api.aspects.Aspect");

                Method getAspect = aspectClass.getMethod("getAspect", String.class);
                Object earth = getAspect.invoke(null, "terra");
                Object metal = getAspect.invoke(null, "metallum");
                Object crystal = getAspect.invoke(null, "vitreus");
                if (earth == null || metal == null || crystal == null) return null;

                Method registerObjectTag = Class.forName("thaumcraft.api.ThaumcraftApi")
                    .getMethod("registerObjectTag", ItemStack.class, listClass);

                Method objectTags = null;
                try {
                    objectTags = Class.forName("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager")
                        .getMethod("getObjectTags", ItemStack.class);
                } catch (Throwable ignored) {
                    // Only costs us the derived aspects; the fallback below still applies.
                }

                return new Aspects(
                    listClass.getConstructor(),
                    listClass.getMethod("add", aspectClass, int.class),
                    listClass.getMethod("getAspects"),
                    listClass.getMethod("getAmount", aspectClass),
                    registerObjectTag,
                    objectTags,
                    earth,
                    metal,
                    crystal);
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** The aspects Thaumcraft already knows for an item, or null when it knows none. */
        Object of(ItemStack stack) {
            if (objectTags == null) return null;

            try {
                Object list = objectTags.invoke(null, stack);
                return list != null && sizeOf(list) > 0 ? list : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        /**
         * What an ore is worth when Thaumcraft has never heard of the material: the aspects every ore carries. A metal
         * is one the pack has an {@code ingot} for; anything else is treated as a gem or crystal.
         */
        Object fallbackFor(Ore ore) {
            boolean isMetal = false;
            for (String base : ore.smeltBases) {
                if (FortuneOres.firstOreDictItem("ingot" + base) != null) {
                    isMetal = true;
                    break;
                }
            }

            try {
                Object list = newList.newInstance();
                add.invoke(list, isMetal ? metal : crystal, Integer.valueOf(2));
                return list;
            } catch (Throwable ignored) {
                return null;
            }
        }

        /**
         * A copy of {@code source} scaled by {@code times}, with the rock the ore sits in added on top. Scaling is
         * what keeps an ore that drops several chunks from being worth several times its own material.
         */
        Object plusEarth(Object source, int times) {
            try {
                Object list = newList.newInstance();
                for (Object aspect : (Object[]) getAspects.invoke(source)) {
                    if (aspect == null) continue;

                    int amount = ((Integer) getAmount.invoke(source, aspect)).intValue();
                    int scaled = Math.max(1, amount * Math.max(1, times));
                    add.invoke(list, aspect, Integer.valueOf(Math.min(64, scaled)));
                }
                add.invoke(list, earth, Integer.valueOf(ROCK_ASPECT));
                return list;
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean assign(ItemStack stack, Object list) {
            try {
                registerObjectTag.invoke(null, stack, list);
                return true;
            } catch (Throwable t) {
                FMLLog.warning("[FortuneOres] Thaumcraft rejected the aspects for %s: %s", stack, t);
                return false;
            }
        }

        private int sizeOf(Object list) throws Exception {
            Object[] aspects = (Object[]) getAspects.invoke(list);
            return aspects == null ? 0 : aspects.length;
        }
    }
}
