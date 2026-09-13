package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.multibot.BotSession;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import dev.fuga.fluxvisuals.gui.ClickGuiScreen;
import dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen;
import dev.fuga.fluxvisuals.gui.ClientGuiProtection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;

/** Repeats the storage resale button without leaving the storage GUI. */
public final class AutoResellAFK extends Module {
    private static final long INTERVAL_MIN_MS = 60_000L;
    private static final long INTERVAL_MAX_MS = 65_000L;
    private static final long ACTION_DELAY_MIN_MS = 250L;
    private static final long ACTION_DELAY_MAX_MS = 500L;
    private static final long OPEN_TIMEOUT_MS = 3_000L;
    private static final long DEFAULT_CHAT_INTERVAL_MS = 60_000L;
    private static final long MIN_CHAT_INTERVAL_MS = 5_000L;
    private static final long MAX_CHAT_INTERVAL_MS = 1_000_000L;

    private Step step = Step.IDLE;
    private long nextActionAt;
    private long deadline;
    private boolean chatEnabled;
    private String chatMessage = "";
    private long chatIntervalMs = DEFAULT_CHAT_INTERVAL_MS;
    private long nextChatAt;
    private boolean sellPurchasedSwords;
    private String sellPrice = "";
    private long settingsVersion;
    private long copiedSettingsVersion = Long.MIN_VALUE;
    private int ahCommandAttempts;
    private boolean serverCommandsBlocked;
    private static final String AFK_COMMAND_BLOCKED_MESSAGE =
            "\u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u043d\u0435 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0432 \u0440\u0435\u0436\u0438\u043c\u0435 \u0430\u0444\u043a";

    public AutoResellAFK() {
        super("AutoResellAFK", "Keeps /ah storage open and resells every 60 seconds.", ModuleCategory.UTILS);
    }

    /** Copies persistent settings without copying timers or the current action step. */
    public void copySettingsFrom(AutoResellAFK source) {
        if (source == null || source == this || copiedSettingsVersion == source.settingsVersion) {
            return;
        }
        chatEnabled = source.chatEnabled;
        chatMessage = source.chatMessage;
        chatIntervalMs = source.chatIntervalMs;
        sellPurchasedSwords = source.sellPurchasedSwords;
        sellPrice = source.sellPrice;
        copiedSettingsVersion = source.settingsVersion;
    }

    public boolean isChatEnabled() { return chatEnabled; }
    public String getChatMessage() { return chatMessage; }
    public long getChatIntervalMs() { return chatIntervalMs; }
    public boolean isSellPurchasedSwords() { return sellPurchasedSwords; }
    public String getSellPrice() { return sellPrice; }

    /** Returns true only for this module/session when the AFK restriction appears. */
    public boolean onChatMessage(String message) {
        if (!isEnabled() || message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("здесь нет команд")) {
            serverCommandsBlocked = true;
            step = Step.OPEN_AH;
            ahCommandAttempts = 0;
            nextActionAt = Long.MAX_VALUE;
            BotDebug.info("AUTO_RESELL_SERVER_BLOCKED", MultiBotManager.currentContextSession(),
                    "reason=hub_or_commands_unavailable");
        } else if (normalized.contains("после входа на режим")
                || normalized.contains("предметы успешно перевыставлены")
                || normalized.contains("можете переставлять предметы")) {
            if (serverCommandsBlocked) {
                serverCommandsBlocked = false;
                restartNow();
                BotDebug.info("AUTO_RESELL_SERVER_READY", MultiBotManager.currentContextSession(),
                        "workflow_restarted=true");
            }
        }
        return normalized.contains(AFK_COMMAND_BLOCKED_MESSAGE)
                || normalized.contains("\u0440\u0435\u0436\u0438\u043c\u0435 \u0430\u0444\u043a")
                || normalized.contains("\u0440\u0435\u0436\u0438\u043c\u0435 afk");
    }

    public void setChatEnabled(boolean value) {
        if (chatEnabled != value) {
            chatEnabled = value;
            settingsVersion++;
            save();
        }
    }
    public void setChatMessage(String value) {
        String next = value == null ? "" : value.trim();
        if (!chatMessage.equals(next)) {
            chatMessage = next;
            settingsVersion++;
            save();
        }
    }
    public void setChatIntervalMs(long value) {
        long next = Math.max(MIN_CHAT_INTERVAL_MS, Math.min(MAX_CHAT_INTERVAL_MS, value));
        if (chatIntervalMs != next) {
            chatIntervalMs = next;
            settingsVersion++;
            save();
        }
    }
    public void setSellPurchasedSwords(boolean value) {
        if (sellPurchasedSwords != value) {
            sellPurchasedSwords = value;
            settingsVersion++;
            save();
        }
    }
    public void setSellPrice(String value) {
        String next = value == null ? "" : value.trim();
        if (!sellPrice.equals(next)) {
            sellPrice = next;
            settingsVersion++;
            save();
        }
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        serverCommandsBlocked = false;
        AutoBuy ab = dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                dev.fuga.fluxvisuals.FluxVisualsClient.MODULE_MANAGER.getAutoBuy());
        if (ab != null && ab.isEnabled()) {
            ab.setEnabledSilently(false);
        }
        restartNow();
        nextChatAt = System.currentTimeMillis() + chatIntervalMs;
    }

    /** Restarts this session's workflow without closing an already opened GUI. */
    public void restartNow() {
        long now = System.currentTimeMillis();
        step = Step.OPEN_AH;
        ahCommandAttempts = 0;
        nextActionAt = now + 150L;
        deadline = now + OPEN_TIMEOUT_MS;
    }

    /** Resumes automation after an explicit /an mode switch. */
    public void resumeAfterModeSwitch() {
        serverCommandsBlocked = false;
        restartNow();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        step = Step.IDLE;
        nextActionAt = 0L;
        deadline = 0L;
        nextChatAt = 0L;
        ahCommandAttempts = 0;
        serverCommandsBlocked = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null
                || client.interactionManager == null || client.getNetworkHandler() == null) {
            return;
        }
        // Never let background resale automation close the client's own
        // configuration screens. Resume the workflow after the GUI is closed.
        if (isClientGui(client)) {
            return;
        }
        if (serverCommandsBlocked) {
            return;
        }
        long now = System.currentTimeMillis();
        BotSession debugSession = MultiBotManager.currentContextSession();
        if (chatEnabled && !chatMessage.isBlank() && now >= nextChatAt) {
            client.getNetworkHandler().sendChatMessage(chatMessage);
            nextChatAt = now + chatIntervalMs;
        }

        // If storage screen is not open (or was closed unexpectedly / during switch), immediately reopen /ah and go to storage
        if (!isStorageLikeScreen(client)) {
            if (step == Step.WAIT_INTERVAL || step == Step.WAIT_RESELL) {
                step = Step.OPEN_AH;
                nextActionAt = now + 150L;
                deadline = now + OPEN_TIMEOUT_MS;
            }
        }

        if (now < nextActionAt) {
            return;
        }

        if (step == Step.OPEN_AH) {
            if (isStorageLikeScreen(client)) {
                step = Step.WAIT_RESELL;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            if (isAuctionLikeScreen(client)) {
                step = Step.OPEN_MENU;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            if (client.currentScreen == null) {
                client.getNetworkHandler().sendChatCommand("ah");
                ahCommandAttempts++;
                BotDebug.info("AUTO_RESELL_AH_SENT", debugSession,
                        "step=OPEN_AH, attempt=" + ahCommandAttempts);
                step = Step.OPEN_MENU;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            if (now >= deadline) {
                closeCurrentScreen(client);
                nextActionAt = now + 150L;
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            step = Step.OPEN_MENU;
        }

        if (step == Step.OPEN_MENU) {
            if (isStorageLikeScreen(client)) {
                step = Step.WAIT_RESELL;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            if (clickNamed(client, "хран", "storage", "мои товар", "мои лот", "мои предмет", "склад", "наград", "ваши предмет", "ваши лот", "ваши товар", "личн")) {
                step = Step.WAIT_RESELL;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
                return;
            }
            if (now >= deadline) {
                closeCurrentScreen(client);
                client.getNetworkHandler().sendChatCommand("ah");
                ahCommandAttempts++;
                BotDebug.warn("AUTO_RESELL_AH_RETRY", debugSession,
                        "attempt=" + ahCommandAttempts);
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
            } else {
                nextActionAt = now + 150L;
            }
            return;
        }

        if (step == Step.WAIT_RESELL) {
            if (clickNamed(client, "перевыстав", "resell", "relist", "переставить", "заново", "обновить все")) {
                BotDebug.info("AUTO_RESELL_CLICK", debugSession, "step=WAIT_RESELL");
                step = Step.WAIT_INTERVAL;
                nextActionAt = now + randomDelay(INTERVAL_MIN_MS, INTERVAL_MAX_MS);
                deadline = 0L;
                return;
            }
            if (now >= deadline) {
                step = Step.OPEN_AH;
                nextActionAt = now + 150L;
            } else {
                nextActionAt = now + 200L;
            }
            return;
        }

        if (step == Step.WAIT_INTERVAL) {
            if (isStorageLikeScreen(client)) {
                step = Step.WAIT_RESELL;
                nextActionAt = now + randomDelay(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
                deadline = now + OPEN_TIMEOUT_MS;
            } else {
                step = Step.OPEN_AH;
                nextActionAt = now + 150L;
                deadline = now + OPEN_TIMEOUT_MS;
            }
        }
    }

    private static void closeCurrentScreen(MinecraftClient client) {
        if (client == null) return;
        if (ClientGuiProtection.isOpen(client)) return;
        if (client.player != null) {
            client.player.closeHandledScreen();
        }
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
    }

    private static boolean isClientGui(MinecraftClient client) {
        return client != null && (client.currentScreen instanceof ClickGuiScreen
                || client.currentScreen instanceof ModernClickGuiScreen);
    }

    private static boolean isStorageLikeScreen(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return containsAny(title, "хран", "storage", "склад", "мои товар", "мои лот", "мои предмет", "ваши предмет", "ваши лот", "ваши товар", "выдач", "личн");
    }

    private static boolean isAuctionLikeScreen(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return containsAny(title, "аук", "auction", "ah", "рынок", "торгов");
    }

    private static boolean clickNamed(MinecraftClient client, String... names) {
        if (!(client.currentScreen instanceof HandledScreen<?> screenSnapshot) || client.player == null
                || client.interactionManager == null) {
            return false;
        }

        ScreenHandler handlerSnapshot = screenSnapshot.getScreenHandler();
        int syncIdSnapshot = handlerSnapshot.syncId;
        if (client.player.currentScreenHandler != handlerSnapshot) {
            return false;
        }

        Slot targetSlot = null;
        for (Slot slot : handlerSnapshot.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (containsAny(name, names)) {
                targetSlot = slot;
                break;
            }
        }
        if (targetSlot == null) {
            return false;
        }

        if (client.currentScreen != screenSnapshot
                || screenSnapshot.getScreenHandler() != handlerSnapshot
                || client.player.currentScreenHandler != handlerSnapshot
                || handlerSnapshot.syncId != syncIdSnapshot) {
            return false;
        }

        client.interactionManager.clickSlot(syncIdSnapshot,
                targetSlot.id, 0, SlotActionType.PICKUP, client.player);
        return true;
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private enum Step {
        IDLE, OPEN_AH, OPEN_MENU, WAIT_RESELL, WAIT_INTERVAL
    }

    private static long randomDelay(long min, long max) {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    private void save() {
        if (dev.fuga.fluxvisuals.FluxVisualsClient.CONFIG_MANAGER != null) {
            dev.fuga.fluxvisuals.FluxVisualsClient.requestConfigSave();
        }
    }
}
