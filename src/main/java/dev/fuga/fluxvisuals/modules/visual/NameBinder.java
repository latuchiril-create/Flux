package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class NameBinder extends Module {
    private final List<Entry> entries = new ArrayList<>();

    public NameBinder() {
        super("NameBinder", "Sends /name for saved text by key bind.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.getNetworkHandler() == null
                || client.currentScreen != null || client.getWindow() == null) {
            return;
        }
        long window = client.getWindow().getHandle();
        for (Entry entry : entries) {
            if (entry.bind == GLFW.GLFW_KEY_UNKNOWN) {
                continue;
            }
            boolean down = GLFW.glfwGetKey(window, entry.bind) == GLFW.GLFW_PRESS;
            if (down && !entry.pressed && !entry.text.isBlank()) {
                client.getNetworkHandler().sendChatCommand("name " + entry.text);
            }
            entry.pressed = down;
        }
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public boolean addEntry(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        entries.add(new Entry(value.trim(), GLFW.GLFW_KEY_UNKNOWN, false));
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        FluxVisualsClient.requestConfigSave();
    }

    public void updateBind(int index, int bind) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry entry = entries.get(index);
        entries.set(index, new Entry(entry.text, bind, false));
        FluxVisualsClient.requestConfigSave();
    }

    public void setEntries(List<Entry> values) {
        entries.clear();
        if (values != null) {
            for (Entry entry : values) {
                if (entry == null || entry.text == null || entry.text.isBlank()) {
                    continue;
                }
                entries.add(new Entry(entry.text.trim(), entry.bind, false));
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public static final class Entry {
        public final String text;
        public final int bind;
        public boolean pressed;

        public Entry(String text, int bind, boolean pressed) {
            this.text = text;
            this.bind = bind;
            this.pressed = pressed;
        }
    }
}
