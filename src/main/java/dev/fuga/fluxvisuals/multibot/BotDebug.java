package dev.fuga.fluxvisuals.multibot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Always-on diagnostic log for bot lifecycle and packet routing. */
public final class BotDebug {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Path FILE = Path.of(
            System.getenv().getOrDefault("USERPROFILE", "."), "Desktop", "FugaClient-Bots-Debug.log"
    );
    private static final ConcurrentMap<String, Long> TRACE_LIMITS = new ConcurrentHashMap<>();
    private static final long NOISY_TRACE_INTERVAL_MILLIS = 500L;

    private BotDebug() {
    }

    public static synchronized void start() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE,
                    "==== FugaClient bot debug started " + LocalDateTime.now().format(TIME) + " ====\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

    public static void info(String event, BotSession session, String details) {
        write("INFO", event, session, details, null);
    }

    public static void warn(String event, BotSession session, String details) {
        write("WARN", event, session, details, null);
    }

    public static void error(String event, BotSession session, String details, Throwable error) {
        write("ERROR", event, session, details, error);
    }

    public static void trace(String event, BotSession session, String details) {
        write("TRACE", event, session, details, null);
    }

    private static volatile boolean chatDebugEnabled = false;

    public static boolean isChatDebugEnabled() {
        return chatDebugEnabled;
    }

    public static void setChatDebugEnabled(boolean enabled) {
        chatDebugEnabled = enabled;
    }

    private static synchronized void write(String level, String event, BotSession session,
                                            String details, Throwable error) {
        if ("TRACE".equals(level) && isNoisyEvent(event)) {
            if (isPerPacketEvent(event)) {
                return;
            }
            String key = event + "|" + (session == null ? "main" : session.getName());
            long now = System.currentTimeMillis();
            Long previous = TRACE_LIMITS.putIfAbsent(key, now);
            if (previous != null && now - previous < NOISY_TRACE_INTERVAL_MILLIS) {
                return;
            }
            TRACE_LIMITS.put(key, now);
        }
        try {
            StringBuilder line = new StringBuilder()
                    .append(LocalDateTime.now().format(TIME))
                    .append(" [").append(level).append("]")
                    .append(" [thread=").append(Thread.currentThread().getName()).append(']')
                    .append(" [event=").append(event).append(']');
            if (session != null) {
                line.append(" [bot=").append(session.getName())
                        .append(", state=").append(session.getState()).append(']');
            }
            if (details != null && !details.isBlank()) {
                line.append(" ").append(details.replace('\n', ' '));
            }
            if (error != null) {
                StringWriter stack = new StringWriter();
                error.printStackTrace(new PrintWriter(stack));
                line.append('\n').append(stack);
            }
            String msg = line.toString();
            System.out.println("[BotDebug] " + msg);
            if (chatDebugEnabled && ("WARN".equals(level) || "ERROR".equals(level) || "INFO".equals(level))) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.inGameHud != null && client.inGameHud.getChatHud() != null) {
                    String botName = session != null ? session.getName() : "System";
                    net.minecraft.text.Text chatMsg = net.minecraft.text.Text.literal("§7[§eBotDebug§7] §b" + botName + "§7: §f" + event + (details != null ? " " + details : ""));
                    client.execute(() -> client.inGameHud.getChatHud().addMessage(chatMsg));
                }
            }
            Files.writeString(FILE, msg + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

    private static boolean isNoisyEvent(String event) {
        return "PACKET_QUEUED".equals(event)
                || "PACKET_APPLY_BEGIN".equals(event)
                || "PACKET_APPLY_END".equals(event)
                || "LATENCY_REPLY_IMMEDIATE".equals(event)
                || "LATENCY_PACKET_IN".equals(event)
                || "LATENCY_PACKET_OUT".equals(event)
                || "BACKGROUND_PACKET_SKIPPED".equals(event)
                || "STALE_PACKET_DROPPED".equals(event)
                || "PLAY_PACKET_WAITING_FOR_JOIN".equals(event)
                || "UI_PACKET_REROUTE".equals(event)
                || "UI_PACKET_QUEUED_FROM_NETTY".equals(event)
                || "CLIENT_NOT_IN_PLAY".equals(event)
                || "BACKGROUND_INVENTORY_SKIPPED".equals(event)
                || "BACKGROUND_VISUAL_SKIPPED".equals(event);
    }

    private static boolean isPerPacketEvent(String event) {
        return "PACKET_QUEUED".equals(event)
                || "PACKET_APPLY_BEGIN".equals(event)
                || "PACKET_APPLY_END".equals(event)
                || "BACKGROUND_PACKET_SKIPPED".equals(event)
                || "STALE_PACKET_DROPPED".equals(event)
                || "PLAY_PACKET_WAITING_FOR_JOIN".equals(event)
                || "UI_PACKET_REROUTE".equals(event)
                || "UI_PACKET_QUEUED_FROM_NETTY".equals(event)
                || "BACKGROUND_INVENTORY_SKIPPED".equals(event)
                || "BACKGROUND_VISUAL_SKIPPED".equals(event);
    }
}
