package dev.fuga.fluxvisuals.mixin;

import baritone.cache.CachedChunk;
import baritone.utils.pathing.PathingBlockType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.BitSet;

/**
 * Baritone's CachedChunk was designed with a y >= 0 indexing assumption
 * ((x << 1) | (z << 5) | (y << 9)). When querying negative y coordinates in
 * MC 1.21.8 (such as y = -1 down to y = -64), bitIndex is negative (-512) and
 * crashes BitSet.get(). This mixin safely returns AIR for out-of-bounds positions.
 */
@Mixin(value = CachedChunk.class, remap = false)
public abstract class BaritoneCachedChunkMixin {
    @Shadow
    @Final
    public int height;

    @Shadow
    @Final
    private BitSet data;

    @Inject(method = "getBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxvisuals$safeGetBlock(int x, int y, int z, DimensionType type, CallbackInfoReturnable<BlockState> cir) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= this.height) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getType", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxvisuals$safeGetType(int index, CallbackInfoReturnable<PathingBlockType> cir) {
        if (index < 0 || this.data == null || index + 1 >= this.data.size()) {
            cir.setReturnValue(PathingBlockType.AIR);
        }
    }
}
