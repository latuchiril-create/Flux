package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.ClientGuiProtection;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automates crafting of Golden Apples / Enchanted Golden Apples via Workbench
 * (including mass stacking and full inventory crafting),
 * and puts them in configured batch sizes on auction via /ah sellgui <price>.
 */
public final class ItemCrafter extends Module {
    public enum Mode {
        GOLDEN_APPLE("Золотые яблоки", "Golden Apples", Items.GOLDEN_APPLE, Items.GOLD_INGOT),
        ENCHANTED_GOLDEN_APPLE("Чарки", "Enchanted Apples", Items.ENCHANTED_GOLDEN_APPLE, Items.GOLD_BLOCK);

        public final String ruName;
        public final String enName;
        public final Item targetItem;
        public final Item goldItem;

        Mode(String ruName, String enName, Item targetItem, Item goldItem) {
            this.ruName = ruName;
            this.enName = enName;
            this.targetItem = targetItem;
            this.goldItem = goldItem;
        }

        public String label(boolean en) {
            return en ? enName : ruName;
        }
    }

    private Mode mode = Mode.GOLDEN_APPLE;
    private int batchCount = 1;
    private String sellPrice = "150k";
    private int maxLots = 5;

    private int goldBlockPricePerStack = 500_000;
    private int goldIngotPricePerStack = 60_000;
    private int applePricePerStack = 50_000;
    private boolean autoBuyIngredients = true;
    /** Set after the server reports insufficient funds; cleared when re-enabled. */
    private boolean purchasesBlockedByBalance = false;
    /** Seconds to wait after listing items before re-checking inventory (default 10). */
    private int sellWaitSeconds = 10;

    private Step step = Step.IDLE;
    private long nextActionAt = 0L;
    private long timeoutAt = 0L;
    private int retryAttempts = 0;
    private BlockPos targetWorkbenchPos = null;

    private int craftGridStep = 0;
    private boolean craftingGoldBlocks = false;
    private boolean decompressingGoldBlocks = false;

    // Quick-craft drag state (vanilla left-drag = start/add/end packets, one per tick)
    private int[] dragNeedy = new int[0];
    private int dragPos = 0;
    // Until this timestamp the recipe output may still arrive — don't close the workbench.
    private long outputWaitUntil = 0L;

    private int sellGuiSlotIndex = -1;
    private int sellGuiConfirmSlot = -1;
    private int sellGuiStep = 0;

    private int filledLotsThisSession = 0;

    // Buying sub-state variables
    private BuyTarget currentBuyTarget = null;
    private boolean buyPageForward = true;
    private int buyPageIndex = 1;
    private int cheapestPageIndex = -1;
    private long cheapestPricePerStack = Long.MAX_VALUE;
    private long lastPageClickAt = 0L;
    private long searchOpenedAt = 0L;
    private long auctionFirstSeenAt = 0L;
    /** Candidate is delayed and revalidated exactly like AutoBuy before it is clicked. */
    private int pendingBuySlotId = -1;
    private int pendingBuySyncId = -1;
    private long pendingBuyClickAt = 0L;
    /** Inventory count before the candidate click; an existing stack is not a purchase result. */
    private int buyInventoryBaseline = -1;
    private int confirmClickAttempts = 0;
    private long nextConfirmAttemptAt = 0L;

    // ── Smooth head-movement (anti-bot) ───────────────────────────────────────
    /** Yaw/pitch anchor — captured when ItemCrafter is enabled. */
    private float baseYaw   = 0f;
    private float basePitch = 0f;
    /** Smooth current angles (the values we actually applied last tick). */
    private float smoothYaw   = 0f;
    private float smoothPitch = 0f;
    /** Drift target we are slowly moving toward. */
    private float targetHeadYaw   = 0f;
    private float targetHeadPitch = 0f;
    /** When to pick the next drift target. */
    private long nextHeadChangeAt = 0L;
    /**
     * Per-tick rate limit for head turns (game runs 20 tps).
     * Constant angular velocity instead of exponential lerp — no initial
     * snap, no jerks. 1.5°/tick ≈ 30°/s, calm human-like look-around.
     */
    private static final float HEAD_MAX_YAW_STEP = 0.65f;
    private static final float HEAD_MAX_PITCH_STEP = 0.42f;
    /** The yaw/pitch we wrote to the player last tick — used to detect real mouse input. */
    private float lastAppliedYaw   = Float.NaN;
    private float lastAppliedPitch = Float.NaN;
    /** While > System.currentTimeMillis(), head movement is paused (player is moving mouse). */
    private long headMovementPausedUntil = 0L;
    /** Accumulated micro-tremor offsets (random walk, slowly decays). */
    private float tremorYaw   = 0f;
    private float tremorPitch = 0f;
    private long nextTremorChangeAt = 0L;
    private HeadPattern headPattern = HeadPattern.SCAN;

    public enum BuyTarget {
        GOLD_BLOCK("Золотой блок", Items.GOLD_BLOCK, 64, 64),
        GOLD_INGOT("Золотой слиток", Items.GOLD_INGOT, 64, 192),
        APPLE("Яблоко", Items.APPLE, 64, 1);

        public final String searchName;
        public final Item item;
        public final int targetCount;
        /** Minimum amount to buy in one purchase run (stack of blocks / 3 stacks of ingots). */
        public final int minBuyCount;

        BuyTarget(String searchName, Item item, int targetCount, int minBuyCount) {
            this.searchName = searchName;
            this.item = item;
            this.targetCount = targetCount;
            this.minBuyCount = minBuyCount;
        }
    }

    public ItemCrafter() {
        super("ItemCrafter", "Авто-крафт яблок в верстаке, авто-закупка ингредиентов на AH и продажа через /ah sellgui.", ModuleCategory.UTILS);
    }

    /** Stops further AH purchases after the server reports insufficient funds. */
    public void onChatMessage(String message) {
        if (!isEnabled() || message == null) {
            return;
        }
        String normalized = message.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (!normalized.contains("не хватает монет")
                && !normalized.contains("недостаточно монет")
                && !normalized.contains("не хватает денег")
                && !normalized.contains("недостаточно денег")
                && !normalized.contains("недостаточно средств")) {
            return;
        }
        purchasesBlockedByBalance = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (step == Step.BUY_SEARCH || step == Step.BUY_PROCESS || step == Step.BUY_CONFIRM) {
            if (client != null) {
                closeScreen(client);
            }
            resetPendingPurchase();
            currentBuyTarget = null;
            step = Step.CHECK_INVENTORY;
            nextActionAt = System.currentTimeMillis() + randomDelay(250, 450);
        }
        debugBuy("LOW_BALANCE", "purchases_blocked=true; crafting_existing_resources=true");
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.GOLDEN_APPLE : mode;
        FluxVisualsClient.requestConfigSave();
    }

    public int getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(int count) {
        this.batchCount = Math.max(1, Math.min(64, count));
        FluxVisualsClient.requestConfigSave();
    }

    public String getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(String price) {
        this.sellPrice = price == null ? "150k" : price.trim();
        FluxVisualsClient.requestConfigSave();
    }

    public int getMaxLots() {
        return maxLots;
    }

    public void setMaxLots(int lots) {
        this.maxLots = Math.max(1, Math.min(15, lots));
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isAutoBuyIngredients() {
        return autoBuyIngredients;
    }

    public void setAutoBuyIngredients(boolean autoBuyIngredients) {
        this.autoBuyIngredients = autoBuyIngredients;
        FluxVisualsClient.requestConfigSave();
    }

    public int getGoldBlockPricePerStack() {
        return goldBlockPricePerStack;
    }

    public void setGoldBlockPricePerStack(int price) {
        this.goldBlockPricePerStack = Math.max(1, price);
        FluxVisualsClient.requestConfigSave();
    }

    public int getGoldIngotPricePerStack() {
        return goldIngotPricePerStack;
    }

    public void setGoldIngotPricePerStack(int price) {
        this.goldIngotPricePerStack = Math.max(1, price);
        FluxVisualsClient.requestConfigSave();
    }

    public int getApplePricePerStack() {
        return applePricePerStack;
    }

    public void setApplePricePerStack(int price) {
        this.applePricePerStack = Math.max(1, price);
        FluxVisualsClient.requestConfigSave();
    }

    public int getSellWaitSeconds() {
        return sellWaitSeconds;
    }

    public void setSellWaitSeconds(int seconds) {
        this.sellWaitSeconds = Math.max(1, Math.min(300, seconds));
        FluxVisualsClient.requestConfigSave();
    }

    public void copySettingsFrom(ItemCrafter other) {
        if (other == null || other == this) {
            return;
        }
        this.mode = other.mode;
        this.batchCount = other.batchCount;
        this.sellPrice = other.sellPrice;
        this.maxLots = other.maxLots;
        this.autoBuyIngredients = other.autoBuyIngredients;
        this.goldBlockPricePerStack = other.goldBlockPricePerStack;
        this.goldIngotPricePerStack = other.goldIngotPricePerStack;
        this.applePricePerStack = other.applePricePerStack;
        this.sellWaitSeconds = other.sellWaitSeconds;
    }

    private final java.util.Set<Integer> filledSellSlotsThisSession = new java.util.HashSet<>();

    @Override
    protected void onEnable(MinecraftClient client) {
        // Force-close any open screen so we always start from a clean state
        if (client != null && client.currentScreen != null) {
            closeScreen(client);
        }

        step = Step.CHECK_INVENTORY;
        nextActionAt = System.currentTimeMillis() + randomDelay(600, 1000); // slight extra delay after closing screen
        timeoutAt = 0L;
        retryAttempts = 0;
        targetWorkbenchPos = null;
        craftGridStep = 0;
        craftingGoldBlocks = false;
        decompressingGoldBlocks = false;
        dragNeedy = new int[0];
        dragPos = 0;
        outputWaitUntil = 0L;
        sellGuiStep = 0;
        filledLotsThisSession = 0;
        filledSellSlotsThisSession.clear();
        currentBuyTarget = null;
        purchasesBlockedByBalance = false;
        buyPageForward = true;
        buyPageIndex = 1;
        cheapestPageIndex = -1;
        cheapestPricePerStack = Long.MAX_VALUE;
        auctionFirstSeenAt = 0L;
        resetPendingPurchase();

        // Snapshot player's current look direction as the anchor
        if (client != null && client.player != null) {
            baseYaw         = client.player.getYaw();
            basePitch       = client.player.getPitch();
            smoothYaw       = baseYaw;
            smoothPitch     = basePitch;
            targetHeadYaw   = baseYaw;
            targetHeadPitch = basePitch;
        }
        lastAppliedYaw          = Float.NaN;
        lastAppliedPitch        = Float.NaN;
        headMovementPausedUntil = 0L;
        tremorYaw               = 0f;
        tremorPitch             = 0f;
        nextTremorChangeAt      = System.currentTimeMillis();
        nextHeadChangeAt        = System.currentTimeMillis();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        step = Step.IDLE;
        nextActionAt = 0L;
        timeoutAt = 0L;
        retryAttempts = 0;
        targetWorkbenchPos = null;
        craftGridStep = 0;
        craftingGoldBlocks = false;
        decompressingGoldBlocks = false;
        dragNeedy = new int[0];
        dragPos = 0;
        outputWaitUntil = 0L;
        sellGuiStep = 0;
        filledLotsThisSession = 0;
        filledSellSlotsThisSession.clear();
        currentBuyTarget = null;
        purchasesBlockedByBalance = false;
        buyPageIndex = 1;
        cheapestPageIndex = -1;
        cheapestPricePerStack = Long.MAX_VALUE;
        nextHeadChangeAt = 0L;
        resetPendingPurchase();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.interactionManager == null
                || client.getNetworkHandler() == null || client.world == null) {
            return;
        }

        // Always tick head movement every game tick (independent of action cooldown)
        tickHeadMovement(client);

        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return;
        }

        // Timeout watchdog
        if (timeoutAt > 0L && now >= timeoutAt) {
            closeScreen(client);
            step = Step.CHECK_INVENTORY;
            nextActionAt = now + randomDelay(300, 600);
            timeoutAt = 0L;
            return;
        }

        switch (step) {
            case CHECK_INVENTORY -> handleCheckInventory(client, now);
            case BUY_SEARCH -> handleBuySearch(client, now);
            case BUY_PROCESS -> handleBuyProcess(client, now);
            case BUY_CONFIRM -> handleBuyConfirm(client, now);
            case FIND_AND_OPEN_WORKBENCH -> handleFindAndOpenWorkbench(client, now);
            case CRAFTING -> handleCrafting(client, now);
            case OPEN_AH_STORAGE -> handleOpenAhStorage(client, now);
            case CLAIM_STORAGE_APPLES -> handleClaimStorageApples(client, now);
            case OPEN_SELL_GUI -> handleOpenSellGui(client, now);
            case PROCESS_SELL_GUI -> handleProcessSellGui(client, now);
            default -> {}
        }
    }

    private void handleCheckInventory(MinecraftClient client, long now) {
        int appleCount = countItemInInventory(client, Items.APPLE);
        int targetAppleCount = countItemInInventory(client, mode.targetItem);

        // Check if we can craft more items first (craft ALL available ingredients)
        boolean canCraftMore = false;

        if (mode == Mode.ENCHANTED_GOLDEN_APPLE) {
            int goldBlockCount = countItemInInventory(client, Items.GOLD_BLOCK);
            int goldIngotCount = countItemInInventory(client, Items.GOLD_INGOT);

            if (goldBlockCount >= 8 && appleCount >= 1) {
                canCraftMore = true;
                craftingGoldBlocks = false;
                decompressingGoldBlocks = false;
            } else if (goldIngotCount >= 9 && (goldBlockCount > 0 || appleCount >= 1)) {
                canCraftMore = true;
                craftingGoldBlocks = true;
                decompressingGoldBlocks = false;
            }
        } else {
            int goldIngotCount = countItemInInventory(client, Items.GOLD_INGOT);
            int goldBlockCount = countItemInInventory(client, Items.GOLD_BLOCK);

            if (goldIngotCount >= 8 && appleCount >= 1) {
                canCraftMore = true;
                craftingGoldBlocks = false;
                decompressingGoldBlocks = false;
            } else if (goldBlockCount >= 1 && (goldIngotCount < 8 || appleCount >= 1)) {
                canCraftMore = true;
                craftingGoldBlocks = false;
                decompressingGoldBlocks = true;
            }
        }

        if (canCraftMore) {
            step = Step.FIND_AND_OPEN_WORKBENCH;
            nextActionAt = now + randomDelay(150, 300);
            return;
        }

        // If we have crafted apples, proceed to storage/sell cycle
        if (targetAppleCount > 0) {
            step = Step.OPEN_AH_STORAGE;
            nextActionAt = now + randomDelay(250, 450);
            return;
        }

        // If ingredients are missing and autoBuyIngredients is enabled -> buy ingredients on AH!
        if (autoBuyIngredients) {
            BuyTarget missingTarget = getMissingIngredientTarget(client);
            if (missingTarget != null) {
                currentBuyTarget = missingTarget;
                step = Step.BUY_SEARCH;
                nextActionAt = now + randomDelay(300, 600);
                return;
            }
        }

        // All ingredients crafted and all apples listed -> auto-disable
        setEnabled(false);
    }

    private BuyTarget getMissingIngredientTarget(MinecraftClient client) {
        if (purchasesBlockedByBalance) {
            return null;
        }
        int apples = countItemInInventory(client, Items.APPLE);
        if (apples < 1) {
            return BuyTarget.APPLE;
        }

        if (mode == Mode.ENCHANTED_GOLDEN_APPLE) {
            int goldBlocks = countItemInInventory(client, Items.GOLD_BLOCK);
            int goldIngots = countItemInInventory(client, Items.GOLD_INGOT);
            if (goldBlocks < 8 && goldIngots < 9) {
                return BuyTarget.GOLD_BLOCK;
            }
        } else {
            int goldIngots = countItemInInventory(client, Items.GOLD_INGOT);
            int goldBlocks = countItemInInventory(client, Items.GOLD_BLOCK);
            if (goldIngots < 8 && goldBlocks < 1) {
                return BuyTarget.GOLD_INGOT;
            }
        }
        return null;
    }

    private int getMaxAllowedPrice(BuyTarget target) {
        return switch (target) {
            case GOLD_BLOCK -> goldBlockPricePerStack;
            case GOLD_INGOT -> goldIngotPricePerStack;
            case APPLE -> applePricePerStack;
        };
    }

    /**
     * Parses the real item quantity from an AH lot tooltip.
     * Tooltip typically contains lines like:
     *   "Количество: 24"  /  "Amount: 24"  /  "x24"
     * Falls back to 64 if not found (treat as full stack for conservative pricing).
     */
    static int extractQuantityFromLot(int displayedStackCount, List<String> tooltip) {
        // Auction plugins normally put the real lot size in the slot stack.  It
        // is the only reliable source on servers whose tooltip contains only a
        // price and seller name.  The old code assumed 64 in that case, which
        // made a 12-item lot for 400k look like 400k per stack.
        int stackCount = displayedStackCount;
        if (tooltip != null) {
            for (String line : tooltip) {
                if (line == null || line.isBlank()) continue;
                String lower = line.toLowerCase(Locale.ROOT);
                // Match "Количество: 24", "Amount: 24", "x 24", "x24", "кол-во: 24"
                if (lower.contains("количест") || lower.contains("amount") || lower.contains("кол-во")
                        || lower.contains("кол:") || lower.matches(".*\\bx\\s*\\d+.*")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(line);
                    if (m.find()) {
                        try {
                            int q = Integer.parseInt(m.group(1));
                            if (q >= 1 && q <= 64) return q;
                        } catch (NumberFormatException ignored) { }
                    }
                }
            }
        }
        // Never invent a full stack. An unknown amount must not pass a
        // per-stack price limit; the caller skips it until the server exposes
        // a count in either the slot or the tooltip.
        return stackCount >= 1 && stackCount <= 64 ? stackCount : -1;
    }

    private void handleBuySearch(MinecraftClient client, long now) {
        if (currentBuyTarget == null) {
            step = Step.CHECK_INVENTORY;
            nextActionAt = now + randomDelay(200, 400);
            return;
        }

        closeScreen(client);
        resetPendingPurchase();
        client.getNetworkHandler().sendChatCommand("ah search " + currentBuyTarget.searchName);
        debugBuy("SEARCH", "target=" + currentBuyTarget.item + " query=" + currentBuyTarget.searchName);
        step = Step.BUY_PROCESS;
        buyPageForward = true;
        buyPageIndex = 1;
        cheapestPageIndex = -1;
        cheapestPricePerStack = Long.MAX_VALUE;
        searchOpenedAt = now;
        lastPageClickAt = now;
        auctionFirstSeenAt = 0L;
        nextActionAt = now + randomDelay(500, 800);
        timeoutAt = now + 15000L;
    }

    private void handleBuyProcess(MinecraftClient client, long now) {
        if (isPurchaseConfirmScreen(client)) {
            step = Step.BUY_CONFIRM;
            nextActionAt = now + randomDelay(150, 300);
            return;
        }

        if (!isAuctionScreen(client)) {
            auctionFirstSeenAt = 0L;
            // Wait for screen to open or reopen search
            if (now - searchOpenedAt > 3000L) {
                step = Step.BUY_SEARCH;
                nextActionAt = now + randomDelay(300, 600);
            }
            return;
        }

        // Wait 3 seconds after auction screen first opens before taking any action
        if (auctionFirstSeenAt == 0L) {
            auctionFirstSeenAt = now;
            nextActionAt = now + 3000L + randomDelay(100, 300);
            return;
        }
        if (now - auctionFirstSeenAt < 3000L) {
            return;
        }

        // Wait at least 500ms after page change for slots to load
        if (now - lastPageClickAt < 500L) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (pendingBuySlotId >= 0) {
            processPendingBuy(client, handler, now);
            return;
        }
        int maxPriceAllowed = getMaxAllowedPrice(currentBuyTarget);
        Slot pageBestSlot = null;
        long pageBestPrice = Long.MAX_VALUE;

        // Scan upper inventory for target item matching max price
        for (Slot slot : handler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isOf(currentBuyTarget.item)) {
                // Parse tooltip for price and quantity
                List<String> tooltip = stack.getTooltip(
                        Item.TooltipContext.create(client.world),
                        client.player,
                        net.minecraft.item.tooltip.TooltipType.BASIC
                ).stream().map(net.minecraft.text.Text::getString).toList();

                Long price = ItemResorter.extractPrice(tooltip);
                if (price != null) {
                    // Real quantity is in tooltip (e.g. "Количество: 24" or "Amount: 24"),
                    // stack.getCount() is always 1 for AH GUI slot icons
                    int count = extractQuantityFromLot(stack.getCount(), tooltip);
                    if (count <= 0) {
                        continue;
                    }
                    long pricePerStack = (price * 64L) / count;
                    if (pricePerStack <= maxPriceAllowed && pricePerStack < pageBestPrice) {
                        pageBestPrice = pricePerStack;
                        pageBestSlot = slot;
                    }
                }
            }
        }

        // Always scan the complete page set before buying. Slot ids are only
        // valid in the current handler, so remember the cheapest page and
        // return to it instead of clicking the first page's first match.
        if (pageBestSlot != null && pageBestPrice < cheapestPricePerStack) {
            cheapestPricePerStack = pageBestPrice;
            cheapestPageIndex = buyPageIndex;
            debugBuy("CHEAPEST", "page=" + buyPageIndex + " slot=" + pageBestSlot.id
                    + " pricePerStack=" + pageBestPrice);
        }

        boolean chooseCurrentPage = pageBestSlot != null && cheapestPageIndex == buyPageIndex
                && (!buyPageForward || findNamedSlot(handler, client, "следующ", "next") == null);
        if (chooseCurrentPage) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(120, 220);
                return;
            }
            if (!stillSameScreen(client, handler)) {
                nextActionAt = now + randomDelay(200, 400);
                return;
            }
            // Match AutoBuy: delay, then validate the same handler and slot before
            // QUICK_MOVE. This avoids clicking a replacement GUI packet as if it
            // were the auction listing.
            pendingBuySlotId = pageBestSlot.id;
            pendingBuySyncId = handler.syncId;
            pendingBuyClickAt = now + randomDelay(180, 420);
            debugBuy("CANDIDATE", "target=" + currentBuyTarget.item + " page=" + buyPageIndex
                    + " slot=" + pendingBuySlotId + " sync=" + pendingBuySyncId
                    + " pricePerStack=" + pageBestPrice + " priceLimit=" + maxPriceAllowed);
            nextActionAt = pendingBuyClickAt;
            return;
        }

        // If not found on this page, handle page navigation
        Slot nextButton = findNamedSlot(handler, client, "следующ", "next");
        Slot prevButton = findNamedSlot(handler, client, "предыдущ", "previous", "prev");
        Slot refreshButton = findNamedSlot(handler, client, "обнов", "refresh");

        if (buyPageForward) {
            if (nextButton != null) {
                // Click next page
                client.interactionManager.clickSlot(handler.syncId, nextButton.id, 0, SlotActionType.PICKUP, client.player);
                buyPageIndex++;
                lastPageClickAt = now;
                nextActionAt = now + randomDelay(550, 850);
                return;
            } else {
                // Reached last page -> start going backwards!
                buyPageForward = false;
                if (pageBestSlot != null && cheapestPageIndex == buyPageIndex) {
                    nextActionAt = now + randomDelay(180, 360);
                    return;
                }
                nextActionAt = now + randomDelay(200, 400);
                return;
            }
        } else {
            if (prevButton != null) {
                // Click previous page
                client.interactionManager.clickSlot(handler.syncId, prevButton.id, 0, SlotActionType.PICKUP, client.player);
                buyPageIndex = Math.max(1, buyPageIndex - 1);
                lastPageClickAt = now;
                nextActionAt = now + randomDelay(550, 850);
                return;
            } else {
                // Back to page 1! If refresh button is present, click refresh and wait
                if (refreshButton != null) {
                    client.interactionManager.clickSlot(handler.syncId, refreshButton.id, 0, SlotActionType.PICKUP, client.player);
                    lastPageClickAt = now;
                    buyPageForward = true;
                    buyPageIndex = 1;
                    cheapestPageIndex = -1;
                    cheapestPricePerStack = Long.MAX_VALUE;
                    nextActionAt = now + randomDelay(600, 1000);
                    return;
                } else {
                    buyPageForward = true;
                    nextActionAt = now + randomDelay(800, 1500);
                    return;
                }
            }
        }
    }

    private void handleBuyConfirm(MinecraftClient client, long now) {
        if (!isPurchaseConfirmScreen(client)) {
            // Check if purchase completed and item arrived
            int countInInv = countItemInInventory(client, currentBuyTarget.item);
            if (buyInventoryBaseline >= 0 && countInInv > buyInventoryBaseline) {
                debugBuy("SUCCESS", "target=" + currentBuyTarget.item + " before=" + buyInventoryBaseline
                        + " after=" + countInInv);
                resetPendingPurchase();
                if (countInInv >= currentBuyTarget.minBuyCount) {
                    // Bought enough (stack of blocks / 3 stacks of ingots)!
                    // Check if we still need other ingredients
                    step = Step.CHECK_INVENTORY;
                    nextActionAt = now + randomDelay(300, 600);
                    return;
                }
                // Bought less than the minimum (e.g. 56 instead of a stack) —
                // keep buying more instead of moving on.
                if (isAuctionScreen(client)) {
                    step = Step.BUY_PROCESS;
                    nextActionAt = now + randomDelay(300, 600);
                    timeoutAt = now + 20000L;
                    return;
                }
                // Auction screen is gone — recheck, CHECK_INVENTORY will
                // re-search and continue buying.
                step = Step.CHECK_INVENTORY;
                nextActionAt = now + randomDelay(300, 600);
                return;
            }

            if (isAuctionScreen(client) && now >= nextConfirmAttemptAt) {
                debugBuy("RETURNED_TO_AUCTION", "target=" + currentBuyTarget.item + " baseline=" + buyInventoryBaseline
                        + " current=" + countInInv);
                step = Step.BUY_PROCESS;
                nextActionAt = now + randomDelay(200, 400);
                return;
            }

            // The server can replace the confirmation GUI one tick after the
            // click. Do not classify that as success until the inventory grows.
            if (now >= timeoutAt) {
                debugBuy("RESULT_TIMEOUT", "target=" + currentBuyTarget.item + " baseline=" + buyInventoryBaseline
                        + " current=" + countInInv);
                resetPendingPurchase();
                step = Step.BUY_SEARCH;
                nextActionAt = now + randomDelay(300, 600);
            } else {
                nextActionAt = now + randomDelay(120, 220);
            }
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        Slot confirmButton = findPurchaseButton(handler, client);
        if (confirmButton != null && now >= nextConfirmAttemptAt) {
            client.interactionManager.clickSlot(handler.syncId, confirmButton.id, 0, SlotActionType.PICKUP, client.player);
            confirmClickAttempts++;
            long retryDelay = randomDelay(350, 600);
            nextConfirmAttemptAt = now + retryDelay;
            nextActionAt = nextConfirmAttemptAt;
            timeoutAt = now + 6000L;
            debugBuy("CONFIRM_CLICK", "slot=" + confirmButton.id + " sync=" + handler.syncId
                    + " attempt=" + confirmClickAttempts);
            return;
        }

        if (now >= timeoutAt) {
            debugBuy("CONFIRM_TIMEOUT", "button=" + (confirmButton == null ? "missing" : confirmButton.id)
                    + " attempts=" + confirmClickAttempts);
            resetPendingPurchase();
            closeScreen(client);
            step = Step.BUY_SEARCH;
            nextActionAt = now + randomDelay(400, 700);
        } else {
            nextActionAt = now + randomDelay(120, 220);
        }
    }

    private boolean isPurchaseConfirmScreen(MinecraftClient client) {
        if (client == null || client.player == null || !(client.currentScreen instanceof HandledScreen<?>)) return false;
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("покуп") || title.contains("buy") || title.contains("подтвержд") || title.contains("confirm")) {
            return true;
        }
        // Several AH plugins leave a generic title on the confirmation menu;
        // in that case the actual action button is the reliable marker.
        // The confirmation menu can retain the auction's decorative title or
        // even one item slot, so the actionable button wins over title checks.
        return findPurchaseButton(client.player.currentScreenHandler, client) != null;
    }

    /** Prefer the actionable Купить/Buy stack over an informational paper title. */
    private static Slot findPurchaseButton(ScreenHandler handler, MinecraftClient client) {
        Slot exact = findNamedSlot(handler, client, "купить", "buy");
        if (exact != null) {
            return exact;
        }
        return findNamedSlot(handler, client, "подтверд", "confirm", "соглас");
    }

    private static Slot findSlotById(ScreenHandler handler, int slotId) {
        if (handler == null) return null;
        for (Slot slot : handler.slots) {
            if (slot != null && slot.id == slotId) {
                return slot;
            }
        }
        return null;
    }

    /** Vanilla left-button drag start: press-and-hold over the first slot. */
    private void dragStart(MinecraftClient client, CraftingScreenHandler handler, int firstSlot) {
        client.interactionManager.clickSlot(handler.syncId, firstSlot, 0, SlotActionType.QUICK_CRAFT, client.player);
    }

    /** Vanilla drag move: include one more slot in the drag. */
    private void dragAdd(MinecraftClient client, CraftingScreenHandler handler, int slot) {
        client.interactionManager.clickSlot(handler.syncId, slot, 1, SlotActionType.QUICK_CRAFT, client.player);
    }

    /** Vanilla drag release: distribute the held stack across added slots. */
    private void dragEnd(MinecraftClient client, CraftingScreenHandler handler, int lastSlot) {
        client.interactionManager.clickSlot(handler.syncId, lastSlot, 2, SlotActionType.QUICK_CRAFT, client.player);
    }

    private static Slot findNamedSlot(ScreenHandler handler, MinecraftClient client, String... names) {
        if (handler == null) return null;
        for (Slot slot : handler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            for (String n : names) {
                if (name.contains(n.toLowerCase(Locale.ROOT))) {
                    return slot;
                }
            }
        }
        return null;
    }

    /** Executes a delayed AutoBuy-style click only while the original auction handler is still open. */
    private void processPendingBuy(MinecraftClient client, ScreenHandler handler, long now) {
        if (now < pendingBuyClickAt) {
            nextActionAt = pendingBuyClickAt;
            return;
        }
        int slotId = pendingBuySlotId;
        int expectedSyncId = pendingBuySyncId;
        pendingBuySlotId = -1;
        pendingBuySyncId = -1;
        pendingBuyClickAt = 0L;
        if (handler.syncId != expectedSyncId) {
            debugBuy("CANDIDATE_DROPPED", "reason=sync_changed expected=" + expectedSyncId + " actual=" + handler.syncId);
            nextActionAt = now + randomDelay(180, 360);
            return;
        }
        Slot candidate = findSlotById(handler, slotId);
        if (candidate == null || !candidate.hasStack() || candidate.inventory == client.player.getInventory()
                || currentBuyTarget == null || !candidate.getStack().isOf(currentBuyTarget.item)) {
            debugBuy("CANDIDATE_DROPPED", "reason=slot_changed slot=" + slotId);
            nextActionAt = now + randomDelay(180, 360);
            return;
        }
        buyInventoryBaseline = countItemInInventory(client, currentBuyTarget.item);
        confirmClickAttempts = 0;
        nextConfirmAttemptAt = now + randomDelay(350, 600);
        client.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player);
        step = Step.BUY_CONFIRM;
        nextActionAt = nextConfirmAttemptAt;
        timeoutAt = now + 6000L;
        debugBuy("LOT_CLICK", "slot=" + slotId + " sync=" + handler.syncId + " action=QUICK_MOVE"
                + " baseline=" + buyInventoryBaseline);
    }

    private void resetPendingPurchase() {
        pendingBuySlotId = -1;
        pendingBuySyncId = -1;
        pendingBuyClickAt = 0L;
        buyInventoryBaseline = -1;
        confirmClickAttempts = 0;
        nextConfirmAttemptAt = 0L;
    }

    private static void debugBuy(String event, String details) {
        BotDebug.info("DEBUG_CRAFTER_BUY_" + event, null, details);
    }

    private void handleFindAndOpenWorkbench(MinecraftClient client, long now) {
        if (client.player.currentScreenHandler instanceof CraftingScreenHandler) {
            step = Step.CRAFTING;
            craftGridStep = 0;
            nextActionAt = now + randomDelay(200, 400);
            timeoutAt = now + 15_000L;
            return;
        }

        // Find nearest crafting table within 4.5 blocks
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos nearestWorkbench = null;
        double nearestDistSq = Double.MAX_VALUE;

        int radius = 4;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (client.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) {
                        double distSq = client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (distSq < nearestDistSq && distSq <= 20.25) {
                            nearestDistSq = distSq;
                            nearestWorkbench = pos;
                        }
                    }
                }
            }
        }

        if (nearestWorkbench == null) {
            // No crafting table found in range
            retryAttempts++;
            if (retryAttempts > 5) {
                // If we already have crafted apples, proceed to sell them
                int targetApples = countItemInInventory(client, mode.targetItem);
                if (targetApples > 0) {
                    step = Step.OPEN_AH_STORAGE;
                    nextActionAt = now + randomDelay(300, 500);
                    return;
                }
                setEnabled(false);
                return;
            }
            nextActionAt = now + randomDelay(600, 1000);
            return;
        }

        targetWorkbenchPos = nearestWorkbench;
        Vec3d hitVec = Vec3d.ofCenter(nearestWorkbench);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, nearestWorkbench, false);

        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
        nextActionAt = now + randomDelay(300, 600);
        timeoutAt = now + 5000L;
    }

    /**
     * Legit grid clearing: moves back at most ONE grid stack per call and
     * reports whether the grid is fully empty. Bursting up to 9 QUICK_MOVEs
     * in a single tick desyncs the container on the server and looks like a
     * packet bot. Callers that transition state must wait when this returns
     * false (one more tick per remaining stack).
     *
     * @return true when the crafting grid (slots 1..9) holds nothing.
     */
    private boolean clearCraftingGrid(MinecraftClient client, CraftingScreenHandler handler) {
        for (int i = 1; i <= 9; i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack()) {
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                return false;
            }
        }
        return true;
    }

    /** Same-screen check: never click a container the server already replaced. */
    private static boolean stillSameScreen(MinecraftClient client, ScreenHandler handler) {
        return client != null && client.player != null
                && client.player.currentScreenHandler == handler;
    }

    private void handleCrafting(MinecraftClient client, long now) {
        if (!(client.player.currentScreenHandler instanceof CraftingScreenHandler handler)) {
            step = Step.FIND_AND_OPEN_WORKBENCH;
            craftGridStep = 0;
            nextActionAt = now + randomDelay(200, 400);
            return;
        }

        // 1. Decompress Gold Blocks -> Gold Ingots (for Golden Apple mode)
        if (decompressingGoldBlocks) {
            handleDecompressGoldBlocks(client, handler, now);
            return;
        }

        // 2. Compress Gold Ingots -> Gold Blocks (for Enchanted Apple mode)
        if (craftingGoldBlocks) {
            handleCraftGoldBlocks(client, handler, now);
            return;
        }

        // 3. Mass craft apples
        handleCraftApples(client, handler, now);
    }

    private void handleDecompressGoldBlocks(MinecraftClient client, CraftingScreenHandler handler, long now) {
        Slot outputSlot = handler.getSlot(0);
        if (outputSlot != null && outputSlot.hasStack() && outputSlot.getStack().isOf(Items.GOLD_INGOT)) {
            // Shift-click ALL crafted output
                client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
                craftGridStep = 0;
                nextActionAt = now + randomDelay(70, 120);

            int ingots = countItemInInventory(client, Items.GOLD_INGOT);
            int goldBlocks = countItemInInventory(client, Items.GOLD_BLOCK);

                if (ingots >= 8 || goldBlocks <= 0) {
                    if (!clearCraftingGrid(client, handler)) {
                        nextActionAt = now + randomDelay(60, 110);
                        return;
                    }
                    decompressingGoldBlocks = false;
                    craftGridStep = 0;
                    nextActionAt = now + randomDelay(70, 130);
            }
            return;
        }

        // Put gold blocks in slot 1
        if (craftGridStep == 0) {
            // Ensure slots 2..9 are clear
            boolean hasOther = false;
            for (int i = 2; i <= 9; i++) {
                if (handler.getSlot(i).hasStack()) {
                    hasOther = true;
                    break;
                }
            }
            if (hasOther) {
                clearCraftingGrid(client, handler);
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            int goldBlockSlot = findItemSlotInHandler(handler, Items.GOLD_BLOCK, 10, handler.slots.size() - 1);
            if (goldBlockSlot == -1) {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                decompressingGoldBlocks = false;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(70, 130);
                return;
            }

            client.interactionManager.clickSlot(handler.syncId, goldBlockSlot, 0, SlotActionType.PICKUP, client.player);
            craftGridStep = 1;
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        if (craftGridStep == 1) {
            // Put all held gold blocks into slot 1
            client.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.PICKUP, client.player);
            craftGridStep = 0;
            nextActionAt = now + randomDelay(45, 90);
        }
    }

    private void handleCraftGoldBlocks(MinecraftClient client, CraftingScreenHandler handler, long now) {
        // Calculate how many ingots to distribute per slot
        int ingotsInInv = countItemInInventory(client, Items.GOLD_INGOT);
        for (int i = 1; i <= 9; i++) {
            if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().isOf(Items.GOLD_INGOT)) {
                ingotsInInv += handler.getSlot(i).getStack().getCount();
            }
        }
        if (handler.getCursorStack().isOf(Items.GOLD_INGOT)) {
            ingotsInInv += handler.getCursorStack().getCount();
        }

        if (ingotsInInv < 9) {
            if (!clearCraftingGrid(client, handler)) {
                nextActionAt = now + randomDelay(60, 110);
                return;
            }
            craftingGoldBlocks = false;
            craftGridStep = 0;
            nextActionAt = now + randomDelay(70, 130);
            return;
        }

        int targetPerSlot = Math.min(64, ingotsInInv / 9);

        // Fill slots 1..9 with targetPerSlot
        if (craftGridStep == 0) {
            // Clear any non-gold-ingot items in grid
            boolean invalid = false;
            for (int i = 1; i <= 9; i++) {
                if (handler.getSlot(i).hasStack() && !handler.getSlot(i).getStack().isOf(Items.GOLD_INGOT)) {
                    invalid = true;
                    break;
                }
            }
            if (invalid) {
                clearCraftingGrid(client, handler);
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            int ingotSlot = findItemSlotInHandler(handler, Items.GOLD_INGOT, 10, handler.slots.size() - 1);
            if (ingotSlot == -1) {
                clearCraftingGrid(client, handler);
                craftingGoldBlocks = false;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(70, 130);
                return;
            }

            client.interactionManager.clickSlot(handler.syncId, ingotSlot, 0, SlotActionType.PICKUP, client.player);
            craftGridStep = 1;
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        // Steps 1..4: vanilla left-drag fill (1 packet/tick) + exact single-click
        // top-up. A drag spreads the held stack across the grid in ~11 packets
        // instead of hundreds of right-clicks; the top-up pass then makes every
        // slot exactly targetPerSlot, so the layout stays precise even if the
        // server drops a drag packet (singles finish the job like before).
        if (craftGridStep == 1) {
            java.util.List<Integer> needy = new java.util.ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                if (handler.getSlot(i).getStack().getCount() < targetPerSlot) {
                    needy.add(i);
                }
            }
            if (needy.isEmpty()) {
                craftGridStep = 10;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (handler.getCursorStack().isEmpty() || !handler.getCursorStack().isOf(Items.GOLD_INGOT)) {
                int nextIngotSlot = findItemSlotInHandler(handler, Items.GOLD_INGOT, 10, handler.slots.size() - 1);
                if (nextIngotSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, nextIngotSlot, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(45, 80);
                    return;
                }
                craftGridStep = 10;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (needy.size() < 2) {
                // A one-slot drag would dump the whole cursor — top-up directly.
                craftGridStep = 4;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            dragNeedy = needy.stream().mapToInt(Integer::intValue).toArray();
            dragPos = 0;
            dragStart(client, handler, dragNeedy[0]);
            craftGridStep = 2;
            nextActionAt = now + randomDelay(50, 85);
            return;
        }

        if (craftGridStep == 2) {
            if (!stillSameScreen(client, handler) || dragPos >= dragNeedy.length) {
                craftGridStep = 3;
                nextActionAt = now + randomDelay(40, 70);
                return;
            }
            dragAdd(client, handler, dragNeedy[dragPos++]);
            nextActionAt = now + randomDelay(45, 75);
            return;
        }

        if (craftGridStep == 3) {
            if (stillSameScreen(client, handler) && dragNeedy.length > 0) {
                dragEnd(client, handler, dragNeedy[dragNeedy.length - 1]);
            }
            craftGridStep = 4;
            // Let the server confirm the drag before verifying counts.
            nextActionAt = now + randomDelay(220, 320);
            return;
        }

        if (craftGridStep == 4) {
            // Exact top-up: one right-click per tick until every slot holds targetPerSlot.
            int shortSlot = -1;
            for (int i = 1; i <= 9; i++) {
                if (handler.getSlot(i).getStack().getCount() < targetPerSlot) {
                    shortSlot = i;
                    break;
                }
            }
            if (shortSlot == -1) {
                craftGridStep = 10;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (handler.getCursorStack().isEmpty()) {
                int nextIngotSlot = findItemSlotInHandler(handler, Items.GOLD_INGOT, 10, handler.slots.size() - 1);
                if (nextIngotSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, nextIngotSlot, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(45, 80);
                    return;
                }
                craftGridStep = 10;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (!handler.getCursorStack().isOf(Items.GOLD_INGOT)) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }
            client.interactionManager.clickSlot(handler.syncId, shortSlot, 1, SlotActionType.PICKUP, client.player);
            nextActionAt = now + randomDelay(50, 85);
            return;
        }

        // Step 10: Return leftover ingots to inventory
        if (craftGridStep == 10) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot == -1) freeSlot = 10;
                client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                nextActionAt = now + randomDelay(55, 100);
                return;
            }
            // All slots filled, cursor clean — harvest output
            craftGridStep = 11;
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        // Step 11: Harvest all gold blocks from output slot 0
        if (craftGridStep == 11) {
            Slot outputSlot = handler.getSlot(0);
            if (outputSlot != null && outputSlot.hasStack() && outputSlot.getStack().isOf(Items.GOLD_BLOCK)) {
                client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
                nextActionAt = now + randomDelay(55, 100);
                return;
            }

            // Output collected — check if we can craft more
            int ingots = countItemInInventory(client, Items.GOLD_INGOT);
            if (ingots >= 9) {
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
            } else {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                craftingGoldBlocks = false;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(70, 130);
            }
        }
    }

    private void handleCraftApples(MinecraftClient client, CraftingScreenHandler handler, long now) {

        // Calculate mass crafting amount
        int totalApples = countItemInInventory(client, Items.APPLE);
        if (handler.getSlot(5).hasStack() && handler.getSlot(5).getStack().isOf(Items.APPLE)) {
            totalApples += handler.getSlot(5).getStack().getCount();
        }
        if (handler.getCursorStack().isOf(Items.APPLE)) {
            totalApples += handler.getCursorStack().getCount();
        }

        int totalGold = countItemInInventory(client, mode.goldItem);
        for (int s : new int[]{1, 2, 3, 4, 6, 7, 8, 9}) {
            if (handler.getSlot(s).hasStack() && handler.getSlot(s).getStack().isOf(mode.goldItem)) {
                totalGold += handler.getSlot(s).getStack().getCount();
            }
        }
        if (handler.getCursorStack().isOf(mode.goldItem)) {
            totalGold += handler.getCursorStack().getCount();
        }

        int maxPossibleCrafts = Math.min(64, Math.min(totalApples, totalGold / 8));
        if (maxPossibleCrafts < 1) {
            // Check conversion
            if (mode == Mode.ENCHANTED_GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_INGOT) >= 9) {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                craftingGoldBlocks = true;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
                return;
            }
            if (mode == Mode.GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_BLOCK) >= 1) {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                decompressingGoldBlocks = true;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
                return;
            }

            if (!clearCraftingGrid(client, handler)) {
                nextActionAt = now + randomDelay(60, 110);
                return;
            }
            closeScreen(client);
            step = Step.OPEN_AH_STORAGE;
            nextActionAt = now + randomDelay(250, 450);
            return;
        }

        // Check if grid has invalid items (e.g. non-apple in 5 or non-gold in 1..4,6..9)
        boolean invalidGrid = false;
        if (handler.getSlot(5).hasStack() && !handler.getSlot(5).getStack().isOf(Items.APPLE)) {
            invalidGrid = true;
        }
        for (int s : new int[]{1, 2, 3, 4, 6, 7, 8, 9}) {
            if (handler.getSlot(s).hasStack() && !handler.getSlot(s).getStack().isOf(mode.goldItem)) {
                invalidGrid = true;
                break;
            }
        }
        if (invalidGrid) {
            clearCraftingGrid(client, handler);
            craftGridStep = 0;
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        // Step 0: Ensure cursor is empty or contains apples before starting
        if (craftGridStep == 0) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            int appleSlot = findItemSlotInHandler(handler, Items.APPLE, 10, handler.slots.size() - 1);
            if (handler.getSlot(5).getStack().getCount() < maxPossibleCrafts) {
                if (appleSlot == -1) {
                    if (!clearCraftingGrid(client, handler)) {
                        nextActionAt = now + randomDelay(60, 110);
                        return;
                    }
                    closeScreen(client);
                    step = Step.OPEN_AH_STORAGE;
                    nextActionAt = now + randomDelay(250, 450);
                    return;
                }
                client.interactionManager.clickSlot(handler.syncId, appleSlot, 0, SlotActionType.PICKUP, client.player);
                craftGridStep = 1;
            } else {
                craftGridStep = 3;
            }
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        // Step 1: Place apples in slot 5 until reaching maxPossibleCrafts.
        // Advancement is driven by the ACTUAL slot count, not by click
        // prediction, so a click dropped by the server is retried in place
        // instead of skipping ahead with a short stack.
        if (craftGridStep == 1) {
            int currentApplesInSlot = handler.getSlot(5).hasStack()
                    && handler.getSlot(5).getStack().isOf(Items.APPLE)
                    ? handler.getSlot(5).getStack().getCount() : 0;
            if (currentApplesInSlot >= maxPossibleCrafts) {
                craftGridStep = 2;
                nextActionAt = now + randomDelay(25, 50);
                return;
            }

            if (handler.getCursorStack().isEmpty()) {
                int nextAppleSlot = findItemSlotInHandler(handler, Items.APPLE, 10, handler.slots.size() - 1);
                if (nextAppleSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, nextAppleSlot, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(45, 90);
                    return;
                }
                craftGridStep = 2;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (!handler.getCursorStack().isOf(Items.APPLE)) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }
            client.interactionManager.clickSlot(handler.syncId, 5, 1, SlotActionType.PICKUP, client.player);
            nextActionAt = now + randomDelay(50, 85);
            return;
        }

        // Step 2: Return remaining apples to inventory
        if (craftGridStep == 2) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot == -1) freeSlot = 10;
                client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                nextActionAt = now + randomDelay(45, 90);
                return;
            }
            craftGridStep = 3;
            nextActionAt = now + randomDelay(40, 75);
            return;
        }

        // Step 3: Pick up gold stack
        if (craftGridStep == 3) {
            if (!handler.getCursorStack().isEmpty() && !handler.getCursorStack().isOf(mode.goldItem)) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }

            if (handler.getCursorStack().isEmpty()) {
                int goldSlot = findItemSlotInHandler(handler, mode.goldItem, 10, handler.slots.size() - 1);
                if (goldSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, goldSlot, 0, SlotActionType.PICKUP, client.player);
                    craftGridStep = 4;
                    nextActionAt = now + randomDelay(45, 90);
                    return;
                } else {
                    // Check if we need gold conversion
                    if (mode == Mode.ENCHANTED_GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_INGOT) >= 9) {
                        if (!clearCraftingGrid(client, handler)) {
                            nextActionAt = now + randomDelay(60, 110);
                            return;
                        }
                        craftingGoldBlocks = true;
                        craftGridStep = 0;
                        nextActionAt = now + randomDelay(55, 100);
                        return;
                    }
                    if (mode == Mode.GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_BLOCK) >= 1) {
                        if (!clearCraftingGrid(client, handler)) {
                            nextActionAt = now + randomDelay(60, 110);
                            return;
                        }
                        decompressingGoldBlocks = true;
                        craftGridStep = 0;
                        nextActionAt = now + randomDelay(55, 100);
                        return;
                    }
                    if (!clearCraftingGrid(client, handler)) {
                        nextActionAt = now + randomDelay(60, 110);
                        return;
                    }
                    closeScreen(client);
                    step = Step.OPEN_AH_STORAGE;
                    nextActionAt = now + randomDelay(250, 450);
                    return;
                }
            }
            craftGridStep = 4;
            nextActionAt = now + randomDelay(40, 75);
            return;
        }

        // Steps 4..7: vanilla left-drag fill of the gold ring (slots 1,2,3,4,6,7,8,9)
        // + exact single-click top-up. Same idea as gold-block compression:
        // the drag places the bulk fast and evenly, the top-up makes every
        // slot exactly maxPossibleCrafts.
        int[] goldGridSlots = {1, 2, 3, 4, 6, 7, 8, 9};
        if (craftGridStep == 4) {
            java.util.List<Integer> needy = new java.util.ArrayList<>();
            for (int gridSlot : goldGridSlots) {
                if (handler.getSlot(gridSlot).getStack().getCount() < maxPossibleCrafts) {
                    needy.add(gridSlot);
                }
            }
            if (needy.isEmpty()) {
                craftGridStep = 12;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (handler.getCursorStack().isEmpty() || !handler.getCursorStack().isOf(mode.goldItem)) {
                int nextGoldSlot = findItemSlotInHandler(handler, mode.goldItem, 10, handler.slots.size() - 1);
                if (nextGoldSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, nextGoldSlot, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(40, 75);
                    return;
                }
                craftGridStep = 12;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (needy.size() < 2) {
                craftGridStep = 7;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            dragNeedy = needy.stream().mapToInt(Integer::intValue).toArray();
            dragPos = 0;
            dragStart(client, handler, dragNeedy[0]);
            craftGridStep = 5;
            nextActionAt = now + randomDelay(50, 85);
            return;
        }

        if (craftGridStep == 5) {
            if (!stillSameScreen(client, handler) || dragPos >= dragNeedy.length) {
                craftGridStep = 6;
                nextActionAt = now + randomDelay(40, 70);
                return;
            }
            dragAdd(client, handler, dragNeedy[dragPos++]);
            nextActionAt = now + randomDelay(45, 75);
            return;
        }

        if (craftGridStep == 6) {
            if (stillSameScreen(client, handler) && dragNeedy.length > 0) {
                dragEnd(client, handler, dragNeedy[dragNeedy.length - 1]);
            }
            craftGridStep = 7;
            nextActionAt = now + randomDelay(220, 320);
            return;
        }

        if (craftGridStep == 7) {
            // Repeat the fast vanilla drag for every remaining group of slots.
            // The first drag puts 8 items into each slot; subsequent drags used
            // to fall back to hundreds of right-clicks, which made later stacks
            // appear to be laid out incorrectly and very slowly.
            java.util.List<Integer> needy = new java.util.ArrayList<>();
            for (int gridSlot : goldGridSlots) {
                if (handler.getSlot(gridSlot).getStack().getCount() < maxPossibleCrafts) {
                    needy.add(gridSlot);
                }
            }
            if (needy.isEmpty()) {
                craftGridStep = 12;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (handler.getCursorStack().isEmpty()) {
                int nextGoldSlot = findItemSlotInHandler(handler, mode.goldItem, 10, handler.slots.size() - 1);
                if (nextGoldSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, nextGoldSlot, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(40, 75);
                    return;
                }
                craftGridStep = 12;
                nextActionAt = now + randomDelay(30, 60);
                return;
            }
            if (!handler.getCursorStack().isOf(mode.goldItem)) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(45, 90);
                return;
            }
            if (needy.size() >= 2) {
                dragNeedy = needy.stream().mapToInt(Integer::intValue).toArray();
                dragPos = 0;
                dragStart(client, handler, dragNeedy[0]);
                craftGridStep = 5;
                nextActionAt = now + randomDelay(50, 85);
                return;
            }
            int shortSlot = needy.get(0);
            client.interactionManager.clickSlot(handler.syncId, shortSlot, 1, SlotActionType.PICKUP, client.player);
            nextActionAt = now + randomDelay(50, 85);
            return;
        }

        // Step 12: Return leftover gold to inventory
        if (craftGridStep == 12) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot == -1) freeSlot = 10;
                client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                nextActionAt = now + randomDelay(55, 100);
                return;
            }
            // All slots filled and cursor is clean — now collect output
            craftGridStep = 13;
            outputWaitUntil = now + 1500L;
            nextActionAt = now + randomDelay(45, 90);
            return;
        }

        // Step 13: Harvest output from slot 0. The server needs a moment to
        // confirm the recipe after the grid completes — if the output is not
        // there yet but the grid still holds ingredients, WAIT instead of
        // dumping the grid and closing the workbench (that loop caused the
        // "takes 1 item, re-lays, closes without apples" bug).
        if (craftGridStep == 13) {
            Slot outputSlot = handler.getSlot(0);
            if (outputSlot != null && outputSlot.hasStack() && outputSlot.getStack().isOf(mode.targetItem)) {
                // Shift-click to grab all output (one legit click per tick)
                client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
                outputWaitUntil = now + 900L;
                nextActionAt = now + randomDelay(120, 200);
                return;
            }

            // Output slot empty — decide what to do next
            int applesLeft = countItemInInventory(client, Items.APPLE);
            int goldLeft = countItemInInventory(client, mode.goldItem);

            // Check if any ingredients remain in grid (shouldn't but be safe)
            boolean hasGridItems = false;
            for (int i = 1; i <= 9; i++) {
                if (handler.getSlot(i).hasStack()) {
                    hasGridItems = true;
                    break;
                }
            }

            if (hasGridItems && now < outputWaitUntil) {
                // Recipe confirmation may still be on the way — keep waiting.
                nextActionAt = now + randomDelay(120, 200);
                return;
            }

            if (hasGridItems || (applesLeft >= 1 && goldLeft >= 8)) {
                // Still have ingredients, loop back for another batch
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
                return;
            }

            // No more ingredients — check if conversion is possible
            if (mode == Mode.ENCHANTED_GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_INGOT) >= 9 && applesLeft >= 1) {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                craftingGoldBlocks = true;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
                return;
            }
            if (mode == Mode.GOLDEN_APPLE && countItemInInventory(client, Items.GOLD_BLOCK) >= 1 && applesLeft >= 1) {
                if (!clearCraftingGrid(client, handler)) {
                    nextActionAt = now + randomDelay(60, 110);
                    return;
                }
                decompressingGoldBlocks = true;
                craftGridStep = 0;
                nextActionAt = now + randomDelay(55, 100);
                return;
            }

            // Completely finished crafting! Close workbench and go to auction
            if (!clearCraftingGrid(client, handler)) {
                nextActionAt = now + randomDelay(60, 110);
                return;
            }
            closeScreen(client);
            step = Step.OPEN_AH_STORAGE;
            nextActionAt = now + randomDelay(250, 450);
            timeoutAt = 0L;
        }
    }

    private void handleOpenAhStorage(MinecraftClient client, long now) {
        if (isStorageScreen(client)) {
            auctionFirstSeenAt = 0L;
            step = Step.CLAIM_STORAGE_APPLES;
            nextActionAt = now + randomDelay(350, 600);
            timeoutAt = now + 8000L;
            return;
        }

        if (isAuctionScreen(client)) {
            if (auctionFirstSeenAt == 0L) {
                auctionFirstSeenAt = now;
                nextActionAt = now + 3000L + randomDelay(100, 300);
                return;
            }
            if (now - auctionFirstSeenAt < 3000L) {
                return;
            }

            if (clickFirstNamedSlot(client, "хран", "storage", "мои лот", "мои товар", "склад")) {
                nextActionAt = now + randomDelay(400, 700);
                timeoutAt = now + 8000L;
            }
            return;
        }

        auctionFirstSeenAt = 0L;
        closeScreen(client);
        client.getNetworkHandler().sendChatCommand("ah");
        nextActionAt = now + randomDelay(450, 750);
        timeoutAt = now + 10000L;
    }

    private void handleClaimStorageApples(MinecraftClient client, long now) {
        if (!isStorageScreen(client)) {
            step = Step.OPEN_AH_STORAGE;
            nextActionAt = now + randomDelay(300, 500);
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        Slot targetAppleSlot = null;

        for (Slot slot : handler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) {
                continue;
            }
            if (slot.getStack().isOf(mode.targetItem)) {
                targetAppleSlot = slot;
                break;
            }
        }

        if (targetAppleSlot != null) {
            client.interactionManager.clickSlot(handler.syncId, targetAppleSlot.id, 0, SlotActionType.QUICK_MOVE, client.player);
            nextActionAt = now + randomDelay(200, 380);
            timeoutAt = now + 8000L;
            return;
        }

        // Storage is clear of target apples, proceed to /ah sellgui <price>
        closeScreen(client);
        step = Step.OPEN_SELL_GUI;
        sellGuiStep = 0;
        filledLotsThisSession = 0;
        filledSellSlotsThisSession.clear();
        nextActionAt = now + randomDelay(300, 500);
        timeoutAt = 0L;
    }

    private void handleOpenSellGui(MinecraftClient client, long now) {
        if (isSellGuiScreen(client)) {
            step = Step.PROCESS_SELL_GUI;
            sellGuiStep = 0;
            filledLotsThisSession = 0;
            filledSellSlotsThisSession.clear();
            nextActionAt = now + randomDelay(250, 450);
            timeoutAt = now + 12000L;
            return;
        }

        closeScreen(client);
        String cmd = (sellPrice != null && !sellPrice.isBlank()) ? "ah sellgui " + sellPrice.trim() : "ah sellgui";
        client.getNetworkHandler().sendChatCommand(cmd);
        nextActionAt = now + randomDelay(400, 700);
        timeoutAt = now + 6000L;
    }

    private void handleProcessSellGui(MinecraftClient client, long now) {
        if (!isSellGuiScreen(client)) {
            step = Step.OPEN_SELL_GUI;
            sellGuiStep = 0;
            nextActionAt = now + randomDelay(300, 500);
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;

        // Sub-state 0: Scan container for all valid/empty lot slots and pick up apples from player inventory
        if (sellGuiStep == 0) {
            sellGuiSlotIndex = -1;
            sellGuiConfirmSlot = -1;

            // Scan container slots (upper inventory)
            for (Slot slot : handler.slots) {
                if (slot == null || slot.inventory == client.player.getInventory()) {
                    continue;
                }
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) {
                    if (sellGuiSlotIndex == -1 && !filledSellSlotsThisSession.contains(slot.id)) {
                        sellGuiSlotIndex = slot.id;
                    }
                } else {
                    String name = stack.getName().getString().toLowerCase(Locale.ROOT);
                    if (name.contains("подтверд") || name.contains("выстав") || name.contains("confirm") || name.contains("готово") || name.contains("продать")) {
                        sellGuiConfirmSlot = slot.id;
                    } else if (name.contains("пуст") || name.contains("свободн") || name.contains("empty") || name.contains("добавить") || name.contains("положит") || name.contains("клик") || name.contains("слот")) {
                        if (sellGuiSlotIndex == -1 && !filledSellSlotsThisSession.contains(slot.id)) {
                            sellGuiSlotIndex = slot.id;
                        }
                    }
                }
            }

            // Find target apple slot in player's inventory
            int appleSlotInInv = -1;
            for (Slot slot : handler.slots) {
                if (slot != null && slot.inventory == client.player.getInventory() && slot.hasStack() && slot.getStack().isOf(mode.targetItem)) {
                    appleSlotInInv = slot.id;
                    break;
                }
            }

            boolean hasApples = (appleSlotInInv != -1) || (!handler.getCursorStack().isEmpty() && handler.getCursorStack().isOf(mode.targetItem));

            if (!hasApples || sellGuiSlotIndex == -1 || filledLotsThisSession >= maxLots) {
                // If we placed lots or have a confirm button, proceed to confirm/finish
                if (filledLotsThisSession > 0 || sellGuiConfirmSlot != -1) {
                    sellGuiStep = 3;
                    nextActionAt = now + randomDelay(150, 300);
                    return;
                }
                closeScreen(client);
                step = Step.CHECK_INVENTORY;
                sellGuiStep = 0;
                nextActionAt = now + randomDelay(300, 500);
                return;
            }

            if (handler.getCursorStack().isEmpty()) {
                client.interactionManager.clickSlot(handler.syncId, appleSlotInInv, 0, SlotActionType.PICKUP, client.player);
            }
            sellGuiStep = 1;
            nextActionAt = now + randomDelay(100, 180);
            return;
        }

        // Sub-state 1: fill sellGuiSlotIndex up to batchCount. Fast path: when
        // the cursor holds exactly what the lot still needs, one legit
        // left-click drops the whole batch at once. Otherwise single
        // right-clicks. The lot is marked filled only when its ACTUAL count
        // reaches batchCount, so dropped clicks are retried in place.
        if (sellGuiStep == 1) {
            if (sellGuiSlotIndex == -1) {
                sellGuiStep = 2;
                nextActionAt = now + randomDelay(80, 150);
                return;
            }
            Slot lotSlot = findSlotById(handler, sellGuiSlotIndex);
            if (lotSlot == null) {
                sellGuiStep = 0;
                nextActionAt = now + randomDelay(80, 140);
                return;
            }
            int inLot = lotSlot.hasStack() && lotSlot.getStack().isOf(mode.targetItem)
                    ? lotSlot.getStack().getCount() : 0;
            int available = countItemInInventory(client, mode.targetItem);
            if (handler.getCursorStack().isOf(mode.targetItem)) {
                available += handler.getCursorStack().getCount();
            }
            // Never put more than the configured batch in a lot. If only 13
            // apples were crafted and batchCount is 8, the target is exactly
            // 8 and the remaining 5 are returned to the inventory.
            int lotTarget = Math.min(batchCount, inLot + available);
            if (inLot >= lotTarget || inLot >= batchCount) {
                filledSellSlotsThisSession.add(sellGuiSlotIndex);
                sellGuiStep = 2;
                nextActionAt = now + randomDelay(80, 150);
                return;
            }
            if (handler.getCursorStack().isEmpty() || !handler.getCursorStack().isOf(mode.targetItem)) {
                // Pick up (more) apples
                int appleSlotInInv = -1;
                for (Slot slot : handler.slots) {
                    if (slot != null && slot.inventory == client.player.getInventory() && slot.hasStack() && slot.getStack().isOf(mode.targetItem)) {
                        appleSlotInInv = slot.id;
                        break;
                    }
                }
                if (appleSlotInInv != -1) {
                    client.interactionManager.clickSlot(handler.syncId, appleSlotInInv, 0, SlotActionType.PICKUP, client.player);
                    nextActionAt = now + randomDelay(80, 140);
                    return;
                }
                // No apples left anywhere: keep a partial lot, forget an empty one.
                if (inLot > 0) {
                    filledSellSlotsThisSession.add(sellGuiSlotIndex);
                }
                sellGuiStep = 2;
                nextActionAt = now + randomDelay(50, 100);
                return;
            }

            int need = lotTarget - inLot;
            if (handler.getCursorStack().getCount() == need) {
                client.interactionManager.clickSlot(handler.syncId, sellGuiSlotIndex, 0, SlotActionType.PICKUP, client.player);
                nextActionAt = now + randomDelay(90, 150);
                return;
            }
            client.interactionManager.clickSlot(handler.syncId, sellGuiSlotIndex, 1, SlotActionType.PICKUP, client.player);
            nextActionAt = now + randomDelay(55, 105);
            return;
        }

        // Sub-state 2: Return remaining cursor items to inventory, then loop for MORE lots!
        if (sellGuiStep == 2) {
            if (!handler.getCursorStack().isEmpty()) {
                int freeSlot = findFreeInvSlot(handler);
                if (freeSlot == -1) {
                    // Fallback to any player slot
                    for (Slot slot : handler.slots) {
                        if (slot != null && slot.inventory == client.player.getInventory()) {
                            if (!slot.hasStack() || (slot.getStack().isOf(mode.targetItem) && slot.getStack().getCount() < slot.getStack().getMaxCount())) {
                                freeSlot = slot.id;
                                break;
                            }
                        }
                    }
                }
                if (freeSlot != -1) {
                    client.interactionManager.clickSlot(handler.syncId, freeSlot, 0, SlotActionType.PICKUP, client.player);
                }
                nextActionAt = now + randomDelay(80, 160);
                return;
            }
            filledLotsThisSession++;

            // Loop back to step 0 to scan and fill the NEXT free slot in the same sellgui window!
            sellGuiStep = 0;
            nextActionAt = now + randomDelay(120, 220);
            return;
        }

        // Sub-state 3: Click confirm button if present, or close screen
        if (sellGuiStep == 3) {
            // Re-find confirm button if needed
            if (sellGuiConfirmSlot == -1) {
                for (Slot slot : handler.slots) {
                    if (slot != null && slot.hasStack() && slot.inventory != client.player.getInventory()) {
                        String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
                        if (name.contains("подтверд") || name.contains("выстав") || name.contains("confirm") || name.contains("готово") || name.contains("продать")) {
                            sellGuiConfirmSlot = slot.id;
                            break;
                        }
                    }
                }
            }

            if (sellGuiConfirmSlot != -1) {
                client.interactionManager.clickSlot(handler.syncId, sellGuiConfirmSlot, 0, SlotActionType.PICKUP, client.player);
                nextActionAt = now + randomDelay(400, 700);
            }

            closeScreen(client);
            step = Step.CHECK_INVENTORY;
            sellGuiStep = 0;
            filledLotsThisSession = 0;
            filledSellSlotsThisSession.clear();
            // Wait configured seconds after listing (+ up to 15% random variance for humanness)
            long waitMs = (long) (sellWaitSeconds * 1000L);
            long variance = (long) (waitMs * 0.15);
            nextActionAt = now + waitMs + randomDelay(0, variance + 500);
        }
    }

    private static boolean isStorageScreen(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) return false;
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("хран") || title.contains("storage") || title.contains("склад") || title.contains("мои лот");
    }

    private boolean isAuctionScreen(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) return false;
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("аук") || title.contains("auction") || title.equals("ah")
                || title.contains("поиск") || title.contains("search") || title.contains("лот")) {
            return true;
        }
        // This server sends a decorative/obfuscated title for /ah search.
        // The menu itself is still identifiable by its target item slots.
        if (client.player == null || client.player.currentScreenHandler instanceof CraftingScreenHandler
                || isStorageScreen(client) || isSellGuiScreen(client)) {
            return false;
        }
        if (currentBuyTarget == null || client.player.currentScreenHandler == null) {
            return false;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot != null && slot.hasStack() && slot.inventory != client.player.getInventory()
                    && slot.getStack().isOf(currentBuyTarget.item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSellGuiScreen(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?>)) return false;
        if (client.player != null && client.player.currentScreenHandler instanceof CraftingScreenHandler) return false;
        if (isStorageScreen(client)) return false;

        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("sellgui") || title.contains("sell gui") || title.contains("продаж") || title.contains("выстав") || title.contains("лот")) {
            return true;
        }

        // If we opened sellgui (step == OPEN_SELL_GUI or PROCESS_SELL_GUI) and the screen is a container handler that isn't main auction / storage
        if (step == Step.OPEN_SELL_GUI || step == Step.PROCESS_SELL_GUI) {
            return true;
        }

        return false;
    }

    private static boolean clickFirstNamedSlot(MinecraftClient client, String... names) {
        if (!(client.currentScreen instanceof HandledScreen<?> screen) || client.player == null || client.interactionManager == null) {
            return false;
        }
        ScreenHandler handler = screen.getScreenHandler();
        for (Slot slot : handler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) continue;
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            for (String needle : names) {
                if (name.contains(needle.toLowerCase(Locale.ROOT))) {
                    client.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, client.player);
                    return true;
                }
            }
        }
        return false;
    }

    private static int findItemSlotInHandler(ScreenHandler handler, Item item, int startSlot, int endSlot) {
        for (int i = startSlot; i <= endSlot && i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack() && slot.getStack().isOf(item)) {
                return slot.id;
            }
        }
        return -1;
    }

    private static int findFreeInvSlot(ScreenHandler handler) {
        int start = Math.max(0, handler.slots.size() - 36);
        for (int i = start; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && !slot.hasStack()) {
                return slot.id;
            }
        }
        return -1;
    }

    private static int countItemInInventory(MinecraftClient client, Item item) {
        if (client.player == null) return 0;
        PlayerInventory inv = client.player.getInventory();
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void closeScreen(MinecraftClient client) {
        if (client == null) return;
        if (ClientGuiProtection.isOpen(client)) return;
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        } else if (client.currentScreen != null) {
            client.setScreen(null);
        }
    }

    private static long randomDelay(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    /** Smooth client-side look direction; it never changes player position or sends movement commands. */
    private void tickHeadMovement(MinecraftClient client) {
        if (client.player == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();

        float currentYaw   = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        if (!Float.isNaN(lastAppliedYaw)) {
            float dyaw   = Math.abs(currentYaw   - lastAppliedYaw);
            float dpitch = Math.abs(currentPitch - lastAppliedPitch);
            if (dyaw > 180f) dyaw = 360f - dyaw;

            if (dyaw > 0.3f || dpitch > 0.3f) {
                headMovementPausedUntil = now + 2000L + rng.nextLong(2000L);
                baseYaw         = currentYaw;
                basePitch       = currentPitch;
                smoothYaw       = currentYaw;
                smoothPitch     = currentPitch;
                targetHeadYaw   = currentYaw;
                targetHeadPitch = currentPitch;
                tremorYaw       = 0f;
                tremorPitch     = 0f;
                nextHeadChangeAt = headMovementPausedUntil;
                nextTremorChangeAt = headMovementPausedUntil;
                lastAppliedYaw   = currentYaw;
                lastAppliedPitch = currentPitch;
                return;
            }
        }

        if (now < headMovementPausedUntil) {
            lastAppliedYaw   = currentYaw;
            lastAppliedPitch = currentPitch;
            return;
        }

        boolean screenOpen = client.currentScreen != null;

        if (now >= nextHeadChangeAt) {
            headPattern = HeadPattern.values()[rng.nextInt(HeadPattern.values().length)];
            float yawRange = screenOpen ? 18f : 48f;
            float pitchRange = screenOpen ? 6f : 18f;
            switch (headPattern) {
                case SCAN -> {
                    targetHeadYaw = baseYaw + (rng.nextFloat() * 2f - 1f) * yawRange;
                    targetHeadPitch = basePitch + (rng.nextFloat() * 2f - 1f) * pitchRange;
                }
                case SIDE_GLANCE -> {
                    targetHeadYaw = baseYaw + (rng.nextBoolean() ? 1f : -1f) * (screenOpen ? 10f : 26f);
                    targetHeadPitch = basePitch + (rng.nextFloat() * 6f - 3f);
                }
                case DOWNWARD_GLANCE -> {
                    targetHeadYaw = baseYaw + (rng.nextFloat() * 2f - 1f) * (screenOpen ? 9f : 20f);
                    targetHeadPitch = basePitch + 7f + rng.nextFloat() * (screenOpen ? 5f : 12f);
                }
                case RETURN_TO_ANCHOR -> {
                    targetHeadYaw = baseYaw;
                    targetHeadPitch = basePitch;
                }
            }
            targetHeadPitch = Math.max(-60f, Math.min(60f, targetHeadPitch));
            long interval = screenOpen ? 5000L + rng.nextLong(5500L) : 4500L + rng.nextLong(5500L);
            nextHeadChangeAt = now + interval;
        }

        float dyaw = targetHeadYaw - smoothYaw;
        while (dyaw > 180f)  dyaw -= 360f;
        while (dyaw < -180f) dyaw += 360f;
        float dpitch = targetHeadPitch - smoothPitch;

        // A low acceleration makes retargeting gradual instead of a visibly
        // abrupt constant-speed turn. The tiny offset changes only a few times
        // per second, eliminating frame-to-frame jitter.
        float yawStep = Math.min(HEAD_MAX_YAW_STEP, 0.10f + Math.abs(dyaw) * 0.035f);
        float pitchStep = Math.min(HEAD_MAX_PITCH_STEP, 0.07f + Math.abs(dpitch) * 0.035f);
        smoothYaw += Math.max(-yawStep, Math.min(yawStep, dyaw));
        smoothPitch += Math.max(-pitchStep, Math.min(pitchStep, dpitch));
        if (now >= nextTremorChangeAt) {
            tremorYaw = (rng.nextFloat() - 0.5f) * 0.08f;
            tremorPitch = (rng.nextFloat() - 0.5f) * 0.05f;
            nextTremorChangeAt = now + 350L + rng.nextLong(350L);
        }

        float finalYaw   = smoothYaw + tremorYaw;
        float finalPitch = Math.max(-60f, Math.min(60f, smoothPitch + tremorPitch));

        client.player.setYaw(finalYaw);
        client.player.setPitch(finalPitch);
        client.player.headYaw = finalYaw;

        lastAppliedYaw   = finalYaw;
        lastAppliedPitch = finalPitch;
    }

    private enum HeadPattern {
        SCAN,
        SIDE_GLANCE,
        DOWNWARD_GLANCE,
        RETURN_TO_ANCHOR
    }

    private enum Step {
        IDLE,
        CHECK_INVENTORY,
        BUY_SEARCH,
        BUY_PROCESS,
        BUY_CONFIRM,
        FIND_AND_OPEN_WORKBENCH,
        CRAFTING,
        OPEN_AH_STORAGE,
        CLAIM_STORAGE_APPLES,
        OPEN_SELL_GUI,
        PROCESS_SELL_GUI
    }
}
