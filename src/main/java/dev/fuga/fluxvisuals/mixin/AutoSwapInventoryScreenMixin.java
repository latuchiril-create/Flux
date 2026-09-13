package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AutoSwapInventoryScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideAutoSwapBlur(DrawContext context, CallbackInfo ci) {
        if (fluxvisuals$shouldHideAutoSwapInventory()
                || (Object) this instanceof dev.fuga.fluxvisuals.gui.modern.AutoBuyConfigScreen
                || (Object) this instanceof dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen
                || (Object) this instanceof dev.fuga.fluxvisuals.gui.ClickGuiScreen) {
            ci.cancel();
        }
    }

    private boolean fluxvisuals$shouldHideAutoSwapInventory() {
        return (Object) this instanceof InventoryScreen
                && (FluxVisualsClient.MODULE_MANAGER.getAutoSwap().shouldHideInventory()
                || FluxVisualsClient.MODULE_MANAGER.getElytraSwap().shouldHideInventory());
    }
}
