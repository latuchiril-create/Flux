package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookScreen.class)
public abstract class RecipeBookScreenAnimationMixin {
    private boolean fluxvisuals$pushedRecipeBookAnimation;

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeBookWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V")
    )
    private void fluxvisuals$pushRecipeBookAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        fluxvisuals$pushedRecipeBookAnimation = fluxvisuals$pushInventoryAnimation(context);
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeBookWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V", shift = At.Shift.AFTER)
    )
    private void fluxvisuals$popRecipeBookAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fluxvisuals$pushedRecipeBookAnimation) {
            context.getMatrices().popMatrix();
            fluxvisuals$pushedRecipeBookAnimation = false;
        }
    }

    private boolean fluxvisuals$pushInventoryAnimation(DrawContext context) {
        return false;
    }
}
