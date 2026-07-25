package io.github.marrybye.mixin;

import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.marrybye.OreGenSuppressor;

/**
 * Marks the window in which {@link MixinWorld} is allowed to deny ore placements.
 *
 * <p>
 * {@code ChunkProviderServer.populate} is the one funnel every ore generator passes through: it runs the inner chunk
 * provider's population (vanilla biome decoration, which is where both the standard ore veins and the hills' emeralds
 * come from) and then FML's {@code GameRegistry.generateWorld}, which drives every modded {@code IWorldGenerator}.
 * Outside of that window ore blocks are placed by players, machines and structures - none of our business.
 *
 * <p>
 * The depth counter is decremented on a normal return only. An exception escaping chunk population takes the server
 * down with it, so there is no live state left to leak into.
 */
@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer {

    @Inject(method = "populate", at = @At("HEAD"))
    private void fortuneores$beginWorldGen(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        OreGenSuppressor.beginWorldGen();
    }

    @Inject(method = "populate", at = @At("RETURN"))
    private void fortuneores$endWorldGen(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        OreGenSuppressor.endWorldGen();
    }
}
