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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Solves captcha billboards directly in-game using the embedded ONNX YOLO neural network
 * ported from Velka (best.onnx + ONNX Runtime).
 *
 * <p>Operates fully in-process without any external Python/Flask servers.
 * Scans item frames holding filled maps in the world, reconstructs the billboard,
 * runs YOLO inference directly on the GPU/CPU via ONNX Runtime, performs NMS,
 * and automatically sends the detected digits to chat for both the local player
 * and background MultiBot sessions.
 */
public final class CaptchaSolver extends Module {
    private static final int MAP_SIZE = 128;
    private static final int MAX_MAPS_TOTAL = 64;
    private static final int MAX_MAPS_PER_WALL = 32;
    private static final double WALL_CLUSTER_DIST = 1.6D;
    private static final int MAX_WALLS = 3;
    private static final int MIN_WALL_MAPS = 4;
    private static final int MIN_MAP_CONTENT_PIXELS = 1200;

    private static final long FIRST_ATTEMPT_DELAY_MS = 250L;
    private static final long SOLVE_POLL_MS = 500L;
    private static final long SOLVE_DEADLINE_MS = 30_000L;
    private static final long SOLVED_COOLDOWN_MS = 1_200L;
    private static final long REARM_QUIET_MS = 15_000L;
    private static final long REPROMPT_ROTATE_MS = 25_000L;
    private static final String MAIN_KEY = "__main__";

    private boolean autoSend = true;
    private float searchRadius = 60.0f;
    private float activeTimeSec = 20.0f;
    private float accuracy = 0.90f;
    private boolean debug = false;
    private String serverUrl = "embedded://onnx";
    private String answerPrefix = "";
    private String wallMode = "auto";

    private final ConcurrentMap<Object, SolveState> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Long> solvedCooldown = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, WallIdentity> lastWallUsed = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Long> lastAnswerAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, String> lastSentSignature = new ConcurrentHashMap<>();

    private volatile boolean aiReady = false;
    private volatile boolean aiLoading = false;
    private volatile boolean isDestroyed = false;

    private static final String[] DIGITS = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private volatile MapColor[] hookedColors = null;
    private volatile Field hookedColorField = null;

    public CaptchaSolver() {
        super("CaptchaSolver", "Автоматически решает капчу с помощью ИИ (ONNX YOLO).", ModuleCategory.UTILS);
        setEnabledSilently(true);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        super.onEnable(client);
        ensureAiLoaded();
        hookMapColors();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        super.onDisable(client);
        pending.clear();
    }

    public void destroy() {
        isDestroyed = true;
        pending.clear();
        AutoCaptchaInference.close();
    }

    public boolean isAutoSend() {
        return autoSend;
    }

    public void setAutoSend(boolean val) {
        this.autoSend = val;
    }

    public float getSearchRadius() {
        return searchRadius;
    }

    public void setSearchRadius(float searchRadius) {
        this.searchRadius = searchRadius;
    }

    public float getActiveTimeSec() {
        return activeTimeSec;
    }

    public void setActiveTimeSec(float activeTimeSec) {
        this.activeTimeSec = activeTimeSec;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean val) {
        this.debug = val;
    }

    public boolean isDebugKeepShots() {
        return debug;
    }

    public void setDebugKeepShots(boolean debugKeepShots) {
        this.debug = debugKeepShots;
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

    private void ensureAiLoaded() {
        if (!aiReady && !aiLoading) {
            aiLoading = true;
            feedback("main", "§e[AutoCaptcha] Load AI...");
            AutoCaptchaInference.initAsync().whenComplete((v, error) -> {
                aiLoading = false;
                if (isDestroyed) {
                    return;
                }
                if (error != null) {
                    feedback("main", "§c[AutoCaptcha] AI Load Error: " + error.getMessage());
                    return;
                }
                aiReady = true;
                feedback("main", "§a[AutoCaptcha] AI Ready!");
            });
        }
    }

    private synchronized boolean hookMapColors() {
        try {
            if (hookedColors != null && hookedColorField != null) {
                return true;
            }
            hookedColors = null;
            hookedColorField = null;
            for (Field field : MapColor.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType().isArray()
                        && field.getType().getComponentType() == MapColor.class) {
                    field.setAccessible(true);
                    hookedColors = (MapColor[]) field.get(null);
                    break;
                }
            }
            for (Field field : MapColor.class.getDeclaredFields()) {
                if (field.getType() == Integer.TYPE && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    if (hookedColors != null && hookedColors.length > 5) {
                        MapColor sample = hookedColors[4];
                        if (sample != null && field.getInt(sample) != 0) {
                            hookedColorField = field;
                            break;
                        }
                    }
                }
            }
            return hookedColors != null && hookedColorField != null;
        } catch (Exception e) {
            hookedColors = null;
            hookedColorField = null;
            return false;
        }
    }

    private int getMapColorRgb(int colorIndex, int shadeIndex) {
        if (hookedColors != null && hookedColorField != null && colorIndex >= 0 && colorIndex < hookedColors.length) {
            try {
                MapColor mc = hookedColors[colorIndex];
                if (mc != null) {
                    int base = hookedColorField.getInt(mc);
                    float f = 1.0f;
                    if (shadeIndex == 0) {
                        f = 0.71f;
                    } else if (shadeIndex == 1) {
                        f = 0.86f;
                    } else if (shadeIndex == 3) {
                        f = 0.53f;
                    }
                    int r = (int) (((base >> 16) & 0xFF) * f);
                    int g = (int) (((base >> 8) & 0xFF) * f);
                    int b = (int) ((base & 0xFF) * f);
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            MapColor mc = MapColor.get(colorIndex);
            if (mc != null) {
                return mc.getRenderColor(shadeIndex);
            }
        } catch (Throwable ignored) {
        }
        return 0xFF000000;
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

    public void cancelFor(BotSession session) {
        if (session == null) {
            return;
        }
        pending.remove(session);
        solvedCooldown.remove(session);
    }

    private WallIdentity rotateAvoidWall(Object key, long now) {
        Long answeredAt = lastAnswerAt.get(key);
        WallIdentity used = lastWallUsed.get(key);
        if (answeredAt != null && used != null && now - answeredAt < REPROMPT_ROTATE_MS) {
            return used;
        }
        return null;
    }

    public static boolean isCaptchaPrompt(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("botfilter")
                || normalized.contains("введите номер с картинки")
                || normalized.contains("номер с картинки")
                || normalized.contains("картинки")
                || normalized.contains("код с картинки")
                || normalized.contains("введите код")
                || normalized.contains("введите номер")
                || normalized.contains("введите цифры")
                || normalized.contains("цифры с картинки")
                || normalized.contains("captcha")
                || normalized.contains("капч");
    }

    public boolean onCaptchaMessage(BotSession session) {
        if (!isEnabledFor(session)) {
            return false;
        }
        ensureAiLoaded();
        long now = System.currentTimeMillis();
        Long cooled = solvedCooldown.get(session);
        if (cooled != null && now - cooled < SOLVED_COOLDOWN_MS) {
            return true;
        }
        SolveState existing = pending.get(session);
        if (existing != null && now - existing.armedAt < REARM_QUIET_MS) {
            return true;
        }
        long deadline = (long) (activeTimeSec * 1000.0f);
        if (deadline < 5000L) deadline = SOLVE_DEADLINE_MS;
        SolveState state = new SolveState(now + deadline, now + FIRST_ATTEMPT_DELAY_MS, now);
        state.avoidWall = rotateAvoidWall(session, now);
        pending.put(session, state);
        BotDebug.info("CAPTCHA_SOLVER_ARMED", session, "deadline_ms=" + deadline);
        feedback(session.getName(), "§a[AutoCaptcha] §fКапча у бота " + session.getName() + ", решаю через ИИ…");
        return true;
    }

    public boolean onCaptchaMain() {
        if (!isEnabled()) {
            return false;
        }
        ensureAiLoaded();
        long now = System.currentTimeMillis();
        Long cooled = solvedCooldown.get(MAIN_KEY);
        if (cooled != null && now - cooled < SOLVED_COOLDOWN_MS) {
            return true;
        }
        SolveState existing = pending.get(MAIN_KEY);
        if (existing != null && now - existing.armedAt < REARM_QUIET_MS) {
            return true;
        }
        long deadline = (long) (activeTimeSec * 1000.0f);
        if (deadline < 5000L) deadline = SOLVE_DEADLINE_MS;
        SolveState state = new SolveState(now + deadline, now + FIRST_ATTEMPT_DELAY_MS, now);
        state.avoidWall = rotateAvoidWall(MAIN_KEY, now);
        pending.put(MAIN_KEY, state);
        BotDebug.info("CAPTCHA_SOLVER_ARMED", null, "target=main");
        feedback("main", "§a[AutoCaptcha] §fКапча на основе, решаю через ИИ…");
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
        BotSession active = manager.getActiveSession();
        if (active != null) {
            tickSession(client, active);
        } else {
            tickUnmanagedMain(client);
        }
    }

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
        // Main session captcha is armed via onCaptchaMain() which uses MAIN_KEY.
        // Use the same key so pending.get() finds the task.
        Object key = session.isMain() ? MAIN_KEY : session;
        tickKeyed(client, key, session.isMain() ? null : session, world, player);
    }

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
            BotDebug.warn("CAPTCHA_SOLVER_TIMEOUT", session, "attempts=" + state.attempts);
            if (session != null) {
                feedback(session.getName(), "§c[AutoCaptcha] §fНе решил капчу за отведенное время.");
                MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
                if (manager != null) {
                    manager.requestManualCaptcha(session);
                }
            } else {
                feedback("main", "§c[AutoCaptcha] §fНе решил капчу за отведенное время.");
            }
            return;
        }
        if (state.inFlight || now < state.nextAttemptAt) {
            return;
        }

        BufferedImage wallImage = collectCaptchaImage(world, player, session, state);
        if (wallImage == null) {
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            return;
        }

        if (!AutoCaptchaInference.isReady()) {
            ensureAiLoaded();
            state.nextAttemptAt = now + SOLVE_POLL_MS;
            return;
        }

        state.inFlight = true;
        state.attempts++;
        state.nextAttemptAt = now + 1000L;

        File debugFolder = debug ? new File(client.runDirectory, "debug") : null;

        AutoCaptchaInference.submit(() -> {
            try {
                String answer = solveOnnxImage(wallImage, debugFolder);
                client.execute(() -> {
                    state.inFlight = false;
                    onSolveResponse(client, key, session, state, answer);
                });
            } catch (Throwable error) {
                client.execute(() -> {
                    state.inFlight = false;
                    BotDebug.error("CAPTCHA_INFERENCE_ERROR", session, "failed", error);
                });
            }
        });
    }

    private String solveOnnxImage(BufferedImage source, File debugDir) throws Exception {
        BufferedImage resized = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, 640, 640, null);
        g.dispose();

        if (debugDir != null) {
            if (!debugDir.exists()) {
                debugDir.mkdirs();
            }
            File dbgFile = new File(debugDir, "captcha_reconstruct_" + System.currentTimeMillis() + ".png");
            try {
                ImageIO.write(resized, "png", dbgFile);
            } catch (Exception ignored) {
            }
        }

        float[] input = new float[1228800];
        for (int y = 0; y < 640; y++) {
            for (int x = 0; x < 640; x++) {
                int rgb = resized.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float gVal = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                input[0 + y * 640 + x] = r;
                input[409600 + y * 640 + x] = gVal;
                input[819200 + y * 640 + x] = b;
            }
        }

        float[][] rawOutput = AutoCaptchaInference.runInference(input);
        return parseYoloDetections(rawOutput);
    }

    private String parseYoloDetections(float[][] output) {
        if (output == null || output.length < 5) {
            return "";
        }
        int numAnchors = output[0].length;
        int numClasses = 10;
        List<DetectionBox> boxes = new ArrayList<>();

        for (int i = 0; i < numAnchors; i++) {
            float maxScore = 0.0f;
            int bestClass = -1;
            for (int c = 0; c < numClasses; c++) {
                float score = output[4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    bestClass = c;
                }
            }
            if (maxScore > 0.4f && bestClass >= 0) {
                float cx = output[0][i];
                float cy = output[1][i];
                float w = output[2][i];
                float h = output[3][i];
                float x1 = cx - w / 2.0f;
                float y1 = cy - h / 2.0f;
                float x2 = cx + w / 2.0f;
                float y2 = cy + h / 2.0f;
                boxes.add(new DetectionBox(x1, y1, x2, y2, maxScore, bestClass));
            }
        }

        List<DetectionBox> nmsBoxes = applyNms(boxes, 0.5f);
        nmsBoxes.sort((a, b) -> Float.compare(a.x1, b.x1));

        StringBuilder sb = new StringBuilder();
        for (DetectionBox box : nmsBoxes) {
            if (box.cls >= 0 && box.cls < DIGITS.length) {
                sb.append(DIGITS[box.cls]);
            }
        }
        return sb.toString();
    }

    private List<DetectionBox> applyNms(List<DetectionBox> list, float threshold) {
        ArrayList<DetectionBox> result = new ArrayList<>();
        list.sort((a, b) -> Float.compare(b.score, a.score));
        boolean[] removed = new boolean[list.size()];

        for (int i = 0; i < list.size(); i++) {
            if (removed[i]) continue;
            DetectionBox current = list.get(i);
            result.add(current);
            for (int j = i + 1; j < list.size(); j++) {
                if (removed[j]) continue;
                if (computeIoU(current, list.get(j)) > threshold) {
                    removed[j] = true;
                }
            }
        }
        return result;
    }

    private float computeIoU(DetectionBox a, DetectionBox b) {
        float x1 = Math.max(a.x1, b.x1);
        float y1 = Math.max(a.y1, b.y1);
        float x2 = Math.min(a.x2, b.x2);
        float y2 = Math.min(a.y2, b.y2);

        float intersection = Math.max(0.0f, x2 - x1) * Math.max(0.0f, y2 - y1);
        float areaA = (a.x2 - a.x1) * (a.y2 - a.y1);
        float areaB = (b.x2 - b.x1) * (b.y2 - b.y1);
        float union = areaA + areaB - intersection;
        return union <= 0.0f ? 0.0f : intersection / union;
    }

    private void onSolveResponse(MinecraftClient client, Object key, BotSession session,
                                 SolveState state, String answer) {
        if (answer != null && answer.matches("[0-9]{3,8}")) {
            pending.remove(key);
            long now = System.currentTimeMillis();
            solvedCooldown.put(key, now);
            lastAnswerAt.put(key, now);
            if (state != null && state.chosenWallIdentity != null) {
                lastWallUsed.put(key, state.chosenWallIdentity);
            }

            feedback(session == null ? "main" : session.getName(), "§a[AutoCaptcha] Result: " + answer);
            BotDebug.info("CAPTCHA_SOLVED", session, "answer=" + answer);

            if (autoSend) {
                sendAnswer(client, key, session, answerPrefix + answer);
            }
            return;
        }

        BotDebug.warn("CAPTCHA_SOLVE_RETRY", session, "answer=" + (answer == null ? "null" : answer));
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

    private BufferedImage collectCaptchaImage(ClientWorld world, PlayerEntity player,
                                               BotSession session, SolveState state) {
        if (world == null || player == null) {
            return null;
        }
        hookMapColors();

        double rad = searchRadius;
        Box box;
        try {
            box = player.getBoundingBox().expand(rad);
        } catch (Throwable error) {
            return null;
        }

        List<Entity> entities;
        try {
            entities = new ArrayList<>(world.getOtherEntities(player, box, entity -> entity instanceof ItemFrameEntity));
        } catch (Throwable error) {
            return null;
        }

        List<FrameMap> frames = new ArrayList<>();
        for (Entity entity : entities) {
            if (!(entity instanceof ItemFrameEntity frame)) {
                continue;
            }
            ItemStack held = frame.getHeldItemStack();
            if (held == null || held.isEmpty() || !held.isOf(Items.FILLED_MAP)) {
                continue;
            }
            MapIdComponent mapId = held.get(DataComponentTypes.MAP_ID);
            if (mapId == null) {
                continue;
            }
            MapState mapState = world.getMapState(mapId);
            if (mapState == null || mapState.colors == null || mapState.colors.length < MAP_SIZE * MAP_SIZE) {
                continue;
            }
            byte[] pixels = mapState.colors.clone();
            if (countContentPixels(pixels) < MIN_MAP_CONTENT_PIXELS) {
                continue;
            }
            int rotation = Math.floorMod(frame.getRotation(), 4);
            frames.add(new FrameMap(frame.getPos(), pixels, frame.getFacing(), rotation));
            if (frames.size() >= MAX_MAPS_TOTAL) {
                break;
            }
        }

        if (frames.isEmpty()) {
            return null;
        }

        Vec3d playerPos = player.getPos();
        double playerYawRad = Math.toRadians(player.getYaw());
        double playerPitchRad = Math.toRadians(player.getPitch());
        Vec3d eyePos;
        try {
            eyePos = player.getEyePos();
        } catch (Throwable error) {
            eyePos = playerPos;
        }

        List<List<FrameMap>> walls = clusterWalls(frames, playerPos);
        if (walls.isEmpty()) {
            return null;
        }

        List<List<FrameMap>> eligible = new ArrayList<>();
        for (List<FrameMap> wall : walls) {
            if (wall.size() >= MIN_WALL_MAPS) {
                eligible.add(wall);
            }
        }
        if (eligible.isEmpty()) {
            eligible = walls;
        }

        int chosenWall = selectWallIndex(eligible, state == null ? null : state.avoidWall,
                playerPos, playerYawRad, eyePos, playerPitchRad, -1, -1);
        List<FrameMap> target = eligible.get(chosenWall);

        if (state != null) {
            state.chosenWall = chosenWall;
            state.chosenWallIdentity = wallIdentity(target);
            state.wallCount = eligible.size();
        }

        return renderWall(target, playerPos, playerYawRad);
    }

    private static List<List<FrameMap>> clusterWalls(List<FrameMap> frames, Vec3d playerPos) {
        List<FrameMap> byDist = new ArrayList<>(frames);
        byDist.sort(Comparator.comparingDouble(frame -> frame.pos.squaredDistanceTo(playerPos)));
        List<List<FrameMap>> walls = new ArrayList<>();

        for (FrameMap frame : byDist) {
            boolean placed = false;
            for (List<FrameMap> wall : walls) {
                for (FrameMap member : wall) {
                    if (!sameBillboardPlane(member, frame)) {
                        continue;
                    }
                    if (member.pos.distanceTo(frame.pos) < WALL_CLUSTER_DIST) {
                        wall.add(frame);
                        placed = true;
                        break;
                    }
                }
                if (placed) break;
            }
            if (!placed) {
                List<FrameMap> wall = new ArrayList<>();
                wall.add(frame);
                walls.add(wall);
            }
        }

        walls.sort(Comparator.comparingDouble(wall -> nearestDist(wall, playerPos)));
        List<List<FrameMap>> result = new ArrayList<>();
        for (List<FrameMap> wall : walls) {
            if (result.size() >= MAX_WALLS) break;
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

    private static double nearestDist(List<FrameMap> wall, Vec3d playerPos) {
        double best = Double.POSITIVE_INFINITY;
        for (FrameMap frame : wall) {
            best = Math.min(best, frame.pos.squaredDistanceTo(playerPos));
        }
        return best;
    }

    private int selectWallIndex(List<List<FrameMap>> walls, WallIdentity avoid,
                                Vec3d playerPos, double playerYawRad, Vec3d eyePos, double playerPitchRad,
                                int stickyIndex, int stickyCount) {
        if (walls.isEmpty()) return 0;
        if (walls.size() == 1) return 0;

        int bestIndex = 0;
        double bestScore = -1.0;

        Vec3d look = new Vec3d(-Math.sin(playerYawRad) * Math.cos(playerPitchRad),
                -Math.sin(playerPitchRad),
                Math.cos(playerYawRad) * Math.cos(playerPitchRad)).normalize();

        for (int i = 0; i < walls.size(); i++) {
            List<FrameMap> wall = walls.get(i);
            if (avoid != null && avoid.equals(wallIdentity(wall))) {
                continue;
            }
            Vec3d center = wallCenter(wall);
            Vec3d toCenter = center.subtract(eyePos).normalize();
            double dot = look.dotProduct(toCenter);
            if (dot > bestScore) {
                bestScore = dot;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static Vec3d wallCenter(List<FrameMap> wall) {
        if (wall.isEmpty()) return Vec3d.ZERO;
        double x = 0, y = 0, z = 0;
        for (FrameMap f : wall) {
            x += f.pos.x;
            y += f.pos.y;
            z += f.pos.z;
        }
        return new Vec3d(x / wall.size(), y / wall.size(), z / wall.size());
    }

    private static WallIdentity wallIdentity(List<FrameMap> wall) {
        if (wall == null || wall.isEmpty()) return new WallIdentity(Direction.NORTH, 0, 0, 0);
        FrameMap first = wall.get(0);
        Vec3d center = wallCenter(wall);
        return new WallIdentity(first.facing(), (int) Math.round(center.x), (int) Math.round(center.y), (int) Math.round(center.z));
    }

    private static final int OUTPUT_SCALE = 2;

    private static BufferedImage renderWall(List<FrameMap> wall, Vec3d playerPos, double playerYawRad) {
        if (wall == null || wall.isEmpty()) return null;
        Direction facing = wall.get(0).facing();
        if (facing == null) facing = Direction.NORTH;

        Map<FrameMap, int[]> gridCoords = new IdentityHashMap<>();
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;

        for (FrameMap frame : wall) {
            int u, v;
            v = (int) Math.round(frame.pos.y);
            u = switch (facing) {
                case SOUTH -> (int) Math.round(frame.pos.x);
                case NORTH -> (int) Math.round(-frame.pos.x);
                case EAST -> (int) Math.round(-frame.pos.z);
                case WEST -> (int) Math.round(frame.pos.z);
                default -> (int) Math.round(frame.pos.x);
            };

            gridCoords.put(frame, new int[]{u, v});
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        int cols = maxU - minU + 1;
        int rows = maxV - minV + 1;
        if (cols <= 0 || rows <= 0 || cols > 12 || rows > 12) {
            return null;
        }

        int tileSize = MAP_SIZE * OUTPUT_SCALE;
        BufferedImage stitched = new BufferedImage(cols * tileSize, rows * tileSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = stitched.createGraphics();

        try {
            for (FrameMap frame : wall) {
                int[] coords = gridCoords.get(frame);
                if (coords == null) continue;
                int col = coords[0] - minU;
                int row = maxV - coords[1];

                BufferedImage mapImg = renderMapImage(frame.pixels, frame.rotation);
                g.drawImage(mapImg, col * tileSize, row * tileSize, tileSize, tileSize, null);
            }
        } finally {
            g.dispose();
        }

        return stitched;
    }

    private static BufferedImage renderMapImage(byte[] pixels, int rotation) {
        BufferedImage mapImg = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < pixels.length && i < MAP_SIZE * MAP_SIZE; i++) {
            int val = pixels[i] & 0xFF;
            if (val == 0) continue;
            int color = getStaticMapColorRgb(val >> 2, val & 3);
            mapImg.setRGB(i % MAP_SIZE, i / MAP_SIZE, color);
        }

        if (rotation != 0) {
            BufferedImage rotated = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = rotated.createGraphics();
            g2.rotate(Math.toRadians(rotation * 90), MAP_SIZE / 2.0, MAP_SIZE / 2.0);
            g2.drawImage(mapImg, 0, 0, null);
            g2.dispose();
            return rotated;
        }
        return mapImg;
    }

    private static int getStaticMapColorRgb(int colorIndex, int shadeIndex) {
        try {
            MapColor mc = MapColor.get(colorIndex);
            if (mc != null) {
                return mc.getRenderColor(shadeIndex);
            }
        } catch (Throwable ignored) {
        }
        return 0xFF000000;
    }

    private static int countContentPixels(byte[] pixels) {
        if (pixels == null) return 0;
        int count = 0;
        for (byte b : pixels) {
            if ((b & 0xFF) > 3) {
                count++;
            }
        }
        return count;
    }

    private void feedback(String target, String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal(message), false);
                }
            });
        }
    }

    public record FrameMap(Vec3d pos, byte[] pixels, Direction facing, int rotation) {
        public FrameMap {
            Objects.requireNonNull(pos, "pos");
            pixels = pixels == null ? new byte[0] : Arrays.copyOf(pixels, pixels.length);
        }
    }

    public record WallIdentity(Direction facing, int x, int y, int z) {
    }

    public static class DetectionBox {
        public final float x1, y1, x2, y2, score;
        public final int cls;

        public DetectionBox(float x1, float y1, float x2, float y2, float score, int cls) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.score = score;
            this.cls = cls;
        }
    }

    private static final class SolveState {
        final long deadlineAt;
        final long armedAt;
        long nextAttemptAt;
        int attempts;
        boolean inFlight;
        int chosenWall;
        int wallCount;
        WallIdentity chosenWallIdentity;
        WallIdentity avoidWall;

        SolveState(long deadlineAt, long nextAttemptAt, long armedAt) {
            this.deadlineAt = deadlineAt;
            this.nextAttemptAt = nextAttemptAt;
            this.armedAt = armedAt;
        }
    }
}
