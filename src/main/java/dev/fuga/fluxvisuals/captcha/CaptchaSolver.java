package dev.fuga.fluxvisuals.captcha;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.multibot.BotSession;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Solves FunTime-style "введите номер с картинки" captchas with the trained
 * YOLO model (captcha/best.pt) served by the local Flask server
 * (captcha server, POST /solve with a PNG file, returns {"result": "12345"}).
 *
 * <p>How it works: the server shows digits on maps inside item frames near
 * the player. The solver scans the session's own world for those frames,
 * renders their MapState pixels to a PNG in memory (no screenshots, no GL),
 * POSTs it to the model and chats the answer. Wall snapshots are also saved
 * to &lt;gamedir&gt;/fuga-captcha/&lt;name&gt;/ and deleted after the captcha
 * is passed, unless the Debug toggle is on. Background bots keep receiving
 * frame + map packets through their normal connection, so solving works
 * without making the bot visible. While the solver is enabled for a bot,
 * MultiBotManager does NOT auto-switch control to it on captcha.
 */
public final class CaptchaSolver extends Module {
    private static final int MAP_SIZE = 128;
    // FunTime billboards are big (6x4+ maps); cutting the wall to 12 maps
    // guaranteed a partial image every time. Small HolyWorld captchas fit
    // anyway, so generous caps only fix the big ones.
    private static final int MAX_MAPS_TOTAL = 64;
    private static final int MAX_MAPS_PER_WALL = 32;
    private static final double SCAN_RADIUS = 24.0D;
    // Tight chaining: neighbour maps inside one billboard are 1.0 apart
    // (1.42 diagonal). Two separate billboards must never chain together —
    // that stitched both captchas into one image for the model.
    private static final double WALL_CLUSTER_DIST = 1.6D;
    private static final int MAX_WALLS = 3;
    /**
     * Fragments (1-3 streamed maps of a billboard, strays) are never solved:
     * the model hallucinates digits from a scribble scrap. Wait for whole walls.
     */
    private static final int MIN_WALL_MAPS = 4;
    // Solve a multi-wall captcha only when the same wall is seen twice in a
    // row; map pixels stream in gradually and every early attempt sees a
    // different half-loaded wall. Fall back to best effort after N rounds.
    private static final int UNSTABLE_ROUNDS_FALLBACK = 4;
    /**
     * Crosshair cone: when the best wall center is within this angle of the
     * look vector, the player is deemed to look AT the captcha and angle
     * beats grid score. Beyond it (bots staring elsewhere) grid score wins.
     */
    private static final double CROSSHAIR_MAX_RAD = 0.5D;
    private static final double CROSSHAIR_STICKY_EPS_RAD = 0.03D;
    // Enough to skip empty/unstreamed maps but keep clean sparse captcha maps.
    private static final int MIN_MAP_CONTENT_PIXELS = 1200;

    private static final long FIRST_ATTEMPT_DELAY_MS = 350L;
    private static final long RETRY_DELAY_MS = 2_000L;
    /** Fast poll while a captcha is pending: collect -> stable -> answer in ~1-2s. */
    private static final long SOLVE_POLL_MS = 500L;
    private static final long SOLVE_DEADLINE_MS = 30_000L;
    private static final long SOLVED_COOLDOWN_MS = 1_200L;
    private static final long REARM_QUIET_MS = 15_000L;
    private static final long HTTP_TIMEOUT_SECONDS = 15L;

    private String serverUrl = "http://127.0.0.1:5000/solve";
    private String answerPrefix = "";
    /** "auto" (crosshair, then grid-regular wall), "nearest", "sparsest" or "crosshair" captcha wall selection. */
    private String wallMode = "auto";
    /**
     * Debug toggle (GUI button): when on, wall screenshots under
     * &lt;gamedir&gt;/fuga-captcha/ are kept after solving; otherwise the
     * folder is deleted once the captcha is passed.
     */
    private volatile boolean debugKeepShots;

    private final ConcurrentMap<Object, SolveState> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Long> solvedCooldown = new ConcurrentHashMap<>();
    /** Last physical billboard answered per key — a fast re-prompt means it was wrong. */
    private final ConcurrentMap<Object, WallIdentity> lastWallUsed = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Long> lastAnswerAt = new ConcurrentHashMap<>();
    /** Image signature already answered per key — resending it repeats a wrong answer. */
    private final ConcurrentMap<Object, String> lastSentSignature = new ConcurrentHashMap<>();
    private static final long REPROMPT_ROTATE_MS = 25_000L;
    /** Key for the unmanaged main connection (no bot sessions tracked). */
    private static final String MAIN_KEY = "__main__";
    /** Reused connection pool: a new client per answer wastes the handshake. */
    private static final HttpClient SHARED_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();

    public CaptchaSolver() {
        super("CaptchaSolver", "Решает капчу с картинки через YOLO-модель на ботах без переключения управления.", ModuleCategory.UTILS);
        // Enabled by default: otherwise captchas silently fall through and it
        // looks like "the solver does nothing at all".
        setEnabledSilently(true);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        if (serverUrl != null && !serverUrl.isBlank()) {
            this.serverUrl = serverUrl.trim();
        }
    }

    public String getAnswerPrefix() {
        return answerPrefix;
    }

    public void setAnswerPrefix(String answerPrefix) {
        this.answerPrefix = answerPrefix == null ? "" : answerPrefix;
    }

    public String getWallMode() {
        return wallMode;
    }

    public void setWallMode(String wallMode) {
        if (wallMode != null && !wallMode.isBlank()) {
            this.wallMode = wallMode.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public boolean isDebugKeepShots() {
        return debugKeepShots;
    }

    public void setDebugKeepShots(boolean debugKeepShots) {
        this.debugKeepShots = debugKeepShots;
    }

    public boolean isEnabledFor(BotSession session) {
        if (!isEnabled() || session == null) {
            return false;
        }
        return session.isMain() || session.isCaptchaSolverEnabled();
    }

    private boolean isEnabledForKey(Object key) {
        if (!isEnabled() || key == null) {
            return false;
        }
        if (MAIN_KEY.equals(key)) {
            return true;
        }
        return key instanceof BotSession session && isEnabledFor(session);
    }

    /** Drops any pending solve for the session (e.g. after a kick). */
    public void cancelFor(BotSession session) {
        if (session == null) {
            return;
        }
        pending.remove(session);
        solvedCooldown.remove(session);
        cleanupDebugDir(session);
    }

    /**
     * If the server re-prompts shortly after our answer, the previous wall
     * was wrong — avoid it this round so the solver rotates to the other one.
     */
    private WallIdentity rotateAvoidWall(Object key, long now) {
        Long answeredAt = lastAnswerAt.get(key);
        WallIdentity used = lastWallUsed.get(key);
        if (answeredAt != null && used != null && now - answeredAt < REPROMPT_ROTATE_MS) {
            return used;
        }
        return null;
    }

    /** Broad trigger matcher for FunTime captcha prompts (chat, titles, actionbar). */
    public static boolean isCaptchaPrompt(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("введите номер с картинки")
                || normalized.contains("номер с картинки")
                || normalized.contains("код с картинки")
                || normalized.contains("введите код")
                || normalized.contains("введите цифры")
                || normalized.contains("цифры с картинки")
                || normalized.contains("captcha")
                || normalized.contains("капч");
    }

    /**
     * Called when a session receives the captcha chat message.
     *
     * @return true when the solver takes ownership (caller must NOT auto-switch control).
     */
    public boolean onCaptchaMessage(BotSession session) {
        if (!isEnabledFor(session)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long cooled = solvedCooldown.get(session);
        if (cooled != null && now - cooled < SOLVED_COOLDOWN_MS) {
            return true;
        }
        SolveState existing = pending.get(session);
        if (existing != null && now - existing.armedAt < REARM_QUIET_MS) {
            return true;
        }
        SolveState state = new SolveState(now + SOLVE_DEADLINE_MS, now + FIRST_ATTEMPT_DELAY_MS, now);
        state.avoidWall = rotateAvoidWall(session, now);
        pending.put(session, state);
        BotDebug.info("CAPTCHA_SOLVER_ARMED", session, "deadline_ms=" + SOLVE_DEADLINE_MS);
        feedback(session.getName(), "§a[CaptchaSolver] §fКапча у бота " + session.getName() + ", решаю фоном…");
        return true;
    }

    /**
     * Same as {@link #onCaptchaMessage(BotSession)} but for the plain main
     * connection when no bot sessions are tracked (session == null).
     *
     * @return true when the solver takes ownership.
     */
    public boolean onCaptchaMain() {
        if (!isEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long cooled = solvedCooldown.get(MAIN_KEY);
        if (cooled != null && now - cooled < SOLVED_COOLDOWN_MS) {
            return true;
        }
        SolveState existing = pending.get(MAIN_KEY);
        if (existing != null && now - existing.armedAt < REARM_QUIET_MS) {
            return true;
        }
        SolveState state = new SolveState(now + SOLVE_DEADLINE_MS, now + FIRST_ATTEMPT_DELAY_MS, now);
        state.avoidWall = rotateAvoidWall(MAIN_KEY, now);
        pending.put(MAIN_KEY, state);
        BotDebug.info("CAPTCHA_SOLVER_ARMED", null, "target=main");
        feedback("main", "§a[CaptchaSolver] §fКапча на основе, решаю…");
        return true;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null) {
            return;
        }
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager == null) {
            return;
        }
        // The visible session is solved here; hidden bots are solved from
        // tickBotAutomation / tickMainAutomation. Calls are idempotent
        // (attempt cooldown), so overlap is harmless.
        BotSession active = manager.getActiveSession();
        if (active != null) {
            tickSession(client, active);
        } else {
            tickUnmanagedMain(client);
        }
    }

    /** Attempts one solve step for the given session. Safe to call every tick. */
    public void tickSession(MinecraftClient client, BotSession session) {
        if (client == null || session == null || !isEnabledFor(session)) {
            return;
        }
        if (session.getState() == BotSession.State.DISCONNECTED || !session.isInPlayProtocol()) {
            return;
        }
        ClientWorld world;
        PlayerEntity player;
        try {
            world = session.getWorld();
            player = session.getPlayer();
        } catch (Throwable error) {
            return;
        }
        tickKeyed(client, session, session, world, player);
    }

    /** Solve loop for the plain main connection (no tracked session). */
    public void tickUnmanagedMain(MinecraftClient client) {
        if (!isEnabled() || client == null || client.world == null
                || client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        tickKeyed(client, MAIN_KEY, null, client.world, client.player);
    }

    private void tickKeyed(MinecraftClient client, Object key, BotSession session,
                           ClientWorld world, PlayerEntity player) {
        if (!isEnabledForKey(key) || world == null || player == null) {
            return;
        }
        SolveState state = pending.get(key);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= state.deadlineAt) {
            pending.remove(key);
            cleanupDebugDir(session);
            BotDebug.warn("CAPTCHA_SOLVER_TIMEOUT", session, "attempts=" + state.attempts);
            if (session != null) {
                feedback(session.getName(), "§c[CaptchaSolver] §fНе решил капчу за 30с — переключись вручную.");
                MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
                if (manager != null) {
                    manager.requestManualCaptcha(session);
                }
            } else {
                feedback("main", "§c[CaptchaSolver] §fНе решил капчу за 30с — введи номер вручную.");
            }
            return;
        }
        if (state.inFlight || now < state.nextAttemptAt) {
            return;
        }
        byte[] png = collectCaptchaPng(world, player, session, state);
        if (png == null) {
            // Frames / map pixels not streamed in yet — wait for more packets.
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            return;
        }
        boolean settled = state.lastSignature != null
                && state.lastSignature.equals(state.stableSignature);
        // Last resort: 4s before the deadline answer the best we have.
        boolean lastResort = state.deadlineAt - now <= 4_000L;
        if (!settled && state.unstableRounds < UNSTABLE_ROUNDS_FALLBACK && !lastResort) {
            // Multi-wall captcha: shoot only at a settled wall. While maps
            // stream in, clustering returns a different subset every attempt
            // and each answer goes to a half-loaded decoy.
            state.stableSignature = state.lastSignature;
            state.unstableRounds++;
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            BotDebug.info("CAPTCHA_WAIT_STABLE", session,
                    "walls=" + state.wallCount + ", unstable_round=" + state.unstableRounds);
            return;
        }
        if (settled && !state.lastRectangular && !lastResort) {
            // Same ragged wall twice: maps are still trickling in (ragged
            // rows = white holes = guaranteed wrong answer). Wait for the
            // missing maps instead of shooting a partial image.
            state.waitRectRounds++;
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            BotDebug.info("CAPTCHA_WAIT_RECT", session,
                    "walls=" + state.wallCount + ", wait_rect_round=" + state.waitRectRounds);
            return;
        }
        if (state.avoidWall != null && state.lastSignature != null
                && state.lastSignature.equals(lastSentSignature.get(key))) {
            // Re-prompt after a wrong answer, but the pixels are identical:
            // the model would repeat the same wrong digits. Wait for new map
            // data instead of burning the attempt.
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            BotDebug.info("CAPTCHA_WAIT_NEW_PIXELS", session, "walls=" + state.wallCount);
            return;
        }
        state.inFlight = true;
        state.attempts++;
        state.nextAttemptAt = now + SOLVE_POLL_MS;
        if (state.lastSignature != null) {
            lastSentSignature.put(key, state.lastSignature);
        }
        String url = serverUrl;
        String prefix = answerPrefix == null ? "" : answerPrefix;
        Thread.ofVirtual().start(() -> {
            String answer = postSolve(url, png);
            client.execute(() -> {
                state.inFlight = false;
                onSolveResponse(client, key, session, state, answer, prefix);
            });
        });
    }

    private void onSolveResponse(MinecraftClient client, Object key, BotSession session,
                                 SolveState state, String answer, String prefix) {
        if (answer != null && answer.matches("[0-9]{3,8}")) {
            pending.remove(key);
            long now = System.currentTimeMillis();
            solvedCooldown.put(key, now);
            lastAnswerAt.put(key, now);
            if (state != null && state.chosenWallIdentity != null) {
                lastWallUsed.put(key, state.chosenWallIdentity);
            }
            saveAnsweredCopy(debugDirFor(session), answer);
            cleanupDebugDir(session);
            sendAnswer(client, key, session, prefix + answer);
            BotDebug.info("CAPTCHA_SOLVED", session, "answer=" + answer
                    + (state == null ? "" : ", wall=" + state.chosenWall + "/" + state.wallCount));
            String who = session != null ? " за бота " + session.getName() : " на основе";
            String wall = state == null ? "" : " (стена " + (state.chosenWall + 1) + "/" + state.wallCount + ")";
            feedback(session == null ? "main" : session.getName(),
                    "§a[CaptchaSolver] §fОтветил" + who + wall + ": " + answer);
            return;
        }
        BotDebug.warn("CAPTCHA_SOLVE_RETRY", session,
                "answer=" + (answer == null ? "null" : answer));
        if (state != null && state.chosenWallIdentity != null) {
            // A decoy often yields no detection. Try the other physical
            // billboard on the next poll instead of reposting this image.
            state.avoidWall = state.chosenWallIdentity;
            state.prevChosen = -1;
            state.prevWallCount = -1;
        }
    }

    private void sendAnswer(MinecraftClient client, Object key, BotSession session, String text) {
        Runnable sendDirect = () -> {
            try {
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatMessage(text);
                }
            } catch (Throwable error) {
                BotDebug.error("CAPTCHA_ANSWER_FAILED", session, "text=" + text, error);
            }
        };
        Runnable sendViaSession = () -> {
            try {
                ClientPlayNetworkHandler handler = session.getNetworkHandler();
                if (handler != null && session.getConnection() != null
                        && session.getConnection().isOpen()) {
                    // Small human-like pause is already provided by the solve
                    // round-trip (screenshot -> model -> answer); send at once.
                    handler.sendChatMessage(text);
                }
            } catch (Throwable error) {
                BotDebug.error("CAPTCHA_ANSWER_FAILED", session, "text=" + text, error);
            }
        };
        try {
            if (session == null) {
                sendDirect.run();
                return;
            }
            MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
            if (manager != null && session == manager.getActiveSession()
                    && client.world == session.getWorld()) {
                sendViaSession.run();
            } else {
                session.runWithContext(sendViaSession);
            }
        } catch (Throwable error) {
            BotDebug.error("CAPTCHA_ANSWER_CONTEXT_FAILED", session, "text=" + text, error);
        }
    }

    /**
     * Renders captcha maps from nearby item frames to a PNG. Runs on the
     * client thread with the session's own world (no GL, no screenshots),
     * so it works for hidden background bots too.
     *
     * @return PNG bytes or null when no map pixels are streamed in yet.
     */
    private byte[] collectCaptchaPng(ClientWorld world, PlayerEntity player,
                                     BotSession session, SolveState state) {
        if (world == null || player == null) {
            return null;
        }
        List<FrameMap> frames = new ArrayList<>();
        Box box;
        try {
            box = player.getBoundingBox().expand(SCAN_RADIUS);
        } catch (Throwable error) {
            return null;
        }
        List<Entity> entities;
        try {
            entities = new ArrayList<>(world.getOtherEntities(player, box,
                    entity -> entity instanceof ItemFrameEntity));
        } catch (Throwable error) {
            return null;
        }
        int withMap = 0;
        int noData = 0;
        Vec3d nearestNoData = null;
        double nearestNoDataDist = Double.POSITIVE_INFINITY;
        for (Entity entity : entities) {
            ItemFrameEntity frame;
            try {
                frame = (ItemFrameEntity) entity;
            } catch (ClassCastException error) {
                continue;
            }
            ItemStack held;
            try {
                held = frame.getHeldItemStack();
            } catch (Throwable error) {
                continue;
            }
            if (held == null || held.isEmpty() || !held.isOf(Items.FILLED_MAP)) {
                continue;
            }
            withMap++;
            MapIdComponent mapId;
            try {
                mapId = held.get(DataComponentTypes.MAP_ID);
            } catch (Throwable error) {
                continue;
            }
            if (mapId == null) {
                continue;
            }
            MapState mapState;
            try {
                mapState = world.getMapState(mapId);
            } catch (Throwable error) {
                continue;
            }
            if (mapState == null || mapState.colors == null
                    || mapState.colors.length < MAP_SIZE * MAP_SIZE) {
                noData++;
                double dist = entity.getPos().squaredDistanceTo(player.getPos());
                if (dist < nearestNoDataDist) {
                    nearestNoDataDist = dist;
                    nearestNoData = entity.getPos();
                }
                continue;
            }
            byte[] pixels = mapState.colors.clone();
            if (countContentPixels(pixels) < MIN_MAP_CONTENT_PIXELS) {
                // Map packet arrived but pixels not streamed yet.
                noData++;
                double dist = entity.getPos().squaredDistanceTo(player.getPos());
                if (dist < nearestNoDataDist) {
                    nearestNoDataDist = dist;
                    nearestNoData = entity.getPos();
                }
                continue;
            }
            int rotation;
            try {
                // Vanilla renders maps in four 90-degree states even though
                // item frames expose eight 45-degree states for normal items.
                rotation = Math.floorMod(frame.getRotation(), 4);
            } catch (Throwable error) {
                rotation = 0;
            }
            frames.add(new FrameMap(frame.getPos(), pixels, frame.getFacing(), rotation));
            if (frames.size() >= MAX_MAPS_TOTAL) {
                break;
            }
        }
        BotDebug.info("CAPTCHA_SCAN", session,
                "frames=" + entities.size() + ", withMap=" + withMap
                        + ", noData=" + noData + ", rendered=" + frames.size()
                        + (nearestNoData == null ? ""
                        : ", nearestNoData=" + String.format(java.util.Locale.ROOT, "%.1f",
                        Math.sqrt(Math.max(0.0D, nearestNoDataDist)))));
        if (frames.isEmpty()) {
            return null;
        }
        // The server may show TWO billboards: a tangled decoy and the normal
        // captcha. Never stitch everything into one image — split frames into
        // separate walls by position, render each alone and solve only the
        // chosen one whole (no cropping, no mixing).
        Vec3d playerPos = player.getPos();
        double playerYawRad = Math.toRadians(player.getYaw());
        double playerPitchRad = Math.toRadians(player.getPitch());
        Vec3d eyePos;
        try {
            eyePos = player.getEyePos();
        } catch (Throwable error) {
            eyePos = playerPos;
        }
        java.util.List<java.util.List<FrameMap>> walls = clusterWalls(frames, playerPos);
        if (walls.isEmpty()) {
            return null;
        }
        java.io.File debugDir = debugDirFor(session);
        for (int i = 0; i < Math.min(walls.size(), MAX_WALLS); i++) {
            try {
                BufferedImage wallImage = renderWall(walls.get(i), playerPos, playerYawRad);
                if (wallImage != null) {
                    saveWallPng(debugDir, wallImage, i);
                }
            } catch (Throwable ignored) {
            }
        }
        // Solve only whole walls. Single maps and ragged scraps are streaming
        // fragments — answering them feeds the model garbage (it invents
        // digits from a scribble corner) and burns the attempt.
        java.util.List<java.util.List<FrameMap>> eligible = new ArrayList<>();
        for (java.util.List<FrameMap> wall : walls) {
            if (wall.size() >= MIN_WALL_MAPS) {
                eligible.add(wall);
            }
        }
        int smalls = walls.size() - eligible.size();
        if (eligible.isEmpty()) {
            BotDebug.info("CAPTCHA_WAIT_MAPS", session,
                    "fragments=" + smalls + ", frames=" + frames.size());
            return null;
        }
        int stickyIndex = state == null ? -1 : state.prevChosen;
        int stickyCount = state == null ? -1 : state.prevWallCount;
        int chosenWall = selectWallIndex(eligible, state == null ? null : state.avoidWall,
                playerPos, playerYawRad, eyePos, playerPitchRad, stickyIndex, stickyCount);
        java.util.List<FrameMap> target = eligible.get(chosenWall);
        java.util.List<java.util.List<FrameMap>> targetRows = gridRows(target);
        boolean rectangular = !targetRows.isEmpty();
        if (rectangular) {
            int rowLen = targetRows.get(0).size();
            for (java.util.List<FrameMap> row : targetRows) {
                if (row.size() != rowLen) {
                    rectangular = false;
                    break;
                }
            }
        }
        if (state != null) {
            state.chosenWall = chosenWall;
            state.chosenWallIdentity = wallIdentity(target);
            state.wallCount = eligible.size();
            state.prevChosen = chosenWall;
            state.prevWallCount = eligible.size();
            state.lastSignature = wallSignature(eligible.size(), chosenWall, target);
            state.lastRectangular = rectangular;
        }
        BotDebug.info("CAPTCHA_WALLS", session,
                describeWalls(eligible, playerPos, playerYawRad, eyePos, playerPitchRad, chosenWall)
                        + " smalls=" + smalls);
        try {
            BufferedImage stitched = renderWall(target, playerPos, playerYawRad);
            if (stitched == null) {
                return null;
            }
            saveLastPng(debugDir, stitched);
            ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
            ImageIO.write(stitched, "png", out);
            return out.toByteArray();
        } catch (Throwable error) {
            BotDebug.error("CAPTCHA_RENDER_FAILED", session, "frames=" + frames.size(), error);
            return null;
        }
    }

    /** Groups nearby frames into separate billboard walls. */
    private static java.util.List<java.util.List<FrameMap>> clusterWalls(
            java.util.List<FrameMap> frames, Vec3d playerPos) {
        java.util.List<FrameMap> byDist = new ArrayList<>(frames);
        byDist.sort(Comparator.comparingDouble(frame -> frame.pos.squaredDistanceTo(playerPos)));
        java.util.List<java.util.List<FrameMap>> walls = new ArrayList<>();
        for (FrameMap frame : byDist) {
            boolean placed = false;
            for (java.util.List<FrameMap> wall : walls) {
                for (FrameMap member : wall) {
                    // A billboard is both one facing and one geometric plane.
                    // Facing alone is insufficient: two nearby parallel captcha
                    // boards can be only one block apart and the transitive
                    // neighbour walk would otherwise stitch both into one wall.
                    if (!sameBillboardPlane(member, frame)) {
                        continue;
                    }
                    if (member.pos.distanceTo(frame.pos) < WALL_CLUSTER_DIST) {
                        wall.add(frame);
                        placed = true;
                        break;
                    }
                }
                if (placed) {
                    break;
                }
            }
            if (!placed) {
                java.util.List<FrameMap> wall = new ArrayList<>();
                wall.add(frame);
                walls.add(wall);
            }
        }
        // Nearest wall first; drop tiny strays but never crop a real wall.
        walls.sort(Comparator.comparingDouble(wall -> nearestDist(wall, playerPos)));
        java.util.List<java.util.List<FrameMap>> result = new ArrayList<>();
        for (java.util.List<FrameMap> wall : walls) {
            if (result.size() >= MAX_WALLS) {
                break;
            }
            wall.sort(Comparator.comparingDouble(frame -> frame.pos.squaredDistanceTo(playerPos)));
            if (wall.size() > MAX_MAPS_PER_WALL) {
                wall = new ArrayList<>(wall.subList(0, MAX_MAPS_PER_WALL));
            }
            result.add(wall);
        }
        return result;
    }

    private static boolean sameBillboardPlane(FrameMap first, FrameMap second) {
        if (first.facing() == null || first.facing() != second.facing()) {
            return false;
        }
        return switch (first.facing().getAxis()) {
            case X -> Math.abs(first.pos.x - second.pos.x) < 0.35D;
            case Z -> Math.abs(first.pos.z - second.pos.z) < 0.35D;
            case Y -> Math.abs(first.pos.y - second.pos.y) < 0.35D;
        };
    }

    private static double nearestDist(java.util.List<FrameMap> wall, Vec3d playerPos) {
        double best = Double.POSITIVE_INFINITY;
        for (FrameMap frame : wall) {
            best = Math.min(best, frame.pos.squaredDistanceTo(playerPos));
        }
        return best;
    }

    private static double wallDensity(java.util.List<FrameMap> wall) {
        if (wall.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        long total = 0;
        for (FrameMap frame : wall) {
            total += countContentPixels(frame.pixels);
        }
        return (double) total / (double) wall.size();
    }

    /**
     * Picks the captcha wall, returned as an index into the nearest-first
     * wall list. Order of preference: explicit crosshair mode ("По прицелу")
     * always takes the wall closest to the look vector; auto mode takes it
     * too when the player clearly looks at a wall (FunTime: the real captcha
     * is the one in the crosshair, the decoy hangs off to the side), and
     * otherwise falls back to the most grid-regular wall. Ties keep
     * nearest-first order. Hysteresis: the previously chosen wall sticks when
     * it is within epsilon of the best score, so streaming noise cannot flip
     * the answer target every half second. A wall avoided after a wrong
     * answer is skipped unless it is the only candidate.
     */
    private int selectWallIndex(java.util.List<java.util.List<FrameMap>> walls,
                                WallIdentity avoidWall, Vec3d playerPos, double playerYawRad,
                                Vec3d eyePos, double playerPitchRad,
                                int stickyIndex, int stickyWallCount) {
        if (walls.size() == 1) {
            return 0;
        }
        String mode = wallMode == null ? "auto" : wallMode.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<Integer> order = new ArrayList<>();
        for (int i = 0; i < walls.size(); i++) {
            order.add(i);
        }
        if (mode.startsWith("sparse") || mode.startsWith("clean")
                || mode.startsWith("чист") || mode.startsWith("редк")) {
            order.sort(Comparator.comparingDouble(i -> wallDensity(walls.get(i))));
        } else if (mode.startsWith("nearest") || mode.startsWith("ближ")) {
            // Nearest-first order as built by clusterWalls.
        } else {
            double[] angles = wallViewAngles(walls, eyePos, playerYawRad, playerPitchRad);
            double bestAngle = angles[0];
            for (double angle : angles) {
                bestAngle = Math.min(bestAngle, angle);
            }
            boolean forceCrosshair = mode.startsWith("cross") || mode.startsWith("прицел");
            if (forceCrosshair || bestAngle <= CROSSHAIR_MAX_RAD) {
                double[] irr = new double[walls.size()];
                for (int i = 0; i < walls.size(); i++) {
                    irr[i] = wallGridIrregularity(walls.get(i), playerPos, playerYawRad);
                }
                final double[] tieIrr = irr;
                // Crosshair first, grid score breaks ties between neighbours.
                order.sort(Comparator.comparingDouble((Integer i) -> angles[i])
                        .thenComparingDouble(i -> tieIrr[i]));
                applyStickyOrder(order, angles, stickyIndex, stickyWallCount,
                        walls.size(), CROSSHAIR_STICKY_EPS_RAD);
            } else {
                double[] irr = new double[walls.size()];
                for (int i = 0; i < walls.size(); i++) {
                    irr[i] = wallGridIrregularity(walls.get(i), playerPos, playerYawRad);
                }
                // Stable sort: equal irregularity keeps nearest-first order.
                order.sort(Comparator.comparingDouble(i -> irr[i]));
                applyStickyOrder(order, irr, stickyIndex, stickyWallCount,
                        walls.size(), 0.05D);
            }
        }
        for (int index : order) {
            if (avoidWall == null || !avoidWall.equals(wallIdentity(walls.get(index)))) {
                return index;
            }
        }
        return order.get(0);
    }

    /** Identity of a physical billboard, independent of list order and pixels. */
    private static WallIdentity wallIdentity(java.util.List<FrameMap> wall) {
        if (wall == null || wall.isEmpty()) {
            return null;
        }
        net.minecraft.util.math.Direction facing = wall.get(0).facing();
        double plane = 0.0D;
        double horizontal = 0.0D;
        double height = 0.0D;
        for (FrameMap frame : wall) {
            if (facing != null && facing.getAxis() == net.minecraft.util.math.Direction.Axis.X) {
                plane += frame.pos.x;
                horizontal += frame.pos.z;
            } else {
                plane += frame.pos.z;
                horizontal += frame.pos.x;
            }
            height += frame.pos.y;
        }
        double count = wall.size();
        return new WallIdentity(facing,
                (int) Math.round(plane / count * 10.0D),
                (int) Math.round(horizontal / count * 10.0D),
                (int) Math.round(height / count * 10.0D));
    }

    /** Moves the previously chosen wall first when it is within eps of best. */
    private static void applyStickyOrder(java.util.List<Integer> order, double[] scores,
                                         int stickyIndex, int stickyWallCount,
                                         int wallCount, double eps) {
        if (order.isEmpty() || stickyIndex < 0 || stickyIndex >= scores.length
                || wallCount != stickyWallCount) {
            return;
        }
        int best = order.get(0);
        if (scores[stickyIndex] <= scores[best] + eps) {
            order.remove((Integer) stickyIndex);
            order.add(0, stickyIndex);
        }
    }

    /**
     * Angular distance from the look vector to each wall center, radians.
     * The captcha the player must read is the one they face.
     */
    private static double[] wallViewAngles(java.util.List<java.util.List<FrameMap>> walls,
                                           Vec3d eyePos, double playerYawRad,
                                           double playerPitchRad) {
        double lookX = -Math.sin(playerYawRad) * Math.cos(playerPitchRad);
        double lookY = -Math.sin(playerPitchRad);
        double lookZ = Math.cos(playerYawRad) * Math.cos(playerPitchRad);
        double[] angles = new double[walls.size()];
        for (int i = 0; i < walls.size(); i++) {
            java.util.List<FrameMap> wall = walls.get(i);
            double cx = 0.0D;
            double cy = 0.0D;
            double cz = 0.0D;
            for (FrameMap frame : wall) {
                cx += frame.pos.x;
                cy += frame.pos.y;
                cz += frame.pos.z;
            }
            cx = cx / wall.size() - eyePos.x;
            cy = cy / wall.size() - eyePos.y;
            cz = cz / wall.size() - eyePos.z;
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len <= 1.0E-6D) {
                angles[i] = 0.0D;
                continue;
            }
            double dot = (cx * lookX + cy * lookY + cz * lookZ) / len;
            dot = Math.max(-1.0D, Math.min(1.0D, dot));
            angles[i] = Math.acos(dot);
        }
        return angles;
    }

    /**
     * Signature of the chosen wall: wall count + index + every frame position
     * and content pixel count. Changes while maps stream in, settles once the
     * wall is fully loaded — solving only settled walls stops answers to
     * half-loaded decoys.
     */
    private static String wallSignature(int wallCount, int chosenWall,
                                        java.util.List<FrameMap> target) {
        StringBuilder sig = new StringBuilder(256);
        sig.append(wallCount).append('/').append(chosenWall).append('/');
        java.util.List<FrameMap> ordered = new ArrayList<>(target);
        ordered.sort(Comparator.comparingDouble((FrameMap frame) -> frame.pos.x)
                .thenComparingDouble(frame -> frame.pos.y)
                .thenComparingDouble(frame -> frame.pos.z));
        for (FrameMap frame : ordered) {
            sig.append((int) Math.round(frame.pos.x * 10.0D)).append(',')
                    .append((int) Math.round(frame.pos.y * 10.0D)).append(',')
                    .append((int) Math.round(frame.pos.z * 10.0D)).append(':')
                    .append(countContentPixels(frame.pixels)).append(';');
        }
        return sig.toString();
    }

    /** One-line wall overview for the log: size, grid score, view angle, distance. */
    private static String describeWalls(java.util.List<java.util.List<FrameMap>> walls,
                                        Vec3d playerPos, double playerYawRad,
                                        Vec3d eyePos, double playerPitchRad, int chosenWall) {
        double[] angles = wallViewAngles(walls, eyePos, playerYawRad, playerPitchRad);
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < walls.size(); i++) {
            java.util.List<FrameMap> wall = walls.get(i);
            double dist = Math.sqrt(Math.max(0.0D, nearestDist(wall, playerPos)));
            String fac = "?";
            try {
                if (!wall.isEmpty() && wall.get(0).facing() != null) {
                    fac = wall.get(0).facing().asString();
                }
            } catch (Throwable ignored) {
            }
            sb.append("[wall").append(i)
                    .append(" size=").append(wall.size())
                    .append(" fac=").append(fac)
                    .append(" rot=").append(rotationSummary(wall))
                    .append(" irr=").append(String.format(java.util.Locale.ROOT, "%.2f",
                    wallGridIrregularity(wall, playerPos, playerYawRad)))
                    .append(" ang=").append(String.format(java.util.Locale.ROOT, "%.2f", angles[i]))
                    .append(" dist=").append(String.format(java.util.Locale.ROOT, "%.1f", dist))
                    .append(']');
        }
        sb.append(" chosen=").append(chosenWall);
        return sb.toString();
    }

    private static String rotationSummary(java.util.List<FrameMap> wall) {
        int[] counts = new int[4];
        for (FrameMap frame : wall) {
            counts[Math.floorMod(frame.rotation(), 4)]++;
        }
        return counts[0] + "," + counts[1] + "," + counts[2] + "," + counts[3];
    }

    /**
     * Grid irregularity of one wall (lower = more like a real captcha).
     * Groups maps by height row, then measures how uniform the gaps between
     * neighbours are (coefficient of variation) plus penalties for ragged
     * rows. A tangled decoy wall scores high, a neat digit grid near zero.
     */
    private static double wallGridIrregularity(java.util.List<FrameMap> wall,
                                               Vec3d playerPos, double playerYawRad) {
        if (wall == null || wall.size() < 2) {
            return 1.0D;
        }
        java.util.TreeMap<Integer, java.util.List<FrameMap>> rows =
                new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        for (FrameMap frame : wall) {
            rows.computeIfAbsent((int) Math.floor(frame.pos.y), ignored -> new ArrayList<>()).add(frame);
        }
        double totalCv = 0.0D;
        int measured = 0;
        int maxRow = 0;
        int minRow = Integer.MAX_VALUE;
        int singleRows = 0;
        for (java.util.List<FrameMap> row : rows.values()) {
            maxRow = Math.max(maxRow, row.size());
            minRow = Math.min(minRow, row.size());
            if (row.size() < 2) {
                singleRows++;
                continue;
            }
            row.sort(Comparator.comparingDouble(CaptchaSolver::wallHorizontalCoordinate));
            java.util.List<Double> gaps = new ArrayList<>();
            double mean = 0.0D;
            for (int i = 1; i < row.size(); i++) {
                double gap = row.get(i).pos.distanceTo(row.get(i - 1).pos);
                gaps.add(gap);
                mean += gap;
            }
            mean /= gaps.size();
            if (mean <= 1.0E-6D) {
                continue;
            }
            double variance = 0.0D;
            for (double gap : gaps) {
                variance += (gap - mean) * (gap - mean);
            }
            totalCv += Math.sqrt(variance / gaps.size()) / mean;
            measured++;
        }
        double cv = measured == 0 ? 1.0D : totalCv / measured;
        return cv + (maxRow - minRow) * 0.25D + singleRows * 0.35D;
    }

    /**
     * Groups wall maps into rows (top first) with a Y tolerance: a billboard
     * standing on uneven ground (y=63.9..64.1) must stay one row, otherwise
     * tiles shred into ragged rows with white holes that blind the model.
     */
    private static java.util.List<java.util.List<FrameMap>> gridRows(
            java.util.List<FrameMap> wall) {
        java.util.List<FrameMap> byHeight = new ArrayList<>(wall);
        byHeight.sort(Comparator.comparingDouble((FrameMap frame) -> frame.pos.y).reversed());
        java.util.List<java.util.List<FrameMap>> grid = new ArrayList<>();
        for (FrameMap frame : byHeight) {
            java.util.List<FrameMap> row = grid.isEmpty() ? null : grid.get(grid.size() - 1);
            if (row == null || Math.abs(rowMeanY(row) - frame.pos.y) >= 0.6D) {
                row = new ArrayList<>();
                grid.add(row);
            }
            row.add(frame);
        }
        return grid;
    }

    /**
     * Renders one wall: groups its maps by height (top row first), sorts each
     * row by the billboard's local horizontal axis and stitches the grid at
     * 2x. Camera-angle sorting cannot be used here: a wall crossing the
     * -pi/pi angle boundary cyclically moves half of a row to the other side.
     */
    private static BufferedImage renderWall(java.util.List<FrameMap> wall,
                                            Vec3d playerPos, double playerYawRad) {
        if (wall == null || wall.isEmpty()) {
            return null;
        }
        java.util.List<java.util.List<FrameMap>> grid = gridRows(wall);
        int cols = 1;
        for (java.util.List<FrameMap> row : grid) {
            row.sort(Comparator.comparingDouble(CaptchaSolver::wallHorizontalCoordinate));
            cols = Math.max(cols, row.size());
        }
        int scale = 2;
        BufferedImage stitched = new BufferedImage(
                MAP_SIZE * cols * scale, MAP_SIZE * grid.size() * scale, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D gfx = stitched.createGraphics();
        try {
            gfx.setColor(java.awt.Color.WHITE);
            gfx.fillRect(0, 0, stitched.getWidth(), stitched.getHeight());
        } finally {
            gfx.dispose();
        }
        for (int row = 0; row < grid.size(); row++) {
            java.util.List<FrameMap> rowFrames = grid.get(row);
            for (int col = 0; col < rowFrames.size(); col++) {
                FrameMap frame = rowFrames.get(col);
                renderMapInto(stitched, frame.pixels,
                        col * MAP_SIZE * scale, row * MAP_SIZE * scale, scale,
                        frame.rotation());
            }
        }
        return stitched;
    }

    /** Left-to-right coordinate as seen from the front of an item-frame wall. */
    private static double wallHorizontalCoordinate(FrameMap frame) {
        if (frame.facing() == null) {
            return frame.pos.x;
        }
        return switch (frame.facing()) {
            case SOUTH -> frame.pos.x;
            case NORTH -> -frame.pos.x;
            case WEST -> frame.pos.z;
            case EAST -> -frame.pos.z;
            default -> frame.pos.x;
        };
    }

    private static double rowMeanY(java.util.List<FrameMap> row) {
        double sum = 0.0D;
        for (FrameMap frame : row) {
            sum += frame.pos.y;
        }
        return row.isEmpty() ? 0.0D : sum / row.size();
    }

    /** 2-bit map shade to brightness (Brightness.get(int) is package-private). */
    private static MapColor.Brightness shade(int bits) {
        return switch (bits & 3) {
            case 0 -> MapColor.Brightness.LOWEST;
            case 1 -> MapColor.Brightness.LOW;
            case 3 -> MapColor.Brightness.HIGH;
            default -> MapColor.Brightness.NORMAL;
        };
    }

    private static int countContentPixels(byte[] pixels) {        int content = 0;
        for (byte pixel : pixels) {
            if ((((pixel & 0xFF) >> 2) & 63) != 0) {
                content++;
            }
        }
        return content;
    }

    private static void renderMapInto(BufferedImage image, byte[] pixels, int offsetX,
                                      int offsetY, int scale, int rotation) {
        int turns = Math.floorMod(rotation, 4);
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int sourceX;
                int sourceY;
                switch (turns) {
                    case 1 -> {
                        sourceX = y;
                        sourceY = MAP_SIZE - 1 - x;
                    }
                    case 2 -> {
                        sourceX = MAP_SIZE - 1 - x;
                        sourceY = MAP_SIZE - 1 - y;
                    }
                    case 3 -> {
                        sourceX = MAP_SIZE - 1 - y;
                        sourceY = x;
                    }
                    default -> {
                        sourceX = x;
                        sourceY = y;
                    }
                }
                int raw = pixels[sourceX + sourceY * MAP_SIZE] & 0xFF;
                int rgb;
                int baseId = (raw >> 2) & 63;
                if (baseId == 0) {
                    rgb = 0xFFFFFF;
                } else {
                    try {
                        MapColor base = MapColor.get(baseId);
                        rgb = base == null ? 0xFFFFFF
                                : base.getRenderColor(shade(raw & 3));
                    } catch (Throwable error) {
                        rgb = 0xFFFFFF;
                    }
                }
                for (int dz = 0; dz < scale; dz++) {
                    for (int dx = 0; dx < scale; dx++) {
                        image.setRGB(offsetX + x * scale + dx, offsetY + y * scale + dz, rgb);
                    }
                }
            }
        }
    }

    /**
     * Debug folder for one session: &lt;gamedir&gt;/fuga-captcha/&lt;name&gt;/.
     * Created on demand; pass false when resolving for deletion.
     */
    private static java.io.File debugDirFor(BotSession session, boolean create) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.runDirectory == null) {
                return null;
            }
            String raw = session == null ? "main" : session.getName();
            String safe = raw == null ? "main" : raw.replaceAll("[^A-Za-z0-9_-]", "_");
            if (safe.isBlank()) {
                safe = "main";
            }
            java.io.File dir = new java.io.File(
                    new java.io.File(client.runDirectory, "fuga-captcha"), safe);
            if (create) {
                dir.mkdirs();
                if (!dir.isDirectory()) {
                    return null;
                }
            }
            return dir;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.io.File debugDirFor(BotSession session) {
        return debugDirFor(session, true);
    }

    /**
     * Deletes the session's screenshot folder after the captcha is passed
     * (or dropped). Does nothing when the Debug toggle is on.
     */
    private void cleanupDebugDir(BotSession session) {
        if (debugKeepShots) {
            return;
        }
        try {
            java.io.File dir = debugDirFor(session, false);
            if (dir == null) {
                return;
            }
            String canonical = dir.getCanonicalPath();
            if (!canonical.contains("fuga-captcha")) {
                return;
            }
            deleteRecursive(dir);
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursive(java.io.File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            java.io.File[] kids = file.listFiles();
            if (kids != null) {
                for (java.io.File kid : kids) {
                    deleteRecursive(kid);
                }
            }
        }
        file.delete();
    }

    /** Dumps the exact image sent to the model so the user can verify framing. */
    private static void saveLastPng(java.io.File dir, BufferedImage image) {
        if (dir == null) {
            return;
        }
        try {
            ImageIO.write(image, "png", new java.io.File(dir, "last.png"));
        } catch (Throwable ignored) {
        }
    }

    /** Dumps every detected wall so the user can see decoy vs normal. */
    private static void saveWallPng(java.io.File dir, BufferedImage image, int index) {
        if (dir == null) {
            return;
        }
        try {
            ImageIO.write(image, "png", new java.io.File(dir, "wall" + index + ".png"));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Archives every answered captcha as &lt;time&gt;_&lt;answer&gt;.png next
     * to the walls. Combined with the Debug toggle that folder becomes the
     * labeled dataset for retraining the model.
     */
    private static void saveAnsweredCopy(java.io.File dir, String answer) {
        if (dir == null) {
            return;
        }
        try {
            java.io.File last = new java.io.File(dir, "last.png");
            if (last.isFile()) {
                String safe = answer.replaceAll("[^0-9]", "");
                java.io.File dest = new java.io.File(dir,
                        System.currentTimeMillis() + "_" + safe + ".png");
                java.nio.file.Files.copy(last.toPath(), dest.toPath());
            }
        } catch (Throwable ignored) {
        }
    }

    private static double wrapAngle(double angle) {
        while (angle > Math.PI) {
            angle -= Math.PI * 2.0D;
        }
        while (angle < -Math.PI) {
            angle += Math.PI * 2.0D;
        }
        return angle;
    }

    /** POSTs the PNG to the Flask model server. Runs on a worker thread. */
    private String postSolve(String url, byte[] png) {
        try {
            String boundary = "----FluxCaptcha" + System.currentTimeMillis();
            byte[] header = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"captcha.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[header.length + png.length + footer.length];
            System.arraycopy(header, 0, body, 0, header.length);
            System.arraycopy(png, 0, body, header.length, png.length);
            System.arraycopy(footer, 0, body, header.length + png.length, footer.length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response =
                    SHARED_HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return extractResult(response.body());
        } catch (Throwable error) {
            return null;
        }
    }

    /** Extracts {"result": "..."} without extra dependencies. */
    private static String extractResult(String json) {
        if (json == null) {
            return null;
        }
        String lower = json.toLowerCase(Locale.ROOT);
        int key = lower.indexOf("\"result\"");
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key);
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        String value = json.substring(firstQuote + 1, secondQuote).trim();
        return value.isEmpty() ? null : value;
    }

    private static void feedback(String sessionName, String message) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.inGameHud != null
                    && client.inGameHud.getChatHud() != null) {
                Text text = Text.literal(message);
                client.execute(() -> client.inGameHud.getChatHud().addMessage(text));
            }
        } catch (Throwable ignored) {
        }
    }

    private record FrameMap(Vec3d pos, byte[] pixels, net.minecraft.util.math.Direction facing,
                            int rotation) {
    }

    private record WallIdentity(net.minecraft.util.math.Direction facing, int planeTenths,
                                int horizontalCenterTenths, int heightCenterTenths) {
    }

    private static final class SolveState {
        final long deadlineAt;
        final long armedAt;
        volatile long nextAttemptAt;
        volatile boolean inFlight;
        volatile int attempts;
        volatile WallIdentity avoidWall;
        volatile int chosenWall;
        volatile WallIdentity chosenWallIdentity;
        volatile int wallCount;
        volatile int prevChosen = -1;
        volatile int prevWallCount = -1;
        volatile String lastSignature;
        volatile String stableSignature;
        volatile int unstableRounds;
        volatile boolean lastRectangular;
        volatile int waitRectRounds;

        SolveState(long deadlineAt, long nextAttemptAt, long armedAt) {
            this.deadlineAt = deadlineAt;
            this.nextAttemptAt = nextAttemptAt;
            this.armedAt = armedAt;
        }
    }
}
