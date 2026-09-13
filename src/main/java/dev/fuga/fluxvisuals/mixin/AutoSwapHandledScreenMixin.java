package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class AutoSwapHandledScreenMixin {
    @Inject(method = "renderMain", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapMain(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapForeground(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "drawSlots", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapSlots(DrawContext context, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCursorStack", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapCursorStack(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    private boolean fluxvisuals$shouldHideAutoSwapInventory() {
        return (Object) this instanceof InventoryScreen
                && (FluxVisualsClient.MODULE_MANAGER.getAutoSwap().shouldHideInventory()
                || FluxVisualsClient.MODULE_MANAGER.getElytraSwap().shouldHideInventory());
    }
}
