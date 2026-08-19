package io.github.marrybye;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Generates the mod's ore blocks in every dimension. Each vein is placed once with a host-aware sphere: for every block
 * it would fill it looks at what is already there and swaps in the matching variant - stone gets the stone-hosted ore,
 * the configured deepslate block the deepslate-hosted ore, netherrack the Nether variant and end stone the End variant.
 * That single host-driven path means a vein straddling the stone/deepslate boundary still looks right on both sides,
 * and the same generator naturally produces netherrack ore in the Nether and end-stone ore in the End without any
 * dimension-specific placement code. Which dimensions an ore may generate in is gated per ore by its
 * {@code SpawnInOverworld/Nether/End} toggles (or an explicit {@code DimensionIds} whitelist), and which biomes by its
 * {@code Biomes} rules. All other parameters (height - per dimension if wanted -, vein size, veins per chunk and the
 * per-vein chance) come from each {@link Ore} and its config category.
 */
public class WorldGenOres implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        int dim = world.provider.dimensionId;

        for (Ore ore : FortuneOres.oreStorage) {
            if (!ore.generates()) continue;
            if (!ore.allowsDimension(dim)) continue;

            int minY = ore.minYFor(dim);
            int span = Math.max(1, ore.maxYFor(dim) - minY + 1);
            for (int i = 0; i < ore.veinsPerChunk; i++) {
                // Rarity beyond "one vein per chunk": most attempts of a rare ore place nothing at all.
                if (ore.veinChance < 100 && random.nextInt(100) >= ore.veinChance) continue;

                int x = chunkX * 16 + random.nextInt(16);
                int z = chunkZ * 16 + random.nextInt(16);
                if (!allowedInBiome(ore, world, x, z)) continue;

                int y = minY + random.nextInt(span);

                generateVein(world, random, x, y, z, ore, ore.veinSize, dim);
            }
        }
    }

    /**
     * Whether the vein's own column sits in a biome the ore accepts. The biome comes from the world's chunk manager
     * rather than {@code World#getBiomeGenForCoords}: a vein may start near a chunk border and the manager answers
     * straight from the biome generator, without needing the chunk at that position to be loaded.
     */
    private boolean allowedInBiome(Ore ore, World world, int x, int z) {
        if (ore.biomes.isUnrestricted()) return true;
        return ore.biomes.matches(
            world.getWorldChunkManager()
                .getBiomeGenAt(x, z));
    }

    /**
     * Places one ore vein, choosing the block variant per position. Mirrors the ellipsoid shape of vanilla
     * {@link net.minecraft.world.gen.feature.WorldGenMinable}. In the Overworld the variant is picked purely by height
     * ({@link #overworldHostFor}): at or below the deepslate ceiling the deepslate variant, above it the plain stone
     * variant - so stone ores never leak into the deepslate layer and vice versa. In the Nether and End the variant is
     * chosen from the terrain block that is actually present ({@link #hostFor}), which yields only the netherrack and
     * end-stone variants there.
     */
    private void generateVein(World world, Random rand, int x, int y, int z, Ore ore, int veinSize, int dim) {
        int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
        float f = rand.nextFloat() * (float) Math.PI;
        double d0 = (x + 8) + MathHelper.sin(f) * veinSize / 8.0F;
        double d1 = (x + 8) - MathHelper.sin(f) * veinSize / 8.0F;
        double d2 = (z + 8) + MathHelper.cos(f) * veinSize / 8.0F;
        double d3 = (z + 8) - MathHelper.cos(f) * veinSize / 8.0F;
        double d4 = y + rand.nextInt(3) - 2;
        double d5 = y + rand.nextInt(3) - 2;

        for (int l = 0; l <= veinSize; ++l) {
            double d6 = d0 + (d1 - d0) * l / veinSize;
            double d7 = d4 + (d5 - d4) * l / veinSize;
            double d8 = d2 + (d3 - d2) * l / veinSize;
            double d9 = rand.nextDouble() * veinSize / 16.0D;
            double d10 = (MathHelper.sin((float) (l * Math.PI / veinSize)) + 1.0F) * d9 + 1.0D;
            double d11 = (MathHelper.sin((float) (l * Math.PI / veinSize)) + 1.0F) * d9 + 1.0D;

            int minX = MathHelper.floor_double(d6 - d10 / 2.0D);
            int minY = MathHelper.floor_double(d7 - d11 / 2.0D);
            int minZ = MathHelper.floor_double(d8 - d10 / 2.0D);
            int maxX = MathHelper.floor_double(d6 + d10 / 2.0D);
            int maxY = MathHelper.floor_double(d7 + d11 / 2.0D);
            int maxZ = MathHelper.floor_double(d8 + d10 / 2.0D);

            for (int bx = minX; bx <= maxX; ++bx) {
                double dx = (bx + 0.5D - d6) / (d10 / 2.0D);
                if (dx * dx >= 1.0D) continue;

                for (int by = minY; by <= maxY; ++by) {
                    double dy = (by + 0.5D - d7) / (d11 / 2.0D);
                    if (dx * dx + dy * dy >= 1.0D) continue;

                    for (int bz = minZ; bz <= maxZ; ++bz) {
                        double dz = (bz + 0.5D - d8) / (d10 / 2.0D);
                        if (dx * dx + dy * dy + dz * dz >= 1.0D) continue;

                        Block existing = world.getBlock(bx, by, bz);
                        OreHost host = (dim == -1 || dim == 1) ? hostFor(existing) : overworldHostFor(existing, by);
                        if (host == null) continue;

                        // Null when this host's block was never registered - the ore does not generate in the
                        // dimension the host belongs to, so it has no variant to place here.
                        Block oreBlock = host.blockFor(ore);
                        if (oreBlock != null) world.setBlock(bx, by, bz, oreBlock, offset, 2);
                    }
                }
            }
        }
    }

    /**
     * Returns the ore host whose terrain block matches {@code existing}, or {@code null} if this block is not a host
     * (so the vein leaves it untouched). Hosts with no resolved source block - deepslate when its providing mod is
     * absent - never match.
     */
    private OreHost hostFor(Block existing) {
        for (OreHost host : OreHost.values()) {
            Block source = host.source();
            if (source != null && existing == source) return host;
        }
        return null;
    }

    /**
     * Overworld variant selection by height alone. Only real terrain (stone or the configured deepslate block) is
     * replaced - anything else leaves the vein untouched. At or below {@link FortuneOres#deepslateMaxY} the deepslate
     * variant is used (when the deepslate block is present), above it the ordinary stone variant, no matter which of
     * the
     * two host blocks happens to sit at that position. This is what keeps stone ores out of the deepslate layer.
     */
    private OreHost overworldHostFor(Block existing, int y) {
        boolean isStone = existing == Blocks.stone;
        boolean isDeepslate = FortuneOres.deepslateBlock != null && existing == FortuneOres.deepslateBlock;
        if (!isStone && !isDeepslate) return null;

        if (FortuneOres.deepslateBlock != null && y <= FortuneOres.deepslateMaxY) return OreHost.DEEPSLATE;
        return OreHost.STONE;
    }
}
