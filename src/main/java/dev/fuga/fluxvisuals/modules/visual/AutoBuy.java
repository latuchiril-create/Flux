package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.ClientGuiProtection;
import dev.fuga.fluxvisuals.gui.ClickGuiScreen;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBuy extends Module {
    private static final DateTimeFormatter DEBUG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBuy.class);
    private static final String SEARCH_COMMAND = "ah search \u041d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0439 \u043c\u0435\u0447";
    private static final long CLOSE_SCREEN_DELAY_MS = 200L;
    private static final long SCREEN_TIMEOUT_MS = 5_000L;
    private static final long CLOSED_AUCTION_REOPEN_DELAY_MS = 1_000L;
    private static final long PURCHASE_RESULT_TIMEOUT_MS = 2_000L;
    private static final long PURCHASE_RETRY_DELAY_MS = 1_400L;
    private static final long PURCHASE_CLICK_DELAY_MIN_MS = 100L;
    private static final long PURCHASE_CLICK_DELAY_MAX_MS = 250L;
    private static final long PURCHASE_STABILITY_DELAY_MS = 1_500L;
    private static final long AFTER_PURCHASE_DELAY_MIN_MS = 500L;
    private static final long AFTER_PURCHASE_DELAY_MAX_MS = 2_000L;
    private static final int EXTENDED_PAUSE_PURCHASE_MIN = 5;
    private static final int EXTENDED_PAUSE_PURCHASE_MAX = 7;
    private static final long EXTENDED_PURCHASE_PAUSE_MIN_MS = 5_000L;
    private static final long EXTENDED_PURCHASE_PAUSE_MAX_MS = 10_000L;
    private static final long AFTER_PAGE_CHANGE_SCAN_DELAY_MS = 100L;
    private static final long RESELL_INTERVAL_MIN_MS = 60_000L;
    private static final long RESELL_INTERVAL_MAX_MS = 65_000L;
    private static final long UNKNOWN_STORAGE_RECHECK_MS = 120_000L;
    private static final long RESELL_TIMEOUT_MS = 12_000L;
    private static final long RESELL_CLOSE_DELAY_MIN_MS = 150L;
    private static final long RESELL_CLOSE_DELAY_MAX_MS = 350L;
    private static final int RESELL_MAX_AH_COMMAND_ATTEMPTS = 2;
    private static final long RESELL_AH_RETRY_MIN_MS = 2_500L;
    private static final long RESELL_AH_RETRY_MAX_MS = 4_500L;
    private static final long AFTER_AH_DELAY_MIN_MS = 350L;
    private static final long AFTER_AH_DELAY_MAX_MS = 550L;
    private static final long AFTER_STORAGE_DELAY_MIN_MS = 750L;
    private static final long AFTER_STORAGE_DELAY_MAX_MS = 1_250L;
    private static final long RESELL_STORAGE_STABILITY_MS = 2_000L;
    private static final long RESELL_RETURN_DELAY_MIN_MS = 500L;
    private static final long RESELL_RETURN_DELAY_MAX_MS = 1_000L;
    private static final long AFTER_RESELL_DELAY_MIN_MS = 1_200L;
    private static final long AFTER_RESELL_DELAY_MAX_MS = 1_800L;
    private static final long RENTAL_INTERVAL_MS = 60L * 60L * 1_000L;
    private static final long MONEY_RESPONSE_TIMEOUT_MS = 4_000L;
    private static final long LOW_BALANCE_IDLE_DELAY_MS = 500L;
    private static final long LOW_BALANCE_MONEY_RECHECK_MIN_MS = 8_000L;
    private static final long LOW_BALANCE_MONEY_RECHECK_MAX_MS = 12_000L;
    private static final long PAGE_CLICK_MIN_DELAY_MS = 200L;
    private static final long PAGE_CLICK_MAX_DELAY_MS = 400L;
    private static final long NEXT_TO_PREVIOUS_MIN_DELAY_MS = 300L;
    private static final long NEXT_TO_PREVIOUS_MAX_DELAY_MS = 400L;
    private static final long PAGE_CONFIRM_INITIAL_LATENCY_MS = 250L;
    private static final long PAGE_CONFIRM_MIN_TIMEOUT_MS = 1_200L;
    private static final long PAGE_CONFIRM_MAX_TIMEOUT_MS = 3_500L;
    private static final long PAGE_CONFIRM_BASE_MARGIN_MS = 180L;
    private static final long PAGE_CONFIRM_RANDOM_MARGIN_MIN_MS = 80L;
    private static final long PAGE_CONFIRM_RANDOM_MARGIN_MAX_MS = 260L;
    private static final long PAGE_RECOVERY_MIN_DELAY_MS = 600L;
    private static final long PAGE_RECOVERY_MAX_DELAY_MS = 1_000L;
    private static final long PAGE_BUTTON_REOPEN_DELAY_MS = 15_000L;
    private static final long PAGE_PROGRESS_WATCHDOG_MS = 30_000L;
    private static final long DEFAULT_ANARCHY_DELAY_MIN_MS = 75_000L;
    private static final long DEFAULT_ANARCHY_DELAY_MAX_MS = 80_000L;
    private static final int INVENTORY_SIZE = 36;
    private static final int RECENT_PURCHASE_LIMIT = 9;
    private static final Pattern MONEY_AMOUNT_PATTERN = Pattern.compile(
            "(?:\\$\\s*|(?:баланс|balance|деньги|монеты)[^0-9]{0,24})([0-9][0-9\\s,._]*(?:[kmкм]{1,2})?)"
                    + "|([0-9][0-9\\s,._]*(?:[kmкм]{1,2})?)\\s*\\$");
    private static final Pattern BALANCE_NUMBER_PATTERN = Pattern.compile(
            "(?:баланс|balance|деньги|монеты)[^0-9]{0,24}([0-9][0-9\\s,._]*(?:[kmкм]{1,2})?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SCOREBOARD_NUMBER_PATTERN = Pattern.compile(
            "(?<![0-9])[0-9][0-9\\s,._]*(?:[kmкм]{1,2})?(?![0-9])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SELLER_NAME_PATTERN = Pattern.compile("([\\p{L}0-9_]{2,32})");

    private final boolean[] swordsBeforePurchase = new boolean[INVENTORY_SIZE];
    private final List<String> anarchyIds = new ArrayList<>();
    private final List<String> bannedSellers = new ArrayList<>();
    private final List<RecentPurchase> recentPurchases = new ArrayList<>();
    private final Set<Long> soldRecentPurchaseTimes = new HashSet<>();
    private Step step = Step.IDLE;
    private boolean processing;
    private boolean anarchySwitchEnabled;
    private boolean anarchyAdEnabled;
    private String anarchyAdText = "";
    private boolean nameEnabled;
    private boolean autoResellEnabled;
    // Runtime rental state belongs to this AutoBuy instance. BotSession creates
    // one AutoBuy per bot, so timers are intentionally never copied from the
    // global settings instance or from another bot.
    private boolean rentalSlotsEnabled;
    private boolean rentalPending;
    private int rentalClicks;
    private long nextRentalAt;
    private String rentalPersistenceKey;
    private boolean resellConfirmationPending;
    private boolean rentalAfterResell;
    /** A sale unlocks the next automatic anarchy rotation in relist mode. */
    private boolean saleSinceAnarchy;
    private boolean auctionListingsKnownPresent;
    private boolean auctionListingsStateKnown;
    private long nextStorageVerificationAt;
    private String nameText = "";
    private int pendingCandidateSlotId = -1;
    private int pendingCandidateSyncId = -1;
    private long pendingCandidateClickAt;
    private PurchaseCandidate pendingPurchase;
    private int pendingPurchaseConfirmationSlotId = -1;
    private int boughtSwordInventoryIndex = -1;
    private int observedPurchaseInventoryIndex = -1;
    private long observedPurchaseAt;
    private boolean purchaseConfirmedByChat;
    private boolean purchaseNotificationSent;
    private int nameSourceInventoryIndex = -1;
    private int previousHotbarSlot = -1;
    private boolean swappedForName;
    private int anarchyCursor;
    private long nextActionAt;
    private long timeoutAt;
    private long nextPageClickAt;
    private boolean awaitingAuctionPageChange;
    private boolean auctionPageClickedNext;
    private int auctionPageClickSyncId = -1;
    private long auctionPageClickAt;
    private long auctionPageConfirmTimeoutMs;
    private long auctionPageChangeDeadline;
    private long auctionPageButtonMissingSince;
    private long auctionPageLastProgressAt;
    private long auctionScanResumeAt;
    private long nextPurchaseAttemptAt;
    private long auctionScreenMissingSince;
    private boolean auctionScreenSeen;
    private boolean auctionScreenConfirmed;
    private int auctionScreenSyncId = -1;
    private long nextAnarchyAt;
    private long nextResellAt;
    private long anarchyDelayMinMs = DEFAULT_ANARCHY_DELAY_MIN_MS;
    private long anarchyDelayMaxMs = DEFAULT_ANARCHY_DELAY_MAX_MS;
    private long resellIntervalMinMs = RESELL_INTERVAL_MIN_MS;
    private long resellIntervalMaxMs = RESELL_INTERVAL_MAX_MS;
    private int resellAuctionSyncId = -1;
    private int resellAhCommandAttempts;
    private long resellStorageOpenedAt;
    /** Sync id of the storage handler whose contents passed the stability wait. */
    private int resellStorageSyncId = -1;
    private long anarchyJoinWaitUntil;
    private boolean pendingAnarchyAd;
    private long externalPauseUntil;
    private boolean externalResumeAuctionScreen;
    private boolean lowBalanceMode;
    private boolean storageCheckPending;
    private boolean resellStorageOpenRequested;
    private boolean resellOpenOnly;
    private boolean resellReturnToSwordSearch;
    private boolean hubSafetyStorageCheckPending;
    private boolean lowBalanceGuardEnabled = true;
    private boolean lowBalanceNotificationSent;
    private boolean lowBalanceResellNotificationSent;
    private boolean resellTriggeredByLowBalance;
    private long lastKnownBalance = -1L;
    private long sessionStartBalance = -1L;
    private long sessionStartedAt;
    private long sessionStoppedAt;
    private int sessionPurchaseCount;
    private int sessionKnownPricePurchaseCount;
    private long sessionPurchaseSpent;
    private int sessionSaleCount;
    private long sessionSaleRevenue;
    private int sessionResellCount;
    private int sessionPageTransitionCount;
    private int sessionPageErrorCount;
    private int sessionSearchReopenCount;
    private long sessionPageLatencyTotalMs;
    private int sessionPageLatencySampleCount;
    private long sessionLastPageConfirmTimeoutMs;
    private long pageLatencyEstimateMs = PAGE_CONFIRM_INITIAL_LATENCY_MS;
    private int purchasesSinceExtendedPause;
    private int purchasesUntilExtendedPause;
    private boolean searchOpenedThisSession;
    private boolean moneyCheckPending;
    private long moneyCheckStartedAt;
    private long nextLowBalanceMoneyCheckAt;
    private MoneyCheckReason moneyCheckReason = MoneyCheckReason.NONE;
    private boolean compassHeldLastTick;
    private long settingsVersion;
    private long copiedSettingsVersion = Long.MIN_VALUE;

    public AutoBuy() {
        super("AutoBuy", "Buys configured netherite swords and coordinates naming, anarchy switching and resale.", ModuleCategory.UTILS);
    }

    public void setRentalPersistenceKey(String key) {
        rentalPersistenceKey = key == null || key.isBlank() ? null : key;
        if (rentalPersistenceKey != null) {
            nextRentalAt = readPersistedRentalAt(rentalPersistenceKey);
        }
    }

    /** Copies user-facing settings while leaving the target bot's runtime state untouched. */
    public void copySettingsFrom(AutoBuy source) {
        if (source == null || source == this || copiedSettingsVersion == source.settingsVersion) {
            return;
        }
        anarchySwitchEnabled = source.anarchySwitchEnabled;
        anarchyAdEnabled = source.anarchyAdEnabled;
        anarchyAdText = source.anarchyAdText;
        nameEnabled = source.nameEnabled;
        nameText = source.nameText;
        autoResellEnabled = source.autoResellEnabled;
        boolean rentalWasEnabled = rentalSlotsEnabled;
        rentalSlotsEnabled = source.rentalSlotsEnabled;
        // Keep nextRentalAt/rentalPending/rentalClicks local to this bot.
        if (rentalWasEnabled && !rentalSlotsEnabled) {
            cancelPendingRentalAndResume();
        }
        lowBalanceGuardEnabled = source.lowBalanceGuardEnabled;
        anarchyDelayMinMs = source.anarchyDelayMinMs;
        anarchyDelayMaxMs = source.anarchyDelayMaxMs;
        resellIntervalMinMs = source.resellIntervalMinMs;
        resellIntervalMaxMs = source.resellIntervalMaxMs;
        anarchyIds.clear();
        anarchyIds.addAll(source.anarchyIds);
        bannedSellers.clear();
        bannedSellers.addAll(source.bannedSellers);
        copiedSettingsVersion = source.settingsVersion;
    }

    @Override
    public void onTick(MinecraftClient client) {
        processCompassAnarchy(client);
        process(client);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        resetDebugLog();
        debug("enabled");
        compassHeldLastTick = false;
        sessionStartBalance = -1L;
        resetRuntime(client);
        resetLowBalanceNotifications();
        clearAnarchyJoinWait();
        long now = System.currentTimeMillis();
        resetSessionStats(now);
        auctionListingsStateKnown = false;
        nextStorageVerificationAt = now;
        resetExtendedPurchasePauseCycle();
        scheduleNextAnarchy(now);
        scheduleNextResell(now);
        step = Step.WAIT_CLICK_GUI_CLOSE;
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        debug("disabled; step=" + step);
        compassHeldLastTick = false;
        if (sessionStartedAt > 0L && sessionStoppedAt <= 0L) {
            sessionStoppedAt = System.currentTimeMillis();
        }
        long finalBalance = lastKnownBalance;
        restoreNamedSword(client);
        resetRuntime(client);
        resetLowBalanceNotifications();
        lastKnownBalance = finalBalance;
        clearAnarchyJoinWait();
    }

    public void onHandledScreenRendered(MinecraftClient client) {
        process(client);
    }

    /** Returns true when this bot's enabled AutoBuy receives the AFK restriction. */
    public boolean shouldTriggerAntiAfkMovement(String message) {
        return false;
    }

    /** Restarts the auction workflow after the session has cleared an AFK restriction. */
    public void onAntiAfkMovementFinished() {
        // Retained for compatibility with older integrations.  Anti-AFK no
        // longer changes the auction state or emits movement packets.
    }

    public void onChatMessage(String message) {
        debug("chat: " + message);
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        if (isEnabled() && isOwnAuctionSaleMessage(message)) {
            noteAuctionSale();
        }
        if (isEnabled() && resellConfirmationPending && isResellSuccessMessage(message)) {
            finishConfirmedResell(client, now);
            return;
        }
        if (isEnabled() && rentalPending && isRentalPurchaseSuccessMessage(message)) {
            finishRentalAndResume(client, now);
            return;
        }
        if (isEnabled() && isPurchaseStep(step)) {
            if (isPurchaseFailureMessage(message)) {
                LOGGER.info("AutoBuy: server rejected purchase, returning to search");
                if (client != null && ready(client)) {
                    restartSearch(client, now);
                }
                return;
            }
            if (isPurchaseSuccessMessage(message)) {
                purchaseConfirmedByChat = true;
            }
        }
        if (isEnabled() && isResellStep(step) && isStorageEmptyForResellMessage(message)) {
            storageCheckPending = false;
            auctionListingsKnownPresent = false;
            auctionListingsStateKnown = true;
            nextStorageVerificationAt = now + UNKNOWN_STORAGE_RECHECK_MS;
            nextResellAt = 0L;
            if (isBalanceBelowConfiguredPrice() && client != null && ready(client)) {
                leaveHubAndDisable(client);
            } else if (client != null && ready(client)) {
                requestMoneyCheck(client, now, MoneyCheckReason.STORAGE_EMPTY);
            }
            return;
        }

        Long balance = extractBalance(message);
        if (balance == null) {
            return;
        }
        updateKnownBalance(balance, client, now);
    }

    public void considerAuctionSlot(Slot slot, List<String> tooltipLines, List<Text> tooltipSnapshot) {
        long now = System.currentTimeMillis();
        if (!isScanningAuction() || !auctionScreenConfirmed || now < auctionScanResumeAt
                || pendingCandidateSlotId >= 0 || slot == null || !slot.hasStack()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.currentScreenHandler == null
                || client.player.currentScreenHandler.syncId != auctionScreenSyncId
                || slot.inventory == client.player.getInventory()) {
            return;
        }

        ItemStack stack = slot.getStack();
        if (!stack.isOf(Items.NETHERITE_SWORD)) {
            return;
        }

        ItemResorter.HighlightMatch match = FluxVisualsClient.MODULE_MANAGER.getItemResorter()
                .matchConfiguredTooltip(stack, tooltipLines);
        if (match.matches() && !isSellerBanned(tooltipLines)) {
            resetPurchaseVerification();
            Long price = ItemResorter.extractPrice(tooltipLines);
            String seller = extractSeller(tooltipLines);
            pendingPurchase = new PurchaseCandidate(
                    stack.getName().getString(),
                    price == null ? -1L : price,
                    seller,
                    stack.copy(),
                    copyTooltip(tooltipSnapshot)
            );
            pendingCandidateSyncId = client.player.currentScreenHandler.syncId;
            pendingCandidateSlotId = slot.id;
            pendingCandidateClickAt = now + randomDelay(
                    PURCHASE_CLICK_DELAY_MIN_MS,
                    PURCHASE_CLICK_DELAY_MAX_MS
            );
        }
    }

    public void considerPurchaseConfirmationSlot(Slot slot) {
        if (!isEnabled() || (step != Step.WAIT_CONFIRM && step != Step.WAIT_PURCHASE) || pendingPurchaseConfirmationSlotId >= 0
                || slot == null || !slot.hasStack()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || !isPurchaseConfirmationScreen(client)
                || slot.inventory == client.player.getInventory()) {
            return;
        }
        String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("\u043a\u0443\u043f\u0438\u0442\u044c") || name.contains("buy")) {
            pendingPurchaseConfirmationSlotId = slot.id;
        }
    }

    public boolean isScanningAuction() {
        return isEnabled() && step == Step.WAIT_AUCTION;
    }

    public boolean isWorking() {
        return isEnabled()
                && step != Step.IDLE
                && step != Step.WAIT_CLICK_GUI_CLOSE
                && step != Step.WAIT_EXTERNAL
                && step != Step.WAIT_MONEY
                && step != Step.LOW_BALANCE_WAIT;
    }

    public long getSessionStartBalance() {
        return sessionStartBalance;
    }

    public long getLastKnownBalance() {
        return lastKnownBalance;
    }

    public void setLastKnownBalance(long balance) {
        if (balance >= 0L) {
            this.lastKnownBalance = balance;
        }
    }

    public List<RecentPurchase> getRecentPurchases() {
        return List.copyOf(recentPurchases);
    }

    public void recordSwordSale(long price) {
        if (!isEnabled() || sessionStartedAt <= 0L || price < 0L) {
            return;
        }
        sessionSaleCount++;
        sessionSaleRevenue = saturatedStatAdd(sessionSaleRevenue, price);
    }

    public void noteAuctionListingConfirmed() {
        auctionListingsKnownPresent = true;
        auctionListingsStateKnown = true;
        nextStorageVerificationAt = 0L;
        if (autoResellEnabled && nextResellAt <= 0L) {
            scheduleNextResell(System.currentTimeMillis());
        }
    }

    public void noteAuctionSale() {
        markOldestRecentPurchaseSold();
        saleSinceAnarchy = true;
    }

    public boolean hasAuctionListingsKnownPresent() {
        return auctionListingsKnownPresent;
    }

    public boolean isRecentPurchaseSold(long purchasedAt) {
        return soldRecentPurchaseTimes.contains(purchasedAt);
    }

    public SessionStats getSessionStats() {
        long sessionEndAt = isEnabled() || sessionStoppedAt <= 0L
                ? System.currentTimeMillis()
                : sessionStoppedAt;
        long runningTimeMs = sessionStartedAt <= 0L
                ? 0L
                : Math.max(0L, sessionEndAt - sessionStartedAt);
        long averagePurchasePrice = sessionKnownPricePurchaseCount <= 0
                ? -1L
                : sessionPurchaseSpent / sessionKnownPricePurchaseCount;
        long averagePageLatencyMs = sessionPageLatencySampleCount <= 0
                ? -1L
                : sessionPageLatencyTotalMs / sessionPageLatencySampleCount;
        return new SessionStats(
                isEnabled(),
                step.name(),
                runningTimeMs,
                sessionPurchaseCount,
                sessionKnownPricePurchaseCount,
                sessionPurchaseSpent,
                averagePurchasePrice,
                sessionSaleCount,
                sessionSaleRevenue,
                sessionResellCount,
                sessionPageTransitionCount,
                sessionPageErrorCount,
                sessionSearchReopenCount,
                averagePageLatencyMs,
                pageLatencyEstimateMs,
                sessionLastPageConfirmTimeoutMs,
                sessionStartBalance,
                lastKnownBalance
        );
    }

    public boolean isAnarchySwitchEnabled() {
        return anarchySwitchEnabled;
    }

    public void setAnarchySwitchEnabled(boolean anarchySwitchEnabled) {
        if (this.anarchySwitchEnabled == anarchySwitchEnabled) {
            return;
        }
        this.anarchySwitchEnabled = anarchySwitchEnabled;
        settingsVersion++;
        scheduleNextAnarchy(System.currentTimeMillis());
        FluxVisualsClient.requestConfigSave();
    }

    public List<String> getAnarchyIds() {
        return List.copyOf(anarchyIds);
    }

    public boolean startImmediateAnarchyFromList(MinecraftClient client) {
        if (!isEnabled() || !ready(client) || anarchyIds.isEmpty()
                || isExternalMaintenanceWorking() || !canStartImmediateAnarchy()) {
            return false;
        }
        pendingPurchase = null;
        resetPurchaseVerification();
        clearAnarchyJoinWait();
        startAnarchySwitch(client, System.currentTimeMillis());
        return true;
    }

    public void setAnarchyIds(List<String> values) {
        settingsVersion++;
        anarchyIds.clear();
        if (values != null) {
            for (String value : values) {
                addAnarchyId(value);
            }
        }
        anarchyCursor = 0;
        scheduleNextAnarchy(System.currentTimeMillis());
        FluxVisualsClient.requestConfigSave();
    }

    public boolean addAnarchyId(String value) {
        String cleaned = sanitizeAnarchyId(value);
        if (cleaned.isEmpty() || anarchyIds.contains(cleaned)) {
            return false;
        }
        anarchyIds.add(cleaned);
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public void removeAnarchyId(String value) {
        if (anarchyIds.remove(sanitizeAnarchyId(value))) {
            settingsVersion++;
            anarchyCursor = 0;
            FluxVisualsClient.requestConfigSave();
        }
    }

    public List<String> getBannedSellers() {
        return List.copyOf(bannedSellers);
    }

    public void setBannedSellers(List<String> values) {
        settingsVersion++;
        bannedSellers.clear();
        if (values != null) {
            for (String value : values) {
                addBannedSeller(value);
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean addBannedSeller(String value) {
        String cleaned = sanitizeSeller(value);
        if (cleaned.isEmpty() || isSellerBanned(cleaned)) {
            return false;
        }
        bannedSellers.add(cleaned);
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    public void removeBannedSeller(String value) {
        String normalized = normalizeSeller(value);
        if (bannedSellers.removeIf(seller -> normalizeSeller(seller).equals(normalized))) {
            settingsVersion++;
            FluxVisualsClient.requestConfigSave();
        }
    }

    public boolean isAnarchyAdEnabled() {
        return anarchyAdEnabled;
    }

    public void setAnarchyAdEnabled(boolean anarchyAdEnabled) {
        if (this.anarchyAdEnabled == anarchyAdEnabled) {
            return;
        }
        this.anarchyAdEnabled = anarchyAdEnabled;
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
    }

    public String getAnarchyAdText() {
        return anarchyAdText;
    }

    public void setAnarchyAdText(String anarchyAdText) {
        String next = anarchyAdText == null ? "" : anarchyAdText.trim();
        if (this.anarchyAdText.equals(next)) {
            return;
        }
        this.anarchyAdText = next;
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isNameEnabled() {
        return nameEnabled;
    }

    public void setNameEnabled(boolean nameEnabled) {
        if (this.nameEnabled == nameEnabled) {
            return;
        }
        this.nameEnabled = nameEnabled;
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
    }

    public String getNameText() {
        return nameText;
    }

    public void setNameText(String nameText) {
        String next = nameText == null ? "" : nameText.trim();
        if (this.nameText.equals(next)) {
            return;
        }
        this.nameText = next;
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isAutoResellEnabled() {
        return autoResellEnabled;
    }

    public boolean isLowBalanceGuardEnabled() {
        return lowBalanceGuardEnabled;
    }

    public boolean isHubSafetyStorageCheckPending() {
        return hubSafetyStorageCheckPending;
    }

    public long getNextAnarchyAt() {
        return nextAnarchyAt;
    }

    public long getNextResellAt() {
        return nextResellAt;
    }

    public boolean isRentalSlotsEnabled() {
        return rentalSlotsEnabled;
    }

    public long getNextRentalAt() {
        return nextRentalAt;
    }

    public void setRentalSlotsEnabled(boolean enabled) {
        if (rentalSlotsEnabled == enabled) {
            return;
        }
        rentalSlotsEnabled = enabled;
        if (!enabled) {
            cancelPendingRentalAndResume();
        }
        settingsVersion++;
        FluxVisualsClient.requestConfigSave();
    }

    /** Cancels only the in-progress rental menu, preserving this session's one-hour timer. */
    private void cancelPendingRentalAndResume() {
        if (!rentalPending && rentalClicks == 0) {
            return;
        }
        rentalPending = false;
        rentalClicks = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (isEnabled() && ready(client) && isResellStep(step)) {
            debug("rental disabled while pending; resuming auction search");
            restartSearch(client, System.currentTimeMillis());
        }
    }

    public void setLowBalanceGuardEnabled(boolean enabled) {
        if (lowBalanceGuardEnabled == enabled) {
            return;
        }
        lowBalanceGuardEnabled = enabled;
        settingsVersion++;
        if (!enabled) {
            lowBalanceMode = false;
            storageCheckPending = false;
            resellStorageOpenRequested = false;
            hubSafetyStorageCheckPending = false;
            resetLowBalanceNotifications();
        } else if (isBalanceBelowConfiguredPrice()) {
            hubSafetyStorageCheckPending = false;
            lowBalanceMode = true;
            storageCheckPending = true;
        }
        FluxVisualsClient.requestConfigSave();
    }

    public long getAnarchyDelayMinMs() {
        return anarchyDelayMinMs;
    }

    public long getAnarchyDelayMaxMs() {
        return anarchyDelayMaxMs;
    }

    public void setAnarchyDelaySeconds(long seconds) {
        setAnarchyDelayRangeSeconds(seconds, seconds);
    }

    public void setAnarchyDelayRangeSeconds(long minSeconds, long maxSeconds) {
        long min = Math.max(11L, Math.min(300L, minSeconds));
        long max = Math.max(min, Math.min(300L, maxSeconds));
        anarchyDelayMinMs = min * 1_000L;
        anarchyDelayMaxMs = max * 1_000L;
        settingsVersion++;
        scheduleNextAnarchy(System.currentTimeMillis());
    }

    public long getResellIntervalMinMs() {
        return resellIntervalMinMs;
    }

    public long getResellIntervalMaxMs() {
        return resellIntervalMaxMs;
    }

    public void setResellIntervalSeconds(long minSeconds, long maxSeconds) {
        long min = Math.max(60L, Math.min(300L, minSeconds));
        long max = Math.max(min, Math.min(300L, maxSeconds));
        resellIntervalMinMs = min * 1_000L;
        resellIntervalMaxMs = max * 1_000L;
        settingsVersion++;
        if (autoResellEnabled && nextResellAt <= System.currentTimeMillis()) {
            scheduleNextResell(System.currentTimeMillis());
        }
    }

    public void setAutoResellEnabled(boolean autoResellEnabled) {
        if (this.autoResellEnabled == autoResellEnabled) {
            return;
        }
        this.autoResellEnabled = autoResellEnabled;
        settingsVersion++;
        scheduleNextResell(System.currentTimeMillis());
        FluxVisualsClient.requestConfigSave();
    }

    private void process(MinecraftClient client) {
        if (processing) {
            return;
        }
        processing = true;
        try {
            if (!isEnabled()) {
                return;
            }
            if (!ready(client)) {
                resetRuntime(client);
                step = Step.WAIT_CLICK_GUI_CLOSE;
                return;
            }
            if (client.currentScreen instanceof ClickGuiScreen) {
                pauseForClickGui(client);
                return;
            }

            // A manually opened /sell or /ah sellgui belongs to the sell
            // workflow. AutoBuy must not interpret it as a closed auction and
            // must never click inventory swords into that container.
            if (isExternalSellScreen(client)) {
                if (step != Step.WAIT_EXTERNAL) {
                    pendingCandidateSlotId = -1;
                    pendingPurchaseConfirmationSlotId = -1;
                    auctionScreenConfirmed = false;
                    auctionScreenSyncId = -1;
                    externalResumeAuctionScreen = false;
                    externalPauseUntil = 0L;
                    step = Step.WAIT_EXTERNAL;
                }
                return;
            }

            long now = System.currentTimeMillis();
            // FunTime exposes the account balance in the sidebar as the
            // "Монеты" line. Read it from this session's world instead of
            // sending a balance command, which is unreliable for bots and
            // can trigger server-side command/rate limits.
            refreshBalanceFromScoreboard(client, now);
            // === AutoSell debug: log every tick when AutoSell has a pending queue ===
            {
                Telegram tg = FluxVisualsClient.MODULE_MANAGER.getTelegram();
                boolean sellPending = tg.isAutomaticSellPendingOrWorking();
                boolean sellInventory = tg.isInventorySellWorking();
                boolean sellPendingOrWorking = tg.isInventorySellPendingOrWorking();
                if (sellPending || sellInventory || sellPendingOrWorking) {
                    debug("AB tick: step=" + step
                            + ", isAutomaticSellPendingOrWorking=" + sellPending
                            + ", isInventorySellWorking=" + sellInventory
                            + ", isInventorySellPendingOrWorking=" + sellPendingOrWorking
                            + ", externalPauseUntil=" + externalPauseUntil
                            + ", isExternalMaintenanceWorking=" + isExternalMaintenanceWorking()
                            + ", isExternalMaintenanceBlockingResume=" + isExternalMaintenanceBlockingResume()
                            + ", autoResellWorking=" + FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()
                            + ", anarchySwitcherWorking=" + FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()
                            + ", leaveReportWorking=" + tg.isLeaveReportWorking());
                }
            }
            if (now < anarchyJoinWaitUntil) {
                return;
            }
            if (anarchyJoinWaitUntil > 0L) {
                anarchyJoinWaitUntil = 0L;
                if (pendingAnarchyAd) {
                    sendAd(client);
                    pendingAnarchyAd = false;
                }
            }
            if (step == Step.WAIT_MONEY) {
                if (isExternalMaintenanceWorking() && tryPauseForExternalAction(client, 0L)) {
                    return;
                }
                processMoneyWait(client, now);
                return;
            }
            if (step == Step.WAIT_EXTERNAL) {
                Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
                boolean automaticSell = telegram.isAutomaticSellPendingOrWorking();
                boolean inventorySell = telegram.isInventorySellPendingOrWorking();
                if (automaticSell || inventorySell || isExternalSellScreen(client)) {
                    // Telegram owns the inventory/sellgui flow until it finishes.
                    // Do not restart search here: that closes sellgui on the next tick.
                    return;
                }
                if (isExternalMaintenanceBlockingResume() || now < externalPauseUntil) {
                    debug("waiting external action: maintenance=" + isExternalMaintenanceBlockingResume()
                            + ", autoSell=" + automaticSell + ", pauseUntil=" + externalPauseUntil);
                    return;
                }
                if (externalResumeAuctionScreen && isSwordSearchScreenLikeRecentPurchases(client)) {
                    externalResumeAuctionScreen = false;
                    step = Step.WAIT_AUCTION;
                    auctionScreenConfirmed = true;
                    auctionScreenSeen = true;
                    auctionScreenMissingSince = 0L;
                    auctionScreenSyncId = client.player.currentScreenHandler.syncId;
                    timeoutAt = now + SCREEN_TIMEOUT_MS;
                    resetAuctionPageCycle(now, true);
                    return;
                }
                externalResumeAuctionScreen = false;
                restartSearch(client, now);
                return;
            }
            if (isExternalMaintenanceWorking() && tryPauseForExternalAction(client, 0L)) {
                return;
            }
            if (hubSafetyStorageCheckPending && isMaintenanceInterruptible(step)) {
                hubSafetyStorageCheckPending = false;
                startResell(client, now, true);
                return;
            }
            if (step == Step.LOW_BALANCE_WAIT) {
                processLowBalance(client, now);
                return;
            }
            if (lowBalanceGuardEnabled && lowBalanceMode
                    && !isNameStep(step) && !isAnarchyStep(step) && !isResellStep(step)) {
                enterLowBalanceMode(client, now, true);
                return;
            }
            if (step == Step.IDLE || step == Step.WAIT_CLICK_GUI_CLOSE) {
                requestMoneyCheck(client, now, MoneyCheckReason.STARTUP);
                return;
            }

            if (isNameStep(step)) {
                processName(client, now);
                return;
            }
            if (isAnarchyStep(step)) {
                processAnarchy(client, now);
                return;
            }
            if (isResellStep(step)) {
                processResell(client, now);
                return;
            }
            if (isMaintenanceInterruptible(step) && shouldResell(now)) {
                startResell(client, now);
                return;
            }
            if (isMaintenanceInterruptible(step) && shouldSwitchAnarchy(now)) {
                startAnarchySwitch(client, now);
                return;
            }

            switch (step) {
                case SEARCH_DELAY -> {
                    if (now >= nextActionAt) {
                        openSearch(client, now);
                    }
                }
                case WAIT_AUCTION -> processAuction(client, now);
                case WAIT_CONFIRM -> processConfirm(client, now);
                case WAIT_PURCHASE -> processPurchaseResult(client, now);
                default -> {
                }
            }
        } finally {
            processing = false;
        }
    }

    private void processMoneyWait(MinecraftClient client, long now) {
        if (moneyCheckPending && now < timeoutAt) {
            return;
        }

        MoneyCheckReason reason = moneyCheckReason;
        moneyCheckPending = false;
        moneyCheckReason = MoneyCheckReason.NONE;
        if (lastKnownBalance < 0L) {
            lowBalanceMode = false;
            restartSearch(client, now);
            return;
        }

        if (reason == MoneyCheckReason.STORAGE_EMPTY) {
            if (isBalanceBelowConfiguredPrice()) {
                leaveHubAndDisable(client);
            } else {
                lowBalanceMode = false;
                restartSearch(client, now);
            }
            return;
        }

        if (lowBalanceGuardEnabled && isBalanceBelowConfiguredPrice()) {
            hubSafetyStorageCheckPending = false;
            enterLowBalanceMode(client, now, reason == MoneyCheckReason.STARTUP);
        } else {
            lowBalanceMode = false;
            hubSafetyStorageCheckPending = false;
            restartSearch(client, now);
        }
    }

    private void processLowBalance(MinecraftClient client, long now) {
        if (!lowBalanceGuardEnabled) {
            lowBalanceMode = false;
            storageCheckPending = false;
            restartSearch(client, now);
            return;
        }
        if (!isBalanceBelowConfiguredPrice()) {
            lowBalanceMode = false;
            restartSearch(client, now);
            return;
        }
        if (now < nextActionAt) {
            return;
        }

        if (resellStorageOpenRequested) {
            startResell(client, now, true);
            return;
        }
        if (storageCheckPending) {
            startResell(client, now);
            return;
        }
        if (shouldResell(now)) {
            startResell(client, now);
            return;
        }
        if (now >= nextLowBalanceMoneyCheckAt) {
            refreshBalanceFromScoreboard(client, now);
            nextLowBalanceMoneyCheckAt = now + randomDelay(
                    LOW_BALANCE_MONEY_RECHECK_MIN_MS,
                    LOW_BALANCE_MONEY_RECHECK_MAX_MS
            );
            return;
        }
        if (shouldSwitchAnarchy(now)) {
            startAnarchySwitch(client, now);
            return;
        }
        nextActionAt = now + LOW_BALANCE_IDLE_DELAY_MS;
    }

    private void processAuction(MinecraftClient client, long now) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            if (auctionScreenMissingSince <= 0L) {
                auctionScreenMissingSince = now;
            }
            long reopenDelay = auctionScreenSeen ? CLOSED_AUCTION_REOPEN_DELAY_MS : SCREEN_TIMEOUT_MS;
            if (now - auctionScreenMissingSince >= reopenDelay) {
                restartSearch(client, now);
            }
            return;
        }
        if (!isAuctionSearchScreen(client)) {
            auctionScreenConfirmed = false;
            auctionScreenSyncId = -1;
            if (now >= timeoutAt) {
                restartSearch(client, now);
            }
            return;
        }
        auctionScreenConfirmed = true;
        auctionScreenSyncId = client.player.currentScreenHandler.syncId;
        timeoutAt = now + SCREEN_TIMEOUT_MS;
        auctionScreenSeen = true;
        auctionScreenMissingSince = 0L;
        if (hasCursorStack(client)) {
            restartSearch(client, now);
            return;
        }
        if (auctionPageLastProgressAt <= 0L) {
            auctionPageLastProgressAt = now;
        } else if (now - auctionPageLastProgressAt >= PAGE_PROGRESS_WATCHDOG_MS) {
            sessionPageErrorCount++;
            LOGGER.warn("AutoBuy: auction page cycle made no confirmed progress, reopening search");
            restartSearch(client, now);
            return;
        }
        if (processPendingAuctionPageChange(client, now)) {
            return;
        }
        if (pendingCandidateSlotId >= 0) {
            if (now < pendingCandidateClickAt) {
                return;
            }
            int candidateSyncId = pendingCandidateSyncId;
            pendingCandidateSyncId = -1;
            if (candidateSyncId != client.player.currentScreenHandler.syncId) {
                pendingCandidateSlotId = -1;
                pendingCandidateClickAt = 0L;
                pendingPurchase = null;
                resetPurchaseVerification();
                return;
            }
            Slot candidate = findSlot(client, pendingCandidateSlotId);
            pendingCandidateSlotId = -1;
            pendingCandidateClickAt = 0L;
            if (candidate != null && candidate.hasStack() && candidate.getStack().isOf(Items.NETHERITE_SWORD)) {
                snapshotSwords(client.player.getInventory());
                clickAuctionSlot(client, candidate.id);
                LOGGER.info("AutoBuy: shift-clicked matched auction sword in slot {}", candidate.id);
                step = Step.WAIT_PURCHASE;
                pendingPurchaseConfirmationSlotId = -1;
                nextPurchaseAttemptAt = now + PURCHASE_RETRY_DELAY_MS;
                timeoutAt = now + PURCHASE_RESULT_TIMEOUT_MS;
                return;
            }
            pendingPurchase = null;
            resetPurchaseVerification();
        }
        if (now >= nextPageClickAt) {
            boolean hasPrevious = hasAuctionPageButton(client, false);
            boolean hasNext = hasAuctionPageButton(client, true);
            boolean clickedNext = !hasPrevious && hasNext;
            boolean clicked = hasPrevious
                    ? clickAuctionPageButton(client, false)
                    : clickedNext && clickAuctionPageButton(client, true);

            if (!hasPrevious && !hasNext) {
                if (auctionPageButtonMissingSince <= 0L) {
                    auctionPageButtonMissingSince = now;
                }
                long missingFor = now - auctionPageButtonMissingSince;
                if (missingFor >= PAGE_BUTTON_REOPEN_DELAY_MS) {
                    sessionPageErrorCount++;
                    restartSearch(client, now);
                    return;
                }
            } else {
                auctionPageButtonMissingSince = 0L;
            }

            if (clicked) {
                auctionPageButtonMissingSince = 0L;
                pendingCandidateSlotId = -1;
                awaitingAuctionPageChange = true;
                auctionPageClickedNext = clickedNext;
                auctionPageClickSyncId = client.player.currentScreenHandler.syncId;
                auctionPageClickAt = now;
                auctionPageConfirmTimeoutMs = randomizedPageConfirmTimeout();
                sessionLastPageConfirmTimeoutMs = auctionPageConfirmTimeoutMs;
                auctionPageChangeDeadline = now + auctionPageConfirmTimeoutMs;
                nextPageClickAt = now + pageDelayAfterClick(clickedNext);
                auctionScanResumeAt = auctionPageChangeDeadline;
            } else {
                nextPageClickAt = now + randomDelay(PAGE_CLICK_MIN_DELAY_MS, PAGE_CLICK_MAX_DELAY_MS);
            }
        }
    }

    private boolean processPendingAuctionPageChange(MinecraftClient client, long now) {
        if (!awaitingAuctionPageChange || client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        if (client.player.currentScreenHandler.syncId != auctionPageClickSyncId) {
            auctionPageClickSyncId = client.player.currentScreenHandler.syncId;
        }

        boolean hasPrevious = hasAuctionPageButton(client, false);
        boolean hasNext = hasAuctionPageButton(client, true);
        boolean directionChangeConfirmed = auctionPageClickedNext
                ? hasPrevious
                : !hasPrevious && hasNext;
        boolean pageChangeConfirmed = directionChangeConfirmed;
        if (pageChangeConfirmed) {
            if (directionChangeConfirmed) {
                auctionPageLastProgressAt = now;
                recordPageTransitionLatency(now - auctionPageClickAt);
            }
            awaitingAuctionPageChange = false;
            auctionPageClickSyncId = -1;
            auctionPageClickAt = 0L;
            auctionPageChangeDeadline = 0L;
            auctionPageClickedNext = false;
            pendingCandidateSlotId = -1;
            auctionScanResumeAt = now + AFTER_PAGE_CHANGE_SCAN_DELAY_MS;
            nextPageClickAt = Math.max(nextPageClickAt, auctionScanResumeAt);
            return true;
        }
        if (now < auctionPageChangeDeadline) {
            return true;
        }

        sessionPageErrorCount++;
        awaitingAuctionPageChange = false;
        auctionPageClickSyncId = -1;
        auctionPageClickAt = 0L;
        auctionPageChangeDeadline = 0L;
        auctionPageClickedNext = false;
        pendingCandidateSlotId = -1;
        auctionScanResumeAt = now + AFTER_PAGE_CHANGE_SCAN_DELAY_MS;
        nextPageClickAt = now + randomDelay(PAGE_RECOVERY_MIN_DELAY_MS, PAGE_RECOVERY_MAX_DELAY_MS);
        if (hasPrevious || hasNext) {
            auctionPageButtonMissingSince = 0L;
            LOGGER.warn("AutoBuy: page click was not confirmed, recovering in the current auction window");
        } else {
            if (auctionPageButtonMissingSince <= 0L) {
                auctionPageButtonMissingSince = now;
            }
            LOGGER.warn("AutoBuy: page click was not confirmed and page buttons are temporarily missing");
        }
        return true;
    }

    private void resetAuctionPageCycle(long now, boolean scheduleNextClick) {
        nextPageClickAt = scheduleNextClick
                ? now + randomDelay(PAGE_CLICK_MIN_DELAY_MS, PAGE_CLICK_MAX_DELAY_MS)
                : 0L;
        awaitingAuctionPageChange = false;
        auctionPageClickedNext = false;
        auctionPageClickSyncId = -1;
        auctionPageClickAt = 0L;
        auctionPageConfirmTimeoutMs = 0L;
        auctionPageChangeDeadline = 0L;
        auctionPageButtonMissingSince = 0L;
        auctionPageLastProgressAt = scheduleNextClick ? now : 0L;
        auctionScanResumeAt = 0L;
    }

    private static long pageDelayAfterClick(boolean clickedNext) {
        return clickedNext
                ? randomDelay(NEXT_TO_PREVIOUS_MIN_DELAY_MS, NEXT_TO_PREVIOUS_MAX_DELAY_MS)
                : randomDelay(PAGE_CLICK_MIN_DELAY_MS, PAGE_CLICK_MAX_DELAY_MS);
    }

    private long randomizedPageConfirmTimeout() {
        long estimatedLatency = Math.max(1L, pageLatencyEstimateMs);
        long baseTimeout = saturatedStatAdd(
                saturatedStatAdd(estimatedLatency, estimatedLatency),
                PAGE_CONFIRM_BASE_MARGIN_MS
        );
        long minTimeout = clamp(
                saturatedStatAdd(baseTimeout, PAGE_CONFIRM_RANDOM_MARGIN_MIN_MS),
                PAGE_CONFIRM_MIN_TIMEOUT_MS,
                PAGE_CONFIRM_MAX_TIMEOUT_MS
        );
        long maxTimeout = clamp(
                saturatedStatAdd(baseTimeout, PAGE_CONFIRM_RANDOM_MARGIN_MAX_MS),
                minTimeout,
                PAGE_CONFIRM_MAX_TIMEOUT_MS
        );
        return randomDelay(minTimeout, maxTimeout);
    }

    private void recordPageTransitionLatency(long latencyMs) {
        long boundedLatency = clamp(latencyMs, 1L, PAGE_CONFIRM_MAX_TIMEOUT_MS);
        sessionPageTransitionCount++;
        sessionPageLatencySampleCount++;
        sessionPageLatencyTotalMs = saturatedStatAdd(sessionPageLatencyTotalMs, boundedLatency);
        if (sessionPageLatencySampleCount == 1) {
            pageLatencyEstimateMs = boundedLatency;
        } else {
            pageLatencyEstimateMs = (pageLatencyEstimateMs * 3L + boundedLatency) / 4L;
        }
    }

    private void processConfirm(MinecraftClient client, long now) {
        if (now < nextActionAt) {
            return;
        }
        if (now >= timeoutAt) {
            restartSearch(client, now);
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return;
        }
        if (now >= nextPurchaseAttemptAt) {
            int confirmationSlotId = pendingPurchaseConfirmationSlotId;
            pendingPurchaseConfirmationSlotId = -1;
            if (confirmationSlotId >= 0 && clickPurchaseConfirmation(client, confirmationSlotId)) {
                LOGGER.info("AutoBuy: clicked purchase confirmation");
                step = Step.WAIT_PURCHASE;
                nextPurchaseAttemptAt = now + PURCHASE_RETRY_DELAY_MS;
                timeoutAt = now + PURCHASE_RESULT_TIMEOUT_MS;
                return;
            }
            if (clickPurchaseConfirmation(client)) {
                LOGGER.info("AutoBuy: clicked purchase confirmation");
                step = Step.WAIT_PURCHASE;
                nextPurchaseAttemptAt = now + PURCHASE_RETRY_DELAY_MS;
                timeoutAt = now + PURCHASE_RESULT_TIMEOUT_MS;
                return;
            }
        }
        if (isAuctionSearchScreen(client) && now >= nextPurchaseAttemptAt) {
            step = Step.WAIT_AUCTION;
            timeoutAt = now + SCREEN_TIMEOUT_MS;
        }
    }

    private void processPurchaseResult(MinecraftClient client, long now) {
        if (client.player == null) {
            return;
        }
        int addedSword = findAddedSword(client.player.getInventory());
        if (addedSword >= 0) {
            if (observedPurchaseInventoryIndex != addedSword) {
                observedPurchaseInventoryIndex = addedSword;
                observedPurchaseAt = now;
            }
            if (!purchaseConfirmedByChat && now - observedPurchaseAt < PURCHASE_STABILITY_DELAY_MS) {
                return;
            }
            boughtSwordInventoryIndex = addedSword;
            closeCurrentScreen(client);
            if (!purchaseNotificationSent) {
                purchaseNotificationSent = true;
                recordSuccessfulPurchase(pendingPurchase, client.player.getInventory().getStack(addedSword));
                sendPurchaseNotification();
            }
            if (nameEnabled && !nameText.isBlank()) {
                step = Step.NAME_PREPARE;
                nextActionAt = now + CLOSE_SCREEN_DELAY_MS;
            } else {
                finishSuccessfulPurchase(client, now);
            }
            return;
        }
        observedPurchaseInventoryIndex = -1;
        observedPurchaseAt = 0L;
        if (now >= nextPurchaseAttemptAt) {
            int confirmationSlotId = pendingPurchaseConfirmationSlotId;
            pendingPurchaseConfirmationSlotId = -1;
            if (confirmationSlotId >= 0 && clickPurchaseConfirmation(client, confirmationSlotId)) {
                LOGGER.info("AutoBuy: clicked fallback purchase confirmation");
                nextPurchaseAttemptAt = now + PURCHASE_RETRY_DELAY_MS;
                timeoutAt = now + PURCHASE_RESULT_TIMEOUT_MS;
                return;
            }
            if (clickPurchaseConfirmation(client)) {
                LOGGER.info("AutoBuy: clicked fallback purchase confirmation");
                nextPurchaseAttemptAt = now + PURCHASE_RETRY_DELAY_MS;
                timeoutAt = now + PURCHASE_RESULT_TIMEOUT_MS;
                return;
            }
        }
        if (isAuctionSearchScreen(client) && now >= nextPurchaseAttemptAt) {
            step = Step.WAIT_AUCTION;
            timeoutAt = now + SCREEN_TIMEOUT_MS;
            return;
        }
        if (now >= timeoutAt) {
            restartSearch(client, now);
        }
    }

    private void processName(MinecraftClient client, long now) {
        if (now < nextActionAt) {
            return;
        }
        if (step == Step.NAME_PREPARE) {
            prepareNamedSword(client, now);
            return;
        }
        if (step == Step.NAME_SEND) {
            client.getNetworkHandler().sendChatCommand("name " + nameText);
            step = Step.NAME_RESTORE;
            nextActionAt = now + 150L;
            return;
        }
        if (step == Step.NAME_RESTORE) {
            restoreNamedSword(client);
            finishSuccessfulPurchase(client, now);
        }
    }

    private void prepareNamedSword(MinecraftClient client, long now) {
        PlayerInventory inventory = client.player.getInventory();
        if (boughtSwordInventoryIndex < 0 || boughtSwordInventoryIndex >= INVENTORY_SIZE
                || !inventory.getStack(boughtSwordInventoryIndex).isOf(Items.NETHERITE_SWORD)) {
            finishSuccessfulPurchase(client, now);
            return;
        }

        nameSourceInventoryIndex = boughtSwordInventoryIndex;
        previousHotbarSlot = inventory.getSelectedSlot();
        swappedForName = nameSourceInventoryIndex >= 9;
        if (swappedForName) {
            client.interactionManager.clickSlot(
                    client.player.playerScreenHandler.syncId,
                    screenSlot(nameSourceInventoryIndex),
                    previousHotbarSlot,
                    SlotActionType.SWAP,
                    client.player
            );
        } else {
            inventory.setSelectedSlot(nameSourceInventoryIndex);
        }
        step = Step.NAME_SEND;
        nextActionAt = now + 100L;
    }

    private void restoreNamedSword(MinecraftClient client) {
        if (client == null || client.player == null || client.interactionManager == null || nameSourceInventoryIndex < 0) {
            clearNameSwap();
            return;
        }
        if (swappedForName && previousHotbarSlot >= 0) {
            client.interactionManager.clickSlot(
                    client.player.playerScreenHandler.syncId,
                    screenSlot(nameSourceInventoryIndex),
                    previousHotbarSlot,
                    SlotActionType.SWAP,
                    client.player
            );
        }
        if (previousHotbarSlot >= 0) {
            client.player.getInventory().setSelectedSlot(previousHotbarSlot);
        }
        clearNameSwap();
    }

    private void startAnarchySwitch(MinecraftClient client, long now) {
        LOGGER.info(
                "AutoBuy: starting timed anarchy switch, list={}, cursor={}",
                anarchyIds,
                anarchyCursor
        );
        closeCurrentScreen(client);
        saleSinceAnarchy = false;
        if (lowBalanceGuardEnabled && lowBalanceMode && autoResellEnabled) {
            resellStorageOpenRequested = true;
        }
        pendingCandidateSlotId = -1;
        pendingPurchaseConfirmationSlotId = -1;
        step = Step.ANARCHY_SEND;
        nextActionAt = now + CLOSE_SCREEN_DELAY_MS;
    }

    private void processAnarchy(MinecraftClient client, long now) {
        if (now < nextActionAt) {
            return;
        }
        if (step == Step.ANARCHY_SEND) {
            if (anarchyIds.isEmpty()) {
                scheduleNextAnarchy(now);
                restartSearch(client, now);
                return;
            }
            String id = anarchyIds.get(anarchyCursor % anarchyIds.size());
            anarchyCursor = (anarchyCursor + 1) % anarchyIds.size();
            client.getNetworkHandler().sendChatCommand("an" + id);
            anarchyJoinWaitUntil = AuctionAccessGuard.blockAfterAnarchyJoin(now);
            scheduleNextAnarchy(anarchyJoinWaitUntil);
            pendingAnarchyAd = true;
            step = Step.ANARCHY_WAIT;
            nextActionAt = anarchyJoinWaitUntil;
            return;
        }
        if (step == Step.ANARCHY_WAIT) {
            restartSearch(client, now);
        }
    }

    private void startResell(MinecraftClient client, long now) {
        startResell(client, now, false);
    }

    private void startResell(MinecraftClient client, long now, boolean openOnly) {
        if (AuctionAccessGuard.isBlocked(now)) {
            nextActionAt = Math.max(nextActionAt, AuctionAccessGuard.readyAt());
            return;
        }
        boolean auctionOrStorageAlreadyOpen = client != null
                && client.currentScreen instanceof HandledScreen<?>
                && (isAuctionLikeScreen(client) || isConfirmedResellStorageScreen(client));
        boolean canUseOpenSwordSearch = !openOnly
                && !lowBalanceMode
                && !isBalanceBelowConfiguredPrice()
                && isSwordSearchScreenLikeRecentPurchases(client);
        // Keep an already opened AH/storage container. Closing it and sending
        // /ah again races the server's container updates and causes bots to
        // loop in the lobby or lose the relist state.
        if (!auctionOrStorageAlreadyOpen && !canUseOpenSwordSearch) {
            closeCurrentScreen(client);
        }
        pendingCandidateSlotId = -1;
        pendingCandidateClickAt = 0L;
        pendingPurchaseConfirmationSlotId = -1;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        resellAuctionSyncId = -1;
        resellStorageSyncId = -1;
        resellAhCommandAttempts = 0;
        resellStorageOpenedAt = 0L;
        resellOpenOnly = openOnly;
        resellReturnToSwordSearch = false;
        resellTriggeredByLowBalance = lowBalanceGuardEnabled
                && (lowBalanceMode || isBalanceBelowConfiguredPrice());
        // A low-balance storage check must relist a sword as soon as the
        // storage contents are stable.  `openOnly` is used for passive
        // verification during the normal timer cycle, but carrying it into
        // the low-balance path made the bot wait for nextResellAt instead of
        // clicking the relist button.
        if (resellTriggeredByLowBalance) {
            resellOpenOnly = false;
        }
        if (resellTriggeredByLowBalance && !lowBalanceResellNotificationSent) {
            lowBalanceResellNotificationSent = true;
            FluxVisualsClient.MODULE_MANAGER.getTelegram()
                    .sendLowBalanceResellMessage(lastKnownBalance, configuredSwordPrice());
        }
        if (isConfirmedResellStorageScreen(client)) {
            step = Step.RESELL_ACTION;
            resellStorageSyncId = client.player.currentScreenHandler.syncId;
            resellStorageOpenedAt = now;
        } else {
            step = auctionOrStorageAlreadyOpen || canUseOpenSwordSearch
                    ? Step.RESELL_STORAGE : Step.RESELL_SEND_AH;
        }
        nextActionAt = now + randomDelay(RESELL_CLOSE_DELAY_MIN_MS, RESELL_CLOSE_DELAY_MAX_MS);
        timeoutAt = now + RESELL_TIMEOUT_MS;
    }

    private void processResell(MinecraftClient client, long now) {
        if (step == Step.RESELL_WAIT_NEXT) {
            if (lowBalanceGuardEnabled && lowBalanceMode && now >= nextLowBalanceMoneyCheckAt) {
                refreshBalanceFromScoreboard(client, now);
                nextLowBalanceMoneyCheckAt = now + randomDelay(
                        LOW_BALANCE_MONEY_RECHECK_MIN_MS,
                        LOW_BALANCE_MONEY_RECHECK_MAX_MS
                );
            }
            if (shouldSwitchAnarchy(now)) {
                startAnarchySwitch(client, now);
                return;
            }
            if (now < nextResellAt) {
                if (isConfirmedResellStorageScreen(client)) {
                    // A delayed inventory update can populate a sword after
                    // the first storage snapshot was classified as empty.
                    // In low-balance mode that sword is actionable now; do
                    // not wait for the normal 60–65 second verification timer.
                    if (lowBalanceGuardEnabled && lowBalanceMode
                            && hasStorageNetheriteSword(client)) {
                        int currentSyncId = client.player.currentScreenHandler.syncId;
                        if (resellStorageSyncId != currentSyncId) {
                            resellStorageSyncId = currentSyncId;
                            resellStorageOpenedAt = now;
                        } else if (resellStorageOpenedAt <= 0L) {
                            resellStorageOpenedAt = now;
                        }
                        resellOpenOnly = false;
                        step = Step.RESELL_ACTION;
                        nextActionAt = now;
                        timeoutAt = now + RESELL_TIMEOUT_MS;
                    }
                    return;
                }
                if (isAuctionLikeScreen(client)) {
                    step = Step.RESELL_STORAGE;
                    nextActionAt = now;
                    timeoutAt = now + RESELL_TIMEOUT_MS;
                    return;
                }
                if (!(client.currentScreen instanceof HandledScreen<?>)) {
                    resellOpenOnly = true;
                    step = Step.RESELL_SEND_AH;
                    nextActionAt = now + randomDelay(RESELL_CLOSE_DELAY_MIN_MS, RESELL_CLOSE_DELAY_MAX_MS);
                    timeoutAt = now + RESELL_TIMEOUT_MS;
                }
                return;
            }
            resellOpenOnly = false;
            if (client.currentScreen instanceof HandledScreen<?> && isConfirmedResellStorageScreen(client)) {
                step = Step.RESELL_ACTION;
                nextActionAt = now;
                timeoutAt = now + RESELL_TIMEOUT_MS;
            } else if (isAuctionLikeScreen(client)) {
                step = Step.RESELL_STORAGE;
                nextActionAt = now;
                timeoutAt = now + RESELL_TIMEOUT_MS;
            } else {
                step = Step.RESELL_SEND_AH;
                nextActionAt = now + randomDelay(RESELL_CLOSE_DELAY_MIN_MS, RESELL_CLOSE_DELAY_MAX_MS);
                timeoutAt = now + RESELL_TIMEOUT_MS;
            }
            return;
        }
        if (step == Step.RESELL_FINISH) {
            if (now >= nextActionAt) {
                if (lowBalanceGuardEnabled && isBalanceBelowConfiguredPrice()) {
                    LOGGER.info("AutoBuy: resale completed, staying on auction in low-balance mode");
                    step = Step.RESELL_WAIT_NEXT;
                    nextResellAt = now + randomDelay(LOW_BALANCE_MONEY_RECHECK_MIN_MS, LOW_BALANCE_MONEY_RECHECK_MAX_MS);
                    nextActionAt = now + LOW_BALANCE_IDLE_DELAY_MS;
                    timeoutAt = now + RESELL_TIMEOUT_MS;
                } else {
                    LOGGER.info("AutoBuy: resale completed, returning to auction search");
                    closeCurrentScreen(client);
                    restartSearch(client, now);
                }
            }
            return;
        }
        if (now < nextActionAt) {
            return;
        }
        if (now >= timeoutAt) {
            if (rentalPending) {
                rentalPending = false;
                rentalClicks = 0;
                nextRentalAt = now + RENTAL_INTERVAL_MS;
                persistRentalAt();
                debug("rental flow timed out; suppressing repeated storage loop for one hour");
            }
            restartSearch(client, now);
            return;
        }
        if (step == Step.RESELL_SEND_AH) {
            if (isAuctionLikeScreen(client) || isConfirmedResellStorageScreen(client)) {
                step = Step.RESELL_STORAGE;
                nextActionAt = now;
                return;
            }
            client.getNetworkHandler().sendChatCommand("ah");
            resellAhCommandAttempts = 1;
            step = Step.RESELL_STORAGE;
            nextActionAt = now + randomDelay(RESELL_AH_RETRY_MIN_MS, RESELL_AH_RETRY_MAX_MS);
            timeoutAt = now + RESELL_TIMEOUT_MS;
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            if (step == Step.RESELL_STORAGE
                    && now >= nextActionAt
                    && resellAhCommandAttempts < RESELL_MAX_AH_COMMAND_ATTEMPTS) {
                client.getNetworkHandler().sendChatCommand("ah");
                resellAhCommandAttempts++;
                nextActionAt = now + randomDelay(RESELL_AH_RETRY_MIN_MS, RESELL_AH_RETRY_MAX_MS);
            }
            return;
        }
        if (step == Step.RESELL_STORAGE) {
            int auctionSyncId = client.player.currentScreenHandler.syncId;
            // Do not use a menu slot discovered from an old AH handler after
            // the server has already replaced the container.
            if (resellAuctionSyncId >= 0 && resellAuctionSyncId != auctionSyncId) {
                resellAuctionSyncId = auctionSyncId;
                return;
            }
            if (clickFirstMenuNamed(client, "\u0445\u0440\u0430\u043d\u0438\u043b\u0438\u0449", "storage")) {
                resellAuctionSyncId = auctionSyncId;
                resellStorageSyncId = -1;
                step = Step.RESELL_ACTION;
                long delay = randomDelay(AFTER_STORAGE_DELAY_MIN_MS, AFTER_STORAGE_DELAY_MAX_MS);
                nextActionAt = now + delay;
                timeoutAt = now + SCREEN_TIMEOUT_MS + delay;
            }
            return;
        }
        if (step == Step.RESELL_ACTION) {
            if (resellConfirmationPending) {
                return;
            }
            if (rentalPending) {
                if (!(client.currentScreen instanceof HandledScreen<?>)) {
                    return;
                }
                if (rentalClicks == 0 && clickRentalMenu(client, 0)) {
                    rentalClicks = 1;
                    nextActionAt = now + randomDelay(700L, 1_100L);
                    return;
                }
                if (rentalClicks == 1 && clickRentalMenu(client, 1)) {
                    rentalClicks = 2;
                    nextActionAt = now + randomDelay(700L, 1_100L);
                    return;
                }
                if (rentalClicks == 2 && clickRentalMenu(client, 2)) {
                    rentalClicks = 3;
                    nextActionAt = now + randomDelay(700L, 1_100L);
                    return;
                }
                if (rentalClicks == 3 && clickRentalMenu(client, 3)) {
                    finishRentalAndResume(client, now);
                    return;
                }
                return;
            }
            if (!isConfirmedResellStorageScreen(client)) {
                resellStorageSyncId = -1;
                resellStorageOpenedAt = 0L;
                return;
            }
            int storageSyncId = client.player.currentScreenHandler.syncId;
            if (resellStorageSyncId < 0) {
                // The storage screen can arrive one or more ticks after the
                // click on the AH menu. Start the stability window only for
                // the currently visible handler.
                resellStorageSyncId = storageSyncId;
            } else if (resellStorageSyncId != storageSyncId) {
                // A stale screen or a server-side container replacement must
                // never be allowed to drive a click using old slot data.
                debug("resell storage sync changed from " + resellStorageSyncId
                        + " to " + storageSyncId + "; restarting stability wait");
                resellStorageSyncId = storageSyncId;
                resellStorageOpenedAt = now;
                return;
            }
            if (resellStorageOpenedAt <= 0L) {
                resellStorageOpenedAt = now;
            }
            // Inventory packets arrive after the storage screen itself. Do
            // not mistake an only partially populated storage for an empty
            // one and jump directly into rental before reselling swords.
            if (now - resellStorageOpenedAt < RESELL_STORAGE_STABILITY_MS) {
                return;
            }
            if (isBalanceBelowConfiguredPrice() && !hasStorageNetheriteSword(client)) {
                storageCheckPending = false;
                leaveHubAndDisable(client);
                return;
            }
            if (!hasStorageNetheriteSword(client)) {
                if (rentalSlotsEnabled && (nextRentalAt <= 0L || now >= nextRentalAt)) {
                    rentalPending = true;
                    rentalClicks = 0;
                    nextActionAt = now + randomDelay(500L, 900L);
                    timeoutAt = now + RESELL_TIMEOUT_MS;
                    return;
                }
                auctionListingsKnownPresent = false;
                auctionListingsStateKnown = true;
                nextStorageVerificationAt = now + UNKNOWN_STORAGE_RECHECK_MS;
                nextResellAt = autoResellEnabled ? nextStorageVerificationAt : 0L;
                if (autoResellEnabled && isBalanceBelowConfiguredPrice()) {
                    // Keep the storage/AH container alive. Inventory updates
                    // can arrive shortly after the first screen packet.
                    step = Step.RESELL_WAIT_NEXT;
                    nextActionAt = nextResellAt;
                    timeoutAt = 0L;
                } else if (resellReturnToSwordSearch || !isBalanceBelowConfiguredPrice()) {
                    closeCurrentScreen(client);
                    restartSearch(client, now);
                } else {
                    step = Step.RESELL_FINISH;
                    nextActionAt = now + randomDelay(AFTER_RESELL_DELAY_MIN_MS, AFTER_RESELL_DELAY_MAX_MS);
                    timeoutAt = nextActionAt + SCREEN_TIMEOUT_MS;
                }
                LOGGER.info("AutoBuy: storage check found no sword, retrying in 120 seconds");
                return;
            }
            auctionListingsKnownPresent = true;
            auctionListingsStateKnown = true;
            if (resellOpenOnly && now < nextResellAt) {
                resellOpenOnly = false;
                if (lowBalanceGuardEnabled && lowBalanceMode) {
                    step = Step.RESELL_WAIT_NEXT;
                    nextActionAt = nextResellAt;
                    timeoutAt = 0L;
                } else {
                    step = Step.RESELL_FINISH;
                    long delay = randomDelay(AFTER_RESELL_DELAY_MIN_MS, AFTER_RESELL_DELAY_MAX_MS);
                    nextActionAt = now + delay;
                    timeoutAt = now + delay + SCREEN_TIMEOUT_MS;
                }
                return;
            }
            // Revalidate both the handler and the sword immediately before
            // clicking: inventory packets may replace the screen after the
            // stability check but before this tick's action.
            if (client.player.currentScreenHandler.syncId != resellStorageSyncId
                    || !isConfirmedResellStorageScreen(client)
                    || !hasStorageNetheriteSword(client)) {
                return;
            }
            if (clickFirstMenuNamed(client, resellStorageSyncId,
                    "\u043f\u0435\u0440\u0435\u0432\u044b\u0441\u0442\u0430\u0432", "resell")) {
                storageCheckPending = false;
                sessionResellCount++;
                LOGGER.info("AutoBuy: resale action submitted");
                resellTriggeredByLowBalance = false;
                resellConfirmationPending = true;
                rentalAfterResell = rentalSlotsEnabled && (nextRentalAt <= 0L || now >= nextRentalAt);
                nextActionAt = now;
                timeoutAt = now + RESELL_TIMEOUT_MS;
                return;
            }
        }
        if (step == Step.RESELL_RETURN || step == Step.RESELL_RETURN_WAIT) {
            closeCurrentScreen(client);
            restartSearch(client, now);
            return;
        }
    }

    private void openSearch(MinecraftClient client, long now) {
        if (AuctionAccessGuard.isBlocked(now)) {
            step = Step.SEARCH_DELAY;
            nextActionAt = AuctionAccessGuard.readyAt();
            timeoutAt = nextActionAt + SCREEN_TIMEOUT_MS;
            return;
        }
        if (lowBalanceGuardEnabled && (lowBalanceMode || isBalanceBelowConfiguredPrice())) {
            enterLowBalanceMode(client, now, true);
            return;
        }
        client.getNetworkHandler().sendChatCommand(SEARCH_COMMAND);
        searchOpenedThisSession = true;
        pendingCandidateSlotId = -1;
        pendingPurchaseConfirmationSlotId = -1;
        step = Step.WAIT_AUCTION;
        timeoutAt = now + SCREEN_TIMEOUT_MS;
        resetAuctionPageCycle(now, true);
        nextPurchaseAttemptAt = 0L;
        auctionScreenMissingSince = now;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
    }

    private void restartSearch(MinecraftClient client, long now) {
        debug("restart search; previous step=" + step);
        resellConfirmationPending = false;
        rentalAfterResell = false;
        if (lowBalanceGuardEnabled && (lowBalanceMode || isBalanceBelowConfiguredPrice())) {
            enterLowBalanceMode(client, now, false);
            return;
        }
        if (sessionStartedAt > 0L && searchOpenedThisSession) {
            sessionSearchReopenCount++;
        }
        searchOpenedThisSession = false;
        closeCurrentScreen(client);
        pendingCandidateSlotId = -1;
        pendingPurchase = null;
        pendingPurchaseConfirmationSlotId = -1;
        boughtSwordInventoryIndex = -1;
        resetPurchaseVerification();
        step = Step.SEARCH_DELAY;
        nextActionAt = now + CLOSE_SCREEN_DELAY_MS;
        timeoutAt = now + CLOSE_SCREEN_DELAY_MS + SCREEN_TIMEOUT_MS;
        auctionScreenMissingSince = 0L;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        nextPurchaseAttemptAt = 0L;
        resetAuctionPageCycle(0L, false);
    }

    private void finishSuccessfulPurchase(MinecraftClient client, long now) {
        debug("purchase completed; queueing AutoSell");
        purchasesSinceExtendedPause++;
        // Publish the sell request before changing the search state. Telegram can
        // process the request on the next tick even when the session handler exits early.
        if (!MultiBotManager.isBotContext()) {
            FluxVisualsClient.MODULE_MANAGER.getTelegram().queueAutoSellAfterPurchase();
        }
        restartSearch(client, now);
        if (step == Step.SEARCH_DELAY) {
            boolean extendedPause = purchasesSinceExtendedPause >= purchasesUntilExtendedPause;
            long delay = extendedPause
                    ? randomDelay(EXTENDED_PURCHASE_PAUSE_MIN_MS, EXTENDED_PURCHASE_PAUSE_MAX_MS)
                    : randomDelay(AFTER_PURCHASE_DELAY_MIN_MS, AFTER_PURCHASE_DELAY_MAX_MS);
            if (extendedPause) {
                resetExtendedPurchasePauseCycle();
            }
            nextActionAt = now + delay;
            timeoutAt = nextActionAt + SCREEN_TIMEOUT_MS;
        }
    }

    private void resetExtendedPurchasePauseCycle() {
        purchasesSinceExtendedPause = 0;
        purchasesUntilExtendedPause = ThreadLocalRandom.current().nextInt(
                EXTENDED_PAUSE_PURCHASE_MIN,
                EXTENDED_PAUSE_PURCHASE_MAX + 1
        );
    }

    private void processCompassAnarchy(MinecraftClient client) {
        if (!isEnabled() || !ready(client)) {
            compassHeldLastTick = false;
            return;
        }
        boolean compassHeld = client.player.getMainHandStack().isOf(Items.COMPASS)
                || client.player.getOffHandStack().isOf(Items.COMPASS);
        if (!compassHeld) {
            compassHeldLastTick = false;
            return;
        }
        if (compassHeldLastTick) {
            return;
        }
        Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
        if (anarchyIds.isEmpty()) {
            compassHeldLastTick = true;
            telegram.sendNotification("⚠️ В руке появился компас, но список анархий AutoBuy пуст.");
            return;
        }
        if (isExternalMaintenanceWorking() || !canStartImmediateAnarchy()) {
            return;
        }
        if (startImmediateAnarchyFromList(client)) {
            compassHeldLastTick = true;
            telegram.sendNotification("🧭 Компас обнаружен в руке.\n"
                    + "🌐 Перехожу на следующую анархию из списка AutoBuy.");
        }
    }

    private void enterLowBalanceMode(MinecraftClient client, long now, boolean forceStorageCheck) {
        lowBalanceMode = true;
        pendingCandidateSlotId = -1;
        pendingPurchase = null;
        pendingPurchaseConfirmationSlotId = -1;
        boughtSwordInventoryIndex = -1;
        resetPurchaseVerification();
        auctionScreenMissingSince = 0L;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        resellAuctionSyncId = -1;
        nextPurchaseAttemptAt = 0L;
        step = Step.LOW_BALANCE_WAIT;
        nextActionAt = now + LOW_BALANCE_IDLE_DELAY_MS;
        nextLowBalanceMoneyCheckAt = now + randomDelay(
                LOW_BALANCE_MONEY_RECHECK_MIN_MS,
                LOW_BALANCE_MONEY_RECHECK_MAX_MS
        );
        timeoutAt = 0L;
        if (forceStorageCheck) {
            storageCheckPending = true;
            nextResellAt = now;
        }
    }

    private void requestMoneyCheck(MinecraftClient client, long now, MoneyCheckReason reason) {
        closeCurrentScreen(client);
        pendingCandidateSlotId = -1;
        pendingPurchase = null;
        pendingPurchaseConfirmationSlotId = -1;
        resetPurchaseVerification();
        auctionScreenMissingSince = 0L;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        nextPurchaseAttemptAt = 0L;
        moneyCheckPending = true;
        moneyCheckStartedAt = now;
        moneyCheckReason = reason;
        step = Step.WAIT_MONEY;
        nextActionAt = now;
        timeoutAt = now + MONEY_RESPONSE_TIMEOUT_MS;
        refreshBalanceFromScoreboard(client, now);
    }

    /**
     * Reads the balance from the active connection's sidebar.  FunTime puts
     * the label "Монеты" in the score-holder text and the actual amount in
     * that entry's numeric scoreboard value (the number rendered on the
     * right-hand side of the line).
     */
    private void refreshBalanceFromScoreboard(MinecraftClient client, long now) {
        Long balance = readScoreboardBalance(client);
        // Scoreboard update packets can briefly expose an entry with value 0
        // before the real score arrives. Never overwrite a known positive
        // balance with that transient value.
        if (balance != null && balance > 0L) {
            updateKnownBalance(balance, client, now);
        }
    }

    public void refreshBalanceFromScoreboardNow(MinecraftClient client) {
        refreshBalanceFromScoreboard(client, System.currentTimeMillis());
    }

    public static Long readScoreboardBalance(MinecraftClient client) {
        if (client == null || client.world == null) {
            return null;
        }
        return readScoreboardBalance(client.world);
    }

    public static Long readScoreboardBalance(net.minecraft.world.World world) {
        if (world == null) {
            return null;
        }
        Scoreboard scoreboard = world.getScoreboard();
        if (scoreboard == null) {
            return null;
        }
        ScoreboardDisplaySlot[] slots = {
                ScoreboardDisplaySlot.SIDEBAR,
                ScoreboardDisplaySlot.LIST
        };
        for (ScoreboardDisplaySlot slot : slots) {
            var objective = scoreboard.getObjectiveForSlot(slot);
            if (objective == null) {
                continue;
            }
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                String line = entry.name() == null ? "" : entry.name().getString();
                var team = scoreboard.getScoreHolderTeam(entry.owner());
                if (team != null) {
                    line = Team.decorateName(team, entry.name()).getString();
                }
                String display = entry.display() == null ? "" : entry.display().getString();
                String formatted = entry.formatted(
                        objective.getNumberFormatOr(StyledNumberFormat.RED)
                ).getString();
                String combined = (line + " " + display + " " + formatted).trim();
                if (!containsMoneyLabel(combined)) {
                    continue;
                }

                Long parsed = extractScoreboardAmount(combined);
                if (parsed != null) {
                    return parsed;
                }
                long score = entry.value();
                if (score > 0L) {
                    return score;
                }
            }
        }
        for (var objective : scoreboard.getObjectives()) {
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                String line = entry.name() == null ? "" : entry.name().getString();
                var team = scoreboard.getScoreHolderTeam(entry.owner());
                if (team != null) {
                    line = Team.decorateName(team, entry.name()).getString();
                }
                String display = entry.display() == null ? "" : entry.display().getString();
                String formatted = entry.formatted(
                        objective.getNumberFormatOr(StyledNumberFormat.RED)
                ).getString();
                String combined = (line + " " + display + " " + formatted).trim();
                if (!containsMoneyLabel(combined)) {
                    continue;
                }
                Long parsed = extractScoreboardAmount(combined);
                if (parsed != null) {
                    return parsed;
                }
                long score = entry.value();
                if (score > 0L) {
                    return score;
                }
            }
        }
        return null;
    }

    public static Long extractScoreboardAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = SCOREBOARD_NUMBER_PATTERN.matcher(text);
        Long best = null;
        int bestDigits = -1;
        while (matcher.find()) {
            String candidate = matcher.group();
            int digits = 0;
            for (int i = 0; i < candidate.length(); i++) {
                if (Character.isDigit(candidate.charAt(i))) {
                    digits++;
                }
            }
            Long parsed = parseMoneyAmount(candidate);
            if (parsed != null && digits > bestDigits) {
                best = parsed;
                bestDigits = digits;
            }
        }
        return best;
    }

    public static boolean containsMoneyLabel(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("монет")
                || normalized.contains("баланс")
                || normalized.contains("деньг")
                || normalized.contains("balance")
                || normalized.contains("$");
    }

    private void updateKnownBalance(Long balance, MinecraftClient client, long now) {
        if (balance == null || balance < 0L) {
            return;
        }
        boolean wasLowBalance = lowBalanceMode;
        if (sessionStartBalance < 0L) {
            sessionStartBalance = balance;
        }
        lastKnownBalance = balance;
        moneyCheckPending = false;
        boolean balanceBelowPrice = isBalanceBelowConfiguredPrice();
        if (lowBalanceGuardEnabled && balanceBelowPrice) {
            hubSafetyStorageCheckPending = false;
            lowBalanceMode = true;
            if (isEnabled() && !lowBalanceNotificationSent) {
                lowBalanceNotificationSent = true;
                FluxVisualsClient.MODULE_MANAGER.getTelegram()
                        .sendLowBalanceMessage(lastKnownBalance, configuredSwordPrice());
            }
            if (client != null && ready(client) && !isAnarchyStep(step)
                    && !isResellStep(step) && step != Step.WAIT_MONEY) {
                enterLowBalanceMode(client, now, true);
            }
        } else {
            lowBalanceMode = false;
            storageCheckPending = false;
            hubSafetyStorageCheckPending = false;
            if (isEnabled() && wasLowBalance && lowBalanceNotificationSent) {
                FluxVisualsClient.MODULE_MANAGER.getTelegram().sendBalanceRecoveredMessage(lastKnownBalance);
            }
            resetLowBalanceNotifications();
        }
    }

    private boolean shouldSwitchAnarchy(long now) {
        return anarchySwitchEnabled
                && nextAnarchyAt > 0L
                && now >= nextAnarchyAt
                && !anarchyIds.isEmpty();
    }

    private boolean shouldResell(long now) {
        return (rentalSlotsEnabled && (nextRentalAt <= 0L || now >= nextRentalAt))
                || (autoResellEnabled
                && ((auctionListingsStateKnown && auctionListingsKnownPresent
                        && nextResellAt > 0L && now >= nextResellAt)
                || (!auctionListingsStateKnown && nextStorageVerificationAt > 0L
                        && now >= nextStorageVerificationAt)));
    }

    private void scheduleNextAnarchy(long now) {
        if (!anarchySwitchEnabled) {
            nextAnarchyAt = 0L;
            return;
        }
        nextAnarchyAt = now + randomDelay(anarchyDelayMinMs, anarchyDelayMaxMs);
    }

    private void scheduleNextResell(long now) {
        nextResellAt = autoResellEnabled && auctionListingsKnownPresent
                ? now + randomDelay(resellIntervalMinMs, resellIntervalMaxMs)
                : 0L;
        if (autoResellEnabled && !auctionListingsStateKnown) {
            nextStorageVerificationAt = now;
        }
    }

    private void markOldestRecentPurchaseSold() {
        for (int index = recentPurchases.size() - 1; index >= 0; index--) {
            RecentPurchase purchase = recentPurchases.get(index);
            if (soldRecentPurchaseTimes.add(purchase.purchasedAt())) {
                return;
            }
        }
    }

    private void pauseForClickGui(MinecraftClient client) {
        restoreNamedSword(client);
        resetRuntime(client);
        step = Step.WAIT_CLICK_GUI_CLOSE;
    }

    private void resetRuntime(MinecraftClient client) {
        restoreNamedSword(client);
        pendingCandidateSlotId = -1;
        pendingPurchase = null;
        pendingPurchaseConfirmationSlotId = -1;
        boughtSwordInventoryIndex = -1;
        resetPurchaseVerification();
        nextActionAt = 0L;
        timeoutAt = 0L;
        resetAuctionPageCycle(0L, false);
        nextPurchaseAttemptAt = 0L;
        auctionScreenMissingSince = 0L;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        resellAuctionSyncId = -1;
        resellStorageSyncId = -1;
        resellAhCommandAttempts = 0;
        resellStorageOpenedAt = 0L;
        externalPauseUntil = 0L;
        externalResumeAuctionScreen = false;
        lowBalanceMode = false;
        storageCheckPending = false;
        resellStorageOpenRequested = false;
        resellOpenOnly = false;
        resellReturnToSwordSearch = false;
        hubSafetyStorageCheckPending = false;
        resellTriggeredByLowBalance = false;
        rentalPending = false;
        rentalClicks = 0;
        resellConfirmationPending = false;
        rentalAfterResell = false;
        saleSinceAnarchy = false;
        lastKnownBalance = -1L;
        moneyCheckPending = false;
        moneyCheckStartedAt = 0L;
        nextLowBalanceMoneyCheckAt = 0L;
        moneyCheckReason = MoneyCheckReason.NONE;
        step = Step.IDLE;
    }

    private void resetLowBalanceNotifications() {
        lowBalanceNotificationSent = false;
        lowBalanceResellNotificationSent = false;
    }

    private void clearAnarchyJoinWait() {
        anarchyJoinWaitUntil = 0L;
        pendingAnarchyAd = false;
    }

    private void resetPurchaseVerification() {
        observedPurchaseInventoryIndex = -1;
        observedPurchaseAt = 0L;
        purchaseConfirmedByChat = false;
        purchaseNotificationSent = false;
    }

    private void resetSessionStats(long now) {
        sessionStartedAt = now;
        sessionStoppedAt = 0L;
        sessionPurchaseCount = 0;
        sessionKnownPricePurchaseCount = 0;
        sessionPurchaseSpent = 0L;
        sessionSaleCount = 0;
        sessionSaleRevenue = 0L;
        sessionResellCount = 0;
        sessionPageTransitionCount = 0;
        sessionPageErrorCount = 0;
        sessionSearchReopenCount = 0;
        sessionPageLatencyTotalMs = 0L;
        sessionPageLatencySampleCount = 0;
        sessionLastPageConfirmTimeoutMs = 0L;
        pageLatencyEstimateMs = PAGE_CONFIRM_INITIAL_LATENCY_MS;
        searchOpenedThisSession = false;
    }

    private void recordSuccessfulPurchase(PurchaseCandidate purchase, ItemStack purchasedStack) {
        if (purchasedStack != null && purchasedStack.isOf(Items.NETHERITE_SWORD)) {
            String seller = purchase == null ? null : purchase.seller();
            long price = purchase == null ? -1L : purchase.price();
            ItemStack displayStack = purchase != null && purchase.stack().isOf(Items.NETHERITE_SWORD)
                    ? purchase.stack()
                    : purchasedStack;
            List<Text> tooltip = purchase == null || purchase.tooltip().isEmpty()
                    ? snapshotTooltip(purchasedStack)
                    : purchase.tooltip();
            recentPurchases.add(0, new RecentPurchase(
                    displayStack,
                    tooltip,
                    price,
                    seller,
                    System.currentTimeMillis()
            ));
            while (recentPurchases.size() > RECENT_PURCHASE_LIMIT) {
                RecentPurchase removed = recentPurchases.remove(recentPurchases.size() - 1);
                soldRecentPurchaseTimes.remove(removed.purchasedAt());
            }
        }
        if (sessionStartedAt <= 0L) {
            return;
        }
        sessionPurchaseCount++;
        if (purchase == null || purchase.price() < 0L) {
            return;
        }
        sessionKnownPricePurchaseCount++;
        sessionPurchaseSpent = saturatedStatAdd(sessionPurchaseSpent, purchase.price());
    }

    private void sendPurchaseNotification() {
        Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
        if (!telegram.isEnabled()) {
            pendingPurchase = null;
            return;
        }
        PurchaseCandidate purchase = pendingPurchase;
        pendingPurchase = null;
        if (purchase == null) {
            telegram.sendPurchaseMessage("Незеритовый меч", -1L, null);
            return;
        }
        telegram.sendPurchaseMessage(purchase.name(), purchase.price(), purchase.seller());
    }

    private void snapshotSwords(PlayerInventory inventory) {
        for (int index = 0; index < INVENTORY_SIZE; index++) {
            swordsBeforePurchase[index] = inventory.getStack(index).isOf(Items.NETHERITE_SWORD);
        }
    }

    private int findAddedSword(PlayerInventory inventory) {
        for (int index = 0; index < INVENTORY_SIZE; index++) {
            if (!swordsBeforePurchase[index] && inventory.getStack(index).isOf(Items.NETHERITE_SWORD)) {
                return index;
            }
        }
        return -1;
    }

    private boolean clickFirstMenuNamed(MinecraftClient client, String russianHint, String englishHint) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(russianHint) || name.contains(englishHint)) {
                clickMenuSlot(client, slot.id);
                return true;
            }
        }
        return false;
    }

    private boolean clickFirstMenuNamed(
            MinecraftClient client,
            int expectedSyncId,
            String russianHint,
            String englishHint
    ) {
        if (client.player == null || client.interactionManager == null
                || !(client.currentScreen instanceof HandledScreen<?> handledScreen)
                || client.player.currentScreenHandler == null) {
            return false;
        }
        var screenHandler = client.player.currentScreenHandler;
        if (screenHandler.syncId != expectedSyncId || handledScreen.getScreenHandler() != screenHandler) {
            return false;
        }
        for (Slot slot : screenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (!name.contains(russianHint) && !name.contains(englishHint)) {
                continue;
            }
            if (client.currentScreen != handledScreen
                    || client.player.currentScreenHandler != screenHandler
                    || screenHandler.syncId != expectedSyncId
                    || handledScreen.getScreenHandler() != screenHandler) {
                return false;
            }
            client.interactionManager.clickSlot(
                    expectedSyncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
            return true;
        }
        return false;
    }

    /** Strict rental navigation: root -> exactly 5 slots -> priced carrot -> buy. */
    private boolean clickRentalMenu(MinecraftClient client, int stage) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            boolean match;
            if (stage == 0) {
                match = (name.contains("\u0430\u0440\u0435\u043d\u0434") || name.contains("rent"))
                        && (name.contains("\u0441\u043b\u043e\u0442") || name.contains("slot"))
                        && !name.matches(".*\\d.*");
            } else if (stage == 1) {
                match = (name.contains("\u0430\u0440\u0435\u043d\u0434") || name.contains("rent"))
                        && (name.contains("\u0441\u043b\u043e\u0442") || name.contains("slot"))
                        && name.matches(".*(^|\\D)5(\\D|$).*");
            } else if (stage == 2) {
                match = stack.isOf(Items.GOLDEN_CARROT) && hasRentalPriceTooltip(stack);
            } else {
                match = name.contains("\u043a\u0443\u043f\u0438\u0442") || name.contains("buy");
            }
            if (match) {
                clickMenuSlot(client, slot.id);
                return true;
            }
        }
        return false;
    }

    private static boolean hasRentalPriceTooltip(ItemStack stack) {
        for (Text line : snapshotTooltip(stack)) {
            String value = line.getString().toLowerCase(Locale.ROOT);
            String digits = value.replaceAll("[^0-9]", "");
            if ((value.contains("\u0446\u0435\u043d") || value.contains("price"))
                    && digits.contains("1500000")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRentalPurchaseSuccessMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
        return normalized.contains("успешная покупка")
                || normalized.contains("аренда успешно")
                || normalized.contains("слоты успешно арендованы")
                || normalized.contains("purchase successful");
    }

    private static boolean isResellSuccessMessage(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("\u0443\u0441\u043f\u0435\u0448\u043d\u043e \u043f\u0435\u0440\u0435\u0432\u044b\u0441\u0442\u0430\u0432\u043b\u0435\u043d")
                || normalized.contains("successfully relisted")
                || normalized.contains("successfully re-listed");
    }

    private void finishConfirmedResell(MinecraftClient client, long now) {
        resellConfirmationPending = false;
        if (rentalAfterResell && rentalSlotsEnabled) {
            rentalAfterResell = false;
            rentalPending = true;
            rentalClicks = 0;
            nextActionAt = now + randomDelay(500L, 900L);
            timeoutAt = now + RESELL_TIMEOUT_MS;
            debug("resell confirmed; starting rental");
            return;
        }
        rentalAfterResell = false;
        scheduleNextResell(now);
        closeCurrentScreen(client);
        if (lowBalanceGuardEnabled && lowBalanceMode) {
            step = Step.RESELL_WAIT_NEXT;
            nextActionAt = nextResellAt > 0L ? nextResellAt : now + randomDelay(RESELL_INTERVAL_MIN_MS, RESELL_INTERVAL_MAX_MS);
            timeoutAt = 0L;
        } else {
            restartSearch(client, now);
        }
    }

    private void finishRentalAndResume(MinecraftClient client, long now) {
        rentalPending = false;
        rentalClicks = 0;
        nextRentalAt = now + RENTAL_INTERVAL_MS;
        persistRentalAt();
        storageCheckPending = false;
        resellStorageOpenRequested = false;
        resellOpenOnly = false;
        resellStorageOpenedAt = 0L;
        nextStorageVerificationAt = now + UNKNOWN_STORAGE_RECHECK_MS;
        scheduleNextResell(now);
        if (autoResellEnabled) {
            step = Step.RESELL_WAIT_NEXT;
            nextActionAt = nextResellAt > 0L ? nextResellAt : now + UNKNOWN_STORAGE_RECHECK_MS;
            timeoutAt = 0L;
            return;
        }
        debug("rental completed; resuming auction search");
        if (client != null && ready(client)) {
            restartSearch(client, now);
        } else {
            step = Step.SEARCH_DELAY;
            nextActionAt = now + CLOSE_SCREEN_DELAY_MS;
            timeoutAt = nextActionAt + SCREEN_TIMEOUT_MS;
        }
    }

    private static Path rentalTimersPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals-rental-timers.properties");
    }

    private long readPersistedRentalAt(String key) {
        try {
            Path path = rentalTimersPath();
            if (!Files.exists(path)) return 0L;
            Properties properties = new Properties();
            try (var input = Files.newInputStream(path)) { properties.load(input); }
            return Long.parseLong(properties.getProperty("rental." + key, "0"));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void persistRentalAt() {
        if (rentalPersistenceKey == null) return;
        try {
            Path path = rentalTimersPath();
            Properties properties = new Properties();
            if (Files.exists(path)) {
                try (var input = Files.newInputStream(path)) { properties.load(input); }
            }
            properties.setProperty("rental." + rentalPersistenceKey, Long.toString(nextRentalAt));
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(path)) { properties.store(output, "FluxVisuals rental timers"); }
        } catch (Exception ignored) {
            LOGGER.debug("AutoBuy: failed to persist rental timer", ignored);
        }
    }

    private boolean hasAuctionPageButton(MinecraftClient client, boolean nextPage) {
        return findAuctionPageButton(client, nextPage) != null;
    }

    private Slot findAuctionPageButton(MinecraftClient client, boolean nextPage) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return null;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isOf(Items.NETHERITE_SWORD)) {
                continue;
            }
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            boolean russianMatch = name.contains(nextPage
                    ? "\u0441\u043b\u0435\u0434\u0443\u044e\u0449"
                    : "\u043f\u0440\u0435\u0434\u044b\u0434\u0443\u0449")
                    && name.contains("\u0441\u0442\u0440\u0430\u043d\u0438\u0446");
            boolean englishMatch = name.contains(nextPage ? "next" : "previous") && name.contains("page");
            if (!russianMatch && !englishMatch) {
                continue;
            }
            return slot;
        }
        return null;
    }

    private boolean clickAuctionPageButton(MinecraftClient client, boolean nextPage) {
        Slot slot = findAuctionPageButton(client, nextPage);
        if (slot == null) {
            return false;
        }
        clickMenuSlot(client, slot.id);
        return true;
    }

    private boolean hasNamedSlot(MinecraftClient client, String russianHint, String englishHint) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(russianHint) || name.contains(englishHint)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStorageNetheriteSword(MinecraftClient client) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            if (slot.getStack().isOf(Items.NETHERITE_SWORD)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStorageScreen(MinecraftClient client) {
        if (client.currentScreen == null) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("\u0445\u0440\u0430\u043d\u0438\u043b\u0438\u0449")
                || title.contains("storage");
    }

    private static boolean isExternalSellScreen(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("sellgui")
                || title.contains("sell gui")
                || title.contains("\u043f\u0440\u043e\u0434\u0430\u0436")
                || title.contains("\u0432\u044b\u0441\u0442\u0430\u0432");
    }

    private boolean isAuctionLikeScreen(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("\u0430\u0443\u043a")
                || title.contains("auction")
                || title.equals("ah")
                || title.startsWith("ah ");
    }

    private boolean isConfirmedResellStorageScreen(MinecraftClient client) {
        if (client.currentScreen == null || client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("\u0445\u0440\u0430\u043d\u0438\u043b\u0438\u0449") || title.contains("storage");
    }

    private boolean isSwordSearchScreenLikeRecentPurchases(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("\u043f\u043e\u043a\u0443\u043f")
                || title.contains("buy")
                || title.contains("confirm")) {
            return false;
        }
        return isAuctionSearchScreen(client);
    }

    private boolean hasVisibleAuctionNetheriteSword(MinecraftClient client) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot != null
                    && slot.hasStack()
                    && slot.inventory != client.player.getInventory()
                    && slot.getStack().isOf(Items.NETHERITE_SWORD)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBalanceBelowConfiguredPrice() {
        long price = configuredSwordPrice();
        return price > 0L && lastKnownBalance >= 0L && lastKnownBalance < price;
    }

    private long configuredSwordPrice() {
        return Math.max(0L, FluxVisualsClient.MODULE_MANAGER.getItemResorter().getMaxPrice());
    }

    private void leaveHubAndDisable(MinecraftClient client) {
        long price = configuredSwordPrice();
        LOGGER.info("AutoBuy: balance {} is below sword price {}, entering low-balance idle check",
                lastKnownBalance, price);
        storageCheckPending = false;
        resellStorageOpenRequested = false;
        enterLowBalanceMode(client, System.currentTimeMillis(), false);
    }

    private boolean isAuctionSearchScreen(MinecraftClient client) {
        if (client == null || client.currentScreen == null) {
            return false;
        }
        if (isStorageScreen(client) || isExternalSellScreen(client)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        boolean titleMatches = title.contains("\u043f\u043e\u0438\u0441\u043a")
                || title.contains("search")
                || title.contains("\u043d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0439 \u043c\u0435\u0447")
                || title.contains("netherite sword");
        if (titleMatches) {
            return true;
        }
        return isAuctionLikeScreen(client) && hasNamedSlot(client, "\u043e\u0431\u043d\u043e\u0432", "refresh");
    }

    private boolean clickPurchaseConfirmation(MinecraftClient client) {
        if (client.player == null || client.player.currentScreenHandler == null
                || !isPurchaseConfirmationScreen(client)) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if ((name.contains("\u043a\u0443\u043f\u0438\u0442\u044c") || name.contains("buy"))
                    && clickPurchaseConfirmation(client, slot.id)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickPurchaseConfirmation(MinecraftClient client, int slotId) {
        if (!isPurchaseConfirmationScreen(client)) {
            return false;
        }
        Slot slot = findSlot(client, slotId);
        if (slot == null || !slot.hasStack() || client.player == null || slot.inventory == client.player.getInventory()) {
            return false;
        }
        String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
        if (!name.contains("\u043a\u0443\u043f\u0438\u0442\u044c") && !name.contains("buy")) {
            return false;
        }
        LOGGER.info("AutoBuy: found purchase button in slot {}", slot.id);
        clickConfirmationSlot(client, slot.id);
        return true;
    }

    private void clickAuctionSlot(MinecraftClient client, int slotId) {
        clickSlot(client, slotId, SlotActionType.QUICK_MOVE);
    }

    private void clickConfirmationSlot(MinecraftClient client, int slotId) {
        clickSlot(client, slotId, SlotActionType.PICKUP);
    }

    private void clickMenuSlot(MinecraftClient client, int slotId) {
        clickSlot(client, slotId, SlotActionType.QUICK_MOVE);
    }

    private void clickSlot(MinecraftClient client, int slotId, SlotActionType actionType) {
        if (client.player == null || client.interactionManager == null || client.player.currentScreenHandler == null) {
            return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                slotId,
                0,
                actionType,
                client.player
        );
    }

    private static boolean hasCursorStack(MinecraftClient client) {
        return client.player != null
                && client.player.currentScreenHandler != null
                && !client.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private static Slot findSlot(MinecraftClient client, int slotId) {
        if (client.player == null || client.player.currentScreenHandler == null) {
            return null;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot != null && slot.id == slotId) {
                return slot;
            }
        }
        return null;
    }

    private void sendAd(MinecraftClient client) {
        if (!anarchyAdEnabled) {
            return;
        }
        String text = anarchyAdText.trim();
        if (text.isEmpty()) {
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

    private void clearNameSwap() {
        nameSourceInventoryIndex = -1;
        previousHotbarSlot = -1;
        swappedForName = false;
    }

    public boolean tryPauseForExternalAction(MinecraftClient client, long pauseMs) {
        return tryPauseForExternalAction(client, pauseMs, false);
    }

    public boolean tryPauseForExternalActionPreservingAuction(MinecraftClient client, long pauseMs) {
        return tryPauseForExternalAction(client, pauseMs, true);
    }

    public void resumeAfterExternalAction(MinecraftClient client) {
        debug("resume requested; step=" + step);
        if (isEnabled() && client != null && ready(client) && step == Step.WAIT_EXTERNAL
                && !isExternalSellScreen(client)) {
            externalPauseUntil = 0L;
            externalResumeAuctionScreen = false;
            restartSearch(client, System.currentTimeMillis());
        }
    }

    private boolean tryPauseForExternalAction(MinecraftClient client, long pauseMs, boolean preserveAuctionScreen) {
        if (!isEnabled()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (step == Step.WAIT_EXTERNAL && pauseMs <= 0L && now < externalPauseUntil) {
            if (FluxVisualsClient.MODULE_MANAGER.getTelegram().isAutomaticSellPendingOrWorking()) {
                // AutoSell may arrive while AutoBuy is already in its normal external wait.
                // It is already safe for the inventory seller to continue.
                externalPauseUntil = 0L;
                return true;
            }
            if (FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()) {
                return true;
            }
            return false;
        }
        if (!canYieldToExternalAction()) {
            return false;
        }
        boolean preserveCurrentScreen = preserveAuctionScreen && isSwordSearchScreenLikeRecentPurchases(client);
        if (!preserveCurrentScreen) {
            closeCurrentScreen(client);
        }
        pendingCandidateSlotId = -1;
        pendingCandidateClickAt = 0L;
        pendingPurchaseConfirmationSlotId = -1;
        boughtSwordInventoryIndex = -1;
        auctionScreenMissingSince = 0L;
        auctionScreenSeen = false;
        auctionScreenConfirmed = false;
        auctionScreenSyncId = -1;
        nextPurchaseAttemptAt = 0L;
        resetAuctionPageCycle(0L, false);
        externalPauseUntil = Math.max(externalPauseUntil, now + Math.max(0L, pauseMs));
        externalResumeAuctionScreen = preserveCurrentScreen;
        step = Step.WAIT_EXTERNAL;
        return true;
    }

    private boolean canYieldToExternalAction() {
        return step == Step.IDLE
                || step == Step.WAIT_CLICK_GUI_CLOSE
                || step == Step.SEARCH_DELAY
                || step == Step.WAIT_AUCTION
                || step == Step.WAIT_MONEY
                || step == Step.LOW_BALANCE_WAIT
                || step == Step.WAIT_EXTERNAL;
    }

    private boolean canStartImmediateAnarchy() {
        return step == Step.IDLE
                || step == Step.WAIT_CLICK_GUI_CLOSE
                || step == Step.SEARCH_DELAY
                || step == Step.WAIT_AUCTION
                || step == Step.LOW_BALANCE_WAIT;
    }

    private boolean isExternalMaintenanceWorking() {
        boolean botContext = MultiBotManager.isBotContext();
        return (!botContext && (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()
                || FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()))
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isAutomaticSellPendingOrWorking()
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellWorking()
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isLeaveReportWorking();
    }

    private boolean isExternalMaintenanceBlockingResume() {
        boolean botContext = MultiBotManager.isBotContext();
        return (!botContext && (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()
                || FluxVisualsClient.MODULE_MANAGER.getAutoResell().isWorking()))
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellWorking()
                || FluxVisualsClient.MODULE_MANAGER.getTelegram().isLeaveReportWorking();
    }

    private static String sanitizeAnarchyId(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "").trim();
    }

    private boolean isSellerBanned(List<String> tooltipLines) {
        if (bannedSellers.isEmpty()) {
            return false;
        }
        String seller = extractSeller(tooltipLines);
        return seller != null && isSellerBanned(seller);
    }

    private boolean isSellerBanned(String seller) {
        String normalized = normalizeSeller(seller);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String bannedSeller : bannedSellers) {
            if (normalizeSeller(bannedSeller).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPurchaseConfirmationScreen(MinecraftClient client) {
        if (client == null || client.currentScreen == null || client.player == null
                || client.player.currentScreenHandler == null || !(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (isAuctionSearchScreen(client)) {
            return false;
        }
        return title.contains("подтверж")
                || title.contains("покуп")
                || title.contains("confirm")
                || title.contains("buy");
    }

    private static String extractSeller(List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return null;
        }
        for (String line : tooltipLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            int labelIndex = lower.indexOf("\u043f\u0440\u043e\u0434\u0430\u0432\u0435\u0446");
            int labelLength = "\u043f\u0440\u043e\u0434\u0430\u0432\u0435\u0446".length();
            if (labelIndex < 0) {
                labelIndex = lower.indexOf("seller");
                labelLength = "seller".length();
            }
            if (labelIndex < 0) {
                continue;
            }

            String tail = line.substring(labelIndex + labelLength).trim();
            Matcher matcher = SELLER_NAME_PATTERN.matcher(tail);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String sanitizeSeller(String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = SELLER_NAME_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String normalizeSeller(String value) {
        return value == null ? "" : sanitizeSeller(value).toLowerCase(Locale.ROOT);
    }

    private static List<Text> snapshotTooltip(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || stack == null || stack.isEmpty()) {
            return List.of();
        }
        try {
            Item.TooltipContext context = Item.TooltipContext.create(client.world);
            return copyTooltip(stack.getTooltip(context, client.player, TooltipType.BASIC));
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<Text> copyTooltip(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return List.of();
        }
        List<Text> result = new ArrayList<>(tooltip.size());
        for (Text line : tooltip) {
            if (line != null) {
                result.add(line.copy());
            }
        }
        return List.copyOf(result);
    }

    public static Long extractBalance(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (!normalized.contains("\u0431\u0430\u043b\u0430\u043d\u0441") && !normalized.contains("balance")
                && !normalized.contains("\u0434\u0435\u043d\u0435\u0433") && !normalized.contains("\u043c\u043e\u043d\u0435\u0442")) {
            return null;
        }

        Matcher moneyMatcher = MONEY_AMOUNT_PATTERN.matcher(message);
        while (moneyMatcher.find()) {
            Long amount = parseMoneyAmount(moneyMatcher.group(1) != null ? moneyMatcher.group(1) : moneyMatcher.group(2));
            if (amount != null) {
                return amount;
            }
        }

        Matcher numberMatcher = BALANCE_NUMBER_PATTERN.matcher(normalized);
        while (numberMatcher.find()) {
            Long amount = parseMoneyAmount(numberMatcher.group(1));
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private static boolean isStorageEmptyForResellMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("\u0445\u0440\u0430\u043d\u0438\u043b\u0438\u0449")
                && normalized.contains("\u043e\u0442\u0441\u0443\u0442\u0441\u0442")
                && normalized.contains("\u043f\u0435\u0440\u0435\u0432\u044b\u0441\u0442\u0430\u0432");
    }

    private static boolean isPurchaseStep(Step step) {
        return step == Step.WAIT_CONFIRM || step == Step.WAIT_PURCHASE;
    }

    private static boolean isPurchaseSuccessMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("\u0432\u044b \u043a\u0443\u043f\u0438\u043b\u0438")
                || normalized.contains("\u0443\u0441\u043f\u0435\u0448\u043d\u043e \u043a\u0443\u043f\u0438\u043b\u0438")
                || normalized.contains("\u043f\u043e\u043a\u0443\u043f\u043a\u0430 \u0443\u0441\u043f\u0435\u0448");
    }

    private static boolean isPurchaseFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("\u0443\u0436\u0435 \u043a\u0443\u043f\u043b\u0435\u043d")
                || normalized.contains("\u0443\u0436\u0435 \u043a\u0443\u043f\u0438\u043b\u0438")
                || normalized.contains("\u043a\u0442\u043e-\u0442\u043e \u0443\u0436\u0435 \u043a\u0443\u043f\u0438\u043b")
                || normalized.contains("\u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043a\u0443\u043f\u0438\u0442\u044c")
                || normalized.contains("\u0442\u043e\u0432\u0430\u0440 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d")
                || normalized.contains("\u043b\u043e\u0442 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d");
    }

    private static boolean isOwnAuctionSaleMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
        boolean ownSale = normalized.contains("\u0443 \u0432\u0430\u0441 \u043a\u0443\u043f\u0438\u043b\u0438")
                || normalized.contains("\u043a\u0443\u043f\u0438\u043b\u0438 \u0443 \u0432\u0430\u0441")
                || normalized.contains("\u0432\u0430\u0448 \u043b\u043e\u0442 \u043f\u0440\u043e\u0434\u0430\u043d")
                || normalized.contains("\u0432\u0430\u0448 \u043b\u043e\u0442 \u043a\u0443\u043f\u0438\u043b\u0438")
                || normalized.contains("your item was sold")
                || normalized.contains("your lot was sold");
        if (!ownSale) {
            return false;
        }
        return normalized.contains("\u043d\u0430 /ah")
                || normalized.contains("\u0430\u0443\u043a\u0446\u0438\u043e\u043d")
                || normalized.contains("auction")
                || normalized.contains("\u043b\u043e\u0442")
                || normalized.contains("\u043f\u0440\u0435\u0434\u043c\u0435\u0442")
                || normalized.contains("item");
    }

    private static Long parseMoneyAmount(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('\u00a0', ' ').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        long multiplier = 1L;
        boolean abbreviated = false;
        if (normalized.endsWith("kk") || normalized.endsWith("кк")) {
            multiplier = 1_000_000L;
            abbreviated = true;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("k") || normalized.endsWith("к")) {
            multiplier = 1_000L;
            abbreviated = true;
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        } else if (normalized.endsWith("m") || normalized.endsWith("м")) {
            multiplier = 1_000_000L;
            abbreviated = true;
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        normalized = normalized.replace(" ", "");
        try {
            if (abbreviated && (normalized.contains(".") || normalized.contains(","))) {
                double decimal = Double.parseDouble(normalized.replace(',', '.'));
                if (!Double.isFinite(decimal) || decimal < 0.0D) return null;
                return Math.round(decimal * multiplier);
            }
            // Unabbreviated balances are normally formatted as 1 500 000,
            // 1,500,000 or 1.500.000. Treat punctuation as grouping here.
            String digits = normalized.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return null;
            return Math.multiplyExact(Long.parseLong(digits), multiplier);
        } catch (NumberFormatException ignored) {
            return null;
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static int screenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static long randomDelay(long minInclusive, long maxInclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1L);
    }

    static void debug(String message) {
        try {
            Path path = Path.of(System.getProperty("user.home"), "Desktop", "AutoBuy.log");
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path,
                    "[" + DEBUG_TIME_FORMAT.format(LocalDateTime.now()) + "] " + message
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void resetDebugLog() {
        try {
            Files.deleteIfExists(Path.of(System.getProperty("user.home"), "Desktop", "AutoBuy.log"));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long saturatedStatAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return second >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static boolean ready(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.interactionManager != null
                && client.getNetworkHandler() != null;
    }

    private static void closeCurrentScreen(MinecraftClient client) {
        if (client == null || client.currentScreen == null) {
            return;
        }
        if (ClientGuiProtection.isOpen(client)) {
            return;
        }
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }
    }

    private static boolean isNameStep(Step step) {
        return step == Step.NAME_PREPARE || step == Step.NAME_SEND || step == Step.NAME_RESTORE;
    }

    private static boolean isAnarchyStep(Step step) {
        return step == Step.ANARCHY_SEND || step == Step.ANARCHY_WAIT;
    }

    private static boolean isResellStep(Step step) {
        return step == Step.RESELL_SEND_AH || step == Step.RESELL_STORAGE || step == Step.RESELL_ACTION
                || step == Step.RESELL_RETURN || step == Step.RESELL_RETURN_WAIT
                || step == Step.RESELL_WAIT_NEXT || step == Step.RESELL_FINISH;
    }

    private static boolean isMaintenanceInterruptible(Step step) {
        return step == Step.SEARCH_DELAY || step == Step.WAIT_AUCTION;
    }

    private enum Step {
        IDLE,
        WAIT_CLICK_GUI_CLOSE,
        SEARCH_DELAY,
        WAIT_AUCTION,
        WAIT_CONFIRM,
        WAIT_PURCHASE,
        WAIT_MONEY,
        LOW_BALANCE_WAIT,
        WAIT_EXTERNAL,
        NAME_PREPARE,
        NAME_SEND,
        NAME_RESTORE,
        ANARCHY_SEND,
        ANARCHY_WAIT,
        RESELL_SEND_AH,
        RESELL_STORAGE,
        RESELL_ACTION,
        RESELL_RETURN,
        RESELL_RETURN_WAIT,
        RESELL_WAIT_NEXT,
        RESELL_FINISH
    }

    private enum MoneyCheckReason {
        NONE,
        STARTUP,
        LOW_BALANCE_RECHECK,
        STORAGE_EMPTY
    }

    private record PurchaseCandidate(
            String name,
            long price,
            String seller,
            ItemStack stack,
            List<Text> tooltip
    ) {
        private PurchaseCandidate {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            tooltip = copyTooltip(tooltip);
        }
    }

    public record RecentPurchase(
            ItemStack stack,
            List<Text> tooltip,
            long price,
            String seller,
            long purchasedAt
    ) {
        public RecentPurchase {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            tooltip = copyTooltip(tooltip);
        }
    }

    public record SessionStats(
            boolean active,
            String step,
            long runningTimeMs,
            int purchaseCount,
            int knownPricePurchaseCount,
            long purchaseSpent,
            long averagePurchasePrice,
            int saleCount,
            long saleRevenue,
            int resellCount,
            int pageTransitionCount,
            int pageErrorCount,
            int searchReopenCount,
            long averagePageLatencyMs,
            long estimatedPageLatencyMs,
            long lastPageConfirmTimeoutMs,
            long startBalance,
            long currentBalance
    ) {
    }
}
