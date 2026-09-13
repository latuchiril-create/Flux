package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public final class HitColor extends Module {
    private static final int HIT_TICKS = 9;
    private static final ThreadLocal<Boolean> ARMOR_TINT_ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final RenderPipeline LIGHT_OVERLAY_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/hit_color_light_overlay"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/hit_color_light_overlay"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/hit_color_light_overlay"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());
    private static final Map<Identifier, RenderLayer> LIGHT_OVERLAY_LAYERS = new HashMap<>();

    private final Map<Integer, Integer> hitTicks = new HashMap<>();
    private float hue = 0.0F;
    private float saturation = 0.76F;
    private float value = 1.0F;
    private float alpha = 0.5F;
    private boolean armorTintEnabled = true;

    public HitColor() {
        super("Hit Color", "Colors hit entities.", ModuleCategory.VISUALS);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        hitTicks.clear();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.world == null) {
            hitTicks.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> iterator = hitTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                iterator.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    public void markHit(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity)) {
            return;
        }

        hitTicks.put(entity.getId(), HIT_TICKS);
    }

    public boolean isHighlighted(int entityId) {
        return isEnabled() && hitTicks.containsKey(entityId);
    }

    public int getArgbColor() {
        int rgb = getColorRgb();
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) & 0xFF;
        return (a << 24) | rgb;
    }

    public boolean shouldRenderLightOverlay() {
        return saturation <= 0.14F && value >= 0.72F;
    }

    public static RenderLayer lightOverlayLayer(Identifier texture) {
        return LIGHT_OVERLAY_LAYERS.computeIfAbsent(texture, id -> RenderLayer.of(
                "fluxvisuals_hit_color_light_overlay_" + Integer.toHexString(id.hashCode()).toLowerCase(Locale.ROOT),
                1536,
                false,
                true,
                LIGHT_OVERLAY_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(id, false))
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                        .target(RenderPhase.ITEM_ENTITY_TARGET)
                        .build(false)
        ));
    }

    public int getColorRgb() {
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getValue() {
        return value;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (this.alpha == clampedAlpha) {
            return;
        }

        this.alpha = clampedAlpha;
        FluxVisualsClient.requestConfigSave();
    }

    public void setColor(float hue, float saturation, float value) {
        float clampedHue = Math.max(0.0F, Math.min(1.0F, hue));
        float clampedSaturation = Math.max(0.0F, Math.min(1.0F, saturation));
        float clampedValue = Math.max(0.0F, Math.min(1.0F, value));
        if (this.hue == clampedHue && this.saturation == clampedSaturation && this.value == clampedValue) {
            return;
        }

        this.hue = clampedHue;
        this.saturation = clampedSaturation;
        this.value = clampedValue;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isArmorTintEnabled() {
        return armorTintEnabled;
    }

    public void setArmorTintEnabled(boolean armorTintEnabled) {
        if (this.armorTintEnabled == armorTintEnabled) {
            return;
        }

        this.armorTintEnabled = armorTintEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public static boolean isArmorTintActive() {
        return ARMOR_TINT_ACTIVE.get();
    }

    public static void setArmorTintActive(boolean active) {
        ARMOR_TINT_ACTIVE.set(active);
    }
}
