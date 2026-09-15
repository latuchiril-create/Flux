package dev.fuga.fluxvisuals.modules.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class Menu extends Module {
    public static final List<String> SCALE_MODES = List.of(
            "50% (Tiny)",
            "75% (Compact)",
            "85% (Small)",
            "100% (Normal)",
            "115% (Large)",
            "130% (Expanded)"
    );

    public static final List<String> LANGUAGE_MODES = List.of(
            "Русский",
            "English"
    );

    public static final List<String> THEME_PRESETS = List.of(
            "Flux Циан",
            "Неон Пурпур",
            "Закат",
            "Изумруд",
            "Океан",
            "Кровавая луна",
            "Киберпанк",
            "Монохром",
            "Кастом"
    );

    public static final List<String> COLOR_COUNT_OPTIONS = List.of(
            "1 цвет (Статичный)",
            "2 цвета (Градиент)",
            "3 цвета (Тройной перелив)"
    );

    public static final List<String> GUI_STYLES = List.of(
            "Modern 2",
            "Modern 1"
    );

    public static final List<String> HUD_SCALE_MODES = List.of(
            "75%",
            "85%",
            "100%",
            "115%",
            "125%"
    );

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals_menu.json");
    private String guiStyle = "Modern 2";
    private String scaleMode = "100% (Normal)";
    private String hudScaleMode = "100%";
    private boolean showDescriptions = true;
    private boolean autoSavePreset = true;
    private String language = "Русский";
    private int keyBind = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private String themePreset = "Flux Циан";
    private String colorCountMode = "1 цвет (Статичный)";
    private int color1 = 0xFF74B9CD;
    private int color2 = 0xFF8AC9DA;
    private int color3 = 0xFF5EA8BC;
    private int customColor1 = 0xFF5C7CFA;
    private int customColor2 = 0xFF8AC9DA;
    private int customColor3 = 0xFF5EA8BC;
    private float flowSpeed = 1.0F;

    public Menu() {
        super("Menu", "Настройка масштаба, оформления, тем и анимаций ClickGUI.", ModuleCategory.SETTINGS);
        setEnabled(true);
        loadConfig();
    }

    public String getGuiStyle() {
        return guiStyle;
    }

    public void setGuiStyle(String guiStyle) {
        if (GUI_STYLES.contains(guiStyle)) {
            this.guiStyle = guiStyle;
            saveConfig();
        }
    }

    public String getHudScaleMode() {
        return hudScaleMode;
    }

    public void setHudScaleMode(String hudScaleMode) {
        if (HUD_SCALE_MODES.contains(hudScaleMode)) {
            this.hudScaleMode = hudScaleMode;
            saveConfig();
        }
    }

    public boolean isShowDescriptions() {
        return showDescriptions;
    }

    public void setShowDescriptions(boolean showDescriptions) {
        this.showDescriptions = showDescriptions;
        saveConfig();
    }

    public boolean isAutoSavePreset() {
        return autoSavePreset;
    }

    public void setAutoSavePreset(boolean autoSavePreset) {
        this.autoSavePreset = autoSavePreset;
        saveConfig();
    }

    public String getScaleMode() {
        return scaleMode;
    }

    public void setScaleMode(String scaleMode) {
        if (SCALE_MODES.contains(scaleMode)) {
            this.scaleMode = scaleMode;
            saveConfig();
        }
    }

    public float getScaleMultiplier() {
        return switch (scaleMode) {
            case "50% (Tiny)" -> 0.50F;
            case "75% (Compact)" -> 0.75F;
            case "85% (Small)" -> 0.85F;
            case "115% (Large)" -> 1.15F;
            case "130% (Expanded)" -> 1.30F;
            default -> 1.0F;
        };
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        if (LANGUAGE_MODES.contains(language)) {
            this.language = language;
            saveConfig();
        }
    }

    public boolean isEnglish() {
        return "English".equalsIgnoreCase(language);
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
        saveConfig();
    }

    public String getThemePreset() {
        return themePreset;
    }

    public void setThemePreset(String themePreset) {
        if (THEME_PRESETS.contains(themePreset)) {
            this.themePreset = themePreset;
            applyPresetColors(themePreset);
            saveConfig();
            dev.fuga.fluxvisuals.gui.modern.setting.ColorSetting.syncAllToTheme();
        }
    }

    private void applyPresetColors(String preset) {
        switch (preset) {
            case "Flux Циан" -> {
                this.color1 = 0xFF74B9CD;
                this.color2 = 0xFF4A90E2;
                this.color3 = 0xFF99E2EC;
                this.colorCountMode = "2 цвета (Градиент)";
                this.flowSpeed = 1.0F;
            }
            case "Неон Пурпур" -> {
                this.color1 = 0xFFA855F7;
                this.color2 = 0xFFEC4899;
                this.color3 = 0xFF6366F1;
                this.colorCountMode = "2 цвета (Градиент)";
                this.flowSpeed = 1.0F;
            }
            case "Закат" -> {
                this.color1 = 0xFFF59E0B;
                this.color2 = 0xFFEF4444;
                this.color3 = 0xFFEC4899;
                this.colorCountMode = "3 цвета (Тройной перелив)";
                this.flowSpeed = 1.0F;
            }
            case "Изумруд" -> {
                this.color1 = 0xFF10B981;
                this.color2 = 0xFF06B6D4;
                this.color3 = 0xFF3B82F6;
                this.colorCountMode = "2 цвета (Градиент)";
                this.flowSpeed = 1.0F;
            }
            case "Океан" -> {
                this.color1 = 0xFF06B6D4;
                this.color2 = 0xFF3B82F6;
                this.color3 = 0xFF6366F1;
                this.colorCountMode = "3 цвета (Тройной перелив)";
                this.flowSpeed = 1.0F;
            }
            case "Кровавая луна" -> {
                this.color1 = 0xFFEF4444;
                this.color2 = 0xFF991B1B;
                this.color3 = 0xFFF87171;
                this.colorCountMode = "2 цвета (Градиент)";
                this.flowSpeed = 1.0F;
            }
            case "Киберпанк" -> {
                this.color1 = 0xFF06B6D4;
                this.color2 = 0xFFF43F5E;
                this.color3 = 0xFFEAB308;
                this.colorCountMode = "3 цвета (Тройной перелив)";
                this.flowSpeed = 1.0F;
            }
            case "Монохром" -> {
                this.color1 = 0xFFE2E8F0;
                this.color2 = 0xFF94A3B8;
                this.color3 = 0xFF64748B;
                this.colorCountMode = "1 цвет (Статичный)";
                this.flowSpeed = 1.0F;
            }
            case "Кастом" -> {
                this.color1 = this.customColor1;
                this.color2 = this.customColor2;
                this.color3 = this.customColor3;
                this.colorCountMode = "1 цвет (Статичный)";
                this.flowSpeed = 1.0F;
            }
            default -> {}
        }
    }

    public String getColorCountMode() {
        return colorCountMode;
    }

    public void setColorCountMode(String colorCountMode) {
        if (COLOR_COUNT_OPTIONS.contains(colorCountMode)) {
            this.colorCountMode = colorCountMode;
            saveConfig();
        }
    }

    public int getColorCount() {
        if (colorCountMode.startsWith("1")) return 1;
        if (colorCountMode.startsWith("2")) return 2;
        if (colorCountMode.startsWith("3")) return 3;
        return 1;
    }

    public int getColor1() {
        return color1;
    }

    public void setColor1(int color1) {
        this.color1 = color1;
        this.customColor1 = color1;
        this.themePreset = "Кастом";
        this.colorCountMode = "1 цвет (Статичный)";
        saveConfig();
        dev.fuga.fluxvisuals.gui.modern.setting.ColorSetting.syncAllToTheme();
    }

    public int getColor2() {
        return color2;
    }

    public void setColor2(int color2) {
        this.color2 = color2;
        this.customColor2 = color2;
        this.themePreset = "Кастом";
        saveConfig();
        dev.fuga.fluxvisuals.gui.modern.setting.ColorSetting.syncAllToTheme();
    }

    public int getColor3() {
        return color3;
    }

    public void setColor3(int color3) {
        this.color3 = color3;
        this.customColor3 = color3;
        this.themePreset = "Кастом";
        saveConfig();
        dev.fuga.fluxvisuals.gui.modern.setting.ColorSetting.syncAllToTheme();
    }

    public float getFlowSpeed() {
        return flowSpeed;
    }

    public void setFlowSpeed(float flowSpeed) {
        this.flowSpeed = Math.max(0.1F, Math.min(5.0F, flowSpeed));
        saveConfig();
    }

    public int getLiveColor(float offset) {
        int count = getColorCount();
        int c1 = 0xFF000000 | (color1 & 0x00FFFFFF);
        if (count <= 1) {
            return c1;
        }
        int c2 = 0xFF000000 | (color2 & 0x00FFFFFF);
        int c3 = 0xFF000000 | (color3 & 0x00FFFFFF);
        long time = System.currentTimeMillis();
        float speedFactor = Math.max(0.1F, flowSpeed);
        float periodMs = 3500.0F / speedFactor;
        float t = (((time % (long) periodMs) / periodMs + offset) % 1.0F + 1.0F) % 1.0F;

        if (count == 2) {
            float wave = (float) (1.0 - Math.cos(t * Math.PI * 2.0)) * 0.5F;
            return interpolateColor(c1, c2, wave);
        } else {
            float step = t * 3.0F;
            if (step < 1.0F) {
                float local = (float) (1.0 - Math.cos(step * Math.PI)) * 0.5F;
                return interpolateColor(c1, c2, local);
            } else if (step < 2.0F) {
                float local = (float) (1.0 - Math.cos((step - 1.0F) * Math.PI)) * 0.5F;
                return interpolateColor(c2, c3, local);
            } else {
                float local = (float) (1.0 - Math.cos((step - 2.0F) * Math.PI)) * 0.5F;
                return interpolateColor(c3, c1, local);
            }
        }
    }

    public int getLiveAccentStrong(float offset) {
        int base = getLiveColor(offset);
        return mixRgb(base, 0xFFFFFFFF, 0.20F);
    }

    public static int interpolateColor(int c1, int c2, float delta) {
        float t = Math.max(0.0F, Math.min(1.0F, delta));
        float ease = t * t * (3.0F - 2.0F * t); // Smooth Hermite S-curve (smoothstep)

        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        float[] hsb1 = java.awt.Color.RGBtoHSB(r1, g1, b1, null);
        float[] hsb2 = java.awt.Color.RGBtoHSB(r2, g2, b2, null);

        float h1 = hsb1[0], s1 = hsb1[1], v1 = hsb1[2];
        float h2 = hsb2[0], s2 = hsb2[1], v2 = hsb2[2];

        // If either color is essentially monochrome/grayscale, blend in gamma-corrected RGB
        if (s1 < 0.04F || s2 < 0.04F) {
            int a = Math.round(a1 + (a2 - a1) * ease);
            int r = Math.min(255, Math.round((float) Math.sqrt((1.0F - ease) * r1 * r1 + ease * r2 * r2)));
            int g = Math.min(255, Math.round((float) Math.sqrt((1.0F - ease) * g1 * g1 + ease * g2 * g2)));
            int b = Math.min(255, Math.round((float) Math.sqrt((1.0F - ease) * b1 * b1 + ease * b2 * b2)));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        float diff = h2 - h1;
        if (diff > 0.5F) {
            diff -= 1.0F;
        } else if (diff < -0.5F) {
            diff += 1.0F;
        }

        float h = (h1 + diff * ease) % 1.0F;
        if (h < 0.0F) h += 1.0F;
        float s = s1 + (s2 - s1) * ease;
        float v = v1 + (v2 - v1) * ease;

        int rgb = java.awt.Color.HSBtoRGB(h, Math.max(0.0F, Math.min(1.0F, s)), Math.max(0.0F, Math.min(1.0F, v))) & 0x00FFFFFF;
        int a = Math.round(a1 + (a2 - a1) * ease);
        return (a << 24) | rgb;
    }

    private static int mixRgb(int c, int target, float ratio) {
        int a = (c >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * (1.0F - ratio) + ((target >> 16) & 0xFF) * ratio));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * (1.0F - ratio) + ((target >> 8) & 0xFF) * ratio));
        int b = Math.min(255, Math.round((c & 0xFF) * (1.0F - ratio) + (target & 0xFF) * ratio));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void saveConfig() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("guiStyle", guiStyle);
            obj.addProperty("scaleMode", scaleMode);
            obj.addProperty("hudScaleMode", hudScaleMode);
            obj.addProperty("showDescriptions", showDescriptions);
            obj.addProperty("autoSavePreset", autoSavePreset);
            obj.addProperty("language", language);
            obj.addProperty("keyBind", keyBind);
            obj.addProperty("themePreset", themePreset);
            obj.addProperty("colorCountMode", colorCountMode);
            obj.addProperty("color1", color1);
            obj.addProperty("color2", color2);
            obj.addProperty("color3", color3);
            obj.addProperty("customColor1", customColor1);
            obj.addProperty("customColor2", customColor2);
            obj.addProperty("customColor3", customColor3);
            obj.addProperty("flowSpeed", flowSpeed);
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, obj.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    public void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String str = Files.readString(CONFIG_PATH);
                JsonObject obj = JsonParser.parseString(str).getAsJsonObject();
                if (obj.has("guiStyle")) {
                    String saved = obj.get("guiStyle").getAsString();
                    if (GUI_STYLES.contains(saved)) {
                        this.guiStyle = saved;
                    }
                }
                if (obj.has("hudScaleMode")) {
                    String saved = obj.get("hudScaleMode").getAsString();
                    if (HUD_SCALE_MODES.contains(saved)) {
                        this.hudScaleMode = saved;
                    }
                }
                if (obj.has("showDescriptions")) {
                    this.showDescriptions = obj.get("showDescriptions").getAsBoolean();
                }
                if (obj.has("autoSavePreset")) {
                    this.autoSavePreset = obj.get("autoSavePreset").getAsBoolean();
                }
                if (obj.has("scaleMode")) {
                    String saved = obj.get("scaleMode").getAsString();
                    if (SCALE_MODES.contains(saved)) {
                        this.scaleMode = saved;
                    }
                }
                if (obj.has("language")) {
                    String saved = obj.get("language").getAsString();
                    if (LANGUAGE_MODES.contains(saved)) {
                        this.language = saved;
                    }
                }
                if (obj.has("keyBind")) {
                    this.keyBind = obj.get("keyBind").getAsInt();
                }
                if (obj.has("themePreset")) {
                    String savedPreset = obj.get("themePreset").getAsString();
                    if (THEME_PRESETS.contains(savedPreset)) {
                        this.themePreset = savedPreset;
                    }
                }
                if (obj.has("colorCountMode")) {
                    String savedCount = obj.get("colorCountMode").getAsString();
                    if (COLOR_COUNT_OPTIONS.contains(savedCount)) {
                        this.colorCountMode = savedCount;
                    }
                }
                if (obj.has("color1")) {
                    this.color1 = obj.get("color1").getAsInt();
                }
                if (obj.has("color2")) {
                    this.color2 = obj.get("color2").getAsInt();
                }
                if (obj.has("color3")) {
                    this.color3 = obj.get("color3").getAsInt();
                }
                if (obj.has("customColor1")) {
                    this.customColor1 = obj.get("customColor1").getAsInt();
                }
                if (obj.has("customColor2")) {
                    this.customColor2 = obj.get("customColor2").getAsInt();
                }
                if (obj.has("customColor3")) {
                    this.customColor3 = obj.get("customColor3").getAsInt();
                }
                if (obj.has("flowSpeed")) {
                    this.flowSpeed = obj.get("flowSpeed").getAsFloat();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
