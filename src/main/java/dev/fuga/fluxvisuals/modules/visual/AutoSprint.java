package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

public final class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", "Automatically keeps sprint enabled while moving.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player)) {
            return;
        }

        // TriggerBot's post-hit sprint pause owns the sprint key while held:
        // forcing sprint back on here creates ON/OFF toggle spam (flag/ban).
        if (dev.fuga.fluxvisuals.modules.combat.TriggerBot.isSprintHeld()) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }
        if (shouldSprint(client, player)) {
            player.setSprinting(true);
        } else if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (client != null && client.player != null && client.player.isSprinting()) {
            client.player.setSprinting(false);
        }
    }

    private static boolean shouldSprint(MinecraftClient client, ClientPlayerEntity player) {
        if (client.currentScreen != null || player.input == null || player.input.playerInput == null) {
            return false;
        }
        if (!player.input.hasForwardMovement() || player.input.playerInput.sneak() || player.isUsingItem()) {
            return false;
        }
        if (player.hasVehicle() || player.isGliding() || player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }
        return player.getAbilities().allowFlying || player.getHungerManager().getFoodLevel() > 6.0F;
    }
}
