package io.github.marrybye;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;

/**
 * Ore processing ("ore doubling") for this mod's chunks and ore blocks in whichever tech mods the pack ships.
 *
 * <p>
 * Every 1.7.10 tech mod builds its ore-processing recipes from a hard-coded list of materials of its own - Mekanism
 * iterates its {@code Resource} enum, Immersive Engineering its {@code addOreProcessingRecipe} list, Thermal Expansion
 * its metals - and only then looks the ore dictionary up for those few names. That is why registering the ore blocks as
 * {@code oreIron} is enough for iron (Mekanism's Enrichment Chamber picks the block up at its own post-init, since this
 * mod ore-dicts in init), while the hundred materials outside those lists - Rutile, Titanium, the Metallurgy metals,
 * the gems - have no recipe in any machine at all. This class fills exactly that gap: for every enabled ore it
 * registers the doubling recipe into every supported machine that is installed.
 *
 * <p>
 * Everything here goes through reflection and {@link Loader#isModLoaded}, so none of these mods is a build dependency
 * and any of them may be missing at runtime. A machine that throws is dropped from the list instead of spamming the log
 * once per ore.
 *
 * <p>
 * What a recipe pays out is {@link #resolveOutput}: a dust when the pack has one, otherwise the same finished item the
 * chunk smelts into (gem, crystal, shard), otherwise the chunk itself. {@code ingot} is deliberately never used - a
 * grinder handing out ingots would skip the smelting step every one of these machines is built around.
 *
 * <p>
 * Both routes out of an ore block pay the same: the chunk it drops when mined, and the block itself when it is silk
 * touched. The block's payout is multiplied by its <em>minimum</em> chunk drop, so the silk-touch route never beats
 * simply mining the ore and processing the chunks - that route still gets Fortune on top.
 */
public final class MachineCompat {

    private MachineCompat() {}

    /** Registers the ore-processing recipes for every enabled ore. Called from {@link FortuneOres#postInit}. */
    public static void register() {
        if (!FortuneOres.techModIntegration) return;

        List<Machine> machines = new ArrayList<Machine>();
        addMachine(machines, EnrichmentChamber.create());
        addMachine(machines, Pulverizer.create());
        addMachine(machines, Macerator.create());
        addMachine(machines, Crusher.create());

        if (machines.isEmpty()) {
            FMLLog.info("[FortuneOres] No supported ore-processing machine found; chunks stay furnace-only.");
            return;
        }

        StringBuilder found = new StringBuilder();
        for (Machine machine : machines) {
            if (found.length() > 0) found.append(", ");
            found.append(machine.name());
        }

        int recipes = 0;
        int materials = 0;
        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.enabled) continue;

            int added = registerOre(ore, machines);
            recipes += added;
            if (added > 0) materials++;
        }

        FMLLog.info(
            "[FortuneOres] Ore processing: %d recipes for %d materials registered in %s (x%d per chunk).",
            recipes,
            materials,
            found,
            FortuneOres.machineOutputMultiplier);
    }

    /** Registers one ore's chunk and ore blocks in every machine; returns how many recipes were accepted. */
    private static int registerOre(Ore ore, List<Machine> machines) {
        int multiplier = FortuneOres.machineOutputMultiplier;
        int smeltCount = Math.max(1, ore.smeltCount);
        int dropCount = Math.max(1, ore.dropCount);

        ItemStack chunk = new ItemStack(FortuneOres.itemChunk, 1, ore.meta);
        ItemStack output = resolveOutput(ore);
        int recipes = 0;

        if (output == null) {
            // Nothing to grind this material into: the ore block still doubles, into the chunks it would have dropped.
            // The chunk itself is left alone - it is the end of the line until the pack provides a dust or a gem.
            return registerBlocks(ore, machines, sized(chunk, multiplier * dropCount));
        }

        // With AllowProcessing off the chunk *is* the pack's dust entry, so grinding it into a dust would either be a
        // no-op or, when another mod provides a real dust, a duplication loop.
        if (FortuneOres.allowProcessing) {
            recipes += addRecipe(machines, chunk, sized(output, multiplier * smeltCount));
        }
        recipes += registerBlocks(ore, machines, sized(output, multiplier * dropCount * smeltCount));
        return recipes;
    }

    /** Registers the same output for all four host variants (stone / deepslate / netherrack / end stone). */
    private static int registerBlocks(Ore ore, List<Machine> machines, ItemStack output) {
        int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
        int recipes = 0;

        for (OreHost host : OreHost.values()) {
            Block block = host.blockFor(ore);
            if (block == null) continue;

            recipes += addRecipe(machines, new ItemStack(block, 1, offset), output);
        }
        return recipes;
    }

    /**
     * What one chunk of this ore is worth in a grinding machine. A dust first - it is what an ore-doubling machine is
     * meant to hand out, since it still has to be smelted - then whatever the chunk smelts into short of an ingot: the
     * ore's explicit smelting targets (Thaumcraft's shards, AE2's certus crystal), then a gem, then a crystal.
     */
    private static ItemStack resolveOutput(Ore ore) {
        ItemStack dust = firstWithPrefix("dust", ore);
        if (dust != null) return dust;

        for (String smeltName : ore.smeltNames) {
            ItemStack match = FortuneOres.firstOreDictItem(smeltName);
            if (match != null) return match;
        }

        ItemStack gem = firstWithPrefix("gem", ore);
        if (gem != null) return gem;

        return firstWithPrefix("crystal", ore);
    }

    /** The first item registered under {@code prefix + } one of the ore's names or aliases, or null. */
    private static ItemStack firstWithPrefix(String prefix, Ore ore) {
        // Vanilla ores (coal, diamond, ...) smelt into a fixed item and carry no smelting bases, so use their name.
        if (ore.smeltBases.isEmpty()) return FortuneOres.firstOreDictItem(prefix + ore.name);

        for (String base : ore.smeltBases) {
            ItemStack match = FortuneOres.firstOreDictItem(prefix + base);
            if (match != null) return match;
        }
        return null;
    }

    /**
     * Offers one recipe to every machine still in the list. A machine that throws is removed rather than left to fail
     * once per ore - a mod whose recipe API moved is a compatibility gap, not a reason to fill the log.
     *
     * @return how many machines accepted the recipe.
     */
    private static int addRecipe(List<Machine> machines, ItemStack input, ItemStack output) {
        if (input == null || output == null || output.getItem() == null) return 0;
        // A recipe turning an item into itself is either a no-op or an infinite loop, depending on the machine.
        if (input.getItem() == output.getItem() && input.getItemDamage() == output.getItemDamage()) return 0;

        int accepted = 0;
        for (Iterator<Machine> it = machines.iterator(); it.hasNext();) {
            Machine machine = it.next();
            try {
                if (machine.add(input.copy(), output.copy())) accepted++;
            } catch (Throwable t) {
                FMLLog.warning(
                    "[FortuneOres] %s rejected an ore-processing recipe (%s); no more recipes will be sent to it: %s",
                    machine.name(),
                    output,
                    t);
                it.remove();
            }
        }
        return accepted;
    }

    private static void addMachine(List<Machine> machines, Machine machine) {
        if (machine != null) machines.add(machine);
    }

    /** A copy of {@code stack} holding {@code count} items, kept inside what the item can actually stack to. */
    private static ItemStack sized(ItemStack stack, int count) {
        ItemStack result = stack.copy();
        int max = Math.min(64, Math.max(1, stack.getMaxStackSize()));
        result.stackSize = Math.max(1, Math.min(count, max));
        return result;
    }

    /** A public static method, or null when the class or the method is not there (i.e. the mod is a different one). */
    private static Method findMethod(String className, String methodName, Class<?>... parameters) {
        try {
            return Class.forName(className)
                .getMethod(methodName, parameters);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---- The machines ----------------------------------------------------------------------------------------------

    /** One tech mod's grinding machine, reached reflectively so that mod stays a soft dependency. */
    private interface Machine {

        /** Name for the log line. */
        String name();

        /**
         * Registers {@code input -> output}.
         *
         * @return true when the recipe was added, false when the machine already had one for this input.
         */
        boolean add(ItemStack input, ItemStack output) throws Exception;
    }

    /**
     * Mekanism's Enrichment Chamber - the first step of its ore-processing chain, and the one it only ever sets up for
     * the eight materials of its own {@code Resource} enum plus a handful of hard-coded names.
     */
    private static final class EnrichmentChamber implements Machine {

        private final Method addRecipe;

        private EnrichmentChamber(Method addRecipe) {
            this.addRecipe = addRecipe;
        }

        static Machine create() {
            if (!Loader.isModLoaded("Mekanism")) return null;

            // mekanism.api.recipe.RecipeHelper only reflects into this very method and swallows every error it hits,
            // so go for the real one first and keep the (deprecated) API wrapper as the fallback.
            Method addRecipe = findMethod(
                "mekanism.common.recipe.RecipeHandler",
                "addEnrichmentChamberRecipe",
                ItemStack.class,
                ItemStack.class);
            if (addRecipe == null) {
                addRecipe = findMethod(
                    "mekanism.api.recipe.RecipeHelper",
                    "addEnrichmentChamberRecipe",
                    ItemStack.class,
                    ItemStack.class);
            }
            return addRecipe == null ? null : new EnrichmentChamber(addRecipe);
        }

        @Override
        public String name() {
            return "Mekanism Enrichment Chamber";
        }

        @Override
        public boolean add(ItemStack input, ItemStack output) throws Exception {
            // Mekanism keys its recipes by input, so a material it handles itself simply keeps one recipe either way.
            addRecipe.invoke(null, input, output);
            return true;
        }
    }

    /** Thermal Expansion's Pulverizer, through the CoFH API helper. It ignores an input it already has a recipe for. */
    private static final class Pulverizer implements Machine {

        /** Thermal Expansion pulverizes its own ores at 4000 RF; ours cost the same. */
        private static final int ENERGY = 4000;

        private final Method addRecipe;

        private Pulverizer(Method addRecipe) {
            this.addRecipe = addRecipe;
        }

        static Machine create() {
            if (!Loader.isModLoaded("ThermalExpansion")) return null;

            Method addRecipe = findMethod(
                "cofh.api.modhelpers.ThermalExpansionHelper",
                "addPulverizerRecipe",
                int.class,
                ItemStack.class,
                ItemStack.class);
            return addRecipe == null ? null : new Pulverizer(addRecipe);
        }

        @Override
        public String name() {
            return "Thermal Expansion Pulverizer";
        }

        @Override
        public boolean add(ItemStack input, ItemStack output) throws Exception {
            addRecipe.invoke(null, Integer.valueOf(ENERGY), input, output);
            return true;
        }
    }

    /**
     * IndustrialCraft 2's Macerator. IC2 registers its own ore recipes against ore-dictionary names, which already
     * covers this mod's blocks for the materials IC2 knows, so an input that already has a recipe is left alone.
     */
    private static final class Macerator implements Machine {

        private final Object manager;
        private final Constructor<?> inputCtor;
        private final Method addRecipe;
        private final Method getOutputFor;
        /** Newer IC2 builds take an extra "overwrite an existing recipe" flag between the metadata and the outputs. */
        private final boolean hasOverwriteFlag;

        private Macerator(Object manager, Constructor<?> inputCtor, Method addRecipe, Method getOutputFor,
            boolean hasOverwriteFlag) {
            this.manager = manager;
            this.inputCtor = inputCtor;
            this.addRecipe = addRecipe;
            this.getOutputFor = getOutputFor;
            this.hasOverwriteFlag = hasOverwriteFlag;
        }

        static Machine create() {
            if (!Loader.isModLoaded("IC2")) return null;

            try {
                Field maceratorField = Class.forName("ic2.api.recipe.Recipes")
                    .getField("macerator");
                Object manager = maceratorField.get(null);
                if (manager == null) return null;

                Class<?> inputClass = Class.forName("ic2.api.recipe.RecipeInputItemStack");
                Constructor<?> inputCtor = inputClass.getConstructor(ItemStack.class);

                // Resolved on the public interface rather than on the manager's own (package-private) class, which
                // would not be callable. Its signature gained a flag between IC2 builds, so match it by shape.
                Method addRecipe = null;
                boolean overwriteFlag = false;
                for (Method candidate : Class.forName("ic2.api.recipe.IMachineRecipeManager")
                    .getMethods()) {
                    if (!candidate.getName()
                        .equals("addRecipe")) continue;

                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (parameters.length < 3) continue;
                    if (!parameters[0].isAssignableFrom(inputClass)) continue;
                    if (parameters[parameters.length - 1] != ItemStack[].class) continue;

                    if (parameters.length == 3) {
                        addRecipe = candidate;
                        overwriteFlag = false;
                        break;
                    }
                    if (parameters.length == 4 && parameters[2] == boolean.class) {
                        addRecipe = candidate;
                        overwriteFlag = true;
                    }
                }
                if (addRecipe == null) return null;

                Method getOutputFor = findMethod(
                    "ic2.api.recipe.IMachineRecipeManager",
                    "getOutputFor",
                    ItemStack.class,
                    boolean.class);

                return new Macerator(manager, inputCtor, addRecipe, getOutputFor, overwriteFlag);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public String name() {
            return "IC2 Macerator";
        }

        @Override
        public boolean add(ItemStack input, ItemStack output) throws Exception {
            if (getOutputFor != null && getOutputFor.invoke(manager, input, Boolean.FALSE) != null) return false;

            Object recipeInput = inputCtor.newInstance(input);
            ItemStack[] outputs = new ItemStack[] { output };
            if (hasOverwriteFlag) {
                addRecipe.invoke(manager, recipeInput, (NBTTagCompound) null, Boolean.FALSE, outputs);
            } else {
                addRecipe.invoke(manager, recipeInput, (NBTTagCompound) null, outputs);
            }
            return true;
        }
    }

    /**
     * Immersive Engineering's Crusher. Like IC2 it registers its own recipes against ore-dictionary names, so an input
     * one of those already covers is skipped.
     */
    private static final class Crusher implements Machine {

        /** What Immersive Engineering charges for its own ore -&gt; 2 dust recipes. */
        private static final int ENERGY = 6000;

        private final Method addRecipe;
        private final Method findRecipe;

        private Crusher(Method addRecipe, Method findRecipe) {
            this.addRecipe = addRecipe;
            this.findRecipe = findRecipe;
        }

        static Machine create() {
            if (!Loader.isModLoaded("ImmersiveEngineering")) return null;

            Method addRecipe = findMethod(
                "blusunrize.immersiveengineering.api.crafting.CrusherRecipe",
                "addRecipe",
                ItemStack.class,
                Object.class,
                int.class);
            if (addRecipe == null) return null;

            Method findRecipe = findMethod(
                "blusunrize.immersiveengineering.api.crafting.CrusherRecipe",
                "findRecipe",
                ItemStack.class);
            return new Crusher(addRecipe, findRecipe);
        }

        @Override
        public String name() {
            return "Immersive Engineering Crusher";
        }

        @Override
        public boolean add(ItemStack input, ItemStack output) throws Exception {
            if (findRecipe != null && findRecipe.invoke(null, input) != null) return false;

            addRecipe.invoke(null, output, input, Integer.valueOf(ENERGY));
            return true;
        }
    }
}
