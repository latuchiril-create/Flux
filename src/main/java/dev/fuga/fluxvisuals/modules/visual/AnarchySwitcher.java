package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public final class AnarchySwitcher extends Module {
    private static final float MIN_DELAY_SECONDS = 0.25F;
    private static final float MAX_DELAY_SECONDS = 300.0F;
    private final List<String> anarchyIds = new ArrayList<>();
    private float delaySeconds = 2.0F;
    private int cursor;
    private long nextSwitchAtMs;
    private long busyUntilMs;
    private boolean adEnabled;
    private String adText = "";

    public AnarchySwitcher() {
        super("AnarchySwitcher", "Cycles /an commands by timer.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (busyUntilMs > 0L) {
            if (now < busyUntilMs) {
                return;
            }
            busyUntilMs = 0L;
            sendAd(client);
            return;
        }
        if (anarchyIds.isEmpty()) {
            return;
        }

        if (nextSwitchAtMs <= 0L) {
            scheduleNext(now);
            return;
        }
        if (now < nextSwitchAtMs) {
            return;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()) {
            return;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellWorking()
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isLeaveReportWorking()) {
            return;
        }
        if (!FluxVisualsClient.MODULE_MANAGER.getAutoBuy().tryPauseForExternalAction(client, 0L)) {
            return;
        }
        String id = anarchyIds.get(cursor % anarchyIds.size());
        cursor = (cursor + 1) % anarchyIds.size();
        client.getNetworkHandler().sendChatCommand("an" + id);
        busyUntilMs = AuctionAccessGuard.blockAfterAnarchyJoin(now);
        scheduleNext(busyUntilMs);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        scheduleNext();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        nextSwitchAtMs = 0L;
        busyUntilMs = 0L;
    }

    public boolean isWorking() {
        return isEnabled() && busyUntilMs > 0L;
    }

    public List<String> getAnarchyIds() {
        return List.copyOf(anarchyIds);
    }

    public void setAnarchyIds(List<String> values) {
        anarchyIds.clear();
        if (values != null) {
            for (String value : values) {
                addAnarchyId(value);
            }
        }
        cursor = 0;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean addAnarchyId(String value) {
        String cleaned = sanitize(value);
        if (cleaned.isEmpty() || anarchyIds.contains(cleaned)) {
            return false;
        }
        anarchyIds.add(cleaned);
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public void removeAnarchyId(String value) {
        if (anarchyIds.remove(sanitize(value))) {
            cursor = 0;
            FluxVisualsClient.requestConfigSave();
        }
    }

    public float getDelaySeconds() {
        return delaySeconds;
    }

    public float getCooldownSeconds() {
        return getDelaySeconds();
    }

    public void setDelaySeconds(float delaySeconds) {
        float next = Math.max(MIN_DELAY_SECONDS, Math.min(MAX_DELAY_SECONDS, delaySeconds));
        if (Math.abs(this.delaySeconds - next) < 0.001F) {
            return;
        }
        this.delaySeconds = next;
        scheduleNext();
        FluxVisualsClient.requestConfigSave();
    }

    public void setCooldownSeconds(float seconds) {
        setDelaySeconds(seconds);
    }

    public boolean isAdEnabled() {
        return adEnabled;
    }

    public void setAdEnabled(boolean adEnabled) {
        if (this.adEnabled == adEnabled) {
            return;
        }
        this.adEnabled = adEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public String getAdText() {
        return adText;
    }

    public void setAdText(String adText) {
        String next = adText == null ? "" : adText.trim();
        if (this.adText.equals(next)) {
            return;
        }
        this.adText = next;
        FluxVisualsClient.requestConfigSave();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "").trim();
    }

    private void scheduleNext() {
        scheduleNext(System.currentTimeMillis());
    }

    private void scheduleNext(long now) {
        nextSwitchAtMs = now + Math.max(250L, Math.round(delaySeconds * 1000.0F));
    }

    private void sendAd(MinecraftClient client) {
        String text = adText == null ? "" : adText.trim();
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
}
