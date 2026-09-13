package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import dev.fuga.fluxvisuals.mixin.BossBarHudAccessor;
import dev.fuga.fluxvisuals.mixin.InGameHudAccessor;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Watermark extends Module {
    private static final Identifier ICON = Identifier.of("fluxvisuals", "hud/icon.png");
    private static final Identifier CONTROL_BACK = Identifier.of("fluxvisuals", "hud/back.png");
    private static final Identifier CONTROL_FRONT = Identifier.of("fluxvisuals", "hud/front.png");
    private static final Identifier CONTROL_PAUSE = Identifier.of("fluxvisuals", "hud/pause.png");
    private static final Identifier CONTROL_PLAY = Identifier.of("fluxvisuals", "hud/play.png");
    private static final RenderPipeline LIQUID_GLASS_PIPELINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.of("fluxvisuals", "pipeline/watermark_liquidglass"))
            .withVertexShader(Identifier.of("rockstar", "core/liquidglass/vertex"))
            .withFragmentShader(Identifier.of("rockstar", "core/liquidglass/fragment"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static SimpleFramebuffer liquidGlassSnapshot;
    private static final int ICON_TEXTURE_SIZE = 60;
    private static final int CONTROL_TEXTURE_SIZE = 64;
    private static final float ICON_CROP_U = 9.0F;
    private static final float ICON_CROP_V = 7.0F;
    private static final float ICON_CROP_SIZE = 40.0F;
    private static final float HEIGHT = 24.0F;
    private static final float ICON_SIZE = 22.5F;
    private static final float MUSIC_HEIGHT = 30.0F;
    private static final float MUSIC_COVER_SIZE = 20.5F;
    private static final float MUSIC_LAYOUT_GAP = 7.0F;
    private static final float MUSIC_LAYOUT_PAD = 6.0F;
    private static final float MUSIC_WATERMARK_WIDTH = 200.0F;
    private static final float MUSIC_MENU_WIDTH = 284.0F;
    private static final float MUSIC_MENU_HEIGHT = 146.0F;
    private static final float MUSIC_MENU_COVER = 60.0F;
    private static final float MUSIC_MENU_PAD = 10.0F;
    private static final float MUSIC_MENU_GAP = 8.0F;
    private static final float MUSIC_MENU_BAR_HEIGHT = 5.0F;
    private static final float MUSIC_MENU_BUTTON = 20.0F;
    private static final float MUSIC_COMPACT_CONTROL = 18.0F;
    private static final float MUSIC_COMPACT_CONTROL_GAP = 5.0F;
    private static final float MUSIC_COMPACT_TIMER_WIDTH = 2.0F;
    private static final float MUSIC_COMPACT_TIMER_HEIGHT = 20.0F;
    private static final float ICON_LEFT_PAD = 0.0F;
    private static final float TEXT_LEFT_GAP = 4.0F;
    private static final float TEXT_GAP = 4.5F;
    private static final float POD_EXTRA_WIDTH = 7.5F;
    private static final float SHELL_INSET = 0.0F;
    private static final float CONTEXT_MENU_WIDTH = 198.0F;
    private static final float CONTEXT_MENU_HEIGHT = 90.0F;
    private static final float CONTEXT_MENU_PAD = 9.0F;
    private static final float CONTEXT_MENU_BUTTON_HEIGHT = 20.0F;
    private static final float TOP_Y = 7.0F;
    private static final float BOSSBAR_Y = 25.0F;
    private static final long MODULE_TOAST_DURATION_MS = 2400L;
    private static final int TEXT_OVERSAMPLE = 2;
    private static final int AA_SAMPLES = 3;
    private static final int MAX_DYNAMIC_TEXTURES = 96;
    private static final Map<RoundKey, Identifier> ROUND_TEXTURES = lruTextureMap();
    private static final Map<TextKey, TextTexture> TEXT_TEXTURES = new java.util.LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextKey, TextTexture> eldest) {
            if (size() > MAX_DYNAMIC_TEXTURES) {
                destroyTexture(eldest.getValue().id());
                return true;
            }
            return false;
        }
    };
    private static final Map<String, CoverTexture> COVER_TEXTURES = new java.util.LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CoverTexture> eldest) {
            if (size() > 16) {
                destroyTexture(eldest.getValue().id());
                destroyTexture(eldest.getValue().compactId());
                return true;
            }
            return false;
        }
    };
    private static final Map<FontRole, Font> FONTS = new HashMap<>();

    private static <K> Map<K, Identifier> lruTextureMap() {
        return new java.util.LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Identifier> eldest) {
                if (size() > MAX_DYNAMIC_TEXTURES) {
                    destroyTexture(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    private static void destroyTexture(Identifier id) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null && id != null) {
                client.getTextureManager().destroyTexture(id);
            }
        } catch (Exception ignored) {
        }
    }

    private float displayedFps;
    private float displayedPing;
    private int targetFps;
    private int targetPing;
    private int sampleTicks;
    private boolean musicEnabled = true;
    private boolean notificationsEnabled = true;
    private boolean contextMenuOpen;
    private boolean musicMenuOpen;
    private boolean draggingSeek;
    private boolean draggingContextMenu;
    private boolean draggingMusicMenu;
    private float musicBlend;
    private float lastX;
    private float lastY;
    private float lastWidth;
    private float lastHeight;
    private float contextMenuX;
    private float contextMenuY;
    private float contextMenuWidth = CONTEXT_MENU_WIDTH;
    private float contextMenuHeight = CONTEXT_MENU_HEIGHT;
    private float menuX;
    private float menuY;
    private float menuWidth = MUSIC_MENU_WIDTH;
    private float menuHeight = MUSIC_MENU_HEIGHT;
    private float contextMenuAnim;
    private float musicMenuAnim;
    private float contextMenuOriginX;
    private float contextMenuOriginY;
    private float musicMenuOriginX;
    private float musicMenuOriginY;
    private float seekPreviewFraction;
    private float dragOffsetX;
    private float dragOffsetY;
    private float moduleToastBlend;
    private long lastSeekApplyAt;
    private long moduleToastEndsAt;
    public enum WatermarkStyle {
        DEFAULT("Default", Identifier.of("fluxvisuals", "hud/icon.png"));

        private final String name;
        private final Identifier icon;

        WatermarkStyle(String name, Identifier icon) {
            this.name = name;
            this.icon = icon;
        }

        public String getName() { return name; }
        public Identifier getIcon() { return icon; }

        public static WatermarkStyle fromName(String name) {
            for (WatermarkStyle style : values()) {
                if (style.name.equalsIgnoreCase(name) || style.name().equalsIgnoreCase(name)) {
                    return style;
                }
            }
            return DEFAULT;
        }
    }

    private WatermarkStyle watermarkStyle = WatermarkStyle.DEFAULT;

    public WatermarkStyle getWatermarkStyle() { return watermarkStyle; }
    public void setWatermarkStyle(WatermarkStyle style) {
        this.watermarkStyle = WatermarkStyle.DEFAULT;
        FluxVisualsClient.requestConfigSave();
    }
    public String getWatermarkStyleName() { return watermarkStyle.getName(); }
    public void setWatermarkStyleByName(String name) { setWatermarkStyle(WatermarkStyle.fromName(name)); }

    private boolean moduleToastEnabled;
    private String moduleToastName = "";
    private MusicBridge.MusicSnapshot lastMusicSnapshot = MusicBridge.MusicSnapshot.empty();

    public Watermark() {
        super("Watermark", "Centered FluxVisuals watermark with ping and FPS.", ModuleCategory.HUD);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null) {
            return;
        }

        sampleTicks++;
        targetFps = Math.max(0, client.getCurrentFps());
        if (sampleTicks >= 8) {
            sampleTicks = 0;
            targetPing = ping(client);
            if (displayedFps <= 0.0F) {
                displayedFps = targetFps;
            }
            if (displayedPing <= 0.0F) {
                displayedPing = targetPing;
            }
        }

        displayedFps += (targetFps - displayedFps) * 0.10F;
        displayedPing += (targetPing - displayedPing) * 0.16F;
    }

    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || context == null || client == null || client.player == null) {
            return;
        }
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            return;
        }

        int fps = Math.max(0, Math.round(displayedFps <= 0.0F ? targetFps : displayedFps));
        int ping = Math.max(0, Math.round(displayedPing <= 0.0F ? targetPing : displayedPing));
        MusicBridge.MusicSnapshot currentMusic = MusicBridge.snapshot();
        if (currentMusic.present()) {
            lastMusicSnapshot = currentMusic;
        }
        long now = System.currentTimeMillis();
        MusicBridge.MusicSnapshot visualMusic = lastMusicSnapshot;
        boolean musicActive = musicEnabled && currentMusic.present();
        boolean moduleToastActive = now < moduleToastEndsAt && !moduleToastName.isBlank();
        musicBlend = approach(musicBlend, musicActive ? 1.0F : 0.0F, 0.16F);
        moduleToastBlend = approach(moduleToastBlend, moduleToastActive ? 1.0F : 0.0F, moduleToastActive ? 0.22F : 0.14F);
        if (!musicActive && musicBlend <= 0.02F) {
            lastMusicSnapshot = MusicBridge.MusicSnapshot.empty();
            visualMusic = lastMusicSnapshot;
        }
        contextMenuAnim = approach(contextMenuAnim, contextMenuOpen ? 1.0F : 0.0F, 0.18F);
        musicMenuAnim = approach(musicMenuAnim, musicMenuOpen ? 1.0F : 0.0F, 0.18F);

        float standardWidth = standardWidth(fps, ping);
        float musicWidth = musicWidth(visualMusic, client);
        float baseWidth = lerp(standardWidth, musicWidth, musicBlend);
        float width = lerp(baseWidth, moduleToastWidth(), moduleToastBlend);
        float height = lerp(HEIGHT, MUSIC_HEIGHT, musicBlend);
        float x = (context.getScaledWindowWidth() - width) * 0.5F;
        float y = calculateWatermarkY(client);

        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;

        drawWatermarkShell(context, x, y, width, height, 1.0F);

        float standardAlpha = clamp((1.0F - musicBlend) * (1.0F - moduleToastBlend), 0.0F, 1.0F);
        float musicAlpha = clamp(musicBlend * (1.0F - moduleToastBlend), 0.0F, 1.0F);
        float toastAlpha = clamp(moduleToastBlend, 0.0F, 1.0F);
        if (standardAlpha > 0.01F) {
            renderStandardWatermark(context, x, y, width, height, fps, ping, standardAlpha);
        }
        if (musicAlpha > 0.01F && visualMusic.present()) {
            renderMusicWatermark(context, x, y, width, height, visualMusic, musicAlpha, client);
        }
        if (toastAlpha > 0.01F) {
            renderModuleToast(context, x, y, width, height, toastAlpha);
        }

        syncFloatingMenus(client);
        if (contextMenuOpen) {
            renderContextMenu(context, client, currentMusic);
        }
    }

    public boolean handleChatRightClick(MinecraftClient client, double mouseX, double mouseY) {
        if (!isEnabled() || client == null || !(client.currentScreen instanceof ChatScreen)) {
            closeMusicMenu();
            return false;
        }

        double scaledX = mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double scaledY = mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean inside = scaledX >= lastX && scaledX <= lastX + lastWidth && scaledY >= lastY && scaledY <= lastY + lastHeight;
        if (inside) {
            if (contextMenuOpen) {
                closeMusicMenu();
            } else {
                openContextMenu(client, (float) scaledX, (float) scaledY);
            }
            return true;
        }

        if (contextMenuOpen) {
            closeMusicMenu();
            return true;
        }
        return false;
    }

    public boolean handleMusicMouseButton(MinecraftClient client, double mouseX, double mouseY, int button, int action) {
        if (!isEnabled() || client == null || !(client.currentScreen instanceof ChatScreen)) {
            if (action == GLFW.GLFW_RELEASE) {
                draggingSeek = false;
                draggingMusicMenu = false;
                draggingContextMenu = false;
            }
            return false;
        }

        double scaledX = mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double scaledY = mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

        if (contextMenuOpen) {
            return handleContextMouseButton(client, mouseX, mouseY, button, action);
        }

        if (!musicMenuOpen) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS || !musicEnabled) {
                if (action == GLFW.GLFW_RELEASE) {
                    draggingSeek = false;
                    draggingMusicMenu = false;
                }
                return false;
            }

            MusicBridge.MusicSnapshot compactSnapshot = lastMusicSnapshot.present() ? lastMusicSnapshot : MusicBridge.snapshot();
            if (!compactSnapshot.present()
                    || scaledX < lastX || scaledX > lastX + lastWidth
                    || scaledY < lastY || scaledY > lastY + lastHeight) {
                return false;
            }

            if (hitCompactControl((float) scaledX, (float) scaledY, compactSnapshot, 0)) {
                MusicBridge.previous();
                return true;
            }
            if (hitCompactControl((float) scaledX, (float) scaledY, compactSnapshot, 1)) {
                MusicBridge.togglePlay();
                return true;
            }
            if (hitCompactControl((float) scaledX, (float) scaledY, compactSnapshot, 2)) {
                MusicBridge.next();
                return true;
            }
            return false;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (action == GLFW.GLFW_RELEASE) {
            if (draggingMusicMenu) {
                draggingMusicMenu = false;
                return true;
            }
            if (draggingSeek) {
                seekPreviewFraction = sliderFraction(client, scaledX);
                if (MusicBridge.canSeek()) {
                    MusicBridge.seekFraction(seekPreviewFraction);
                }
                draggingSeek = false;
                lastSeekApplyAt = 0L;
                return true;
            }
            return false;
        }

        if (action != GLFW.GLFW_PRESS) {
            return false;
        }

        if (!insideMenu((float) scaledX, (float) scaledY)) {
            closeMusicMenu();
            return true;
        }

        if (hitBackButton((float) scaledX, (float) scaledY)) {
            openContextMenu(client, (float) scaledX, (float) scaledY);
            return true;
        }
        if (hitToggle((float) scaledX, (float) scaledY)) {
            setMusicEnabled(!musicEnabled);
            return true;
        }
        if (hitMusicHeader((float) scaledX, (float) scaledY)) {
            musicMenuOriginX = menuX;
            musicMenuOriginY = menuY;
            musicMenuAnim = 1.0F;
            draggingMusicMenu = true;
            dragOffsetX = (float) scaledX - currentMusicMenuX();
            dragOffsetY = (float) scaledY - currentMusicMenuY();
            return true;
        }
        if (hitControl((float) scaledX, (float) scaledY, 0)) {
            MusicBridge.previous();
            return true;
        }
        if (hitControl((float) scaledX, (float) scaledY, 1)) {
            MusicBridge.togglePlay();
            return true;
        }
        if (hitControl((float) scaledX, (float) scaledY, 2)) {
            MusicBridge.next();
            return true;
        }
        if (MusicBridge.canSeek() && hitSlider((float) scaledX, (float) scaledY)) {
            seekPreviewFraction = sliderFraction(client, scaledX);
            draggingSeek = true;
            lastSeekApplyAt = 0L;
            MusicBridge.seekFraction(seekPreviewFraction);
            return true;
        }

        return true;
    }

    private boolean handleContextMouseButton(MinecraftClient client, double mouseX, double mouseY, int button, int action) {
        double scaledX = mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double scaledY = mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        if (action == GLFW.GLFW_RELEASE) {
            if (draggingContextMenu) {
                draggingContextMenu = false;
            }
            return true;
        }

        if (action != GLFW.GLFW_PRESS) {
            return false;
        }

        if (!insideContextMenu((float) scaledX, (float) scaledY)) {
            closeMusicMenu();
            return true;
        }

        if (hitContextToggle((float) scaledX, (float) scaledY) || hitContextMusicRow((float) scaledX, (float) scaledY)) {
            setMusicEnabled(!musicEnabled);
            return true;
        }
        if (hitContextNotifications((float) scaledX, (float) scaledY)) {
            setNotificationsEnabled(!notificationsEnabled);
            return true;
        }

        if (hitContextHeader((float) scaledX, (float) scaledY)) {
            contextMenuOriginX = contextMenuX;
            contextMenuOriginY = contextMenuY;
            contextMenuAnim = 1.0F;
            draggingContextMenu = true;
            dragOffsetX = (float) scaledX - currentContextMenuX();
            dragOffsetY = (float) scaledY - currentContextMenuY();
            return true;
        }

        return true;
    }

    private static int ping(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }

    private static boolean hasBossBar(MinecraftClient client) {
        if (client.inGameHud instanceof InGameHudAccessor hudAccessor) {
            try {
                return !((BossBarHudAccessor) hudAccessor.fluxvisuals$getBossBarHud()).fluxvisuals$getBossBars().isEmpty();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private void renderContextMenu(DrawContext context, MinecraftClient client, MusicBridge.MusicSnapshot snapshot) {
        if (!contextMenuOpen || context == null || client == null || !(client.currentScreen instanceof ChatScreen)) {
            if (contextMenuOpen && client != null && !(client.currentScreen instanceof ChatScreen)) {
                closeMusicMenu();
            }
            return;
        }

        contextMenuWidth = CONTEXT_MENU_WIDTH;
        contextMenuHeight = 92.0F;
        float contextX = currentContextMenuX();
        float contextY = currentContextMenuY();

        rounded(context, contextX, contextY, contextMenuWidth, contextMenuHeight, 13.0F, 0xF0090A0E);
        rounded(context, contextX + 1.0F, contextY + 1.0F, contextMenuWidth - 2.0F, contextMenuHeight - 2.0F, 12.0F, 0xEA121318);
        rounded(context, contextX + 6.0F, contextY + 6.0F, contextMenuWidth - 12.0F, 21.0F, 9.0F, 0x1F8F5CFF);

        TextTexture header = textTexture("Watermark", 8, FontRole.DISPLAY);
        drawText(context, header, contextX + 12.0F, contextY + 7.0F, 0xFFF6F2FF);
        drawContextToggle(context);

        float panelX = contextX + CONTEXT_MENU_PAD;
        float panelY = contextY + 31.0F;
        float panelW = contextMenuWidth - CONTEXT_MENU_PAD * 2.0F;
        float panelH = 22.0F;
        rounded(context, panelX, panelY, panelW, panelH, 9.0F, 0x1AFFFFFF);
        rounded(context, panelX + 1.0F, panelY + 1.0F, panelW - 2.0F, panelH - 2.0F, 8.0F, 0x18191F26);

        TextTexture label = textTexture("Music bar", 7, FontRole.DISPLAY_MEDIUM);
        TextTexture status = textTexture(musicEnabled ? "Включен" : "Выключен", 6, FontRole.BODY);
        drawText(context, label, panelX + 9.0F, panelY + 4.0F, 0xFFF1ECFF);
        drawText(context, status, panelX + panelW - status.width - 10.0F, panelY + 5.0F, musicEnabled ? 0xFFCFC7E0 : 0xFF9A94A8);

        float notifyY = panelY + panelH + 7.0F;
        rounded(context, panelX, notifyY, panelW, panelH, 9.0F, 0x1AFFFFFF);
        rounded(context, panelX + 1.0F, notifyY + 1.0F, panelW - 2.0F, panelH - 2.0F, 8.0F, 0x18191F26);
        TextTexture notifyLabel = textTexture("Уведомления", 7, FontRole.DISPLAY_MEDIUM);
        TextTexture notifyStatus = textTexture(notificationsEnabled ? "Включен" : "Выключен", 6, FontRole.BODY);
        drawText(context, notifyLabel, panelX + 9.0F, notifyY + 4.0F, 0xFFF1ECFF);
        drawText(context, notifyStatus, panelX + panelW - notifyStatus.width - 10.0F, notifyY + 5.0F,
                notificationsEnabled ? 0xFFCFC7E0 : 0xFF9A94A8);
    }

    private void renderMusicMenu(DrawContext context, MinecraftClient client, MusicBridge.MusicSnapshot snapshot) {
        if (!musicMenuOpen || context == null || client == null || !(client.currentScreen instanceof ChatScreen)) {
            if (musicMenuOpen && client != null && !(client.currentScreen instanceof ChatScreen)) {
                closeMusicMenu();
            }
            return;
        }

        MusicBridge.MusicSnapshot menuSnapshot = snapshot != null && snapshot.present() ? snapshot : lastMusicSnapshot;
        if (draggingSeek) {
            syncSeekDrag(client, menuSnapshot);
        }
        float drawMenuX = currentMusicMenuX();
        float drawMenuY = currentMusicMenuY();

        rounded(context, drawMenuX, drawMenuY, menuWidth, menuHeight, 15.0F, 0xF0090A0E);
        rounded(context, drawMenuX + 1.0F, drawMenuY + 1.0F, menuWidth - 2.0F, menuHeight - 2.0F, 14.0F, 0xEA121318);
        rounded(context, drawMenuX + 6.0F, drawMenuY + 6.0F, menuWidth - 12.0F, 22.0F, 10.0F, 0x1F8F5CFF);

        drawControlIconButton(context, drawMenuX + 8.0F, drawMenuY + 8.0F, 16.0F, CONTROL_BACK, 0xFFF7F2FF, 0.78F);
        TextTexture header = textTexture("Music", 8, FontRole.DISPLAY);
        drawText(context, header, drawMenuX + 30.0F, drawMenuY + 8.0F, 0xFFF6F2FF);
        drawMusicToggle(context, drawMenuX, drawMenuY);

        float coverX = drawMenuX + MUSIC_MENU_PAD;
        float coverY = drawMenuY + 34.0F;
        drawMusicCover(context, menuSnapshot, coverX, coverY, MUSIC_MENU_COVER, MUSIC_MENU_COVER, 14.0F, 1.0F, false);

        float textX = coverX + MUSIC_MENU_COVER + 10.0F;
        float textWidth = menuWidth - (textX - drawMenuX) - MUSIC_MENU_PAD - 6.0F;
        String titleValue = menuSnapshot.title().isEmpty() ? "No music" : menuSnapshot.title();
        String artistValue = trimToWidth(menuSnapshot.artist().isEmpty() ? menuSnapshot.sourceApp() : menuSnapshot.artist(), 8, FontRole.BODY, textWidth);
        String statusValue = trimToWidth(menuSnapshot.sourceApp() + (menuSnapshot.playing() ? "  Playing" : "  Paused"), 7, FontRole.BODY, textWidth);
        TextTexture songTitle = textTexture(titleValue, 10, FontRole.DISPLAY);
        TextTexture artist = textTexture(artistValue.isEmpty() ? menuSnapshot.sourceApp() : artistValue, 8, FontRole.BODY);
        TextTexture status = textTexture(statusValue.trim(), 7, FontRole.BODY);
        drawScrollingText(context, songTitle, textX, coverY + 4.0F, textWidth, 0xFFFFFFFF, menuSnapshot.metadataKey());
        drawText(context, artist, textX, coverY + 24.0F, 0xFFDCD4E7);
        drawText(context, status, textX, coverY + 38.0F, 0xFF9B92A8);

        drawMenuProgress(context, menuSnapshot);
        drawMenuControls(context, menuSnapshot);
    }

    private void renderStandardWatermark(DrawContext context, float x, float y, float width, float height, int fps, int ping, float alpha) {
        TextTexture brand = textTexture("FluxVisuals", 10, FontRole.DISPLAY);
        TextTexture separator = textTexture("\u2022", 8, FontRole.DISPLAY_MEDIUM);
        TextTexture pingText = textTexture(ping + "ms", 8, FontRole.BODY);
        TextTexture fpsText = textTexture(fps + "fps", 8, FontRole.BODY);
        float pingSlot = slotWidth("999ms", 8, FontRole.BODY);
        float fpsSlot = slotWidth("999fps", 8, FontRole.BODY);

        float podX = x + ICON_LEFT_PAD;
        float podY = y + SHELL_INSET;
        float podW = standardPodWidth();
        float podH = height - SHELL_INSET * 2.0F;
        float iconX = podX + (podW - ICON_SIZE) * 0.5F;
        float iconY = y + (height - ICON_SIZE) * 0.5F;
        drawIconGlow(context, iconX, iconY, ICON_SIZE, 0.98F * alpha);

        float tx = podX + podW + TEXT_LEFT_GAP;
        drawText(context, brand, tx, y + (height - brand.height) * 0.5F - 0.5F, alphaColor(0xFFFFFFFF, alpha));
        tx += brand.width + TEXT_GAP;

        drawText(context, separator, tx, y + (height - separator.height) * 0.5F - 0.5F, alphaColor(0xFFAF7CFF, alpha * 0.92F));
        tx += separator.width + TEXT_GAP;

        drawText(context, pingText, tx + pingSlot - pingText.width, y + (height - pingText.height) * 0.5F - 0.5F, alphaColor(0xFFC8C3CE, alpha));
        tx += pingSlot + TEXT_GAP;

        drawText(context, separator, tx, y + (height - separator.height) * 0.5F - 0.5F, alphaColor(0xFFAF7CFF, alpha * 0.92F));
        tx += separator.width + TEXT_GAP;

        drawText(context, fpsText, tx + fpsSlot - fpsText.width, y + (height - fpsText.height) * 0.5F - 0.5F, alphaColor(0xFFC8C3CE, alpha));
    }

    private void renderModuleToast(DrawContext context, float x, float y, float width, float height, float alpha) {
        String moduleName = moduleToastName == null || moduleToastName.isBlank() ? "Module" : moduleToastName;
        String name = "Модуль " + moduleName;
        String statusValue = moduleToastEnabled ? "Включен" : "Выключен";
        TextTexture title = textTexture(name, 10, FontRole.DISPLAY);
        TextTexture status = textTexture(statusValue, 6, FontRole.BODY);

        float podX = x + ICON_LEFT_PAD;
        float podW = standardPodWidth();
        float iconX = podX + (podW - ICON_SIZE) * 0.5F;
        float iconY = y + (height - ICON_SIZE) * 0.5F;
        drawIconGlow(context, iconX, iconY, ICON_SIZE, 0.98F * alpha);

        float textX = podX + podW + TEXT_LEFT_GAP;
        float badgeH = Math.min(15.0F, height - 6.0F);
        float badgeW = status.width + 12.0F;
        float badgeX = x + width - badgeW - 4.0F;
        float textMaxWidth = Math.max(34.0F, badgeX - textX - 6.0F);
        drawScrollingText(context, title, textX, y + (height - title.height) * 0.5F - 0.5F, textMaxWidth,
                alphaColor(0xFFFFFFFF, alpha), moduleName + ":" + moduleToastEnabled);

        float badgeY = y + (height - badgeH) * 0.5F;
        int badgeOuter = moduleToastEnabled ? 0xFF2DBE74 : 0xFF6E647D;
        int badgeInner = moduleToastEnabled ? 0xFF1E9A5C : 0xFF524B5C;
        rounded(context, badgeX, badgeY, badgeW, badgeH, badgeH * 0.5F, alphaColor(badgeOuter, alpha * 0.92F));
        rounded(context, badgeX + 1.0F, badgeY + 1.0F, badgeW - 2.0F, badgeH - 2.0F,
                Math.max(0.0F, badgeH * 0.5F - 1.0F), alphaColor(badgeInner, alpha * 0.94F));
        drawText(context, status, badgeX + (badgeW - status.width) * 0.5F, badgeY + (badgeH - status.height) * 0.5F - 0.5F,
                alphaColor(0xFFF6FFFB, alpha));
    }

    private void renderMusicWatermark(DrawContext context, float x, float y, float width, float height, MusicBridge.MusicSnapshot snapshot, float alpha, MinecraftClient client) {
        if (snapshot == null || !snapshot.present()) {
            return;
        }

        long now = System.currentTimeMillis();
        float barsWidth = waveBlockWidth(snapshot);
        float controlWidth = compactControlsWidth();
        float coverPodX = x + ICON_LEFT_PAD;
        float coverPodY = y + SHELL_INSET;
        float coverPodW = musicPodWidth();
        float coverPodH = height - SHELL_INSET * 2.0F;
        float coverX = coverPodX + (coverPodW - MUSIC_COVER_SIZE) * 0.5F;
        float coverY = y + (height - MUSIC_COVER_SIZE) * 0.5F;
        float timerX = coverPodX + coverPodW + 4.0F;
        float timerY = y + (height - MUSIC_COMPACT_TIMER_HEIGHT) * 0.5F;
        float barX = x + width - MUSIC_LAYOUT_PAD - barsWidth;
        float controlsX = barX - 10.0F - controlWidth;
        float textX = timerX + 12.0F;
        float textMaxWidth = Math.max(62.0F, controlsX - textX - 10.0F);
        String titleValue = snapshot.title().isEmpty() ? "No track" : snapshot.title();
        String artistValue = trimToWidth(snapshot.artist().isEmpty() ? snapshot.sourceApp() : snapshot.artist(), 8, FontRole.BODY, textMaxWidth);

        drawMusicCover(context, snapshot, coverX, coverY, MUSIC_COVER_SIZE, MUSIC_COVER_SIZE, 9.0F, alpha, true);
        rounded(context, timerX, timerY, MUSIC_COMPACT_TIMER_WIDTH, MUSIC_COMPACT_TIMER_HEIGHT, 1.0F, alphaColor(0xFF4B4C54, alpha * 0.28F));
        float progress = clamp(snapshot.progressFraction(now), 0.0F, 1.0F);
        float timerFill = Math.max(2.0F, MUSIC_COMPACT_TIMER_HEIGHT * progress);
        rounded(context, timerX, timerY, MUSIC_COMPACT_TIMER_WIDTH, timerFill, 1.0F, alphaColor(0xFFE7D8FF, alpha * 0.96F));
        float timerDotSize = 4.0F;
        float timerDotY = clamp(timerY + MUSIC_COMPACT_TIMER_HEIGHT * progress - timerDotSize * 0.5F,
                timerY - 0.25F, timerY + MUSIC_COMPACT_TIMER_HEIGHT - timerDotSize + 0.25F);
        float timerDotX = timerX - (timerDotSize - MUSIC_COMPACT_TIMER_WIDTH) * 0.5F;
        rounded(context, timerDotX, timerDotY, timerDotSize, timerDotSize, timerDotSize * 0.5F,
                alphaColor(0xFFF8F1FF, alpha * 0.95F));
        rounded(context, timerX + 7.0F, y + 6.0F, 1.0F, height - 12.0F, 0.5F, alphaColor(0xFF8D8B95, alpha * 0.16F));

        TextTexture title = textTexture(titleValue, 10, FontRole.DISPLAY);
        TextTexture artist = textTexture(artistValue.isEmpty() ? snapshot.sourceApp() : artistValue, 8, FontRole.BODY);
        float textBlockHeight = title.height + artist.height + 1.0F;
        float titleY = y + (height - textBlockHeight) * 0.5F - 0.5F;
        float artistY = titleY + title.height + 1.0F;
        drawScrollingText(context, title, textX, titleY, textMaxWidth, alphaColor(0xFFFFFFFF, alpha), snapshot.metadataKey());
        drawText(context, artist, textX, artistY, alphaColor(0xFFDCD4E7, alpha));

        float controlY = y + (height - MUSIC_COMPACT_CONTROL) * 0.5F;
        drawCompactControlButton(context, controlsX, controlY, MUSIC_COMPACT_CONTROL, CONTROL_BACK, 0xFFF6F0FF, alpha * 0.82F);
        if (snapshot.playing()) {
            drawCompactControlButton(context, controlsX + MUSIC_COMPACT_CONTROL + MUSIC_COMPACT_CONTROL_GAP, controlY, MUSIC_COMPACT_CONTROL, CONTROL_PAUSE, 0xFFFFFFFF, alpha * 0.92F);
        } else {
            drawCompactControlButton(context, controlsX + MUSIC_COMPACT_CONTROL + MUSIC_COMPACT_CONTROL_GAP, controlY, MUSIC_COMPACT_CONTROL, CONTROL_PLAY, 0xFFFFFFFF, alpha * 0.92F);
        }
        drawCompactControlButton(context, controlsX + (MUSIC_COMPACT_CONTROL + MUSIC_COMPACT_CONTROL_GAP) * 2.0F, controlY, MUSIC_COMPACT_CONTROL, CONTROL_FRONT, 0xFFF6F0FF, alpha * 0.82F);
        rounded(context, controlsX - 8.0F, y + 6.0F, 1.0F, height - 12.0F, 0.5F, alphaColor(0xFF8D8B95, alpha * 0.16F));

        float barTop = y + 4.0F;
        float barBottom = y + height - 4.0F;
        float barRange = Math.max(8.0F, barBottom - barTop);
        float barWidth = 3.2F;
        float barGap = 2.1F;
        float barRadius = barWidth * 0.5F;
        float[] bars = snapshot.waveHeights();
        for (int i = 0; i < bars.length; i++) {
            float normalized = clamp(bars[i] / 11.5F, 0.0F, 1.0F);
            float barHeight = Math.max(5.0F, 5.0F + normalized * (barRange - 5.0F));
            float bx = barX + i * (barWidth + barGap);
            rounded(context, bx, barBottom - barHeight, barWidth, barHeight, barRadius, alphaColor(0xFFF0E5FF, alpha));
        }
    }

    private float standardWidth(int fps, int ping) {
        TextTexture brand = textTexture("FluxVisuals", 10, FontRole.DISPLAY);
        TextTexture separator = textTexture("\u2022", 8, FontRole.DISPLAY_MEDIUM);
        float pingSlot = slotWidth("999ms", 8, FontRole.BODY);
        float fpsSlot = slotWidth("999fps", 8, FontRole.BODY);
        float textWidth = brand.width + TEXT_GAP + separator.width + TEXT_GAP + pingSlot + TEXT_GAP + separator.width + TEXT_GAP + fpsSlot;
        return Math.max(190.0F, ICON_LEFT_PAD + standardPodWidth() + TEXT_LEFT_GAP + textWidth + 4.0F);
    }

    private float musicWidth(MusicBridge.MusicSnapshot snapshot, MinecraftClient client) {
        if (snapshot == null || !snapshot.present()) {
            return standardWidth(Math.round(displayedFps), Math.round(displayedPing));
        }
        String titleValue = snapshot.title().isEmpty() ? "No track" : snapshot.title();
        String artistValue = snapshot.artist().isEmpty() ? snapshot.sourceApp() : snapshot.artist();
        float desiredText = clamp(Math.max(
                measureTextWidth(titleValue, 10, FontRole.DISPLAY),
                measureTextWidth(artistValue, 8, FontRole.BODY)
        ), 72.0F, 112.0F);
        float contentWidth = ICON_LEFT_PAD + musicPodWidth() + 10.0F + desiredText + 10.0F + compactControlsWidth() + 10.0F + waveBlockWidth(snapshot) + MUSIC_LAYOUT_PAD;
        return Math.max(MUSIC_WATERMARK_WIDTH, Math.max(standardWidth(Math.round(displayedFps), Math.round(displayedPing)) + 4.0F, contentWidth));
    }

    private float moduleToastWidth() {
        String name = "Модуль " + (moduleToastName == null || moduleToastName.isBlank() ? "Module" : moduleToastName);
        TextTexture title = textTexture(name, 10, FontRole.DISPLAY);
        TextTexture status = textTexture(moduleToastEnabled ? "Включен" : "Выключен", 6, FontRole.BODY);
        float badgeW = status.width + 12.0F;
        float textWidth = Math.min(title.width, 154.0F);
        return Math.max(196.0F, ICON_LEFT_PAD + standardPodWidth() + TEXT_LEFT_GAP + textWidth + 6.0F + badgeW + 4.0F);
    }

    private float waveBlockWidth(MusicBridge.MusicSnapshot snapshot) {
        int bars = Math.max(1, snapshot.waveHeights().length);
        return bars * 3.2F + (bars - 1) * 2.1F;
    }

    private float standardPodWidth() {
        return ICON_SIZE + POD_EXTRA_WIDTH;
    }

    private float musicPodWidth() {
        return MUSIC_COVER_SIZE + POD_EXTRA_WIDTH;
    }

    private float compactControlsWidth() {
        return MUSIC_COMPACT_CONTROL * 3.0F + MUSIC_COMPACT_CONTROL_GAP * 2.0F;
    }

    private void drawMusicToggle(DrawContext context, float x, float y) {
        float buttonW = 68.0F;
        float buttonH = 16.0F;
        float buttonX = x + menuWidth - MUSIC_MENU_PAD - buttonW;
        float buttonY = y + 8.0F;
        int fill = musicEnabled ? 0x2B8F5CFF : 0x25191B22;
        int border = musicEnabled ? 0x779C75FF : 0x553C404D;
        rounded(context, buttonX, buttonY, buttonW, buttonH, 8.0F, fill);
        rounded(context, buttonX + 1.0F, buttonY + 1.0F, buttonW - 2.0F, buttonH - 2.0F, 7.0F, border);
        TextTexture label = textTexture("Music", 6, FontRole.BODY);
        drawText(context, label, buttonX + 8.0F, buttonY + 4.0F, musicEnabled ? 0xFFFFFFFF : 0xFFC8C3CE);
        float dotX = buttonX + buttonW - 12.0F;
        float dotY = buttonY + 4.0F;
        rounded(context, dotX, dotY, 6.0F, 6.0F, 3.0F, musicEnabled ? 0xFFE9DCFF : 0xFF515162);
    }

    private void drawContextToggle(DrawContext context) {
        float buttonW = 80.0F;
        float buttonH = 16.0F;
        float buttonX = currentContextMenuX() + contextMenuWidth - CONTEXT_MENU_PAD - buttonW;
        float buttonY = currentContextMenuY() + 7.0F;
        int fill = musicEnabled ? 0x2B8F5CFF : 0x25191B22;
        int border = musicEnabled ? 0x779C75FF : 0x553C404D;
        rounded(context, buttonX, buttonY, buttonW, buttonH, 8.0F, fill);
        rounded(context, buttonX + 1.0F, buttonY + 1.0F, buttonW - 2.0F, buttonH - 2.0F, 7.0F, border);
        TextTexture label = textTexture("Music bar", 6, FontRole.BODY);
        drawText(context, label, buttonX + 8.0F, buttonY + 4.0F, musicEnabled ? 0xFFFFFFFF : 0xFFC8C3CE);
        float dotX = buttonX + buttonW - 12.0F;
        float dotY = buttonY + 4.0F;
        rounded(context, dotX, dotY, 6.0F, 6.0F, 3.0F, musicEnabled ? 0xFFE9DCFF : 0xFF515162);
    }

    private void drawMenuButton(DrawContext context, float x, float y, float w, float h, String label, boolean enabled) {
        int fill = enabled ? 0x338F5CFF : 0x22191B22;
        int border = enabled ? 0xAA9A74FF : 0x663D4150;
        rounded(context, x, y, w, h, 8.0F, fill);
        rounded(context, x + 1.0F, y + 1.0F, w - 2.0F, h - 2.0F, 7.0F, border);
        TextTexture text = textTexture(label, 6, FontRole.BODY);
        drawText(context, text, x + (w - text.width) * 0.5F, y + (h - text.height) * 0.5F - 0.5F, enabled ? 0xFFF8F5FF : 0xFFC8C3CE);
    }

    private void drawMenuProgress(DrawContext context, MusicBridge.MusicSnapshot snapshot) {
        float barX = currentMusicMenuX() + MUSIC_MENU_PAD;
        float barW = menuWidth - MUSIC_MENU_PAD * 2.0F;
        float barY = currentMusicMenuY() + menuHeight - 42.0F;
        float barH = MUSIC_MENU_BAR_HEIGHT;
        long now = System.currentTimeMillis();
        float fraction = draggingSeek ? seekPreviewFraction : snapshot.progressFraction(now);
        fraction = clamp(fraction, 0.0F, 1.0F);

        long currentPosition = draggingSeek ? Math.round(snapshot.durationMs() * fraction) : snapshot.currentPositionMs(now);
        TextTexture elapsed = textTexture(formatTime(currentPosition), 8, FontRole.BODY);
        TextTexture total = textTexture(formatTime(snapshot.durationMs()), 8, FontRole.BODY);
        drawText(context, elapsed, barX, barY - 14.0F, 0xFFBEB8C8);
        drawText(context, total, barX + barW - total.width, barY - 14.0F, 0xFFBEB8C8);

        rounded(context, barX, barY, barW, barH, 2.5F, 0x2912151A);
        rounded(context, barX, barY, Math.max(2.0F, barW * fraction), barH, 2.5F, 0xFFE9DCFF);

        float thumbX = clamp(barX + barW * fraction - 3.0F, barX - 1.0F, barX + barW - 5.0F);
        rounded(context, thumbX, barY - 1.0F, 6.0F, 7.0F, 3.0F, MusicBridge.canSeek() ? 0xFFF8F3FF : 0xFFD6CCEB);
    }

    private void drawMenuControls(DrawContext context, MusicBridge.MusicSnapshot snapshot) {
        float buttonY = currentMusicMenuY() + menuHeight - 24.0F;
        float buttonGap = 10.0F;
        float buttonW = MUSIC_MENU_BUTTON;
        float totalW = buttonW * 3.0F + buttonGap * 2.0F;
        float buttonX = currentMusicMenuX() + (menuWidth - totalW) * 0.5F;

        drawControlIconButton(context, buttonX, buttonY, buttonW, CONTROL_BACK, 0xFFF5F2FF, 0.70F);
        if (snapshot.playing()) {
            drawControlIconButton(context, buttonX + buttonW + buttonGap, buttonY, buttonW, CONTROL_PAUSE, 0xFFFFFFFF, 0.90F);
        } else {
            drawControlIconButton(context, buttonX + buttonW + buttonGap, buttonY, buttonW, CONTROL_PLAY, 0xFFFFFFFF, 0.90F);
        }
        drawControlIconButton(context, buttonX + (buttonW + buttonGap) * 2.0F, buttonY, buttonW, CONTROL_FRONT, 0xFFF5F2FF, 0.70F);
    }

    private void drawControlIconButton(DrawContext context, float x, float y, float size, Identifier icon, int color, float alpha) {
        rounded(context, x, y, size, size, 6.0F, 0x2513151A);
        rounded(context, x + 1.0F, y + 1.0F, size - 2.0F, size - 2.0F, 5.0F, 0x57363A47);
        drawTexture(context, icon, x + 4.0F, y + 4.0F, size - 8.0F, size - 8.0F, CONTROL_TEXTURE_SIZE, CONTROL_TEXTURE_SIZE, alphaColor(color, alpha));
    }

    private void drawCompactControlButton(DrawContext context, float x, float y, float size, Identifier icon, int color, float alpha) {
        float pad = size <= 18.0F ? 1.5F : 2.0F;
        drawTexture(context, icon, x + pad, y + pad, size - pad * 2.0F, size - pad * 2.0F,
                CONTROL_TEXTURE_SIZE, CONTROL_TEXTURE_SIZE, alphaColor(color, alpha));
    }

    private float calculateWatermarkY(MinecraftClient client) {
        if (client != null && client.inGameHud != null && client.inGameHud.getBossBarHud() != null) {
            try {
                Map<?, ?> bossBars = ((dev.fuga.fluxvisuals.mixin.BossBarHudAccessor) (Object) client.inGameHud.getBossBarHud()).fluxvisuals$getBossBars();
                int count = (bossBars != null) ? bossBars.size() : 0;
                if (count > 0) {
                    return 14.0F + count * 19.0F + 4.0F;
                }
            } catch (Exception ignored) {}
        }
        return TOP_Y;
    }

    private void drawWatermarkShell(DrawContext context, float x, float y, float w, float h, float alpha) {
        float radius = 10.0F;
        Render2D.drawShadow(context, x, y, w, h, radius, 8.0F, alphaColor(0x60000000, alpha));
        Render2D.drawRound(context, x, y, w, h, radius, alphaColor(0xD8090B12, alpha));
    }

    private static void drawLiquidGlass(DrawContext context, float x, float y, float w, float h) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachment() == null || main.getColorAttachmentView() == null) {
            return;
        }
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }
        if (liquidGlassSnapshot == null || liquidGlassSnapshot.textureWidth != width
                || liquidGlassSnapshot.textureHeight != height) {
            if (liquidGlassSnapshot != null) {
                liquidGlassSnapshot.delete();
            }
            liquidGlassSnapshot = new SimpleFramebuffer("FluxVisuals Watermark liquid glass", width, height, false);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                main.getColorAttachment(), liquidGlassSnapshot.getColorAttachment(),
                0, 0, 0, 0, 0, width, height);
        context.fill(LIQUID_GLASS_PIPELINE, TextureSetup.of(liquidGlassSnapshot.getColorAttachmentView()),
                Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
    }

    private void drawWatermarkPod(DrawContext context, float x, float y, float w, float h, float alpha) {
        float radius = h * 0.5F;
        rounded(context, x, y, w, h, radius, alphaColor(0x24111418, alpha));
        rounded(context, x + 1.0F, y + 1.0F, w - 1.0F, h - 2.0F, Math.max(0.0F, radius - 1.0F), alphaColor(0x16191D24, alpha));
    }

    private void drawMusicCover(DrawContext context, MusicBridge.MusicSnapshot snapshot, float x, float y, float w, float h, float radius, float alpha, boolean compactWatermark) {
        CoverTexture coverTexture = musicCoverTexture(snapshot);
        if (coverTexture != null) {
            rounded(context, x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, radius + 1.0F, alphaColor(0xFF8F5CFF, alpha * 0.08F));
            rounded(context, x, y, w, h, radius, alphaColor(0xFFFFFFFF, alpha * 0.05F));
            if (compactWatermark) {
                float inset = 1.0F;
                float drawW = Math.max(1.0F, w - inset * 2.0F);
                float drawH = Math.max(1.0F, h - inset * 2.0F);
                drawTexture(context, coverTexture.compactId(), x + inset, y + inset, drawW, drawH,
                        coverTexture.compactWidth(), coverTexture.compactHeight(), alphaColor(0xFFFFFFFF, alpha));
            } else {
                drawTexture(context, coverTexture.id(), x + 1.0F, y + 1.0F, w - 2.0F, h - 2.0F,
                        coverTexture.width(), coverTexture.height(), alphaColor(0xFFFFFFFF, alpha));
            }
        } else {
            rounded(context, x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, radius + 1.0F, alphaColor(0xFF8F5CFF, alpha * 0.10F));
            rounded(context, x, y, w, h, radius, alphaColor(0xFF121218, alpha * 0.78F));
            drawIconGlow(context, x + 5.0F, y + 5.0F, Math.max(8.0F, w - 10.0F), alpha * 0.85F);
        }
    }

    private void syncSeekDrag(MinecraftClient client, MusicBridge.MusicSnapshot snapshot) {
        if (!draggingSeek || !MusicBridge.canSeek() || snapshot == null || !snapshot.present() || snapshot.durationMs() <= 0L) {
            return;
        }

        double scaledX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        seekPreviewFraction = sliderFraction(client, scaledX);
        long now = System.currentTimeMillis();
        if (now - lastSeekApplyAt >= 75L) {
            if (MusicBridge.seekFraction(seekPreviewFraction)) {
                lastSeekApplyAt = now;
            }
        }
    }

    private void openMusicMenu(MinecraftClient client, float mouseX, float mouseY) {
        musicMenuOriginX = contextMenuOpen ? currentContextMenuX() : watermarkSpawnX(MUSIC_MENU_WIDTH);
        musicMenuOriginY = contextMenuOpen ? currentContextMenuY() : watermarkSpawnY();
        contextMenuOpen = false;
        contextMenuAnim = 0.0F;
        draggingContextMenu = false;
        menuWidth = MUSIC_MENU_WIDTH;
        menuHeight = MUSIC_MENU_HEIGHT;
        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();
        menuX = clamp(mouseX - menuWidth * 0.5F, 8.0F, Math.max(8.0F, screenWidth - menuWidth - 8.0F));
        menuY = clamp(mouseY + 8.0F, 8.0F, Math.max(8.0F, screenHeight - menuHeight - 8.0F));
        musicMenuOpen = true;
        musicMenuAnim = 0.0F;
        draggingMusicMenu = false;
        draggingSeek = false;
        seekPreviewFraction = 0.0F;
    }

    private void openContextMenu(MinecraftClient client, float mouseX, float mouseY) {
        contextMenuOriginX = musicMenuOpen ? currentMusicMenuX() : watermarkSpawnX(CONTEXT_MENU_WIDTH);
        contextMenuOriginY = musicMenuOpen ? currentMusicMenuY() : watermarkSpawnY();
        musicMenuOpen = false;
        musicMenuAnim = 0.0F;
        draggingMusicMenu = false;
        contextMenuOpen = true;
        contextMenuWidth = CONTEXT_MENU_WIDTH;
        contextMenuHeight = 68.0F;
        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();
        contextMenuX = clamp(mouseX - contextMenuWidth * 0.5F, 8.0F, Math.max(8.0F, screenWidth - contextMenuWidth - 8.0F));
        contextMenuY = clamp(mouseY + 8.0F, 8.0F, Math.max(8.0F, screenHeight - contextMenuHeight - 8.0F));
        contextMenuAnim = 0.0F;
        draggingContextMenu = false;
        draggingSeek = false;
    }

    private void closeMusicMenu() {
        musicMenuOpen = false;
        contextMenuOpen = false;
        musicMenuAnim = 0.0F;
        contextMenuAnim = 0.0F;
        draggingSeek = false;
        draggingContextMenu = false;
        draggingMusicMenu = false;
    }

    public void setMusicEnabled(boolean enabled) {
        if (musicEnabled == enabled) {
            return;
        }
        musicEnabled = enabled;
        FluxVisualsClient.requestConfigSave();
    }

    public void setNotificationsEnabled(boolean enabled) {
        if (notificationsEnabled == enabled) {
            return;
        }
        notificationsEnabled = enabled;
        FluxVisualsClient.requestConfigSave();
    }

    private boolean insideMenu(float x, float y) {
        float drawX = currentMusicMenuX();
        float drawY = currentMusicMenuY();
        return x >= drawX && x <= drawX + menuWidth && y >= drawY && y <= drawY + menuHeight;
    }

    private boolean insideContextMenu(float x, float y) {
        float drawX = currentContextMenuX();
        float drawY = currentContextMenuY();
        return x >= drawX && x <= drawX + contextMenuWidth && y >= drawY && y <= drawY + contextMenuHeight;
    }

    private boolean hitToggle(float x, float y) {
        float buttonW = 68.0F;
        float buttonH = 16.0F;
        float buttonX = currentMusicMenuX() + menuWidth - MUSIC_MENU_PAD - buttonW;
        float buttonY = currentMusicMenuY() + 8.0F;
        return x >= buttonX && x <= buttonX + buttonW && y >= buttonY && y <= buttonY + buttonH;
    }

    private boolean hitContextToggle(float x, float y) {
        float buttonW = 80.0F;
        float buttonH = 16.0F;
        float buttonX = currentContextMenuX() + contextMenuWidth - CONTEXT_MENU_PAD - buttonW;
        float buttonY = currentContextMenuY() + 7.0F;
        return x >= buttonX && x <= buttonX + buttonW && y >= buttonY && y <= buttonY + buttonH;
    }

    private boolean hitContextMusicRow(float x, float y) {
        float panelX = currentContextMenuX() + CONTEXT_MENU_PAD;
        float panelY = currentContextMenuY() + 31.0F;
        float panelW = contextMenuWidth - CONTEXT_MENU_PAD * 2.0F;
        float panelH = 22.0F;
        return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
    }

    private boolean hitContextNotifications(float x, float y) {
        float panelX = currentContextMenuX() + CONTEXT_MENU_PAD;
        float panelY = currentContextMenuY() + 31.0F + 22.0F + 7.0F;
        float panelW = contextMenuWidth - CONTEXT_MENU_PAD * 2.0F;
        float panelH = 22.0F;
        return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
    }

    private boolean hitBackButton(float x, float y) {
        float buttonX = currentMusicMenuX() + 8.0F;
        float buttonY = currentMusicMenuY() + 8.0F;
        return x >= buttonX && x <= buttonX + 16.0F && y >= buttonY && y <= buttonY + 16.0F;
    }

    private boolean hitControl(float x, float y, int index) {
        float buttonY = currentMusicMenuY() + menuHeight - 24.0F;
        float buttonGap = 10.0F;
        float buttonW = MUSIC_MENU_BUTTON;
        float totalW = buttonW * 3.0F + buttonGap * 2.0F;
        float buttonX = currentMusicMenuX() + (menuWidth - totalW) * 0.5F + index * (buttonW + buttonGap);
        return x >= buttonX && x <= buttonX + buttonW && y >= buttonY && y <= buttonY + MUSIC_MENU_BUTTON;
    }

    private boolean hitSlider(float x, float y) {
        if (!MusicBridge.canSeek()) {
            return false;
        }
        float barX = currentMusicMenuX() + MUSIC_MENU_PAD;
        float barW = menuWidth - MUSIC_MENU_PAD * 2.0F;
        float barY = currentMusicMenuY() + menuHeight - 42.0F;
        return x >= barX && x <= barX + barW && y >= barY - 7.0F && y <= barY + 8.0F;
    }

    private float sliderFraction(MinecraftClient client, double scaledX) {
        float barX = currentMusicMenuX() + MUSIC_MENU_PAD;
        float barW = menuWidth - MUSIC_MENU_PAD * 2.0F;
        if (barW <= 0.0F) {
            return 0.0F;
        }
        return clamp((float) ((scaledX - barX) / barW), 0.0F, 1.0F);
    }

    private boolean hitMusicHeader(float x, float y) {
        float drawX = currentMusicMenuX();
        float drawY = currentMusicMenuY();
        return x >= drawX + 24.0F && x <= drawX + menuWidth - 76.0F && y >= drawY + 6.0F && y <= drawY + 28.0F;
    }

    private boolean hitContextHeader(float x, float y) {
        float drawX = currentContextMenuX();
        float drawY = currentContextMenuY();
        return x >= drawX + 8.0F && x <= drawX + contextMenuWidth - 78.0F && y >= drawY + 6.0F && y <= drawY + 28.0F;
    }

    private boolean hitCompactControl(float x, float y, MusicBridge.MusicSnapshot snapshot, int index) {
        if (snapshot == null || !snapshot.present()) {
            return false;
        }
        float barX = lastX + lastWidth - MUSIC_LAYOUT_PAD - waveBlockWidth(snapshot);
        float controlsX = barX - 10.0F - compactControlsWidth() + index * (MUSIC_COMPACT_CONTROL + MUSIC_COMPACT_CONTROL_GAP);
        float controlY = lastY + (lastHeight - MUSIC_COMPACT_CONTROL) * 0.5F;
        return x >= controlsX && x <= controlsX + MUSIC_COMPACT_CONTROL
                && y >= controlY && y <= controlY + MUSIC_COMPACT_CONTROL;
    }

    private void syncFloatingMenus(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }

        float scaledX = (float) (client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        float scaledY = (float) (client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();

        if (draggingContextMenu && contextMenuOpen) {
            contextMenuX = clamp(scaledX - dragOffsetX, 8.0F, Math.max(8.0F, screenWidth - contextMenuWidth - 8.0F));
            contextMenuY = clamp(scaledY - dragOffsetY, 8.0F, Math.max(8.0F, screenHeight - contextMenuHeight - 8.0F));
            contextMenuOriginX = contextMenuX;
            contextMenuOriginY = contextMenuY;
            contextMenuAnim = 1.0F;
        }
        if (draggingMusicMenu && musicMenuOpen) {
            menuX = clamp(scaledX - dragOffsetX, 8.0F, Math.max(8.0F, screenWidth - menuWidth - 8.0F));
            menuY = clamp(scaledY - dragOffsetY, 8.0F, Math.max(8.0F, screenHeight - menuHeight - 8.0F));
            musicMenuOriginX = menuX;
            musicMenuOriginY = menuY;
            musicMenuAnim = 1.0F;
        }
    }

    private float currentContextMenuX() {
        return lerp(contextMenuOriginX, contextMenuX, easeOutCubic(contextMenuAnim));
    }

    private float currentContextMenuY() {
        return lerp(contextMenuOriginY, contextMenuY, easeOutCubic(contextMenuAnim));
    }

    private float currentMusicMenuX() {
        return lerp(musicMenuOriginX, menuX, easeOutCubic(musicMenuAnim));
    }

    private float currentMusicMenuY() {
        return lerp(musicMenuOriginY, menuY, easeOutCubic(musicMenuAnim));
    }

    private float watermarkSpawnX(float menuWidth) {
        return lastX + (lastWidth - menuWidth) * 0.5F;
    }

    private float watermarkSpawnY() {
        return lastY + Math.max(2.0F, lastHeight * 0.28F);
    }

    private CoverTexture musicCoverTexture(MusicBridge.MusicSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasCover()) {
            return null;
        }

        String key = snapshot.thumbnailHash();
        if (key.isEmpty()) {
            key = Integer.toHexString(java.util.Arrays.hashCode(snapshot.thumbnailBytes()));
        }
        CoverTexture cached = COVER_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(snapshot.thumbnailBytes()));
            if (image == null) {
                return null;
            }

            BufferedImage compactImage = cropCompactCover(image);
            int width = Math.max(1, image.getWidth());
            int height = Math.max(1, image.getHeight());
            NativeImage nativeImage = toNativeImage(image);
            applyRoundedMask(nativeImage, width, height, Math.max(6, Math.round(Math.min(width, height) * 0.22F)));

            Identifier id = Identifier.of("fluxvisuals", "dynamic/watermark_cover_" + key);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
            texture.setFilter(true, false);
            texture.setClamp(true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture.upload();

            int compactWidth = Math.max(1, compactImage.getWidth());
            int compactHeight = Math.max(1, compactImage.getHeight());
            NativeImage compactNativeImage = toNativeImage(compactImage);
            applyRoundedMask(compactNativeImage, compactWidth, compactHeight, Math.max(6, Math.round(Math.min(compactWidth, compactHeight) * 0.22F)));

            Identifier compactId = Identifier.of("fluxvisuals", "dynamic/watermark_cover_compact_" + key);
            NativeImageBackedTexture compactTexture = new NativeImageBackedTexture(() -> compactId.toString(), compactNativeImage);
            compactTexture.setFilter(true, false);
            compactTexture.setClamp(true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(compactId, compactTexture);
            compactTexture.upload();

            CoverTexture coverTexture = new CoverTexture(id, width, height, compactId, compactWidth, compactHeight);
            COVER_TEXTURES.put(key, coverTexture);
            return coverTexture;
        } catch (IOException exception) {
            return null;
        }
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int width = Math.max(1, image.getWidth());
        int height = Math.max(1, image.getHeight());
        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                nativeImage.setColorArgb(ix, iy, image.getRGB(ix, iy));
            }
        }
        return nativeImage;
    }

    private static BufferedImage cropCompactCover(BufferedImage image) {
        int width = Math.max(1, image.getWidth());
        int height = Math.max(1, image.getHeight());
        int cropX = Math.max(0, Math.min(width - 1, Math.round(width * 0.09F)));
        int cropY = Math.max(0, Math.min(height - 1, Math.round(height * 0.05F)));
        int cropW = Math.max(1, Math.min(width - cropX, Math.round(width * 0.82F)));
        int cropH = Math.max(1, Math.min(height - cropY, Math.round(height * 0.72F)));
        return image.getSubimage(cropX, cropY, cropW, cropH);
    }

    private static void applyRoundedMask(NativeImage image, int width, int height, int radius) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getColorArgb(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int mask = Math.round(coverage(width, height, radius, x, y) * 255.0F);
                int outAlpha = alpha * mask / 255;
                image.setColorArgb(x, y, (outAlpha << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private static float approach(float current, float target, float speed) {
        return current + (target - current) * clamp(speed, 0.0F, 1.0F);
    }

    private static String trimToWidth(String value, int pixelSize, FontRole role, float maxWidth) {
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isEmpty()) {
            return "";
        }
        if (measureTextWidth(safeValue, pixelSize, role) <= maxWidth) {
            return safeValue;
        }
        String ellipsis = "...";
        if (measureTextWidth(ellipsis, pixelSize, role) > maxWidth) {
            return "";
        }

        String trimmed = safeValue;
        while (trimmed.length() > 1 && measureTextWidth(trimmed + ellipsis, pixelSize, role) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    private static float slotWidth(String template, int pixelSize, FontRole role) {
        return Math.max(1.0F, measureTextWidth(template, pixelSize, role));
    }

    private static void drawScrollingText(DrawContext context, TextTexture text, float x, float y, float maxWidth, int color, String key) {
        if (text.width <= maxWidth + 0.5F) {
            drawText(context, text, x, y, color);
            return;
        }

        int x1 = Math.round(x);
        int y1 = Math.round(y - 1.0F);
        int x2 = Math.round(x + maxWidth);
        int y2 = Math.round(y + text.height + 1.0F);
        float overflow = Math.max(0.0F, text.width - maxWidth);
        float offset = marqueeOffset(overflow, key);

        context.enableScissor(x1, y1, x2, y2);
        drawText(context, text, x - offset, y, color);
        context.disableScissor();
    }

    private static float marqueeOffset(float overflow, String key) {
        if (overflow <= 0.0F) {
            return 0.0F;
        }

        long seed = Math.abs((key == null ? 0 : key.hashCode()) % 1300);
        float phase = ((System.currentTimeMillis() + seed) % 5400L) / 5400.0F;
        float travel;
        if (phase < 0.18F) {
            travel = 0.0F;
        } else if (phase < 0.48F) {
            travel = smoothstep((phase - 0.18F) / 0.30F);
        } else if (phase < 0.68F) {
            travel = 1.0F;
        } else {
            travel = 1.0F - smoothstep((phase - 0.68F) / 0.32F);
        }
        return overflow * clamp(travel, 0.0F, 1.0F);
    }

    private static float smoothstep(float value) {
        float t = clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float t = clamp(value, 0.0F, 1.0F);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static float measureTextWidth(String value, int pixelSize, FontRole role) {
        String safeValue = value == null || value.isEmpty() ? " " : value;
        int renderSize = pixelSize * TEXT_OVERSAMPLE;
        Font font = font(role);
        if (font.canDisplayUpTo(safeValue) != -1) {
            font = new Font(role.fallbackFamily, role.style, renderSize);
        } else {
            font = font.deriveFont(role.style, renderSize);
        }

        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measure.createGraphics();
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        float width = metrics.stringWidth(safeValue) / (float) TEXT_OVERSAMPLE;
        graphics.dispose();
        return width;
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
    private void drawIconGlow(DrawContext context, float x, float y, float size, float alpha) {
        Identifier texture = watermarkStyle != null ? watermarkStyle.getIcon() : ICON;
        if (watermarkStyle == null || watermarkStyle == WatermarkStyle.DEFAULT) {
            drawTexture(context, texture, x - 10.0F, y - 10.0F, size + 20.0F, size + 20.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, alphaColor(0xFF8F5CFF, alpha * 0.06F));
            drawTexture(context, texture, x - 6.0F, y - 6.0F, size + 12.0F, size + 12.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, alphaColor(0xFFB57CFF, alpha * 0.16F));
            drawTexture(context, texture, x - 2.0F, y - 2.0F, size + 4.0F, size + 4.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, alphaColor(0xFFE9DCFF, alpha * 0.26F));
            drawTextureRegion(context, texture, x, y, size, size,
                    ICON_CROP_U, ICON_CROP_V, ICON_CROP_SIZE, ICON_CROP_SIZE,
                    ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, alphaColor(0xFFFFFFFF, alpha));
        } else {
            drawTexture(context, texture, x - 6.0F, y - 6.0F, size + 12.0F, size + 12.0F, 64, 64, alphaColor(0xFF8F5CFF, alpha * 0.12F));
            drawTexture(context, texture, x - 2.0F, y - 2.0F, size + 4.0F, size + 4.0F, 64, 64, alphaColor(0xFFE9DCFF, alpha * 0.22F));
            drawTexture(context, texture, x, y, size, size, 64, 64, alphaColor(0xFFFFFFFF, alpha));
        }
    }

    private static void drawText(DrawContext context, TextTexture text, float x, float y, int color) {
        drawTexture(context, text.id, x, y, text.width, text.height, text.textureWidth, text.textureHeight, color);
    }

    private static void drawTexture(DrawContext context, Identifier texture, float x, float y, float drawWidth, float drawHeight,
                                    int textureWidth, int textureHeight, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(drawWidth));
        int ih = Math.max(1, Math.round(drawHeight));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0F, 0.0F, iw, ih, textureWidth, textureHeight,
                textureWidth, textureHeight, color);
    }

    private static void drawTextureRegion(DrawContext context, Identifier texture, float x, float y, float drawWidth, float drawHeight,
                                          float u, float v, float regionWidth, float regionHeight, int textureWidth, int textureHeight, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(drawWidth));
        int ih = Math.max(1, Math.round(drawHeight));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u, v, iw, ih,
                Math.max(1, Math.round(regionWidth)), Math.max(1, Math.round(regionHeight)), textureWidth, textureHeight, color);
    }

    private static int alphaColor(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static void rounded(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if ((color >>> 24) == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        int ir = Math.max(0, Math.min(Math.round(radius), Math.min(iw, ih) / 2));
        Identifier texture = roundedTexture(iw, ih, ir);
        drawTexture(context, texture, ix, iy, iw, ih, iw, ih, color);
    }

    private static Identifier roundedTexture(int width, int height, int radius) {
        RoundKey key = new RoundKey(width, height, radius);
        Identifier cached = ROUND_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = Math.round(coverage(width, height, radius, x, y) * 255.0F);
                image.setColorArgb(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/watermark_round_" + width + "_" + height + "_" + radius);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        ROUND_TEXTURES.put(key, id);
        return id;
    }

    private static float coverage(int width, int height, int radius, int x, int y) {
        int hits = 0;
        int total = AA_SAMPLES * AA_SAMPLES;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                float px = x + (sx + 0.5F) / AA_SAMPLES;
                float py = y + (sy + 0.5F) / AA_SAMPLES;
                if (insideRound(width, height, radius, px, py)) {
                    hits++;
                }
            }
        }
        return hits / (float) total;
    }

    private static boolean insideRound(int width, int height, int radius, float x, float y) {
        if (width <= 0 || height <= 0 || x < 0.0F || y < 0.0F || x >= width || y >= height) {
            return false;
        }
        float cx = clamp(x, radius, width - radius);
        float cy = clamp(y, radius, height - radius);
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TextTexture textTexture(String value, int pixelSize, FontRole role) {
        String safeValue = value == null || value.isEmpty() ? " " : value;
        TextKey key = new TextKey(safeValue, pixelSize, role);
        TextTexture cached = TEXT_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        int renderSize = pixelSize * TEXT_OVERSAMPLE;
        Font font = font(role);
        if (font.canDisplayUpTo(safeValue) != -1) {
            font = new Font(role.fallbackFamily, role.style, renderSize);
        } else {
            font = font.deriveFont(role.style, renderSize);
        }

        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measure.createGraphics();
        measureGraphics.setFont(font);
        measureGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measureGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int highWidth = Math.max(1, metrics.stringWidth(safeValue) + 8);
        int highHeight = Math.max(1, metrics.getAscent() + metrics.getDescent() + 8);
        int ascent = metrics.getAscent();
        measureGraphics.dispose();

        BufferedImage highImage = new BufferedImage(highWidth, highHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highImage.createGraphics();
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.drawString(safeValue, 4, ascent + 4);
        graphics.dispose();

        int width = Math.max(1, Math.round(highWidth / (float) TEXT_OVERSAMPLE));
        int height = Math.max(1, Math.round(highHeight / (float) TEXT_OVERSAMPLE));

        NativeImage nativeImage = new NativeImage(highWidth, highHeight, false);
        for (int y = 0; y < highHeight; y++) {
            for (int x = 0; x < highWidth; x++) {
                nativeImage.setColorArgb(x, y, highImage.getRGB(x, y));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/watermark_text_" + role.name().toLowerCase(Locale.ROOT) + "_"
                + Integer.toHexString(safeValue.hashCode()) + "_" + pixelSize);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        TextTexture text = new TextTexture(id, width, height, highWidth, highHeight);
        TEXT_TEXTURES.put(key, text);
        return text;
    }

    private static Font font(FontRole role) {
        Font cached = FONTS.get(role);
        if (cached != null) {
            return cached;
        }

        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(role.id())) {
            cached = Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (FontFormatException | IOException | RuntimeException exception) {
            cached = new Font(role.fallbackFamily, role.style, 16);
        }
        FONTS.put(role, cached);
        return cached;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void showModuleToast(String moduleName, boolean enabled) {
        if (!notificationsEnabled) {
            return;
        }
        if (moduleName == null || moduleName.isBlank()) {
            return;
        }
        moduleToastName = moduleName;
        moduleToastEnabled = enabled;
        moduleToastEndsAt = System.currentTimeMillis() + MODULE_TOAST_DURATION_MS;
    }

    public float getWidth() {
        return lastWidth;
    }

    private record RoundKey(int width, int height, int radius) {
    }

    private record TextKey(String value, int pixelSize, FontRole role) {
    }

    private record TextTexture(Identifier id, int width, int height, int textureWidth, int textureHeight) {
    }

    private record CoverTexture(Identifier id, int width, int height, Identifier compactId, int compactWidth, int compactHeight) {
    }

    private enum FontRole {
        DISPLAY("font/sf-pro-display-semibold.otf", Font.PLAIN, "SansSerif"),
        DISPLAY_MEDIUM("font/sfprodisplaymedium.ttf", Font.PLAIN, "SansSerif"),
        BODY("font/inter_18pt-medium.ttf", Font.PLAIN, "SansSerif");

        private final String path;
        private final int style;
        private final String fallbackFamily;

        FontRole(String path, int style, String fallbackFamily) {
            this.path = path;
            this.style = style;
            this.fallbackFamily = fallbackFamily;
        }

        private Identifier id() {
            return Identifier.of("fluxvisuals", path);
        }
    }
}
