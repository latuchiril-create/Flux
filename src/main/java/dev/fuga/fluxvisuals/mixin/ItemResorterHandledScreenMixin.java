package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.gui.AutoBuyButtonScreen;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.ItemResorter;
import dev.fuga.fluxvisuals.modules.visual.AutoResellAFK;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class ItemResorterHandledScreenMixin extends Screen implements AutoBuyButtonScreen {
    @Unique
    private static final int FLUXVISUALS_AUTOBUY_BUTTON_WIDTH = 110;
    @Unique
    private static final int FLUXVISUALS_AUTOBUY_BUTTON_HEIGHT = 20;
    @Unique
    private static final int FLUXVISUALS_AUTOBUY_BUTTON_GAP = 5;
    @Unique
    private static final long FLUXVISUALS_AUTOBUY_GUI_RECREATE_GRACE_MS = 2_000L;
    @Unique
    private static final int FLUXVISUALS_RECENT_PURCHASE_SLOT_COUNT = 9;
    @Unique
    private static final int FLUXVISUALS_RECENT_PURCHASE_SLOT_SIZE = 18;
    @Unique
    private static final int FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH = 26;
    @Unique
    private static final DateTimeFormatter FLUXVISUALS_PURCHASE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    @Unique
    private static boolean fluxvisuals$autoBuyButtonGlobalLatch;
    @Unique
    private static long fluxvisuals$lastAuctionRefreshSeenAt;
    @Unique
    private ButtonWidget fluxvisuals$autoBuyButton;
    @Unique
    private boolean fluxvisuals$autoBuyButtonLatched;

    protected ItemResorterHandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void fluxvisuals$clearAhHelperOnInit(CallbackInfo ci) {
        FluxVisualsClient.MODULE_MANAGER.getAHHelper().clear();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void fluxvisuals$clearAhHelperOnRemoved(CallbackInfo ci) {
        FluxVisualsClient.MODULE_MANAGER.getAHHelper().clear();
    }

    @Unique
    private static long fluxvisuals$slotOverlayErrors;
    @Unique
    private static long fluxvisuals$lastSlotOverlayErrorAt;
    @Unique
    private static long fluxvisuals$lastItemResorterSwordDiagnosticAt;

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void fluxvisuals$drawSlotBackgroundHighlight(DrawContext context, Slot slot, CallbackInfo ci) {
        try {
            fluxvisuals$drawSlotBackgroundHighlightInner(context, slot);
        } catch (Throwable error) {
            fluxvisuals$logSlotOverlayError(error);
        }
    }

    @Unique
    private void fluxvisuals$drawSlotBackgroundHighlightInner(DrawContext context, Slot slot) {
        if (slot == null || !slot.hasStack()) {
            return;
        }
        dev.fuga.fluxvisuals.modules.visual.AHHelper ahHelper = FluxVisualsClient.MODULE_MANAGER.getAHHelper();
        if (ahHelper.isEnabled() && fluxvisuals$hasAuctionTitle()
                && slot.inventory != MinecraftClient.getInstance().player.getInventory()) {
            int ahColor = ahHelper.colorFor(slot.id);
            if (ahColor != 0) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x40000000 | ahColor);
            }
        }
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        if (itemResorter.isEnabled()) {
            List<String> tooltipLines = fluxvisuals$tooltipLines(fluxvisuals$tooltipTexts(slot.getStack()));
            ItemResorter.HighlightMatch match = itemResorter.matchTooltip(slot.getStack(), tooltipLines);
            if (match.matches()) {
                int rgb = match.colorRgb();
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x36000000 | rgb);
            }
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void fluxvisuals$drawItemResorterHighlight(DrawContext context, Slot slot, CallbackInfo ci) {
        try {
            fluxvisuals$drawItemResorterHighlightInner(context, slot);
        } catch (Throwable error) {
            fluxvisuals$logSlotOverlayError(error);
        }
    }

    @Unique
    private static void fluxvisuals$logSlotOverlayError(Throwable error) {
        // Кастомный оверлей слотов никогда не должен ломать ванильную отрисовку:
        // при любой ошибке тултипа/матчинга слот рисуется как обычно.
        // Лог троттлится, чтобы не спамить каждый кадр.
        long now = System.currentTimeMillis();
        fluxvisuals$slotOverlayErrors++;
        if (now - fluxvisuals$lastSlotOverlayErrorAt > 30_000L) {
            fluxvisuals$lastSlotOverlayErrorAt = now;
            BotDebug.error("SLOT_OVERLAY_ERROR", null,
                    "custom slot overlay failed " + fluxvisuals$slotOverlayErrors + " times, vanilla draw preserved",
                    error);
        }
    }

    @Unique
    private void fluxvisuals$drawItemResorterHighlightInner(DrawContext context, Slot slot) {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        AutoBuy autoBuy = FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
        if (slot == null || !slot.hasStack()) {
            return;
        }

        dev.fuga.fluxvisuals.modules.visual.AHHelper ahHelper = FluxVisualsClient.MODULE_MANAGER.getAHHelper();
        if (ahHelper.isEnabled() && fluxvisuals$hasAuctionTitle()
                && slot.inventory != MinecraftClient.getInstance().player.getInventory()) {
            List<String> ahTooltip = fluxvisuals$tooltipLines(new ArrayList<>(getTooltipFromItem(
                    MinecraftClient.getInstance(), slot.getStack())));
            Long ahPrice = ItemResorter.extractPrice(ahTooltip);
            if (ahHelper.isDye(slot.getStack())) {
                ahHelper.removeSlot(slot.id);
                return;
            }
            ahHelper.updateSlot(slot.id, ahPrice);
            int ahColor = ahHelper.colorFor(slot.id);
            if (ahColor != 0) {
                context.drawBorder(slot.x, slot.y, 16, 16, 0xD0000000 | ahColor);
            }
        }

        autoBuy.considerPurchaseConfirmationSlot(slot);
        if (!itemResorter.isEnabled() && !autoBuy.isScanningAuction()) {
            return;
        }
        List<Text> tooltipSnapshot = fluxvisuals$tooltipTexts(slot.getStack());
        List<String> tooltipLines = fluxvisuals$tooltipLines(tooltipSnapshot);
        autoBuy.considerAuctionSlot(slot, tooltipLines, tooltipSnapshot);
        if (!itemResorter.isEnabled()) {
            return;
        }

        ItemResorter.HighlightMatch match = itemResorter.matchTooltip(slot.getStack(), tooltipLines);
        fluxvisuals$debugItemResorterSword(slot, itemResorter, tooltipLines, match);
        if (!match.matches()) {
            return;
        }

        int left = slot.x;
        int top = slot.y;
        int rgb = match.colorRgb();
        int edge = 0xD0000000 | rgb;
        context.drawBorder(left, top, 16, 16, edge);
    }

    @Unique
    private static void fluxvisuals$debugItemResorterSword(
            Slot slot,
            ItemResorter itemResorter,
            List<String> tooltipLines,
            ItemResorter.HighlightMatch match
    ) {
        if (!slot.getStack().isOf(Items.NETHERITE_SWORD)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - fluxvisuals$lastItemResorterSwordDiagnosticAt < 2_000L) {
            return;
        }
        fluxvisuals$lastItemResorterSwordDiagnosticAt = now;
        BotDebug.info("DEBUG-ir7d", null,
                "slot=" + slot.id
                        + " match=" + match.matches()
                        + " enchantNeeded=" + itemResorter.getEnchantNeeded()
                        + " enchantIgnored=" + itemResorter.getEnchantIgnored()
                        + " priceFilter=" + itemResorter.isPriceFilterEnabled()
                        + " maxPrice=" + itemResorter.getMaxPrice()
                        + " parsedPrice=" + ItemResorter.extractPrice(tooltipLines)
                        + " tooltip=" + tooltipLines);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fluxvisuals$createAutoBuyButton(CallbackInfo ci) {
        AutoBuy autoBuy = FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
        fluxvisuals$autoBuyButton = ButtonWidget.builder(
                        fluxvisuals$autoBuyButtonText(autoBuy.isEnabled()),
                        button -> {
                            autoBuy.toggle();
                            button.setMessage(fluxvisuals$autoBuyButtonText(autoBuy.isEnabled()));
                        }
                )
                .dimensions(
                        fluxvisuals$autoBuyButtonX(),
                        fluxvisuals$autoBuyButtonY(),
                        FLUXVISUALS_AUTOBUY_BUTTON_WIDTH,
                        FLUXVISUALS_AUTOBUY_BUTTON_HEIGHT
                )
                .build();
        fluxvisuals$autoBuyButton.visible = false;
        addDrawableChild(fluxvisuals$autoBuyButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void fluxvisuals$prepareAutoBuyButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (fluxvisuals$autoBuyButton == null) {
            return;
        }
        AutoBuy autoBuy = FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
        long now = System.currentTimeMillis();
        int auctionContent = fluxvisuals$auctionButtonContent();
        boolean hasRefresh = (auctionContent & 1) != 0;
        boolean hasNetheriteSword = (auctionContent & 2) != 0;
        boolean hasNetheriteSwordTitle = fluxvisuals$hasNetheriteSwordTitle();
        if (hasNetheriteSwordTitle) {
            fluxvisuals$autoBuyButtonGlobalLatch = true;
            fluxvisuals$autoBuyButtonLatched = true;
            fluxvisuals$lastAuctionRefreshSeenAt = now;
        }
        if (!fluxvisuals$autoBuyButtonLatched
                && fluxvisuals$autoBuyButtonGlobalLatch
                && now - fluxvisuals$lastAuctionRefreshSeenAt > FLUXVISUALS_AUTOBUY_GUI_RECREATE_GRACE_MS) {
            fluxvisuals$autoBuyButtonGlobalLatch = false;
        }
        if (!fluxvisuals$autoBuyButtonLatched
                && fluxvisuals$autoBuyButtonGlobalLatch
                && now - fluxvisuals$lastAuctionRefreshSeenAt <= FLUXVISUALS_AUTOBUY_GUI_RECREATE_GRACE_MS
                && fluxvisuals$hasAuctionTitle()) {
            fluxvisuals$autoBuyButtonLatched = true;
        }
        if (hasRefresh) {
            if (hasNetheriteSword) {
                fluxvisuals$autoBuyButtonGlobalLatch = true;
            }
            if (fluxvisuals$autoBuyButtonGlobalLatch) {
                fluxvisuals$autoBuyButtonLatched = true;
            }
            fluxvisuals$lastAuctionRefreshSeenAt = now;
        }
        fluxvisuals$autoBuyButton.visible = fluxvisuals$autoBuyButtonLatched;
        fluxvisuals$autoBuyButton.active = true;
        fluxvisuals$autoBuyButton.setX(fluxvisuals$autoBuyButtonX());
        fluxvisuals$autoBuyButton.setY(fluxvisuals$autoBuyButtonY());
        fluxvisuals$autoBuyButton.setMessage(fluxvisuals$autoBuyButtonText(autoBuy.isEnabled()));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fluxvisuals$runAutoBuy(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AutoBuy autoBuy = FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
        fluxvisuals$drawRecentPurchases(context, mouseX, mouseY, autoBuy);
        autoBuy.onHandledScreenRendered(MinecraftClient.getInstance());
        if (fluxvisuals$hasResellButton()) {
            dev.fuga.fluxvisuals.modules.visual.AHHelper ahHelper = FluxVisualsClient.MODULE_MANAGER.getAHHelper();
            long storageTotal = 0L;
            for (Slot slot : ((HandledScreen<?>) (Object) this).getScreenHandler().slots) {
                if (!slot.hasStack()
                        || slot.inventory == MinecraftClient.getInstance().player.getInventory()) {
                    continue;
                }
                List<String> tooltip = fluxvisuals$tooltipLines(new ArrayList<>(getTooltipFromItem(
                        MinecraftClient.getInstance(), slot.getStack())));
                Long price = ItemResorter.extractPrice(tooltip);
                if (price != null && price > 0L) {
                    storageTotal += price;
                }
            }
            ahHelper.setTotalPrice(storageTotal);
            String ahTitle = "В хранилище: " + String.format(Locale.ROOT, "%,d", ahHelper.totalPrice())
                    + "$";
            HandledScreenAccessor accessor = (HandledScreenAccessor) this;
            int width = textRenderer.getWidth(ahTitle);
            int left = accessor.fluxvisuals$getX() - width - 8;
            int top = accessor.fluxvisuals$getY() + 20;
            if (left < 4) {
                left = accessor.fluxvisuals$getX() + accessor.fluxvisuals$getBackgroundWidth() + 8;
            }
            context.drawTextWithShadow(textRenderer, Text.literal(ahTitle), left, top, 0xFFFFFFFF);
        }
    }

    @Unique
    private boolean fluxvisuals$hasResellButton() {
        for (Slot slot : ((HandledScreen<?>) (Object) this).getScreenHandler().slots) {
            if (!slot.hasStack()) continue;
            String name = slot.getStack().getName().getString()
                .replaceAll("§[0-9a-fk-or]", "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
            if (name.contains("перевыстав") || name.contains("перевыставить")
                    || name.contains("resell")) return true;
            List<String> tooltip = fluxvisuals$tooltipLines(new ArrayList<>(getTooltipFromItem(
                    MinecraftClient.getInstance(), slot.getStack())));
            for (String line : tooltip) {
                String normalized = line.replaceAll("§[0-9a-fk-or]", "")
                    .toLowerCase(Locale.ROOT)
                    .replace('ё', 'е');
                if (normalized.contains("перевыстав") || normalized.contains("resell")) return true;
            }
        }
        return false;
    }

    @Unique
    private void fluxvisuals$drawRecentPurchases(
            DrawContext context,
            int mouseX,
            int mouseY,
            AutoBuy autoBuy
    ) {
        if (!fluxvisuals$isSwordSearchScreen()) {
            return;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        int panelX = accessor.fluxvisuals$getX() + accessor.fluxvisuals$getBackgroundWidth() + 6;
        int panelY = Math.max(4, accessor.fluxvisuals$getY());
        int visibleSlots = Math.min(
                FLUXVISUALS_RECENT_PURCHASE_SLOT_COUNT,
                Math.max(1, (height - panelY - 8) / FLUXVISUALS_RECENT_PURCHASE_SLOT_SIZE)
        );
        int panelHeight = visibleSlots * FLUXVISUALS_RECENT_PURCHASE_SLOT_SIZE + 8;

        context.fill(
                panelX + 2,
                panelY + 2,
                panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH + 2,
                panelY + panelHeight + 2,
                0xFF202020
        );
        context.fill(
                panelX,
                panelY,
                panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH,
                panelY + panelHeight,
                0xFFC6C6C6
        );
        context.fill(panelX, panelY, panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH, panelY + 2, 0xFFFFFFFF);
        context.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0xFFFFFFFF);
        context.fill(panelX, panelY + panelHeight - 2,
                panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH, panelY + panelHeight, 0xFF555555);
        context.fill(panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH - 2, panelY,
                panelX + FLUXVISUALS_RECENT_PURCHASE_PANEL_WIDTH, panelY + panelHeight, 0xFF555555);

        List<AutoBuy.RecentPurchase> purchases = autoBuy.getRecentPurchases();
        AutoBuy.RecentPurchase hovered = null;
        for (int index = 0; index < visibleSlots; index++) {
            int slotX = panelX + 4;
            int slotY = panelY + 4 + index * FLUXVISUALS_RECENT_PURCHASE_SLOT_SIZE;
            boolean isHovered = mouseX >= slotX && mouseX < slotX + 18
                    && mouseY >= slotY && mouseY < slotY + 18;
            fluxvisuals$drawMinecraftSlot(context, slotX, slotY, isHovered);

            if (index >= purchases.size()) {
                continue;
            }
            AutoBuy.RecentPurchase purchase = purchases.get(index);
            if (purchase.stack().isEmpty()) {
                continue;
            }
            context.drawItem(purchase.stack(), slotX + 1, slotY + 1);
            // drawStackOverlay уже рисует полоску прочности ванильным способом;
            // отдельный кастомный бар давал задвоение ("накладывается 2 раза").
            context.drawStackOverlay(textRenderer, purchase.stack(), slotX + 1, slotY + 1);
            if (isHovered) {
                hovered = purchase;
            }
        }

        if (hovered != null) {
            fluxvisuals$drawRecentPurchaseTooltip(context, mouseX, mouseY, hovered);
        }
    }

    @Unique
    private static void fluxvisuals$drawMinecraftSlot(DrawContext context, int x, int y, boolean hovered) {
        context.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        context.fill(x, y, x + 18, y + 1, 0xFF373737);
        context.fill(x, y, x + 1, y + 18, 0xFF373737);
        context.fill(x, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        context.fill(x + 17, y, x + 18, y + 18, 0xFFFFFFFF);
        if (hovered) {
            context.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
        }
    }

    @Unique
    private void fluxvisuals$drawRecentPurchaseTooltip(
            DrawContext context,
            int mouseX,
            int mouseY,
            AutoBuy.RecentPurchase purchase
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        List<Text> tooltip = purchase.tooltip().isEmpty()
                ? new ArrayList<>(getTooltipFromItem(client, purchase.stack()))
                : new ArrayList<>(purchase.tooltip());
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("\u0426\u0435\u043d\u0430 \u043f\u043e\u043a\u0443\u043f\u043a\u0438: "
                        + fluxvisuals$formatPrice(purchase.price()))
                .formatted(Formatting.GOLD));
        tooltip.add(Text.literal("\u041f\u0440\u043e\u0434\u0430\u0432\u0435\u0446: "
                        + (purchase.seller() == null || purchase.seller().isBlank()
                        ? "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u0435\u043d"
                        : purchase.seller()))
                .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("\u0412\u0440\u0435\u043c\u044f \u043f\u043e\u043a\u0443\u043f\u043a\u0438: "
                        + FLUXVISUALS_PURCHASE_TIME_FORMAT.format(
                        Instant.ofEpochMilli(purchase.purchasedAt()).atZone(ZoneId.systemDefault())
                ))
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("\u042d\u0442\u043e \u043a\u043e\u043f\u0438\u044f \u0438\u0437 \u0438\u0441\u0442\u043e\u0440\u0438\u0438, \u0435\u0451 \u043d\u0435\u043b\u044c\u0437\u044f \u0432\u0437\u044f\u0442\u044c")
                .formatted(Formatting.DARK_GRAY));
        context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
    }

    @Unique
    private static String fluxvisuals$formatPrice(long price) {
        if (price < 0L) {
            return "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430";
        }
        return String.format(Locale.ROOT, "%,d$", price).replace(',', ' ');
    }

    @Unique
    private boolean fluxvisuals$isSwordSearchScreen() {
        String title = getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("\u043f\u043e\u043a\u0443\u043f")
                || title.contains("buy")
                || title.contains("confirm")) {
            return false;
        }
        boolean searchTitle = title.contains("\u043f\u043e\u0438\u0441\u043a") || title.contains("search");
        boolean netheriteSwordTitle = fluxvisuals$hasNetheriteSwordTitle(title);
        boolean visibleNetheriteSword = (fluxvisuals$auctionButtonContent() & 2) != 0;
        return netheriteSwordTitle || searchTitle && visibleNetheriteSword;
    }

    @Unique
    private static Text fluxvisuals$autoBuyButtonText(boolean enabled) {
        return Text.literal(enabled ? "AutoBuy ON" : "AutoBuy OFF");
    }

    @Override
    public boolean fluxvisuals$isMouseOverAutoBuyButton(double mouseX, double mouseY) {
        return fluxvisuals$autoBuyButton != null
                && fluxvisuals$autoBuyButton.visible
                && fluxvisuals$autoBuyButton.active
                && fluxvisuals$autoBuyButton.isMouseOver(mouseX, mouseY);
    }

    @Unique
    private int fluxvisuals$auctionButtonContent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.currentScreenHandler == null) {
            return 0;
        }

        boolean hasRefresh = false;
        boolean hasNetheriteSword = false;
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isOf(Items.NETHERITE_SWORD)) {
                hasNetheriteSword = true;
            }
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("\u043e\u0431\u043d\u043e\u0432") || name.contains("refresh")) {
                hasRefresh = true;
            }
        }
        return (hasRefresh ? 1 : 0) | (hasNetheriteSword ? 2 : 0);
    }

    @Unique
    private boolean fluxvisuals$hasAuctionTitle() {
        String title = getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("\u0430\u0443\u043a\u0446\u0438\u043e\u043d")
                || title.contains("auction")
                || title.contains("\u043f\u043e\u0438\u0441\u043a")
                || title.contains("search")
                || fluxvisuals$hasNetheriteSwordTitle(title);
    }

    @Unique
    private boolean fluxvisuals$hasNetheriteSwordTitle() {
        return fluxvisuals$hasNetheriteSwordTitle(getTitle().getString().toLowerCase(Locale.ROOT));
    }

    @Unique
    private static boolean fluxvisuals$hasNetheriteSwordTitle(String title) {
        return title.contains("\u043d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0439 \u043c\u0435\u0447")
                || title.contains("netherite sword");
    }

    @Unique
    private int fluxvisuals$autoBuyButtonX() {
        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        return accessor.fluxvisuals$getX()
                + (accessor.fluxvisuals$getBackgroundWidth() - FLUXVISUALS_AUTOBUY_BUTTON_WIDTH) / 2;
    }

    @Unique
    private int fluxvisuals$autoBuyButtonY() {
        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        return accessor.fluxvisuals$getY() + accessor.fluxvisuals$getBackgroundHeight()
                + FLUXVISUALS_AUTOBUY_BUTTON_GAP;
    }

    private List<Text> fluxvisuals$tooltipTexts(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return List.of();
        }
        return new ArrayList<>(getTooltipFromItem(client, stack));
    }

    private static List<String> fluxvisuals$tooltipLines(List<Text> tooltip) {
        List<String> lines = new ArrayList<>();
        for (Text text : tooltip) {
            String line = text == null ? "" : text.getString();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
