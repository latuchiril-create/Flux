package dev.fuga.fluxvisuals.modules.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.ClientGuiProtection;
import dev.fuga.fluxvisuals.multibot.BotSession;
import dev.fuga.fluxvisuals.multibot.BotInventoryRenderer;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Telegram extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("TG");
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern SALE_PRICE_PATTERN = Pattern.compile("(?iu)за\\s*\\$?\\s*([0-9][0-9\\s,._]*)");
    private static final Pattern PING_RESPONSE_PATTERN = Pattern.compile(
            "(?iu)(?:ваш\\s+пинг|your\\s+ping|ping)\\D{0,24}([0-9]{1,5})\\s*(?:ms|мс)?"
    );
    private static final long BALANCE_RESPONSE_TIMEOUT_MS = 22_000L;
    private static final long BALANCE_COMMAND_DELAY_MIN_MS = 8_000L;
    private static final long BALANCE_COMMAND_DELAY_MAX_MS = 12_000L;
    private static final long PING_RESPONSE_TIMEOUT_MS = 8_000L;
    private static final long SALE_DUPLICATE_WINDOW_MS = 10_000L;
    private static final long CHECK_CALL_DUPLICATE_WINDOW_MS = 30_000L;
    private static final long CLOSE_CONFIRMATION_TIMEOUT_MS = 30_000L;
    private static final long TELEGRAM_CONNECT_TIMEOUT_MS = 20_000L;
    private static final long TELEGRAM_BACKOFF_MIN_MS = 2_000L;
    private static final long TELEGRAM_BACKOFF_MAX_MS = 60_000L;
    private static final long TELEGRAM_DIAGNOSTIC_INTERVAL_MS = 30_000L;
    private static final int TELEGRAM_MAX_ASYNC_REQUESTS = 3;
    private static final long SELL_PAUSE_TIMEOUT_MS = 15_000L;
    private static final long SELL_SCREEN_CLOSE_TIMEOUT_MS = 3_000L;
    private static final long SELL_SERVER_TIMEOUT_MS = 4_000L;
    private static final long SELL_HUMAN_ACTION_MIN_MS = 500L;
    private static final long SELL_HUMAN_ACTION_MAX_MS = 2_000L;
    private static final long SELL_HUMAN_LONG_PAUSE_MIN_MS = 1_400L;
    private static final int SELL_HUMAN_BURST_MIN_ITEMS = 2;
    private static final int SELL_HUMAN_BURST_MAX_ITEMS = 5;
    private static final int SELL_HUMAN_DRIFT_MIN_ACTIONS = 3;
    private static final int SELL_HUMAN_DRIFT_MAX_ACTIONS = 8;
    private static final int SELL_HUMAN_PING_CAP_MS = 250;
    private static final int SELL_MAX_RETRIES = 5;
    private static final long AUTO_SELL_SLOT_REACTION_MIN_MS = 1_800L;
    private static final long AUTO_SELL_SLOT_REACTION_MAX_MS = 4_800L;
    private static final int SELL_REJECTION_RETRIES = 3;
    private static final long LEAVE_PAUSE_TIMEOUT_MS = 15_000L;
    private static final long LEAVE_BALANCE_TIMEOUT_MS = 8_000L;
    private static final long LEAVE_GUI_TIMEOUT_MS = 10_000L;
    private static final long LEAVE_GUI_RETRY_MS = 1_000L;
    private static final long LEAVE_STORAGE_LOAD_MS = 2_000L;
    private static final long LEAVE_HUMAN_PAUSE_MIN_MS = 900L;
    private static final long LEAVE_HUMAN_PAUSE_MAX_MS = 2_800L;
    private static final long LEAVE_HUMAN_ACTION_MIN_MS = 350L;
    private static final long LEAVE_HUMAN_ACTION_MAX_MS = 1_250L;
    private static final int SELL_ALL_SWORDS = Integer.MAX_VALUE;
    private static final int TELEGRAM_PHOTO_MAX_BYTES = 10 * 1024 * 1024;
    private static final int TELEGRAM_DOCUMENT_MAX_BYTES = 50 * 1024 * 1024;
    private static final Pattern SELL_PRICE_PATTERN = Pattern.compile(
            "^[0-9]+(?:[.,][0-9]+)?(?:(?:[kKmMbB]|[кК]){1,3}|[мМ]|млн|МЛН)?$"
    );
    private static final Pattern PURCHASE_PRICE_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)(k{1,3}|к{1,3}|m|м|млн|b)?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern AUTOMATION_DURATION_PART_PATTERN = Pattern.compile(
            "(?iu)([0-9]+)\\s*([smhdсмчд])"
    );
    private static final Pattern AUTOMATION_RANGE_PATTERN = Pattern.compile(
            "^\\s*([0-9]+)(?:\\s*[-:]\\s*([0-9]+))?\\s*$"
    );
    private static final long AUTOMATION_MIN_DURATION_MS = 5L * 60_000L;
    private static final long AUTOMATION_MAX_DURATION_MS = 7L * 24L * 60L * 60_000L;
    private static final AtomicInteger HTTP_THREAD_COUNTER = new AtomicInteger();
    private static final Executor HTTP_EXECUTOR = Executors.newFixedThreadPool(4, command -> {
        Thread thread = new Thread(command, "TG-HTTP-" + HTTP_THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final Executor POLL_EXECUTOR = Executors.newSingleThreadExecutor(command -> {
        Thread thread = new Thread(command, "TG-Poll");
        thread.setDaemon(true);
        return thread;
    });

    private volatile HttpClient httpClient;
    private volatile String httpClientRoute = "direct";
    private volatile boolean pollerRunning;
    private volatile long pollGeneration;
    private volatile int telegramNetworkFailureStreak;
    private volatile long telegramNetworkRetryAt;
    private volatile long telegramLastDiagnosticAt;
    private volatile long telegramQueueDiagnosticAt;
    private int telegramAsyncRequestsInFlight;
    private volatile boolean screenshotRequestPending;
    private volatile boolean pingRequestPending;
    private volatile long pingRequestDeadline;
    private volatile boolean resumeFromHubOnPlay;
    private volatile boolean checkScreenshotQueued;
    private volatile String pendingBanScreenshotCaption = "";
    private volatile long pendingBanScreenshotAt;
    private volatile long closeConfirmationDeadline;
    private volatile String closeConfirmationId = "";
    private volatile String panicConfirmationId = "";
    private volatile long panicConfirmationDeadline;
    private volatile long lastCheckCallAt;
    private volatile String lastCheckCallMessage = "";
    private volatile String lastSaleFingerprint = "";
    private volatile long lastSaleAt;
    private volatile boolean staffCheckActive;
    private volatile LeaveStage leaveStage = LeaveStage.IDLE;
    private volatile boolean leaveHumanized;
    private long leaveDeadline;
    private long leaveNextActionAt;
    private long leaveStartBalance = -1L;
    private long leaveEndBalance = -1L;
    private long leaveFallbackBalance = -1L;
    private int leaveAuctionSyncId = -1;
    private String leaveScreenshotCaption = "";
    private final Object sellLock = new Object();
    private boolean inventorySellQueued;
    private boolean inventorySellWorking;
    private boolean inventorySellAutomatic;
    private boolean autoSellAfterPurchaseQueued;
    private boolean autoSellStorageKnownFull;
    private int auctionSlotLimit;
    private int auctionActiveListings;
    private int auctionTotalListed;
    private int auctionTotalSold;
    private int autoSellConsecutiveFailures;
    private long autoSellRetryAt;
    private int autoSellReleasedSlots;
    private boolean autoSellSlotLimitedRun;
    private long autoSellReleaseReadyAt;
    private long autoSellStorageFullSince;
    private SellStage sellStage = SellStage.IDLE;
    private String inventorySellQueuedPrice = "";
    private int inventorySellQueuedLimit = SELL_ALL_SWORDS;
    private String inventorySellPrice = "";
    private int inventorySellTargetCount;
    private int inventorySellCount;
    private int inventorySellSwordCountBeforeCommand;
    private int inventorySellRetryCount;
    private boolean inventorySellServerRejected;
    private long lastAutoSellDiagnosticAt;
    private boolean inventorySellResumeAutoBuy;
    private int inventorySellInitialSelectedSlot = -1;
    private int inventorySellHotbarSlot = -1;
    private int inventorySellSwapSourceIndex = -1;
    private int inventorySellGuiTargetSlot = -1;
    private int inventorySellGuiFilledCount;
    private int inventorySellGuiSourceIndex = -1;
    private boolean inventorySellUseSellGui;
    private long inventorySellNextActionAt;
    private long inventorySellNextCommandAt;
    private long inventorySellDeadline;
    private long inventorySellLastHumanDelay;
    private int inventorySellItemsUntilLongPause;
    private int inventorySellActionsUntilDriftChange;
    private int inventorySellLastSwordIndex = -1;
    private double inventorySellTempoFactor = 1.0D;
    private double inventorySellRhythmMs;
    private double inventorySellDrift;
    private double inventorySellDriftTarget;
    private final Object balanceLock = new Object();
    private final List<PendingPurchaseNotification> pendingPurchaseNotifications = new ArrayList<>();
    private boolean balanceCheckPending;
    private boolean balanceReplyRequested;
    private boolean statsReplyRequested;
    private long balanceRequestDeadline;
    private String botToken = "";
    private String chatId = "";
    private boolean logsEnabled;
    private volatile boolean notificationsEnabled = true;
    private volatile boolean autoSellEnabled;
    private volatile String autoSellPrice = "";
    private volatile AutomationWizard automationWizard;
    private volatile boolean pendingWizardRenameInput;
    private volatile AutomationSession automationSession;
    private volatile boolean automationTransactionsOnly;
    private volatile boolean automationEndedByHubSafety;
    private volatile boolean automationPaused;
    private volatile boolean pausedAutoBuyWasEnabled;
    private volatile boolean pausedAutoSellWasEnabled;
    private volatile long pausedSessionRemainingMs;
    private volatile String registeredCommandsToken = "";
    private volatile boolean runtimeRestoreNoticePending;
    private volatile long lastRuntimeCheckpointAt;
    private volatile PendingBotInput pendingBotInput = PendingBotInput.NONE;
    private volatile String pendingBotTarget = "";
    private volatile int pendingCustomSellPurchaseIndex = -1;
    private volatile long balanceCommandDueAt;

    public Telegram() {
        super("TG", "Уведомления и команды Telegram для AutoBuy.", ModuleCategory.UTILS);
    }

    private AutoBuy telegramAutoBuy() {
        return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForTelegramSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
        );
    }

    private AutoResellAFK telegramAutoResellAFK() {
        return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoResellAFKForTelegramSession(
                FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK()
        );
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) {
            return;
        }
        if (autoSellAfterPurchaseQueued) {
            AutoBuy.debug("TG onTick: start. automationPaused=" + automationPaused
                    + ", isConfigured=" + isConfigured());
        }
        if (isConfigured()) {
            ensurePolling();
        }
        long now = System.currentTimeMillis();
        handleBalanceTimeout();
        processBalanceCommand(client, now);
        handlePingTimeout();
        processQueuedCheckScreenshot();
        processQueuedBanScreenshot(now);
        if (runtimeRestoreNoticePending && isEnabled() && isConfigured()) {
            runtimeRestoreNoticePending = false;
            if (automationPaused) {
                sendMessageWithButtonRows(
                        "💾 Сохранённое состояние восстановлено на паузе.\n"
                                + "Проверьте статус перед продолжением.",
                        buttonRow(button("📊 Статус", "menu:status"), button("▶️ Продолжить", "menu:resume")),
                        buttonRow(button("🛑 PANIC", "menu:panic"))
                );
            } else if (automationWizard != null) {
                sendMessage("💾 Незавершённый мастер настройки восстановлен.");
                sendAutomationWizardPrompt(automationWizard);
            }
        }
        checkpointRuntimeState(now);

        // AutoSell must be processed even while automation is paused or
        // AutoBuy is disabled. A queued sword would otherwise leave AutoBuy
        // in WAIT_EXTERNAL forever (mgmt pause restores the queue, then never
        // runs it).
        processQueuedAutoSell(client);
        processQueuedInventorySell(client);
        processInventorySell(client, System.currentTimeMillis());
        if (automationPaused) {
            return;
        }
        if (processAutomationSession(client, now)) {
            return;
        }
        if (processLeaveReport(client, now)) {
            return;
        }
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        ensurePolling();
        resetLogFilesForRun();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (automationSession != null) {
            finishAutomationSession(client, false, false);
        }
        stopPolling();
        synchronized (balanceLock) {
            balanceCheckPending = false;
            balanceReplyRequested = false;
            statsReplyRequested = false;
            pendingPurchaseNotifications.clear();
        }
        balanceCommandDueAt = 0L;
        checkScreenshotQueued = false;
        pendingBanScreenshotCaption = "";
        pendingBanScreenshotAt = 0L;
        pingRequestPending = false;
        closeConfirmationDeadline = 0L;
        automationWizard = null;
        automationTransactionsOnly = false;
        automationEndedByHubSafety = false;
        resetLeaveReport();
        synchronized (sellLock) {
            autoSellAfterPurchaseQueued = false;
            resetAutoSellStorageWaitLocked();
        }
        cancelInventorySell(client, null);
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        String next = botToken == null ? "" : botToken.trim();
        if (this.botToken.equals(next)) {
            return;
        }
        this.botToken = next;
        this.httpClient = null;
        this.registeredCommandsToken = "";
        resetTelegramNetworkState();
        restartPolling();
        trace("Bot token updated");
        FluxVisualsClient.requestConfigSave();
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        String next = chatId == null ? "" : chatId.trim();
        if (this.chatId.equals(next)) {
            return;
        }
        this.chatId = next;
        resetTelegramNetworkState();
        restartPolling();
        trace("Chat ID updated: " + safe(next));
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isLogsEnabled() {
        return logsEnabled;
    }

    public void setLogsEnabled(boolean logsEnabled) {
        if (this.logsEnabled == logsEnabled) {
            return;
        }
        this.logsEnabled = logsEnabled;
        if (logsEnabled) {
            ensureLogFile();
            trace("LOGS enabled");
            trace("Log file: " + logFile());
        } else {
            LOGGER.info("[TG] LOGS disabled");
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isConfigured() {
        return !botToken.isBlank() && !chatId.isBlank();
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        if (this.notificationsEnabled == notificationsEnabled) {
            return;
        }
        this.notificationsEnabled = notificationsEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isInventorySellWorking() {
        synchronized (sellLock) {
            return inventorySellQueued || inventorySellWorking;
        }
    }

    public boolean isInventorySellPendingOrWorking() {
        synchronized (sellLock) {
            boolean autoSellReadyToStart = autoSellAfterPurchaseQueued
                    && (!autoSellStorageKnownFull || autoSellReleasedSlots > 0);
            return inventorySellQueued || inventorySellWorking || autoSellReadyToStart;
        }
    }

    public boolean isLeaveReportWorking() {
        return leaveStage != LeaveStage.IDLE;
    }

    public boolean isAutomationSessionActive() {
        return automationSession != null;
    }

    public void markAutoBuyHubSafetyExit() {
        resumeFromHubOnPlay = true;
        if (automationSession != null) {
            automationEndedByHubSafety = true;
        }
    }

    public boolean isAutoSellEnabled() {
        return autoSellEnabled;
    }

    public void setAutoSellEnabled(boolean autoSellEnabled) {
        boolean next = autoSellEnabled && normalizeSellPrice(autoSellPrice) != null;
        if (this.autoSellEnabled == next) {
            return;
        }
        this.autoSellEnabled = next;
        if (!next) {
            synchronized (sellLock) {
                autoSellAfterPurchaseQueued = false;
                resetAutoSellStorageWaitLocked();
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public String getAutoSellPrice() {
        return autoSellPrice;
    }

    public void setAutoSellPrice(String autoSellPrice) {
        String normalized = normalizeSellPrice(autoSellPrice);
        String next = normalized == null ? "" : normalized;
        if (this.autoSellPrice.equals(next)) {
            return;
        }
        this.autoSellPrice = next;
        if (next.isEmpty()) {
            this.autoSellEnabled = false;
            synchronized (sellLock) {
                autoSellAfterPurchaseQueued = false;
                resetAutoSellStorageWaitLocked();
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public void queueAutoSellAfterPurchase() {
        if (!isEnabled() || !autoSellEnabled || normalizeSellPrice(autoSellPrice) == null) {
            AutoBuy.debug("TG queueAutoSellAfterPurchase: SKIPPED enabled=" + isEnabled()
                    + ", autoSellEnabled=" + autoSellEnabled
                    + ", price=" + autoSellPrice
                    + ", normalized=" + normalizeSellPrice(autoSellPrice));
            return;
        }
        synchronized (sellLock) {
            autoSellAfterPurchaseQueued = true;
        }
        AutoBuy.debug("TG queueAutoSellAfterPurchase: QUEUED price=" + autoSellPrice
                + ", storageFull=" + autoSellStorageKnownFull);
        trace("AutoSell: purchase queued at price " + autoSellPrice
                + (autoSellStorageKnownFull ? " while storage is known full" : ""));
        persistRuntimeState();
    }

    public String getRuntimeStateJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        AutoBuy autoBuy = telegramAutoBuy();
        AutomationSession session = automationSession;
        boolean desiredAutoBuy = automationPaused ? pausedAutoBuyWasEnabled : autoBuy.isEnabled();
        boolean desiredAutoSell = automationPaused ? pausedAutoSellWasEnabled : autoSellEnabled;
        long sessionRemaining = session == null
                ? 0L
                : automationPaused
                ? pausedSessionRemainingMs
                : Math.max(0L, session.endsAt() - System.currentTimeMillis());
        root.addProperty("paused", automationPaused);
        root.addProperty("desiredAutoBuy", desiredAutoBuy);
        root.addProperty("desiredAutoSell", desiredAutoSell);
        root.addProperty("sessionRemainingMs", sessionRemaining);

        synchronized (sellLock) {
            JsonObject sell = new JsonObject();
            sell.addProperty("queued", autoSellAfterPurchaseQueued);
            sell.addProperty("storageFull", autoSellStorageKnownFull);
            sell.addProperty("releasedSlots", autoSellReleasedSlots);
            sell.addProperty("storageFullSince", autoSellStorageFullSince);
            sell.addProperty("slotLimit", auctionSlotLimit);
            sell.addProperty("activeListings", auctionActiveListings);
            sell.addProperty("totalListed", auctionTotalListed);
            sell.addProperty("totalSold", auctionTotalSold);
            sell.addProperty(
                    "releaseReadyDelayMs",
                    Math.max(0L, autoSellReleaseReadyAt - System.currentTimeMillis())
            );
            root.add("autoSellRuntime", sell);
        }
        if (automationWizard != null) {
            root.add("wizard", automationWizardToJson(automationWizard));
        }
        if (session != null) {
            root.add("session", automationSessionToJson(session, sessionRemaining));
        }
        return root.toString();
    }

    public void restoreRuntimeStateJson(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(value).getAsJsonObject();
            long now = System.currentTimeMillis();
            pausedAutoBuyWasEnabled = jsonBoolean(root, "desiredAutoBuy", false);
            pausedAutoSellWasEnabled = jsonBoolean(root, "desiredAutoSell", false);
            pausedSessionRemainingMs = Math.max(0L, jsonLong(root, "sessionRemainingMs", 0L));

            if (root.has("wizard") && root.get("wizard").isJsonObject()) {
                automationWizard = automationWizardFromJson(root.getAsJsonObject("wizard"));
            }
            if (root.has("session") && root.get("session").isJsonObject()) {
                automationSession = automationSessionFromJson(
                        root.getAsJsonObject("session"),
                        now,
                        pausedSessionRemainingMs
                );
                automationTransactionsOnly = automationSession != null;
            }
            if (root.has("autoSellRuntime") && root.get("autoSellRuntime").isJsonObject()) {
                JsonObject sell = root.getAsJsonObject("autoSellRuntime");
                synchronized (sellLock) {
                    autoSellAfterPurchaseQueued = jsonBoolean(sell, "queued", false);
                    autoSellStorageKnownFull = jsonBoolean(sell, "storageFull", false);
                    autoSellReleasedSlots = Math.max(0, jsonInt(sell, "releasedSlots", 0));
                    autoSellStorageFullSince = Math.max(0L, jsonLong(sell, "storageFullSince", 0L));
                    auctionSlotLimit = Math.max(0, jsonInt(sell, "slotLimit", 0));
                    auctionActiveListings = Math.max(0, jsonInt(sell, "activeListings", 0));
                    auctionTotalListed = Math.max(0, jsonInt(sell, "totalListed", 0));
                    auctionTotalSold = Math.max(0, jsonInt(sell, "totalSold", 0));
                    autoSellReleaseReadyAt = now
                            + Math.max(0L, jsonLong(sell, "releaseReadyDelayMs", 0L));
                }
            }

            boolean hasPendingRuntime = pausedAutoBuyWasEnabled
                    || automationSession != null;
            automationPaused = hasPendingRuntime || jsonBoolean(root, "paused", false);
            if (automationPaused) {
                telegramAutoBuy().setEnabled(false);
                // A queued sell must survive a restore: the sword is already
                // bought and still waiting to be listed. Only pause the buy
                // side, keep the sell queue armed.
                if (!autoSellAfterPurchaseQueued) {
                    autoSellEnabled = false;
                }
            }
            runtimeRestoreNoticePending = automationPaused || automationWizard != null;
        } catch (RuntimeException ex) {
            trace("Runtime state restore failed: " + ex);
            automationWizard = null;
            automationSession = null;
            automationPaused = false;
        }
    }

    private static JsonObject automationWizardToJson(AutomationWizard wizard) {
        JsonObject value = new JsonObject();
        value.addProperty("id", wizard.id);
        value.addProperty("kind", wizard.kind.name());
        value.addProperty("step", wizard.step.name());
        value.addProperty("durationMs", wizard.durationMs);
        value.addProperty("buyPrice", wizard.buyPrice);
        value.addProperty("auctionSlots", wizard.auctionSlots);
        value.addProperty("budgetSpec", wizard.budgetSpec);
        value.addProperty("maxPurchases", wizard.maxPurchases);
        value.addProperty("targetProfit", wizard.targetProfit);
        value.addProperty("autoResellEnabled", wizard.autoResellEnabled);
        value.addProperty("anarchySwitchEnabled", wizard.anarchySwitchEnabled);
        value.addProperty("renameText", wizard.renameText);
        value.addProperty("autoSellEnabled", wizard.autoSellEnabled);
        value.addProperty("sellPrice", wizard.sellPrice);
        value.add("anarchyIds", stringArray(wizard.anarchyIds));
        value.addProperty("anarchyDelayMinSeconds", wizard.anarchyDelayMinSeconds);
        value.addProperty("anarchyDelayMaxSeconds", wizard.anarchyDelayMaxSeconds);
        value.addProperty("resellMinSeconds", wizard.resellMinSeconds);
        value.addProperty("resellMaxSeconds", wizard.resellMaxSeconds);
        value.addProperty("selfMode", wizard.selfMode);
        return value;
    }

    private static AutomationWizard automationWizardFromJson(JsonObject value) {
        AutomationKind kind = enumValue(
                AutomationKind.class,
                jsonString(value, "kind", AutomationKind.SCHOOL.name()),
                AutomationKind.SCHOOL
        );
        AutomationWizard wizard = new AutomationWizard(
                jsonString(value, "id", newCallbackId()),
                kind
        );
        wizard.step = enumValue(
                AutomationWizardStep.class,
                jsonString(value, "step", AutomationWizardStep.DURATION.name()),
                AutomationWizardStep.DURATION
        );
        wizard.durationMs = Math.max(0L, jsonLong(value, "durationMs", 0L));
        wizard.buyPrice = Math.max(0L, jsonLong(value, "buyPrice", 0L));
        wizard.auctionSlots = Math.max(0, jsonInt(value, "auctionSlots", 0));
        wizard.budgetSpec = jsonString(value, "budgetSpec", "максимум");
        wizard.maxPurchases = Math.max(0, jsonInt(value, "maxPurchases", 0));
        wizard.targetProfit = Math.max(0L, jsonLong(value, "targetProfit", 0L));
        wizard.autoResellEnabled = jsonBoolean(value, "autoResellEnabled", true);
        wizard.anarchySwitchEnabled = jsonBoolean(value, "anarchySwitchEnabled", true);
        wizard.renameText = jsonString(value, "renameText", "");
        wizard.autoSellEnabled = jsonBoolean(value, "autoSellEnabled", true);
        wizard.sellPrice = jsonString(value, "sellPrice", "");
        wizard.anarchyIds = jsonStringList(value, "anarchyIds");
        wizard.anarchyDelayMinSeconds = jsonLong(value, "anarchyDelayMinSeconds", 75L);
        wizard.anarchyDelayMaxSeconds = jsonLong(value, "anarchyDelayMaxSeconds", 80L);
        wizard.resellMinSeconds = jsonLong(value, "resellMinSeconds", 60L);
        wizard.resellMaxSeconds = jsonLong(value, "resellMaxSeconds", 65L);
        wizard.selfMode = jsonBoolean(value, "selfMode", true);
        return wizard;
    }

    private static JsonObject automationSessionToJson(AutomationSession session, long remainingMs) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", session.kind().name());
        value.addProperty("remainingMs", remainingMs);
        value.addProperty("selfMode", session.selfMode());
        value.addProperty("buyPrice", session.buyPrice());
        value.addProperty("autoSellEnabled", session.autoSellEnabled());
        value.addProperty("sellPrice", session.sellPrice());
        value.addProperty("budgetSpec", session.budgetSpec());
        value.addProperty("maxPurchases", session.maxPurchases());
        value.addProperty("targetProfit", session.targetProfit());
        value.add("snapshot", automationSnapshotToJson(session.snapshot()));
        return value;
    }

    private static AutomationSession automationSessionFromJson(JsonObject value, long now, long fallbackRemainingMs) {
        if (!value.has("snapshot") || !value.get("snapshot").isJsonObject()) {
            return null;
        }
        long remainingMs = Math.max(
                1_000L,
                jsonLong(value, "remainingMs", fallbackRemainingMs)
        );
        return new AutomationSession(
                enumValue(
                        AutomationKind.class,
                        jsonString(value, "kind", AutomationKind.SCHOOL.name()),
                        AutomationKind.SCHOOL
                ),
                now,
                now + remainingMs,
                jsonBoolean(value, "selfMode", true),
                Math.max(0L, jsonLong(value, "buyPrice", 0L)),
                jsonBoolean(value, "autoSellEnabled", true),
                jsonString(value, "sellPrice", ""),
                jsonString(value, "budgetSpec", "максимум"),
                Math.max(0, jsonInt(value, "maxPurchases", 0)),
                Math.max(0L, jsonLong(value, "targetProfit", 0L)),
                automationSnapshotFromJson(value.getAsJsonObject("snapshot"))
        );
    }

    private static JsonObject automationSnapshotToJson(AutomationSnapshot snapshot) {
        JsonObject value = new JsonObject();
        value.addProperty("maxBuyPrice", snapshot.maxBuyPrice());
        value.addProperty("priceFilterEnabled", snapshot.priceFilterEnabled());
        value.addProperty("autoSellPrice", snapshot.autoSellPrice());
        value.addProperty("autoSellEnabled", snapshot.autoSellEnabled());
        value.addProperty("autoResellEnabled", snapshot.autoResellEnabled());
        value.addProperty("anarchySwitchEnabled", snapshot.anarchySwitchEnabled());
        value.add("anarchyIds", stringArray(snapshot.anarchyIds()));
        value.addProperty("lowBalanceGuardEnabled", snapshot.lowBalanceGuardEnabled());
        value.addProperty("notificationsEnabled", snapshot.notificationsEnabled());
        value.addProperty("anarchyDelayMinMs", snapshot.anarchyDelayMinMs());
        value.addProperty("anarchyDelayMaxMs", snapshot.anarchyDelayMaxMs());
        value.addProperty("resellIntervalMinMs", snapshot.resellIntervalMinMs());
        value.addProperty("resellIntervalMaxMs", snapshot.resellIntervalMaxMs());
        return value;
    }

    private static AutomationSnapshot automationSnapshotFromJson(JsonObject value) {
        return new AutomationSnapshot(
                jsonLong(value, "maxBuyPrice", 0L),
                jsonBoolean(value, "priceFilterEnabled", false),
                jsonString(value, "autoSellPrice", ""),
                jsonBoolean(value, "autoSellEnabled", false),
                jsonBoolean(value, "autoResellEnabled", false),
                jsonBoolean(value, "anarchySwitchEnabled", false),
                jsonStringList(value, "anarchyIds"),
                jsonBoolean(value, "lowBalanceGuardEnabled", true),
                jsonBoolean(value, "notificationsEnabled", true),
                jsonLong(value, "anarchyDelayMinMs", 75_000L),
                jsonLong(value, "anarchyDelayMaxMs", 80_000L),
                jsonLong(value, "resellIntervalMinMs", 60_000L),
                jsonLong(value, "resellIntervalMaxMs", 65_000L)
        );
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values == null ? List.<String>of() : values) {
            result.add(value);
        }
        return result;
    }

    private static List<String> jsonStringList(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return List.copyOf(result);
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean jsonBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long jsonLong(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        long value = jsonLong(object, key, fallback);
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? fallback : (int) value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return fallback;
        }
    }

    private static String newCallbackId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void persistRuntimeState() {
        FluxVisualsClient.requestConfigSave();
    }

    private void checkpointRuntimeState(long now) {
        if (now - lastRuntimeCheckpointAt < 30_000L) {
            return;
        }
        if (automationWizard == null
                && automationSession == null
                && !automationPaused
                && !autoSellAfterPurchaseQueued
                && !autoSellStorageKnownFull) {
            return;
        }
        lastRuntimeCheckpointAt = now;
        persistRuntimeState();
    }

    public void sendTestMessage() {
        if (!isConfigured()) {
            showLocalTelegramStatus("Telegram: заполните Bot token и Chat ID.");
            return;
        }
        if (!looksLikeBotToken(botToken)) {
            showLocalTelegramStatus("Telegram: токен имеет неверный формат. Скопируйте его целиком из BotFather.");
            return;
        }
        if (!chatId.matches("-?[0-9]+")) {
            showLocalTelegramStatus("Telegram: Chat ID должен быть числом, например 5932605788.");
            return;
        }
        if (!beginTelegramAsyncRequest("test sendMessage")) {
            showLocalTelegramStatus("Telegram: тест пока не отправлен, активна сетевая пауза или другой запрос.");
            return;
        }

        String tokenSnapshot = botToken;
        String chatSnapshot = chatId;
        String text = "🤖✨ Telegram-бот подключён!\n\n"
                + "✅ Уведомления работают\n"
                + "💰 Команда баланса: /bal\n"
                + "📶 Проверка пинга: /ping\n"
                + "📸 Скриншот игры: /screen\n"
                + "⚔️ AutoBuy готов отправлять события";
        String body = "{\"chat_id\":" + jsonQuote(chatSnapshot) + ",\"text\":" + jsonQuote(text) + "}";
        HttpRequest request = jsonRequest(tokenSnapshot, "sendMessage", body, Duration.ofSeconds(30));
        showLocalTelegramStatus("Telegram: отправляю тестовое сообщение...");
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        if (throwable != null) {
                            recordTelegramNetworkFailure("test sendMessage", throwable);
                            Throwable cause = rootCause(throwable);
                            showLocalTelegramStatus("Telegram: соединение не установлено: "
                                    + cause.getClass().getSimpleName() + ". Проверьте VPN, firewall или proxy.");
                            return;
                        }
                        if (response.statusCode() == 200 && isTelegramOk(response.body())) {
                            recordTelegramApiResult(true);
                            trace("test sendMessage HTTP 200 ok=true");
                            showLocalTelegramStatus(isEnabled()
                                    ? "Telegram: тестовое сообщение успешно отправлено."
                                    : "Telegram: тест отправлен, но модуль TG выключен. Включите TG для команд и уведомлений.");
                            return;
                        }

                        recordTelegramApiResult(false);
                        String description = extractDescription(response.body());
                        LOGGER.warn("[TG] test sendMessage error: {} (HTTP {})",
                                description, response.statusCode());
                        trace("test sendMessage error: " + description + " (HTTP " + response.statusCode() + ')');
                        showLocalTelegramStatus(formatTestSendError(response.statusCode(), description));
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            recordTelegramNetworkFailure("test sendMessage", ex);
            showLocalTelegramStatus("Telegram: тест не запущен: " + safe(rootCause(ex).getMessage()));
        }
    }

    public void sendPurchaseMessage(String itemName, long price, String seller) {
        if (!isEnabled() || !isConfigured() || !notificationsEnabled) {
            return;
        }
        synchronized (balanceLock) {
            pendingPurchaseNotifications.add(new PendingPurchaseNotification(itemName, price, seller));
        }
        requestBalanceCheck(false);
    }

    public void sendLowBalanceMessage(long balance, long requiredPrice) {
        if (!isEnabled() || !shouldSendRoutineNotification()) {
            return;
        }
        sendMessage("⚠️💸 НИЗКИЙ БАЛАНС!\n\n"
                + "💰 Сейчас: " + formatMoney(balance) + "\n"
                + "⚔️ Нужно для покупки: " + formatMoney(requiredPrice) + "\n"
                + "📦 AutoBuy проверяет хранилище и готовится к перевыставлению.");
    }

    public void sendLowBalanceResellMessage(long balance, long requiredPrice) {
        if (!isEnabled() || !shouldSendRoutineNotification()) {
            return;
        }
        sendMessage("♻️📦 ПЕРЕВЫСТАВЛЕНИЕ ЗАПУЩЕНО!\n\n"
                + "⚠️ Денег на новый меч недостаточно\n"
                + "💰 Баланс: " + formatMoney(balance) + "\n"
                + "🎯 Требуется: " + formatMoney(requiredPrice) + "\n"
                + "🔄 Открываю хранилище и перевыставляю мечи.");
    }

    public void sendBalanceRecoveredMessage(long balance) {
        if (!isEnabled() || !shouldSendRoutineNotification()) {
            return;
        }
        sendMessage("🎉💰 БАЛАНС ВОССТАНОВЛЕН!\n\n"
                + "💳 Сейчас: " + formatMoney(balance) + "\n"
                + "🔎 AutoBuy возвращается к поиску мечей.");
    }

    public void sendHubExitMessage(long balance, long requiredPrice) {
        if (!isEnabled() || !shouldSendRoutineNotification()) {
            return;
        }
        sendMessage("🚪⚠️ AUTOBUY УШЁЛ В ХАБ!\n\n"
                + "💰 Баланс: " + formatMoney(balance) + "\n"
                + "⚔️ Требуется: " + formatMoney(requiredPrice) + "\n"
                + "📦 В хранилище нет мечей для перевыставления\n"
                + "⛔ AutoBuy автоматически выключен.");
    }

    public void onGameChatMessage(String message) {
        if (!isEnabled() || !isConfigured() || message == null || message.isBlank()) {
            return;
        }

        if (isCheckCallMessage(message)) {
            handleCheckCall(message);
            return;
        }
        Long balance = AutoBuy.extractBalance(message);
        if (balance != null) {
            acceptLeaveReportBalance(balance);
        }
        if (isBalanceCheckPending()) {
            if (balance != null) {
                completeBalanceCheck(balance, null);
            }
        }
        if (pingRequestPending) {
            Integer ping = extractPing(message);
            if (ping != null) {
                pingRequestPending = false;
                pingRequestDeadline = 0L;
                sendMessage("📶 Ваш пинг: " + ping + " мс");
            }
        }
        if (handleInventoryStorageFullMessage(message)) {
            return;
        }
        if (handleInventorySellFailureMessage(message)) {
            return;
        }

        if (isOwnAuctionSaleMessage(message)) {
            registerAuctionSaleReleasedSlot(message);
        }
        Long salePrice = extractSwordSalePrice(message);
        if (salePrice == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String fingerprint = normalizeMessage(message) + ':' + salePrice;
        if (fingerprint.equals(lastSaleFingerprint) && now - lastSaleAt < SALE_DUPLICATE_WINDOW_MS) {
            return;
        }
        lastSaleFingerprint = fingerprint;
        lastSaleAt = now;
        telegramAutoBuy().recordSwordSale(salePrice);
        if (notificationsEnabled) {
            sendMessage("💸🎊 МЕЧ ПРОДАН! 🎊💸\n\n"
                    + "⚔️ У вас купили Меч за " + formatPrice(salePrice) + "$!\n"
                    + "💰 Деньги уже должны быть на балансе\n"
                    + "🕒 Время: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT) + "\n\n"
                    + "✅ Сделка завершена!");
        }
    }

    public void handleAutoResellPurchaseMessage(String message) {
        if (message == null || !telegramAutoResellAFK().isEnabled()
                || !telegramAutoResellAFK().isSellPurchasedSwords()) return;
        String normalized = message.toLowerCase(Locale.ROOT);
        String price = telegramAutoResellAFK().getSellPrice();
        if (price.isBlank()) return;
        if (normalized.contains("успешно купили") && normalized.contains("незеритовый меч")) {
            requestAutomaticSwordSell(price);
            return;
        }
    }

    public void sellPurchasedSword(String price) {
        requestInventorySell(price + " 1", true);
    }

    public void handleDisconnect(String reason) {
        if (!isBanDisconnectReason(reason)) {
            return;
        }
        String safeReason = reason == null || reason.isBlank()
                ? "Причина отключения не указана сервером."
                : reason.replace('\n', ' ').replace('\r', ' ').trim();
        if (safeReason.length() > 500) {
            safeReason = safeReason.substring(0, 500) + "...";
        }
        final String disconnectReason = safeReason;

        MinecraftClient minecraft = MinecraftClient.getInstance();
        Runnable stopTask = () -> {
            if (automationSession != null) {
                finishAutomationSession(minecraft, false, false);
            }
            cancelInventorySell(minecraft, null);
            telegramAutoBuy().setEnabled(false);
            setAutoSellEnabled(false);
            FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(false);
            resumeFromHubOnPlay = false;
            staffCheckActive = false;

            String caption = "🚫 ВЫ ЗАБАНЕНЫ НА СЕРВЕРЕ\n"
                    + "⛔ Все системы AutoBuy отключены.\n"
                    + "📄 Причина: " + disconnectReason;
            pendingBanScreenshotCaption = caption;
            pendingBanScreenshotAt = System.currentTimeMillis() + 750L;
            sendMessage(caption);
        };
        if (minecraft == null) {
            stopTask.run();
        } else {
            FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, stopTask);
        }
    }

    private void handleCheckCall(String message) {
        long now = System.currentTimeMillis();
        String normalized = normalizeMessage(message);
        if (normalized.equals(lastCheckCallMessage) && now - lastCheckCallAt < CHECK_CALL_DUPLICATE_WINDOW_MS) {
            return;
        }
        lastCheckCallMessage = normalized;
        lastCheckCallAt = now;
        staffCheckActive = true;

        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft != null) {
            FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
                if (automationSession != null) {
                    finishAutomationSession(minecraft, false, false);
                }
                if (isLeaveReportWorking()) {
                    resetLeaveReport();
                    performImmediateLeave(minecraft, true);
                    return;
                }
                cancelInventorySell(minecraft, "🚨 Выставление остановлено из-за вызова на проверку.");
                telegramAutoBuy().setEnabled(false);
            });
        }
        if (notificationsEnabled) {
            checkScreenshotQueued = true;
            sendMessage("🚨⚠️ ВАС ВЫЗВАЛИ НА ПРОВЕРКУ!\n\n"
                    + "⛔ AutoBuy автоматически выключен\n"
                    + "📸 Скриншот Minecraft будет отправлен в этот чат.");
        }
    }

    public void sendMessage(String text) {
        sendMessage(text, null);
    }

    private void sendMessageWithButtons(String text, InlineButton... buttons) {
        sendMessageWithButtonRows(text, buttons);
    }

    private void sendMessageWithButtonRows(String text, InlineButton[]... rows) {
        sendMessage(text, inlineKeyboard(rows));
    }

    private static JsonObject inlineKeyboard(InlineButton[]... rows) {
        JsonArray keyboard = new JsonArray();
        for (InlineButton[] buttons : rows) {
            JsonArray row = new JsonArray();
            for (InlineButton button : buttons) {
                JsonObject item = new JsonObject();
                item.addProperty("text", button.text());
                if (button.url() != null && !button.url().isBlank()) {
                    item.addProperty("url", button.url());
                } else {
                    item.addProperty("callback_data", button.callbackData());
                }
                row.add(item);
            }
            keyboard.add(row);
        }
        JsonObject replyMarkup = new JsonObject();
        replyMarkup.add("inline_keyboard", keyboard);
        return replyMarkup;
    }

    private void editMessage(BotUpdate update, String text, InlineButton[]... rows) {
        if (update == null || update.messageId() <= 0L) {
            if (rows.length == 0) {
                sendMessage(text);
            } else {
                sendMessageWithButtonRows(text, rows);
            }
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", update.chatId());
        body.addProperty("message_id", update.messageId());
        body.addProperty("text", text);
        body.addProperty("parse_mode", "HTML");
        body.add("reply_markup", rows.length == 0 ? inlineKeyboard() : inlineKeyboard(rows));
        sendTelegramRequest("editMessageText", body, Duration.ofSeconds(30));
    }

    private void sendTelegramRequest(String method, JsonObject body, Duration timeout) {
        if (!isConfigured() || !beginTelegramAsyncRequest(method)) {
            return;
        }
        HttpRequest request = jsonRequest(botToken, method, body.toString(), timeout);
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        if (throwable == null) {
                            handleApiResponse(method, response);
                        } else {
                            recordTelegramNetworkFailure(method, throwable);
                        }
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            recordTelegramNetworkFailure(method, ex);
        }
    }

    private static InlineButton[] buttonRow(InlineButton... buttons) {
        return buttons;
    }

    private void sendMessage(String text, JsonObject replyMarkup) {
        if (!isConfigured()) {
            trace("Message skipped: token or chat id is empty");
            return;
        }
        if (!beginTelegramAsyncRequest("sendMessage")) {
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("text", text);
        body.addProperty("parse_mode", "HTML");
        if (replyMarkup != null) {
            body.add("reply_markup", replyMarkup);
        }
        HttpRequest request = jsonRequest(botToken, "sendMessage", body.toString(), Duration.ofSeconds(30));
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        if (throwable == null) {
                            handleApiResponse("sendMessage", response);
                        } else {
                            recordTelegramNetworkFailure("sendMessage", throwable);
                        }
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            recordTelegramNetworkFailure("sendMessage", ex);
        }
    }

    public void sendNotification(String text) {
        if (shouldSendRoutineNotification()) {
            sendMessage(text);
        }
    }

    private boolean shouldSendRoutineNotification() {
        return notificationsEnabled && !automationTransactionsOnly;
    }

    private void sendMessageThenCloseMinecraft(String text) {
        if (!isConfigured()) {
            scheduleMinecraftStop();
            return;
        }
        if (!beginTelegramAsyncRequest("final sendMessage")) {
            scheduleMinecraftStop();
            return;
        }

        String body = "{\"chat_id\":" + jsonQuote(chatId) + ",\"text\":" + jsonQuote(text) + ",\"parse_mode\":\"HTML\"}";
        HttpRequest request = jsonRequest(botToken, "sendMessage", body, Duration.ofSeconds(30));
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        if (throwable != null) {
                            recordTelegramNetworkFailure("final sendMessage", throwable);
                        } else {
                            handleApiResponse("sendMessage", response);
                        }
                        scheduleMinecraftStop();
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            trace("Final Telegram message failed before shutdown: " + ex);
            LOGGER.warn("[TG] Final Telegram message failed before shutdown: {}", ex.toString());
            scheduleMinecraftStop();
        }
    }

    private static void scheduleMinecraftStop() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft != null) {
            FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, minecraft::scheduleStop);
        }
    }

    private void ensurePolling() {
        String tokenSnapshot;
        long generation;
        synchronized (this) {
            if (pollerRunning || !isEnabled() || !isConfigured()) {
                return;
            }
            pollerRunning = true;
            generation = ++pollGeneration;
            tokenSnapshot = botToken;
        }
        POLL_EXECUTOR.execute(() -> pollLoop(generation, tokenSnapshot));
    }

    private void restartPolling() {
        stopPolling();
        if (!pollerRunning && isEnabled() && isConfigured()) {
            ensurePolling();
        }
    }

    private synchronized void stopPolling() {
        pollGeneration++;
    }

    private void pollLoop(long generation, String tokenSnapshot) {
        trace("Telegram polling started");
        boolean webhookReady = deleteWebhook(tokenSnapshot);
        if (!webhookReady) {
            sleepPollingBackoff();
        }
        registerBotCommands(tokenSnapshot);
        long offset = webhookReady ? discardPendingUpdates(tokenSnapshot) : 0L;
        try {
            while (isPollActive(generation, tokenSnapshot)) {
                PollBatch batch = requestUpdates(tokenSnapshot, offset, 25, 100);
                if (!batch.success()) {
                    sleepPollingBackoff();
                    continue;
                }
                offset = batch.nextOffset();
                for (BotUpdate update : batch.updates()) {
                    handleBotUpdate(update);
                }
            }
        } finally {
            boolean restart;
            synchronized (this) {
                pollerRunning = false;
                restart = isEnabled() && isConfigured();
            }
            if (restart) {
                ensurePolling();
            }
            trace("Telegram polling stopped");
        }
    }

    private boolean deleteWebhook(String tokenSnapshot) {
        HttpRequest request = jsonRequest(
                tokenSnapshot,
                "deleteWebhook",
                "{\"drop_pending_updates\":true}",
                Duration.ofSeconds(30)
        );
        try {
            HttpResponse<String> response = client().send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200 || !isTelegramOk(response.body())) {
                LOGGER.warn("[TG] deleteWebhook failed: {} (HTTP {})",
                        extractDescription(response.body()), response.statusCode());
                recordTelegramApiResult(false);
                return false;
            }
            recordTelegramApiResult(true);
            return true;
        } catch (IOException ex) {
            recordTelegramNetworkFailure("deleteWebhook", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private void registerBotCommands(String tokenSnapshot) {
        if (tokenSnapshot.equals(registeredCommandsToken)) {
            return;
        }
        JsonArray commands = new JsonArray();
        addBotCommand(commands, "menu", "Открыть главное меню");
        addBotCommand(commands, "status", "Статус и статистика AutoBuy");
        addBotCommand(commands, "play", "Запустить AutoBuy");
        addBotCommand(commands, "stop", "Остановить AutoBuy");
        addBotCommand(commands, "pause", "Поставить автоматизацию на паузу");
        addBotCommand(commands, "resume", "Продолжить после паузы");
        addBotCommand(commands, "panic", "Экстренно остановить все действия");
        addBotCommand(commands, "inventory", "Мечи и состояние хранилища");
        addBotCommand(commands, "slots", "Указать количество слотов аукциона");
        addBotCommand(commands, "ban", "Запретить покупки у продавца");
        addBotCommand(commands, "unban", "Снять запрет с продавца");
        addBotCommand(commands, "info", "Информация о купленном мече");
        addBotCommand(commands, "connect", "Подключиться к Minecraft-серверу");
        addBotCommand(commands, "autosell", "Настроить или проверить AutoSell");
        addBotCommand(commands, "stopautosell", "Отключить AutoSell");
        addBotCommand(commands, "sell", "Выставить мечи вручную");
        addBotCommand(commands, "price", "Изменить максимальную цену покупки");
        addBotCommand(commands, "bal", "Проверить баланс");
        addBotCommand(commands, "ping", "Проверить пинг");
        addBotCommand(commands, "lowbalance", "Настроить защиту низкого баланса");
        addBotCommand(commands, "school", "Запустить мастер SCHOOL");
        addBotCommand(commands, "night", "Запустить мастер NIGHT");
        addBotCommand(commands, "daily", "Запустить повседневную сессию");
        addBotCommand(commands, "cancel", "Отменить текущий мастер");
        addBotCommand(commands, "leave", "Отчёт и переход в хаб");
        addBotCommand(commands, "crash", "Закрыть Minecraft с подтверждением");
        addBotCommand(commands, "version", "Версия клиента и состояние связи");
        addBotCommand(commands, "bot", "Выбрать аккаунт для управления: /bot <ник|main>");
        addBotCommand(commands, "help", "Список команд");

        JsonObject body = new JsonObject();
        body.add("commands", commands);
        HttpRequest request = jsonRequest(tokenSnapshot, "setMyCommands", body.toString(), Duration.ofSeconds(30));
        try {
            HttpResponse<String> response = client().send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() == 200 && isTelegramOk(response.body())) {
                registeredCommandsToken = tokenSnapshot;
                recordTelegramApiResult(true);
            } else {
                recordTelegramApiResult(false);
                trace("setMyCommands failed: " + extractDescription(response.body()));
            }
        } catch (IOException ex) {
            recordTelegramNetworkFailure("setMyCommands", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void addBotCommand(JsonArray commands, String command, String description) {
        JsonObject value = new JsonObject();
        value.addProperty("command", command);
        value.addProperty("description", description);
        commands.add(value);
    }

    private long discardPendingUpdates(String tokenSnapshot) {
        PollBatch batch = requestUpdates(tokenSnapshot, -1L, 0, 1);
        return batch.success() ? Math.max(0L, batch.nextOffset()) : 0L;
    }

    private PollBatch requestUpdates(String tokenSnapshot, long offset, int timeoutSeconds, int limit) {
        String body = "{\"offset\":" + offset
                + ",\"timeout\":" + timeoutSeconds
                + ",\"limit\":" + limit
                + ",\"allowed_updates\":[\"message\",\"callback_query\"]}";
        HttpRequest request = jsonRequest(
                tokenSnapshot,
                "getUpdates",
                body,
                Duration.ofSeconds(Math.max(10, timeoutSeconds + 10))
        );
        try {
            HttpResponse<String> response = client().send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                LOGGER.warn("[TG] getUpdates failed with HTTP {}", response.statusCode());
                trace("getUpdates HTTP " + response.statusCode() + ": " + response.body());
                recordTelegramApiResult(false);
                return PollBatch.failed(offset);
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                LOGGER.warn("[TG] getUpdates error: {}", description(root));
                trace("getUpdates ok=false: " + response.body());
                recordTelegramApiResult(false);
                return PollBatch.failed(offset);
            }

            JsonArray result = root.getAsJsonArray("result");
            List<BotUpdate> updates = new ArrayList<>();
            long nextOffset = Math.max(0L, offset);
            for (JsonElement element : result) {
                JsonObject update = element.getAsJsonObject();
                long updateId = update.get("update_id").getAsLong();
                nextOffset = Math.max(nextOffset, updateId + 1L);
                if (update.has("message")) {
                    JsonObject message = update.getAsJsonObject("message");
                    if (!message.has("text") || !message.has("chat")) {
                        continue;
                    }
                    JsonObject chat = message.getAsJsonObject("chat");
                    if (!chat.has("id")) {
                        continue;
                    }
                    updates.add(new BotUpdate(
                            updateId,
                            chat.get("id").getAsString(),
                            message.get("text").getAsString(),
                            "",
                            message.has("message_id") ? message.get("message_id").getAsLong() : 0L,
                            message.get("text").getAsString(),
                            extractReplyText(message)
                    ));
                } else if (update.has("callback_query")) {
                    JsonObject callback = update.getAsJsonObject("callback_query");
                    if (!callback.has("id") || !callback.has("data") || !callback.has("message")) {
                        continue;
                    }
                    JsonObject message = callback.getAsJsonObject("message");
                    if (!message.has("chat")) {
                        continue;
                    }
                    JsonObject chat = message.getAsJsonObject("chat");
                    if (!chat.has("id")) {
                        continue;
                    }
                    updates.add(new BotUpdate(
                            updateId,
                            chat.get("id").getAsString(),
                            callback.get("data").getAsString(),
                            callback.get("id").getAsString(),
                            message.has("message_id") ? message.get("message_id").getAsLong() : 0L,
                            message.has("text") ? message.get("text").getAsString() : "",
                            ""
                    ));
                }
            }
            recordTelegramApiResult(true);
            return new PollBatch(true, nextOffset, updates);
        } catch (IOException ex) {
            recordTelegramNetworkFailure("getUpdates", ex);
            return PollBatch.failed(offset);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PollBatch.failed(offset);
        } catch (RuntimeException ex) {
            LOGGER.warn("[TG] Failed to parse getUpdates response: {}", ex.toString());
            trace("getUpdates parse failure: " + ex);
            return PollBatch.failed(offset);
        }
    }

    private void handleBotUpdate(BotUpdate update) {
        if (!chatId.equals(update.chatId())) {
            trace("Ignored command from chat " + update.chatId());
            return;
        }
        if (!update.callbackQueryId().isEmpty()) {
            answerCallbackQuery(update.callbackQueryId());
            handleBotCallback(update);
            return;
        }
        String text = update.text().trim();
        if (text.isEmpty()) {
            return;
        }
        if (automationWizard != null && pendingWizardRenameInput && !text.startsWith("/")) {
            handleAutomationWizardAnswer(text);
            return;
        }
        if (pendingBotInput != PendingBotInput.NONE && !text.startsWith("/")) {
            handlePendingBotInput(text);
            return;
        }
        if (automationWizard != null && !text.startsWith("/")) {
            handleAutomationWizardAnswer(text);
            return;
        }
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String arguments = parts.length > 1 ? parts[1].trim() : "";
        int mentionIndex = command.indexOf('@');
        if (mentionIndex >= 0) {
            command = command.substring(0, mentionIndex);
        }
        switch (command) {
            case "/bal" -> requestBalanceFromMinecraft();
            case "/ping" -> requestPingFromMinecraft();
            case "/screen" -> requestScreenshotFromMinecraft();
            case "/price" -> requestPurchasePrice(arguments);
            case "/sell" -> requestInventorySell(arguments);
            case "/autosell" -> requestAutoSellEnable(arguments);
            case "/stopautosell" -> requestAutoSellDisable();
            case "/nf" -> toggleNotifications();
            case "/crash" -> requestMinecraftClose();
            case "/play" -> requestAutoBuyStart();
            case "/stop" -> requestAutoBuyStop();
            case "/pause" -> requestAutomationPause();
            case "/resume" -> requestAutomationResume();
            case "/panic" -> requestPanicConfirmation();
            case "/inventory" -> sendInventoryStatus(null);
            case "/slots" -> requestAuctionSlots(arguments);
            case "/ban" -> requestBanSeller(arguments);
            case "/unban" -> requestUnbanSeller(arguments);
            case "/info" -> requestPurchaseInfo(update, arguments);
            case "/connect" -> requestServerConnect(arguments);
            case "/version" -> sendVersionStatus(null);
            case "/lowbalance" -> requestLowBalanceGuard(arguments);
            case "/nolowbalance" -> requestLowBalanceGuard("off");
            case "/school" -> requestAutomationWizard(AutomationKind.SCHOOL, arguments);
            case "/night" -> requestAutomationWizard(AutomationKind.NIGHT, arguments);
            case "/daily" -> requestAutomationWizard(AutomationKind.DAILY, arguments);
            case "/cancel" -> cancelAutomationWizard();
            case "/status" -> requestAutoBuyStats();
            case "/bot", "/bots", "/b", "/d" -> {
                if (arguments.isBlank()) {
                    sendBotsMenu(null);
                } else {
                    BotSession s = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(arguments);
                    if (s != null) {
                        sendBotControlPanel(null, s.getName());
                    } else {
                        sendMessage(FluxVisualsClient.MULTI_BOT_MANAGER.selectTelegramTarget(arguments));
                    }
                }
            }
            case "/start", "/menu", "/m" -> sendMainMenu(null);
            case "/help" -> sendHelpMessage();
            default -> {
            }
        }
    }

    private void sendMainMenu(BotUpdate update) {
        AutoBuy autoBuy = telegramAutoBuy();
        String text = "🎛️ FUGA BOT\n\n"
                + "AutoBuy: " + (autoBuy.isEnabled() ? "▶️ работает" : automationPaused ? "⏸️ пауза" : "⏹️ выключен")
                + "\nAutoSell: " + (autoSellEnabled ? "включён" : "выключен")
                + "\nАккаунт TG: " + FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetName()
                + "\nСессия: " + (automationSession == null
                ? "нет"
                : automationSession.kind().label() + (automationPaused ? " — пауза" : ""));
        InlineButton[] controlRow = automationPaused
                ? buttonRow(button("▶️ Продолжить", "menu:resume"), button("🛑 PANIC", "menu:panic"))
                : buttonRow(button("▶️ Запуск", "menu:play"), button("⏸️ Пауза", "menu:pause"));
        InlineButton[] secondControlRow = automationPaused
                ? buttonRow(button("⏹️ Остановить", "menu:stop"))
                : buttonRow(button("⏹️ Остановить", "menu:stop"), button("🛑 PANIC", "menu:panic"));
        InlineButton[][] rows = new InlineButton[][]{
                controlRow,
                secondControlRow,
                buttonRow(button("🤖 Боты", "menu:bots"), button("📊 Статус", "menu:status")),
                buttonRow(button("🎒 Инвентарь", "menu:inventory"), button("🤖 AutoSell", "menu:autosell")),
                buttonRow(button("⚙️ Сессии", "menu:sessions"), button("🌐 Зайти на FT", "menu:ft")),
                buttonRow(button("ℹ️ Версия", "menu:version"), button("❓ Помощь", "menu:help")),
                buttonRow(urlButton("💬 Связь с поддержкой", "https://t.me/xFUGAx")),
                buttonRow(button("❌ Закрыть меню", "menu:close"))
        };
        if (update == null) {
            sendMessageWithButtonRows(text, rows);
        } else {
            editMessage(update, text, rows);
        }
    }

    private static InlineButton button(String text, String callbackData) {
        return new InlineButton(text, callbackData);
    }

    private static InlineButton urlButton(String text, String url) {
        return new InlineButton(text, "", url);
    }

    private void handleMenuCallback(BotUpdate update, String action) {
        switch (action) {
            case "home" -> sendMainMenu(update);
            case "bots" -> sendBotsMenu(update);
            case "play" -> {
                editMessage(update, "▶️ Команда запуска принята.");
                requestAutoBuyStart();
            }
            case "stop" -> {
                editMessage(update, "⏹️ Команда остановки принята.");
                requestAutoBuyStop();
            }
            case "pause" -> {
                editMessage(update, "⏸️ Ставлю автоматизацию на паузу...");
                requestAutomationPause();
            }
            case "resume" -> {
                editMessage(update, "▶️ Возобновляю автоматизацию...");
                requestAutomationResume();
            }
            case "panic" -> requestPanicConfirmation(update);
            case "status", "status_refresh" -> sendStatusPanel(update);
            case "inventory", "inventory_refresh" -> sendInventoryStatus(update);
            case "autosell" -> sendAutoSellStatus(update);
            case "autosell_enable" -> {
                if (normalizeSellPrice(autoSellPrice) == null) {
                    editMessage(update, "⚠️ Сначала задайте цену командой /autosell <цена>.",
                            buttonRow(button("⬅️ Назад", "menu:autosell")));
                } else {
                    setAutoSellEnabled(true);
                    sendAutoSellStatus(update);
                }
            }
            case "autosell_disable" -> {
                requestAutoSellDisable();
                sendAutoSellStatus(update);
            }
            case "autosell_price" -> {
                pendingBotInput = PendingBotInput.AUTOSELL_PRICE;
                editMessage(update, "💰 Отправьте новую цену AutoSell, например 3.8kk.",
                        buttonRow(button("⬅️ Назад", "menu:autosell")));
            }
            case "sessions" -> editMessage(
                    update,
                    "⚙️ АВТОНОМНЫЕ СЕССИИ\n\nВыберите мастер настройки.",
                    buttonRow(button("🎒 SCHOOL", "menu:school"), button("🌙 NIGHT", "menu:night")),
                    buttonRow(button("☀️ Повседневная", "menu:daily")),
                    buttonRow(button("⬅️ Назад", "menu:home"))
            );
            case "school" -> {
                editMessage(update, "🎒 Запускаю мастер SCHOOL...");
                requestAutomationWizard(AutomationKind.SCHOOL, "");
            }
            case "night" -> {
                editMessage(update, "🌙 Запускаю мастер NIGHT...");
                requestAutomationWizard(AutomationKind.NIGHT, "");
            }
            case "daily" -> {
                editMessage(update, "☀️ Запускаю мастер повседневной сессии...");
                requestAutomationWizard(AutomationKind.DAILY, "");
            }
            case "ft" -> requestServerConnect("mc.funtime.su");
            case "version" -> sendVersionStatus(update);
            case "help" -> editMessage(
                    update,
                    buildHelpText(),
                    buttonRow(button("⬅️ Назад", "menu:home"))
            );
            case "close" -> editMessage(update, "Меню закрыто. Для открытия используйте /menu.");
            default -> editMessage(update, "ℹ️ Эта кнопка больше не актуальна.",
                    buttonRow(button("🏠 Меню", "menu:home")));
        }
    }

    private void sendBotsMenu(BotUpdate update) {
        List<BotSession> sessions = FluxVisualsClient.MULTI_BOT_MANAGER.getSessions();
        BotSession activeSession = FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession();
        String tgTarget = FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetName();

        StringBuilder text = new StringBuilder("🤖 УПРАВЛЕНИЕ БОТАМИ (MultiBot)\n\n");
        if (sessions.isEmpty()) {
            text.append("ℹ️ Сейчас нет запущенных сессий ботов.\n"
                    + "Вы можете подключить ботов через пресеты или команду в клиенте: .bot add <ник>.");
        } else {
            text.append("📋 Подключенные аккаунты:\n");
            for (BotSession session : sessions) {
                String type = session.isMain() ? " [Основа]" : " [Бот]";
                String activeMark = session == activeSession ? " 👁️" : "";
                String tgMark = tgTarget.equalsIgnoreCase(session.getName()) ? " 🎯" : "";
                String statusEmoji = switch (session.getState()) {
                    case PLAYING -> "🟢";
                    case CONNECTING, LOGGING_IN -> "🟡";
                    case DISCONNECTED -> "🔴";
                };
                long bal = FluxVisualsClient.MULTI_BOT_MANAGER.resolveSessionBalance(session);
                String balStr = bal >= 0L ? formatPrice(bal) + "$" : "баланс ?";
                boolean ab = session.isMain()
                        ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isEnabled()
                        : (session.getAutoBuy() != null && session.getAutoBuy().isEnabled());
                String abStr = ab ? " | 🛒 AB: ВКЛ" : "";

                text.append(statusEmoji).append(" <b>").append(session.getName()).append("</b>")
                        .append(type).append(activeMark).append(tgMark).append("\n")
                        .append("   └ Статус: ").append(session.getState().name())
                        .append(" | 💰 ").append(balStr).append(abStr).append("\n");
            }
            text.append("\n👁️ — отображается в окне игры\n🎯 — выбран целью для общих команд TG\n\n")
                    .append("Нажмите на имя бота для управления:");
        }

        List<InlineButton[]> rowsList = new ArrayList<>();
        for (BotSession session : sessions) {
            String name = session.getName();
            String icon = session.getState() == BotSession.State.PLAYING ? "🟢" : "🔴";
            rowsList.add(buttonRow(button(icon + " " + name + (session.isMain() ? " (Основа)" : ""), "bot:panel:" + name)));
        }

        rowsList.add(buttonRow(button("🔄 Обновить список", "menu:bots"), button("➕ Пресеты", "bot:presets_menu")));
        rowsList.add(buttonRow(button("🏠 Главное меню", "menu:home")));

        InlineButton[][] rows = rowsList.toArray(new InlineButton[0][]);
        if (update == null) {
            sendMessageWithButtonRows(text.toString(), rows);
        } else {
            editMessage(update, text.toString(), rows);
        }
    }

    private void sendBotControlPanel(BotUpdate update, String botName) {
        BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(botName);
        if (session == null) {
            String err = "⚠️ Бот " + botName + " не найден.";
            if (update != null) {
                editMessage(update, err, buttonRow(button("⬅️ К списку ботов", "menu:bots")));
            } else {
                sendMessageWithButtonRows(err, buttonRow(button("⬅️ К списку ботов", "menu:bots")));
            }
            return;
        }

        BotSession active = FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession();
        boolean isActive = session == active;
        boolean isTgTarget = FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetName().equalsIgnoreCase(session.getName());

        float hp = 0f, maxHp = 20f;
        int food = 20;
        int swords = 0;
        String screenTitle = "В мире";
        if (session.getPlayer() != null) {
            hp = session.getPlayer().getHealth();
            maxHp = session.getPlayer().getMaxHealth();
            food = session.getPlayer().getHungerManager().getFoodLevel();
            swords = countInventorySwords(session.getPlayer().getInventory());
        }
        if (session.getScreen() != null) {
            screenTitle = formatScreenTitle(session.getScreen());
        }

        long balance = FluxVisualsClient.MULTI_BOT_MANAGER.resolveSessionBalance(session);
        boolean abEnabled = session.isMain()
                ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isEnabled()
                : (session.getAutoBuy() != null && session.getAutoBuy().isEnabled());
        boolean arEnabled = session.isMain()
                ? FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().isEnabled()
                : (session.getAutoResellAFK() != null && session.getAutoResellAFK().isEnabled());

        String statusEmoji = session.getState() == BotSession.State.PLAYING ? "🟢" : "🔴";
        StringBuilder sb = new StringBuilder();
        sb.append("🎛️ ПАНЕЛЬ УПРАВЛЕНИЯ: <b>").append(session.getName()).append("</b>\n\n")
                .append("👤 Тип: ").append(session.isMain() ? "Основной аккаунт" : "Фоновый бот").append("\n")
                .append("📶 Статус: ").append(statusEmoji).append(" ").append(session.getState().name()).append("\n")
                .append("🌐 Адрес/Сервер: ").append(session.getAddress().isBlank() ? "локально" : session.getAddress()).append("\n")
                .append("❤️ Здоровье: ").append(String.format(Locale.ROOT, "%.1f/%.1f", hp, maxHp))
                .append(" | 🍖 Голод: ").append(food).append("/20\n")
                .append("💰 Баланс: ").append(balance >= 0L ? formatPrice(balance) + "$" : "неизвестен").append("\n")
                .append("🗡️ Мечей в инвентаре: ").append(swords).append(" шт.\n")
                .append("🖥️ Текущий экран: ").append(screenTitle).append("\n")
                .append("🛒 AutoBuy: ").append(abEnabled ? "▶️ ВКЛЮЧЁН" : "⏹️ ВЫКЛЮЧЕН").append("\n")
                .append("🔁 AutoResell: ").append(arEnabled ? "▶️ ВКЛЮЧЁН" : "⏹️ ВЫКЛЮЧЕН").append("\n")
                .append("👁️ Виден в игре: ").append(isActive ? "✅ Да" : "❌ Нет (в фоне)").append("\n")
                .append("🎯 Цель для общих команд TG: ").append(isTgTarget ? "✅ Да" : "❌ Нет");

        InlineButton photoBtn = button("📸 Скриншот инвентаря", "bot:photo:" + session.getName());
        InlineButton refreshBtn = button("🔄 Обновить", "bot:panel:" + session.getName());

        InlineButton abToggleBtn = button(abEnabled ? "🛒 AutoBuy: ВЫКЛ" : "🛒 AutoBuy: ВКЛ", "bot:toggle_ab:" + session.getName());
        InlineButton arToggleBtn = button(arEnabled ? "🔁 AutoResell: ВЫКЛ" : "🔁 AutoResell: ВКЛ", "bot:toggle_ar:" + session.getName());

        InlineButton anarchyBtn = button("⚔️ Сменить анархию", "bot:anarchy_menu:" + session.getName());
        InlineButton sayBtn = button("💬 Сказать в чат / Команда", "bot:say_prompt:" + session.getName());

        InlineButton tgTargetBtn = button(isTgTarget ? "🎯 Цель TG: активна" : "🎯 Сделать целью TG", "bot:tg_target:" + session.getName());
        InlineButton switchActiveBtn = button("👁️ Переключить экран", "bot:switch_active:" + session.getName());

        InlineButton reconnectBtn = button("🔄 Реконнект", "bot:reconnect:" + session.getName());
        InlineButton disconnectBtn = button("🛑 Отключить бота", "bot:disconnect:" + session.getName());

        InlineButton backBtn = button("◀️ К списку ботов", "menu:bots");
        InlineButton homeBtn = button("🏠 Главное меню", "menu:home");

        List<InlineButton[]> rows = new ArrayList<>();
        rows.add(buttonRow(photoBtn, refreshBtn));
        rows.add(buttonRow(abToggleBtn, arToggleBtn));
        rows.add(buttonRow(anarchyBtn, sayBtn));
        rows.add(buttonRow(tgTargetBtn, switchActiveBtn));
        if (!session.isMain()) {
            rows.add(buttonRow(reconnectBtn, disconnectBtn));
        }
        rows.add(buttonRow(backBtn, homeBtn));

        if (update == null) {
            sendMessageWithButtonRows(sb.toString(), rows.toArray(new InlineButton[0][]));
        } else {
            editMessage(update, sb.toString(), rows.toArray(new InlineButton[0][]));
        }
    }

    public static String formatScreenTitle(Screen screen) {
        if (screen == null) {
            return "В мире (без окон)";
        }
        String title = screen.getTitle() != null ? screen.getTitle().getString().trim() : "";
        if (!title.isBlank() && !title.startsWith("class_") && !title.startsWith("net.minecraft.")) {
            return title;
        }
        String className = screen.getClass().getSimpleName();
        if (className.equals("class_434") || className.contains("GenericContainer")) {
            return !title.isBlank() ? title : "Контейнер / Меню сервера";
        }
        if (className.equals("class_490") || className.contains("InventoryScreen")) {
            return "Инвентарь игрока (E)";
        }
        if (className.equals("class_408") || className.contains("ChatScreen")) {
            return "Чат";
        }
        if (className.equals("class_433") || className.contains("GameMenuScreen")) {
            return "Меню паузы (ESC)";
        }
        if (className.contains("ClickGui")) {
            return "ClickGUI";
        }
        if (className.equals("class_471") || className.contains("AnvilScreen")) {
            return "Наковальня";
        }
        if (className.equals("class_485") || className.contains("CraftingScreen")) {
            return "Верстак";
        }
        if (className.equals("class_465") || className.contains("HandledScreen")) {
            return !title.isBlank() ? title : "Интерфейс контейнера";
        }
        return !title.isBlank() ? title : "Интерфейс (" + className + ")";
    }

    private void sendBotInventoryPhoto(String botName) {
        BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(botName);
        if (session == null) {
            sendMessage("⚠️ Бот <b>" + botName + "</b> не найден.");
            return;
        }

        if (session.getPlayer() == null) {
            sendMessage("⚠️ Бот <b>" + botName + "</b> ещё не загрузился в мир.");
            return;
        }

        int swords = countInventorySwords(session.getPlayer().getInventory());
        long bal = FluxVisualsClient.MULTI_BOT_MANAGER.resolveSessionBalance(session);
        String screenTitle = formatScreenTitle(session.getScreen());
        String accountLabel = session.getName() + (session.isMain() ? " [Основа]" : " [Бот]");

        String caption = "🎒 Настоящий скриншот (<b>" + accountLabel + "</b>)\n"
                + "🖥️ Экран: " + screenTitle + "\n"
                + "💰 Баланс: " + (bal >= 0L ? formatPrice(bal) + "$" : "неизвестен") + "\n"
                + "⚔️ Незеритовых мечей: " + swords + " шт.\n"
                + "🕒 Время: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT);

        FluxVisualsClient.MULTI_BOT_MANAGER.requestRealBotScreenshot(session, true, png -> {
            if (png != null && png.length > 0) {
                MinecraftClient mc = MinecraftClient.getInstance();
                int w = mc != null && mc.getWindow() != null ? mc.getWindow().getFramebufferWidth() : 1920;
                int h = mc != null && mc.getWindow() != null ? mc.getWindow().getFramebufferHeight() : 1080;
                sendScreenshot(png, w, h, caption);
            } else {
                byte[] fallback = BotInventoryRenderer.renderInventoryPng(session);
                if (fallback != null && fallback.length > 0) {
                    sendScreenshot(fallback, 176 * 3 + 32, 166 * 3 + 72 + 32, caption);
                } else {
                    sendMessage("⚠️ Не удалось сформировать снимок инвентаря бота.");
                }
            }
        });
    }

    private void sendBotAnarchyMenu(BotUpdate update, String botName) {
        BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(botName);
        if (session == null) {
            editMessage(update, "⚠️ Бот " + botName + " не найден.", buttonRow(button("⬅️ К списку ботов", "menu:bots")));
            return;
        }

        String text = "⚔️ ВЫБОР АНАРХИИ ДЛЯ <b>" + session.getName() + "</b>\n\n"
                + "Нажмите на номер анархии для мгновенного перехода:";

        List<InlineButton[]> rows = new ArrayList<>();
        rows.add(buttonRow(
                button("101", "bot:anarchy_set:" + session.getName() + ":101"),
                button("102", "bot:anarchy_set:" + session.getName() + ":102"),
                button("103", "bot:anarchy_set:" + session.getName() + ":103"),
                button("104", "bot:anarchy_set:" + session.getName() + ":104")
        ));
        rows.add(buttonRow(
                button("105", "bot:anarchy_set:" + session.getName() + ":105"),
                button("106", "bot:anarchy_set:" + session.getName() + ":106"),
                button("107", "bot:anarchy_set:" + session.getName() + ":107"),
                button("108", "bot:anarchy_set:" + session.getName() + ":108")
        ));
        rows.add(buttonRow(
                button("201", "bot:anarchy_set:" + session.getName() + ":201"),
                button("202", "bot:anarchy_set:" + session.getName() + ":202"),
                button("203", "bot:anarchy_set:" + session.getName() + ":203"),
                button("204", "bot:anarchy_set:" + session.getName() + ":204")
        ));
        rows.add(buttonRow(
                button("301", "bot:anarchy_set:" + session.getName() + ":301"),
                button("302", "bot:anarchy_set:" + session.getName() + ":302"),
                button("303", "bot:anarchy_set:" + session.getName() + ":303"),
                button("304", "bot:anarchy_set:" + session.getName() + ":304")
        ));
        rows.add(buttonRow(
                button("⬅️ Назад к боту", "bot:panel:" + session.getName()),
                button("📋 К списку ботов", "menu:bots")
        ));

        editMessage(update, text, rows.toArray(new InlineButton[0][]));
    }

    private void sendPresetsMenu(BotUpdate update) {
        List<String> presets = FluxVisualsClient.MULTI_BOT_MANAGER.getBotPresetNames();
        StringBuilder text = new StringBuilder("📁 ПРЕСЕТЫ БОТОВ\n\n");
        if (presets.isEmpty()) {
            text.append("ℹ️ Сохранённых пресетов пока нет.\n"
                    + "Создать пресет в игре: .bot preset save <имя>");
        } else {
            text.append("Выберите пресет для запуска группы ботов:");
        }

        List<InlineButton[]> rows = new ArrayList<>();
        for (String preset : presets) {
            rows.add(buttonRow(button("🚀 Запустить: " + preset, "bot:preset_load:" + preset)));
        }
        rows.add(buttonRow(button("⬅️ К списку ботов", "menu:bots"), button("🏠 Главное меню", "menu:home")));

        editMessage(update, text.toString(), rows.toArray(new InlineButton[0][]));
    }

    private void handleMultiBotCallback(BotUpdate update, String payload) {
        if (payload.startsWith("panel:")) {
            String name = payload.substring("panel:".length());
            sendBotControlPanel(update, name);
            return;
        }
        if (payload.startsWith("photo:")) {
            String name = payload.substring("photo:".length());
            sendBotInventoryPhoto(name);
            return;
        }
        if (payload.startsWith("toggle_ab:")) {
            String name = payload.substring("toggle_ab:".length());
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
            if (session != null) {
                if (session.isMain()) {
                    AutoBuy ab = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
                    ab.setEnabled(!ab.isEnabled());
                } else if (session.getAutoBuy() != null) {
                    session.getAutoBuy().setEnabledSilently(!session.getAutoBuy().isEnabled());
                }
            }
            sendBotControlPanel(update, name);
            return;
        }
        if (payload.startsWith("toggle_ar:")) {
            String name = payload.substring("toggle_ar:".length());
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
            if (session != null) {
                if (session.isMain()) {
                    AutoResellAFK ar = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
                    ar.setEnabled(!ar.isEnabled());
                } else if (session.getAutoResellAFK() != null) {
                    session.getAutoResellAFK().setEnabledSilently(!session.getAutoResellAFK().isEnabled());
                }
            }
            sendBotControlPanel(update, name);
            return;
        }
        if (payload.startsWith("anarchy_menu:") || payload.startsWith("anarchy_prompt:")) {
            String name = payload.startsWith("anarchy_menu:")
                    ? payload.substring("anarchy_menu:".length())
                    : payload.substring("anarchy_prompt:".length());
            pendingBotInput = PendingBotInput.BOT_ANARCHY_NUMBER;
            pendingBotTarget = name;
            editMessage(update, "⚔️ Введите номер анархии для бота <b>" + name + "</b> (например, напишите <code>219</code> или <code>101</code>):",
                    buttonRow(button("⬅️ В панель бота", "bot:panel:" + name), button("📋 К списку ботов", "menu:bots")));
            return;
        }
        if (payload.startsWith("anarchy_set:")) {
            String[] parts = payload.split(":", 3);
            if (parts.length == 3) {
                String name = parts[1];
                String num = parts[2];
                BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
                if (session != null) {
                    FluxVisualsClient.MULTI_BOT_MANAGER.sendBotChat(session, "/an" + num);
                    editMessage(update, "⚔️ Бот <b>" + session.getName() + "</b> переходит на <b>/an" + num + "</b>!",
                            buttonRow(button("🔄 В панель бота", "bot:panel:" + session.getName()),
                                    button("📋 К списку ботов", "menu:bots")));
                    return;
                }
            }
            sendBotsMenu(update);
            return;
        }
        if (payload.startsWith("say_prompt:")) {
            String name = payload.substring("say_prompt:".length());
            pendingBotInput = PendingBotInput.BOT_CHAT_MESSAGE;
            pendingBotTarget = name;
            editMessage(update, "💬 Отправьте в этот чат сообщение или команду (например <code>/ah</code> или <code>/spawn</code>), которую выполнит бот <b>" + name + "</b>:",
                    buttonRow(button("⬅️ Отмена", "bot:panel:" + name)));
            return;
        }
        if (payload.startsWith("tg_target:")) {
            String name = payload.substring("tg_target:".length());
            FluxVisualsClient.MULTI_BOT_MANAGER.selectTelegramTarget(name);
            sendBotControlPanel(update, name);
            return;
        }
        if (payload.startsWith("switch_active:")) {
            String name = payload.substring("switch_active:".length());
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
            if (session != null) {
                FluxVisualsClient.MULTI_BOT_MANAGER.switchActiveTo(session);
            }
            sendBotControlPanel(update, name);
            return;
        }
        if (payload.startsWith("reconnect:")) {
            String name = payload.substring("reconnect:".length());
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
            if (session != null) {
                FluxVisualsClient.MULTI_BOT_MANAGER.reconnectBotSession(session);
                editMessage(update, "🔄 Переподключаю бота <b>" + name + "</b>...",
                        buttonRow(button("🔄 Обновить панель", "bot:panel:" + name), button("📋 К списку ботов", "menu:bots")));
                return;
            }
            sendBotsMenu(update);
            return;
        }
        if (payload.startsWith("disconnect:")) {
            String name = payload.substring("disconnect:".length());
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(name);
            if (session != null) {
                FluxVisualsClient.MULTI_BOT_MANAGER.disconnectBotSession(session);
                editMessage(update, "🛑 Бот <b>" + name + "</b> отключён.",
                        buttonRow(button("📋 К списку ботов", "menu:bots")));
                return;
            }
            sendBotsMenu(update);
            return;
        }
        if (payload.equals("presets_menu")) {
            sendPresetsMenu(update);
            return;
        }
        if (payload.startsWith("preset_load:")) {
            String name = payload.substring("preset_load:".length());
            FluxVisualsClient.MULTI_BOT_MANAGER.loadPreset(name);
            editMessage(update, "🚀 Запущен пресет ботов: <b>" + name + "</b>!",
                    buttonRow(button("📋 К списку ботов", "menu:bots")));
            return;
        }
        sendBotsMenu(update);
    }

    private void handleBotCallback(BotUpdate update) {
        String data = update.text();
        if (data == null || data.isBlank()) {
            return;
        }
        if (data.startsWith("wizard:")) {
            handleWizardCallback(update, data);
            return;
        }
        if (data.startsWith("menu:")) {
            handleMenuCallback(update, data.substring("menu:".length()));
            return;
        }
        if (data.startsWith("bot:")) {
            handleMultiBotCallback(update, data.substring("bot:".length()));
            return;
        }
        if (data.startsWith("purchase:sell:")) {
            try {
                pendingCustomSellPurchaseIndex = Integer.parseInt(data.substring("purchase:sell:".length()));
                pendingBotInput = PendingBotInput.CUSTOM_SELL_PRICE;
                editMessage(update, update.sourceMessageText()
                        + "\n\n💰 Отправьте цену, по которой нужно выставить этот меч.");
            } catch (NumberFormatException ignored) {
                editMessage(update, "⚠️ Не удалось определить выбранный меч.");
            }
            return;
        }
        if (data.startsWith("confirm:")) {
            handleConfirmationCallback(update, data);
        }
    }

    private void handleConfirmationCallback(BotUpdate update, String data) {
        String[] parts = data.split(":", 4);
        if (parts.length != 4) {
            editMessage(update, "ℹ️ Некорректное подтверждение.");
            return;
        }
        String action = parts[1];
        String id = parts[2];
        boolean confirmed = parts[3].equals("yes");
        long now = System.currentTimeMillis();
        if (action.equals("crash")) {
            if (!id.equals(closeConfirmationId) || now > closeConfirmationDeadline) {
                editMessage(update, "⌛ Подтверждение закрытия устарело.",
                        buttonRow(button("🏠 Меню", "menu:home")));
                return;
            }
            closeConfirmationId = "";
            closeConfirmationDeadline = 0L;
            if (!confirmed) {
                editMessage(update, "✅ Закрытие Minecraft отменено.",
                        buttonRow(button("🏠 Меню", "menu:home")));
                return;
            }
            editMessage(update, "🛑 Закрываю Minecraft...");
            performMinecraftClose();
            return;
        }
        if (action.equals("panic")) {
            if (!id.equals(panicConfirmationId) || now > panicConfirmationDeadline) {
                editMessage(update, "⌛ Подтверждение PANIC устарело.",
                        buttonRow(button("🏠 Меню", "menu:home")));
                return;
            }
            panicConfirmationId = "";
            panicConfirmationDeadline = 0L;
            if (!confirmed) {
                editMessage(update, "✅ Экстренная остановка отменена.",
                        buttonRow(button("🏠 Меню", "menu:home")));
                return;
            }
            performPanicStop();
            editMessage(update, "🛑 Все автоматические действия остановлены.",
                    buttonRow(button("🏠 Меню", "menu:home")));
        }
    }

    private void handleWizardCallback(BotUpdate update, String data) {
        String[] parts = data.split(":", 5);
        if (parts.length != 5) {
            editMessage(update, "ℹ️ Некорректная кнопка мастера.");
            return;
        }
        AutomationWizard wizard = automationWizard;
        if (wizard == null
                || !wizard.id.equals(parts[1])
                || !wizard.step.name().equals(parts[2])) {
            editMessage(update, "ℹ️ Эта кнопка мастера больше не актуальна.",
                    buttonRow(button("🏠 Меню", "menu:home")));
            return;
        }
        String action = parts[4];
        if (action.equals("cancel")) {
            automationWizard = null;
            persistRuntimeState();
            editMessage(update, "❌ Настройка автономного режима отменена.",
                    buttonRow(button("🏠 Меню", "menu:home")));
            return;
        }
        if (action.equals("back")) {
            AutomationWizardStep previous = previousWizardStep(wizard);
            if (previous != null) {
                wizard.step = previous;
                persistRuntimeState();
            }
            editMessage(update, update.sourceMessageText() + "\n\n⬅️ Возврат к предыдущему шагу.");
            sendAutomationWizardPrompt(wizard);
            return;
        }
        editMessage(update, update.sourceMessageText() + "\n\n✅ Выбрано: " + wizardChoiceLabel(action));
        handleAutomationWizardAnswer(action);
    }

    private static String wizardChoiceLabel(String value) {
        return switch (value) {
            case "yes", "on", "start" -> "Да";
            case "no", "off" -> "Нет";
            default -> value;
        };
    }

    private static AutomationWizardStep previousWizardStep(AutomationWizard wizard) {
        return switch (wizard.step) {
            case DURATION -> null;
            case BUY_PRICE -> AutomationWizardStep.DURATION;
            case AUCTION_SLOTS -> AutomationWizardStep.BUY_PRICE;
            case LIMITS, BUDGET -> AutomationWizardStep.AUCTION_SLOTS;
            case PURCHASE_LIMIT -> AutomationWizardStep.BUDGET;
            case PROFIT_TARGET -> AutomationWizardStep.PURCHASE_LIMIT;
            case RENAME -> AutomationWizardStep.PROFIT_TARGET;
            case AUTO_RESELL -> AutomationWizardStep.RENAME;
            case ANARCHY_SWITCH -> AutomationWizardStep.AUTO_RESELL;
            case AUTO_SELL -> AutomationWizardStep.ANARCHY_SWITCH;
            case SELL_PRICE -> AutomationWizardStep.AUTO_SELL;
            case ANARCHIES -> wizard.autoSellEnabled
                    ? AutomationWizardStep.SELL_PRICE
                    : AutomationWizardStep.AUTO_SELL;
            case ANARCHY_DELAY -> AutomationWizardStep.ANARCHIES;
            case RESELL_INTERVAL -> AutomationWizardStep.ANARCHY_DELAY;
            case SELF_MODE -> AutomationWizardStep.RESELL_INTERVAL;
            case CONFIRM -> AutomationWizardStep.SELF_MODE;
        };
    }

    private void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank() || !beginTelegramAsyncRequest("answerCallbackQuery")) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("callback_query_id", callbackQueryId);
        HttpRequest request = jsonRequest(botToken, "answerCallbackQuery", body.toString(), Duration.ofSeconds(15));
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        if (throwable == null) {
                            handleApiResponse("answerCallbackQuery", response);
                        } else {
                            recordTelegramNetworkFailure("answerCallbackQuery", throwable);
                        }
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            recordTelegramNetworkFailure("answerCallbackQuery", ex);
        }
    }

    private void sendHelpMessage() {
        sendMessageWithButtonRows(
                buildHelpText(),
                buttonRow(button("🏠 Меню", "menu:home"))
        );
    }

    private static String buildHelpText() {
        return "🤖✨ КОМАНДЫ FUGA BOT\n\n"
                + "▶️ /play — включить AutoBuy и начать поиск мечей\n"
                + "⏹️ /stop — полностью выключить AutoBuy\n"
                + "⏸️ /pause — временно остановить автоматизацию\n"
                + "▶️ /resume — продолжить после паузы\n"
                + "🛑 /panic — экстренно остановить все действия\n"
                + "💸 /lowbalance on|off — включить или отключить остановку при низком балансе\n"
                + "🎒 /school [время] — настроить автономную сессию, пример: /school 3h\n"
                + "   /school status — проверить состояние и оставшееся время\n"
                + "🌙 /night [время] — настроить автономную ночную сессию\n"
                + "   /night status — проверить состояние и оставшееся время\n"
                + "❌ /cancel — отменить текущий мастер настройки\n"
                + "📊 /status — статус и статистика AutoBuy\n"
                + "🎒 /inventory — мечи и состояние хранилища\n"
                + "💰 /bal — проверить текущий баланс на сервере\n"
                + "🏷️ /price <цена> — изменить максимальную цену покупки меча\n"
                + "   Пример: /price 3.5kk\n"
                + "📶 /ping — проверить текущий пинг на сервере\n"
                + "📸 /screen — получить скриншот Minecraft\n"
                + "⚔️ /sell <цена> [количество|all] — выставить указанное количество мечей\n"
                + "   Примеры: /sell 3.5kk 1, /sell 3.5kk all\n"
                + "🤖 /autosell <цена> — автоматически продавать мечи после покупки\n"
                + "   Пример: /autosell 3.5kk\n"
                + "⚠️ /sell и /autosell могут нарушать правила сервера и привести к блокировке аккаунта\n"
                + "🛑 /stopautosell — отключить автоматическую продажу\n"
                + "🔕 /nf — включить или выключить автоматические уведомления\n"
                + "❌ /crash — безопасно закрыть Minecraft после подтверждения\n"
                + "🚪 /leave — отправить итоговый отчёт и перейти в хаб\n"
                + "ℹ️ /version — версия клиента и состояние Telegram\n"
                + "🎮 /bot <ник|main> — выбрать аккаунт для команд Telegram\n"
                + "🎛️ /menu — открыть интерактивное меню\n"
                + "❓ /help — показать описание всех команд";
    }

    private void toggleNotifications() {
        boolean next = !notificationsEnabled;
        setNotificationsEnabled(next);
        sendMessage(next
                ? "🔔 Автоматические уведомления включены."
                : "🔕 Автоматические уведомления выключены.\n"
                        + "Бот будет отвечать только на ваши команды.");
    }

    private void requestBalanceFromMinecraft() {
        requestBalanceCheck(true);
    }

    private void requestPingFromMinecraft() {
        if (pingRequestPending) {
            sendMessage("⏳ Пинг уже проверяется. Подождите ответ сервера.");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft сейчас не подключён к серверу, поэтому проверить пинг нельзя.");
            return;
        }

        pingRequestPending = true;
        pingRequestDeadline = System.currentTimeMillis() + PING_RESPONSE_TIMEOUT_MS;
        sendMessage("📡 Проверяю пинг на сервере...");
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            if (!pingRequestPending) {
                return;
            }
            if (minecraft.player == null || minecraft.getNetworkHandler() == null) {
                pingRequestPending = false;
                pingRequestDeadline = 0L;
                sendMessage("⚠️ Соединение с сервером потеряно. Пинг не проверен.");
                return;
            }
            minecraft.getNetworkHandler().sendChatCommand("ping");
        });
    }

    private void requestBalanceCheck(boolean replyToUser) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            synchronized (balanceLock) {
                if (replyToUser) {
                    balanceReplyRequested = true;
                }
            }
            completeBalanceCheck(null, "⚠️ Minecraft сейчас не подключён к серверу, поэтому проверить баланс нельзя.");
            return;
        }

        boolean alreadyPending;
        synchronized (balanceLock) {
            if (replyToUser) {
                balanceReplyRequested = true;
            }
            alreadyPending = balanceCheckPending;
            if (!alreadyPending) {
                balanceCheckPending = true;
                balanceRequestDeadline = System.currentTimeMillis() + BALANCE_RESPONSE_TIMEOUT_MS;
            }
        }
        if (alreadyPending) {
            if (replyToUser) {
                sendMessage("⏳ Баланс уже проверяется. Подождите ответ сервера.");
            }
            return;
        }
        if (replyToUser) {
            sendMessage("🔎💰 Проверяю баланс в Minecraft...");
        }
        balanceCommandDueAt = System.currentTimeMillis()
                + randomDelay(BALANCE_COMMAND_DELAY_MIN_MS, BALANCE_COMMAND_DELAY_MAX_MS);
    }

    private void processBalanceCommand(MinecraftClient minecraft, long now) {
        if (balanceCommandDueAt <= 0L || now < balanceCommandDueAt) {
            return;
        }
        balanceCommandDueAt = 0L;
        if (!isBalanceCheckPending()) {
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            completeBalanceCheck(null, "⚠️ Соединение с сервером потеряно. Баланс не проверен.");
            return;
        }
        AutoBuy autoBuy = telegramAutoBuy();
        autoBuy.refreshBalanceFromScoreboardNow(minecraft);
        long balance = autoBuy.getLastKnownBalance();
        if (balance >= 0L) {
            completeBalanceCheck(balance, null);
        }
    }

    private void handleBalanceTimeout() {
        boolean timedOut;
        synchronized (balanceLock) {
            timedOut = balanceCheckPending && System.currentTimeMillis() >= balanceRequestDeadline;
        }
        if (timedOut) {
            completeBalanceCheck(null, "⚠️ Не удалось получить баланс за 8 секунд.\n"
                    + "Проверьте подключение к серверу и доступность команды /bal.");
        }
    }

    private void handlePingTimeout() {
        if (!pingRequestPending || System.currentTimeMillis() < pingRequestDeadline) {
            return;
        }
        pingRequestPending = false;
        pingRequestDeadline = 0L;
        sendMessage("⚠️ Сервер не ответил на команду /ping за 8 секунд.");
    }

    private boolean isBalanceCheckPending() {
        synchronized (balanceLock) {
            return balanceCheckPending;
        }
    }

    private void completeBalanceCheck(Long balance, String userError) {
        boolean replyToUser;
        boolean replyWithStats;
        List<PendingPurchaseNotification> purchases;
        synchronized (balanceLock) {
            replyToUser = balanceReplyRequested;
            replyWithStats = statsReplyRequested;
            purchases = List.copyOf(pendingPurchaseNotifications);
            balanceCheckPending = false;
            balanceReplyRequested = false;
            statsReplyRequested = false;
            balanceRequestDeadline = 0L;
            pendingPurchaseNotifications.clear();
            balanceCommandDueAt = 0L;
        }
        if (replyToUser) {
            if (balance != null) {
                sendMessage("💰 Ваш баланс сейчас: " + formatPrice(balance) + "$");
            } else {
                sendMessage(userError == null ? "⚠️ Не удалось определить текущий баланс." : userError);
            }
        }
        if (replyWithStats) {
            sendMessageWithButtonRows(
                    buildAutoBuyStats(
                            telegramAutoBuy().getSessionStats(),
                            balance
                    ),
                    buttonRow(button("🔄 Обновить", "menu:status")),
                    buttonRow(button("🏠 Меню", "menu:home"))
            );
        }
        for (PendingPurchaseNotification purchase : purchases) {
            String purchaseText = buildPurchaseMessage(
                    purchase.itemName(), purchase.price(), purchase.seller(), balance
            );
            // Последняя подтверждённая покупка всегда находится первой в истории.
            // Кнопка остаётся доступной даже при включённом AutoSell: это ручной
            // аварийный способ выставить конкретный купленный меч по своей цене.
            sendMessageWithButtonRows(
                    purchaseText,
                    buttonRow(button("💰 Продать этот меч", "purchase:sell:0"))
            );
        }
    }

    private void requestLowBalanceGuard(String argument) {
        String value = argument == null ? "" : argument.trim().toLowerCase(Locale.ROOT);
        AutoBuy autoBuy = telegramAutoBuy();
        if (value.isEmpty()) {
            sendMessage("💸 Режим низкого баланса: "
                    + (autoBuy.isLowBalanceGuardEnabled() ? "ВКЛЮЧЁН" : "ОТКЛЮЧЁН")
                    + "\n🚪 Хаб-сейф при пустом хранилище всегда остаётся активен."
                    + "\nИспользование: /lowbalance on или /lowbalance off");
            return;
        }
        Boolean enabled = parseOnOff(value);
        if (enabled == null) {
            sendMessage("⚠️ Использование: /lowbalance on или /lowbalance off");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            autoBuy.setLowBalanceGuardEnabled(enabled);
            sendMessage(enabled
                    ? "✅ Режим низкого баланса включён.\n"
                            + "При балансе ниже price AutoBuy перейдёт к перевыставлению."
                    : "✅ Режим низкого баланса отключён.\n"
                            + "AutoBuy продолжит покупать даже при балансе ниже price.\n"
                            + "🚪 Хаб-сейф при пустом хранилище остаётся активен.");
        });
    }

    private static String extractReplyText(JsonObject message) {
        if (message == null || !message.has("reply_to_message")
                || !message.get("reply_to_message").isJsonObject()) {
            return "";
        }
        JsonObject reply = message.getAsJsonObject("reply_to_message");
        return reply.has("text") ? reply.get("text").getAsString() : "";
    }

    private void requestAuctionSlots(String argument) {
        String value = argument == null ? "" : argument.trim();
        if (value.isEmpty()) {
            sendMessage("🎟️ Слоты аукциона: "
                    + (auctionSlotLimit > 0 ? auctionSlotLimit : "не указаны")
                    + "\nАктивных лотов учтено: " + auctionActiveListings
                    + "\nИспользование: /slots <количество>");
            return;
        }
        try {
            int slots = Integer.parseInt(value);
            if (slots < 1 || slots > 1000) {
                throw new NumberFormatException();
            }
            synchronized (sellLock) {
                auctionSlotLimit = slots;
                auctionActiveListings = Math.min(auctionActiveListings, slots);
                autoSellStorageKnownFull = auctionActiveListings >= slots;
                if (autoSellEnabled && countInventorySwordsSafe() > 0) {
                    autoSellAfterPurchaseQueued = true;
                }
            }
            persistRuntimeState();
            sendMessage("✅ Лимит аукциона установлен: " + slots
                    + "\nСвободно по учёту: " + Math.max(0, slots - auctionActiveListings));
        } catch (NumberFormatException ignored) {
            sendMessage("⚠️ Укажите целое количество слотов от 1 до 1000.");
        }
    }

    private void requestBanSeller(String argument) {
        AutoBuy autoBuy = telegramAutoBuy();
        String seller = argument == null ? "" : argument.trim();
        if (seller.isEmpty()) {
            List<String> sellers = autoBuy.getBannedSellers();
            sendMessage("🚫 Заблокированные продавцы: "
                    + (sellers.isEmpty() ? "список пуст" : String.join(", ", sellers))
                    + "\nИспользование: /ban <ник>");
            return;
        }
        sendMessage(autoBuy.addBannedSeller(seller)
                ? "✅ Продавец " + seller + " заблокирован для покупок."
                : "ℹ️ Ник некорректен или уже находится в списке.");
    }

    private void requestUnbanSeller(String argument) {
        String seller = argument == null ? "" : argument.trim();
        if (seller.isEmpty()) {
            sendMessage("Использование: /unban <ник>");
            return;
        }
        telegramAutoBuy().removeBannedSeller(seller);
        sendMessage("✅ Запрет для продавца " + seller + " снят, если он был установлен.");
    }

    private void requestPurchaseInfo(BotUpdate update, String argument) {
        List<AutoBuy.RecentPurchase> purchases = telegramAutoBuy().getRecentPurchases();
        if (purchases.isEmpty()) {
            sendMessage("ℹ️ История купленных мечей пока пуста.");
            return;
        }
        int index = 0;
        String value = argument == null ? "" : argument.trim();
        if (!value.isEmpty()) {
            try {
                index = Math.max(0, Integer.parseInt(value) - 1);
            } catch (NumberFormatException ignored) {
                index = 0;
            }
        } else if (update != null && !update.replyToMessageText().isBlank()) {
            String reply = normalizeMessage(update.replyToMessageText());
            for (int i = 0; i < purchases.size(); i++) {
                AutoBuy.RecentPurchase purchase = purchases.get(i);
                if (reply.contains(normalizeMessage(purchase.seller()))
                        || purchase.price() >= 0L && reply.contains(formatPrice(purchase.price()))) {
                    index = i;
                    break;
                }
            }
        }
        if (index >= purchases.size()) {
            sendMessage("⚠️ В истории нет меча с таким номером.");
            return;
        }
        AutoBuy.RecentPurchase purchase = purchases.get(index);
        StringBuilder text = new StringBuilder("⚔️ ИНФОРМАЦИЯ О МЕЧЕ\n\n")
                .append("Название: ").append(purchase.stack().getName().getString()).append('\n')
                .append("Цена покупки: ").append(formatMoney(purchase.price())).append('\n')
                .append("Продавец: ").append(purchase.seller() == null ? "неизвестен" : purchase.seller()).append('\n')
                .append("Время покупки: ")
                .append(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(purchase.purchasedAt()),
                        java.time.ZoneId.systemDefault()
                ).format(DISPLAY_TIME_FORMAT))
                .append("\n\nЧары и данные:\n");
        if (purchase.tooltip().isEmpty()) {
            text.append("• данные tooltip отсутствуют\n");
        } else {
            for (Text line : purchase.tooltip()) {
                String clean = line.getString().trim();
                if (!clean.isEmpty() && !isTechnicalTooltipLine(clean)) {
                    text.append("• ").append(clean).append('\n');
                }
            }
        }
        if (telegramAutoBuy().isAutoResellEnabled()) {
            text.append("\nСтатус: ")
                    .append(telegramAutoBuy()
                            .isRecentPurchaseSold(purchase.purchasedAt()) ? "продан" : "не продан");
        }
        if (!autoSellEnabled) {
            sendMessageWithButtonRows(
                    text.toString(),
                    buttonRow(button("💰 Продать по своей цене", "purchase:sell:" + index))
            );
        } else {
            sendMessage(text.toString());
        }
    }

    private void requestServerConnect(String argument) {
        String addressText = argument == null ? "" : argument.trim();
        if (addressText.isEmpty() || !ServerAddress.isValid(addressText)) {
            sendMessage("⚠️ Использование: /connect <адрес[:порт]>");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            ServerAddress address = ServerAddress.parse(addressText);
            ServerInfo info = new ServerInfo("FUGA remote", addressText, ServerInfo.ServerType.OTHER);
            if (minecraft.getNetworkHandler() != null) {
                minecraft.getNetworkHandler().getConnection()
                        .disconnect(Text.literal("Remote reconnect"));
            }
            ConnectScreen.connect(
                    new TitleScreen(),
                    minecraft,
                    address,
                    info,
                    false,
                    new CookieStorage(Map.of())
            );
            sendMessage("🔌 Подключаюсь к " + addressText + "...");
        });
    }

    private void handlePendingBotInput(String text) {
        PendingBotInput input = pendingBotInput;
        pendingBotInput = PendingBotInput.NONE;
        if (input == PendingBotInput.AUTOSELL_PRICE) {
            requestAutoSellEnable(text);
            return;
        }
        if (input == PendingBotInput.CUSTOM_SELL_PRICE) {
            String price = normalizeSellPrice(text);
            if (price == null) {
                sendMessage("⚠️ Цена некорректна. Повторите команду /info и нажмите кнопку продажи.");
                return;
            }
            pendingCustomSellPurchaseIndex = -1;
            requestInventorySell(price + " 1", false);
            return;
        }
        if (input == PendingBotInput.BOT_CHAT_MESSAGE) {
            String target = pendingBotTarget;
            pendingBotTarget = "";
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(target);
            if (session == null) {
                sendMessage("⚠️ Бот " + target + " не найден.");
                return;
            }
            FluxVisualsClient.MULTI_BOT_MANAGER.sendBotChat(session, text);
            sendMessageWithButtonRows("💬 Отправлено от лица <b>" + session.getName() + "</b>:\n<code>" + text + "</code>",
                    buttonRow(button("🎛️ В панель бота", "bot:panel:" + session.getName()), button("📋 К списку ботов", "menu:bots")));
            return;
        }
        if (input == PendingBotInput.BOT_ANARCHY_NUMBER) {
            String target = pendingBotTarget;
            pendingBotTarget = "";
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findByName(target);
            if (session == null) {
                sendMessage("⚠️ Бот " + target + " не найден.");
                return;
            }
            String num = text.replaceAll("[^0-9]", "").trim();
            if (num.isBlank()) {
                sendMessageWithButtonRows("⚠️ Некорректный номер анархии. Введите только цифры (например <code>219</code>):",
                        buttonRow(button("⬅️ В панель бота", "bot:panel:" + session.getName()), button("📋 К списку ботов", "menu:bots")));
                return;
            }
            FluxVisualsClient.MULTI_BOT_MANAGER.sendBotChat(session, "/an" + num);
            sendMessageWithButtonRows("⚔️ Бот <b>" + session.getName() + "</b> переходит на <b>/an" + num + "</b>!",
                    buttonRow(button("🎛️ В панель бота", "bot:panel:" + session.getName()), button("📋 К списку ботов", "menu:bots")));
            return;
        }
    }

    private int countInventorySwordsSafe() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        return minecraft != null && minecraft.player != null
                ? countInventorySwords(minecraft.player.getInventory())
                : 0;
    }

    private void requestAutomationWizard(AutomationKind kind, String argument) {
        String value = argument == null ? "" : argument.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("status") || normalized.equals("check")
                || normalized.equals("проверка") || normalized.equals("статус")) {
            requestAutomationStatus(kind);
            return;
        }
        if (value.equalsIgnoreCase("stop") || value.equalsIgnoreCase("off")) {
            requestAutomationStop();
            return;
        }
        if (automationSession != null) {
            sendMessage("⏳ Автономная сессия уже работает.\n"
                    + "Для остановки используйте /" + kind.command() + " stop.");
            return;
        }

        AutomationWizard wizard = new AutomationWizard(kind);
        if (!value.isEmpty()) {
            Long duration = parseAutomationDuration(value);
            if (duration == null) {
                sendMessage("⚠️ Не удалось распознать длительность.\n"
                        + "Примеры: /" + kind.command() + " 3h, /" + kind.command() + " 90m, /"
                        + kind.command() + " 2ч");
                return;
            }
            wizard.durationMs = duration;
            wizard.step = AutomationWizardStep.BUY_PRICE;
        }
        automationWizard = wizard;
        persistRuntimeState();
        sendAutomationWizardPrompt(wizard);
    }

    private void cancelAutomationWizard() {
        if (automationWizard == null) {
            sendMessage("ℹ️ Активного мастера настройки нет.");
            return;
        }
        automationWizard = null;
        persistRuntimeState();
        sendMessage("❌ Настройка автономного режима отменена.");
    }

    private void requestAutomationStatus(AutomationKind requestedKind) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        Runnable statusTask = () -> {
            AutomationSession session = automationSession;
            AutoBuy autoBuy = telegramAutoBuy();
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            sendMessage(session == null
                    ? buildAutomationReadinessReport(requestedKind, minecraft, autoBuy, itemResorter)
                    : buildAutomationStatusReport(requestedKind, session, minecraft, autoBuy, itemResorter));
        };
        if (minecraft == null) {
            statusTask.run();
        } else {
            FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, statusTask);
        }
    }

    private void handleAutomationWizardAnswer(String answer) {
        AutomationWizard wizard = automationWizard;
        if (wizard == null) {
            return;
        }
        String value = answer == null ? "" : answer.trim();
        if (value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена")) {
            cancelAutomationWizard();
            return;
        }

        switch (wizard.step) {
            case DURATION -> {
                Long duration = parseAutomationDuration(value);
                if (duration == null) {
                    sendMessage("⚠️ Укажите время от 5 минут до 7 дней. Примеры: 3h, 90m, 2ч.");
                    return;
                }
                wizard.durationMs = duration;
                wizard.step = AutomationWizardStep.BUY_PRICE;
            }
            case BUY_PRICE -> {
                Long price = parsePurchasePrice(value);
                if (price == null || price <= 0L) {
                    sendMessage("⚠️ Укажите корректную максимальную цену покупки, например 3.5kk.");
                    return;
                }
                wizard.buyPrice = price;
                wizard.step = AutomationWizardStep.AUCTION_SLOTS;
            }
            case AUCTION_SLOTS -> {
                try {
                    int slots = Integer.parseInt(value);
                    if (slots < 1 || slots > 1000) {
                        throw new NumberFormatException();
                    }
                    wizard.auctionSlots = slots;
                    wizard.step = AutomationWizardStep.BUDGET;
                } catch (NumberFormatException ignored) {
                    sendMessage("⚠️ Укажите количество слотов аукциона от 1 до 1000.");
                    return;
                }
            }
            case LIMITS -> {
                if (!parseSessionLimits(value, wizard)) {
                    sendMessage("⚠️ Укажите настройки или нажмите «Пропустить». Пример: бюджет=50% покупки=10 заработать=500000");
                    return;
                }
                wizard.step = AutomationWizardStep.AUTO_SELL;
                if (wizard.kind == AutomationKind.NIGHT) {
                    wizard.autoSellEnabled = true;
                    wizard.step = AutomationWizardStep.SELL_PRICE;
                }
            }
            case BUDGET -> {
                if (value.equalsIgnoreCase("custom") || value.equalsIgnoreCase("своё значение")) {
                    sendMessage("✏️ Введите свой бюджет: сумму (например 3000000) или процент (например 50%).");
                    return;
                }
                if (isWizardSkip(value) || value.equalsIgnoreCase("max") || value.equalsIgnoreCase("максимум")) {
                    wizard.budgetSpec = "максимум";
                } else if (!isValidBudgetSpec(value)) {
                    sendMessage("⚠️ Введите бюджет числом или процентом, например 3000000 или 50%.");
                    return;
                } else {
                    wizard.budgetSpec = value;
                }
                wizard.step = AutomationWizardStep.PURCHASE_LIMIT;
            }
            case PURCHASE_LIMIT -> {
                if (value.equalsIgnoreCase("custom") || value.equalsIgnoreCase("своё значение")) {
                    sendMessage("✏️ Введите своё максимальное количество покупок числом, например 15.");
                    return;
                }
                if (isWizardSkip(value) || value.equalsIgnoreCase("max") || value.equalsIgnoreCase("максимум")) {
                    wizard.maxPurchases = 0;
                } else {
                    try {
                        int limit = Integer.parseInt(value);
                        if (limit < 1 || limit > 100_000) throw new NumberFormatException();
                        wizard.maxPurchases = limit;
                    } catch (NumberFormatException ignored) {
                        sendMessage("⚠️ Введите количество покупок от 1 до 100000 или нажмите «Максимум».");
                        return;
                    }
                }
                wizard.step = AutomationWizardStep.PROFIT_TARGET;
            }
            case PROFIT_TARGET -> {
                if (value.equalsIgnoreCase("custom") || value.equalsIgnoreCase("своё значение")) {
                    sendMessage("✏️ Введите свою цель заработка, например 500000 или 1kk.");
                    return;
                }
                if (isWizardSkip(value) || value.equalsIgnoreCase("max") || value.equalsIgnoreCase("без цели")) {
                    wizard.targetProfit = 0L;
                } else {
                    Long target = parsePurchasePrice(value);
                    if (target == null || target < 1L) {
                        sendMessage("⚠️ Введите цель заработка, например 500000 или 1kk.");
                        return;
                    }
                    wizard.targetProfit = target;
                }
                wizard.step = AutomationWizardStep.RENAME;
            }
            case RENAME -> {
                if (value.equalsIgnoreCase("enable") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("включить")) {
                    wizard.renameText = "";
                    sendMessage("✏️ Напишите текст, которым переименовывать мечи (например: FUGA). Отправьте /skip, чтобы оставить без ренейма.");
                    wizard.step = AutomationWizardStep.RENAME;
                    pendingWizardRenameInput = true;
                    return;
                }
                if (pendingWizardRenameInput) {
                    pendingWizardRenameInput = false;
                    if (isWizardSkip(value) || value.isBlank()) {
                        wizard.renameText = "";
                    } else {
                        wizard.renameText = value;
                    }
                    wizard.step = AutomationWizardStep.AUTO_RESELL;
                } else {
                    wizard.renameText = "";
                    wizard.step = AutomationWizardStep.AUTO_RESELL;
                }
            }
            case AUTO_RESELL -> {
                if (value.equalsIgnoreCase("custom") || value.equalsIgnoreCase("своё значение")) {
                    wizard.autoResellEnabled = true;
                    wizard.step = AutomationWizardStep.ANARCHY_SWITCH;
                    break;
                }
                Boolean enabled = parseOnOff(value);
                if (enabled == null) {
                    sendMessage("⚠️ Выберите, включать ли авто-перевыставление.");
                    return;
                }
                wizard.autoResellEnabled = enabled;
                wizard.step = AutomationWizardStep.ANARCHY_SWITCH;
            }
            case ANARCHY_SWITCH -> {
                if (value.equalsIgnoreCase("custom") || value.equalsIgnoreCase("своё значение")) {
                    wizard.anarchySwitchEnabled = true;
                    wizard.step = wizard.kind == AutomationKind.NIGHT
                            ? AutomationWizardStep.SELL_PRICE : AutomationWizardStep.AUTO_SELL;
                    if (wizard.kind == AutomationKind.NIGHT) wizard.autoSellEnabled = true;
                    break;
                }
                Boolean enabled = parseOnOff(value);
                if (enabled == null) {
                    sendMessage("⚠️ Выберите, включать ли автоматическую смену анархий.");
                    return;
                }
                wizard.anarchySwitchEnabled = enabled;
                wizard.step = wizard.kind == AutomationKind.NIGHT
                        ? AutomationWizardStep.SELL_PRICE : AutomationWizardStep.AUTO_SELL;
                if (wizard.kind == AutomationKind.NIGHT) wizard.autoSellEnabled = true;
            }
            case AUTO_SELL -> {
                Boolean enabled = parseOnOff(value);
                if (enabled == null) {
                    sendMessage("⚠️ Выберите, использовать ли AutoSell.");
                    return;
                }
                wizard.autoSellEnabled = enabled;
                wizard.step = enabled
                        ? AutomationWizardStep.SELL_PRICE
                        : AutomationWizardStep.ANARCHIES;
            }
            case SELL_PRICE -> {
                String price = normalizeSellPrice(value);
                if (price == null) {
                    sendMessage("⚠️ Укажите корректную цену продажи, например 3.8kk.");
                    return;
                }
                Long numericPrice = parsePurchasePrice(price);
                if (numericPrice == null || numericPrice <= wizard.buyPrice) {
                    sendMessage("⚠️ Цена продажи должна быть выше максимальной цены покупки "
                            + formatPrice(wizard.buyPrice) + "$.\n"
                            + "Иначе автономный режим будет заведомо продавать без наценки.");
                    return;
                }
                wizard.sellPrice = price;
                wizard.step = AutomationWizardStep.ANARCHIES;
            }
            case ANARCHIES -> {
                if (value.equalsIgnoreCase("skip") || value.equalsIgnoreCase("пропустить")) {
                    List<String> selected = telegramAutoBuy().getAnarchyIds();
                    if (selected.isEmpty()) {
                        sendMessage("⚠️ Нельзя пропустить: список анархий пуст.");
                        return;
                    }
                    wizard.anarchyIds = selected;
                    wizard.step = AutomationWizardStep.ANARCHY_DELAY;
                    break;
                }
                if (value.equalsIgnoreCase("selected") || value.equalsIgnoreCase("выбранные")) {
                    List<String> selected = telegramAutoBuy().getAnarchyIds();
                    if (selected.isEmpty()) {
                        sendMessage("⚠️ Список выбранных анархий пуст. Укажите номера вручную.");
                        return;
                    }
                    wizard.anarchyIds = selected;
                    wizard.step = AutomationWizardStep.ANARCHY_DELAY;
                    break;
                }
                List<String> ids = parseAutomationAnarchyIds(value);
                if (ids == null || ids.isEmpty()) {
                    sendMessage("⚠️ Укажите номера анархий через пробел или запятую, например: 101, 102, 103.\n"
                            + "Или нажмите кнопку «Выбранные».");
                    return;
                }
                wizard.anarchyIds = ids;
                wizard.step = AutomationWizardStep.ANARCHY_DELAY;
            }
            case ANARCHY_DELAY -> {
                if (value.equalsIgnoreCase("skip") || value.equalsIgnoreCase("пропустить")) {
                    wizard.anarchyDelayMinSeconds = 75L;
                    wizard.anarchyDelayMaxSeconds = 80L;
                    wizard.step = AutomationWizardStep.RESELL_INTERVAL;
                    break;
                }
                long[] range = parseAutomationRange(value, 30L, 300L);
                if (range == null) {
                    sendMessage("⚠️ Укажите интервал смены анархии в секундах, например 75-80.\n"
                            + "Для автономной работы минимум 30 секунд.");
                    return;
                }
                wizard.anarchyDelayMinSeconds = range[0];
                wizard.anarchyDelayMaxSeconds = range[1];
                wizard.step = AutomationWizardStep.RESELL_INTERVAL;
            }
            case RESELL_INTERVAL -> {
                if (value.equalsIgnoreCase("skip") || value.equalsIgnoreCase("пропустить")) {
                    wizard.resellMinSeconds = 60L;
                    wizard.resellMaxSeconds = 65L;
                    wizard.step = AutomationWizardStep.SELF_MODE;
                    break;
                }
                long[] range = parseAutomationRange(value, 60L, 300L);
                if (range == null) {
                    sendMessage("⚠️ Укажите интервал перевыставления, например 60-65.\n"
                            + "Минимум 60 секунд.");
                    return;
                }
                wizard.resellMinSeconds = range[0];
                wizard.resellMaxSeconds = range[1];
                wizard.step = AutomationWizardStep.SELF_MODE;
            }
            case SELF_MODE -> {
                if (value.equalsIgnoreCase("skip") || value.equalsIgnoreCase("пропустить")) {
                    wizard.selfMode = true;
                    wizard.step = AutomationWizardStep.CONFIRM;
                    break;
                }
                Boolean enabled = parseOnOff(value);
                if (enabled == null) {
                    sendMessage("⚠️ Ответьте on или off.");
                    return;
                }
                wizard.selfMode = enabled;
                wizard.step = AutomationWizardStep.CONFIRM;
            }
            case CONFIRM -> {
                Boolean confirmed = parseConfirmation(value);
                if (confirmed == null) {
                    sendMessage("⚠️ Ответьте start/да для запуска или cancel/нет для отмены.");
                    return;
                }
                if (!confirmed) {
                    cancelAutomationWizard();
                    return;
                }
                automationWizard = null;
                persistRuntimeState();
                activateAutomation(wizard);
                return;
            }
        }
        persistRuntimeState();
        sendAutomationWizardPrompt(wizard);
    }

    private void sendAutomationWizardPrompt(AutomationWizard wizard) {
        if (wizard == null) {
            return;
        }
        switch (wizard.step) {
            case DURATION -> sendWizardTextPrompt(
                    wizard,
                    "⏱️ " + wizard.kind.label() + ": укажите длительность.\nПримеры: 3h, 90m, 2ч."
            );
            case BUY_PRICE -> sendWizardTextPrompt(
                    wizard,
                    "1/7 🏷️ Максимальная цена покупки меча?\nПример: 3.5kk"
            );
            case AUCTION_SLOTS -> sendWizardTextPrompt(
                    wizard,
                    "🎟️ Сколько слотов доступно на вашем аукционе?\nПример: 10"
            );
            case LIMITS -> sendMessageWithButtonRows(
                    "⚙️ Дополнительные лимиты (необязательно)\n"
                            + "Укажите одной строкой: бюджет=50% покупки=10 заработать=500000\n"
                            + "Бюджет можно задать суммой или процентом от стартового баланса.",
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case BUDGET -> sendMessageWithButtonRows(
                    "💳 Укажите максимальный бюджет на покупки: суммой (3000000) или процентом от стартового баланса (50%).",
                    buttonRow(wizardButton("💰 Максимум", wizard, "max"), wizardButton("✏️ Своё значение", wizard, "custom")),
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case PURCHASE_LIMIT -> sendMessageWithButtonRows(
                    "🛒 Сколько мечей максимум купить за сессию?",
                    buttonRow(wizardButton("♾ Максимум", wizard, "max"), wizardButton("✏️ Своё значение", wizard, "custom")),
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case PROFIT_TARGET -> sendMessageWithButtonRows(
                    "📈 Укажите цель заработка (например 500000 или 1kk).",
                    buttonRow(wizardButton("♾ Без цели", wizard, "max"), wizardButton("✏️ Своё значение", wizard, "custom")),
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case RENAME -> sendMessageWithButtonRows(
                    "✏️ Переименовывать купленные мечи? После включения бот попросит текст ренейма.",
                    buttonRow(wizardButton("✏️ Включить ренейм", wizard, "enable"), wizardButton("🚫 Не переименовывать", wizard, "disable")),
                    wizardNavigationRow(wizard)
            );
            case AUTO_RESELL -> sendMessageWithButtonRows(
                    "♻️ Режим авто-реселла:",
                    buttonRow(wizardButton("🔁 Обычный", wizard, "on"), wizardButton("✏️ Своё значение", wizard, "custom")),
                    buttonRow(wizardButton("🚫 Отключить", wizard, "off")),
                    wizardNavigationRow(wizard)
            );
            case ANARCHY_SWITCH -> sendMessageWithButtonRows(
                    "🌐 Автоматически менять анархии во время сессии?",
                    buttonRow(wizardButton("🔁 Обычный", wizard, "on"), wizardButton("✏️ Своё значение", wizard, "custom")),
                    buttonRow(wizardButton("🚫 Отключить", wizard, "off")),
                    wizardNavigationRow(wizard)
            );
            case AUTO_SELL -> sendMessageWithButtonRows(
                    "2/7 🤖 Использовать AutoSell во время сессии?",
                    buttonRow(
                            wizardButton("✅ Да", wizard, "yes"),
                            wizardButton("❌ Нет", wizard, "no")
                    ),
                    wizardNavigationRow(wizard)
            );
            case SELL_PRICE -> sendWizardTextPrompt(
                    wizard,
                    "3/7 💰 По какой цене автоматически продавать каждый купленный меч?\nПример: 3.8kk"
            );
            case ANARCHIES -> {
                List<String> current = telegramAutoBuy().getAnarchyIds();
                sendMessageWithButtonRows(
                        "4/7 🌐 Укажите анархии через запятую.\n"
                                + "Пример: 101, 102, 103\n"
                                + "Текущий список: " + (current.isEmpty() ? "пуст" : String.join(", ", current)),
                        buttonRow(wizardButton("📌 Выбранные", wizard, "selected")),
                        buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                        wizardNavigationRow(wizard)
                );
            }
            case ANARCHY_DELAY -> sendMessageWithButtonRows(
                    "5/7 ⏳ Интервал смены анархии в секундах?\nРекомендуется 75-80, минимум 30.",
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case RESELL_INTERVAL -> sendMessageWithButtonRows(
                    "6/7 ♻️ Интервал перевыставления в секундах?\nРекомендуется 60-65, меньше 60 установить нельзя.",
                    buttonRow(wizardButton("⏭️ Пропустить", wizard, "skip")),
                    wizardNavigationRow(wizard)
            );
            case SELF_MODE -> sendMessageWithButtonRows(
                    "7/7 🤖 Включить self-режим?\n\n"
                            + "Да: AutoBuy продолжает покупки при балансе ниже price.\n"
                            + "Нет: при низком балансе переходит к перевыставлению.\n"
                            + "🚪 Хаб-сейф при пустом хранилище активен в обоих вариантах.",
                    buttonRow(
                            wizardButton("✅ Да", wizard, "on"),
                            wizardButton("❌ Нет", wizard, "off"),
                            wizardButton("⏭️ Пропустить", wizard, "skip")
                    ),
                    wizardNavigationRow(wizard)
            );
            case CONFIRM -> sendMessageWithButtonRows(
                    buildAutomationSummary(wizard) + "\n\nЗапустить сессию?",
                    buttonRow(wizardButton("▶️ Запустить", wizard, "start")),
                    wizardNavigationRow(wizard)
            );
        }
    }

    private void sendWizardTextPrompt(AutomationWizard wizard, String text) {
        sendMessageWithButtonRows(text, wizardNavigationRow(wizard));
    }

    private static InlineButton[] wizardNavigationRow(AutomationWizard wizard) {
        InlineButton cancel = wizardButton("❌ Отмена", wizard, "cancel");
        if (previousWizardStep(wizard) == null) {
            return buttonRow(cancel);
        }
        return buttonRow(
                wizardButton("⬅️ Назад", wizard, "back"),
                cancel
        );
    }

    private static InlineButton wizardButton(String text, AutomationWizard wizard, String value) {
        return new InlineButton(
                text,
                "wizard:" + wizard.id + ':' + wizard.step.name() + ":value:" + value
        );
    }

    private void activateAutomation(AutomationWizard wizard) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft не подключён к серверу. Автономную сессию запустить нельзя.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            if (automationSession != null) {
                sendMessage("⏳ Автономная сессия уже работает.");
                return;
            }
            if (isLeaveReportWorking()) {
                sendMessage("⚠️ Сначала дождитесь завершения команды /leave.");
                return;
            }
            cancelInventorySell(minecraft, "🛑 Текущее выставление остановлено перед автономной сессией.");
            AutoBuy autoBuy = telegramAutoBuy();
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            if (itemResorter.getEnchantNeeded().isEmpty() && itemResorter.getBuffNeeded().isEmpty()) {
                sendMessage("⚠️ Автономная сессия не запущена.\n"
                        + "В ItemResorter не настроен ни один обязательный фильтр чар или бафов.\n"
                        + "Без фильтра AutoBuy не сможет определить подходящий меч.");
                return;
            }
            AutomationSnapshot snapshot = new AutomationSnapshot(
                    itemResorter.getMaxPrice(),
                    itemResorter.isPriceFilterEnabled(),
                    autoSellPrice,
                    autoSellEnabled,
                    autoBuy.isAutoResellEnabled(),
                    autoBuy.isAnarchySwitchEnabled(),
                    autoBuy.getAnarchyIds(),
                    autoBuy.isLowBalanceGuardEnabled(),
                    notificationsEnabled,
                    autoBuy.getAnarchyDelayMinMs(),
                    autoBuy.getAnarchyDelayMaxMs(),
                    autoBuy.getResellIntervalMinMs(),
                    autoBuy.getResellIntervalMaxMs()
            );

            long now = System.currentTimeMillis();
            automationSession = new AutomationSession(
                    wizard.kind,
                    now,
                    now + wizard.durationMs,
                    wizard.selfMode,
                    wizard.buyPrice,
                    wizard.autoSellEnabled,
                    wizard.sellPrice,
                    wizard.budgetSpec,
                    wizard.maxPurchases,
                    wizard.targetProfit,
                    snapshot
            );
            automationPaused = false;
            pausedSessionRemainingMs = 0L;
            automationTransactionsOnly = true;
            automationEndedByHubSafety = false;
            setNotificationsEnabled(true);
            staffCheckActive = false;
            synchronized (sellLock) {
                auctionSlotLimit = wizard.auctionSlots;
                auctionActiveListings = 0;
                auctionTotalListed = 0;
                auctionTotalSold = 0;
                resetAutoSellStorageWaitLocked();
            }

            FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(false);
            itemResorter.setMaxPrice(wizard.buyPrice);
            itemResorter.setPriceFilterEnabled(true);
            if (wizard.autoSellEnabled) {
                setAutoSellPrice(wizard.sellPrice);
                setAutoSellEnabled(true);
            } else {
                setAutoSellEnabled(false);
            }
            autoBuy.setAnarchyIds(wizard.anarchyIds);
            autoBuy.setAnarchyDelayRangeSeconds(
                    wizard.anarchyDelayMinSeconds,
                    wizard.anarchyDelayMaxSeconds
            );
            autoBuy.setResellIntervalSeconds(wizard.resellMinSeconds, wizard.resellMaxSeconds);
            autoBuy.setAutoResellEnabled(wizard.autoResellEnabled);
            autoBuy.setAnarchySwitchEnabled(wizard.anarchySwitchEnabled);
            autoBuy.setNameText(wizard.renameText);
            autoBuy.setNameEnabled(!wizard.renameText.isBlank());
            autoBuy.setLowBalanceGuardEnabled(!wizard.selfMode);

            closeCurrentScreen(minecraft);
            if (autoBuy.isEnabled()) {
                autoBuy.setEnabled(false);
            }
            autoBuy.setEnabled(true);
            if (!autoBuy.startImmediateAnarchyFromList(minecraft)) {
                finishAutomationSession(minecraft, false, false);
                sendMessage("⚠️ Автономная сессия не запущена: не удалось начать переход на анархию.");
                return;
            }

            persistRuntimeState();
            sendMessage("✅ " + wizard.kind.label() + " запущен.\n\n"
                    + "⏱️ Длительность: " + formatDuration(wizard.durationMs) + "\n"
                    + "🏷️ Покупка до: " + formatPrice(wizard.buyPrice) + "$\n"
                    + "💳 Бюджет: " + wizard.budgetSpec + "\n"
                    + "🛒 Максимум покупок: " + (wizard.maxPurchases > 0 ? wizard.maxPurchases : "максимум") + "\n"
                    + "📈 Цель заработка: " + (wizard.targetProfit > 0 ? formatPrice(wizard.targetProfit) + "$" : "не задана") + "\n"
                    + "♻️ Авто-реселл: " + (wizard.autoResellEnabled ? "включён" : "выключен") + "\n"
                    + "🌐 Переключение анархий: " + (wizard.anarchySwitchEnabled ? "включено" : "выключено") + "\n"
                    + "✏️ Переименование: " + (wizard.renameText.isBlank() ? "пропущено" : wizard.renameText) + "\n"
                    + "💰 Автопродажа: " + (wizard.autoSellEnabled ? wizard.sellPrice : "выключена") + "\n"
                    + "🤖 Self: " + (wizard.selfMode
                            ? "on — продолжает покупать при балансе ниже price"
                            : "off — при низком балансе переходит к перевыставлению") + "\n"
                    + "🌐 Анархии: " + String.join(", ", wizard.anarchyIds) + "\n"
                    + "🔕 В процессе отправляются только покупки и продажи.");
        });
    }

    private boolean processAutomationSession(MinecraftClient minecraft, long now) {
        AutomationSession session = automationSession;
        if (session == null) {
            return false;
        }
        if (automationPaused) {
            return true;
        }
        if (now >= session.startedAt() + 15_000L
                && !telegramAutoBuy().isEnabled()) {
            pauseSessionBecauseAutoBuyDisabled(minecraft, session, now);
            return true;
        }
        AutoBuy.SessionStats stats = telegramAutoBuy().getSessionStats();
        long spendingLimit = resolveSessionBudget(session.budgetSpec(), stats.startBalance());
        long profit = saturatedSubtract(stats.saleRevenue(), stats.purchaseSpent());
        if (session.maxPurchases() > 0 && stats.purchaseCount() >= session.maxPurchases()
                || spendingLimit > 0 && stats.purchaseSpent() >= spendingLimit
                || session.targetProfit() > 0 && profit >= session.targetProfit()) {
            finishAutomationSession(minecraft, false, false);
            return true;
        }
        if (now < session.endsAt()) {
            return false;
        }
        finishAutomationSession(minecraft, true, true);
        return true;
    }

    private static long resolveSessionBudget(String specification, long startBalance) {
        if (specification == null || specification.isBlank()
                || specification.equalsIgnoreCase("максимум")) {
            return 0L;
        }
        try {
            if (specification.endsWith("%")) {
                if (startBalance < 0L) {
                    return 0L;
                }
                double percent = Double.parseDouble(specification.substring(0, specification.length() - 1));
                return percent <= 0D ? 0L : Math.max(1L, Math.round(startBalance * percent / 100D));
            }
            Long amount = parsePurchasePrice(specification);
            return amount == null ? 0L : Math.max(0L, amount);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private void pauseSessionBecauseAutoBuyDisabled(
            MinecraftClient minecraft,
            AutomationSession session,
            long now
    ) {
        if (automationPaused) {
            return;
        }
        automationPaused = true;
        pausedSessionRemainingMs = Math.max(1_000L, session.endsAt() - now);
        pausedAutoBuyWasEnabled = true;
        pausedAutoSellWasEnabled = autoSellEnabled;
        cancelInventorySell(minecraft, null);
        setAutoSellEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(false);
        persistRuntimeState();
        sendMessageWithButtonRows(
                "⏸️ AutoBuy был выключен в Minecraft.\n"
                        + "Сессия поставлена на паузу и не завершена.\n"
                        + "Осталось: " + formatSessionDuration(pausedSessionRemainingMs),
                buttonRow(button("▶️ Продолжить", "menu:resume")),
                buttonRow(button("⏹️ Завершить сессию", "menu:stop"))
        );
    }

    private void requestAutomationStop() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (automationSession == null) {
            sendMessage("ℹ️ Автономная сессия сейчас не запущена.");
            return;
        }
        if (minecraft == null) {
            finishAutomationSession(null, false, false);
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> finishAutomationSession(minecraft, false, false));
    }

    private void finishAutomationSession(MinecraftClient minecraft, boolean expired, boolean goHub) {
        AutomationSession session = automationSession;
        if (session == null) {
            return;
        }
        boolean endedByHubSafety = automationEndedByHubSafety;
        automationSession = null;
        automationWizard = null;
        automationPaused = false;
        pausedSessionRemainingMs = 0L;
        pausedAutoBuyWasEnabled = false;
        pausedAutoSellWasEnabled = false;
        automationEndedByHubSafety = false;

        cancelInventorySell(minecraft, null);
        AutoBuy autoBuy = telegramAutoBuy();
        AutoBuy.SessionStats finalStats = autoBuy.getSessionStats();
        autoBuy.setEnabled(false);
        setAutoSellEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
        closeCurrentScreen(minecraft);
        if (goHub && minecraft != null && minecraft.getNetworkHandler() != null) {
            resumeFromHubOnPlay = true;
        }

        AutomationSnapshot snapshot = session.snapshot();
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        itemResorter.setMaxPrice(snapshot.maxBuyPrice());
        itemResorter.setPriceFilterEnabled(snapshot.priceFilterEnabled());
        setAutoSellPrice(snapshot.autoSellPrice());
        setAutoSellEnabled(snapshot.autoSellEnabled());
        autoBuy.setAnarchyIds(snapshot.anarchyIds());
        autoBuy.setAnarchyDelayRangeSeconds(
                Math.max(11L, snapshot.anarchyDelayMinMs() / 1_000L),
                Math.max(11L, snapshot.anarchyDelayMaxMs() / 1_000L)
        );
        autoBuy.setResellIntervalSeconds(
                Math.max(60L, snapshot.resellIntervalMinMs() / 1_000L),
                Math.max(60L, snapshot.resellIntervalMaxMs() / 1_000L)
        );
        autoBuy.setAutoResellEnabled(snapshot.autoResellEnabled());
        autoBuy.setAnarchySwitchEnabled(snapshot.anarchySwitchEnabled());
        autoBuy.setLowBalanceGuardEnabled(snapshot.lowBalanceGuardEnabled());
        setNotificationsEnabled(snapshot.notificationsEnabled());
        automationTransactionsOnly = false;

        persistRuntimeState();
        sendMessage(buildAutomationCompletionReport(session, finalStats, expired, goHub, endedByHubSafety));
        if (goHub && minecraft != null && minecraft.player != null && minecraft.getNetworkHandler() != null) {
            // Завершение сессии использует тот же полный цикл, что и /leave:
            // баланс, хранилище, стоимость мечей и скриншот перед переходом в хаб.
            startLeaveReport(minecraft, true);
        }
    }

    private void requestAutoBuyStart() {
        if (automationPaused) {
            requestAutomationResume();
            return;
        }
        if (auctionSlotLimit <= 0) {
            sendMessage("🎟️ Перед запуском укажите количество доступных слотов аукциона:\n"
                    + "/slots <количество>\n\nПосле этого повторите /play.");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft не подключён к серверу. AutoBuy запустить нельзя.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            AutoBuy autoBuy = telegramAutoBuy();
            staffCheckActive = false;
            if (autoBuy.isEnabled()) {
                sendMessage("▶️ AutoBuy уже включён и работает.");
                return;
            }
            closeCurrentScreen(minecraft);
            autoBuy.setEnabled(true);
            if (resumeFromHubOnPlay) {
                if (!autoBuy.startImmediateAnarchyFromList(minecraft)) {
                    autoBuy.setEnabled(false);
                    sendMessage("⚠️ AutoBuy не запущен: список анархий пуст.\n"
                            + "Добавьте хотя бы одну анархию в настройках AutoBuy.");
                    return;
                }
                resumeFromHubOnPlay = false;
                sendMessage("▶️⚔️ AutoBuy включён!\n"
                        + "🌐 Подключаюсь к следующей анархии из списка\n"
                        + "🔎 После входа поиск мечей продолжится автоматически.");
                return;
            }
            sendMessage("▶️⚔️ AutoBuy включён!\n"
                    + "🔎 Начинаю поиск подходящих незеритовых мечей.");
        });
    }

    private void requestAutoBuyStop() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            AutoBuy autoBuy = telegramAutoBuy();
            if (automationSession != null) {
                finishAutomationSession(minecraft, false, false);
                return;
            }
            if (automationPaused) {
                automationPaused = false;
                pausedAutoBuyWasEnabled = false;
                pausedAutoSellWasEnabled = false;
                pausedSessionRemainingMs = 0L;
                synchronized (sellLock) {
                    autoSellAfterPurchaseQueued = false;
                    resetAutoSellStorageWaitLocked();
                }
                persistRuntimeState();
                sendMessage("⏹️ Сохранённая пауза и очередь автоматизации очищены.");
                return;
            }
            if (!autoBuy.isEnabled()) {
                sendMessage("⏹️ AutoBuy уже выключен.");
                return;
            }
            autoBuy.setEnabled(false);
            closeCurrentScreen(minecraft);
            sendMessage("⏹️🛑 AutoBuy выключен.\n"
                    + "Покупка, поиск и обновление аукциона остановлены.");
        });
    }

    private void requestAutoBuyStats() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            synchronized (balanceLock) {
                statsReplyRequested = true;
            }
            sendMessage("🔎 Запрашиваю настоящий баланс через /bal и собираю статистику...");
            requestBalanceCheck(false);
        });
    }

    private void sendStatusPanel(BotUpdate update) {
        AutoBuy autoBuy = telegramAutoBuy();
        AutoBuy.SessionStats stats = autoBuy.getSessionStats();
        String text = "📊 ТЕКУЩИЙ СТАТУС\n\n"
                + "AutoBuy: " + (autoBuy.isEnabled() ? "▶️ работает" : automationPaused ? "⏸️ пауза" : "⏹️ выключен")
                + "\nЭтап: " + formatAutoBuyStep(stats.step())
                + "\nПокупок: " + stats.purchaseCount()
                + "\nПродаж: " + stats.saleCount()
                + "\nAutoSell: " + (autoSellEnabled ? "включён" : "выключен")
                + (autoSellStorageKnownFull ? " — хранилище заполнено" : "")
                + "\nСессия: " + (automationSession == null
                ? "нет"
                : automationSession.kind().label() + ", осталось "
                + formatSessionDuration(automationPaused
                ? pausedSessionRemainingMs
                : Math.max(0L, automationSession.endsAt() - System.currentTimeMillis())));
        editMessage(
                update,
                text,
                buttonRow(button("🔄 Обновить", "menu:status_refresh")),
                buttonRow(button("⬅️ Назад", "menu:home"))
        );
    }

    private void sendInventoryStatus(BotUpdate update) {
        BotSession target = FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetSession();
        if (target == null) {
            target = FluxVisualsClient.MULTI_BOT_MANAGER.getMainSession();
        }
        int swords = 0;
        long bal = -1L;
        String screenTitle = "В мире";
        if (target != null) {
            bal = FluxVisualsClient.MULTI_BOT_MANAGER.resolveSessionBalance(target);
            if (target.getPlayer() != null) {
                swords = countInventorySwords(target.getPlayer().getInventory());
            }
            if (target.getScreen() != null) {
                screenTitle = formatScreenTitle(target.getScreen());
            }
        }

        String accountLabel = target != null ? (target.getName() + (target.isMain() ? " [Основа]" : " [Бот]")) : "Основа";

        StringBuilder text = new StringBuilder("🎒 ИНВЕНТАРЬ (<b>" + accountLabel + "</b>)\n\n")
                .append("⚔️ Незеритовых мечей: ").append(swords).append(" шт.\n")
                .append("💰 Баланс: ").append(bal >= 0L ? formatPrice(bal) + "$" : "неизвестен").append('\n')
                .append("🖥️ Экран: ").append(screenTitle).append('\n');

        if (target != null && target.isMain()) {
            text.append("📦 Хранилище: ").append(autoSellStorageKnownFull
                    ? "известно как заполненное"
                    : "заполненность не подтверждена").append('\n')
                .append("🆓 Учтено освободившихся слотов: ").append(autoSellReleasedSlots).append('\n')
                .append("🤖 AutoSell: ").append(autoSellEnabled ? "включён" : "выключен").append('\n')
                .append("⏳ Выставление: ").append(isInventorySellWorking() ? "выполняется" : "не выполняется");
        }

        if (update == null) {
            sendMessageWithButtonRows(
                    text.toString(),
                    buttonRow(button("🔄 Обновить", "menu:inventory_refresh")),
                    buttonRow(button("🏠 Меню", "menu:home"))
            );
            if (target != null && !target.isMain()) {
                sendBotInventoryPhoto(target.getName());
            } else {
                requestScreenshotFromMinecraft(
                        "🎒 Инвентарь (<b>" + accountLabel + "</b>)\n"
                                + "⚔️ Незеритовых мечей: " + swords + " шт.\n"
                                + "💰 Баланс: " + (bal >= 0L ? formatPrice(bal) + "$" : "неизвестен"),
                        false
                );
            }
        } else {
            editMessage(
                    update,
                    text.toString(),
                    buttonRow(button("🔄 Обновить", "menu:inventory_refresh")),
                    buttonRow(button("⬅️ Назад", "menu:home"))
            );
        }
    }

    private void sendAutoSellStatus(BotUpdate update) {
        String text = "🤖 AUTОSELL\n\n"
                + "Статус: " + (autoSellEnabled ? "включён" : "выключен")
                + "\nЦена: " + (autoSellPrice.isBlank() ? "не задана" : autoSellPrice)
                + "\nОчередь: " + (autoSellAfterPurchaseQueued ? "ожидает" : "пусто")
                + "\nХранилище: " + (autoSellStorageKnownFull ? "заполнено" : "не подтверждено как заполненное")
                + "\nОсвободившихся слотов: " + autoSellReleasedSlots
                + "\nВыставление: " + (isInventorySellWorking() ? "выполняется" : "не выполняется");
        InlineButton toggle = autoSellEnabled
                ? button("🛑 Выключить", "menu:autosell_disable")
                : button("▶️ Включить", "menu:autosell_enable");
        if (update == null) {
            sendMessageWithButtonRows(
                    text,
                    buttonRow(toggle),
                    buttonRow(button("💰 Установить новую цену", "menu:autosell_price")),
                    buttonRow(button("🏠 Меню", "menu:home"))
            );
        } else {
            editMessage(
                    update,
                    text,
                    buttonRow(toggle),
                    buttonRow(button("💰 Установить новую цену", "menu:autosell_price")),
                    buttonRow(button("⬅️ Назад", "menu:home"))
            );
        }
    }

    private void sendVersionStatus(BotUpdate update) {
        String version = FabricLoader.getInstance()
                .getModContainer("fluxvisuals")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        String text = "ℹ️ ВЕРСИЯ\n\n"
                + "FluxVisuals: " + version
                + "\nMinecraft: 1.21.8"
                + "\nJava: " + System.getProperty("java.version", "unknown")
                + "\nTelegram: " + (pollerRunning ? "подключён" : "остановлен")
                + "\nСетевых ошибок подряд: " + telegramNetworkFailureStreak;
        if (update == null) {
            sendMessageWithButtonRows(text, buttonRow(button("🏠 Меню", "menu:home")));
        } else {
            editMessage(update, text, buttonRow(button("⬅️ Назад", "menu:home")));
        }
    }

    private void requestAutomationPause() {
        if (automationPaused) {
            sendMessageWithButtons("⏸️ Автоматизация уже находится на паузе.",
                    button("▶️ Продолжить", "menu:resume"),
                    button("🏠 Меню", "menu:home"));
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        AutoBuy autoBuy = telegramAutoBuy();
        pausedAutoBuyWasEnabled = autoBuy.isEnabled();
        pausedAutoSellWasEnabled = autoSellEnabled;
        AutomationSession session = automationSession;
        pausedSessionRemainingMs = session == null
                ? 0L
                : Math.max(0L, session.endsAt() - System.currentTimeMillis());
        automationPaused = true;
        cancelInventorySell(minecraft, null);
        autoBuy.setEnabled(false);
        autoSellEnabled = false;
        FluxVisualsClient.requestConfigSave();
        FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(false);
        persistRuntimeState();
        sendMessageWithButtonRows(
                "⏸️ Автоматизация поставлена на паузу.\n"
                        + "Текущая сессия и оставшееся время сохранены.",
                buttonRow(button("▶️ Продолжить", "menu:resume")),
                buttonRow(button("🛑 PANIC", "menu:panic"), button("🏠 Меню", "menu:home"))
        );
    }

    private void requestAutomationResume() {
        if (!automationPaused) {
            sendMessage("ℹ️ Сохранённой паузы нет.");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft не подключён к серверу. Возобновление невозможно.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            AutoBuy autoBuy = telegramAutoBuy();
            AutomationSession session = automationSession;
            if (session != null) {
                long now = System.currentTimeMillis();
                automationSession = new AutomationSession(
                        session.kind(),
                        session.startedAt(),
                        now + Math.max(1_000L, pausedSessionRemainingMs),
                        session.selfMode(),
                        session.buyPrice(),
                        session.autoSellEnabled(),
                        session.sellPrice(),
                        session.budgetSpec(),
                        session.maxPurchases(),
                        session.targetProfit(),
                        session.snapshot()
                );
                pausedAutoBuyWasEnabled = true;
                pausedAutoSellWasEnabled = session.autoSellEnabled();
            }
            automationPaused = false;
            if (pausedAutoSellWasEnabled && normalizeSellPrice(autoSellPrice) != null) {
                setAutoSellEnabled(true);
            }
            if (pausedAutoBuyWasEnabled) {
                autoBuy.setEnabled(true);
                if (session != null && !autoBuy.startImmediateAnarchyFromList(minecraft)) {
                    autoBuy.setEnabled(false);
                    automationPaused = true;
                    sendMessage("⚠️ Не удалось продолжить: список анархий пуст.");
                    persistRuntimeState();
                    return;
                }
            }
            pausedSessionRemainingMs = 0L;
            persistRuntimeState();
            sendMessageWithButtons("▶️ Автоматизация продолжена.",
                    button("📊 Статус", "menu:status"),
                    button("🏠 Меню", "menu:home"));
        });
    }

    private void requestPanicConfirmation() {
        requestPanicConfirmation(null);
    }

    private void requestPanicConfirmation(BotUpdate update) {
        panicConfirmationId = newCallbackId();
        panicConfirmationDeadline = System.currentTimeMillis() + CLOSE_CONFIRMATION_TIMEOUT_MS;
        String text = "🛑 ЭКСТРЕННАЯ ОСТАНОВКА\n\n"
                + "Будут остановлены AutoBuy, AutoSell, сессия, перевыставление и смена анархии.\n"
                + "Подтвердить?";
        InlineButton[] row = buttonRow(
                button("🛑 Остановить всё", "confirm:panic:" + panicConfirmationId + ":yes"),
                button("Отмена", "confirm:panic:" + panicConfirmationId + ":no")
        );
        if (update == null) {
            sendMessageWithButtonRows(text, row);
        } else {
            editMessage(update, text, row);
        }
    }

    private void performPanicStop() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        automationSession = null;
        automationWizard = null;
        automationPaused = false;
        pausedSessionRemainingMs = 0L;
        automationTransactionsOnly = false;
        automationEndedByHubSafety = false;
        synchronized (sellLock) {
            autoSellAfterPurchaseQueued = false;
            resetAutoSellStorageWaitLocked();
        }
        cancelInventorySell(minecraft, null);
        telegramAutoBuy().setEnabled(false);
        setAutoSellEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(false);
        FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(false);
        closeCurrentScreen(minecraft);
        persistRuntimeState();
    }

    private void requestLeaveToHub() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft не подключён к серверу. Перейти в хаб нельзя.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            if (automationSession != null) {
                finishAutomationSession(minecraft, false, false);
            }
            if (staffCheckActive) {
                performImmediateLeave(minecraft, true);
                return;
            }
            if (isLeaveReportWorking()) {
                sendMessage("⏳ Итоговый отчёт /leave уже формируется.");
                return;
            }
                startLeaveReport(minecraft, true);
        });
    }

    private void startLeaveReport(MinecraftClient minecraft) {
        startLeaveReport(minecraft, false);
    }

    private void startLeaveReport(MinecraftClient minecraft, boolean humanized) {
        AutoBuy autoBuy = telegramAutoBuy();
        leaveHumanized = humanized;
        leaveStage = LeaveStage.WAIT_AUTOBUY_PAUSE;
        leaveDeadline = System.currentTimeMillis() + LEAVE_PAUSE_TIMEOUT_MS;
        leaveNextActionAt = humanized
                ? System.currentTimeMillis() + randomDelay(LEAVE_HUMAN_PAUSE_MIN_MS, LEAVE_HUMAN_PAUSE_MAX_MS)
                : 0L;
        leaveStartBalance = autoBuy.getSessionStartBalance();
        leaveFallbackBalance = autoBuy.getLastKnownBalance();
        leaveEndBalance = -1L;
        leaveAuctionSyncId = -1;
        leaveScreenshotCaption = "";
        cancelInventorySell(minecraft, "🚪 Текущее выставление остановлено для подготовки отчёта /leave.");
        sendMessage("📊 Подготавливаю итоговый отчёт AutoBuy...\n"
                + "⏸️ Останавливаю действия, проверяю баланс и открываю хранилище.");
    }

    private boolean processLeaveReport(MinecraftClient minecraft, long now) {
        LeaveStage stage = leaveStage;
        if (stage == LeaveStage.IDLE) {
            return false;
        }
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Итоговый отчёт не завершён: соединение с сервером потеряно.");
            resetLeaveReport();
            return true;
        }

        AutoBuy autoBuy = telegramAutoBuy();
        if (stage == LeaveStage.WAIT_AUTOBUY_PAUSE) {
            if (now < leaveNextActionAt) {
                return true;
            }
            if (FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()
                    || FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()) {
                if (now < leaveDeadline) {
                    return true;
                }
                FluxVisualsClient.MODULE_MANAGER.getAutoResell().cancelCurrentRun(minecraft);
                if (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()) {
                    leaveDeadline = Math.max(now + 1_000L, AuctionAccessGuard.readyAt());
                    return true;
                }
            }
            if (!autoBuy.tryPauseForExternalAction(minecraft, 0L)) {
                if (now < leaveDeadline) {
                    return true;
                }
                autoBuy.setEnabled(false);
            }
            closeCurrentScreen(minecraft);
            autoBuy.refreshBalanceFromScoreboardNow(minecraft);
            leaveStage = LeaveStage.WAIT_BALANCE;
            leaveDeadline = now + LEAVE_BALANCE_TIMEOUT_MS
                    + (leaveHumanized ? randomDelay(250L, 900L) : 0L);
            return true;
        }

        if (stage == LeaveStage.WAIT_BALANCE) {
            if (now < leaveDeadline) {
                return true;
            }
            leaveEndBalance = leaveFallbackBalance;
            leaveStage = LeaveStage.OPEN_AUCTION;
            leaveNextActionAt = now + (leaveHumanized
                    ? randomDelay(LEAVE_HUMAN_ACTION_MIN_MS, LEAVE_HUMAN_ACTION_MAX_MS) : 0L);
            return true;
        }

        if (stage == LeaveStage.OPEN_AUCTION) {
            if (now < leaveNextActionAt) {
                return true;
            }
            if (AuctionAccessGuard.isBlocked(now)) {
                leaveNextActionAt = AuctionAccessGuard.readyAt();
                return true;
            }
            closeCurrentScreen(minecraft);
            minecraft.getNetworkHandler().sendChatCommand("ah");
            leaveStage = LeaveStage.WAIT_AUCTION;
            leaveNextActionAt = now + LEAVE_GUI_RETRY_MS
                    + (leaveHumanized ? randomDelay(250L, 900L) : 0L);
            leaveDeadline = now + LEAVE_GUI_TIMEOUT_MS;
            return true;
        }

        if (stage == LeaveStage.WAIT_AUCTION) {
            if (isLeaveStorageScreen(minecraft)) {
                leaveStage = LeaveStage.WAIT_STORAGE;
                leaveNextActionAt = now + LEAVE_STORAGE_LOAD_MS
                        + (leaveHumanized ? randomDelay(450L, 1_400L) : 0L);
                leaveDeadline = leaveNextActionAt + LEAVE_GUI_TIMEOUT_MS;
                return true;
            }
            if (minecraft.currentScreen instanceof HandledScreen<?>
                    && minecraft.player.currentScreenHandler != null) {
                int auctionSyncId = minecraft.player.currentScreenHandler.syncId;
                if (clickLeaveStorageButton(minecraft)) {
                    leaveAuctionSyncId = auctionSyncId;
                    leaveStage = LeaveStage.WAIT_STORAGE_OPEN;
                    leaveNextActionAt = 0L;
                    leaveDeadline = now + LEAVE_GUI_TIMEOUT_MS;
                    return true;
                }
                if (now >= leaveDeadline) {
                    prepareLeaveReport(minecraft, StorageSnapshot.unavailable());
                }
                return true;
            }
            if (now >= leaveDeadline) {
                prepareLeaveReport(minecraft, StorageSnapshot.unavailable());
                return true;
            }
            if (now >= leaveNextActionAt) {
                minecraft.getNetworkHandler().sendChatCommand("ah");
                leaveNextActionAt = now + LEAVE_GUI_RETRY_MS;
            }
            return true;
        }

        if (stage == LeaveStage.WAIT_STORAGE_OPEN) {
            if (isLeaveStorageScreen(minecraft)) {
                leaveStage = LeaveStage.WAIT_STORAGE;
                leaveNextActionAt = now + LEAVE_STORAGE_LOAD_MS
                        + (leaveHumanized ? randomDelay(450L, 1_400L) : 0L);
                leaveDeadline = leaveNextActionAt + LEAVE_GUI_TIMEOUT_MS;
                return true;
            }
            if (now >= leaveDeadline) {
                prepareLeaveReport(minecraft, StorageSnapshot.unavailable());
            }
            return true;
        }

        if (stage == LeaveStage.WAIT_STORAGE) {
            if (!isLeaveStorageScreen(minecraft)) {
                if (now >= leaveDeadline) {
                    prepareLeaveReport(minecraft, StorageSnapshot.unavailable());
                }
                return true;
            }
            if (now < leaveNextActionAt) {
                return true;
            }
            prepareLeaveReport(minecraft, scanStorageSwords(minecraft));
            return true;
        }

        if (stage == LeaveStage.CAPTURE_SCREENSHOT) {
            if (now < leaveNextActionAt) {
                return true;
            }
            if (screenshotRequestPending) {
                return true;
            }
            requestScreenshotFromMinecraft(leaveScreenshotCaption, false);
            if (screenshotRequestPending) {
                leaveStage = LeaveStage.WAIT_SCREENSHOT;
            } else {
                performImmediateLeave(minecraft, false);
            }
            return true;
        }

        if (stage == LeaveStage.WAIT_SCREENSHOT) {
            if (!screenshotRequestPending) {
                performImmediateLeave(minecraft, false);
            }
            return true;
        }
        return true;
    }

    private void acceptLeaveReportBalance(long balance) {
        if (leaveStage != LeaveStage.WAIT_BALANCE) {
            return;
        }
        leaveEndBalance = balance;
        leaveStage = LeaveStage.OPEN_AUCTION;
        leaveNextActionAt = System.currentTimeMillis() + 200L
                + (leaveHumanized ? randomDelay(LEAVE_HUMAN_ACTION_MIN_MS, LEAVE_HUMAN_ACTION_MAX_MS) : 0L);
    }

    private void prepareLeaveReport(MinecraftClient minecraft, StorageSnapshot snapshot) {
        String report = buildLeaveReport(snapshot);
        sendMessage(report);
        if (!snapshot.available()) {
            sendMessage("⚠️ Скриншот хранилища пропущен: подтверждённый экран хранилища не открылся.");
            performImmediateLeave(minecraft, false);
            return;
        }
        leaveScreenshotCaption = "📸 Хранилище на момент завершения AutoBuy\n"
                + "⚔️ Мечей в хранилище: " + snapshot.storageSwordCount();
        leaveStage = LeaveStage.CAPTURE_SCREENSHOT;
        leaveNextActionAt = System.currentTimeMillis() + (leaveHumanized
                ? randomDelay(LEAVE_HUMAN_ACTION_MIN_MS, LEAVE_HUMAN_ACTION_MAX_MS) : 0L);
    }

    private StorageSnapshot scanStorageSwords(MinecraftClient minecraft) {
        if (minecraft.player == null || minecraft.player.currentScreenHandler == null) {
            return StorageSnapshot.unavailable();
        }
        int storageSwordCount = 0;
        int inventorySwordCount = 0;
        int pricedSwordCount = 0;
        int unpricedSwordCount = 0;
        long totalSwordValue = 0L;
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (!stack.isOf(Items.NETHERITE_SWORD)) {
                continue;
            }
            int count = Math.max(1, stack.getCount());
            if (slot.inventory == minecraft.player.getInventory()) {
                inventorySwordCount += count;
            } else {
                storageSwordCount += count;
            }
            Long price = ItemResorter.extractPrice(tooltipLines(minecraft, stack));
            if (price == null) {
                unpricedSwordCount += count;
                continue;
            }
            pricedSwordCount += count;
            totalSwordValue = saturatedAdd(totalSwordValue, saturatedMultiply(price, count));
        }
        return new StorageSnapshot(
                true,
                storageSwordCount,
                inventorySwordCount,
                pricedSwordCount,
                unpricedSwordCount,
                totalSwordValue
        );
    }

    private boolean clickLeaveStorageButton(MinecraftClient minecraft) {
        if (minecraft.player == null || minecraft.interactionManager == null
                || minecraft.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == minecraft.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (!name.contains("хранилищ") && !name.contains("storage")) {
                continue;
            }
            minecraft.interactionManager.clickSlot(
                    minecraft.player.currentScreenHandler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    minecraft.player
            );
            return true;
        }
        return false;
    }

    private boolean isLeaveStorageScreen(MinecraftClient minecraft) {
        if (minecraft.currentScreen == null || minecraft.player == null
                || minecraft.player.currentScreenHandler == null) {
            return false;
        }
        String title = minecraft.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("хранилищ") || title.contains("storage")) {
            return true;
        }
        if (leaveAuctionSyncId < 0 || minecraft.player.currentScreenHandler.syncId == leaveAuctionSyncId) {
            return false;
        }
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == minecraft.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("перевыстав") || name.contains("resell")) {
                return true;
            }
        }
        return false;
    }

    private void performImmediateLeave(MinecraftClient minecraft, boolean checkLeave) {
        resetLeaveReport();
        cancelInventorySell(minecraft, null);
        telegramAutoBuy().setEnabled(false);
        closeCurrentScreen(minecraft);
        if (minecraft.getNetworkHandler() != null) {
            minecraft.getNetworkHandler().sendChatCommand("hub");
        }
        resumeFromHubOnPlay = true;
        if (checkLeave) {
            staffCheckActive = false;
            sendMessage("🚨 Команда /hub отправлена без отчёта из-за активной проверки.");
        } else {
            sendMessage("🚪 Итоговый отчёт отправлен. Команда /hub выполнена, AutoBuy остановлен.");
        }
    }

    private void resetLeaveReport() {
        leaveStage = LeaveStage.IDLE;
        leaveDeadline = 0L;
        leaveNextActionAt = 0L;
        leaveStartBalance = -1L;
        leaveEndBalance = -1L;
        leaveFallbackBalance = -1L;
        leaveAuctionSyncId = -1;
        leaveScreenshotCaption = "";
        leaveHumanized = false;
    }

    private void requestMinecraftClose() {
        closeConfirmationId = newCallbackId();
        closeConfirmationDeadline = System.currentTimeMillis() + CLOSE_CONFIRMATION_TIMEOUT_MS;
        sendMessageWithButtonRows(
                "⚠️ ПОДТВЕРЖДЕНИЕ ЗАКРЫТИЯ\n\n"
                        + "Minecraft будет закрыт штатно, без искусственного краша и краш-репорта.",
                buttonRow(
                        button("🛑 Закрыть Minecraft", "confirm:crash:" + closeConfirmationId + ":yes"),
                        button("Отмена", "confirm:crash:" + closeConfirmationId + ":no")
                )
        );
    }

    private void performMinecraftClose() {
        closeConfirmationDeadline = 0L;
        closeConfirmationId = "";
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен для закрытия.");
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            cancelInventorySell(minecraft, null);
            telegramAutoBuy().setEnabled(false);
            closeCurrentScreen(minecraft);
        });
        sendMessageThenCloseMinecraft("🛑 Minecraft был закрыт удалённой командой /crash.\n"
                + "✅ Завершение выполнено штатно, без создания краш-репорта.");
    }

    private void requestInventorySell(String argument) {
        requestInventorySell(argument, true);
    }

    private void requestInventorySell(String argument, boolean useSellGui) {
        requestInventorySell(argument, useSellGui, false);
    }

    private void requestInventorySell(String argument, boolean useSellGui, boolean automatic) {
        InventorySellRequest request = parseInventorySellRequest(argument);
        if (request == null) {
            sendMessage("⚠️ Укажите корректную цену и количество.\n"
                    + "Примеры: /sell 3.5kk 1 или /sell 3.5kk all");
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.interactionManager == null
                || minecraft.getNetworkHandler() == null) {
            sendMessage("⚠️ Minecraft не подключён к серверу. Выставить мечи нельзя.");
            return;
        }
        synchronized (sellLock) {
            if (inventorySellQueued || inventorySellWorking) {
                sendMessage("⏳ Выставление мечей уже выполняется.");
                return;
            }
            inventorySellQueued = true;
            inventorySellAutomatic = automatic;
            inventorySellUseSellGui = useSellGui;
            inventorySellQueuedPrice = request.price();
            inventorySellQueuedLimit = request.count();
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> processQueuedInventorySell(minecraft));
    }

    public void requestAutomaticSwordSell(String price) {
        if (price == null || price.isBlank()) return;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        synchronized (sellLock) {
            if (inventorySellQueued || inventorySellWorking) return;
            inventorySellQueued = true;
            inventorySellAutomatic = true;
            inventorySellUseSellGui = true;
            inventorySellQueuedPrice = normalizeSellPrice(price);
            inventorySellQueuedLimit = 1;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> processQueuedInventorySell(minecraft));
    }

    private void requestPurchasePrice(String argument) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен. Изменить цену покупки нельзя.");
            return;
        }
        if (argument == null || argument.isBlank()) {
            long currentPrice = FluxVisualsClient.MODULE_MANAGER.getItemResorter().getMaxPrice();
            sendMessage("🏷️ Текущая максимальная цена покупки: " + formatPrice(currentPrice) + "$\n"
                    + "Для изменения используйте: /price <цена>\n"
                    + "Пример: /price 3.5kk");
            return;
        }

        Long price = parsePurchasePrice(argument);
        if (price == null || price <= 0L) {
            sendMessage("⚠️ Укажите корректную цену больше нуля.\n"
                    + "Примеры: /price 3500000, /price 3.5kk или /price 3.5млн");
            return;
        }

        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setMaxPrice(price);
            itemResorter.setPriceFilterEnabled(true);
            sendMessage("✅ Максимальная цена покупки изменена.\n\n"
                    + "🏷️ Новая цена: " + formatPrice(price) + "$\n"
                    + "🔎 Фильтр цены включён.");
        });
    }

    private void requestAutoSellEnable(String argument) {
        String normalizedArgument = argument == null ? "" : argument.trim();
        if (normalizedArgument.equalsIgnoreCase("status")
                || normalizedArgument.equalsIgnoreCase("статус")) {
            sendAutoSellStatus(null);
            return;
        }
        String price = normalizeSellPrice(normalizedArgument);
        if (price == null) {
            sendMessage("⚠️ Для включения автопродажи обязательно укажите цену.\n"
                    + "Пример: /autosell 3.5kk");
            return;
        }
        setAutoSellPrice(price);
        setAutoSellEnabled(true);
        sendMessage("🤖✅ АВТОПРОДАЖА ВКЛЮЧЕНА!\n\n"
                + "💰 Цена каждого меча: " + price + "\n"
                + "⚔️ После подтверждённой покупки AutoBuy приостановится, выставит мечи и продолжит работу.\n\n"
                + "⚠️ ВНИМАНИЕ: автоматизированное выставление может нарушать правила сервера "
                + "и привести к блокировке аккаунта.");
    }

    private void requestAutoSellDisable() {
        boolean wasEnabled = autoSellEnabled;
        setAutoSellEnabled(false);

        MinecraftClient minecraft = MinecraftClient.getInstance();
        boolean activeAutomaticSell;
        synchronized (sellLock) {
            activeAutomaticSell = inventorySellAutomatic && (inventorySellQueued || inventorySellWorking);
        }
        if (activeAutomaticSell && minecraft != null) {
            sendMessage("🛑 Автопродажа отключена. Текущее автоматическое выставление остановлено.");
            FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> cancelInventorySell(
                    minecraft,
                    "🛑 Автопродажа отключена. Текущее автоматическое выставление остановлено."
            ));
            return;
        }
        sendMessage(wasEnabled
                ? "🛑 Автопродажа отключена. Новые покупки больше не будут выставляться автоматически."
                : "ℹ️ Автопродажа уже выключена.");
    }

    private void processQueuedAutoSell(MinecraftClient minecraft) {
        String price;
        long now = System.currentTimeMillis();
        int limit;
        AutoBuy autoBuy = telegramAutoBuy();
        if (!autoBuy.isEnabled()) {
            // Do not clear the queued sell when AutoBuy is off: the sword is
            // still in inventory and must be listed. Just skip the pause step.
            if (autoSellAfterPurchaseQueued) {
                AutoBuy.debug("TG processQueuedAutoSell: autoBuy disabled, keeping queue");
            }
            return;
        }
        synchronized (sellLock) {
            if (!autoSellAfterPurchaseQueued) {
                return;
            }
            AutoBuy.debug("TG processQueuedAutoSell: ENTER queued=true"
                    + ", autoSellEnabled=" + autoSellEnabled
                    + ", inventorySellQueued=" + inventorySellQueued
                    + ", inventorySellWorking=" + inventorySellWorking
                    + ", autoSellRetryAt=" + autoSellRetryAt
                    + ", now=" + now
                    + ", autoBuyStep=" + autoBuy.getSessionStats().step()
                    + ", automationPaused=" + automationPaused
                    + ", autoSellPrice=" + autoSellPrice);
            if (!autoSellEnabled) {
                AutoBuy.debug("TG processQueuedAutoSell: EXIT autoSellEnabled=false");
                autoSellAfterPurchaseQueued = false;
                resetAutoSellStorageWaitLocked();
                return;
            }
            if (inventorySellQueued || inventorySellWorking) {
                AutoBuy.debug("TG processQueuedAutoSell: EXIT inventorySellQueued=" + inventorySellQueued
                        + " inventorySellWorking=" + inventorySellWorking);
                return;
            }
            // Only try to pause AutoBuy if it is not already paused for us.
            AutoBuy.SessionStats autoBuyStats = autoBuy.getSessionStats();
            if (!"WAIT_EXTERNAL".equals(autoBuyStats.step())
                    && !"SEARCH_DELAY".equals(autoBuyStats.step())) {
                if (!autoBuy.tryPauseForExternalAction(minecraft, 0L)) {
                    AutoBuy.debug("TG processQueuedAutoSell: EXIT tryPauseForExternalAction failed, step=" + autoBuyStats.step());
                    autoSellRetryAt = now + 250L;
                    return;
                }
            }
            if (now < autoSellRetryAt) {
                AutoBuy.debug("TG processQueuedAutoSell: EXIT autoSellRetryAt not reached, delta=" + (autoSellRetryAt - now));
                return;
            }
            limit = SELL_ALL_SWORDS;
            price = normalizeSellPrice(autoSellPrice);
            if (price == null) {
                autoSellAfterPurchaseQueued = false;
                autoSellEnabled = false;
                resetAutoSellStorageWaitLocked();
                FluxVisualsClient.requestConfigSave();
                sendNotification("⚠️ Автопродажа отключена: сохранённая цена некорректна.\n"
                        + "Включите её снова командой /autosell <цена>.");
                return;
            }
            autoSellAfterPurchaseQueued = false;
            inventorySellQueued = true;
            inventorySellAutomatic = true;
            inventorySellUseSellGui = true;
            autoSellTrace("starting sellgui: price=" + price + ", limit=" + limit
                    + ", slots=" + auctionSlotLimit + ", active=" + auctionActiveListings);
            inventorySellQueuedPrice = price;
            inventorySellQueuedLimit = limit;
            autoSellSlotLimitedRun = limit != SELL_ALL_SWORDS;
            autoSellReleasedSlots = 0;
            autoSellReleaseReadyAt = 0L;
            if (!autoSellSlotLimitedRun) {
                autoSellStorageKnownFull = false;
                autoSellStorageFullSince = 0L;
                autoSellRetryAt = 0L;
            }
        }
        if (limit == SELL_ALL_SWORDS) {
            trace("AutoSell: starting queued sell without a slot limit");
        } else {
            trace("AutoSell: storage released " + limit
                    + " slot(s), listing up to " + limit + " sword(s)");
        }
        persistRuntimeState();
    }

    private void resetAutoSellStorageWaitLocked() {
        autoSellStorageKnownFull = false;
        autoSellConsecutiveFailures = 0;
        autoSellRetryAt = 0L;
        autoSellReleasedSlots = 0;
        autoSellSlotLimitedRun = false;
        autoSellReleaseReadyAt = 0L;
        autoSellStorageFullSince = 0L;
    }

    private void armAutoSellStorageWaitLocked(long now) {
        autoSellStorageKnownFull = true;
        autoSellAfterPurchaseQueued = true;
        autoSellConsecutiveFailures = 0;
        autoSellReleasedSlots = 0;
        autoSellSlotLimitedRun = false;
        autoSellReleaseReadyAt = 0L;
        autoSellStorageFullSince = now;
        autoSellRetryAt = 0L;
    }

    private void processQueuedInventorySell(MinecraftClient minecraft) {
        if (minecraft == null) {
            return;
        }
        // A queued post-purchase sale has priority over background maintenance.
        // Otherwise AutoResell or an auction cooldown can leave AutoBuy in
        // WAIT_EXTERNAL without ever opening sellgui.
        String price;
        boolean automatic;
        int requestedCount;
        synchronized (sellLock) {
            if (!inventorySellQueued || inventorySellWorking) {
                return;
            }
            AutoBuy.debug("TG processQueuedInventorySell: STARTING inventorySellQueued=true"
                    + ", price=" + inventorySellQueuedPrice
                    + ", automatic=" + inventorySellAutomatic
                    + ", limit=" + inventorySellQueuedLimit);
            price = inventorySellQueuedPrice;
            automatic = inventorySellAutomatic;
            requestedCount = inventorySellQueuedLimit;
        }
        startInventorySell(minecraft, price, automatic, requestedCount);
    }

    public boolean isAutomaticSellPendingOrWorking() {
        synchronized (sellLock) {
            return (autoSellEnabled && autoSellAfterPurchaseQueued)
                    || (inventorySellAutomatic && (inventorySellQueued || inventorySellWorking));
        }
    }

    private void startInventorySell(MinecraftClient minecraft, String price, boolean automatic, int requestedCount) {
        boolean useSellGui;
        synchronized (sellLock) {
            if (!inventorySellQueued || inventorySellWorking
                    || inventorySellAutomatic != automatic
                    || !inventorySellQueuedPrice.equals(price)
                    || inventorySellQueuedLimit != requestedCount) {
                return;
            }
            useSellGui = inventorySellUseSellGui;
        }
        if (minecraft.player == null || minecraft.interactionManager == null
                || minecraft.getNetworkHandler() == null) {
            synchronized (sellLock) {
                inventorySellQueued = false;
                inventorySellAutomatic = false;
                inventorySellQueuedPrice = "";
                inventorySellQueuedLimit = SELL_ALL_SWORDS;
            }
            if (!automatic || shouldSendRoutineNotification()) {
                sendMessage("⚠️ Соединение с сервером потеряно. Выставление не началось.");
            }
            return;
        }

        int swordCount = countInventorySwords(minecraft.player.getInventory());
        if (swordCount == 0) {
            synchronized (sellLock) {
                inventorySellQueued = false;
                inventorySellAutomatic = false;
                inventorySellQueuedPrice = "";
                inventorySellQueuedLimit = SELL_ALL_SWORDS;
            }
            if (!automatic || shouldSendRoutineNotification()) {
                sendMessage(automatic
                        ? "📦 Автопродажа пропущена: в инвентаре нет незеритовых мечей."
                        : "📦 В инвентаре нет незеритовых мечей для выставления.");
            }
            return;
        }

        int targetCount = requestedCount == SELL_ALL_SWORDS
                ? swordCount
                : Math.min(swordCount, requestedCount);
        synchronized (sellLock) {
            inventorySellQueued = false;
            inventorySellWorking = true;
            inventorySellAutomatic = automatic;
            inventorySellUseSellGui = useSellGui;
            inventorySellQueuedPrice = "";
            inventorySellQueuedLimit = SELL_ALL_SWORDS;
            sellStage = SellStage.WAIT_AUTOBUY_PAUSE;
            inventorySellPrice = price;
            inventorySellTargetCount = targetCount;
            inventorySellCount = 0;
            inventorySellSwordCountBeforeCommand = 0;
            inventorySellRetryCount = 0;
            inventorySellServerRejected = false;
            inventorySellResumeAutoBuy = telegramAutoBuy().isEnabled();
            inventorySellInitialSelectedSlot = minecraft.player.getInventory().getSelectedSlot();
            inventorySellHotbarSlot = -1;
            inventorySellSwapSourceIndex = -1;
            initializeHumanSellProfile(targetCount);
            inventorySellNextActionAt = System.currentTimeMillis()
                    + nextHumanSellDelay(SellDelayAction.START, false, 0);
            inventorySellNextCommandAt = 0L;
            inventorySellDeadline = System.currentTimeMillis() + SELL_PAUSE_TIMEOUT_MS;
        }
        boolean autoBuyWasEnabled;
        synchronized (sellLock) {
            autoBuyWasEnabled = inventorySellResumeAutoBuy;
        }
        trace("Inventory sell: human timing profile tempo="
                + String.format(Locale.ROOT, "%.2f", inventorySellTempoFactor)
                + ", rhythm=" + Math.round(inventorySellRhythmMs)
                + " ms, ping=" + currentServerPing() + " ms"
                + ", first burst=" + inventorySellItemsUntilLongPause + " items");
        if (!automatic || shouldSendRoutineNotification()) {
            sendMessage((automatic ? "🤖⚔️ АВТОПРОДАЖА ЗАПУЩЕНА!\n\n" : "⚔️📦 НАЧИНАЮ ВЫСТАВЛЕНИЕ!\n\n")
                    + "🗡️ Найдено мечей: " + swordCount + "\n"
                    + "🎯 Будет выставлено: " + targetCount + "\n"
                    + "💰 Цена каждого: " + price
                    + (autoBuyWasEnabled ? "\n⏸️ AutoBuy приостановлен до завершения." : "")
                    + "\n\n⚠️ Автоматизированное выставление может привести к блокировке аккаунта.");
        }
    }

    private void processInventorySell(MinecraftClient minecraft, long now) {
        SellStage stage;
        synchronized (sellLock) {
            if (!inventorySellWorking) {
                return;
            }
            stage = sellStage;
        }
        if (now - lastAutoSellDiagnosticAt >= 1_000L) {
            lastAutoSellDiagnosticAt = now;
            trace("AutoSell tick: stage=" + stage + ", automatic=" + inventorySellAutomatic
                    + ", queued=" + inventorySellQueued + ", working=" + inventorySellWorking
                    + ", count=" + inventorySellCount + "/" + inventorySellTargetCount
                    + ", gui=" + inventorySellUseSellGui + ", screen="
                    + (minecraft == null || minecraft.currentScreen == null
                    ? "none" : minecraft.currentScreen.getClass().getSimpleName()));
        }
        if (minecraft == null || minecraft.player == null || minecraft.interactionManager == null
                || minecraft.getNetworkHandler() == null) {
            finishInventorySell(minecraft, false, "⚠️ Соединение с сервером потеряно.");
            return;
        }

        if (stage == SellStage.WAIT_AUTOBUY_PAUSE) {
            if (now < inventorySellNextActionAt) {
                return;
            }
            if (now >= inventorySellDeadline) {
                finishInventorySell(minecraft, false,
                        "⚠️ Не удалось безопасно приостановить AutoBuy за 15 секунд.");
                return;
            }
            closeCurrentScreen(minecraft);
            synchronized (sellLock) {
                sellStage = SellStage.WAIT_SCREEN_CLOSE;
                inventorySellNextActionAt = 0L;
                inventorySellDeadline = now + SELL_SCREEN_CLOSE_TIMEOUT_MS;
            }
            trace("Inventory sell: AutoBuy paused, waiting for player inventory handler");
            return;
        }

        if (stage == SellStage.WAIT_SCREEN_CLOSE) {
            if (minecraft.player.currentScreenHandler != minecraft.player.playerScreenHandler) {
                closeCurrentScreen(minecraft);
                if (now >= inventorySellDeadline) {
                    finishInventorySell(minecraft, false,
                            "⚠️ Не удалось закрыть меню AutoBuy перед выставлением.");
                }
                return;
            }
            if (inventorySellNextActionAt <= 0L) {
                synchronized (sellLock) {
                    inventorySellNextActionAt = now
                            + nextHumanSellDelay(SellDelayAction.AFTER_SCREEN_CLOSE, false, 0);
                }
                return;
            }
            if (now < inventorySellNextActionAt) {
                return;
            }
            synchronized (sellLock) {
                sellStage = inventorySellUseSellGui ? SellStage.SELLGUI_WAIT : SellStage.PREPARE_SWORD;
                inventorySellGuiFilledCount = 0;
                inventorySellGuiTargetSlot = -1;
                inventorySellGuiSourceIndex = -1;
                inventorySellNextActionAt = now;
                inventorySellDeadline = now + SELL_SERVER_TIMEOUT_MS * 3L;
                inventorySellNextActionAt = now;
            }
            if (inventorySellUseSellGui) {
                minecraft.getNetworkHandler().sendChatCommand("ah sellgui " + inventorySellPrice);
                trace("Inventory sell: opening sellgui for bulk listing");
            } else {
                trace("Inventory sell: using the single-item listing flow");
            }
            return;
        }

        if (stage == SellStage.SELLGUI_WAIT) {
            if (!(minecraft.currentScreen instanceof HandledScreen<?>)) {
                if (now >= inventorySellDeadline) {
                    finishInventorySell(minecraft, false, "⚠️ Не открылось меню массового выставления.");
                }
                return;
            }
            if (!isSellGuiScreen(minecraft)) {
                trace("AutoSell sellgui wait: wrong screen title=" + minecraft.currentScreen.getTitle().getString());
                return;
            }
            synchronized (sellLock) {
                sellStage = SellStage.SELLGUI_FILL;
                inventorySellNextActionAt = now + randomDelay(500L, 1_100L);
                inventorySellDeadline = now + SELL_SERVER_TIMEOUT_MS * 3L;
            }
            return;
        }

        if (stage == SellStage.SELLGUI_FILL) {
            if (!(minecraft.currentScreen instanceof HandledScreen<?>)) {
                if (now >= inventorySellDeadline) {
                    trace("AutoSell sellgui fill aborted: screen disappeared before filling");
                    finishInventorySell(minecraft, false,
                            "⚠️ Меню sellgui закрылось до заполнения мечей.");
                }
                return;
            }
            int confirmSlot = findSellGuiConfirmSlot(minecraft);
            if (confirmSlot >= 0 && inventorySellGuiFilledCount >= inventorySellTargetCount) {
                synchronized (sellLock) {
                    sellStage = SellStage.SELLGUI_CONFIRM;
                    inventorySellGuiTargetSlot = confirmSlot;
                    inventorySellNextActionAt = now + randomDelay(650L, 1_400L);
                }
                return;
            }
            if (now < inventorySellNextActionAt) {
                return;
            }
            int sourceIndex = findHumanizedInventorySword(minecraft.player.getInventory());
            int targetSlot = findSellGuiEmptySlot(minecraft);
            trace("AutoSell sellgui scan: source=" + sourceIndex + ", target=" + targetSlot
                    + ", confirm=" + confirmSlot + ", filled=" + inventorySellGuiFilledCount);
            if (sourceIndex < 0 || targetSlot < 0 || inventorySellGuiFilledCount >= inventorySellTargetCount) {
                if (confirmSlot >= 0) {
                    synchronized (sellLock) {
                        sellStage = SellStage.SELLGUI_CONFIRM;
                        inventorySellGuiTargetSlot = confirmSlot;
                        inventorySellNextActionAt = now + randomDelay(500L, 1_100L);
                    }
                } else if (now >= inventorySellDeadline) {
                    finishInventorySell(minecraft, false, "⚠️ В меню sellgui не найдено достаточно слотов.");
                }
                return;
            }
            int sourceSlot = findPlayerInventorySlot(minecraft, sourceIndex);
            if (sourceSlot < 0) {
                return;
            }
            clickSlot(minecraft, sourceSlot, SlotActionType.PICKUP);
            clickSlot(minecraft, targetSlot, SlotActionType.PICKUP);
            trace("AutoSell sellgui moved sword: sourceSlot=" + sourceSlot + ", targetSlot=" + targetSlot);
            synchronized (sellLock) {
                inventorySellGuiFilledCount++;
                inventorySellGuiSourceIndex = sourceIndex;
                inventorySellNextActionAt = now + randomDelay(700L, 1_600L);
            }
            return;
        }

        if (stage == SellStage.SELLGUI_CONFIRM) {
            if (now < inventorySellNextActionAt) {
                return;
            }
            if (inventorySellGuiTargetSlot >= 0 && isSellGuiScreen(minecraft)) {
                clickSlot(minecraft, inventorySellGuiTargetSlot, SlotActionType.PICKUP);
                trace("AutoSell sellgui confirmed: slot=" + inventorySellGuiTargetSlot);
                synchronized (sellLock) {
                    inventorySellCount = inventorySellGuiFilledCount;
                }
                finishInventorySell(minecraft, true, null);
            } else if (now >= inventorySellDeadline) {
                finishInventorySell(minecraft, false, "⚠️ Кнопка подтверждения sellgui не найдена.");
            }
            return;
        }

        if (stage == SellStage.PREPARE_SWORD) {
            if (now < inventorySellNextActionAt) {
                return;
            }
            boolean targetReached;
            boolean automatic;
            boolean slotLimitedRun;
            synchronized (sellLock) {
                targetReached = inventorySellCount >= inventorySellTargetCount;
                automatic = inventorySellAutomatic;
                slotLimitedRun = autoSellSlotLimitedRun;
            }
            if (targetReached) {
                if (automatic && slotLimitedRun) {
                    completeAutoSellSlotLimitedRun(minecraft, now);
                    return;
                }
                finishInventorySell(minecraft, true, null);
                return;
            }
            int swordIndex = findHumanizedInventorySword(minecraft.player.getInventory());
            if (swordIndex < 0) {
                finishInventorySell(minecraft, true, null);
                return;
            }
            prepareSwordInHand(minecraft, swordIndex);
            long delayBeforeListing;
            synchronized (sellLock) {
                delayBeforeListing = nextHumanSellDelay(SellDelayAction.BEFORE_LISTING, false, 0);
                sellStage = SellStage.SEND_SELL_COMMAND;
                inventorySellNextActionAt = now + delayBeforeListing;
                inventorySellDeadline = now + SELL_SERVER_TIMEOUT_MS;
                inventorySellRetryCount = 0;
            }
            trace("Inventory sell: prepared sword from inventory index " + swordIndex
                    + ", waiting " + delayBeforeListing + " ms before listing");
            return;
        }

        if (stage == SellStage.SEND_SELL_COMMAND) {
            if (now < inventorySellNextActionAt || now < inventorySellNextCommandAt) {
                return;
            }
            if (minecraft.player.currentScreenHandler != minecraft.player.playerScreenHandler) {
                closeCurrentScreen(minecraft);
                synchronized (sellLock) {
                    sellStage = SellStage.WAIT_SCREEN_CLOSE;
                    inventorySellNextActionAt = 0L;
                    inventorySellDeadline = now + SELL_SCREEN_CLOSE_TIMEOUT_MS;
                }
                return;
            }
            if (!selectedStackIsSword(minecraft.player.getInventory())) {
                if (inventorySellDeadline <= 0L) {
                    restoreSellSwap(minecraft);
                    synchronized (sellLock) {
                        sellStage = SellStage.PREPARE_SWORD;
                        inventorySellNextActionAt = now
                                + nextHumanSellDelay(SellDelayAction.BEFORE_TAKE, false, 0);
                        inventorySellDeadline = now + SELL_SERVER_TIMEOUT_MS;
                    }
                    trace("Inventory sell: selected sword moved, preparing it again before retry");
                    return;
                }
                if (now >= inventorySellDeadline) {
                    finishInventorySell(minecraft, false,
                            "⚠️ Не удалось взять следующий меч в руку.");
                }
                return;
            }
            trace("Inventory sell: sending /ah sell " + inventorySellPrice);
            synchronized (sellLock) {
                inventorySellSwordCountBeforeCommand = countInventorySwords(minecraft.player.getInventory());
            }
            minecraft.getNetworkHandler().sendChatCommand("ah sell " + inventorySellPrice);
            synchronized (sellLock) {
                sellStage = SellStage.WAIT_SERVER_SALE;
                inventorySellDeadline = now + SELL_SERVER_TIMEOUT_MS;
                inventorySellNextCommandAt = now
                        + nextHumanSellDelay(SellDelayAction.AFTER_COMMAND, false, 0);
            }
            return;
        }

        if (stage == SellStage.WAIT_SERVER_SALE) {
            int swordsBeforeCommand;
            synchronized (sellLock) {
                swordsBeforeCommand = inventorySellSwordCountBeforeCommand;
            }
            int currentSwordCount = countInventorySwords(minecraft.player.getInventory());
            
            if (currentSwordCount >= swordsBeforeCommand) {
                if (now >= inventorySellDeadline) {
                    boolean automatic;
                    synchronized (sellLock) {
                        automatic = inventorySellAutomatic;
                    }
                    if (automatic) {
                        finishInventorySell(
                                minecraft,
                                false,
                                "⚠️ Сервер не подтвердил автоматическое выставление меча.\n"
                                        + "🗡️ Повторные команды не отправлялись, меч оставлен в инвентаре."
                        );
                    } else {
                        retryInventorySellOrStop(
                                minecraft,
                                now,
                                "Сервер не подтвердил выставление меча"
                        );
                    }
                }
                return;
            }
            long delayBeforeNextSword;
            boolean longPause;
            synchronized (sellLock) {
                inventorySellCount++;
                if (inventorySellAutomatic) {
                    auctionActiveListings = Math.min(
                            Math.max(auctionSlotLimit, 1),
                            auctionActiveListings + 1
                    );
                    auctionTotalListed++;
                    telegramAutoBuy().noteAuctionListingConfirmed();
                }
                if (!autoSellSlotLimitedRun) {
                    autoSellStorageKnownFull = false;
                }
                autoSellConsecutiveFailures = 0;
                if (!autoSellSlotLimitedRun) {
                    autoSellRetryAt = 0L;
                }
                inventorySellServerRejected = false;
                inventorySellItemsUntilLongPause--;
                longPause = inventorySellItemsUntilLongPause <= 0;
                if (longPause) {
                    inventorySellItemsUntilLongPause = randomHumanSellBurstLength();
                }
                delayBeforeNextSword = nextHumanSellDelay(
                        SellDelayAction.BEFORE_TAKE,
                        longPause,
                        0
                );
            }
            trace("Inventory sell: server removed listed sword");
            restoreSellSwap(minecraft);
            synchronized (sellLock) {
                sellStage = SellStage.PREPARE_SWORD;
                inventorySellNextActionAt = now + delayBeforeNextSword;
                inventorySellRetryCount = 0;
            }
            trace("Inventory sell: waiting " + delayBeforeNextSword + " ms before taking the next sword"
                    + (longPause ? " after a completed item burst" : ""));
            return;
        }

    }

    private boolean handleInventoryStorageFullMessage(String message) {
        if (!isAuctionStorageFullMessage(message)) {
            return false;
        }
        boolean automatic;
        int selectedSlot;
        synchronized (sellLock) {
            if (!inventorySellWorking || sellStage != SellStage.WAIT_SERVER_SALE) {
                return false;
            }
            automatic = inventorySellAutomatic;
            selectedSlot = inventorySellInitialSelectedSlot;
        }
        if (!automatic) {
            trace("Inventory sell: storage is full, stopping manual /sell");
            finishInventorySell(
                    MinecraftClient.getInstance(),
                    false,
                    "📦 Хранилище заполнено.\n"
                            + "🗡️ Ручное выставление остановлено, меч оставлен в инвентаре."
            );
            return true;
        }
        
        // Для автопродажи: НЕМЕДЛЕННО останавливаем текущее выставление и ждём продажи
        trace("Inventory sell: storage full during automatic sell, STOPPING immediately and waiting for auction sales");

        MinecraftClient minecraft = MinecraftClient.getInstance();

        // Возвращаем меч обратно в инвентарь
        restoreSellSwap(minecraft);

        // Восстанавливаем выбранный слот хотбара
        if (minecraft != null && minecraft.player != null && selectedSlot >= 0 && selectedSlot < 9) {
            selectHotbarSlot(minecraft, selectedSlot);
        }

        long now = System.currentTimeMillis();
        int listedCount;
        // КРИТИЧЕСКИ ВАЖНО: останавливаем операцию БЕЗ полного сброса состояния
        synchronized (sellLock) {
            listedCount = inventorySellCount;

            // Переходим в режим ожидания освободившегося слота хранилища
            armAutoSellStorageWaitLocked(now);

            // Останавливаем текущую операцию
            inventorySellWorking = false;
            sellStage = SellStage.IDLE;
            inventorySellServerRejected = false;

            // Сбрасываем только "текущие" переменные операции
            inventorySellCount = 0;
            inventorySellRetryCount = 0;
            inventorySellNextActionAt = 0L;
            inventorySellNextCommandAt = 0L;
            inventorySellDeadline = 0L;
            inventorySellLastSwordIndex = -1;

            // НЕ сбрасываем эти важные флаги для возобновления:
            // - inventorySellAutomatic (остаётся true)
            // - inventorySellResumeAutoBuy (сохраняем для возобновления)
            // - inventorySellPrice, autoSellPrice (сохраняем цену)
            // - inventorySellQueued может быть сброшен, т.к. мы не в очереди
            inventorySellQueued = false;
        }

        inventorySellSwapSourceIndex = -1;
        inventorySellHotbarSlot = -1;

        int waitingSwords = minecraft != null && minecraft.player != null
                ? countInventorySwords(minecraft.player.getInventory())
                : 0;
        trace("Inventory sell: STOPPED immediately after listing " + listedCount
                + " sword(s), " + waitingSwords + " left in inventory, waiting for an auction sale");
        if (shouldSendRoutineNotification()) {
            sendMessage("📦 Хранилище полное — выставить меч не удалось.\n\n"
                    + "⚔️ Успел выставить: " + listedCount + "\n"
                    + "🗡️ Осталось в инвентаре: " + waitingSwords + "\n"
                    + "⏳ Жду продажи лота — выставлю ровно столько мечей, сколько освободится слотов.\n"
                    + "🔎 AutoBuy продолжает работать.");
        }
        persistRuntimeState();
        return true;
    }

    private boolean handleInventorySellFailureMessage(String message) {
        if (!isAuctionListingFailureMessage(message)) {
            return false;
        }
        boolean automatic;
        synchronized (sellLock) {
            if (!inventorySellWorking || sellStage != SellStage.WAIT_SERVER_SALE) {
                return false;
            }
            automatic = inventorySellAutomatic;
        }
        if (automatic) {
            finishInventorySell(
                    MinecraftClient.getInstance(),
                    false,
                    "⚠️ Сервер отклонил автоматическое выставление меча.\n"
                            + "🗡️ Повторные команды не отправлялись, меч оставлен в инвентаре."
            );
            return true;
        }
        synchronized (sellLock) {
            inventorySellServerRejected = true;
        }
        retryManualSellAfterRejection(
                MinecraftClient.getInstance(),
                System.currentTimeMillis()
        );
        return true;
    }

    private void retryManualSellAfterRejection(MinecraftClient minecraft, long now) {
        int retryNumber;
        synchronized (sellLock) {
            if (!inventorySellWorking || sellStage != SellStage.WAIT_SERVER_SALE) {
                return;
            }
            if (inventorySellRetryCount >= SELL_REJECTION_RETRIES) {
                retryNumber = -1;
            } else {
                inventorySellRetryCount++;
                retryNumber = inventorySellRetryCount;
                sellStage = SellStage.SEND_SELL_COMMAND;
                inventorySellNextActionAt = now
                        + nextHumanSellDelay(SellDelayAction.RETRY, false, retryNumber);
                inventorySellDeadline = 0L;
            }
        }
        if (retryNumber >= 0) {
            trace("Inventory sell: manual /sell rejection retry "
                    + retryNumber + '/' + SELL_REJECTION_RETRIES + " scheduled");
            return;
        }
        trace("Inventory sell: manual /sell rejection retry limit reached");
        finishInventorySell(
                minecraft,
                false,
                "⚠️ Сервер отклонил выставление после "
                        + SELL_REJECTION_RETRIES + " повторных попыток.\n"
                        + "🗡️ Меч оставлен в инвентаре."
        );
    }

    /**
     * Сервер подтвердил продажу нашего лота — значит в хранилище освободился ровно один слот.
     * Копим такие слоты как «кредиты»: сколько продалось, столько мечей и выставим дальше.
     * Само выставление запускает {@link #processQueuedAutoSell(MinecraftClient)}.
     */
    private void registerAuctionSaleReleasedSlot(String message) {
        long now = System.currentTimeMillis();
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int swordsInInventory = minecraft != null && minecraft.player != null
                ? countInventorySwords(minecraft.player.getInventory())
                : 0;
        int releasedSlots = 0;
        long waitedMs = 0L;
        boolean nothingLeft = false;
        boolean granted = false;

        synchronized (sellLock) {
            if (auctionActiveListings > 0) {
                auctionActiveListings--;
            }
            auctionTotalSold++;
            if (!autoSellEnabled) {
                return;
            }

            if (swordsInInventory <= 0) {
                autoSellAfterPurchaseQueued = false;
                autoSellStorageKnownFull = auctionSlotLimit > 0
                        && auctionActiveListings >= auctionSlotLimit;
                nothingLeft = true;
            } else {
                autoSellReleasedSlots++;
                autoSellAfterPurchaseQueued = true;
                granted = true;
                releasedSlots = autoSellReleasedSlots;
                waitedMs = autoSellStorageFullSince > 0L ? now - autoSellStorageFullSince : 0L;
                // Пауза «на реакцию»: человек сначала замечает сообщение в чате и только потом идёт выставлять.
                if (autoSellReleaseReadyAt <= 0L) {
                    autoSellReleaseReadyAt = now
                            + randomDelay(AUTO_SELL_SLOT_REACTION_MIN_MS, AUTO_SELL_SLOT_REACTION_MAX_MS);
                }
                autoSellRetryAt = 0L;
            }
        }

        if (nothingLeft) {
            trace("AutoSell: storage slot released but no swords left in inventory, leaving the wait state");
            return;
        }
        if (!granted) {
            trace("AutoSell: storage slot released, but all " + swordsInInventory
                    + " sword(s) in inventory are already covered by " + releasedSlots + " credit(s)");
            return;
        }
        trace("AutoSell: storage slot released (" + releasedSlots + '/' + swordsInInventory
                + " swords covered) after waiting " + waitedMs + " ms");
        if (shouldSendRoutineNotification()) {
            sendMessage("✅📦 Лот продан, слот в хранилище освободился!\n\n"
                    + "🔄 Выставлю ещё: " + releasedSlots + " из " + swordsInInventory + " меч(ей)\n"
                    + "💰 Цена: " + autoSellPrice + "\n"
                    + "⏳ Остальные ждут следующих продаж.");
        }
        persistRuntimeState();
    }

    private void completeAutoSellSlotLimitedRun(MinecraftClient minecraft, long now) {
        int remainingSwords = minecraft != null && minecraft.player != null
                ? countInventorySwords(minecraft.player.getInventory())
                : 0;
        int pendingSlots;
        synchronized (sellLock) {
            autoSellSlotLimitedRun = false;
            if (remainingSwords <= 0) {
                autoSellAfterPurchaseQueued = false;
                resetAutoSellStorageWaitLocked();
                pendingSlots = 0;
            } else {
                autoSellStorageKnownFull = true;
                autoSellAfterPurchaseQueued = true;
                if (autoSellStorageFullSince <= 0L) {
                    autoSellStorageFullSince = now;
                }
                if (autoSellReleasedSlots > 0) {
                    autoSellReleaseReadyAt = Math.max(autoSellReleaseReadyAt, now);
                }
                autoSellRetryAt = 0L;
                pendingSlots = autoSellReleasedSlots;
            }
        }
        finishInventorySell(minecraft, true, null, false);
        if (remainingSwords <= 0) {
            trace("AutoSell: slot-limited batch completed, no swords remain in inventory");
            persistRuntimeState();
            return;
        }
        trace("AutoSell: slot-limited batch completed, " + remainingSwords
                + " sword(s) remain and " + pendingSlots + " newly released slot(s) are pending");
        persistRuntimeState();
    }

    private void retryInventorySellOrStop(MinecraftClient minecraft, long now, String reason) {
        int retryNumber;
        synchronized (sellLock) {
            if (!inventorySellWorking || sellStage != SellStage.WAIT_SERVER_SALE) {
                return;
            }
            if (inventorySellRetryCount >= SELL_MAX_RETRIES) {
                retryNumber = -1;
            } else {
                inventorySellRetryCount++;
                retryNumber = inventorySellRetryCount;
                sellStage = SellStage.SEND_SELL_COMMAND;
                inventorySellNextActionAt = now
                        + nextHumanSellDelay(SellDelayAction.RETRY, false, retryNumber);
                inventorySellDeadline = 0L;
            }
        }
        if (retryNumber < 0) {
            trace("Inventory sell: retry limit reached, leaving sword in inventory");
            finishInventorySell(
                    minecraft,
                    false,
                    "⚠️ " + reason + " после 5 повторных попыток.\n"
                            + "🗡️ Меч оставлен в инвентаре и больше не будет выставляться в этой операции."
            );
            return;
        }
        trace("Inventory sell: retry " + retryNumber + '/' + SELL_MAX_RETRIES + " scheduled");
    }

    private void prepareSwordInHand(MinecraftClient minecraft, int inventoryIndex) {
        PlayerInventory inventory = minecraft.player.getInventory();
        inventorySellSwapSourceIndex = -1;
        if (inventoryIndex < 9) {
            inventorySellHotbarSlot = inventoryIndex;
            selectHotbarSlot(minecraft, inventoryIndex);
            return;
        }

        int hotbarSlot = findEmptyHotbarSlot(inventory);
        boolean restorePreviousItem = hotbarSlot < 0;
        if (hotbarSlot < 0) {
            hotbarSlot = inventory.getSelectedSlot();
        }
        inventorySellHotbarSlot = hotbarSlot;
        selectHotbarSlot(minecraft, hotbarSlot);
        minecraft.interactionManager.clickSlot(
                minecraft.player.playerScreenHandler.syncId,
                screenSlot(inventoryIndex),
                hotbarSlot,
                SlotActionType.SWAP,
                minecraft.player
        );
        if (restorePreviousItem) {
            inventorySellSwapSourceIndex = inventoryIndex;
        }
    }

    private void restoreSellSwap(MinecraftClient minecraft) {
        if (inventorySellSwapSourceIndex >= 0 && inventorySellHotbarSlot >= 0
                && minecraft.player != null && minecraft.interactionManager != null) {
            minecraft.interactionManager.clickSlot(
                    minecraft.player.playerScreenHandler.syncId,
                    screenSlot(inventorySellSwapSourceIndex),
                    inventorySellHotbarSlot,
                    SlotActionType.SWAP,
                    minecraft.player
            );
        }
        inventorySellSwapSourceIndex = -1;
        inventorySellHotbarSlot = -1;
    }

    private void finishInventorySell(MinecraftClient minecraft, boolean success, String error) {
        finishInventorySell(minecraft, success, error, true);
    }

    private void finishInventorySell(MinecraftClient minecraft, boolean success, String error, boolean notify) {
        int listedCount;
        int selectedSlot;
        SellStage finalStage;
        boolean resumeAutoBuy;
        boolean automatic;
        boolean serverRejected;
        synchronized (sellLock) {
            listedCount = inventorySellCount;
            selectedSlot = inventorySellInitialSelectedSlot;
            finalStage = sellStage;
            resumeAutoBuy = inventorySellResumeAutoBuy;
            automatic = inventorySellAutomatic;
            serverRejected = inventorySellServerRejected;
        }
        boolean completed = success && !serverRejected;
        boolean swordStillInHand = minecraft != null
                && minecraft.player != null
                && selectedStackIsSword(minecraft.player.getInventory());
        if (finalStage != SellStage.WAIT_SERVER_SALE || swordStillInHand) {
            restoreSellSwap(minecraft);
        }
        synchronized (sellLock) {
            inventorySellQueued = false;
            inventorySellWorking = false;
            inventorySellAutomatic = false;
            sellStage = SellStage.IDLE;
            inventorySellQueuedPrice = "";
            inventorySellQueuedLimit = SELL_ALL_SWORDS;
            inventorySellPrice = "";
            inventorySellTargetCount = 0;
            inventorySellCount = 0;
            inventorySellUseSellGui = false;
            inventorySellGuiTargetSlot = -1;
            inventorySellGuiFilledCount = 0;
            inventorySellGuiSourceIndex = -1;
            inventorySellSwordCountBeforeCommand = 0;
            inventorySellRetryCount = 0;
            inventorySellServerRejected = false;
            inventorySellResumeAutoBuy = false;
            inventorySellInitialSelectedSlot = -1;
            inventorySellNextActionAt = 0L;
            inventorySellNextCommandAt = 0L;
            inventorySellDeadline = 0L;
            inventorySellLastHumanDelay = 0L;
            inventorySellItemsUntilLongPause = 0;
            inventorySellActionsUntilDriftChange = 0;
            inventorySellLastSwordIndex = -1;
            inventorySellTempoFactor = 1.0D;
            inventorySellRhythmMs = 0.0D;
            inventorySellDrift = 0.0D;
            inventorySellDriftTarget = 0.0D;
        }
        if (minecraft != null && minecraft.player != null && selectedSlot >= 0 && selectedSlot < 9) {
            selectHotbarSlot(minecraft, selectedSlot);
        }
        inventorySellSwapSourceIndex = -1;
        inventorySellHotbarSlot = -1;

        if (resumeAutoBuy && minecraft != null) {
            telegramAutoBuy().resumeAfterExternalAction(minecraft);
        }

        if (!notify || automatic && (!notificationsEnabled || automationTransactionsOnly && !completed)) {
            return;
        }
        if (completed) {
            sendMessage((automatic ? "✅🤖 АВТОПРОДАЖА ЗАВЕРШЕНА!\n\n" : "✅🎉 ВСЕ МЕЧИ ВЫСТАВЛЕНЫ!\n\n")
                    + "⚔️ Выставлено мечей: " + listedCount
                    + (resumeAutoBuy ? "\n🔄 AutoBuy возвращается к своей работе." : ""));
        } else {
            sendMessage((error == null ? "⚠️ Выставление мечей остановлено." : error)
                    + "\n📊 Успешно выставлено до остановки: " + listedCount);
        }
    }

    private void cancelInventorySell(MinecraftClient minecraft, String message) {
        boolean active;
        synchronized (sellLock) {
            active = inventorySellQueued || inventorySellWorking;
        }
        if (!active) {
            return;
        }
        finishInventorySell(minecraft, false, message == null ? "🛑 Выставление мечей отменено." : message);
    }

    private static int countInventorySwords(PlayerInventory inventory) {
        int count = 0;
        for (int index = 0; index < 36; index++) {
            if (inventory.getStack(index).isOf(Items.NETHERITE_SWORD)) {
                count++;
            }
        }
        return count;
    }

    private int findHumanizedInventorySword(PlayerInventory inventory) {
        int[] candidates = new int[36];
        int candidateCount = 0;
        for (int index = 0; index < 36; index++) {
            if (inventory.getStack(index).isOf(Items.NETHERITE_SWORD)) {
                candidates[candidateCount++] = index;
            }
        }
        if (candidateCount == 0) {
            return -1;
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        int candidateOffset = 0;
        if (candidateCount > 2 && roll >= 95) {
            candidateOffset = 2;
        } else if (candidateCount > 1 && roll >= 78) {
            candidateOffset = 1;
        }
        int selectedIndex = candidates[candidateOffset];
        if (candidateCount > 1 && selectedIndex == inventorySellLastSwordIndex) {
            selectedIndex = candidates[(candidateOffset + 1) % Math.min(candidateCount, 3)];
        }
        inventorySellLastSwordIndex = selectedIndex;
        return selectedIndex;
    }

    private static int findEmptyHotbarSlot(PlayerInventory inventory) {
        for (int index = 0; index < 9; index++) {
            if (inventory.getStack(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static boolean selectedStackIsSword(PlayerInventory inventory) {
        return inventory.getStack(inventory.getSelectedSlot()).isOf(Items.NETHERITE_SWORD);
    }

    private static void selectHotbarSlot(MinecraftClient minecraft, int hotbarSlot) {
        if (minecraft == null || minecraft.player == null || hotbarSlot < 0 || hotbarSlot >= 9) {
            return;
        }
        minecraft.player.getInventory().setSelectedSlot(hotbarSlot);
        if (minecraft.getNetworkHandler() != null) {
            minecraft.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }
    }

    private static String normalizeSellPrice(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 16 && SELL_PRICE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private static InventorySellRequest parseInventorySellRequest(String argument) {
        if (argument == null || argument.isBlank()) {
            return null;
        }
        String[] parts = argument.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 2) {
            return null;
        }
        String price = normalizeSellPrice(parts[0]);
        if (price == null) {
            return null;
        }
        if (parts.length == 1 || parts[1].equalsIgnoreCase("all")) {
            return new InventorySellRequest(price, SELL_ALL_SWORDS);
        }
        try {
            long requestedCount = Long.parseLong(parts[1]);
            if (requestedCount <= 0L) {
                return null;
            }
            return new InventorySellRequest(price, (int) Math.min(requestedCount, Integer.MAX_VALUE));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int screenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static boolean isSellGuiScreen(MinecraftClient minecraft) {
        if (minecraft == null || minecraft.currentScreen == null) {
            return false;
        }
        String title = minecraft.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("sellgui") || title.contains("продаж") || title.contains("выстав");
    }

    private static int findSellGuiEmptySlot(MinecraftClient minecraft) {
        if (minecraft.player == null || minecraft.player.currentScreenHandler == null) {
            return -1;
        }
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot != null && slot.inventory != minecraft.player.getInventory()
                    && !slot.hasStack()) {
                return slot.id;
            }
        }
        return -1;
    }

    private static int findSellGuiConfirmSlot(MinecraftClient minecraft) {
        if (minecraft.player == null || minecraft.player.currentScreenHandler == null) {
            return -1;
        }
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == minecraft.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("подтверд") || name.contains("выстав") || name.contains("confirm")) {
                return slot.id;
            }
        }
        return -1;
    }

    private static int findPlayerInventorySlot(MinecraftClient minecraft, int inventoryIndex) {
        if (minecraft.player == null || minecraft.player.currentScreenHandler == null) {
            return -1;
        }
        for (Slot slot : minecraft.player.currentScreenHandler.slots) {
            if (slot != null && slot.inventory == minecraft.player.getInventory()
                    && slot.getIndex() == inventoryIndex) {
                return slot.id;
            }
        }
        return -1;
    }

    private static void clickSlot(MinecraftClient minecraft, int slotId, SlotActionType actionType) {
        if (minecraft == null || minecraft.player == null || minecraft.interactionManager == null
                || minecraft.player.currentScreenHandler == null || slotId < 0) {
            return;
        }
        minecraft.interactionManager.clickSlot(
                minecraft.player.currentScreenHandler.syncId,
                slotId,
                0,
                actionType,
                minecraft.player
        );
    }

    private void initializeHumanSellProfile(int targetCount) {
        int profile = ThreadLocalRandom.current().nextInt(100);
        if (profile < 20) {
            inventorySellTempoFactor = ThreadLocalRandom.current().nextDouble(0.88D, 0.99D);
        } else if (profile < 80) {
            inventorySellTempoFactor = ThreadLocalRandom.current().nextDouble(0.98D, 1.11D);
        } else {
            inventorySellTempoFactor = ThreadLocalRandom.current().nextDouble(1.10D, 1.23D);
        }
        inventorySellRhythmMs = randomDelay(850L, 1_250L);
        inventorySellDrift = 0.0D;
        inventorySellDriftTarget = ThreadLocalRandom.current().nextDouble(-0.08D, 0.11D);
        inventorySellActionsUntilDriftChange = randomHumanDriftLength();
        inventorySellLastHumanDelay = 0L;
        inventorySellItemsUntilLongPause = randomHumanSellBurstLength();
        inventorySellLastSwordIndex = -1;
        if (targetCount >= 8 && ThreadLocalRandom.current().nextInt(100) < 35) {
            inventorySellTempoFactor += ThreadLocalRandom.current().nextDouble(0.03D, 0.08D);
        }
    }

    private long nextHumanSellDelay(SellDelayAction action, boolean preferLongPause, int retryNumber) {
        if (inventorySellActionsUntilDriftChange <= 0) {
            inventorySellDriftTarget = ThreadLocalRandom.current().nextDouble(-0.10D, 0.14D);
            inventorySellActionsUntilDriftChange = randomHumanDriftLength();
        }
        inventorySellDrift += (inventorySellDriftTarget - inventorySellDrift)
                * ThreadLocalRandom.current().nextDouble(0.18D, 0.34D);
        inventorySellActionsUntilDriftChange--;

        long firstSample = randomDelay(action.minDelayMs(), action.maxDelayMs());
        long secondSample = randomDelay(action.minDelayMs(), action.maxDelayMs());
        double actionTarget = (firstSample + secondSample) / 2.0D;
        actionTarget *= inventorySellTempoFactor * (1.0D + inventorySellDrift);

        if (inventorySellRhythmMs <= 0.0D) {
            inventorySellRhythmMs = actionTarget;
        } else {
            inventorySellRhythmMs += (actionTarget - inventorySellRhythmMs)
                    * ThreadLocalRandom.current().nextDouble(0.24D, 0.43D);
        }

        double delay = actionTarget * 0.64D + inventorySellRhythmMs * 0.36D;
        int ping = currentServerPing();
        if (ping > 0) {
            delay += Math.min(SELL_HUMAN_PING_CAP_MS, ping)
                    * ThreadLocalRandom.current().nextDouble(0.18D, 0.38D);
        }

        if (inventorySellTargetCount >= 6) {
            double progress = inventorySellTargetCount <= 1
                    ? 0.0D
                    : Math.min(1.0D, inventorySellCount / (double) (inventorySellTargetCount - 1));
            delay += progress * 220.0D;
            delay += Math.min(120.0D, Math.max(0, inventorySellTargetCount - 6) * 8.0D);
        }
        if (retryNumber > 0) {
            delay += Math.min(320.0D, retryNumber * 105.0D);
        }
        if (preferLongPause) {
            delay = Math.max(
                    delay,
                    randomDelay(SELL_HUMAN_LONG_PAUSE_MIN_MS, SELL_HUMAN_ACTION_MAX_MS)
            );
        }
        delay += ThreadLocalRandom.current().nextDouble(-95.0D, 96.0D);

        long roundedDelay = clampHumanSellDelay(Math.round(delay));
        if (inventorySellLastHumanDelay > 0L
                && Math.abs(roundedDelay - inventorySellLastHumanDelay) < 55L) {
            long correction = randomDelay(80L, 180L);
            roundedDelay = clampHumanSellDelay(ThreadLocalRandom.current().nextBoolean()
                    ? roundedDelay + correction
                    : roundedDelay - correction);
        }
        inventorySellLastHumanDelay = roundedDelay;
        return roundedDelay;
    }

    private static long clampHumanSellDelay(long delay) {
        return Math.max(SELL_HUMAN_ACTION_MIN_MS, Math.min(SELL_HUMAN_ACTION_MAX_MS, delay));
    }

    private static int currentServerPing() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            return 0;
        }
        try {
            var entry = minecraft.getNetworkHandler().getPlayerListEntry(minecraft.player.getUuid());
            return entry == null ? 0 : Math.max(0, entry.getLatency());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int randomHumanDriftLength() {
        return ThreadLocalRandom.current().nextInt(
                SELL_HUMAN_DRIFT_MIN_ACTIONS,
                SELL_HUMAN_DRIFT_MAX_ACTIONS + 1
        );
    }

    private static int randomHumanSellBurstLength() {
        return ThreadLocalRandom.current().nextInt(
                SELL_HUMAN_BURST_MIN_ITEMS,
                SELL_HUMAN_BURST_MAX_ITEMS + 1
        );
    }

    private static long randomDelay(long minInclusive, long maxInclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1L);
    }

    private enum SellDelayAction {
        START(800L, 1_800L),
        AFTER_SCREEN_CLOSE(650L, 1_500L),
        BEFORE_TAKE(500L, 1_450L),
        BEFORE_LISTING(700L, 1_900L),
        AFTER_COMMAND(550L, 1_450L),
        RETRY(850L, 1_900L);

        private final long minDelayMs;
        private final long maxDelayMs;

        SellDelayAction(long minDelayMs, long maxDelayMs) {
            this.minDelayMs = minDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        private long minDelayMs() {
            return minDelayMs;
        }

        private long maxDelayMs() {
            return maxDelayMs;
        }
    }

    private static void closeCurrentScreen(MinecraftClient minecraft) {
        if (minecraft == null || minecraft.currentScreen == null) {
            return;
        }
        if (ClientGuiProtection.isOpen(minecraft)) {
            return;
        }
        if (minecraft.player != null && minecraft.currentScreen instanceof HandledScreen<?>) {
            minecraft.player.closeHandledScreen();
        } else {
            minecraft.setScreen(null);
        }
    }

    private void processQueuedCheckScreenshot() {
        if (!checkScreenshotQueued || screenshotRequestPending) {
            return;
        }
        checkScreenshotQueued = false;
        requestScreenshotFromMinecraft(
                "🚨 Вас вызвали на проверку!\n⛔ AutoBuy выключен автоматически",
                false
        );
    }

    private void processQueuedBanScreenshot(long now) {
        String caption = pendingBanScreenshotCaption;
        if (caption == null || caption.isBlank() || now < pendingBanScreenshotAt || screenshotRequestPending) {
            return;
        }
        pendingBanScreenshotCaption = "";
        pendingBanScreenshotAt = 0L;
        requestScreenshotFromMinecraft(caption, false);
    }

    public void requestScreenshot(BotSession targetSession, boolean openInventory, String customCaption, boolean announceProgress) {
        if (targetSession != null && !targetSession.isMain()) {
            sendBotInventoryPhoto(targetSession.getName());
            return;
        }
        requestScreenshotFromMinecraft(customCaption, announceProgress);
    }

    public void requestScreenshotFromMinecraft(String customCaption, boolean announceProgress) {
        BotSession tgTarget = FluxVisualsClient.MULTI_BOT_MANAGER != null
                ? FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetSession()
                : null;
        if (tgTarget != null && !tgTarget.isMain()) {
            sendBotInventoryPhoto(tgTarget.getName());
            return;
        }

        if (screenshotRequestPending) {
            if (announceProgress) {
                sendMessage("⏳ Скриншот уже создаётся или отправляется. Подождите.");
            }
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.getWindow() == null || minecraft.getFramebuffer() == null) {
            sendMessage("⚠️ Minecraft сейчас недоступен, поэтому сделать скриншот нельзя.");
            return;
        }

        screenshotRequestPending = true;
        if (announceProgress) {
            sendMessage("📸✨ Делаю скриншот экрана...");
        }

        minecraft.execute(() -> {
            if (!screenshotRequestPending || !isEnabled() || minecraft.getFramebuffer() == null) {
                screenshotRequestPending = false;
                return;
            }
            try {
                ScreenshotRecorder.takeScreenshot(minecraft.getFramebuffer(), image -> {
                    Path temporaryFile = null;
                    try (image) {
                        int width = image.getWidth();
                        int height = image.getHeight();
                        temporaryFile = Files.createTempFile("fuga-minecraft-screen-", ".png");
                        image.writeTo(temporaryFile);
                        byte[] png = Files.readAllBytes(temporaryFile);
                        String caption;
                        if (customCaption == null || customCaption.isBlank()) {
                            BotSession target = FluxVisualsClient.MULTI_BOT_MANAGER.getTelegramTargetSession();
                            String accountLabel = target != null ? (target.getName() + (target.isMain() ? " [Основа]" : " [Бот]")) : "Minecraft";
                            caption = "📸 Скриншот экрана (<b>" + accountLabel + "</b>)\n"
                                    + "🖥️ Разрешение: " + width + "x" + height + "\n"
                                    + "🕒 Время: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT);
                        } else {
                            caption = customCaption + "\n"
                                    + "🖥️ Разрешение: " + width + "x" + height + "\n"
                                    + "🕒 Время: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT);
                        }
                        sendScreenshot(png, width, height, caption);
                    } catch (IOException | RuntimeException ex) {
                        screenshotRequestPending = false;
                        LOGGER.warn("[TG] Failed to create screenshot: {}", ex.toString());
                        sendMessage("⚠️ Не удалось создать скриншот Minecraft.");
                    } finally {
                        if (temporaryFile != null) {
                            try {
                                Files.deleteIfExists(temporaryFile);
                            } catch (IOException ex) {
                                trace("Failed to delete temporary screenshot: " + ex);
                            }
                        }
                    }
                });
            } catch (RuntimeException ex) {
                screenshotRequestPending = false;
                LOGGER.warn("[TG] Failed to capture framebuffer: {}", ex.toString());
                sendMessage("⚠️ Не удалось захватить игровой кадр.");
            }
        });
    }

    public void requestScreenshotFromMinecraft() {
        requestScreenshotFromMinecraft(null, true);
    }

    private void sendScreenshot(byte[] png, int width, int height, String caption) {
        if (png == null || png.length == 0) {
            screenshotRequestPending = false;
            sendMessage("⚠️ Получился пустой скриншот.");
            return;
        }

        boolean sendAsPhoto = png.length <= TELEGRAM_PHOTO_MAX_BYTES && width + height <= 10_000;
        if (!sendAsPhoto && png.length > TELEGRAM_DOCUMENT_MAX_BYTES) {
            screenshotRequestPending = false;
            sendMessage("⚠️ Скриншот слишком большой для отправки в Telegram.");
            return;
        }

        String method = sendAsPhoto ? "sendPhoto" : "sendDocument";
        String fileField = sendAsPhoto ? "photo" : "document";
        if (!beginTelegramAsyncRequest(method)) {
            screenshotRequestPending = false;
            return;
        }
        String boundary = "----FugaClient" + UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        try {
            body = multipartBody(boundary, fileField, "minecraft-screen.png", png, caption);
        } catch (IOException ex) {
            endTelegramAsyncRequest();
            screenshotRequestPending = false;
            LOGGER.warn("[TG] Failed to prepare screenshot upload: {}", ex.toString());
            sendMessage("⚠️ Не удалось подготовить скриншот к отправке.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + botToken + '/' + method))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        endTelegramAsyncRequest();
                        screenshotRequestPending = false;
                        if (throwable == null) {
                            handleApiResponse(method, response);
                        } else {
                            recordTelegramNetworkFailure(method, throwable);
                        }
                    });
        } catch (RuntimeException ex) {
            endTelegramAsyncRequest();
            screenshotRequestPending = false;
            recordTelegramNetworkFailure(method, ex);
        }
    }

    private byte[] multipartBody(
            String boundary,
            String fileField,
            String fileName,
            byte[] fileData,
            String caption
    ) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(fileData.length + 1024)) {
            writeMultipartText(output, boundary, "chat_id", chatId);
            writeMultipartText(output, boundary, "caption", caption);
            writeMultipartText(output, boundary, "parse_mode", "HTML");
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                    + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(fileData);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }
    }

    private static void writeMultipartText(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String value
    ) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private boolean isPollActive(long generation, String tokenSnapshot) {
        return isEnabled()
                && isConfigured()
                && generation == pollGeneration
                && tokenSnapshot.equals(botToken);
    }

    private void sleepPollingBackoff() {
        long now = System.currentTimeMillis();
        long delay = Math.max(TELEGRAM_BACKOFF_MIN_MS, telegramNetworkRetryAt - now);
        delay = Math.min(TELEGRAM_BACKOFF_MAX_MS, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpClient client() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    HttpClient.Builder builder = HttpClient.newBuilder()
                            .executor(HTTP_EXECUTOR)
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofMillis(TELEGRAM_CONNECT_TIMEOUT_MS));
                    TelegramProxy proxy = telegramProxy();
                    if (proxy != null) {
                        builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
                        httpClientRoute = "proxy " + proxy.host() + ':' + proxy.port();
                    } else {
                        ProxySelector systemProxy = ProxySelector.getDefault();
                        if (systemProxy != null) {
                            builder.proxy(systemProxy);
                            httpClientRoute = "system proxy/direct";
                        } else {
                            httpClientRoute = "direct";
                        }
                    }
                    local = builder.build();
                    httpClient = local;
                    trace("Telegram HTTP client initialized: HTTP/1.1, route=" + httpClientRoute
                            + ", connect timeout=" + TELEGRAM_CONNECT_TIMEOUT_MS + " ms");
                }
            }
        }
        return local;
    }

    private static TelegramProxy telegramProxy() {
        String propertyHost = firstNonBlank(
                System.getProperty("https.proxyHost"),
                System.getProperty("http.proxyHost")
        );
        if (propertyHost != null) {
            String propertyPort = firstNonBlank(
                    System.getProperty("https.proxyPort"),
                    System.getProperty("http.proxyPort")
            );
            int port = parseProxyPort(propertyPort, 8080);
            return new TelegramProxy(propertyHost, port);
        }

        String rawProxy = firstNonBlank(
                System.getenv("HTTPS_PROXY"),
                System.getenv("https_proxy"),
                System.getenv("HTTP_PROXY"),
                System.getenv("http_proxy"),
                System.getenv("ALL_PROXY"),
                System.getenv("all_proxy")
        );
        if (rawProxy == null) {
            return null;
        }
        try {
            String value = rawProxy.contains("://") ? rawProxy : "http://" + rawProxy;
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 8080;
            return new TelegramProxy(host, port);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private synchronized boolean beginTelegramAsyncRequest(String operation) {
        if (!canAttemptTelegramRequest(operation)) {
            return false;
        }
        if (telegramAsyncRequestsInFlight >= TELEGRAM_MAX_ASYNC_REQUESTS) {
            long now = System.currentTimeMillis();
            if (now - telegramQueueDiagnosticAt >= TELEGRAM_DIAGNOSTIC_INTERVAL_MS) {
                telegramQueueDiagnosticAt = now;
                trace(operation + " skipped: " + telegramAsyncRequestsInFlight
                        + " Telegram requests are already in progress");
            }
            return false;
        }
        telegramAsyncRequestsInFlight++;
        return true;
    }

    private synchronized void endTelegramAsyncRequest() {
        if (telegramAsyncRequestsInFlight > 0) {
            telegramAsyncRequestsInFlight--;
        }
    }

    private boolean canAttemptTelegramRequest(String operation) {
        long now = System.currentTimeMillis();
        if (now >= telegramNetworkRetryAt) {
            return true;
        }
        if (now - telegramLastDiagnosticAt >= TELEGRAM_DIAGNOSTIC_INTERVAL_MS) {
            telegramLastDiagnosticAt = now;
            trace(operation + " delayed by Telegram network cooldown for "
                    + Math.max(0L, telegramNetworkRetryAt - now) + " ms");
        }
        return false;
    }

    private void recordTelegramNetworkFailure(String operation, Throwable throwable) {
        Throwable cause = rootCause(throwable);
        long now = System.currentTimeMillis();
        int streak;
        long delay;
        synchronized (this) {
            telegramNetworkFailureStreak = Math.min(16, telegramNetworkFailureStreak + 1);
            streak = telegramNetworkFailureStreak;
            int shift = Math.min(5, Math.max(0, streak - 1));
            long baseDelay = Math.min(
                    TELEGRAM_BACKOFF_MAX_MS,
                    TELEGRAM_BACKOFF_MIN_MS << shift
            );
            delay = Math.min(
                    TELEGRAM_BACKOFF_MAX_MS,
                    baseDelay + ThreadLocalRandom.current().nextLong(250L, 1_001L)
            );
            telegramNetworkRetryAt = Math.max(telegramNetworkRetryAt, now + delay);
            if (streak == 1 || streak % 3 == 0) {
                httpClient = null;
            }
        }

        if (now - telegramLastDiagnosticAt >= TELEGRAM_DIAGNOSTIC_INTERVAL_MS
                || telegramLastDiagnosticAt <= 0L) {
            telegramLastDiagnosticAt = now;
            String diagnostic = operation + " network failure via " + httpClientRoute + ": "
                    + cause.getClass().getSimpleName() + ": " + safe(cause.getMessage())
                    + "; retry in " + delay + " ms. "
                    + "api.telegram.org is unreachable; check VPN/firewall or configure HTTPS_PROXY.";
            trace(diagnostic);
            LOGGER.warn("[TG] {}", diagnostic);
        }
    }

    private synchronized void recordTelegramApiResult(boolean success) {
        if (success) {
            telegramNetworkFailureStreak = 0;
            telegramNetworkRetryAt = 0L;
            return;
        }
        telegramNetworkRetryAt = Math.max(
                telegramNetworkRetryAt,
                System.currentTimeMillis() + 15_000L
        );
    }

    private synchronized void resetTelegramNetworkState() {
        telegramNetworkFailureStreak = 0;
        telegramNetworkRetryAt = 0L;
        telegramLastDiagnosticAt = 0L;
        telegramQueueDiagnosticAt = 0L;
    }

    private static boolean looksLikeBotToken(String value) {
        if (value == null || value.isBlank() || value.indexOf(' ') >= 0) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator < 6 || separator == value.length() - 1) {
            return false;
        }
        for (int i = 0; i < separator; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        for (int i = separator + 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-') {
                return false;
            }
        }
        return true;
    }

    private static String formatTestSendError(int statusCode, String description) {
        String normalized = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (statusCode == 401) {
            return "Telegram: токен отклонён (HTTP 401). Скопируйте новый токен из BotFather.";
        }
        if (statusCode == 400 && normalized.contains("chat not found")) {
            return "Telegram: чат не найден. Проверьте Chat ID и сначала нажмите Start у бота.";
        }
        if (statusCode == 403) {
            return "Telegram: бот не может написать в этот чат. Откройте бота, нажмите Start и разблокируйте его.";
        }
        if (statusCode == 429) {
            return "Telegram: превышен лимит запросов. Подождите и повторите тест.";
        }
        return "Telegram: отправка отклонена, HTTP " + statusCode + ": " + safe(description);
    }

    private void showLocalTelegramStatus(String message) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            LOGGER.info("[TG] {}", message);
            return;
        }
        FluxVisualsClient.MULTI_BOT_MANAGER.executeTelegramAction(minecraft, () -> {
            if (minecraft.player != null) {
                minecraft.player.sendMessage(Text.literal(message), false);
            } else {
                LOGGER.info("[TG] {}", message);
            }
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable == null ? new IOException("unknown Telegram network error") : throwable;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static int parseProxyPort(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65_535 ? port : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static HttpRequest jsonRequest(String token, String method, String body, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + token + '/' + method))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private void handleApiResponse(String method, HttpResponse<String> response) {
        String responseBody = response.body();
        if (response.statusCode() == 200 && isTelegramOk(responseBody)) {
            recordTelegramApiResult(true);
            trace(method + " HTTP 200 ok=true");
            return;
        }
        recordTelegramApiResult(false);
        String error = extractDescription(responseBody);
        LOGGER.warn("[TG] {} error: {} (HTTP {})", method, error, response.statusCode());
        trace(method + " error: " + error + " (HTTP " + response.statusCode() + ')');
    }

    private static String buildPurchaseMessage(String itemName, long price, String seller, Long balance) {
        String name = itemName == null || itemName.isBlank() ? "Незеритовый меч" : itemName;
        String sellerText = seller == null || seller.isBlank() ? "неизвестен" : seller;
        String priceText = price >= 0 ? formatPrice(price) + "$" : "неизвестна";
        String balanceText = balance == null ? "не удалось определить" : formatPrice(balance) + "$";
        return "🎉⚔️ ПОКУПКА УСПЕШНА! ⚔️🎉\n\n"
                + "🗡️ Название: " + name + "\n"
                + "💰 Цена: " + priceText + "\n"
                + "👤 Продавец: " + sellerText + "\n"
                + "💳 Текущий баланс: " + balanceText + "\n"
                + "🕒 Время: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT) + "\n\n"
                + "✅ Меч подтверждён и уже в инвентаре!";
    }

    private static boolean isTechnicalTooltipLine(String line) {
        String normalized = normalizeMessage(line);
        return normalized.contains("нажмите чтобы купить")
                || normalized.contains("нажмите, чтобы купить")
                || normalized.contains("minecraft:netherite_sword")
                || normalized.contains("компонент")
                || normalized.contains("components");
    }

    private static String buildAutoBuyStats(AutoBuy.SessionStats stats, Long verifiedBalance) {
        if (stats.runningTimeMs() <= 0L) {
            return "📊 СТАТИСТИКА AUTOBUY\n\n"
                    + "⏹️ Сессия ещё не запускалась.\n"
                    + "💵 Текущий баланс (/bal): " + formatVerifiedBalance(verifiedBalance) + "\n"
                    + "🕒 Время отчёта: " + LocalDateTime.now().format(DISPLAY_TIME_FORMAT);
        }

        int unknownPricePurchases = Math.max(0, stats.purchaseCount() - stats.knownPricePurchaseCount());
        long cashDifference = saturatedSubtract(stats.saleRevenue(), stats.purchaseSpent());
        StringBuilder report = new StringBuilder("📊 СТАТИСТИКА AUTOBUY\n\n")
                .append(stats.active() ? "▶️ Статус: работает\n" : "⏹️ Статус: остановлен\n")
                .append("⚙️ Этап: ").append(formatAutoBuyStep(stats.step())).append('\n')
                .append("⏱️ Время работы: ").append(formatSessionDuration(stats.runningTimeMs())).append("\n\n")
                .append("🛒 Покупок: ").append(stats.purchaseCount()).append('\n')
                .append("💸 Потрачено: ").append(formatPrice(stats.purchaseSpent())).append("$\n")
                .append("📉 Средняя цена покупки: ")
                .append(stats.averagePurchasePrice() >= 0L
                        ? formatPrice(stats.averagePurchasePrice()) + "$"
                        : "нет данных")
                .append('\n');
        if (unknownPricePurchases > 0) {
            report.append("⚠️ Покупок без распознанной цены: ").append(unknownPricePurchases).append('\n');
        }

        report.append("💰 Продаж: ").append(stats.saleCount()).append('\n')
                .append("💵 Сумма продаж: ").append(formatPrice(stats.saleRevenue())).append("$\n")
                .append("📈 Выручка (продажи - покупки): ")
                .append(cashDifference > 0L ? "+" : "")
                .append(formatPrice(cashDifference)).append("$\n")
                .append("♻️ Перевыставлений: ").append(stats.resellCount()).append("\n\n")
                .append("🔄 Переходов страниц: ").append(stats.pageTransitionCount()).append('\n')
                .append("📡 Средний ответ страницы: ")
                .append(stats.averagePageLatencyMs() >= 0L
                        ? stats.averagePageLatencyMs() + " мс"
                        : "нет данных")
                .append('\n')
                .append("🧠 Оценка задержки сервера: ").append(stats.estimatedPageLatencyMs()).append(" мс\n")
                .append("🎲 Последний случайный таймаут: ")
                .append(stats.lastPageConfirmTimeoutMs() > 0L
                        ? stats.lastPageConfirmTimeoutMs() + " мс"
                        : "не назначен")
                .append('\n')
                .append("⚠️ Ошибок страниц: ").append(stats.pageErrorCount()).append('\n')
                .append("🔁 Переоткрытий поиска: ").append(stats.searchReopenCount()).append('\n');

        report.append("\n💳 Баланс в начале: ").append(formatMoney(stats.startBalance())).append('\n')
                .append("💵 Текущий баланс (/bal): ").append(formatVerifiedBalance(verifiedBalance)).append('\n');
        report.append("🕒 Время отчёта: ").append(LocalDateTime.now().format(DISPLAY_TIME_FORMAT));
        return report.toString();
    }

    private static String formatVerifiedBalance(Long balance) {
        return balance == null ? "не удалось получить" : formatPrice(balance) + "$";
    }

    private static String formatAutoBuyStep(String step) {
        if (step == null) {
            return "неизвестно";
        }
        return switch (step) {
            case "WAIT_CLICK_GUI_CLOSE" -> "ожидание закрытия GUI";
            case "SEARCH_DELAY" -> "подготовка поиска";
            case "WAIT_AUCTION" -> "страницы аукциона";
            case "WAIT_CONFIRM" -> "подтверждение покупки";
            case "WAIT_PURCHASE" -> "проверка покупки";
            case "WAIT_MONEY" -> "проверка баланса";
            case "LOW_BALANCE_WAIT" -> "ожидание денег";
            case "WAIT_EXTERNAL" -> "внешнее действие";
            case "NAME_PREPARE", "NAME_SEND", "NAME_RESTORE" -> "переименование меча";
            case "ANARCHY_SEND", "ANARCHY_WAIT" -> "смена анархии";
            case "RESELL_SEND_AH", "RESELL_STORAGE", "RESELL_ACTION", "RESELL_FINISH" -> "перевыставление";
            case "IDLE" -> "ожидание";
            default -> step;
        };
    }

    private static String formatSessionDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d ч %02d мин %02d сек", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%d мин %02d сек", minutes, seconds);
        }
        return seconds + " сек";
    }

    private String buildLeaveReport(StorageSnapshot snapshot) {
        String startText = leaveStartBalance >= 0L ? formatPrice(leaveStartBalance) + "$" : "не удалось определить";
        String endText = leaveEndBalance >= 0L ? formatPrice(leaveEndBalance) + "$" : "не удалось определить";
        StringBuilder report = new StringBuilder("📊 ИТОГОВЫЙ ОТЧЁТ AUTOBUY\n\n")
                .append("💰 Баланс в начале: ").append(startText).append('\n')
                .append("💳 Баланс в конце: ").append(endText).append('\n');

        if (snapshot.available()) {
            int totalSwordCount = snapshot.storageSwordCount() + snapshot.inventorySwordCount();
            report.append("📦 Мечей в хранилище: ").append(snapshot.storageSwordCount()).append('\n')
                    .append("🎒 Мечей в инвентаре: ").append(snapshot.inventorySwordCount()).append('\n')
                    .append("⚔️ Всего учтено мечей: ").append(totalSwordCount).append('\n')
                    .append("🏷️ Стоимость распознанных мечей: ")
                    .append(formatPrice(snapshot.totalSwordValue())).append("$\n");
            if (snapshot.unpricedSwordCount() > 0) {
                report.append("⚠️ Без распознанной цены: ")
                        .append(snapshot.unpricedSwordCount())
                        .append(" шт. Их стоимость не включена.\n");
            }
        } else {
            report.append("📦 Хранилище: не удалось открыть\n")
                    .append("🏷️ Стоимость мечей: не удалось определить\n");
        }

        if (leaveStartBalance >= 0L && leaveEndBalance >= 0L) {
            long cashProfit = saturatedSubtract(leaveEndBalance, leaveStartBalance);
            report.append("\n📈 Окуп без учёта мечей: ")
                    .append(formatProfit(cashProfit, leaveStartBalance))
                    .append('\n');
            if (snapshot.available()) {
                long assetsAtEnd = saturatedAdd(leaveEndBalance, snapshot.totalSwordValue());
                long totalProfit = saturatedSubtract(assetsAtEnd, leaveStartBalance);
                report.append("💎 Окуп с учётом мечей: ")
                        .append(formatProfit(totalProfit, leaveStartBalance))
                        .append('\n');
            } else {
                report.append("💎 Окуп с учётом мечей: недоступен\n");
            }
        } else {
            report.append("\n📈 Окуп: не удалось рассчитать без начального и конечного баланса\n");
        }
        report.append("🕒 Время: ").append(LocalDateTime.now().format(DISPLAY_TIME_FORMAT));
        return report.toString();
    }

    private static List<String> tooltipLines(MinecraftClient minecraft, ItemStack stack) {
        if (minecraft == null || minecraft.world == null || stack == null || stack.isEmpty()) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>();
            Item.TooltipContext context = Item.TooltipContext.create(minecraft.world);
            for (Text text : stack.getTooltip(context, minecraft.player, TooltipType.BASIC)) {
                String line = text == null ? "" : text.getString();
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String formatProfit(long profit, long startBalance) {
        String money = (profit > 0L ? "+" : "") + formatPrice(profit) + "$";
        if (startBalance <= 0L) {
            return money + " (процент недоступен)";
        }
        double percent = profit * 100.0D / startBalance;
        return money + " (" + String.format(Locale.ROOT, "%+.2f%%", percent) + ')';
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return second >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long saturatedSubtract(long first, long second) {
        try {
            return Math.subtractExact(first, second);
        } catch (ArithmeticException ignored) {
            return first >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long saturatedMultiply(long value, int multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException ignored) {
            return value >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static String formatMoney(long value) {
        return value >= 0L ? formatPrice(value) + "$" : "неизвестен";
    }

    private static Long extractSwordSalePrice(String message) {
        String normalized = normalizeMessage(message);
        if (!normalized.contains("у вас купили")
                || !normalized.contains("незерит")
                || !normalized.contains("меч")) {
            return null;
        }
        Matcher matcher = SALE_PRICE_PATTERN.matcher(message);
        Long result = null;
        while (matcher.find()) {
            result = parseAmount(matcher.group(1));
        }
        return result;
    }

    private static Integer extractPing(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = PING_RESPONSE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isCheckCallMessage(String message) {
        String normalized = normalizeMessage(message);
        return normalized.contains("вы были вызваны")
                || normalized.contains("вас вызвали на проверку");
    }

    private static boolean isBanDisconnectReason(String message) {
        String normalized = normalizeMessage(message);
        return normalized.contains("вы забанены")
                || normalized.contains("вы были забанены")
                || normalized.contains("ваш аккаунт заблокирован")
                || normalized.contains("доступ заблокирован")
                || normalized.contains("you are banned")
                || normalized.contains("you were banned")
                || normalized.contains("banned by")
                || normalized.startsWith("banned");
    }

    private static boolean isAuctionStorageFullMessage(String message) {
        String normalized = normalizeMessage(message);
        boolean auctionContext = normalized.contains("хранилищ")
                || normalized.contains("аукцион")
                || normalized.contains("лот")
                || normalized.contains("товар")
                || normalized.contains("предмет");
        boolean full = normalized.contains("заполн")
                || normalized.contains("переполн")
                || normalized.contains("полно")
                || normalized.contains("нет свободн")
                || normalized.contains("достигнут лимит")
                || normalized.contains("достигли лимит")
                || normalized.contains("слишком много")
                || normalized.contains("нельзя выставить больше")
                || normalized.contains("не можете выставить больше")
                || normalized.contains("освободите хранилище")
                || normalized.contains("арендуйте больше слотов")
                || normalized.contains("ah rent")
                || normalized.contains("освободите место")
                || normalized.contains("нет места")
                || normalized.contains("лимит активных")
                || normalized.contains("лимит выставленных")
                || normalized.contains("максимум лотов")
                || normalized.contains("максимум товаров")
                || normalized.contains("места законч")
                || normalized.contains("все слоты занят")
                || normalized.contains("максимальн")
                && (normalized.contains("количеств")
                || normalized.contains("лимит")
                || normalized.contains("слот"));
        return auctionContext && full;
    }

    private static boolean isOwnAuctionSaleMessage(String message) {
        String normalized = normalizeMessage(message);
        boolean explicitOwnSale = normalized.contains("у вас купили")
                || normalized.contains("купили у вас")
                || normalized.contains("ваш лот купили")
                || normalized.contains("ваш товар купили")
                || normalized.contains("ваш предмет купили")
                || normalized.contains("ваш лот был куплен")
                || normalized.contains("ваш товар был куплен")
                || normalized.contains("ваш предмет был куплен")
                || normalized.contains("ваш лот продан")
                || normalized.contains("ваш товар продан")
                || normalized.contains("ваш предмет продан");
        boolean ownContext = normalized.contains("ваш")
                || normalized.contains("у вас")
                || normalized.contains("у тебя")
                || normalized.contains("your");
        boolean auctionContext = normalized.contains("лот")
                || normalized.contains("товар")
                || normalized.contains("предмет")
                || normalized.contains("аукцион")
                || normalized.contains("меч")
                || normalized.contains("auction")
                || normalized.contains("item");
        boolean soldContext = normalized.contains("продан")
                || normalized.contains("купил")
                || normalized.contains("sold")
                || normalized.contains("bought");
        return explicitOwnSale || ownContext && auctionContext && soldContext;
    }

    private static boolean isAuctionListingFailureMessage(String message) {
        if (isAuctionStorageFullMessage(message)) {
            return false;
        }
        String normalized = normalizeMessage(message);
        boolean listingContext = normalized.contains("выстав")
                || normalized.contains("продаж")
                || normalized.contains("аукцион");
        boolean failure = normalized.contains("не удалось")
                || normalized.contains("не получилось")
                || normalized.contains("не можете")
                || normalized.contains("нельзя")
                || normalized.contains("ошибк")
                || normalized.contains("отклон");
        return listingContext && failure;
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static Long parseAmount(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parsePurchasePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("$", "")
                .replace(',', '.');
        Matcher matcher = PURCHASE_PRICE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        long multiplier = switch (matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "k", "к" -> 1_000L;
            case "kk", "кк", "m", "м", "млн" -> 1_000_000L;
            case "kkk", "ккк", "b" -> 1_000_000_000L;
            default -> 1L;
        };
        try {
            BigDecimal result = new BigDecimal(matcher.group(1))
                    .multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.DOWN);
            if (result.signum() <= 0 || result.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return null;
            }
            return result.longValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private void trace(String message) {
        if (!logsEnabled) {
            return;
        }
        writeLog("[" + LOG_TIME_FORMAT.format(LocalDateTime.now()) + "] " + message);
    }

    private void autoSellTrace(String message) {
        if (!logsEnabled) {
            return;
        }
        writeAutoBuyLog("[" + LOG_TIME_FORMAT.format(LocalDateTime.now()) + "] " + message);
    }

    private void ensureLogFile() {
        Path path = logFile();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(path)) {
                Files.writeString(
                        path,
                        "[" + LOG_TIME_FORMAT.format(LocalDateTime.now()) + "] TG log initialized"
                                + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException ex) {
            LOGGER.warn("[TG] Failed to create TG.log: {}", ex.toString());
        }
    }

    private void writeLog(String line) {
        Path path = logFile();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            LOGGER.warn("[TG] Failed to write TG.log: {}", ex.toString());
        }
    }

    private static Path logFile() {
        return Path.of(System.getProperty("user.home"), "Desktop", "TG.log");
    }

    private static Path autoBuyLogFile() {
        return Path.of(System.getProperty("user.home"), "Desktop", "AutoBuy.log");
    }

    private static void resetLogFilesForRun() {
        try {
            Files.deleteIfExists(logFile());
            Files.deleteIfExists(autoBuyLogFile());
        } catch (IOException ex) {
            LOGGER.warn("[TG] Failed to reset run logs: {}", ex.toString());
        }
    }

    private static void writeAutoBuyLog(String line) {
        try {
            Path path = autoBuyLogFile();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            LOGGER.warn("[TG] Failed to write AutoBuy.log: {}", ex.toString());
        }
    }

    private static boolean isTelegramOk(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return root.has("ok") && root.get("ok").getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String description(JsonObject root) {
        return root.has("description") ? root.get("description").getAsString() : "unknown error";
    }

    private static String formatPrice(long price) {
        String digits = Long.toString(price);
        StringBuilder result = new StringBuilder(digits.length() + digits.length() / 3);
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            if (count == 3) {
                result.append(' ');
                count = 0;
            }
            result.append(digits.charAt(i));
            count++;
        }
        return result.reverse().toString();
    }

    private static Boolean parseOnOff(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "yes", "да", "вкл", "включить", "1" -> true;
            case "off", "no", "нет", "выкл", "выключить", "0" -> false;
            default -> null;
        };
    }

    private static Boolean parseConfirmation(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "start", "yes", "да", "запуск", "запустить" -> true;
            case "cancel", "no", "нет", "отмена", "отменить" -> false;
            default -> null;
        };
    }

    private static boolean parseSessionLimits(String value, AutomationWizard wizard) {
        if (value == null || value.isBlank()
                || value.equalsIgnoreCase("skip")
                || value.equalsIgnoreCase("пропустить")
                || value.equalsIgnoreCase("максимум")
                || value.equalsIgnoreCase("max")) {
            wizard.budgetSpec = "максимум";
            wizard.maxPurchases = 0;
            wizard.targetProfit = 0L;
            return true;
        }
        String compact = value.toLowerCase(Locale.ROOT).replace(',', '.');
        Matcher budget = Pattern.compile("(?:бюджет|budget)\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]+)?(?:kk|кк|m|м)?%?)").matcher(compact);
        Matcher purchases = Pattern.compile("(?:покупки|мечей|мечи|buy)\\s*[:=]?\\s*(\\d+)").matcher(compact);
        Matcher profit = Pattern.compile("(?:заработать|доход|прибыль|profit)\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]+)?(?:kk|кк|m|м)?)").matcher(compact);
        Matcher resell = Pattern.compile("(?:реселл|автореселл|resell)\\s*[:=]?\\s*(on|off|да|нет|вкл|выкл)").matcher(compact);
        Matcher anarchySwitch = Pattern.compile("(?:переключение|смена|анархии)\\s*[:=]?\\s*(on|off|да|нет|вкл|выкл)").matcher(compact);
        Matcher rename = Pattern.compile("(?:rename|переименовать)\\s*[:=]?\\s*([^,;]+)").matcher(compact);
        boolean found = false;
        if (budget.find()) {
            wizard.budgetSpec = budget.group(1);
            found = true;
        }
        if (purchases.find()) {
            wizard.maxPurchases = Integer.parseInt(purchases.group(1));
            if (wizard.maxPurchases < 1 || wizard.maxPurchases > 100_000) {
                return false;
            }
            found = true;
        }
        if (profit.find()) {
            Long parsed = parsePurchasePrice(profit.group(1));
            if (parsed == null || parsed < 0L) {
                return false;
            }
            wizard.targetProfit = parsed;
            found = true;
        }
        if (resell.find()) {
            wizard.autoResellEnabled = parseOnOff(resell.group(1));
            found = true;
        }
        if (anarchySwitch.find()) {
            wizard.anarchySwitchEnabled = parseOnOff(anarchySwitch.group(1));
            found = true;
        }
        if (rename.find()) {
            wizard.renameText = rename.group(1).trim();
            found = !wizard.renameText.isEmpty();
        }
        return found;
    }

    private static boolean isWizardSkip(String value) {
        return value == null || value.isBlank()
                || value.equalsIgnoreCase("skip")
                || value.equalsIgnoreCase("пропустить");
    }

    private static boolean isValidBudgetSpec(String value) {
        if (value == null || value.isBlank()) return false;
        String compact = value.trim().replace(',', '.');
        if (compact.endsWith("%")) {
            try {
                double percent = Double.parseDouble(compact.substring(0, compact.length() - 1));
                return percent > 0 && percent <= 100;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return parsePurchasePrice(compact) != null && parsePurchasePrice(compact) > 0;
    }

    private static Long parseAutomationDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.toLowerCase(Locale.ROOT)
                .replace('\u00a0', ' ')
                .replace('\uff1a', ':')
                .replaceAll("\\s+", "");
        String clockValue = compact.startsWith("до") ? compact.substring(2) : compact;
        Matcher clock = Pattern.compile("^(\\d{1,2})[:.](\\d{2})$").matcher(clockValue);
        if (clock.matches()) {
            try {
                int hour = Integer.parseInt(clock.group(1));
                int minute = Integer.parseInt(clock.group(2));
                if (hour > 23 || minute > 59) {
                    return null;
                }
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime end = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                if (!end.isAfter(now)) {
                    end = end.plusDays(1);
                }
                long duration = java.time.Duration.between(now, end).toMillis();
                return duration < AUTOMATION_MIN_DURATION_MS || duration > AUTOMATION_MAX_DURATION_MS
                        ? null : duration;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher matcher = AUTOMATION_DURATION_PART_PATTERN.matcher(compact);
        long total = 0L;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return null;
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (amount <= 0L) {
                return null;
            }
            long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s", "с" -> 1_000L;
                case "m", "м" -> 60_000L;
                case "h", "ч" -> 60L * 60_000L;
                case "d", "д" -> 24L * 60L * 60_000L;
                default -> 0L;
            };
            if (multiplier <= 0L || amount > AUTOMATION_MAX_DURATION_MS / multiplier) {
                return null;
            }
            total += amount * multiplier;
            if (total > AUTOMATION_MAX_DURATION_MS) {
                return null;
            }
            consumed = matcher.end();
        }
        if (consumed != compact.length() || total < AUTOMATION_MIN_DURATION_MS) {
            return null;
        }
        return total;
    }

    private static long[] parseAutomationRange(String value, long minimum, long maximum) {
        if (value == null) {
            return null;
        }
        Matcher matcher = AUTOMATION_RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            long min = Long.parseLong(matcher.group(1));
            long max = matcher.group(2) == null ? min : Long.parseLong(matcher.group(2));
            if (min < minimum || max < min || max > maximum) {
                return null;
            }
            return new long[]{min, max};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> parseAutomationAnarchyIds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("current") || normalized.equals("текущие") || normalized.equals("текущий")) {
            List<String> current = telegramAutoBuy().getAnarchyIds();
            return current.isEmpty() ? null : current;
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split("[,;\\s]+")) {
            String id = part.replaceAll("[^0-9]", "");
            if (id.isEmpty() || result.contains(id)) {
                continue;
            }
            result.add(id);
            if (result.size() >= 32) {
                break;
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private static String buildAutomationSummary(AutomationWizard wizard) {
        return "📋 ПРОВЕРЬТЕ НАСТРОЙКИ\n\n"
                + "Режим: " + wizard.kind.label() + "\n"
                + "⏱️ Время: " + formatDuration(wizard.durationMs) + "\n"
                + "🏷️ Покупка до: " + formatPrice(wizard.buyPrice) + "$\n"
                + "💳 Бюджет: " + wizard.budgetSpec + "\n"
                + "🛒 Максимум покупок: " + (wizard.maxPurchases > 0 ? wizard.maxPurchases : "максимум") + "\n"
                + "📈 Цель заработка: " + (wizard.targetProfit > 0 ? formatPrice(wizard.targetProfit) + "$" : "не задана") + "\n"
                + "♻️ Авто-реселл: " + (wizard.autoResellEnabled ? "включён" : "выключен") + "\n"
                + "🌐 Переключение анархий: " + (wizard.anarchySwitchEnabled ? "включено" : "выключено") + "\n"
                + "✏️ Переименование: " + (wizard.renameText.isBlank() ? "пропущено" : wizard.renameText) + "\n"
                + "💰 AutoSell: " + (wizard.autoSellEnabled ? wizard.sellPrice : "выключен") + "\n"
                + "🌐 Анархии: " + String.join(", ", wizard.anarchyIds) + "\n"
                + "🔄 Смена анархии: " + formatSecondsRange(
                        wizard.anarchyDelayMinSeconds,
                        wizard.anarchyDelayMaxSeconds
                ) + "\n"
                + "♻️ Перевыставление: " + formatSecondsRange(
                        wizard.resellMinSeconds,
                        wizard.resellMaxSeconds
                ) + "\n"
                + "🤖 Self: " + (wizard.selfMode ? "on" : "off") + "\n"
                + "🚪 Хаб-сейф: всегда on";
    }

    private static String buildAutomationReadinessReport(
            AutomationKind requestedKind,
            MinecraftClient minecraft,
            AutoBuy autoBuy,
            ItemResorter itemResorter
    ) {
        boolean connected = minecraft != null
                && minecraft.player != null
                && minecraft.getNetworkHandler() != null;
        boolean filtersConfigured = !itemResorter.getEnchantNeeded().isEmpty()
                || !itemResorter.getBuffNeeded().isEmpty();
        StringBuilder report = new StringBuilder("🔎 ПРОВЕРКА ")
                .append(requestedKind.label())
                .append("\n\n")
                .append(connected ? "✅ Minecraft подключён\n" : "❌ Minecraft не подключён\n")
                .append(filtersConfigured
                        ? "✅ Фильтры покупки настроены\n"
                        : "❌ Не настроены обязательные фильтры чар/бафов\n")
                .append("⚙️ AutoBuy: ")
                .append(autoBuy.isEnabled() ? "включён" : "выключен")
                .append('\n')
                .append("🌐 Текущий список анархий: ")
                .append(autoBuy.getAnarchyIds().isEmpty() ? "пуст" : "настроен")
                        .append('\n')
                .append("🏷️ Текущий лимит покупки: ")
                .append(formatPrice(itemResorter.getMaxPrice())).append("$\n\n")
                .append("Мастер ").append(requestedKind.command())
                .append(" отдельно спросит про AutoSell, анархии и таймеры.\n")
                .append("Хаб-сейф работает независимо от Self.");
        return report.toString();
    }

    private String buildAutomationStatusReport(
            AutomationKind requestedKind,
            AutomationSession session,
            MinecraftClient minecraft,
            AutoBuy autoBuy,
            ItemResorter itemResorter
    ) {
        long now = System.currentTimeMillis();
        AutoBuy.SessionStats stats = autoBuy.getSessionStats();
        boolean connected = minecraft != null
                && minecraft.player != null
                && minecraft.getNetworkHandler() != null;
        boolean filtersConfigured = !itemResorter.getEnchantNeeded().isEmpty()
                || !itemResorter.getBuffNeeded().isEmpty();
        boolean priceMatches = itemResorter.isPriceFilterEnabled()
                && itemResorter.getMaxPrice() == session.buyPrice();
        boolean selfMatches = (!autoBuy.isLowBalanceGuardEnabled()) == session.selfMode();
        boolean autoSellMatches = autoSellEnabled == session.autoSellEnabled();
        boolean storageKnownFull;
        boolean autoSellQueued;
        synchronized (sellLock) {
            storageKnownFull = autoSellStorageKnownFull;
            autoSellQueued = autoSellAfterPurchaseQueued;
        }

        StringBuilder report = new StringBuilder("📡 СТАТУС АВТОНОМНОГО РЕЖИМА\n\n")
                .append("Запрошено: ").append(requestedKind.label()).append('\n')
                .append("Активен: ").append(session.kind().label()).append('\n')
                .append("⏱️ Осталось: ")
                .append(formatSessionDuration(Math.max(0L, session.endsAt() - now))).append('\n')
                .append("🤖 Self: ").append(session.selfMode() ? "on" : "off").append('\n')
                .append("⚙️ Этап: ").append(formatAutoBuyStep(stats.step())).append('\n')
                .append("▶️ AutoBuy: ").append(autoBuy.isEnabled() ? "работает" : "выключен").append('\n')
                .append("🔌 Соединение: ").append(connected ? "активно" : "потеряно").append('\n')
                .append("🛒 Покупок: ").append(stats.purchaseCount())
                .append(" на ").append(formatPrice(stats.purchaseSpent())).append("$\n")
                .append("💰 Продаж: ").append(stats.saleCount())
                .append(" на ").append(formatPrice(stats.saleRevenue())).append("$\n")
                .append("♻️ Перевыставлений: ").append(stats.resellCount()).append('\n')
                .append("💳 Последний баланс: ").append(formatMoney(stats.currentBalance())).append('\n')
                .append("💸 Автопродажа: ")
                .append(autoSellEnabled ? "включена" : "выключена")
                .append(autoSellQueued ? ", ожидает мечи" : "")
                .append(isInventorySellWorking() ? ", выполняется" : "")
                .append(storageKnownFull ? ", хранилище заполнено" : "")
                .append('\n')
                .append("🕒 Следующая смена анархии: ")
                .append(formatRemainingUntil(autoBuy.getNextAnarchyAt(), now)).append('\n')
                .append("♻️ Следующее перевыставление: ")
                .append(formatRemainingUntil(autoBuy.getNextResellAt(), now)).append('\n');

        List<String> problems = new ArrayList<>();
        if (!connected) {
            problems.add("соединение с сервером потеряно");
        }
        if (!autoBuy.isEnabled()) {
            problems.add("AutoBuy выключен");
        }
        if (!filtersConfigured) {
            problems.add("фильтры покупки пустые, мечи не будут найдены");
        }
        if (!priceMatches) {
            problems.add("лимит покупки отличается от настроек сессии");
        }
        if (!selfMatches) {
            problems.add("настройка Self не совпадает с сессией");
        }
        if (!autoSellMatches) {
            problems.add("настройка AutoSell не совпадает с сессией");
        }
        if (autoBuy.isHubSafetyStorageCheckPending()) {
            problems.add("ожидается проверка хаб-сейфа");
        }
        if (problems.isEmpty()) {
            report.append("✅ Критических проблем по состоянию модулей не обнаружено.");
        } else {
            report.append("\n⚠️ Что проверить:\n");
            for (String problem : problems) {
                report.append("• ").append(problem).append('\n');
            }
        }
        return report.toString();
    }

    private static String formatRemainingUntil(long timestamp, long now) {
        if (timestamp <= 0L) {
            return "не запланирована";
        }
        if (timestamp <= now) {
            return "сейчас";
        }
        return "через " + formatSessionDuration(timestamp - now);
    }

    private static String buildAutomationCompletionReport(
            AutomationSession session,
            AutoBuy.SessionStats stats,
            boolean expired,
            boolean goHub,
            boolean endedByHubSafety
    ) {
        int unknownPricePurchases = Math.max(0, stats.purchaseCount() - stats.knownPricePurchaseCount());
        long cashDifference = saturatedSubtract(stats.saleRevenue(), stats.purchaseSpent());
        StringBuilder report = new StringBuilder("📊 ИТОГОВЫЙ ОТЧЁТ AUTOBUY\n\n")
                .append("Режим: ").append(session.kind().label()).append('\n')
                .append("Завершение: ")
                .append(endedByHubSafety
                        ? "хаб-сейф: баланс ниже цены и в хранилище не осталось мечей"
                        : expired ? "время сессии закончилось" : "остановлен досрочно")
                .append('\n')
                .append("⏱️ Запланировано: ")
                .append(formatDuration(Math.max(0L, session.endsAt() - session.startedAt())))
                .append('\n')
                .append("⏱️ Фактически: ").append(formatSessionDuration(stats.runningTimeMs())).append('\n')
                .append("🤖 Self: ").append(session.selfMode() ? "on" : "off").append('\n')
                .append("🏷️ Лимит покупки: ").append(formatPrice(session.buyPrice())).append("$\n")
                .append("💳 Бюджет: ").append(session.budgetSpec()).append('\n')
                .append("🛒 Лимит покупок: ").append(session.maxPurchases() > 0 ? session.maxPurchases() : "максимум").append('\n')
                .append("📈 Цель заработка: ").append(session.targetProfit() > 0 ? formatPrice(session.targetProfit()) + "$" : "не задана").append('\n')
                .append("💰 AutoSell: ")
                .append(session.autoSellEnabled() ? session.sellPrice() : "выключен")
                .append("\n\n")
                .append("🛒 Покупок: ").append(stats.purchaseCount()).append('\n')
                .append("💸 Потрачено: ").append(formatPrice(stats.purchaseSpent())).append("$\n")
                .append("📉 Средняя цена покупки: ")
                .append(stats.averagePurchasePrice() >= 0L
                        ? formatPrice(stats.averagePurchasePrice()) + "$"
                        : "нет данных")
                .append('\n');
        if (unknownPricePurchases > 0) {
            report.append("⚠️ Покупок без распознанной цены: ")
                    .append(unknownPricePurchases)
                    .append('\n');
        }
        report.append("💰 Продаж: ").append(stats.saleCount()).append('\n')
                .append("💵 Сумма продаж: ").append(formatPrice(stats.saleRevenue())).append("$\n")
                .append("📈 Продажи - покупки: ")
                .append(cashDifference > 0L ? "+" : "")
                .append(formatPrice(cashDifference)).append("$\n")
                .append("♻️ Перевыставлений: ").append(stats.resellCount()).append('\n')
                .append("💳 Баланс в начале: ").append(formatMoney(stats.startBalance())).append('\n')
                .append("💳 Последний известный баланс: ").append(formatMoney(stats.currentBalance())).append('\n');
        if (goHub || endedByHubSafety) {
            report.append("🚪 Отправлена команда /hub.\n");
        }
        report.append("🕒 Время отчёта: ").append(LocalDateTime.now().format(DISPLAY_TIME_FORMAT));
        return report.toString();
    }

    private static String formatDuration(long durationMs) {
        long totalMinutes = Math.max(0L, durationMs / 60_000L);
        long days = totalMinutes / (24L * 60L);
        long hours = totalMinutes % (24L * 60L) / 60L;
        long minutes = totalMinutes % 60L;
        StringBuilder result = new StringBuilder();
        if (days > 0L) {
            result.append(days).append("д ");
        }
        if (hours > 0L) {
            result.append(hours).append("ч ");
        }
        if (minutes > 0L || result.isEmpty()) {
            result.append(minutes).append("м");
        }
        return result.toString().trim();
    }

    private static String formatSecondsRange(long min, long max) {
        return min == max ? min + " сек" : min + "-" + max + " сек";
    }

    private static String jsonQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        result.append('"');
        return result.toString();
    }

    private static String extractDescription(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return description(root);
        } catch (RuntimeException ignored) {
            return body == null || body.isBlank() ? "empty response" : body.substring(0, Math.min(200, body.length()));
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private enum AutomationKind {
        SCHOOL("school", "🎒 SCHOOL"),
        NIGHT("night", "🌙 NIGHT"),
        DAILY("daily", "☀️ ПОВСЕДНЕВНАЯ");

        private final String command;
        private final String label;

        AutomationKind(String command, String label) {
            this.command = command;
            this.label = label;
        }

        private String command() {
            return command;
        }

        private String label() {
            return label;
        }
    }

    private enum AutomationWizardStep {
        DURATION,
        BUY_PRICE,
        AUCTION_SLOTS,
        LIMITS,
        BUDGET,
        PURCHASE_LIMIT,
        PROFIT_TARGET,
        RENAME,
        AUTO_RESELL,
        ANARCHY_SWITCH,
        AUTO_SELL,
        SELL_PRICE,
        ANARCHIES,
        ANARCHY_DELAY,
        RESELL_INTERVAL,
        SELF_MODE,
        CONFIRM
    }

    private static final class AutomationWizard {
        private final String id;
        private final AutomationKind kind;
        private AutomationWizardStep step = AutomationWizardStep.DURATION;
        private long durationMs;
        private long buyPrice;
        private int auctionSlots;
        private String budgetSpec = "максимум";
        private int maxPurchases;
        private long targetProfit;
        private boolean autoResellEnabled = true;
        private boolean anarchySwitchEnabled = true;
        private String renameText = "";
        private boolean autoSellEnabled = true;
        private String sellPrice = "";
        private List<String> anarchyIds = List.of();
        private long anarchyDelayMinSeconds = 75L;
        private long anarchyDelayMaxSeconds = 80L;
        private long resellMinSeconds = 60L;
        private long resellMaxSeconds = 65L;
        private boolean selfMode = true;

        private AutomationWizard(AutomationKind kind) {
            this(newCallbackId(), kind);
        }

        private AutomationWizard(String id, AutomationKind kind) {
            this.id = id == null || id.isBlank() || id.length() > 16 ? newCallbackId() : id;
            this.kind = kind;
        }
    }

    private record AutomationSnapshot(
            long maxBuyPrice,
            boolean priceFilterEnabled,
            String autoSellPrice,
            boolean autoSellEnabled,
            boolean autoResellEnabled,
            boolean anarchySwitchEnabled,
            List<String> anarchyIds,
            boolean lowBalanceGuardEnabled,
            boolean notificationsEnabled,
            long anarchyDelayMinMs,
            long anarchyDelayMaxMs,
            long resellIntervalMinMs,
            long resellIntervalMaxMs
    ) {
        private AutomationSnapshot {
            anarchyIds = anarchyIds == null ? List.of() : List.copyOf(anarchyIds);
        }
    }

    private record AutomationSession(
            AutomationKind kind,
            long startedAt,
            long endsAt,
            boolean selfMode,
            long buyPrice,
            boolean autoSellEnabled,
            String sellPrice,
            String budgetSpec,
            int maxPurchases,
            long targetProfit,
            AutomationSnapshot snapshot
    ) {
        private AutomationSession {
            sellPrice = sellPrice == null || sellPrice.isBlank() ? "не задана" : sellPrice;
            budgetSpec = budgetSpec == null || budgetSpec.isBlank() ? "максимум" : budgetSpec;
            maxPurchases = Math.max(0, maxPurchases);
            targetProfit = Math.max(0L, targetProfit);
        }
    }

    private record BotUpdate(
            long updateId,
            String chatId,
            String text,
            String callbackQueryId,
            long messageId,
            String sourceMessageText,
            String replyToMessageText
    ) {
    }

    private record InlineButton(String text, String callbackData, String url) {
        private InlineButton(String text, String callbackData) {
            this(text, callbackData, "");
        }
    }

    private enum PendingBotInput {
        NONE,
        AUTOSELL_PRICE,
        CUSTOM_SELL_PRICE,
        BOT_CHAT_MESSAGE,
        BOT_ANARCHY_NUMBER
    }

    private record PendingPurchaseNotification(String itemName, long price, String seller) {
    }

    private record InventorySellRequest(String price, int count) {
    }

    private record StorageSnapshot(
            boolean available,
            int storageSwordCount,
            int inventorySwordCount,
            int pricedSwordCount,
            int unpricedSwordCount,
            long totalSwordValue
    ) {
        private static StorageSnapshot unavailable() {
            return new StorageSnapshot(false, 0, 0, 0, 0, 0L);
        }
    }

    private enum LeaveStage {
        IDLE,
        WAIT_AUTOBUY_PAUSE,
        WAIT_BALANCE,
        OPEN_AUCTION,
        WAIT_AUCTION,
        WAIT_STORAGE_OPEN,
        WAIT_STORAGE,
        CAPTURE_SCREENSHOT,
        WAIT_SCREENSHOT
    }

    private enum SellStage {
        IDLE,
        WAIT_AUTOBUY_PAUSE,
        WAIT_SCREEN_CLOSE,
        SELLGUI_WAIT,
        SELLGUI_FILL,
        SELLGUI_CONFIRM,
        PREPARE_SWORD,
        SEND_SELL_COMMAND,
        WAIT_SERVER_SALE
    }

    private record PollBatch(boolean success, long nextOffset, List<BotUpdate> updates) {
        private static PollBatch failed(long offset) {
            return new PollBatch(false, Math.max(0L, offset), List.of());
        }
    }

    private record TelegramProxy(String host, int port) {
    }
}

