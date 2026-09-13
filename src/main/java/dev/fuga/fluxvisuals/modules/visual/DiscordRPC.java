package dev.fuga.fluxvisuals.modules.visual;

import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.LogLevel;
import de.jcm.discordgamesdk.Result;
import de.jcm.discordgamesdk.activity.Activity;
import de.jcm.discordgamesdk.activity.ActivityButton;
import de.jcm.discordgamesdk.activity.ActivityButtonsMode;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import java.time.Instant;
import net.minecraft.client.MinecraftClient;

/** Discord Rich Presence integration backed by discord-game-sdk4j. */
public final class DiscordRPC extends Module {
    private static final long APPLICATION_ID = 1539281573143380059L;
    private static final String AUTOBUY_URL = "https://funpay.com/users/11601920/";
    private static final String TELEGRAM_URL = "https://t.me/fugachenal";
    private static final long RETRY_DELAY_MS = 10_000L;
    private static final long UPDATE_DELAY_MS = 5_000L;

    private Core core;
    private CreateParams params;
    private long nextRetryAt;
    private long nextUpdateAt;
    private Instant startedAt = Instant.now();
    private String lastSignature = "";
    private boolean customButtonEnabled;
    private String customButtonLabel = "";
    private String customButtonUrl = "";

    public DiscordRPC() {
        super("DiscordRPC", "Discord Rich Presence", ModuleCategory.UTILS);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        closeCore();
        nextRetryAt = 0L;
        nextUpdateAt = 0L;
        startedAt = Instant.now();
        lastSignature = "";
        BotDebug.info("DISCORD_RPC_ENABLE", null, "application=" + APPLICATION_ID);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        closeCore();
        BotDebug.info("DISCORD_RPC_DISABLE", null, "application=" + APPLICATION_ID);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((core == null || !core.isOpen()) && now >= nextRetryAt) {
            connect(now);
        }
        Core activeCore = core;
        if (activeCore == null || !activeCore.isOpen()) {
            return;
        }
        try {
            // isDiscordRunning() relies on File.exists() for a Windows named
            // pipe and can be false even while the SDK channel is usable.
            // Let the SDK callback report the real transport result instead.
            activeCore.runCallbacks();
            if (now >= nextUpdateAt) {
                updateActivity(activeCore);
                nextUpdateAt = now + UPDATE_DELAY_MS;
            }
        } catch (Throwable error) {
            // The SDK is optional at runtime; a closed Discord process must
            // never affect the Minecraft client tick.
            BotDebug.error("DISCORD_RPC_ERROR", null, "tick failed", error);
            closeCore();
            nextRetryAt = now + RETRY_DELAY_MS;
        }
    }

    private void connect(long now) {
        closeCore();
        nextRetryAt = now + RETRY_DELAY_MS;
        try {
            params = new CreateParams();
            params.setClientID(APPLICATION_ID);
            params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD,
                    CreateParams.Flags.SUPPRESS_EXCEPTIONS);
            core = new Core(params);
            Core activeCore = core;
            activeCore.setLogHook(LogLevel.ERROR,
                    (level, message) -> BotDebug.warn("DISCORD_RPC_ERROR", null,
                            "sdk=" + level + ": " + message));
            startedAt = Instant.now();
            lastSignature = "";
            BotDebug.info("DISCORD_RPC_CONNECT", null,
                    "open=" + activeCore.isOpen() + ", discordRunning=" + activeCore.isDiscordRunning());
        } catch (Throwable error) {
            BotDebug.error("DISCORD_RPC_ERROR", null, "connect failed", error);
            closeCore();
        }
    }

    private void updateActivity(Core activeCore) {
        int count = FluxVisualsClient.MULTI_BOT_MANAGER.getSessionCount();
        String state = "В игре";
        MinecraftClient client = MinecraftClient.getInstance();
        String address = client.getCurrentServerEntry() == null
                ? "меню"
                : client.getCurrentServerEntry().address;
        String details = "Играет на " + address + " | Ботов: " + Math.max(0, count - 1);
        String button1Label = "Купить автобай";
        String button1Url = AUTOBUY_URL;
        String button2Label = "ТГК";
        String button2Url = TELEGRAM_URL;
        // Кастомная кнопка ЗАМЕНЯЕТ первую ("Купить автобай"), а не добавляется третьей.
        // Раньше первая кнопка была захардкожена и никогда не менялась + Discord SDK
        // принимает максимум 2 кнопки, поэтому кастом визуально "не применялся".
        if (customButtonEnabled) {
            String normalizedLabel = customButtonLabel == null ? "" : customButtonLabel.trim();
            String normalizedUrl = normalizeUrl(customButtonUrl);
            if (!normalizedLabel.isBlank() && isValidUrl(normalizedUrl)) {
                button1Label = normalizedLabel;
                button1Url = normalizedUrl;
            }
        }
        String signature = details + "|" + state + "|" + button1Label + "|" + button1Url
                + "|" + button2Label + "|" + button2Url;
        if (signature.equals(lastSignature) && System.currentTimeMillis() < nextUpdateAt) {
            return;
        }
        try (Activity activity = new Activity()) {
            activity.setDetails(details);
            activity.setState(state);
            activity.timestamps().setStart(startedAt);
            activity.setActivityButtonsMode(ActivityButtonsMode.BUTTONS);
            activity.addButton(new ActivityButton(button1Label, button1Url));
            activity.addButton(new ActivityButton(button2Label, button2Url));
            final String sentSignature = signature;
            activeCore.activityManager().updateActivity(activity, result -> {
                if (activeCore != core) {
                    return;
                }
                if (result == Result.OK) {
                    lastSignature = sentSignature;
                    BotDebug.info("DISCORD_RPC_ACTIVITY", null,
                            "result=OK, state=" + state);
                } else {
                    BotDebug.error("DISCORD_RPC_ERROR", null,
                            "activity result=" + result, null);
                    closeCore();
                    nextRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
                }
            });
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        // Юзеры часто вводят "discord.gg/xxx" или "t.me/xxx" без схемы —
        // раньше такой URL молча отбрасывался и кнопка откатывалась на дефолт.
        if (!trimmed.contains(" ") && trimmed.contains(".") && !trimmed.contains("://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private static boolean isValidUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    public boolean isCustomButtonEnabled() { return customButtonEnabled; }
    public void setCustomButtonEnabled(boolean enabled) {
        customButtonEnabled = enabled;
        requestRefresh();
        FluxVisualsClient.requestConfigSave();
    }
    public String getCustomButtonLabel() { return customButtonLabel; }
    public void setCustomButtonLabel(String label) {
        String cleaned = label == null ? "" : label.trim();
        customButtonLabel = cleaned.substring(0, Math.min(32, cleaned.length()));
        requestRefresh();
        FluxVisualsClient.requestConfigSave();
    }
    public String getCustomButtonUrl() { return customButtonUrl; }
    public void setCustomButtonUrl(String url) {
        String normalized = normalizeUrl(url);
        customButtonUrl = normalized.substring(0, Math.min(512, normalized.length()));
        requestRefresh();
        FluxVisualsClient.requestConfigSave();
    }

    /** Сбросить кэш и заставить onTick сразу перезалить presence. */
    private void requestRefresh() {
        lastSignature = "";
        nextUpdateAt = 0L;
    }

    private void closeCore() {
        if (core != null) {
            try {
                core.close();
            } catch (Throwable ignored) {
            }
        }
        core = null;
        if (params != null) {
            try {
                params.close();
            } catch (Throwable ignored) {
            }
        }
        params = null;
    }
}
