package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Highlights the three cheapest priced entries in any handled auction screen. */
public final class AHHelper extends Module {
    private final Map<Integer, Long> prices = new HashMap<>();
    private long totalPrice;

    public AHHelper() {
        super("AHHelper", "Highlights the three cheapest priced items.", ModuleCategory.UTILS);
    }

    public void clear() {
        prices.clear();
        totalPrice = 0L;
    }

    public void updateSlot(int slotId, Long price) {
        if (price == null || price <= 0L) prices.remove(slotId);
        else prices.put(slotId, price);
        totalPrice = prices.values().stream().mapToLong(Long::longValue).sum();
    }

    public void removeSlot(int slotId) {
        prices.remove(slotId);
        totalPrice = prices.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean isDye(net.minecraft.item.ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem().getName().getString().toLowerCase(Locale.ROOT).contains("красител");
    }

    public int colorFor(int slotId) {
        if (!isEnabled()) return 0;
        List<Map.Entry<Integer, Long>> cheapest = prices.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .toList();
        for (int i = 0; i < cheapest.size(); i++) {
            if (cheapest.get(i).getKey() == slotId) {
                return i == 0 ? 0x55FF55 : i == 1 ? 0xFFFF55 : 0xFF5555;
            }
        }
        return 0;
    }

    public long totalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(long totalPrice) {
        this.totalPrice = Math.max(0L, totalPrice);
    }

    public String visualTitle(MinecraftClient client, boolean resellGui) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) return null;
        if (!resellGui) return null;
        return "В хранилище: " + String.format(Locale.ROOT, "%,d", totalPrice) + "$";
    }
}
