package dev.fuga.fluxvisuals.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer;
import dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer;
import dev.fuga.fluxvisuals.gui.modern.setting.KeybindSetting;
import dev.fuga.fluxvisuals.modules.Module;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class BindCommand {
    private static final String PREFIX = ".bind";
    private static final String SHORT_PREFIX = ".b";

    private static final Map<String, Integer> KEY_MAP = new HashMap<>();
    public static final List<String> COMMON_KEY_SUGGESTIONS = List.of(
            "none",
            "rshift", "lshift", "rctrl", "lctrl", "ralt", "lalt",
            "r", "c", "v", "g", "f", "x", "z", "b", "h", "j", "k", "l", "m",
            "q", "e", "y", "u", "i", "o", "p", "t", "n",
            "mouse3", "mouse4", "mouse5",
            "tab", "space", "grave", "minus", "equal", "backslash"
    );

    static {
        // Unbind keywords
        KEY_MAP.put("none", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("null", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("clear", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("0", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("unbind", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("remove", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("delete", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("del", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("off", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("нету", GLFW.GLFW_KEY_UNKNOWN);
        KEY_MAP.put("нет", GLFW.GLFW_KEY_UNKNOWN);

        // Letters
        for (char c = 'a'; c <= 'z'; c++) {
            KEY_MAP.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        }

        // Digits
        for (char c = '0'; c <= '9'; c++) {
            KEY_MAP.put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }

        // Function Keys
        for (int i = 1; i <= 25; i++) {
            KEY_MAP.put("f" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }

        // Modifiers and Controls
        KEY_MAP.put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KEY_MAP.put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KEY_MAP.put("right_shift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KEY_MAP.put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        KEY_MAP.put("leftshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        KEY_MAP.put("left_shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        KEY_MAP.put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);

        KEY_MAP.put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEY_MAP.put("rightctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEY_MAP.put("right_ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEY_MAP.put("rcontrol", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEY_MAP.put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEY_MAP.put("leftctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEY_MAP.put("left_ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEY_MAP.put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEY_MAP.put("control", GLFW.GLFW_KEY_LEFT_CONTROL);

        KEY_MAP.put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        KEY_MAP.put("rightalt", GLFW.GLFW_KEY_RIGHT_ALT);
        KEY_MAP.put("right_alt", GLFW.GLFW_KEY_RIGHT_ALT);
        KEY_MAP.put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        KEY_MAP.put("leftalt", GLFW.GLFW_KEY_LEFT_ALT);
        KEY_MAP.put("left_alt", GLFW.GLFW_KEY_LEFT_ALT);
        KEY_MAP.put("alt", GLFW.GLFW_KEY_LEFT_ALT);

        KEY_MAP.put("space", GLFW.GLFW_KEY_SPACE);
        KEY_MAP.put("spacebar", GLFW.GLFW_KEY_SPACE);
        KEY_MAP.put("tab", GLFW.GLFW_KEY_TAB);
        KEY_MAP.put("enter", GLFW.GLFW_KEY_ENTER);
        KEY_MAP.put("return", GLFW.GLFW_KEY_ENTER);
        KEY_MAP.put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        KEY_MAP.put("back", GLFW.GLFW_KEY_BACKSPACE);
        KEY_MAP.put("caps", GLFW.GLFW_KEY_CAPS_LOCK);
        KEY_MAP.put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        KEY_MAP.put("esc", GLFW.GLFW_KEY_ESCAPE);
        KEY_MAP.put("escape", GLFW.GLFW_KEY_ESCAPE);

        KEY_MAP.put("insert", GLFW.GLFW_KEY_INSERT);
        KEY_MAP.put("ins", GLFW.GLFW_KEY_INSERT);
        KEY_MAP.put("home", GLFW.GLFW_KEY_HOME);
        KEY_MAP.put("end", GLFW.GLFW_KEY_END);
        KEY_MAP.put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        KEY_MAP.put("pgup", GLFW.GLFW_KEY_PAGE_UP);
        KEY_MAP.put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        KEY_MAP.put("pgdn", GLFW.GLFW_KEY_PAGE_DOWN);

        KEY_MAP.put("up", GLFW.GLFW_KEY_UP);
        KEY_MAP.put("down", GLFW.GLFW_KEY_DOWN);
        KEY_MAP.put("left", GLFW.GLFW_KEY_LEFT);
        KEY_MAP.put("right", GLFW.GLFW_KEY_RIGHT);

        KEY_MAP.put("minus", GLFW.GLFW_KEY_MINUS);
        KEY_MAP.put("-", GLFW.GLFW_KEY_MINUS);
        KEY_MAP.put("equal", GLFW.GLFW_KEY_EQUAL);
        KEY_MAP.put("equals", GLFW.GLFW_KEY_EQUAL);
        KEY_MAP.put("=", GLFW.GLFW_KEY_EQUAL);

        KEY_MAP.put("backslash", GLFW.GLFW_KEY_BACKSLASH);
        KEY_MAP.put("bslash", GLFW.GLFW_KEY_BACKSLASH);
        KEY_MAP.put("\\", GLFW.GLFW_KEY_BACKSLASH);
        KEY_MAP.put("slash", GLFW.GLFW_KEY_SLASH);
        KEY_MAP.put("/", GLFW.GLFW_KEY_SLASH);

        KEY_MAP.put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        KEY_MAP.put("tilde", GLFW.GLFW_KEY_GRAVE_ACCENT);
        KEY_MAP.put("`", GLFW.GLFW_KEY_GRAVE_ACCENT);
        KEY_MAP.put("~", GLFW.GLFW_KEY_GRAVE_ACCENT);

        KEY_MAP.put("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        KEY_MAP.put(";", GLFW.GLFW_KEY_SEMICOLON);
        KEY_MAP.put("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        KEY_MAP.put("quote", GLFW.GLFW_KEY_APOSTROPHE);
        KEY_MAP.put("'", GLFW.GLFW_KEY_APOSTROPHE);
        KEY_MAP.put("comma", GLFW.GLFW_KEY_COMMA);
        KEY_MAP.put(",", GLFW.GLFW_KEY_COMMA);
        KEY_MAP.put("period", GLFW.GLFW_KEY_PERIOD);
        KEY_MAP.put("dot", GLFW.GLFW_KEY_PERIOD);
        KEY_MAP.put(".", GLFW.GLFW_KEY_PERIOD);

        KEY_MAP.put("lbracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        KEY_MAP.put("[", GLFW.GLFW_KEY_LEFT_BRACKET);
        KEY_MAP.put("rbracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        KEY_MAP.put("]", GLFW.GLFW_KEY_RIGHT_BRACKET);

        // Numpad Keys
        for (int i = 0; i <= 9; i++) {
            KEY_MAP.put("num" + i, GLFW.GLFW_KEY_KP_0 + i);
            KEY_MAP.put("numpad" + i, GLFW.GLFW_KEY_KP_0 + i);
        }
        KEY_MAP.put("numlock", GLFW.GLFW_KEY_NUM_LOCK);
        KEY_MAP.put("numdivide", GLFW.GLFW_KEY_KP_DIVIDE);
        KEY_MAP.put("nummultiply", GLFW.GLFW_KEY_KP_MULTIPLY);
        KEY_MAP.put("numsubtract", GLFW.GLFW_KEY_KP_SUBTRACT);
        KEY_MAP.put("numadd", GLFW.GLFW_KEY_KP_ADD);
        KEY_MAP.put("numenter", GLFW.GLFW_KEY_KP_ENTER);
        KEY_MAP.put("numdecimal", GLFW.GLFW_KEY_KP_DECIMAL);

        // Mouse buttons
        for (int i = 1; i <= 8; i++) {
            KEY_MAP.put("mouse" + i, 1000 + (i - 1));
            KEY_MAP.put("m" + i, 1000 + (i - 1));
        }
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
        ClientSendMessageEvents.ALLOW_CHAT.register(BindCommand::onChatMessage);
        ClientSendMessageEvents.ALLOW_COMMAND.register(BindCommand::onCommandMessage);
    }

    private static boolean onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }
        String trimmed = message.trim();
        if (trimmed.equalsIgnoreCase(PREFIX) || trimmed.regionMatches(true, 0, PREFIX + " ", 0, PREFIX.length() + 1)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                String args = trimmed.length() > PREFIX.length() ? trimmed.substring(PREFIX.length()).trim() : "";
                client.execute(() -> executeFromChat(client, args));
            }
            return false;
        }
        if (trimmed.equalsIgnoreCase(SHORT_PREFIX) || trimmed.regionMatches(true, 0, SHORT_PREFIX + " ", 0, SHORT_PREFIX.length() + 1)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                String args = trimmed.length() > SHORT_PREFIX.length() ? trimmed.substring(SHORT_PREFIX.length()).trim() : "";
                client.execute(() -> executeFromChat(client, args));
            }
            return false;
        }
        return true;
    }

    private static boolean onCommandMessage(String command) {
        if (command == null || command.isBlank()) {
            return true;
        }
        String trimmed = command.trim();
        if (trimmed.regionMatches(true, 0, "bind ", 0, 5) || trimmed.equalsIgnoreCase("bind")) {
            return true; // Let Brigadier execute
        }
        return true;
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = ClientCommandManager.literal("bind")
                .executes(context -> {
                    executeFromChat(context.getSource().getClient(), "");
                    return 1;
                });

        root.then(ClientCommandManager.argument("functionName", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(suggestFunctions(), builder))
                .executes(context -> {
                    String func = StringArgumentType.getString(context, "functionName");
                    executeFromChat(context.getSource().getClient(), func);
                    return 1;
                })
                .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestKeys(), builder))
                        .executes(context -> {
                            String func = StringArgumentType.getString(context, "functionName");
                            String key = StringArgumentType.getString(context, "key");
                            executeFromChat(context.getSource().getClient(), func + " " + key);
                            return 1;
                        })));

        dispatcher.register(root);
    }

    public static List<String> suggestFunctions() {
        List<String> list = new ArrayList<>();
        list.add("ModernGui");
        list.add("ModernGui2");
        list.add("PremiumGui");
        list.add("list");
        if (FluxVisualsClient.MODULE_MANAGER != null) {
            for (Module m : FluxVisualsClient.MODULE_MANAGER.getModules()) {
                if (m != null && m.getName() != null) {
                    list.add(m.getName().replaceAll("\\s+", ""));
                }
            }
        }
        return list;
    }

    public static List<String> suggestKeys() {
        return COMMON_KEY_SUGGESTIONS;
    }

    public static int parseKey(String name) {
        if (name == null || name.isBlank()) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (KEY_MAP.containsKey(key)) {
            return KEY_MAP.get(key);
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {}

        return GLFW.GLFW_KEY_UNKNOWN;
    }

    public static String getKeyDisplay(int code) {
        return KeybindSetting.getKeyName(code, true);
    }

    public static void executeFromChat(MinecraftClient client, String args) {
        if (args == null || args.isBlank()) {
            showHelp(client);
            return;
        }

        String[] parts = args.trim().split("\\s+");
        String funcName = parts[0];

        if (funcName.equalsIgnoreCase("list") || funcName.equalsIgnoreCase("список")) {
            listAllBinds(client);
            return;
        }

        if (parts.length == 1) {
            // Show current bind of that function
            showSingleBind(client, funcName);
            return;
        }

        String keyStr = parts[1];
        int keyCode = parseKey(keyStr);

        boolean isNone = "none".equalsIgnoreCase(keyStr)
                || "null".equalsIgnoreCase(keyStr)
                || "clear".equalsIgnoreCase(keyStr)
                || "0".equals(keyStr)
                || "unbind".equalsIgnoreCase(keyStr)
                || "delete".equalsIgnoreCase(keyStr)
                || "del".equalsIgnoreCase(keyStr)
                || "remove".equalsIgnoreCase(keyStr)
                || "нету".equalsIgnoreCase(keyStr)
                || "нет".equalsIgnoreCase(keyStr);

        if (keyCode == GLFW.GLFW_KEY_UNKNOWN && !isNone) {
            feedback(client, "\u00a7c[FLUX] \u00a7fНеизвестная клавиша: '\u00a7e" + keyStr + "\u00a7f'. Используй: \u00a77R, RSHIFT, LCTRL, C, V, none и т.д.", Formatting.RED);
            return;
        }

        // Apply bind to the isolated ModernGui2 scaffold.
        if (isModernGui2Alias(funcName)) {
            ModernClickGuiRenderer.MODULE_BINDS.put("ModernGui2", keyCode);
            FluxVisualsClient.requestConfigSave();
            if (isNone) {
                feedback(client, "\u00a7b[FLUX] \u00a7fБинд для \u00a7bModernGui2 \u00a7cудален\u00a7f.", Formatting.GREEN);
            } else {
                feedback(client, "\u00a7b[FLUX] \u00a7fМеню \u00a7bModernGui2 \u00a7fпривязано к \u00a7e" + getKeyDisplay(keyCode) + "\u00a7f.", Formatting.GREEN);
            }
            return;
        }

        // Apply bind to ModernGui
        if (isModernGuiAlias(funcName)) {
            if (FluxVisualsClient.MODULE_MANAGER != null && FluxVisualsClient.MODULE_MANAGER.getMenu() != null) {
                FluxVisualsClient.MODULE_MANAGER.getMenu().setKeyBind(keyCode);
            }
            ModernClickGuiRenderer.MODULE_BINDS.put("Menu", keyCode);
            ModernClickGuiRenderer.MODULE_BINDS.put("ModernGui", keyCode);
            FluxVisualsClient.requestConfigSave();

            if (isNone) {
                feedback(client, "\u00a7b[FLUX] \u00a7fБинд для \u00a7bModernGui \u00a7cудален\u00a7f.", Formatting.GREEN);
            } else {
                feedback(client, "\u00a7b[FLUX] \u00a7fМеню \u00a7bModernGui \u00a7fуспешно привязано к клавише \u00a7e" + getKeyDisplay(keyCode) + "\u00a7f.", Formatting.GREEN);
            }
            return;
        }

        // Apply bind to PremiumGui
        if (isPremiumGuiAlias(funcName)) {
            PremiumClickGuiRenderer.setMenuKey(keyCode);
            ModernClickGuiRenderer.MODULE_BINDS.put("PremiumGui", keyCode);
            FluxVisualsClient.requestConfigSave();

            if (isNone) {
                feedback(client, "\u00a7b[FLUX] \u00a7fБинд для \u00a7bPremiumGui \u00a7cудален\u00a7f.", Formatting.GREEN);
            } else {
                feedback(client, "\u00a7b[FLUX] \u00a7fМеню \u00a7bPremiumGui \u00a7fуспешно привязано к клавише \u00a7e" + getKeyDisplay(keyCode) + "\u00a7f.", Formatting.GREEN);
            }
            return;
        }

        // Check special action binds
        if (funcName.equalsIgnoreCase("zoom")) {
            ModernClickGuiRenderer.MODULE_BINDS.put("action_zoom", keyCode);
            if (FluxVisualsClient.MODULE_MANAGER != null) {
                FluxVisualsClient.MODULE_MANAGER.getZoom().setKeyBind(keyCode);
            }
            FluxVisualsClient.requestConfigSave();
            reportSuccess(client, "Zoom", keyCode, isNone);
            return;
        }

        if (funcName.equalsIgnoreCase("autoswap") || funcName.equalsIgnoreCase("swap") || funcName.equalsIgnoreCase("itemswap")) {
            ModernClickGuiRenderer.MODULE_BINDS.put("action_autoswap", keyCode);
            if (FluxVisualsClient.MODULE_MANAGER != null) {
                FluxVisualsClient.MODULE_MANAGER.getAutoSwap().setKeyBind(keyCode);
            }
            FluxVisualsClient.requestConfigSave();
            reportSuccess(client, "AutoSwap", keyCode, isNone);
            return;
        }

        if (funcName.equalsIgnoreCase("elytraswap")) {
            ModernClickGuiRenderer.MODULE_BINDS.put("action_elytraswap", keyCode);
            if (FluxVisualsClient.MODULE_MANAGER != null) {
                FluxVisualsClient.MODULE_MANAGER.getElytraSwap().setKeyBind(keyCode);
            }
            FluxVisualsClient.requestConfigSave();
            reportSuccess(client, "ElytraSwap", keyCode, isNone);
            return;
        }

        if (funcName.equalsIgnoreCase("freelook")) {
            ModernClickGuiRenderer.MODULE_BINDS.put("action_freelook", keyCode);
            if (FluxVisualsClient.MODULE_MANAGER != null) {
                FluxVisualsClient.MODULE_MANAGER.getFreeLook().setKeyBind(keyCode);
            }
            FluxVisualsClient.requestConfigSave();
            reportSuccess(client, "FreeLook", keyCode, isNone);
            return;
        }

        // Find module by name (fuzzy case-insensitive, strip spaces)
        Module targetModule = findModule(funcName);
        if (targetModule == null) {
            feedback(client, "\u00a7c[FLUX] \u00a7fФункция '\u00a7e" + funcName + "\u00a7f' не найдена! Используй \u00a77.bind list\u00a7f для просмотра.", Formatting.RED);
            return;
        }

        String canonicalName = targetModule.getName();
        if (isNone) {
            ModernClickGuiRenderer.MODULE_BINDS.remove(canonicalName);
            ModernClickGuiRenderer.MODULE_BINDS.put(canonicalName, GLFW.GLFW_KEY_UNKNOWN);
        } else {
            ModernClickGuiRenderer.MODULE_BINDS.put(canonicalName, keyCode);
        }
        FluxVisualsClient.requestConfigSave();

        reportSuccess(client, canonicalName, keyCode, isNone);
    }

    private static void reportSuccess(MinecraftClient client, String targetName, int keyCode, boolean isNone) {
        if (isNone) {
            feedback(client, "\u00a7b[FLUX] \u00a7fБинд для функции \u00a7b" + targetName + " \u00a7cудален\u00a7f.", Formatting.GREEN);
        } else {
            feedback(client, "\u00a7b[FLUX] \u00a7fФункция \u00a7b" + targetName + " \u00a7fуспешно привязана к \u00a7e" + getKeyDisplay(keyCode) + "\u00a7f.", Formatting.GREEN);
        }
    }

    private static void showSingleBind(MinecraftClient client, String funcName) {
        if (isModernGui2Alias(funcName)) {
            int key = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("ModernGui2", GLFW.GLFW_KEY_UNKNOWN);
            feedback(client, "\u00a7b[FLUX] \u00a7fБинд \u00a7bModernGui2\u00a7f: \u00a7e" + getKeyDisplay(key) + " \u00a77(Используй .bind ModernGui2 <key|none>)", Formatting.GRAY);
            return;
        }
        if (isModernGuiAlias(funcName)) {
            int key = FluxVisualsClient.MODULE_MANAGER != null && FluxVisualsClient.MODULE_MANAGER.getMenu() != null
                    ? FluxVisualsClient.MODULE_MANAGER.getMenu().getKeyBind() : GLFW.GLFW_KEY_RIGHT_SHIFT;
            feedback(client, "\u00a7b[FLUX] \u00a7fБинд \u00a7bModernGui\u00a7f: \u00a7e" + getKeyDisplay(key) + " \u00a77(Используй .bind ModernGui <key|none>)", Formatting.GRAY);
            return;
        }
        if (isPremiumGuiAlias(funcName)) {
            int key = PremiumClickGuiRenderer.getMenuKey();
            feedback(client, "\u00a7b[FLUX] \u00a7fБинд \u00a7bPremiumGui\u00a7f: \u00a7e" + getKeyDisplay(key) + " \u00a77(Используй .bind PremiumGui <key|none>)", Formatting.GRAY);
            return;
        }

        Module mod = findModule(funcName);
        if (mod != null) {
            int key = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(mod.getName(), GLFW.GLFW_KEY_UNKNOWN);
            feedback(client, "\u00a7b[FLUX] \u00a7fБинд \u00a7b" + mod.getName() + "\u00a7f: \u00a7e" + getKeyDisplay(key) + " \u00a77(Используй .bind " + mod.getName() + " <key|none>)", Formatting.GRAY);
            return;
        }

        feedback(client, "\u00a7c[FLUX] \u00a7fФункция '\u00a7e" + funcName + "\u00a7f' не найдена. Используй \u00a77.bind list\u00a7f.", Formatting.RED);
    }

    private static void listAllBinds(MinecraftClient client) {
        feedback(client, "\u00a7b[FLUX] \u00a7f=== \u00a7bСписок активных биндов \u00a7f===", Formatting.AQUA);
        int modernKey = FluxVisualsClient.MODULE_MANAGER != null && FluxVisualsClient.MODULE_MANAGER.getMenu() != null
                ? FluxVisualsClient.MODULE_MANAGER.getMenu().getKeyBind() : GLFW.GLFW_KEY_RIGHT_SHIFT;
        feedback(client, "\u00a77• \u00a7bModernGui \u00a78-> \u00a7e" + getKeyDisplay(modernKey), Formatting.WHITE);
        int modernGui2Key = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("ModernGui2", GLFW.GLFW_KEY_UNKNOWN);
        feedback(client, "\u00a77• \u00a7bModernGui2 \u00a78-> \u00a7e" + getKeyDisplay(modernGui2Key), Formatting.WHITE);

        int premiumKey = PremiumClickGuiRenderer.getMenuKey();
        feedback(client, "\u00a77• \u00a7bPremiumGui \u00a78-> \u00a7e" + getKeyDisplay(premiumKey), Formatting.WHITE);

        int count = 0;
        if (FluxVisualsClient.MODULE_MANAGER != null) {
            for (Module mod : FluxVisualsClient.MODULE_MANAGER.getModules()) {
                if ("Menu".equalsIgnoreCase(mod.getName())) continue;
                int key = ModernClickGuiRenderer.MODULE_BINDS.getOrDefault(mod.getName(), GLFW.GLFW_KEY_UNKNOWN);
                if (key != GLFW.GLFW_KEY_UNKNOWN && key != 0) {
                    feedback(client, "\u00a77• \u00a7b" + mod.getName() + " \u00a78-> \u00a7e" + getKeyDisplay(key), Formatting.WHITE);
                    count++;
                }
            }
        }
        if (count == 0) {
            feedback(client, "\u00a77(Других привязанных модулей пока нет. Пример: \u00a7b.bind AimBot R\u00a77)", Formatting.GRAY);
        }
    }

    private static void showHelp(MinecraftClient client) {
        feedback(client, "\u00a7b[FLUX] \u00a7fКоманда привязки клавиш (Keybinds):", Formatting.AQUA);
        feedback(client, "\u00a77• \u00a7b.bind <функция|ModernGui|ModernGui2|PremiumGui> <клавиша> \u00a78- \u00a7fустановить бинд", Formatting.WHITE);
        feedback(client, "\u00a77• \u00a7b.bind <функция|ModernGui|ModernGui2|PremiumGui> none \u00a78- \u00a7fудалить бинд", Formatting.WHITE);
        feedback(client, "\u00a77• \u00a7b.bind list \u00a78- \u00a7fпоказать все активные бинды", Formatting.WHITE);
        feedback(client, "\u00a77Примеры: \u00a7e.bind AimBot R \u00a77| \u00a7e.bind ModernGui2 RSHIFT \u00a77| \u00a7e.bind Fly none", Formatting.GRAY);
    }

    private static boolean isModernGui2Alias(String name) {
        if (name == null) return false;
        String clean = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);
        return clean.equals("moderngui2") || clean.equals("modern2") || clean.equals("меню2") || clean.equals("модерн2");
    }

    private static boolean isModernGuiAlias(String name) {
        if (name == null) return false;
        String clean = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);
        return clean.equals("moderngui") || clean.equals("modernclickgui") || clean.equals("modern") || clean.equals("меню") || clean.equals("модерн");
    }

    private static boolean isPremiumGuiAlias(String name) {
        if (name == null) return false;
        String clean = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);
        return clean.equals("premiumgui") || clean.equals("premiumclickgui") || clean.equals("premium") || clean.equals("clickgui") || clean.equals("премиум");
    }

    private static Module findModule(String name) {
        if (name == null || FluxVisualsClient.MODULE_MANAGER == null) return null;
        String clean = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);

        for (Module mod : FluxVisualsClient.MODULE_MANAGER.getModules()) {
            if (mod.getName().equalsIgnoreCase(name)) {
                return mod;
            }
            String modClean = mod.getName().replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);
            if (modClean.equals(clean)) {
                return mod;
            }
        }

        // Fuzzy partial match
        for (Module mod : FluxVisualsClient.MODULE_MANAGER.getModules()) {
            String modClean = mod.getName().replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "").toLowerCase(Locale.ROOT);
            if (modClean.startsWith(clean) || clean.startsWith(modClean)) {
                return mod;
            }
        }

        return null;
    }

    private static void feedback(MinecraftClient client, String message, Formatting fallbackColor) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.sendMessage(Text.literal(message), false);
    }
}
