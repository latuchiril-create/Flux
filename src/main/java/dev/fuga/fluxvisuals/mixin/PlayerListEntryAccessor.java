package dev.fuga.fluxvisuals.mixin;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerListEntry.class)
public interface PlayerListEntryAccessor {
    @Accessor("gameMode")
    void fluxvisuals$setGameMode(GameMode gameMode);
}
