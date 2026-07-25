package io.github.marrybye.mixin;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.marrybye.OreSwapper;

/**
 * Swaps a mined ore block's drops for this mod's chunks inside {@code Block#getDrops}.
 *
 * <p>
 * Forge only fires {@code BlockEvent.HarvestDropsEvent} from {@code Block#dropBlockAsItemWithChance} and
 * {@code Block#harvestBlock}, i.e. when the drops are spawned as items in the world. Machine miners - Mekanism's
 * Digital Miner, BuildCraft-style quarries, and friends - skip both: they call {@code Block#getDrops} directly and put
 * the result straight into their inventory. That is why they used to pull raw foreign ore blocks out of the ground
 * while hand mining the very same block yielded a chunk. Hooking {@code getDrops} covers every one of those callers
 * (and hand mining too, which goes through {@code getDrops} first).
 *
 * <p>
 * The injection lands on {@code Block}'s own implementation, so it reaches every ore block that does not override
 * {@code getDrops} - all vanilla ores and the overwhelming majority of modded ones. A block whose class replaces
 * {@code getDrops} entirely is still handled by the harvest-event fallback in {@link OreSwapper} when a player mines
 * it. This mod's own {@link io.github.marrybye.BlockFortuneOre} overrides {@code getDrops} and already
 * returns chunks, so it is unaffected either way.
 */
@Mixin(Block.class)
public class MixinBlock {

    // remap = false: getDrops is added by Forge, so its name is not part of the SRG mappings.
    @Inject(method = "getDrops", at = @At("RETURN"), remap = false)
    private void fortuneores$swapOreDrops(World world, int x, int y, int z, int metadata, int fortune,
        CallbackInfoReturnable<ArrayList<ItemStack>> cir) {
        ArrayList<ItemStack> drops = cir.getReturnValue();
        if (drops == null || drops.isEmpty()) return;

        // Mutated in place, so the return value stays valid without cancelling the callback.
        OreSwapper.swapDrops((Block) (Object) this, metadata, drops, fortune, world.rand);
    }
}
