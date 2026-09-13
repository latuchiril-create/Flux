package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth inventory open animation.
 *
 * <p>Vanilla draws the fullscreen dim gradient inside
 * {@code renderBackground}, i.e. inside {@code renderWithTooltip}. Scaling the
 * whole method would also scale/slide that fullscreen backdrop, making the
 * background visibly jerk during the animation. Instead the static dim is
 * drawn first (unscaled), the vanilla one is suppressed while animating, and
 * only the inventory panel + contents render under the scale matrix.
 */
@Mixin(Screen.class)
public abstract class InventoryScreenAnimationMixin {
    // Matches Screen.renderInGameBackground gradient (vanilla 1.21.x).
    private static final int DIM_TOP = -1072689136;
    private static final int DIM_BOTTOM = -804253680;

    private boolean fluxvisuals$pushedInventoryAnimation;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void fluxvisuals$pushInventoryAnimationHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        fluxvisuals$pushedInventoryAnimation = fluxvisuals$pushInventoryAnimation(context);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void fluxvisuals$popInventoryAnimationReturn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fluxvisuals$pushedInventoryAnimation) {
            context.getMatrices().popMatrix();
            fluxvisuals$pushedInventoryAnimation = false;
        }
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepBackdropStatic(DrawContext context, CallbackInfo ci) {
        if (fluxvisuals$pushedInventoryAnimation) {
            ci.cancel();
        }
    }

    private boolean fluxvisuals$pushInventoryAnimation(DrawContext context) {
        if (!((Object) this instanceof HandledScreen)) {
            return false;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getAutoSwap().shouldHideInventory()
                || FluxVisualsClient.MODULE_MANAGER.getElytraSwap().shouldHideInventory()) {
            return false;
        }

        float progress = FluxVisualsClient.MODULE_MANAGER.getAnimations().inventoryOpenProgress(this);
        if (progress >= 0.999F) {
            return false;
        }

        Screen screen = (Screen) (Object) this;
        // Static backdrop: covers the screen before the animated matrix is
        // applied, so no uncovered edges slide around behind the inventory.
        context.fillGradient(0, 0, screen.width, screen.height, DIM_TOP, DIM_BOTTOM);

        float scale = 0.70F + progress * 0.30F;
        float yOffset = (1.0F - progress) * 24.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(screen.width * 0.5F, screen.height * 0.5F + yOffset);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-screen.width * 0.5F, -screen.height * 0.5F);
        return true;
    }
}
