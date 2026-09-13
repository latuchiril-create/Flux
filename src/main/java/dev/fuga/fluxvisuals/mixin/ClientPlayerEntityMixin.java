package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipStaleBotSnapshotTick(CallbackInfo ci) {
        if (MultiBotManager.shouldSkipBotPlayerTick((ClientPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
