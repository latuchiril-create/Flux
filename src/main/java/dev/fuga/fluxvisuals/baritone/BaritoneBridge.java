package dev.fuga.fluxvisuals.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.Rotation;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/** Small integration point for the official Baritone API bundled by FluxVisuals. */
public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("baritone.BaritoneProvider", false, BaritoneBridge.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Forces class loading after Fabric has discovered the nested Baritone mod. */
    public static boolean initialize() {
        if (!isAvailable()) {
            return false;
        }
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Executes a Baritone command without sending it to the Minecraft server. */
    public static boolean execute(String command) {
        if (command == null || command.isBlank() || !initialize()) {
            return false;
        }
        String normalized = command.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1).trim();
        }
        // Force the official implementation to use the same client prefix.
        // This also enables Baritone's own chat mixin when the bundled Fabric
        // implementation is initialized after FluxVisuals.
        BaritoneAPI.getSettings().chatControl.value = true;
        BaritoneAPI.getSettings().prefixControl.value = true;
        BaritoneAPI.getSettings().prefix.value = "#";
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            return baritone != null && !normalized.isBlank()
                    && baritone.getCommandManager().execute(normalized);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Starts a Baritone path for the player currently installed in MinecraftClient. */
    public static boolean walkTo(BlockPos target, int radius) {
        if (target == null || !initialize()) {
            return false;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return false;
            }
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(client.player);
            if (baritone == null) {
                return false;
            }
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(target, Math.max(0, radius)));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Feeds one gradual target rotation to Baritone's look controller. The
     * look behavior performs the normal client-side interpolation and packet
     * scheduling; callers should provide small per-tick steps rather than
     * jumping the player's yaw directly.
     */
    public static boolean updateRotation(float yaw, float pitch, boolean headOnly) {
        if (!initialize()) {
            return false;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return false;
            }
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(client.player);
            if (baritone == null) {
                return false;
            }
            baritone.getLookBehavior().updateTarget(
                    new Rotation(yaw, Rotation.clampPitch(pitch)), headOnly);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPathingForCurrentPlayer() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || !initialize()) {
                return false;
            }
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(client.player);
            return baritone != null && baritone.getPathingBehavior().isPathing();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void cancelWalkForCurrentPlayer() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || !initialize()) {
                return;
            }
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(client.player);
            if (baritone != null) {
                baritone.getPathingBehavior().forceCancel();
            }
        } catch (Throwable ignored) {
            // Baritone is optional.
        }
    }
}
