package dev.fuga.fluxvisuals.mixin;

import net.minecraft.client.network.WorldLoadingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerStateAccess {
    @Accessor("worldLoadingState")
    WorldLoadingState fluxvisuals$getWorldLoadingState();

    @Mutable
    @Accessor("worldLoadingState")
    void fluxvisuals$setWorldLoadingState(WorldLoadingState state);
}
