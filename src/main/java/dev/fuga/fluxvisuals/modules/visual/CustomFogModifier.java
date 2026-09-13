package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public final class CustomFogModifier extends FogModifier {
    @Override
    public boolean shouldApply(CameraSubmersionType submersionType, Entity cameraEntity) {
        WorldCustomizer customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        return customizer.isEnabled()
                && customizer.isCustomFogEnabled()
                && (submersionType == CameraSubmersionType.ATMOSPHERIC
                || submersionType == CameraSubmersionType.DIMENSION_OR_BOSS
                || submersionType == CameraSubmersionType.NONE);
    }

    @Override
    public int getFogColor(ClientWorld world, Camera camera, int viewDistance, float skyDarkness) {
        return 0xFF000000 | FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().getFogColorRgb();
    }

    @Override
    public void applyStartEndModifier(FogData data, Entity cameraEntity, BlockPos cameraPos, ClientWorld world,
                                      float viewDistance, RenderTickCounter tickCounter) {
        float distance = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().getFogDistance();
        data.environmentalStart = 0.0F;
        data.environmentalEnd = distance;
        data.skyEnd = distance;
        data.cloudEnd = distance;
    }
}
