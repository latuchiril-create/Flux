package dev.fuga.fluxvisuals.gui.modern;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.modern.font.ModernFont;
import dev.fuga.fluxvisuals.gui.modern.setting.*;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import dev.fuga.fluxvisuals.modules.visual.Menu;
import dev.fuga.fluxvisuals.multibot.BotSession;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import dev.fuga.fluxvisuals.render.util.ScissorStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.*;

public final class ModernGui2Renderer {
    public static ModernGui2Renderer INSTANCE;

    // Design tokens matching deep dark obsidian glass
    private static final int UI_BG = 0xF2030406;            // Deep pitch-black background (~94.5% opaque, tuned 3% more transparent)
    private static final int UI_BORDER = 0x18FFFFFF;        // Subtle clean 1px border
    private static final int UI_SIDEBAR_DIVIDER = 0x12FFFFFF;
    private static final int UI_CARD_BG = 0x9007090D;       // Ultra dark sleek card surface
    private static final int UI_CARD_BORDER = 0x14FFFFFF;   // Subtle card outline
    private static final int UI_POPOVER_BG = 0xF8040507;    // Pitch-dark obsidian popup surface
    private static final int UI_ROW_HOVER = 0x0EFFFFFF;
    private static final int UI_TEXT_MAIN = 0xFFD8DCE6;     // Clean light gray text
    private static final int UI_TEXT_SEC = 0xFF7D8390;      // Secondary gray text
    private static final int UI_TEXT_MUTED = 0xFF4E5362;    // Muted text
    private static final int UI_SWITCH_OFF_TRACK = 0xFF14161E; // Muted dark track
    private static final int UI_SWITCH_OFF_THUMB = 0xFF353945; // Dark thumb
    private static final int UI_SWITCH_ON_TRACK = 0xFF35445E;  // Muted slate blue active track
    private static final int UI_SWITCH_ON_THUMB = 0xFFFFFFFF;  // Bright thumb

    private static final Identifier ICON_COMBAT = Identifier.of("fluxvisuals", "icons/combat.png");
    private static final Identifier ICON_MOVEMENT = Identifier.of("fluxvisuals", "icons/movement.png");
    private static final Identifier ICON_VISUALS = Identifier.of("fluxvisuals", "icons/visuals.png");
    private static final Identifier ICON_PLAYER = Identifier.of("fluxvisuals", "icons/player.png");
    private static final Identifier ICON_MISC = Identifier.of("fluxvisuals", "icons/wrench.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("fluxvisuals", "icons/settings.png");
    private static final Identifier ICON_AUTOBUY = Identifier.of("fluxvisuals", "icons/autobuy.png");
    private static final Identifier ICON_KEYBOARD = Identifier.of("fluxvisuals", "icons/keyboard.png");
    private static final Identifier ICON_SEARCH = Identifier.of("fluxvisuals", "icons/search.png");
    private static final Identifier ICON_PIPETTE = Identifier.of("fluxvisuals", "icons/pipette.png");
    private static final Identifier ICON_COPY = Identifier.of("fluxvisuals", "icons/copy.png");
    private static final Identifier ICON_CHECK = Identifier.of("fluxvisuals", "icons/check.png");
    private static final Identifier ICON_DOTS = Identifier.of("fluxvisuals", "icons/dots.png");

    public enum Tab {
        COMBAT("Бой", "Combat", ICON_COMBAT, false),
        MOVEMENT("Движение", "Movement", ICON_MOVEMENT, false),
        VISUALS("Визуалы", "Visuals", ICON_VISUALS, false),
        PLAYER("Игрок", "Player", ICON_PLAYER, false),
        MISC("Разное", "Misc", ICON_MISC, false),
        PRESETS("Пресеты", "Presets", ICON_SETTINGS, true),
        AUTOBUY("Авто покупка", "Auto Buy", ICON_AUTOBUY, true),
        ACCOUNTS("Аккаунты", "Accounts", ICON_PLAYER, true);

        public final String ruName;
        public final String enName;
        public final Identifier icon;
        public final boolean isManagement;

        Tab(String ruName, String enName, Identifier icon, boolean isManagement) {
            this.ruName = ruName;
            this.enName = enName;
            this.icon = icon;
            this.isManagement = isManagement;
        }

        public String title(boolean en) { return en ? enName : ruName; }
        public String label(boolean en) { return en ? enName : ruName; }
    }

    private final ModuleManager moduleManager;
    private final Map<Module, List<Setting<?>>> moduleSettings = new HashMap<>();
    private final Map<String, Float> toggleAnims = new HashMap<>();
    private final Map<Setting<?>, Float> settingToggleAnims = new HashMap<>();
    private final Map<String, Boolean> collapsedSections = new HashMap<>();
    private final Map<String, Float> sectionExpandAnims = new HashMap<>();
    private final Map<String, Boolean> dummyToggleStates = new HashMap<>();

    private static Tab savedSelectedTab = Tab.COMBAT;
    private static String savedSearch = "";
    private static float savedScrollY = 0.0F;
    private static float savedTargetScrollY = 0.0F;

    private Tab selectedTab = savedSelectedTab;
    private Tab previousTab = null;
    private float tabTransition = 1.0F; // 0 to 1 smooth transition
    private String search = savedSearch;
    private boolean searchFocused = false;

    // Scrolling
    private float scrollY = savedScrollY;
    private float targetScrollY = savedTargetScrollY;
    private float maxScrollY = 0.0F;

    // Module settings context menu
    private Module contextMenuModule = null;
    private float contextMenuX = 0.0F;
    private float contextMenuY = 0.0F;
    private float contextMenuScrollY = 0.0F;
    private float maxContextMenuScrollY = 0.0F;
    private SliderSetting draggingSlider = null;
    private StringSetting activeStringSetting = null;
    private String activeStringDraft = "";
    private KeybindSetting listeningSettingBind = null;

    // Keybind Popover & Fullscreen Listen Modal
    private Module activeBindModule = null;
    private float bindPopoverX = 0.0F;
    private float bindPopoverY = 0.0F;
    private boolean bindHoldMode = false; // false = Toggle, true = Hold
    private boolean listeningFullscreenKey = false;
    private float bindModeSliderAnim = 0.0F;

    // Floating Dropdowns (renders on top of everything)
    private ModeSetting openDropdownMode = null;
    private MultiModeSetting openDropdownMulti = null;
    private String openGlobalDropdown = null; // "lang", "scale", "preset"
    private float dropdownX = 0.0F;
    private float dropdownY = 0.0F;
    private float dropdownW = 0.0F;
    private float dropdownTriggerX = 0.0F;
    private float dropdownTriggerY = 0.0F;
    private float dropdownTriggerW = 0.0F;
    private float dropdownTriggerH = 0.0F;

    // Color picker
    private ColorSetting activeColorPicker = null;
    private ColorSetting customThemeColorSetting = null;
    private float pickerHue = 0.0F;
    private float pickerSat = 1.0F;
    private float pickerBri = 1.0F;
    private float pickerAlpha = 1.0F;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;
    private String hexBuffer = "";
    private boolean hexFocused = false;
    private long copyFeedbackTime = 0L;

    // Global settings menu (Hamburger)
    private boolean globalSettingsOpen = false;
    private boolean listeningMenuKey = false;

    // Tooltip: shown ONLY when hovering three dots '•••'
    private Module hoveredModule = null;
    private Module currentFrameDotsHover = null;
    private long hoverStartTime = 0L;

    // Open/Close Animation with 3D Depth
    private float openProgress = 0.0F;
    private boolean closing = false;
    private float closeProgress = 0.0F;
    private boolean closed = false;
    private float dropdownAnim = 0.0F;
    private long lastFrameTime = System.currentTimeMillis();

    public ModernGui2Renderer(ModuleManager moduleManager) {
        INSTANCE = this;
        this.moduleManager = moduleManager;
        initSettings();
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isClosed() {
        return closed;
    }

    public void startClosing() {
        if (!closing) {
            closing = true;
            closeProgress = 0.0F;
            closed = false;
        }
    }

    private static int color(int rgb, float alpha) {
        int a = Math.round(((rgb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int interpolateColor(int c1, int c2, float ratio) {
        float r = Math.max(0.0F, Math.min(1.0F, ratio));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * r);
        int red = Math.round(r1 + (r2 - r1) * r);
        int g = Math.round(g1 + (g2 - g1) * r);
        int b = Math.round(b1 + (b2 - b1) * r);
        return (a << 24) | (red << 16) | (g << 8) | b;
    }

    private void initSettings() {
        if (moduleManager == null) return;
        moduleSettings.clear();
        for (Module mod : moduleManager.getModules()) {
            List<Setting<?>> list = new ArrayList<>();
            ModernClickGuiRenderer.populateModuleSettings(moduleManager, mod, list);
            moduleSettings.put(mod, list);
        }
    }

    public void open() {
        INSTANCE = this;
        openProgress = 0.0F;
        closing = false;
        closeProgress = 0.0F;
        closed = false;
        dropdownAnim = 0.0F;
        lastFrameTime = System.currentTimeMillis();
        contextMenuModule = null;
        activeColorPicker = null;
        activeBindModule = null;
        listeningFullscreenKey = false;
        bindModeSliderAnim = 0.0F;
        openDropdownMode = null;
        openDropdownMulti = null;
        openGlobalDropdown = null;
        dropdownTriggerW = 0.0F;
        draggingSlider = null;
        activeStringSetting = null;
        globalSettingsOpen = false;
        listeningMenuKey = false;

        this.selectedTab = savedSelectedTab;
        this.search = savedSearch;
        this.scrollY = savedScrollY;
        this.targetScrollY = savedTargetScrollY;
        this.tabTransition = 1.0F;
    }

    private boolean isEn() {
        return moduleManager != null && moduleManager.getMenu() != null && moduleManager.getMenu().isEnglish();
    }

    private float getGuiScale() {
        return moduleManager != null && moduleManager.getMenu() != null ? moduleManager.getMenu().getScaleMultiplier() : 1.0F;
    }

    private int getAccentColor() {
        int col = ModernClickGuiRenderer.getAccentColor();
        if (col == 0xFF416474 || col == 0xFF00E5FF || col == 0xFF5865F2) {
            return 0xFF5C7CFA; // Clean periwinkle blue
        }
        return col;
    }

    private ColorSetting getCustomThemeColorSetting() {
        if (customThemeColorSetting == null && moduleManager != null && moduleManager.getMenu() != null) {
            Menu menu = moduleManager.getMenu();
            customThemeColorSetting = new ColorSetting("Menu:custom_color", "Цвет темы", menu::getColor1, col -> {
                menu.setColor1(col);
                ColorSetting.syncAllToTheme();
            });
        }
        return customThemeColorSetting;
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameTime) / 1000.0F, 0.1F);
        lastFrameTime = now;

        float alpha;
        float slideY;
        float scaleAnimX;
        float scaleAnimY;
        float scale = getGuiScale();

        if (closing) {
            closeProgress = Math.min(1.0F, closeProgress + dt * 3.5F);
            float ease = (float) (1.0 - Math.pow(1.0 - closeProgress, 3.0));
            alpha = Math.max(0.0F, 1.0F - ease);
            slideY = ease * 35.0F * scale; // Smooth subtle downward glide
            scaleAnimX = 1.0F - 0.08F * ease; // Smooth scale from 1.0 down to 0.92
            scaleAnimY = 1.0F - 0.08F * ease;
            if (closeProgress >= 1.0F) {
                closed = true;
                return;
            }
        } else {
            // Smooth 3D bottom-to-top rising animation ("снизу вверх")
            openProgress = Math.min(1.0F, openProgress + dt * 3.2F);
            if (openProgress <= 0.01F) return;
            float ease = (float) (1.0 - Math.pow(1.0 - openProgress, 3.5));
            alpha = ease;
            slideY = (1.0F - ease) * 50.0F * scale; // Glides smoothly upwards into place
            scaleAnimX = 0.92F + 0.08F * ease;
            scaleAnimY = 0.92F + 0.08F * ease;
        }

        // Tab switching transition
        if (tabTransition < 1.0F) {
            tabTransition = Math.min(1.0F, tabTransition + dt * 4.5F);
        }

        // Floating dropdown animation state
        boolean hasDropdown = (openDropdownMode != null || openDropdownMulti != null || openGlobalDropdown != null);
        if (hasDropdown) {
            dropdownAnim = Math.min(1.0F, dropdownAnim + dt * 14.0F);
        } else {
            dropdownAnim = Math.max(0.0F, dropdownAnim - dt * 14.0F);
        }

        // Background blur and scrim on the world only (translucent GUI is crisp on top)
        BlurRenderer.drawBlur(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, alpha * 0.85F);
        Render2D.drawRound(context, 0.0F, 0.0F, screenWidth, screenHeight, 0.0F, color(0x50000000, alpha));

        float w = 780.0F * scale;
        float h = 480.0F * scale;
        float x = (screenWidth - w) * 0.5F;
        float y = (screenHeight - h) * 0.5F;

        // Smooth scrolling
        scrollY += (targetScrollY - scrollY) * Math.min(1.0F, dt * 14.0F);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        float centerX = x + w * 0.5F;
        float centerY = y + h * 0.5F;
        matrices.translate(centerX, centerY + slideY);
        matrices.scale(scaleAnimX, scaleAnimY);
        matrices.translate(-centerX, -centerY);

        // Main Window Frame (Liquid Glass with subtle refraction, dark obsidian tone and soft shadow)
        Render2D.drawShadow(context, x, y, w, h, 30.0F * scale, 14.0F * scale, color(0x95000000, alpha));

        // Real liquid glass backdrop with physical world refraction
        Color glassTint = new Color(4, 5, 8, 212);
        BlurRenderer.drawLiquidGlass(
                context,
                x, y, w, h, 12.0F * scale,
                glassTint, alpha,
                1.4F, 1.2F, 0.0F,
                1.0F, 0.8F, true
        );
        // Subtle deep dark obsidian depth layer
        Render2D.drawRound(context, x, y, w, h, 12.0F * scale, color(0x50030406, alpha));

        float sidebarW = 188.0F * scale;

        // Subtle vertical divider between sidebar and main content
        Render2D.drawLine(context, x + sidebarW, y + 16.0F * scale, x + sidebarW, y + h - 16.0F * scale, 1.0F, color(UI_SIDEBAR_DIVIDER, alpha));

        // Reset hover detection for this frame
        currentFrameDotsHover = null;

        // Check if mouse is hovering over any popup/overlay layer
        boolean overOverlay = isMouseOverAnyOverlay((float) mouseX, (float) mouseY, scale, screenWidth, screenHeight);
        int bgMouseX = overOverlay ? -9999 : mouseX;
        int bgMouseY = overOverlay ? -9999 : mouseY;

        // Render Sidebar (Unified with panel, no isolated dark box)
        drawSidebar(context, x, y, sidebarW, h, scale, bgMouseX, bgMouseY, alpha, dt);

        // Render Top Bar (Search + Hamburger)
        float contentX = x + sidebarW + 16.0F * scale;
        float contentW = w - sidebarW - 32.0F * scale;
        float topBarY = y + 16.0F * scale;
        float topBarH = 32.0F * scale;
        drawTopBar(context, contentX, topBarY, contentW, topBarH, scale, bgMouseX, bgMouseY, alpha);

        // Render Main Content (Module Columns or Management tabs) with GPU ScissorStack
        float listY = topBarY + topBarH + 12.0F * scale;
        float listH = h - (listY - y) - 16.0F * scale;

        ScissorStack.push(contentX, listY, contentW, listH);
        try {
            float tabSlide = (1.0F - tabTransition) * 10.0F * scale;
            float tabAlpha = alpha * tabTransition;

            matrices.pushMatrix();
            matrices.translate(0.0F, tabSlide);

            if (selectedTab.isManagement) {
                drawManagementTab(context, contentX, listY + scrollY, contentW, listH, scale, bgMouseX, bgMouseY, tabAlpha, dt);
            } else {
                drawModuleColumns(context, contentX, listY + scrollY, contentW, listH, scale, bgMouseX, bgMouseY, tabAlpha, dt);
            }

            matrices.popMatrix();
        } finally {
            ScissorStack.pop();
        }

        // Update tooltip hover state strictly based on dots hover
        if (!overOverlay && currentFrameDotsHover != null) {
            if (hoveredModule != currentFrameDotsHover) {
                hoveredModule = currentFrameDotsHover;
                hoverStartTime = System.currentTimeMillis();
            }
        } else {
            hoveredModule = null;
        }

        // Hover Tooltip (Suppressed if mouse is over any overlay)
        if (!overOverlay) {
            drawHoverTooltip(context, scale, mouseX, mouseY, alpha);
        }

        // Check if mouse is over dropdown or color picker for lower popup layers
        boolean overTopPopups = isMouseOverDropdownOrPicker((float) mouseX, (float) mouseY, scale);
        int popupMouseX = overTopPopups ? -9999 : mouseX;
        int popupMouseY = overTopPopups ? -9999 : mouseY;

        // Popups (Context Menu & Global Settings)
        if (contextMenuModule != null) {
            drawContextMenu(context, scale, popupMouseX, popupMouseY, alpha, dt);
        }
        if (globalSettingsOpen) {
            drawGlobalSettings(context, contentX + contentW - 250.0F * scale, topBarY + topBarH + 8.0F * scale, 250.0F * scale, scale, popupMouseX, popupMouseY, alpha);
        }

        // Keybind Popover Modal
        if (activeBindModule != null && !listeningFullscreenKey) {
            drawKeybindPopover(context, scale, popupMouseX, popupMouseY, alpha, dt);
        }

        // Floating Dropdowns (Always floats on top of context menu and cards)
        drawFloatingDropdown(context, scale, mouseX, mouseY, alpha);

        // Color Picker (Always on top of all popups)
        if (activeColorPicker != null) {
            drawColorPicker(context, scale, mouseX, mouseY, alpha);
        }

        matrices.popMatrix();

        // Fullscreen Key Listen Modal (Dimmable overlay on entire screen)
        if (listeningFullscreenKey && activeBindModule != null) {
            drawFullscreenKeyListenModal(context, screenWidth, screenHeight, scale, mouseX, mouseY, alpha);
        }
    }

    private void drawSidebar(DrawContext context, float x, float y, float w, float h, float scale, int mouseX, int mouseY, float alpha, float dt) {
        boolean en = isEn();

        // Brand Logo: "FLUX"
        float logoX = x + 24.0F * scale;
        float logoY = y + 20.0F * scale;
        ModernFont.draw(context, "FLUX", logoX, logoY, 21.0F * scale, color(getAccentColor(), alpha), ModernFont.Type.SF_BOLD);
        float fluxW = ModernFont.getWidth("FLUX", 21.0F * scale, ModernFont.Type.SF_BOLD);
        Render2D.drawCircle(context, logoX + fluxW + 5.0F * scale, logoY + 13.5F * scale, 2.8F * scale, color(getAccentColor(), alpha));
        ModernFont.draw(context, "v1.0", logoX + fluxW + 11.0F * scale, logoY + 9.5F * scale, 8.5F * scale, color(UI_TEXT_MUTED, alpha), ModernFont.Type.INTER_MEDIUM);

        // Category sections
        float catY = y + 74.0F * scale;
        ModernFont.draw(context, en ? "FUNCTIONS" : "ФУНКЦИИ", x + 24.0F * scale, catY, 9.0F * scale, color(UI_TEXT_MUTED, alpha), ModernFont.Type.INTER_SEMIBOLD);
        catY += 14.0F * scale;

        float itemH = 32.0F * scale;
        float itemW = w - 32.0F * scale;
        float itemX = x + 16.0F * scale;

        for (Tab tab : Tab.values()) {
            if (tab == Tab.PRESETS) {
                catY += 10.0F * scale;
                ModernFont.draw(context, en ? "MANAGEMENT" : "УПРАВЛЕНИЕ", x + 24.0F * scale, catY, 9.0F * scale, color(UI_TEXT_MUTED, alpha), ModernFont.Type.INTER_SEMIBOLD);
                catY += 14.0F * scale;
            }

            boolean active = (selectedTab == tab);
            boolean hover = inside(mouseX, mouseY, itemX, catY, itemW, itemH);

            if (active) {
                Render2D.drawRound(context, itemX, catY, itemW, itemH, 6.0F * scale, color(0x184C6EF5, alpha));
                Render2D.drawRoundOutline(context, itemX, catY, itemW, itemH, 6.0F * scale, 1.0F, color(0x304C6EF5, alpha));
            } else if (hover) {
                Render2D.drawRound(context, itemX, catY, itemW, itemH, 6.0F * scale, color(0x0CFFFFFF, alpha));
            }

            int itemColor = active ? color(0xFF6586F8, alpha) : color(hover ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);
            ModernClickGuiRenderer.drawTexture(context, tab.icon, itemX + 10.0F * scale, catY + 8.5F * scale, 15.0F * scale, 15.0F * scale, itemColor);
            ModernFont.draw(context, tab.label(en), itemX + 34.0F * scale, catY + 9.5F * scale, 11.5F * scale, itemColor, ModernFont.Type.INTER_MEDIUM);

            catY += itemH + 3.0F * scale;
        }

        // Bottom profile
        float profY = y + h - 44.0F * scale;
        String username = MinecraftClient.getInstance().getSession() != null ? MinecraftClient.getInstance().getSession().getUsername() : "soezproject";
        ModernFont.draw(context, username, x + 24.0F * scale, profY, 11.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_SEMIBOLD);
        ModernFont.draw(context, en ? "Until Jan 11, 3928" : "До 11 января 3928", x + 24.0F * scale, profY + 14.0F * scale, 8.5F * scale, color(UI_TEXT_MUTED, alpha), ModernFont.Type.INTER_MEDIUM);
    }

    private void drawTopBar(DrawContext context, float x, float y, float w, float h, float scale, int mouseX, int mouseY, float alpha) {
        float btnSize = h;
        float searchW = w - btnSize - 8.0F * scale;

        // Search Bar container
        Render2D.drawRound(context, x, y, searchW, h, 6.0F * scale, color(0x35000000, alpha));
        Render2D.drawRoundOutline(context, x, y, searchW, h, 6.0F * scale, 1.0F,
                color(searchFocused ? getAccentColor() : 0x14FFFFFF, alpha));

        ModernClickGuiRenderer.drawTexture(context, ICON_SEARCH, x + 10.0F * scale, y + 9.5F * scale, 13.0F * scale, 13.0F * scale, color(UI_TEXT_MUTED, alpha));

        String query = search.isEmpty()
                ? (searchFocused ? (System.currentTimeMillis() % 1000L < 500L ? "|" : "") : (isEn() ? "Search..." : "Поиск"))
                : (search + (searchFocused && System.currentTimeMillis() % 1000L < 500L ? "|" : ""));

        ModernFont.draw(context, query, x + 30.0F * scale, y + 9.5F * scale, 11.0F * scale,
                color(search.isEmpty() ? UI_TEXT_MUTED : UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

        if (!search.isEmpty()) {
            float clrSize = 14.0F * scale;
            float clrX = x + searchW - clrSize - 8.0F * scale;
            float clrY = y + (h - clrSize) * 0.5F;
            Render2D.drawRound(context, clrX, clrY, clrSize, clrSize, 3.0F * scale, color(0xFF222836, alpha));
            ModernFont.draw(context, "×", clrX + 4.0F * scale, clrY + 1.0F * scale, 11.0F * scale, color(UI_TEXT_SEC, alpha), ModernFont.Type.INTER_SEMIBOLD);
        }

        // Hamburger button
        float btnX = x + searchW + 8.0F * scale;
        boolean btnHover = inside(mouseX, mouseY, btnX, y, btnSize, btnSize);
        Render2D.drawRound(context, btnX, y, btnSize, btnSize, 6.0F * scale, color(btnHover || globalSettingsOpen ? 0xFF181C26 : 0x35000000, alpha));
        Render2D.drawRoundOutline(context, btnX, y, btnSize, btnSize, 6.0F * scale, 1.0F,
                color(globalSettingsOpen ? getAccentColor() : 0x14FFFFFF, alpha));

        // 3 horizontal bars
        float lineW = 14.0F * scale;
        float lineH = 1.5F * scale;
        float lineX = btnX + (btnSize - lineW) * 0.5F;
        float lineY1 = y + 10.0F * scale;
        float lineY2 = y + 15.0F * scale;
        float lineY3 = y + 20.0F * scale;
        int barCol = color(globalSettingsOpen ? getAccentColor() : (btnHover ? UI_TEXT_MAIN : UI_TEXT_SEC), alpha);

        Render2D.drawRound(context, lineX, lineY1, lineW, lineH, 0.75F * scale, barCol);
        Render2D.drawRound(context, lineX, lineY2, lineW, lineH, 0.75F * scale, barCol);
        Render2D.drawRound(context, lineX, lineY3, lineW, lineH, 0.75F * scale, barCol);
    }

    private static final class SectionDef {
        final String titleRu;
        final String titleEn;
        final List<String> moduleNames;

        SectionDef(String titleRu, String titleEn, List<String> moduleNames) {
            this.titleRu = titleRu;
            this.titleEn = titleEn;
            this.moduleNames = moduleNames;
        }

        String title(boolean en) { return en ? titleEn : titleRu; }
    }

    private List<SectionDef> getSectionsForTab(Tab tab, boolean column1) {
        List<SectionDef> res = new ArrayList<>();
        if (tab == Tab.COMBAT) {
            if (column1) {
                res.add(new SectionDef("Драка", "Combat", List.of("AimBot", "TriggerBot", "AutoMace", "NoJumpDelay")));
                res.add(new SectionDef("Базовые", "Basic", List.of("AutoSwap")));
            } else {
                res.add(new SectionDef("Инструменты", "Tools", List.of("TapeMouse", "HitboxCustomizer", "HitColor")));
                res.add(new SectionDef("Остальное", "Other", List.of("TrapTracker")));
            }
        } else if (tab == Tab.MOVEMENT) {
            if (column1) {
                res.add(new SectionDef("Передвижение", "Locomotion", List.of("AutoSprint", "ElytraSwap")));
            } else {
                res.add(new SectionDef("Камера и обзор", "View", List.of("FreeLook", "Zoom")));
            }
        } else if (tab == Tab.VISUALS) {
            if (column1) {
                res.add(new SectionDef("ESP и HUD", "ESP & HUD", List.of("TargetEsp", "TargetHud", "TEST", "ItemRadius", "Crosshair")));
                res.add(new SectionDef("Косметика", "Cosmetics", List.of("ChinaHat", "Wings", "FullBright", "AspectRatio")));
            } else {
                res.add(new SectionDef("Эффекты", "Effects", List.of("Particles", "JumpCircles", "Trails", "Animations")));
                res.add(new SectionDef("Мир", "World", List.of("WorldCustomizer", "BlockOverlay", "Removals")));
            }
        } else if (tab == Tab.PLAYER) {
            if (column1) {
                res.add(new SectionDef("Скрытность", "Stealth", List.of("SafeNametag", "NameProtect", "TabCustomizer")));
                res.add(new SectionDef("Утилиты игрока", "Player Utilities", List.of("FakePlayer")));
            } else {
                res.add(new SectionDef("Инвентарь", "Inventory", List.of("ItemResorter", "ItemCrafter", "ItemScroller")));
            }
        } else if (tab == Tab.MISC) {
            if (column1) {
                res.add(new SectionDef("Торговля и аукцион", "Trading & AH", List.of("AutoBuy", "AHHelper", "AutoResell", "AutoResellAFK")));
                res.add(new SectionDef("Автоматизация", "Automation", List.of("CaptchaSolver", "AnarchySwitcher")));
            } else {
                res.add(new SectionDef("Интеграции", "Integrations", List.of("Telegram", "DiscordRPC", "Watermark")));
                res.add(new SectionDef("Бинды и макросы", "Binds & Macros", List.of("Macros", "NameBind")));
            }
        }
        return res;
    }

    private List<SectionDef> getAllSections(boolean column1) {
        List<SectionDef> all = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            if (!tab.isManagement) {
                all.addAll(getSectionsForTab(tab, column1));
            }
        }
        return all;
    }

    public static void drawChevronDown(DrawContext context, float cx, float cy, float scale, int color, boolean open) {
        float arm = 2.8F * scale;
        float h = 1.8F * scale;
        if (open) {
            Render2D.drawLine(context, cx - arm, cy + h * 0.5F, cx, cy - h * 0.5F, 1.3F, color);
            Render2D.drawLine(context, cx, cy - h * 0.5F, cx + arm, cy + h * 0.5F, 1.3F, color);
        } else {
            Render2D.drawLine(context, cx - arm, cy - h * 0.5F, cx, cy + h * 0.5F, 1.3F, color);
            Render2D.drawLine(context, cx, cy + h * 0.5F, cx + arm, cy - h * 0.5F, 1.3F, color);
        }
    }

    public static String formatKeyName(int key) {
        if (key == 0 || key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "R-Shift";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "L-Shift";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "R-Ctrl";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL) return "L-Ctrl";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "R-Alt";
        if (key == GLFW.GLFW_KEY_LEFT_ALT) return "L-Alt";
        if (key == GLFW.GLFW_KEY_SPACE) return "Space";
        if (key == GLFW.GLFW_KEY_TAB) return "Tab";
        if (key == GLFW.GLFW_KEY_CAPS_LOCK) return "Caps";
        if (key == GLFW.GLFW_KEY_ENTER) return "Enter";
        if (key == GLFW.GLFW_KEY_BACKSPACE) return "Backspace";
        if (key == GLFW.GLFW_KEY_DELETE) return "Delete";
        if (key == GLFW.GLFW_KEY_UP) return "Up";
        if (key == GLFW.GLFW_KEY_DOWN) return "Down";
        if (key == GLFW.GLFW_KEY_LEFT) return "Left";
        if (key == GLFW.GLFW_KEY_RIGHT) return "Right";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.trim().isEmpty()) {
            return name.toUpperCase(Locale.ROOT);
        }
        return "KEY_" + key;
    }

    private Module findModuleByName(String name) {
        if (moduleManager == null) return null;
        for (Module m : moduleManager.getModules()) {
            if (m.getName().equalsIgnoreCase(name) || m.getName().replace(" ", "").equalsIgnoreCase(name)) {
                return m;
            }
        }
        if (name.equalsIgnoreCase("AutoSwap") || name.equalsIgnoreCase("ItemSwap")) {
            return moduleManager.getAutoSwap();
        }
        return null;
    }

    private String getModuleDisplayName(String rawName) {
        return switch (rawName) {
            case "AimBot" -> "Attack Aura";
            case "TriggerBot" -> "Trigger Bot";
            case "AutoSwap", "ItemSwap" -> "Item Swap";
            case "AutoMace" -> "Auto Mace";
            case "NoJumpDelay" -> "No Jump Delay";
            case "TapeMouse" -> "Tape Mouse";
            case "HitboxCustomizer" -> "Hitbox Customizer";
            case "HitColor" -> "Hit Color";
            case "TrapTracker" -> "Trap Tracker";
            case "AutoSprint" -> "Auto Sprint";
            case "ElytraSwap" -> "Elytra Swap";
            case "FreeLook" -> "Free Look";
            case "Zoom" -> "Zoom";
            case "TargetEsp" -> "Target ESP";
            case "TargetHud" -> "Target HUD";
            case "ItemRadius" -> "Item Radius";
            case "Crosshair" -> "Crosshair";
            case "ChinaHat" -> "China Hat";
            case "Wings" -> "Wings";
            case "FullBright" -> "Full Bright";
            case "AspectRatio" -> "Aspect Ratio";
            case "Particles" -> "Particles";
            case "JumpCircles" -> "Jump Circles";
            case "Trails" -> "Trails";
            case "Animations" -> "Animations";
            case "WorldCustomizer" -> "World Customizer";
            case "BlockOverlay" -> "Block Overlay";
            case "Removals" -> "Removals";
            case "SafeNametag" -> "Safe Nametag";
            case "NameProtect" -> "Name Protect";
            case "TabCustomizer" -> "Tab Customizer";
            case "FakePlayer" -> "Fake Player";
            case "ItemResorter" -> "Item Resorter";
            case "ItemCrafter" -> "Item Crafter";
            case "ItemScroller" -> "Item Scroller";
            case "AutoBuy" -> "Auto Buy";
            case "AHHelper" -> "AH Helper";
            case "AutoResell" -> "Auto Resell";
            case "AutoResellAFK" -> "Auto Resell AFK";
            case "CaptchaSolver" -> "Captcha Solver";
            case "AnarchySwitcher" -> "Anarchy Switcher";
            case "Telegram" -> "Telegram";
            case "DiscordRPC" -> "Discord RPC";
            case "Watermark" -> "Watermark";
            case "Macros" -> "Macros";
            case "NameBind" -> "Name Bind";
            default -> rawName;
        };
    }

    private void drawModuleColumns(DrawContext context, float x, float y, float w, float h, float scale, int mouseX, int mouseY, float alpha, float dt) {
        boolean en = isEn();
        float colGap = 12.0F * scale;
        float colW = (w - colGap) * 0.5F;
        float col1X = x;
        float col2X = x + colW + colGap;

        if (!search.isEmpty()) {
            List<SectionDef> allCol1 = getAllSections(true);
            List<SectionDef> allCol2 = getAllSections(false);

            int matchCount = 0;
            for (SectionDef sec : allCol1) {
                for (String raw : sec.moduleNames) {
                    if (findModuleByName(raw) != null && getModuleDisplayName(raw).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                        matchCount++;
                    }
                }
            }
            for (SectionDef sec : allCol2) {
                for (String raw : sec.moduleNames) {
                    if (findModuleByName(raw) != null && getModuleDisplayName(raw).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                        matchCount++;
                    }
                }
            }

            if (matchCount == 0) {
                drawSearchEmptyState(context, x, y, w, h, scale, alpha);
                maxScrollY = 0.0F;
                return;
            }

            float curY1 = drawSectionList(context, allCol1, col1X, y, colW, scale, mouseX, mouseY, alpha, dt, en);
            float curY2 = drawSectionList(context, allCol2, col2X, y, colW, scale, mouseX, mouseY, alpha, dt, en);
            float totalH = Math.max(curY1, curY2) - y;
            maxScrollY = Math.max(0.0F, totalH - h);
            return;
        }

        float curY1 = y;
        float curY2 = y;

        List<SectionDef> col1 = getSectionsForTab(selectedTab, true);
        List<SectionDef> col2 = getSectionsForTab(selectedTab, false);

        curY1 = drawSectionList(context, col1, col1X, curY1, colW, scale, mouseX, mouseY, alpha, dt, en);
        curY2 = drawSectionList(context, col2, col2X, curY2, colW, scale, mouseX, mouseY, alpha, dt, en);

        float totalH = Math.max(curY1, curY2) - y;
        maxScrollY = Math.max(0.0F, totalH - h);
    }

    private void drawSearchEmptyState(DrawContext context, float x, float y, float w, float h, float scale, float alpha) {
        float centerX = x + w * 0.5F;
        float centerY = y + h * 0.38F;

        // 3 Animated sequentially bouncing dots
        long time = System.currentTimeMillis();
        float dotSpacing = 16.0F * scale;
        int accent = getAccentColor();

        // 1200ms total loop period
        long cycle = time % 1200L;
        for (int i = 0; i < 3; i++) {
            long dotStart = i * 180L;
            long dotTime = (cycle - dotStart + 1200L) % 1200L;
            float bounceProgress = (dotTime < 420L) ? (dotTime / 420.0F) : 0.0F;
            float bounceHeight = (bounceProgress > 0.0F) ? (float) Math.sin(bounceProgress * Math.PI) * (10.0F * scale) : 0.0F;

            float dx = centerX + (i - 1) * dotSpacing;
            float dy = centerY - bounceHeight;
            float dotRadius = (3.0F + (bounceProgress > 0.0F ? 0.6F * (float) Math.sin(bounceProgress * Math.PI) : 0.0F)) * scale;
            float dotAlpha = (bounceProgress > 0.0F) ? (0.7F + 0.3F * (float) Math.sin(bounceProgress * Math.PI)) : 0.45F;

            // Subtle floor shadow under each jumping dot
            float shadowScale = Math.max(0.3F, 1.0F - (bounceHeight / (10.0F * scale)) * 0.7F);
            Render2D.drawCircle(context, dx, centerY + 5.0F * scale, 2.2F * scale * shadowScale, color(0x35000000, alpha * dotAlpha));

            // Bouncing dot
            Render2D.drawCircle(context, dx, dy, dotRadius, color(accent, alpha * dotAlpha));
        }

        String queryStr = search.length() > 24 ? search.substring(0, 24) + "..." : search;
        String title = isEn()
                ? ("No results for \"" + queryStr + "\"")
                : ("По запросу «" + queryStr + "» ничего не найдено");
        ModernFont.drawCentered(context, title, centerX, centerY + 24.0F * scale, 12.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_SEMIBOLD);

        String sub = isEn()
                ? "Try checking for typos or searching another term"
                : "Попробуйте ввести другое название функции";
        ModernFont.drawCentered(context, sub, centerX, centerY + 40.0F * scale, 9.5F * scale, color(UI_TEXT_MUTED, alpha), ModernFont.Type.INTER_MEDIUM);
    }

    private float drawSectionList(DrawContext context, List<SectionDef> sections, float x, float startY, float w, float scale, int mouseX, int mouseY, float alpha, float dt, boolean en) {
        float curY = startY;
        for (SectionDef sec : sections) {
            boolean collapsed = collapsedSections.getOrDefault(sec.titleRu, false);
            float targetExpand = collapsed ? 0.0F : 1.0F;
            float currentExpand = sectionExpandAnims.getOrDefault(sec.titleRu, targetExpand);
            currentExpand += (targetExpand - currentExpand) * Math.min(1.0F, dt * 7.0F); // Slow, smooth expansion
            sectionExpandAnims.put(sec.titleRu, currentExpand);

            List<String> visibleMods = new ArrayList<>();
            for (String raw : sec.moduleNames) {
                if (findModuleByName(raw) == null) continue;
                String disp = getModuleDisplayName(raw);
                if (search.isEmpty() || disp.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                    visibleMods.add(raw);
                }
            }

            if (visibleMods.isEmpty() && !search.isEmpty()) {
                continue;
            }

            float headerH = 34.0F * scale;
            float rowH = 32.0F * scale;
            float fullContentH = visibleMods.size() * rowH + 4.0F * scale;
            float cardH = headerH + fullContentH * currentExpand;

            // Section Card container
            Render2D.drawRound(context, x, curY, w, cardH, 6.0F * scale, color(UI_CARD_BG, alpha));
            Render2D.drawRoundOutline(context, x, curY, w, cardH, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));

            // Section Header
            boolean headerHover = inside(mouseX, mouseY, x, curY, w, headerH);
            int titleCol = color(getAccentColor(), alpha);
            float titleY = curY + (headerH - 11.5F * scale) * 0.5F;
            ModernFont.draw(context, sec.title(en), x + 14.0F * scale, titleY, 11.5F * scale, titleCol, ModernFont.Type.INTER_SEMIBOLD);

            // Clean, clearly separated vector chevrons centered on headerCenterY
            float headerCenterY = curY + headerH * 0.5F;
            drawSectionChevrons(context, x + w - 18.0F * scale, headerCenterY, scale, collapsed, headerHover, alpha);

            // Modules list with smooth sliding expand
            if (currentExpand > 0.01F) {
                float rowY = curY + headerH;
                ScissorStack.push(x, curY + headerH, w, fullContentH * currentExpand);
                try {
                    for (String raw : visibleMods) {
                        Module mod = findModuleByName(raw);
                        String disp = getModuleDisplayName(raw);
                        drawModuleRow(context, mod, raw, disp, x, rowY, w, rowH, scale, mouseX, mouseY, alpha * currentExpand, dt);
                        rowY += rowH;
                    }
                } finally {
                    ScissorStack.pop();
                }
            }

            curY += cardH + 10.0F * scale;
        }
        return curY;
    }

    private static void drawSectionChevrons(DrawContext context, float x, float headerCenterY, float scale, boolean collapsed, boolean hover, float alpha) {
        int col = color(hover ? UI_TEXT_MAIN : 0xFF5C7CFA, alpha);
        float armW = 3.6F * scale;
        float armH = 2.2F * scale;

        if (collapsed) {
            // Single down chevron perfectly centered on headerCenterY
            Render2D.drawLine(context, x - armW, headerCenterY - armH * 0.5F, x, headerCenterY + armH * 0.5F, 1.3F, col);
            Render2D.drawLine(context, x, headerCenterY + armH * 0.5F, x + armW, headerCenterY - armH * 0.5F, 1.3F, col);
        } else {
            // Distinct UP chevron
            float topY = headerCenterY - 4.5F * scale;
            Render2D.drawLine(context, x - armW, topY + armH * 0.5F, x, topY - armH * 0.5F, 1.3F, col);
            Render2D.drawLine(context, x, topY - armH * 0.5F, x + armW, topY + armH * 0.5F, 1.3F, col);

            // Distinct DOWN chevron with 9px clear separation
            float botY = headerCenterY + 4.5F * scale;
            Render2D.drawLine(context, x - armW, botY - armH * 0.5F, x, botY + armH * 0.5F, 1.3F, col);
            Render2D.drawLine(context, x, botY + armH * 0.5F, x + armW, botY - armH * 0.5F, 1.3F, col);
        }
    }

    private void drawModuleRow(DrawContext context, Module mod, String rawName, String displayName, float x, float y, float w, float h, float scale, int mouseX, int mouseY, float alpha, float dt) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        if (hover) {
            Render2D.drawRound(context, x + 4.0F * scale, y, w - 8.0F * scale, h, 4.0F * scale, color(UI_ROW_HOVER, alpha));
        }

        boolean enabled = mod != null ? mod.isEnabled() : dummyToggleStates.getOrDefault(rawName, false);
        boolean hasSettings = mod != null && moduleSettings.containsKey(mod) && !moduleSettings.get(mod).isEmpty();

        // Module Name
        int nameColor = enabled ? color(UI_TEXT_MAIN, alpha) : color(UI_TEXT_SEC, alpha);
        ModernFont.draw(context, displayName, x + 14.0F * scale, y + 10.5F * scale, 11.0F * scale,
                nameColor, ModernFont.Type.INTER_MEDIUM);

        // Right side controls: Keybind icon -> Three dots button -> Pill switch
        float ctrlRight = x + w - 12.0F * scale;

        // Pill Switch
        float switchW = 28.0F * scale;
        float switchH = 15.0F * scale;
        float switchX = ctrlRight - switchW;
        float switchY = y + (h - switchH) * 0.5F;

        // Snappy, smooth toggle animation
        float anim = toggleAnims.getOrDefault(displayName, enabled ? 1.0F : 0.0F);
        float targetAnim = enabled ? 1.0F : 0.0F;
        anim += (targetAnim - anim) * Math.min(1.0F, dt * 16.0F);
        toggleAnims.put(displayName, anim);

        int trackColor = interpolateColor(UI_SWITCH_OFF_TRACK, UI_SWITCH_ON_TRACK, anim);
        Render2D.drawRound(context, switchX, switchY, switchW, switchH, switchH * 0.5F, color(trackColor, alpha));

        float thumbDiam = switchH - 4.0F * scale;
        float thumbX = switchX + 2.0F * scale + (switchW - thumbDiam - 4.0F * scale) * anim;
        float thumbY = switchY + 2.0F * scale;
        int thumbColor = interpolateColor(UI_SWITCH_OFF_THUMB, UI_SWITCH_ON_THUMB, anim);
        Render2D.drawRound(context, thumbX, thumbY, thumbDiam, thumbDiam, thumbDiam * 0.5F, color(thumbColor, alpha));

        // Three dots button '•••' (ALWAYS shown for all modules)
        float nextLeft = switchX - 6.0F * scale;
        float dotsW = 18.0F * scale;
        float dotsH = 16.0F * scale;
        float dotsX = nextLeft - dotsW;
        float dotsY = y + (h - dotsH) * 0.5F;
        boolean dotsHover = inside(mouseX, mouseY, dotsX, dotsY, dotsW, dotsH);

        if (dotsHover) {
            currentFrameDotsHover = mod;
            Render2D.drawRound(context, dotsX, dotsY, dotsW, dotsH, 3.0F * scale, color(0x18FFFFFF, alpha));
        }

        ModernFont.drawCentered(context, "•••", dotsX + dotsW * 0.5F, dotsY + 3.0F * scale, 10.0F * scale,
                color(dotsHover ? UI_TEXT_MAIN : (hasSettings ? UI_TEXT_MUTED : 0x50FFFFFF), alpha), ModernFont.Type.INTER_MEDIUM);
        nextLeft = dotsX - 6.0F * scale;

        // Keyboard bind button: shown ONLY on hover or when bind popover is active (matching 3-dots button exactly)
        if (hover || (activeBindModule == mod)) {
            float keyBtnW = 18.0F * scale;
            float keyBtnH = 16.0F * scale;
            float keyIconX = nextLeft - keyBtnW;
            float keyIconY = y + (h - keyBtnH) * 0.5F;
            boolean keyHover = inside(mouseX, mouseY, keyIconX, keyIconY, keyBtnW, keyBtnH);

            // Neutral pill background on hover identical to 3 dots (3.0px radius, 0x18FFFFFF background)
            if (keyHover || activeBindModule == mod) {
                Render2D.drawRound(context, keyIconX, keyIconY, keyBtnW, keyBtnH, 3.0F * scale, color(0x18FFFFFF, alpha));
            }

            int iconCol = (keyHover || activeBindModule == mod) ? UI_TEXT_MAIN : UI_TEXT_MUTED;
            float iconSize = 13.0F * scale;
            float offX = (keyBtnW - iconSize) * 0.5F;
            float offY = (keyBtnH - iconSize) * 0.5F;
            drawKeyboardIcon(context, keyIconX + offX, keyIconY + offY, iconSize, iconCol, alpha);
        }
    }

    public static void drawKeyboardIcon(DrawContext context, float x, float y, float size, int col, float alpha) {
        if (size <= 0.0F) return;
        int finalCol = color(col, alpha);
        float s = size / 16.0F;
        float kw = size;
        float kh = size * 0.72F;
        float kx = x;
        float ky = y + (size - kh) * 0.5F;

        // 1. Chassis outer outline with SDF anti-aliasing
        float chassisRadius = 2.8F * s;
        float borderThickness = 1.1F * s;
        Render2D.drawRoundOutline(context, kx, ky, kw, kh, chassisRadius, borderThickness, finalCol);

        // 2. Keys inside chassis
        float padX = 2.4F * s;
        float padY = 2.2F * s;
        float innerW = kw - padX * 2.0F;
        float innerH = kh - padY * 2.0F;
        float gap = 1.6F * s;
        float keyH = (innerH - gap) * 0.5F;
        float keyR = 0.8F * s;

        // Row 1: 4 evenly spaced keys
        float keyW = (innerW - gap * 3.0F) / 4.0F;
        float r1Y = ky + padY;
        for (int i = 0; i < 4; i++) {
            Render2D.drawRound(context, kx + padX + i * (keyW + gap), r1Y, keyW, keyH, keyR, finalCol);
        }

        // Row 2: Left Key + Center Spacebar + Right Key
        float r2Y = r1Y + keyH + gap;
        float spaceW = innerW * 0.52F;
        float sideKeyW = (innerW - spaceW - gap * 2.0F) * 0.5F;
        Render2D.drawRound(context, kx + padX, r2Y, sideKeyW, keyH, keyR, finalCol);
        Render2D.drawRound(context, kx + padX + sideKeyW + gap, r2Y, spaceW, keyH, keyR, finalCol);
        Render2D.drawRound(context, kx + padX + sideKeyW + gap + spaceW + gap, r2Y, sideKeyW, keyH, keyR, finalCol);
    }

    private void drawHoverTooltip(DrawContext context, float scale, int mouseX, int mouseY, float alpha) {
        if (hoveredModule == null) return;
        Menu menu = moduleManager != null ? moduleManager.getMenu() : null;
        if (menu != null && !menu.isShowDescriptions()) return;

        long hoverDur = System.currentTimeMillis() - hoverStartTime;
        if (hoverDur < 120L) return;

        float tooltipAlpha = Math.min(1.0F, (hoverDur - 120L) / 80.0F) * alpha;
        String desc = ModernClickGuiRenderer.getModuleDescription(hoveredModule, isEn());
        if (desc == null || desc.isBlank()) desc = isEn() ? "No description available." : "Описание отсутствует.";

        String title = getModuleDisplayName(hoveredModule.getName());
        float titleW = ModernFont.getWidth(title, 10.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
        float descW = ModernFont.getWidth(desc, 9.0F * scale, ModernFont.Type.INTER_MEDIUM);
        float tw = Math.max(140.0F * scale, Math.max(titleW, descW) + 26.0F * scale);
        float th = 38.0F * scale;

        float tx = mouseX + 14.0F * scale;
        float ty = mouseY + 14.0F * scale;

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (tx + tw > screenW - 10.0F) tx = mouseX - tw - 10.0F * scale;
        if (ty + th > screenH - 10.0F) ty = mouseY - th - 10.0F * scale;

        // Premium floating dark glass container with soft atmospheric shadow
        Render2D.drawShadow(context, tx, ty, tw, th, 18.0F * scale, 6.0F * scale, color(0xB0000000, tooltipAlpha));
        Render2D.drawRound(context, tx, ty, tw, th, 6.0F * scale, color(0xF607080C, tooltipAlpha));
        Render2D.drawRoundOutline(context, tx, ty, tw, th, 6.0F * scale, 1.0F, color(0x18FFFFFF, tooltipAlpha));

        // Subtle accent dot next to module name
        Render2D.drawCircle(context, tx + 10.0F * scale, ty + 12.0F * scale, 2.0F * scale, color(getAccentColor(), tooltipAlpha));
        ModernFont.draw(context, title, tx + 16.0F * scale, ty + 7.5F * scale, 10.5F * scale, color(UI_TEXT_MAIN, tooltipAlpha), ModernFont.Type.INTER_SEMIBOLD);
        ModernFont.draw(context, desc, tx + 10.0F * scale, ty + 22.0F * scale, 9.0F * scale, color(0xFF94A3B8, tooltipAlpha), ModernFont.Type.INTER_MEDIUM);
    }

    private void drawContextMenu(DrawContext context, float scale, int mouseX, int mouseY, float alpha, float dt) {
        if (contextMenuModule == null) return;
        List<Setting<?>> settings = moduleSettings.get(contextMenuModule);
        boolean hasSettings = (settings != null && !settings.isEmpty());
        if (!hasSettings) {
            contextMenuModule = null;
            return;
        }

        float cardW = 220.0F * scale;
        float headerH = 34.0F * scale;

        float contentH = 0.0F;
        for (Setting<?> s : settings) {
            if (!s.isVisible()) continue;
            if (s instanceof BooleanSetting) contentH += 26.0F * scale;
            else if (s instanceof SliderSetting) contentH += 34.0F * scale;
            else if (s instanceof ModeSetting) contentH += 26.0F * scale;
            else if (s instanceof MultiModeSetting) contentH += 26.0F * scale;
            else if (s instanceof KeybindSetting) contentH += 26.0F * scale;
            else if (s instanceof ColorSetting) contentH += 26.0F * scale;
            else if (s instanceof StringSetting) contentH += 38.0F * scale;
            else if (s instanceof ActionSetting) contentH += 28.0F * scale;
            else contentH += 24.0F * scale;
        }

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float maxVisibleSettingsH = Math.min(contentH, Math.min(300.0F * scale, screenH - 120.0F * scale));
        float visibleSettingsH = maxVisibleSettingsH;
        float cardH = headerH + visibleSettingsH + 10.0F * scale;

        maxContextMenuScrollY = Math.max(0.0F, contentH - visibleSettingsH);
        contextMenuScrollY = Math.min(0.0F, Math.max(-maxContextMenuScrollY, contextMenuScrollY));

        float x = Math.max(10.0F, Math.min(contextMenuX, screenW - cardW - 10.0F));
        float y = Math.max(10.0F, Math.min(contextMenuY, screenH - cardH - 10.0F));

        Render2D.drawShadow(context, x, y, cardW, cardH, 6.0F * scale, 14.0F * scale, color(0xCC000000, alpha));
        Color contextGlassTint = new Color(6, 8, 12, 245);
        BlurRenderer.drawLiquidGlass(
                context,
                x, y, cardW, cardH, 6.0F * scale,
                contextGlassTint, alpha,
                1.1F, 0.8F, 0.0F,
                1.0F, 0.5F, true
        );
        Render2D.drawRound(context, x, y, cardW, cardH, 6.0F * scale, color(UI_CARD_BG, alpha));
        Render2D.drawRoundOutline(context, x, y, cardW, cardH, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));

        // Header: Clean module title
        String modTitle = getModuleDisplayName(contextMenuModule.getName());
        ModernFont.draw(context, modTitle, x + 14.0F * scale, y + 11.5F * scale, 11.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);

        // Divider
        Render2D.drawLine(context, x + 8.0F * scale, y + headerH, x + cardW - 8.0F * scale, y + headerH, 1.0F, color(0x14FFFFFF, alpha));

        // Settings list with GPU ScissorStack
        float listAreaY = y + headerH + 4.0F * scale;
        ScissorStack.push(x, listAreaY, cardW, visibleSettingsH);
        try {
            float curY = listAreaY + contextMenuScrollY;

            for (Setting<?> s : settings) {
                if (!s.isVisible()) continue;

                if (s instanceof BooleanSetting b) {
                    float rowH = 26.0F * scale;
                    ModernFont.draw(context, s.getName(), x + 12.0F * scale, curY + 6.5F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    float swW = 26.0F * scale;
                    float swH = 14.0F * scale;
                    float swX = x + cardW - swW - 12.0F * scale;
                    float swY = curY + 5.0F * scale;

                    float targetAnim = b.get() ? 1.0F : 0.0F;
                    float anim = settingToggleAnims.getOrDefault(b, targetAnim);
                    anim += (targetAnim - anim) * Math.min(1.0F, dt * 16.0F);
                    settingToggleAnims.put(b, anim);

                    int track = interpolateColor(UI_SWITCH_OFF_TRACK, UI_SWITCH_ON_TRACK, anim);
                    Render2D.drawRound(context, swX, swY, swW, swH, swH * 0.5F, color(track, alpha));
                    float thDiam = swH - 4.0F * scale;
                    float thX = swX + 2.0F * scale + (swW - thDiam - 4.0F * scale) * anim;
                    int thumbCol = interpolateColor(UI_SWITCH_OFF_THUMB, UI_SWITCH_ON_THUMB, anim);
                    Render2D.drawRound(context, thX, swY + 2.0F * scale, thDiam, thDiam, thDiam * 0.5F, color(thumbCol, alpha));

                    curY += rowH;
                } else if (s instanceof SliderSetting sl) {
                    float rowH = 34.0F * scale;
                    ModernFont.draw(context, sl.getName(), x + 12.0F * scale, curY + 3.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    String valStr = sl.getValueString();
                    float valW = ModernFont.getWidth(valStr, 9.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
                    ModernFont.draw(context, valStr, x + cardW - valW - 12.0F * scale, curY + 3.0F * scale, 9.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);

                    float barX = x + 12.0F * scale;
                    float barY = curY + 18.0F * scale;
                    float barW = cardW - 24.0F * scale;
                    float barH = 4.5F * scale;
                    float progress = sl.getNormalized();

                    Render2D.drawRound(context, barX, barY, barW, barH, 2.0F * scale, color(0xFF0A0D14, alpha));
                    Render2D.drawRound(context, barX, barY, barW * progress, barH, 2.0F * scale, color(getAccentColor(), alpha));

                    float knobX = barX + barW * progress;
                    float knobY = barY + barH * 0.5F;
                    Render2D.drawCircle(context, knobX, knobY, 3.5F * scale, color(0xFFFFFFFF, alpha));
                    Render2D.drawCircle(context, knobX, knobY, 2.0F * scale, color(getAccentColor(), alpha));

                    curY += rowH;
                } else if (s instanceof ModeSetting m) {
                    float rowH = 26.0F * scale;
                    ModernFont.draw(context, m.getName(), x + 12.0F * scale, curY + 6.5F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    String val = m.get();
                    float textW = ModernFont.getWidth(val, 9.0F * scale, ModernFont.Type.INTER_MEDIUM);
                    float chevronSpace = 16.0F * scale;
                    float valW = Math.max(64.0F * scale, textW + chevronSpace + 14.0F * scale);
                    float valX = x + cardW - valW - 12.0F * scale;
                    float valY = curY + 3.0F * scale;
                    float valH = 19.0F * scale;
                    boolean isOpen = (openDropdownMode == m);
                    boolean valHover = inside(mouseX, mouseY, valX, valY, valW, valH);

                    int bgCol = isOpen ? 0xFF141924 : (valHover ? 0xFF10141E : 0xFF0A0D14);
                    int borderCol = isOpen ? getAccentColor() : (valHover ? 0x2AFFFFFF : 0x14FFFFFF);
                    Render2D.drawRound(context, valX, valY, valW, valH, 4.0F * scale, color(bgCol, alpha));
                    Render2D.drawRoundOutline(context, valX, valY, valW, valH, 4.0F * scale, 1.0F, color(borderCol, alpha));

                    int textCol = isOpen ? color(getAccentColor(), alpha) : color(valHover ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);
                    ModernFont.draw(context, val, valX + 8.0F * scale, valY + 5.0F * scale, 9.0F * scale, textCol, ModernFont.Type.INTER_MEDIUM);

                    float chX = valX + valW - 10.0F * scale;
                    float chY = valY + valH * 0.5F;
                    float chArm = 2.8F * scale;
                    int chCol = isOpen ? color(getAccentColor(), alpha) : color(valHover ? UI_TEXT_MAIN : UI_TEXT_MUTED, alpha);
                    if (isOpen) {
                        Render2D.drawLine(context, chX - chArm, chY + 1.2F * scale, chX, chY - 1.5F * scale, 1.2F, chCol);
                        Render2D.drawLine(context, chX, chY - 1.5F * scale, chX + chArm, chY + 1.2F * scale, 1.2F, chCol);
                    } else {
                        Render2D.drawLine(context, chX - chArm, chY - 1.5F * scale, chX, chY + 1.2F * scale, 1.2F, chCol);
                        Render2D.drawLine(context, chX, chY + 1.2F * scale, chX + chArm, chY - 1.5F * scale, 1.2F, chCol);
                    }

                    curY += rowH;
                } else if (s instanceof MultiModeSetting mm) {
                    float rowH = 26.0F * scale;
                    ModernFont.draw(context, mm.getName(), x + 12.0F * scale, curY + 6.5F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    String val = mm.getDisplaySummary(isEn());
                    float textW = ModernFont.getWidth(val, 9.0F * scale, ModernFont.Type.INTER_MEDIUM);
                    float chevronSpace = 16.0F * scale;
                    float valW = Math.max(64.0F * scale, textW + chevronSpace + 14.0F * scale);
                    float valX = x + cardW - valW - 12.0F * scale;
                    float valY = curY + 3.0F * scale;
                    float valH = 19.0F * scale;
                    boolean isOpen = (openDropdownMulti == mm);
                    boolean valHover = inside(mouseX, mouseY, valX, valY, valW, valH);

                    int bgCol = isOpen ? 0xFF141924 : (valHover ? 0xFF10141E : 0xFF0A0D14);
                    int borderCol = isOpen ? getAccentColor() : (valHover ? 0x2AFFFFFF : 0x14FFFFFF);
                    Render2D.drawRound(context, valX, valY, valW, valH, 4.0F * scale, color(bgCol, alpha));
                    Render2D.drawRoundOutline(context, valX, valY, valW, valH, 4.0F * scale, 1.0F, color(borderCol, alpha));

                    int textCol = isOpen ? color(getAccentColor(), alpha) : color(valHover ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);
                    ModernFont.draw(context, val, valX + 8.0F * scale, valY + 5.0F * scale, 9.0F * scale, textCol, ModernFont.Type.INTER_MEDIUM);

                    float chX = valX + valW - 10.0F * scale;
                    float chY = valY + valH * 0.5F;
                    float chArm = 2.8F * scale;
                    int chCol = isOpen ? color(getAccentColor(), alpha) : color(valHover ? UI_TEXT_MAIN : UI_TEXT_MUTED, alpha);
                    if (isOpen) {
                        Render2D.drawLine(context, chX - chArm, chY + 1.2F * scale, chX, chY - 1.5F * scale, 1.2F, chCol);
                        Render2D.drawLine(context, chX, chY - 1.5F * scale, chX + chArm, chY + 1.2F * scale, 1.2F, chCol);
                    } else {
                        Render2D.drawLine(context, chX - chArm, chY - 1.5F * scale, chX, chY + 1.2F * scale, 1.2F, chCol);
                        Render2D.drawLine(context, chX, chY + 1.2F * scale, chX + chArm, chY - 1.5F * scale, 1.2F, chCol);
                    }

                    curY += rowH;
                } else if (s instanceof KeybindSetting kb) {
                    float rowH = 26.0F * scale;
                    ModernFont.draw(context, kb.getName(), x + 12.0F * scale, curY + 6.5F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    boolean isListening = (listeningSettingBind == kb);
                    String keyName = isListening ? "..." : KeybindSetting.getKeyName(kb.get(), isEn());
                    float btnW = Math.max(42.0F * scale, ModernFont.getWidth(keyName, 9.0F * scale, ModernFont.Type.INTER_MEDIUM) + 12.0F * scale);
                    float btnH = 18.0F * scale;
                    float btnX = x + cardW - btnW - 12.0F * scale;
                    float btnY = curY + 4.0F * scale;
                    boolean btnHover = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);

                    int bgCol = isListening ? 0xFF141924 : (btnHover ? 0xFF10141E : 0xFF0A0D14);
                    int borderCol = isListening ? getAccentColor() : (btnHover ? 0x2AFFFFFF : 0x14FFFFFF);
                    Render2D.drawRound(context, btnX, btnY, btnW, btnH, 3.0F * scale, color(bgCol, alpha));
                    Render2D.drawRoundOutline(context, btnX, btnY, btnW, btnH, 3.0F * scale, 1.0F, color(borderCol, alpha));

                    int textCol = isListening ? color(getAccentColor(), alpha) : color(btnHover ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);
                    ModernFont.drawCentered(context, keyName, btnX + btnW * 0.5F, btnY + 4.5F * scale, 9.0F * scale, textCol, ModernFont.Type.INTER_MEDIUM);

                    curY += rowH;
                } else if (s instanceof ColorSetting c) {
                    float rowH = 26.0F * scale;
                    ModernFont.draw(context, c.getName(), x + 12.0F * scale, curY + 6.5F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    float dotW = 26.0F * scale;
                    float dotH = 15.0F * scale;
                    float dotX = x + cardW - dotW - 12.0F * scale;
                    float dotY = curY + 5.0F * scale;

                    Render2D.drawRound(context, dotX, dotY, dotW, dotH, 3.0F * scale, color(c.get(), alpha));
                    Render2D.drawRoundOutline(context, dotX, dotY, dotW, dotH, 3.0F * scale, 1.0F, color(activeColorPicker == c ? getAccentColor() : 0x18FFFFFF, alpha));

                    curY += rowH;
                } else if (s instanceof StringSetting str) {
                    float rowH = 38.0F * scale;
                    ModernFont.draw(context, str.getName(), x + 12.0F * scale, curY + 3.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);

                    boolean focused = activeStringSetting == str;
                    String val = focused ? activeStringDraft : str.get();
                    String displayStr = val + (focused && (System.currentTimeMillis() % 1000L < 500L) ? "|" : "");
                    String shownText = val.isEmpty() && !focused ? str.getPlaceholder() : displayStr;

                    float boxX = x + 12.0F * scale;
                    float boxY = curY + 16.0F * scale;
                    float boxW = cardW - 24.0F * scale;
                    float boxH = 18.0F * scale;

                    Render2D.drawRound(context, boxX, boxY, boxW, boxH, 3.0F * scale, color(0xFF0A0D14, alpha));
                    Render2D.drawRoundOutline(context, boxX, boxY, boxW, boxH, 3.0F * scale, 1.0F, color(focused ? getAccentColor() : 0x14FFFFFF, alpha));
                    try {
                        ScissorStack.push(boxX + 3.0F * scale, boxY, boxW - 6.0F * scale, boxH);
                        ModernFont.draw(context, shownText, boxX + 7.0F * scale, boxY + 4.0F * scale, 9.5F * scale, color(val.isEmpty() && !focused ? UI_TEXT_MUTED : UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
                    } finally {
                        ScissorStack.pop();
                    }

                    curY += rowH;
                } else if (s instanceof ActionSetting act) {
                    float rowH = 28.0F * scale;
                    float btnX = x + 12.0F * scale;
                    float btnY = curY + 2.0F * scale;
                    float btnW = cardW - 24.0F * scale;
                    float btnH = 22.0F * scale;
                    boolean btnHover = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);

                    // Liquid Glass substrate without harsh outline
                    Color glassTint = btnHover ? new Color(22, 32, 54, 220) : new Color(12, 16, 26, 175);
                    BlurRenderer.drawLiquidGlass(
                            context,
                            btnX, btnY, btnW, btnH, 5.0F * scale,
                            glassTint, alpha,
                            1.2F, 0.9F, 0.0F,
                            1.0F, 0.6F, true
                    );
                    int depthCol = btnHover ? color(getAccentColor(), 0.22F) : color(0x50030509, alpha);
                    Render2D.drawRound(context, btnX, btnY, btnW, btnH, 5.0F * scale, depthCol);

                    // Crisp icon + clean text (no blocky unicode gear)
                    String rawText = act.getButtonText();
                    if (rawText == null || rawText.isEmpty()) rawText = act.getName();
                    String cleanText = rawText.replaceAll("^[⚙💧🔧✓]\\s*", "").trim();

                    Identifier icon = rawText.contains("💧") ? ICON_PIPETTE : ICON_SETTINGS;
                    float iconSize = 11.5F * scale;
                    float textW = ModernFont.getWidth(cleanText, 9.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
                    float gap = 5.0F * scale;
                    float totalW = iconSize + gap + textW;
                    float startX = btnX + (btnW - totalW) * 0.5F;

                    int itemCol = btnHover ? color(0xFFFFFFFF, alpha) : color(getAccentColor(), alpha);
                    ModernClickGuiRenderer.drawTexture(context, icon, startX, btnY + (btnH - iconSize) * 0.5F, iconSize, iconSize, itemCol);
                    ModernFont.draw(context, cleanText, startX + iconSize + gap, btnY + 6.0F * scale, 9.5F * scale, itemCol, ModernFont.Type.INTER_SEMIBOLD);

                    curY += rowH;
                }
            }
        } finally {
            ScissorStack.pop();
        }

        // Scrollbar if overflowing
        if (maxContextMenuScrollY > 0.0F) {
            float scrollbarW = 2.5F * scale;
            float scrollbarX = x + cardW - scrollbarW - 3.0F * scale;
            float scrollTrackH = visibleSettingsH;
            float thumbH = Math.max(14.0F * scale, scrollTrackH * (visibleSettingsH / (visibleSettingsH + maxContextMenuScrollY)));
            float thumbY = listAreaY + (-contextMenuScrollY / maxContextMenuScrollY) * (scrollTrackH - thumbH);
            Render2D.drawRound(context, scrollbarX, thumbY, scrollbarW, thumbH, 1.25F * scale, color(UI_TEXT_MUTED, alpha * 0.6F));
        }
    }

    private void drawKeybindPopover(DrawContext context, float scale, int mouseX, int mouseY, float alpha, float dt) {
        if (activeBindModule == null) return;

        int boundKey = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(activeBindModule.getName(), GLFW.GLFW_KEY_UNKNOWN);
        boolean isBound = (boundKey != GLFW.GLFW_KEY_UNKNOWN && boundKey != 0);

        float popW = 216.0F * scale;
        float popH = isBound ? (154.0F * scale) : (124.0F * scale);
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float px = Math.max(10.0F, Math.min(bindPopoverX, screenW - popW - 10.0F));
        float py = Math.max(10.0F, Math.min(bindPopoverY, screenH - popH - 10.0F));

        Render2D.drawShadow(context, px, py, popW, popH, 8.0F * scale, 14.0F * scale, color(0xCC000000, alpha));
        Color popGlassTint = new Color(6, 8, 12, 245);
        BlurRenderer.drawLiquidGlass(
                context,
                px, py, popW, popH, 8.0F * scale,
                popGlassTint, alpha,
                1.1F, 0.8F, 0.0F,
                1.0F, 0.5F, true
        );
        Render2D.drawRound(context, px, py, popW, popH, 8.0F * scale, color(UI_POPOVER_BG, alpha));
        Render2D.drawRoundOutline(context, px, py, popW, popH, 8.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));

        // 1. Header: Keyboard icon + Module Name + Close button
        drawKeyboardIcon(context, px + 12.0F * scale, py + 10.0F * scale, 16.0F * scale, 0xFFFFFFFF, alpha);
        String modName = getModuleDisplayName(activeBindModule.getName());
        ModernFont.draw(context, modName, px + 34.0F * scale, py + 12.5F * scale, 10.5F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_SEMIBOLD);

        float closeSize = 16.0F * scale;
        float closeX = px + popW - closeSize - 10.0F * scale;
        float closeY = py + 10.0F * scale;
        boolean closeHover = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        Render2D.drawRound(context, closeX, closeY, closeSize, closeSize, 3.5F * scale, color(closeHover ? 0xFF28181A : 0x00000000, alpha));
        ModernFont.draw(context, "×", closeX + 4.5F * scale, closeY + 2.0F * scale, 11.5F * scale, color(closeHover ? 0xFFEF4444 : UI_TEXT_SEC, alpha), ModernFont.Type.INTER_SEMIBOLD);

        // 2. Sleek dark elevated rounded card for key display
        float boxX = px + 12.0F * scale;
        float boxY = py + 34.0F * scale;
        float boxW = popW - 24.0F * scale;
        float boxH = 46.0F * scale;
        float boxRadius = 8.0F * scale;
        boolean boxHover = inside(mouseX, mouseY, boxX, boxY, boxW, boxH);

        Render2D.drawRound(context, boxX, boxY, boxW, boxH, boxRadius, color(boxHover ? 0xFF141824 : 0xFF0A0D14, alpha));
        Render2D.drawRoundOutline(context, boxX, boxY, boxW, boxH, boxRadius, 1.0F, color(boxHover ? 0x30FFFFFF : 0x16FFFFFF, alpha));

        String keyTitle = isBound ? formatKeyName(boundKey) : (isEn() ? "NONE" : "НЕ НАЗНАЧЕНО");
        int keyTitleCol = isBound ? color(0xFFFFFFFF, alpha) : color(0xFFE2E8F0, alpha);
        ModernFont.drawCentered(context, keyTitle, boxX + boxW * 0.5F, boxY + 10.0F * scale, 13.0F * scale, keyTitleCol, ModernFont.Type.SF_BOLD);

        String clickHint = isEn() ? "Click to change key" : "Нажмите для изменения";
        ModernFont.drawCentered(context, clickHint, boxX + boxW * 0.5F, boxY + 28.0F * scale, 8.5F * scale, color(0xFF64748B, alpha), ModernFont.Type.INTER_MEDIUM);

        // 3. Mode Selection Pills (Toggle / Hold with smooth animated slider)
        float segX = px + 12.0F * scale;
        float segY = boxY + boxH + 8.0F * scale;
        float segW = popW - 24.0F * scale;
        float segH = 24.0F * scale;
        float pillRadius = 5.5F * scale;

        // Container track
        Render2D.drawRound(context, segX, segY, segW, segH, pillRadius, color(0xFF0A0D14, alpha));
        Render2D.drawRoundOutline(context, segX, segY, segW, segH, pillRadius, 1.0F, color(0x14FFFFFF, alpha));

        // Animated target: 0.0 for Toggle, 1.0 for Hold
        float targetSlide = bindHoldMode ? 1.0F : 0.0F;
        bindModeSliderAnim += (targetSlide - bindModeSliderAnim) * Math.min(1.0F, dt * 14.0F);

        float halfW = (segW - 4.0F * scale) * 0.5F;
        float indicatorX = segX + 2.0F * scale + halfW * bindModeSliderAnim;
        float indicatorY = segY + 2.0F * scale;
        float indicatorH = segH - 4.0F * scale;

        // Sliding active pill
        Render2D.drawRound(context, indicatorX, indicatorY, halfW, indicatorH, pillRadius - 1.5F * scale, color(0x2A4C6EF5, alpha));
        Render2D.drawRoundOutline(context, indicatorX, indicatorY, halfW, indicatorH, pillRadius - 1.5F * scale, 1.0F, color(getAccentColor(), alpha * 0.85F));

        float btn1X = segX + 2.0F * scale;
        float btn2X = btn1X + halfW;
        boolean hov1 = inside(mouseX, mouseY, btn1X, indicatorY, halfW, indicatorH);
        boolean hov2 = inside(mouseX, mouseY, btn2X, indicatorY, halfW, indicatorH);

        int col1 = !bindHoldMode ? color(getAccentColor(), alpha) : color(hov1 ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);
        int col2 = bindHoldMode ? color(getAccentColor(), alpha) : color(hov2 ? UI_TEXT_MAIN : UI_TEXT_SEC, alpha);

        ModernFont.drawCentered(context, isEn() ? "Toggle" : "Переключение", btn1X + halfW * 0.5F, indicatorY + 5.0F * scale, 9.0F * scale, col1, !bindHoldMode ? ModernFont.Type.INTER_SEMIBOLD : ModernFont.Type.INTER_MEDIUM);
        ModernFont.drawCentered(context, isEn() ? "Hold" : "Зажатие", btn2X + halfW * 0.5F, indicatorY + 5.0F * scale, 9.0F * scale, col2, bindHoldMode ? ModernFont.Type.INTER_SEMIBOLD : ModernFont.Type.INTER_MEDIUM);

        // 4. Clear option if bound (Strictly positioned inside card frame!)
        if (isBound) {
            float unbindX = px + 12.0F * scale;
            float unbindY = segY + segH + 7.0F * scale;
            float unbindW = popW - 24.0F * scale;
            float unbindH = 20.0F * scale;
            boolean unbindHover = inside(mouseX, mouseY, unbindX, unbindY, unbindW, unbindH);
            Render2D.drawRound(context, unbindX, unbindY, unbindW, unbindH, 4.0F * scale, color(unbindHover ? 0x2E2A1215 : 0x1A221012, alpha));
            Render2D.drawRoundOutline(context, unbindX, unbindY, unbindW, unbindH, 4.0F * scale, 1.0F, color(unbindHover ? 0xFFEF4444 : 0x45EF4444, alpha));
            ModernFont.drawCentered(context, isEn() ? "Clear bind" : "Сбросить бинд", unbindX + unbindW * 0.5F, unbindY + 5.0F * scale, 9.0F * scale, color(unbindHover ? 0xFFEF4444 : 0xEEF87171, alpha), ModernFont.Type.INTER_MEDIUM);
        }
    }

    private void drawFullscreenKeyListenModal(DrawContext context, int screenWidth, int screenHeight, float scale, int mouseX, int mouseY, float alpha) {
        // Dim entire screen with sleek dark blur & shadow scrim
        Render2D.drawRound(context, 0.0F, 0.0F, screenWidth, screenHeight, 0.0F, color(0xD8000000, alpha));

        float cardW = 320.0F * scale;
        float cardH = 150.0F * scale;
        float cx = (screenWidth - cardW) * 0.5F;
        float cy = (screenHeight - cardH) * 0.5F;

        Render2D.drawShadow(context, cx, cy, cardW, cardH, 20.0F * scale, 18.0F * scale, color(0xFF000000, alpha));
        Color glassTint = new Color(5, 7, 10, 252);
        BlurRenderer.drawLiquidGlass(
                context,
                cx, cy, cardW, cardH, 12.0F * scale,
                glassTint, alpha,
                1.4F, 1.0F, 0.0F,
                1.0F, 0.6F, true
        );
        Render2D.drawRound(context, cx, cy, cardW, cardH, 12.0F * scale, color(UI_POPOVER_BG, alpha));
        Render2D.drawRoundOutline(context, cx, cy, cardW, cardH, 12.0F * scale, 1.0F, color(0x22FFFFFF, alpha));

        // Keyboard icon
        float iconSize = 32.0F * scale;
        float iconX = cx + (cardW - iconSize) * 0.5F;
        float iconY = cy + 18.0F * scale;
        drawKeyboardIcon(context, iconX, iconY, iconSize, 0xFFFFFFFF, alpha);

        // Function Name
        String modName = activeBindModule != null ? getModuleDisplayName(activeBindModule.getName()) : "";
        ModernFont.drawCentered(context, modName, cx + cardW * 0.5F, iconY + iconSize + 8.0F * scale, 11.5F * scale, color(0xFF94A3B8, alpha), ModernFont.Type.INTER_MEDIUM);

        // Big crisp neutral prompt: "Нажмите любую клавишу..." (no blue glowing text)
        String prompt = isEn() ? "Press any key..." : "Нажмите любую клавишу...";
        ModernFont.drawCentered(context, prompt, cx + cardW * 0.5F, iconY + iconSize + 25.0F * scale, 14.5F * scale, color(0xFFF8FAFC, alpha), ModernFont.Type.SF_BOLD);

        // Cancel / Clear Hint
        String hint = isEn() ? "ESC to cancel  •  DELETE to clear bind" : "ESC — отмена  •  DELETE — сбросить";
        ModernFont.drawCentered(context, hint, cx + cardW * 0.5F, cy + cardH - 18.0F * scale, 9.0F * scale, color(0xFF64748B, alpha), ModernFont.Type.INTER_MEDIUM);
    }

    private void drawFloatingDropdown(DrawContext context, float scale, int mouseX, int mouseY, float alpha) {
        if (dropdownAnim <= 0.01F) return;

        List<String> options = null;
        if (openDropdownMode != null) options = openDropdownMode.getModes();
        else if (openDropdownMulti != null) options = openDropdownMulti.getAllOptions();
        else if ("lang".equals(openGlobalDropdown)) options = List.of("Русский", "English");
        else if ("scale".equals(openGlobalDropdown)) options = Menu.SCALE_MODES;
        else if ("preset".equals(openGlobalDropdown)) options = Menu.THEME_PRESETS;

        if (options == null || options.isEmpty()) return;

        float maxOptW = 0.0F;
        float extraPadding = (openDropdownMulti != null) ? 38.0F * scale : 24.0F * scale;
        for (String opt : options) {
            float w = ModernFont.getWidth(opt, 9.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
            if (w > maxOptW) maxOptW = w;
        }
        float menuW = Math.max(dropdownW, Math.max(105.0F * scale, maxOptW + extraPadding));

        float itemH = 21.0F * scale;
        float menuH = options.size() * itemH + 6.0F * scale;
        float menuX = (dropdownTriggerW > 0.0F) ? (dropdownTriggerX + dropdownTriggerW - menuW) : dropdownX;
        float menuY = dropdownY;

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        // If overflows bottom, open upwards
        boolean opensUp = (menuY + menuH > screenH - 10.0F);
        if (opensUp) {
            menuY = Math.max(10.0F, dropdownY - menuH - 24.0F * scale);
        }
        if (menuX + menuW > screenW - 10.0F) {
            menuX = Math.max(10.0F, screenW - menuW - 10.0F);
        }
        if (menuX < 10.0F) {
            menuX = 10.0F;
        }

        float ddAlpha = alpha * dropdownAnim;
        float ease = (float) (1.0 - Math.pow(1.0 - dropdownAnim, 3.0));
        float slideY = opensUp ? (1.0F - ease) * 6.0F * scale : (1.0F - ease) * -6.0F * scale;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        float mCenterX = menuX + menuW * 0.5F;
        float mCenterY = menuY + menuH * 0.5F;
        matrices.translate(mCenterX, mCenterY + slideY);
        matrices.scale(0.96F + 0.04F * ease, 0.96F + 0.04F * ease);
        matrices.translate(-mCenterX, -mCenterY);

        Render2D.drawShadow(context, menuX, menuY, menuW, menuH, 5.0F * scale, 12.0F * scale, color(0xCC000000, ddAlpha));
        Color ddGlassTint = new Color(6, 8, 12, 245);
        BlurRenderer.drawLiquidGlass(
                context,
                menuX, menuY, menuW, menuH, 5.0F * scale,
                ddGlassTint, ddAlpha,
                1.0F, 0.8F, 0.0F,
                1.0F, 0.5F, true
        );
        Render2D.drawRound(context, menuX, menuY, menuW, menuH, 5.0F * scale, color(UI_POPOVER_BG, ddAlpha));
        Render2D.drawRoundOutline(context, menuX, menuY, menuW, menuH, 5.0F * scale, 1.0F, color(UI_CARD_BORDER, ddAlpha));

        float optY = menuY + 3.0F * scale;
        for (String opt : options) {
            boolean selected = false;
            if (openDropdownMode != null) selected = opt.equalsIgnoreCase(openDropdownMode.get());
            else if (openDropdownMulti != null) selected = openDropdownMulti.isSelected(opt);
            else if ("lang".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                selected = opt.equalsIgnoreCase(moduleManager.getMenu().getLanguage());
            } else if ("scale".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                selected = opt.equalsIgnoreCase(moduleManager.getMenu().getScaleMode());
            } else if ("preset".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                selected = opt.equalsIgnoreCase(moduleManager.getMenu().getThemePreset());
            }

            boolean optHover = inside(mouseX, mouseY, menuX + 2.0F * scale, optY, menuW - 4.0F * scale, itemH);

            if (selected) {
                Render2D.drawRound(context, menuX + 3.0F * scale, optY, menuW - 6.0F * scale, itemH, 3.5F * scale, color(0x2A4C6EF5, ddAlpha));
                Render2D.drawRoundOutline(context, menuX + 3.0F * scale, optY, menuW - 6.0F * scale, itemH, 3.5F * scale, 1.0F, color(getAccentColor(), ddAlpha * 0.7F));
            } else if (optHover) {
                Render2D.drawRound(context, menuX + 3.0F * scale, optY, menuW - 6.0F * scale, itemH, 3.5F * scale, color(0x15FFFFFF, ddAlpha));
            }

            int col = selected ? color(getAccentColor(), ddAlpha) : color(optHover ? UI_TEXT_MAIN : UI_TEXT_SEC, ddAlpha);
            if (openDropdownMulti != null) {
                float boxSize = 11.0F * scale;
                float boxX = menuX + 7.0F * scale;
                float boxY = optY + (itemH - boxSize) * 0.5F;
                Render2D.drawRound(context, boxX, boxY, boxSize, boxSize, 2.5F * scale, color(selected ? getAccentColor() : 0xFF0A0D14, ddAlpha));
                Render2D.drawRoundOutline(context, boxX, boxY, boxSize, boxSize, 2.5F * scale, 1.0F, color(selected ? getAccentColor() : 0x20FFFFFF, ddAlpha));
                if (selected) {
                    ModernFont.drawCentered(context, "✓", boxX + boxSize * 0.5F, boxY + 1.0F * scale, 8.5F * scale, color(0xFFFFFFFF, ddAlpha), ModernFont.Type.INTER_SEMIBOLD);
                }
                ModernFont.draw(context, opt, boxX + boxSize + 6.0F * scale, optY + 5.5F * scale, 9.5F * scale, col, selected ? ModernFont.Type.INTER_SEMIBOLD : ModernFont.Type.INTER_MEDIUM);
            } else {
                ModernFont.draw(context, opt, menuX + 9.0F * scale, optY + 5.5F * scale, 9.5F * scale, col, selected ? ModernFont.Type.INTER_SEMIBOLD : ModernFont.Type.INTER_MEDIUM);
            }

            optY += itemH;
        }

        matrices.popMatrix();
    }

    private void drawColorPicker(DrawContext context, float scale, int mouseX, int mouseY, float alpha) {
        if (activeColorPicker == null) return;

        float pickW = 196.0F * scale;
        float pickH = 164.0F * scale;
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float px, py;
        if (contextMenuModule != null) {
            px = contextMenuX + 228.0F * scale;
            if (px + pickW > screenW - 10.0F) {
                px = Math.max(10.0F, contextMenuX - pickW - 8.0F * scale);
            }
            py = Math.max(10.0F, Math.min(contextMenuY, screenH - pickH - 10.0F));
        } else {
            px = (screenW - pickW) * 0.5F;
            py = (screenH - pickH) * 0.5F;
        }

        Render2D.drawShadow(context, px, py, pickW, pickH, 6.0F * scale, 14.0F * scale, color(0xCC000000, alpha));
        Color pickGlassTint = new Color(5, 7, 10, 252);
        BlurRenderer.drawLiquidGlass(
                context,
                px, py, pickW, pickH, 6.0F * scale,
                pickGlassTint, alpha,
                1.1F, 0.8F, 0.0F,
                1.0F, 0.5F, true
        );
        Render2D.drawRound(context, px, py, pickW, pickH, 6.0F * scale, color(UI_POPOVER_BG, alpha));
        Render2D.drawRoundOutline(context, px, py, pickW, pickH, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));

        // Header: Title and Close button
        ModernFont.draw(context, activeColorPicker.getName(), px + 12.0F * scale, py + 9.0F * scale, 10.5F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_SEMIBOLD);

        float closeSize = 16.0F * scale;
        float closeX = px + pickW - closeSize - 8.0F * scale;
        float closeY = py + 7.0F * scale;
        boolean closeHover = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        Render2D.drawRound(context, closeX, closeY, closeSize, closeSize, 3.0F * scale, color(closeHover ? 0xFF28181A : 0xFF10141E, alpha));
        ModernFont.draw(context, "×", closeX + 5.0F * scale, closeY + 2.0F * scale, 11.0F * scale, color(closeHover ? 0xFFEF4444 : UI_TEXT_SEC, alpha), ModernFont.Type.INTER_SEMIBOLD);

        // 1. SV Box (Saturation & Brightness)
        float svX = px + 10.0F * scale;
        float svY = py + 28.0F * scale;
        float svW = pickW - 20.0F * scale;
        float svH = 68.0F * scale;

        int baseHueCol = Color.HSBtoRGB(pickerHue, 1.0F, 1.0F);
        Render2D.drawRound(context, svX, svY, svW, svH, 4.0F * scale, color(baseHueCol, alpha));
        Render2D.drawGradientRoundLR(context, svX, svY, svW, svH, 4.0F * scale, color(0xFFFFFFFF, alpha), color(0x00FFFFFF, alpha));
        Render2D.drawGradientRoundTB(context, svX, svY, svW, svH, 4.0F * scale, color(0x00000000, alpha), color(0xFF000000, alpha));
        Render2D.drawRoundOutline(context, svX, svY, svW, svH, 4.0F * scale, 1.0F, color(UI_BORDER, alpha));

        // SV Knob
        float cursorX = svX + svW * pickerSat;
        float cursorY = svY + svH * (1.0F - pickerBri);
        Render2D.drawCircle(context, cursorX, cursorY, 3.5F * scale, color(0xFFFFFFFF, alpha));
        Render2D.drawCircle(context, cursorX, cursorY, 2.2F * scale, color(0xFF000000 | Color.HSBtoRGB(pickerHue, pickerSat, pickerBri), alpha));

        // 2. Hue Slider Bar
        float hueY = svY + svH + 8.0F * scale;
        float hueH = 6.0F * scale;
        drawHueBar(context, svX, hueY, svW, hueH, 3.0F * scale, alpha);

        float hueKnobX = svX + svW * pickerHue;
        float hueKnobY = hueY + hueH * 0.5F;
        Render2D.drawCircle(context, hueKnobX, hueKnobY, 3.5F * scale, color(0xFFFFFFFF, alpha));
        Render2D.drawCircle(context, hueKnobX, hueKnobY, 2.2F * scale, color(0xFF000000 | Color.HSBtoRGB(pickerHue, 1.0F, 1.0F), alpha));

        // 3. Alpha Slider Bar
        float alphaBarY = hueY + hueH + 8.0F * scale;
        float alphaBarH = 6.0F * scale;
        int rgbNoAlpha = Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF;

        Render2D.drawRound(context, svX, alphaBarY, svW, alphaBarH, 3.0F * scale, color(0xFF0A0D14, alpha));
        Render2D.drawGradientRoundLR(context, svX, alphaBarY, svW, alphaBarH, 3.0F * scale, color(rgbNoAlpha, 0.0F), color(0xFF000000 | rgbNoAlpha, alpha));
        Render2D.drawRoundOutline(context, svX, alphaBarY, svW, alphaBarH, 3.0F * scale, 1.0F, color(UI_BORDER, alpha));

        float alphaKnobX = svX + svW * pickerAlpha;
        float alphaKnobY = alphaBarY + alphaBarH * 0.5F;
        Render2D.drawCircle(context, alphaKnobX, alphaKnobY, 3.5F * scale, color(0xFFFFFFFF, alpha));
        Render2D.drawCircle(context, alphaKnobX, alphaKnobY, 2.2F * scale, color(activeColorPicker.get(), alpha));

        // 4. Preview, HEX Input, Pipette (Theme Sync), and Copy Button
        float ctrlY = alphaBarY + alphaBarH + 9.0F * scale;
        float ctrlH = 22.0F * scale;
        float prevW = 20.0F * scale;
        float pipetteW = 22.0F * scale;
        float copyW = 22.0F * scale;
        float hexW = svW - prevW - pipetteW - copyW - 12.0F * scale;

        // Preview Swatch
        Render2D.drawRound(context, svX, ctrlY, prevW, ctrlH, 4.0F * scale, color(0xFF0A0D14, alpha));
        Render2D.drawRound(context, svX, ctrlY, prevW, ctrlH, 4.0F * scale, color(activeColorPicker.get(), alpha));
        Render2D.drawRoundOutline(context, svX, ctrlY, prevW, ctrlH, 4.0F * scale, 1.0F, color(UI_BORDER, alpha));

        // Hex Box
        float hexX = svX + prevW + 4.0F * scale;
        boolean hexHover = inside(mouseX, mouseY, hexX, ctrlY, hexW, ctrlH);
        Render2D.drawRound(context, hexX, ctrlY, hexW, ctrlH, 4.0F * scale, color(0xFF0A0D14, alpha));
        Render2D.drawRoundOutline(context, hexX, ctrlY, hexW, ctrlH, 4.0F * scale, 1.0F, color(hexFocused ? getAccentColor() : (hexHover ? UI_TEXT_SEC : UI_BORDER), alpha));

        String hexDisplay = hexFocused ? ("#" + hexBuffer + (System.currentTimeMillis() % 1000L < 500L ? "|" : "")) : ("#" + hexBuffer);
        ModernFont.draw(context, hexDisplay, hexX + 4.0F * scale, ctrlY + 6.0F * scale, 8.5F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_SEMIBOLD);

        // Pipette (Theme Sync) Button with real Pipette icon
        float pipX = hexX + hexW + 4.0F * scale;
        boolean pipHover = inside(mouseX, mouseY, pipX, ctrlY, pipetteW, ctrlH);
        boolean isSynced = activeColorPicker.isSyncedWithTheme();
        Render2D.drawRound(context, pipX, ctrlY, pipetteW, ctrlH, 4.0F * scale, color(isSynced ? 0x244C6EF5 : (pipHover ? 0xFF141924 : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, pipX, ctrlY, pipetteW, ctrlH, 4.0F * scale, 1.0F, color(isSynced ? getAccentColor() : (pipHover ? 0x30FFFFFF : UI_BORDER), alpha));
        int pipCol = isSynced ? getAccentColor() : (pipHover ? UI_TEXT_MAIN : UI_TEXT_SEC);
        ModernClickGuiRenderer.drawTexture(context, ICON_PIPETTE, pipX + 4.5F * scale, ctrlY + 4.5F * scale, 13.0F * scale, 13.0F * scale, color(pipCol, alpha));

        // Copy Button with real Copy icon / Checkmark feedback icon
        float copyX = pipX + pipetteW + 4.0F * scale;
        boolean copyHover = inside(mouseX, mouseY, copyX, ctrlY, copyW, ctrlH);
        boolean copiedRecently = (System.currentTimeMillis() - copyFeedbackTime < 1200L);
        Render2D.drawRound(context, copyX, ctrlY, copyW, ctrlH, 4.0F * scale, color(copiedRecently ? 0xFF143022 : (copyHover ? 0xFF141924 : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, copyX, ctrlY, copyW, ctrlH, 4.0F * scale, 1.0F, color(copiedRecently ? 0xFF10B981 : (copyHover ? 0x30FFFFFF : UI_BORDER), alpha));
        int copyIconCol = copiedRecently ? 0xFF10B981 : (copyHover ? UI_TEXT_MAIN : UI_TEXT_SEC);
        Identifier copyIcon = copiedRecently ? ICON_CHECK : ICON_COPY;
        ModernClickGuiRenderer.drawTexture(context, copyIcon, copyX + 4.5F * scale, ctrlY + 4.5F * scale, 13.0F * scale, 13.0F * scale, color(copyIconCol, alpha));
    }

    private static void drawHueBar(DrawContext context, float x, float y, float w, float h, float r, float alpha) {
        Render2D.drawRound(context, x, y, w, h, r, color(0xFF0A0D14, alpha));
        ScissorStack.push(x, y, w, h);
        try {
            float step = 1.0F;
            for (float i = 0; i < w; i += step) {
                float hue = i / w;
                int rgb = Color.HSBtoRGB(hue, 1.0F, 1.0F);
                Render2D.drawRound(context, x + i, y, step + 0.5F, h, 0.0F, color(rgb, alpha));
            }
        } finally {
            ScissorStack.pop();
        }
        Render2D.drawRoundOutline(context, x, y, w, h, r, 1.0F, color(UI_BORDER, alpha));
    }

    private void openColorPickerFor(ColorSetting setting) {
        this.activeColorPicker = setting;
        int argb = setting.get();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerBri = hsb[2];
        this.pickerAlpha = a / 255.0F;
        this.hexBuffer = String.format(Locale.ROOT, "%08X", argb);
        this.hexFocused = false;
        this.draggingSV = false;
        this.draggingHue = false;
        this.draggingAlpha = false;
    }

    private void updateColorPickerColor() {
        if (activeColorPicker == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF;
        int a = Math.max(0, Math.min(255, Math.round(pickerAlpha * 255.0F)));
        int argb = (a << 24) | rgb;
        activeColorPicker.set(argb);
        activeColorPicker.setSyncedWithTheme(false);
        if (!hexFocused) {
            hexBuffer = String.format(Locale.ROOT, "%08X", argb);
        }
    }

    private void drawGlobalSettings(DrawContext context, float x, float y, float w, float scale, int mouseX, int mouseY, float alpha) {
        float h = 156.0F * scale;
        Render2D.drawShadow(context, x, y, w, h, 6.0F * scale, 14.0F * scale, color(0xCC000000, alpha));
        Color globalGlassTint = new Color(6, 8, 12, 245);
        BlurRenderer.drawLiquidGlass(
                context,
                x, y, w, h, 6.0F * scale,
                globalGlassTint, alpha,
                1.1F, 0.8F, 0.0F,
                1.0F, 0.5F, true
        );
        Render2D.drawRound(context, x, y, w, h, 6.0F * scale, color(UI_CARD_BG, alpha));
        Render2D.drawRoundOutline(context, x, y, w, h, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));

        boolean en = isEn();
        Menu menu = moduleManager != null ? moduleManager.getMenu() : null;

        float curY = y + 10.0F * scale;
        float rowH = 22.0F * scale;

        // 1. Menu Key
        ModernFont.draw(context, en ? "Menu Key" : "Клавиша меню", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        int menuKey = menu != null ? menu.getKeyBind() : GLFW.GLFW_KEY_RIGHT_SHIFT;
        String keyText = listeningMenuKey ? "[ ... ]" : (menuKey == GLFW.GLFW_KEY_RIGHT_SHIFT ? "RShift" : GLFW.glfwGetKeyName(menuKey, 0) != null ? GLFW.glfwGetKeyName(menuKey, 0).toUpperCase(Locale.ROOT) : "KEY_" + menuKey);
        float keyW = ModernFont.getWidth(keyText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 14.0F * scale;
        float keyX = x + w - keyW - 12.0F * scale;
        boolean keyHover = inside(mouseX, mouseY, keyX, curY, keyW, 16.0F * scale);
        Render2D.drawRound(context, keyX, curY, keyW, 16.0F * scale, 3.0F * scale, color(listeningMenuKey ? 0xFF141924 : (keyHover ? 0xFF10141E : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, keyX, curY, keyW, 16.0F * scale, 3.0F * scale, 1.0F, color(listeningMenuKey ? getAccentColor() : 0x18FFFFFF, alpha));
        ModernFont.draw(context, keyText, x + w - keyW - 4.0F * scale, curY + 3.5F * scale, 9.5F * scale, color(listeningMenuKey ? getAccentColor() : UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);

        curY += rowH + 2.0F * scale;

        // 2. Language (Floating Dropdown)
        ModernFont.draw(context, en ? "Language" : "Язык", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        String langText = en ? "English" : "Русский";
        float langW = ModernFont.getWidth(langText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
        float langX = x + w - langW - 12.0F * scale;
        boolean langOpen = "lang".equals(openGlobalDropdown);
        boolean langHover = inside(mouseX, mouseY, langX, curY, langW, 16.0F * scale);
        Render2D.drawRound(context, langX, curY, langW, 16.0F * scale, 3.0F * scale, color(langOpen ? 0xFF141924 : (langHover ? 0xFF10141E : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, langX, curY, langW, 16.0F * scale, 3.0F * scale, 1.0F, color(langOpen ? getAccentColor() : 0x18FFFFFF, alpha));
        ModernFont.draw(context, langText, langX + 6.0F * scale, curY + 3.5F * scale, 9.5F * scale, color(langOpen ? getAccentColor() : UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);
        drawChevronDown(context, langX + langW - 7.0F * scale, curY + 8.0F * scale, scale, color(langOpen ? getAccentColor() : UI_TEXT_MUTED, alpha), langOpen);

        curY += rowH + 2.0F * scale;

        // 3. Menu Scale (Floating Dropdown)
        ModernFont.draw(context, en ? "Menu Scale" : "Масштаб меню", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        String scaleText = menu != null ? menu.getScaleMode().split(" ")[0] : "100%";
        float scaleW = ModernFont.getWidth(scaleText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
        float scaleX = x + w - scaleW - 12.0F * scale;
        boolean scaleOpen = "scale".equals(openGlobalDropdown);
        boolean scaleHover = inside(mouseX, mouseY, scaleX, curY, scaleW, 16.0F * scale);
        Render2D.drawRound(context, scaleX, curY, scaleW, 16.0F * scale, 3.0F * scale, color(scaleOpen ? 0xFF141924 : (scaleHover ? 0xFF10141E : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, scaleX, curY, scaleW, 16.0F * scale, 3.0F * scale, 1.0F, color(scaleOpen ? getAccentColor() : 0x18FFFFFF, alpha));
        ModernFont.draw(context, scaleText, scaleX + 6.0F * scale, curY + 3.5F * scale, 9.5F * scale, color(scaleOpen ? getAccentColor() : UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);
        drawChevronDown(context, scaleX + scaleW - 7.0F * scale, curY + 8.0F * scale, scale, color(scaleOpen ? getAccentColor() : UI_TEXT_MUTED, alpha), scaleOpen);

        curY += rowH + 2.0F * scale;

        // 4. Accent Color / Theme Preset (Floating Dropdown + Custom Swatch)
        ModernFont.draw(context, en ? "Accent Color" : "Акцентный цвет", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        String presetText = menu != null ? menu.getThemePreset() : "Blue";
        float presetW = ModernFont.getWidth(presetText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
        float presetX = x + w - presetW - 12.0F * scale;
        boolean presetOpen = "preset".equals(openGlobalDropdown);
        boolean presetHover = inside(mouseX, mouseY, presetX, curY, presetW, 16.0F * scale);
        Render2D.drawRound(context, presetX, curY, presetW, 16.0F * scale, 3.0F * scale, color(presetOpen ? 0xFF141924 : (presetHover ? 0xFF10141E : 0xFF0A0D14), alpha));
        Render2D.drawRoundOutline(context, presetX, curY, presetW, 16.0F * scale, 3.0F * scale, 1.0F, color(presetOpen ? getAccentColor() : 0x18FFFFFF, alpha));
        ModernFont.draw(context, presetText, presetX + 6.0F * scale, curY + 3.5F * scale, 9.5F * scale, color(presetOpen ? getAccentColor() : getAccentColor(), alpha), ModernFont.Type.INTER_MEDIUM);
        drawChevronDown(context, presetX + presetW - 7.0F * scale, curY + 8.0F * scale, scale, color(presetOpen ? getAccentColor() : UI_TEXT_MUTED, alpha), presetOpen);

        // Custom Color Swatch button when preset is "Кастом"
        if (menu != null && "Кастом".equalsIgnoreCase(menu.getThemePreset())) {
            float swatchW = 16.0F * scale;
            float swatchH = 16.0F * scale;
            float swatchX = presetX - swatchW - 4.0F * scale;
            boolean swatchHover = inside(mouseX, mouseY, swatchX, curY, swatchW, swatchH);
            Render2D.drawRound(context, swatchX, curY, swatchW, swatchH, 3.0F * scale, color(menu.getColor1(), alpha));
            Render2D.drawRoundOutline(context, swatchX, curY, swatchW, swatchH, 3.0F * scale, 1.0F, color(swatchHover || activeColorPicker == customThemeColorSetting ? getAccentColor() : 0x25FFFFFF, alpha));
        }

        curY += rowH + 2.0F * scale;

        // 5. Descriptions toggle
        ModernFont.draw(context, en ? "Module Tooltips" : "Описания модулей", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        boolean desc = menu != null && menu.isShowDescriptions();
        float swW = 26.0F * scale;
        float swH = 14.0F * scale;
        float swX = x + w - swW - 12.0F * scale;
        Render2D.drawRound(context, swX, curY + 2.0F * scale, swW, swH, swH * 0.5F, desc ? color(UI_SWITCH_ON_TRACK, alpha) : color(UI_SWITCH_OFF_TRACK, alpha));
        float thDiam = swH - 4.0F * scale;
        float thX = desc ? swX + swW - thDiam - 2.0F * scale : swX + 2.0F * scale;
        Render2D.drawRound(context, thX, curY + 4.0F * scale, thDiam, thDiam, thDiam * 0.5F, color(desc ? UI_SWITCH_ON_THUMB : UI_SWITCH_OFF_THUMB, alpha));

        curY += rowH + 2.0F * scale;

        // 6. Auto-save preset
        ModernFont.draw(context, en ? "Auto-save Preset" : "Автосохранение пресета", x + 12.0F * scale, curY + 4.0F * scale, 10.0F * scale, color(UI_TEXT_MAIN, alpha), ModernFont.Type.INTER_MEDIUM);
        boolean autoSave = menu != null && menu.isAutoSavePreset();
        Render2D.drawRound(context, swX, curY + 2.0F * scale, swW, swH, swH * 0.5F, autoSave ? color(UI_SWITCH_ON_TRACK, alpha) : color(UI_SWITCH_OFF_TRACK, alpha));
        float thX2 = autoSave ? swX + swW - thDiam - 2.0F * scale : swX + 2.0F * scale;
        Render2D.drawRound(context, thX2, curY + 4.0F * scale, thDiam, thDiam, thDiam * 0.5F, color(autoSave ? UI_SWITCH_ON_THUMB : UI_SWITCH_OFF_THUMB, alpha));
    }

    private void drawManagementTab(DrawContext context, float x, float y, float w, float h, float scale, int mouseX, int mouseY, float alpha, float dt) {
        boolean en = isEn();
        if (selectedTab == Tab.PRESETS) {
            float colGap = 12.0F * scale;
            float colW = (w - colGap) * 0.5F;
            float col1X = x;
            float col2X = x + colW + colGap;

            // Col 1: Configs
            Render2D.drawRound(context, col1X, y, colW, 260.0F * scale, 6.0F * scale, color(UI_CARD_BG, alpha));
            Render2D.drawRoundOutline(context, col1X, y, colW, 260.0F * scale, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));
            ModernFont.draw(context, en ? "Configurations" : "Конфигурации", col1X + 14.0F * scale, y + 11.0F * scale, 11.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);

            // Col 2: Info
            Render2D.drawRound(context, col2X, y, colW, 160.0F * scale, 6.0F * scale, color(UI_CARD_BG, alpha));
            Render2D.drawRoundOutline(context, col2X, y, colW, 160.0F * scale, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));
            ModernFont.draw(context, en ? "Preset Management" : "Управление пресетами", col2X + 14.0F * scale, y + 11.0F * scale, 11.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);
            ModernFont.draw(context, en ? "Save your active modules and settings to configs." : "Сохраняйте и загружайте готовые наборы настроек.", col2X + 14.0F * scale, y + 36.0F * scale, 10.0F * scale, color(UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);

        } else if (selectedTab == Tab.AUTOBUY) {
            Render2D.drawRound(context, x, y, w, 280.0F * scale, 6.0F * scale, color(UI_CARD_BG, alpha));
            Render2D.drawRoundOutline(context, x, y, w, 280.0F * scale, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));
            ModernFont.draw(context, en ? "Auto Buy Configuration" : "Настройки авто покупки", x + 14.0F * scale, y + 11.0F * scale, 11.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);
            ModernFont.draw(context, en ? "Use dedicated screen for items filter." : "Используйте модуль Auto Buy для детальной настройки фильтров и цен.", x + 14.0F * scale, y + 36.0F * scale, 10.0F * scale, color(UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);

        } else if (selectedTab == Tab.ACCOUNTS) {
            Render2D.drawRound(context, x, y, w, 240.0F * scale, 6.0F * scale, color(UI_CARD_BG, alpha));
            Render2D.drawRoundOutline(context, x, y, w, 240.0F * scale, 6.0F * scale, 1.0F, color(UI_CARD_BORDER, alpha));
            ModernFont.draw(context, en ? "Bot Accounts & MultiBot" : "Мультибот и аккаунты", x + 14.0F * scale, y + 11.0F * scale, 11.5F * scale, color(getAccentColor(), alpha), ModernFont.Type.INTER_SEMIBOLD);
            ModernFont.draw(context, en ? "Active sessions: " + (FluxVisualsClient.MULTI_BOT_MANAGER != null ? FluxVisualsClient.MULTI_BOT_MANAGER.getSessions().size() : 0) : "Активных сессий: " + (FluxVisualsClient.MULTI_BOT_MANAGER != null ? FluxVisualsClient.MULTI_BOT_MANAGER.getSessions().size() : 0), x + 14.0F * scale, y + 36.0F * scale, 10.0F * scale, color(UI_TEXT_SEC, alpha), ModernFont.Type.INTER_MEDIUM);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getGuiScale();

        // 0. Fullscreen Key Listen Modal clicks
        if (listeningFullscreenKey && activeBindModule != null) {
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float cardW = 320.0F * scale;
            float cardH = 150.0F * scale;
            float cx = (screenW - cardW) * 0.5F;
            float cy = (screenH - cardH) * 0.5F;

            if (!inside((float) mouseX, (float) mouseY, cx, cy, cardW, cardH)) {
                listeningFullscreenKey = false;
            }
            return true;
        }

        // 1. Color Picker clicks (highest priority)
        if (activeColorPicker != null) {
            float pickW = 196.0F * scale;
            float pickH = 164.0F * scale;
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

            float px, py;
            if (contextMenuModule != null) {
                px = contextMenuX + 228.0F * scale;
                if (px + pickW > screenW - 10.0F) {
                    px = Math.max(10.0F, contextMenuX - pickW - 8.0F * scale);
                }
                py = Math.max(10.0F, Math.min(contextMenuY, screenH - pickH - 10.0F));
            } else {
                px = (screenW - pickW) * 0.5F;
                py = (screenH - pickH) * 0.5F;
            }

            if (inside((float) mouseX, (float) mouseY, px, py, pickW, pickH)) {
                // Close button
                float closeSize = 16.0F * scale;
                float closeX = px + pickW - closeSize - 8.0F * scale;
                float closeY = py + 7.0F * scale;
                if (inside((float) mouseX, (float) mouseY, closeX, closeY, closeSize, closeSize)) {
                    activeColorPicker = null;
                    return true;
                }

                // SV Box
                float svX = px + 10.0F * scale;
                float svY = py + 28.0F * scale;
                float svW = pickW - 20.0F * scale;
                float svH = 68.0F * scale;
                if (inside((float) mouseX, (float) mouseY, svX, svY, svW, svH)) {
                    draggingSV = true;
                    pickerSat = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                    pickerBri = Math.max(0.0F, Math.min(1.0F, 1.0F - (float) ((mouseY - svY) / svH)));
                    updateColorPickerColor();
                    return true;
                }

                // Hue Slider
                float hueY = svY + svH + 8.0F * scale;
                float hueH = 6.0F * scale;
                if (inside((float) mouseX, (float) mouseY, svX, hueY - 2.0F * scale, svW, hueH + 4.0F * scale)) {
                    draggingHue = true;
                    pickerHue = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                    updateColorPickerColor();
                    return true;
                }

                // Alpha Slider
                float alphaBarY = hueY + hueH + 8.0F * scale;
                float alphaBarH = 6.0F * scale;
                if (inside((float) mouseX, (float) mouseY, svX, alphaBarY - 2.0F * scale, svW, alphaBarH + 4.0F * scale)) {
                    draggingAlpha = true;
                    pickerAlpha = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                    updateColorPickerColor();
                    return true;
                }

                // Hex Box
                float ctrlY = alphaBarY + alphaBarH + 9.0F * scale;
                float ctrlH = 22.0F * scale;
                float prevW = 20.0F * scale;
                float pipetteW = 22.0F * scale;
                float copyW = 22.0F * scale;
                float hexW = svW - prevW - pipetteW - copyW - 12.0F * scale;
                float hexX = svX + prevW + 4.0F * scale;

                if (inside((float) mouseX, (float) mouseY, hexX, ctrlY, hexW, ctrlH)) {
                    hexFocused = true;
                    return true;
                } else {
                    hexFocused = false;
                }

                // Pipette (Theme Sync) Button click
                float pipX = hexX + hexW + 4.0F * scale;
                if (inside((float) mouseX, (float) mouseY, pipX, ctrlY, pipetteW, ctrlH)) {
                    activeColorPicker.setSyncedWithTheme(true);
                    int themeRgb = moduleManager != null && moduleManager.getMenu() != null ? moduleManager.getMenu().getLiveColor(0.0F) : 0xFF5C7CFA;
                    int curAlpha = (activeColorPicker.get() >>> 24);
                    if (curAlpha == 0 && (activeColorPicker.get() & 0x00FFFFFF) == 0) curAlpha = 255;
                    activeColorPicker.set((curAlpha << 24) | (themeRgb & 0x00FFFFFF));
                    float[] hsb = Color.RGBtoHSB((themeRgb >> 16) & 0xFF, (themeRgb >> 8) & 0xFF, themeRgb & 0xFF, null);
                    pickerHue = hsb[0];
                    pickerSat = hsb[1];
                    pickerBri = hsb[2];
                    hexBuffer = String.format(Locale.ROOT, "%08X", activeColorPicker.get());
                    return true;
                }

                // Copy Button
                float copyX = pipX + pipetteW + 4.0F * scale;
                if (inside((float) mouseX, (float) mouseY, copyX, ctrlY, copyW, ctrlH)) {
                    MinecraftClient.getInstance().keyboard.setClipboard("#" + hexBuffer);
                    copyFeedbackTime = System.currentTimeMillis();
                    return true;
                }

                return true;
            } else {
                activeColorPicker = null;
            }
        }

        // 2. Keybind Popover clicks
        if (activeBindModule != null) {
            int boundKey = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(activeBindModule.getName(), GLFW.GLFW_KEY_UNKNOWN);
            boolean isBound = (boundKey != GLFW.GLFW_KEY_UNKNOWN && boundKey != 0);

            float popW = 216.0F * scale;
            float popH = isBound ? (154.0F * scale) : (124.0F * scale);
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

            float px = Math.max(10.0F, Math.min(bindPopoverX, screenW - popW - 10.0F));
            float py = Math.max(10.0F, Math.min(bindPopoverY, screenH - popH - 10.0F));

            if (inside((float) mouseX, (float) mouseY, px, py, popW, popH)) {
                // Close button
                float closeSize = 16.0F * scale;
                float closeX = px + popW - closeSize - 10.0F * scale;
                float closeY = py + 10.0F * scale;
                if (inside((float) mouseX, (float) mouseY, closeX, closeY, closeSize, closeSize)) {
                    activeBindModule = null;
                    return true;
                }

                // Key display card click -> Open Fullscreen Key Listen Modal
                float boxX = px + 12.0F * scale;
                float boxY = py + 34.0F * scale;
                float boxW = popW - 24.0F * scale;
                float boxH = 46.0F * scale;
                if (inside((float) mouseX, (float) mouseY, boxX, boxY, boxW, boxH)) {
                    listeningFullscreenKey = true;
                    return true;
                }

                // Mode segmented pills [ Toggle | Hold ]
                float segX = px + 12.0F * scale;
                float segY = boxY + boxH + 8.0F * scale;
                float segW = popW - 24.0F * scale;
                float segH = 24.0F * scale;
                float halfW = (segW - 4.0F * scale) * 0.5F;
                float btn1X = segX + 2.0F * scale;
                float btn2X = btn1X + halfW;
                float btnH = segH - 4.0F * scale;
                float btnY = segY + 2.0F * scale;

                if (inside((float) mouseX, (float) mouseY, btn1X, btnY, halfW, btnH)) {
                    bindHoldMode = false;
                    ModernClickGuiRenderer.MODULE_BIND_MODES.put(activeBindModule.getName(), false);
                    return true;
                }
                if (inside((float) mouseX, (float) mouseY, btn2X, btnY, halfW, btnH)) {
                    bindHoldMode = true;
                    ModernClickGuiRenderer.MODULE_BIND_MODES.put(activeBindModule.getName(), true);
                    return true;
                }

                // Unbind button click (if bound)
                if (isBound) {
                    float unbindX = px + 12.0F * scale;
                    float unbindY = segY + segH + 7.0F * scale;
                    float unbindW = popW - 24.0F * scale;
                    float unbindH = 20.0F * scale;
                    if (inside((float) mouseX, (float) mouseY, unbindX, unbindY, unbindW, unbindH)) {
                        ModernClickGuiRenderer.MODULE_BINDS.remove(activeBindModule.getName());
                        return true;
                    }
                }

                return true;
            } else {
                activeBindModule = null;
            }
        }

        // 3. Floating Dropdown clicks (check repeat click on trigger to toggle close!)
        if (openDropdownMode != null || openDropdownMulti != null || openGlobalDropdown != null) {
            if (dropdownTriggerW > 0.0F && inside((float) mouseX, (float) mouseY, dropdownTriggerX, dropdownTriggerY, dropdownTriggerW, dropdownTriggerH)) {
                openDropdownMode = null;
                openDropdownMulti = null;
                openGlobalDropdown = null;
                dropdownTriggerW = 0.0F;
                return true;
            }

            List<String> options = null;
            if (openDropdownMode != null) options = openDropdownMode.getModes();
            else if (openDropdownMulti != null) options = openDropdownMulti.getAllOptions();
            else if ("lang".equals(openGlobalDropdown)) options = List.of("Русский", "English");
            else if ("scale".equals(openGlobalDropdown)) options = Menu.SCALE_MODES;
            else if ("preset".equals(openGlobalDropdown)) options = Menu.THEME_PRESETS;

            if (options != null && !options.isEmpty()) {
                float maxOptW = 0.0F;
                float extraPadding = (openDropdownMulti != null) ? 38.0F * scale : 24.0F * scale;
                for (String opt : options) {
                    float w = ModernFont.getWidth(opt, 9.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
                    if (w > maxOptW) maxOptW = w;
                }
                float menuW = Math.max(dropdownW, Math.max(105.0F * scale, maxOptW + extraPadding));

                float itemH = 21.0F * scale;
                float menuH = options.size() * itemH + 6.0F * scale;
                float menuX = (dropdownTriggerW > 0.0F) ? (dropdownTriggerX + dropdownTriggerW - menuW) : dropdownX;
                float menuY = dropdownY;

                int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
                int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

                if (menuY + menuH > screenH - 10.0F) {
                    menuY = Math.max(10.0F, dropdownY - menuH - 24.0F * scale);
                }
                if (menuX + menuW > screenW - 10.0F) {
                    menuX = Math.max(10.0F, screenW - menuW - 10.0F);
                }
                if (menuX < 10.0F) {
                    menuX = 10.0F;
                }

                if (inside((float) mouseX, (float) mouseY, menuX, menuY, menuW, menuH)) {
                    float optY = menuY + 3.0F * scale;
                    for (String opt : options) {
                        if (inside((float) mouseX, (float) mouseY, menuX + 2.0F * scale, optY, menuW - 4.0F * scale, itemH)) {
                            if (openDropdownMode != null) {
                                openDropdownMode.set(opt);
                                openDropdownMode = null;
                            } else if (openDropdownMulti != null) {
                                openDropdownMulti.toggleOption(opt);
                            } else if ("lang".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                                moduleManager.getMenu().setLanguage(opt);
                                openGlobalDropdown = null;
                            } else if ("scale".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                                moduleManager.getMenu().setScaleMode(opt);
                                openGlobalDropdown = null;
                            } else if ("preset".equals(openGlobalDropdown) && moduleManager != null && moduleManager.getMenu() != null) {
                                moduleManager.getMenu().setThemePreset(opt);
                                openGlobalDropdown = null;
                            }
                            return true;
                        }
                        optY += itemH;
                    }
                    return true;
                }
            }
            openDropdownMode = null;
            openDropdownMulti = null;
            openGlobalDropdown = null;
            dropdownTriggerW = 0.0F;
            return true;
        }

        // 4. Module Context Menu clicks
        if (contextMenuModule != null) {
            List<Setting<?>> settings = moduleSettings.get(contextMenuModule);
            float cardW = 220.0F * scale;
            float headerH = 34.0F * scale;

            float contentH = 0.0F;
            if (settings != null) {
                for (Setting<?> s : settings) {
                    if (!s.isVisible()) continue;
                    if (s instanceof BooleanSetting) contentH += 26.0F * scale;
                    else if (s instanceof SliderSetting) contentH += 34.0F * scale;
                    else if (s instanceof ModeSetting) contentH += 26.0F * scale;
                    else if (s instanceof MultiModeSetting) contentH += 26.0F * scale;
                    else if (s instanceof ColorSetting) contentH += 26.0F * scale;
                    else if (s instanceof StringSetting) contentH += 38.0F * scale;
                    else contentH += 24.0F * scale;
                }
            }

            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

            float maxVisibleSettingsH = Math.min(contentH, Math.min(300.0F * scale, screenH - 120.0F * scale));
            float visibleSettingsH = maxVisibleSettingsH;
            float cardH = headerH + visibleSettingsH + 10.0F * scale;

            float cmX = Math.max(10.0F, Math.min(contextMenuX, screenW - cardW - 10.0F));
            float cmY = Math.max(10.0F, Math.min(contextMenuY, screenH - cardH - 10.0F));

            if (inside((float) mouseX, (float) mouseY, cmX, cmY, cardW, cardH)) {
                // Settings list clicks
                float listAreaY = cmY + headerH + 4.0F * scale;
                if (settings != null && inside((float) mouseX, (float) mouseY, cmX, listAreaY, cardW, visibleSettingsH)) {
                    float curY = listAreaY + contextMenuScrollY;

                    for (Setting<?> s : settings) {
                        if (!s.isVisible()) continue;

                        if (s instanceof BooleanSetting b) {
                            float rowH = 26.0F * scale;
                            if (inside((float) mouseX, (float) mouseY, cmX, curY, cardW, rowH)) {
                                b.set(!b.get());
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof SliderSetting sl) {
                            float rowH = 34.0F * scale;
                            float barX = cmX + 12.0F * scale;
                            float barY = curY + 18.0F * scale;
                            float barW = cardW - 24.0F * scale;
                            float barH = 10.0F * scale;

                            if (inside((float) mouseX, (float) mouseY, barX, barY - 4.0F * scale, barW, barH)) {
                                draggingSlider = sl;
                                sl.setNormalized((float) ((mouseX - barX) / barW));
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof ModeSetting m) {
                            float rowH = 26.0F * scale;
                            String val = m.get();
                            float textW = ModernFont.getWidth(val, 9.0F * scale, ModernFont.Type.INTER_MEDIUM);
                            float chevronSpace = 16.0F * scale;
                            float valW = Math.max(64.0F * scale, textW + chevronSpace + 14.0F * scale);
                            float valX = cmX + cardW - valW - 12.0F * scale;
                            float valY = curY + 3.0F * scale;
                            float valH = 19.0F * scale;

                            if (inside((float) mouseX, (float) mouseY, valX, valY, valW, valH)) {
                                if (openDropdownMode == m) {
                                    openDropdownMode = null; // Toggle closed on repeat click!
                                    return true;
                                }
                                openDropdownMode = m;
                                openDropdownMulti = null;
                                openGlobalDropdown = null;
                                dropdownX = valX;
                                dropdownY = curY + 24.0F * scale;
                                dropdownW = Math.max(valW, 100.0F * scale);
                                dropdownTriggerX = valX;
                                dropdownTriggerY = valY;
                                dropdownTriggerW = valW;
                                dropdownTriggerH = valH;
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof MultiModeSetting mm) {
                            float rowH = 26.0F * scale;
                            String val = mm.getDisplaySummary(isEn());
                            float textW = ModernFont.getWidth(val, 9.0F * scale, ModernFont.Type.INTER_MEDIUM);
                            float chevronSpace = 16.0F * scale;
                            float valW = Math.max(64.0F * scale, textW + chevronSpace + 14.0F * scale);
                            float valX = cmX + cardW - valW - 12.0F * scale;
                            float valY = curY + 3.0F * scale;
                            float valH = 19.0F * scale;

                            if (inside((float) mouseX, (float) mouseY, valX, valY, valW, valH)) {
                                if (openDropdownMulti == mm) {
                                    openDropdownMulti = null; // Toggle closed on repeat click!
                                    return true;
                                }
                                openDropdownMulti = mm;
                                openDropdownMode = null;
                                openGlobalDropdown = null;
                                dropdownX = valX;
                                dropdownY = curY + 24.0F * scale;
                                dropdownW = Math.max(valW, 100.0F * scale);
                                dropdownTriggerX = valX;
                                dropdownTriggerY = valY;
                                dropdownTriggerW = valW;
                                dropdownTriggerH = valH;
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof KeybindSetting kb) {
                            float rowH = 26.0F * scale;
                            if (inside((float) mouseX, (float) mouseY, cmX, curY, cardW, rowH)) {
                                listeningSettingBind = (listeningSettingBind == kb) ? null : kb;
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof ColorSetting c) {
                            float rowH = 26.0F * scale;
                            float dotW = 26.0F * scale;
                            float dotX = cmX + cardW - dotW - 12.0F * scale;

                            if (inside((float) mouseX, (float) mouseY, dotX, curY + 2.0F * scale, dotW, 18.0F * scale)) {
                                openColorPickerFor(c);
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof StringSetting str) {
                            float rowH = 38.0F * scale;
                            float boxX = cmX + 12.0F * scale;
                            float boxY = curY + 16.0F * scale;
                            float boxW = cardW - 24.0F * scale;
                            float boxH = 18.0F * scale;

                            if (inside((float) mouseX, (float) mouseY, boxX, boxY, boxW, boxH)) {
                                activeStringSetting = str;
                                activeStringDraft = str.get();
                                return true;
                            }
                            curY += rowH;
                        } else if (s instanceof ActionSetting act) {
                            float rowH = 28.0F * scale;
                            float btnX = cmX + 12.0F * scale;
                            float btnY = curY + 2.0F * scale;
                            float btnW = cardW - 24.0F * scale;
                            float btnH = 22.0F * scale;
                            if (inside((float) mouseX, (float) mouseY, btnX, btnY, btnW, btnH)) {
                                act.run();
                                return true;
                            }
                            curY += rowH;
                        }
                    }
                }
                return true;
            } else {
                contextMenuModule = null;
                activeBindModule = null;
                draggingSlider = null;
                activeStringSetting = null;
                listeningSettingBind = null;
            }
        }

        float w = 780.0F * scale;
        float h = 480.0F * scale;
        float x = (MinecraftClient.getInstance().getWindow().getScaledWidth() - w) * 0.5F;
        float y = (MinecraftClient.getInstance().getWindow().getScaledHeight() - h) * 0.5F;
        float sidebarW = 188.0F * scale;

        // 5. Global settings clicks
        if (globalSettingsOpen) {
            float gW = 250.0F * scale;
            float gH = 156.0F * scale;
            float gX = x + w - gW - 16.0F * scale;
            float gY = y + 16.0F * scale + 32.0F * scale + 8.0F * scale;

            if (inside((float) mouseX, (float) mouseY, gX, gY, gW, gH)) {
                Menu menu = moduleManager != null ? moduleManager.getMenu() : null;
                boolean en = isEn();
                float curY = gY + 10.0F * scale;
                float rowH = 22.0F * scale;

                // 1. Menu key
                int menuKey = menu != null ? menu.getKeyBind() : GLFW.GLFW_KEY_RIGHT_SHIFT;
                String keyText = listeningMenuKey ? "[ ... ]" : (menuKey == GLFW.GLFW_KEY_RIGHT_SHIFT ? "RShift" : GLFW.glfwGetKeyName(menuKey, 0) != null ? GLFW.glfwGetKeyName(menuKey, 0).toUpperCase(Locale.ROOT) : "KEY_" + menuKey);
                float keyW = ModernFont.getWidth(keyText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 14.0F * scale;
                if (inside((float) mouseX, (float) mouseY, gX + gW - keyW - 12.0F * scale, curY, keyW, 16.0F * scale)) {
                    listeningMenuKey = !listeningMenuKey;
                    return true;
                }
                curY += rowH + 2.0F * scale;

                // 2. Language Dropdown
                String langText = en ? "English" : "Русский";
                float langW = ModernFont.getWidth(langText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
                float langX = gX + gW - langW - 12.0F * scale;
                if (inside((float) mouseX, (float) mouseY, langX, curY, langW, 16.0F * scale)) {
                    if ("lang".equals(openGlobalDropdown)) {
                        openGlobalDropdown = null;
                        return true;
                    }
                    openGlobalDropdown = "lang";
                    openDropdownMode = null;
                    openDropdownMulti = null;
                    dropdownX = langX;
                    dropdownY = curY + 20.0F * scale;
                    dropdownW = Math.max(langW, 90.0F * scale);
                    dropdownTriggerX = langX;
                    dropdownTriggerY = curY;
                    dropdownTriggerW = langW;
                    dropdownTriggerH = 16.0F * scale;
                    return true;
                }
                curY += rowH + 2.0F * scale;

                // 3. Menu scale Dropdown
                String scaleText = menu != null ? menu.getScaleMode().split(" ")[0] : "100%";
                float scaleW = ModernFont.getWidth(scaleText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
                float scaleX = gX + gW - scaleW - 12.0F * scale;
                if (inside((float) mouseX, (float) mouseY, scaleX, curY, scaleW, 16.0F * scale)) {
                    if ("scale".equals(openGlobalDropdown)) {
                        openGlobalDropdown = null;
                        return true;
                    }
                    openGlobalDropdown = "scale";
                    openDropdownMode = null;
                    openDropdownMulti = null;
                    dropdownX = scaleX;
                    dropdownY = curY + 20.0F * scale;
                    dropdownW = Math.max(scaleW, 90.0F * scale);
                    dropdownTriggerX = scaleX;
                    dropdownTriggerY = curY;
                    dropdownTriggerW = scaleW;
                    dropdownTriggerH = 16.0F * scale;
                    return true;
                }
                curY += rowH + 2.0F * scale;

                // 4. Accent Color / Preset Dropdown + Custom Swatch
                String presetText = menu != null ? menu.getThemePreset() : "Blue";
                float presetW = ModernFont.getWidth(presetText, 9.5F * scale, ModernFont.Type.INTER_MEDIUM) + 20.0F * scale;
                float presetX = gX + gW - presetW - 12.0F * scale;
                if (menu != null && "Кастом".equalsIgnoreCase(menu.getThemePreset())) {
                    float swatchW = 16.0F * scale;
                    float swatchH = 16.0F * scale;
                    float swatchX = presetX - swatchW - 4.0F * scale;
                    if (inside((float) mouseX, (float) mouseY, swatchX, curY, swatchW, swatchH)) {
                        openColorPickerFor(getCustomThemeColorSetting());
                        return true;
                    }
                }
                if (inside((float) mouseX, (float) mouseY, presetX, curY, presetW, 16.0F * scale)) {
                    if ("preset".equals(openGlobalDropdown)) {
                        openGlobalDropdown = null;
                        return true;
                    }
                    openGlobalDropdown = "preset";
                    openDropdownMode = null;
                    openDropdownMulti = null;
                    dropdownX = presetX;
                    dropdownY = curY + 20.0F * scale;
                    dropdownW = Math.max(presetW, 90.0F * scale);
                    dropdownTriggerX = presetX;
                    dropdownTriggerY = curY;
                    dropdownTriggerW = presetW;
                    dropdownTriggerH = 16.0F * scale;
                    return true;
                }
                curY += rowH + 2.0F * scale;

                // 5. Descriptions toggle
                float swW = 26.0F * scale;
                float swH = 14.0F * scale;
                float swX = gX + gW - swW - 12.0F * scale;
                if (inside((float) mouseX, (float) mouseY, swX, curY, swW, swH + 4.0F * scale)) {
                    if (menu != null) {
                        menu.setShowDescriptions(!menu.isShowDescriptions());
                    }
                    return true;
                }
                curY += rowH + 2.0F * scale;

                // 6. Auto-save preset toggle
                if (inside((float) mouseX, (float) mouseY, swX, curY, swW, swH + 4.0F * scale)) {
                    if (menu != null) {
                        menu.setAutoSavePreset(!menu.isAutoSavePreset());
                    }
                    return true;
                }
                return true;
            } else {
                globalSettingsOpen = false;
                listeningMenuKey = false;
                openGlobalDropdown = null;
            }
        }

        // 6. Sidebar clicks
        if (inside((float) mouseX, (float) mouseY, x, y, sidebarW, h)) {
            float catY = y + 74.0F * scale + 14.0F * scale;
            float itemH = 32.0F * scale;
            float itemW = sidebarW - 32.0F * scale;
            float itemX = x + 16.0F * scale;

            for (Tab tab : Tab.values()) {
                if (tab == Tab.PRESETS) {
                    catY += 10.0F * scale + 14.0F * scale;
                }
                if (inside((float) mouseX, (float) mouseY, itemX, catY, itemW, itemH)) {
                    if (selectedTab != tab) {
                        previousTab = selectedTab;
                        selectedTab = tab;
                        savedSelectedTab = tab;
                        tabTransition = 0.0F; // Smooth cross-fade animation!
                        targetScrollY = 0.0F;
                        scrollY = 0.0F;
                        savedScrollY = 0.0F;
                        savedTargetScrollY = 0.0F;
                    }
                    return true;
                }
                catY += itemH + 3.0F * scale;
            }
            return true;
        }

        // 7. Top bar hamburger button click
        float contentX = x + sidebarW + 16.0F * scale;
        float contentW = w - sidebarW - 32.0F * scale;
        float topBarY = y + 16.0F * scale;
        float topBarH = 32.0F * scale;
        float btnX = contentX + contentW - topBarH;

        if (inside((float) mouseX, (float) mouseY, btnX, topBarY, topBarH, topBarH)) {
            globalSettingsOpen = !globalSettingsOpen;
            return true;
        }

        // 8. Search bar click & clear
        if (!search.isEmpty()) {
            float clrSize = 14.0F * scale;
            float clrX = contentX + (contentW - topBarH - 8.0F * scale) - clrSize - 8.0F * scale;
            float clrY = topBarY + (topBarH - clrSize) * 0.5F;
            if (inside((float) mouseX, (float) mouseY, clrX, clrY, clrSize, clrSize)) {
                search = "";
                savedSearch = "";
                return true;
            }
        }

        if (inside((float) mouseX, (float) mouseY, contentX, topBarY, contentW - topBarH - 8.0F * scale, topBarH)) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // 9. Module columns clicks
        if (!selectedTab.isManagement || !search.isEmpty()) {
            float listY = topBarY + topBarH + 12.0F * scale;
            float colGap = 12.0F * scale;
            float colW = (contentW - colGap) * 0.5F;
            float col1X = contentX;
            float col2X = contentX + colW + colGap;

            boolean handled = handleColumnClick(col1X, listY + scrollY, colW, scale, mouseX, mouseY, button, true)
                    || handleColumnClick(col2X, listY + scrollY, colW, scale, mouseX, mouseY, button, false);
            if (handled) return true;
        }

        return false;
    }

    private boolean handleColumnClick(float colX, float startY, float colW, float scale, double mouseX, double mouseY, int mouseBtn, boolean isCol1) {
        List<SectionDef> sections = !search.isEmpty() ? getAllSections(isCol1) : getSectionsForTab(selectedTab, isCol1);
        float curY = startY;

        for (SectionDef sec : sections) {
            boolean collapsed = collapsedSections.getOrDefault(sec.titleRu, false);
            float currentExpand = search.isEmpty() ? sectionExpandAnims.getOrDefault(sec.titleRu, collapsed ? 0.0F : 1.0F) : 1.0F;

            List<String> visibleMods = new ArrayList<>();
            for (String raw : sec.moduleNames) {
                if (findModuleByName(raw) == null) continue;
                String disp = getModuleDisplayName(raw);
                if (search.isEmpty() || disp.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                    visibleMods.add(raw);
                }
            }
            if (visibleMods.isEmpty() && !search.isEmpty()) continue;

            float headerH = 34.0F * scale;
            float rowH = 32.0F * scale;
            float fullContentH = visibleMods.size() * rowH + 4.0F * scale;
            float cardH = headerH + fullContentH * currentExpand;

            // Header click -> collapse / expand with smooth animation (only when not searching)
            if (search.isEmpty() && inside((float) mouseX, (float) mouseY, colX, curY, colW, headerH)) {
                collapsedSections.put(sec.titleRu, !collapsed);
                return true;
            }

            // Modules clicks
            if (currentExpand > 0.5F || !search.isEmpty()) {
                float rowY = curY + headerH;
                for (String raw : visibleMods) {
                    Module mod = findModuleByName(raw);
                    boolean hasSettings = mod != null && moduleSettings.containsKey(mod) && !moduleSettings.get(mod).isEmpty();

                    if (inside((float) mouseX, (float) mouseY, colX, rowY, colW, rowH)) {
                        float ctrlRight = colX + colW - 12.0F * scale;
                        float swW = 28.0F * scale;
                        float swH = 15.0F * scale;
                        float swX = ctrlRight - swW;
                        
                        float nextLeft = swX - 6.0F * scale;
                        float dotsW = 18.0F * scale;
                        float dotsX = nextLeft - dotsW;
                        nextLeft = dotsX - 6.0F * scale;

                        float keyBtnW = 18.0F * scale;
                        float keyIconX = nextLeft - keyBtnW;

                        // Click Keyboard Icon -> open keybind modal popover
                        if (mod != null && inside((float) mouseX, (float) mouseY, keyIconX, rowY, keyBtnW, rowH)) {
                            if (activeBindModule == mod) {
                                activeBindModule = null;
                            } else {
                                activeBindModule = mod;
                                bindPopoverX = (float) mouseX;
                                bindPopoverY = (float) mouseY + 10.0F * scale;
                                bindHoldMode = ModernClickGuiRenderer.MODULE_BIND_MODES.getOrDefault(mod.getName(), false);
                                contextMenuModule = null;
                            }
                            return true;
                        }

                        // Click 3 dots or right-click -> open settings context menu (if settings exist)
                        if (inside((float) mouseX, (float) mouseY, dotsX, rowY, dotsW, rowH) || mouseBtn == 1) {
                            if (mod != null) {
                                if (hasSettings) {
                                    contextMenuModule = (contextMenuModule == mod) ? null : mod;
                                    contextMenuX = (float) mouseX;
                                    contextMenuY = (float) mouseY;
                                    contextMenuScrollY = 0.0F;
                                    activeColorPicker = null;
                                    activeBindModule = null;
                                    openDropdownMode = null;
                                    openDropdownMulti = null;
                                } else {
                                    // No settings: do NOT open menu, show tooltip immediately
                                    contextMenuModule = null;
                                    hoveredModule = mod;
                                    hoverStartTime = System.currentTimeMillis() - 200L;
                                }
                            }
                            return true;
                        }

                        // Click toggle switch or row -> toggle module
                        if (mod != null) {
                            mod.toggle();
                        } else {
                            boolean st = dummyToggleStates.getOrDefault(raw, false);
                            dummyToggleStates.put(raw, !st);
                        }
                        return true;
                    }
                    rowY += rowH;
                }
            }
            curY += cardH + 10.0F * scale;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
        draggingAlpha = false;
        draggingSlider = null;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float scale = getGuiScale();

        // 1. Color Picker dragging
        if (activeColorPicker != null) {
            float pickW = 196.0F * scale;
            float pickH = 164.0F * scale;
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

            float px, py;
            if (contextMenuModule != null) {
                px = contextMenuX + 228.0F * scale;
                if (px + pickW > screenW - 10.0F) {
                    px = Math.max(10.0F, contextMenuX - pickW - 8.0F * scale);
                }
                py = Math.max(10.0F, Math.min(contextMenuY, screenH - pickH - 10.0F));
            } else {
                px = (screenW - pickW) * 0.5F;
                py = (screenH - pickH) * 0.5F;
            }

            float svX = px + 10.0F * scale;
            float svY = py + 28.0F * scale;
            float svW = pickW - 20.0F * scale;
            float svH = 68.0F * scale;

            if (draggingSV) {
                pickerSat = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                pickerBri = Math.max(0.0F, Math.min(1.0F, 1.0F - (float) ((mouseY - svY) / svH)));
                updateColorPickerColor();
                return true;
            }

            float hueY = svY + svH + 8.0F * scale;
            if (draggingHue) {
                pickerHue = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                updateColorPickerColor();
                return true;
            }

            float alphaBarY = hueY + 6.0F * scale + 8.0F * scale;
            if (draggingAlpha) {
                pickerAlpha = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - svX) / svW)));
                updateColorPickerColor();
                return true;
            }
        }

        // 2. Slider dragging
        if (draggingSlider != null && contextMenuModule != null) {
            float cardW = 220.0F * scale;
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float cmX = Math.max(10.0F, Math.min(contextMenuX, screenW - cardW - 10.0F));
            float barX = cmX + 12.0F * scale;
            float barW = cardW - 24.0F * scale;

            draggingSlider.setNormalized((float) ((mouseX - barX) / barW));
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float scale = getGuiScale();
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        // 1. Scroll inside Context Menu if cursor is hovering over it
        if (contextMenuModule != null) {
            float cardW = 220.0F * scale;
            float headerH = 34.0F * scale;
            List<Setting<?>> settings = moduleSettings.get(contextMenuModule);

            float contentH = 0.0F;
            if (settings != null) {
                for (Setting<?> s : settings) {
                    if (!s.isVisible()) continue;
                    if (s instanceof BooleanSetting) contentH += 26.0F * scale;
                    else if (s instanceof SliderSetting) contentH += 34.0F * scale;
                    else if (s instanceof ModeSetting) contentH += 26.0F * scale;
                    else if (s instanceof MultiModeSetting) contentH += 26.0F * scale;
                    else if (s instanceof KeybindSetting) contentH += 26.0F * scale;
                    else if (s instanceof ColorSetting) contentH += 26.0F * scale;
                    else if (s instanceof StringSetting) contentH += 38.0F * scale;
                    else if (s instanceof ActionSetting) contentH += 28.0F * scale;
                    else contentH += 24.0F * scale;
                }
            }

            float maxVisibleSettingsH = Math.min(contentH, Math.min(300.0F * scale, screenH - 120.0F * scale));
            float visibleSettingsH = maxVisibleSettingsH;
            float cardH = headerH + visibleSettingsH + 10.0F * scale;

            float cmX = Math.max(10.0F, Math.min(contextMenuX, screenW - cardW - 10.0F));
            float cmY = Math.max(10.0F, Math.min(contextMenuY, screenH - cardH - 10.0F));

            if (inside((float) mouseX, (float) mouseY, cmX, cmY, cardW, cardH)) {
                if (maxContextMenuScrollY > 0.0F) {
                    contextMenuScrollY = Math.min(0.0F, Math.max(-maxContextMenuScrollY, (float) (contextMenuScrollY + amount * 24.0F * scale)));
                }
                return true;
            }
        }

        // If hovering over any overlay/popup, consume scroll so background doesn't scroll underneath
        if (isMouseOverAnyOverlay((float) mouseX, (float) mouseY, scale, screenW, screenH)) {
            return true;
        }

        // 2. Scroll main content
        targetScrollY = Math.min(0.0F, Math.max(-maxScrollY, (float) (targetScrollY + amount * 30.0F * scale)));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        // Keybind setting inside module settings
        if (listeningSettingBind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                listeningSettingBind.set(0);
            } else {
                listeningSettingBind.set(keyCode);
            }
            listeningSettingBind = null;
            return true;
        }

        // Keybind capture in the fullscreen modal
        if (listeningFullscreenKey && activeBindModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningFullscreenKey = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ModernClickGuiRenderer.MODULE_BINDS.remove(activeBindModule.getName());
            } else {
                ModernClickGuiRenderer.MODULE_BINDS.put(activeBindModule.getName(), keyCode);
                ModernClickGuiRenderer.MODULE_BIND_MODES.put(activeBindModule.getName(), bindHoldMode);
            }
            listeningFullscreenKey = false;
            return true;
        }

        // Keybind popover escape
        if (activeBindModule != null && !listeningFullscreenKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeBindModule = null;
                return true;
            }
        }

        // Listening for Menu Key
        if (listeningMenuKey) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE && moduleManager != null && moduleManager.getMenu() != null) {
                moduleManager.getMenu().setKeyBind(keyCode);
            }
            listeningMenuKey = false;
            return true;
        }

        // String setting editing
        if (activeStringSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                activeStringSetting.set(activeStringDraft);
                activeStringSetting = null;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!activeStringDraft.isEmpty()) {
                    activeStringDraft = activeStringDraft.substring(0, activeStringDraft.length() - 1);
                }
                return true;
            }
        }

        // Hex editing in color picker
        if (hexFocused && activeColorPicker != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!hexBuffer.isEmpty()) {
                    hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                applyHexBuffer();
                return true;
            }
        }

        // Search editing
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                    savedSearch = search;
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                savedSearch = search;
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(char chr) {
        if (activeStringSetting != null) {
            activeStringDraft += chr;
            return true;
        }

        if (hexFocused && activeColorPicker != null) {
            String cStr = String.valueOf(chr).toUpperCase(Locale.ROOT);
            if ("0123456789ABCDEF".contains(cStr) && hexBuffer.length() < 8) {
                hexBuffer += cStr;
                applyHexBuffer();
            }
            return true;
        }

        if (searchFocused) {
            search += chr;
            savedSearch = search;
            return true;
        }

        return false;
    }

    private void applyHexBuffer() {
        if (activeColorPicker == null) return;
        try {
            long val = Long.parseLong(hexBuffer, 16);
            int argb = (int) (hexBuffer.length() <= 6 ? (0xFF000000L | val) : val);
            activeColorPicker.set(argb);
            activeColorPicker.setSyncedWithTheme(false);

            int a = (argb >>> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            pickerHue = hsb[0];
            pickerSat = hsb[1];
            pickerBri = hsb[2];
            pickerAlpha = a / 255.0F;
        } catch (NumberFormatException ignored) {}
    }

    public void removed() {
        savedSelectedTab = selectedTab;
        savedSearch = search;
        savedScrollY = scrollY;
        savedTargetScrollY = targetScrollY;

        contextMenuModule = null;
        activeColorPicker = null;
        activeBindModule = null;
        listeningFullscreenKey = false;
        openDropdownMode = null;
        openDropdownMulti = null;
        openGlobalDropdown = null;
        listeningMenuKey = false;
        searchFocused = false;
        hexFocused = false;
        listeningSettingBind = null;
    }

    public boolean isMouseOverFullscreenModal() {
        return listeningFullscreenKey && activeBindModule != null;
    }

    public boolean isMouseOverColorPicker(float mouseX, float mouseY, float scale) {
        if (activeColorPicker == null) return false;
        float pickW = 196.0F * scale;
        float pickH = 164.0F * scale;
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float px, py;
        if (contextMenuModule != null) {
            px = contextMenuX + 228.0F * scale;
            if (px + pickW > screenW - 10.0F) {
                px = Math.max(10.0F, contextMenuX - pickW - 8.0F * scale);
            }
            py = Math.max(10.0F, Math.min(contextMenuY, screenH - pickH - 10.0F));
        } else {
            px = (screenW - pickW) * 0.5F;
            py = (screenH - pickH) * 0.5F;
        }
        return inside(mouseX, mouseY, px, py, pickW, pickH);
    }

    public boolean isMouseOverDropdown(float mouseX, float mouseY, float scale) {
        if (dropdownAnim <= 0.01F) return false;
        List<String> options = null;
        if (openDropdownMode != null) options = openDropdownMode.getModes();
        else if (openDropdownMulti != null) options = openDropdownMulti.getAllOptions();
        else if ("lang".equals(openGlobalDropdown)) options = List.of("Русский", "English");
        else if ("scale".equals(openGlobalDropdown)) options = Menu.SCALE_MODES;
        else if ("preset".equals(openGlobalDropdown)) options = Menu.THEME_PRESETS;

        if (options == null || options.isEmpty()) return false;

        float maxOptW = 0.0F;
        float extraPadding = (openDropdownMulti != null) ? 38.0F * scale : 24.0F * scale;
        for (String opt : options) {
            float ow = ModernFont.getWidth(opt, 9.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
            if (ow > maxOptW) maxOptW = ow;
        }
        float menuW = Math.max(dropdownW, Math.max(105.0F * scale, maxOptW + extraPadding));

        float itemH = 21.0F * scale;
        float menuH = options.size() * itemH + 6.0F * scale;
        float menuX = (dropdownTriggerW > 0.0F) ? (dropdownTriggerX + dropdownTriggerW - menuW) : dropdownX;
        float menuY = dropdownY;

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        if (menuY + menuH > screenH - 10.0F) {
            menuY = Math.max(10.0F, dropdownY - menuH - 24.0F * scale);
        }
        if (menuX + menuW > screenW - 10.0F) {
            menuX = Math.max(10.0F, screenW - menuW - 10.0F);
        }
        if (menuX < 10.0F) {
            menuX = 10.0F;
        }

        return inside(mouseX, mouseY, menuX, menuY, menuW, menuH);
    }

    public boolean isMouseOverKeybindPopover(float mouseX, float mouseY, float scale) {
        if (activeBindModule == null || listeningFullscreenKey) return false;
        int boundKey = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(activeBindModule.getName(), GLFW.GLFW_KEY_UNKNOWN);
        boolean isBound = (boundKey != GLFW.GLFW_KEY_UNKNOWN && boundKey != 0);

        float popW = 216.0F * scale;
        float popH = isBound ? (154.0F * scale) : (124.0F * scale);
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float px = Math.max(10.0F, Math.min(bindPopoverX, screenW - popW - 10.0F));
        float py = Math.max(10.0F, Math.min(bindPopoverY, screenH - popH - 10.0F));

        return inside(mouseX, mouseY, px, py, popW, popH);
    }

    public boolean isMouseOverGlobalSettings(float mouseX, float mouseY, float scale, int screenWidth, int screenHeight) {
        if (!globalSettingsOpen) return false;
        float mainW = 780.0F * scale;
        float sidebarW = 188.0F * scale;
        float mainX = (screenWidth - mainW) * 0.5F;
        float contentX = mainX + sidebarW + 16.0F * scale;
        float contentW = mainW - sidebarW - 32.0F * scale;
        float mainH = 480.0F * scale;
        float mainY = (screenHeight - mainH) * 0.5F;
        float topBarY = mainY + 16.0F * scale;
        float topBarH = 32.0F * scale;

        float gsX = contentX + contentW - 250.0F * scale;
        float gsY = topBarY + topBarH + 8.0F * scale;
        float gsW = 250.0F * scale;
        float gsH = 156.0F * scale;

        return inside(mouseX, mouseY, gsX, gsY, gsW, gsH);
    }

    public boolean isMouseOverContextMenu(float mouseX, float mouseY, float scale) {
        if (contextMenuModule == null) return false;
        List<Setting<?>> settings = moduleSettings.get(contextMenuModule);
        if (settings == null || settings.isEmpty()) return false;

        float cardW = 220.0F * scale;
        float headerH = 34.0F * scale;

        float contentH = 0.0F;
        for (Setting<?> s : settings) {
            if (!s.isVisible()) continue;
            if (s instanceof BooleanSetting) contentH += 26.0F * scale;
            else if (s instanceof SliderSetting) contentH += 34.0F * scale;
            else if (s instanceof ModeSetting) contentH += 26.0F * scale;
            else if (s instanceof MultiModeSetting) contentH += 26.0F * scale;
            else if (s instanceof KeybindSetting) contentH += 26.0F * scale;
            else if (s instanceof ColorSetting) contentH += 26.0F * scale;
            else if (s instanceof StringSetting) contentH += 38.0F * scale;
            else if (s instanceof ActionSetting) contentH += 28.0F * scale;
            else contentH += 24.0F * scale;
        }

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float maxVisibleSettingsH = Math.min(contentH, Math.min(300.0F * scale, screenH - 120.0F * scale));
        float visibleSettingsH = maxVisibleSettingsH;
        float cardH = headerH + visibleSettingsH + 10.0F * scale;

        float x = Math.max(10.0F, Math.min(contextMenuX, screenW - cardW - 10.0F));
        float y = Math.max(10.0F, Math.min(contextMenuY, screenH - cardH - 10.0F));

        return inside(mouseX, mouseY, x, y, cardW, cardH);
    }

    public boolean isMouseOverDropdownOrPicker(float mouseX, float mouseY, float scale) {
        return isMouseOverFullscreenModal() || isMouseOverColorPicker(mouseX, mouseY, scale) || isMouseOverDropdown(mouseX, mouseY, scale);
    }

    public boolean isMouseOverAnyOverlay(float mouseX, float mouseY, float scale, int screenWidth, int screenHeight) {
        return isMouseOverFullscreenModal()
                || isMouseOverColorPicker(mouseX, mouseY, scale)
                || isMouseOverDropdown(mouseX, mouseY, scale)
                || isMouseOverKeybindPopover(mouseX, mouseY, scale)
                || isMouseOverContextMenu(mouseX, mouseY, scale)
                || isMouseOverGlobalSettings(mouseX, mouseY, scale, screenWidth, screenHeight);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
