package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

public final class KeybindSetting extends Setting<Integer> {
    public boolean listening;

    public KeybindSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(name, getter, setter);
    }

    public static String getKeyName(int keyCode) {
        return getKeyName(keyCode, false);
    }

    public static String getKeyName(int keyCode, boolean isEn) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0) {
            return isEn ? "NONE" : "НЕТУ";
        }
        // Mouse Buttons (1000+)
        if (keyCode >= 1000 && keyCode <= 1010) {
            int btn = keyCode - 1000;
            return switch (btn) {
                case 0 -> "M1";
                case 1 -> "M2";
                case 2 -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6";
                case 6 -> "M7";
                case 7 -> "M8";
                default -> "M" + (btn + 1);
            };
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('A' + keyCode - GLFW.GLFW_KEY_A));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) ('0' + keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "ALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSLASH -> "BSLASH";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            default -> {
                String name = GLFW.glfwGetKeyName(keyCode, 0);
                yield name != null ? name.toUpperCase() : "KEY_" + keyCode;
            }
        };
    }
}
