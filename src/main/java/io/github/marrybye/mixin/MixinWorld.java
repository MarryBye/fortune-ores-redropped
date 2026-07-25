package io.github.marrybye.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.marrybye.OreGenSuppressor;

/**
 * Denies the placement of a foreign ore block while a chunk is being generated, for every ore this mod generates
 * itself.
 *
 * <p>
 * {@code OreGenEvent.GenerateMinable} only reaches generators that bother to fire it. Vanilla emerald does not - it is
 * placed directly from {@code BiomeGenHills.decorate} - and neither do plenty of mod generators that simply write
 * blocks into the world from their own {@code IWorldGenerator}. All of them do funnel through {@code World.setBlock},
 * which is why the last word on "this ore does not generate" lives here.
 *
 * <p>
 * The 4-argument {@code setBlock} overload delegates to this one, so hooking it covers both. All the gating (are we in
 * world generation at all, is this block one of ours, is it replacing solid ground) is in
 * {@link OreGenSuppressor#shouldSuppress}, which is written to be nearly free for the millions of ordinary block
 * placements that are none of our business.
 */
@Mixin(World.class)
public class MixinWorld {

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("HEAD"), cancellable = true)
    private void fortuneores$denyForeignOreGen(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        if (OreGenSuppressor.shouldSuppress((World) (Object) this, x, y, z, block, meta)) {
            // Same outcome as a denied ore-gen event: the vein simply never appears, the host block stays put.
            cir.setReturnValue(false);
        }
    }
}
