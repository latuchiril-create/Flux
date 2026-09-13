package dev.fuga.fluxvisuals;

import dev.fuga.fluxvisuals.config.ConfigManager;
import dev.fuga.fluxvisuals.baritone.BaritoneBridge;
import dev.fuga.fluxvisuals.gui.ClickGuiScreen;
import dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer;
import dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class FluxVisualsClient implements ClientModInitializer {
    public static final ModuleManager MODULE_MANAGER = new ModuleManager();
    public static final ConfigManager CONFIG_MANAGER = new ConfigManager(MODULE_MANAGER);
    public static final MultiBotManager MULTI_BOT_MANAGER = new MultiBotManager();

    private static boolean suppressOpenGuiUntilRightShiftRelease;
    private static boolean rightShiftWasDown;
    private static boolean suppressOpenModernGuiUntilBackslashRelease;
    private static boolean backslashWasDown;
    private static long lastFrameNs = -1L;

    @Override
    public void onInitializeClient() {
        BotDebug.start();
        CONFIG_MANAGER.load();
        MULTI_BOT_MANAGER.initialize();
        MinecraftClient startupClient = MinecraftClient.getInstance();
        LicenseManager.checkLicense(startupClient);
        if (startupClient.getSession() != null) {
            MODULE_MANAGER.getAutoBuy().setRentalPersistenceKey(startupClient.getSession().getUsername());
        }
        BotDebug.info("BARITONE_INIT", null, "available=" + BaritoneBridge.initialize());
        ClientTickEvents.END_CLIENT_TICK.register(FluxVisualsClient::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MODULE_MANAGER.getChinaHat().render(context);
            MODULE_MANAGER.getWings().render(context);
            MODULE_MANAGER.getTargetEsp().render(context);
            MODULE_MANAGER.getHitboxCustomizer().render(context);
            MODULE_MANAGER.getParticles().render(context);
            MODULE_MANAGER.getJumpCircles().render(context);
            MODULE_MANAGER.getTrails().render(context);
            MODULE_MANAGER.getBlockOverlay().render(context);
            MODULE_MANAGER.getItemRadius().render(context);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MULTI_BOT_MANAGER.shutdown();
            CONFIG_MANAGER.saveNow();
        });
    }

    public static void requestConfigSave() {
        if (CONFIG_MANAGER != null) {
            CONFIG_MANAGER.requestSave();
        }
    }

    /** Per-frame (FPS-based) module dispatch for human-like smoothing. Called from render thread. */
    public static void onFrame(MinecraftClient client) {
        if (client == null || client.player == null || MODULE_MANAGER == null) {
            lastFrameNs = -1L;
            return;
        }
        long now = System.nanoTime();
        if (lastFrameNs < 0L) {
            lastFrameNs = now;
            return;
        }
        float dt = (now - lastFrameNs) / 1_000_000_000.0F;
        lastFrameNs = now;
        if (dt <= 0.0F || dt > 0.25F) {
            return;
        }
        try {
            MODULE_MANAGER.onFrame(client, dt);
        } catch (Exception ignored) {
        }
    }

    public static void suppressOpenGuiUntilRightShiftRelease() {
        suppressOpenGuiUntilRightShiftRelease = true;
    }

    public static void suppressOpenModernGuiUntilBackslashRelease() {
        suppressOpenModernGuiUntilBackslashRelease = true;
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        if (client.currentScreen instanceof dev.fuga.fluxvisuals.gui.ClickGuiScreen || client.currentScreen instanceof dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen) {
            LicenseManager.checkLicense(client);
            if (!LicenseManager.isLicensed) {
                client.setScreen(null);
            }
        }

        MULTI_BOT_MANAGER.tick(client);
        MODULE_MANAGER.onTick(client);
        PremiumClickGuiRenderer.handleBinds(client);
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.handleBinds(client);
        CONFIG_MANAGER.tick();
        handleOpenGuiHotkey(client);
    }

    private static void handleOpenGuiHotkey(MinecraftClient client) {
        if (client.getWindow() == null) {
            rightShiftWasDown = false;
            backslashWasDown = false;
            return;
        }

        long window = client.getWindow().getHandle();
        int modernMenuKey = MODULE_MANAGER.getMenu() == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : MODULE_MANAGER.getMenu().getKeyBind();
        if (modernMenuKey == 0 || modernMenuKey == GLFW.GLFW_KEY_UNKNOWN) {
            modernMenuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
        }

        // Modern GUI: Right Shift by default (or configured menu key)
        boolean modernDown = GLFW.glfwGetKey(window, modernMenuKey) == GLFW.GLFW_PRESS;

        // Premium ClickGui on Backslash '\'
        int premiumMenuKey = GLFW.GLFW_KEY_BACKSLASH;
        boolean premiumDown = modernMenuKey != premiumMenuKey && GLFW.glfwGetKey(window, premiumMenuKey) == GLFW.GLFW_PRESS;

        // Modern GUI on Right Shift
        if (!modernDown) {
            rightShiftWasDown = false;
            suppressOpenModernGuiUntilBackslashRelease = false;
        } else if (!rightShiftWasDown && !suppressOpenModernGuiUntilBackslashRelease && client.currentScreen == null) {
            openModernGui(client);
            rightShiftWasDown = true;
        }

        // Premium ClickGui on Backslash '\'
        if (!premiumDown) {
            backslashWasDown = false;
            suppressOpenGuiUntilRightShiftRelease = false;
        } else if (!backslashWasDown && !suppressOpenGuiUntilRightShiftRelease && client.currentScreen == null) {
            if (LicenseManager.canOpenGui(client)) {
                client.setScreen(new ClickGuiScreen(MODULE_MANAGER));
            }
            backslashWasDown = true;
        }
    }

    /** Opens only the modern GUI when the asynchronous license check has succeeded. */
    public static boolean openModernGui(MinecraftClient client) {
        if (!LicenseManager.canOpenGui(client)) {
            return false;
        }
        client.setScreen(new ModernClickGuiScreen(MODULE_MANAGER));
        return true;
    }
}
