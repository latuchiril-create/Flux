package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class Macros extends Module {
    private final List<Entry> entries = new ArrayList<>();

    public Macros() {
        super("Macros", "Sends saved chat text or command by key bind.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.getNetworkHandler() == null
                || client.getWindow() == null) {
            return;
        }
        long window = client.getWindow().getHandle();
        if (client.currentScreen instanceof ChatScreen) {
            // Keep the physical key state latched while the user is typing,
            // so closing chat cannot fire a macro on the key still held down.
            for (Entry entry : entries) {
                if (entry.keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                    entry.pressed = GLFW.glfwGetKey(window, entry.keyCode) == GLFW.GLFW_PRESS;
                }
            }
            return;
        }
        if (client.currentScreen != null) {
            for (Entry entry : entries) {
                entry.pressed = false;
            }
            return;
        }
        for (Entry entry : entries) {
            if (entry.keyCode == GLFW.GLFW_KEY_UNKNOWN) {
                continue;
            }
            boolean down = GLFW.glfwGetKey(window, entry.keyCode) == GLFW.GLFW_PRESS;
            if (down && !entry.pressed) {
                sendText(client, entry.text);
            }
            entry.pressed = down;
        }
    }

    public List<EntryView> getEntries() {
        List<EntryView> out = new ArrayList<>();
        for (Entry entry : entries) {
            out.add(new EntryView(entry.text, entry.keyCode));
        }
        return out;
    }

    public void setEntries(List<EntryView> values) {
        entries.clear();
        if (values != null) {
            for (EntryView view : values) {
                if (view == null || view.text() == null || view.text().isBlank()) {
                    continue;
                }
                entries.add(new Entry(view.text().trim(), view.keyCode(), false));
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean addEntry(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        entries.add(new Entry(text.trim(), GLFW.GLFW_KEY_UNKNOWN, false));
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public void updateBind(int index, int keyCode) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry current = entries.get(index);
        entries.set(index, new Entry(current.text, keyCode, false));
        FluxVisualsClient.requestConfigSave();
    }

    public void updateText(int index, String text) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry current = entries.get(index);
        entries.set(index, new Entry(text != null ? text : "", current.keyCode, false));
        FluxVisualsClient.requestConfigSave();
    }

    public void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        FluxVisualsClient.requestConfigSave();
    }

    private static void sendText(MinecraftClient client, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (text.startsWith("/")) {
            String command = text.substring(1).trim();
            if (!command.isEmpty()) {
                client.getNetworkHandler().sendChatCommand(command);
            }
        } else {
            client.getNetworkHandler().sendChatMessage(text);
        }
    }

    public record EntryView(String text, int keyCode) {
    }

    private static final class Entry {
        private final String text;
        private final int keyCode;
        private boolean pressed;

        private Entry(String text, int keyCode, boolean pressed) {
            this.text = text;
            this.keyCode = keyCode;
            this.pressed = pressed;
        }
    }
}
