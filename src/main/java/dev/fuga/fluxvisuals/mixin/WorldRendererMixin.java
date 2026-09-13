package dev.fuga.fluxvisuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldRenderer.class, priority = 2000)
public abstract class WorldRendererMixin {
    @Inject(method = "setWorld", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepVisibleWorld(ClientWorld world, CallbackInfo ci) {
        if (MultiBotManager.shouldBlockNonVisibleRendererWorld(world)
                || (world == null && MultiBotManager.isCurrentBotContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipRendererTickWithoutWorld(CallbackInfo ci) {
        WorldRendererAccessor renderer = (WorldRendererAccessor) (Object) this;
        if (MultiBotManager.isBackgroundContext() || renderer.fluxvisuals$getWorld() == null) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipRenderDuringSessionSwap(
            net.minecraft.client.util.ObjectAllocator unused,
            net.minecraft.client.render.RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            org.joml.Matrix4f positionMatrix,
            org.joml.Matrix4f projectionMatrix,
            GpuBufferSlice fog,
            org.joml.Vector4f skyColor,
            boolean renderEntityOutlines,
            CallbackInfo ci
    ) {
        WorldRendererAccessor renderer = (WorldRendererAccessor) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (MultiBotManager.isBackgroundContext()
                || client.world == null || client.player == null || client.getNetworkHandler() == null
                || renderer.fluxvisuals$getWorld() == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluxvisuals$skipBackgroundParticle(
            ParticleEffect effect,
            boolean force,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()
                || ((WorldRendererAccessor) (Object) this).fluxvisuals$getWorld() == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluxvisuals$skipBackgroundImportantParticle(
            ParticleEffect effect,
            boolean force,
            boolean important,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()
                || ((WorldRendererAccessor) (Object) this).fluxvisuals$getWorld() == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluxvisuals$skipBackgroundParticleDirect(
            ParticleEffect effect,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()
                || ((WorldRendererAccessor) (Object) this).fluxvisuals$getWorld() == null) {
            ci.cancel();
        }
    }

    @Inject(method = "reload()V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundReload(CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleChunkRender(III)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundChunkRender(int x, int y, int z, CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleBlockRenders", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundBlockRenders(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundBlockRerender(
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "isRenderingReady", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (MultiBotManager.isBackgroundContext()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateBlock", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundBlockUpdate(
            BlockView world,
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            int flags,
            CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundBlockBreakingInfo(int entityId, BlockPos pos, int progress, CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleNeighborUpdates", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundNeighborUpdates(ChunkPos chunkPos, CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleTerrainUpdate", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundTerrainUpdate(CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleChunkRenders3x3x3(III)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundChunkRenders3x3x3(int x, int y, int z, CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleChunkRenders(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundChunkRenders(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cancelWeather(FrameGraphBuilder frameGraphBuilder, Vec3d cameraPos, float tickProgress, GpuBufferSlice fog, CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeBadWeather()) {
            ci.cancel();
        }
    }

    @Inject(method = "addWeatherParticlesAndSound", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cancelWeatherParticles(Camera camera, CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeBadWeather()) {
            ci.cancel();
        }
    }

    @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cancelEntityGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeEntityGlowing()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cancelVanillaBlockOutline(Camera camera, VertexConsumerProvider.Immediate vertexConsumers,
                                                       MatrixStack matrices, boolean translucent, CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isEnabled()) {
            ci.cancel();
        }
    }
}
