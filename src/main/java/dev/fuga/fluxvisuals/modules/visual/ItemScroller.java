package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.mixin.HandledScreenAccessor;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class ItemScroller extends Module {
    public static final int MIN_DELAY_MS = 0;
    public static final int MAX_DELAY_MS = 150;
    public static final int DEFAULT_DELAY_MS = 18;

    private long lastMoveMs;
    private int delayMs = DEFAULT_DELAY_MS;

    public ItemScroller() {
        super("ItemScroller", "Moves hovered stacks quickly with mouse wheel.", ModuleCategory.UTILS);
    }

    public boolean onMouseScroll(MinecraftClient client, double vertical) {
        if (!isEnabled() || client == null || client.player == null || client.interactionManager == null
                || !(client.currentScreen instanceof HandledScreen<?> handledScreen)
                || Math.abs(vertical) < 0.01D) {
            return false;
        }

        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        if (client.getWindow() != null && client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            mouseX = mouseX * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
            mouseY = mouseY * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
        }

        Slot slot = ((HandledScreenAccessor) handledScreen).fluxvisuals$getFocusedSlot();
        if (slot == null) {
            slot = ((HandledScreenAccessor) handledScreen).fluxvisuals$getSlotAt(mouseX, mouseY);
        }
        if (slot == null) {
            int guiLeft = ((HandledScreenAccessor) handledScreen).fluxvisuals$getX();
            int guiTop = ((HandledScreenAccessor) handledScreen).fluxvisuals$getY();
            for (Slot s : handledScreen.getScreenHandler().slots) {
                if (s.isEnabled() && mouseX >= guiLeft + s.x - 1 && mouseX < guiLeft + s.x + 17
                        && mouseY >= guiTop + s.y - 1 && mouseY < guiTop + s.y + 17) {
                    slot = s;
                    break;
                }
            }
        }
        if (slot == null || !slot.hasStack()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (delayMs > 0 && now - lastMoveMs < delayMs) {
            return true;
        }
        lastMoveMs = now;

        if (Screen.hasShiftDown()) {
            net.minecraft.item.Item targetItem = slot.getStack().getItem();
            int syncId = handledScreen.getScreenHandler().syncId;
            for (Slot s : handledScreen.getScreenHandler().slots) {
                if (s.isEnabled() && s.hasStack() && s.getStack().isOf(targetItem)) {
                    client.interactionManager.clickSlot(syncId, s.id, 0,
                            SlotActionType.QUICK_MOVE, client.player);
                }
            }
        } else {
            client.interactionManager.clickSlot(handledScreen.getScreenHandler().syncId, slot.id, 0,
                    SlotActionType.QUICK_MOVE, client.player);
        }
        return true;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        int next = Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, delayMs));
        if (this.delayMs == next) {
            return;
        }
        this.delayMs = next;
        dev.fuga.fluxvisuals.FluxVisualsClient.requestConfigSave();
    }
}
