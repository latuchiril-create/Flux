package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public final class AutoSwap extends Module {
    public static final String BIND_KEY = "autoswap_bind";
    private static final long SWAP_DELAY_MS = 200L;
    private static final int OFFHAND_SWAP_BUTTON = 40;
    private static final String FORBIDDEN_SERVER = "mc.space-times.ru";

    private SwapItem firstItem = SwapItem.TALISMAN;
    private SwapItem secondItem = SwapItem.SPHERE;
    private PendingSwap pendingSwap;
    private boolean hidingInventory;

    public AutoSwap() {
        super("ItemSwap", "Swaps selected items into the offhand.", ModuleCategory.UTILS);
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

        PendingSwap pending = pendingSwap;
        if (pending == null) {
            return;
        }

        if (pending.hideInventory()) {
            hidingInventory = true;
            hideCursor(client);
        }

        if (System.currentTimeMillis() - pending.startedAt() < SWAP_DELAY_MS) {
            return;
        }

        executeSwap(client, pending);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        cancelPending(client);
    }

    public void triggerSwap() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || pendingSwap != null || client == null || client.player == null || client.interactionManager == null) {
            return;
        }

        String forbiddenAddress = forbiddenServerAddress(client);
        if (forbiddenAddress != null) {
            actionbar(client, Text.literal("AutoSwap \u043d\u0430 \u0441\u0435\u0440\u0432\u0435 ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(forbiddenAddress).formatted(Formatting.WHITE))
                    .append(Text.literal(" \u0437\u0430\u043f\u0440\u0435\u0449\u0435\u043d\u043d").formatted(Formatting.RED)));
            return;
        }

        SwapItem target = targetForCurrentOffhand(client.player.getOffHandStack());
        FindResult result = findBestSlot(client.player.getInventory(), target);
        if (result == null) {
            actionbar(client, Text.literal("\u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u0434\u043b\u044f \u0441\u0432\u0430\u043f\u0430 \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442: ")
                    .formatted(Formatting.RED)
                    .append(target.fallbackText()));
            return;
        }

        MutableText itemName = displayName(result.stack());
        int previousHotbarSlot = client.player.getInventory().getSelectedSlot();
        if (result.hotbarSlot()) {
            client.player.getInventory().setSelectedSlot(result.hotbarSlotIndex());
        }

        pendingSwap = new PendingSwap(result.screenSlot(), result.hotbarSlotIndex(), previousHotbarSlot, itemName, result.hotbarSlot(), !result.hotbarSlot(), System.currentTimeMillis());
        if (result.hotbarSlot()) {
            return;
        }

        hidingInventory = true;
        client.setScreen(new InventoryScreen(client.player));
        hideCursor(client);
    }

    public SwapItem getFirstItem() {
        return firstItem;
    }

    public void setFirstItem(SwapItem firstItem) {
        if (firstItem == null || this.firstItem == firstItem) {
            return;
        }
        this.firstItem = firstItem;
        FluxVisualsClient.requestConfigSave();
    }

    public SwapItem getSecondItem() {
        return secondItem;
    }

    public void setSecondItem(SwapItem secondItem) {
        if (secondItem == null || this.secondItem == secondItem) {
            return;
        }
        this.secondItem = secondItem;
        FluxVisualsClient.requestConfigSave();
    }

    public int getKeyBind() {
        return dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("action_autoswap", dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.getBind(BIND_KEY, GLFW.GLFW_KEY_R));
    }

    public void setKeyBind(int key) {
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.put("action_autoswap", key);
        dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.setBind(BIND_KEY, key);
        FluxVisualsClient.requestConfigSave();
    }

    public boolean shouldHideInventory() {
        return hidingInventory;
    }

    private void executeSwap(MinecraftClient client, PendingSwap pending) {
        try {
            if (pending.hotbarSlot() >= 0) {
                client.player.getInventory().setSelectedSlot(pending.hotbarSlot());
            }
            if (pending.swapWithKeyAction()) {
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN
                ));
            } else {
                client.interactionManager.clickSlot(
                        client.player.playerScreenHandler.syncId,
                        pending.screenSlot(),
                        OFFHAND_SWAP_BUTTON,
                        SlotActionType.SWAP,
                        client.player
                );
            }
            actionbar(client, Text.literal("\u0421\u0432\u0430\u043f\u043d\u0443\u043b \u043d\u0430 ")
                    .formatted(Formatting.WHITE)
                    .append(pending.itemName()));
        } finally {
            if (pending.previousHotbarSlot() >= 0) {
                client.player.getInventory().setSelectedSlot(pending.previousHotbarSlot());
            }
            pendingSwap = null;
            closeHiddenInventory(client);
        }
    }

    private void cancelPending(MinecraftClient client) {
        pendingSwap = null;
        closeHiddenInventory(client);
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

    private SwapItem targetForCurrentOffhand(ItemStack offhand) {
        if (firstItem.matches(offhand) && firstItem != secondItem) {
            return secondItem;
        }
        return firstItem;
    }

    private static FindResult findBestSlot(PlayerInventory inventory, SwapItem target) {
        FindResult best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < 36; index++) {
            ItemStack stack = inventory.getStack(index);
            if (!target.matches(stack)) {
                continue;
            }

            int score = target.score(stack, index);
            if (score > bestScore) {
                bestScore = score;
                best = new FindResult(screenSlot(index), index >= 0 && index < 9, index >= 0 && index < 9 ? index : -1, stack.copy());
            }
        }
        return best;
    }

    private static int screenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static String forbiddenServerAddress(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.address == null) {
            return null;
        }
        return server.address.toLowerCase(Locale.ROOT).contains(FORBIDDEN_SERVER) ? server.address : null;
    }

    private static void actionbar(MinecraftClient client, Text message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(message, true);
        }
    }

    private static MutableText displayName(ItemStack stack) {
        MutableText name = stack.getName().copy();
        if (name.getStyle().getColor() == null) {
            name.formatted(stack.getRarity().getFormatting());
        }
        return name;
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

    public enum SwapItem {
        SPHERE("\u0421\u0444\u0435\u0440\u0430", Items.PLAYER_HEAD, Formatting.AQUA),
        TALISMAN("\u0422\u0430\u043b\u0438\u0441\u043c\u0430\u043d", Items.TOTEM_OF_UNDYING, Formatting.GOLD);

        private final String label;
        private final Item item;
        private final Formatting color;

        SwapItem(String label, Item item, Formatting color) {
            this.label = label;
            this.item = item;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public String fullLabel() {
            return label;
        }

        private Text fallbackText() {
            return Text.literal(label).formatted(color);
        }

        private boolean matches(ItemStack stack) {
            return stack != null && stack.isOf(item);
        }

        private int score(ItemStack stack, int inventoryIndex) {
            int score = 1000 - inventoryIndex;
            if (this == TALISMAN && stack.hasEnchantments()) {
                score += 10_000;
            }
            if (inventoryIndex >= 0 && inventoryIndex < 9) {
                score += 100;
            }
            return score;
        }
    }

    private record FindResult(int screenSlot, boolean hotbarSlot, int hotbarSlotIndex, ItemStack stack) {
    }

    private record PendingSwap(int screenSlot, int hotbarSlot, int previousHotbarSlot, MutableText itemName, boolean swapWithKeyAction,
                               boolean hideInventory, long startedAt) {
    }
}
