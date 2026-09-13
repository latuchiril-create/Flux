package dev.fuga.fluxvisuals.multibot;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.baritone.BaritoneBridge;

import dev.fuga.fluxvisuals.modules.visual.ItemCrafter;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.AutoResellAFK;
import dev.fuga.fluxvisuals.mixin.WorldRendererAccessor;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.WorldLoadingState;
import net.minecraft.client.network.PlayerListEntry;
import dev.fuga.fluxvisuals.mixin.PlayerListEntryAccessor;
import dev.fuga.fluxvisuals.mixin.ClientPlayNetworkHandlerStateAccess;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.network.ClientConnection;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** A live main-account or background-bot connection and its client-side state. */
public final class BotSession {
    // Anti-AFK deliberately uses one ordinary vanilla hand swing. Detached
    // sessions must never synthesize look/position streams: the old rotation
    // implementation was indistinguishable from automated movement to FunAC.
    private static final long SAFE_ANTI_AFK_INTERVAL_MIN_MS = 75_000L;
    private static final long SAFE_ANTI_AFK_INTERVAL_MAX_MS = 135_000L;
    private static final long SAFE_ANTI_AFK_WARNING_COOLDOWN_MS = 30_000L;
    private static final long SAFE_ANTI_AFK_WARNING_DELAY_MIN_MS = 900L;
    private static final long SAFE_ANTI_AFK_WARNING_DELAY_MAX_MS = 2_200L;

    public enum State {
        CONNECTING,
        LOGGING_IN,
        PLAYING,
        DISCONNECTED
    }

    private final String name;
    private final boolean main;
    private volatile String address;
    private final long createdAt = System.currentTimeMillis();
    private final AutoBuy autoBuy;
    private final AutoResellAFK autoResellAFK;
    private final ItemCrafter itemCrafter;
    private final Map<String, Boolean> moduleStates = new HashMap<>();

    private volatile ClientConnection connection;
    private ClientWorld world;
    private ClientPlayerEntity player;
    private ClientPlayerInteractionManager interactionManager;
    private ClientPlayNetworkHandler networkHandler;
    private WorldLoadingState worldLoadingState;
    private InGameHud inGameHud;
    private Screen screen;
    private Entity cameraEntity;
    private HitResult crosshairTarget;
    private final List<Text> chatHistory = new ArrayList<>();
    private boolean chatHistoryNeedsRestore;
    private String lastHandledChatMessage = "";
    private long lastHandledChatAt;

    private volatile State state;
    private volatile String statusText = "";
    private long nextAutomationScanAt;
    private boolean automationInitialized;
    private boolean automationDefaultsPrepared;
    private boolean moduleStatesInitialized;
    private boolean mirroredAttackHeld;
    private boolean mirroredJumpHeld;
    private boolean readyAnnounced;
    private boolean disconnectionHandled;
    private int reconnectAttempts;
    private long nextReconnectAt;
    private volatile long reconnectBlockedUntil;
    private long connectionGeneration;
    private volatile long worldStabilizeUntil;
    private volatile boolean antiAfkEnabled;
    private volatile long nextSafeAntiAfkActionAt;
    private volatile long lastSafeAntiAfkActionAt;
    private volatile boolean gotoActive;
    private double gotoTargetX;
    private double gotoTargetY;
    private double gotoTargetZ;
    private final List<Vec3d> gotoPath = new ArrayList<>();
    private int gotoPathIndex;
    private long nextGotoPathRebuildAt;
    private int gotoStuckTicks;
    private Vec3d gotoLastPosition;
    private boolean gotoJump;
    private boolean inventoryDropInProgress;
    private boolean resumeAutoBuyAfterDrop;
    private long nextInventoryDropAt;
    private volatile boolean verificationCompletionAwaitingTransfer;
    private volatile long discardVerificationVehicleUntil;
    // Kept deliberately separate from ordinary trace rate limits. This is a
    // temporary high-detail flight investigation stream in the Desktop debug
    // file, sampled often enough to see the exact state before/after a world
    // transfer without making a log line for every game tick.
    private long lastFlightDiagnosticAt;
    private static final long FLIGHT_DIAGNOSTIC_INTERVAL_MS = 250L;

    private BotSession(String name, boolean main, String address) {
        this.name = name;
        this.main = main;
        this.address = address;
        this.state = main ? State.PLAYING : State.CONNECTING;
        this.autoBuy = main ? null : new AutoBuy();
        if (this.autoBuy != null) {
            this.autoBuy.setRentalPersistenceKey(name);
        }
        this.autoResellAFK = main ? null : new AutoResellAFK();
        this.itemCrafter = main ? null : new ItemCrafter();
        this.inGameHud = new InGameHud(MinecraftClient.getInstance());
        this.nextSafeAntiAfkActionAt = scheduleNextSafeAntiAfkAction(System.currentTimeMillis());
    }

    public static BotSession main(String name, String address) {
        return new BotSession(name, true, address);
    }

    public static BotSession bot(String name, String address) {
        return new BotSession(name, false, address);
    }

    /** Runs work against this session without making it the visible active session. */
    public void runWithContext(Runnable body) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld savedWorld = client.world;
        ClientPlayerEntity savedPlayer = client.player;
        ClientPlayerInteractionManager savedInteraction = client.interactionManager;
        InGameHud savedHud = ((MinecraftClientStateAccess) client)
                .fluxvisuals$getInGameHud();
        WorldLoadingState savedWorldLoadingState = getWorldLoadingState(savedPlayer == null ? null : savedPlayer.networkHandler);
        Screen savedScreen = client.currentScreen;
        Entity savedCamera = client.cameraEntity;
        HitResult savedCrosshair = client.crosshairTarget;
        boolean cursorLocked = client.mouse.isCursorLocked();
        BotSession previousContext = MultiBotManager.enterContext(this);

        client.world = world;
        client.player = player;
        client.interactionManager = interactionManager;
        ((MinecraftClientStateAccess) client).fluxvisuals$setInGameHud(inGameHud);
        setWorldLoadingState(networkHandler, worldLoadingState);
        client.currentScreen = screen;
        client.cameraEntity = (!main || cameraEntity == null || (world != null && cameraEntity.getWorld() != world)) ? player : cameraEntity;
        client.crosshairTarget = crosshairTarget;
        try {
            body.run();
        } finally {
            // State capture is best-effort.  It must never prevent restoration
            // of the visible account: a packet handler can transiently clear
            // vanilla globals while entering configuration, and an exception
            // here would otherwise leave the whole client on a null world.
            try {
                captureFromClient();
            } catch (Throwable error) {
                BotDebug.error("SESSION_CONTEXT_CAPTURE_FAILED", this,
                        "context restored despite capture failure", error);
            } finally {
                try {
                    boolean isActive = dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER != null
                            && dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession() == this;
                    if (isActive) {
                        installAsActive();
                    } else {
                        client.world = savedWorld;
                        client.player = savedPlayer;
                        client.interactionManager = savedInteraction;
                        ((MinecraftClientStateAccess) client).fluxvisuals$setInGameHud(savedHud);
                        setWorldLoadingState(savedPlayer == null ? null : savedPlayer.networkHandler, savedWorldLoadingState);
                        client.currentScreen = savedScreen;
                        client.cameraEntity = savedCamera;
                        client.crosshairTarget = savedCrosshair;
                        // Packet handlers such as GameJoin/Respawn can mutate
                        // renderer-owned world references while this detached
                        // session is installed. Restoring only client.world leaves
                        // a blank/foreign scene after the context is released.
                        if (savedWorld != null && client.worldRenderer instanceof WorldRendererAccessor renderer) {
                            if (renderer.fluxvisuals$getWorld() != savedWorld) {
                                client.worldRenderer.setWorld(savedWorld);
                                client.particleManager.setWorld(savedWorld);
                                client.gameRenderer.setWorld(savedWorld);
                                client.getBlockEntityRenderDispatcher().setWorld(savedWorld);
                                client.getEntityRenderDispatcher().setWorld(savedWorld);
                            }
                        }
                        if (cursorLocked != client.mouse.isCursorLocked()) {
                            if (cursorLocked) {
                                client.mouse.lockCursor();
                            } else {
                                client.mouse.unlockCursor();
                            }
                        }
                    }
                } finally {
                    MultiBotManager.leaveContext(previousContext);
                }
            }
        }
    }

    /** Captures the currently installed MinecraftClient globals into this session. */
    public void captureFromClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld previousWorld = world;

        // Sync networkHandler from the connection ground truth: after a
        // reconfiguration the connection's listener is replaced with a new
        // play handler, but client.player.networkHandler still points to
        // the old one until a new player is created by onGameJoin.
        if (connection != null && connection.isOpen()) {
            PacketListener listener = connection.getPacketListener();
            if (listener instanceof ClientPlayNetworkHandler playHandler) {
                networkHandler = playHandler;
            }
        }
        if (networkHandler == null
                && (connection == null || connection.getPacketListener() instanceof ClientPlayNetworkHandler)) {
            networkHandler = ((MinecraftClientStateAccess) client)
                    .fluxvisuals$getNetworkHandler();
        }

        boolean isActive = dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER != null
                && dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession() == this;
        boolean isCurrentContext = MultiBotManager.currentContextSession() == this;
        boolean canCaptureClient = isActive || isCurrentContext;

        if (canCaptureClient && client.world != null) {
            world = client.world;
        } else if (networkHandler != null && networkHandler.getWorld() != null) {
            world = networkHandler.getWorld();
        }

        if (canCaptureClient && client.player != null) {
            player = client.player;
        }

        boolean worldChanged = previousWorld != null && world != null && previousWorld != world;
        if (worldChanged) {
            if (verificationCompletionAwaitingTransfer) {
                verificationCompletionAwaitingTransfer = false;
                // Passenger packets may be delivered after GameJoin. Keep the
                // cleanup armed long enough to catch that late ghost boat.
                discardVerificationVehicleUntil = System.currentTimeMillis() + 15_000L;
            }
            worldStabilizeUntil = System.currentTimeMillis() + 8_000L;
            resetMovementAfterWorldTransition();
            traceFlightDiagnostic("world_changed", true);
        }
        if (client.interactionManager != null && canCaptureClient) {
            interactionManager = client.interactionManager;
        }

        if (main) {
            inGameHud = ((MinecraftClientStateAccess) client).fluxvisuals$getInGameHud();
        } else if (inGameHud == null) {
            inGameHud = new InGameHud(client);
        }
        worldLoadingState = getWorldLoadingState(networkHandler);
        restoreChatHistory();

        // In a detached bot context MinecraftClient.setScreen is intercepted
        // and writes directly to this session. The global currentScreen is
        // still the saved visible screen (often null), so copying it here
        // would immediately erase a newly opened background container.
        if (!(MultiBotManager.isBackgroundContext() && !main)) {
            screen = client.currentScreen;
        }
        if (screen instanceof DownloadingTerrainScreen || screen instanceof ReconfiguringScreen) {
            screen = null;
        }

        if (main) {
            cameraEntity = (canCaptureClient && client.cameraEntity != null) ? client.cameraEntity : (player != null ? player : cameraEntity);
        } else {
            cameraEntity = player;
        }
        if (canCaptureClient) {
            crosshairTarget = client.crosshairTarget;
        }
        if (state != State.DISCONNECTED && isReadyToControl() && isInPlayProtocol()) {
            state = State.PLAYING;
            statusText = "Готов";
        }
    }

    private void resetMovementAfterWorldTransition() {
        nextSafeAntiAfkActionAt = scheduleNextSafeAntiAfkAction(System.currentTimeMillis());
        lastSafeAntiAfkActionAt = 0L;
        mirroredAttackHeld = false;
        mirroredJumpHeld = false;
        gotoActive = false;
        gotoPath.clear();
        gotoPathIndex = 0;
        gotoJump = false;
        // A world transfer invalidates all container/automation references from
        // the previous world. Keeping them makes background bots issue clicks
        // against stale sync ids and appear completely desynchronized.
        inventoryDropInProgress = false;
        resumeAutoBuyAfterDrop = false;
        nextInventoryDropAt = 0L;
        automationInitialized = false;
        lastHandledChatMessage = "";
        lastHandledChatAt = 0L;
        crosshairTarget = null;
        cameraEntity = player;
        screen = null;
        // A transfer is server-authoritative. In particular, do not change
        // velocity, riding, abilities, effects, or game mode here: SpookyTime
        // sends those states across several packets while moving a verified
        // player from its boat through the hub to the destination world.
        BotDebug.trace("WORLD_MOVEMENT_RESET", this, "automation state cleared; player state preserved");
    }

    /** Installs this session as the visible session controlled by keyboard and mouse. */
    public void installAsActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen targetScreen = screen;
        client.world = world;
        client.player = player;
        client.interactionManager = interactionManager;
        ((MinecraftClientStateAccess) client).fluxvisuals$setInGameHud(inGameHud);
        if (client.inGameHud != null && client.inGameHud.getBossBarHud() != null) {
            client.inGameHud.getBossBarHud().clear();
        }
        setWorldLoadingState(networkHandler, worldLoadingState);
        restoreChatHistory();
        client.cameraEntity = (!main || cameraEntity == null || (world != null && cameraEntity.getWorld() != world)) ? player : cameraEntity;
        client.crosshairTarget = crosshairTarget;
        WorldRendererAccessor renderer = (WorldRendererAccessor) client.worldRenderer;
        boolean rendererChanged = renderer.fluxvisuals$getWorld() != world;
        if (rendererChanged || (world != null && renderer.fluxvisuals$getWorld() == null)) {
            client.worldRenderer.setWorld(world);
            client.particleManager.setWorld(world);
            client.gameRenderer.setWorld(world);
            client.getBlockEntityRenderDispatcher().setWorld(world);
        }
        // WorldRenderer can already point at this world after a transition,
        // while EntityRenderDispatcher still keeps the previous session's
        // world. Rebind it unconditionally so entity lookup/rendering follows
        // the visible bot even when the terrain renderer did not change.
        if (world != null) {
            client.getEntityRenderDispatcher().setWorld(world);
        }
        if (client.currentScreen != targetScreen) {
            client.setScreen(targetScreen);
        }
        if (player != null) {
            // Selection only changes which session is visible. It must never
            // rewrite state owned by the server (mode, flight, gravity or a
            // vehicle), otherwise a transfer can appear as a frozen hub.
            if (!(player.input instanceof KeyboardInput)) {
                player.input = new KeyboardInput(client.options);
            }
            client.options.jumpKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            player.input.playerInput = PlayerInput.DEFAULT;
            player.input.tick();
        }
        traceFlightDiagnostic("installed_as_active", true);
        if (targetScreen == null && !client.mouse.isCursorLocked()) {
            client.mouse.lockCursor();
        }
    }

    public void clearTransientScreen() {
        if (!(screen instanceof HandledScreen<?>)) {
            screen = null;
        }
    }

    private static WorldLoadingState getWorldLoadingState(ClientPlayNetworkHandler handler) {
        if (!(handler instanceof ClientPlayNetworkHandlerStateAccess access)) {
            return null;
        }
        return access.fluxvisuals$getWorldLoadingState();
    }

    private static void setWorldLoadingState(ClientPlayNetworkHandler handler, WorldLoadingState state) {
        if (handler instanceof ClientPlayNetworkHandlerStateAccess access) {
            access.fluxvisuals$setWorldLoadingState(state);
        }
    }

    /** Stores chat in this account's HUD history without sharing another session's messages. */
    public void rememberChat(Text message, boolean alreadyDisplayed) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        chatHistory.add(message);
        while (chatHistory.size() > 200) {
            chatHistory.remove(0);
        }
        if (!alreadyDisplayed && inGameHud != null && !chatHistoryNeedsRestore) {
            inGameHud.getChatHud().addMessage(message);
        }
    }

    /** Rejects only an immediate replay of the same S2C chat packet. */
    public boolean isDuplicateChatMessage(String message, long now) {
        String value = message == null ? "" : message;
        boolean duplicate = value.equals(lastHandledChatMessage)
                && now - lastHandledChatAt >= 0L
                && now - lastHandledChatAt <= 150L;
        lastHandledChatMessage = value;
        lastHandledChatAt = now;
        return duplicate;
    }

    public void rememberChat(String message, boolean alreadyDisplayed) {
        if (message != null) {
            rememberChat(Text.literal(message), alreadyDisplayed);
        }
    }

    private void restoreChatHistory() {
        if (!chatHistoryNeedsRestore || inGameHud == null) {
            return;
        }
        inGameHud.getChatHud().clear(true);
        for (Text line : chatHistory) {
            inGameHud.getChatHud().addMessage(line);
        }
        chatHistoryNeedsRestore = false;
    }

    /** Drops play-only state while the same connection changes protocol/world. */
    public void enterReconfiguration(Screen transitionScreen) {
        // Vanilla MinecraftClient.enterReconfiguration clears world, player
        // and interactionManager. ClientPlayNetworkHandler.onGameJoin relies
        // on player == null to create a player that belongs to its fresh
        // ClientWorld. Retaining the old snapshot here makes vanilla add the
        // old-world player to the new world and leaves the destination chunks
        // and collision state detached from the controlled entity.
        networkHandler = null;
        world = null;
        player = null;
        interactionManager = null;
        cameraEntity = null;
        crosshairTarget = null;
        // WorldLoadingState belongs to a single play handler. Reusing it after
        // configuration installs a stale loading gate on the new handler and
        // can leave DownloadingTerrainScreen open forever.
        worldLoadingState = null;
        screen = main ? transitionScreen : null;
        chatHistoryNeedsRestore = true;
        state = State.LOGGING_IN;
        worldStabilizeUntil = System.currentTimeMillis() + 8_000L;
        if (autoResellAFK != null && autoResellAFK.isEnabled()) {
            autoResellAFK.restartNow();
        }
        statusText = "Переключение мира";
        BotDebug.info("SESSION_RECONFIGURATION", this,
                "vanilla play state cleared; next GameJoin must create a fresh player/world pair");
    }

    public void setScreenFromBackground(Screen screen) {
        this.screen = screen;
    }

    public String getName() {
        return name;
    }

    public boolean isMain() {
        return main;
    }

    public String getAddress() {
        return address == null ? "" : address;
    }

    public void setAddress(String address) {
        if (address != null && !address.isBlank()) {
            this.address = address;
        }
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public void setConnection(ClientConnection connection) {
        this.connection = connection;
    }

    public ClientWorld getWorld() {
        return world;
    }

    public ClientPlayerEntity getPlayer() {
        return player;
    }

    public HitResult getCrosshairTarget() {
        return crosshairTarget;
    }

    public Screen getScreen() {
        return screen;
    }

    public ClientPlayerInteractionManager getInteractionManager() {
        return interactionManager;
    }

    public ClientPlayNetworkHandler getNetworkHandler() {
        if (connection != null && connection.isOpen()
                && connection.getPacketListener() instanceof ClientPlayNetworkHandler playHandler) {
            networkHandler = playHandler;
        }
        return networkHandler;
    }

    public void setNetworkHandler(ClientPlayNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public AutoBuy getAutoBuy() {
        return autoBuy;
    }

    public AutoResellAFK getAutoResellAFK() {
        return autoResellAFK;
    }

    public ItemCrafter getItemCrafter() {
        return itemCrafter;
    }

    /** Starts a normal client-side inventory drop for this bot only. */
    public boolean beginInventoryDrop() {
        if (main || !isReadyToControl() || player == null || interactionManager == null) {
            return false;
        }
        if (inventoryDropInProgress) {
            return true;
        }
        resumeAutoBuyAfterDrop = autoBuy.isEnabled();
        if (resumeAutoBuyAfterDrop) {
            autoBuy.setEnabledSilently(false);
        }
        inventoryDropInProgress = true;
        nextInventoryDropAt = 0L;
        BotDebug.info("INVENTORY_DROP_BEGIN", this,
                "resume_autobuy=" + resumeAutoBuyAfterDrop);
        return true;
    }

    /** Performs at most one ordinary THROW slot action per tick. */
    public boolean tickInventoryDrop(MinecraftClient client) {
        if (!inventoryDropInProgress) {
            return false;
        }
        if (client == null || player == null || interactionManager == null || !isInPlayProtocol()) {
            finishInventoryDrop();
            return false;
        }
        long now = System.currentTimeMillis();
        if (client.currentScreen != null) {
            if (client.currentScreen instanceof HandledScreen<?>) {
                player.closeHandledScreen();
            } else {
                client.setScreen(null);
            }
            nextInventoryDropAt = now + 150L;
            BotDebug.trace("INVENTORY_DROP_CLOSE_GUI", this, "screen_closed=true");
            return true;
        }
        if (now < nextInventoryDropAt) {
            return true;
        }

        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory != player.getInventory()
                    || slot.getIndex() < 0 || slot.getIndex() >= 36) {
                continue;
            }
            interactionManager.clickSlot(
                    player.currentScreenHandler.syncId,
                    slot.id,
                    1,
                    SlotActionType.THROW,
                    player
            );
            nextInventoryDropAt = now + 100L;
            BotDebug.trace("INVENTORY_DROP_SLOT", this,
                    "slot=" + slot.id + ", inventory_index=" + slot.getIndex());
            return true;
        }

        finishInventoryDrop();
        return false;
    }

    private void finishInventoryDrop() {
        if (!inventoryDropInProgress) {
            return;
        }
        inventoryDropInProgress = false;
        nextInventoryDropAt = 0L;
        boolean resume = resumeAutoBuyAfterDrop;
        resumeAutoBuyAfterDrop = false;
        if (resume && !autoBuy.isEnabled()) {
            autoBuy.setEnabledSilently(true);
        }
        BotDebug.info("INVENTORY_DROP_FINISH", this, "autobuy_resumed=" + resume);
    }

    public void prepareAutomationDefaults(AutoBuy autoBuySource, AutoResellAFK autoResellSource) {
        prepareAutomationDefaults(autoBuySource, autoResellSource, null);
    }

    public void prepareAutomationDefaults(AutoBuy autoBuySource, AutoResellAFK autoResellSource, ItemCrafter itemCrafterSource) {
        if (main || automationDefaultsPrepared) {
            return;
        }
        if (autoBuySource != null) {
            autoBuy.copySettingsFrom(autoBuySource);
        }
        if (autoResellSource != null) {
            autoResellAFK.copySettingsFrom(autoResellSource);
        }
        if (itemCrafterSource != null && itemCrafter != null) {
            itemCrafter.copySettingsFrom(itemCrafterSource);
        }
        automationDefaultsPrepared = true;
    }

    public void initializeAutomation(AutoBuy autoBuySource, AutoResellAFK autoResellSource) {
        initializeAutomation(autoBuySource, autoResellSource, null);
    }

    public void initializeAutomation(AutoBuy autoBuySource, AutoResellAFK autoResellSource, ItemCrafter itemCrafterSource) {
        if (main || automationInitialized) {
            return;
        }
        if (!automationDefaultsPrepared) {
            prepareAutomationDefaults(autoBuySource, autoResellSource, itemCrafterSource);
        }
        // Automation is opt-in per session. New modules start disabled, but
        // do not overwrite an explicit command such as `.bot resell` that was
        // issued before the first background tick initialized this session.
        automationInitialized = true;
    }

    public void initializeModuleStates(ModuleManager manager) {
        if (moduleStatesInitialized || manager == null) {
            return;
        }
        captureModuleStates(manager);
        moduleStatesInitialized = true;
    }

    public void captureModuleStates(ModuleManager manager) {
        if (manager == null) {
            return;
        }
        for (Module module : manager.getStatefulModules()) {
            if (module == manager.getAutoBuy() || module == manager.getAutoResellAFK()) {
                continue;
            }
            moduleStates.put(module.getName(), module.isEnabled());
        }
    }

    public void installModuleStates(ModuleManager manager) {
        if (!moduleStatesInitialized || manager == null) {
            return;
        }
        for (Module module : manager.getStatefulModules()) {
            if (module == manager.getAutoBuy() || module == manager.getAutoResellAFK()) {
                continue;
            }
            Boolean enabled = moduleStates.get(module.getName());
            if (enabled != null) {
                module.setEnabledSilently(enabled);
            }
        }
    }

    public void setModuleState(String moduleName, boolean enabled) {
        if (moduleName != null && !moduleName.isBlank()) {
            moduleStates.put(moduleName, enabled);
        }
    }

    /**
     * Schedules one ordinary vanilla hand swing.  This intentionally never
     * changes yaw/pitch and never calls ClientPlayerEntity.tick(): both paths
     * create synthetic movement packets in a detached session.
     */
    private void startPassiveLook(long now) {
        scheduleSafeAntiAfkAction(now + randomSafeAntiAfkDelay(
                SAFE_ANTI_AFK_WARNING_DELAY_MIN_MS, SAFE_ANTI_AFK_WARNING_DELAY_MAX_MS));
    }

    private void startPassiveLook(long now, String reason) {
        startPassiveLook(now);
        BotDebug.trace("ANTI_AFK_SCHEDULED", this, "reason=" + reason);
    }

    /** AFK warnings are informational; never answer them with synthetic input. */
    public void triggerAntiAfkLookFromServerMessage() {
        // The server warning itself must not cause a packet burst or a
        // deterministic hand/rotation response that can trip anti-cheat.
        BotDebug.trace("ANTI_AFK_WARNING_IGNORED", this, "transport=none");
    }

    /** The hidden primary is not managed by the bot anti-AFK switch. */
    public void triggerAntiAfkLookFromBackgroundSwitch() {
        // Keep the primary connection completely vanilla while hidden.
    }

    /** Stops background anti-AFK as soon as the user takes control of this bot. */
    public void cancelAntiAfkMovementForManualControl() {
        nextSafeAntiAfkActionAt = scheduleNextSafeAntiAfkAction(System.currentTimeMillis());
    }

    public boolean isAntiAfkEnabled() {
        return antiAfkEnabled;
    }

    public void setAntiAfkEnabled(boolean enabled) {
        antiAfkEnabled = enabled;
        lastSafeAntiAfkActionAt = 0L;
        nextSafeAntiAfkActionAt = scheduleNextSafeAntiAfkAction(System.currentTimeMillis());
        BotDebug.info("ANTI_AFK_STATE", this, "enabled=" + enabled + ", transport=connection_tick_only");
    }

    /**
     * Kept for command sequencing compatibility. Anti-AFK does not add a
     * command delay because it has no synthetic turn to wait for.
     */
    public long prepareAntiAfkAction() {
        return 0L;
    }

    /** Starts a session-local movement fallback for Baritone commands. */
    public void startGoto(double x, double y, double z) {
        if (!isReadyToControl() || !isInPlayProtocol()) {
            return;
        }
        gotoTargetX = x;
        gotoTargetY = y;
        gotoTargetZ = z;
        gotoActive = true;
        gotoPath.clear();
        gotoPathIndex = 0;
        gotoStuckTicks = 0;
        gotoLastPosition = player == null ? null : player.getPos();
        gotoJump = false;
        nextGotoPathRebuildAt = 0L;
        rebuildGotoPath();
        BotDebug.info("GOTO_FALLBACK_START", this,
                "x=" + x + ", y=" + y + ", z=" + z);
    }

    private boolean tickGotoMovement() {
        if (!gotoActive || player == null) {
            return false;
        }
        double dx = gotoTargetX - player.getX();
        double dz = gotoTargetZ - player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance <= 1.35D && Math.abs(gotoTargetY - player.getY()) <= 2.5D) {
            gotoActive = false;
            gotoPath.clear();
            gotoJump = false;
            BotDebug.info("GOTO_FALLBACK_FINISH", this,
                    "distance=" + String.format(java.util.Locale.ROOT, "%.2f", horizontalDistance));
            return false;
        }

        long now = System.currentTimeMillis();
        if ((gotoPath.isEmpty() && now >= nextGotoPathRebuildAt)
                || gotoPathIndex >= gotoPath.size() || now >= nextGotoPathRebuildAt) {
            rebuildGotoPath();
        }
        // Never fall back to blind forward movement. If the surrounding
        // chunks are not ready or A* cannot find a safe stand node yet, wait
        // for the scheduled rebuild instead of walking directly into a wall.
        if (gotoPath.isEmpty()) {
            gotoJump = false;
            return false;
        }
        while (gotoPathIndex < gotoPath.size()
                && player.getPos().squaredDistanceTo(gotoPath.get(gotoPathIndex)) <= 0.72D) {
            gotoPathIndex++;
        }
        if (gotoPathIndex >= gotoPath.size()) {
            nextGotoPathRebuildAt = 0L;
            return false;
        }
        Vec3d waypoint = gotoPath.get(gotoPathIndex);
        double waypointDx = waypoint.x - player.getX();
        double waypointDz = waypoint.z - player.getZ();
        double waypointDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-waypointDx, waypointDz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(waypoint.y - player.getY(),
                Math.max(0.001D, waypointDistance)));
        // Never snap detached players to a waypoint angle. Feed ordinary
        // vanilla ticks a bounded rotation delta so outgoing movement looks
        // like regular continuous input instead of a packet-level teleport.
        float yawDelta = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchTarget = Math.max(-25.0F, Math.min(25.0F, targetPitch));
        float pitchDelta = pitchTarget - player.getPitch();
        player.setYaw(player.getYaw() + Math.max(-18.0F, Math.min(18.0F, yawDelta)));
        player.setPitch(player.getPitch() + Math.max(-6.0F, Math.min(6.0F, pitchDelta)));

        // The route already contains collision-checked nodes. A forward
        // collision probe treats stairs and slabs as walls and made the bot
        // rebuild forever at the first valid step.
        gotoJump = waypoint.y > player.getY() + 0.35D;
        if (gotoLastPosition != null && player.getPos().squaredDistanceTo(gotoLastPosition) < 0.0064D) {
            gotoStuckTicks++;
        } else {
            gotoStuckTicks = 0;
            gotoLastPosition = player.getPos();
        }
        if (gotoStuckTicks >= 30) {
            gotoStuckTicks = 0;
            nextGotoPathRebuildAt = 0L;
            rebuildGotoPath();
        }
        return true;
    }

    /** Builds a small, session-local walk route so detached bots do not walk
     * straight through walls when the official Baritone primary is owned by
     * the visible Minecraft player. */
    private void rebuildGotoPath() {
        if (!gotoActive || world == null || player == null) {
            return;
        }
        BlockPos start = findStandNode(BlockPos.ofFloored(player.getX(), player.getY(), player.getZ()), 3);
        double totalDx = gotoTargetX - player.getX();
        double totalDz = gotoTargetZ - player.getZ();
        double totalDistance = Math.sqrt(totalDx * totalDx + totalDz * totalDz);
        double scale = totalDistance > 64.0D ? 64.0D / totalDistance : 1.0D;
        int goalX = (int) Math.floor(player.getX() + totalDx * scale);
        int goalZ = (int) Math.floor(player.getZ() + totalDz * scale);
        BlockPos goal = findStandNode(new BlockPos(goalX, (int) Math.floor(gotoTargetY), goalZ), 3);
        if (start == null || goal == null) {
            gotoPath.clear();
            gotoPathIndex = 0;
            nextGotoPathRebuildAt = System.currentTimeMillis() + 1_000L;
            return;
        }

        int minX = Math.min(start.getX(), goal.getX()) - 24;
        int maxX = Math.max(start.getX(), goal.getX()) + 24;
        int minZ = Math.min(start.getZ(), goal.getZ()) - 24;
        int maxZ = Math.max(start.getZ(), goal.getZ()) + 24;
        GridNode startNode = new GridNode(start.getX(), start.getY(), start.getZ());
        GridNode goalNode = new GridNode(goal.getX(), goal.getY(), goal.getZ());
        PriorityQueue<PathCandidate> open = new PriorityQueue<>(Comparator.comparingDouble(PathCandidate::score));
        Map<GridNode, Double> costs = new HashMap<>();
        Map<GridNode, GridNode> parents = new HashMap<>();
        open.add(new PathCandidate(startNode, 0.0D));
        costs.put(startNode, 0.0D);
        GridNode best = startNode;
        double bestHeuristic = heuristic(startNode, goalNode);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 8_000) {
            GridNode current = open.poll().node();
            double currentHeuristic = heuristic(current, goalNode);
            if (currentHeuristic < bestHeuristic) {
                best = current;
                bestHeuristic = currentHeuristic;
            }
            if (current.equals(goalNode)) {
                best = current;
                break;
            }
            for (int[] direction : PATH_DIRECTIONS) {
                int nx = current.x() + direction[0];
                int nz = current.z() + direction[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                    continue;
                }
                int ny = findStandY(nx, nz, current.y());
                if (ny == Integer.MIN_VALUE) {
                    continue;
                }
                GridNode next = new GridNode(nx, ny, nz);
                if (!canTraverse(current, next)) {
                    continue;
                }
                double nextCost = costs.getOrDefault(current, Double.POSITIVE_INFINITY) + 1.0D
                        + Math.abs(ny - current.y()) * 0.35D;
                if (nextCost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                costs.put(next, nextCost);
                parents.put(next, current);
                open.add(new PathCandidate(next, nextCost + heuristic(next, goalNode)));
            }
        }

        List<Vec3d> rebuilt = new ArrayList<>();
        if (best.equals(startNode) && !best.equals(goalNode)) {
            gotoPath.clear();
            gotoPathIndex = 0;
            nextGotoPathRebuildAt = System.currentTimeMillis() + 350L;
            BotDebug.trace("GOTO_PATH_BLOCKED", this,
                    "expanded=" + expanded + ", start=" + startNode + ", goal=" + goalNode);
            return;
        }
        GridNode cursor = best;
        while (cursor != null && !cursor.equals(startNode)) {
            rebuilt.add(new Vec3d(cursor.x() + 0.5D, cursor.y(), cursor.z() + 0.5D));
            cursor = parents.get(cursor);
        }
        java.util.Collections.reverse(rebuilt);
        gotoPath.clear();
        gotoPath.addAll(rebuilt);
        gotoPathIndex = 0;
        nextGotoPathRebuildAt = System.currentTimeMillis() + 1_200L;
        BotDebug.trace("GOTO_PATH_READY", this,
                "nodes=" + gotoPath.size() + ", expanded=" + expanded
                        + ", reached_goal=" + best.equals(goalNode));
    }

    private BlockPos findStandNode(BlockPos preferred, int radius) {
        int y = findStandY(preferred.getX(), preferred.getZ(), preferred.getY());
        if (y != Integer.MIN_VALUE) {
            return new BlockPos(preferred.getX(), y, preferred.getZ());
        }
        for (int distance = 1; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.abs(dx) != distance && Math.abs(dz) != distance) {
                        continue;
                    }
                    int candidateY = findStandY(preferred.getX() + dx, preferred.getZ() + dz, preferred.getY());
                    if (candidateY != Integer.MIN_VALUE) {
                        return new BlockPos(preferred.getX() + dx, candidateY, preferred.getZ() + dz);
                    }
                }
            }
        }
        return null;
    }

    private int findStandY(int x, int z, int aroundY) {
        int[] offsets = {0, 1, -1, 2, -2, -3, 3};
        for (int offset : offsets) {
            int y = aroundY + offset;
            BlockPos feet = new BlockPos(x, y, z);
            if (isStandable(feet)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isStandable(BlockPos feet) {
        if (!isPassable(feet) || !isPassable(feet.up()) || isPassable(feet.down())) {
            return false;
        }
        // Keep the full player volume clear. This prevents routes from
        // selecting one-block gaps whose corners clip the player hitbox.
        Box body = new Box(
                feet.getX() + 0.20D, feet.getY(), feet.getZ() + 0.20D,
                feet.getX() + 0.80D, feet.getY() + 1.80D, feet.getZ() + 0.80D);
        int minX = BlockPos.ofFloored(body.minX, body.minY, body.minZ).getX();
        int maxX = BlockPos.ofFloored(body.maxX - 1.0E-6D, body.maxY - 1.0E-6D, body.maxZ - 1.0E-6D).getX();
        // Include shapes protruding from the support block. This rejects
        // fences/walls as one-block steps while still allowing slabs/stairs.
        int minY = BlockPos.ofFloored(body.minX, body.minY - 1.0E-6D, body.minZ).getY();
        int maxY = BlockPos.ofFloored(body.maxX - 1.0E-6D, body.maxY - 1.0E-6D, body.maxZ - 1.0E-6D).getY();
        int minZ = BlockPos.ofFloored(body.minX, body.minY, body.minZ).getZ();
        int maxZ = BlockPos.ofFloored(body.maxX - 1.0E-6D, body.maxY - 1.0E-6D, body.maxZ - 1.0E-6D).getZ();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    BlockPos occupied = new BlockPos(bx, by, bz);
                    if (!world.getBlockState(occupied).getCollisionShape(world, occupied).isEmpty()
                            && world.getBlockState(occupied).getCollisionShape(world, occupied)
                            .getBoundingBox().offset(occupied.getX(), occupied.getY(), occupied.getZ())
                            .intersects(body)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean canTraverse(GridNode from, GridNode to) {
        if (Math.abs(to.y() - from.y()) > 1) {
            return false;
        }
        BlockPos fromFeet = new BlockPos(from.x(), from.y(), from.z());
        BlockPos toFeet = new BlockPos(to.x(), to.y(), to.z());
        if (!isStandable(fromFeet) || !isStandable(toFeet)) {
            return false;
        }
        // Cardinal neighbours have no diagonal corner to cut, but checking
        // the intermediate column catches wall faces at the block boundary.
        int midX = from.x() + Integer.signum(to.x() - from.x());
        int midZ = from.z() + Integer.signum(to.z() - from.z());
        int passageY = Math.max(from.y(), to.y());
        return isPassable(new BlockPos(midX, passageY, midZ))
                && isPassable(new BlockPos(midX, passageY + 1, midZ));
    }

    private boolean isPassable(BlockPos pos) {
        return world != null && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static double heuristic(GridNode first, GridNode second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z())
                + Math.abs(first.y() - second.y()) * 0.5D;
    }

    private static final int[][] PATH_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private record GridNode(int x, int y, int z) {
    }

    private record PathCandidate(GridNode node, double score) {
    }

    /** Applies the session-local goto fallback to the visible bot input. */
    public void tickActiveGotoMovement() {
        if (main || player == null || !isInPlayProtocol()) {
            return;
        }
        if (tickGotoMovement()) {
            player.setSprinting(true);
            player.input = new BackgroundInput(
                    new PlayerInput(true, false, false, false, gotoJump, false, true),
                    new Vec2f(0.0F, 1.0F)
            );
        } else if (player.input instanceof BackgroundInput) {
            player.input = new KeyboardInput(MinecraftClient.getInstance().options);
        }
    }

    /**
     * Kept as a compatibility hook for the manager. Server position/look
     * corrections are authoritative; writing a cached yaw/pitch back here
     * would undo those corrections and create an artificial rotation stream.
     */
    public void holdBackgroundRotation() {
        // Intentionally empty. The hidden session keeps the orientation sent
        // by the server until it is selected and rendered.
    }

    public void stabilizeActiveWorldLoad(MinecraftClient client) {
        if (main || player == null || client == null) {
            return;
        }
        boolean transitionScreen = client.currentScreen instanceof DownloadingTerrainScreen
                || client.currentScreen instanceof ReconfiguringScreen;
        if (transitionScreen) {
            traceFlightDiagnostic("active_transition_screen", false);
            return;
        }
        traceFlightDiagnostic("active_world_stable", false);
    }

    /** Prevents auction/anarchy automation from racing the server's lobby transfer. */
    public boolean isWorldStabilizing() {
        if (main) {
            return false;
        }
        if (!isReadyToControl() || !isInPlayProtocol()) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean transitionScreen = client != null
                && (client.currentScreen instanceof DownloadingTerrainScreen
                || client.currentScreen instanceof ReconfiguringScreen);
        // The timestamp is only a fallback while the new play handler is
        // being installed. Once PLAY is active and no transition screen is
        // present, never delay bot automation by an arbitrary wall-clock
        // stabilization window.
        return state == State.LOGGING_IN || transitionScreen;
    }

    /** FunTime uses a lobby_* dimension for the hub. Automation commands such
     * as /ah are invalid there and can trigger another server transfer. */
    public boolean isLobbyWorld() {
        if (world == null || world.getRegistryKey() == null
                || world.getRegistryKey().getValue() == null) {
            return false;
        }
        String path = world.getRegistryKey().getValue().getPath();
        return path != null && (path.startsWith("lobby") || path.contains("hub"));
    }

    /** Keeps an inactive session advancing without letting global keyboard state drive it. */
    public void tickBackgroundWorld(
            ClientPlayerEntity movementSource,
            PlayerInput mirroredInput,
            HitResult sourceTarget,
            boolean attackHeld
    ) {
        if (world == null || player == null || !isInPlayProtocol()) {
            return;
        }

        // mirroredInput is sampled directly from MinecraftClient options, so
        // do not depend on the visible player's Input object being installed.
        boolean mirrorControls = movementSource != null && mirroredInput != null;
        Input activeInput = player.input;
        PlayerInput playerInput = PlayerInput.DEFAULT;
        Vec2f movement = Vec2f.ZERO;
        if (mirrorControls) {
            playerInput = mirroredInput == null ? movementSource.input.playerInput : mirroredInput;
            float horizontal = (playerInput.right() ? 1.0F : 0.0F)
                    - (playerInput.left() ? 1.0F : 0.0F);
            float vertical = (playerInput.forward() ? 1.0F : 0.0F)
                    - (playerInput.backward() ? 1.0F : 0.0F);
            movement = new Vec2f(horizontal, vertical);
            if (movement.lengthSquared() > 1.0F) {
                movement = movement.normalize();
            }
            player.setYaw(movementSource.getYaw());
            player.setPitch(movementSource.getPitch());
            player.setSprinting(movementSource.isSprinting());
            player.setSneaking(movementSource.isSneaking());
        } else {
            player.setSprinting(false);
            player.setSneaking(false);
        }

        boolean gotoMoving = tickGotoMovement();
        tickSafeAntiAfk(System.currentTimeMillis(), mirrorControls, gotoMoving);
        if (gotoMoving) {
            playerInput = new PlayerInput(true, false, false, false, gotoJump, false, true);
            movement = new Vec2f(0.0F, 1.0F);
            player.setSprinting(true);
        }

        // Give every session its own vanilla keyboard input object. It reads
        // the same physical key bindings as the main client, but its state is
        // not shared with another player session.
        player.input = mirrorControls
                ? new KeyboardInput(MinecraftClient.getInstance().options)
                : new BackgroundInput(playerInput, movement);
        try {
            if (mirrorControls) {
                tickMirroredAttack(sourceTarget, attackHeld);
            } else if (mirroredAttackHeld) {
                interactionManager.cancelBlockBreaking();
                mirroredAttackHeld = false;
            }
            // A full detached ClientWorld tick is not protocol-neutral: it
            // advances extra client entities during the server's bot check.
            // Keep vanilla player movement/acknowledgements only; rendering
            // and whole-world simulation stay disabled for hidden sessions.
            if (player.isAlive()) {
                player.tick();
            }
            traceFlightDiagnostic("background_player_tick", false);
        } finally {
            player.input = activeInput;
        }
    }

    /** Detailed state sample for diagnosing the post-verification hover. */
    private void traceFlightDiagnostic(String point, boolean force) {
        if (main) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastFlightDiagnosticAt < FLIGHT_DIAGNOSTIC_INTERVAL_MS) {
            return;
        }
        lastFlightDiagnosticAt = now;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity currentPlayer = player;
            if (currentPlayer == null) {
                BotDebug.info("FLIGHT_DIAGNOSTIC", this,
                        "point=" + point + ", player=null, session_world=" + worldLabel(world)
                                + ", client_world=" + worldLabel(client == null ? null : client.world));
                return;
            }
            Entity vehicle = currentPlayer.getVehicle();
            Vec3d velocity = currentPlayer.getVelocity();
            PlayerAbilities abilities = currentPlayer.getAbilities();
            GameMode mode = interactionManager == null ? null : interactionManager.getCurrentGameMode();
            PlayerInput input = currentPlayer.input == null ? null : currentPlayer.input.playerInput;
            BlockPos feet = currentPlayer.getBlockPos();
            BlockPos below = feet.down();
            boolean feetChunkLoaded = world != null && world.isChunkLoaded(feet);
            boolean belowChunkLoaded = world != null && world.isChunkLoaded(below);
            String feetBlock = world == null ? "null" : world.getBlockState(feet).getBlock().getTranslationKey();
            String belowBlock = world == null ? "null" : world.getBlockState(below).getBlock().getTranslationKey();
            boolean floorCollision = false;
            if (world != null) {
                for (VoxelShape ignored : world.getBlockCollisions(
                        currentPlayer, currentPlayer.getBoundingBox().offset(0.0D, -0.0625D, 0.0D)
                )) {
                    floorCollision = true;
                    break;
                }
            }
            boolean active = FluxVisualsClient.MULTI_BOT_MANAGER != null
                    && FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession() == this;
            String details = "point=" + point
                    + ", active=" + active
                    + ", session_world=" + worldLabel(world)
                    + ", player_world=" + worldLabel(currentPlayer.getWorld())
                    + ", client_world=" + worldLabel(client == null ? null : client.world)
                    + ", renderer_world=" + worldLabel(client == null ? null
                    : ((WorldRendererAccessor) client.worldRenderer).fluxvisuals$getWorld())
                    + ", same_player_world=" + (world == currentPlayer.getWorld())
                    + ", same_client_world=" + (client != null && world == client.world)
                    + ", pos=" + vector(currentPlayer.getPos())
                    + ", velocity=" + vector(velocity)
                    + ", on_ground=" + currentPlayer.isOnGround()
                    + ", fall_distance=" + currentPlayer.fallDistance
                    + ", feet={pos=" + feet.toShortString()
                    + ", chunk_loaded=" + feetChunkLoaded
                    + ", block=" + feetBlock + "}"
                    + ", below={pos=" + below.toShortString()
                    + ", chunk_loaded=" + belowChunkLoaded
                    + ", block=" + belowBlock
                    + ", collision=" + floorCollision + "}"
                    + ", no_gravity=" + currentPlayer.hasNoGravity()
                    + ", no_clip=" + currentPlayer.noClip
                    + ", alive=" + currentPlayer.isAlive()
                    + ", age=" + currentPlayer.age
                    + ", mode=" + mode
                    + ", abilities={flying=" + abilities.flying
                    + ", allow_flying=" + abilities.allowFlying
                    + ", creative=" + abilities.creativeMode
                    + ", invulnerable=" + abilities.invulnerable
                    + ", fly_speed=" + abilities.getFlySpeed()
                    + ", walk_speed=" + abilities.getWalkSpeed() + "}"
                    + ", vehicle=" + entityLabel(vehicle)
                    + ", input=" + (currentPlayer.input == null ? "null" : currentPlayer.input.getClass().getSimpleName())
                    + ", keys=" + inputLabel(input)
                    + ", sneaking=" + currentPlayer.isSneaking()
                    + ", sprinting=" + currentPlayer.isSprinting()
                    + ", screen=" + (client == null || client.currentScreen == null
                    ? "null" : client.currentScreen.getClass().getSimpleName())
                    + ", camera=" + entityLabel(client == null ? null : client.cameraEntity);
            BotDebug.info("FLIGHT_DIAGNOSTIC", this, details);
        } catch (Throwable error) {
            BotDebug.error("FLIGHT_DIAGNOSTIC_FAILED", this, "point=" + point, error);
        }
    }

    private static String worldLabel(World value) {
        if (value == null || value.getRegistryKey() == null || value.getRegistryKey().getValue() == null) {
            return "null";
        }
        return value.getRegistryKey().getValue() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static String vector(Vec3d value) {
        return value == null ? "null" : value.x + "," + value.y + "," + value.z;
    }

    private static String entityLabel(Entity value) {
        if (value == null) {
            return "null";
        }
        return value.getType().getTranslationKey() + "#" + value.getId()
                + "{world=" + worldLabel(value.getWorld())
                + ",pos=" + vector(value.getPos())
                + ",alive=" + value.isAlive() + ",removed=" + value.isRemoved() + "}";
    }

    private static String inputLabel(PlayerInput input) {
        if (input == null) {
            return "null";
        }
        return "forward=" + input.forward() + ",backward=" + input.backward()
                + ",left=" + input.left() + ",right=" + input.right()
                + ",jump=" + input.jump() + ",sneak=" + input.sneak()
                + ",sprint=" + input.sprint();
    }

    /**
     * Drops a ghost vehicle left behind by an antibot boat ride + server
     * teleport. While the boat entity is alive and nearby the bot keeps
     * riding it (antibot check in progress). Once the server teleports the
     * player away the client-side boat stays at the old position: vanilla
     * then ticks vehicle physics, the player never falls and drifts slowly
     * horizontally. Distance/removed checks distinguish the two cases
     * without breaking a legitimate ongoing check.
     */
    private void dismountStaleVehicle() {
        if (player == null || !player.hasVehicle()) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            player.dismountVehicle();
            player.fallDistance = 0.0F;
            return;
        }
        // SpookyTime's verification boat is legitimate until /reg finishes.
        // The subsequent hub transfer does not always send a passenger-removal
        // packet to a detached client, so the cached boat looks alive and is
        // still nearby.  A hub is never a valid place to remain in that
        // verification vehicle; detach there so control resumes as ordinary
        // walking instead of boat physics being rendered as slow horizontal
        // flight.
        boolean stale = shouldDiscardVerificationVehicle()
                || isLobbyWorld()
                || vehicle.isRemoved() || !vehicle.isAlive()
                || (vehicle.getWorld() != null && player.getWorld() != null
                && vehicle.getWorld() != player.getWorld())
                || vehicle.squaredDistanceTo(player) > 144.0D;
        if (stale) {
            player.dismountVehicle();
            player.fallDistance = 0.0F;
            discardVerificationVehicleUntil = 0L;
            BotDebug.trace("STALE_VEHICLE_DROPPED", this,
                    "vehicle=" + vehicle.getType().getTranslationKey());
        }
    }

    /** Arms a one-transfer cleanup after SpookyTime confirms its boat check. */
    public void markVerificationCompleted() {
        if (!main) {
            verificationCompletionAwaitingTransfer = true;
        }
    }


    private boolean shouldDiscardVerificationVehicle() {
        long until = discardVerificationVehicleUntil;
        if (until == 0L) {
            return false;
        }
        if (System.currentTimeMillis() <= until) {
            return true;
        }
        discardVerificationVehicleUntil = 0L;
        return false;
    }

    /**
     * Advances only the hidden primary account's view. Calling player.tick()
     * here would run physics and emit unrelated movement packets, so this
     * path invokes vanilla's movement sender only when yaw/pitch actually
     * changes.
     */
    public void tickBackgroundLookOnly() {
        // No synthetic rotation packets are sent for a hidden account.
    }

    /** Advances a sparse anti-AFK action without touching movement state. */
    private void tickSafeAntiAfk(long now, boolean mirrorControls, boolean gotoMoving) {
        if (!antiAfkEnabled || main || mirrorControls || gotoMoving || player == null
                || !isInPlayProtocol() || connection == null || !connection.isOpen()
                || isWorldStabilizing() || isLobbyWorld() || now < nextSafeAntiAfkActionAt
                || now - lastSafeAntiAfkActionAt < SAFE_ANTI_AFK_WARNING_COOLDOWN_MS
                || player.isUsingItem() || player.currentScreenHandler != player.playerScreenHandler
                || MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>) {
            return;
        }
        // Do not synthesize movement, look, hand swings, or client ticks for
        // idle sessions. Their regular connection tick/keepalive stream is
        // still serviced by MultiBotManager.
        nextSafeAntiAfkActionAt = scheduleNextSafeAntiAfkAction(now);
        lastSafeAntiAfkActionAt = now;
        BotDebug.trace("ANTI_AFK_IDLE", this, "transport=connection_tick_only");
    }

    private void scheduleSafeAntiAfkAction(long at) {
        nextSafeAntiAfkActionAt = Math.max(nextSafeAntiAfkActionAt, at);
    }

    private static long scheduleNextSafeAntiAfkAction(long now) {
        return now + randomSafeAntiAfkDelay(
                SAFE_ANTI_AFK_INTERVAL_MIN_MS, SAFE_ANTI_AFK_INTERVAL_MAX_MS);
    }

    private static long randomSafeAntiAfkDelay(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    public boolean shouldTickBackgroundWorld(long nowNanos) {
        // MultiBotManager is already called exactly once from Fabric's
        // END_CLIENT_TICK. A second 50 ms wall-clock gate occasionally saw
        // 49.x ms and discarded a complete game tick, reducing detached
        // movement to 10 TPS and making it visibly jerk. Never throttle it.
        return true;
    }

    private void tickMirroredAttack(HitResult sourceTarget, boolean attackHeld) {
        if (interactionManager == null || sourceTarget == null || sourceTarget.getType() == HitResult.Type.MISS) {
            if (mirroredAttackHeld && !attackHeld && interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }
            mirroredAttackHeld = attackHeld;
            return;
        }

        boolean pressedThisTick = attackHeld && !mirroredAttackHeld;
        if (sourceTarget instanceof EntityHitResult entityHit) {
            Entity target = world.getEntityById(entityHit.getEntity().getId());
            if (pressedThisTick && target != null && target != player) {
                interactionManager.attackEntity(player, target);
            }
        } else if (sourceTarget instanceof BlockHitResult blockHit) {
            if (pressedThisTick) {
                interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
            } else if (!attackHeld && mirroredAttackHeld) {
                interactionManager.cancelBlockBreaking();
            }
            if (attackHeld) {
                interactionManager.updateBlockBreakingProgress(blockHit.getBlockPos(), blockHit.getSide());
            }
        }
        mirroredAttackHeld = attackHeld;
    }

    private static final class BackgroundInput extends Input {
        private BackgroundInput(PlayerInput playerInput, Vec2f movementVector) {
            this.playerInput = playerInput;
            this.movementVector = movementVector;
        }

        @Override
        public void tick() {
            // The input is a per-tick snapshot; polling global keys would merge session controls.
        }
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        if (this.state != state) {
            BotDebug.info("STATE_CHANGE", this, this.state + " -> " + state);
        }
        this.state = state;
    }

    public boolean markReadyAnnounced() {
        if (main || readyAnnounced || !isReadyToControl() || !isInPlayProtocol()) {
            return false;
        }
        readyAnnounced = true;
        return true;
    }

    public boolean markDisconnectionHandled() {
        if (disconnectionHandled) {
            return false;
        }
        disconnectionHandled = true;
        return true;
    }

    public boolean prepareReconnect(long now) {
        if (main || reconnectAttempts >= 3 || now < nextReconnectAt
                || now < reconnectBlockedUntil) {
            return false;
        }
        reconnectAttempts++;
        nextReconnectAt = now + reconnectAttempts * 2500L;
        state = State.CONNECTING;
        statusText = "Повторное подключение " + reconnectAttempts + "/3";
        connection = null;
        networkHandler = null;
        readyAnnounced = false;
        disconnectionHandled = false;
        return true;
    }

    /**
     * Blocks automatic reconnects until the given timestamp (e.g. after a
     * captcha/botfilter kick, where an instant reconnect storm flags the
     * whole IP and gets the main account kicked too).
     */
    public void delayReconnect(long untilMillis) {
        reconnectBlockedUntil = Math.max(reconnectBlockedUntil, untilMillis);
    }

    public long getReconnectDelayMillis() {
        return Math.max(1L, nextReconnectAt - System.currentTimeMillis());
    }

    public synchronized long beginConnectionGeneration() {
        this.state = State.CONNECTING;
        this.statusText = "Подключение...";
        this.disconnectionHandled = false;
        this.readyAnnounced = false;
        return ++connectionGeneration;
    }

    public synchronized boolean isConnectionGeneration(long generation) {
        return connectionGeneration == generation;
    }

    public synchronized void invalidateConnectionGeneration() {
        connectionGeneration++;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText == null ? "" : statusText;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isReadyToControl() {
        return world != null && player != null && interactionManager != null;
    }

    public boolean isInPlayProtocol() {
        if (connection == null || !connection.isOpen()) {
            return false;
        }
        PacketListener listener = connection.getPacketListener();
        if (listener instanceof ClientPlayNetworkHandler playHandler) {
            networkHandler = playHandler;
            return true;
        }
        return false;
    }

    public boolean shouldScanAutomation(long now) {
        // Called once from the normal client tick. Do not add a second
        // wall-clock throttle: it makes background AutoBuy/AutoResell run at
        // a different cadence from the visible account.
        nextAutomationScanAt = now;
        return true;
    }

    private volatile BotProxyConfig proxyConfig;
    private volatile long knownBalance = -1L;

    public BotProxyConfig getProxy() {
        return proxyConfig;
    }

    public void setProxy(BotProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    public long getKnownBalance() {
        if (knownBalance >= 0L) {
            return knownBalance;
        }
        if (autoBuy != null && autoBuy.getLastKnownBalance() >= 0L) {
            return autoBuy.getLastKnownBalance();
        }
        if (isMain()) {
            AutoBuy ab = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            if (ab != null && ab.getLastKnownBalance() >= 0L) {
                return ab.getLastKnownBalance();
            }
        }
        return -1L;
    }

    public void setKnownBalance(long balance) {
        if (balance >= 0L) {
            this.knownBalance = balance;
            if (this.autoBuy != null) {
                this.autoBuy.setLastKnownBalance(balance);
            }
        }
    }

    private volatile boolean chatMirrorEnabled;

    public boolean isChatMirrorEnabled() {
        return chatMirrorEnabled;
    }

    public void setChatMirrorEnabled(boolean chatMirrorEnabled) {
        this.chatMirrorEnabled = chatMirrorEnabled;
    }

    /** Per-bot CaptchaSolver switch (.bot function captchasolver). Default on. */
    private volatile boolean captchaSolverEnabled = true;

    public boolean isCaptchaSolverEnabled() {
        return captchaSolverEnabled;
    }

    public void setCaptchaSolverEnabled(boolean captchaSolverEnabled) {
        this.captchaSolverEnabled = captchaSolverEnabled;
    }
}
