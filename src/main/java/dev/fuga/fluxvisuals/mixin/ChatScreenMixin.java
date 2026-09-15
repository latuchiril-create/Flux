package dev.fuga.fluxvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Esc available for closing chat even while a dot-command menu is open. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$closeChatWithEscape(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir
    ) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            MinecraftClient.getInstance().setScreen(null);
            cir.setReturnValue(true);
        }
    }
}
