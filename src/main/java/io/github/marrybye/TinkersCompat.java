package io.github.marrybye;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;

/**
 * Tinkers' Construct smeltery support: an ore chunk (and the ore block it came from) melts into the material's molten
 * fluid instead of being furnace-only.
 *
 * <p>
 * Tinkers builds its own melting recipes from a hard-coded material list plus whatever another mod registers by hand,
 * exactly like the grinders in {@link MachineCompat} do, so without this every chunk outside iron/gold/copper/tin/…
 * is simply not a smeltery input at all. Here every enabled ore whose material has a molten fluid gets one.
 *
 * <p>
 * The fluid is looked up in Forge's own {@link FluidRegistry} rather than in a table of our own, under the naming
 * conventions the 1.7.10 ecosystem settled on - {@code iron.molten} (Tinkers), {@code molten.iron} (GregTech and the
 * mods that copied it), {@code emerald.liquid} and the bare material name. That means a pack's own molten metals work
 * without this mod knowing they exist, which is the whole point: the chunk carries the ore's ore-dictionary names, so
 * it has to behave like the ore everywhere the ore is accepted.
 *
 * <p>
 * How much comes out follows the same rule the grinders use: a chunk is worth {@code MachineOutputMultiplier} ingots
 * (2 by default - the ore doubling every smeltery is built around), and a silk-touched ore block is worth that times
 * the chunks it would have dropped, so melting the block is never better than mining it and melting the chunks. An
 * input Tinkers already has a recipe for is left alone, so its own materials keep their own balance and temperatures.
 */
public final class TinkersCompat {

    private TinkersCompat() {}

    private static final String MODID = "TConstruct";
    private static final String SMELTERY_CLASS = "tconstruct.library.crafting.Smeltery";
    private static final String FLUID_TYPE_CLASS = "tconstruct.library.crafting.FluidType";

    /** Millibuckets one ingot is worth, when Tinkers' own value cannot be read. */
    private static final int DEFAULT_INGOT_MB = 144;
    /** Melting point for a fluid Tinkers has no {@code FluidType} for; roughly its own iron. */
    private static final int DEFAULT_TEMPERATURE = 600;

    /** The suffix/prefix shapes a molten fluid is registered under across 1.7.10 mods, in the order they are tried. */
    private static final String[] FLUID_NAME_PATTERNS = { "%s.molten", "molten.%s", "molten%s", "%s.liquid", "%s" };

    /**
     * Registers the melting recipes for every enabled ore. Called from {@link FortuneOres#postInit}, late enough that
     * every mod has registered its fluids and Tinkers has built its own recipe list.
     */
    public static void register() {
        if (!FortuneOres.tinkersIntegration) return;
        if (!Loader.isModLoaded(MODID)) return;

        Smeltery smeltery = Smeltery.create();
        if (smeltery == null) {
            FMLLog.warning(
                "[FortuneOres] Tinkers' Construct is installed but its smeltery recipe API could not be reached; "
                    + "the chunks stay furnace-only.");
            return;
        }

        int ingot = smeltery.ingotLiquidValue();
        int recipes = 0;
        int materials = 0;
        int skipped = 0;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.isActive()) continue;

            Fluid fluid = resolveFluid(ore);
            if (fluid == null) {
                // Gems, quartz and every material whose mod ships no molten form: nothing to melt them into, which is
                // not a fault - the chunk keeps its furnace recipe.
                skipped++;
                continue;
            }

            int added = registerOre(ore, fluid, smeltery, ingot);
            recipes += added;
            if (added > 0) materials++;
        }

        FMLLog.info(
            "[FortuneOres] Tinkers' Construct smeltery: %d melting recipes for %d materials (x%d per chunk, %d mB per "
                + "ingot); %d ores have no molten fluid in this pack.",
            recipes,
            materials,
            FortuneOres.machineOutputMultiplier,
            ingot,
            skipped);
    }

    /** Registers one ore's chunk and its four host blocks; returns how many recipes were accepted. */
    private static int registerOre(Ore ore, Fluid fluid, Smeltery smeltery, int ingot) {
        int multiplier = Math.max(1, FortuneOres.machineOutputMultiplier);
        int smeltCount = Math.max(1, ore.smeltCount);
        int dropCount = Math.max(1, ore.dropCount);
        int temperature = smeltery.temperatureOf(fluid);

        int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
        // What the smeltery shows melting inside itself. The ore's own block when the pack generates one, so the
        // animation matches what was mined; Tinkers' own render block for the fluid otherwise.
        Block renderBlock = OreHost.STONE.blockFor(ore);
        int renderMeta = renderBlock != null ? offset : smeltery.renderMetaOf(fluid);
        if (renderBlock == null) renderBlock = smeltery.renderBlockOf(fluid);

        int recipes = 0;
        ItemStack chunk = new ItemStack(FortuneOres.itemChunk, 1, ore.meta);
        if (smeltery.add(chunk, renderBlock, renderMeta, temperature, fluid, ingot * multiplier * smeltCount)) {
            recipes++;
        }

        int blockAmount = ingot * multiplier * dropCount * smeltCount;
        for (OreHost host : OreHost.values()) {
            Block block = host.blockFor(ore);
            if (block == null) continue;

            if (smeltery.add(new ItemStack(block, 1, offset), block, offset, temperature, fluid, blockAmount)) {
                recipes++;
            }
        }
        return recipes;
    }

    /**
     * The molten fluid for an ore's material, or null when the pack has none. Every name the ore answers to is tried -
     * its own plus the aliases - so {@code Aluminum} finds {@code aluminium.molten} just as well, and Rutile falls back
     * to molten titanium the same way its chunk falls back to a titanium ingot.
     */
    private static Fluid resolveFluid(Ore ore) {
        for (String base : materialNames(ore)) {
            String lower = base.toLowerCase(Locale.ROOT);
            for (String pattern : FLUID_NAME_PATTERNS) {
                Fluid fluid = FluidRegistry.getFluid(String.format(pattern, lower));
                if (fluid != null) return fluid;
            }
        }
        return null;
    }

    /** The material names to try, in priority order: the ore's smelting bases, or its own name when it has none. */
    private static Iterable<String> materialNames(Ore ore) {
        if (!ore.smeltBases.isEmpty()) return ore.smeltBases;

        java.util.List<String> names = new java.util.ArrayList<String>(1);
        names.add(ore.name);
        return names;
    }

    // ---- The smeltery, reached reflectively so Tinkers stays a soft dependency ------------------------------------

    /**
     * Tinkers' {@code Smeltery} recipe registry. Everything is resolved by shape rather than by an exact signature:
     * the melting registration gained and lost parameters across the 1.7.10 builds, and a pack running a fork should
     * still get its recipes rather than a stack trace.
     */
    private static final class Smeltery {

        private final Object instance;
        private final Method addMelting;
        private final Method getResult;
        private final int ingotValue;
        /** Fluid -> {temperature, render block, render metadata}, read out of Tinkers' own FluidType table. */
        private final Map<Fluid, FluidInfo> fluidInfo;

        private Smeltery(Object instance, Method addMelting, Method getResult, int ingotValue,
            Map<Fluid, FluidInfo> fluidInfo) {
            this.instance = instance;
            this.addMelting = addMelting;
            this.getResult = getResult;
            this.ingotValue = ingotValue;
            this.fluidInfo = fluidInfo;
        }

        static Smeltery create() {
            try {
                Class<?> smelteryClass = Class.forName(SMELTERY_CLASS);

                Method addMelting = null;
                for (Method candidate : smelteryClass.getMethods()) {
                    if (!candidate.getName()
                        .equals("addMelting")) continue;

                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (parameters.length != 5) continue;
                    if (parameters[0] != ItemStack.class) continue;
                    if (!parameters[1].isAssignableFrom(Block.class)) continue;
                    if (parameters[2] != int.class || parameters[3] != int.class) continue;
                    if (parameters[4] != FluidStack.class) continue;

                    addMelting = candidate;
                    break;
                }
                if (addMelting == null) return null;

                Object instance = Modifier.isStatic(addMelting.getModifiers()) ? null : instanceOf(smelteryClass);
                if (instance == null && !Modifier.isStatic(addMelting.getModifiers())) return null;

                // "does this already melt?" - matched by name as well as by shape, so an unrelated one-argument
                // helper can never be mistaken for it and silently make every recipe look like a duplicate.
                Method getResult = null;
                for (Method candidate : smelteryClass.getMethods()) {
                    if (!candidate.getName()
                        .equals("getSmelteryResult")) continue;

                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (parameters.length != 1 || parameters[0] != ItemStack.class) continue;
                    if (candidate.getReturnType() != FluidStack.class) continue;

                    getResult = candidate;
                    break;
                }

                return new Smeltery(instance, addMelting, getResult, readIngotValue(), readFluidTypes());
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** The singleton a non-static recipe method would have to be called on. */
        private static Object instanceOf(Class<?> smelteryClass) {
            try {
                Field field = smelteryClass.getField("instance");
                return field.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** Tinkers' own "one ingot is this many millibuckets", so a pack that retuned it stays consistent. */
        private static int readIngotValue() {
            for (String owner : new String[] { "tconstruct.TConstruct", "tconstruct.library.TConstructRegistry" }) {
                try {
                    Field field = Class.forName(owner)
                        .getField("ingotLiquidValue");
                    int value = field.getInt(null);
                    if (value > 0) return value;
                } catch (Throwable ignored) {
                    // Try the next place it has lived.
                }
            }
            return DEFAULT_INGOT_MB;
        }

        /**
         * Reads Tinkers' {@code FluidType} table into a fluid-keyed map. That table is where the melting point of
         * every material Tinkers knows lives, so taking the temperature from it means molten iron from a chunk melts
         * at exactly the temperature molten iron from anything else does - guessing one would either make the ore
         * cheaper than Tinkers' own or impossible to melt at all.
         */
        private static Map<Fluid, FluidInfo> readFluidTypes() {
            Map<Fluid, FluidInfo> map = new HashMap<Fluid, FluidInfo>();
            try {
                Class<?> fluidTypeClass = Class.forName(FLUID_TYPE_CLASS);
                Field typesField = fluidTypeClass.getField("fluidTypes");
                Object types = typesField.get(null);
                if (!(types instanceof Map)) return map;

                Field fluidField = fluidTypeClass.getField("fluid");
                Field temperatureField = fluidTypeClass.getField("baseTemperature");
                Field blockField = fluidTypeClass.getField("baseBlock");
                Field metaField = fluidTypeClass.getField("metaData");

                for (Object type : ((Map<?, ?>) types).values()) {
                    if (type == null) continue;

                    Object fluid = fluidField.get(type);
                    if (!(fluid instanceof Fluid)) continue;

                    Object block = blockField.get(type);
                    map.put(
                        (Fluid) fluid,
                        new FluidInfo(
                            temperatureField.getInt(type),
                            block instanceof Block ? (Block) block : null,
                            metaField.getInt(type)));
                }
            } catch (Throwable ignored) {
                // No table, no temperatures - every recipe falls back to the default melting point below.
            }
            return map;
        }

        int ingotLiquidValue() {
            return ingotValue;
        }

        int temperatureOf(Fluid fluid) {
            FluidInfo info = fluidInfo.get(fluid);
            if (info != null && info.temperature > 0) return info.temperature;
            // Forge's own fluid temperature is in Kelvin and defaults to 300 for everything, so it is only usable as a
            // hint: anything at or below room temperature means "the mod never set one".
            int declared = fluid.getTemperature();
            return declared > 400 ? declared : DEFAULT_TEMPERATURE;
        }

        Block renderBlockOf(Fluid fluid) {
            FluidInfo info = fluidInfo.get(fluid);
            if (info != null && info.block != null) return info.block;
            // Only ever a stand-in for the block shown melting inside the smeltery.
            return Blocks.stone;
        }

        int renderMetaOf(Fluid fluid) {
            FluidInfo info = fluidInfo.get(fluid);
            return info != null && info.block != null ? info.meta : 0;
        }

        /**
         * Adds one melting recipe.
         *
         * @return true when it was accepted, false when Tinkers already melts this input (its own materials keep
         *         their own recipe) or when the call failed.
         */
        boolean add(ItemStack input, Block renderBlock, int renderMeta, int temperature, Fluid fluid, int amount) {
            if (input == null || input.getItem() == null || amount <= 0) return false;

            try {
                if (getResult != null && getResult.invoke(instance, input) != null) return false;

                addMelting.invoke(
                    instance,
                    input,
                    renderBlock,
                    Integer.valueOf(renderMeta),
                    Integer.valueOf(temperature),
                    new FluidStack(fluid, amount));
                return true;
            } catch (Throwable t) {
                FMLLog.warning("[FortuneOres] Tinkers' Construct rejected a melting recipe for %s: %s", input, t);
                return false;
            }
        }
    }

    /** What Tinkers knows about one molten fluid: its melting point and the block shown melting in the smeltery. */
    private static final class FluidInfo {

        final int temperature;
        final Block block;
        final int meta;

        FluidInfo(int temperature, Block block, int meta) {
            this.temperature = temperature;
            this.block = block;
            this.meta = meta;
        }
    }
}
