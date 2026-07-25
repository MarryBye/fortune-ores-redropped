package io.github.marrybye;

import net.minecraft.block.Block;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Another mod's ore block, addressed by registry id and (optionally) metadata: {@code "modid:name"} or
 * {@code "modid:name:meta"}.
 *
 * <p>
 * Ore blocks are normally found through the ore dictionary, which is enough for the vast majority of mods. This covers
 * the ones it is not enough for:
 *
 * <ul>
 * <li>ores that are never registered in the ore dictionary at all, so nothing can match them by name;</li>
 * <li>ores sharing one block across many metadata values, where only some of those values are actually the ore - the
 * metadata in the id pins down exactly which ones this mod may touch.</li>
 * </ul>
 *
 * <p>
 * Resolution is a soft dependency: an id whose mod is not installed simply resolves to {@code null} and is dropped.
 */
public final class ForeignOreBlock {

    public final Block block;
    /** {@link OreDictionary#WILDCARD_VALUE} when the id carries no metadata, i.e. every metadata value matches. */
    public final int meta;
    /** The id this was resolved from, kept for log messages. */
    public final String id;

    private ForeignOreBlock(Block block, int meta, String id) {
        this.block = block;
        this.meta = meta;
        this.id = id;
    }

    /**
     * Resolves {@code "modid:name[:meta]"} against the block registry; returns null when the block is not installed.
     */
    public static ForeignOreBlock resolve(String spec) {
        if (spec == null) return null;

        String id = spec.trim();
        if (id.isEmpty()) return null;

        String blockId = id;
        int meta = OreDictionary.WILDCARD_VALUE;

        // "modid:name:meta" - the metadata is whatever follows the second colon.
        int metaSep = blockId.lastIndexOf(':');
        if (metaSep > 0 && metaSep != blockId.indexOf(':')) {
            try {
                meta = Integer.parseInt(
                    blockId.substring(metaSep + 1)
                        .trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (meta < 0 || meta > 15) return null;
            blockId = blockId.substring(0, metaSep);
        }

        int domainSep = blockId.indexOf(':');
        if (domainSep <= 0 || domainSep == blockId.length() - 1) return null;

        Block block = GameRegistry.findBlock(
            blockId.substring(0, domainSep)
                .trim(),
            blockId.substring(domainSep + 1)
                .trim());
        if (block == null) return null;

        return new ForeignOreBlock(block, meta, id);
    }

    @Override
    public String toString() {
        return id;
    }
}
