package dev.fuga.fluxvisuals.gui.modern;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.modern.font.ModernFont;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.ItemResorter;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Handcrafted, fully dynamic AutoBuy configuration interface for FugaClient.
 * Architecture:
 * - Deterministic, uniform spacing hierarchy (SECTION_GAP = 16px, TITLE_OFFSET = 14px, PADDING = 12px).
 * - Solves all card clipping / label cramming issues across every group.
 * - Cards dynamically adapt height to wrap any number of enchants or sellers with zero protrusion.
 * - Full-height click response on all inputs and switches.
 * - Deep obsidian matte palette (#0A0C10 / #0E1015 / #07080B).
 * - Smooth animated fold/unfold for anarchy settings and chat ad message.
 * - World Gaussian blur without UI blur.
 */
public final class AutoBuyConfigScreen extends Screen {
    // Icons
    private static final Identifier ICON_AUTOBUY = Identifier.of("fluxvisuals", "icons/autobuy.png");
    private static final Identifier ICON_COMBAT  = Identifier.of("fluxvisuals", "icons/combat.png");

    // Unified Spacing Architecture Constants
    private static final float SECTION_GAP      = 16.0F; // Consistent margin between cards and next section titles
    private static final float TITLE_OFFSET     = 14.0F; // Distance from section title to card top
    private static final float CARD_PADDING     = 12.0F; // Symmetrical inner padding for cards
    private static final float ROW_HEIGHT       = 32.0F; // Standard row height for toggle rows

    // Deep obsidian & matte dark palette
    private static final int SURFACE_WINDOW       = 0xFF0A0C10;
    private static final int SURFACE_PANEL        = 0xFF0E1015;
    private static final int SURFACE_INPUT        = 0xFF07080B;
    private static final int BORDER_PANEL         = 0xFF181B24;
    private static final int BORDER_INPUT         = 0xFF1E222D;
    private static final int BORDER_FOCUS         = 0xFF7C3AED;
    private static final int DIVIDER_LINE         = 0xFF13151D;
    private static final int CHIP_BG              = 0xFF13161F;
    private static final int CHIP_BORDER          = 0xFF202534;
    private static final int TEXT_PRIMARY         = 0xFFD2D6E2;
    private static final int TEXT_SECONDARY       = 0xFF727B90;
    private static final int TEXT_MUTED           = 0xFF4A5264;
    private static final int ACCENT_PURPLE        = 0xFF7C3AED;
    private static final int ACCENT_PURPLE_HOVER  = 0xFF8B5CF6;
    private static final int TOGGLE_OFF           = 0xFF1C202C;
    private static final int DANGER_HOVER         = 0xFFEF4444;

    private final Screen parentScreen;

    // State
    private boolean autoBuyEnabled;
    private String category = "Мечи";
    private boolean categoryDropdownOpen = false;

    private boolean priceFilterEnabled;
    private long maxPrice;
    private String priceInput;

    private boolean durabilityFilterEnabled;
    private int minDurabilityPercent;
    private boolean durabilityDragging;

    private final List<String> neededEnchants = new ArrayList<>();
    private String neededInput = "";

    private final List<String> ignoredEnchants = new ArrayList<>();
    private String ignoredInput = "";

    private boolean autoResellEnabled;
    private boolean lowBalanceGuardEnabled;
    private boolean anarchySwitchEnabled;
    private final List<String> anarchyIds = new ArrayList<>();
    private String anarchyInput = "";

    private boolean anarchyAdEnabled;
    private String anarchyAdText = "";
    private boolean rentalSlotsEnabled;

    private final List<String> bannedSellers = new ArrayList<>();
    private String bannedInput = "";

    // Smooth Animation Interpolations
    private float masterToggleAnim = 0.0F;
    private float priceToggleAnim = 0.0F;
    private float durToggleAnim = 0.0F;
    private float resellToggleAnim = 0.0F;
    private float balanceToggleAnim = 0.0F;
    private float anarchyToggleAnim = 0.0F;
    private float anarchySubAnim = 0.0F;
    private float adToggleAnim = 0.0F;
    private float adSubAnim = 0.0F;
    private float rentalToggleAnim = 0.0F;
    private float saveHoverAnim = 0.0F;
    private float cancelHoverAnim = 0.0F;
    private float resetHoverAnim = 0.0F;
    private float dropdownAnim = 0.0F;

    // Focus
    private enum FocusField {
        NONE, PRICE, NEEDED, IGNORED, ANARCHY, AD_TEXT, BANNED
    }
    private FocusField focusedField = FocusField.NONE;

    // Scrolling
    private float scrollY = 0.0F;
    private float targetScrollY = 0.0F;
    private float maxScrollY = 0.0F;
    private boolean scrollThumbDragging = false;
    private float scrollDragStartMouseY = 0.0F;
    private float scrollDragStartScroll = 0.0F;

    // Shared Synchronized Layout Variables
    private float catBarY, catBarH;
    private float itemParamsY, itemParamsCardY, itemParamsH;
    private float enchantGroupY, enchantCardY, enchantGroupH;
    private float neededHeaderY, neededInputY;
    private final List<float[]> neededChipRects = new ArrayList<>();
    private float enchantDividerY;
    private float ignoredHeaderY, ignoredInputY;
    private final List<float[]> ignoredChipRects = new ArrayList<>();
    private float automationGroupY, automationCardY, automationGroupH;
    private float resellRowY, balanceRowY, anarchyRowY;
    private float anarchySubY, anarchySubH;
    private final List<float[]> anarchyChipRects = new ArrayList<>();
    private float anarchyInputY;
    private float adRowY;
    private float adFieldY, adSubH;
    private float rentalRowY;
    private float blacklistGroupY, blacklistCardY, blacklistGroupH;
    private float bannedHeaderY, bannedInputY;
    private final List<float[]> bannedChipRects = new ArrayList<>();
    private float totalContentH;

    public AutoBuyConfigScreen(Screen parentScreen) {
        super(Text.literal("AutoBuy Settings"));
        this.parentScreen = parentScreen;
        loadSettings();
    }

    private void loadSettings() {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER != null
                ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy() : null;
        ItemResorter resorter = FluxVisualsClient.MODULE_MANAGER != null
                ? FluxVisualsClient.MODULE_MANAGER.getItemResorter() : null;

        if (autoBuy != null) {
            autoBuyEnabled = autoBuy.isEnabled();
            autoResellEnabled = autoBuy.isAutoResellEnabled();
            lowBalanceGuardEnabled = autoBuy.isLowBalanceGuardEnabled();
            anarchySwitchEnabled = autoBuy.isAnarchySwitchEnabled();
            anarchyIds.clear();
            anarchyIds.addAll(autoBuy.getAnarchyIds());
            anarchyAdEnabled = autoBuy.isAnarchyAdEnabled();
            anarchyAdText = autoBuy.getAnarchyAdText();
            rentalSlotsEnabled = autoBuy.isRentalSlotsEnabled();
            bannedSellers.clear();
            bannedSellers.addAll(autoBuy.getBannedSellers());
        }

        if (resorter != null) {
            priceFilterEnabled = resorter.isPriceFilterEnabled();
            maxPrice = resorter.getMaxPrice();
            priceInput = String.valueOf(maxPrice);
            durabilityFilterEnabled = resorter.isDurabilityFilterEnabled();
            minDurabilityPercent = resorter.getMinDurabilityPercent();
            neededEnchants.clear();
            neededEnchants.addAll(resorter.getEnchantNeeded());
            ignoredEnchants.clear();
            ignoredEnchants.addAll(resorter.getEnchantIgnored());
        } else {
            priceInput = "3000000";
            minDurabilityPercent = 80;
        }

        masterToggleAnim = autoBuyEnabled ? 1.0F : 0.0F;
        priceToggleAnim = priceFilterEnabled ? 1.0F : 0.0F;
        durToggleAnim = durabilityFilterEnabled ? 1.0F : 0.0F;
        resellToggleAnim = autoResellEnabled ? 1.0F : 0.0F;
        balanceToggleAnim = lowBalanceGuardEnabled ? 1.0F : 0.0F;
        anarchyToggleAnim = anarchySwitchEnabled ? 1.0F : 0.0F;
        anarchySubAnim = anarchySwitchEnabled ? 1.0F : 0.0F;
        adToggleAnim = anarchyAdEnabled ? 1.0F : 0.0F;
        adSubAnim = anarchyAdEnabled ? 1.0F : 0.0F;
        rentalToggleAnim = rentalSlotsEnabled ? 1.0F : 0.0F;
    }

    private void saveAndClose() {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER != null
                ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy() : null;
        ItemResorter resorter = FluxVisualsClient.MODULE_MANAGER != null
                ? FluxVisualsClient.MODULE_MANAGER.getItemResorter() : null;

        if (autoBuy != null) {
            if (autoBuy.isEnabled() != autoBuyEnabled) {
                autoBuy.setEnabled(autoBuyEnabled);
            }
            autoBuy.setAutoResellEnabled(autoResellEnabled);
            autoBuy.setLowBalanceGuardEnabled(lowBalanceGuardEnabled);
            autoBuy.setAnarchySwitchEnabled(anarchySwitchEnabled);
            autoBuy.setAnarchyIds(anarchyIds);
            autoBuy.setAnarchyAdEnabled(anarchyAdEnabled);
            autoBuy.setAnarchyAdText(anarchyAdText);
            autoBuy.setRentalSlotsEnabled(rentalSlotsEnabled);
            autoBuy.setBannedSellers(bannedSellers);
        }

        if (resorter != null) {
            resorter.setPriceFilterEnabled(priceFilterEnabled);
            try {
                long parsed = Long.parseLong(priceInput.replaceAll("[^0-9]", ""));
                resorter.setMaxPrice(parsed);
            } catch (Exception ignored) {
            }
            resorter.setDurabilityFilterEnabled(durabilityFilterEnabled);
            resorter.setMinDurabilityPercent(minDurabilityPercent);
            resorter.setEnchantNeeded(neededEnchants);
            resorter.setEnchantIgnored(ignoredEnchants);
        }

        FluxVisualsClient.requestConfigSave();

        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§7[§dAutoBuy§7] §aНастройки сохранены!"), false);
        }

        close();
    }

    private void resetToDefaults() {
        priceFilterEnabled = true;
        maxPrice = 3_000_000L;
        priceInput = "3000000";
        durabilityFilterEnabled = true;
        minDurabilityPercent = 80;
        autoResellEnabled = true;
        lowBalanceGuardEnabled = true;
        anarchySwitchEnabled = false;
        anarchyAdEnabled = false;
        anarchyAdText = "";
        rentalSlotsEnabled = false;
        neededEnchants.clear();
        neededEnchants.addAll(List.of("Острота 7", "Яд 2", "Добыча 3"));
        ignoredEnchants.clear();
        ignoredEnchants.addAll(List.of("Нестабильный", "Проклятие утраты"));
        anarchyIds.clear();
        anarchyIds.addAll(List.of("231", "232", "234"));
        bannedSellers.clear();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parentScreen);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Suppress vanilla post-effect blur over custom screen
    }

    private static void drawTexture(DrawContext context, Identifier texture, float x, float y, float w, float h, int color) {
        if (texture == null || w <= 0.0F || h <= 0.0F) return;
        Render2D.drawRoundTexture(context, texture, x, y, w, h, 0.0F, color);
    }

    /**
     * Unified deterministic layout pass.
     * Guaranteed 100% mathematical synchronization between drawing, hitboxes, and spacing.
     */
    private void computeLayout(float contentX, float startY, float contentW) {
        float curY = startY;
        float innerX = contentX + CARD_PADDING;
        float innerW = contentW - CARD_PADDING * 2.0F;

        // 1. Category Bar
        catBarY = curY;
        catBarH = 34.0F;
        curY += catBarH + SECTION_GAP;

        // 2. Item Parameters Group
        itemParamsY = curY;
        itemParamsCardY = itemParamsY + TITLE_OFFSET;
        itemParamsH = 66.0F;
        curY = itemParamsCardY + itemParamsH + SECTION_GAP;

        // 3. Enchant Filters Group (Fully dynamic height based on wrapped chips)
        enchantGroupY = curY;
        enchantCardY = enchantGroupY + TITLE_OFFSET;
        float eY = enchantCardY + CARD_PADDING;

        neededHeaderY = eY;
        eY += 14.0F;

        neededChipRects.clear();
        float chipH = 19.0F;
        if (!neededEnchants.isEmpty()) {
            float cX = innerX;
            for (int i = 0; i < neededEnchants.size(); i++) {
                String ench = neededEnchants.get(i);
                float chipW = ModernFont.getWidth(ench, 8.5F, ModernFont.Type.INTER_MEDIUM) + 20.0F;
                if (cX + chipW > innerX + innerW - 4.0F && cX > innerX) {
                    cX = innerX;
                    eY += chipH + 4.0F;
                }
                neededChipRects.add(new float[]{cX, eY, chipW, chipH});
                cX += chipW + 4.0F;
            }
            eY += chipH + 6.0F;
        }

        neededInputY = eY;
        eY += 21.0F + 10.0F;

        enchantDividerY = eY;
        eY += 1.0F + 10.0F;

        ignoredHeaderY = eY;
        eY += 14.0F;

        ignoredChipRects.clear();
        if (!ignoredEnchants.isEmpty()) {
            float cX = innerX;
            for (int i = 0; i < ignoredEnchants.size(); i++) {
                String ench = ignoredEnchants.get(i);
                float chipW = ModernFont.getWidth(ench, 8.5F, ModernFont.Type.INTER_MEDIUM) + 20.0F;
                if (cX + chipW > innerX + innerW - 4.0F && cX > innerX) {
                    cX = innerX;
                    eY += chipH + 4.0F;
                }
                ignoredChipRects.add(new float[]{cX, eY, chipW, chipH});
                cX += chipW + 4.0F;
            }
            eY += chipH + 6.0F;
        }

        ignoredInputY = eY;
        eY += 21.0F + CARD_PADDING; // Symmetrical clean bottom padding

        enchantGroupH = eY - enchantCardY;
        curY = enchantCardY + enchantGroupH + SECTION_GAP;

        // 4. Automation Group
        automationGroupY = curY;
        automationCardY = automationGroupY + TITLE_OFFSET;
        float autoY = automationCardY;

        resellRowY = autoY;
        balanceRowY = autoY + ROW_HEIGHT;
        anarchyRowY = autoY + ROW_HEIGHT * 2.0F;

        anarchySubY = autoY + ROW_HEIGHT * 3.0F;
        anarchyChipRects.clear();
        float curAnarchySubH = 0.0F;
        if (anarchySubAnim > 0.02F) {
            float cX = innerX;
            float aY = anarchySubY + 3.0F;
            if (!anarchyIds.isEmpty()) {
                for (int i = 0; i < anarchyIds.size(); i++) {
                    String aid = anarchyIds.get(i);
                    float chipW = ModernFont.getWidth(aid, 8.5F, ModernFont.Type.SF_BOLD) + 18.0F;
                    if (cX + chipW > innerX + innerW - 4.0F && cX > innerX) {
                        cX = innerX;
                        aY += 20.0F;
                    }
                    anarchyChipRects.add(new float[]{cX, aY, chipW, 17.0F});
                    cX += chipW + 4.0F;
                }
                aY += 20.0F;
            }
            anarchyInputY = aY;
            aY += 20.0F + 5.0F;
            curAnarchySubH = (aY - anarchySubY) * anarchySubAnim;
        }
        anarchySubH = curAnarchySubH;

        adRowY = anarchySubY + anarchySubH;
        float adFieldTrackY = adRowY + ROW_HEIGHT;
        float curAdSubH = 0.0F;
        if (adSubAnim > 0.02F) {
            adFieldY = adFieldTrackY + 3.0F;
            curAdSubH = 26.0F * adSubAnim;
        }
        adSubH = curAdSubH;

        rentalRowY = adFieldTrackY + adSubH;
        automationGroupH = (rentalRowY + ROW_HEIGHT) - autoY;
        curY = automationCardY + automationGroupH + SECTION_GAP;

        // 5. Blacklist Group (Fully dynamic height based on wrapped chips)
        blacklistGroupY = curY;
        blacklistCardY = blacklistGroupY + TITLE_OFFSET;
        float bY = blacklistCardY + CARD_PADDING;
        bannedHeaderY = bY;
        bY += 14.0F;

        bannedChipRects.clear();
        if (!bannedSellers.isEmpty()) {
            float cX = innerX;
            for (int i = 0; i < bannedSellers.size(); i++) {
                String seller = bannedSellers.get(i);
                float chipW = ModernFont.getWidth(seller, 8.5F, ModernFont.Type.SF_BOLD) + 18.0F;
                if (cX + chipW > innerX + innerW - 4.0F && cX > innerX) {
                    cX = innerX;
                    bY += 21.0F;
                }
                bannedChipRects.add(new float[]{cX, bY, chipW, 18.0F});
                cX += chipW + 4.0F;
            }
            bY += 21.0F;
        }

        bannedInputY = bY;
        bY += 21.0F + CARD_PADDING; // Symmetrical clean bottom padding

        blacklistGroupH = bY - blacklistCardY;
        curY = blacklistCardY + blacklistGroupH + 16.0F;

        totalContentH = curY - startY;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. World Gaussian Blur (matching Modern ClickGUI)
        BlurRenderer.drawBlur(0.0F, 0.0F, (float) width, (float) height, 0.0F, 0.75F);

        // 2. Dark backdrop
        Render2D.drawRound(context, 0.0F, 0.0F, (float) width, (float) height, 0.0F, 0x8507090E);

        // Update animations smoothly
        scrollY += (targetScrollY - scrollY) * 0.35F;
        if (Math.abs(targetScrollY - scrollY) < 0.1F) {
            scrollY = targetScrollY;
        }

        masterToggleAnim = approach(masterToggleAnim, autoBuyEnabled ? 1.0F : 0.0F, 0.24F);
        priceToggleAnim  = approach(priceToggleAnim, priceFilterEnabled ? 1.0F : 0.0F, 0.24F);
        durToggleAnim    = approach(durToggleAnim, durabilityFilterEnabled ? 1.0F : 0.0F, 0.24F);
        resellToggleAnim = approach(resellToggleAnim, autoResellEnabled ? 1.0F : 0.0F, 0.24F);
        balanceToggleAnim= approach(balanceToggleAnim, lowBalanceGuardEnabled ? 1.0F : 0.0F, 0.24F);
        anarchyToggleAnim= approach(anarchyToggleAnim, anarchySwitchEnabled ? 1.0F : 0.0F, 0.24F);
        anarchySubAnim   = approach(anarchySubAnim, anarchySwitchEnabled ? 1.0F : 0.0F, 0.25F);
        adToggleAnim     = approach(adToggleAnim, anarchyAdEnabled ? 1.0F : 0.0F, 0.24F);
        adSubAnim        = approach(adSubAnim, anarchyAdEnabled ? 1.0F : 0.0F, 0.25F);
        rentalToggleAnim = approach(rentalToggleAnim, rentalSlotsEnabled ? 1.0F : 0.0F, 0.24F);
        dropdownAnim     = approach(dropdownAnim, categoryDropdownOpen ? 1.0F : 0.0F, 0.28F);

        // Geometry
        float winW = Math.min(width - 24.0F, 470.0F);
        float winH = Math.min(height - 24.0F, 510.0F);
        float winX = (width - winW) * 0.5F;
        float winY = (height - winH) * 0.5F;

        float headerH = 44.0F;
        float footerH = 42.0F;
        float footerY = winY + winH - footerH;

        float clipTop = winY + headerH;
        float clipBottom = footerY;
        float clipHeight = clipBottom - clipTop;

        float contentX = winX + 14.0F;
        float contentW = winW - 28.0F;
        float contentStartY = clipTop + 10.0F - scrollY;

        // Compute synchronized dynamic layout
        computeLayout(contentX, contentStartY, contentW);

        maxScrollY = Math.max(0.0F, totalContentH - clipHeight + 20.0F);
        if (scrollY > maxScrollY) {
            targetScrollY = maxScrollY;
            scrollY = maxScrollY;
        }

        // 3. Window Container
        Render2D.drawRound(context, winX, winY, winW, winH, 10.0F, SURFACE_WINDOW);
        Render2D.drawRoundOutline(context, winX, winY, winW, winH, 10.0F, 1.0F, BORDER_PANEL);

        // 4. Scrollable Content
        Render2D.pushScissor(winX + 2.0F, clipTop, winW - 4.0F, clipHeight);

        renderCategoryBar(context, contentX, contentW, mouseX, mouseY, clipTop, clipBottom);
        renderItemParametersGroup(context, contentX, contentW, mouseX, mouseY, clipTop, clipBottom);
        renderEnchantFiltersGroup(context, contentX, contentW, mouseX, mouseY, clipTop, clipBottom);
        renderAutomationGroup(context, contentX, contentW, mouseX, mouseY, clipTop, clipBottom);
        renderBlacklistGroup(context, contentX, contentW, mouseX, mouseY, clipTop, clipBottom);

        Render2D.popScissor();

        // 5. Scrollbar
        if (maxScrollY > 1.0F) {
            float scrollTrackX = winX + winW - 6.0F;
            float scrollTrackY = clipTop + 6.0F;
            float scrollTrackH = clipHeight - 12.0F;
            float thumbH = Math.max(22.0F, scrollTrackH * (clipHeight / (clipHeight + maxScrollY)));
            float thumbY = scrollTrackY + (scrollY / maxScrollY) * (scrollTrackH - thumbH);

            Render2D.drawRound(context, scrollTrackX, scrollTrackY, 3.0F, scrollTrackH, 1.5F, 0x22171B26);
            Render2D.drawRound(context, scrollTrackX, thumbY, 3.0F, thumbH, 1.5F,
                    scrollThumbDragging ? ACCENT_PURPLE : 0xFF353C4E);
        }

        // 6. Solid Header & Footer
        renderHeader(context, winX, winY, winW, headerH, mouseX, mouseY);
        renderFooter(context, winX, footerY, winW, footerH, mouseX, mouseY);

        // 7. Category Dropdown Menu Popup
        if (dropdownAnim > 0.02F) {
            renderCategoryDropdownMenu(context, contentX, clipTop + 10.0F - scrollY, contentW, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext context, float x, float y, float w, float h, int mouseX, int mouseY) {
        Render2D.drawRound(context, x, y, w, h, 10.0F, 10.0F, 0.0F, 0.0F, SURFACE_WINDOW);

        drawTexture(context, ICON_AUTOBUY, x + 14.0F, y + 12.0F, 20.0F, 20.0F, 0xFFFFFFFF);
        ModernFont.draw(context, "Автобай", x + 40.0F, y + 11.0F, 11.5F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);
        ModernFont.draw(context, "Конфигурация авто-покупки", x + 40.0F, y + 24.5F, 8.5F, TEXT_SECONDARY, ModernFont.Type.INTER_MEDIUM);

        float toggleW = 32.0F;
        float toggleH = 16.0F;
        float toggleX = x + w - toggleW - 14.0F;
        float toggleY = y + (h - toggleH) * 0.5F;

        boolean toggleHover = inside(mouseX, mouseY, toggleX, toggleY, toggleW, toggleH);
        drawCompactToggle(context, toggleX, toggleY, toggleW, toggleH, masterToggleAnim, toggleHover);

        String toggleLabel = autoBuyEnabled ? "ВКЛ" : "ВЫКЛ";
        int labelColor = blend(TEXT_SECONDARY, 0xFFFFFFFF, masterToggleAnim);
        ModernFont.drawRight(context, toggleLabel, toggleX - 6.0F, y + 18.0F, 8.5F, labelColor, ModernFont.Type.SF_BOLD);

        Render2D.drawRound(context, x + 10.0F, y + h - 1.0F, w - 20.0F, 1.0F, 0.0F, DIVIDER_LINE);
    }

    private void renderFooter(DrawContext context, float x, float y, float w, float h, int mouseX, int mouseY) {
        Render2D.drawRound(context, x, y, w, h, 0.0F, 0.0F, 10.0F, 10.0F, SURFACE_WINDOW);
        Render2D.drawRound(context, x + 10.0F, y, w - 20.0F, 1.0F, 0.0F, DIVIDER_LINE);

        float resetX = x + 14.0F;
        float resetY = y + 14.0F;
        float resetW = ModernFont.getWidth("Сбросить", 9.5F, ModernFont.Type.INTER_MEDIUM);
        boolean resetHover = inside(mouseX, mouseY, resetX - 2.0F, y + 8.0F, resetW + 4.0F, 24.0F);
        resetHoverAnim = approach(resetHoverAnim, resetHover ? 1.0F : 0.0F, 0.2F);
        int resetColor = blend(TEXT_SECONDARY, 0xFFFFFFFF, resetHoverAnim);
        ModernFont.draw(context, "Сбросить", resetX, resetY, 9.5F, resetColor, ModernFont.Type.INTER_MEDIUM);

        float btnH = 24.0F;
        float saveW = 76.0F;
        float saveX = x + w - saveW - 14.0F;
        float saveY = y + (h - btnH) * 0.5F;

        float cancelW = 64.0F;
        float cancelX = saveX - cancelW - 8.0F;
        float cancelY = saveY;

        boolean cancelHover = inside(mouseX, mouseY, cancelX, cancelY, cancelW, btnH);
        cancelHoverAnim = approach(cancelHoverAnim, cancelHover ? 1.0F : 0.0F, 0.2F);
        int cancelBg = blend(0xFF141720, 0xFF1C202C, cancelHoverAnim);
        int cancelText = blend(TEXT_SECONDARY, TEXT_PRIMARY, cancelHoverAnim);

        Render2D.drawRound(context, cancelX, cancelY, cancelW, btnH, 5.0F, cancelBg);
        Render2D.drawRoundOutline(context, cancelX, cancelY, cancelW, btnH, 5.0F, 1.0F, BORDER_PANEL);
        ModernFont.drawCentered(context, "Отмена", cancelX + cancelW * 0.5F, cancelY + 7.5F, 9.0F, cancelText, ModernFont.Type.SF_BOLD);

        boolean saveHover = inside(mouseX, mouseY, saveX, saveY, saveW, btnH);
        saveHoverAnim = approach(saveHoverAnim, saveHover ? 1.0F : 0.0F, 0.2F);
        int saveBg = blend(ACCENT_PURPLE, ACCENT_PURPLE_HOVER, saveHoverAnim);

        Render2D.drawRound(context, saveX, saveY, saveW, btnH, 5.0F, saveBg);
        ModernFont.drawCentered(context, "Сохранить", saveX + saveW * 0.5F, saveY + 7.5F, 9.0F, 0xFFFFFFFF, ModernFont.Type.SF_BOLD);
    }

    private void renderCategoryBar(DrawContext context, float x, float w, int mouseX, int mouseY, float cTop, float cBottom) {
        if (catBarY + catBarH >= cTop && catBarY <= cBottom) {
            Render2D.drawRound(context, x, catBarY, w, catBarH, 6.0F, SURFACE_PANEL);
            Render2D.drawRoundOutline(context, x, catBarY, w, catBarH, 6.0F, 1.0F, BORDER_PANEL);

            ModernFont.draw(context, "Категория", x + 12.0F, catBarY + 12.0F, 9.5F, TEXT_SECONDARY, ModernFont.Type.INTER_MEDIUM);

            float dropW = 100.0F;
            float dropH = 22.0F;
            float dropX = x + w - dropW - 8.0F;
            float dropY = catBarY + 6.0F;

            boolean dropHover = inside(mouseX, mouseY, dropX, dropY, dropW, dropH);
            Render2D.drawRound(context, dropX, dropY, dropW, dropH, 4.0F, dropHover ? 0xFF161922 : 0xFF0B0D12);
            Render2D.drawRoundOutline(context, dropX, dropY, dropW, dropH, 4.0F, 1.0F,
                    categoryDropdownOpen ? ACCENT_PURPLE : BORDER_INPUT);

            drawTexture(context, ICON_COMBAT, dropX + 7.0F, dropY + 5.5F, 11.0F, 11.0F,
                    categoryDropdownOpen ? ACCENT_PURPLE_HOVER : TEXT_PRIMARY);
            ModernFont.draw(context, category, dropX + 22.0F, dropY + 6.5F, 9.0F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);
            ModernFont.drawRight(context, categoryDropdownOpen ? "▲" : "▼", dropX + dropW - 7.0F, dropY + 6.5F, 8.0F,
                    categoryDropdownOpen ? ACCENT_PURPLE : TEXT_SECONDARY, ModernFont.Type.SF_BOLD);
        }
    }

    private void renderCategoryDropdownMenu(DrawContext context, float x, float catY, float w, int mouseX, int mouseY) {
        float dropW = 100.0F;
        float dropX = x + w - dropW - 8.0F;
        float dropY = catY + 30.0F;

        List<String> categories = List.of("Мечи", "Броня", "Инструменты", "Элитры");
        float totalMenuH = categories.size() * 22.0F + 4.0F;
        float currentH = totalMenuH * dropdownAnim;

        if (currentH < 2.0F) return;

        Render2D.pushScissor(dropX - 1.0F, dropY - 1.0F, dropW + 2.0F, currentH + 2.0F);

        Render2D.drawRound(context, dropX, dropY, dropW, totalMenuH, 5.0F, 0xFF0E1016);
        Render2D.drawRoundOutline(context, dropX, dropY, dropW, totalMenuH, 5.0F, 1.0F, ACCENT_PURPLE);

        float itemY = dropY + 2.0F;
        for (String cat : categories) {
            boolean itemHover = inside(mouseX, mouseY, dropX + 2.0F, itemY, dropW - 4.0F, 20.0F);
            boolean selected = cat.equalsIgnoreCase(category);
            if (itemHover || selected) {
                Render2D.drawRound(context, dropX + 2.0F, itemY, dropW - 4.0F, 20.0F, 3.0F,
                        selected ? 0xFF261D3E : 0xFF171A24);
            }
            ModernFont.draw(context, cat, dropX + 8.0F, itemY + 5.5F, 8.5F,
                    selected ? 0xFFFFFFFF : (itemHover ? TEXT_PRIMARY : TEXT_SECONDARY),
                    ModernFont.Type.INTER_MEDIUM);
            itemY += 22.0F;
        }

        Render2D.popScissor();
    }

    private void renderItemParametersGroup(DrawContext context, float x, float w, int mouseX, int mouseY, float cTop, float cBottom) {
        if (itemParamsY + 12.0F >= cTop && itemParamsY <= cBottom) {
            ModernFont.draw(context, "ПАРАМЕТРЫ ПРЕДМЕТА", x + 2.0F, itemParamsY, 7.5F, TEXT_MUTED, ModernFont.Type.SF_BOLD);
        }

        float cardY = itemParamsCardY;
        if (cardY + itemParamsH >= cTop && cardY <= cBottom) {
            Render2D.drawRound(context, x, cardY, w, itemParamsH, 6.0F, SURFACE_PANEL);
            Render2D.drawRoundOutline(context, x, cardY, w, itemParamsH, 6.0F, 1.0F, BORDER_PANEL);

            float toggleX = x + w - 28.0F - 12.0F;

            // Row 1: Max Price (Height 32.0F)
            float r1Y = cardY;
            ModernFont.draw(context, "Максимальная цена", x + 12.0F, r1Y + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

            float monetW = ModernFont.getWidth("монет", 8.5F, ModernFont.Type.INTER_MEDIUM);
            float monetX = toggleX - 10.0F - monetW;
            ModernFont.draw(context, "монет", monetX, r1Y + 11.5F, 8.5F, TEXT_SECONDARY, ModernFont.Type.INTER_MEDIUM);

            float inputW = 95.0F;
            float inputH = 20.0F;
            float inputX = monetX - 8.0F - inputW;
            float inputY = r1Y + 6.0F;

            boolean isFocused = focusedField == FocusField.PRICE;
            boolean hover = inside(mouseX, mouseY, inputX, inputY, inputW, inputH);

            Render2D.drawRound(context, inputX, inputY, inputW, inputH, 4.0F, SURFACE_INPUT);
            Render2D.drawRoundOutline(context, inputX, inputY, inputW, inputH, 4.0F, 1.0F,
                    isFocused ? BORDER_FOCUS : (hover ? 0xFF2A2E3D : BORDER_INPUT));

            String displayText = formatNumber(priceInput) + (isFocused ? "_" : "");
            ModernFont.draw(context, displayText, inputX + 6.0F, inputY + 5.5F, 8.5F,
                    isFocused ? 0xFFFFFFFF : TEXT_PRIMARY, ModernFont.Type.SF_BOLD);

            float toggleY = r1Y + 8.5F;
            drawCompactToggle(context, toggleX, toggleY, 28.0F, 15.0F, priceToggleAnim,
                    inside(mouseX, mouseY, toggleX, toggleY, 28.0F, 15.0F));

            Render2D.drawRound(context, x + 8.0F, cardY + 32.0F, w - 16.0F, 1.0F, 0.0F, DIVIDER_LINE);

            // Row 2: Durability (Height 32.0F)
            float r2Y = cardY + 33.0F;
            ModernFont.draw(context, "Учитывать прочность", x + 12.0F, r2Y + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

            String percentStr = minDurabilityPercent + "%";
            float percentW = ModernFont.getWidth(percentStr, 8.5F, ModernFont.Type.SF_BOLD);
            float percentX = toggleX - 10.0F - percentW;
            ModernFont.draw(context, percentStr, percentX, r2Y + 11.5F, 8.5F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);

            float sliderW = 85.0F;
            float sliderH = 4.0F;
            float sliderX = percentX - 10.0F - sliderW;
            float sliderY = r2Y + 14.0F;

            Render2D.drawRound(context, sliderX, sliderY, sliderW, sliderH, 2.0F, 0xFF191C25);
            float fillW = sliderW * (minDurabilityPercent / 100.0F);
            if (fillW > 0.0F) {
                Render2D.drawRound(context, sliderX, sliderY, fillW, sliderH, 2.0F, ACCENT_PURPLE);
            }
            float knobR = 4.0F;
            float knobX = sliderX + fillW;
            Render2D.drawRound(context, knobX - knobR, sliderY + sliderH * 0.5F - knobR, knobR * 2.0F, knobR * 2.0F, knobR, 0xFFFFFFFF);

            float durToggleY = r2Y + 8.5F;
            drawCompactToggle(context, toggleX, durToggleY, 28.0F, 15.0F, durToggleAnim,
                    inside(mouseX, mouseY, toggleX, durToggleY, 28.0F, 15.0F));
        }
    }

    private void renderEnchantFiltersGroup(DrawContext context, float x, float w, int mouseX, int mouseY, float cTop, float cBottom) {
        if (enchantGroupY + 12.0F >= cTop && enchantGroupY <= cBottom) {
            ModernFont.draw(context, "ФИЛЬТР ЧАР", x + 2.0F, enchantGroupY, 7.5F, TEXT_MUTED, ModernFont.Type.SF_BOLD);
        }

        float cardY = enchantCardY;
        if (cardY + enchantGroupH >= cTop && cardY <= cBottom) {
            Render2D.drawRound(context, x, cardY, w, enchantGroupH, 6.0F, SURFACE_PANEL);
            Render2D.drawRoundOutline(context, x, cardY, w, enchantGroupH, 6.0F, 1.0F, BORDER_PANEL);

            float innerX = x + CARD_PADDING;
            float innerW = w - CARD_PADDING * 2.0F;

            // Needed Header
            ModernFont.draw(context, "Обязательные чары", innerX, neededHeaderY + 1.0F, 9.0F, 0xFFA78BFA, ModernFont.Type.SF_BOLD);

            // Needed Chips
            for (int i = 0; i < neededChipRects.size(); i++) {
                float[] r = neededChipRects.get(i);
                String ench = neededEnchants.get(i);
                boolean closeHover = inside(mouseX, mouseY, r[0] + r[2] - 14.0F, r[1] + 2.0F, 12.0F, 14.0F);
                Render2D.drawRound(context, r[0], r[1], r[2], r[3], 4.0F, CHIP_BG);
                Render2D.drawRoundOutline(context, r[0], r[1], r[2], r[3], 4.0F, 1.0F, CHIP_BORDER);
                ModernFont.draw(context, ench, r[0] + 6.0F, r[1] + 5.0F, 8.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
                ModernFont.draw(context, "×", r[0] + r[2] - 10.0F, r[1] + 4.0F, 9.5F,
                        closeHover ? DANGER_HOVER : TEXT_SECONDARY, ModernFont.Type.SF_BOLD);
            }

            // Needed Input Row
            float inputW = 160.0F;
            float inputH = 21.0F;
            boolean isNeededFocused = focusedField == FocusField.NEEDED;
            Render2D.drawRound(context, innerX, neededInputY, inputW, inputH, 4.0F, SURFACE_INPUT);
            Render2D.drawRoundOutline(context, innerX, neededInputY, inputW, inputH, 4.0F, 1.0F, isNeededFocused ? BORDER_FOCUS : BORDER_INPUT);

            String nPlaceholder = neededInput.isEmpty() ? "+ Добавить чары..." : neededInput + (isNeededFocused ? "_" : "");
            ModernFont.draw(context, nPlaceholder, innerX + 6.0F, neededInputY + 5.5F, 8.0F,
                    neededInput.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

            float nAddBtnX = innerX + inputW + 5.0F;
            boolean nAddHover = inside(mouseX, mouseY, nAddBtnX, neededInputY, 21.0F, inputH);
            Render2D.drawRound(context, nAddBtnX, neededInputY, 21.0F, inputH, 4.0F, nAddHover ? 0xFF242A38 : 0xFF151922);
            ModernFont.drawCentered(context, "+", nAddBtnX + 10.5F, neededInputY + 4.5F, 10.0F, 0xFFFFFFFF, ModernFont.Type.SF_BOLD);

            // Divider
            Render2D.drawRound(context, innerX, enchantDividerY, innerW, 1.0F, 0.0F, DIVIDER_LINE);

            // Ignored Header
            ModernFont.draw(context, "Игнорируемые чары", innerX, ignoredHeaderY + 1.0F, 9.0F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);

            // Ignored Chips
            for (int i = 0; i < ignoredChipRects.size(); i++) {
                float[] r = ignoredChipRects.get(i);
                String ench = ignoredEnchants.get(i);
                boolean closeHover = inside(mouseX, mouseY, r[0] + r[2] - 14.0F, r[1] + 2.0F, 12.0F, 14.0F);
                Render2D.drawRound(context, r[0], r[1], r[2], r[3], 4.0F, CHIP_BG);
                Render2D.drawRoundOutline(context, r[0], r[1], r[2], r[3], 4.0F, 1.0F, BORDER_PANEL);
                ModernFont.draw(context, ench, r[0] + 6.0F, r[1] + 5.0F, 8.5F, TEXT_SECONDARY, ModernFont.Type.INTER_MEDIUM);
                ModernFont.draw(context, "×", r[0] + r[2] - 10.0F, r[1] + 4.0F, 9.5F,
                        closeHover ? DANGER_HOVER : TEXT_MUTED, ModernFont.Type.SF_BOLD);
            }

            // Ignored Input Row
            boolean isIgnoredFocused = focusedField == FocusField.IGNORED;
            Render2D.drawRound(context, innerX, ignoredInputY, inputW, inputH, 4.0F, SURFACE_INPUT);
            Render2D.drawRoundOutline(context, innerX, ignoredInputY, inputW, inputH, 4.0F, 1.0F, isIgnoredFocused ? BORDER_FOCUS : BORDER_INPUT);

            String ignPlaceholder = ignoredInput.isEmpty() ? "+ Игнорировать чары..." : ignoredInput + (isIgnoredFocused ? "_" : "");
            ModernFont.draw(context, ignPlaceholder, innerX + 6.0F, ignoredInputY + 5.5F, 8.0F,
                    ignoredInput.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

            float ignAddBtnX = innerX + inputW + 5.0F;
            boolean ignAddHover = inside(mouseX, mouseY, ignAddBtnX, ignoredInputY, 21.0F, inputH);
            Render2D.drawRound(context, ignAddBtnX, ignoredInputY, 21.0F, inputH, 4.0F, ignAddHover ? 0xFF242A38 : 0xFF151922);
            ModernFont.drawCentered(context, "+", ignAddBtnX + 10.5F, ignoredInputY + 4.5F, 10.0F, 0xFFFFFFFF, ModernFont.Type.SF_BOLD);
        }
    }

    private void renderAutomationGroup(DrawContext context, float x, float w, int mouseX, int mouseY, float cTop, float cBottom) {
        if (automationGroupY + 12.0F >= cTop && automationGroupY <= cBottom) {
            ModernFont.draw(context, "АВТОМАТИЗАЦИЯ И СЕРВЕРЫ", x + 2.0F, automationGroupY, 7.5F, TEXT_MUTED, ModernFont.Type.SF_BOLD);
        }

        float cardY = automationCardY;
        if (cardY + automationGroupH >= cTop && cardY <= cBottom) {
            Render2D.drawRound(context, x, cardY, w, automationGroupH, 6.0F, SURFACE_PANEL);
            Render2D.drawRoundOutline(context, x, cardY, w, automationGroupH, 6.0F, 1.0F, BORDER_PANEL);

            float toggleX = x + w - 28.0F - 12.0F;

            // Row 1: Resell
            ModernFont.draw(context, "Авто-перепродажа", x + 12.0F, resellRowY + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
            drawCompactToggle(context, toggleX, resellRowY + 8.5F, 28.0F, 15.0F, resellToggleAnim,
                    inside(mouseX, mouseY, toggleX, resellRowY + 8.5F, 28.0F, 15.0F));
            Render2D.drawRound(context, x + 8.0F, resellRowY + 32.0F, w - 16.0F, 1.0F, 0.0F, DIVIDER_LINE);

            // Row 2: Balance
            ModernFont.draw(context, "Анти-слив баланса", x + 12.0F, balanceRowY + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
            drawCompactToggle(context, toggleX, balanceRowY + 8.5F, 28.0F, 15.0F, balanceToggleAnim,
                    inside(mouseX, mouseY, toggleX, balanceRowY + 8.5F, 28.0F, 15.0F));
            Render2D.drawRound(context, x + 8.0F, balanceRowY + 32.0F, w - 16.0F, 1.0F, 0.0F, DIVIDER_LINE);

            // Row 3: Anarchy Switch
            ModernFont.draw(context, "Свитч по анархиям", x + 12.0F, anarchyRowY + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
            drawCompactToggle(context, toggleX, anarchyRowY + 8.5F, 28.0F, 15.0F, anarchyToggleAnim,
                    inside(mouseX, mouseY, toggleX, anarchyRowY + 8.5F, 28.0F, 15.0F));

            // Anarchy Sub-settings
            if (anarchySubAnim > 0.02F) {
                Render2D.pushScissor(x, anarchySubY, w, anarchySubH);

                float innerX = x + CARD_PADDING;
                for (int i = 0; i < anarchyChipRects.size(); i++) {
                    float[] r = anarchyChipRects.get(i);
                    String aid = anarchyIds.get(i);
                    boolean closeHover = inside(mouseX, mouseY, r[0] + r[2] - 12.0F, r[1] + 1.0F, 10.0F, 12.0F);
                    Render2D.drawRound(context, r[0], r[1], r[2], r[3], 3.0F, CHIP_BG);
                    ModernFont.draw(context, aid, r[0] + 5.0F, r[1] + 4.0F, 8.5F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);
                    ModernFont.draw(context, "×", r[0] + r[2] - 9.0F, r[1] + 3.0F, 9.0F,
                            closeHover ? DANGER_HOVER : TEXT_SECONDARY, ModernFont.Type.SF_BOLD);
                }

                boolean isFocused = focusedField == FocusField.ANARCHY;
                Render2D.drawRound(context, innerX, anarchyInputY, 80.0F, 20.0F, 3.0F, SURFACE_INPUT);
                Render2D.drawRoundOutline(context, innerX, anarchyInputY, 80.0F, 20.0F, 3.0F, 1.0F, isFocused ? BORDER_FOCUS : BORDER_INPUT);

                String placeholder = anarchyInput.isEmpty() ? "+ Номер..." : anarchyInput + (isFocused ? "_" : "");
                ModernFont.draw(context, placeholder, innerX + 5.0F, anarchyInputY + 5.5F, 8.0F,
                        anarchyInput.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

                float addBtnX = innerX + 85.0F;
                boolean addHover = inside(mouseX, mouseY, addBtnX, anarchyInputY, 20.0F, 20.0F);
                Render2D.drawRound(context, addBtnX, anarchyInputY, 20.0F, 20.0F, 3.0F, addHover ? 0xFF242A38 : 0xFF151922);
                ModernFont.drawCentered(context, "+", addBtnX + 10.0F, anarchyInputY + 4.0F, 10.0F, 0xFFFFFFFF, ModernFont.Type.SF_BOLD);

                Render2D.popScissor();
            }

            Render2D.drawRound(context, x + 8.0F, adRowY, w - 16.0F, 1.0F, 0.0F, DIVIDER_LINE);

            // Row 4: Anarchy Ad
            ModernFont.draw(context, "Реклама на анархии", x + 12.0F, adRowY + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
            drawCompactToggle(context, toggleX, adRowY + 8.5F, 28.0F, 15.0F, adToggleAnim,
                    inside(mouseX, mouseY, toggleX, adRowY + 8.5F, 28.0F, 15.0F));

            // Ad Text Field
            if (adSubAnim > 0.02F) {
                Render2D.pushScissor(x, adRowY + 32.0F, w, adSubH);

                float innerX = x + CARD_PADDING;
                float innerW = w - CARD_PADDING * 2.0F;

                boolean isFocused = focusedField == FocusField.AD_TEXT;
                Render2D.drawRound(context, innerX, adFieldY, innerW, 20.0F, 4.0F, SURFACE_INPUT);
                Render2D.drawRoundOutline(context, innerX, adFieldY, innerW, 20.0F, 4.0F, 1.0F, isFocused ? BORDER_FOCUS : BORDER_INPUT);

                String placeholder = anarchyAdText.isEmpty() ? "Текст рекламы в чат анархии..." : anarchyAdText + (isFocused ? "_" : "");
                ModernFont.draw(context, placeholder, innerX + 6.0F, adFieldY + 5.5F, 8.5F,
                        anarchyAdText.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

                Render2D.popScissor();
            }

            Render2D.drawRound(context, x + 8.0F, rentalRowY, w - 16.0F, 1.0F, 0.0F, DIVIDER_LINE);

            // Row 5: Rental
            ModernFont.draw(context, "Аренда слотов", x + 12.0F, rentalRowY + 11.5F, 9.5F, TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);
            drawCompactToggle(context, toggleX, rentalRowY + 8.5F, 28.0F, 15.0F, rentalToggleAnim,
                    inside(mouseX, mouseY, toggleX, rentalRowY + 8.5F, 28.0F, 15.0F));
        }
    }

    private void renderBlacklistGroup(DrawContext context, float x, float w, int mouseX, int mouseY, float cTop, float cBottom) {
        if (blacklistGroupY + 12.0F >= cTop && blacklistGroupY <= cBottom) {
            ModernFont.draw(context, "ЧЕРНЫЙ СПИСОК", x + 2.0F, blacklistGroupY, 7.5F, TEXT_MUTED, ModernFont.Type.SF_BOLD);
        }

        float cardY = blacklistCardY;
        if (cardY + blacklistGroupH >= cTop && cardY <= cBottom) {
            Render2D.drawRound(context, x, cardY, w, blacklistGroupH, 6.0F, SURFACE_PANEL);
            Render2D.drawRoundOutline(context, x, cardY, w, blacklistGroupH, 6.0F, 1.0F, BORDER_PANEL);

            float innerX = x + CARD_PADDING;

            ModernFont.draw(context, "Запрещённые продавцы", innerX, bannedHeaderY + 1.0F, 9.0F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);

            for (int i = 0; i < bannedChipRects.size(); i++) {
                float[] r = bannedChipRects.get(i);
                String seller = bannedSellers.get(i);
                boolean closeHover = inside(mouseX, mouseY, r[0] + r[2] - 14.0F, r[1] + 1.0F, 14.0F, 16.0F);
                Render2D.drawRound(context, r[0], r[1], r[2], r[3], 3.0F, CHIP_BG);
                Render2D.drawRoundOutline(context, r[0], r[1], r[2], r[3], 3.0F, 1.0F, BORDER_PANEL);
                ModernFont.draw(context, seller, r[0] + 5.0F, r[1] + 4.5F, 8.5F, TEXT_PRIMARY, ModernFont.Type.SF_BOLD);
                ModernFont.draw(context, "×", r[0] + r[2] - 10.0F, r[1] + 3.5F, 9.0F,
                        closeHover ? DANGER_HOVER : TEXT_SECONDARY, ModernFont.Type.SF_BOLD);
            }

            float inputW = 130.0F;
            float inputH = 21.0F;
            boolean isFocused = focusedField == FocusField.BANNED;
            Render2D.drawRound(context, innerX, bannedInputY, inputW, inputH, 4.0F, SURFACE_INPUT);
            Render2D.drawRoundOutline(context, innerX, bannedInputY, inputW, inputH, 4.0F, 1.0F, isFocused ? BORDER_FOCUS : BORDER_INPUT);

            String placeholder = bannedInput.isEmpty() ? "+ Ник игрока..." : bannedInput + (isFocused ? "_" : "");
            ModernFont.draw(context, placeholder, innerX + 6.0F, bannedInputY + 5.5F, 8.0F,
                    bannedInput.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY, ModernFont.Type.INTER_MEDIUM);

            float addBtnX = innerX + inputW + 5.0F;
            boolean addHover = inside(mouseX, mouseY, addBtnX, bannedInputY, 21.0F, inputH);
            Render2D.drawRound(context, addBtnX, bannedInputY, 21.0F, inputH, 4.0F, addHover ? 0xFF242A38 : 0xFF151922);
            ModernFont.drawCentered(context, "+", addBtnX + 10.5F, bannedInputY + 4.5F, 10.0F, 0xFFFFFFFF, ModernFont.Type.SF_BOLD);
        }
    }

    private void drawCompactToggle(DrawContext context, float x, float y, float w, float h, float anim, boolean hover) {
        int bgOff = hover ? 0xFF242938 : TOGGLE_OFF;
        int bgOn = hover ? ACCENT_PURPLE_HOVER : ACCENT_PURPLE;
        int currentBg = blend(bgOff, bgOn, anim);

        Render2D.drawRound(context, x, y, w, h, h * 0.5F, currentBg);

        float knobR = (h - 4.0F) * 0.5F;
        float knobSize = knobR * 2.0F;
        float travel = w - knobSize - 4.0F;
        float knobX = x + 2.0F + travel * anim;
        float knobY = y + 2.0F;
        Render2D.drawRound(context, knobX, knobY, knobSize, knobSize, knobR, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        float winW = Math.min(width - 24.0F, 470.0F);
        float winH = Math.min(height - 24.0F, 510.0F);
        float winX = (width - winW) * 0.5F;
        float winY = (height - winH) * 0.5F;

        float headerH = 44.0F;
        float footerH = 42.0F;
        float footerY = winY + winH - footerH;

        float clipTop = winY + headerH;
        float clipBottom = footerY;

        // Click outside window -> close
        if (!inside((float) mouseX, (float) mouseY, winX, winY, winW, winH)) {
            close();
            return true;
        }

        // Header Master Toggle
        float toggleW = 32.0F;
        float toggleH = 16.0F;
        float toggleX = winX + winW - toggleW - 14.0F;
        float toggleY = winY + (headerH - toggleH) * 0.5F;
        if (inside((float) mouseX, (float) mouseY, toggleX, toggleY, toggleW, toggleH)) {
            autoBuyEnabled = !autoBuyEnabled;
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER != null
                    ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy() : null;
            if (autoBuy != null) {
                autoBuy.setEnabled(autoBuyEnabled);
            }
            return true;
        }

        // Footer buttons
        if (mouseY >= footerY && mouseY <= winY + winH) {
            float resetW = ModernFont.getWidth("Сбросить", 9.5F, ModernFont.Type.INTER_MEDIUM);
            if (inside((float) mouseX, (float) mouseY, winX + 14.0F - 4.0F, footerY + 8.0F, resetW + 8.0F, 26.0F)) {
                resetToDefaults();
                return true;
            }
            float saveW = 76.0F;
            float saveX = winX + winW - saveW - 14.0F;
            float saveY = footerY + (footerH - 24.0F) * 0.5F;
            if (inside((float) mouseX, (float) mouseY, saveX, saveY, saveW, 24.0F)) {
                saveAndClose();
                return true;
            }
            float cancelW = 64.0F;
            float cancelX = saveX - cancelW - 8.0F;
            if (inside((float) mouseX, (float) mouseY, cancelX, saveY, cancelW, 24.0F)) {
                close();
                return true;
            }
            return true;
        }

        // Compute current synchronized layout so click coordinates match 1:1
        float contentX = winX + 14.0F;
        float contentW = winW - 28.0F;
        computeLayout(contentX, clipTop + 10.0F - scrollY, contentW);

        // Category dropdown menu click handling
        float dropW = 100.0F;
        float dropH = 22.0F;
        float dropX = contentX + contentW - dropW - 8.0F;
        float dropSelectorY = catBarY + 6.0F;

        if (categoryDropdownOpen) {
            if (inside((float) mouseX, (float) mouseY, dropX, dropSelectorY, dropW, dropH)) {
                categoryDropdownOpen = false;
                return true;
            }

            float dropMenuY = clipTop + 10.0F - scrollY + 30.0F;
            List<String> categories = List.of("Мечи", "Броня", "Инструменты", "Элитры");
            float totalMenuH = categories.size() * 22.0F + 4.0F;
            if (inside((float) mouseX, (float) mouseY, dropX, dropMenuY, dropW, totalMenuH)) {
                float itemY = dropMenuY + 2.0F;
                for (String cat : categories) {
                    if (inside((float) mouseX, (float) mouseY, dropX, itemY, dropW, 20.0F)) {
                        category = cat;
                        categoryDropdownOpen = false;
                        return true;
                    }
                    itemY += 22.0F;
                }
            }

            categoryDropdownOpen = false;
        }

        // Scrollbar thumb click / drag start
        if (maxScrollY > 1.0F && mouseX >= winX + winW - 8.0F && mouseX <= winX + winW) {
            float scrollTrackH = clipBottom - clipTop - 12.0F;
            float thumbH = Math.max(22.0F, scrollTrackH * ((clipBottom - clipTop) / ((clipBottom - clipTop) + maxScrollY)));
            float thumbY = clipTop + 6.0F + (scrollY / maxScrollY) * (scrollTrackH - thumbH);
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                scrollThumbDragging = true;
                scrollDragStartMouseY = (float) mouseY;
                scrollDragStartScroll = scrollY;
                return true;
            }
        }

        // Scrollable content area clicks
        if (mouseY >= clipTop && mouseY <= clipBottom) {
            // 1. Category selector click
            if (inside((float) mouseX, (float) mouseY, dropX, dropSelectorY, dropW, dropH)) {
                categoryDropdownOpen = true;
                return true;
            }

            // 2. Item Parameters Group
            float cardY = itemParamsCardY;
            float rowToggleX = contentX + contentW - 28.0F - 12.0F;
            float monetW = ModernFont.getWidth("монет", 8.5F, ModernFont.Type.INTER_MEDIUM);
            float monetX = rowToggleX - 10.0F - monetW;
            float inputW = 95.0F;
            float inputX = monetX - 8.0F - inputW;

            // Price input & toggle
            if (inside((float) mouseX, (float) mouseY, inputX, cardY + 6.0F, inputW, 20.0F)) {
                focusedField = FocusField.PRICE;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, rowToggleX, cardY + 8.5F, 28.0F, 15.0F)) {
                priceFilterEnabled = !priceFilterEnabled;
                return true;
            }

            // Durability slider & toggle
            float r2Y = cardY + 33.0F;
            String percentStr = minDurabilityPercent + "%";
            float percentW = ModernFont.getWidth(percentStr, 8.5F, ModernFont.Type.SF_BOLD);
            float percentX = rowToggleX - 10.0F - percentW;
            float sliderW = 85.0F;
            float sliderX = percentX - 10.0F - sliderW;

            if (inside((float) mouseX, (float) mouseY, sliderX - 4.0F, r2Y + 6.0F, sliderW + 8.0F, 20.0F)) {
                durabilityDragging = true;
                updateDurabilityFromMouse((float) mouseX, sliderX, sliderW);
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, rowToggleX, r2Y + 8.5F, 28.0F, 15.0F)) {
                durabilityFilterEnabled = !durabilityFilterEnabled;
                return true;
            }

            // 3. Enchant Filters Group
            float innerX = contentX + CARD_PADDING;

            // Needed chips removal
            for (int i = 0; i < neededChipRects.size(); i++) {
                float[] r = neededChipRects.get(i);
                if (inside((float) mouseX, (float) mouseY, r[0] + r[2] - 14.0F, r[1], 16.0F, r[3])) {
                    neededEnchants.remove(i);
                    return true;
                }
            }

            // Needed input row
            float enchInputW = 160.0F;
            float enchInputH = 21.0F;
            if (inside((float) mouseX, (float) mouseY, innerX, neededInputY, enchInputW, enchInputH)) {
                focusedField = FocusField.NEEDED;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, innerX + enchInputW + 5.0F, neededInputY, 21.0F, enchInputH)) {
                submitNeededEnchant();
                return true;
            }

            // Ignored chips removal
            for (int i = 0; i < ignoredChipRects.size(); i++) {
                float[] r = ignoredChipRects.get(i);
                if (inside((float) mouseX, (float) mouseY, r[0] + r[2] - 14.0F, r[1], 16.0F, r[3])) {
                    ignoredEnchants.remove(i);
                    return true;
                }
            }

            // Ignored input row
            if (inside((float) mouseX, (float) mouseY, innerX, ignoredInputY, enchInputW, enchInputH)) {
                focusedField = FocusField.IGNORED;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, innerX + enchInputW + 5.0F, ignoredInputY, 21.0F, enchInputH)) {
                submitIgnoredEnchant();
                return true;
            }

            // 4. Automation Group
            if (inside((float) mouseX, (float) mouseY, contentX, resellRowY, contentW, ROW_HEIGHT)) {
                autoResellEnabled = !autoResellEnabled;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, contentX, balanceRowY, contentW, ROW_HEIGHT)) {
                lowBalanceGuardEnabled = !lowBalanceGuardEnabled;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, contentX, anarchyRowY, contentW, ROW_HEIGHT)) {
                anarchySwitchEnabled = !anarchySwitchEnabled;
                return true;
            }

            // Anarchy chips removal
            if (anarchySubAnim > 0.02F) {
                for (int i = 0; i < anarchyChipRects.size(); i++) {
                    float[] r = anarchyChipRects.get(i);
                    if (inside((float) mouseX, (float) mouseY, r[0] + r[2] - 14.0F, r[1], 16.0F, r[3])) {
                        anarchyIds.remove(i);
                        return true;
                    }
                }
                float aInnerX = contentX + CARD_PADDING;
                if (inside((float) mouseX, (float) mouseY, aInnerX, anarchyInputY, 80.0F, 20.0F)) {
                    focusedField = FocusField.ANARCHY;
                    return true;
                }
                if (inside((float) mouseX, (float) mouseY, aInnerX + 85.0F, anarchyInputY, 20.0F, 20.0F)) {
                    submitAnarchyId();
                    return true;
                }
            }

            // Ad Toggle & Field
            if (inside((float) mouseX, (float) mouseY, contentX, adRowY, contentW, ROW_HEIGHT)) {
                anarchyAdEnabled = !anarchyAdEnabled;
                return true;
            }
            if (adSubAnim > 0.02F) {
                float inX = contentX + CARD_PADDING;
                float inW = contentW - CARD_PADDING * 2.0F;
                if (inside((float) mouseX, (float) mouseY, inX, adFieldY, inW, 20.0F)) {
                    focusedField = FocusField.AD_TEXT;
                    return true;
                }
            }

            // Rental Toggle (Entire 32px row and toggle switch fully clickable)
            if (inside((float) mouseX, (float) mouseY, contentX, rentalRowY, contentW, ROW_HEIGHT)) {
                rentalSlotsEnabled = !rentalSlotsEnabled;
                return true;
            }

            // 5. Blacklist Group
            // Banned chips removal
            for (int i = 0; i < bannedChipRects.size(); i++) {
                float[] r = bannedChipRects.get(i);
                if (inside((float) mouseX, (float) mouseY, r[0] + r[2] - 14.0F, r[1] - 2.0F, 16.0F, r[3] + 4.0F)) {
                    bannedSellers.remove(i);
                    return true;
                }
            }

            // Banned input row (FULL 21px height hitbox accurately tested from top to bottom)
            float banInputW = 130.0F;
            float banInputH = 21.0F;
            if (inside((float) mouseX, (float) mouseY, innerX, bannedInputY, banInputW, banInputH)) {
                focusedField = FocusField.BANNED;
                return true;
            }
            if (inside((float) mouseX, (float) mouseY, innerX + banInputW + 5.0F, bannedInputY, 21.0F, banInputH)) {
                submitBannedSeller();
                return true;
            }
        }

        focusedField = FocusField.NONE;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        durabilityDragging = false;
        scrollThumbDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (durabilityDragging) {
            float winW = Math.min(width - 24.0F, 470.0F);
            float winX = (width - winW) * 0.5F;
            float contentX = winX + 14.0F;
            float contentW = winW - 28.0F;
            float rowToggleX = contentX + contentW - 28.0F - 12.0F;
            String percentStr = minDurabilityPercent + "%";
            float percentW = ModernFont.getWidth(percentStr, 8.5F, ModernFont.Type.SF_BOLD);
            float percentX = rowToggleX - 10.0F - percentW;
            float sliderW = 85.0F;
            float sliderX = percentX - 10.0F - sliderW;
            updateDurabilityFromMouse((float) mouseX, sliderX, sliderW);
            return true;
        }

        if (scrollThumbDragging && maxScrollY > 1.0F) {
            float winH = Math.min(height - 24.0F, 510.0F);
            float headerH = 44.0F;
            float footerH = 42.0F;
            float clipHeight = winH - headerH - footerH;
            float scrollTrackH = clipHeight - 12.0F;
            float thumbH = Math.max(22.0F, scrollTrackH * (clipHeight / (clipHeight + maxScrollY)));
            float maxTravel = scrollTrackH - thumbH;

            float delta = (float) mouseY - scrollDragStartMouseY;
            if (maxTravel > 0.0F) {
                targetScrollY = clamp(scrollDragStartScroll + (delta / maxTravel) * maxScrollY, 0.0F, maxScrollY);
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScrollY > 0.0F) {
            targetScrollY = clamp(targetScrollY - (float) verticalAmount * 26.0F, 0.0F, maxScrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (categoryDropdownOpen) {
                categoryDropdownOpen = false;
                return true;
            }
            if (focusedField != FocusField.NONE) {
                focusedField = FocusField.NONE;
                return true;
            }
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            switch (focusedField) {
                case NEEDED -> submitNeededEnchant();
                case IGNORED -> submitIgnoredEnchant();
                case ANARCHY -> submitAnarchyId();
                case BANNED -> submitBannedSeller();
                case AD_TEXT, PRICE -> focusedField = FocusField.NONE;
                default -> {}
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            switch (focusedField) {
                case PRICE -> {
                    if (!priceInput.isEmpty()) {
                        priceInput = priceInput.substring(0, priceInput.length() - 1);
                    }
                }
                case NEEDED -> {
                    if (!neededInput.isEmpty()) {
                        neededInput = neededInput.substring(0, neededInput.length() - 1);
                    }
                }
                case IGNORED -> {
                    if (!ignoredInput.isEmpty()) {
                        ignoredInput = ignoredInput.substring(0, ignoredInput.length() - 1);
                    }
                }
                case ANARCHY -> {
                    if (!anarchyInput.isEmpty()) {
                        anarchyInput = anarchyInput.substring(0, anarchyInput.length() - 1);
                    }
                }
                case AD_TEXT -> {
                    if (!anarchyAdText.isEmpty()) {
                        anarchyAdText = anarchyAdText.substring(0, anarchyAdText.length() - 1);
                    }
                }
                case BANNED -> {
                    if (!bannedInput.isEmpty()) {
                        bannedInput = bannedInput.substring(0, bannedInput.length() - 1);
                    }
                }
                default -> {}
            }
            return true;
        }

        // Clipboard paste (Ctrl+V)
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (client != null && client.keyboard != null) {
                String paste = client.keyboard.getClipboard();
                if (paste != null && !paste.isEmpty()) {
                    for (char c : paste.toCharArray()) {
                        charTyped(c, modifiers);
                    }
                }
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) {
            return false;
        }

        switch (focusedField) {
            case PRICE -> {
                if (Character.isDigit(chr) && priceInput.length() < 12) {
                    priceInput += chr;
                    return true;
                }
            }
            case NEEDED -> {
                if (neededInput.length() < 30) {
                    neededInput += chr;
                    return true;
                }
            }
            case IGNORED -> {
                if (ignoredInput.length() < 30) {
                    ignoredInput += chr;
                    return true;
                }
            }
            case ANARCHY -> {
                if (Character.isDigit(chr) && anarchyInput.length() < 6) {
                    anarchyInput += chr;
                    return true;
                }
            }
            case AD_TEXT -> {
                if (anarchyAdText.length() < 120) {
                    anarchyAdText += chr;
                    return true;
                }
            }
            case BANNED -> {
                if (bannedInput.length() < 24) {
                    bannedInput += chr;
                    return true;
                }
            }
            default -> {}
        }
        return super.charTyped(chr, modifiers);
    }

    private void updateDurabilityFromMouse(float mouseX, float sliderX, float sliderW) {
        float ratio = clamp((mouseX - sliderX) / sliderW, 0.0F, 1.0F);
        minDurabilityPercent = Math.round(ratio * 100.0F);
    }

    private void submitNeededEnchant() {
        String clean = neededInput.trim();
        if (!clean.isEmpty()) {
            neededEnchants.add(clean);
            neededInput = "";
        }
        focusedField = FocusField.NONE;
    }

    private void submitIgnoredEnchant() {
        String clean = ignoredInput.trim();
        if (!clean.isEmpty()) {
            ignoredEnchants.add(clean);
            ignoredInput = "";
        }
        focusedField = FocusField.NONE;
    }

    private void submitAnarchyId() {
        String clean = anarchyInput.replaceAll("[^0-9]", "").trim();
        if (!clean.isEmpty()) {
            anarchyIds.add(clean);
            anarchyInput = "";
        }
        focusedField = FocusField.NONE;
    }

    private void submitBannedSeller() {
        String clean = bannedInput.trim();
        if (!clean.isEmpty()) {
            bannedSellers.add(clean);
            bannedInput = "";
        }
        focusedField = FocusField.NONE;
    }

    private static String formatNumber(String num) {
        if (num == null || num.isEmpty()) return "0";
        try {
            long val = Long.parseLong(num.replaceAll("[^0-9]", ""));
            return String.format("%,d", val).replace(',', ' ');
        } catch (Exception e) {
            return num;
        }
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float approach(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private static int blend(int c1, int c2, float t) {
        t = clamp(t, 0.0F, 1.0F);
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}