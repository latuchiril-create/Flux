package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkThreadUtils.class)
public abstract class NetworkThreadUtilsMixin {
    @Inject(
            method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static <T extends PacketListener> void fluxvisuals$applyInBotContext(
            Packet<T> packet,
            T listener,
            ThreadExecutor<?> executor,
            CallbackInfo ci
    ) {
        if (!FluxVisualsClient.MULTI_BOT_MANAGER.isManagedListener(listener)
                || executor.isOnThread()) {
            return;
        }
        if (FluxVisualsClient.MULTI_BOT_MANAGER.enqueueBackgroundPacket(packet, listener)) {
            ci.cancel();
            throw OffThreadException.INSTANCE;
        }
    }
}
