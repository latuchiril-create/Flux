package dev.fuga.fluxvisuals.gui.modern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.LicenseManager;
import dev.fuga.fluxvisuals.gui.ClickGuiScreen;
import dev.fuga.fluxvisuals.gui.modern.font.ModernFont;
import dev.fuga.fluxvisuals.gui.modern.setting.*;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import dev.fuga.fluxvisuals.modules.visual.*;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;

public final class ModernClickGuiRenderer {
    private static final Map<Identifier, Identifier> WHITE_ICONS = new HashMap<>();
    private static final Identifier ICON_COMBAT = Identifier.of("fluxvisuals", "icons/combat.png");
    private static final Identifier ICON_MOVEMENT = Identifier.of("fluxvisuals", "icons/movement.png");
    private static final Identifier ICON_PLAYER = Identifier.of("fluxvisuals", "icons/player.png");
    private static final Identifier ICON_VISUALS = Identifier.of("fluxvisuals", "icons/visuals.png");
    private static final Identifier ICON_AUTOBUY = Identifier.of("fluxvisuals", "icons/autobuy.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("fluxvisuals", "icons/settings.png");
    private static final Identifier ICON_WRENCH = Identifier.of("fluxvisuals", "icons/wrench.png");
    private static final Identifier ICON_SEARCH = Identifier.of("fluxvisuals", "icons/search.png");
    private static final Identifier ICON_DOTS = Identifier.of("fluxvisuals", "icons/dots.png");
    private static final Identifier ICON_CHEVRON = Identifier.of("fluxvisuals", "icons/chevron.png");
    private static final Identifier ICON_USER = Identifier.of("fluxvisuals", "icons/user.png");
    private static final Identifier ICON_PIPETTE = Identifier.of("fluxvisuals", "icons/pipette.png");
    private static final Identifier ICON_RESET = Identifier.of("fluxvisuals", "icons/reset.png");
    private static final Identifier ICON_SORT = Identifier.of("fluxvisuals", "icons/sort.png");
    private static final Identifier ICON_INFO = Identifier.of("fluxvisuals", "icons/info.png");
    private static final Identifier ICON_COPY = Identifier.of("fluxvisuals", "icons/copy.png");
    private static final Identifier ICON_PASTE = Identifier.of("fluxvisuals", "icons/paste.png");
    private static final Identifier ICON_PIN = Identifier.of("fluxvisuals", "icons/pin.png");
    private static final Identifier ICON_NO_SETTINGS = Identifier.of("fluxvisuals", "icons/no_settings.png");

    public static final Set<String> PINNED_MODULES = new LinkedHashSet<>();
    private static final Identifier ICON_SLIDERS = Identifier.of("fluxvisuals", "icons/sliders.png");
    private static final Identifier ICON_ZAP = Identifier.of("fluxvisuals", "icons/zap.png");
    private static final Identifier ICON_KEYBOARD = Identifier.of("fluxvisuals", "icons/keyboard.png");

    public static final Map<String, Integer> MODULE_BINDS = new HashMap<>();
    private static final Set<Integer> PRESSED_KEYS = new HashSet<>();

    /**
     * Persists module toggle binds (MODULE_BINDS) into the shared
     * fluxvisuals.properties file. Without this, binds assigned in the
     * modern GUI only lived in memory and were lost on every restart.
     */
    public static void loadBinds(Properties properties) {
        if (properties == null) {
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("gui.modernbind.")) {
                continue;
            }
            String bindName = key.substring("gui.modernbind.".length());
            if (bindName.isEmpty()) {
                continue;
            }
            try {
                MODULE_BINDS.put(bindName, Integer.parseInt(properties.getProperty(key)));
            } catch (NumberFormatException ignored) {
                // Ignore broken bind values and keep the menu usable.
            }
        }
        // First run / upgrade: import binds previously saved by the premium
        // GUI (gui.bind.<ModuleName>) so old binds keep toggling modules.
        if (FluxVisualsClient.MODULE_MANAGER != null) {
            for (Module mod : FluxVisualsClient.MODULE_MANAGER.getModules()) {
                if (mod == null || mod.getName() == null || MODULE_BINDS.containsKey(mod.getName())) {
                    continue;
                }
                String legacy = properties.getProperty("gui.bind." + mod.getName());
                if (legacy != null) {
                    try {
                        int code = Integer.parseInt(legacy);
                        if (code != GLFW.GLFW_KEY_UNKNOWN && code != 0) {
                            MODULE_BINDS.put(mod.getName(), code);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public static void saveBinds(Properties properties) {
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : MODULE_BINDS.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                continue;
            }
            properties.setProperty("gui.modernbind." + entry.getKey(), Integer.toString(entry.getValue()));
        }
    }

    public enum SortMode {
        DEFAULT("По умолчанию", "Default"),
        ALPHABETICAL("По алфавиту (А-Я)", "Alphabetical (A-Z)"),
        ACTIVE_FIRST("Сначала активные", "Active First");

        public final String ru;
        public final String en;
        SortMode(String ru, String en) { this.ru = ru; this.en = en; }
        public String label(boolean isEn) { return isEn ? en : ru; }
    }

    private static Category savedCategory = Category.COMBAT;
    private static String savedModuleName = null;
    private static SortMode sortMode = SortMode.DEFAULT;

    private static float catIndicatorY = -1.0F;
    private static float moduleIndicatorY = -1.0F;
    private static float moduleIndicatorAlpha = 0.0F;

    private static boolean settingsOpen = false;
    private static float settingsAnim = 0.0F;

    private final ModuleManager moduleManager;
    private final Map<Module, List<Setting<?>>> moduleSettings = new LinkedHashMap<>();
    private final Map<String, Float> toggleAnims = new HashMap<>();
    private final Map<SliderSetting, Float> sliderVisualAnim = new HashMap<>();
    private final Map<Module, Float> moduleVisualY = new HashMap<>();
    private final Map<Module, Float> bindListeningAnim = new HashMap<>();
    private final Map<Module, Float> bindBadgeAnim = new HashMap<>();
    private final Map<Module, String> lastBoundKeyNames = new HashMap<>();

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;

    private String search = "";
    private boolean searchFocused = false;

    private float openProgress = 0.0F;
    private long lastTime = System.currentTimeMillis();

    private float scrollLeft = 0.0F;
    private float scrollRight = 0.0F;
    private SliderSetting activeSlider = null;
    private SliderSetting activeSliderInput = null;
    private String sliderInputBuffer = "";

    private Module listeningModuleBind = null;
    private KeybindSetting listeningSettingBind = null;

    private int listeningMacroIndex = -1;
    private int activeMacroTextIndex = -1;
    private int listeningNameBindIndex = -1;
    private int activeNameBindTextIndex = -1;

    private final Map<ModeSetting, Float> dropdownAnim = new HashMap<>();
    private ModeSetting openDropdown = null;

    private final Map<MultiModeSetting, Float> multiDropdownAnim = new HashMap<>();
    private MultiModeSetting openMultiDropdown = null;

    private boolean sortDropdownOpen = false;
    private float sortDropdownAnim = 0.0F;

    // 3-Dots Module Context Menu Popup
    private Module activeContextMenuModule = null;
    private float contextMenuX = 0.0F, contextMenuY = 0.0F;
    private float contextMenuAnim = 0.0F;
    private boolean contextMenuClosing = false;
    private String contextMenuFeedback = null;
    private long contextMenuFeedbackTime = 0L;

    // Unrolling Floating Info Banner above GUI
    private Module unrollingInfoModule = null;
    private float unrollAnim = 0.0F;
    private boolean unrollClosing = false;
    private long unrollStartTime = 0L;

    private ColorSetting activeColorPicker = null;
    private float pickerX = 0, pickerY = 0;
    private float pickerHue = 0.75F;
    private float pickerSat = 0.8F;
    private float pickerBri = 0.9F;
    private float pickerAlpha = 1.0F;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;
    private boolean hexFocused = false;
    private String hexBuffer = "";
    private float colorPickerAnim = 0.0F;
    private boolean colorPickerClosing = false;
    private String colorFeedback = null;
    private long colorFeedbackTime = 0L;
    private boolean pipetteActive = false;

    private static Robot AWT_ROBOT = null;
    private static Robot getAwtRobot() {
        if (AWT_ROBOT == null) {
            try {
                AWT_ROBOT = new Robot();
            } catch (Exception ignored) {}
        }
        return AWT_ROBOT;
    }

    private void sampleScreenPixelColor() {
        if (activeColorPicker == null) return;
        try {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo != null) {
                Point point = pointerInfo.getLocation();
                Robot robot = getAwtRobot();
                if (robot != null) {
                    Color c = robot.getPixelColor(point.x, point.y);
                    int rgb = c.getRGB() & 0x00FFFFFF;
                    int a = Math.round(pickerAlpha * 255.0F) & 0xFF;
                    int argb = (a << 24) | rgb;
                    activeColorPicker.set(argb);
                    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
                    pickerHue = hsb[0];
                    pickerSat = hsb[1];
                    pickerBri = hsb[2];
                    hexBuffer = (a == 255) ? String.format("%06X", rgb) : String.format("%08X", argb);
                    colorFeedback = "pipette_success";
                    colorFeedbackTime = System.currentTimeMillis();
                }
            }
        } catch (Exception ignored) {}
    }

    private StringSetting activeStringSetting = null;
    private String activeStringDraft = "";

    public static final class GuiBounds {
        public static final float PANEL_GAP = 12.0F;

        public final float scale;
        public final float w, h, x, y;
        public final float sidebarW;
        public final float contentX, contentW;
        public final float boxW, boxH, boxY;
        public final float box1X, box2X;
        public final float rowW;
        public final float settingsProgress;

        public GuiBounds(int screenWidth, int screenHeight, float scale) {
            this(screenWidth, screenHeight, scale, easeInOutCubic(settingsAnim));
        }

        public GuiBounds(int screenWidth, int screenHeight, float scale, float settingsProgress) {
            this.scale = scale;
            this.settingsProgress = settingsProgress;
            this.sidebarW = 184.0F * scale;
            this.boxW = 295.0F * scale;
            this.boxH = 460.0F * scale;
            float gap = PANEL_GAP * scale;

            float twoPanelW = sidebarW + gap + boxW;
            float threePanelW = twoPanelW + gap + boxW;
            this.w = twoPanelW + (threePanelW - twoPanelW) * settingsProgress;
            this.h = boxH;
            this.x = (screenWidth - w) * 0.5F;
            this.y = (screenHeight - h) * 0.5F;
            this.boxY = y;
            this.box1X = x + sidebarW + gap;

            float tuckedX = box1X;
            float expandedX = box1X + boxW + gap;
            this.box2X = tuckedX + (expandedX - tuckedX) * settingsProgress;

            this.contentX = box1X;
            this.contentW = boxW * 2.0F + gap;
            this.rowW = boxW - 32.0F * scale;
        }
    }

    public enum Category {
        COMBAT("Combat", "Бой", ICON_COMBAT),
        MOVEMENT("Movement", "Движение", ICON_MOVEMENT),
        PLAYER("Player", "Игрок", ICON_PLAYER),
        VISUALS("Visuals", "Визуалы", ICON_VISUALS),
        AUTOBUY("AutoBuy", "АвтоБай", ICON_AUTOBUY),
        SETTINGS("Settings", "Настройки", ICON_SETTINGS);

        public final String enName;
        public final String ruName;
        public final Identifier icon;

        Category(String enName, String ruName, Identifier icon) {
            this.enName = enName;
            this.ruName = ruName;
            this.icon = icon;
        }

        public String getName(boolean isEn) {
            return isEn ? enName : ruName;
        }
    }

    public ModernClickGuiRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        initSettings();
    }

    private boolean isEn() {
        return moduleManager != null && moduleManager.getMenu() != null && moduleManager.getMenu().isEnglish();
    }

    private String t(String ru, String en) {
        return isEn() ? en : ru;
    }

    private String getSettingDisplayName(String original) {
        if (!isEn() || original == null) return original;
        return switch (original) {
            case "Режим" -> "Mode";
            case "Размер" -> "Size";
            case "Цвет" -> "Color";
            case "Цвет удара" -> "Damage Color";
            case "Цвет шляпы" -> "Hat Color";
            case "Цвет крыльев" -> "Wing Color";
            case "Цвет тумана" -> "Fog Color";
            case "Цвет обводки" -> "Outline Color";
            case "Цвет заливки" -> "Fill Color";
            case "Цвет прицела" -> "Crosshair Color";
            case "Цвет частиц" -> "Particle Color";
            case "Цвет кругов" -> "Circle Color";
            case "Цвет шлейфа" -> "Trail Color";
            case "Цвет ESP" -> "ESP Color";
            case "Горячая клавиша" -> "Keybind";
            case "Бинд зума" -> "Zoom Keybind";
            case "Бинд камеры" -> "Camera Keybind";
            case "Бинд свапа" -> "Swap Keybind";
            case "Бинд меню" -> "Menu Keybind";
            case "Масштаб GUI" -> "GUI Scale";
            case "Язык / Language" -> "Language";
            case "Время суток" -> "Time of Day";
            case "Кастомный туман" -> "Custom Fog";
            case "Дистанция тумана" -> "Fog Distance";
            case "Заливка бокса" -> "Box Fill";
            case "Сквозь стены" -> "Through Walls";
            case "Плавный переход" -> "Smooth Transition";
            case "Толщина линий" -> "Line Thickness";
            case "Стиль" -> "Style";
            case "Цели" -> "Targets";
            case "Красный при ударе" -> "Red on Hit";
            case "Красный при уроне" -> "Red on Damage";
            case "Частицы при ударе" -> "Hit Particles";
            case "Размер HUD" -> "HUD Scale";
            case "Зазор" -> "Gap";
            case "Толщина" -> "Thickness";
            case "Точка в центре" -> "Center Dot";
            case "Плавность" -> "Smoothness";
            case "Зум колесиком" -> "Scroll Zoom";
            case "Режим активации" -> "Activation Mode";
            case "Дистанция камеры" -> "Camera Distance";
            case "Первый предмет" -> "First Item";
            case "Второй предмет" -> "Second Item";
            case "Соотношение" -> "Aspect Ratio";
            case "Текстура" -> "Texture";
            case "Количество" -> "Amount";
            case "Время жизни" -> "Lifetime";
            case "Длина шлейфа" -> "Trail Length";
            case "Огонь на экране" -> "Fire Overlay";
            case "Свечение сущностей" -> "Entity Glowing";
            case "Плохая погода" -> "Bad Weather";
            case "Тряска от урона" -> "Hurt Camera";
            case "FOV спринта" -> "Sprint FOV";
            case "Пузыри песка душ" -> "Soul Sand Bubbles";
            case "Задержка смены" -> "Switch Delay";
            case "Список анархий" -> "Anarchy List";
            case "Авто-реклама в чат" -> "Auto Chat Ads";
            case "Текст рекламы" -> "Ad Text";
            case "Свитч по анархиям" -> "Anarchy Switcher";
            case "Авто-перепродажа" -> "Auto Resell";
            case "Анти-слив баланса" -> "Balance Guard";
            case "Аренда слотов" -> "Slot Rental";
            case "Уведомления в чат" -> "Chat Notifications";
            case "Сообщение в чат" -> "Chat Message";
            case "Интервал сообщений" -> "Message Interval";
            case "Продавать мечи" -> "Sell Swords";
            case "Цена продажи" -> "Sell Price";
            case "Всегда показывать" -> "Always Show";
            case "Только углы" -> "Corners Only";
            case "Вектор взгляда" -> "Look Vector";
            case "Включая себя" -> "Include Self";
            case "Предметы" -> "Items";
            case "Драконья трапка" -> "Draconic Trap";
            case "Анимация таба" -> "Tab Animation";
            case "Анимация 3-го лица" -> "Third Person Anim";
            case "Плавный хотбар" -> "Smooth Hotbar";
            case "Анимация инвентаря" -> "Inventory Anim";
            case "Кастомный таб" -> "Custom Tab";
            case "Кнопка мыши" -> "Mouse Button";
            case "Задержка клика" -> "Click Delay";
            case "Музыкальный бар" -> "Music Bar";
            case "Уведомления" -> "Notifications";
            case "Кастомная кнопка" -> "Custom Button";
            case "Текст кнопки" -> "Button Label";
            case "Ссылка кнопки" -> "Button URL";
            case "Подмена ника" -> "Name Spoof";
            case "Скрывать анархию" -> "Hide Anarchy";
            case "Текст анархии" -> "Anarchy Text";
            case "Задержка" -> "Delay";
            case "Активен" -> "Active";
            case "Авто-продажа" -> "Auto Sell";
            case "Цена авто-продажи" -> "Auto Sell Price";
            case "Броня в цвет удара" -> "Armor Hit Tint";
            case "Шейдерная заливка" -> "Shader Fill";
            case "Взмахи" -> "Flapping";
            case "Скорость взмахов" -> "Flap Speed";
            case "Размер кристаллов" -> "Crystal Size";
            case "Количество кристаллов" -> "Crystal Count";
            case "Скорость кристаллов" -> "Crystal Speed";
            case "Радиус кристаллов" -> "Crystal Radius";
            case "Мультипоинты" -> "Multipoints";
            case "Скорость Yaw" -> "Yaw Speed";
            case "Скорость Pitch" -> "Pitch Speed";
            case "Наводка Yaw" -> "Aim Yaw";
            case "Наводка Pitch" -> "Aim Pitch";
            case "Держать цель" -> "Sticky Target";
            case "Хуманайз" -> "Humanize";
            case "Макс. скорость" -> "Max Speed";
            case "Мёртвая зона" -> "Deadzone";
            case "Реакция" -> "Reaction";
            case "Проверка стен" -> "Wall Check";
            case "Только при атаке" -> "Only While Attacking";
            case "По кнопке" -> "Require Key";
            case "Кнопка наводки" -> "Aim Key";
            case "Общая кнопка Aim+Trigger" -> "Aim+Trigger Combo";
            case "Кнопка связки" -> "Combo Key";
            case "Удержание прицела" -> "Aim Lock-on";
            case "Не бить при еде/блоке" -> "No Hit While Using";
            case "Шанс крит-тайминга" -> "Critical Timing Chance";
            case "Рандомизация задержки +50–150 мс" -> "Random Delay +50–150 ms";
            case "Булава без КД" -> "Mace Ignores Cooldown";
            case "Свежая наводка" -> "Fresh Raycast";
            case "Только с ЛКМ" -> "Only With LMB";
            case "Прыгать для крита" -> "Jump For Crit";
            case "Ответный удар" -> "Counter Hit";
            case "Окно ответки" -> "Counter Window";
            case "Только с триггером" -> "Only With Trigger";
            case "Возвращать предмет" -> "Restore Item";
            case "Таймаут" -> "Timeout";
            case "Рандомизация прыжков" -> "Jump Randomization";
            default -> original;
        };
    }

    public static void handleBinds(MinecraftClient client) {
        if (client == null || client.player == null || client.getWindow() == null
                || (client.currentScreen != null && !isClientGui(client))) {
            PRESSED_KEYS.clear();
            return;
        }

        long handle = client.getWindow().getHandle();

        // Check Menu open keybind
        Menu menuMod = FluxVisualsClient.MODULE_MANAGER.getMenu();
        if (menuMod != null && menuMod.getKeyBind() != 0 && menuMod.getKeyBind() != GLFW.GLFW_KEY_UNKNOWN) {
            int menuKey = menuMod.getKeyBind();
            boolean isDown = (menuKey >= 1000 && menuKey <= 1010)
                    ? GLFW.glfwGetMouseButton(handle, menuKey - 1000) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(handle, menuKey) == GLFW.GLFW_PRESS;

            if (isDown && !PRESSED_KEYS.contains(menuKey)) {
                PRESSED_KEYS.add(menuKey);
                FluxVisualsClient.openModernGui(client);
                return;
            } else if (!isDown) {
                PRESSED_KEYS.remove(menuKey);
            }
        }

        for (Map.Entry<String, Integer> entry : MODULE_BINDS.entrySet()) {
            int key = entry.getValue();
            if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) continue;

            boolean isDown;
            if (key >= 1000 && key <= 1010) {
                isDown = GLFW.glfwGetMouseButton(handle, key - 1000) == GLFW.GLFW_PRESS;
            } else {
                isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
            }

            String bindName = entry.getKey();
            if (isDown && !PRESSED_KEYS.contains(key)) {
                PRESSED_KEYS.add(key);

                // Without a license functions stay off and the player is told why (once per cooldown).
                if (!("Menu".equalsIgnoreCase(bindName) || "ClickGUI".equalsIgnoreCase(bindName) || "action_freelook".equalsIgnoreCase(bindName)) && !LicenseManager.isLicensed) {
                    LicenseManager.canOpenGui(client);
                } else if ("action_autoswap".equalsIgnoreCase(bindName)) {
                    FluxVisualsClient.MODULE_MANAGER.getAutoSwap().triggerSwap();
                } else if ("action_elytraswap".equalsIgnoreCase(bindName)) {
                    FluxVisualsClient.MODULE_MANAGER.getElytraSwap().triggerSwap();
                } else if ("action_zoom".equalsIgnoreCase(bindName)) {
                    FluxVisualsClient.MODULE_MANAGER.getZoom().toggle();
                } else if ("Menu".equalsIgnoreCase(bindName) || "ClickGUI".equalsIgnoreCase(bindName)) {
                    FluxVisualsClient.openModernGui(client);
                } else if ("action_freelook".equalsIgnoreCase(bindName)) {
                    // FreeLook handles hold/toggle internally in onTick
                } else {
                    for (Module mod : FluxVisualsClient.MODULE_MANAGER.getModules()) {
                        if (mod.getName().equalsIgnoreCase(bindName)) {
                            sessionToggleTarget(mod).toggle();
                            break;
                        }
                    }
                }
            } else if (!isDown) {
                PRESSED_KEYS.remove(key);
            }
        }
    }

    private static boolean isClientGui(MinecraftClient client) {
        return client != null && (client.currentScreen instanceof ModernClickGuiScreen
                || client.currentScreen instanceof ClickGuiScreen);
    }

    /**
     * Automation modules (AutoBuy / AutoResellAFK / ItemCrafter) exist both as
     * a global instance and as a per-bot copy. While a bot session is active
     * the GUI must toggle and display the BOT's copy — otherwise enabling the
     * crafter "on the bot" flips the global module and it starts working on
     * the main account (spamming /ah commands there).
     */
    private static Module sessionToggleTarget(Module mod) {
        if (mod == null || FluxVisualsClient.MODULE_MANAGER == null || FluxVisualsClient.MULTI_BOT_MANAGER == null) {
            return mod;
        }
        if (mod == FluxVisualsClient.MODULE_MANAGER.getAutoBuy()) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(FluxVisualsClient.MODULE_MANAGER.getAutoBuy());
        }
        if (mod == FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK()) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoResellAFKForCurrentSession(FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK());
        }
        if (mod == FluxVisualsClient.MODULE_MANAGER.getItemCrafter()) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getItemCrafterForCurrentSession(FluxVisualsClient.MODULE_MANAGER.getItemCrafter());
        }
        return mod;
    }

    public void open() {
        openProgress = 0.0F;
        lastTime = System.currentTimeMillis();
        search = "";
        searchFocused = false;
        moduleIndicatorY = -1.0F;
        moduleIndicatorAlpha = 0.0F;
        settingsOpen = false;
        settingsAnim = 0.0F;
        listeningModuleBind = null;
        listeningSettingBind = null;
        listeningMacroIndex = -1;
        activeMacroTextIndex = -1;
        listeningNameBindIndex = -1;
        activeNameBindTextIndex = -1;
        openDropdown = null;
        openMultiDropdown = null;
        sortDropdownAnim = 0.0F;
        activeContextMenuModule = null;
        contextMenuAnim = 0.0F;
        contextMenuClosing = false;
        unrollingInfoModule = null;
        unrollAnim = 0.0F;
        unrollClosing = false;
        activeColorPicker = null;
        colorPickerAnim = 0.0F;
        colorPickerClosing = false;
        activeStringSetting = null;
        activeStringDraft = "";
        activeSlider = null;
        activeSliderInput = null;
        sliderInputBuffer = "";
        hexFocused = false;
        colorFeedback = null;
        colorFeedbackTime = 0L;

        selectedCategory = savedCategory != null ? savedCategory : Category.COMBAT;
        catIndicatorY = -1.0F;
        if (savedModuleName != null) {
            for (Module mod : moduleManager.getModules()) {
                if (mod.getName().equalsIgnoreCase(savedModuleName)) {
                    selectedModule = mod;
                    break;
                }
            }
        }
        if (selectedModule == null) {
            List<Module> mods = getModulesForCategory(selectedCategory);
            if (!mods.isEmpty()) selectedModule = mods.get(0);
        }
    }

    private void initSettings() {
        moduleSettings.clear();
        for (Module mod : moduleManager.getModules()) {
            List<Setting<?>> list = new ArrayList<>();
            populateModuleSettings(mod, list);
            moduleSettings.put(mod, list);
        }
    }

    private void populateModuleSettings(Module module, List<Setting<?>> list) {
        String name = module.getName();
        if ("Menu".equalsIgnoreCase(name)) {
            Menu menu = moduleManager.getMenu();
            list.add(new ModeSetting("Масштаб GUI", Menu.SCALE_MODES, menu::getScaleMode, menu::setScaleMode));
            list.add(new ModeSetting("Язык / Language", Menu.LANGUAGE_MODES, menu::getLanguage, menu::setLanguage));
        } else if ("Telegram".equalsIgnoreCase(name) || "TG".equalsIgnoreCase(name)) {
            Telegram tg = moduleManager.getTelegram();
            list.add(new StringSetting("Bot Token", "123456:...", tg::getBotToken, tg::setBotToken));
            list.add(new StringSetting("Chat ID", "-100123...", tg::getChatId, tg::setChatId));
            list.add(new BooleanSetting("Уведомления", tg::isNotificationsEnabled, tg::setNotificationsEnabled));
            list.add(new BooleanSetting("Авто-продажа", tg::isAutoSellEnabled, tg::setAutoSellEnabled));
            list.add(new StringSetting("Цена авто-продажи", "150k", tg::getAutoSellPrice, tg::setAutoSellPrice).visible(tg::isAutoSellEnabled));
        } else if ("ItemCrafter".equalsIgnoreCase(name)) {
            ItemCrafter ic = moduleManager.getItemCrafter();
            list.add(new ModeSetting("Режим крафта", List.of("Золотые яблоки", "Чарки"),
                    () -> ic.getMode() == ItemCrafter.Mode.GOLDEN_APPLE ? "Золотые яблоки" : "Чарки",
                    v -> ic.setMode("Чарки".equalsIgnoreCase(v) ? ItemCrafter.Mode.ENCHANTED_GOLDEN_APPLE : ItemCrafter.Mode.GOLDEN_APPLE)));
            list.add(new SliderSetting("Размер пачки", 1.0F, 64.0F, 1.0F, " шт", () -> (float) ic.getBatchCount(), v -> ic.setBatchCount(Math.round(v))));
            list.add(new StringSetting("Цена продажи", "150k", ic::getSellPrice, ic::setSellPrice));
            list.add(new SliderSetting("Макс. лотов", 1.0F, 15.0F, 1.0F, "", () -> (float) ic.getMaxLots(), v -> ic.setMaxLots(Math.round(v))));
            list.add(new BooleanSetting("Авто-покупка на AH", ic::isAutoBuyIngredients, ic::setAutoBuyIngredients));
            list.add(new StringSetting("Цена золотого блока/ст", "500000", () -> Integer.toString(ic.getGoldBlockPricePerStack()), v -> { try { ic.setGoldBlockPricePerStack(Integer.parseInt(v.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {} }).visible(ic::isAutoBuyIngredients));
            list.add(new StringSetting("Цена слитка/ст", "60000", () -> Integer.toString(ic.getGoldIngotPricePerStack()), v -> { try { ic.setGoldIngotPricePerStack(Integer.parseInt(v.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {} }).visible(ic::isAutoBuyIngredients));
            list.add(new StringSetting("Цена яблок/ст", "50000", () -> Integer.toString(ic.getApplePricePerStack()), v -> { try { ic.setApplePricePerStack(Integer.parseInt(v.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {} }).visible(ic::isAutoBuyIngredients));
            list.add(new SliderSetting("Ожидание после продажи", 1.0F, 120.0F, 1.0F, " с", () -> (float) ic.getSellWaitSeconds(), v -> ic.setSellWaitSeconds(Math.round(v))));
        } else if ("CaptchaSolver".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.captcha.CaptchaSolver cs = moduleManager.getCaptchaSolver();
            list.add(new StringSetting("URL сервера", "http://127.0.0.1:5000/solve", cs::getServerUrl, cs::setServerUrl));
            list.add(new StringSetting("Префикс ответа", "", cs::getAnswerPrefix, cs::setAnswerPrefix));
            list.add(new ModeSetting("Стена капчи", List.of("Авто", "По прицелу", "Ближайшая", "Самая чистая"),
                    () -> {
                        String mode = cs.getWallMode().trim().toLowerCase(java.util.Locale.ROOT);
                        if (mode.startsWith("nearest") || mode.startsWith("ближ")) return "Ближайшая";
                        if (mode.startsWith("sparse") || mode.startsWith("чист") || mode.startsWith("редк")) return "Самая чистая";
                        if (mode.startsWith("cross") || mode.startsWith("прицел")) return "По прицелу";
                        return "Авто";
                    },
                    v -> cs.setWallMode("Ближайшая".equalsIgnoreCase(v) ? "nearest"
                            : "Самая чистая".equalsIgnoreCase(v) ? "sparsest"
                            : "По прицелу".equalsIgnoreCase(v) ? "crosshair" : "auto")));
            list.add(new BooleanSetting("Дебаг: не удалять скрины", cs::isDebugKeepShots, cs::setDebugKeepShots));
        } else if ("Hit Color".equalsIgnoreCase(name) || "HitColor".equalsIgnoreCase(name)) {
            HitColor hitColor = moduleManager.getHitColor();
            list.add(new BooleanSetting("Броня в цвет удара", hitColor::isArmorTintEnabled, hitColor::setArmorTintEnabled));
            list.add(new ColorSetting("Цвет удара", hitColor::getArgbColor, col -> {
                float[] hsb = Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                hitColor.setColor(hsb[0], hsb[1], hsb[2]);
                hitColor.setAlpha(((col >> 24) & 0xFF) / 255.0F);
            }));
        } else if ("ChinaHat".equalsIgnoreCase(name)) {
            ChinaHat hat = moduleManager.getChinaHat();
            list.add(new SliderSetting("Размер", 0.3F, 1.5F, 0.05F, "m", hat::getSize, hat::setSize));
            list.add(new ModeSetting("Режим", List.of("Обычная", "Шейдер"),
                    () -> hat.getFillMode() == ChinaHat.FillMode.SHADER ? "Шейдер" : "Обычная",
                    v -> hat.setFillMode("Шейдер".equalsIgnoreCase(v) ? ChinaHat.FillMode.SHADER : ChinaHat.FillMode.FILL)));
            list.add(new ColorSetting("Цвет шляпы", hat::getArgbColor, hat::setArgbColor).visible(() -> hat.getFillMode() == ChinaHat.FillMode.FILL));
        } else if ("Wings".equalsIgnoreCase(name)) {
            Wings wings = moduleManager.getWings();
            list.add(new MultiModeSetting("Цели", List.of("Self", "Players", "Bots"), wings::getTargets, wings::setTargets));
            list.add(new ModeSetting("Форма", List.of("Ангельские", "Дракон", "Бабочка", "Феникс", "Кристалл", "Механические", "Фея", "Демон"),
                    () -> switch (wings.getWingType()) { case ANGELIC -> "Ангельские"; case DRAGON -> "Дракон"; case BUTTERFLY -> "Бабочка"; case PHOENIX -> "Феникс"; case CRYSTAL -> "Кристалл"; case MECHANICAL -> "Механические"; case FAIRY -> "Фея"; case DEMON -> "Демон"; },
                    v -> { Wings.WingType type = Wings.WingType.ANGELIC; if ("Дракон".equals(v)) type = Wings.WingType.DRAGON; else if ("Бабочка".equals(v)) type = Wings.WingType.BUTTERFLY; else if ("Феникс".equals(v)) type = Wings.WingType.PHOENIX; else if ("Кристалл".equals(v)) type = Wings.WingType.CRYSTAL; else if ("Механические".equals(v)) type = Wings.WingType.MECHANICAL; else if ("Фея".equals(v)) type = Wings.WingType.FAIRY; else if ("Демон".equals(v)) type = Wings.WingType.DEMON; wings.setWingType(type); }));
            list.add(new BooleanSetting("Шейдерная заливка", wings::isShaderFill, wings::setShaderFill));
            list.add(new BooleanSetting("Взмахи", wings::isFlapping, wings::setFlapping));
            list.add(new SliderSetting("Размер", 0.3F, 3.0F, 0.1F, "x", wings::getScale, wings::setScale));
            list.add(new SliderSetting("Скорость взмахов", 0.5F, 8.0F, 0.2F, "x", wings::getFlapSpeed, wings::setFlapSpeed).visible(wings::isFlapping));
            list.add(new ColorSetting("Цвет крыльев", wings::getArgbColor, wings::setArgbColor));
        } else if ("WorldCustomizer".equalsIgnoreCase(name) || "World Customizer".equalsIgnoreCase(name)) {
            WorldCustomizer wc = moduleManager.getWorldCustomizer();
            list.add(new ModeSetting("Время суток", List.of("День", "Ночь", "Полночь", "Закат", "Кастом"),
                    () -> switch (wc.getTimePreset()) {
                        case DAY -> "День";
                        case NIGHT -> "Ночь";
                        case MIDNIGHT -> "Полночь";
                        case SUNSET -> "Закат";
                        case CUSTOM -> "Кастом";
                        default -> "День";
                    },
                    v -> {
                        WorldCustomizer.TimePreset preset = switch (v) {
                            case "Ночь" -> WorldCustomizer.TimePreset.NIGHT;
                            case "Полночь" -> WorldCustomizer.TimePreset.MIDNIGHT;
                            case "Закат" -> WorldCustomizer.TimePreset.SUNSET;
                            case "Кастом" -> WorldCustomizer.TimePreset.CUSTOM;
                            default -> WorldCustomizer.TimePreset.DAY;
                        };
                        wc.setTimePreset(preset);
                    }));
            list.add(new SliderSetting("Кастомное время", 0.0F, 24000.0F, 500.0F, "", () -> (float) wc.getCustomTime(), v -> wc.setCustomTime(Math.round(v))).visible(() -> wc.getTimePreset() == WorldCustomizer.TimePreset.CUSTOM));
            list.add(new BooleanSetting("Кастомный туман", wc::isCustomFogEnabled, wc::setCustomFogEnabled));
            list.add(new SliderSetting("Дистанция тумана", 16.0F, 1024.0F, 16.0F, "m", wc::getFogDistance, wc::setFogDistance).visible(wc::isCustomFogEnabled));
            list.add(new ColorSetting("Цвет тумана", wc::getFogArgbColor, wc::setFogArgbColor).visible(wc::isCustomFogEnabled));
        } else if ("BlockOverlay".equalsIgnoreCase(name)) {
            BlockOverlay bo = moduleManager.getBlockOverlay();
            list.add(new BooleanSetting("Заливка бокса", bo::isFillEnabled, bo::setFillEnabled));
            list.add(new ModeSetting("Режим заливки", List.of("Обычная", "Шейдер"),
                    () -> bo.getFillMode() == BlockOverlay.FillMode.SHADER ? "Шейдер" : "Обычная",
                    v -> bo.setFillMode("Шейдер".equalsIgnoreCase(v) ? BlockOverlay.FillMode.SHADER : BlockOverlay.FillMode.FILL)).visible(bo::isFillEnabled));
            list.add(new ModeSetting("Тип шейдера", List.of("Мягкий", "Паутина", "Туманность", "Плазма", "Звезды"),
                    () -> bo.getShaderType() != null ? bo.getShaderType().label() : "Мягкий",
                    v -> {
                        for (BlockOverlay.ShaderType st : BlockOverlay.ShaderType.values()) {
                            if (st.label().equalsIgnoreCase(v) || st.name().equalsIgnoreCase(v)) {
                                bo.setShaderType(st);
                                break;
                            }
                        }
                    }).visible(() -> bo.isFillEnabled() && bo.getFillMode() == BlockOverlay.FillMode.SHADER));
            list.add(new SliderSetting("Скорость анимации", 0.2F, 3.0F, 0.1F, "x", bo::getAnimationSpeed, bo::setAnimationSpeed).visible(() -> bo.isFillEnabled() && bo.getFillMode() == BlockOverlay.FillMode.SHADER));
            list.add(new BooleanSetting("Сквозь стены", bo::isThroughWalls, bo::setThroughWalls));
            list.add(new BooleanSetting("Плавный переход", bo::isSmoothSwitch, bo::setSmoothSwitch));
            list.add(new SliderSetting("Толщина линий", 0.5F, 4.0F, 0.1F, "px", bo::getLineThickness, bo::setLineThickness));
            list.add(new ColorSetting("Цвет обводки", bo::getOutlineArgbColor, bo::setOutlineArgbColor));
            list.add(new ColorSetting("Цвет заливки", bo::getFillArgbColor, bo::setFillArgbColor).visible(bo::isFillEnabled));
        } else if ("TargetESP".equalsIgnoreCase(name) || "Target ESP".equalsIgnoreCase(name)) {
            TargetEsp te = moduleManager.getTargetEsp();
            list.add(new ModeSetting("Стиль", Arrays.stream(TargetEsp.Style.values()).map(TargetEsp.Style::label).toList(),
                    () -> te.getStyle() != null ? te.getStyle().label() : "Прицел",
                    v -> {
                        for (TargetEsp.Style s : TargetEsp.Style.values()) {
                            if (s.label().equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v)) {
                                te.setStyle(s);
                                break;
                            }
                        }
                    }));
            list.add(new SliderSetting("Анимация", 0.2F, 3.0F, 0.1F, "x", te::getAnimationSpeed, te::setAnimationSpeed));
            list.add(new SliderSetting("Дистанция", 2.0F, 32.0F, 1.0F, "m", te::getMaxDistance, te::setMaxDistance));
            list.add(new SliderSetting("Задержка потери", 0.2F, 4.0F, 0.1F, "s", te::getLostDelaySeconds, te::setLostDelaySeconds));
            list.add(new ModeSetting("Цели", List.of("Игроки", "Мобы", "Все"),
                    () -> te.getTargetFilter() != null ? te.getTargetFilter().label() : "Игроки",
                    v -> {
                        for (TargetEsp.TargetFilter f : TargetEsp.TargetFilter.values()) {
                            if (f.label().equalsIgnoreCase(v) || f.name().equalsIgnoreCase(v)) {
                                te.setTargetFilter(f);
                                break;
                            }
                        }
                    }));
            list.add(new BooleanSetting("Красный при ударе", te::isRedOnHit, te::setRedOnHit));
            list.add(new ColorSetting("Цвет ESP", te::getArgbColor, te::setArgbColor));

            // Standard styles size
            list.add(new SliderSetting("Размер прицела", 0.45F, 2.0F, 0.05F, "x", te::getNormalSize, te::setNormalSize)
                    .visible(() -> te.getStyle() != TargetEsp.Style.CRYSTALS && te.getStyle() != TargetEsp.Style.GHOSTS));

            // Crystal settings (only for CRYSTALS)
            list.add(new SliderSetting("Размер кристаллов", 0.55F, 2.2F, 0.05F, "", te::getCrystalSize, te::setCrystalSize)
                    .visible(() -> te.getStyle() == TargetEsp.Style.CRYSTALS));
            list.add(new SliderSetting("Количество кристаллов", 1.0F, 12.0F, 1.0F, "", () -> (float) te.getCrystalCount(), v -> te.setCrystalCount(Math.round(v)))
                    .visible(() -> te.getStyle() == TargetEsp.Style.CRYSTALS));
            list.add(new SliderSetting("Скорость кристаллов", 0.2F, 4.0F, 0.1F, "x", te::getCrystalSpeed, te::setCrystalSpeed)
                    .visible(() -> te.getStyle() == TargetEsp.Style.CRYSTALS));
            list.add(new SliderSetting("Радиус кристаллов", 0.35F, 1.65F, 0.05F, "m", te::getCrystalRadius, te::setCrystalRadius)
                    .visible(() -> te.getStyle() == TargetEsp.Style.CRYSTALS));

            // Ghost settings (only for GHOSTS)
            list.add(new SliderSetting("Размер призраков", 0.45F, 3.5F, 0.05F, "", te::getGhostSize, te::setGhostSize)
                    .visible(() -> te.getStyle() == TargetEsp.Style.GHOSTS));
            list.add(new SliderSetting("Количество призраков", 1.0F, 12.0F, 1.0F, "", () -> (float) te.getGhostCount(), v -> te.setGhostCount(Math.round(v)))
                    .visible(() -> te.getStyle() == TargetEsp.Style.GHOSTS));
            list.add(new SliderSetting("Длина призраков", 0.4F, 5.0F, 0.1F, "", te::getGhostLength, te::setGhostLength)
                    .visible(() -> te.getStyle() == TargetEsp.Style.GHOSTS));
            list.add(new SliderSetting("Скорость призраков", 0.2F, 5.0F, 0.1F, "x", te::getGhostSpeed, te::setGhostSpeed)
                    .visible(() -> te.getStyle() == TargetEsp.Style.GHOSTS));
        } else if ("TargetHud".equalsIgnoreCase(name)) {
            TargetHud th = moduleManager.getTargetHud();
            list.add(new BooleanSetting("Красный при уроне", th::isRedOnDamage, th::setRedOnDamage));
            list.add(new BooleanSetting("Частицы при ударе", th::isHitParticles, th::setHitParticles));
            list.add(new SliderSetting("Размер HUD", 0.75F, 1.8F, 0.05F, "x", th::getScale, th::setScale));
        } else if ("Crosshair".equalsIgnoreCase(name)) {
            Crosshair cross = moduleManager.getCrosshair();
            list.add(new ModeSetting("Пресет", Arrays.stream(Crosshair.Preset.values()).map(Crosshair.Preset::label).toList(), () -> cross.getPreset().label(), v -> {
                for (Crosshair.Preset p : Crosshair.Preset.values()) if (p.label().equalsIgnoreCase(v)) cross.applyPreset(p);
            }));
            list.add(new ModeSetting("Форма", List.of("Плюс", "Т", "Углы", "Ромб", "Кольцо"), () -> switch (cross.getShapeName()) {
                case "T" -> "Т"; case "CORNERS" -> "Углы"; case "DIAMOND" -> "Ромб"; case "RING" -> "Кольцо"; default -> "Плюс";
            }, v -> { cross.setShapeName(switch (v) { case "Т" -> "T"; case "Углы" -> "CORNERS"; case "Ромб" -> "DIAMOND"; case "Кольцо" -> "RING"; default -> "PLUS"; }); }));
            list.add(new SliderSetting("Размер", 1.0F, 20.0F, 0.5F, "px", cross::getSize, cross::setSize));
            list.add(new SliderSetting("Зазор", 0.0F, 15.0F, 0.5F, "px", cross::getGap, cross::setGap));
            list.add(new SliderSetting("Толщина", 1.0F, 8.0F, 0.5F, "px", cross::getThickness, cross::setThickness));
            list.add(new BooleanSetting("Точка в центре", cross::isDot, cross::setDot));
            list.add(new BooleanSetting("Обводка", cross::isOutline, cross::setOutline));
            list.add(new BooleanSetting("Красный на цели", cross::isRedOnTarget, cross::setRedOnTarget));
            list.add(new BooleanSetting("В третьем лице", cross::isShowInThirdPerson, cross::setShowInThirdPerson));
            list.add(new ColorSetting("Цвет прицела", cross::getColorArgb, cross::setColorArgb));
        } else if ("Zoom".equalsIgnoreCase(name)) {
            Zoom zoom = moduleManager.getZoom();
            list.add(new KeybindSetting("Бинд зума", zoom::getKeyBind, zoom::setKeyBind));
            list.add(new SliderSetting("Плавность", 0.1F, 1.0F, 0.05F, "", zoom::getSmoothness, zoom::setSmoothness));
            list.add(new BooleanSetting("Зум колесиком", zoom::isWheelZoomEnabled, zoom::setWheelZoomEnabled));
        } else if ("FreeLook".equalsIgnoreCase(name)) {
            FreeLook fl = moduleManager.getFreeLook();
            list.add(new ModeSetting("Режим активации", List.of("Удержание", "Переключение"),
                    () -> fl.getActivationMode() == FreeLook.ActivationMode.TOGGLE ? "Переключение" : "Удержание",
                    v -> fl.setActivationMode("Переключение".equalsIgnoreCase(v) ? FreeLook.ActivationMode.TOGGLE : FreeLook.ActivationMode.HOLD)));
            list.add(new KeybindSetting("Бинд камеры", fl::getKeyBind, fl::setKeyBind));
            list.add(new SliderSetting("Дистанция камеры", 2.0F, 12.0F, 0.5F, "m", fl::getCameraDistance, fl::setCameraDistance));
        } else if ("ItemSwap".equalsIgnoreCase(name) || "AutoSwap".equalsIgnoreCase(name)) {
            AutoSwap as = moduleManager.getAutoSwap();
            list.add(new KeybindSetting("Бинд свапа", as::getKeyBind, as::setKeyBind));
            list.add(new ModeSetting("Первый предмет", List.of("Талисман", "Сфера"),
                    () -> as.getFirstItem() == AutoSwap.SwapItem.SPHERE ? "Сфера" : "Талисман",
                    v -> as.setFirstItem("Сфера".equalsIgnoreCase(v) ? AutoSwap.SwapItem.SPHERE : AutoSwap.SwapItem.TALISMAN)));
            list.add(new ModeSetting("Второй предмет", List.of("Сфера", "Талисман"),
                    () -> as.getSecondItem() == AutoSwap.SwapItem.TALISMAN ? "Талисман" : "Сфера",
                    v -> as.setSecondItem("Талисман".equalsIgnoreCase(v) ? AutoSwap.SwapItem.TALISMAN : AutoSwap.SwapItem.SPHERE)));
        } else if ("AspectRatio".equalsIgnoreCase(name)) {
            AspectRatio ar = moduleManager.getAspectRatio();
            list.add(new ModeSetting("Режим", List.of("Кастом", "4:3", "16:9", "16:10", "3:2", "21:9", "1:1", "5:4"),
                    () -> switch (ar.getPreset()) {
                        case FOUR_THREE -> "4:3";
                        case SIXTEEN_NINE -> "16:9";
                        case SIXTEEN_TEN -> "16:10";
                        case THREE_TWO -> "3:2";
                        case TWENTY_ONE_NINE -> "21:9";
                        case ONE_ONE -> "1:1";
                        case FIVE_FOUR -> "5:4";
                        case CUSTOM -> "Кастом";
                    },
                    v -> {
                        AspectRatio.Preset p = switch (v) {
                            case "4:3" -> AspectRatio.Preset.FOUR_THREE;
                            case "16:9" -> AspectRatio.Preset.SIXTEEN_NINE;
                            case "16:10" -> AspectRatio.Preset.SIXTEEN_TEN;
                            case "3:2" -> AspectRatio.Preset.THREE_TWO;
                            case "21:9" -> AspectRatio.Preset.TWENTY_ONE_NINE;
                            case "1:1" -> AspectRatio.Preset.ONE_ONE;
                            case "5:4" -> AspectRatio.Preset.FIVE_FOUR;
                            default -> AspectRatio.Preset.CUSTOM;
                        };
                        ar.setPreset(p);
                    }));
            list.add(new SliderSetting("Соотношение", 0.5F, 3.0F, 0.05F, "", ar::getCustomRatio, ar::setCustomRatio)
                    .visible(() -> ar.getPreset() == AspectRatio.Preset.CUSTOM));
        } else if ("Particles".equalsIgnoreCase(name)) {
            Particles part = moduleManager.getParticles();
            list.add(new ModeSetting("Текстура", List.of("Звезда", "Искры", "Снежинка", "Сердце", "Свечение", "Доллар"),
                    () -> switch (part.getPreviewTexture()) {
                        case SPARKLE -> "Искры";
                        case SNOWFLAKE -> "Снежинка";
                        case HEART -> "Сердце";
                        case GLOW -> "Свечение";
                        case DOLLAR -> "Доллар";
                        default -> "Звезда";
                    },
                    v -> {
                        Particles.TextureType type = switch (v) {
                            case "Искры" -> Particles.TextureType.SPARKLE;
                            case "Снежинка" -> Particles.TextureType.SNOWFLAKE;
                            case "Сердце" -> Particles.TextureType.HEART;
                            case "Свечение" -> Particles.TextureType.GLOW;
                            case "Доллар" -> Particles.TextureType.DOLLAR;
                            default -> Particles.TextureType.STAR;
                        };
                        part.setPreviewTexture(type);
                    }));
            list.add(new SliderSetting("Количество", 1.0F, 64.0F, 1.0F, "", () -> (float) part.getAmount(), v -> part.setAmount(Math.round(v))));
            list.add(new SliderSetting("Размер", 0.05F, 0.5F, 0.01F, "", part::getSize, part::setSize));
            list.add(new SliderSetting("Время жизни", 0.5F, 5.0F, 0.1F, "s", part::getLifeSeconds, part::setLifeSeconds));
            list.add(new ColorSetting("Цвет частиц", part::getArgbColor, part::setArgbColor));
        } else if ("JumpCircles".equalsIgnoreCase(name)) {
            JumpCircles jc = moduleManager.getJumpCircles();
            list.add(new ModeSetting("Режим", List.of("Круг", "Кольцо", "Flux", "Блоки"),
                    () -> jc.getMode() == JumpCircles.Mode.BLOCKS ? "Блоки" : jc.getTextureType().label(),
                    v -> {
                        if ("Блоки".equalsIgnoreCase(v)) {
                            jc.setMode(JumpCircles.Mode.BLOCKS);
                        } else if ("Кольцо".equalsIgnoreCase(v)) {
                            jc.setMode(JumpCircles.Mode.NORMAL);
                            jc.setTextureType(JumpCircles.TextureType.RING);
                        } else if ("Flux".equalsIgnoreCase(v)) {
                            jc.setMode(JumpCircles.Mode.NORMAL);
                            jc.setTextureType(JumpCircles.TextureType.FLUX);
                        } else {
                            jc.setMode(JumpCircles.Mode.NORMAL);
                            jc.setTextureType(JumpCircles.TextureType.CIRCLE);
                        }
                    }));
            list.add(new BooleanSetting("Частицы", jc::isParticlesEnabled, jc::setParticlesEnabled));
            list.add(new ModeSetting("Текстура частиц", List.of("Звезда", "Искры", "Снежинка", "Сердце", "Свечение", "Доллар"),
                    () -> jc.getParticleTexture().label(),
                    v -> {
                        for (Particles.TextureType t : Particles.TextureType.values()) {
                            if (t.label().equalsIgnoreCase(v) || t.name().equalsIgnoreCase(v)) {
                                jc.setParticleTexture(t);
                                break;
                            }
                        }
                    }).visible(jc::isParticlesEnabled));
            list.add(new SliderSetting("Количество частиц", 1.0F, 96.0F, 1.0F, "", () -> (float) jc.getAmount(), v -> jc.setAmount(Math.round(v))).visible(jc::isParticlesEnabled));
            list.add(new SliderSetting("Размер частиц", 0.25F, 2.5F, 0.05F, "", jc::getParticleSize, jc::setParticleSize).visible(jc::isParticlesEnabled));
            list.add(new SliderSetting("Разброс", 0.0F, 1.0F, 0.01F, "", jc::getSpread, jc::setSpread).visible(jc::isParticlesEnabled));
            list.add(new SliderSetting("Время жизни", 0.5F, 3.0F, 0.1F, "s", jc::getLifeSeconds, jc::setLifeSeconds));
            list.add(new SliderSetting("Размер круга", 0.35F, 3.0F, 0.05F, "", jc::getSize, jc::setSize).visible(() -> jc.getMode() != JumpCircles.Mode.BLOCKS));
            list.add(new ColorSetting("Цвет кругов", jc::getArgbColor, jc::setArgbColor));
        } else if ("Trails".equalsIgnoreCase(name)) {
            Trails trails = moduleManager.getTrails();
            list.add(new SliderSetting("Длина шлейфа", 5.0F, 50.0F, 1.0F, "pts", trails::getMaxLength, trails::setMaxLength));
            list.add(new ColorSetting("Цвет шлейфа", trails::getArgbColor, trails::setArgbColor));
        } else if ("Removals".equalsIgnoreCase(name)) {
            Removals rem = moduleManager.getRemovals();
            list.add(new BooleanSetting("Огонь на экране", rem::isFireOverlay, rem::setFireOverlay));
            list.add(new BooleanSetting("Свечение сущностей", rem::isEntityGlowing, rem::setEntityGlowing));
            list.add(new BooleanSetting("Плохая погода", rem::isBadWeather, rem::setBadWeather));
            list.add(new BooleanSetting("Тряска от урона", rem::isHurtCamera, rem::setHurtCamera));
            list.add(new BooleanSetting("FOV спринта", rem::isSprintFov, rem::setSprintFov));
            list.add(new BooleanSetting("Пузыри песка душ", rem::isSoulSandBubbles, rem::setSoulSandBubbles));
        } else if ("AnarchySwitcher".equalsIgnoreCase(name)) {
            AnarchySwitcher as = moduleManager.getAnarchySwitcher();
            list.add(new SliderSetting("Задержка смены", 0.25F, 300.0F, 0.25F, "s", as::getDelaySeconds, as::setDelaySeconds));
            list.add(new StringSetting("Список анархий", "101, 102...", () -> String.join(", ", as.getAnarchyIds()), v -> {
                if (v == null || v.isBlank()) {
                    as.setAnarchyIds(Collections.emptyList());
                } else {
                    String[] parts = v.split("[,; ]+");
                    List<String> ids = new ArrayList<>();
                    for (String p : parts) if (!p.isBlank()) ids.add(p.trim());
                    as.setAnarchyIds(ids);
                }
            }));
            list.add(new BooleanSetting("Авто-реклама в чат", as::isAdEnabled, as::setAdEnabled));
            list.add(new StringSetting("Текст рекламы", "/ah sell 150k", as::getAdText, as::setAdText).visible(as::isAdEnabled));
        } else if ("AutoBuy".equalsIgnoreCase(name)) {
            AutoBuy ab = moduleManager.getAutoBuy();
            ItemResorter ir = moduleManager.getItemResorter();
            list.add(new BooleanSetting("Свитч по анархиям", ab::isAnarchySwitchEnabled, ab::setAnarchySwitchEnabled));
            list.add(new BooleanSetting("Авто-перепродажа", ab::isAutoResellEnabled, ab::setAutoResellEnabled));
            list.add(new BooleanSetting("Анти-слив баланса", ab::isLowBalanceGuardEnabled, ab::setLowBalanceGuardEnabled));
            list.add(new StringSetting("Нужные чары", "Острота V, Заговор огня",
                    () -> String.join(", ", ir.getEnchantNeeded()),
                    v -> {
                        ir.getEnchantNeeded().clear();
                        for (String s : v.split(",")) {
                            String trimmed = s.trim();
                            if (!trimmed.isEmpty()) ir.addEnchantNeeded(trimmed);
                        }
                    }));
            list.add(new StringSetting("Игнор чары", "Проклятие, Нестабильный",
                    () -> String.join(", ", ir.getEnchantIgnored()),
                    v -> {
                        ir.getEnchantIgnored().clear();
                        for (String s : v.split(",")) {
                            String trimmed = s.trim();
                            if (!trimmed.isEmpty()) ir.addEnchantIgnored(trimmed);
                        }
                    }));
            list.add(new StringSetting("Макс. цена", "150000",
                    () -> Long.toString(ir.getMaxPrice()),
                    v -> { try { ir.setMaxPrice(Long.parseLong(v.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {} }));
        } else if ("AutoResellAFK".equalsIgnoreCase(name)) {
            AutoResellAFK afk = moduleManager.getAutoResellAFK();
            list.add(new BooleanSetting("Уведомления в чат", afk::isChatEnabled, afk::setChatEnabled));
            list.add(new StringSetting("Сообщение в чат", "!sell", afk::getChatMessage, afk::setChatMessage).visible(afk::isChatEnabled));
            list.add(new SliderSetting("Интервал сообщений", 5.0F, 300.0F, 5.0F, "s", () -> (float) (afk.getChatIntervalMs() / 1000L), v -> afk.setChatIntervalMs(Math.round(v * 1000L))).visible(afk::isChatEnabled));
            list.add(new BooleanSetting("Продавать мечи", afk::isSellPurchasedSwords, afk::setSellPurchasedSwords));
            list.add(new StringSetting("Цена продажи", "150000", afk::getSellPrice, afk::setSellPrice).visible(afk::isSellPurchasedSwords));
        } else if ("HitboxCustomizer".equalsIgnoreCase(name) || "Hitbox Customizer".equalsIgnoreCase(name)) {
            HitboxCustomizer hc = moduleManager.getHitboxCustomizer();
            list.add(new MultiModeSetting("Цели", List.of("Игроки", "Мобы", "Предметы", "Снаряды"), hc::getTargets, hc::setTargets));
            list.add(new BooleanSetting("Всегда показывать", hc::isAlwaysShow, hc::setAlwaysShow));
            list.add(new BooleanSetting("Заливка бокса", hc::isFillEnabled, hc::setFillEnabled));
            list.add(new BooleanSetting("Только углы", hc::isCornersOnly, hc::setCornersOnly));
            list.add(new BooleanSetting("Вектор взгляда", hc::isShowLookVector, hc::setShowLookVector));
            list.add(new BooleanSetting("Включая себя", hc::isIncludeSelf, hc::setIncludeSelf));
            list.add(new SliderSetting("Толщина линий", 0.5F, 5.0F, 0.1F, "px", hc::getLineThickness, hc::setLineThickness));
            list.add(new ColorSetting("Цвет обводки", hc::getOutlineArgbColor, hc::setOutlineArgbColor));
            list.add(new ColorSetting("Цвет заливки", hc::getFillArgbColor, hc::setFillArgbColor).visible(hc::isFillEnabled));
        } else if ("ItemRadius".equalsIgnoreCase(name)) {
            ItemRadius ir = moduleManager.getItemRadius();
            list.add(new MultiModeSetting("Предметы", List.of("Дезка", "Явка", "Огненый Заряд", "Божья Аура", "Трапка", "Пласт", "Снежок Заморозка"),
                    ir::getSelectedLabels, ir::setSelectedLabels));
            list.add(new BooleanSetting("Драконья трапка", ir::isDraconicTrap, ir::setDraconicTrap).visible(() -> ir.isItemEnabledPublic(ItemRadius.RadiusItem.TRAP)));
        } else if ("ItemResorter".equalsIgnoreCase(name)) {
            ItemResorter ir = moduleManager.getItemResorter();
            list.add(new BooleanSetting("Фильтр цены", ir::isPriceFilterEnabled, ir::setPriceFilterEnabled));
            list.add(new StringSetting("Макс. цена", "150000", () -> Long.toString(ir.getMaxPrice()), v -> { try { ir.setMaxPrice(Long.parseLong(v.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {} }).visible(ir::isPriceFilterEnabled));
            list.add(new BooleanSetting("Фильтр прочности", ir::isDurabilityFilterEnabled, ir::setDurabilityFilterEnabled));
            list.add(new SliderSetting("Мин. прочность", 1.0F, 100.0F, 1.0F, "%", () -> (float) ir.getMinDurabilityPercent(), v -> ir.setMinDurabilityPercent(Math.round(v))).visible(ir::isDurabilityFilterEnabled));
        } else if ("Animations".equalsIgnoreCase(name)) {
            Animations anim = moduleManager.getAnimations();
            list.add(new BooleanSetting("Анимация таба", anim::isTabEnabled, anim::setTabEnabled));
            list.add(new BooleanSetting("Анимация 3-го лица", anim::isThirdPersonEnabled, anim::setThirdPersonEnabled));
            list.add(new BooleanSetting("Плавный хотбар", anim::isHotbarEnabled, anim::setHotbarEnabled));
            list.add(new BooleanSetting("Анимация инвентаря", anim::isInventoryEnabled, anim::setInventoryEnabled));
        } else if ("TabCustomizer".equalsIgnoreCase(name)) {
            TabCustomizer tc = moduleManager.getTabCustomizer();
            list.add(new SliderSetting("Колонки", 1.0F, 8.0F, 1.0F, "", () -> (float) tc.getColumns(), v -> tc.setColumns(Math.round(v))));
            list.add(new SliderSetting("Игроков в колонке", 1.0F, 40.0F, 1.0F, "", () -> (float) tc.getPlayersPerColumn(), v -> tc.setPlayersPerColumn(Math.round(v))));
            list.add(new SliderSetting("Масштаб", 0.5F, 1.5F, 0.05F, "x", tc::getScale, tc::setScale));
        } else if ("ElytraSwap".equalsIgnoreCase(name)) {
            ElytraSwap es = moduleManager.getElytraSwap();
            list.add(new KeybindSetting("Бинд свапа", es::getKeyBind, es::setKeyBind));
            list.add(new KeybindSetting("Бинд фейерверка", es::getFireworkKeyBind, es::setFireworkKeyBind));
        } else if ("TapeMouse".equalsIgnoreCase(name)) {
            TapeMouse tm = moduleManager.getTapeMouse();
            list.add(new ModeSetting("Кнопка мыши", List.of("Левая", "Правая"), () -> tm.getButtonMode() == TapeMouse.ButtonMode.RIGHT ? "Правая" : "Левая", v -> tm.setButtonMode("Правая".equalsIgnoreCase(v) ? TapeMouse.ButtonMode.RIGHT : TapeMouse.ButtonMode.LEFT)));
            list.add(new SliderSetting("Задержка клика", 10.0F, 1000.0F, 10.0F, "ms", () -> (float) tm.getDelayMs(), v -> tm.setDelayMs(Math.round(v))));
        } else if ("Watermark".equalsIgnoreCase(name)) {
            Watermark wm = moduleManager.getWatermark();
            list.add(new BooleanSetting("Музыкальный бар", wm::isMusicEnabled, wm::setMusicEnabled));
            list.add(new BooleanSetting("Уведомления", wm::isNotificationsEnabled, wm::setNotificationsEnabled));
        } else if ("DiscordRPC".equalsIgnoreCase(name)) {
            DiscordRPC rpc = moduleManager.getDiscordRPC();
            list.add(new BooleanSetting("Кастомная кнопка", rpc::isCustomButtonEnabled, rpc::setCustomButtonEnabled));
            list.add(new StringSetting("Текст кнопки", "Join Discord", rpc::getCustomButtonLabel, rpc::setCustomButtonLabel).visible(rpc::isCustomButtonEnabled));
            list.add(new StringSetting("Ссылка кнопки", "https://discord.gg/...", rpc::getCustomButtonUrl, rpc::setCustomButtonUrl).visible(rpc::isCustomButtonEnabled));
        } else if ("NameProtect".equalsIgnoreCase(name)) {
            NameProtect np = moduleManager.getNameProtect();
            list.add(new StringSetting("Подмена ника", "Protected", np::getReplacement, np::setReplacement));
            list.add(new BooleanSetting("Скрывать анархию", np::isProtectAnarchy, np::setProtectAnarchy));
            list.add(new StringSetting("Текст анархии", "0000", np::getAnarchyReplacement, np::setAnarchyReplacement).visible(np::isProtectAnarchy));
        } else if ("ItemScroller".equalsIgnoreCase(name)) {
            ItemScroller is = moduleManager.getItemScroller();
            list.add(new SliderSetting("Задержка", 0.0F, 100.0F, 5.0F, "ms", () -> (float) is.getDelayMs(), v -> is.setDelayMs(Math.round(v))));
        } else if ("AimBot".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.AimBot ab = moduleManager.getAimBot();
            list.add(new ModeSetting("Цели", List.of("Игроки", "Мобы", "Все"), ab::getTargetsName, ab::setTargetsName));
            list.add(new SliderSetting("Скорость Yaw", 1.0F, 30.0F, 0.5F, "", ab::getYawSpeed, ab::setYawSpeed));
            list.add(new SliderSetting("Скорость Pitch", 1.0F, 30.0F, 0.5F, "", ab::getPitchSpeed, ab::setPitchSpeed));
            list.add(new BooleanSetting("Наводка Yaw", ab::isAimYaw, ab::setAimYaw));
            list.add(new BooleanSetting("Наводка Pitch", ab::isAimPitch, ab::setAimPitch));
            list.add(new SliderSetting("Хуманайз", 0.0F, 100.0F, 1.0F, "%", ab::getHumanize, ab::setHumanize));
            list.add(new SliderSetting("FOV", 5.0F, 360.0F, 1.0F, "°", ab::getFov, ab::setFov));
            list.add(new SliderSetting("Дистанция", 2.0F, 10.0F, 0.1F, "m", ab::getDistance, ab::setDistance));
            list.add(new BooleanSetting("Держать цель", ab::isStickyTarget, ab::setStickyTarget));
            list.add(new BooleanSetting("Проверка стен", ab::isCheckWalls, ab::setCheckWalls));
            list.add(new BooleanSetting("Общая кнопка Aim+Trigger", ab::isComboEnabled, ab::setComboEnabled));
            list.add(new KeybindSetting("Кнопка связки", ab::getComboKey, ab::setComboKey).visible(ab::isComboEnabled));
        } else if ("TriggerBot".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.TriggerBot tb = moduleManager.getTriggerBot();
            list.add(new ModeSetting("Цели", List.of("Игроки", "Мобы", "Все"), tb::getTargetsName, tb::setTargetsName));
            list.add(new BooleanSetting("Прыгать для крита", tb::isJumpForCrit, tb::setJumpForCrit));
            list.add(new BooleanSetting("Рандомизация задержки +50–150 мс", tb::isCooldownJitter, tb::setCooldownJitter));
            list.add(new SliderSetting("Шанс крит-тайминга", 1.0F, 100.0F, 1.0F, "%", tb::getChance, tb::setChance));
            list.add(new BooleanSetting("Не бить при еде/блоке", tb::isNoUseHit, tb::setNoUseHit));
        } else if ("AutoMace".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.AutoMace am = moduleManager.getAutoMace();
            list.add(new BooleanSetting("Только с триггером", am::isRequireTrigger, am::setRequireTrigger));
            list.add(new BooleanSetting("Возвращать предмет", am::isRestoreItem, am::setRestoreItem));
            list.add(new SliderSetting("Таймаут", 500.0F, 10000.0F, 100.0F, "ms", am::getTimeoutMs, am::setTimeoutMs));
        } else if ("NoJumpDelay".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.NoJumpDelay njd = moduleManager.getNoJumpDelay();
            list.add(new BooleanSetting("Рандомизация прыжков", njd::isJumpRandomize, njd::setJumpRandomize));
        }
    }

    public static String getModuleDescription(Module mod, boolean isEn) {
        if (mod == null) return "";
        String name = mod.getName();
        if (isEn) {
            return switch (name) {
                case "FullBright" -> "Provides unlimited night vision without potion effects.";
                case "ChinaHat" -> "Renders a stylish conical hat above the player's head.";
                case "Wings" -> "Renders animated customizable 3D wings on player's back.";
                case "AspectRatio" -> "Changes aspect ratio of viewport (stretched display).";
                case "Particles" -> "Custom movement and attack hit particle effects.";
                case "JumpCircles" -> "Renders expanding glowing rings upon jumping.";
                case "Trails" -> "Draws a smooth trail following the player movement.";
                case "Removals" -> "Disables unwanted visual obstructions and screen fire.";
                case "WorldCustomizer", "World Customizer" -> "Customizes world time, fog distance and fog color.";
                case "SafeNametag" -> "Hides player nametags on screen for security.";
                case "Hit Color", "HitColor" -> "Customizes color and transparency of damage flash.";
                case "HitboxCustomizer", "Hitbox Customizer" -> "Renders customizable colored hitboxes on entities.";
                case "BlockOverlay" -> "Smooth animation and highlight for targeted blocks.";
                case "TargetEsp" -> "Animated 3D markers (crystals, ghosts) around target.";
                case "TargetHud" -> "Information panel displaying target's health and head.";
                case "ItemRadius" -> "Displays usage radius for items held in hands.";
                case "Animations" -> "Smooth UI, tab, inventory, and hotbar animations.";
                case "TabCustomizer" -> "Clean compact player list in tab menu.";
                case "FakePlayer" -> "Spawns local bots for practicing hits and combos.";
                case "AutoSprint" -> "Automatically keeps sprint active while moving.";
                case "AutoSwap", "ItemSwap" -> "Fast automatic hotbar item swap via keybind.";
                case "ElytraSwap" -> "Quickly switches between chestplate and elytra.";
                case "ItemResorter" -> "Auto-sorts inventory items into designated slots.";
                case "TrapTracker" -> "Action timer for regular and draconic traps.";
                case "FreeLook" -> "Free camera overview without changing body angle.";
                case "Zoom" -> "Smooth camera zoom when holding designated keybind.";
                case "Crosshair" -> "Custom stylish crosshair with animations and dot.";
                case "Watermark" -> "Client watermark with integrated music player info.";
                case "AnarchySwitcher" -> "Automated cyclical server anarchy switching.";
                case "NameBind" -> "Quick command dispatch with /name via keybinds.";
                case "Macros" -> "Automated command and chat message bindings.";
                case "TapeMouse" -> "Automated holding or clicking of mouse buttons.";
                case "AutoResell" -> "Automated auction item listing and reselling.";
                case "AutoResellAFK" -> "Background storage /ah reselling during AFK.";
                case "AHHelper" -> "Assistance and hotkeys for fast auction buyouts.";
                case "AutoBuy" -> "Automated auction scanner and smart item buyout.";
                case "ItemCrafter" -> "Auto-crafts golden apples in workbench and lists them on /ah.";
                case "CaptchaSolver" -> "Solves FunTime captcha from item-frame maps via YOLO model, background-friendly.";
                case "TG", "Telegram" -> "Remote notifications and control bot via Telegram.";
                case "DiscordRPC" -> "Displays custom game activity status in Discord.";
                case "NameProtect" -> "Masks actual player username and anarchy server ID.";
                case "ItemScroller" -> "Rapid item movement by mouse wheel scrolling.";
                case "AimBot" -> "Smooth human-like FPS aim assist with multipoints.";
                case "TriggerBot" -> "Auto-attacks under crosshair with smart crits.";
                case "AutoMace" -> "Auto-holds mace after wind charge throw.";
                case "NoJumpDelay" -> "Removes jump cooldown for instant re-jumps.";
                case "Menu" -> "Scale, theme, language, and keybind settings.";
                default -> "Module is ready and activated via keybind or toggle.";
            };
        } else {
            return switch (name) {
                case "FullBright" -> "Дает бесконечное ночное зрение без эффекта зелья.";
                case "ChinaHat" -> "Отображает коническую шляпу над головой.";
                case "Wings" -> "Отображает анимированные 3D крылья за спиной игрока.";
                case "AspectRatio" -> "Позволяет изменить соотношение сторон экрана.";
                case "Particles" -> "Кастомные эффекты частиц при движении и ударах.";
                case "JumpCircles" -> "Расширяющиеся кольца на земле при прыжках.";
                case "Trails" -> "Плавный шлейф позади движения игрока.";
                case "Removals" -> "Отключает лишние визуальные эффекты и огонь.";
                case "WorldCustomizer", "World Customizer" -> "Кастомизация времени суток, плотности и цвета тумана.";
                case "SafeNametag" -> "Скрывает ники игроков на экране для безопасности.";
                case "Hit Color", "HitColor" -> "Настройка цвета и прозрачности вспышки при получении урона.";
                case "HitboxCustomizer", "Hitbox Customizer" -> "Отображает настраиваемые цветные хитбоксы сущностей.";
                case "BlockOverlay" -> "Подсветка и анимация выделенного блока при наведении прицела.";
                case "TargetEsp" -> "Маркер и 3D анимации (кристаллы, призраки) вокруг текущей цели.";
                case "TargetHud" -> "Информационная панель со здоровьем и головой цели.";
                case "ItemRadius" -> "Радиус действия предметов в руках (дезка, явка, трапка и др.).";
                case "Animations" -> "Плавные анимации интерфейса, таба, инвентаря и хотбара.";
                case "TabCustomizer" -> "Кастомный компактный список игроков в табе.";
                case "FakePlayer" -> "Создает локальных ботов для тренировки ударов и комбо.";
                case "AutoSprint" -> "Автоматически удерживает спринт во время бега.";
                case "AutoSwap", "ItemSwap" -> "Автоматическая быстрая смена предметов по бинду.";
                case "ElytraSwap" -> "Быстрое переключение между нагрудником и элитрами.";
                case "ItemResorter" -> "Автоматическая сортировка предметов по слотам хотбара.";
                case "TrapTracker" -> "Таймер действия обычной и драконьей трапки по скрапу.";
                case "FreeLook" -> "Свободный обзор камерой без изменения направления взгляда персонажа.";
                case "Zoom" -> "Плавное приближение камеры по нажатию горячей клавиши.";
                case "Crosshair" -> "Кастомный стильный прицел с точкой и анимациями.";
                case "Watermark" -> "Водяной знак клиента с музыкальным плеером и инфо.";
                case "AnarchySwitcher" -> "Автоматическое циклическое переключение между анархиями.";
                case "NameBind" -> "Быстрая отправка команд /name по горячим клавишам.";
                case "Macros" -> "Автоматическая отправка команд и сообщений по биндам.";
                case "TapeMouse" -> "Автоматический зажим или кликер левой/правой кнопки мыши.";
                case "AutoResell" -> "Автоматическое выставление и перепродажа предметов на аукционе.";
                case "AutoResellAFK" -> "Фоновая перепродажа в хранилище /ah во время AFK.";
                case "AHHelper" -> "Помощник и горячие клавиши для быстрого выкупа на аукционе.";
                case "AutoBuy" -> "Автоматический сканер и скупка выгодных предметов на аукционе.";
                case "ItemCrafter" -> "Крафтит яблоки в верстаке, снимает с аукциона и выставляет через /ah sellgui.";
                case "CaptchaSolver" -> "Решает капчу с карт в рамках через YOLO-модель, работает на ботах фоном.";
                case "TG", "Telegram" -> "Уведомления и управление ботом через Telegram.";
                case "DiscordRPC" -> "Отображение статуса игры в Discord профиле.";
                case "NameProtect" -> "Замена реального никнейма и номера анархии на экране.";
                case "ItemScroller" -> "Быстрое перемещение предметов по прокрутке колеса мыши.";
                case "AimBot" -> "Плавная хуманизированная FPS-наводка с мультипоинтами.";
                case "TriggerBot" -> "Авто-удар по прицелу с умными критами.";
                case "AutoMace" -> "Сам берёт булаву после заряда ветра.";
                case "NoJumpDelay" -> "Убирает задержку прыжка для мгновенных репрыгов.";
                case "Menu" -> "Настройка масштаба, языка и горячей клавиши меню.";
                default -> "Модуль готов к работе и управляется горячей клавишей.";
            };
        }
    }

    private static List<String> wrapText(String text, int maxCharsPerLine) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.length() + w.length() + 1 > maxCharsPerLine) {
                if (cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(); }
            }
            if (cur.length() > 0) cur.append(" ");
            cur.append(w);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private float getGuiScale() {
        return moduleManager != null && moduleManager.getMenu() != null ? moduleManager.getMenu().getScaleMultiplier() : 1.0F;
    }

    private float getCategoryY(Category cat, float startCatY, float catH, float catSpacing, float scale) {
        int ord = cat.ordinal();
        if (ord < 4) {
            return startCatY + ord * (catH + catSpacing);
        } else {
            return startCatY + 4 * (catH + catSpacing) + 24.0F * scale + (ord - 4) * (catH + catSpacing);
        }
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000.0F, 0.1F);
        lastTime = now;
        openProgress = Math.min(1.0F, openProgress + dt * 4.0F);
        if (openProgress <= 0.01F) return;
        float alpha = easeOutCubic(openProgress);

        float speed = settingsOpen ? 3.6F : 4.0F;
        if (settingsOpen) {
            settingsAnim = Math.min(1.0F, settingsAnim + dt * speed);
        } else {
            settingsAnim = Math.max(0.0F, settingsAnim - dt * speed);
        }
        float settingsProgress = easeInOutCubic(settingsAnim);

        BlurRenderer.drawBlur(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, alpha * 0.75F);
        Render2D.drawRound(context, 0.0F, 0.0F, screenWidth, screenHeight, 0.0F, argb(120, 0, 0, 0, alpha));

        GuiBounds b = new GuiBounds(screenWidth, screenHeight, getGuiScale(), settingsProgress);

        // Panel 1: Sidebar (Разделы) - Standalone floating obsidian card
        Render2D.drawShadow(context, b.x, b.y, b.sidebarW, b.h, 20.0F * b.scale, 16.0F * b.scale, argb(150, 0, 0, 0, alpha));
        Render2D.drawRound(context, b.x, b.y, b.sidebarW, b.h, 10.0F * b.scale, argb(252, 11, 13, 18, alpha));
        Render2D.drawRoundOutline(context, b.x, b.y, b.sidebarW, b.h, 10.0F * b.scale, 1.0F, argb(255, 26, 29, 39, alpha));
        Render2D.drawLine(context, b.x + 10.0F * b.scale, b.y + 1.0F, b.x + b.sidebarW - 10.0F * b.scale, b.y + 1.0F, 1.0F, argb(20, 255, 255, 255, alpha));

        drawSidebar(context, b, mouseX, mouseY, alpha, dt);
        drawGroupboxes(context, b, mouseX, mouseY, alpha, dt);
        drawPopups(context, b, mouseX, mouseY, alpha, dt);
    }

    private void drawSidebar(DrawContext context, GuiBounds b, int mouseX, int mouseY, float alpha, float dt) {
        float scale = b.scale;
        boolean en = isEn();
        float logoX = b.x + 20.0F * scale;
        float logoY = b.y + 18.0F * scale;
        ModernFont.drawFluxLogo(context, logoX, logoY, 20.0F * scale, argb(255, 255, 255, 255, alpha));

        float searchX = b.x + 12.0F * scale;
        float searchY = b.y + 48.0F * scale;
        float searchW = b.sidebarW - 24.0F * scale;
        float searchH = 26.0F * scale;

        Render2D.drawRound(context, searchX, searchY, searchW, searchH, 6.0F * scale, argb(255, 15, 17, 24, alpha));
        Render2D.drawRoundOutline(context, searchX, searchY, searchW, searchH, 6.0F * scale, 1.0F, searchFocused ? argb(180, 124, 58, 237, alpha) : argb(255, 26, 29, 39, alpha));
        Render2D.drawLine(context, searchX + 4.0F * scale, searchY + 1.0F, searchX + searchW - 4.0F * scale, searchY + 1.0F, 1.0F, argb(15, 255, 255, 255, alpha));
        
        drawTexture(context, ICON_SEARCH, searchX + 8.0F * scale, searchY + 6.5F * scale, 13.0F * scale, 13.0F * scale, argb(140, 140, 152, 170, alpha));
        String queryText = search.isEmpty()
                ? (searchFocused ? (System.currentTimeMillis() % 1000L < 500L ? "|" : "") : t("Поиск... (Ctrl+F)", "Search... (Ctrl+F)"))
                : (search + (searchFocused && System.currentTimeMillis() % 1000L < 500L ? "|" : ""));
        ModernFont.draw(context, queryText, searchX + 26.0F * scale, centeredTextY(searchY, searchH, 11.0F * scale, ModernFont.Type.INTER_MEDIUM), 11.0F * scale, argb(230, 235, 240, 245, alpha), ModernFont.Type.INTER_MEDIUM);

        if (!search.isEmpty()) {
            float clrSize = 14.0F * scale;
            float clrX = searchX + searchW - clrSize - 6.0F * scale;
            float clrY = searchY + (searchH - clrSize) * 0.5F;
            boolean clrHover = inside(mouseX, mouseY, clrX, clrY, clrSize, clrSize);
            Render2D.drawRound(context, clrX, clrY, clrSize, clrSize, 3.0F * scale, clrHover ? argb(60, 239, 68, 68, alpha) : argb(20, 255, 255, 255, alpha));
            float ccx = clrX + clrSize * 0.5F, ccy = clrY + clrSize * 0.5F, ccr = 2.5F * scale;
            int crossCol = clrHover ? argb(255, 252, 165, 165, alpha) : argb(140, 148, 163, 184, alpha);
            Render2D.drawLine(context, ccx - ccr, ccy - ccr, ccx + ccr, ccy + ccr, 1.0F * scale, crossCol);
            Render2D.drawLine(context, ccx + ccr, ccy - ccr, ccx - ccr, ccy + ccr, 1.0F * scale, crossCol);
        }

        float startCatY = searchY + 34.0F * scale;
        float catH = 32.0F * scale;
        float catSpacing = 4.0F * scale;
        Category[] cats = Category.values();

        float targetIndicatorY = getCategoryY(selectedCategory, startCatY, catH, catSpacing, scale);
        if (catIndicatorY < 0.0F) {
            catIndicatorY = targetIndicatorY;
        } else {
            catIndicatorY = catIndicatorY + (targetIndicatorY - catIndicatorY) * Math.min(1.0F, dt * 14.0F);
        }

        // Active category: subtle deep obsidian-violet container with crisp accent indicator
        Render2D.drawRound(context, b.x + 12.0F * scale, catIndicatorY, b.sidebarW - 24.0F * scale, catH, 6.0F * scale, argb(255, 20, 22, 32, alpha));
        Render2D.drawRoundOutline(context, b.x + 12.0F * scale, catIndicatorY, b.sidebarW - 24.0F * scale, catH, 6.0F * scale, 1.0F, argb(50, 139, 92, 246, alpha));
        
        float indW = 3.0F * scale;
        float indH = 14.0F * scale;
        float indX = b.x + 14.0F * scale;
        float indY = catIndicatorY + (catH - indH) * 0.5F;
        Render2D.drawRound(context, indX, indY, indW, indH, 1.5F * scale, argb(255, 168, 85, 247, alpha));

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            if (i == 4) {
                float sepY = startCatY + 4 * (catH + catSpacing) + 5.0F * scale;
                String utiHeader = t("УТИЛИТЫ", "UTILITIES");
                float titleSize = 8.5F * scale;
                float textH = ModernFont.getHeight(titleSize, ModernFont.Type.INTER_SEMIBOLD);
                float textY = sepY + 1.0F * scale;
                ModernFont.draw(context, utiHeader, b.x + 16.0F * scale, textY, titleSize, argb(160, 115, 128, 150, alpha), ModernFont.Type.INTER_SEMIBOLD);
                float headerW = ModernFont.getWidth(utiHeader, titleSize, ModernFont.Type.INTER_SEMIBOLD);
                float lineX1 = b.x + 16.0F * scale + headerW + 6.0F * scale;
                float lineX2 = b.x + b.sidebarW - 14.0F * scale;
                float lineY = textY + textH * 0.5F;
                Render2D.drawLine(context, lineX1, lineY, lineX2, lineY, 1.0F, argb(255, 26, 30, 42, alpha));
            }

            boolean active = cat == selectedCategory;
            float catY = getCategoryY(cat, startCatY, catH, catSpacing, scale);
            boolean hover = inside(mouseX, mouseY, b.x + 12.0F * scale, catY, b.sidebarW - 24.0F * scale, catH);
            if (!active && hover) {
                Render2D.drawRound(context, b.x + 12.0F * scale, catY, b.sidebarW - 24.0F * scale, catH, 6.0F * scale, argb(12, 255, 255, 255, alpha));
            }
            int iconCol = active ? argb(255, 255, 255, 255, alpha) : (hover ? argb(255, 203, 166, 247, alpha) : argb(160, 148, 163, 184, alpha));
            drawTexture(context, cat.icon, b.x + 22.0F * scale, catY + 7.5F * scale, 17.0F * scale, 17.0F * scale, iconCol);
            int textColor = active ? argb(255, 248, 250, 252, alpha) : (hover ? argb(230, 226, 232, 240, alpha) : argb(160, 148, 163, 184, alpha));
            ModernFont.draw(context, cat.getName(en), b.x + 46.0F * scale, centeredTextY(catY, catH, 12.0F * scale, ModernFont.Type.INTER_MEDIUM), 12.0F * scale, textColor, ModernFont.Type.INTER_MEDIUM);
        }

        float userX = b.x + 12.0F * scale;
        float userY = b.y + b.h - 54.0F * scale;
        float userW = b.sidebarW - 24.0F * scale;
        float userH = 42.0F * scale;
        Render2D.drawRound(context, userX, userY, userW, userH, 6.0F * scale, argb(255, 17, 19, 27, alpha));
        Render2D.drawRoundOutline(context, userX, userY, userW, userH, 6.0F * scale, 1.0F, argb(255, 30, 34, 46, alpha));
        Render2D.drawLine(context, userX + 4.0F * scale, userY + 1.0F, userX + userW - 4.0F * scale, userY + 1.0F, 1.0F, argb(20, 255, 255, 255, alpha));
        
        float avCx = userX + 20.0F * scale, avCy = userY + 21.0F * scale, avR = 12.0F * scale;
        Render2D.drawCircle(context, avCx, avCy, avR, argb(255, 24, 28, 40, alpha));
        Render2D.drawCircleOutline(context, avCx, avCy, avR, 1.0F, argb(255, 42, 49, 68, alpha));
        drawTexture(context, ICON_USER, avCx - 7.0F * scale, avCy - 7.0F * scale, 14.0F * scale, 14.0F * scale, argb(200, 196, 181, 253, alpha));
        MinecraftClient client = MinecraftClient.getInstance();
        ModernFont.draw(context, client.getSession() != null ? client.getSession().getUsername() : "User", userX + 38.0F * scale, userY + 6.5F * scale, 11.5F * scale, argb(245, 241, 245, 249, alpha), ModernFont.Type.INTER_SEMIBOLD);
        ModernFont.drawBadge(context, "LIFETIME", userX + 38.0F * scale, userY + 21.5F * scale, 0xFF2A2356, 0xFFD8B4FE, Math.round(14.0F * scale), alpha);
    }

    private void drawGroupboxes(DrawContext context, GuiBounds b, int mouseX, int mouseY, float alpha, float dt) {
        if (b.settingsProgress > 0.001F) {
            drawRightGroupbox(context, b, mouseX, mouseY, alpha * b.settingsProgress, dt);
        }
        drawLeftGroupbox(context, b, mouseX, mouseY, alpha, dt);
    }

    private void drawLeftGroupbox(DrawContext context, GuiBounds b, int mouseX, int mouseY, float alpha, float dt) {
        float scale = b.scale;
        boolean en = isEn();
        
        // Panel 2: Modules (Функции) - Standalone floating obsidian card
        Render2D.drawShadow(context, b.box1X, b.boxY, b.boxW, b.boxH, 20.0F * b.scale, 16.0F * b.scale, argb(150, 0, 0, 0, alpha));
        Render2D.drawRound(context, b.box1X, b.boxY, b.boxW, b.boxH, 10.0F * b.scale, argb(252, 11, 13, 18, alpha));
        Render2D.drawRoundOutline(context, b.box1X, b.boxY, b.boxW, b.boxH, 10.0F * b.scale, 1.0F, argb(255, 26, 29, 39, alpha));
        Render2D.drawLine(context, b.box1X + 10.0F * scale, b.boxY + 1.0F, b.box1X + b.boxW - 10.0F * scale, b.boxY + 1.0F, 1.0F, argb(20, 255, 255, 255, alpha));

        boolean hasSearch = !search.trim().isEmpty();
        Identifier headerIcon = hasSearch ? ICON_SEARCH : selectedCategory.icon;
        String headerTitle = hasSearch ? (t("Поиск: ", "Search: ") + "\"" + search.trim() + "\"") : selectedCategory.getName(en);
        drawTexture(context, headerIcon, b.box1X + 16.0F * scale, b.boxY + 14.0F * scale, 16.0F * scale, 16.0F * scale, argb(220, 226, 232, 240, alpha));
        ModernFont.draw(context, headerTitle, b.box1X + 38.0F * scale, b.boxY + 15.0F * scale, 12.5F * scale, argb(255, 241, 245, 249, alpha), ModernFont.Type.INTER_SEMIBOLD);

        // Sort Button
        float sortBtnW = 24.0F * scale, sortBtnH = 20.0F * scale;
        float sortBtnX = b.box1X + b.boxW - sortBtnW - 14.0F * scale;
        float sortBtnY = b.boxY + 12.0F * scale;
        boolean sortHover = inside(mouseX, mouseY, sortBtnX, sortBtnY, sortBtnW, sortBtnH);
        Render2D.drawRound(context, sortBtnX, sortBtnY, sortBtnW, sortBtnH, 5.0F * scale,
                sortHover || sortDropdownOpen ? argb(255, 28, 32, 46, alpha) : argb(255, 20, 23, 33, alpha));
        Render2D.drawRoundOutline(context, sortBtnX, sortBtnY, sortBtnW, sortBtnH, 5.0F * scale, 1.0F,
                sortHover || sortDropdownOpen ? argb(200, 124, 58, 237, alpha) : argb(255, 32, 37, 50, alpha));
        drawTexture(context, ICON_SORT, sortBtnX + 5.0F * scale, sortBtnY + 3.0F * scale, 14.0F * scale, 14.0F * scale,
                sortHover || sortDropdownOpen ? argb(255, 255, 255, 255, alpha) : argb(170, 148, 163, 184, alpha));

        List<Module> modules = getFilteredModules();
        long pinnedCount = modules.stream().filter(m -> PINNED_MODULES.contains(m.getName())).count();
        boolean hasPinnedDivider = pinnedCount > 0 && pinnedCount < modules.size();
        float dividerH = hasPinnedDivider ? (8.0F * scale) : 0.0F;

        float totalH = modules.size() * (35.0F * scale) + dividerH;
        float scissorTop = b.boxY + 38.0F * scale;
        float scissorBottom = b.boxY + b.boxH - 28.0F * scale;
        float visibleHeight = scissorBottom - scissorTop;
        scrollLeft = Math.max(0.0F, Math.min(scrollLeft, Math.max(0.0F, totalH - visibleHeight)));
        float targetItemY = scissorTop + 4.0F * scale - scrollLeft;
        float itemH = 31.0F * scale;

        // Calculate target Y for smooth module selection rolling pill
        float targetModY = -1.0F;
        if (selectedModule != null) {
            float tempOffset = 0.0F;
            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                if (i == pinnedCount && hasPinnedDivider) {
                    tempOffset += dividerH;
                }
                if (m == selectedModule) {
                    targetModY = targetItemY + tempOffset;
                    break;
                }
                tempOffset += itemH + 4.0F * scale;
            }
        }

        if (targetModY >= -100.0F && targetModY <= b.boxY + b.boxH + 200.0F && selectedModule != null) {
            if (moduleIndicatorY < -10.0F) {
                moduleIndicatorY = targetModY;
            } else {
                moduleIndicatorY = moduleIndicatorY + (targetModY - moduleIndicatorY) * Math.min(1.0F, dt * 16.0F);
            }
            moduleIndicatorAlpha = Math.min(1.0F, moduleIndicatorAlpha + dt * 14.0F);
        } else {
            moduleIndicatorAlpha = Math.max(0.0F, moduleIndicatorAlpha - dt * 14.0F);
        }

        Render2D.pushScissor(b.box1X, scissorTop, b.boxW, scissorBottom - scissorTop);
        context.enableScissor((int) b.box1X, (int) scissorTop, (int) (b.box1X + b.boxW), (int) scissorBottom);

        // Smooth rolling gliding selection pill
        if (moduleIndicatorAlpha > 0.01F && moduleIndicatorY + itemH >= scissorTop - 10.0F * scale && moduleIndicatorY <= scissorBottom + 10.0F * scale) {
            float pillAlpha = alpha * moduleIndicatorAlpha;
            Render2D.drawRound(context, b.box1X + 8.0F * scale, moduleIndicatorY, b.boxW - 16.0F * scale, itemH, 6.0F * scale, argb(255, 22, 26, 38, pillAlpha));
            Render2D.drawRoundOutline(context, b.box1X + 8.0F * scale, moduleIndicatorY, b.boxW - 16.0F * scale, itemH, 6.0F * scale, 1.0F, argb(255, 38, 44, 62, pillAlpha));
            Render2D.drawLine(context, b.box1X + 12.0F * scale, moduleIndicatorY + 1.0F, b.box1X + b.boxW - 12.0F * scale, moduleIndicatorY + 1.0F, 1.0F, argb(20, 255, 255, 255, pillAlpha));
            // Subtle 2.5px left indicator pip in electric cyber violet
            Render2D.drawRound(context, b.box1X + 10.0F * scale, moduleIndicatorY + (itemH - 12.0F * scale) * 0.5F, 2.5F * scale, 12.0F * scale, 1.0F * scale, argb(255, 139, 92, 246, pillAlpha));
        }

        float curListOffset = 0.0F;
        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            boolean isPinned = PINNED_MODULES.contains(mod.getName());

            if (i == pinnedCount && hasPinnedDivider) {
                float sepY = targetItemY + curListOffset;
                Render2D.drawLine(context, b.box1X + 16.0F * scale, sepY + 3.0F * scale, b.box1X + b.boxW - 16.0F * scale, sepY + 3.0F * scale, 1.0F, argb(20, 255, 255, 255, alpha));
                curListOffset += dividerH;
            }

            float destY = targetItemY + curListOffset;
            curListOffset += itemH + 4.0F * scale;

            float curY = moduleVisualY.getOrDefault(mod, destY);
            curY += (destY - curY) * Math.min(1.0F, dt * 16.0F);
            moduleVisualY.put(mod, curY);

            if (curY + itemH >= scissorTop - 10.0F * scale && curY <= scissorBottom + 10.0F * scale) {
                boolean isSelected = selectedModule == mod;
                boolean hover = inside(mouseX, mouseY, b.box1X + 8.0F * scale, curY, b.boxW - 16.0F * scale, itemH);
                
                if (!isSelected) {
                    if (isPinned) {
                        Render2D.drawRound(context, b.box1X + 8.0F * scale, curY, b.boxW - 16.0F * scale, itemH, 6.0F * scale, argb(10, 139, 92, 246, alpha));
                        Render2D.drawRoundOutline(context, b.box1X + 8.0F * scale, curY, b.boxW - 16.0F * scale, itemH, 6.0F * scale, 1.0F, argb(30, 139, 92, 246, alpha));
                    } else if (hover) {
                        Render2D.drawRound(context, b.box1X + 8.0F * scale, curY, b.boxW - 16.0F * scale, itemH, 6.0F * scale, argb(10, 255, 255, 255, alpha));
                    }
                }

                float modNameY = centeredTextY(curY, itemH, 11.5F * scale, ModernFont.Type.INTER_MEDIUM);
                float nameX = isPinned ? (b.box1X + 32.0F * scale) : (isSelected ? (b.box1X + 18.0F * scale) : (b.box1X + 16.0F * scale));
                if (isPinned) {
                    float pinSize = 12.0F * scale;
                    float pinY = curY + (itemH - pinSize) * 0.5F;
                    drawTexture(context, ICON_PIN, b.box1X + 16.0F * scale, pinY, pinSize, pinSize, argb(220, 192, 132, 252, alpha));
                }
                int modNameCol = sessionToggleTarget(mod).isEnabled() ? argb(255, 238, 242, 246, alpha) : argb(150, 148, 163, 184, alpha);
                ModernFont.draw(context, mod.getName(), nameX, modNameY, 11.5F * scale, modNameCol, ModernFont.Type.INTER_MEDIUM);

                // Standardized controls geometry
                float toggleW = 26.0F * scale, toggleH = 14.0F * scale;
                float toggleX = b.box1X + b.boxW - toggleW - 14.0F * scale;
                float toggleY = curY + (itemH - toggleH) * 0.5F;

                // Listening animation for expanding 3-dots card
                boolean isListening = (listeningModuleBind == mod);
                float curListenAnim = bindListeningAnim.getOrDefault(mod, 0.0F);
                curListenAnim += ((isListening ? 1.0F : 0.0F) - curListenAnim) * Math.min(1.0F, dt * 16.0F);
                bindListeningAnim.put(mod, curListenAnim);

                float baseDotsW = 18.0F * scale, dotsH = 18.0F * scale;
                float expandedDotsW = 114.0F * scale;
                float dotsW = baseDotsW + (expandedDotsW - baseDotsW) * easeOutCubic(curListenAnim);
                float dotsX = toggleX - dotsW - 8.0F * scale;
                float dotsY = curY + (itemH - dotsH) * 0.5F;

                // Bound Key badge - cleanly anchored next to the 3-dots button
                int boundKey = MODULE_BINDS.getOrDefault(mod.getName(), 0);
                boolean hasBind = boundKey != 0 && boundKey != GLFW.GLFW_KEY_UNKNOWN;
                if (hasBind) {
                    lastBoundKeyNames.put(mod, KeybindSetting.getKeyName(boundKey, en));
                }

                float curBadgeAnim = bindBadgeAnim.getOrDefault(mod, hasBind ? 1.0F : 0.0F);
                float targetBadgeAnim = (hasBind && !isListening) ? 1.0F : 0.0F;
                curBadgeAnim += (targetBadgeAnim - curBadgeAnim) * Math.min(1.0F, dt * 16.0F);
                bindBadgeAnim.put(mod, curBadgeAnim);

                if (curBadgeAnim > 0.01F) {
                    String keyName = lastBoundKeyNames.getOrDefault(mod, "");
                    if (!keyName.isEmpty()) {
                        float keyTextW = ModernFont.getWidth(keyName, 8.5F * scale, ModernFont.Type.INTER_SEMIBOLD);
                        float badgeH = 15.0F * scale;
                        float badgeW = keyName.length() <= 1 ? badgeH : Math.max(badgeH, keyTextW + 8.0F * scale);
                        float badgeX = dotsX - badgeW - 6.0F * scale;
                        float badgeY = curY + (itemH - badgeH) * 0.5F;

                        float nameW = ModernFont.getWidth(mod.getName(), 11.5F * scale, ModernFont.Type.INTER_MEDIUM);
                        if (badgeX > nameX + nameW + 6.0F * scale) {
                            float bAlpha = alpha * curBadgeAnim;
                            // Clean dark obsidian keycap styling (exact square with geometric optical centering)
                            Render2D.drawRound(context, badgeX, badgeY, badgeW, badgeH, 4.0F * scale, argb(255, 18, 20, 28, bAlpha));
                            Render2D.drawRoundOutline(context, badgeX, badgeY, badgeW, badgeH, 4.0F * scale, 1.0F, argb(255, 38, 42, 58, bAlpha));
                            Render2D.drawLine(context, badgeX + 2.5F * scale, badgeY + 1.0F, badgeX + badgeW - 2.5F * scale, badgeY + 1.0F, 1.0F, argb(18, 255, 255, 255, bAlpha));
                            float textY = centeredTextY(badgeY, badgeH, 8.5F * scale, ModernFont.Type.INTER_SEMIBOLD) - 1.25F * scale;
                            ModernFont.drawCentered(context, keyName, badgeX + badgeW * 0.5F, textY, 8.5F * scale, argb(255, 160, 172, 192, bAlpha), ModernFont.Type.INTER_SEMIBOLD);
                        }
                    }
                } else if (!hasBind) {
                    lastBoundKeyNames.remove(mod);
                }

                boolean dotsHover = inside(mouseX, mouseY, dotsX, dotsY, dotsW, dotsH);
                boolean isMenuOpen = activeContextMenuModule == mod && !contextMenuClosing;

                if (curListenAnim > 0.001F) {
                    float pAlpha = alpha * Math.min(1.0F, curListenAnim * 1.5F);
                    Render2D.drawRound(context, dotsX, dotsY, dotsW, dotsH, 4.0F * scale, argb(255, 18, 20, 28, pAlpha));
                    drawDashedOutline(context, dotsX, dotsY, dotsW, dotsH, scale, argb(180, 139, 92, 246, pAlpha));

                    if (curListenAnim > 0.3F) {
                        float textFade = ((curListenAnim - 0.3F) / 0.7F) * pAlpha;
                        String prompt = t("Нажмите кнопку...", "Press any key...");
                        ModernFont.drawCentered(context, prompt, dotsX + dotsW * 0.5F, centeredTextY(dotsY, dotsH, 9.0F * scale, ModernFont.Type.INTER_MEDIUM), 9.0F * scale, argb(255, 203, 213, 225, textFade), ModernFont.Type.INTER_MEDIUM);
                    }
                } else {
                    if (dotsHover || isMenuOpen) {
                        Render2D.drawRound(context, dotsX, dotsY, dotsW, dotsH, 4.0F * scale, argb(255, 30, 35, 48, alpha));
                        Render2D.drawRoundOutline(context, dotsX, dotsY, dotsW, dotsH, 4.0F * scale, 1.0F, argb(255, 44, 52, 72, alpha));
                    }
                    drawTexture(context, ICON_DOTS, Math.round(dotsX + 2.0F * scale), Math.round(dotsY + 2.0F * scale), Math.round(14.0F * scale), Math.round(14.0F * scale),
                            dotsHover || isMenuOpen ? argb(255, 255, 255, 255, alpha) : argb(120, 148, 163, 184, alpha));
                }

                drawToggle(context, "mod_" + mod.getName(), toggleX, toggleY, toggleW, toggleH, sessionToggleTarget(mod).isEnabled(), alpha, dt, scale);
            }
        }
        if (modules.isEmpty()) {
            float emptyTextY = scissorTop + visibleHeight * 0.4F;
            String emptyMsg = hasSearch ? t("Ничего не найдено по запросу", "No modules found for query") : t("В этом разделе пока нет модулей", "No modules in this category");
            ModernFont.drawCentered(context, emptyMsg, b.box1X + b.boxW * 0.5F, emptyTextY, 11.5F * scale, argb(120, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
        }
        context.disableScissor();
        Render2D.popScissor();

        // Bottom Stats Footer
        float footerY = b.boxY + b.boxH - 24.0F * scale;
        Render2D.drawLine(context, b.box1X + 12.0F * scale, footerY, b.box1X + b.boxW - 12.0F * scale, footerY, 1.0F, argb(255, 22, 24, 33, alpha));
        long activeCount = modules.stream().filter(Module::isEnabled).count();
        String statsLeft = t("Активно: ", "Active: ") + activeCount + " " + t("из", "of") + " " + modules.size();
        ModernFont.draw(context, statsLeft, b.box1X + 16.0F * scale, centeredTextY(footerY, 24.0F * scale, 9.5F * scale, ModernFont.Type.INTER_MEDIUM), 9.5F * scale, argb(140, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
        String statsRight = sortMode.label(en);
        ModernFont.drawRight(context, statsRight, b.box1X + b.boxW - 16.0F * scale, centeredTextY(footerY, 24.0F * scale, 9.5F * scale, ModernFont.Type.INTER_MEDIUM), 9.5F * scale, argb(150, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
    }

    private void drawRightGroupbox(DrawContext context, GuiBounds b, int mouseX, int mouseY, float alpha, float dt) {
        float scale = b.scale;
        boolean en = isEn();
        
        // Panel 3: Settings (Настройки) - Standalone floating obsidian card
        Render2D.drawShadow(context, b.box2X, b.boxY, b.boxW, b.boxH, 20.0F * b.scale, 16.0F * b.scale, argb(150, 0, 0, 0, alpha));
        Render2D.drawRound(context, b.box2X, b.boxY, b.boxW, b.boxH, 10.0F * b.scale, argb(252, 11, 13, 18, alpha));
        Render2D.drawRoundOutline(context, b.box2X, b.boxY, b.boxW, b.boxH, 10.0F * b.scale, 1.0F, argb(255, 26, 29, 39, alpha));
        Render2D.drawLine(context, b.box2X + 10.0F * scale, b.boxY + 1.0F, b.box2X + b.boxW - 10.0F * scale, b.boxY + 1.0F, 1.0F, argb(20, 255, 255, 255, alpha));

        if (selectedModule == null) {
            drawTexture(context, ICON_INFO, b.box2X + b.boxW * 0.5F - 12.0F * scale, b.boxY + b.boxH * 0.5F - 28.0F * scale, 24.0F * scale, 24.0F * scale, argb(100, 148, 163, 184, alpha));
            ModernFont.drawCentered(context, t("Выберите модуль слева", "Select a module on the left"), b.box2X + b.boxW * 0.5F, b.boxY + b.boxH * 0.5F + 6.0F * scale, 12.5F * scale, argb(140, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
            return;
        }

        // Header: White Wrench Icon + Module Name Settings
        drawTexture(context, ICON_WRENCH, b.box2X + 16.0F * scale, b.boxY + 14.0F * scale, 16.0F * scale, 16.0F * scale, argb(230, 235, 240, 245, alpha));

        // Neat collapse button at top-right
        float closeBtnW = 20.0F * scale, closeBtnH = 20.0F * scale;
        float closeBtnX = b.box2X + b.boxW - closeBtnW - 12.0F * scale;
        float closeBtnY = b.boxY + 11.0F * scale;
        boolean closeHover = inside(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnW, closeBtnH);

        Render2D.drawRound(context, closeBtnX, closeBtnY, closeBtnW, closeBtnH, 4.0F * scale,
                closeHover ? argb(255, 28, 32, 46, alpha) : argb(255, 18, 20, 30, alpha));
        Render2D.drawRoundOutline(context, closeBtnX, closeBtnY, closeBtnW, closeBtnH, 4.0F * scale, 1.0F,
                closeHover ? argb(200, 139, 92, 246, alpha) : argb(255, 32, 37, 50, alpha));

        // Clean collapse icon: dock bar + left chevron [|<]
        float ccx = closeBtnX + closeBtnW * 0.5F;
        float ccy = closeBtnY + closeBtnH * 0.5F;
        float arm = 3.5F * scale;
        int iconCol = closeHover ? argb(255, 255, 255, 255, alpha) : argb(160, 148, 163, 184, alpha);
        Render2D.drawLine(context, ccx - 3.5F * scale, ccy - arm, ccx - 3.5F * scale, ccy + arm, 1.5F * scale, iconCol);
        Render2D.drawLine(context, ccx + 2.5F * scale, ccy - arm, ccx - 1.0F * scale, ccy, 1.5F * scale, iconCol);
        Render2D.drawLine(context, ccx - 1.0F * scale, ccy, ccx + 2.5F * scale, ccy + arm, 1.5F * scale, iconCol);
        
        float maxTitleW = closeBtnX - (b.box2X + 38.0F * scale) - 8.0F * scale;
        context.enableScissor((int) (b.box2X + 38.0F * scale), (int) b.boxY, (int) (b.box2X + 38.0F * scale + maxTitleW), (int) (b.boxY + 36.0F * scale));
        String headerTitle = selectedModule.getName() + " " + t("Настройки", "Settings");
        ModernFont.draw(context, headerTitle, b.box2X + 38.0F * scale, b.boxY + 15.0F * scale, 12.5F * scale, argb(255, 241, 245, 249, alpha), ModernFont.Type.INTER_SEMIBOLD);
        context.disableScissor();

        float rowW = b.rowW;

        if ("Macros".equalsIgnoreCase(selectedModule.getName())) {
            drawMacroManager(context, b.box2X + 16.0F * scale, b.boxY, rowW, b.boxH, mouseX, mouseY, alpha, dt, scale);
            return;
        }
        if ("NameBind".equalsIgnoreCase(selectedModule.getName()) || "NameBinder".equalsIgnoreCase(selectedModule.getName())) {
            drawNameBindManager(context, b.box2X + 16.0F * scale, b.boxY, rowW, b.boxH, mouseX, mouseY, alpha, dt, scale);
            return;
        }

        List<Setting<?>> settings = moduleSettings.getOrDefault(selectedModule, Collections.emptyList());
        boolean hasVisibleSettings = false;
        float totalH = 24.0F * scale + 34.0F * scale;
        if ("AutoBuy".equalsIgnoreCase(selectedModule.getName())) {
            totalH += 34.0F * scale;
        }
        for (Setting<?> s : settings) {
            if (!s.isVisible()) continue;
            hasVisibleSettings = true;
            if (s instanceof ModeSetting mode) {
                float curAnim = dropdownAnim.getOrDefault(mode, 0.0F);
                totalH += 34.0F * scale + mode.getModes().size() * (22.0F * scale) * curAnim;
            } else if (s instanceof MultiModeSetting multi) {
                float curAnim = multiDropdownAnim.getOrDefault(multi, 0.0F);
                totalH += 34.0F * scale + multi.getAllOptions().size() * (22.0F * scale) * curAnim;
            } else {
                totalH += 34.0F * scale;
            }
        }
        if (hasVisibleSettings) totalH += 24.0F * scale;

        float scissorTop = b.boxY + 38.0F * scale;
        float scissorBottom = b.boxY + b.boxH - 12.0F * scale;
        float visibleHeight = scissorBottom - scissorTop;
        scrollRight = Math.max(0.0F, Math.min(scrollRight, Math.max(0.0F, totalH - visibleHeight)));
        float itemY = scissorTop + 4.0F * scale - scrollRight;

        Render2D.pushScissor(b.box2X, scissorTop, b.boxW, scissorBottom - scissorTop);
        context.enableScissor((int) b.box2X, (int) scissorTop, (int) (b.box2X + b.boxW), (int) scissorBottom);

        // Section 1: Controls & Binds (noble gray typography)
        drawSectionHeader(context, t("УПРАВЛЕНИЕ И БИНДЫ", "CONTROLS & BINDS"), ICON_KEYBOARD, b.box2X + 16.0F * scale, itemY, rowW, alpha, scale);
        itemY += 24.0F * scale;

        if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
            int moduleKey = selectedModule == moduleManager.getMenu() ? moduleManager.getMenu().getKeyBind() : MODULE_BINDS.getOrDefault(selectedModule.getName(), GLFW.GLFW_KEY_UNKNOWN);
            drawKeybindRow(context, t("Горячая клавиша", "Keybind"), moduleKey,
                    listeningModuleBind == selectedModule, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, scale);
        }
        itemY += 34.0F * scale;

        // Section 2: Parameters
        if (hasVisibleSettings) {
            drawSectionHeader(context, t("ПАРАМЕТРЫ ФУНКЦИИ", "MODULE SETTINGS"), ICON_SLIDERS, b.box2X + 16.0F * scale, itemY, rowW, alpha, scale);
            itemY += 24.0F * scale;

            for (Setting<?> s : settings) {
                if (!s.isVisible()) continue;
                if (s instanceof BooleanSetting bool) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                        drawBooleanRow(context, bool, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, dt, scale);
                    }
                    itemY += 34.0F * scale;
                } else if (s instanceof KeybindSetting keybind) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                        drawKeybindRow(context, keybind.getName(), keybind.get(), listeningSettingBind == keybind,
                                b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, scale);
                    }
                    itemY += 34.0F * scale;
                } else if (s instanceof SliderSetting slider) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                        drawSliderRow(context, slider, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, dt, scale);
                    }
                    itemY += 34.0F * scale;
                } else if (s instanceof ModeSetting mode) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom + 200.0F * scale) {
                        float addedH = drawModeAccordion(context, mode, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, dt, scale);
                        itemY += addedH;
                    } else {
                        float curAnim = dropdownAnim.getOrDefault(mode, 0.0F);
                        itemY += 34.0F * scale + mode.getModes().size() * (22.0F * scale) * curAnim;
                    }
                } else if (s instanceof MultiModeSetting multi) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom + 200.0F * scale) {
                        float addedH = drawMultiModeAccordion(context, multi, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, dt, scale);
                        itemY += addedH;
                    } else {
                        float curAnim = multiDropdownAnim.getOrDefault(multi, 0.0F);
                        itemY += 34.0F * scale + multi.getAllOptions().size() * (22.0F * scale) * curAnim;
                    }
                } else if (s instanceof ColorSetting color) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                        drawColorRow(context, color, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, scale);
                    }
                    itemY += 34.0F * scale;
                } else if (s instanceof StringSetting str) {
                    if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                        drawStringRow(context, str, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, scale);
                    }
                    itemY += 34.0F * scale;
                }
            }
            if ("AutoBuy".equalsIgnoreCase(selectedModule.getName())) {
                if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                    drawAutoBuyPanelButton(context, b.box2X + 16.0F * scale, itemY, rowW, mouseX, mouseY, alpha, scale);
                }
                itemY += 34.0F * scale;
            }
        } else {
            // Placeholder: Unobtrusive icon and text when module has no additional parameters
            float cx = b.box2X + b.boxW * 0.5F;
            float availH = scissorBottom - itemY;
            if (availH > 40.0F * scale) {
                float centerY = itemY + availH * 0.44F;
                float iconSize = 28.0F * scale;
                float iconX = cx - iconSize * 0.5F;
                float iconY = centerY - iconSize * 0.5F - 10.0F * scale;

                Render2D.drawCircle(context, cx, iconY + iconSize * 0.5F, 20.0F * scale, argb(14, 255, 255, 255, alpha));
                Render2D.drawCircleOutline(context, cx, iconY + iconSize * 0.5F, 20.0F * scale, 1.0F, argb(30, 255, 255, 255, alpha));
                drawTexture(context, ICON_NO_SETTINGS, iconX, iconY, iconSize, iconSize, argb(120, 148, 163, 184, alpha));

                float textY = iconY + iconSize + 14.0F * scale;
                String fullMsg = t("У этого модуля нет дополнительных параметров", "This module has no additional parameters");
                float fullW = ModernFont.getWidth(fullMsg, 11.0F * scale, ModernFont.Type.INTER_MEDIUM);
                if (fullW <= b.boxW - 32.0F * scale) {
                    ModernFont.drawCentered(context, fullMsg, cx, textY, 11.0F * scale, argb(130, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
                } else {
                    ModernFont.drawCentered(context, t("У этого модуля нет", "This module has no"), cx, textY, 11.0F * scale, argb(130, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
                    ModernFont.drawCentered(context, t("дополнительных параметров", "additional parameters"), cx, textY + 14.5F * scale, 11.0F * scale, argb(130, 148, 163, 184, alpha), ModernFont.Type.INTER_MEDIUM);
                }
            }
        }

        context.disableScissor();
        Render2D.popScissor();
    }

    /** Returns the draw baseline that centers ModernFont's visual texture in a row. */
    private static float centeredTextY(float rowY, float rowH, float size, ModernFont.Type type) {
        return rowY + (rowH - ModernFont.getHeight(size, type)) * 0.5F;
    }

    private void drawSectionHeader(DrawContext context, String title, Identifier icon, float x, float y, float w, float alpha, float scale) {
        drawTexture(context, icon, x, y + 1.0F * scale, 13.0F * scale, 13.0F * scale, argb(160, 122, 128, 153, alpha));
        float titleSize = 8.5F * scale;
        ModernFont.draw(context, title, x + 18.0F * scale, centeredTextY(y, 15.0F * scale, titleSize, ModernFont.Type.INTER_SEMIBOLD), titleSize,
                argb(175, 115, 128, 150, alpha), ModernFont.Type.INTER_SEMIBOLD);
        float titleW = ModernFont.getWidth(title, titleSize, ModernFont.Type.INTER_SEMIBOLD) + 24.0F * scale;
        if (x + titleW < x + w) {
            Render2D.drawLine(context, x + titleW, y + 7.5F * scale, x + w, y + 7.5F * scale, 1.0F, argb(255, 22, 25, 34, alpha));
        }
    }

    private void drawMacroManager(DrawContext context, float x, float y, float w, float h, int mouseX, int mouseY, float alpha, float dt, float scale) {
        Macros macros = moduleManager.getMacros();
        var entries = macros.getEntries();
        float totalH = 24.0F * scale + 34.0F * scale + 24.0F * scale + entries.size() * (34.0F * scale) + 40.0F * scale;
        float scissorTop = y + 38.0F * scale;
        float scissorBottom = y + h - 12.0F * scale;
        float visibleHeight = scissorBottom - scissorTop;
        scrollRight = Math.max(0.0F, Math.min(scrollRight, Math.max(0.0F, totalH - visibleHeight)));
        float itemY = scissorTop + 4.0F * scale - scrollRight;

        Render2D.pushScissor(x - 16.0F * scale, scissorTop, w + 32.0F * scale, scissorBottom - scissorTop);
        context.enableScissor((int) (x - 16.0F * scale), (int) scissorTop, (int) (x + w + 16.0F * scale), (int) scissorBottom);

        drawSectionHeader(context, t("СТАТУС МОДУЛЯ", "MODULE STATUS"), ICON_ZAP, x, itemY, w, alpha, scale);
        itemY += 24.0F * scale;

        if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
            ModernFont.draw(context, t("Включить макросы", "Enable Macros"), x, itemY + 6.0F * scale, 12.0F * scale, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
            drawToggle(context, "macro_active", x + w - 26.0F * scale, itemY + 5.0F * scale, 26.0F * scale, 14.0F * scale, macros.isEnabled(), alpha, dt, scale);
        }
        itemY += 34.0F * scale;

        drawSectionHeader(context, t("СПИСОК МАКРОСОВ (", "MACROS LIST (") + entries.size() + ")", ICON_KEYBOARD, x, itemY, w, alpha, scale);
        itemY += 24.0F * scale;

        for (int i = 0; i < entries.size(); i++) {
            if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                var entry = entries.get(i);
                boolean focused = activeMacroTextIndex == i;
                float boxH = 22.0F * scale;
                float delBtnW = 20.0F * scale;
                float bindBtnW = 54.0F * scale;
                float textW = w - delBtnW - bindBtnW - 12.0F * scale;

                Render2D.drawRound(context, x, itemY, textW, boxH, 5.0F * scale, focused ? argb(255, 24, 29, 44, alpha) : argb(255, 20, 24, 36, alpha));
                Render2D.drawRoundOutline(context, x, itemY, textW, boxH, 5.0F * scale, 1.0F, focused ? argb(200, 124, 58, 237, alpha) : argb(255, 38, 45, 64, alpha));
                String shownText = entry.text() + (focused && (System.currentTimeMillis() % 1000L < 500L) ? "|" : "");
                drawMarqueeText(context, shownText.isEmpty() && !focused ? t("Команда или текст...", "Command or text...") : shownText, x, itemY, textW, boxH, 11.0F * scale,
                        shownText.isEmpty() && !focused ? argb(140, 148, 163, 184, alpha) : argb(255, 240, 245, 255, alpha), focused, scale);

                float bindX = x + textW + 6.0F * scale;
                boolean listening = listeningMacroIndex == i;
                boolean bindHover = inside(mouseX, mouseY, bindX, itemY, bindBtnW, boxH);
                Render2D.drawRound(context, bindX, itemY, bindBtnW, boxH, 5.0F * scale, argb(255, 20, 24, 36, alpha));
                if (listening) {
                    drawAnimatedDashedBorder(context, bindX, itemY, bindBtnW, boxH, 1.0F * scale, argb(255, 192, 132, 252, alpha));
                } else {
                    Render2D.drawRoundOutline(context, bindX, itemY, bindBtnW, boxH, 5.0F * scale, 1.0F, bindHover ? argb(200, 124, 58, 237, alpha) : argb(255, 38, 45, 64, alpha));
                }

                String keyText = listening ? "..." : KeybindSetting.getKeyName(entry.keyCode(), isEn());
                float fontSize = 10.0F * scale;
                ModernFont.drawCentered(context, keyText, bindX + bindBtnW * 0.5F, centeredTextY(itemY, boxH, fontSize, ModernFont.Type.SF_BOLD), fontSize,
                        listening ? argb(255, 216, 180, 254, alpha) : argb(255, 226, 232, 240, alpha), ModernFont.Type.SF_BOLD);

                float delX = bindX + bindBtnW + 6.0F * scale;
                boolean delHover = inside(mouseX, mouseY, delX, itemY, delBtnW, boxH);
                Render2D.drawRound(context, delX, itemY, delBtnW, boxH, 5.0F * scale, delHover ? argb(35, 220, 70, 70, alpha) : argb(16, 255, 255, 255, alpha));
                Render2D.drawRoundOutline(context, delX, itemY, delBtnW, boxH, 5.0F * scale, 1.0F, delHover ? argb(140, 220, 70, 70, alpha) : argb(255, 36, 42, 58, alpha));
                float cx = delX + delBtnW * 0.5F;
                float cy = itemY + boxH * 0.5F;
                float cr = 3.0F * scale;
                int crossCol = delHover ? argb(255, 248, 140, 140, alpha) : argb(160, 148, 163, 184, alpha);
                Render2D.drawLine(context, cx - cr, cy - cr, cx + cr, cy + cr, 1.2F * scale, crossCol);
                Render2D.drawLine(context, cx + cr, cy - cr, cx - cr, cy + cr, 1.2F * scale, crossCol);
            }
            itemY += 34.0F * scale;
        }

        if (itemY + 24.0F * scale >= scissorTop && itemY <= scissorBottom) {
            boolean addHover = inside(mouseX, mouseY, x, itemY + 4.0F * scale, w, 24.0F * scale);
            Render2D.drawRound(context, x, itemY + 4.0F * scale, w, 24.0F * scale, 5.0F * scale, addHover ? argb(40, 124, 58, 237, alpha) : argb(20, 124, 58, 237, alpha));
            Render2D.drawRoundOutline(context, x, itemY + 4.0F * scale, w, 24.0F * scale, 5.0F * scale, 1.0F, argb(100, 124, 58, 237, alpha));
            ModernFont.drawCentered(context, t("+ Добавить макрос", "+ Add Macro"), x + w * 0.5F, itemY + 10.5F * scale, 11.0F * scale, argb(255, 196, 181, 253, alpha), ModernFont.Type.SF_BOLD);
        }
        context.disableScissor();
        Render2D.popScissor();
    }

    private void drawNameBindManager(DrawContext context, float x, float y, float w, float h, int mouseX, int mouseY, float alpha, float dt, float scale) {
        NameBind nameBind = moduleManager.getNameBind();
        var entries = nameBind.getEntries();
        float totalH = 24.0F * scale + 34.0F * scale + 24.0F * scale + entries.size() * (34.0F * scale) + 40.0F * scale;
        float scissorTop = y + 38.0F * scale;
        float scissorBottom = y + h - 12.0F * scale;
        float visibleHeight = scissorBottom - scissorTop;
        scrollRight = Math.max(0.0F, Math.min(scrollRight, Math.max(0.0F, totalH - visibleHeight)));
        float itemY = scissorTop + 4.0F * scale - scrollRight;

        Render2D.pushScissor(x - 16.0F * scale, scissorTop, w + 32.0F * scale, scissorBottom - scissorTop);
        context.enableScissor((int) (x - 16.0F * scale), (int) scissorTop, (int) (x + w + 16.0F * scale), (int) scissorBottom);

        drawSectionHeader(context, t("СТАТУС МОДУЛЯ", "MODULE STATUS"), ICON_ZAP, x, itemY, w, alpha, scale);
        itemY += 24.0F * scale;

        if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
            ModernFont.draw(context, t("Включить NameBind", "Enable NameBind"), x, itemY + 6.0F * scale, 12.0F * scale, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
            drawToggle(context, "namebind_active", x + w - 26.0F * scale, itemY + 5.0F * scale, 26.0F * scale, 14.0F * scale, nameBind.isEnabled(), alpha, dt, scale);
        }
        itemY += 34.0F * scale;

        drawSectionHeader(context, t("СПИСОК БИНДОВ (", "BINDS LIST (") + entries.size() + ")", ICON_KEYBOARD, x, itemY, w, alpha, scale);
        itemY += 24.0F * scale;

        for (int i = 0; i < entries.size(); i++) {
            if (itemY + 34.0F * scale >= scissorTop && itemY <= scissorBottom) {
                var entry = entries.get(i);
                boolean focused = activeNameBindTextIndex == i;
                float boxH = 22.0F * scale;
                float delBtnW = 20.0F * scale;
                float bindBtnW = 54.0F * scale;
                float textW = w - delBtnW - bindBtnW - 12.0F * scale;

                Render2D.drawRound(context, x, itemY, textW, boxH, 5.0F * scale, focused ? argb(255, 24, 29, 44, alpha) : argb(255, 20, 24, 36, alpha));
                Render2D.drawRoundOutline(context, x, itemY, textW, boxH, 5.0F * scale, 1.0F, focused ? argb(200, 124, 58, 237, alpha) : argb(255, 38, 45, 64, alpha));
                String shownText = entry.text() + (focused && (System.currentTimeMillis() % 1000L < 500L) ? "|" : "");
                drawMarqueeText(context, shownText.isEmpty() && !focused ? t("Ник или текст...", "Player name or text...") : shownText, x, itemY, textW, boxH, 11.0F * scale,
                        shownText.isEmpty() && !focused ? argb(140, 148, 163, 184, alpha) : argb(255, 240, 245, 255, alpha), focused, scale);

                float bindX = x + textW + 6.0F * scale;
                boolean listening = listeningNameBindIndex == i;
                boolean bindHover = inside(mouseX, mouseY, bindX, itemY, bindBtnW, boxH);
                Render2D.drawRound(context, bindX, itemY, bindBtnW, boxH, 5.0F * scale, argb(255, 20, 24, 36, alpha));
                if (listening) {
                    drawAnimatedDashedBorder(context, bindX, itemY, bindBtnW, boxH, 1.0F * scale, argb(255, 192, 132, 252, alpha));
                } else {
                    Render2D.drawRoundOutline(context, bindX, itemY, bindBtnW, boxH, 5.0F * scale, 1.0F, bindHover ? argb(200, 124, 58, 237, alpha) : argb(255, 38, 45, 64, alpha));
                }

                String keyText = listening ? "..." : KeybindSetting.getKeyName(entry.keyCode(), isEn());
                float fontSize = 10.0F * scale;
                ModernFont.drawCentered(context, keyText, bindX + bindBtnW * 0.5F, centeredTextY(itemY, boxH, fontSize, ModernFont.Type.SF_BOLD), fontSize,
                        listening ? argb(255, 216, 180, 254, alpha) : argb(255, 226, 232, 240, alpha), ModernFont.Type.SF_BOLD);

                float delX = bindX + bindBtnW + 6.0F * scale;
                boolean delHover = inside(mouseX, mouseY, delX, itemY, delBtnW, boxH);
                Render2D.drawRound(context, delX, itemY, delBtnW, boxH, 5.0F * scale, delHover ? argb(35, 220, 70, 70, alpha) : argb(16, 255, 255, 255, alpha));
                Render2D.drawRoundOutline(context, delX, itemY, delBtnW, boxH, 5.0F * scale, 1.0F, delHover ? argb(140, 220, 70, 70, alpha) : argb(255, 36, 42, 58, alpha));
                float cx = delX + delBtnW * 0.5F;
                float cy = itemY + boxH * 0.5F;
                float cr = 3.0F * scale;
                int crossCol = delHover ? argb(255, 248, 140, 140, alpha) : argb(160, 148, 163, 184, alpha);
                Render2D.drawLine(context, cx - cr, cy - cr, cx + cr, cy + cr, 1.2F * scale, crossCol);
                Render2D.drawLine(context, cx + cr, cy - cr, cx - cr, cy + cr, 1.2F * scale, crossCol);
            }
            itemY += 34.0F * scale;
        }

        if (itemY + 24.0F * scale >= scissorTop && itemY <= scissorBottom) {
            boolean addHover = inside(mouseX, mouseY, x, itemY + 4.0F * scale, w, 24.0F * scale);
            Render2D.drawRound(context, x, itemY + 4.0F * scale, w, 24.0F * scale, 5.0F * scale, addHover ? argb(40, 124, 58, 237, alpha) : argb(20, 124, 58, 237, alpha));
            Render2D.drawRoundOutline(context, x, itemY + 4.0F * scale, w, 24.0F * scale, 5.0F * scale, 1.0F, argb(100, 124, 58, 237, alpha));
            ModernFont.drawCentered(context, t("+ Добавить никнейм", "+ Add Name"), x + w * 0.5F, itemY + 10.5F * scale, 11.0F * scale, argb(255, 196, 181, 253, alpha), ModernFont.Type.SF_BOLD);
        }
        context.disableScissor();
        Render2D.popScissor();
    }

    private void drawBooleanRow(DrawContext context, BooleanSetting bool, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt, float scale) {
        float textSize = 12.0F * scale;
        ModernFont.draw(context, getSettingDisplayName(bool.getName()), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
        drawToggle(context, "set_" + bool.getName(), x + w - 26.0F * scale, y + 5.5F * scale, 26.0F * scale, 14.0F * scale, bool.get(), alpha, dt, scale);
    }

    private void drawSliderRow(DrawContext context, SliderSetting slider, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt, float scale) {
        float valBoxW = 36.0F * scale;
        float valBoxH = 18.0F * scale;
        float valBoxX = x + w - valBoxW;
        float valBoxY = y + 3.5F * scale;
        boolean valFocused = activeSliderInput == slider;
        boolean valHover = inside(mouseX, mouseY, valBoxX, valBoxY, valBoxW, valBoxH);

        float trackW = 56.0F * scale;
        float trackH = 3.5F * scale;
        float trackX = valBoxX - trackW - 8.0F * scale;
        float trackY = y + (25.0F * scale - trackH) * 0.5F;

        // Guaranteed non-overlapping label with truncation + scissor
        float maxLabelW = trackX - x - 8.0F * scale;
        String rawLabel = getSettingDisplayName(slider.getName());
        String displayLabel = rawLabel;
        float labelSize = 12.0F * scale;
        if (ModernFont.getWidth(displayLabel, labelSize, ModernFont.Type.INTER_MEDIUM) > maxLabelW) {
            while (displayLabel.length() > 4 && ModernFont.getWidth(displayLabel + "…", labelSize, ModernFont.Type.INTER_MEDIUM) > maxLabelW) {
                displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
            }
            displayLabel += "…";
        }

        Render2D.pushScissor(x, y, maxLabelW, 22.0F * scale);
        context.enableScissor((int) x, (int) (y - 4.0F * scale), (int) (x + maxLabelW), (int) (y + 24.0F * scale));
        ModernFont.draw(context, displayLabel, x, centeredTextY(y, 25.0F * scale, labelSize, ModernFont.Type.INTER_MEDIUM), labelSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
        context.disableScissor();
        Render2D.popScissor();

        Render2D.drawRound(context, valBoxX, valBoxY, valBoxW, valBoxH, 4.0F * scale, valFocused ? argb(255, 20, 24, 35, alpha) : (valHover ? argb(255, 20, 24, 34, alpha) : argb(255, 16, 18, 26, alpha)));
        Render2D.drawRoundOutline(context, valBoxX, valBoxY, valBoxW, valBoxH, 4.0F * scale, 1.0F, valFocused ? argb(200, 124, 58, 237, alpha) : argb(255, 30, 35, 48, alpha));

        String displayVal = valFocused ? (sliderInputBuffer + (System.currentTimeMillis() % 1000L < 500L ? "|" : "")) : slider.getValueString();
        ModernFont.drawCentered(context, displayVal, valBoxX + valBoxW * 0.5F, centeredTextY(valBoxY, valBoxH, 10.0F * scale, ModernFont.Type.INTER_SEMIBOLD), 10.0F * scale,
                valFocused ? argb(255, 216, 180, 254, alpha) : argb(240, 226, 232, 240, alpha), ModernFont.Type.INTER_SEMIBOLD);

        Render2D.drawRound(context, trackX, trackY, trackW, trackH, 1.75F * scale, argb(255, 22, 25, 36, alpha));

        float targetNorm = slider.getNormalized(), currentNorm = sliderVisualAnim.getOrDefault(slider, targetNorm);
        currentNorm = currentNorm + (targetNorm - currentNorm) * Math.min(1.0F, dt * 14.0F);
        sliderVisualAnim.put(slider, currentNorm);

        float fillW = trackW * currentNorm;
        if (fillW > 0.5F) {
            Render2D.drawRound(context, trackX, trackY, fillW, trackH, 1.75F * scale, argb(255, 124, 58, 237, alpha));
        }

        float knobX = trackX + fillW, knobY = trackY + trackH * 0.5F;
        boolean hovered = inside(mouseX, mouseY, trackX - 6.0F * scale, trackY - 8.0F * scale, trackW + 12.0F * scale, trackH + 16.0F * scale);
        float knobR = (hovered || activeSlider == slider) ? 4.0F * scale : 3.0F * scale;

        Render2D.drawCircle(context, knobX, knobY + 0.5F * scale, knobR, argb(70, 0, 0, 0, alpha));
        Render2D.drawCircle(context, knobX, knobY, knobR, argb(255, 250, 250, 255, alpha));
        Render2D.drawCircleOutline(context, knobX, knobY, knobR, 1.0F, argb(200, 124, 58, 237, alpha));
    }

    private float drawModeAccordion(DrawContext context, ModeSetting mode, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt, float scale) {
        float targetAnim = (openDropdown == mode) ? 1.0F : 0.0F;
        float curAnim = dropdownAnim.getOrDefault(mode, 0.0F);
        curAnim = curAnim + (targetAnim - curAnim) * Math.min(1.0F, dt * 18.0F);
        dropdownAnim.put(mode, curAnim);

        float boxW = 136.0F * scale, baseBoxH = 22.0F * scale, boxX = x + w - boxW, boxY = y + 1.5F * scale;
        float textSize = 12.0F * scale;
        ModernFont.draw(context, getSettingDisplayName(mode.getName()), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);

        boolean isOpen = curAnim > 0.01F;
        boolean headerHover = inside(mouseX, mouseY, boxX, boxY, boxW, baseBoxH);
        Render2D.drawRound(context, boxX, boxY, boxW, baseBoxH, 5.0F * scale, (headerHover || isOpen) ? argb(255, 24, 28, 40, alpha) : argb(255, 18, 21, 30, alpha));
        Render2D.drawRoundOutline(context, boxX, boxY, boxW, baseBoxH, 5.0F * scale, 1.0F, (headerHover || isOpen) ? argb(200, 124, 58, 237, alpha) : argb(255, 30, 35, 48, alpha));
        ModernFont.draw(context, mode.get(), boxX + 8.0F * scale, centeredTextY(boxY, baseBoxH, 10.5F * scale, ModernFont.Type.INTER_SEMIBOLD), 10.5F * scale, argb(255, 240, 245, 255, alpha), ModernFont.Type.INTER_SEMIBOLD);

        float chW = 9.0F * scale, chH = 9.0F * scale;
        float chCenterX = boxX + boxW - 12.0F * scale;
        float chCenterY = boxY + baseBoxH * 0.5F;
        drawRotatedChevron(context, chCenterX, chCenterY, chW, chH, easeOutCubic(curAnim) * (float) Math.PI, argb(140, 148, 163, 184, alpha));

        float totalOptionH = mode.getModes().size() * 22.0F * scale;
        float animH = totalOptionH * easeOutCubic(curAnim);

        if (curAnim > 0.001F) {
            float dropY = boxY + baseBoxH + 4.0F * scale;
            Render2D.drawRound(context, boxX, dropY, boxW, animH, 5.0F * scale, argb(255, 16, 18, 26, alpha * curAnim));
            Render2D.drawRoundOutline(context, boxX, dropY, boxW, animH, 5.0F * scale, 1.0F, argb(255, 36, 42, 60, alpha * curAnim));

            Render2D.pushScissor(boxX, dropY, boxW, animH);
            context.enableScissor((int) boxX, (int) dropY, (int) (boxX + boxW), (int) (dropY + animH));

            float optY = dropY;
            for (String m : mode.getModes()) {
                boolean active = m.equalsIgnoreCase(mode.get());
                boolean optHover = inside(mouseX, mouseY, boxX, optY, boxW, 22.0F * scale);
                if (active) {
                    Render2D.drawRound(context, boxX + 2.0F * scale, optY + 1.0F * scale, boxW - 4.0F * scale, 20.0F * scale, 4.0F * scale, argb(40, 124, 58, 237, alpha * curAnim));
                } else if (optHover) {
                    Render2D.drawRound(context, boxX + 2.0F * scale, optY + 1.0F * scale, boxW - 4.0F * scale, 20.0F * scale, 4.0F * scale, argb(20, 255, 255, 255, alpha * curAnim));
                }

                ModernFont.draw(context, m, boxX + 8.0F * scale, centeredTextY(optY, 22.0F * scale, 10.5F * scale, ModernFont.Type.INTER_MEDIUM), 10.5F * scale,
                        active ? argb(255, 192, 132, 252, alpha * curAnim) : argb(180, 203, 213, 225, alpha * curAnim), ModernFont.Type.INTER_MEDIUM);

                if (active) {
                    Render2D.drawCircle(context, boxX + boxW - 12.0F * scale, optY + 11.0F * scale, 2.5F * scale, argb(255, 124, 58, 237, alpha * curAnim));
                }
                optY += 22.0F * scale;
            }

            context.disableScissor();
            Render2D.popScissor();
        }

        return 34.0F * scale + animH;
    }

    private float drawMultiModeAccordion(DrawContext context, MultiModeSetting multi, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt, float scale) {
        boolean en = isEn();
        float targetAnim = (openMultiDropdown == multi) ? 1.0F : 0.0F;
        float curAnim = multiDropdownAnim.getOrDefault(multi, 0.0F);
        curAnim = curAnim + (targetAnim - curAnim) * Math.min(1.0F, dt * 18.0F);
        multiDropdownAnim.put(multi, curAnim);

        float boxW = 136.0F * scale, baseBoxH = 22.0F * scale, boxX = x + w - boxW, boxY = y + 1.5F * scale;
        float textSize = 12.0F * scale;
        ModernFont.draw(context, getSettingDisplayName(multi.getName()), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);

        boolean isOpen = curAnim > 0.01F;
        boolean headerHover = inside(mouseX, mouseY, boxX, boxY, boxW, baseBoxH);
        Render2D.drawRound(context, boxX, boxY, boxW, baseBoxH, 5.0F * scale, (headerHover || isOpen) ? argb(255, 24, 28, 40, alpha) : argb(255, 18, 21, 30, alpha));
        Render2D.drawRoundOutline(context, boxX, boxY, boxW, baseBoxH, 5.0F * scale, 1.0F, (headerHover || isOpen) ? argb(200, 124, 58, 237, alpha) : argb(255, 30, 35, 48, alpha));
        ModernFont.draw(context, multi.getDisplaySummary(en), boxX + 8.0F * scale, centeredTextY(boxY, baseBoxH, 10.5F * scale, ModernFont.Type.INTER_SEMIBOLD), 10.5F * scale, argb(255, 240, 245, 255, alpha), ModernFont.Type.INTER_SEMIBOLD);

        float chW = 9.0F * scale, chH = 9.0F * scale;
        float chCenterX = boxX + boxW - 12.0F * scale;
        float chCenterY = boxY + baseBoxH * 0.5F;
        drawRotatedChevron(context, chCenterX, chCenterY, chW, chH, easeOutCubic(curAnim) * (float) Math.PI, argb(140, 148, 163, 184, alpha));

        float totalOptionH = multi.getAllOptions().size() * 22.0F * scale;
        float animH = totalOptionH * easeOutCubic(curAnim);

        if (curAnim > 0.001F) {
            float dropY = boxY + baseBoxH + 4.0F * scale;
            Render2D.drawRound(context, boxX, dropY, boxW, animH, 5.0F * scale, argb(255, 16, 18, 26, alpha * curAnim));
            Render2D.drawRoundOutline(context, boxX, dropY, boxW, animH, 5.0F * scale, 1.0F, argb(255, 36, 42, 60, alpha * curAnim));

            Render2D.pushScissor(boxX, dropY, boxW, animH);
            context.enableScissor((int) boxX, (int) dropY, (int) (boxX + boxW), (int) (dropY + animH));

            float optY = dropY;
            for (String m : multi.getAllOptions()) {
                boolean active = multi.isSelected(m);
                boolean optHover = inside(mouseX, mouseY, boxX, optY, boxW, 22.0F * scale);
                if (active) {
                    Render2D.drawRound(context, boxX + 2.0F * scale, optY + 1.0F * scale, boxW - 4.0F * scale, 20.0F * scale, 4.0F * scale, argb(40, 124, 58, 237, alpha * curAnim));
                } else if (optHover) {
                    Render2D.drawRound(context, boxX + 2.0F * scale, optY + 1.0F * scale, boxW - 4.0F * scale, 20.0F * scale, 4.0F * scale, argb(20, 255, 255, 255, alpha * curAnim));
                }

                ModernFont.draw(context, m, boxX + 8.0F * scale, centeredTextY(optY, 22.0F * scale, 10.5F * scale, ModernFont.Type.INTER_MEDIUM), 10.5F * scale,
                        active ? argb(255, 192, 132, 252, alpha * curAnim) : argb(180, 203, 213, 225, alpha * curAnim), ModernFont.Type.INTER_MEDIUM);

                if (active) {
                    Render2D.drawCircle(context, boxX + boxW - 12.0F * scale, optY + 11.0F * scale, 2.5F * scale, argb(255, 124, 58, 237, alpha * curAnim));
                }
                optY += 22.0F * scale;
            }

            context.disableScissor();
            Render2D.popScissor();
        }

        return 34.0F * scale + animH;
    }

    private static void drawAnimatedDashedBorder(DrawContext context, float x, float y, float w, float h, float thickness, int color) {
        float dash = 5.0F;
        float gap = 3.5F;
        float seg = dash + gap;
        float offset = ((System.currentTimeMillis() % 1200L) / 1200.0F) * seg;

        for (float cur = -offset; cur < w; cur += seg) {
            float startX = Math.max(0.0F, cur);
            float endX = Math.min(w, cur + dash);
            if (endX > startX) Render2D.drawLine(context, x + startX, y, x + endX, y, thickness, color);
        }
        for (float cur = -offset + (w % seg); cur < h; cur += seg) {
            float startY = Math.max(0.0F, cur);
            float endY = Math.min(h, cur + dash);
            if (endY > startY) Render2D.drawLine(context, x + w, y + startY, x + w, y + endY, thickness, color);
        }
        for (float cur = -offset + ((w + h) % seg); cur < w; cur += seg) {
            float startX = Math.max(0.0F, cur);
            float endX = Math.min(w, cur + dash);
            if (endX > startX) Render2D.drawLine(context, x + w - startX, y + h, x + w - endX, y + h, thickness, color);
        }
        for (float cur = -offset + ((2 * w + h) % seg); cur < h; cur += seg) {
            float startY = Math.max(0.0F, cur);
            float endY = Math.min(h, cur + dash);
            if (endY > startY) Render2D.drawLine(context, x, y + h - startY, x, y + h - endY, thickness, color);
        }
    }

    private void drawAutoBuyPanelButton(DrawContext context, float x, float y, float w, int mouseX, int mouseY, float alpha, float scale) {
        boolean hover = inside(mouseX, mouseY, x, y + 2.0F * scale, w, 22.0F * scale);
        int fill = hover ? argb(255, 109, 75, 196, alpha) : argb(235, 48, 36, 85, alpha);
        Render2D.drawRound(context, x, y + 2.0F * scale, w, 22.0F * scale, 6.0F * scale, fill);
        Render2D.drawRoundOutline(context, x, y + 2.0F * scale, w, 22.0F * scale, 6.0F * scale, 1.0F,
                argb(hover ? 220 : 125, 177, 139, 255, alpha));
        drawTexture(context, ICON_AUTOBUY, x + 8.0F * scale, y + 6.0F * scale, 14.0F * scale, 14.0F * scale,
                argb(255, 255, 255, 255, alpha));
        ModernFont.draw(context, t("Открыть панель покупки", "Open purchase panel"), x + 28.0F * scale,
                y + 7.0F * scale, 9.5F * scale, argb(255, 255, 255, 255, alpha), ModernFont.Type.SF_BOLD);
        ModernFont.drawRight(context, ">", x + w - 10.0F * scale, y + 7.0F * scale, 10.0F * scale,
                argb(210, 255, 255, 255, alpha), ModernFont.Type.SF_BOLD);
    }

    private static List<String> commaList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void drawKeybindRow(DrawContext context, String name, int keyCode, boolean listening, float x, float y, float w, int mouseX, int mouseY, float alpha, float scale) {
        float textSize = 12.0F * scale;
        ModernFont.draw(context, getSettingDisplayName(name), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
        
        String keyText = listening ? "..." : KeybindSetting.getKeyName(keyCode, isEn());
        float fontSize = 10.0F * scale;
        float btnW = Math.max(54.0F * scale, ModernFont.getWidth(keyText, fontSize, ModernFont.Type.SF_BOLD) + 16.0F * scale);
        float btnH = 20.0F * scale, btnX = x + w - btnW, btnY = y + 2.5F * scale;
        boolean hovered = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);

        Render2D.drawRound(context, btnX, btnY, btnW, btnH, 5.0F * scale, listening ? argb(255, 24, 20, 36, alpha) : argb(255, 18, 21, 30, alpha));
        if (listening) {
            drawAnimatedDashedBorder(context, btnX, btnY, btnW, btnH, 1.0F * scale, argb(255, 192, 132, 252, alpha));
        } else {
            Render2D.drawRoundOutline(context, btnX, btnY, btnW, btnH, 5.0F * scale, 1.0F, hovered ? argb(160, 124, 58, 237, alpha) : argb(255, 30, 35, 48, alpha));
        }

        ModernFont.drawCentered(context, keyText, btnX + btnW * 0.5F, centeredTextY(btnY, btnH, fontSize, ModernFont.Type.SF_BOLD), fontSize,
                listening ? argb(255, 216, 180, 254, alpha) : argb(255, 226, 232, 240, alpha), ModernFont.Type.SF_BOLD);
    }

    private void drawCheckerboard(DrawContext context, float x, float y, float w, float h, float r, float alpha) {
        Render2D.drawRound(context, x, y, w, h, r, argb(255, 204, 204, 204, alpha));
        Render2D.pushScissor(x, y, w, h);
        context.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        float checkSize = 4.0F;
        int darkCol = argb(255, 150, 150, 150, alpha);
        int cols = (int) Math.ceil(w / checkSize) + 1;
        int rows = (int) Math.ceil(h / checkSize) + 1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if ((row + col) % 2 == 1) {
                    float cx = x + col * checkSize;
                    float cy = y + row * checkSize;
                    float cw = Math.min(checkSize, x + w - cx);
                    float ch = Math.min(checkSize, y + h - cy);
                    boolean inTopLeft = cx < x + r && cy < y + r;
                    boolean inTopRight = (cx + cw) > (x + w - r) && cy < y + r;
                    boolean inBottomLeft = cx < x + r && (cy + ch) > (y + h - r);
                    boolean inBottomRight = (cx + cw) > (x + w - r) && (cy + ch) > (y + h - r);
                    if (inTopLeft || inTopRight || inBottomLeft || inBottomRight) {
                        continue;
                    }
                    Render2D.drawRound(context, cx, cy, cw, ch, 0.0F, darkCol);
                }
            }
        }
        context.disableScissor();
        Render2D.popScissor();
    }

    private void drawColorRow(DrawContext context, ColorSetting color, float x, float y, float w, int mouseX, int mouseY, float alpha, float scale) {
        float textSize = 12.0F * scale;
        ModernFont.draw(context, getSettingDisplayName(color.getName()), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);

        float btnSize = 18.0F * scale;
        float btnY = y + (25.0F * scale - btnSize) * 0.5F;
        float swatchX = x + w - btnSize;
        float pipetteX = swatchX - btnSize - 4.0F * scale;
        boolean pipetteHover = inside(mouseX, mouseY, pipetteX, btnY, btnSize, btnSize);
        Render2D.drawRound(context, pipetteX, btnY, btnSize, btnSize, 4.0F * scale,
        pipetteHover ? argb(255, 40, 48, 70, alpha) : argb(255, 24, 29, 44, alpha));
        Render2D.drawRoundOutline(context, pipetteX, btnY, btnSize, btnSize, 4.0F * scale, 1.0F,
                pipetteHover ? argb(255, 255, 255, 255, alpha) : argb(255, 42, 48, 66, alpha));
        drawTexture(context, ICON_PIPETTE, pipetteX + 3.0F * scale, btnY + 3.0F * scale,
                12.0F * scale, 12.0F * scale, argb(220, 168, 177, 198, alpha));
        boolean swatchHover = inside(mouseX, mouseY, swatchX, btnY, btnSize, btnSize);
        boolean isActive = activeColorPicker == color;

        // Color square swatch with checkerboard underlay for transparency
        drawCheckerboard(context, swatchX, btnY, btnSize, btnSize, 4.0F * scale, alpha);
        Render2D.drawRound(context, swatchX, btnY, btnSize, btnSize, 4.0F * scale, color(color.get(), alpha));
        Render2D.drawRoundOutline(context, swatchX, btnY, btnSize, btnSize, 4.0F * scale, 1.0F,
                isActive ? argb(220, 124, 58, 237, alpha) : (swatchHover ? argb(255, 255, 255, 255, alpha) : argb(255, 42, 48, 66, alpha)));

        if (isActive) {
            pickerX = swatchX + btnSize;
            pickerY = btnY;
        }
    }

    private void drawStringRow(DrawContext context, StringSetting str, float x, float y, float w, int mouseX, int mouseY, float alpha, float scale) {
        float textSize = 11.5F * scale;
        boolean focused = activeStringSetting == str;
        String val = focused ? activeStringDraft : str.get();
        String displayStr = val + (focused && (System.currentTimeMillis() % 1000L < 500L) ? "|" : "");
        String shownText = val.isEmpty() && !focused ? str.getPlaceholder() : displayStr;

        float labelW = ModernFont.getWidth(getSettingDisplayName(str.getName()), textSize, ModernFont.Type.INTER_MEDIUM);
        float textW = ModernFont.getWidth(shownText, 10.5F * scale, ModernFont.Type.INTER_MEDIUM);
        float maxBoxW = w - labelW - 12.0F * scale;
        float boxW = Math.max(120.0F * scale, Math.min(maxBoxW, textW + 24.0F * scale));
        boxW = Math.min(boxW, w - 16.0F * scale);
        float boxH = 20.0F * scale, boxX = x + w - boxW, boxY = y + 2.5F * scale;

        if (boxX > x + 10.0F * scale) {
            ModernFont.draw(context, getSettingDisplayName(str.getName()), x, centeredTextY(y, 25.0F * scale, textSize, ModernFont.Type.INTER_MEDIUM), textSize, argb(220, 226, 232, 240, alpha), ModernFont.Type.INTER_MEDIUM);
        }

        Render2D.drawRound(context, boxX, boxY, boxW, boxH, 5.0F * scale, focused ? argb(255, 24, 29, 44, alpha) : argb(255, 18, 21, 30, alpha));
        Render2D.drawRoundOutline(context, boxX, boxY, boxW, boxH, 5.0F * scale, 1.0F, focused ? argb(200, 124, 58, 237, alpha) : argb(255, 38, 45, 64, alpha));

        drawMarqueeText(context, shownText, boxX, boxY, boxW, boxH, 10.5F * scale,
                val.isEmpty() && !focused ? argb(140, 148, 163, 184, alpha) : argb(255, 240, 245, 255, alpha), focused, scale);
    }

    private void drawMarqueeText(DrawContext context, String text, float boxX, float boxY, float boxW, float boxH, float fontSize, int color, boolean focused, float scale) {
        float availW = boxW - 14.0F * scale;
        float textW = ModernFont.getWidth(text, fontSize, ModernFont.Type.INTER_MEDIUM);
        Render2D.pushScissor(boxX + 3.0F * scale, boxY, boxW - 6.0F * scale, boxH);
        float drawX = boxX + 7.0F * scale;
        if (textW > availW) {
            if (focused) {
                drawX = boxX + boxW - 7.0F * scale - textW;
            } else {
                float maxScroll = textW - availW + 4.0F * scale;
                float cycle = (System.currentTimeMillis() % 6000L) / 6000.0F;
                float wave = (float) (0.5 - 0.5 * Math.cos(cycle * Math.PI * 2.0));
                drawX = boxX + 7.0F * scale - wave * maxScroll;
            }
        }
        ModernFont.draw(context, text, drawX, centeredTextY(boxY, boxH, fontSize, ModernFont.Type.INTER_MEDIUM), fontSize, color, ModernFont.Type.INTER_MEDIUM);
        Render2D.popScissor();
    }

    private void drawPopups(DrawContext context, GuiBounds b, int mouseX, int mouseY, float alpha, float dt) {
        float scale = b.scale;
        boolean en = isEn();

        // 1. Sort Dropdown Popup
        if (sortDropdownOpen || sortDropdownAnim > 0.001F) {
            float targetAnim = sortDropdownOpen ? 1.0F : 0.0F;
            sortDropdownAnim = sortDropdownAnim + (targetAnim - sortDropdownAnim) * Math.min(1.0F, dt * 16.0F);
            if (sortDropdownAnim > 0.001F) {
                float sortAlpha = easeOutCubic(sortDropdownAnim) * alpha;
                float popW = 144.0F * scale;
                float popH = SortMode.values().length * (23.0F * scale) + 8.0F * scale;
                float popX = b.box1X + b.boxW - popW - 10.0F * scale;
                float popY = b.boxY + 35.0F * scale;

                Render2D.drawShadow(context, popX, popY, popW, popH, 12.0F * scale, 12.0F * scale, argb(220, 0, 0, 0, sortAlpha));
                Render2D.drawRound(context, popX, popY, popW, popH, 6.0F * scale, argb(255, 18, 20, 29, sortAlpha));
                Render2D.drawRoundOutline(context, popX, popY, popW, popH, 6.0F * scale, 1.0F, argb(255, 42, 48, 68, sortAlpha));

                float itemY = popY + 4.0F * scale;
                for (SortMode mode : SortMode.values()) {
                    boolean active = mode == sortMode;
                    boolean hover = inside(mouseX, mouseY, popX + 4.0F * scale, itemY, popW - 8.0F * scale, 21.0F * scale);
                    if (active) {
                        Render2D.drawRound(context, popX + 4.0F * scale, itemY, popW - 8.0F * scale, 21.0F * scale, 4.0F * scale, argb(50, 124, 58, 237, sortAlpha));
                    } else if (hover) {
                        Render2D.drawRound(context, popX + 4.0F * scale, itemY, popW - 8.0F * scale, 21.0F * scale, 4.0F * scale, argb(20, 255, 255, 255, sortAlpha));
                    }
                    ModernFont.draw(context, mode.label(en), popX + 8.0F * scale, itemY + 5.5F * scale, 10.5F * scale,
                            active ? argb(255, 192, 132, 252, sortAlpha) : argb(180, 203, 213, 225, sortAlpha), ModernFont.Type.INTER_MEDIUM);
                    if (active) {
                        Render2D.drawCircle(context, popX + popW - 12.0F * scale, itemY + 10.5F * scale, 2.5F * scale, argb(255, 124, 58, 237, sortAlpha));
                    }
                    itemY += 23.0F * scale;
                }
            }
        }

        // 2. 3-Dots Module Context Menu Popup (#181922 design)
        if (activeContextMenuModule != null) {
            if (contextMenuClosing) {
                contextMenuAnim = Math.max(0.0F, contextMenuAnim - dt * 16.0F);
                if (contextMenuAnim <= 0.001F) {
                    activeContextMenuModule = null;
                    contextMenuClosing = false;
                }
            } else {
                contextMenuAnim = Math.min(1.0F, contextMenuAnim + dt * 7.5F);
            }

            if (contextMenuAnim > 0.001F && activeContextMenuModule != null) {
                float cmAlpha = easeOutCubic(contextMenuAnim) * alpha;
                float popW = 168.0F * scale;
                float itemH = 25.0F * scale;
                float popH = 5 * itemH + 8.0F * scale;
                
                float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
                float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
                float popX = Math.min(screenW - popW - 10.0F * scale, contextMenuX);
                float popY = Math.min(screenH - popH - 10.0F * scale, contextMenuY);

                Render2D.drawShadow(context, popX, popY, popW, popH, 16.0F * scale, 12.0F * scale, argb(200, 0, 0, 0, cmAlpha));
                Render2D.drawRound(context, popX, popY, popW, popH, 7.0F * scale, argb(255, 15, 17, 24, cmAlpha));
                Render2D.drawRoundOutline(context, popX, popY, popW, popH, 7.0F * scale, 1.0F, argb(255, 32, 37, 50, cmAlpha));
                Render2D.drawLine(context, popX + 6.0F * scale, popY + 1.0F, popX + popW - 6.0F * scale, popY + 1.0F, 1.0F, argb(20, 255, 255, 255, cmAlpha));

                float curItemY = popY + 4.0F * scale;
                float iconOffY = (itemH - 16.0F * scale) * 0.5F;
                float menuTextSize = 10.5F * scale;

                // Item 0: Pin / Unpin
                boolean isPinned = PINNED_MODULES.contains(activeContextMenuModule.getName());
                boolean h0 = inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH);
                if (h0) Render2D.drawRound(context, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH, 4.5F * scale, argb(24, 255, 255, 255, cmAlpha));
                drawTexture(context, ICON_PIN, Math.round(popX + 10.0F * scale), Math.round(curItemY + iconOffY), Math.round(16.0F * scale), Math.round(16.0F * scale),
                        isPinned ? argb(255, 196, 181, 253, cmAlpha) : argb(200, 148, 163, 184, cmAlpha));
                
                String pinLabel = isPinned ? t("Открепить", "Unpin") : t("Закрепить вверху", "Pin to Top");
                if (contextMenuFeedback != null && System.currentTimeMillis() - contextMenuFeedbackTime < 1500L) {
                    if ("pinned".equals(contextMenuFeedback)) pinLabel = t("Закреплено!", "Pinned!");
                    else if ("unpinned".equals(contextMenuFeedback)) pinLabel = t("Откреплено!", "Unpinned!");
                }
                ModernFont.draw(context, pinLabel, popX + 30.0F * scale, centeredTextY(curItemY, itemH, menuTextSize, ModernFont.Type.INTER_MEDIUM), menuTextSize,
                        isPinned ? argb(255, 196, 181, 253, cmAlpha) : argb(255, 226, 232, 240, cmAlpha), ModernFont.Type.INTER_MEDIUM);
                curItemY += itemH;

                // Item 1: Module Description
                boolean h1 = inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH);
                if (h1) Render2D.drawRound(context, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH, 4.5F * scale, argb(24, 255, 255, 255, cmAlpha));
                drawTexture(context, ICON_INFO, Math.round(popX + 10.0F * scale), Math.round(curItemY + iconOffY), Math.round(16.0F * scale), Math.round(16.0F * scale), argb(220, 196, 181, 253, cmAlpha));
                String infoLabel = t("Описание модуля", "Module Info");
                ModernFont.draw(context, infoLabel, popX + 30.0F * scale, centeredTextY(curItemY, itemH, menuTextSize, ModernFont.Type.INTER_MEDIUM), menuTextSize, argb(255, 241, 245, 249, cmAlpha), ModernFont.Type.INTER_MEDIUM);
                curItemY += itemH;

                // Item 2: Copy Settings
                boolean h2 = inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH);
                if (h2) Render2D.drawRound(context, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH, 4.5F * scale, argb(24, 255, 255, 255, cmAlpha));
                drawTexture(context, ICON_COPY, Math.round(popX + 10.0F * scale), Math.round(curItemY + iconOffY), Math.round(16.0F * scale), Math.round(16.0F * scale), argb(200, 148, 163, 184, cmAlpha));
                String copyLabel = (contextMenuFeedback != null && System.currentTimeMillis() - contextMenuFeedbackTime < 1500L && "copied".equals(contextMenuFeedback))
                        ? t("Скопировано!", "Copied!") : t("Скопировать настройки", "Copy Settings");
                ModernFont.draw(context, copyLabel, popX + 30.0F * scale, centeredTextY(curItemY, itemH, menuTextSize, ModernFont.Type.INTER_MEDIUM), menuTextSize,
                        "copied".equals(contextMenuFeedback) ? argb(255, 52, 211, 153, cmAlpha) : argb(255, 226, 232, 240, cmAlpha), ModernFont.Type.INTER_MEDIUM);
                curItemY += itemH;

                // Item 3: Paste Settings
                boolean h3 = inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH);
                if (h3) Render2D.drawRound(context, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH, 4.5F * scale, argb(24, 255, 255, 255, cmAlpha));
                drawTexture(context, ICON_PASTE, Math.round(popX + 10.0F * scale), Math.round(curItemY + iconOffY), Math.round(16.0F * scale), Math.round(16.0F * scale), argb(200, 148, 163, 184, cmAlpha));
                String pasteLabel = (contextMenuFeedback != null && System.currentTimeMillis() - contextMenuFeedbackTime < 1500L && "pasted".equals(contextMenuFeedback))
                        ? t("Вставлено!", "Pasted!") : t("Вставить настройки", "Paste Settings");
                ModernFont.draw(context, pasteLabel, popX + 30.0F * scale, centeredTextY(curItemY, itemH, menuTextSize, ModernFont.Type.INTER_MEDIUM), menuTextSize,
                        "pasted".equals(contextMenuFeedback) ? argb(255, 52, 211, 153, cmAlpha) : argb(255, 226, 232, 240, cmAlpha), ModernFont.Type.INTER_MEDIUM);
                curItemY += itemH;

                // Item 4: Reset to Default (with subtle separator line before destructive action)
                Render2D.drawLine(context, popX + 8.0F * scale, curItemY - 1.5F * scale, popX + popW - 8.0F * scale, curItemY - 1.5F * scale, 1.0F, argb(20, 255, 255, 255, cmAlpha));
                boolean h4 = inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH);
                if (h4) Render2D.drawRound(context, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH, 4.5F * scale, argb(26, 220, 60, 60, cmAlpha));
                int warnColor = h4 ? argb(255, 248, 140, 140, cmAlpha) : argb(170, 220, 130, 130, cmAlpha);
                drawTexture(context, ICON_RESET, Math.round(popX + 10.0F * scale), Math.round(curItemY + iconOffY), Math.round(16.0F * scale), Math.round(16.0F * scale), warnColor);
                ModernFont.draw(context, t("Сбросить по умолчанию", "Reset to Default"), popX + 30.0F * scale, centeredTextY(curItemY, itemH, menuTextSize, ModernFont.Type.INTER_MEDIUM), menuTextSize, warnColor, ModernFont.Type.INTER_MEDIUM);
            }
        }

        // 3. Color Picker Popup
        if (activeColorPicker != null) {
            if (colorPickerClosing) {
                colorPickerAnim = Math.max(0.0F, colorPickerAnim - dt * 16.0F);
                if (colorPickerAnim <= 0.001F) {
                    activeColorPicker = null;
                    colorPickerClosing = false;
                }
            } else {
                colorPickerAnim = Math.min(1.0F, colorPickerAnim + dt * 8.0F);
            }

            if (colorPickerAnim > 0.001F && activeColorPicker != null) {
                float popAlpha = Math.min(1.0F, easeOutCubic(colorPickerAnim)) * alpha;
                float animYOffset = (1.0F - easeOutCubic(colorPickerAnim)) * 6.0F * scale;

                float pickW = 194.0F * scale;
                float pickH = 192.0F * scale;
                float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
                float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
                float px = (pickerX > 0) ? (pickerX - pickW) : (screenW - pickW) * 0.5F;
                float py = (pickerY > 0) ? (pickerY + 24.0F * scale + animYOffset) : (screenH - pickH) * 0.5F;
                px = Math.max(10.0F * scale, Math.min(screenW - pickW - 10.0F * scale, px));
                py = Math.max(10.0F * scale, Math.min(screenH - pickH - 10.0F * scale, py));

                Render2D.drawShadow(context, px, py, pickW, pickH, 16.0F * scale, 16.0F * scale, argb(180, 0, 0, 0, popAlpha));
                Render2D.drawRound(context, px, py, pickW, pickH, 6.0F * scale, argb(255, 18, 21, 30, popAlpha));
                Render2D.drawRoundOutline(context, px, py, pickW, pickH, 6.0F * scale, 1.0F, argb(255, 38, 45, 64, popAlpha));

                // Header: Pipette Icon + Title & Close Button
                float hdrY = py + 8.0F * scale;
                drawTexture(context, ICON_PIPETTE, px + 10.0F * scale, hdrY + 2.5F * scale, 13.0F * scale, 13.0F * scale, argb(255, 168, 85, 247, popAlpha));
                String title = getSettingDisplayName(activeColorPicker.getName());
                ModernFont.draw(context, title, px + 27.0F * scale, hdrY + 2.0F * scale, 11.5F * scale, argb(255, 241, 245, 249, popAlpha), ModernFont.Type.SF_BOLD);

                float closeSize = 16.0F * scale;
                float closeX = px + pickW - closeSize - 8.0F * scale;
                float closeY = hdrY + 1.0F * scale;
                boolean closeHover = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
                Render2D.drawRound(context, closeX, closeY, closeSize, closeSize, 3.5F * scale,
                        closeHover ? argb(60, 239, 68, 68, popAlpha) : argb(18, 255, 255, 255, popAlpha));
                float cx = closeX + closeSize * 0.5F, cy = closeY + closeSize * 0.5F, cr = 2.5F * scale;
                int crossCol = closeHover ? argb(255, 252, 165, 165, popAlpha) : argb(160, 148, 163, 184, popAlpha);
                Render2D.drawLine(context, cx - cr, cy - cr, cx + cr, cy + cr, 1.2F * scale, crossCol);
                Render2D.drawLine(context, cx + cr, cy - cr, cx - cr, cy + cr, 1.2F * scale, crossCol);

                Render2D.drawLine(context, px + 10.0F * scale, hdrY + 18.0F * scale, px + pickW - 10.0F * scale, hdrY + 18.0F * scale, 1.0F, argb(255, 28, 33, 46, popAlpha));

                // 1. SV Area
                float svX = px + 10.0F * scale;
                float svY = hdrY + 24.0F * scale;
                float svW = pickW - 20.0F * scale;
                float svH = 64.0F * scale;

                int baseHueCol = Color.HSBtoRGB(pickerHue, 1.0F, 1.0F);
                Render2D.drawRound(context, svX, svY, svW, svH, 4.0F * scale, color(baseHueCol, popAlpha));
                Render2D.drawGradientRoundLR(context, svX, svY, svW, svH, 4.0F * scale, color(0xFFFFFFFF, popAlpha), color(0x00FFFFFF, popAlpha));
                Render2D.drawGradientRoundTB(context, svX, svY, svW, svH, 4.0F * scale, color(0x00000000, popAlpha), color(0xFF000000, popAlpha));
                Render2D.drawRoundOutline(context, svX, svY, svW, svH, 4.0F * scale, 1.0F, argb(40, 255, 255, 255, popAlpha));

                // SV Knob
                float svKnobInset = 4.0F * scale;
                float cursorX = svX + svKnobInset + (svW - svKnobInset * 2.0F) * pickerSat;
                float cursorY = svY + svKnobInset + (svH - svKnobInset * 2.0F) * (1.0F - pickerBri);
                Render2D.drawCircle(context, cursorX, cursorY, 4.0F * scale, argb(255, 255, 255, 255, popAlpha));
                Render2D.drawCircle(context, cursorX, cursorY, 2.8F * scale, color(0xFF000000 | Color.HSBtoRGB(pickerHue, pickerSat, pickerBri), popAlpha));
                Render2D.drawCircleOutline(context, cursorX, cursorY, 4.0F * scale, 1.0F, argb(120, 0, 0, 0, popAlpha));

                // 2. Hue Bar
                float hueY = svY + svH + 8.0F * scale;
                float hueH = 7.0F * scale;
                drawSeamlessHueBar(context, svX, hueY, svW, hueH, 3.5F * scale, popAlpha);

                // Hue Knob
                float hueKnobInset = 4.0F * scale;
                float hueKnobX = svX + hueKnobInset + (svW - hueKnobInset * 2.0F) * pickerHue;
                float hueKnobY = hueY + hueH * 0.5F;
                float knobR = 4.5F * scale;
                Render2D.drawCircle(context, hueKnobX, hueKnobY, knobR, argb(255, 255, 255, 255, popAlpha));
                Render2D.drawCircle(context, hueKnobX, hueKnobY, 3.0F * scale, color(0xFF000000 | Color.HSBtoRGB(pickerHue, 1.0F, 1.0F), popAlpha));
                Render2D.drawCircleOutline(context, hueKnobX, hueKnobY, knobR, 1.0F, argb(100, 0, 0, 0, popAlpha));

                // 3. Opacity Row
                float opacityLabelY = hueY + hueH + 10.0F * scale;
                float opacTextSize = 10.0F * scale;
                ModernFont.draw(context, t("Прозрачность", "Opacity"), svX, opacityLabelY, opacTextSize, argb(200, 148, 163, 184, popAlpha), ModernFont.Type.INTER_MEDIUM);
                String pctText = Math.round(pickerAlpha * 100.0F) + "%";
                ModernFont.drawRight(context, pctText, svX + svW, opacityLabelY, opacTextSize, argb(255, 203, 213, 225, popAlpha), ModernFont.Type.SF_BOLD);

                // Alpha Slider
                float alphaBarY = opacityLabelY + 16.0F * scale;
                float alphaBarH = 8.0F * scale;
                drawCheckerboard(context, svX, alphaBarY, svW, alphaBarH, 4.0F * scale, popAlpha);
                int rgbNoA = Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF;
                Render2D.drawGradientRoundLR(context, svX, alphaBarY, svW, alphaBarH, 4.0F * scale, color(rgbNoA, 0.0F), color(0xFF000000 | rgbNoA, popAlpha));
                Render2D.drawRoundOutline(context, svX, alphaBarY, svW, alphaBarH, 4.0F * scale, 1.0F, argb(60, 255, 255, 255, popAlpha));

                // Alpha Knob
                float alphaKnobInset = 4.0F * scale;
                float alphaKnobX = svX + alphaKnobInset + (svW - alphaKnobInset * 2.0F) * pickerAlpha;
                float alphaKnobY = alphaBarY + alphaBarH * 0.5F;
                Render2D.drawCircle(context, alphaKnobX, alphaKnobY, knobR, argb(255, 255, 255, 255, popAlpha));
                Render2D.drawCircle(context, alphaKnobX, alphaKnobY, 3.0F * scale, color(activeColorPicker.get(), popAlpha));
                Render2D.drawCircleOutline(context, alphaKnobX, alphaKnobY, knobR, 1.0F, argb(100, 0, 0, 0, popAlpha));

                // 4. Compact Control Row: [Preview] [HEX Input] [Paste Icon] [Copy Icon]
                float hexRowY = alphaBarY + alphaBarH + 12.0F * scale;
                float hexRowH = 22.0F * scale;
                float prevW = 22.0F * scale;

                // Live Color Preview with checkerboard
                drawCheckerboard(context, svX, hexRowY, prevW, hexRowH, 4.0F * scale, popAlpha);
                Render2D.drawRound(context, svX, hexRowY, prevW, hexRowH, 4.0F * scale, color(activeColorPicker.get(), popAlpha));
                Render2D.drawRoundOutline(context, svX, hexRowY, prevW, hexRowH, 4.0F * scale, 1.0F, argb(80, 255, 255, 255, popAlpha));

                // Icon buttons dimensions
                float iconBtnSize = 22.0F * scale;
                float copyBtnX = svX + svW - iconBtnSize;
                float pasteBtnX = copyBtnX - iconBtnSize - 4.0F * scale;

                // Clean HEX Input Box
                float hexBoxX = svX + prevW + 5.0F * scale;
                float hexBoxW = pasteBtnX - 5.0F * scale - hexBoxX;
                boolean hexHover = inside(mouseX, mouseY, hexBoxX, hexRowY, hexBoxW, hexRowH);
                Render2D.drawRound(context, hexBoxX, hexRowY, hexBoxW, hexRowH, 4.0F * scale,
                        hexFocused ? argb(255, 28, 34, 52, popAlpha) : (hexHover ? argb(255, 24, 29, 44, popAlpha) : argb(255, 19, 23, 34, popAlpha)));
                Render2D.drawRoundOutline(context, hexBoxX, hexRowY, hexBoxW, hexRowH, 4.0F * scale, 1.0F,
                        hexFocused ? argb(200, 124, 58, 237, popAlpha) : argb(255, 38, 45, 64, popAlpha));

                String displayHex = hexFocused ? ("#" + hexBuffer + (System.currentTimeMillis() % 1000L < 500L ? "|" : ""))
                        : ((((activeColorPicker.get() >> 24) & 0xFF) == 255)
                            ? String.format("#%06X", activeColorPicker.get() & 0xFFFFFF)
                            : String.format("#%08X", activeColorPicker.get()));
                ModernFont.drawCentered(context, displayHex, hexBoxX + hexBoxW * 0.5F, centeredTextY(hexRowY, hexRowH, 9.5F * scale, ModernFont.Type.SF_BOLD),
                        9.5F * scale, argb(255, 226, 232, 240, popAlpha), ModernFont.Type.SF_BOLD);

                // Feedback status
                boolean isPasteSuccess = "paste_success".equals(colorFeedback) && (System.currentTimeMillis() - colorFeedbackTime < 1100L);
                boolean isPasteError = "paste_error".equals(colorFeedback) && (System.currentTimeMillis() - colorFeedbackTime < 1100L);
                boolean isCopySuccess = "copy_success".equals(colorFeedback) && (System.currentTimeMillis() - colorFeedbackTime < 1100L);

                // Paste Icon Button [ 📋 ]
                boolean pasteHover = inside(mouseX, mouseY, pasteBtnX, hexRowY, iconBtnSize, iconBtnSize);
                int pasteBg = isPasteSuccess ? argb(60, 34, 197, 94, popAlpha)
                        : (isPasteError ? argb(60, 239, 68, 68, popAlpha)
                        : (pasteHover ? argb(255, 30, 36, 52, popAlpha) : argb(255, 20, 24, 34, popAlpha)));
                int pasteBorder = isPasteSuccess ? argb(220, 34, 197, 94, popAlpha)
                        : (isPasteError ? argb(220, 239, 68, 68, popAlpha)
                        : (pasteHover ? argb(160, 124, 58, 237, popAlpha) : argb(255, 38, 45, 64, popAlpha)));
                int pasteIconCol = isPasteSuccess ? argb(255, 134, 239, 172, popAlpha)
                        : (isPasteError ? argb(255, 252, 165, 165, popAlpha)
                        : (pasteHover ? argb(255, 255, 255, 255, popAlpha) : argb(200, 203, 213, 225, popAlpha)));

                Render2D.drawRound(context, pasteBtnX, hexRowY, iconBtnSize, iconBtnSize, 4.0F * scale, pasteBg);
                Render2D.drawRoundOutline(context, pasteBtnX, hexRowY, iconBtnSize, iconBtnSize, 4.0F * scale, 1.0F, pasteBorder);
                drawTexture(context, ICON_PASTE, pasteBtnX + 5.0F * scale, hexRowY + 5.0F * scale, 12.0F * scale, 12.0F * scale, pasteIconCol);

                // Copy Icon Button [ 📄 ]
                boolean copyHover = inside(mouseX, mouseY, copyBtnX, hexRowY, iconBtnSize, iconBtnSize);
                int copyBg = isCopySuccess ? argb(60, 34, 197, 94, popAlpha)
                        : (copyHover ? argb(255, 30, 36, 52, popAlpha) : argb(255, 20, 24, 34, popAlpha));
                int copyBorder = isCopySuccess ? argb(220, 34, 197, 94, popAlpha)
                        : (copyHover ? argb(160, 124, 58, 237, popAlpha) : argb(255, 38, 45, 64, popAlpha));
                int copyIconCol = isCopySuccess ? argb(255, 134, 239, 172, popAlpha)
                        : (copyHover ? argb(255, 255, 255, 255, popAlpha) : argb(200, 203, 213, 225, popAlpha));

                Render2D.drawRound(context, copyBtnX, hexRowY, iconBtnSize, iconBtnSize, 4.0F * scale, copyBg);
                Render2D.drawRoundOutline(context, copyBtnX, hexRowY, iconBtnSize, iconBtnSize, 4.0F * scale, 1.0F, copyBorder);
                drawTexture(context, ICON_COPY, copyBtnX + 5.0F * scale, hexRowY + 5.0F * scale, 12.0F * scale, 12.0F * scale, copyIconCol);

                if (pipetteActive) {
                    float curX = (float) mouseX + 10.0F * scale;
                    float curY = (float) mouseY + 10.0F * scale;
                    Render2D.drawRound(context, curX, curY, 20.0F * scale, 20.0F * scale, 4.0F * scale, argb(230, 18, 21, 30, popAlpha));
                    Render2D.drawRoundOutline(context, curX, curY, 20.0F * scale, 20.0F * scale, 4.0F * scale, 1.0F, argb(255, 34, 197, 94, popAlpha));
                    drawTexture(context, ICON_PIPETTE, curX + 4.0F * scale, curY + 4.0F * scale, 12.0F * scale, 12.0F * scale, argb(255, 134, 239, 172, popAlpha));
                }
            }
        }

        // 4. Unrolling Floating Module Description Banner above GUI
        if (unrollingInfoModule != null) {
            long totalDuration = 7000L;
            long elapsed = System.currentTimeMillis() - unrollStartTime;
            float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - ((float) elapsed / (float) totalDuration)));

            if (unrollClosing) {
                unrollAnim = Math.max(0.0F, unrollAnim - dt * 6.0F);
                if (unrollAnim <= 0.001F) {
                    unrollingInfoModule = null;
                    unrollClosing = false;
                }
            } else {
                unrollAnim = Math.min(1.0F, unrollAnim + dt * 3.8F);
                if (elapsed > totalDuration) {
                    unrollClosing = true;
                }
            }

            if (unrollAnim > 0.001F && unrollingInfoModule != null) {
                float bannerAlpha = easeOutCubic(unrollAnim) * alpha;
                
                String desc = getModuleDescription(unrollingInfoModule, en);
                List<String> descLines = wrapText(desc, 44);

                // Calculate snug compact width based on actual content
                float maxTextWidth = 0.0F;
                for (String line : descLines) {
                    float lw = ModernFont.getWidth(line, 11.0F * scale, ModernFont.Type.INTER_MEDIUM);
                    if (lw > maxTextWidth) maxTextWidth = lw;
                }
                String title = unrollingInfoModule.getName();
                float titleW = ModernFont.getWidth(title, 12.5F * scale, ModernFont.Type.SF_BOLD);
                float badgeW = ModernFont.getBadgeWidth(t("ОПИСАНИЕ", "OVERVIEW"), 0xFF312E81, 0xFFE0E7FF, Math.round(13.0F * scale));
                float headerReqW = 44.0F * scale + titleW + 16.0F * scale + badgeW + 36.0F * scale;
                float contentReqW = Math.max(headerReqW, maxTextWidth + 36.0F * scale);
                float bannerW = Math.max(280.0F * scale, Math.min(b.w - 32.0F * scale, contentReqW));
                float bannerX = b.x + (b.w - bannerW) * 0.5F;
                
                float targetH = 46.0F * scale + descLines.size() * (14.5F * scale) + 6.0F * scale;
                
                // Unrolling dynamic scroll effect (expanding height with elastic spring)
                float curH = targetH * easeOutBack(unrollAnim);
                float bannerY = b.y - curH - 8.0F * scale;
                if (bannerY < 6.0F * scale) {
                    bannerY = 6.0F * scale;
                }

                // Strict dark obsidian card with subtle shadow and clean 1px border
                Render2D.drawShadow(context, bannerX, bannerY, bannerW, curH, 14.0F * scale, 10.0F * scale, argb(180, 0, 0, 0, bannerAlpha));
                Render2D.drawRound(context, bannerX, bannerY, bannerW, curH, 6.0F * scale, argb(253, 15, 17, 24, bannerAlpha));
                Render2D.drawRoundOutline(context, bannerX, bannerY, bannerW, curH, 6.0F * scale, 1.0F, argb(255, 32, 37, 50, bannerAlpha));

                Render2D.pushScissor(bannerX, bannerY, bannerW, curH);
                context.enableScissor((int) bannerX, (int) bannerY, (int) (bannerX + bannerW), (int) (bannerY + curH));

                // Header
                float hdrY = bannerY + 8.0F * scale;
                float iconSize = 14.0F * scale;
                drawTexture(context, ICON_INFO, Math.round(bannerX + 12.0F * scale), Math.round(hdrY + 1.0F * scale), Math.round(iconSize), Math.round(iconSize), argb(220, 196, 181, 253, bannerAlpha));

                ModernFont.draw(context, title, bannerX + 32.0F * scale, hdrY + 1.5F * scale, 12.5F * scale, argb(255, 255, 255, 255, bannerAlpha), ModernFont.Type.SF_BOLD);
                ModernFont.drawBadge(context, t("ОПИСАНИЕ", "OVERVIEW"), bannerX + 46.0F * scale + titleW, hdrY + 1.5F * scale, 0xFF312E81, 0xFFE0E7FF, Math.round(13.0F * scale), bannerAlpha);

                // Close (X) button
                float closeBtnW = 16.0F * scale, closeBtnH = 16.0F * scale;
                float closeBtnX = bannerX + bannerW - closeBtnW - 8.0F * scale;
                float closeBtnY = hdrY;
                boolean closeHover = inside(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnW, closeBtnH);
                Render2D.drawRound(context, closeBtnX, closeBtnY, closeBtnW, closeBtnH, 4.0F * scale, closeHover ? argb(60, 239, 68, 68, bannerAlpha) : argb(18, 255, 255, 255, bannerAlpha));
                float cx = closeBtnX + closeBtnW * 0.5F, cy = closeBtnY + closeBtnH * 0.5F, cr = 2.5F * scale;
                int crossCol = closeHover ? argb(255, 252, 165, 165, bannerAlpha) : argb(160, 148, 163, 184, bannerAlpha);
                Render2D.drawLine(context, cx - cr, cy - cr, cx + cr, cy + cr, 1.0F * scale, crossCol);
                Render2D.drawLine(context, cx + cr, cy - cr, cx - cr, cy + cr, 1.0F * scale, crossCol);

                // Separator line
                Render2D.drawLine(context, bannerX + 10.0F * scale, hdrY + 18.0F * scale, bannerX + bannerW - 10.0F * scale, hdrY + 18.0F * scale, 1.0F, argb(255, 26, 29, 39, bannerAlpha));

                // Description lines
                float textY = hdrY + 24.0F * scale;
                for (String line : descLines) {
                    if (textY + 10.0F * scale <= bannerY + curH) {
                        ModernFont.draw(context, line, bannerX + 12.0F * scale, textY, 11.0F * scale, argb(230, 226, 232, 240, bannerAlpha), ModernFont.Type.INTER_MEDIUM);
                        textY += 14.5F * scale;
                    }
                }

                // 2px Progress bar timeline at bottom
                float barH = 2.0F * scale;
                float barW = (bannerW - 4.0F * scale) * progress;
                float barX = bannerX + 2.0F * scale;
                float barY = bannerY + targetH - barH - 2.0F * scale;
                if (barW > 1.0F && barY <= bannerY + curH) {
                    Render2D.drawRound(context, barX, barY, barW, barH, 1.0F * scale, argb(255, 139, 92, 246, bannerAlpha));
                }

                context.disableScissor();
                Render2D.popScissor();
            }
        }

    }

    private static void drawSeamlessHueBar(DrawContext context, float x, float y, float w, float h, float r, float alpha) {
        Render2D.drawRound(context, x, y, w, h, r, argb(255, 20, 24, 36, alpha));
        Render2D.pushScissor(x, y, w, h);
        context.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        float step = 1.0F;
        for (float i = 0; i < w; i += step) {
            float hue = i / w;
            int rgb = Color.HSBtoRGB(hue, 1.0F, 1.0F);
            Render2D.drawRound(context, x + i, y, step + 0.6F, h, 0.0F, color(rgb, alpha));
        }
        context.disableScissor();
        Render2D.popScissor();
        Render2D.drawRoundOutline(context, x, y, w, h, r, 1.0F, argb(60, 255, 255, 255, alpha));
    }

    private void drawToggle(DrawContext context, String id, float x, float y, float w, float h, boolean active, float alpha, float dt, float scale) {
        float cur = toggleAnims.getOrDefault(id, active ? 1.0F : 0.0F);
        cur = cur + ((active ? 1.0F : 0.0F) - cur) * Math.min(1.0F, dt * 16.0F);
        toggleAnims.put(id, cur);

        if (cur > 0.05F) {
            Render2D.drawShadow(context, x, y, w, h, 2.5F * scale, 1.5F * scale, argb(Math.round(35 * cur), 124, 58, 237, alpha));
        }

        // Controlled purple accent (#7C3AED to #6D28D9) / Refined matte graphite when off
        int offCol = argb(255, 22, 25, 34, alpha);
        int onColL = argb(255, 124, 58, 237, alpha);
        int onColR = argb(255, 109, 40, 217, alpha);
        
        int curColL = mixColor(offCol, onColL, cur);
        int curColR = mixColor(offCol, onColR, cur);

        Render2D.drawGradientRoundLR(context, x, y, w, h, h * 0.5F, curColL, curColR);
        Render2D.drawRoundOutline(context, x, y, w, h, h * 0.5F, 1.0F, cur > 0.05F ? argb(Math.round(100 * cur), 139, 92, 246, alpha) : argb(255, 36, 41, 56, alpha));
        Render2D.drawLine(context, x + 3.0F * scale, y + 1.0F, x + w - 3.0F * scale, y + 1.0F, 1.0F, argb(20, 255, 255, 255, alpha));

        // Smooth centered knob with subtle micro-depth and responsive state transition
        float knobR = (h - 4.0F * scale) * 0.5F;
        float knobX = x + 2.0F * scale + (w - h) * cur + knobR;
        float knobY = y + 2.0F * scale + knobR;
        int knobColOff = argb(255, 148, 163, 184, alpha);
        int knobColOn = argb(255, 255, 255, 255, alpha);
        int curKnobCol = mixColor(knobColOff, knobColOn, cur);
        Render2D.drawCircle(context, knobX, knobY + 0.5F * scale, knobR, argb(60, 0, 0, 0, alpha));
        Render2D.drawCircle(context, knobX, knobY, knobR, curKnobCol);
    }

    private static int mixColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private List<Module> getFilteredModules() {
        List<Module> list;
        boolean hasSearch = !search.trim().isEmpty();
        if (hasSearch) {
            list = new ArrayList<>();
            for (Category cat : Category.values()) {
                for (Module m : getModulesForCategory(cat)) {
                    if (!list.contains(m)) list.add(m);
                }
            }
        } else {
            list = new ArrayList<>(getModulesForCategory(selectedCategory));
        }

        if (sortMode == SortMode.ALPHABETICAL) {
            list.sort(Comparator.comparing(Module::getName));
        } else if (sortMode == SortMode.ACTIVE_FIRST) {
            list.sort((m1, m2) -> Boolean.compare(m2.isEnabled(), m1.isEnabled()));
        }

        if (hasSearch) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            List<Module> filtered = new ArrayList<>();
            for (Module m : list) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                String desc = m.getDescription() != null ? m.getDescription().toLowerCase(Locale.ROOT) : "";
                String ruDesc = getModuleDescription(m, false).toLowerCase(Locale.ROOT);
                String enDesc = getModuleDescription(m, true).toLowerCase(Locale.ROOT);
                if (name.contains(q) || desc.contains(q) || ruDesc.contains(q) || enDesc.contains(q)) {
                    filtered.add(m);
                }
            }
            list = filtered;
        }

        // Partition: Pinned modules stay at the very top
        List<Module> pinned = new ArrayList<>();
        List<Module> unpinned = new ArrayList<>();
        for (Module m : list) {
            if (PINNED_MODULES.contains(m.getName())) {
                pinned.add(m);
            } else {
                unpinned.add(m);
            }
        }
        List<Module> res = new ArrayList<>(pinned.size() + unpinned.size());
        res.addAll(pinned);
        res.addAll(unpinned);
        return res;
    }

    private List<Module> getModulesForCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module mod : moduleManager.getModules()) {
            String name = mod.getName();
            switch (cat) {
                case COMBAT -> {
                    if (name.equalsIgnoreCase("Hit Color") || name.equalsIgnoreCase("HitColor")
                            || name.equalsIgnoreCase("HitboxCustomizer") || name.equalsIgnoreCase("Hitbox Customizer")
                            || name.equalsIgnoreCase("TargetEsp") || name.equalsIgnoreCase("TargetHud")
                            || name.equalsIgnoreCase("TrapTracker") || name.equalsIgnoreCase("FakePlayer")
                            || name.equalsIgnoreCase("ItemRadius")
                            || name.equalsIgnoreCase("AimBot") || name.equalsIgnoreCase("TriggerBot")
                            || name.equalsIgnoreCase("AutoMace")) {
                        result.add(mod);
                    }
                }
                case MOVEMENT -> {
                    if (name.equalsIgnoreCase("AutoSprint") || name.equalsIgnoreCase("ElytraSwap")
                            || name.equalsIgnoreCase("ItemResorter") || name.equalsIgnoreCase("ItemScroller")
                            || name.equalsIgnoreCase("NoJumpDelay")) {
                        result.add(mod);
                    }
                }
                case PLAYER -> {
                    if (name.equalsIgnoreCase("ItemSwap") || name.equalsIgnoreCase("AutoSwap")
                            || name.equalsIgnoreCase("FreeLook") || name.equalsIgnoreCase("TapeMouse")
                            || name.equalsIgnoreCase("SafeNametag")) {
                        result.add(mod);
                    }
                }
                case VISUALS -> {
                    if (name.equalsIgnoreCase("FullBright") || name.equalsIgnoreCase("ChinaHat")
                            || name.equalsIgnoreCase("Wings") || name.equalsIgnoreCase("Crosshair")
                            || name.equalsIgnoreCase("Zoom") || name.equalsIgnoreCase("BlockOverlay")
                            || name.equalsIgnoreCase("WorldCustomizer") || name.equalsIgnoreCase("World Customizer")
                            || name.equalsIgnoreCase("AspectRatio") || name.equalsIgnoreCase("Particles")
                            || name.equalsIgnoreCase("JumpCircles") || name.equalsIgnoreCase("Trails")
                            || name.equalsIgnoreCase("Removals") || name.equalsIgnoreCase("Animations")
                            || name.equalsIgnoreCase("TabCustomizer") || name.equalsIgnoreCase("Watermark")) {
                        result.add(mod);
                    }
                }
                case AUTOBUY -> {
                    if (name.equalsIgnoreCase("AutoBuy") || name.equalsIgnoreCase("AHHelper")
                            || name.equalsIgnoreCase("AutoResell") || name.equalsIgnoreCase("AutoResellAFK")
                            || name.equalsIgnoreCase("ItemCrafter")
                            || name.equalsIgnoreCase("CaptchaSolver")
                            || name.equalsIgnoreCase("AnarchySwitcher")) {
                        result.add(mod);
                    }
                }
                case SETTINGS -> {
                    if (name.equalsIgnoreCase("Menu") || name.equalsIgnoreCase("TG")
                            || name.equalsIgnoreCase("Telegram") || name.equalsIgnoreCase("DiscordRPC")
                            || name.equalsIgnoreCase("NameProtect") || name.equalsIgnoreCase("NameBind")
                            || name.equalsIgnoreCase("Macros")) {
                        result.add(mod);
                    }
                }
            }
        }
        return result;
    }

    private Category getCategoryForModule(Module mod) {
        if (mod == null) return Category.COMBAT;
        for (Category cat : Category.values()) {
            if (getModulesForCategory(cat).contains(mod)) return cat;
        }
        return Category.COMBAT;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GuiBounds b = new GuiBounds(MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight(), getGuiScale());
        float scale = b.scale;

        if (listeningModuleBind != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                listeningModuleBind = null;
                return true;
            } else if (button > 2) {
                MODULE_BINDS.put(listeningModuleBind.getName(), 1000 + button);
                listeningModuleBind = null;
                FluxVisualsClient.requestConfigSave();
                return true;
            }
        }
        if (listeningSettingBind != null) {
            listeningSettingBind.set(1000 + button);
            listeningSettingBind = null;
            return true;
        }
        if (listeningMacroIndex >= 0) {
            moduleManager.getMacros().updateBind(listeningMacroIndex, 1000 + button);
            listeningMacroIndex = -1;
            return true;
        }
        if (listeningNameBindIndex >= 0) {
            moduleManager.getNameBind().updateBind(listeningNameBindIndex, 1000 + button);
            listeningNameBindIndex = -1;
            return true;
        }

        // Unrolling Floating Info Banner dismiss on click
        if (unrollingInfoModule != null && !unrollClosing) {
            String desc = getModuleDescription(unrollingInfoModule, isEn());
            List<String> descLines = wrapText(desc, 44);
            float maxTextWidth = 0.0F;
            for (String line : descLines) {
                float lw = ModernFont.getWidth(line, 11.0F * scale, ModernFont.Type.INTER_MEDIUM);
                if (lw > maxTextWidth) maxTextWidth = lw;
            }
            String title = unrollingInfoModule.getName();
            float titleW = ModernFont.getWidth(title, 12.5F * scale, ModernFont.Type.SF_BOLD);
            float badgeW = ModernFont.getBadgeWidth(t("ОПИСАНИЕ", "OVERVIEW"), 0xFF312E81, 0xFFE0E7FF, Math.round(13.0F * scale));
            float headerReqW = 44.0F * scale + titleW + 16.0F * scale + badgeW + 36.0F * scale;
            float contentReqW = Math.max(headerReqW, maxTextWidth + 36.0F * scale);
            float bannerW = Math.max(280.0F * scale, Math.min(b.w - 32.0F * scale, contentReqW));
            float bannerX = b.x + (b.w - bannerW) * 0.5F;
            float targetH = 46.0F * scale + descLines.size() * (14.5F * scale) + 6.0F * scale;
            float bannerY = Math.max(6.0F * scale, b.y - targetH - 8.0F * scale);

            if (inside(mouseX, mouseY, bannerX, bannerY, bannerW, targetH)) {
                unrollClosing = true;
                return true;
            }
        }

        // 3-Dots Context Menu Item Clicks
        if (activeContextMenuModule != null && !contextMenuClosing) {
            float popW = 168.0F * scale;
            float itemH = 25.0F * scale;
            float popH = 5 * itemH + 8.0F * scale;
            float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float popX = Math.min(screenW - popW - 10.0F * scale, contextMenuX);
            float popY = Math.min(screenH - popH - 10.0F * scale, contextMenuY);

            if (inside(mouseX, mouseY, popX, popY, popW, popH)) {
                float curItemY = popY + 4.0F * scale;

                // Click 0: Pin / Unpin
                if (inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH)) {
                    if (PINNED_MODULES.contains(activeContextMenuModule.getName())) {
                        PINNED_MODULES.remove(activeContextMenuModule.getName());
                        contextMenuFeedback = "unpinned";
                    } else {
                        PINNED_MODULES.add(activeContextMenuModule.getName());
                        contextMenuFeedback = "pinned";
                    }
                    contextMenuFeedbackTime = System.currentTimeMillis();
                    return true;
                }
                curItemY += itemH;

                // Click 1: Unroll description banner above GUI
                if (inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH)) {
                    unrollingInfoModule = activeContextMenuModule;
                    unrollAnim = 0.0F;
                    unrollClosing = false;
                    unrollStartTime = System.currentTimeMillis();
                    contextMenuClosing = true;
                    return true;
                }
                curItemY += itemH;

                // Click 2: Copy settings
                if (inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH)) {
                    copyModuleSettings(activeContextMenuModule);
                    contextMenuFeedback = "copied";
                    contextMenuFeedbackTime = System.currentTimeMillis();
                    return true;
                }
                curItemY += itemH;

                // Click 3: Paste settings
                if (inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH)) {
                    pasteModuleSettings(activeContextMenuModule);
                    contextMenuFeedback = "pasted";
                    contextMenuFeedbackTime = System.currentTimeMillis();
                    return true;
                }
                curItemY += itemH;

                // Click 4: Reset to default
                if (inside(mouseX, mouseY, popX + 4.0F * scale, curItemY, popW - 8.0F * scale, itemH)) {
                    resetModuleSettings(activeContextMenuModule);
                    contextMenuClosing = true;
                    return true;
                }
                return true;
            } else {
                contextMenuClosing = true;
            }
        }

        // Sort Dropdown Click
        if (sortDropdownOpen) {
            float popW = 144.0F * scale;
            float popH = SortMode.values().length * (23.0F * scale) + 8.0F * scale;
            float popX = b.box1X + b.boxW - popW - 10.0F * scale;
            float popY = b.boxY + 35.0F * scale;
            if (inside(mouseX, mouseY, popX, popY, popW, popH)) {
                float itemY = popY + 4.0F * scale;
                for (SortMode mode : SortMode.values()) {
                    if (inside(mouseX, mouseY, popX + 4.0F * scale, itemY, popW - 8.0F * scale, 21.0F * scale)) {
                        sortMode = mode;
                        sortDropdownOpen = false;
                        return true;
                    }
                    itemY += 23.0F * scale;
                }
                return true;
            } else {
                sortDropdownOpen = false;
            }
        }

        if (activeColorPicker != null && !colorPickerClosing) {
            float pickW = 194.0F * scale;
            float pickH = 192.0F * scale;
            float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float px = (pickerX > 0) ? (pickerX - pickW) : (screenW - pickW) * 0.5F;
            float py = (pickerY > 0) ? (pickerY + 24.0F * scale) : (screenH - pickH) * 0.5F;
            px = Math.max(10.0F * scale, Math.min(screenW - pickW - 10.0F * scale, px));
            py = Math.max(10.0F * scale, Math.min(screenH - pickH - 10.0F * scale, py));

            if (inside(mouseX, mouseY, px, py, pickW, pickH)) {
                float hdrY = py + 8.0F * scale;
                float closeSize = 16.0F * scale;
                float closeX = px + pickW - closeSize - 8.0F * scale;
                float closeY = hdrY + 1.0F * scale;
                if (inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize)) {
                    colorPickerClosing = true;
                    hexFocused = false;
                    return true;
                }

                float svX = px + 10.0F * scale;
                float svY = hdrY + 24.0F * scale;
                float svW = pickW - 20.0F * scale;
                float svH = 64.0F * scale;
                float hueY = svY + svH + 8.0F * scale, hueH = 7.0F * scale;
                float opacityLabelY = hueY + hueH + 10.0F * scale;
                float alphaBarY = opacityLabelY + 16.0F * scale, alphaBarH = 8.0F * scale;
                float hexRowY = alphaBarY + alphaBarH + 12.0F * scale, hexRowH = 22.0F * scale;
                float prevW = 22.0F * scale;
                float iconBtnSize = 22.0F * scale;
                float copyBtnX = svX + svW - iconBtnSize;
                float pasteBtnX = copyBtnX - iconBtnSize - 4.0F * scale;
                float hexBoxX = svX + prevW + 5.0F * scale;
                float hexBoxW = pasteBtnX - 5.0F * scale - hexBoxX;

                float svKnobInset = 4.0F * scale;
                if (inside(mouseX, mouseY, svX, svY, svW, svH)) {
                    draggingSV = true;
                    pickerSat = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + svKnobInset)) / (svW - svKnobInset * 2.0F))));
                    pickerBri = Math.max(0.0F, Math.min(1.0F, 1.0F - (float) ((mouseY - (svY + svKnobInset)) / (svH - svKnobInset * 2.0F))));
                    updatePickerColor();
                    hexFocused = false;
                    return true;
                }
                float hueKnobInset = 4.0F * scale;
                if (inside(mouseX, mouseY, svX, hueY - 3.0F * scale, svW, hueH + 6.0F * scale)) {
                    draggingHue = true;
                    pickerHue = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + hueKnobInset)) / (svW - hueKnobInset * 2.0F))));
                    updatePickerColor();
                    hexFocused = false;
                    return true;
                }
                float alphaKnobInset = 4.0F * scale;
                if (inside(mouseX, mouseY, svX, alphaBarY - 3.0F * scale, svW, alphaBarH + 6.0F * scale)) {
                    draggingAlpha = true;
                    pickerAlpha = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + alphaKnobInset)) / (svW - alphaKnobInset * 2.0F))));
                    updatePickerColor();
                    hexFocused = false;
                    return true;
                }
                if (inside(mouseX, mouseY, hexBoxX, hexRowY, hexBoxW, hexRowH)) {
                    hexFocused = true;
                    int curCol = activeColorPicker.get();
                    hexBuffer = (((curCol >> 24) & 0xFF) == 255) ? String.format("%06X", curCol & 0xFFFFFF) : String.format("%08X", curCol);
                    return true;
                }
                if (inside(mouseX, mouseY, pasteBtnX, hexRowY, iconBtnSize, iconBtnSize)) {
                    hexFocused = false;
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null && !clip.trim().isEmpty()) {
                        String clean = clip.trim().replace("#", "");
                        try {
                            long parsed = Long.parseLong(clean, 16);
                            int col;
                            if (clean.length() <= 6) {
                                col = ((int) parsed) | 0xFF000000;
                            } else {
                                col = (int) parsed;
                            }
                            activeColorPicker.set(col);
                            float[] hsb = Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                            pickerHue = hsb[0];
                            pickerSat = hsb[1];
                            pickerBri = hsb[2];
                            pickerAlpha = ((col >> 24) & 0xFF) / 255.0F;
                            hexBuffer = (((col >> 24) & 0xFF) == 255) ? String.format("%06X", col & 0xFFFFFF) : String.format("%08X", col);
                            colorFeedback = "paste_success";
                            colorFeedbackTime = System.currentTimeMillis();
                        } catch (Exception ex) {
                            colorFeedback = "paste_error";
                            colorFeedbackTime = System.currentTimeMillis();
                        }
                    } else {
                        colorFeedback = "paste_error";
                        colorFeedbackTime = System.currentTimeMillis();
                    }
                    return true;
                }
                if (inside(mouseX, mouseY, copyBtnX, hexRowY, iconBtnSize, iconBtnSize)) {
                    hexFocused = false;
                    int col = activeColorPicker.get();
                    String hexStr = (((col >> 24) & 0xFF) == 255) ? String.format("#%06X", col & 0xFFFFFF) : String.format("#%08X", col);
                    MinecraftClient.getInstance().keyboard.setClipboard(hexStr);
                    colorFeedback = "copy_success";
                    colorFeedbackTime = System.currentTimeMillis();
                    return true;
                }
                hexFocused = false;
                return true;
            } else {
                colorPickerClosing = true;
                hexFocused = false;
                pipetteActive = false;
            }
        }

        if (inside(mouseX, mouseY, b.x + 12.0F * scale, b.y + 48.0F * scale, b.sidebarW - 24.0F * scale, 26.0F * scale)) {
            float searchX = b.x + 12.0F * scale;
            float searchY = b.y + 48.0F * scale;
            float searchW = b.sidebarW - 24.0F * scale;
            float searchH = 26.0F * scale;
            float clrSize = 14.0F * scale;
            float clrX = searchX + searchW - clrSize - 6.0F * scale;
            float clrY = searchY + (searchH - clrSize) * 0.5F;
            if (!search.isEmpty() && inside(mouseX, mouseY, clrX - 3.0F * scale, clrY - 3.0F * scale, clrSize + 6.0F * scale, clrSize + 6.0F * scale)) {
                search = "";
                searchFocused = false;
                return true;
            }
            searchFocused = true;
            activeStringSetting = null;
            activeSliderInput = null;
            activeMacroTextIndex = -1;
            activeNameBindTextIndex = -1;
            return true;
        } else {
            searchFocused = false;
        }

        // Categories click
        float startCatY = b.y + 82.0F * scale;
        float catH = 32.0F * scale;
        float catSpacing = 4.0F * scale;
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            float catY = getCategoryY(cats[i], startCatY, catH, catSpacing, scale);
            if (inside(mouseX, mouseY, b.x + 12.0F * scale, catY, b.sidebarW - 24.0F * scale, catH)) {
                if (selectedCategory != cats[i]) {
                    selectedCategory = cats[i];
                    savedCategory = selectedCategory;
                    scrollLeft = 0.0F;
                    scrollRight = 0.0F;
                    moduleIndicatorY = -1.0F;
                    moduleIndicatorAlpha = 0.0F;
                    openDropdown = null;
                    openMultiDropdown = null;
                    sortDropdownOpen = false;
                    activeContextMenuModule = null;
                    unrollingInfoModule = null;
                    activeColorPicker = null;
                    activeStringSetting = null;
                    activeSliderInput = null;
                    activeMacroTextIndex = -1;
                    activeNameBindTextIndex = -1;
                    List<Module> mods = getModulesForCategory(selectedCategory);
                    if (!mods.isEmpty()) {
                        selectedModule = mods.get(0);
                        savedModuleName = selectedModule.getName();
                    }
                }
                return true;
            }
        }

        // Sort button click
        float sortBtnW = 24.0F * scale, sortBtnH = 20.0F * scale;
        float sortBtnX = b.box1X + b.boxW - sortBtnW - 14.0F * scale;
        float sortBtnY = b.boxY + 12.0F * scale;
        if (inside(mouseX, mouseY, sortBtnX, sortBtnY, sortBtnW, sortBtnH)) {
            sortDropdownOpen = !sortDropdownOpen;
            return true;
        }

        // Left box modules click
        List<Module> modules = getFilteredModules();
        float itemH = 31.0F * scale;
        for (Module mod : modules) {
            float curY = moduleVisualY.getOrDefault(mod, b.boxY + 38.0F * scale);
            if (inside(mouseX, mouseY, b.box1X + 8.0F * scale, curY, b.boxW - 16.0F * scale, itemH)) {
                float toggleW = 26.0F * scale, toggleH = 14.0F * scale;
                float toggleX = b.box1X + b.boxW - toggleW - 14.0F * scale;
                float toggleY = curY + (itemH - toggleH) * 0.5F;

                float dotsW = 18.0F * scale, dotsH = 18.0F * scale;
                float dotsX = toggleX - dotsW - 8.0F * scale;
                float dotsY = curY + (itemH - dotsH) * 0.5F;

                if (inside(mouseX, mouseY, dotsX - 2.0F * scale, dotsY - 2.0F * scale, dotsW + 4.0F * scale, dotsH + 4.0F * scale)) {
                    if (activeContextMenuModule == mod) {
                        contextMenuClosing = true;
                    } else {
                        activeContextMenuModule = mod;
                        contextMenuX = dotsX - 168.0F * scale;
                        contextMenuY = dotsY;
                        contextMenuAnim = 0.0F;
                        contextMenuClosing = false;
                        contextMenuFeedback = null;
                    }
                    return true;
                }
                // Middle click on module (СКМ): toggle bind listening
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    if (listeningModuleBind == mod) {
                        listeningModuleBind = null;
                    } else {
                        listeningModuleBind = mod;
                        activeContextMenuModule = null;
                        openDropdown = null;
                        openMultiDropdown = null;
                    }
                    return true;
                }

                // If right-clicked on module: open or toggle settings panel
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (settingsOpen && selectedModule == mod) {
                        settingsOpen = false;
                    } else {
                        selectedModule = mod;
                        savedModuleName = mod.getName();
                        settingsOpen = true;
                    }
                    if (!search.trim().isEmpty()) {
                        Category targetCat = getCategoryForModule(mod);
                        if (selectedCategory != targetCat) {
                            selectedCategory = targetCat;
                            savedCategory = selectedCategory;
                            moduleIndicatorY = -1.0F;
                            moduleIndicatorAlpha = 0.0F;
                        }
                    }
                    openDropdown = null;
                    openMultiDropdown = null;
                    sortDropdownOpen = false;
                    activeColorPicker = null;
                    activeStringSetting = null;
                    activeSliderInput = null;
                    activeMacroTextIndex = -1;
                    activeNameBindTextIndex = -1;
                    return true;
                }

                // Left click on module: toggle module ON/OFF ONLY (DO NOT OPEN OR SWITCH SETTINGS!)
                sessionToggleTarget(mod).toggle();
                return true;
            }
        }

        if (listeningModuleBind != null && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            MODULE_BINDS.put(listeningModuleBind.getName(), 1000 + button);
            listeningModuleBind = null;
            FluxVisualsClient.requestConfigSave();
            return true;
        }

        // Settings collapse button click
        float closeBtnW = 20.0F * scale, closeBtnH = 20.0F * scale;
        float closeBtnX = b.box2X + b.boxW - closeBtnW - 12.0F * scale;
        float closeBtnY = b.boxY + 11.0F * scale;
        if (settingsOpen && settingsAnim > 0.3F && inside(mouseX, mouseY, closeBtnX - 2.0F * scale, closeBtnY - 2.0F * scale, closeBtnW + 4.0F * scale, closeBtnH + 4.0F * scale)) {
            settingsOpen = false;
            openDropdown = null;
            openMultiDropdown = null;
            activeColorPicker = null;
            activeStringSetting = null;
            activeSliderInput = null;
            return true;
        }

        // Right box settings click
        if (selectedModule != null && settingsOpen && settingsAnim > 0.3F) {
            float scissorTopR = b.boxY + 38.0F * scale;
            float setY = scissorTopR + 4.0F * scale - scrollRight;
            float rowW = b.rowW;

            if ("Macros".equalsIgnoreCase(selectedModule.getName())) {
                Macros macros = moduleManager.getMacros();
                setY += 24.0F * scale;
                if (inside(mouseX, mouseY, b.box2X + 16.0F * scale + rowW - 28.0F * scale, setY + 3.0F * scale, 28.0F * scale, 16.0F * scale)) {
                    macros.toggle();
                    return true;
                }
                setY += 34.0F * scale;
                setY += 24.0F * scale;

                for (int i = 0; i < macros.getEntries().size(); i++) {
                    float boxH = 22.0F * scale;
                    float delBtnW = 20.0F * scale;
                    float bindBtnW = 54.0F * scale;
                    float textW = rowW - delBtnW - bindBtnW - 12.0F * scale;
                    float bindX = b.box2X + 16.0F * scale + textW + 6.0F * scale;
                    float delX = bindX + bindBtnW + 6.0F * scale;

                    if (inside(mouseX, mouseY, b.box2X + 16.0F * scale, setY, textW, boxH)) {
                        activeMacroTextIndex = i;
                        activeStringSetting = null;
                        activeSliderInput = null;
                        activeNameBindTextIndex = -1;
                        searchFocused = false;
                        return true;
                    }
                    if (inside(mouseX, mouseY, bindX, setY, bindBtnW, boxH)) {
                        listeningMacroIndex = (listeningMacroIndex == i) ? -1 : i;
                        return true;
                    }
                    if (inside(mouseX, mouseY, delX, setY, delBtnW, boxH)) {
                        if (activeMacroTextIndex == i) activeMacroTextIndex = -1;
                        if (listeningMacroIndex == i) listeningMacroIndex = -1;
                        macros.removeEntry(i);
                        return true;
                    }
                    setY += 34.0F * scale;
                }
                if (inside(mouseX, mouseY, b.box2X + 16.0F * scale, setY + 4.0F * scale, rowW, 24.0F * scale)) {
                    macros.addEntry("/help");
                    activeMacroTextIndex = macros.getEntries().size() - 1;
                    return true;
                }
                return false;
            }

            if ("NameBind".equalsIgnoreCase(selectedModule.getName()) || "NameBinder".equalsIgnoreCase(selectedModule.getName())) {
                NameBind nb = moduleManager.getNameBind();
                setY += 24.0F * scale;
                if (inside(mouseX, mouseY, b.box2X + 16.0F * scale + rowW - 28.0F * scale, setY + 3.0F * scale, 28.0F * scale, 16.0F * scale)) {
                    nb.toggle();
                    return true;
                }
                setY += 34.0F * scale;
                setY += 24.0F * scale;

                for (int i = 0; i < nb.getEntries().size(); i++) {
                    float boxH = 22.0F * scale;
                    float delBtnW = 20.0F * scale;
                    float bindBtnW = 54.0F * scale;
                    float textW = rowW - delBtnW - bindBtnW - 12.0F * scale;
                    float bindX = b.box2X + 16.0F * scale + textW + 6.0F * scale;
                    float delX = bindX + bindBtnW + 6.0F * scale;

                    if (inside(mouseX, mouseY, b.box2X + 16.0F * scale, setY, textW, boxH)) {
                        activeNameBindTextIndex = i;
                        activeStringSetting = null;
                        activeSliderInput = null;
                        activeMacroTextIndex = -1;
                        searchFocused = false;
                        return true;
                    }
                    if (inside(mouseX, mouseY, bindX, setY, bindBtnW, boxH)) {
                        listeningNameBindIndex = (listeningNameBindIndex == i) ? -1 : i;
                        return true;
                    }
                    if (inside(mouseX, mouseY, delX, setY, delBtnW, boxH)) {
                        if (activeNameBindTextIndex == i) activeNameBindTextIndex = -1;
                        if (listeningNameBindIndex == i) listeningNameBindIndex = -1;
                        nb.removeEntry(i);
                        return true;
                    }
                    setY += 34.0F * scale;
                }
                if (inside(mouseX, mouseY, b.box2X + 16.0F * scale, setY + 4.0F * scale, rowW, 24.0F * scale)) {
                    nb.addEntry("Player");
                    activeNameBindTextIndex = nb.getEntries().size() - 1;
                    return true;
                }
                return false;
            }

            setY += 24.0F * scale;

            // Module Keybind row
            int moduleKey = selectedModule == moduleManager.getMenu() ? moduleManager.getMenu().getKeyBind() : MODULE_BINDS.getOrDefault(selectedModule.getName(), GLFW.GLFW_KEY_UNKNOWN);
            String modKeyText = listeningModuleBind == selectedModule ? "..." : KeybindSetting.getKeyName(moduleKey, isEn());
            float modBtnW = Math.max(54.0F * scale, ModernFont.getWidth(modKeyText, 10.0F * scale, ModernFont.Type.SF_BOLD) + 16.0F * scale);
            if (inside(mouseX, mouseY, b.box2X + 16.0F * scale + rowW - modBtnW, setY + 2.5F * scale, modBtnW, 20.0F * scale)) {
                listeningModuleBind = (listeningModuleBind == selectedModule) ? null : selectedModule;
                listeningSettingBind = null;
                return true;
            }
            setY += 34.0F * scale;

            List<Setting<?>> settings = moduleSettings.getOrDefault(selectedModule, Collections.emptyList());
            if (!settings.isEmpty()) {
                setY += 24.0F * scale;
            }

            for (Setting<?> s : settings) {
                if (!s.isVisible()) continue;
                if (s instanceof BooleanSetting bool) {
                    if (inside(mouseX, mouseY, b.box2X + 16.0F * scale + rowW - 28.0F * scale, setY + 4.0F * scale, 30.0F * scale, 18.0F * scale)) {
                        bool.set(!bool.get());
                        return true;
                    }
                    setY += 34.0F * scale;
                } else if (s instanceof KeybindSetting keybind) {
                    String setKeyText = listeningSettingBind == keybind ? "..." : KeybindSetting.getKeyName(keybind.get(), isEn());
                    float setBtnW = Math.max(54.0F * scale, ModernFont.getWidth(setKeyText, 10.0F * scale, ModernFont.Type.SF_BOLD) + 16.0F * scale);
                    if (inside(mouseX, mouseY, b.box2X + 16.0F * scale + rowW - setBtnW, setY + 2.5F * scale, setBtnW, 20.0F * scale)) {
                        listeningSettingBind = (listeningSettingBind == keybind) ? null : keybind;
                        listeningModuleBind = null;
                        return true;
                    }
                    setY += 34.0F * scale;
                } else if (s instanceof SliderSetting slider) {
                    float valBoxW = 36.0F * scale;
                    float valBoxH = 18.0F * scale;
                    float valBoxX = b.box2X + 16.0F * scale + rowW - valBoxW;
                    float trackW = 56.0F * scale;
                    float trackX = valBoxX - trackW - 8.0F * scale;

                    if (inside(mouseX, mouseY, valBoxX, setY + 3.5F * scale, valBoxW, valBoxH)) {
                        activeSliderInput = slider;
                        sliderInputBuffer = slider.getValueString().replaceAll("[^0-9.,-]", "");
                        activeStringSetting = null;
                        activeMacroTextIndex = -1;
                        activeNameBindTextIndex = -1;
                        searchFocused = false;
                        return true;
                    }
                    if (inside(mouseX, mouseY, trackX - 6.0F * scale, setY + 2.0F * scale, trackW + 12.0F * scale, 24.0F * scale)) {
                        activeSlider = slider;
                        slider.setNormalized(Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - trackX) / trackW))));
                        return true;
                    }
                    setY += 34.0F * scale;
                } else if (s instanceof ModeSetting mode) {
                    float curAnim = dropdownAnim.getOrDefault(mode, 0.0F);
                    float boxW = 136.0F * scale;
                    float boxX = b.box2X + 16.0F * scale + rowW - boxW;

                    if (openDropdown == mode && curAnim > 0.5F) {
                        float dropY = setY + 1.5F * scale + 22.0F * scale + 4.0F * scale;
                        float optY = dropY;
                        for (String m : mode.getModes()) {
                            if (inside(mouseX, mouseY, boxX, optY, boxW, 22.0F * scale)) {
                                mode.set(m);
                                openDropdown = null;
                                return true;
                            }
                            optY += 22.0F * scale;
                        }
                    }

                    if (inside(mouseX, mouseY, boxX, setY + 1.5F * scale, boxW, 22.0F * scale)) {
                        openDropdown = (openDropdown == mode) ? null : mode;
                        openMultiDropdown = null;
                        return true;
                    }
                    setY += 34.0F * scale + mode.getModes().size() * (22.0F * scale) * curAnim;
                } else if (s instanceof MultiModeSetting multi) {
                    float curAnim = multiDropdownAnim.getOrDefault(multi, 0.0F);
                    float boxW = 136.0F * scale;
                    float boxX = b.box2X + 16.0F * scale + rowW - boxW;

                    if (openMultiDropdown == multi && curAnim > 0.5F) {
                        float dropY = setY + 1.5F * scale + 22.0F * scale + 4.0F * scale;
                        float optY = dropY;
                        for (String m : multi.getAllOptions()) {
                            if (inside(mouseX, mouseY, boxX, optY, boxW, 22.0F * scale)) {
                                multi.toggleOption(m);
                                return true;
                            }
                            optY += 22.0F * scale;
                        }
                    }

                    if (inside(mouseX, mouseY, boxX, setY + 1.5F * scale, boxW, 22.0F * scale)) {
                        openMultiDropdown = (openMultiDropdown == multi) ? null : multi;
                        openDropdown = null;
                        return true;
                    }
                    setY += 34.0F * scale + multi.getAllOptions().size() * (22.0F * scale) * curAnim;
                } else if (s instanceof ColorSetting color) {
                    float btnSize = 18.0F * scale;
                    float btnY = setY + (25.0F * scale - btnSize) * 0.5F;
                    float swatchX = b.box2X + 16.0F * scale + rowW - btnSize;
                    float pipetteX = swatchX - btnSize - 4.0F * scale;
                    if (inside(mouseX, mouseY, swatchX, btnY, btnSize, btnSize) || inside(mouseX, mouseY, pipetteX, btnY, btnSize, btnSize)) {
                        if (activeColorPicker == color && !colorPickerClosing) {
                            colorPickerClosing = true;
                        } else {
                            activeColorPicker = color;
                            colorPickerClosing = false;
                            colorPickerAnim = 0.0F;
                            pickerX = swatchX + btnSize;
                            pickerY = btnY;
                            int curCol = color.get();
                            float[] hsb = Color.RGBtoHSB((curCol >> 16) & 0xFF, (curCol >> 8) & 0xFF, curCol & 0xFF, null);
                            pickerHue = hsb[0];
                            pickerSat = hsb[1];
                            pickerBri = hsb[2];
                            pickerAlpha = ((curCol >> 24) & 0xFF) / 255.0F;
                            hexBuffer = (((curCol >> 24) & 0xFF) == 255) ? String.format("%06X", curCol & 0xFFFFFF) : String.format("%08X", curCol);
                            hexFocused = false;
                            pipetteActive = false;
                            colorFeedback = null;
                        }
                        return true;
                    }
                    setY += 34.0F * scale;
                } else if (s instanceof StringSetting str) {
                    float textSize = 11.5F * scale;
                    String val = str.get();
                    float labelW = ModernFont.getWidth(getSettingDisplayName(str.getName()), textSize, ModernFont.Type.INTER_MEDIUM);
                    float textW = ModernFont.getWidth(val.isEmpty() ? str.getPlaceholder() : val, 10.5F * scale, ModernFont.Type.INTER_MEDIUM);
                    float maxBoxW = rowW - labelW - 12.0F * scale;
                    float boxW = Math.max(120.0F * scale, Math.min(maxBoxW, textW + 24.0F * scale));
                    boxW = Math.min(boxW, rowW - 16.0F * scale);
                    float boxX = b.box2X + 16.0F * scale + rowW - boxW;
                    if (inside(mouseX, mouseY, boxX, setY + 2.5F * scale, boxW, 20.0F * scale)) {
                        if (activeStringSetting != null && activeStringSetting != str) {
                            activeStringSetting.set(activeStringDraft);
                        }
                        activeStringSetting = str;
                        activeStringDraft = str.get();
                        activeSliderInput = null;
                        activeMacroTextIndex = -1;
                        activeNameBindTextIndex = -1;
                        searchFocused = false;
                        return true;
                    }
                    setY += 34.0F * scale;
                }
            }
            if ("AutoBuy".equalsIgnoreCase(selectedModule.getName())) {
                if (inside(mouseX, mouseY, b.box2X + 16.0F * scale, setY + 2.0F * scale, rowW, 22.0F * scale)) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(new AutoBuyConfigScreen(mc.currentScreen));
                    return true;
                }
                setY += 34.0F * scale;
            }
        }
        return false;
    }

    private void copyModuleSettings(Module module) {
        if (module == null) return;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("module", module.getName());
            obj.addProperty("enabled", module.isEnabled());
            obj.addProperty("bind", MODULE_BINDS.getOrDefault(module.getName(), GLFW.GLFW_KEY_UNKNOWN));

            JsonObject setObj = new JsonObject();
            List<Setting<?>> list = moduleSettings.get(module);
            if (list != null) {
                for (Setting<?> s : list) {
                    if (s instanceof BooleanSetting b) setObj.addProperty(b.getName(), b.get());
                    else if (s instanceof SliderSetting sl) setObj.addProperty(sl.getName(), sl.get());
                    else if (s instanceof ModeSetting m) setObj.addProperty(m.getName(), m.get());
                    else if (s instanceof ColorSetting c) setObj.addProperty(c.getName(), c.get());
                    else if (s instanceof StringSetting st) setObj.addProperty(st.getName(), st.get());
                    else if (s instanceof KeybindSetting k) setObj.addProperty(k.getName(), k.get());
                }
            }
            obj.add("settings", setObj);
            MinecraftClient.getInstance().keyboard.setClipboard(obj.toString());
        } catch (Exception ignored) {}
    }

    private void pasteModuleSettings(Module module) {
        if (module == null) return;
        try {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clip == null || clip.isBlank()) return;
            JsonObject obj = JsonParser.parseString(clip).getAsJsonObject();
            if (obj.has("enabled")) module.setEnabled(obj.get("enabled").getAsBoolean());
            if (obj.has("bind")) {
                MODULE_BINDS.put(module.getName(), obj.get("bind").getAsInt());
                FluxVisualsClient.requestConfigSave();
            }
            if (obj.has("settings")) {
                JsonObject setObj = obj.getAsJsonObject("settings");
                List<Setting<?>> list = moduleSettings.get(module);
                if (list != null) {
                    for (Setting<?> s : list) {
                        if (setObj.has(s.getName())) {
                            if (s instanceof BooleanSetting b) b.set(setObj.get(s.getName()).getAsBoolean());
                            else if (s instanceof SliderSetting sl) sl.set(setObj.get(s.getName()).getAsFloat());
                            else if (s instanceof ModeSetting m) m.set(setObj.get(s.getName()).getAsString());
                            else if (s instanceof ColorSetting c) c.set(setObj.get(s.getName()).getAsInt());
                            else if (s instanceof StringSetting st) st.set(setObj.get(s.getName()).getAsString());
                            else if (s instanceof KeybindSetting k) k.set(setObj.get(s.getName()).getAsInt());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void resetModuleSettings(Module module) {
        if (module == null) return;
        String name = module.getName();
        if ("Hit Color".equalsIgnoreCase(name) || "HitColor".equalsIgnoreCase(name)) {
            HitColor hc = moduleManager.getHitColor();
            hc.setArmorTintEnabled(false);
            hc.setColor(0.75F, 0.8F, 1.0F);
            hc.setAlpha(0.6F);
        } else if ("ChinaHat".equalsIgnoreCase(name)) {
            ChinaHat ch = moduleManager.getChinaHat();
            ch.setSize(0.7F);
            ch.setFillMode(ChinaHat.FillMode.FILL);
            ch.setColor(0.75F, 0.8F, 1.0F);
        } else if ("Wings".equalsIgnoreCase(name)) {
            Wings w = moduleManager.getWings();
            w.setWingType(Wings.WingType.ANGELIC);
            w.setShaderFill(true);
            w.setFlapping(true);
            w.setScale(1.0F);
            w.setFlapSpeed(3.0F);
        } else if ("WorldCustomizer".equalsIgnoreCase(name)) {
            WorldCustomizer wc = moduleManager.getWorldCustomizer();
            wc.setTimePreset(WorldCustomizer.TimePreset.DAY);
            wc.setCustomFogEnabled(false);
            wc.setFogDistance(128.0F);
        } else if ("BlockOverlay".equalsIgnoreCase(name)) {
            BlockOverlay bo = moduleManager.getBlockOverlay();
            bo.setFillEnabled(true);
            bo.setThroughWalls(false);
            bo.setSmoothSwitch(true);
            bo.setLineThickness(1.5F);
        } else if ("TargetEsp".equalsIgnoreCase(name)) {
            TargetEsp te = moduleManager.getTargetEsp();
            te.setStyle(TargetEsp.Style.NORMAL);
            te.setTargetFilter(TargetEsp.TargetFilter.PLAYERS);
            te.setRedOnHit(true);
            te.setCrystalCount(4);
            te.setCrystalSize(1.0F);
        } else if ("Crosshair".equalsIgnoreCase(name)) {
            Crosshair c = moduleManager.getCrosshair();
            c.setSize(6.0F);
            c.setGap(3.0F);
            c.setThickness(2.0F);
            c.setDot(true);
        } else if ("HitboxCustomizer".equalsIgnoreCase(name)) {
            HitboxCustomizer hc = moduleManager.getHitboxCustomizer();
            hc.setAlwaysShow(false);
            hc.setFillEnabled(true);
            hc.setCornersOnly(false);
            hc.setShowLookVector(true);
            hc.setLineThickness(1.5F);
        } else if ("AimBot".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.AimBot ab = moduleManager.getAimBot();
            ab.setFov(60.0F);
            ab.setDistance(5.0F);
            ab.setYawSpeed(7.0F);
            ab.setPitchSpeed(6.5F);
            ab.setHumanize(45.0F);
            ab.setMaxSpeed(280.0F);
            ab.setDeadzone(0.5F);
            ab.setReactionMs(110.0F);
            ab.setAimYaw(true);
            ab.setAimPitch(true);
            ab.setStickyTarget(true);
            ab.setCheckWalls(true);
            ab.setOnlyOnAttack(false);
            ab.setRequireAimKey(false);
            ab.setComboEnabled(false);
            ab.setComboKey(0);
            ab.setTargetsName("Игроки");
            ab.setMultipoints(new java.util.HashSet<>(java.util.Set.of("Голова", "Грудь")));
        } else if ("TriggerBot".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.TriggerBot tb = moduleManager.getTriggerBot();
            tb.setChance(100.0F);
            tb.setCooldownJitter(false);
            tb.setMaceNoCooldown(true);
            tb.setOnlyOnAttackKey(false);
            tb.setNoUseHit(true);
            tb.setJumpForCrit(false);
            tb.setTargetsName("Все");
        } else if ("AutoMace".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.AutoMace am = moduleManager.getAutoMace();
            am.setRequireTrigger(true);
            am.setRestoreItem(true);
            am.setTimeoutMs(4000.0F);
        } else if ("NoJumpDelay".equalsIgnoreCase(name)) {
            dev.fuga.fluxvisuals.modules.combat.NoJumpDelay njd = moduleManager.getNoJumpDelay();
            njd.setJumpRandomize(true);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        GuiBounds b = new GuiBounds(MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight(), getGuiScale());
        float scale = b.scale;

        if (activeSlider != null) {
            float valBoxW = 36.0F * scale;
            float valBoxX = b.box2X + 16.0F * scale + b.rowW - valBoxW;
            float trackW = 56.0F * scale;
            float trackX = valBoxX - trackW - 8.0F * scale;
            float norm = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - trackX) / trackW)));
            activeSlider.setNormalized(norm);
            return true;
        }

        if (activeColorPicker != null) {
            float pickW = 194.0F * scale;
            float pickH = 192.0F * scale;
            float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float px = (pickerX > 0) ? (pickerX - pickW) : (screenW - pickW) * 0.5F;
            float py = (pickerY > 0) ? (pickerY + 24.0F * scale) : (screenH - pickH) * 0.5F;
            px = Math.max(10.0F * scale, Math.min(screenW - pickW - 10.0F * scale, px));
            py = Math.max(10.0F * scale, Math.min(screenH - pickH - 10.0F * scale, py));

            float hdrY = py + 8.0F * scale;
            float svX = px + 10.0F * scale;
            float svY = hdrY + 24.0F * scale;
            float svW = pickW - 20.0F * scale;
            float svH = 64.0F * scale;
            float hueY = svY + svH + 8.0F * scale;
            float hueH = 7.0F * scale;
            float opacityLabelY = hueY + hueH + 10.0F * scale;
            float alphaBarY = opacityLabelY + 16.0F * scale;

            if (draggingSV) {
                float svKnobInset = 4.0F * scale;
                pickerSat = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + svKnobInset)) / (svW - svKnobInset * 2.0F))));
                pickerBri = Math.max(0.0F, Math.min(1.0F, 1.0F - (float) ((mouseY - (svY + svKnobInset)) / (svH - svKnobInset * 2.0F))));
                updatePickerColor();
                return true;
            }
            if (draggingHue) {
                float hueKnobInset = 4.0F * scale;
                pickerHue = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + hueKnobInset)) / (svW - hueKnobInset * 2.0F))));
                updatePickerColor();
                return true;
            }
            if (draggingAlpha) {
                float alphaKnobInset = 4.0F * scale;
                pickerAlpha = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - (svX + alphaKnobInset)) / (svW - alphaKnobInset * 2.0F))));
                updatePickerColor();
                return true;
            }
        }
        return false;
    }

    public void mouseReleased() {
        if (activeSlider != null) {
            activeSlider.dragging = false;
            activeSlider = null;
        }
        draggingSV = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        GuiBounds b = new GuiBounds(MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight(), getGuiScale());
        if (inside(mouseX, mouseY, b.box1X, b.boxY, b.boxW, b.boxH)) {
            scrollLeft = Math.max(0.0F, scrollLeft - (float) amount * 24.0F * b.scale);
            return true;
        }
        if (settingsOpen && settingsAnim > 0.3F && inside(mouseX, mouseY, b.box2X, b.boxY, b.boxW, b.boxH)) {
            scrollRight = Math.max(0.0F, scrollRight - (float) amount * 24.0F * b.scale);
            return true;
        }
        return false;
    }

    private void updatePickerColor() {
        if (activeColorPicker == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF;
        int a = Math.round(pickerAlpha * 255.0F) & 0xFF;
        int argb = (a << 24) | rgb;
        activeColorPicker.set(argb);
        if (!hexFocused) {
            hexBuffer = (a == 255) ? String.format("%06X", rgb) : String.format("%08X", argb);
        }
    }

    private static Identifier whiteIcon(Identifier source) {
        if (source == null) return null;
        Identifier cached = WHITE_ICONS.get(source);
        if (cached != null) return cached;
        MinecraftClient client = MinecraftClient.getInstance();
        try (InputStream stream = client.getResourceManager().open(source);
             NativeImage image = NativeImage.read(stream)) {
            NativeImage white = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = image.getColorArgb(x, y) >>> 24;
                    white.setColorArgb(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.of("fluxvisuals", "dynamic/white_modern_" + source.getPath().replace('/', '_'));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), white);
            client.getTextureManager().registerTexture(id, texture);
            texture.upload();
            WHITE_ICONS.put(source, id);
            return id;
        } catch (IOException | RuntimeException exception) {
            return source;
        }
    }

    private static void drawRotatedChevron(DrawContext context, float cx, float cy, float w, float h, float angle, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, cy);
        matrices.rotate(angle);
        drawTexture(context, ICON_CHEVRON, -w * 0.5F, -h * 0.5F, w, h, color);
        matrices.popMatrix();
    }

    private static void drawTexture(DrawContext context, Identifier texture, float x, float y, float w, float h, int color) {
        if (texture == null || w <= 0.0F || h <= 0.0F) return;
        int[] source = iconSourceDimensions(texture);
        float sourceAspect = source[0] / (float) source[1];
        float boxAspect = w / h;
        float drawW = w;
        float drawH = h;
        if (boxAspect > sourceAspect) {
            drawW = h * sourceAspect;
        } else if (boxAspect < sourceAspect) {
            drawH = w / sourceAspect;
        }
        float drawX = x + (w - drawW) * 0.5F;
        float drawY = y + (h - drawH) * 0.5F;
        Render2D.drawRoundTexture(context, texture, drawX, drawY, drawW, drawH, 0.0F, color);
    }

    private static int[] iconSourceDimensions(Identifier texture) {
        return new int[]{128, 128};
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static int argb(int a, int r, int g, int b) {
        return argb(a, r, g, b, 1.0F);
    }

    private static int argb(int a, int r, int g, int b, float mulAlpha) {
        int alpha = Math.max(0, Math.min(255, Math.round(a * mulAlpha)));
        return (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int color(int rgb, float alpha) {
        int a = Math.round(((rgb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0) / 2.0F;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.3F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3.0) + c1 * (float) Math.pow(x - 1.0F, 2.0);
    }

    private static void drawDashedOutline(DrawContext context, float x, float y, float w, float h, float scale, int color) {
        float dash = 3.5F * scale;
        float gap = 2.5F * scale;
        float step = dash + gap;
        float xStart = x + 3.0F * scale;
        float xEnd = x + w - 3.0F * scale;
        for (float cx = xStart; cx < xEnd; cx += step) {
            float segW = Math.min(dash, xEnd - cx);
            Render2D.drawLine(context, cx, y, cx + segW, y, 1.0F, color);
            Render2D.drawLine(context, cx, y + h, cx + segW, y + h, 1.0F, color);
        }
        float yStart = y + 3.0F * scale;
        float yEnd = y + h - 3.0F * scale;
        for (float cy = yStart; cy < yEnd; cy += step) {
            float segH = Math.min(dash, yEnd - cy);
            Render2D.drawLine(context, x, cy, x, cy + segH, 1.0F, color);
            Render2D.drawLine(context, x + w, cy, x + w, cy + segH, 1.0F, color);
        }
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_F && isCtrlDown()) {
            searchFocused = true;
            activeStringSetting = null;
            activeSliderInput = null;
            activeMacroTextIndex = -1;
            activeNameBindTextIndex = -1;
            hexFocused = false;
            return true;
        }
        if (pipetteActive && (keyCode == GLFW.GLFW_KEY_ESCAPE)) {
            pipetteActive = false;
            return true;
        }
        if (listeningModuleBind != null) {
            int newKey = keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
            if (listeningModuleBind == moduleManager.getMenu()) {
                moduleManager.getMenu().setKeyBind(newKey);
            } else {
                MODULE_BINDS.put(listeningModuleBind.getName(), newKey);
                FluxVisualsClient.requestConfigSave();
            }
            listeningModuleBind = null;
            return true;
        }
        if (listeningSettingBind != null) {
            listeningSettingBind.set(keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            listeningSettingBind = null;
            return true;
        }
        if (listeningMacroIndex >= 0) {
            moduleManager.getMacros().updateBind(listeningMacroIndex, keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            listeningMacroIndex = -1;
            return true;
        }
        if (listeningNameBindIndex >= 0) {
            moduleManager.getNameBind().updateBind(listeningNameBindIndex, keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            listeningNameBindIndex = -1;
            return true;
        }
        Menu menu = moduleManager.getMenu();
        if (menu != null && menu.getKeyBind() != 0 && menu.getKeyBind() != GLFW.GLFW_KEY_UNKNOWN && keyCode == menu.getKeyBind()) {
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        if (activeSliderInput != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !sliderInputBuffer.isEmpty()) {
                sliderInputBuffer = sliderInputBuffer.substring(0, sliderInputBuffer.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applySliderInput();
                activeSliderInput = null;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeSliderInput = null;
            }
            return true;
        }
        if (activeMacroTextIndex >= 0) {
            var entries = moduleManager.getMacros().getEntries();
            if (activeMacroTextIndex < entries.size()) {
                String cur = entries.get(activeMacroTextIndex).text();
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !cur.isEmpty()) {
                    moduleManager.getMacros().updateText(activeMacroTextIndex, cur.substring(0, cur.length() - 1));
                } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    activeMacroTextIndex = -1;
                } else if (keyCode == GLFW.GLFW_KEY_V && isCtrlDown()) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null) moduleManager.getMacros().updateText(activeMacroTextIndex, cur + clip);
                } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                    moduleManager.getMacros().updateText(activeMacroTextIndex, cur + " ");
                }
                return true;
            }
        }
        if (activeNameBindTextIndex >= 0) {
            var entries = moduleManager.getNameBind().getEntries();
            if (activeNameBindTextIndex < entries.size()) {
                String cur = entries.get(activeNameBindTextIndex).text();
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !cur.isEmpty()) {
                    moduleManager.getNameBind().updateText(activeNameBindTextIndex, cur.substring(0, cur.length() - 1));
                } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    activeNameBindTextIndex = -1;
                } else if (keyCode == GLFW.GLFW_KEY_V && isCtrlDown()) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null) moduleManager.getNameBind().updateText(activeNameBindTextIndex, cur + clip);
                } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                    moduleManager.getNameBind().updateText(activeNameBindTextIndex, cur + " ");
                }
                return true;
            }
        }
        if (activeStringSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!activeStringDraft.isEmpty()) {
                    activeStringDraft = activeStringDraft.substring(0, activeStringDraft.length() - 1);
                    activeStringSetting.set(activeStringDraft);
                }
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeStringSetting.set(activeStringDraft);
                activeStringSetting = null;
            } else if (keyCode == GLFW.GLFW_KEY_V && isCtrlDown()) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) {
                    activeStringDraft += clip;
                    activeStringSetting.set(activeStringDraft);
                }
            } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                activeStringDraft += " ";
                activeStringSetting.set(activeStringDraft);
            }
            return true;
        }
        if (hexFocused && activeColorPicker != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !hexBuffer.isEmpty()) {
                hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                applyHexBuffer();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_V && isCtrlDown()) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) {
                    hexBuffer = clip.replaceAll("[^0-9a-fA-F]", "");
                    applyHexBuffer();
                }
            } else if (keyCode == GLFW.GLFW_KEY_C && isCtrlDown()) {
                int col = activeColorPicker.get();
                String hexStr = (((col >> 24) & 0xFF) == 255) ? String.format("#%06X", col & 0xFFFFFF) : String.format("#%08X", col);
                MinecraftClient.getInstance().keyboard.setClipboard(hexStr);
                colorFeedback = t("Скопировано!", "Copied!");
                colorFeedbackTime = System.currentTimeMillis();
            }
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_V && isCtrlDown()) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) search += clip;
            } else if (keyCode == GLFW.GLFW_KEY_A && isCtrlDown()) {
                search = "";
            } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                search += " ";
            }
            return true;
        }
        return false;
    }

    private static boolean isCtrlDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public boolean charTyped(char chr) {
        if (chr < 32 && chr != 9) return false;

        if (activeSliderInput != null) {
            if ("0123456789.,-".indexOf(chr) >= 0 && sliderInputBuffer.length() < 10) {
                sliderInputBuffer += (chr == ',' ? '.' : chr);
                return true;
            }
            return true;
        }
        if (searchFocused) {
            search += chr;
            return true;
        }
        if (activeMacroTextIndex >= 0) {
            var entries = moduleManager.getMacros().getEntries();
            if (activeMacroTextIndex < entries.size()) {
                moduleManager.getMacros().updateText(activeMacroTextIndex, entries.get(activeMacroTextIndex).text() + chr);
                return true;
            }
        }
        if (activeNameBindTextIndex >= 0) {
            var entries = moduleManager.getNameBind().getEntries();
            if (activeNameBindTextIndex < entries.size()) {
                moduleManager.getNameBind().updateText(activeNameBindTextIndex, entries.get(activeNameBindTextIndex).text() + chr);
                return true;
            }
        }
        if (activeStringSetting != null) {
            activeStringDraft += chr;
            activeStringSetting.set(activeStringDraft);
            return true;
        }
        if (hexFocused && activeColorPicker != null && "0123456789abcdefABCDEF".indexOf(chr) >= 0 && hexBuffer.length() < 8) {
            hexBuffer += chr;
            applyHexBuffer();
            return true;
        }
        return false;
    }

    private void applySliderInput() {
        if (activeSliderInput == null) return;
        try {
            float val = Float.parseFloat(sliderInputBuffer.replace(',', '.'));
            activeSliderInput.set(val);
        } catch (Exception ignored) {}
    }

    private void applyHexBuffer() {
        if (activeColorPicker == null) return;
        if (hexBuffer.length() != 6 && hexBuffer.length() != 8) return;
        try {
            long parsed = Long.parseLong(hexBuffer, 16);
            int col;
            if (hexBuffer.length() == 6) {
                col = ((int) parsed) | 0xFF000000;
                pickerAlpha = 1.0F;
            } else {
                col = (int) parsed;
                pickerAlpha = ((col >> 24) & 0xFF) / 255.0F;
            }
            activeColorPicker.set(col);
            float[] hsb = Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
            pickerHue = hsb[0];
            pickerSat = hsb[1];
            pickerBri = hsb[2];
        } catch (Exception ignored) {}
    }

}
