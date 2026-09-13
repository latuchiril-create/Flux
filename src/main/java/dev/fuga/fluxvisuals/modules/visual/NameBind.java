package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class NameBind extends Module {
    private static final long AUTO_BUY_PAUSE_MS = 650L;
    private final List<Entry> entries = new ArrayList<>();

    public NameBind() {
        super("NameBind", "Sends /name for saved text by key bind.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.getNetworkHandler() == null
                || client.getWindow() == null) {
            return;
        }
        long window = client.getWindow().getHandle();
        for (Entry entry : entries) {
            if (entry.keyCode == GLFW.GLFW_KEY_UNKNOWN) {
                continue;
            }
            boolean down = GLFW.glfwGetKey(window, entry.keyCode) == GLFW.GLFW_PRESS;
            if (down && !entry.pressed) {
                String text = entry.text == null ? "" : entry.text.trim();
                if (!text.isEmpty()) {
                    sendName(client, text);
                }
            }
            entry.pressed = down;
        }
    }

    public List<EntryView> getEntries() {
        List<EntryView> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(new EntryView(e.text, e.keyCode));
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

    public boolean addEntry(String text, int keyCode) {
        if (text == null || text.isBlank()) {
            return false;
        }
        entries.add(new Entry(text.trim(), keyCode, false));
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public boolean addEntry(String text) {
        return addEntry(text, GLFW.GLFW_KEY_UNKNOWN);
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

    public void sendEntry(int index) {
        if (!isEnabled() || index < 0 || index >= entries.size()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        String text = entries.get(index).text == null ? "" : entries.get(index).text.trim();
        if (!text.isEmpty()) {
            sendName(client, text);
        }
    }

    public void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        FluxVisualsClient.requestConfigSave();
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

    private static void sendName(MinecraftClient client, String text) {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        if (client.currentScreen != null && !autoBuy.isScanningAuction()) {
            return;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()
                || FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()
                || !autoBuy.tryPauseForExternalAction(client, AUTO_BUY_PAUSE_MS)
                || client.currentScreen != null) {
            return;
        }
        client.getNetworkHandler().sendChatCommand("name " + text);
    }
}
