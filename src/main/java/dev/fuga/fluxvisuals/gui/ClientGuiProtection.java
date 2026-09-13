package dev.fuga.fluxvisuals.gui;

import dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen;
import net.minecraft.client.MinecraftClient;

/** Screens owned by the client UI must never be closed by background automation. */
public final class ClientGuiProtection {
    private ClientGuiProtection() {
    }

    public static boolean isOpen(MinecraftClient client) {
        return client != null && (client.currentScreen instanceof ClickGuiScreen
                || client.currentScreen instanceof ModernClickGuiScreen);
    }
}
