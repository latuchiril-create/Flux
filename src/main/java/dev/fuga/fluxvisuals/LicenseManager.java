package dev.fuga.fluxvisuals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fuga.fluxvisuals.gui.ClickGuiScreen;
import dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

/** Performs continuous remote license checks without blocking Minecraft's client thread. */
public final class LicenseManager {
    private static final String AUTH_URL = "https://client-auth-8ode.onrender.com/api/auth";
    private static final AtomicBoolean IS_CHECKING = new AtomicBoolean(false);
    private static volatile long lastCheckTime = 0;
    private static final long CHECK_COOLDOWN_MS = 5000; // Re-verify every 5s or on GUI attempt

    public static final boolean FREE_MODE;

    static {
        boolean free = Boolean.getBoolean("fuga.free") || Boolean.getBoolean("fluxvisuals.free");
        if (!free) {
            try (InputStream stream = LicenseManager.class.getResourceAsStream("/assets/fluxvisuals/free_mode.flag")) {
                if (stream != null) {
                    free = true;
                }
            } catch (Exception ignored) {
            }
        }
        FREE_MODE = free;
    }

    public static volatile boolean isLicensed = FREE_MODE;

    private static volatile long lastDenyMessageTime = 0;
    private static final long DENY_MESSAGE_COOLDOWN_MS = 3000;

    private LicenseManager() {
    }

    public static boolean canOpenGui(MinecraftClient client) {
        if (FREE_MODE) {
            isLicensed = true;
            return true;
        }
        checkLicenseForce(client, false);
        if (!isLicensed) {
            long now = System.currentTimeMillis();
            if (now - lastDenyMessageTime >= DENY_MESSAGE_COOLDOWN_MS) {
                lastDenyMessageTime = now;
                if (client != null && client.player != null) {
                    client.player.sendMessage(Text.literal("§c[License] §fВаша лицензия не активирована."), false);
                }
            }
            return false;
        }
        return true;
    }

    public static void checkLicense(MinecraftClient client) {
        if (FREE_MODE) {
            isLicensed = true;
            return;
        }
        checkLicenseForce(client, false);
    }

    public static void checkLicenseForce(MinecraftClient client, boolean ignoreCooldown) {
        if (FREE_MODE) {
            isLicensed = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (!ignoreCooldown && isLicensed && (now - lastCheckTime < CHECK_COOLDOWN_MS)) {
            return;
        }
        if (!IS_CHECKING.compareAndSet(false, true)) {
            return;
        }
        lastCheckTime = now;

        String launcherUser = System.getProperty("fluxclient.username");
        String username = "unknown";
        if (launcherUser != null && !launcherUser.trim().isEmpty()) {
            username = launcherUser.trim();
        } else if (client != null && client.getSession() != null) {
            String s = client.getSession().getUsername();
            if (s != null && !s.trim().isEmpty()) {
                username = s.trim();
            }
        }

        final String targetUser = username;
        Thread thread = new Thread(() -> performCheck(client, targetUser), "fluxvisuals-license-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void performCheck(MinecraftClient client, String username) {
        HttpURLConnection connection = null;
        boolean wasLicensedBefore = isLicensed;
        try {
            String launcherHwid = System.getProperty("fluxclient.hwid");
            String hwid = (launcherHwid != null && !launcherHwid.trim().isEmpty())
                    ? launcherHwid.trim()
                    : deviceId();

            String customUrl = System.getProperty("fluxclient.serverUrl");
            String targetUrl = (customUrl != null && !customUrl.trim().isEmpty())
                    ? customUrl.trim()
                    : AUTH_URL;
            if (!targetUrl.endsWith("/api/auth")) {
                targetUrl = targetUrl.replaceAll("/+$", "") + "/api/auth";
            }

            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("hwid", hwid);

            connection = (HttpURLConnection) URI.create(targetUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (var output = connection.getOutputStream()) {
                output.write(payload);
            }

            try (InputStream input = connection.getResponseCode() >= 400
                    ? connection.getErrorStream() : connection.getInputStream()) {
                if (input == null) {
                    onLicenseRevoked(client, wasLicensedBefore, "Empty server response.");
                    return;
                }
                String respStr = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject response = JsonParser.parseString(respStr).getAsJsonObject();
                String status = response.has("status") && response.get("status").isJsonPrimitive()
                        ? response.get("status").getAsString() : "";
                if ("ok".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status)) {
                    isLicensed = true;
                } else if ("banned".equalsIgnoreCase(status)) {
                    System.err.println("[LicenseManager] Account is banned. Terminating client.");
                    System.exit(0);
                } else {
                    onLicenseRevoked(client, wasLicensedBefore, "Status is not 'ok': " + status);
                }
            }
        } catch (IOException | RuntimeException exception) {
            String launcherStatus = System.getProperty("fluxclient.status");
            if (wasLicensedBefore || "ACTIVE".equalsIgnoreCase(launcherStatus)) {
                // Keep session alive on momentary network hiccups
                isLicensed = true;
            } else {
                onLicenseRevoked(client, wasLicensedBefore, "Network or server error.");
            }
        } finally {
            IS_CHECKING.set(false);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void onLicenseRevoked(MinecraftClient client, boolean wasLicensedBefore, String reason) {
        isLicensed = false;
        if (client != null) {
            client.execute(() -> {
                if (client.currentScreen instanceof ClickGuiScreen
                        || client.currentScreen instanceof ModernClickGuiScreen
                        || client.currentScreen instanceof dev.fuga.fluxvisuals.gui.modern.ModernGui2Screen) {
                    client.setScreen(null);
                }
                if (wasLicensedBefore && client.player != null) {
                    client.player.sendMessage(Text.literal("§c[License] §fВаша лицензия была аннулирована."), false);
                }
            });
        }
    }

    private static String deviceId() {
        String source = System.getProperty("os.name", "") + "|"
                + System.getProperty("os.arch", "") + "|"
                + System.getProperty("user.name", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
