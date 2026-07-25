package io.github.marrybye;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Suppresses foreign ore generation per ore, driven by each ore's {@code EnableOreGen} switch: when that switch is on
 * this mod generates the ore itself, so the same ore type coming from Minecraft or any other mod through the standard
 * {@link OreGenEvent.GenerateMinable} event is denied to avoid duplicates ("full replacement"). Ores whose
 * {@code EnableOreGen} is off are left alone, so their foreign generation keeps running (useful together with
 * {@code EnableRawOre}, which turns those foreign ores into chunks when mined).
 *
 * <p>
 * Matching works two ways. Vanilla veins carry a specific {@link EventType} (COAL, IRON, ...), which is matched
 * directly. Modded veins fire {@link EventType#CUSTOM}; for those the block the {@link WorldGenMinable} would place is
 * read reflectively and matched against the ore dictionary, so any mod ore sharing the ore-dict name (oreIron, ...) of
 * a
 * replaced ore is caught too. Dirt and gravel pockets travel through the same event but are terrain, not ore, so they
 * are always left alone.
 *
 * <p>
 * The event alone cannot catch everything: generators that are not {@link WorldGenMinable} cannot be inspected, and a
 * fair number of them never fire the event in the first place - vanilla emerald is placed straight from
 * {@code BiomeGenHills.decorate}, and plenty of mods just run their own {@code IWorldGenerator}. That gap is closed by
 * the strict layer further down, which denies the block placement itself; see {@link #shouldSuppress}.
 */
public class OreGenSuppressor {

    private boolean built;
    /** Vanilla ore-gen event types to deny (one per replaced vanilla ore). */
    private final Set<EventType> suppressTypes = EnumSet.noneOf(EventType.class);
    /** Ore-dictionary ids of the replaced ores; used to catch modded CUSTOM veins by the block they place. */
    private final Set<Integer> suppressOreIds = new HashSet<Integer>();
    /** The {@link Block}-typed fields of {@link WorldGenMinable} (ore block + target block), resolved by type. */
    private Field[] minableBlockFields;

    /** Maps this mod's ore name onto the vanilla ore-gen {@link EventType} it corresponds to, or {@code null}. */
    private static EventType vanillaType(String oreName) {
        if (oreName.equalsIgnoreCase("Coal")) return EventType.COAL;
        if (oreName.equalsIgnoreCase("Iron")) return EventType.IRON;
        if (oreName.equalsIgnoreCase("Gold")) return EventType.GOLD;
        if (oreName.equalsIgnoreCase("Diamond")) return EventType.DIAMOND;
        if (oreName.equalsIgnoreCase("Redstone")) return EventType.REDSTONE;
        if (oreName.equalsIgnoreCase("Lapis")) return EventType.LAPIS;
        if (oreName.equalsIgnoreCase("Quartz")) return EventType.QUARTZ;
        return null;
    }

    /** Builds the suppression sets once, lazily, after the config has populated every ore's EnableOreGen switch. */
    private void build() {
        built = true;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.enableOreGen) continue;

            EventType type = vanillaType(ore.name);
            if (type != null) suppressTypes.add(type);

            for (String oreName : ore.oreNames) {
                if (oreName.contains("Nether")) continue;
                suppressOreIds.add(OreDictionary.getOreID(oreName));
            }
        }

        List<Field> fields = new ArrayList<Field>();
        try {
            for (Field f : WorldGenMinable.class.getDeclaredFields()) {
                if (Block.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
        } catch (Throwable ignored) {
            // Reflection unavailable: modded CUSTOM veins simply can't be inspected (vanilla types still work).
        }
        minableBlockFields = fields.toArray(new Field[fields.size()]);
    }

    @SubscribeEvent
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
        // Never cancel plain terrain fillers - only actual ore veins.
        if (event.type == EventType.DIRT || event.type == EventType.GRAVEL) return;

        if (!built) build();

        if (suppressTypes.contains(event.type)) {
            event.setResult(Result.DENY);
            return;
        }

        // Modded ores arrive as CUSTOM; identify them by the block they would place.
        if (event.type == EventType.CUSTOM && matchesSuppressedOre(event.generator)) {
            event.setResult(Result.DENY);
        }
    }

    // ---- Strict suppression: deny the block placement itself -------------------------------------------------------

    /** Foreign ore block -> which of its 16 metadata values belong to an ore we generate ourselves. */
    private static final HashMap<Block, boolean[]> suppressBlocks = new HashMap<Block, boolean[]>();
    /** False until {@link #buildBlockTable()} has found at least one block worth checking; keeps the hook free. */
    private static volatile boolean blockTableActive;
    private static boolean blockTableBuilt;

    /**
     * How deep into chunk population this thread currently is. A counter rather than a flag because generating one
     * chunk can cascade into generating its neighbours, nesting {@code populate} calls.
     */
    private static final ThreadLocal<int[]> WORLD_GEN_DEPTH = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /** Called from {@link io.github.marrybye.mixin.MixinChunkProviderServer} around chunk population. */
    public static void beginWorldGen() {
        if (!FortuneOres.strictOreGenSuppression) return;
        if (!blockTableBuilt) buildBlockTable();
        WORLD_GEN_DEPTH.get()[0]++;
    }

    public static void endWorldGen() {
        if (!FortuneOres.strictOreGenSuppression) return;
        int[] depth = WORLD_GEN_DEPTH.get();
        if (depth[0] > 0) depth[0]--;
    }

    /**
     * True when this block placement is a foreign ore we replace with our own and must therefore be dropped on the
     * floor. Called from the {@code World.setBlock} mixin, i.e. on every block the game ever places, so the cheap
     * checks come first: a plain static boolean, then the thread's world-gen depth, then the lookup table.
     *
     * <p>
     * Two deliberate restrictions keep this from reaching beyond ore generation:
     * <ul>
     * <li>it only fires while a chunk is being populated, so a player (or a machine) placing an ore block is never
     * touched;</li>
     * <li>it only fires when the ore would replace an existing block. Ore veins carve into stone, netherrack or end
     * stone; something writing ore into thin air is a structure or a decoration, and punching holes in those is not
     * what "replace ore generation" means.</li>
     * </ul>
     */
    public static boolean shouldSuppress(World world, int x, int y, int z, Block block, int meta) {
        if (!blockTableActive) return false;
        if (WORLD_GEN_DEPTH.get()[0] <= 0) return false;

        boolean[] metas = suppressBlocks.get(block);
        if (metas == null || meta < 0 || meta >= metas.length || !metas[meta]) return false;

        return world.getBlock(x, y, z) != Blocks.air;
    }

    /**
     * Collects every foreign block registered under an ore-dictionary name of an ore whose EnableOreGen is on. Built
     * lazily on the first chunk population so that every mod has long finished registering its ore dictionary entries.
     */
    private static synchronized void buildBlockTable() {
        if (blockTableBuilt) return;
        blockTableBuilt = true;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.enableOreGen) continue;

            for (String oreName : ore.oreNames) {
                for (ItemStack stack : OreDictionary.getOres(oreName)) {
                    if (stack == null) continue;
                    mark(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
                }
            }

            // Vanilla ore states that are not in the ore dictionary themselves (lit redstone ore).
            if (ore.vanillaBlocks != null) {
                for (Block block : ore.vanillaBlocks) mark(block, OreDictionary.WILDCARD_VALUE);
            }
        }

        blockTableActive = !suppressBlocks.isEmpty();
    }

    private static void mark(Block block, int meta) {
        if (block == null || block == Blocks.air) return;
        // Our own ore blocks carry the very same ore-dictionary names - never deny our own generator.
        if (block instanceof BlockFortuneOre) return;

        boolean[] metas = suppressBlocks.get(block);
        if (metas == null) {
            metas = new boolean[16];
            suppressBlocks.put(block, metas);
        }

        if (meta == OreDictionary.WILDCARD_VALUE) {
            Arrays.fill(metas, true);
        } else if (meta >= 0 && meta < metas.length) {
            metas[meta] = true;
        }
    }

    /**
     * Best-effort identification of a modded ore vein: reads the block(s) the {@link WorldGenMinable} would place and
     * returns true when any ore-dictionaries to an ore whose EnableOreGen is on. Generators that are not
     * {@link WorldGenMinable} cannot be inspected and are left alone.
     */
    private boolean matchesSuppressedOre(WorldGenerator generator) {
        if (suppressOreIds.isEmpty()) return false;
        if (minableBlockFields.length == 0) return false;
        if (!(generator instanceof WorldGenMinable)) return false;

        for (Field f : minableBlockFields) {
            try {
                Object value = f.get(generator);
                if (!(value instanceof Block)) continue;

                Item item = Item.getItemFromBlock((Block) value);
                if (item == null) continue;

                int[] ids = OreDictionary.getOreIDs(new ItemStack(item, 1, 0));
                for (int id : ids) {
                    if (suppressOreIds.contains(id)) return true;
                }
            } catch (Throwable ignored) {
                // Unreadable field: skip it.
            }
        }
        return false;
    }
}
