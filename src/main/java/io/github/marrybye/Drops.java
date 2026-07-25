package io.github.marrybye;

import java.util.Random;

/**
 * Shared drop-count maths so the harvest-drop swapper ({@link OreSwapper}) and the mod's own ore blocks
 * ({@link BlockFortuneOre}) roll chunk amounts identically.
 */
public final class Drops {

    private Drops() {}

    /** Rolls a uniform amount in [min, max]; returns min when the range is empty or inverted. */
    public static int randomInRange(int min, int max, Random rand) {
        if (max <= min) return min;
        return min + rand.nextInt(max - min + 1);
    }

    /** Applies the mod's Fortune curve to a base amount (same behaviour vanilla uses for ore drops). */
    public static int applyFortune(int baseCount, int fortuneLevel, Random rand) {
        if (fortuneLevel > 0) {
            int j = rand.nextInt(fortuneLevel + 2) - 1;
            if (j < 0) j = 0;
            return baseCount * (j + 1);
        }
        return baseCount;
    }

    /** Rolls the number of chunks an ore should drop: a random amount in its configured range, boosted by Fortune. */
    public static int rollChunks(Ore ore, int fortuneLevel, Random rand) {
        int base = randomInRange(ore.dropCount, Math.max(ore.dropCount, ore.dropCountMax), rand);
        return applyFortune(base, fortuneLevel, rand);
    }
}
