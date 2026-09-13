package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.AutoBuyButtonScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$watermarkContextMenuHook(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.mouse == null) {
            return;
        }

        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        double scaledMouseX = mouseX;
        double scaledMouseY = mouseY;
        if (client.getWindow() != null && client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            scaledMouseX = mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            scaledMouseY = mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        }
        boolean autoBuyButtonClick = client.currentScreen instanceof AutoBuyButtonScreen screen
                && screen.fluxvisuals$isMouseOverAutoBuyButton(scaledMouseX, scaledMouseY);
        if (fluxvisuals$isAutomationInputLocked()) {
            if (autoBuyButtonClick && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return;
            }
            ci.cancel();
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
            if (FluxVisualsClient.MODULE_MANAGER.getWatermark().handleChatRightClick(client, mouseX, mouseY)) {
                ci.cancel();
                return;
            }
        }

        if (FluxVisualsClient.MODULE_MANAGER.getWatermark().handleMusicMouseButton(client, mouseX, mouseY, button, action)) {
            ci.cancel();
            return;
        }

        if (FluxVisualsClient.MODULE_MANAGER.getTargetHud().handleMouse(client, mouseX, mouseY, button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos", at = @At("TAIL"))
    private void fluxvisuals$targetHudDragHook(long window, double x, double y, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        FluxVisualsClient.MODULE_MANAGER.getTargetHud().handleMouseMove(client, x, y);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$handleZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // Другой мод (напр. masa itemscroller) мог уже перехватить скролл.
        // Двойной quick-move двигает стак дважды за тик: предметы "прыгают".
        if (ci.isCancelled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (FluxVisualsClient.MODULE_MANAGER.getItemScroller().onMouseScroll(client, vertical)) {
            ci.cancel();
            return;
        }
        if (fluxvisuals$isAutomationInputLocked()) {
            ci.cancel();
            return;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getZoom().onMouseScroll(vertical)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean fluxvisuals$isAutomationInputLocked() {
        var autoBuy = FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
        boolean currentAutoBuyWorkflow = autoBuy.isWorking()
                || (autoBuy.isEnabled() && (FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()
                || FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()));
        boolean currentTelegramWorkflow = FluxVisualsClient.MULTI_BOT_MANAGER.isCurrentSessionTelegramTarget()
                && (FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellWorking()
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isLeaveReportWorking());
        return currentAutoBuyWorkflow || currentTelegramWorkflow;
    }

    @Redirect(
            method = "updateMouse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void fluxvisuals$redirectFreeLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        if (FluxVisualsClient.MODULE_MANAGER.getFreeLook().isActive()) {
            FluxVisualsClient.MODULE_MANAGER.getFreeLook().changeLookDirection(cursorDeltaX, cursorDeltaY);
        } else {
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }


}
