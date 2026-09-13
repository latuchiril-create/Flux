package dev.fuga.fluxvisuals.multibot;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.mixin.WorldRendererAccessor;
import dev.fuga.fluxvisuals.modules.visual.ItemCrafter;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.AutoResellAFK;
import dev.fuga.fluxvisuals.modules.visual.Telegram;
import dev.fuga.fluxvisuals.baritone.BaritoneBridge;
import dev.fuga.fluxvisuals.modules.Module;
import io.netty.channel.ChannelFuture;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.util.ScreenshotRecorder;
import java.util.function.Consumer;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ClientConfigurationNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.state.LoginStates;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.effect.StatusEffects;

/** Owns background offline connections and the currently controlled account. */
public final class MultiBotManager {
    private static final String PREFIX = ".bot";
    private static final long SOCKET_CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long PAY_ALL_BOT_SPACING_MS = 450L;
    private static final int PAY_BALANCE_RETRY_LIMIT = 10;
    private static final ThreadLocal<BotSession> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ROUTING_MESSAGE = ThreadLocal.withInitial(() -> false);
    /**
     * Distinguishes the one connection tick owned by this manager from the
     * duplicate tick attempted by MinecraftClient after a bot is installed as
     * the visible account. ClientConnectionMixin may cancel only the latter.
     */
    private static final ThreadLocal<Boolean> MANAGER_CONNECTION_TICK =
            ThreadLocal.withInitial(() -> false);

    private final CopyOnWriteArrayList<BotSession> sessions = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<PacketListener, BotSession> listenerSessions = new ConcurrentHashMap<>();
    // Listener installation races with account switching. Track bot
    // connections by identity so protocol compatibility filtering works
    // during the whole connection lifecycle.
    private final ConcurrentMap<ClientConnection, BotSession> connectionSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<BotSession, PendingPayAll> pendingPayAll = new ConcurrentHashMap<>();
    private final ConcurrentMap<BotSession, String> pendingBotCommands = new ConcurrentHashMap<>();
    // Each connection owns a strict FIFO. Sharing and reprioritizing packets
    // between accounts breaks the ordering guaranteed by Netty and lets one
    // world transfer stall every other live session.
    private final ConcurrentMap<BotSession, ConcurrentLinkedDeque<BackgroundPacket>> sessionPacketQueues =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<BotSession, NetworkHealth> networkHealth = new ConcurrentHashMap<>();
    private final AtomicBoolean backgroundDrainScheduled = new AtomicBoolean();
    // Incoming packets use MinecraftClient's normal executor without any
    // per-session quota. Resource savings are render-only.
    private static final long CONNECTION_TICK_GAP_WARN_NANOS = 500_000_000L;
    private static final long CONNECTION_TICK_DURATION_WARN_NANOS = 50_000_000L;
    private static final long NETWORK_HEALTH_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private volatile BotSession activeSession;
    private volatile BotSession mainSession;
    private volatile BotSession pendingAutoSwitchSession;
    private volatile BotSession reconfiguringSession;
    private volatile String telegramTargetName = "main";
    private volatile boolean switching;
    private boolean playMode;
    private boolean initialized;
    private final ConcurrentMap<String, List<String>> botPresets = new ConcurrentHashMap<>();
    private final Path botPresetsPath = FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals-bot-presets.properties");
    private final Path botProxyPath = FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals-bot-proxy.properties");
    private volatile BotProxyConfig globalProxyConfig;
    private static final ThreadLocal<BotProxyConfig> CURRENT_CONNECTING_PROXY = new ThreadLocal<>();

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        loadBotPresets();
        loadBotProxyConfig();
        BotDebug.info("MANAGER_INIT", null, "automatic bot switching disabled");
        ClientSendMessageEvents.ALLOW_CHAT.register(this::allowChatMessage);
        ClientSendMessageEvents.ALLOW_COMMAND.register(this::allowCommandMessage);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerClientCommands(dispatcher));
    }

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (switching) {
            // Account hand-off may temporarily replace MinecraftClient's
            // visible world, but it must never pause socket servicing. Keep
            // every connection's normal Netty tick/keepalive alive during the
            // hand-off; skip only world/automation work until the next tick.
            tickManagedConnections();
            return;
        }
        // With no background accounts the vanilla client must own the whole
        // primary connection.  Do this check before ensureMainSession(): even
        // registering/capturing the primary session is unnecessary until a
        // bot command explicitly asks the manager to attach.  This keeps the
        // no-bot path completely vanilla while still letting addBot() create
        // the main session on demand.
        if (!hasManagedSessions()) {
            return;
        }
        ensureMainSession(client);
        // Repair the active bot's visible state before the null-guard early
        // return.  During a hub transfer vanilla enterReconfiguration can
        // temporarily null client.world/player/interactionManager.  Fix 1
        // cancels that for bot contexts, but this guard remains as a safety
        // net: if any client global is null while the bot session still has
        // valid state, re-install it immediately so the vanilla tick loop
        // keeps working.
        BotSession active = activeSession;
        if (active != null && !active.isMain() && active.isReadyToControl() && active.isInPlayProtocol()
                && !isVisibleStateFor(client, active)) {
            BotDebug.warn("ACTIVE_STATE_REPAIR", active,
                    "clientWorld=" + (client.world != null) + ", sessionWorld=" + (active.getWorld() != null)
                            + ", clientPlayer=" + (client.player != null) + ", sessionPlayer=" + (active.getPlayer() != null));
            active.installAsActive();
        }
        // Tick every managed socket exactly once even if the visible account
        // is between worlds. Login/configuration handlers, outbound flushing
        // and queued connection tasks must not depend on a PLAY world being
        // installed in the singleton MinecraftClient.
        tickManagedConnections();
        // Packet processing is independent for every connection and must
        // continue while the visible account temporarily has no world/player
        // during a server transfer. Apply queued world state only after all
        // sockets have been serviced so chunk work cannot delay keep-alives.
        drainBackgroundPackets();
        // The vanilla client is deliberately allowed to leave the server. Do
        // not restore a cached bot world after the main connection has begun
        // closing; doing so resurrects a half-disconnected network handler and
        // makes the render thread crash during the disconnect screen.
        if (client.world == null || client.player == null || client.getNetworkHandler() == null
                || client.getCurrentServerEntry() == null) {
            // A dimension transfer briefly clears vanilla globals. If a bot is
            // the selected visible session, restore its complete context before
            // returning; otherwise the render thread keeps a null/foreign world
            // and the camera renders an empty scene with detached hand input.
            if (active != null && !active.isMain() && active.isReadyToControl()
                    && active.isInPlayProtocol() && client.getCurrentServerEntry() != null) {
                active.installAsActive();
            }
            BotDebug.trace("CLIENT_NOT_IN_PLAY", activeSession,
                    "world=" + (client.world != null) + ", player=" + (client.player != null)
                            + ", handler=" + (client.getNetworkHandler() != null));
            return;
        }
        if (active == null) {
            return;
        }
        active.captureFromClient();

        for (BotSession session : sessions) {
            if (session.getState() == BotSession.State.DISCONNECTED) {
                continue;
            }
            if (session == active) {
                if (!session.isMain() && session.isInPlayProtocol()) {
                    tickConnection(session, false, null, PlayerInput.DEFAULT, null, false, false);
                    session.cancelAntiAfkMovementForManualControl();
                    session.tickActiveGotoMovement();
                    session.stabilizeActiveWorldLoad(client);
                    flushPendingBotCommand(session);
                    tickBotAutomation(client, session);
                }
                continue;
            }
            Runnable backgroundTick = () -> {
                boolean mirrorMainMovement = playMode && session != mainSession
                        && mainSession != null && mainSession.isReadyToControl();
                PlayerInput mirroredInput = mirrorMainMovement ? readPlayInput(client) : PlayerInput.DEFAULT;
                tickConnection(
                        session,
                        mirrorMainMovement,
                        mirrorMainMovement && mainSession != null ? mainSession.getPlayer() : null,
                        mirroredInput,
                        mirrorMainMovement && mainSession != null ? mainSession.getCrosshairTarget() : null,
                        mirrorMainMovement && client.options.attackKey.isPressed(),
                        true
                );
                if (!session.isMain()) {
                    if (!mirrorMainMovement) {
                        session.holdBackgroundRotation();
                    }
                }
                if (!session.isInPlayProtocol()) {
                    return;
                }
                // Minecraft sends ClientTickEndC2SPacket for the visible
                // account at the end of every client tick. Detached sessions
                // do not pass through that vanilla path, so send the same
                // boundary explicitly. Without it FunTime can treat a healthy
                // socket as a stalled/high-ping client and move it to lobby.
                sendManagedTickEnd(session);
                flushPendingBotCommand(session);
                if (session.isMain()) {
                    // When the primary account is visible, ModuleManager is
                    // the single owner of its AutoBuy/AutoResell tick.  The
                    // manager only needs to run the hidden primary workflow
                    // while a bot is being controlled; otherwise commands
                    // and screen clicks would be emitted twice per client
                    // tick.
                    if (activeSession != mainSession) {
                        tickMainAutomation(client);
                    }
                } else {
                    tickBotAutomation(client, session);
                }
            };
            // Every non-visible session, including main while a bot is active,
            // must run with its own client/world/network context. Otherwise
            // the player tick reads the visible bot's client state and the
            // main connection becomes effectively idle.
            session.runWithContext(backgroundTick);
        }
        // Never capture blindly here. A background login/transfer must not be
        // allowed to replace the active session's cached world with whatever
        // leaked into MinecraftClient for one tick.
        if (!active.isMain() && active.isReadyToControl() && active.isInPlayProtocol()
                && !isVisibleStateFor(client, active)) {
            BotDebug.warn("ACTIVE_STATE_REPAIR_AFTER_BACKGROUND", active,
                    "clientWorld=" + (client.world != null) + ", clientPlayer=" + (client.player != null));
            active.installAsActive();
        }
        active.captureFromClient();
        if (!active.isMain() && active.isReadyToControl() && active.isInPlayProtocol()
                && !isVisibleStateFor(client, active)) {
            active.installAsActive();
        }
        for (BotSession session : sessions) {
            if (session.markReadyAnnounced()) {
                feedback(client, "Бот " + session.getName() + " подключился к серверу. Управление не переключено автоматически.", Formatting.GREEN);
                BotDebug.info("BOT_READY", session, "address=" + session.getAddress());
            }
        }
        BotSession pendingSwitch = pendingAutoSwitchSession;
        if (pendingSwitch != null) {
            if (pendingSwitch.getState() == BotSession.State.DISCONNECTED) {
                pendingAutoSwitchSession = null;
            } else if (pendingSwitch.isReadyToControl() && pendingSwitch.isInPlayProtocol()) {
                pendingAutoSwitchSession = null;
                switchTo(pendingSwitch);
                feedback(client, "Подключено. Управление переключено на " + pendingSwitch.getName() + ".", Formatting.GREEN);
            }
        }
        BotSession currentActive = activeSession;
        if (currentActive != null && currentActive.getState() == BotSession.State.DISCONNECTED) {
            BotSession fallback = findReadyFallback(currentActive);
            if (fallback != null) {
                switchTo(fallback);
            }
        }
    }

    public void shutdown() {
        BotSession active = activeSession;
        if (active != null) {
            active.captureModuleStates(FluxVisualsClient.MODULE_MANAGER);
        }
        if (mainSession != null) {
            mainSession.installModuleStates(FluxVisualsClient.MODULE_MANAGER);
        }
        for (BotSession session : sessions) {
            if (!session.isMain()) {
                disconnect(session, "Клиент закрывается");
            }
        }
        sessionPacketQueues.clear();
        networkHealth.clear();
        pendingBotCommands.clear();
        sessions.clear();
        listenerSessions.clear();
        connectionSessions.clear();
        pendingAutoSwitchSession = null;
        telegramTargetName = "main";
        activeSession = null;
        mainSession = null;
    }

    public boolean shouldSkipGlobalAutomation() {
        BotSession active = activeSession;
        return active != null && !active.isMain();
    }

    /** True while the multi-account layer owns any session lifecycle state. */
    public boolean hasManagedSessions() {
        return switching || sessions.stream().anyMatch(session ->
                !session.isMain() && session.getState() != BotSession.State.DISCONNECTED);
    }

    public void beginBotReconfiguration(Screen transitionScreen) {
        BotSession session = CONTEXT.get();
        // A vanilla reconfiguration callback without a session context belongs
        // to the visible Minecraft connection. Falling back to activeSession
        // here can clear a background bot when the main account changes worlds.
        if (session != null && !session.isMain()) {
            reconfiguringSession = session;
            session.enterReconfiguration(transitionScreen);
        }
    }

    public void finishBotReconfiguration() {
        // The session intentionally remains empty until GameJoin. Reinstalling
        // its old snapshot here breaks vanilla's player == null contract.
        reconfiguringSession = null;
    }

    public boolean isBotRendererTransition() {
        return reconfiguringSession != null;
    }

    /** Renderer-owned globals are singletons; only the visible session may mutate them. */
    public boolean isVisibleRendererWorld(World candidate) {
        BotSession active = activeSession;
        if (candidate == null || active == null || candidate == active.getWorld()) {
            return true;
        }
        for (BotSession session : sessions) {
            if (session != active && candidate == session.getWorld()) {
                return false;
            }
        }
        // A world not owned by another session is a legitimate replacement
        // for the active connection during respawn/server transfer.
        return true;
    }

    public static boolean shouldBlockNonVisibleRendererWorld(World candidate) {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        return isBackgroundContext() || (manager != null && !manager.isVisibleRendererWorld(candidate));
    }

    /** Solver fallback: solver failed/timed out, ask the user to solve manually. */
    public void requestManualCaptcha(BotSession session) {
        if (session == null || session.isMain()
                || session.getState() == BotSession.State.DISCONNECTED) {
            return;
        }
        if (session.isReadyToControl() && session.isInPlayProtocol()) {
            pendingAutoSwitchSession = session;
            BotDebug.info("CAPTCHA_MANUAL_FALLBACK", session, "auto_switch_pending=true");
        }
    }

    public BotSession getActiveSession() {
        return activeSession;
    }

    public boolean isManagedBotName(String name) {
        if (name == null || name.isBlank()) return false;
        return sessions.stream().anyMatch(session -> !session.isMain()
                && session.getState() != BotSession.State.DISCONNECTED
                && session.getName().equalsIgnoreCase(name));
    }

    public String getActiveSessionName() {
        BotSession active = activeSession;
        return active == null ? "" : active.getName();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    /** Names that belong to the primary account or one of its managed bots. */
    public java.util.Set<String> getManagedPlayerNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (BotSession session : sessions) {
            if (session.getState() != BotSession.State.DISCONNECTED && session.getName() != null) {
                names.add(session.getName());
            }
        }
        BotSession main = mainSession;
        if (main != null && main.getName() != null) {
            names.add(main.getName());
        }
        return names;
    }

    public AutoBuy getAutoBuyForCurrentSession(AutoBuy global) {
        BotSession session = CONTEXT.get();
        if (session == null) {
            session = activeSession;
        }
        return session != null && !session.isMain() ? session.getAutoBuy() : global;
    }

    public AutoResellAFK getAutoResellAFKForCurrentSession(AutoResellAFK global) {
        BotSession session = CONTEXT.get();
        if (session == null) {
            session = activeSession;
        }
        return session != null && !session.isMain() ? session.getAutoResellAFK() : global;
    }

    public ItemCrafter getItemCrafterForCurrentSession(ItemCrafter global) {
        BotSession session = CONTEXT.get();
        if (session == null) {
            session = activeSession;
        }
        return session != null && !session.isMain() ? session.getItemCrafter() : global;
    }

    public boolean isBackgroundListener(PacketListener listener) {
        BotSession session = findSession(listener);
        return session != null && session != activeSession && CONTEXT.get() != session;
    }

    public boolean isManagedListener(PacketListener listener) {
        if (!hasManagedSessions()) {
            return false;
        }
        BotSession session = findSession(listener);
        if (session == null || CONTEXT.get() == session) {
            return false;
        }
        if (!session.isMain() || session != activeSession) {
            return true;
        }
        // The visible primary connection must return to vanilla processing.
        // During the short account-view swap it remains managed so packets
        // cannot overtake the FIFO accumulated while main was hidden.
        return switching;
    }

    public boolean isManagedBotHandler(PacketListener listener) {
        if (listener == null || !hasManagedSessions()) {
            return false;
        }
        for (BotSession session : sessions) {
            if (!session.isMain() && session.getNetworkHandler() == listener) {
                return true;
            }
            ClientConnection connection = session.getConnection();
            // The primary account uses MinecraftClient's real handler and
            // must never be routed through BotSession.runWithContext(). A
            // main-world respawn would then be followed by restoring its
            // cached hub world, producing the frozen-in-air hub view while
            // chat from the new server continued to arrive.
            if (!session.isMain() && connection != null && connection.getPacketListener() == listener) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSkipConnectionTick(ClientConnection connection) {
        return false;
    }

    public boolean isManagerConnectionTicking() {
        return Boolean.TRUE.equals(MANAGER_CONNECTION_TICK.get());
    }

    private void tickConnectionAsManager(ClientConnection connection) {
        boolean wasManagerTick = Boolean.TRUE.equals(MANAGER_CONNECTION_TICK.get());
        MANAGER_CONNECTION_TICK.set(true);
        try {
            connection.tick();
        } finally {
            if (wasManagerTick) {
                MANAGER_CONNECTION_TICK.set(true);
            } else {
                MANAGER_CONNECTION_TICK.remove();
            }
        }
    }

    /**
     * Returns true if the given play handler belongs to a non-main bot
     * session that is currently in the configuration phase (not in play
     * protocol). During config transitions vanilla continues to tick the
     * old play handler and sends {@code ClientTickEndC2SPacket}; the
     * server-side config codec does not understand play packets and would
     * disconnect the bot.
     */
    public boolean shouldSuppressBotTickEnd(ClientPlayNetworkHandler handler) {
        BotSession session = findSession(handler);
        if (session == null || session.isMain()) {
            return false;
        }
        // A normal 1.21.8 PLAY connection sends this packet every client
        // tick. Suppressing it made bot traffic differ from the primary
        // account and let FunTime treat otherwise healthy sessions as
        // stalled. Only block the stale play handler during configuration.
        return !session.isInPlayProtocol();
    }

    public boolean shouldSuppressBotTickEnd(PacketListener listener) {
        BotSession session = findSession(listener);
        return session != null && !session.isMain() && !session.isInPlayProtocol();
    }

    /**
     * Returns true if the given connection belongs to a non-main bot
     * session that is currently in the configuration phase (not in play
     * protocol).
     */
    public boolean shouldSuppressBotTickEnd(ClientConnection connection) {
        if (connection == null) {
            return false;
        }
        BotSession session = connectionSessions.get(connection);
        if (session == null) {
            for (BotSession s : sessions) {
                if (s.getConnection() == connection) {
                    session = s;
                    break;
                }
            }
        }
        if (session == null || session.isMain()) {
            return false;
        }
        return !session.isInPlayProtocol();
    }

    /** Prevent stale play packets from being encoded while a bot connection
     * is in the configuration protocol and its old player is still rendered. */
    public boolean shouldSuppressBotPlayPacket(ClientConnection connection, Packet<?> packet) {
        if (connection == null) {
            return false;
        }
        BotSession session = connectionSessions.get(connection);
        if (session == null) {
            for (BotSession candidate : sessions) {
                if (candidate.getConnection() == connection) {
                    session = candidate;
                    break;
                }
            }
        }
        if (session == null || session.isMain() || session.isInPlayProtocol()) {
            return false;
        }
        if (packet == null || packet.getPacketType() == null || packet.getPacketType().id() == null) {
            return true;
        }
        // Packets that change the protocol state must always reach the current
        // encoder. In particular, AcknowledgeReconfigurationC2SPacket causes
        // vanilla to install the temporary outbound configuration handler.
        // Blocking it leaves the old PLAY encoder in the pipeline; the next
        // transition then writes an EncoderTransitioner into a ByteBuf-only
        // pipeline and disconnects the bot.
        if (packet.transitionsNetworkState()) {
            return false;
        }
        String path = packet.getPacketType().id().getPath();
        // These packets are valid while the connection speaks LOGIN/
        // CONFIGURATION. Every movement, interaction and chat packet belongs
        // to PLAY and must not be encoded against the configuration codec.
        return switch (path) {
            // Handshake/login must pass through this filter. Suppressing the
            // first handshake leaves the protocol transitioner at the raw
            // Netty socket instead of installing the LOGIN encoder.
            case "intention", "hello", "key", "custom_query_answer", "login_acknowledged",
                    "ready", "finish_configuration", "select_known_packs", "client_information", "custom_payload",
                    "resource_pack", "cookie_response", "keep_alive", "pong" -> false;
            default -> true;
        };
    }


    /** Records server latency probes before vanilla answers them. */
    public void observeInboundLatencyPacket(PacketListener listener, Packet<?> packet) {
        BotSession session = findSession(listener);
        if (session == null || session.isMain() || (!(packet instanceof KeepAliveS2CPacket)
                && !(packet instanceof CommonPingS2CPacket))) {
            return;
        }
        long now = System.nanoTime();
        NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
        health.lastInboundLatencyNanos = now;
        health.lastInboundLatencyKind = packet instanceof KeepAliveS2CPacket ? "keep_alive" : "common_ping";
        BotDebug.trace("LATENCY_PACKET_IN", session, "packet=" + health.lastInboundLatencyKind);
    }

    /** Records the actual latency reply as it enters ClientConnection. */
    public void observeOutboundLatencyPacket(ClientConnection connection, Packet<?> packet) {
        if (!(packet instanceof KeepAliveC2SPacket) && !(packet instanceof CommonPongC2SPacket)) {
            return;
        }
        BotSession session = findSession(connection);
        if (session == null || session.isMain()) {
            return;
        }
        long now = System.nanoTime();
        NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
        health.lastOutboundLatencyNanos = now;
        health.lastOutboundLatencyKind = packet instanceof KeepAliveC2SPacket ? "keep_alive" : "pong";
        BotDebug.trace("LATENCY_PACKET_OUT", session, "packet=" + health.lastOutboundLatencyKind);
    }

    public boolean enqueueBackgroundPacket(Packet<?> packet, PacketListener listener) {
        // Route bot login/configuration packets through the same session context as
        // play packets.  Login handlers call MinecraftClient methods (and may open
        // the disconnect screen); running them against the visible account used to
        // kick the user to the main menu while a new bot entered the hub.
        if (!(listener instanceof ClientPlayNetworkHandler
                || listener instanceof ClientConfigurationNetworkHandler
                || listener instanceof ClientLoginNetworkHandler)
                || !isManagedListener(listener)) {
            return false;
        }
        BotSession session = findSession(listener);
        if (session == null) {
            return false;
        }
        // isManagedListener and this method run separately on the Netty
        // thread. Recheck after an account switch so a packet that observed
        // switching=true cannot enter the old FIFO after main has returned to
        // vanilla processing.
        if (session.isMain() && session == activeSession && !switching) {
            return false;
        }
        MinecraftClient.getInstance().execute(() -> applyBackgroundPacket(session, packet, listener));
        return true;
    }

    private void drainBackgroundPackets() {
        if (!backgroundDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            List<BotSession> ordered = new ArrayList<>();
            BotSession active = activeSession;
            if (active != null && sessionPacketQueues.containsKey(active)) {
                ordered.add(active);
            }
            for (BotSession session : sessions) {
                if (session != active && sessionPacketQueues.containsKey(session)) {
                    ordered.add(session);
                }
            }
            // Include a just-removed/disconnecting session long enough to
            // consume or reject its final queued packets without affecting
            // any other connection.
            for (BotSession session : sessionPacketQueues.keySet()) {
                if (!ordered.contains(session)) {
                    ordered.add(session);
                }
            }
            for (BotSession session : ordered) {
                ConcurrentLinkedDeque<BackgroundPacket> queue = sessionPacketQueues.get(session);
                if (queue == null) {
                    continue;
                }
                while (true) {
                    BackgroundPacket queued = queue.pollFirst();
                    if (queued == null) {
                        break;
                    }
                    applyBackgroundPacket(queued.session(), queued.packet(), queued.listener());
                }
            }
        } finally {
            backgroundDrainScheduled.set(false);
        }
    }

    public AutoBuy getAutoBuyForTelegramSession(AutoBuy global) {
        BotSession session = getTelegramTargetSession();
        return session != null && !session.isMain() ? session.getAutoBuy() : global;
    }

    public AutoResellAFK getAutoResellAFKForTelegramSession(AutoResellAFK global) {
        BotSession session = getTelegramTargetSession();
        return session != null && !session.isMain() ? session.getAutoResellAFK() : global;
    }

    public String selectTelegramTarget(String requestedName) {
        String requested = requestedName == null ? "" : requestedName.trim();
        if (requested.isBlank() || requested.equalsIgnoreCase("main")
                || requested.equalsIgnoreCase("основа") || requested.equalsIgnoreCase("основной")) {
            telegramTargetName = "main";
            return "Telegram теперь управляет основным аккаунтом.";
        }
        BotSession session = findByName(requested);
        if (session == null || session.isMain() || !session.isReadyToControl()
                || !session.isInPlayProtocol() || session.getState() == BotSession.State.DISCONNECTED) {
            telegramTargetName = "main";
            return "Бот " + requested + " не найден или ещё не подключён. Выбрана основа.";
        }
        telegramTargetName = session.getName();
        return "Telegram теперь управляет ботом " + session.getName() + ".";
    }

    public String getTelegramTargetName() {
        BotSession session = getTelegramTargetSession();
        return session == null || session.isMain() ? "main" : session.getName();
    }

    public boolean isCurrentSessionTelegramTarget() {
        BotSession current = CONTEXT.get();
        if (current == null) {
            current = activeSession;
        }
        return current != null && current == getTelegramTargetSession();
    }

    public boolean isTelegramTargetListener(PacketListener listener) {
        BotSession session = findSession(listener);
        return session != null && session == getTelegramTargetSession();
    }

    public void tickTelegram(MinecraftClient client, Telegram telegram) {
        BotSession target = getTelegramTargetSession();
        if (target == null || target == activeSession) {
            telegram.onTick(client);
            return;
        }
        target.runWithContext(() -> telegram.onTick(client));
    }

    public void executeTelegramAction(MinecraftClient client, Runnable action) {
        if (client == null || action == null) {
            return;
        }
        client.execute(() -> {
            BotSession target = getTelegramTargetSession();
            if (target == null || target == activeSession) {
                action.run();
                return;
            }
            target.runWithContext(action);
        });
    }

    public BotSession getTelegramTargetSession() {
        String selected = telegramTargetName;
        if (selected == null || selected.equalsIgnoreCase("main") || selected.equalsIgnoreCase("основа")) {
            return mainSession != null ? mainSession : activeSession;
        }
        BotSession session = findByName(selected);
        if (session == null || session.getState() == BotSession.State.DISCONNECTED
                || !session.isReadyToControl() || !session.isInPlayProtocol()) {
            telegramTargetName = "main";
            return mainSession != null ? mainSession : activeSession;
        }
        return session;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void applyBackgroundPacket(Packet<?> packet, PacketListener listener) {
        applyBackgroundPacket(findSession(listener), packet, listener);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyBackgroundPacket(BotSession session, Packet<?> packet, PacketListener listener) {
        BotDebug.trace("PACKET_APPLY_BEGIN", session, "packet=" + packet.getClass().getSimpleName()
                + ", active=" + (session != null && session == activeSession));
        if (session == null) {
            ((Packet) packet).apply(listener);
            return;
        }
        // Listener instances are replaced at login/configuration/play
        // boundaries. A packet queued for an older listener is stale and can
        // otherwise mutate the newly selected world or reopen an old screen.
        ClientConnection connection = session.getConnection();
        if (connection != null && connection.getPacketListener() != listener) {
            BotDebug.trace("STALE_PACKET_DROPPED", session,
                    "packet=" + packet.getClass().getSimpleName()
                            + ", listener=" + listener.getClass().getSimpleName()
                            + ", current=" + (connection.getPacketListener() == null
                            ? "null" : connection.getPacketListener().getClass().getSimpleName()));
            return;
        }
        if (listener instanceof ClientPlayNetworkHandler
                && !session.isReadyToControl()
                && !(packet instanceof GameJoinS2CPacket)
                && !(packet instanceof PlayerRespawnS2CPacket)
                && !(packet instanceof EnterReconfigurationS2CPacket)
                && !(packet instanceof GameMessageS2CPacket)) {
            BotDebug.trace("PLAY_PACKET_WAITING_FOR_JOIN", session,
                    "packet=" + packet.getClass().getSimpleName());
            return;
        }
        if (session == activeSession) {
            // Sync networkHandler from the listener: after a reconfiguration
            // the connection's listener is replaced with a new play handler,
            // but the session may still reference the old one.
            if (listener instanceof ClientPlayNetworkHandler playHandler) {
                session.setNetworkHandler(playHandler);
            }
            // Active bot packets can still be queued by the network-thread
            // bridge while the visible account/world is being switched. Apply
            // them against their own session context so an old queued packet
            // cannot mutate the newly selected world or crash on missing HUD
            // and player-list state.
            Runnable applyActivePacket = () -> {
                ((Packet) packet).apply(listener);
                if (isInventoryPacket(packet) || packet instanceof OpenScreenS2CPacket
                        || packet instanceof PlayerRespawnS2CPacket
                        || packet instanceof GameJoinS2CPacket) {
                    session.captureFromClient();
                }
            };
            try {
                if (CONTEXT.get() == session) {
                    applyActivePacket.run();
                } else if (MinecraftClient.getInstance().world == session.getWorld()
                        && MinecraftClient.getInstance().player == session.getPlayer()) {
                // The active bot already owns MinecraftClient's visible
                // state. Applying directly preserves the screen opened by
                // OpenScreenS2CPacket; wrapping it would restore the old
                // currentScreen in runWithContext and immediately erase the
                // newly opened container.
                    BotSession previousContext = enterContext(session);
                    try {
                        applyActivePacket.run();
                    } finally {
                        leaveContext(previousContext);
                    }
                } else {
                    session.runWithContext(applyActivePacket);
                    if (session == activeSession) {
                        session.installAsActive();
                    }
                }
            } catch (Throwable error) {
                BotDebug.error("ACTIVE_PACKET_APPLY_FAILED", session,
                        "packet=" + packet.getClass().getSimpleName(), error);
            }
            if (isInventoryPacket(packet) || packet instanceof OpenScreenS2CPacket
                    || packet instanceof PlayerRespawnS2CPacket
                    || packet instanceof GameJoinS2CPacket) {
                BotDebug.trace("ACTIVE_GUI_STATE_CAPTURED", session,
                        "packet=" + packet.getClass().getSimpleName()
                                + ", screen=" + (session.getScreen() != null));
            }
            return;
        }
        if (listener instanceof ClientPlayNetworkHandler playHandler) {
            session.setNetworkHandler(playHandler);
        }
        try {
            session.runWithContext(() -> ((Packet) packet).apply(listener));
        } catch (Throwable error) {
            BotDebug.error("PACKET_APPLY_FAILED", session,
                    "packet=" + packet.getClass().getSimpleName(), error);
        }
        BotDebug.trace("PACKET_APPLY_END", session, "packet=" + packet.getClass().getSimpleName());
    }

    public boolean rerouteBackgroundUiPacket(Packet<?> packet, PacketListener listener) {
        BotSession session = findSession(listener);
        if (session == null || session == activeSession || CONTEXT.get() == session) {
            return false;
        }
        BotDebug.trace("UI_PACKET_REROUTE", session, "packet=" + packet.getClass().getSimpleName());
        // HUD state belongs to the session that received it. Apply it against
        // that session's detached InGameHud/network handler so boss bars,
        // titles and the tab header are ready after a switch, while render
        // mixins keep the detached HUD completely invisible and cheap.
        // Chat and container packets keep their existing specialized paths.
        if (isInventoryPacket(packet)) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> applyBackgroundPacket(packet, listener));
            return true;
        }
        if (packet instanceof AdvancementUpdateS2CPacket) {
            BotDebug.trace("BACKGROUND_UI_SKIPPED", session,
                    "packet=" + packet.getClass().getSimpleName() + ", reason=toast_only");
            return true;
        }
        // Apply the packet in the bot's session context on the next client
        // tick. This preserves HUD/handler state without mutating the active
        // account's singleton client state.
        // The CONTEXT check in applyBackgroundPacket prevents infinite
        // recursion: when packet.apply(listener) re-enters this mixin,
        // CONTEXT.get() == session is true and we return false.
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> applyBackgroundPacket(packet, listener));
        return true;
    }

    public boolean handleGameMessage(PacketListener listener, Text message) {
        return handleChatMessage(listener, message);
    }

    public boolean handleChatMessage(PacketListener listener, String message) {
        return handleChatMessage(listener, message == null ? null : Text.literal(message));
    }

    public boolean handleChatMessage(PacketListener listener, Text formattedMessage) {
        String earlyMessage = formattedMessage == null ? null : formattedMessage.getString();
        BotSession earlySession = findSession(listener);
        // Captcha prompt is checked BEFORE the managed-sessions gate so the
        // plain main connection (no bots) also arms the solver.
        if (dev.fuga.fluxvisuals.captcha.CaptchaSolver.isCaptchaPrompt(earlyMessage)
                && (earlySession == null
                || earlySession.getState() != BotSession.State.DISCONNECTED)) {
            boolean handled = false;
            try {
                dev.fuga.fluxvisuals.captcha.CaptchaSolver solver =
                        FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver();
                if (solver != null) {
                    handled = earlySession != null
                            ? solver.onCaptchaMessage(earlySession)
                            : solver.onCaptchaMain();
                }
            } catch (Throwable ignored) {
            }
            if (handled) {
                BotDebug.info("CAPTCHA_SOLVER_TAKEN", earlySession, "auto_switch_suppressed=true");
            } else if (earlySession != null && !earlySession.isMain() && earlySession != activeSession
                    && earlySession.getState() != BotSession.State.DISCONNECTED) {
                pendingAutoSwitchSession = earlySession;
                BotDebug.info("CAPTCHA_BOT_DETECTED", earlySession, "auto_switch_pending=true");
                feedback(MinecraftClient.getInstance(),
                        "Капча у бота " + earlySession.getName()
                                + " (солвер выкл) — переключаю управление для ручного ввода.",
                        Formatting.YELLOW);
            }
        }
        if (!hasManagedSessions()) {
            return false;
        }
        BotSession session = earlySession;
        String message = formattedMessage == null ? null : formattedMessage.getString();
        String preview = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
        if (preview.length() > 120) {
            preview = preview.substring(0, 120) + "...";
        }
        BotDebug.trace("CHAT_PACKET", session,
                "length=" + (message == null ? -1 : message.length())
                        + ", preview=\"" + preview + "\"");
        if (session != null) {
            if (session.isDuplicateChatMessage(message, System.currentTimeMillis())) {
                BotDebug.trace("CHAT_PACKET_DUPLICATE", session,
                        "length=" + (message == null ? -1 : message.length()));
                return !session.isMain();
            }
            // This hook runs at TAIL after vanilla has already appended the
            // packet to the session's ChatHud. Store only the history copy;
            // adding it to the HUD here duplicates every background message.
            session.rememberChat(formattedMessage, true);
        }
        String normalizedServerMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (session != null && (normalizedServerMessage.contains("успешно прошли проверку")
                || normalizedServerMessage.contains("verification completed"))) {
            session.markVerificationCompleted();
            BotDebug.info("VERIFICATION_TRANSFER_ARMED", session,
                    "discard_vehicle_after_next_world_transfer=true");
        }
        if (normalizedServerMessage.contains("\u0441\u0435\u0440\u0432\u0435\u0440, \u043d\u0430 \u043a\u043e\u0442\u043e\u0440\u043e\u043c \u0432\u044b \u0438\u0433\u0440\u0430\u043b\u0438, \u0432\u044b\u043a\u043b\u044e\u0447\u0438\u043b\u0441\u044f")
                && normalizedServerMessage.contains("\u043f\u0435\u0440\u0435\u043c\u0435\u0449\u0435\u043d\u044b \u0432 \u043b\u043e\u0431\u0431\u0438")) {
            BotDebug.warn("SERVER_TRANSFER_TO_LOBBY", session,
                    "cause=server_reported, possible_high_ping_kick=true");
        }
        Long parsedBalance = AutoBuy.extractBalance(message);
        if (parsedBalance != null && parsedBalance >= 0L) {
            if (session != null) {
                session.setKnownBalance(parsedBalance);
            }
            if (session == null || session.isMain()) {
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy().setLastKnownBalance(parsedBalance);
            }
        }
        if (session == null || session.isMain()) {
            return false;
        }
        if (session.isChatMirrorEnabled() && formattedMessage != null && session != activeSession && !session.isMain()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.inGameHud != null && client.inGameHud.getChatHud() != null) {
                Text prefix = Text.literal("§b" + session.getName() + "§7> §f");
                Text forwardText = Text.empty().append(prefix).append(formattedMessage);
                client.execute(() -> client.inGameHud.getChatHud().addMessage(forwardText));
            }
        }
        session.getAutoBuy().onChatMessage(message);
        ItemCrafter itemCrafter = session.isMain()
                ? FluxVisualsClient.MODULE_MANAGER.getItemCrafter()
                : session.getItemCrafter();
        if (itemCrafter != null) {
            itemCrafter.onChatMessage(message);
        }
        String normalizedChat = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (session != activeSession && isServerAfkMessage(normalizedChat)
                && session.isAntiAfkEnabled()) {
            session.triggerAntiAfkLookFromServerMessage();
        }
        session.getAutoResellAFK().onChatMessage(message);
        handlePendingPayBalance(session, message);
        if (getTelegramTargetSession() == session) {
            Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
            telegram.handleAutoResellPurchaseMessage(message);
            telegram.onGameChatMessage(message);
        }
        return true;
    }

    private static boolean isServerAfkMessage(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return normalizedMessage.contains("\u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u043d\u0435 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0432 \u0440\u0435\u0436\u0438\u043c\u0435 \u0430\u0444\u043a")
                || normalizedMessage.contains("\u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0432 \u0440\u0435\u0436\u0438\u043c\u0435 \u0430\u0444\u043a")
                || normalizedMessage.contains("\u0432 \u0440\u0435\u0436\u0438\u043c\u0435 \u0430\u0444\u043a")
                || normalizedMessage.contains("\u0432 \u0440\u0435\u0436\u0438\u043c\u0435 afk")
                || normalizedMessage.contains("afk mode");
    }

    private void handlePendingPayBalance(BotSession session, String message) {
        PendingPayAll pending = pendingPayAll.get(session);
        if (pending == null || System.currentTimeMillis() - pending.requestedAt() > 15_000L) {
            if (pending != null) {
                pendingPayAll.remove(session, pending);
                BotDebug.warn("PAY_BALANCE_TIMEOUT", session, "recipient=" + pending.recipient());
            }
            return;
        }
        Long balance = AutoBuy.extractBalance(message);
        if (balance == null) {
            return;
        }
        pendingPayAll.remove(session, pending);
        if (balance <= 0L) {
            BotDebug.info("PAY_BALANCE_EMPTY", session, "recipient=" + pending.recipient());
            return;
        }
        sendPayCommand(session, pending.recipient(), balance);
    }

    public boolean handleDisconnected(PacketListener listener, String reason) {
        BotSession session = findSession(listener);
        if (session == null || session.isMain()) {
            return false;
        }
        pendingBotCommands.remove(session);
        sessionPacketQueues.remove(session);
        // Drop stale routing so a later connection never resolves to this dead session.
        listenerSessions.entrySet().removeIf(entry -> entry.getValue() == session);
        connectionSessions.entrySet().removeIf(entry -> entry.getValue() == session);
        session.setState(BotSession.State.DISCONNECTED);
        BotDebug.warn("DISCONNECTED", session,
                "reason=" + reason + ", " + networkHealthDetails(session, System.nanoTime()));
        session.setStatusText(reason == null || reason.isBlank() ? "Отключено" : reason);
        if (isCaptchaKick(reason)) {
            // Captcha/botfilter kick: instant reconnect storms flag the whole
            // IP (BotFilter) and get the main account kicked with a network
            // protocol error next. Back off instead of hammering.
            long now = System.currentTimeMillis();
            session.delayReconnect(now + 90_000L);
            try {
                dev.fuga.fluxvisuals.captcha.CaptchaSolver solver =
                        FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver();
                if (solver != null) {
                    solver.cancelFor(session);
                }
            } catch (Throwable ignored) {
            }
            BotDebug.warn("CAPTCHA_KICK_BACKOFF", session, "reconnect_blocked_90s=true");
        }
        BotSession main = mainSession;
        if (session == activeSession && main != null && main.isReadyToControl()
                && main.getState() != BotSession.State.DISCONNECTED) {
            MinecraftClient.getInstance().execute(() -> {
                if (activeSession == session) {
                    switchTo(main);
                    BotDebug.warn("RETURN_TO_MAIN", session, "active bot disconnected");
                }
            });
        }
        MinecraftClient.getInstance().execute(() -> feedback(
                MinecraftClient.getInstance(),
                "Бот " + session.getName() + " не подключился/отключился. Причина: " + session.getStatusText(),
                Formatting.RED));
        session.setStatusText(reason == null || reason.isBlank() ? "Отключён" : reason);
        return true;
    }

    /** Kick reasons that mean "failed bot/captcha check" — never reconnect these fast. */
    private static boolean isCaptchaKick(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("капч") || normalized.contains("captcha")
                || normalized.contains("botfilter") || normalized.contains("bot filter")
                || normalized.contains("проверк") || normalized.contains("verify")
                || normalized.contains("verification") || normalized.contains("подозр")
                || normalized.contains("suspicious");
    }

    public boolean handleBotResourcePack(PacketListener listener, UUID id) {
        BotSession session = findSession(listener);
        if (session == null || session.isMain() || id == null) {
            return false;
        }
        ClientConnection connection = session.getConnection();
        if (connection != null && connection.isOpen()) {
            MinecraftClient client = MinecraftClient.getInstance();
            ServerInfo server = client.getCurrentServerEntry();
            ServerInfo.ResourcePackPolicy policy = server == null
                    ? ServerInfo.ResourcePackPolicy.ENABLED
                    : server.getResourcePackPolicy();
            if (policy == ServerInfo.ResourcePackPolicy.DISABLED) {
                connection.send(new ResourcePackStatusC2SPacket(
                        id, ResourcePackStatusC2SPacket.Status.DECLINED));
                BotDebug.info("BOT_RESOURCE_PACK_DECLINED", session, "policy=DISABLED");
            } else {
                // Resource-pack files are global to this Minecraft process.
                // The main account owns the actual download; bots mirror its
                // accepted/loaded protocol state without replacing the visible
                // client's resource manager or freezing the render thread.
                connection.send(new ResourcePackStatusC2SPacket(
                        id, ResourcePackStatusC2SPacket.Status.ACCEPTED));
                connection.send(new ResourcePackStatusC2SPacket(
                        id, ResourcePackStatusC2SPacket.Status.DOWNLOADED));
                connection.send(new ResourcePackStatusC2SPacket(
                        id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                BotDebug.info("BOT_RESOURCE_PACK_MIRRORED", session,
                        "policy=" + policy + ", status=SUCCESSFULLY_LOADED");
            }
        }
        return true;
    }

    public boolean shouldIgnoreBotResourcePackRemoval(PacketListener listener) {
        BotSession session = findSession(listener);
        return session != null && !session.isMain();
    }

    public static boolean isBackgroundContext() {
        BotSession context = CONTEXT.get();
        return context != null && context != FluxVisualsClient.MULTI_BOT_MANAGER.activeSession;
    }

    /** True only during a local account-view swap, not a server world change. */
    public static boolean isManagedSessionSwitch() {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        return manager != null && manager.switching;
    }

    /** True while a bot, active or background, is applying gameplay logic. */
    public static boolean isBotContext() {
        BotSession context = CONTEXT.get();
        if (context != null) {
            return !context.isMain();
        }
        BotSession active = FluxVisualsClient.MULTI_BOT_MANAGER.activeSession;
        return active != null && !active.isMain();
    }

    public static boolean isCurrentBotContext() {
        BotSession context = CONTEXT.get();
        return context != null && !context.isMain();
    }

    /** Returns the session whose detached client state is currently active. */
    public static BotSession currentContextSession() {
        return CONTEXT.get();
    }

    /**
     * During PLAY -> CONFIGURATION the old player snapshot remains visible so
     * the renderer cannot flash the title screen. Vanilla must not tick that
     * snapshot, however: its old play handler now points at a configuration
     * codec and movement packets would otherwise corrupt the transition.
     */
    public static boolean shouldSkipBotPlayerTick(ClientPlayerEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || entity == null || client.player != entity) {
            return false;
        }
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager == null) {
            return false;
        }
        BotSession active = manager.getActiveSession();
        if (active == null || active.isMain()) {
            return false;
        }
        // If the active bot is already in play protocol and world is loaded, NEVER skip ticking!
        if (active.isInPlayProtocol() && client.world != null) {
            return false;
        }
        // Only skip if the active bot is currently undergoing reconfiguration/login
        return active.getState() == BotSession.State.LOGGING_IN;
    }

    public static boolean shouldSkipBotPlayerTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return shouldSkipBotPlayerTick(client != null ? client.player : null);
    }

    /** True when vanilla's global client handler currently belongs to the active bot. */
    public boolean isActiveBotConnection() {
        BotSession active = activeSession;
        if (active == null || active.isMain()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = active.getNetworkHandler();
        return handler != null && client.getNetworkHandler() == handler;
    }

    /** True while the active bot still owns an open socket, including the
     * short configuration interval where vanilla has already nulled its
     * global network handler. */
    public boolean isActiveBotLifecycle() {
        BotSession active = activeSession;
        ClientConnection connection = active == null ? null : active.getConnection();
        return active != null && !active.isMain() && connection != null && connection.isOpen();
    }

    /**
     * ReconfiguringScreen already ticks its own connection every frame. Do
     * not tick that same socket from the manager as well: doing so outside the
     * session context makes ClientPlayNetworkHandler read a null global
     * MinecraftClient network handler during PLAY -> CONFIGURATION.
     */
    public boolean isConnectionTickedByReconfigurationScreen(ClientConnection connection) {
        if (connection == null) {
            return false;
        }
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof net.minecraft.client.gui.screen.ReconfiguringScreen)) {
            return false;
        }
        if (!(screen instanceof dev.fuga.fluxvisuals.mixin.ReconfiguringScreenAccessor accessor)) {
            return false;
        }
        return accessor.fluxvisuals$getConnection() == connection;
    }

    /** Applies the transition screen's connection tick in its owning session. */
    public boolean tickReconfigurationScreenConnection(ClientConnection connection) {
        BotSession session = findSession(connection);
        if (session == null || session.isMain()) {
            return false;
        }
        try {
            Runnable tick = () -> {
                if (connection.isOpen()) {
                    tickConnectionAsManager(connection);
                } else {
                    connection.handleDisconnection();
                }
            };
            if (session == activeSession || CONTEXT.get() == session) {
                tick.run();
            } else {
                session.runWithContext(tick);
            }
        } catch (Throwable error) {
            BotDebug.error("RECONFIGURATION_TICK_FAILED", session,
                    "connection handled without crashing the client", error);
        }
        return true;
    }

    public void captureBackgroundScreen(Screen screen) {
        BotSession context = CONTEXT.get();
        if (context != null && !context.isMain()) {
            if (screen instanceof DownloadingTerrainScreen || screen instanceof ReconfiguringScreen) {
                context.setScreenFromBackground(null);
            } else {
                context.setScreenFromBackground(screen);
            }
        }
    }

    static BotSession enterContext(BotSession session) {
        BotSession previous = CONTEXT.get();
        CONTEXT.set(session);
        return previous;
    }

    static void leaveContext(BotSession previous) {
        if (previous == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(previous);
        }
    }

    private boolean allowChatMessage(String message) {
        if (message == null) {
            return true;
        }
        if (CONTEXT.get() != null) {
            return true;
        }
        // Keep the primary account's normal chat/Baritone path untouched
        // until a bot is actually active.  The local .bot command remains
        // available so the first background account can still be started.
        if (!hasManagedSessions()
                && !message.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return true;
        }
        BotSession active = activeSession;
        if (message.trim().startsWith("#")) {
            // Baritone commands are always client-only, including invalid
            // commands, so a leading '#' can never become server chat.
            boolean executed = executeBaritoneForSession(active, message.trim());
            MinecraftClient client = MinecraftClient.getInstance();
            feedback(client, executed
                    ? "Baritone: команда принята."
                    : (BaritoneBridge.isAvailable()
                    ? "Baritone: неизвестная команда или нет активного исполнителя."
                    : "Baritone не загружен в текущем клиенте."),
                    executed ? Formatting.GREEN : Formatting.RED);
            return false;
        }
        if (!message.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            if (!ROUTING_MESSAGE.get() && active != null && !active.isMain() && active.getNetworkHandler() != null) {
                ROUTING_MESSAGE.set(true);
                try {
                    active.getNetworkHandler().sendChatMessage(message);
                } finally {
                    ROUTING_MESSAGE.set(false);
                }
                return false;
            }
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> executeCommand(client, message.substring(PREFIX.length()).trim()));
        return false;
    }

    private boolean allowCommandMessage(String command) {
        if (command == null || command.isBlank()) {
            return true;
        }
        if (CONTEXT.get() != null) {
            return true;
        }
        String normalized = command.trim();
        if (normalized.equalsIgnoreCase("bot") || normalized.regionMatches(true, 0, "bot ", 0, 4)) {
            return true;
        }
        BotSession active = activeSession;
        if (!ROUTING_MESSAGE.get() && active != null && !active.isMain() && active.getNetworkHandler() != null) {
            ROUTING_MESSAGE.set(true);
            try {
                active.getNetworkHandler().sendChatCommand(normalized);
            } finally {
                ROUTING_MESSAGE.set(false);
            }
            return false;
        }
        return true;
    }

    private void registerClientCommands(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = ClientCommandManager.literal("bot")
                .executes(context -> executeRegistered(context, ""));
        root.then(ClientCommandManager.literal("drop")
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(false), builder))
                        .executes(context -> executeRegistered(context,
                                "drop " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("pay")
                .then(ClientCommandManager.argument("targetOrAmount", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                suggestPayTargetsAndAll(), builder))
                        .executes(context -> executeRegistered(context,
                                "pay " + StringArgumentType.getString(context, "targetOrAmount")))
                        .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        List.of("all"), builder))
                                .executes(context -> executeRegistered(context,
                                        "pay " + StringArgumentType.getString(context, "targetOrAmount")
                                                + " " + StringArgumentType.getString(context, "amount"))))));
        root.then(ClientCommandManager.literal("an")
                .then(ClientCommandManager.argument("number", StringArgumentType.word())
                        .executes(context -> executeRegistered(context,
                                "an " + StringArgumentType.getString(context, "number")))));
        root.then(ClientCommandManager.literal("baritone")
                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                suggestBaritoneCommands(), builder))
                        .executes(context -> executeRegistered(context,
                                "baritone " + StringArgumentType.getString(context, "command")))));
        root.then(ClientCommandManager.literal("resell")
                .executes(context -> executeRegistered(context, "resell on"))
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("on", "off"), builder))
                        .executes(context -> executeRegistered(context,
                                "resell " + StringArgumentType.getString(context, "state")))));
        root.then(ClientCommandManager.literal("antiafk")
                .executes(context -> executeRegistered(context, "antiafk"))
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("on", "off", "вкл", "выкл"), builder))
                        .executes(context -> executeRegistered(context,
                                "antiafk " + StringArgumentType.getString(context, "state")))));
        root.then(ClientCommandManager.literal("anti-afk")
                .executes(context -> executeRegistered(context, "anti-afk"))
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("on", "off", "вкл", "выкл"), builder))
                        .executes(context -> executeRegistered(context,
                                "anti-afk " + StringArgumentType.getString(context, "state")))));
        root.then(ClientCommandManager.literal("preset")
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> executeRegistered(context,
                                        "preset save " + StringArgumentType.getString(context, "name")))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        suggestPresetNames(), builder))
                                .executes(context -> executeRegistered(context,
                                        "preset remove " + StringArgumentType.getString(context, "name")))))
                .then(ClientCommandManager.literal("load")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        suggestPresetNames(), builder))
                                .executes(context -> executeRegistered(context,
                                        "preset load " + StringArgumentType.getString(context, "name"))))));
        root.then(ClientCommandManager.literal("lineup")
                .executes(context -> executeRegistered(context, "lineup")));
        root.then(ClientCommandManager.literal("goto")
                .then(ClientCommandManager.literal("me")
                        .executes(context -> executeRegistered(context, "goto me"))
                        .then(ClientCommandManager.argument("botName", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        suggestNames(false), builder))
                                .executes(context -> executeRegistered(context,
                                        "goto me " + StringArgumentType.getString(context, "botName"))))));
        root.then(ClientCommandManager.literal("proxy")
                .executes(context -> executeRegistered(context, "proxy"))
                .then(ClientCommandManager.argument("proxyConfig", StringArgumentType.greedyString())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("off", "clear"), builder))
                        .executes(context -> executeRegistered(context,
                                "proxy " + StringArgumentType.getString(context, "proxyConfig")))));
        root.then(ClientCommandManager.literal("прокси")
                .executes(context -> executeRegistered(context, "proxy"))
                .then(ClientCommandManager.argument("proxyConfig", StringArgumentType.greedyString())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("off", "clear"), builder))
                        .executes(context -> executeRegistered(context,
                                "proxy " + StringArgumentType.getString(context, "proxyConfig")))));
        root.then(ClientCommandManager.literal("chat")
                .executes(context -> executeRegistered(context, "chat"))
                .then(ClientCommandManager.argument("chatTargetOrState", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                suggestChatTargets(), builder))
                        .executes(context -> executeRegistered(context,
                                "chat " + StringArgumentType.getString(context, "chatTargetOrState")))
                        .then(ClientCommandManager.argument("chatState", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        List.of("on", "off", "вкл", "выкл"), builder))
                                .executes(context -> executeRegistered(context,
                                        "chat " + StringArgumentType.getString(context, "chatTargetOrState")
                                                + " " + StringArgumentType.getString(context, "chatState"))))));
        root.then(ClientCommandManager.literal("чат")
                .executes(context -> executeRegistered(context, "chat"))
                .then(ClientCommandManager.argument("chatTargetOrState", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                suggestChatTargets(), builder))
                        .executes(context -> executeRegistered(context,
                                "chat " + StringArgumentType.getString(context, "chatTargetOrState")))
                        .then(ClientCommandManager.argument("chatState", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        List.of("on", "off", "вкл", "выкл"), builder))
                                .executes(context -> executeRegistered(context,
                                        "chat " + StringArgumentType.getString(context, "chatTargetOrState")
                                                + " " + StringArgumentType.getString(context, "chatState"))))));
        root.then(ClientCommandManager.argument("botName", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(false), builder))
                .then(ClientCommandManager.literal("goto")
                        .then(ClientCommandManager.literal("me")
                                .executes(context -> executeRegistered(context,
                                        StringArgumentType.getString(context, "botName") + " goto me"))))
                .then(ClientCommandManager.literal("drop")
                        .executes(context -> executeRegistered(context,
                                StringArgumentType.getString(context, "botName") + " drop")))
                .then(ClientCommandManager.literal("chat")
                        .executes(context -> executeRegistered(context,
                                StringArgumentType.getString(context, "botName") + " chat"))
                        .then(ClientCommandManager.argument("botChatState", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        List.of("on", "off", "вкл", "выкл"), builder))
                                .executes(context -> executeRegistered(context,
                                        StringArgumentType.getString(context, "botName") + " chat "
                                                + StringArgumentType.getString(context, "botChatState")))))
                .then(ClientCommandManager.literal("proxy")
                        .executes(context -> executeRegistered(context,
                                StringArgumentType.getString(context, "botName") + " proxy"))
                        .then(ClientCommandManager.argument("botProxyConfig", StringArgumentType.greedyString())
                                .executes(context -> executeRegistered(context,
                                        StringArgumentType.getString(context, "botName") + " proxy "
                                                + StringArgumentType.getString(context, "botProxyConfig")))))
                .then(ClientCommandManager.literal("function")
                        .then(ClientCommandManager.argument("functionName", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        suggestFunctionNames(), builder))
                                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                List.of("on", "off", "включить", "выключить"), builder))
                                        .executes(context -> executeRegistered(context,
                                                StringArgumentType.getString(context, "botName") + " function "
                                                        + StringArgumentType.getString(context, "functionName") + " "
                                                        + StringArgumentType.getString(context, "state")))))));
        root.then(ClientCommandManager.literal("add")
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .executes(context -> executeRegistered(context, "add " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("remove")
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(false), builder))
                        .executes(context -> executeRegistered(context, "remove " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("removeall")
                .executes(context -> executeRegistered(context, "removeall")));
        root.then(ClientCommandManager.literal("list")
                .executes(context -> executeRegistered(context, "list")));
        root.then(ClientCommandManager.literal("switch")
                .executes(context -> executeRegistered(context, "switch"))
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(true), builder))
                        .executes(context -> executeRegistered(context, "switch " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("main")
                .executes(context -> executeRegistered(context, "main")));
        root.then(ClientCommandManager.literal("command")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> executeRegistered(context,
                                "command " + StringArgumentType.getString(context, "message")))));
        root.then(ClientCommandManager.literal("tp")
                .executes(context -> executeRegistered(context, "tp"))
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(true), builder))
                        .executes(context -> executeRegistered(context,
                                "tp " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("play")
                .executes(context -> executeRegistered(context, "play"))
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("on", "off", "вкл", "выкл"), builder))
                        .executes(context -> executeRegistered(context,
                                "play " + StringArgumentType.getString(context, "state")))));
        root.then(ClientCommandManager.literal("help").executes(context -> executeRegistered(context, "help")));
        root.then(ClientCommandManager.literal("function")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                suggestFunctionNames(), builder))
                        .then(ClientCommandManager.argument("state", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        List.of("on", "off", "включить", "выключить"), builder))
                                .executes(context -> executeRegistered(context,
                                        "function " + StringArgumentType.getString(context, "name") + " "
                                                + StringArgumentType.getString(context, "state"))))));
        root.then(ClientCommandManager.literal("добавить")
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .executes(context -> executeRegistered(context, "add " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("удалить")
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(false), builder))
                        .executes(context -> executeRegistered(context, "remove " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("удалитьвсех")
                .executes(context -> executeRegistered(context, "removeall")));
        root.then(ClientCommandManager.literal("список")
                .executes(context -> executeRegistered(context, "list")));
        root.then(ClientCommandManager.literal("переключить")
                .executes(context -> executeRegistered(context, "switch"))
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(true), builder))
                        .executes(context -> executeRegistered(context, "switch " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("основной")
                .executes(context -> executeRegistered(context, "main")));
        root.then(ClientCommandManager.literal("команда")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> executeRegistered(context,
                                "command " + StringArgumentType.getString(context, "message")))));
        root.then(ClientCommandManager.literal("тп")
                .executes(context -> executeRegistered(context, "tp"))
                .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(suggestNames(true), builder))
                        .executes(context -> executeRegistered(context,
                                "tp " + StringArgumentType.getString(context, "nickname")))));
        root.then(ClientCommandManager.literal("играть")
                .executes(context -> executeRegistered(context, "play"))
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("вкл", "выкл", "on", "off"), builder))
                        .executes(context -> executeRegistered(context,
                                "play " + StringArgumentType.getString(context, "state")))));
        root.then(ClientCommandManager.literal("помощь")
                .executes(context -> executeRegistered(context, "help")));
        dispatcher.register(root);
    }

    private int executeRegistered(CommandContext<FabricClientCommandSource> context, String command) {
        executeCommand(context.getSource().getClient(), command);
        return 1;
    }

    private List<String> suggestNames(boolean includeMain) {
        List<String> names = new ArrayList<>();
        for (BotSession session : sessions) {
            if (session.isMain() && !includeMain) {
                continue;
            }
            if (session.getState() != BotSession.State.DISCONNECTED) {
                names.add(session.isMain() ? "main" : session.getName());
            }
        }
        return names;
    }

    private List<String> suggestPayTargetsAndAll() {
        List<String> names = suggestNames(false);
        names.add("all");
        return names;
    }

    private List<String> suggestChatTargets() {
        List<String> names = suggestNames(false);
        names.add("all");
        names.add("on");
        names.add("off");
        return names;
    }

    private List<String> suggestPresetNames() {
        return botPresets.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void executeCommand(MinecraftClient client, String commandLine) {
        BotDebug.info("COMMAND", activeSession, "command=" + commandLine);
        if (commandLine.isBlank()) {
            showHelp(client);
            return;
        }
        String[] split = commandLine.split("\\s+", 2);
        String command = split[0].toLowerCase(Locale.ROOT);
        String arguments = split.length > 1 ? split[1].trim() : "";
        BotSession namedGotoTarget = findByName(command);
        if (namedGotoTarget != null && arguments.equalsIgnoreCase("goto me")) {
            gotoMe(client, namedGotoTarget.getName());
            return;
        }
        if (command.equals("goto")) {
            String[] gotoParts = arguments.split("\\s+");
            if (gotoParts.length > 0 && gotoParts[0].equalsIgnoreCase("me")) {
                gotoMe(client, gotoParts.length > 1 ? gotoParts[1] : "");
            } else {
                feedback(client, "Использование: .bot goto me [имя_бота]", Formatting.RED);
            }
            return;
        }
        if (!arguments.isBlank() && "drop".equalsIgnoreCase(arguments)
                && findByName(command) != null) {
            dropItems(client, command);
            return;
        }
        if (arguments.toLowerCase(Locale.ROOT).startsWith("chat") && findByName(command) != null) {
            String chatArgs = arguments.substring("chat".length()).trim();
            setBotChatForSingle(client, findByName(command), chatArgs);
            return;
        }
        if (arguments.toLowerCase(Locale.ROOT).startsWith("proxy") && findByName(command) != null) {
            String proxyArgs = arguments.substring("proxy".length()).trim();
            setBotProxy(client, findByName(command), proxyArgs);
            return;
        }
        if (arguments.toLowerCase(Locale.ROOT).startsWith("function ") && findByName(command) != null) {
            setBotFunction(client, findByName(command), arguments.substring("function".length()).trim());
            return;
        }
        switch (command) {
            case "chat", "чат" -> setBotChat(client, arguments);
            case "proxy", "прокси" -> executeProxyCommand(client, arguments);
            case "drop" -> dropItems(client, arguments);
            case "pay" -> payBots(client, arguments);
            case "antiafk", "anti-afk" -> setAntiAfk(client, arguments);
            case "an" -> sendAllBotsToAnarchy(client, arguments);
            case "baritone", "bar" -> executeBaritone(client, arguments);
            case "resell" -> setResellForBots(client, arguments);
            case "preset" -> executePresetCommand(client, arguments);
            case "lineup" -> lineupBots(client);
            case "add", "добавить" -> addBot(client, arguments);
            case "remove", "удалить" -> removeBot(client, arguments);
            case "removeall", "удалитьвсех" -> removeAllBots(client);
            case "list", "список" -> listBots(client);
            case "switch", "переключить" -> {
                if (arguments.isBlank()) {
                    switchNext(client);
                } else {
                    switchByName(client, arguments);
                }
            }
            case "main", "основной" -> switchToMain(client);
            case "command", "команда" -> sendBotCommand(client, arguments);
            case "tp", "тп" -> legacyTeleportBots(client, arguments);
            case "play", "играть" -> setPlayMode(client, arguments);
            case "debug", "дебаг" -> {
                if (arguments.equalsIgnoreCase("on") || arguments.equalsIgnoreCase("вкл") || arguments.equalsIgnoreCase("true")) {
                    BotDebug.setChatDebugEnabled(true);
                    feedback(client, "BotDebug в чате: включен. Полный лог: Desktop/FugaClient-Bots-Debug.log", Formatting.GREEN);
                } else if (arguments.equalsIgnoreCase("off") || arguments.equalsIgnoreCase("выкл") || arguments.equalsIgnoreCase("false")) {
                    BotDebug.setChatDebugEnabled(false);
                    feedback(client, "BotDebug в чате: выключен.", Formatting.YELLOW);
                } else {
                    printBotDebugStatus(client, arguments);
                }
            }
            case "function", "функция" -> setBotFunction(client, activeSession, arguments);
            case "help", "помощь" -> showHelp(client);
            default -> feedback(client, "Неизвестная команда. Используй .bot помощь", Formatting.RED);
        }
    }

    private void executePresetCommand(MinecraftClient client, String arguments) {
        String[] parts = arguments == null ? new String[0] : arguments.trim().split("\\s+");
        if (parts.length != 2 || !parts[0].matches("(?i)save|remove|load")
                || !parts[1].matches("[A-Za-z0-9_-]{1,32}")) {
            feedback(client, "Использование: .bot preset <save|remove|load> <имя>", Formatting.RED);
            return;
        }
        String action = parts[0].toLowerCase(Locale.ROOT);
        String name = parts[1];
        if (action.equals("save")) {
            List<String> names = sessions.stream().filter(s -> !s.isMain() && s.getState() != BotSession.State.DISCONNECTED)
                    .map(BotSession::getName).distinct().toList();
            if (names.isEmpty()) {
                feedback(client, "Нет подключённых ботов для сохранения.", Formatting.RED);
                return;
            }
            botPresets.put(name, List.copyOf(names));
            saveBotPresets();
            feedback(client, "Пресет " + name + " сохранён: " + names.size() + " ботов.", Formatting.GREEN);
        } else if (action.equals("remove")) {
            if (botPresets.remove(name) == null) {
                feedback(client, "Пресет не найден: " + name, Formatting.RED);
                return;
            }
            saveBotPresets();
            feedback(client, "Пресет " + name + " удалён.", Formatting.YELLOW);
        } else {
            List<String> names = botPresets.get(name);
            if (names == null) {
                feedback(client, "Пресет не найден: " + name, Formatting.RED);
                return;
            }
            // Preset loading can be the first multibot command in this
            // connection. Register the already connected vanilla account
            // before checking the cached main session.
            ensureMainSession(client);
            if (mainSession == null || mainSession.getAddress().isBlank()
                    || mainSession.getConnection() == null
                    || !mainSession.getConnection().isOpen()
                    || !mainSession.isInPlayProtocol()) {
                feedback(client, "Сначала зайди на сервер основным аккаунтом.", Formatting.RED);
                return;
            }
            int started = 0;
            long delay = 0L;
            for (String bot : names) {
                if (findByName(bot) == null) {
                    if (delay == 0L) {
                        addBot(client, bot);
                    } else {
                        long connectDelay = delay;
                        Thread.ofPlatform().daemon().name("Fuga-Preset-" + bot).start(() -> {
                            try {
                                Thread.sleep(connectDelay);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            client.execute(() -> addBot(client, bot));
                        });
                    }
                    started++;
                    delay += 1_250L;
                }
            }
            feedback(client, "Загрузка пресета " + name + ": запускаю " + started + " ботов.", Formatting.GREEN);
        }
    }

    private void setAntiAfk(MinecraftClient client, String argument) {
        String state = argument == null ? "" : argument.trim().toLowerCase(Locale.ROOT);
        boolean enabled;
        if (state.isBlank()) {
            enabled = sessions.stream().filter(session -> !session.isMain())
                    .noneMatch(BotSession::isAntiAfkEnabled);
        } else if (state.equals("on") || state.equals("вкл")) {
            enabled = true;
        } else if (state.equals("off") || state.equals("выкл")) {
            enabled = false;
        } else {
            feedback(client, "Использование: .bot antiafk [on|off|вкл|выкл]", Formatting.RED);
            return;
        }
        int changed = 0;
        for (BotSession session : sessions) {
            if (session.isMain() || session.getState() == BotSession.State.DISCONNECTED) {
                continue;
            }
            session.setAntiAfkEnabled(enabled);
            changed++;
        }
        String status = enabled
                ? "Anti-AFK включён: соединение поддерживается без искусственных movement/rotation/hand-пакетов."
                : "Anti-AFK выключен: дополнительные действия для ботов не выполняются.";
        feedback(client, status + (changed > 0 ? " Ботов обработано: " + changed + "." : ""),
                enabled ? Formatting.GREEN : Formatting.YELLOW);
    }

    private void setBotChat(MinecraftClient client, String arguments) {
        String payload = arguments == null ? "" : arguments.trim();
        if (payload.isBlank()) {
            StringBuilder sb = new StringBuilder("Трансляция чата ботов в ваш чат:\n");
            int count = 0;
            for (BotSession session : sessions) {
                if (session.isMain() || session.getState() == BotSession.State.DISCONNECTED) continue;
                sb.append(session.isChatMirrorEnabled() ? "§a[ВКЛ] §f" : "§7[ВЫКЛ] §f")
                        .append(session.getName()).append("\n");
                count++;
            }
            if (count == 0) {
                feedback(client, "Нет подключённых ботов.", Formatting.YELLOW);
                return;
            }
            sb.append("§7Используй: .bot chat <имя_бота|all> [on|off]");
            feedback(client, sb.toString(), Formatting.YELLOW);
            return;
        }

        String[] parts = payload.split("\\s+");
        String target = parts[0];
        String state = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

        if (target.equalsIgnoreCase("all") || target.equalsIgnoreCase("все")) {
            boolean enable;
            if (state.isBlank()) {
                enable = sessions.stream().filter(s -> !s.isMain() && s.getState() != BotSession.State.DISCONNECTED)
                        .noneMatch(BotSession::isChatMirrorEnabled);
            } else {
                enable = state.equals("on") || state.equals("вкл") || state.equals("1") || state.equals("true");
            }
            int changed = 0;
            for (BotSession session : sessions) {
                if (session.isMain() || session.getState() == BotSession.State.DISCONNECTED) continue;
                session.setChatMirrorEnabled(enable);
                changed++;
            }
            feedback(client, "Трансляция чата для ВСЕХ ботов (" + changed + "): " + (enable ? "§aВКЛЮЧЕНА" : "§cВЫКЛЮЧЕНА"),
                    enable ? Formatting.GREEN : Formatting.YELLOW);
            return;
        }

        BotSession bot = findByName(target);
        if (bot == null || bot.isMain()) {
            feedback(client, "Бот не найден: " + target + ". Используй .bot chat all [on|off]", Formatting.RED);
            return;
        }

        setBotChatForSingle(client, bot, state);
    }

    private void setBotChatForSingle(MinecraftClient client, BotSession bot, String state) {
        boolean enable;
        if (state.isBlank()) {
            enable = !bot.isChatMirrorEnabled();
        } else {
            enable = state.equalsIgnoreCase("on") || state.equalsIgnoreCase("вкл") || state.equalsIgnoreCase("1") || state.equalsIgnoreCase("true");
        }
        bot.setChatMirrorEnabled(enable);
        feedback(client, "Трансляция сообщений бота " + bot.getName() + " в ваш чат: " + (enable ? "§aВКЛЮЧЕНА" : "§cВЫКЛЮЧЕНА"),
                enable ? Formatting.GREEN : Formatting.YELLOW);
    }

    private void loadBotPresets() {
        if (!Files.exists(botPresetsPath)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(botPresetsPath)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("preset.") || key.length() <= 7) continue;
                List<String> names = new ArrayList<>();
                for (String value : properties.getProperty(key, "").split(",")) {
                    String name = value.trim();
                    if (name.matches("[A-Za-z0-9_]{3,16}")) names.add(name);
                }
                if (!names.isEmpty()) botPresets.put(key.substring(7), List.copyOf(names));
            }
        } catch (IOException error) {
            BotDebug.error("PRESET_LOAD_ERROR", null, "path=" + botPresetsPath, error);
        }
    }

    private void saveBotPresets() {
        Properties properties = new Properties();
        botPresets.forEach((name, names) -> properties.setProperty("preset." + name, String.join(",", names)));
        try {
            Files.createDirectories(botPresetsPath.getParent());
            try (OutputStream output = Files.newOutputStream(botPresetsPath)) {
                properties.store(output, "FluxVisuals bot presets");
            }
        } catch (IOException error) {
            BotDebug.error("PRESET_SAVE_ERROR", null, "path=" + botPresetsPath, error);
        }
    }

    private void loadBotProxyConfig() {
        if (!Files.exists(botProxyPath)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(botProxyPath)) {
            properties.load(input);
            String raw = properties.getProperty("proxy", "").trim();
            if (!raw.isBlank()) {
                globalProxyConfig = BotProxyConfig.parse(raw);
                BotDebug.info("PROXY_LOADED", null, "proxy=" + globalProxyConfig);
            }
        } catch (IOException error) {
            BotDebug.error("PROXY_LOAD_ERROR", null, "path=" + botProxyPath, error);
        }
    }

    private void saveBotProxyConfig() {
        Properties properties = new Properties();
        if (globalProxyConfig != null) {
            properties.setProperty("proxy", globalProxyConfig.getRaw());
        }
        try {
            Files.createDirectories(botProxyPath.getParent());
            try (OutputStream output = Files.newOutputStream(botProxyPath)) {
                properties.store(output, "FluxVisuals bot proxy configuration");
            }
        } catch (IOException error) {
            BotDebug.error("PROXY_SAVE_ERROR", null, "path=" + botProxyPath, error);
        }
    }

    public BotProxyConfig getGlobalProxyConfig() {
        return globalProxyConfig;
    }

    public void setGlobalProxyConfig(BotProxyConfig proxyConfig) {
        this.globalProxyConfig = proxyConfig;
        saveBotProxyConfig();
    }

    public void attachProxyIfPresent(io.netty.channel.Channel channel) {
        BotProxyConfig proxy = CURRENT_CONNECTING_PROXY.get();
        if (proxy != null && channel != null) {
            try {
                channel.pipeline().addFirst("proxy", proxy.createHandler());
                BotDebug.info("PROXY_ATTACHED", null, "proxy=" + proxy);
            } catch (Exception error) {
                BotDebug.error("PROXY_ATTACH_FAILED", null, "proxy=" + proxy, error);
            }
        }
    }

    private void executeProxyCommand(MinecraftClient client, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            if (globalProxyConfig == null) {
                feedback(client, "Глобальный прокси для ботов: ВЫКЛЮЧЕН (прямое подключение).", Formatting.YELLOW);
            } else {
                feedback(client, "Глобальный прокси для ботов: " + globalProxyConfig
                        + " (хост: " + globalProxyConfig.getHost() + ":" + globalProxyConfig.getPort() + ")", Formatting.GREEN);
            }
            long customCount = sessions.stream().filter(s -> s.getProxy() != null).count();
            if (customCount > 0) {
                feedback(client, "Боты с индивидуальными прокси: " + customCount, Formatting.AQUA);
                for (BotSession s : sessions) {
                    if (s.getProxy() != null) {
                        feedback(client, " - " + s.getName() + " -> " + s.getProxy(), Formatting.GRAY);
                    }
                }
            }
            feedback(client, "Установить: .bot proxy <user:pass@host:port> | Выключить: .bot proxy off", Formatting.GRAY);
            return;
        }

        String arg = arguments.trim();
        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("clear")
                || arg.equalsIgnoreCase("none") || arg.equalsIgnoreCase("выкл")
                || arg.equalsIgnoreCase("remove") || arg.equalsIgnoreCase("reset")) {
            setGlobalProxyConfig(null);
            feedback(client, "Прокси для ботов выключен. Новые боты будут подключаться напрямую.", Formatting.YELLOW);
            return;
        }

        BotProxyConfig parsed = BotProxyConfig.parse(arg);
        if (parsed == null) {
            feedback(client, "Ошибка: неверный формат прокси! Примеры:\n"
                    + ".bot proxy proxyapi-v2.xxx:pass@93.123.85.144:1080\n"
                    + ".bot proxy 93.123.85.144:1080:user:pass\n"
                    + ".bot proxy 93.123.85.144:1080\n"
                    + ".bot proxy off", Formatting.RED);
            return;
        }

        setGlobalProxyConfig(parsed);
        feedback(client, "Прокси для ботов установлен: " + parsed + " (" + parsed.getHost() + ":" + parsed.getPort()
                + "). Все новые подключения ботов пойдут через этот прокси.", Formatting.GREEN);
    }

    private void setBotProxy(MinecraftClient client, BotSession session, String arguments) {
        if (session == null) {
            feedback(client, "Бот не найден.", Formatting.RED);
            return;
        }
        if (arguments == null || arguments.isBlank()) {
            BotProxyConfig p = session.getProxy();
            if (p == null) {
                feedback(client, "Бот " + session.getName() + " использует глобальный прокси ("
                        + (globalProxyConfig != null ? globalProxyConfig.toString() : "выкл") + ").", Formatting.YELLOW);
            } else {
                feedback(client, "Бот " + session.getName() + " использует индивидуальный прокси: " + p, Formatting.GREEN);
            }
            return;
        }
        String arg = arguments.trim();
        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("clear")
                || arg.equalsIgnoreCase("none") || arg.equalsIgnoreCase("выкл")
                || arg.equalsIgnoreCase("remove") || arg.equalsIgnoreCase("reset")) {
            session.setProxy(null);
            feedback(client, "Индивидуальный прокси для бота " + session.getName() + " сброшен на глобальный.", Formatting.YELLOW);
            return;
        }
        BotProxyConfig parsed = BotProxyConfig.parse(arg);
        if (parsed == null) {
            feedback(client, "Ошибка: неверный формат прокси для бота " + session.getName() + "!", Formatting.RED);
            return;
        }
        session.setProxy(parsed);
        feedback(client, "Индивидуальный прокси для бота " + session.getName() + " установлен: " + parsed, Formatting.GREEN);
    }

    private void setBotFunction(MinecraftClient client, BotSession target, String arguments) {
        String[] parts = arguments == null ? new String[0] : arguments.trim().split("\\s+");
        if (parts.length != 2) {
            feedback(client, "Использование: .bot function <название_функции> <on|off>", Formatting.RED);
            return;
        }
        String function = parts[0].toLowerCase(Locale.ROOT);
        boolean enabled;
        switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "on", "включить", "вкл" -> enabled = true;
            case "off", "выключить", "выкл" -> enabled = false;
            default -> {
                feedback(client, "Состояние должно быть on/off или включить/выключить.", Formatting.RED);
                return;
            }
        }
        if (target == null) {
            feedback(client, "Нет активной сессии.", Formatting.RED);
            return;
        }
        Module module = findFunction(function);
        if (module == null) {
            feedback(client, "Неизвестная функция. Используй подсказку после .bot <ник> function.", Formatting.RED);
            return;
        }
        if (target.isMain()) {
            module.setEnabled(enabled);
        } else {
            if (module == FluxVisualsClient.MODULE_MANAGER.getAutoBuy()) {
                target.runWithContext(() -> target.getAutoBuy().setEnabledSilently(enabled));
            } else if (module == FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK()) {
                target.runWithContext(() -> target.getAutoResellAFK().setEnabledSilently(enabled));
            } else if (module == FluxVisualsClient.MODULE_MANAGER.getItemCrafter()) {
                target.runWithContext(() -> target.getItemCrafter().setEnabledSilently(enabled));
            } else if (module == FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver()) {
                target.setCaptchaSolverEnabled(enabled);
            } else {
                target.setModuleState(module.getName(), enabled);
                if (target == activeSession) {
                    target.runWithContext(() -> module.setEnabledSilently(enabled));
                }
            }
        }
        BotDebug.info("FUNCTION_STATE_CHANGED", target,
                "function=" + function + ", enabled=" + enabled);
        feedback(client, "Функция " + function + " для " + target.getName()
                + (enabled ? " включена." : " выключена."), Formatting.GREEN);
    }

    private Module findFunction(String requested) {
        String normalized = requested.toLowerCase(Locale.ROOT).replace("_", "");
        for (Module module : FluxVisualsClient.MODULE_MANAGER.getStatefulModules()) {
            String name = module.getName().toLowerCase(Locale.ROOT).replace("_", "");
            if (name.equals(normalized)) {
                return module;
            }
        }
        return null;
    }

    private List<String> suggestFunctionNames() {
        return FluxVisualsClient.MODULE_MANAGER.getStatefulModules().stream()
                .map(Module::getName)
                .toList();
    }

    private List<String> suggestBaritoneCommands() {
        return List.of(
                "help", "goto", "goal", "path", "mine", "tunnel", "follow",
                "explore", "farm", "build", "sel", "stop", "pause", "resume",
                "cancel", "surface", "thisway", "waypoints", "eta", "proc",
                "version", "modified", "chatcontrol", "come", "click", "rightclick",
                "axis", "blacklist", "cleararea", "con", "coords", "destroy",
                "echo", "find", "invert", "item", "litem", "look", "mobfarm",
                "repack", "reset", "set", "spawn", "unpause", "world"
        );
    }

    private void addBot(MinecraftClient client, String name) {
        ensureMainSession(client);
        if (mainSession == null || mainSession.getAddress().isBlank()) {
            feedback(client, "Сначала зайди на многопользовательский сервер.", Formatting.RED);
            return;
        }
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) {
            feedback(client, "Ник должен содержать 3-16 букв, цифр или символов подчёркивания.", Formatting.RED);
            return;
        }
        BotSession existing = findByName(trimmed);
        if (existing != null && existing.getState() == BotSession.State.DISCONNECTED && !existing.isMain()) {
            BotSession stale = existing;
            sessionPacketQueues.remove(stale);
            sessions.remove(existing);
            listenerSessions.entrySet().removeIf(entry -> entry.getValue() == stale);
            if (pendingAutoSwitchSession == existing) {
                pendingAutoSwitchSession = null;
            }
            if (telegramTargetName.equalsIgnoreCase(existing.getName())) {
                telegramTargetName = "main";
            }
            existing = null;
        }
        if (existing != null) {
            feedback(client, "Аккаунт с таким ником уже подключён.", Formatting.RED);
            return;
        }

        BotSession session = BotSession.bot(trimmed, mainSession.getAddress());
        session.prepareAutomationDefaults(
                getAutoBuyForCurrentSession(FluxVisualsClient.MODULE_MANAGER.getAutoBuy()),
                getAutoResellAFKForCurrentSession(FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK())
        );
        session.initializeModuleStates(FluxVisualsClient.MODULE_MANAGER);
        sessions.add(session);
        pendingAutoSwitchSession = null;
        BotDebug.info("CONNECT_REQUEST", session, "address=" + session.getAddress());
        feedback(client, "Подключаю " + trimmed + " к " + session.getAddress() + "...", Formatting.YELLOW);
        startConnection(client, session);
    }

    private void startConnection(MinecraftClient client, BotSession session) {
        if ((session.getAddress() == null || session.getAddress().isBlank()) && mainSession != null && !mainSession.getAddress().isBlank()) {
            session.setAddress(mainSession.getAddress());
        }
        final String targetAddress = session.getAddress();
        final boolean nativeTransport = client.options.shouldUseNativeTransport();
        final ServerInfo currentServer = client.getCurrentServerEntry();
        final long generation = session.beginConnectionGeneration();
        session.setState(BotSession.State.CONNECTING);
        session.setStatusText("Подключение к серверу...");
        final BotProxyConfig proxy = session.getProxy() != null ? session.getProxy() : globalProxyConfig;
        BotDebug.info("CONNECTION_BEGIN", session, "generation=" + generation + ", address=" + targetAddress
                + ", proxy=" + (proxy != null ? proxy.toString() : "none"));
        Thread.ofPlatform().daemon().name("Fuga-Bot-" + session.getName()).start(() -> {
            CURRENT_CONNECTING_PROXY.set(proxy);
            try {
                ServerAddress serverAddress = ServerAddress.parse(targetAddress);
                Optional<Address> resolved = AllowedAddressResolver.DEFAULT.resolve(serverAddress);
                if (resolved.isEmpty()) {
                    throw new IllegalStateException("Unable to resolve server address");
                }
                Address address = resolved.get();
                ClientConnection connection = new ClientConnection(NetworkSide.CLIENTBOUND);
                bindBotConnection(session, connection);
                ChannelFuture future = ClientConnection.connect(
                        address.getInetSocketAddress(),
                        nativeTransport,
                        connection
                );
                BotDebug.trace("SOCKET_CREATED", session, "generation=" + generation
                        + ", success=" + future.isSuccess() + ", done=" + future.isDone());
                if (!future.awaitUninterruptibly(SOCKET_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    future.cancel(true);
                    throw new IllegalStateException("Таймаут соединения с сервером");
                }
                if (!future.isSuccess()) {
                    Throwable cause = future.cause();
                    throw new IllegalStateException(
                            cause == null ? "Не удалось подключиться к серверу" : cause.getMessage(),
                            cause
                    );
                }
                if (session.getState() == BotSession.State.DISCONNECTED) {
                    connection.disconnect(Text.literal("Подключение отменено"));
                    return;
                }

                if (!session.isConnectionGeneration(generation)
                        || session.getConnection() != connection
                        || session.getState() == BotSession.State.DISCONNECTED) {
                    BotDebug.warn("STALE_CONNECTION_IGNORED", session, "generation=" + generation);
                    return;
                }
                future.channel().eventLoop().execute(() -> {
                    try {
                        if (!session.isConnectionGeneration(generation)
                                || session.getConnection() != connection
                                || session.getState() == BotSession.State.DISCONNECTED) {
                            BotDebug.warn("STALE_CONNECTION_IGNORED", session,
                                    "generation=" + generation);
                            return;
                        }
                        if (!future.channel().isActive()) {
                            failConnection(client, session,
                                    new IllegalStateException("Канал подключения закрыт до входа"));
                            return;
                        }
                        // Match vanilla ConnectScreen, but keep protocol setup
                        // ordered behind Netty's channelActive callback.
                        ServerInfo serverInfo = currentServer;
                        if (serverInfo == null) {
                            serverInfo = new ServerInfo(
                                    "Fuga bot " + session.getName(),
                                    session.getAddress(),
                                    ServerInfo.ServerType.OTHER
                            );
                        }
                        ClientLoginNetworkHandler loginHandler = new ClientLoginNetworkHandler(
                                connection,
                                client,
                                serverInfo,
                                null,
                                false,
                                (Duration) null,
                                status -> session.setStatusText(status.getString()),
                                null
                        );
                        connection.connect(
                                address.getHostName(),
                                address.getPort(),
                                LoginStates.C2S,
                                LoginStates.S2C,
                                loginHandler,
                                false
                        );
                        BotDebug.info("PROTOCOL_LOGIN", session, "generation=" + generation
                                + ", host=" + address.getHostName() + ", port=" + address.getPort());
                        UUID offlineUuid = UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + session.getName()).getBytes(StandardCharsets.UTF_8)
                        );
                        session.setState(BotSession.State.LOGGING_IN);
                        session.setStatusText("Вход на сервер");
                        connection.send(new LoginHelloC2SPacket(session.getName(), offlineUuid));
                        BotDebug.info("LOGIN_HELLO_SENT", session, "address=" + targetAddress);
                    } catch (Throwable error) {
                        failConnection(client, session, error);
                    }
                });
                return;
            } catch (Throwable error) {
                session.setState(BotSession.State.DISCONNECTED);
                String text = error.getMessage();
                session.setStatusText(text == null || text.isBlank() ? error.getClass().getSimpleName() : text);
                BotDebug.error("CONNECT_FAILED", session, "address=" + targetAddress, error);
                client.execute(() -> feedback(
                        client,
                        "Бот " + session.getName() + " не подключился. Причина: " + session.getStatusText(),
                        Formatting.RED));
                ClientConnection failedConnection = session.getConnection();
                if (failedConnection != null && failedConnection.isOpen()) {
                    failedConnection.disconnect(Text.literal(session.getStatusText()));
                }
            } finally {
                CURRENT_CONNECTING_PROXY.remove();
            }
        });
    }

    private void failConnection(MinecraftClient client, BotSession session, Throwable error) {
        session.setState(BotSession.State.DISCONNECTED);
        String text = error.getMessage();
        session.setStatusText(text == null || text.isBlank() ? error.getClass().getSimpleName() : text);
        BotDebug.error("CONNECT_FAILED", session, session.getStatusText(), error);
        client.execute(() -> feedback(client,
                "Бот " + session.getName() + " не подключился. Причина: " + session.getStatusText(),
                Formatting.RED));
        ClientConnection connection = session.getConnection();
        if (connection != null && connection.isOpen()) {
            connection.disconnect(Text.literal(session.getStatusText()));
        }
    }

    private void removeBot(MinecraftClient client, String name) {
        BotDebug.info("REMOVE_REQUEST", activeSession, "target=" + name);
        BotSession session = findByName(name);
        if (session == null || session.isMain()) {
            feedback(client, "Бот не найден: " + name, Formatting.RED);
            return;
        }
        if (session == activeSession && mainSession != null && mainSession.isReadyToControl()) {
            switchTo(mainSession);
        }
        disconnect(session, "Удалён");
        sessionPacketQueues.remove(session);
        sessions.remove(session);
        if (pendingAutoSwitchSession == session) {
            pendingAutoSwitchSession = null;
        }
        if (telegramTargetName.equalsIgnoreCase(session.getName())) {
            telegramTargetName = "main";
        }
        feedback(client, "Бот " + session.getName() + " удалён.", Formatting.YELLOW);
    }

    private void removeAllBots(MinecraftClient client) {
        if (activeSession != null && !activeSession.isMain()
                && mainSession != null && mainSession.isReadyToControl()) {
            switchTo(mainSession);
        }
        int removed = 0;
        for (BotSession session : new ArrayList<>(sessions)) {
            if (!session.isMain()) {
                disconnect(session, "Удалён");
                sessionPacketQueues.remove(session);
                sessions.remove(session);
                removed++;
            }
        }
        feedback(client, "Удалено ботов: " + removed + ".", Formatting.YELLOW);
    }

    private void listBots(MinecraftClient client) {
        if (sessions.isEmpty()) {
            feedback(client, "Нет подключённых сессий.", Formatting.GRAY);
            return;
        }
        feedback(client, "Сессии:", Formatting.AQUA);
        for (BotSession session : sessions) {
            String marker = session == activeSession ? "* " : "  ";
            String type = session.isMain() ? "основа" : "бот";
            String status = switch (session.getState()) {
                case CONNECTING -> "подключение";
                case LOGGING_IN -> "вход";
                case PLAYING -> "в игре";
                case DISCONNECTED -> "отключён";
            };
            if (!session.getStatusText().isBlank()) {
                status += ": " + session.getStatusText();
            }
            feedback(client, marker + session.getName() + " [" + type + "] " + status, Formatting.GRAY);
        }
    }

    private void printBotDebugStatus(MinecraftClient client, String arguments) {
        String targetName = arguments == null ? "" : arguments.trim();
        List<BotSession> targets = new ArrayList<>();
        if (!targetName.isBlank() && !targetName.equalsIgnoreCase("status") && !targetName.equalsIgnoreCase("all") && !targetName.equalsIgnoreCase("инфо")) {
            BotSession found = findByName(targetName);
            if (found != null) {
                targets.add(found);
            } else {
                feedback(client, "Сессия не найдена: " + targetName, Formatting.RED);
                return;
            }
        } else {
            targets.addAll(sessions);
        }
        if (targets.isEmpty()) {
            feedback(client, "Нет подключённых сессий для диагностики.", Formatting.GRAY);
            return;
        }
        feedback(client, "§6=== [Bot Debug Inspector] ===", Formatting.GOLD);
        for (BotSession session : targets) {
            boolean isActive = session == activeSession;
            String header = "§e[" + (isActive ? "§aACTIVE§e " : "") + (session.isMain() ? "MAIN" : "BOT") + "] §f" + session.getName() + " §7(" + session.getState() + ")";
            feedback(client, header, Formatting.YELLOW);

            net.minecraft.client.world.ClientWorld w = session.getWorld();
            String worldName = w != null && w.getRegistryKey() != null ? w.getRegistryKey().getValue().toString() : "null";

            ClientPlayerEntity p = session.getPlayer();
            if (p != null) {
                String pos = String.format(Locale.ROOT, "Pos: §fX=%.1f Y=%.1f Z=%.1f §7(yaw=%.1f, pitch=%.1f)", p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
                feedback(client, "  §7" + pos, Formatting.GRAY);

                ClientPlayerInteractionManager im = session.getInteractionManager();
                GameMode imMode = im != null ? im.getCurrentGameMode() : null;
                GameMode tabMode = null;
                if (session.getNetworkHandler() != null) {
                    PlayerListEntry entry = session.getNetworkHandler().getPlayerListEntry(p.getGameProfile().getId());
                    if (entry != null) tabMode = entry.getGameMode();
                }
                feedback(client, "  §7World: §f" + worldName + " §7| GameMode: IM=§a" + imMode + "§7, Tab=§a" + tabMode, Formatting.GRAY);

                PlayerAbilities a = p.getAbilities();
                String abilitiesStr = String.format(Locale.ROOT, "Abilities: fly=§%s%b§7, allowFly=§%s%b§7, creative=§%s%b§7, invuln=§%s%b§7",
                        a.flying ? "a" : "c", a.flying,
                        a.allowFlying ? "a" : "c", a.allowFlying,
                        a.creativeMode ? "a" : "c", a.creativeMode,
                        a.invulnerable ? "a" : "c", a.invulnerable);
                feedback(client, "  §7" + abilitiesStr, Formatting.GRAY);

                Vec3d vel = p.getVelocity();
                String physicsStr = String.format(Locale.ROOT, "Physics: onGround=§%s%b§7, noClip=§%s%b§7, noGrav=§%s%b§7, fall=%.1f, vel=(%.2f, %.2f, %.2f)",
                        p.isOnGround() ? "a" : "c", p.isOnGround(),
                        p.noClip ? "c" : "a", p.noClip,
                        p.hasNoGravity() ? "c" : "a", p.hasNoGravity(),
                        p.fallDistance,
                        vel != null ? vel.x : 0, vel != null ? vel.y : 0, vel != null ? vel.z : 0);
                feedback(client, "  §7" + physicsStr, Formatting.GRAY);
            } else {
                feedback(client, "  §7World: §f" + worldName + " §7| Player: §cnull", Formatting.GRAY);
            }
        }
        feedback(client, "§6============================", Formatting.GOLD);
    }

    private void switchByName(MinecraftClient client, String name) {
        BotDebug.info("SWITCH_REQUEST", activeSession, "target=" + name);
        BotSession session = findByName(name);
        if (session == null) {
            feedback(client, "Сессия не найдена: " + name, Formatting.RED);
            return;
        }
        if (!session.isReadyToControl() || !session.isInPlayProtocol()
                || session.getState() == BotSession.State.DISCONNECTED) {
            feedback(client, session.getName() + " ещё не готов: " + session.getStatusText(), Formatting.RED);
            return;
        }
        switchTo(session);
        feedback(client, "Управление переключено на " + session.getName() + ".", Formatting.GREEN);
    }

    private void switchNext(MinecraftClient client) {
        BotDebug.info("SWITCH_NEXT_REQUEST", activeSession, "sessions=" + sessions.size());
        if (sessions.size() < 2 || activeSession == null) {
            feedback(client, "Нет другой готовой сессии для переключения.", Formatting.RED);
            return;
        }
        int start = Math.max(0, sessions.indexOf(activeSession));
        for (int offset = 1; offset <= sessions.size(); offset++) {
            BotSession candidate = sessions.get((start + offset) % sessions.size());
            if (candidate != activeSession && candidate.isReadyToControl()
                    && candidate.isInPlayProtocol()
                    && candidate.getState() != BotSession.State.DISCONNECTED) {
                switchTo(candidate);
                feedback(client, "Управление переключено на " + candidate.getName() + ".", Formatting.GREEN);
                return;
            }
        }
        feedback(client, "Нет другой готовой сессии для переключения.", Formatting.RED);
    }

    private void switchToMain(MinecraftClient client) {
        if (mainSession == null || !mainSession.isReadyToControl()) {
            feedback(client, "Основной аккаунт ещё не готов.", Formatting.RED);
            return;
        }
        switchTo(mainSession);
        feedback(client, "Управление переключено на основной аккаунт.", Formatting.GREEN);
    }

    public void switchTo(BotSession target) {
        BotSession current = activeSession;
        if (target == null) {
            return;
        }
        if (current == target) {
            if (target.isReadyToControl()) {
                target.installAsActive();
            }
            return;
        }
        if (target.getState() == BotSession.State.DISCONNECTED && !target.isMain()) {
            BotDebug.warn("SWITCH_REJECTED", target, "target is disconnected");
            return;
        }
        if (!target.isReadyToControl() && !target.isMain()) {
            BotDebug.warn("SWITCH_REJECTED", target,
                    "ready=" + target.isReadyToControl()
                            + ", protocol=" + target.isInPlayProtocol());
            return;
        }
        switching = true;
        BotDebug.info("SWITCH_BEGIN", target, "previous=" + (current == null ? "none" : current.getName()));
        try {
            if (current != null) {
                current.captureFromClient();
                if (!current.isMain()) {
                    // Capture the exact orientation at the moment control is
                    // released; later background packets must not rotate it.
                    current.holdBackgroundRotation();
                }
                current.captureModuleStates(FluxVisualsClient.MODULE_MANAGER);
                // Detach the old screen handler before changing the network/player context.
                if (MinecraftClient.getInstance().currentScreen != null) {
                    MinecraftClient.getInstance().setScreen(null);
                }
            }
            activeSession = target;
            if (current != null && current.isMain() && !target.isMain()) {
                current.triggerAntiAfkLookFromBackgroundSwitch();
            }
            target.clearTransientScreen();
            target.installAsActive();
            target.initializeModuleStates(FluxVisualsClient.MODULE_MANAGER);
            target.installModuleStates(FluxVisualsClient.MODULE_MANAGER);
            BotDebug.info("SWITCH_COMPLETE", target,
                    "previous=" + (current == null ? "none" : current.getName())
                            + ", world=" + (target.getWorld() != null)
                            + ", player=" + (target.getPlayer() != null)
                            + ", handler=" + (target.getNetworkHandler() != null));
        } catch (Throwable error) {
            BotDebug.error("SWITCH_FAILED", target,
                    "previous=" + (current == null ? "none" : current.getName()), error);
            activeSession = current;
            if (current != null && current.isReadyToControl()) {
                try {
                    current.installAsActive();
                } catch (Throwable restoreError) {
                    BotDebug.error("SWITCH_ROLLBACK_FAILED", current,
                            "visible state could not be restored", restoreError);
                }
            }
        } finally {
            switching = false;
            BotSession visible = activeSession;
            if (visible != null && visible.isMain()) {
                drainQueuedPacketsForSession(visible);
            }
            BotDebug.info("SWITCH_FINISH", activeSession, "switching=" + switching);
        }
    }

    // State for capturing real rendered in-game bot screenshots
    private volatile boolean botScreenCaptureActive = false;
    private int botScreenFramesRemaining = 0;
    private BotSession botScreenTargetSession = null;
    private BotSession botScreenRestoreSession = null;
    private Screen botScreenRestoreScreen = null;
    private boolean botScreenOpenedInventory = false;
    private Consumer<byte[]> botScreenCallback = null;

    public synchronized void requestRealBotScreenshot(BotSession target, boolean openInventory, Consumer<byte[]> callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            callback.accept(null);
            return;
        }

        client.execute(() -> {
            if (target == null || target.getPlayer() == null) {
                callback.accept(null);
                return;
            }

            BotSession current = activeSession;
            botScreenRestoreSession = current;
            botScreenRestoreScreen = client.currentScreen;
            botScreenTargetSession = target;
            botScreenCallback = callback;
            botScreenOpenedInventory = false;

            if (current != target) {
                switchTo(target);
            }

            // After switchTo, client.world must be non-null for InventoryScreen to tick safely.
            // Background bots may not have a captured world — fall back gracefully.
            if (client.world == null) {
                BotDebug.warn("BOT_SCREENSHOT_NO_WORLD", target, "client.world is null after switchTo, aborting screenshot");
                if (current != null && current != activeSession && current.isReadyToControl()) {
                    switchTo(current);
                }
                callback.accept(null);
                botScreenTargetSession = null;
                botScreenRestoreSession = null;
                botScreenRestoreScreen = null;
                botScreenCallback = null;
                return;
            }

            if (openInventory) {
                if (client.currentScreen == null && target.getPlayer() != null) {
                    client.setScreen(new InventoryScreen(target.getPlayer()));
                    botScreenOpenedInventory = true;
                }
            }

            botScreenFramesRemaining = 2;
            botScreenCaptureActive = true;
        });
    }

    public void onFrameRendered(MinecraftClient client) {
        if (!botScreenCaptureActive) {
            return;
        }

        botScreenFramesRemaining--;
        if (botScreenFramesRemaining > 0) {
            return;
        }

        botScreenCaptureActive = false;
        final BotSession restoreSession = botScreenRestoreSession;
        final Screen restoreScreen = botScreenRestoreScreen;
        final boolean closeInventory = botScreenOpenedInventory;
        final Consumer<byte[]> callback = botScreenCallback;

        try {
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
                Path temp = null;
                try (image) {
                    temp = Files.createTempFile("fuga-real-bot-", ".png");
                    image.writeTo(temp);
                    byte[] bytes = Files.readAllBytes(temp);
                    if (callback != null) {
                        callback.accept(bytes);
                    }
                } catch (IOException | RuntimeException ex) {
                    BotDebug.error("BOT_SCREENSHOT_SAVE_FAILED", botScreenTargetSession, "failed to save screenshot", ex);
                    if (callback != null) {
                        callback.accept(null);
                    }
                } finally {
                    if (temp != null) {
                        try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                    }
                }
            });
        } finally {
            if (closeInventory && client.currentScreen instanceof InventoryScreen) {
                client.setScreen(restoreScreen);
            }
            if (restoreSession != null && restoreSession != activeSession && restoreSession.isReadyToControl()) {
                switchTo(restoreSession);
            }
            if (client.player != null) {
                if (!(client.player.input instanceof KeyboardInput)) {
                    client.player.input = new KeyboardInput(client.options);
                }
                if (client.currentScreen == null && !client.mouse.isCursorLocked()) {
                    client.mouse.lockCursor();
                }
            }
            botScreenTargetSession = null;
            botScreenRestoreSession = null;
            botScreenRestoreScreen = null;
            botScreenCallback = null;
        }
    }

    private void sendAllBotsToAnarchy(MinecraftClient client, String input) {
        String number = input == null ? "" : input.trim();
        if (!number.matches("\\d+")) {
            feedback(client, "Использование: .bot an <номер>", Formatting.RED);
            return;
        }
        int sent = 0;
        for (BotSession session : sessions) {
            if (session.isMain() || session.getState() == BotSession.State.DISCONNECTED) {
                continue;
            }
            sent++;
            runAfterAntiAfk(session, 0L, () -> {
                boolean[] sentFromSession = {false};
                ClientPlayNetworkHandler handler = session.getNetworkHandler();
                if (handler != null && session.getConnection() != null
                        && session.getConnection().isOpen()
                        && session.getConnection().getPacketListener() == handler) {
                    session.getAutoResellAFK().resumeAfterModeSwitch();
                    handler.sendChatCommand("an" + number);
                    sentFromSession[0] = true;
                    BotDebug.info("ANARCHY_COMMAND_SENT", session, "command=/an" + number);
                } else {
                    pendingBotCommands.put(session, "an" + number);
                    BotDebug.info("ANARCHY_COMMAND_PENDING", session,
                            "command=/an" + number + ", reason=protocol_transition");
                    sentFromSession[0] = true;
                }
            });
        }
        BotDebug.info("ANARCHY_ALL_SENT", activeSession, "number=" + number + ", bots=" + sent);
        feedback(client, "Команду /an" + number + " отправили боты: " + sent + ".",
                sent > 0 ? Formatting.GREEN : Formatting.RED);
    }

    private void executeBaritone(MinecraftClient client, String input) {
        if (input == null || input.isBlank()) {
            feedback(client, "Использование: .bot baritone <команда> или #<команда>", Formatting.GRAY);
            return;
        }
        feedback(client, executeBaritoneForSession(activeSession, input)
                        ? "Команда Baritone выполнена."
                        : "Команда Baritone не выполнена.",
                Formatting.AQUA);
    }

    private boolean executeBaritoneForSession(BotSession session, String command) {
        boolean[] result = {false};
        if (session != null && !session.isMain() && session.isInPlayProtocol()) {
            session.runWithContext(() -> result[0] = BaritoneBridge.execute(command));
        } else {
            result[0] = BaritoneBridge.execute(command);
        }
        return result[0];
    }

    private void sendBotCommand(MinecraftClient client, String input) {
        if (input == null || input.isBlank()) {
            feedback(client, "Использование: .bot команда <сообщение или /команда>", Formatting.RED);
            return;
        }
        String payload = input.trim();
        BotSession namedTarget = null;
        int separator = payload.indexOf(' ');
        if (separator > 0) {
            BotSession candidate = findByName(payload.substring(0, separator));
            if (candidate != null) {
                namedTarget = candidate;
                payload = payload.substring(separator + 1).trim();
            }
        }
        if (payload.isBlank()) {
            feedback(client, "Укажи сообщение после ника.", Formatting.RED);
            return;
        }

        int sent = 0;
        for (BotSession session : sessions) {
            if (session.isMain() || session.getState() == BotSession.State.DISCONNECTED
                    || (namedTarget != null && session != namedTarget)) {
                continue;
            }
            String message = payload;
            sent++;
            runAfterAntiAfk(session, 0L, () -> {
                ClientPlayNetworkHandler handler = session.getNetworkHandler();
                if (handler != null && session.getConnection() != null
                        && session.getConnection().isOpen()
                        && session.getConnection().getPacketListener() == handler) {
                    if (message.startsWith("/") && message.length() > 1) {
                        handler.sendChatCommand(message.substring(1));
                    } else {
                        handler.sendChatMessage(message);
                    }
                    BotDebug.info("BOT_COMMAND_SENT", session,
                            "payload=" + message.replace('\n', ' '));
                } else {
                    pendingBotCommands.put(session, message.startsWith("/") && message.length() > 1
                            ? message.substring(1) : message);
                    BotDebug.info("BOT_COMMAND_PENDING", session,
                            "payload=" + message.replace('\n', ' ') + ", reason=protocol_transition");
                }
            });
        }
        feedback(client, "Сообщение отправили боты: " + sent + ".", sent > 0 ? Formatting.GREEN : Formatting.RED);
    }

    private void flushPendingBotCommand(BotSession session) {
        if (session == null || session.isMain() || !session.isInPlayProtocol()) {
            return;
        }
        String command = pendingBotCommands.get(session);
        ClientPlayNetworkHandler handler = session.getNetworkHandler();
        if (command == null || handler == null || session.getConnection() == null
                || !session.getConnection().isOpen()
                || session.getConnection().getPacketListener() != handler) {
            return;
        }
        pendingBotCommands.remove(session, command);
        if (command.startsWith("/") && command.length() > 1) {
            handler.sendChatCommand(command.substring(1));
        } else if (command.matches("[A-Za-z0-9_]+(?:\\s+.*)?")) {
            handler.sendChatCommand(command);
        } else {
            handler.sendChatMessage(command);
        }
        BotDebug.info("BOT_COMMAND_FLUSHED", session, "payload=" + command.replace('\n', ' '));
    }

    private void dropItems(MinecraftClient client, String name) {
        String targetName = name == null ? "" : name.trim();
        BotSession target = findByName(targetName);
        if (target == null || target.isMain()) {
            feedback(client, "Бот не найден: " + targetName, Formatting.RED);
            return;
        }
        if (!target.isReadyToControl() || !target.isInPlayProtocol()) {
            feedback(client, "Бот " + target.getName() + " ещё не готов к действиям.", Formatting.RED);
            return;
        }
        final boolean[] started = {false};
        target.runWithContext(() -> started[0] = target.beginInventoryDrop());
        BotDebug.info("INVENTORY_DROP_REQUEST", target,
                "started=" + started[0] + ", active=" + (target == activeSession));
        feedback(client, started[0]
                        ? "Бот " + target.getName() + ": GUI закрывается, инвентарь выбрасывается."
                        : "Не удалось начать выброс предметов у " + target.getName() + ".",
                started[0] ? Formatting.YELLOW : Formatting.RED);
    }

    private void payBots(MinecraftClient client, String input) {
        String payload = input == null ? "" : input.trim();
        if (payload.isBlank()) {
            feedback(client, ".bot pay [name|all] <сумма|all>", Formatting.RED);
            return;
        }
        String[] parts = payload.split("\\s+");
        BotSession recipient = activeSession;
        if (recipient == null || !recipient.isReadyToControl() || !recipient.isInPlayProtocol()) {
            feedback(client, "Нет готового контролируемого аккаунта для получения денег.", Formatting.RED);
            return;
        }

        BotSession selectedSender = null;
        String amountToken;
        if (parts.length == 1) {
            amountToken = parts[0];
        } else if (parts.length == 2) {
            if (!"all".equalsIgnoreCase(parts[0])) {
                selectedSender = findByName(parts[0]);
            }
            if (!"all".equalsIgnoreCase(parts[0])
                    && (selectedSender == null || selectedSender.isMain())) {
                feedback(client, "Бот-отправитель не найден: " + parts[0], Formatting.RED);
                return;
            }
            amountToken = parts[1];
        } else {
            feedback(client, ".bot pay [name|all] <сумма|all>", Formatting.RED);
            return;
        }

        List<BotSession> senders = new ArrayList<>();
        if (selectedSender != null) {
            if (selectedSender != recipient && selectedSender.isReadyToControl()
                    && selectedSender.isInPlayProtocol()) {
                senders.add(selectedSender);
            }
        } else {
            for (BotSession session : sessions) {
                if (!session.isMain() && session != recipient
                        && session.isReadyToControl() && session.isInPlayProtocol()) {
                    senders.add(session);
                }
            }
        }
        if (senders.isEmpty()) {
            feedback(client, "Нет готовых ботов-отправителей.", Formatting.RED);
            return;
        }

        if ("all".equalsIgnoreCase(amountToken)) {
            for (int index = 0; index < senders.size(); index++) {
                scheduleBalanceRequest(senders.get(index), recipient.getName(),
                        index * PAY_ALL_BOT_SPACING_MS, 0);
            }
            feedback(client, "Запросил баланс у ботов: " + senders.size() + ". После ответа они переведут всё.", Formatting.YELLOW);
            return;
        }

        long amount;
        try {
            amount = parseMoneyToken(amountToken);
        } catch (NumberFormatException ignored) {
            feedback(client, "Сумма должна быть целым положительным числом или all.", Formatting.RED);
            return;
        }
        if (amount <= 0L) {
            feedback(client, "Сумма должна быть больше нуля.", Formatting.RED);
            return;
        }
        for (BotSession sender : senders) {
            sendPayCommand(sender, recipient.getName(), amount);
        }
        feedback(client, "Перевод отправлен от ботов: " + senders.size() + ".", Formatting.GREEN);
    }

    private static long parseMoneyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new NumberFormatException("empty amount");
        }
        String value = token.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        long multiplier = 1L;
        if (value.endsWith("kk")) {
            multiplier = 1_000_000L;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("m")) {
            multiplier = 1_000_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("k")) {
            multiplier = 1_000L;
            value = value.substring(0, value.length() - 1);
        }
        try {
            return new BigDecimal(value).multiply(BigDecimal.valueOf(multiplier))
                    .toBigIntegerExact().longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new NumberFormatException("invalid amount: " + token);
        }
    }

    private void sendPayCommand(BotSession sender, String recipient, long amount) {
        runAfterAntiAfk(sender, 0L, () -> {
            if (!sendPayCommandNow(sender, recipient, amount, false)) {
                BotDebug.warn("PAY_COMMAND_SKIPPED", sender,
                        "reason=play_handler_not_current, recipient=" + recipient + ", amount=" + amount);
                return;
            }
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(750L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                MinecraftClient.getInstance().execute(() -> {
                    if (isCurrentPlayHandler(sender)) {
                        sendPayCommandNow(sender, recipient, amount, true);
                    } else {
                        BotDebug.warn("PAY_REPEAT_SKIPPED", sender, "connection_not_ready=true");
                    }
                });
            });
        });
    }

    private boolean sendPayCommandNow(BotSession sender, String recipient, long amount, boolean repeat) {
        if (!isCurrentPlayHandler(sender)) {
            return false;
        }
        boolean[] sent = {false};
        sender.runWithContext(() -> {
            ClientPlayNetworkHandler handler = sender.getNetworkHandler();
            if (handler != null && sender.getConnection() != null
                    && sender.getConnection().isOpen()
                    && sender.getConnection().getPacketListener() == handler) {
                handler.sendChatCommand("pay " + recipient + " " + amount);
                sent[0] = true;
            }
        });
        if (sent[0]) {
            BotDebug.info(repeat ? "PAY_COMMAND_REPEAT" : "PAY_COMMAND_SENT", sender,
                    "recipient=" + recipient + ", amount=" + amount);
        }
        return sent[0];
    }

    private void scheduleBalanceRequest(BotSession sender, String recipient, long delayMs, int attempt) {
        Thread.ofVirtual().start(() -> {
            try {
                if (delayMs > 0L) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                if (!isCurrentPlayHandler(sender)) {
                    if (attempt < PAY_BALANCE_RETRY_LIMIT
                            && sender.getState() != BotSession.State.DISCONNECTED) {
                        BotDebug.warn("PAY_BALANCE_RETRY", sender,
                                "attempt=" + (attempt + 1) + ", reason=play_handler_not_current");
                        scheduleBalanceRequest(sender, recipient, 1_000L, attempt + 1);
                    } else {
                        BotDebug.warn("PAY_BALANCE_SKIPPED", sender,
                                "reason=play_handler_not_current, attempts=" + (attempt + 1));
                    }
                    return;
                }

                runAfterAntiAfk(sender, 0L, () -> {
                    pendingPayAll.put(sender, new PendingPayAll(recipient, System.currentTimeMillis()));
                    boolean[] sent = {false};
                    sender.runWithContext(() -> {
                        ClientPlayNetworkHandler handler = sender.getNetworkHandler();
                        if (handler != null && sender.getConnection() != null
                                && sender.getConnection().isOpen()
                                && sender.getConnection().getPacketListener() == handler) {
                            handler.sendChatCommand("money");
                            sent[0] = true;
                        }
                    });
                    if (sent[0]) {
                        BotDebug.info("PAY_BALANCE_REQUEST", sender, "recipient=" + recipient);
                    } else {
                        pendingPayAll.remove(sender);
                        if (attempt < PAY_BALANCE_RETRY_LIMIT) {
                            BotDebug.warn("PAY_BALANCE_RETRY", sender,
                                    "attempt=" + (attempt + 1) + ", reason=handler_changed_during_send");
                            scheduleBalanceRequest(sender, recipient, 1_000L, attempt + 1);
                        }
                    }
                });
            });
        });
    }

    private boolean isCurrentPlayHandler(BotSession session) {
        if (session == null || session.isMain() || !session.isReadyToControl()
                || !session.isInPlayProtocol() || session.isWorldStabilizing()) {
            return false;
        }
        ClientPlayNetworkHandler handler = session.getNetworkHandler();
        return handler != null && session.getConnection() != null
                && session.getConnection().isOpen()
                && session.getConnection().getPacketListener() == handler;
    }

    private void runAfterAntiAfk(BotSession session, long extraDelayMs, Runnable action) {
        if (session == null || action == null) {
            return;
        }
        long turnDelay = session.prepareAntiAfkAction();
        long delay = Math.max(0L, turnDelay) + Math.max(0L, extraDelayMs);
        if (delay == 0L) {
            action.run();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            MinecraftClient.getInstance().execute(action);
        });
    }

    private record PendingPayAll(String recipient, long requestedAt) {
    }

    private void gotoMe(MinecraftClient client, String requestedName) {
        BotSession destination = activeSession;
        if (destination == null || destination.getPlayer() == null
                || !destination.isReadyToControl() || !destination.isInPlayProtocol()) {
            feedback(client, "No active player is ready for goto me.", Formatting.RED);
            return;
        }
        String requested = requestedName == null ? "" : requestedName.trim();
        BotSession selected = requested.isBlank() ? null : findByName(requested);
        if (!requested.isBlank() && (selected == null || selected.isMain())) {
            feedback(client, "Bot not found: " + requested, Formatting.RED);
            return;
        }
        if (selected == destination) {
            feedback(client, "The active bot is already the destination.", Formatting.RED);
            return;
        }

        double x = destination.getPlayer().getX();
        double y = destination.getPlayer().getY();
        double z = destination.getPlayer().getZ();
        int started = 0;
        for (BotSession session : sessions) {
            if (session.isMain() || session == destination || (selected != null && session != selected)
                    || !session.isReadyToControl() || !session.isInPlayProtocol()) {
                continue;
            }
            // Baritone's primary instance belongs to the singleton visible
            // Minecraft client. Executing it for a detached session can move
            // the main account instead of the bot, so detached bots use their
            // session-local vanilla input/path controller only.
            session.startGoto(x, y, z);
            started++;
            BotDebug.info("GOTO_ME_REQUEST", session,
                    "destination=" + destination.getName() + ", selected=" + (selected != null));
        }
        feedback(client, selected == null
                        ? "Goto me started for bots: " + started + "."
                        : "Goto me started for " + selected.getName() + ".",
                started > 0 ? Formatting.GREEN : Formatting.RED);
    }

    private void lineupBots(MinecraftClient client) {
        BotSession destination = activeSession;
        if (destination == null || destination.getPlayer() == null
                || !destination.isReadyToControl() || !destination.isInPlayProtocol()) {
            feedback(client, "No active player is ready for lineup.", Formatting.RED);
            return;
        }
        List<BotSession> movers = new ArrayList<>();
        for (BotSession session : sessions) {
            if (!session.isMain() && session != destination
                    && session.isReadyToControl() && session.isInPlayProtocol()) {
                movers.add(session);
            }
        }
        if (movers.isEmpty()) {
            feedback(client, "No ready bots for lineup.", Formatting.RED);
            return;
        }

        double yaw = Math.toRadians(destination.getPlayer().getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double center = (movers.size() - 1) / 2.0D;
        double baseX = destination.getPlayer().getX();
        double baseY = destination.getPlayer().getY();
        double baseZ = destination.getPlayer().getZ();
        for (int index = 0; index < movers.size(); index++) {
            double offset = index - center;
            double x = baseX + forwardX * 2.0D + rightX * offset;
            double z = baseZ + forwardZ * 2.0D + rightZ * offset;
            movers.get(index).startGoto(x, baseY, z);
        }
        feedback(client, "Lineup started: " + movers.size() + " bots, 2 blocks ahead and 1 block spacing.",
                Formatting.GREEN);
    }

    private void setResellForBots(MinecraftClient client, String requestedState) {
        boolean enabledState = requestedState == null || requestedState.isBlank()
                || requestedState.equalsIgnoreCase("on")
                || requestedState.equalsIgnoreCase("вкл");
        if (!enabledState && !(requestedState.equalsIgnoreCase("off")
                || requestedState.equalsIgnoreCase("выкл"))) {
            feedback(client, "Использование: .bot resell on|off", Formatting.RED);
            return;
        }
        int enabled = 0;
        for (BotSession session : sessions) {
            if (session.isMain() || !session.isReadyToControl() || !session.isInPlayProtocol()) {
                continue;
            }
            session.runWithContext(() -> {
                session.getAutoResellAFK().setEnabledSilently(enabledState);
                if (enabledState) {
                    session.getAutoBuy().setEnabledSilently(false);
                    session.getAutoResellAFK().restartNow();
                }
            });
            enabled++;
        }
        feedback(client, "AutoResellAFK " + (enabledState ? "enabled" : "disabled") + " for bots: " + enabled + ".",
                enabled > 0 ? Formatting.GREEN : Formatting.RED);
    }

    private void teleportBots(MinecraftClient client, String requestedName) {
        String requested = requestedName == null ? "" : requestedName.trim();
        BotSession destinationSession = requested.isBlank() ? activeSession : findByName(requested);
        if (destinationSession == null || destinationSession.getPlayer() == null
                || !destinationSession.isReadyToControl() || !destinationSession.isInPlayProtocol()) {
            feedback(client, "Целевая сессия ещё не готова для Baritone goto.", Formatting.RED);
            return;
        }
        double x = destinationSession.getPlayer().getX();
        double y = destinationSession.getPlayer().getY();
        double z = destinationSession.getPlayer().getZ();
        int executed = 0;
        for (BotSession session : sessions) {
            if (session == destinationSession || !session.isReadyToControl() || !session.isInPlayProtocol()) {
                continue;
            }
            session.startGoto(x, y, z);
            executed++;
            BotDebug.info("BARITONE_GOTO_ME", session,
                    "target=" + destinationSession.getName() + ", x=" + x + ", y=" + y + ", z=" + z
                            + ", controller=session_local");
        }
        feedback(client, "Baritone goto me принят ботами: " + executed + ". Координаты "
                        + String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z) + ".",
                executed > 0 ? Formatting.GREEN : Formatting.RED);
    }

    @SuppressWarnings("unused")
    private void legacyTeleportBots(MinecraftClient client, String requestedName) {
        String targetName = requestedName == null ? "" : requestedName.trim();
        if (targetName.isBlank()) {
            BotSession target = activeSession;
            if (target == null || !target.isReadyToControl() || !target.isInPlayProtocol()) {
                feedback(client, "Нет активного подключённого аккаунта.", Formatting.RED);
                return;
            }
            targetName = target.getName();
        } else {
            BotSession sessionTarget = findByName(targetName);
            if (sessionTarget != null) {
                targetName = sessionTarget.getName();
            }
            if (!targetName.matches("[A-Za-z0-9_]{3,16}")) {
                feedback(client, "Ник должен содержать 3-16 букв, цифр или символов подчёркивания.", Formatting.RED);
                return;
            }
        }

        String destination = targetName;
        List<BotSession> targets = new ArrayList<>();
        for (BotSession session : sessions) {
            if (!session.getName().equalsIgnoreCase(destination)
                    && session.isReadyToControl() && session.isInPlayProtocol()
                    && session.getState() != BotSession.State.DISCONNECTED) {
                targets.add(session);
            }
        }
        int sent = sendTeleportRequests(targets, destination, 0);
        feedback(client, "Запрос телепортации к " + destination + " отправили боты: " + sent + ".",
                sent > 0 ? Formatting.GREEN : Formatting.RED);
    }

    private int sendTeleportRequests(List<BotSession> targets, String destination, int attempt) {
        int sent = 0;
        for (BotSession session : targets) {
            if (session.getState() == BotSession.State.DISCONNECTED || !session.isInPlayProtocol()) {
                continue;
            }
            boolean[] sentFromSession = {false};
            session.runWithContext(() -> {
                ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
                if (handler != null) {
                    handler.sendChatCommand("tpa " + destination);
                    sentFromSession[0] = true;
                }
            });
            if (sentFromSession[0]) {
                sent++;
            }
        }
        if (attempt < 2 && !targets.isEmpty()) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1_500L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                MinecraftClient.getInstance().execute(() -> sendTeleportRequests(targets, destination, attempt + 1));
            });
        }
        return sent;
    }

    private void setPlayMode(MinecraftClient client, String argument) {
        String state = argument == null ? "" : argument.trim().toLowerCase(Locale.ROOT);
        if (state.isBlank()) {
            playMode = !playMode;
        } else if (state.equals("on") || state.equals("вкл")) {
            playMode = true;
        } else if (state.equals("off") || state.equals("выкл")) {
            playMode = false;
        } else {
            feedback(client, "Использование: .bot play [on|off]", Formatting.RED);
            return;
        }
        feedback(client, playMode
                ? "Повтор движений main включён. Управляй основным аккаунтом."
                : "Повтор движений main выключен.", Formatting.GREEN);
    }

    private void ensureMainSession(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (client.player == null || handler == null || serverInfo == null) {
            return;
        }
        if (mainSession != null) {
            ClientPlayNetworkHandler mainHandler = mainSession.getNetworkHandler();
            if (mainHandler != null) {
                listenerSessions.put(mainHandler, mainSession);
            }
            // While a bot is controlled, client.getNetworkHandler() belongs
            // to that bot. Never remap it to main: doing so merges chat and
            // routes world-transition packets into the wrong session.
            if (activeSession != mainSession) {
                return;
            }
            if (mainSession.getConnection() == handler.getConnection()) {
                listenerSessions.put(handler, mainSession);
                return;
            }

            ClientConnection previousMainConnection = mainSession.getConnection();
            if (previousMainConnection != null && previousMainConnection.isOpen()) {
                // A background packet temporarily exposed another player's handler. The main
                // connection is still alive, so this is not a real server reconnect.
                mainSession.installAsActive();
                return;
            }
            replaceMainSession(client, handler, serverInfo);
            return;
        }
        BotSession session = BotSession.main(client.getSession().getUsername(), serverInfo.address);
        session.setConnection(handler.getConnection());
        session.setNetworkHandler(handler);
        session.captureFromClient();
        session.initializeModuleStates(FluxVisualsClient.MODULE_MANAGER);
        mainSession = session;
        activeSession = session;
        sessions.add(0, session);
        listenerSessions.put(handler, session);
    }

    private void replaceMainSession(
            MinecraftClient client,
            ClientPlayNetworkHandler handler,
            ServerInfo serverInfo
    ) {
        BotSession previous = mainSession;
        if (previous != null) {
            previous.captureModuleStates(FluxVisualsClient.MODULE_MANAGER);
            sessionPacketQueues.remove(previous);
            sessions.remove(previous);
            listenerSessions.entrySet().removeIf(entry -> entry.getValue() == previous);
        }
        BotSession replacement = BotSession.main(client.getSession().getUsername(), serverInfo.address);
        replacement.setConnection(handler.getConnection());
        replacement.setNetworkHandler(handler);
        replacement.captureFromClient();
        replacement.initializeModuleStates(FluxVisualsClient.MODULE_MANAGER);
        sessions.add(0, replacement);
        mainSession = replacement;
        activeSession = replacement;
        listenerSessions.put(handler, replacement);
        pendingAutoSwitchSession = null;
        telegramTargetName = "main";
    }

    private void tickConnection(
            BotSession session,
            boolean mirrorMainMovement,
            ClientPlayerEntity mainPlayer,
            PlayerInput mirroredInput,
            net.minecraft.util.hit.HitResult mainTarget,
            boolean attackHeld,
            boolean advancePlayer
    ) {
        ClientConnection connection = session.getConnection();
        if (connection == null) {
            return;
        }
        if (connection.isOpen()) {
            // The hidden primary account must not run a second detached
            // ClientPlayerEntity tick while a bot is visible. That tick emits
            // movement/rotation packets from the render thread and was the
            // main reason the primary account was kicked during switching.
            // Its connection and explicit ClientTickEnd packet are serviced
            // below, while normal vanilla ticks resume as soon as it is
            // visible again.
            if (advancePlayer && session.shouldTickBackgroundWorld(System.nanoTime())) {
                if (session.isMain()) {
                    session.tickBackgroundLookOnly();
                } else {
                    session.tickBackgroundWorld(
                            mirrorMainMovement ? mainPlayer : null,
                            mirrorMainMovement ? mirroredInput : PlayerInput.DEFAULT,
                            mainTarget,
                            attackHeld
                    );
                }
            }
            // PLAY sockets have already received their normal connection
            // tick above. Do not tick a configuration/login handler as PLAY;
            // its protocol transition is driven by the handler itself.
            // A retained world/player snapshot is intentionally kept while a
            // server performs PLAY -> CONFIGURATION -> PLAY. Do not report
            // that snapshot as a live connection until its new play handler
            // has actually been installed by GameJoin.
            if (session.isReadyToControl() && session.isInPlayProtocol()) {
                session.setState(BotSession.State.PLAYING);
            }
        } else {
            // ClientConnection.connect() completes asynchronously. During
            // this short window isOpen() is still false even though the
            // socket is being established. Do not race the connector and
            // mark a healthy first attempt as disconnected.
            if (session.getState() == BotSession.State.CONNECTING) {
                BotDebug.trace("CONNECTING_SOCKET_PENDING", session,
                        "connection_open=" + connection.isOpen());
                return;
            }
            if (session.getState() == BotSession.State.DISCONNECTED) {
                return;
            }
            session.setState(BotSession.State.DISCONNECTED);
            if (session.markDisconnectionHandled()) {
                String reason = connection.getDisconnectionInfo() == null
                        || connection.getDisconnectionInfo().reason() == null
                        ? "connection closed before normal disconnect"
                        : connection.getDisconnectionInfo().reason().getString();
                session.setStatusText(reason);
                BotDebug.warn("CONNECTION_CLOSED", session, "reason=" + reason);
                if (session.prepareReconnect(System.currentTimeMillis())) {
                    BotDebug.info("RECONNECT_SCHEDULED", session, "attempt=" + session.getStatusText());
                    Thread.ofVirtual().start(() -> {
                        try {
                            Thread.sleep(Math.max(1L, session.getReconnectDelayMillis()));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        MinecraftClient.getInstance().execute(() -> startConnection(MinecraftClient.getInstance(), session));
                    });
                }
            }
            if (session.getStatusText().isBlank()) {
                session.setStatusText("Отключён");
            }
        }
    }

    /**
     * Services every non-vanilla-managed connection once per client tick.
     * The visible main connection is already ticked by MinecraftClient; all
     * bot connections and the hidden main connection are owned by this layer.
     */
    private void tickManagedConnections() {
        for (BotSession session : sessions) {
            if (session.getState() == BotSession.State.DISCONNECTED) {
                continue;
            }
            ClientConnection connection = session.getConnection();
            if (connection == null || !connection.isOpen()) {
                continue;
            }
            // The vanilla client owns the visible primary connection during a
            // normal tick and during its ReconfiguringScreen. Calling its old
            // PLAY handler while vanilla has cleared client.player causes the
            // exact NPE seen in the crash log.
            if (session.isMain() && session == activeSession) {
                // Vanilla owns this socket while visible. Refresh the
                // diagnostic baseline so returning to background does not
                // report the whole visible interval as a network gap.
                NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
                health.lastConnectionTickNanos = System.nanoTime();
                continue;
            }
            if (isConnectionTickedByReconfigurationScreen(connection)) {
                NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
                health.lastConnectionTickNanos = System.nanoTime();
                continue;
            }
            long tickStarted = System.nanoTime();
            NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
            long previousTick = health.lastConnectionTickNanos;
            if (previousTick != 0L && tickStarted - previousTick > CONNECTION_TICK_GAP_WARN_NANOS) {
                BotDebug.warn("CONNECTION_TICK_GAP", session,
                        "gap_ms=" + TimeUnit.NANOSECONDS.toMillis(tickStarted - previousTick)
                                + ", " + networkHealthDetails(session, tickStarted));
            }
            health.lastConnectionTickNanos = tickStarted;
            health.connectionTickCount++;
            try {
                if (session == activeSession || CONTEXT.get() == session) {
                    tickConnectionAsManager(connection);
                } else {
                    session.runWithContext(() -> tickConnectionAsManager(connection));
                }
            } catch (Throwable error) {
                BotDebug.error("CONNECTION_TICK_FAILED", session,
                        "listener=" + (connection.getPacketListener() == null
                                ? "null" : connection.getPacketListener().getClass().getSimpleName()), error);
            }
            long finished = System.nanoTime();
            long duration = finished - tickStarted;
            if (duration > CONNECTION_TICK_DURATION_WARN_NANOS) {
                BotDebug.warn("CONNECTION_TICK_SLOW", session,
                        "duration_ms=" + TimeUnit.NANOSECONDS.toMillis(duration)
                                + ", " + networkHealthDetails(session, finished));
            }
            if (finished - health.lastHealthLogNanos >= NETWORK_HEALTH_LOG_INTERVAL_NANOS) {
                health.lastHealthLogNanos = finished;
                BotDebug.info("NETWORK_HEALTH", session, networkHealthDetails(session, finished));
            }
        }
    }

    private void sendManagedTickEnd(BotSession session) {
        if (session == null || session == activeSession || !session.isInPlayProtocol()) {
            return;
        }
        ClientConnection connection = session.getConnection();
        if (connection == null || !connection.isOpen()) {
            return;
        }
        ClientPlayNetworkHandler handler = session.getNetworkHandler();
        if (handler == null || connection.getPacketListener() != handler) {
            // Stale play handler (e.g. mid-reconfiguration or after a kick):
            // pushing a PLAY tick-end through it corrupts the new protocol
            // codec and gets the account kicked with a network protocol error.
            return;
        }
        handler.sendPacket(ClientTickEndC2SPacket.INSTANCE);
        NetworkHealth health = networkHealth.computeIfAbsent(session, ignored -> new NetworkHealth());
        health.managedTickEndCount++;
    }

    private String networkHealthDetails(BotSession session, long nowNanos) {
        NetworkHealth health = networkHealth.get(session);
        ConcurrentLinkedDeque<BackgroundPacket> queue = sessionPacketQueues.get(session);
        int queueSize = queue == null ? 0 : queue.size();
        BackgroundPacket oldest = queue == null ? null : queue.peekFirst();
        long oldestQueueMs = oldest == null ? 0L
                : TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nowNanos - oldest.enqueuedAtNanos()));
        if (health == null) {
            return "ticks=0, tick_end=0, queue=" + queueSize + ", oldest_queue_ms=" + oldestQueueMs;
        }
        return "ticks=" + health.connectionTickCount
                + ", tick_end=" + health.managedTickEndCount
                + ", queue=" + queueSize
                + ", oldest_queue_ms=" + oldestQueueMs
                + ", latency_in=" + latencyAge(health.lastInboundLatencyKind,
                health.lastInboundLatencyNanos, nowNanos)
                + ", latency_out=" + latencyAge(health.lastOutboundLatencyKind,
                health.lastOutboundLatencyNanos, nowNanos);
    }

    private static String latencyAge(String kind, long timestampNanos, long nowNanos) {
        if (timestampNanos == 0L) {
            return "none";
        }
        return kind + ":" + TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nowNanos - timestampNanos)) + "ms_ago";
    }

    /**
     * Completes the hidden primary account's FIFO before vanilla is allowed
     * to apply newly arriving packets to the now-visible connection.
     */
    private void drainQueuedPacketsForSession(BotSession session) {
        ConcurrentLinkedDeque<BackgroundPacket> queue = sessionPacketQueues.get(session);
        if (queue == null) {
            return;
        }
        BackgroundPacket queued;
        int drained = 0;
        while ((queued = queue.pollFirst()) != null) {
            applyBackgroundPacket(queued.session(), queued.packet(), queued.listener());
            drained++;
        }
        sessionPacketQueues.remove(session, queue);
        if (drained > 0) {
            BotDebug.info("SESSION_FIFO_DRAINED", session, "count=" + drained + ", handoff=vanilla");
        }
    }

    private static PlayerInput readPlayInput(MinecraftClient client) {
        return new PlayerInput(
                client.options.forwardKey.isPressed(),
                client.options.backKey.isPressed(),
                client.options.leftKey.isPressed(),
                client.options.rightKey.isPressed(),
                client.options.jumpKey.isPressed(),
                client.options.sneakKey.isPressed(),
                client.options.sprintKey.isPressed()
        );
    }

    public static void restoreActiveRenderer(MinecraftClient client, BotSession active) {
        if (active == null || !active.isReadyToControl()) {
            return;
        }
        if (!active.isMain() && active.getWorld() != null && client.world != active.getWorld()) {
            client.world = active.getWorld();
            client.player = active.getPlayer();
        }
        if (client.world != active.getWorld()) {
            return;
        }
        WorldRendererAccessor renderer = (WorldRendererAccessor) client.worldRenderer;
        if (renderer.fluxvisuals$getWorld() != active.getWorld()) {
            client.worldRenderer.setWorld(active.getWorld());
            client.particleManager.setWorld(active.getWorld());
            client.gameRenderer.setWorld(active.getWorld());
            client.getBlockEntityRenderDispatcher().setWorld(active.getWorld());
        }
        // EntityRenderDispatcher keeps its own world reference. Without
        // rebinding it after a bot switch, terrain renders from the active
        // world while entity iteration still uses the previous session.
        client.getEntityRenderDispatcher().setWorld(active.getWorld());
    }

    private static boolean isVisibleStateFor(MinecraftClient client, BotSession session) {
        return client != null && session != null
                && client.world == session.getWorld()
                && client.player == session.getPlayer()
                && client.interactionManager == session.getInteractionManager()
                && client.world != null && client.player != null
                && client.getNetworkHandler() != null;
    }

    private void tickMainAutomation(MinecraftClient client) {
        AutoResellAFK autoResell = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        ItemCrafter itemCrafter = FluxVisualsClient.MODULE_MANAGER.getItemCrafter();
        autoResell.onTick(client);
        autoBuy.onTick(client);
        if (itemCrafter != null) {
            itemCrafter.onTick(client);
        }
        try {
            FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver().tickSession(client, mainSession);
        } catch (Throwable ignored) {
        }
        scanAutoBuyScreen(client, autoBuy, mainSession);
    }

    private void tickBotAutomation(MinecraftClient client, BotSession session) {
        AutoBuy globalAutoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        AutoResellAFK globalAutoResell = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        ItemCrafter globalItemCrafter = FluxVisualsClient.MODULE_MANAGER.getItemCrafter();
        AutoBuy autoBuy = session.getAutoBuy();
        AutoResellAFK autoResell = session.getAutoResellAFK();
        ItemCrafter itemCrafter = session.getItemCrafter();

        if (autoBuy != null && globalAutoBuy != null) {
            autoBuy.copySettingsFrom(globalAutoBuy);
        }
        if (autoResell != null && globalAutoResell != null) {
            autoResell.copySettingsFrom(globalAutoResell);
        }
        if (itemCrafter != null && globalItemCrafter != null) {
            itemCrafter.copySettingsFrom(globalItemCrafter);
        }
        session.initializeAutomation(globalAutoBuy, globalAutoResell, globalItemCrafter);
        // FunTime sends GameJoin before it finishes its lobby transfer. Do not
        // issue /an, /ah or resale commands during that short transition: the
        // proxy interprets them as an immediate anarchy join and may kick the
        // account before the world is fully ready.
        if (session.isWorldStabilizing()) {
            return;
        }
        if (session.tickInventoryDrop(client)) {
            return;
        }
        if (autoResell != null) {
            autoResell.onTick(client);
        }
        if (autoBuy != null) {
            autoBuy.onTick(client);
        }
        if (itemCrafter != null) {
            itemCrafter.onTick(client);
        }
        try {
            FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver().tickSession(client, session);
        } catch (Throwable ignored) {
        }
        // The active bot also needs the screen scanner; its GUI is the one
        // currently installed in MinecraftClient, while background bots get
        // their own scanner through runWithContext above.
        if (autoBuy != null) {
            scanAutoBuyScreen(client, autoBuy, session);
        }
    }

    private void scanAutoBuyScreen(MinecraftClient client, AutoBuy autoBuy, BotSession session) {
        if (!autoBuy.isEnabled() || client.player == null || client.world == null
                || !(client.currentScreen instanceof HandledScreen<?>)
                || session == null || !session.shouldScanAutomation(System.currentTimeMillis())) {
            return;
        }
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(client.world);
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            autoBuy.considerPurchaseConfirmationSlot(slot);
            if (!autoBuy.isScanningAuction()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, TooltipType.BASIC);
            List<String> lines = tooltip.stream().map(Text::getString).filter(line -> !line.isBlank()).toList();
            autoBuy.considerAuctionSlot(slot, lines, tooltip);
        }
        autoBuy.onHandledScreenRendered(client);
    }

    public BotSession findSession(PacketListener listener) {
        if (listener == null) {
            return null;
        }
        BotSession known = listenerSessions.get(listener);
        if (known != null) {
            ClientConnection knownConnection = known.getConnection();
            if (knownConnection != null && knownConnection.getPacketListener() == listener) {
                return known;
            }
            listenerSessions.remove(listener, known);
        }
        for (BotSession session : sessions) {
            ClientConnection connection = session.getConnection();
            if (connection != null && connection.getPacketListener() == listener) {
                listenerSessions.put(listener, session);
                return session;
            }
        }
        // Fallback for stale listener races (bot login, server transfers):
        // resolve the session through the handler's OWN connection instead of
        // the session's cached one. Without this a packet from a freshly
        // installed bot handler falls through to vanilla processing with the
        // visible account's globals — bot chat and the "press shift to
        // dismount" hint end up on the main screen.
        // NOTE: only ClientPlayNetworkHandler exposes getConnection(); login
        // and configuration handlers never touch HUD state, so they do not
        // need this fallback.
        if (listener instanceof ClientPlayNetworkHandler playHandler) {
            try {
                ClientConnection ownConnection = playHandler.getConnection();
                if (ownConnection != null) {
                    BotSession byConnection = findSession(ownConnection);
                    if (byConnection != null) {
                        listenerSessions.put(listener, byConnection);
                        return byConnection;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public BotSession findSession(ClientConnection connection) {
        if (connection == null) {
            return null;
        }
        BotSession known = connectionSessions.get(connection);
        if (known != null) {
            return known;
        }
        for (BotSession session : sessions) {
            if (session.getConnection() == connection) {
                connectionSessions.putIfAbsent(connection, session);
                return session;
            }
        }
        return null;
    }

    public List<BotSession> getSessions() {
        return List.copyOf(sessions);
    }

    public BotSession getMainSession() {
        return mainSession;
    }

    public BotSession findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.trim().equalsIgnoreCase("main") || name.trim().equalsIgnoreCase("основа")) {
            return mainSession;
        }
        for (BotSession session : sessions) {
            if (session.getName().equalsIgnoreCase(name.trim())) {
                return session;
            }
        }
        return null;
    }

    public void reconnectBotSession(BotSession session) {
        if (session == null || session.isMain()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            disconnect(session, "Реконнект");
            sessionPacketQueues.remove(session);
            startConnection(client, session);
        });
    }

    public void disconnectBotSession(BotSession session) {
        if (session == null || session.isMain()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> removeBot(client, session.getName()));
    }

    public void switchActiveTo(BotSession session) {
        if (session == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> switchTo(session));
    }

    public void sendBotChat(BotSession session, String message) {
        if (session == null || message == null || message.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            String command = message.trim();
            if (session.isMain()) {
                if (client.getNetworkHandler() != null) {
                    if (command.startsWith("/")) {
                        client.getNetworkHandler().sendChatCommand(command.substring(1));
                    } else {
                        client.getNetworkHandler().sendChatMessage(command);
                    }
                }
            } else {
                session.runWithContext(() -> {
                    ClientPlayNetworkHandler handler = session.getNetworkHandler();
                    if (handler != null) {
                        if (command.startsWith("/")) {
                            handler.sendChatCommand(command.substring(1));
                        } else {
                            handler.sendChatMessage(command);
                        }
                    }
                });
            }
        });
    }

    private final ConcurrentMap<BotSession, Long> lastBalanceQueryAt = new ConcurrentHashMap<>();

    public long resolveSessionBalance(BotSession session) {
        if (session == null) {
            return -1L;
        }
        if (session.getWorld() != null) {
            Long scBal = AutoBuy.readScoreboardBalance(session.getWorld());
            if (scBal != null && scBal > 0L) {
                session.setKnownBalance(scBal);
                return scBal;
            }
        }
        long known = session.getKnownBalance();
        if (known >= 0L) {
            return known;
        }
        if (session.isMain()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.world != null) {
                Long mcBal = AutoBuy.readScoreboardBalance(mc.world);
                if (mcBal != null && mcBal > 0L) {
                    session.setKnownBalance(mcBal);
                    return mcBal;
                }
            }
            AutoBuy ab = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            if (ab != null && ab.getLastKnownBalance() >= 0L) {
                return ab.getLastKnownBalance();
            }
        } else if (session.getState() == BotSession.State.PLAYING) {
            requestBotBalance(session);
        }
        return -1L;
    }

    public void requestBotBalance(BotSession session) {
        if (session == null || session.getState() != BotSession.State.PLAYING) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastQuery = lastBalanceQueryAt.get(session);
        if (lastQuery != null && now - lastQuery < 5_000L) {
            return;
        }
        lastBalanceQueryAt.put(session, now);
        sendBotChat(session, "/money");
    }

    public List<String> getBotPresetNames() {
        return List.copyOf(botPresets.keySet());
    }

    public boolean loadPreset(String name) {
        List<String> names = botPresets.get(name);
        if (names == null || names.isEmpty()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        client.execute(() -> {
            for (String botName : names) {
                addBot(client, botName);
            }
        });
        return true;
    }

    private BotSession findReadyFallback(BotSession excluded) {
        if (mainSession != null && mainSession != excluded && mainSession.isReadyToControl()
                && mainSession.getState() != BotSession.State.DISCONNECTED) {
            return mainSession;
        }
        return null;
    }

    private static boolean isBackgroundVisualPacket(Packet<?> packet) {
        return packet instanceof ParticleS2CPacket
                || packet instanceof ItemPickupAnimationS2CPacket
                || packet instanceof BlockBreakingProgressS2CPacket
                || packet instanceof WorldEventS2CPacket
                || packet instanceof EntityAnimationS2CPacket
                || packet instanceof EntityStatusS2CPacket
                || packet instanceof PlaySoundS2CPacket
                || packet instanceof PlaySoundFromEntityS2CPacket
                || packet instanceof StopSoundS2CPacket
                || packet instanceof AdvancementUpdateS2CPacket;
    }

    private static boolean isDiscardableHiddenPacket(Packet<?> packet) {
        if (isBackgroundVisualPacket(packet)) {
            return true;
        }
        // Entity spawn/movement, player-list and scoreboard packets are state,
        // not disposable effects. Dropping them while a bot is hidden leaves
        // its ClientWorld without players/entities and with an incomplete
        // sidebar when the user switches to it later.
        return false;
    }

    private static boolean isBackgroundChatPacket(Packet<?> packet) {
        return packet instanceof ChatMessageS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof ProfilelessChatMessageS2CPacket;
    }

    private static boolean isInventoryPacket(Packet<?> packet) {
        return packet instanceof CloseScreenS2CPacket
                || packet instanceof InventoryS2CPacket
                || packet instanceof OpenScreenS2CPacket
                || packet instanceof ScreenHandlerPropertyUpdateS2CPacket
                || packet instanceof ScreenHandlerSlotUpdateS2CPacket
                || packet instanceof SetPlayerInventoryS2CPacket
                || packet instanceof SetTradeOffersS2CPacket;
    }

    private record BackgroundPacket(
            BotSession session,
            Packet<?> packet,
            PacketListener listener,
            long enqueuedAtNanos
    ) {
    }

    private static final class NetworkHealth {
        private volatile long lastConnectionTickNanos;
        private volatile long lastHealthLogNanos;
        private volatile long lastInboundLatencyNanos;
        private volatile long lastOutboundLatencyNanos;
        private volatile String lastInboundLatencyKind = "none";
        private volatile String lastOutboundLatencyKind = "none";
        private volatile long connectionTickCount;
        private volatile long managedTickEndCount;
    }

    private void dispatchBackgroundChat(BotSession session, Packet<?> packet, PacketListener listener) {
        Text formattedMessage = null;
        if (packet instanceof ChatMessageS2CPacket chat) {
            formattedMessage = chat.unsignedContent();
            if (formattedMessage == null) {
                formattedMessage = chat.serializedParameters()
                        .applyChatDecoration(Text.literal(chat.body().content()));
            }
        } else if (packet instanceof GameMessageS2CPacket game) {
            formattedMessage = game.content();
        } else if (packet instanceof ProfilelessChatMessageS2CPacket profileless) {
            formattedMessage = profileless.message();
        }
        if (formattedMessage != null) {
            handleChatMessage(listener, formattedMessage);
        }
    }

    private static void disconnect(BotSession session, String reason) {
        BotDebug.info("MANUAL_DISCONNECT", session, "reason=" + reason);
        session.invalidateConnectionGeneration();
        ClientConnection connection = session.getConnection();
        if (connection != null && connection.isOpen()) {
            connection.disconnect(Text.literal(reason));
        }
        session.setState(BotSession.State.DISCONNECTED);
        session.setStatusText(reason);
    }

    private void bindBotConnection(BotSession session, ClientConnection connection) {
        connectionSessions.entrySet().removeIf(entry -> entry.getValue() == session);
        networkHealth.remove(session);
        session.setConnection(connection);
        if (!session.isMain() && connection != null) {
            connectionSessions.put(connection, session);
            BotDebug.trace("BOT_CONNECTION_BOUND", session,
                    "connection=" + Integer.toHexString(System.identityHashCode(connection)));
        }
    }

    private static void feedback(MinecraftClient client, String message, Formatting color) {
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(
                    Text.literal("[Bot] ").formatted(Formatting.AQUA)
                            .append(Text.literal(message).formatted(color))
            );
        }
    }

    private static void showHelp(MinecraftClient client) {
        feedback(client, ".bot chat <ник|all> [on|off] — трансляция сообщений из чата бота в ваш чат", Formatting.GRAY);
        feedback(client, ".bot goto me [bot] | .bot lineup | .bot resell on|off", Formatting.GRAY);
        feedback(client, ".bot antiafk on|off|вкл|выкл — поддержание соединения без искусственных movement/rotation-пакетов", Formatting.GRAY);
        feedback(client, ".bot baritone <команда> | #<команда> (help, goto, goal, mine, tunnel, follow, stop)", Formatting.GRAY);
        feedback(client, ".bot <ник> function <название_функции> <on|off>", Formatting.GRAY);
        feedback(client, ".bot <ник> function captchasolver <on|off> — автоответ капчи без переключения управления", Formatting.GRAY);
        feedback(client, ".bot drop <ник> | .bot <ник> drop", Formatting.GRAY);
        feedback(client, ".bot pay [name|all] <сумма|all>", Formatting.GRAY);
        feedback(client, ".bot proxy <user:pass@host:port|off> | .bot <ник> proxy <настройки|off>", Formatting.GRAY);
        feedback(client, ".bot preset save|remove|load <имя>", Formatting.GRAY);
        feedback(client, ".bot добавить <ник> | .bot add <ник>", Formatting.GRAY);
        feedback(client, ".bot удалить <ник> | .bot удалитьвсех", Formatting.GRAY);
        feedback(client, ".bot список | .bot переключить <ник> | .bot основной", Formatting.GRAY);
        feedback(client, ".bot команда <сообщение или /команда> | .bot тп", Formatting.GRAY);
    }
}
