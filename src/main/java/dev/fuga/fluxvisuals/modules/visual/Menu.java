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

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals_menu.json");
    private String scaleMode = "100% (Normal)";
    private String language = "Русский";
    private int keyBind = GLFW.GLFW_KEY_RIGHT_SHIFT;

    public Menu() {
        super("Menu", "Настройка масштаба, оформления и анимаций ClickGUI.", ModuleCategory.SETTINGS);
        setEnabled(true);
        loadConfig();
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

    public void saveConfig() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("scaleMode", scaleMode);
            obj.addProperty("language", language);
            obj.addProperty("keyBind", keyBind);
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
                    int savedKey = obj.get("keyBind").getAsInt();
                    this.keyBind = (savedKey == 0 || savedKey == GLFW.GLFW_KEY_UNKNOWN || savedKey == GLFW.GLFW_KEY_BACKSLASH) ? GLFW.GLFW_KEY_RIGHT_SHIFT : savedKey;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
