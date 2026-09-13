package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ElytraSwap extends Module {
    public static final String BIND_KEY = "elytra_swap_bind";
    public static final String FIREWORK_BIND_KEY = "elytra_firework_bind";

    private static final long ACTION_DELAY_MS = 170L;
    private static final int CHEST_SLOT = 6;

    private PendingAction pendingAction;
    private boolean hidingInventory;

    public ElytraSwap() {
        super("ElytraSwap", "Swaps elytra and chestplate by bind.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) {
            cancelPending(client);
            return;
        }
        if (client == null || client.player == null || client.interactionManager == null) {
            cancelPending(client);
            return;
        }

        PendingAction pending = pendingAction;
        if (pending == null) {
            return;
        }

        if (pending.hideInventory()) {
            hidingInventory = true;
            hideCursor(client);
        }

        if (System.currentTimeMillis() - pending.startedAt() < ACTION_DELAY_MS) {
            return;
        }

        try {
            switch (pending.kind()) {
                case SWAP_EQUIP -> executeEquipSwap(client, pending);
                case REMOVE_ONLY -> executeRemoveOnly(client, pending);
                case FIREWORK_USE -> executeFirework(client, pending);
            }
        } finally {
            pendingAction = null;
            closeHiddenInventory(client);
        }
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        cancelPending(client);
    }

    public void triggerSwap() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ready(client)) {
            return;
        }

        ItemStack equipped = client.player.getEquippedStack(EquipmentSlot.CHEST);
        if (equipped.isOf(Items.ELYTRA)) {
            FindResult chestplate = findBestChestplate(client.player.getInventory());
            if (chestplate == null) {
                FindResult emptySlot = findEmptySlot(client.player.getInventory());
                if (emptySlot == null) {
                    actionbar(client, "Нагрудник не найден и нет места снять элитры");
                    return;
                }
                pendingAction = new PendingAction(ActionKind.REMOVE_ONLY, emptySlot.screenSlot(), emptySlot.hotbarSlot(), client.player.getInventory().getSelectedSlot(), true, "Нагрудник не найден", System.currentTimeMillis());
                openHiddenInventory(client);
                return;
            }
            pendingAction = new PendingAction(ActionKind.SWAP_EQUIP, chestplate.screenSlot(), chestplate.hotbarSlot(), client.player.getInventory().getSelectedSlot(), true, chestplate.label(), System.currentTimeMillis());
            openHiddenInventory(client);
            return;
        }

        FindResult elytra = findItem(client.player.getInventory(), Items.ELYTRA, "Элитры");
        if (elytra == null) {
            actionbar(client, "Элитры не найдены");
            return;
        }
        pendingAction = new PendingAction(ActionKind.SWAP_EQUIP, elytra.screenSlot(), elytra.hotbarSlot(), client.player.getInventory().getSelectedSlot(), true, "Элитры", System.currentTimeMillis());
        openHiddenInventory(client);
    }

    public void triggerFirework() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ready(client)) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        if (client.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.OFF_HAND);
            return;
        }

        FindResult firework = findItem(inventory, Items.FIREWORK_ROCKET, "Фейерверк");
        if (firework == null) {
            actionbar(client, "Фейерверк не найден");
            return;
        }

        if (firework.hotbarSlot() >= 0) {
            inventory.setSelectedSlot(firework.hotbarSlot());
            client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
            inventory.setSelectedSlot(selectedSlot);
            return;
        }

        pendingAction = new PendingAction(ActionKind.FIREWORK_USE, firework.screenSlot(), selectedSlot, selectedSlot, true, "Фейерверк", System.currentTimeMillis());
        openHiddenInventory(client);
    }

    public int getKeyBind() {
        return dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("action_elytraswap", dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.getBind(BIND_KEY, GLFW.GLFW_KEY_G));
    }

    public int getFireworkKeyBind() {
        return dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(FIREWORK_BIND_KEY, dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.getBind(FIREWORK_BIND_KEY, GLFW.GLFW_KEY_H));
    }

    public void setFireworkKeyBind(int key) {
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.put(FIREWORK_BIND_KEY, key);
        dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.setBind(FIREWORK_BIND_KEY, key);
        FluxVisualsClient.requestConfigSave();
    }

    public void setKeyBind(int key) {
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.put("action_elytraswap", key);
        dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.setBind(BIND_KEY, key);
        FluxVisualsClient.requestConfigSave();
    }

    public boolean shouldHideInventory() {
        return hidingInventory;
    }

    private void executeEquipSwap(MinecraftClient client, PendingAction pending) {
        pickup(client, CHEST_SLOT);
        pickup(client, pending.sourceSlot());
        pickup(client, CHEST_SLOT);

        ItemStack equipped = client.player.getEquippedStack(EquipmentSlot.CHEST);
        if (equipped.isOf(Items.ELYTRA)) {
            actionbar(client, "Свапнул на Элитры");
        } else {
            actionbar(client, "Свапнул на нагрудник");
        }
    }

    private void executeRemoveOnly(MinecraftClient client, PendingAction pending) {
        pickup(client, CHEST_SLOT);
        pickup(client, pending.sourceSlot());
        actionbar(client, pending.message());
    }

    private void executeFirework(MinecraftClient client, PendingAction pending) {
        int targetHotbarSlot = pending.hotbarSlot();
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                pending.sourceSlot(),
                targetHotbarSlot,
                SlotActionType.SWAP,
                client.player
        );
        client.player.getInventory().setSelectedSlot(targetHotbarSlot);
        client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
        client.player.getInventory().setSelectedSlot(pending.previousHotbarSlot());
    }

    private void pickup(MinecraftClient client, int slot) {
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                client.player
        );
    }

    private void cancelPending(MinecraftClient client) {
        pendingAction = null;
        closeHiddenInventory(client);
    }

    private void openHiddenInventory(MinecraftClient client) {
        hidingInventory = true;
        client.setScreen(new InventoryScreen(client.player));
        hideCursor(client);
    }

    private void closeHiddenInventory(MinecraftClient client) {
        if (!hidingInventory) {
            return;
        }
        hidingInventory = false;
        if (client != null && client.player != null) {
            if (client.currentScreen instanceof InventoryScreen) {
                client.player.closeHandledScreen();
                client.setScreen(null);
            }
            restoreCursor(client);
        }
    }

    private boolean ready(MinecraftClient client) {
        return isEnabled() && pendingAction == null && client != null && client.player != null && client.interactionManager != null;
    }

    private static FindResult findItem(PlayerInventory inventory, Item item, String label) {
        for (int index = 0; index < 36; index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isOf(item)) {
                return new FindResult(screenSlot(index), hotbarSlot(index), label);
            }
        }
        return null;
    }

    private static FindResult findEmptySlot(PlayerInventory inventory) {
        for (int index = 9; index < 36; index++) {
            if (inventory.getStack(index).isEmpty()) {
                return new FindResult(screenSlot(index), hotbarSlot(index), "");
            }
        }
        for (int index = 0; index < 9; index++) {
            if (inventory.getStack(index).isEmpty()) {
                return new FindResult(screenSlot(index), hotbarSlot(index), "");
            }
        }
        return null;
    }

    private static FindResult findBestChestplate(PlayerInventory inventory) {
        FindResult best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < 36; index++) {
            ItemStack stack = inventory.getStack(index);
            int score = chestplateScore(stack);
            if (score <= Integer.MIN_VALUE / 4) {
                continue;
            }
            score += stack.getEnchantments().getSize() * 8;
            score += Math.max(0, stack.getMaxDamage() - stack.getDamage()) / 20;
            if (score > bestScore) {
                bestScore = score;
                best = new FindResult(screenSlot(index), hotbarSlot(index), chestplateLabel(stack.getItem()));
            }
        }
        return best;
    }

    private static int chestplateScore(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.NETHERITE_CHESTPLATE) return 700;
        if (item == Items.DIAMOND_CHESTPLATE) return 600;
        if (item == Items.IRON_CHESTPLATE) return 500;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 400;
        if (item == Items.GOLDEN_CHESTPLATE) return 300;
        if (item == Items.LEATHER_CHESTPLATE) return 200;
        return Integer.MIN_VALUE / 4;
    }

    private static String chestplateLabel(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return "Незеритовый нагрудник";
        if (item == Items.DIAMOND_CHESTPLATE) return "Алмазный нагрудник";
        if (item == Items.IRON_CHESTPLATE) return "Железный нагрудник";
        if (item == Items.CHAINMAIL_CHESTPLATE) return "Кольчужный нагрудник";
        if (item == Items.GOLDEN_CHESTPLATE) return "Золотой нагрудник";
        if (item == Items.LEATHER_CHESTPLATE) return "Кожаный нагрудник";
        return "Нагрудник";
    }

    private static int hotbarSlot(int inventoryIndex) {
        return inventoryIndex >= 0 && inventoryIndex < 9 ? inventoryIndex : -1;
    }

    private static int screenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static void actionbar(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static void hideCursor(MinecraftClient client) {
        if (client != null && client.getWindow() != null) {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    private static void restoreCursor(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.currentScreen == null) {
            client.mouse.lockCursor();
        } else {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private enum ActionKind {
        SWAP_EQUIP,
        REMOVE_ONLY,
        FIREWORK_USE
    }

    private record PendingAction(ActionKind kind, int sourceSlot, int hotbarSlot, int previousHotbarSlot,
                                 boolean hideInventory, String message, long startedAt) {
    }

    private record FindResult(int screenSlot, int hotbarSlot, String label) {
    }
}
