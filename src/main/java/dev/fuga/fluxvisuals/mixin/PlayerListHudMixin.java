package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @ModifyConstant(method = "render", constant = @Constant(intValue = 20, ordinal = 0))
    private int fluxvisuals$customRowsPerColumn(int original) {
        var tabCustomizer = FluxVisualsClient.MODULE_MANAGER.getTabCustomizer();
        if (!tabCustomizer.isEnabled()) {
            return original;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int players = 0;
        if (client != null && client.getNetworkHandler() != null) {
            players = client.getNetworkHandler().getListedPlayerListEntries().size();
        }
        return tabCustomizer.effectiveRows(players);
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void fluxvisuals$protectPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(FluxVisualsClient.MODULE_MANAGER.getNameProtect().protect(cir.getReturnValue()));
    }
}
