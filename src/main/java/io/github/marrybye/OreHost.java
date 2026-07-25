package io.github.marrybye;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * One terrain host the mod's ores can generate in. Each host owns its own {@link BlockFortuneOre} instance (all of the
 * mod's materials live inside it as metadata), a set of icons ("{@code <infix>_<material>}") and a registry id. The
 * world generator is host-driven: for every block a vein would fill it looks at what terrain is already there and swaps
 * in the variant whose {@link #source()} matches, so a single code path places stone ore in the overworld, netherrack
 * ore in the Nether and end-stone ore in the End without knowing which dimension it is running in.
 *
 * <p>
 * Order is append-only: {@link #STONE} and {@link #DEEPSLATE} keep their original registry ids
 * ("fortuneOre"/"fortuneDeepslateOre") so existing worlds are unaffected; new hosts are added at the end.
 */
public enum OreHost {

    STONE("ore", "fortuneOre"),
    DEEPSLATE("deepslate_ore", "fortuneDeepslateOre"),
    NETHERRACK("netherrack_ore", "fortuneNetherrackOre"),
    ENDSTONE("endstone_ore", "fortuneEndstoneOre");

    /**
     * Infix shared by the block's unlocalized name ("{@code fortuneores.<infix>}") and its icons
     * ("{@code <infix>_<material>}").
     */
    public final String infix;
    /** GameRegistry block id; must stay stable for existing hosts. */
    public final String registryName;

    /**
     * The blocks registered for this host, one per ore group (16 ores each); assigned in {@link FortuneOres#preInit}.
     * The block holding a given ore is {@code groupBlocks[ore.meta / BlockFortuneOre.GROUP_SIZE]}.
     */
    public Block[] groupBlocks;

    OreHost(String infix, String registryName) {
        this.infix = infix;
        this.registryName = registryName;
    }

    /** The block that carries {@code ore} for this host. */
    public Block blockFor(Ore ore) {
        return groupBlocks[ore.meta / BlockFortuneOre.GROUP_SIZE];
    }

    /**
     * The vanilla/modded terrain block this host replaces, or {@code null} when it is unavailable (deepslate is a soft
     * dependency resolved at runtime, so it may be absent). Resolved lazily because {@link Blocks} and the deepslate
     * soft-dep are only ready after init.
     */
    public Block source() {
        switch (this) {
            case STONE:
                return Blocks.stone;
            case DEEPSLATE:
                return FortuneOres.deepslateBlock;
            case NETHERRACK:
                return Blocks.netherrack;
            case ENDSTONE:
                return Blocks.end_stone;
            default:
                return null;
        }
    }
}
