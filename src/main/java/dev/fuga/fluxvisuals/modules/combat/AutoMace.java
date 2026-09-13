package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Wind charge -> mace flow: after throwing a wind charge, automatically holds
 * the mace (if present in the hotbar). Keeps it while airborne (a landed hit
 * that launches you again keeps the mace), and gives back the previous item
 * once you land or the timeout expires. Only restores if you did not switch
 * slots manually meanwhile.
 */
public final class AutoMace extends Module {
    private boolean requireTrigger = true;
    private float timeoutMs = 4000.0F;
    private boolean restoreItem = true;

    private boolean useWasDown = false;
    private boolean armed = false;
    private long armedAtMs = 0L;
    private int maceSlot = -1;
    private int prevSlot = -1;
    private boolean wasAirborne = false;
    private int groundTicks = 0;

    public AutoMace() {
        super("AutoMace", "Auto-holds mace after wind charge throw.", ModuleCategory.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player)
                || client.world == null || client.interactionManager == null) {
            useWasDown = false;
            return;
        }
        if (client.currentScreen != null) {
            useWasDown = client.options.useKey.isPressed();
            return;
        }
        boolean triggerOn = FluxVisualsClient.MODULE_MANAGER.getTriggerBot().isEnabled();
        if (requireTrigger && !triggerOn) {
            restoreIfHolding(player);
            useWasDown = client.options.useKey.isPressed();
            return;
        }

        boolean useDown = client.options.useKey.isPressed();
        if (useDown && !useWasDown && isHoldingWindCharge(player)) {
            onWindChargeThrown(player);
        }
        useWasDown = useDown;

        if (!armed) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - armedAtMs > timeoutMs) {
            restoreIfHolding(player);
            disarm();
            return;
        }
        if (maceSlot >= 0) {
            try {
                ItemStack held = player.getInventory().getStack(maceSlot);
                if (held == null || !held.isOf(Items.MACE)) {
                    disarm(); // mace moved away: stop tracking silently
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        if (player.isOnGround()) {
            // count landing only after a real flight, otherwise a ground
            // throw would snap the mace back before you even jump
            if (wasAirborne) {
                groundTicks++;
            }
        } else {
            wasAirborne = true;
            groundTicks = 0;
        }
        if (wasAirborne && groundTicks >= 4) {
            // landed without launching again: give the old item back
            restoreIfHolding(player);
            disarm();
        }
    }

    private void onWindChargeThrown(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int mace = findMaceSlot(inv);
        if (mace < 0) {
            return;
        }
        prevSlot = inv.getSelectedSlot();
        maceSlot = mace;
        if (prevSlot != maceSlot) {
            selectSlot(player, maceSlot);
        }
        armed = true;
        armedAtMs = System.currentTimeMillis();
        wasAirborne = !player.isOnGround();
        groundTicks = 0;
    }

    private void restoreIfHolding(ClientPlayerEntity player) {
        if (!armed || !restoreItem || maceSlot < 0 || prevSlot < 0 || prevSlot == maceSlot) {
            return;
        }
        try {
            PlayerInventory inv = player.getInventory();
            if (inv.getSelectedSlot() != maceSlot) {
                return; // user switched manually: do not yank the slot
            }
            ItemStack prev = inv.getStack(prevSlot);
            if (prev == null || prev.isEmpty()) {
                return; // previous stack is gone (e.g. last charge used): stay, never select air
            }
            selectSlot(player, prevSlot);
        } catch (Exception ignored) {
        }
    }

    /** Client slot change alone is invisible to the server: sync it explicitly. */
    private static void selectSlot(ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        try {
            if (player.networkHandler != null) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Exception ignored) {
        }
    }

    private void disarm() {
        armed = false;
        maceSlot = -1;
        prevSlot = -1;
        wasAirborne = false;
        groundTicks = 0;
    }

    private static boolean isHoldingWindCharge(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        return (main != null && main.isOf(Items.WIND_CHARGE)) || (off != null && off.isOf(Items.WIND_CHARGE));
    }

    private static int findMaceSlot(PlayerInventory inv) {
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getStack(i);
                if (stack != null && stack.isOf(Items.MACE)) {
                    return i;
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (client != null && client.player instanceof ClientPlayerEntity player) {
            restoreIfHolding(player);
        }
        disarm();
        useWasDown = false;
    }

    public boolean isRequireTrigger() { return requireTrigger; }
    public void setRequireTrigger(boolean v) { requireTrigger = v; FluxVisualsClient.requestConfigSave(); }
    public float getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(float v) { timeoutMs = net.minecraft.util.math.MathHelper.clamp(v, 500.0F, 10000.0F); FluxVisualsClient.requestConfigSave(); }
    public boolean isRestoreItem() { return restoreItem; }
    public void setRestoreItem(boolean v) { restoreItem = v; FluxVisualsClient.requestConfigSave(); }
}
