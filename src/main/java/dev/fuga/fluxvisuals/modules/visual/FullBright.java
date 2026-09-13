package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class FullBright extends Module {
    private boolean replacedExistingNightVision;

    public FullBright() {
        super("FullBright", "Gives local night vision while enabled.", ModuleCategory.VISUALS);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        replacedExistingNightVision = client != null && client.player != null
                && client.player.hasStatusEffect(StatusEffects.NIGHT_VISION);
        applyNightVision(client);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (!replacedExistingNightVision && client != null && client.player != null) {
            client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        replacedExistingNightVision = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (isEnabled()) {
            applyNightVision(client);
        }
    }

    private static void applyNightVision(MinecraftClient client) {
        if (client != null && client.player != null) {
            client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, false, false, false));
        }
    }
}
