package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.ClientGuiProtection;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoResell extends Module {
    private static final long INTERVAL_MIN_MS = 60_000L;
    private static final long INTERVAL_MAX_MS = 65_000L;
    private static final long CLOSE_SCREEN_DELAY_MIN_MS = 150L;
    private static final long CLOSE_SCREEN_DELAY_MAX_MS = 350L;
    private static final long SCREEN_TIMEOUT_MS = 12_000L;
    private static final int MAX_AH_COMMAND_ATTEMPTS = 2;
    private static final long AH_RETRY_DELAY_MIN_MS = 2_500L;
    private static final long AH_RETRY_DELAY_MAX_MS = 4_500L;
    private static final long AFTER_STORAGE_DELAY_MIN_MS = 750L;
    private static final long AFTER_STORAGE_DELAY_MAX_MS = 1_250L;
    private static final long RETURN_DELAY_MIN_MS = 500L;
    private static final long RETURN_DELAY_MAX_MS = 1_000L;
    private static final long AFTER_RESELL_DELAY_MIN_MS = 1_200L;
    private static final long AFTER_RESELL_DELAY_MAX_MS = 1_800L;

    private Step step = Step.IDLE;
    private long nextRunAt;
    private long nextActionAt;
    private long timeoutAt;
    private int ahCommandAttempts;
    private boolean returnToSwordSearch;
    private boolean listingsKnownPresent;

    public AutoResell() {
        super("AutoResell", "Refreshes auction resale menu every 60-65 seconds.", ModuleCategory.UTILS);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        reset();
        scheduleNextRun();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.interactionManager == null
                || client.getNetworkHandler() == null) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isEnabled()
                && FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isAutoResellEnabled()) {
            reset();
            return;
        }
        if (FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellPendingOrWorking()) {
            if (step != Step.IDLE) {
                closeCurrentScreen(client);
                reset();
            }
            return;
        }
        if (AuctionAccessGuard.isBlocked(now)) {
            return;
        }
        if (step == Step.FINISH) {
            closeCurrentScreen(client);
            if (now >= nextActionAt) {
                finish(client);
            }
            return;
        }
        if (step == Step.IDLE) {
            if (now < nextRunAt) {
                return;
            }
            if (!listingsKnownPresent) {
                return;
            }
            if (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isWorking()) {
                return;
            }
            if (FluxVisualsClient.MODULE_MANAGER.getTelegram().isInventorySellPendingOrWorking()
                    || FluxVisualsClient.MODULE_MANAGER.getTelegram().isLeaveReportWorking()) {
                return;
            }
            boolean swordSearchScreen = isSwordSearchScreenLikeRecentPurchases(client);
            boolean autoBuyPaused = swordSearchScreen
                    ? FluxVisualsClient.MODULE_MANAGER.getAutoBuy()
                            .tryPauseForExternalActionPreservingAuction(client, 0L)
                    : FluxVisualsClient.MODULE_MANAGER.getAutoBuy().tryPauseForExternalAction(client, 0L);
            if (!autoBuyPaused) {
                return;
            }
            returnToSwordSearch = swordSearchScreen;
            if (swordSearchScreen) {
                step = Step.WAIT_STORAGE;
                nextActionAt = now + randomDelay(CLOSE_SCREEN_DELAY_MIN_MS, CLOSE_SCREEN_DELAY_MAX_MS);
                timeoutAt = now + SCREEN_TIMEOUT_MS;
            } else if (client.currentScreen != null) {
                closeCurrentScreen(client);
                step = Step.SEND_AH;
                long delay = randomDelay(CLOSE_SCREEN_DELAY_MIN_MS, CLOSE_SCREEN_DELAY_MAX_MS);
                nextActionAt = now + delay;
                timeoutAt = now + delay + SCREEN_TIMEOUT_MS;
            } else {
                openAuction(client, now);
            }
            return;
        }

        if (now < nextActionAt) {
            return;
        }
        if (now > timeoutAt && (step == Step.WAIT_RETURN || step == Step.WAIT_SWORD_SEARCH)) {
            if (isSwordSearchScreenLikeRecentPurchases(client)) {
                finishPreservingScreen();
            } else {
                timeoutAt = now + SCREEN_TIMEOUT_MS;
            }
            return;
        }
        if (now > timeoutAt) {
            finish(client);
            return;
        }
        if (step == Step.SEND_AH) {
            openAuction(client, now);
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            if (step == Step.WAIT_STORAGE && ahCommandAttempts < MAX_AH_COMMAND_ATTEMPTS) {
                client.getNetworkHandler().sendChatCommand("ah");
                ahCommandAttempts++;
                nextActionAt = now + randomDelay(AH_RETRY_DELAY_MIN_MS, AH_RETRY_DELAY_MAX_MS);
            }
            return;
        }

        if (step == Step.WAIT_STORAGE) {
            if (clickFirst(client, "хранилище", "storage")) {
                step = Step.WAIT_RESELL;
                long delay = randomDelay(AFTER_STORAGE_DELAY_MIN_MS, AFTER_STORAGE_DELAY_MAX_MS);
                nextActionAt = now + delay;
                timeoutAt = now + SCREEN_TIMEOUT_MS + delay;
            }
            return;
        }

        if (step == Step.WAIT_RESELL && isStorageScreen(client)
                && clickFirst(client, "перевыстав", "resell")) {
            if (returnToSwordSearch) {
                step = Step.WAIT_RETURN;
                nextActionAt = now + randomDelay(RETURN_DELAY_MIN_MS, RETURN_DELAY_MAX_MS);
                timeoutAt = now + SCREEN_TIMEOUT_MS;
                return;
            }
            closeCurrentScreen(client);
            step = Step.FINISH;
            long delay = randomDelay(AFTER_RESELL_DELAY_MIN_MS, AFTER_RESELL_DELAY_MAX_MS);
            nextActionAt = now + delay;
            timeoutAt = now + delay + SCREEN_TIMEOUT_MS;
            return;
        }

        if (step == Step.WAIT_RETURN) {
            if (isSwordSearchScreenLikeRecentPurchases(client)) {
                finishPreservingScreen();
                return;
            }
            if (clickFirst(client, "вернуться", "return")
                    || clickFirst(client, "назад", "back")) {
                step = Step.WAIT_SWORD_SEARCH;
                nextActionAt = now + randomDelay(RETURN_DELAY_MIN_MS, RETURN_DELAY_MAX_MS);
                timeoutAt = now + SCREEN_TIMEOUT_MS;
            }
            return;
        }

        if (step == Step.WAIT_SWORD_SEARCH) {
            if (isSwordSearchScreenLikeRecentPurchases(client)) {
                finishPreservingScreen();
                return;
            }
            if (clickFirst(client, "вернуться", "return")
                    || clickFirst(client, "назад", "back")) {
                nextActionAt = now + randomDelay(RETURN_DELAY_MIN_MS, RETURN_DELAY_MAX_MS);
                timeoutAt = now + SCREEN_TIMEOUT_MS;
            }
        }
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        reset();
        nextRunAt = 0L;
    }

    public boolean isWorking() {
        return isEnabled() && step != Step.IDLE;
    }

    public void cancelCurrentRun(MinecraftClient client) {
        if (step == Step.IDLE) {
            return;
        }
        closeCurrentScreen(client);
        reset();
        scheduleNextRun();
    }

    public void noteListingConfirmed() {
        listingsKnownPresent = true;
        if (isEnabled() && nextRunAt <= 0L) {
            scheduleNextRun();
        }
    }

    public void noteStorageEmpty() {
        listingsKnownPresent = false;
        nextRunAt = 0L;
    }

    private void openAuction(MinecraftClient client, long now) {
        client.getNetworkHandler().sendChatCommand("ah");
        ahCommandAttempts = 1;
        step = Step.WAIT_STORAGE;
        nextActionAt = now + randomDelay(AH_RETRY_DELAY_MIN_MS, AH_RETRY_DELAY_MAX_MS);
        timeoutAt = now + SCREEN_TIMEOUT_MS;
    }

    private boolean clickFirst(MinecraftClient client, String russianHint, String englishHint) {
        if (client.player == null || client.interactionManager == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (!matches(stack, russianHint, englishHint)) {
                continue;
            }
            client.interactionManager.clickSlot(
                    client.player.currentScreenHandler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
            return true;
        }
        return false;
    }

    private static boolean matches(ItemStack stack, String russianHint, String englishHint) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        return name.contains(russianHint) || name.contains(englishHint);
    }

    private static boolean isStorageScreen(MinecraftClient client) {
        if (client == null || client.currentScreen == null) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("хранилищ") || title.contains("storage");
    }

    private static boolean isSwordSearchScreenLikeRecentPurchases(MinecraftClient client) {
        if (client == null
                || !(client.currentScreen instanceof HandledScreen<?>)
                || client.player == null
                || client.player.currentScreenHandler == null) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("покуп") || title.contains("buy") || title.contains("confirm")) {
            return false;
        }
        if (title.contains("хранилищ") || title.contains("storage")) {
            return false;
        }
        boolean searchTitle = title.contains("поиск") || title.contains("search");
        boolean netheriteSwordTitle = title.contains("незеритовый меч")
                || title.contains("netherite sword");
        boolean visibleNetheriteSword = false;
        boolean refreshButton = false;
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot != null
                    && slot.hasStack()
                    && slot.inventory != client.player.getInventory()) {
                ItemStack stack = slot.getStack();
                if (stack.isOf(Items.NETHERITE_SWORD)) {
                    visibleNetheriteSword = true;
                }
                String name = stack.getName().getString().toLowerCase(Locale.ROOT);
                if (name.contains("обнов") || name.contains("refresh")) {
                    refreshButton = true;
                }
            }
        }
        return refreshButton || netheriteSwordTitle || searchTitle && visibleNetheriteSword;
    }

    private void finish(MinecraftClient client) {
        if (client != null) {
            closeCurrentScreen(client);
        }
        reset();
        scheduleNextRun();
    }

    private void finishPreservingScreen() {
        reset();
        scheduleNextRun();
    }

    private void reset() {
        step = Step.IDLE;
        nextActionAt = 0L;
        timeoutAt = 0L;
        ahCommandAttempts = 0;
        returnToSwordSearch = false;
    }

    private void scheduleNextRun() {
        nextRunAt = System.currentTimeMillis() + randomDelay(INTERVAL_MIN_MS, INTERVAL_MAX_MS);
    }

    private static void closeCurrentScreen(MinecraftClient client) {
        if (ClientGuiProtection.isOpen(client)) return;
        if (client == null || client.currentScreen == null) {
            return;
        }
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }
    }

    private static long randomDelay(long minInclusive, long maxInclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1L);
    }

    private enum Step {
        IDLE,
        SEND_AH,
        WAIT_STORAGE,
        WAIT_RESELL,
        WAIT_RETURN,
        WAIT_SWORD_SEARCH,
        FINISH
    }
}
