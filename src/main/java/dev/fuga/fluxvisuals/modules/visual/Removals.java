package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.EnumSet;
import net.minecraft.block.enums.CameraSubmersionType;

public final class Removals extends Module {
    private boolean fireOverlay = true;
    private boolean entityGlowing = true;
    private boolean badWeather = true;
    private boolean hurtCamera = true;
    private boolean sprintFov = true;
    private boolean soulSandBubbles = true;
    private final EnumSet<FluidType> noFluidTypes = EnumSet.noneOf(FluidType.class);

    public Removals() {
        super("Removals", "Removes selected client-side visual annoyances.", ModuleCategory.VISUALS);
    }

    public boolean removeFireOverlay() {
        return isEnabled() && fireOverlay;
    }

    public boolean removeEntityGlowing() {
        return isEnabled() && entityGlowing;
    }

    public boolean removeBadWeather() {
        return isEnabled() && badWeather;
    }

    public boolean removeHurtCamera() {
        return isEnabled() && hurtCamera;
    }

    public boolean removeSprintFov() {
        return isEnabled() && sprintFov;
    }

    public boolean removeSoulSandBubbles() {
        return isEnabled() && soulSandBubbles;
    }

    public boolean removeFluid(CameraSubmersionType submersionType) {
        if (!isEnabled() || submersionType == null) {
            return false;
        }

        return switch (submersionType) {
            case WATER -> noFluidTypes.contains(FluidType.WATER);
            case LAVA -> noFluidTypes.contains(FluidType.LAVA);
            default -> false;
        };
    }

    public boolean isFireOverlay() {
        return fireOverlay;
    }

    public void setFireOverlay(boolean fireOverlay) {
        this.fireOverlay = fireOverlay;
    }

    public boolean isEntityGlowing() {
        return entityGlowing;
    }

    public void setEntityGlowing(boolean entityGlowing) {
        this.entityGlowing = entityGlowing;
    }

    public boolean isBadWeather() {
        return badWeather;
    }

    public void setBadWeather(boolean badWeather) {
        this.badWeather = badWeather;
    }

    public boolean isHurtCamera() {
        return hurtCamera;
    }

    public void setHurtCamera(boolean hurtCamera) {
        this.hurtCamera = hurtCamera;
    }

    public boolean isSprintFov() {
        return sprintFov;
    }

    public void setSprintFov(boolean sprintFov) {
        this.sprintFov = sprintFov;
    }

    public boolean isSoulSandBubbles() {
        return soulSandBubbles;
    }

    public void setSoulSandBubbles(boolean soulSandBubbles) {
        this.soulSandBubbles = soulSandBubbles;
    }

    public boolean isNoFluidEnabled(FluidType type) {
        return type != null && noFluidTypes.contains(type);
    }

    public void setNoFluidEnabled(FluidType type, boolean enabled) {
        if (type == null) {
            return;
        }

        if (enabled) {
            noFluidTypes.add(type);
        } else {
            noFluidTypes.remove(type);
        }
    }

    public String enabledNoFluidTypeNames() {
        StringBuilder builder = new StringBuilder();
        for (FluidType type : FluidType.values()) {
            if (noFluidTypes.contains(type)) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(type.name());
            }
        }
        return builder.toString();
    }

    public void setEnabledNoFluidTypeNames(String names) {
        EnumSet<FluidType> next = EnumSet.noneOf(FluidType.class);
        if (names != null) {
            for (String raw : names.split(",")) {
                try {
                    next.add(FluidType.valueOf(raw.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore stale config values.
                }
            }
        }

        noFluidTypes.clear();
        noFluidTypes.addAll(next);
    }

    public enum FluidType {
        WATER,
        LAVA
    }
}
