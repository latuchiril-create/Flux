package dev.fuga.fluxvisuals.multibot;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/** Mutable access to client state that is final/private in MinecraftClient. */
public interface MinecraftClientStateAccess {
    InGameHud fluxvisuals$getInGameHud();

    void fluxvisuals$setInGameHud(InGameHud hud);

    ClientPlayNetworkHandler fluxvisuals$getNetworkHandler();

}
