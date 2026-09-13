package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.mixin.LivingEntityAccessor;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Removes the vanilla 10-tick jump cooldown so holding space bunny-hops
 * without gaps. Optional jump randomization skips some hops to break a
 * robotic rhythm.
 */
public final class NoJumpDelay extends Module {
    private boolean jumpRandomize = true;
    private final java.util.Random random = new java.util.Random();

    public NoJumpDelay() {
        super("NoJumpDelay", "Removes jump cooldown for instant re-jumps.", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player)) {
            return;
        }
        if (client.currentScreen != null || player.hasVehicle() || player.isGliding()) {
            return;
        }
        LivingEntityAccessor access = (LivingEntityAccessor) player;
        if (jumpRandomize && player.isOnGround() && client.options.jumpKey.isPressed()
                && !player.isSneaking() && random.nextFloat() < 0.22F) {
            // occasionally hold a tiny cooldown so hops are not metronome-perfect
            access.fluxvisuals$setJumpingCooldown(1 + random.nextInt(3));
            return;
        }
        if (access.fluxvisuals$getJumpingCooldown() != 0) {
            access.fluxvisuals$setJumpingCooldown(0);
        }
    }

    public boolean isJumpRandomize() { return jumpRandomize; }
    public void setJumpRandomize(boolean v) { jumpRandomize = v; FluxVisualsClient.requestConfigSave(); }
}
