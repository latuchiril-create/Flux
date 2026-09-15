package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.gui.modern.font.ModernFont;
import dev.fuga.fluxvisuals.gui.modern.setting.BooleanSetting;
import dev.fuga.fluxvisuals.gui.modern.setting.ColorSetting;
import dev.fuga.fluxvisuals.gui.modern.setting.Setting;
import dev.fuga.fluxvisuals.gui.modern.setting.SliderSetting;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class TestHud extends Module {
    private float x = 40.0F;
    private float y = 140.0F;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    private float plateWidth = 150.0F;
    private float plateHeight = 60.0F;
    private float cornerRadius = 14.0F;
    private float distortion = 1.8F;
    private float shine = 1.2F;
    private boolean innerBlur = true;
    private int tintArgb = 0x20FFFFFF;

    private final SliderSetting widthSetting = new SliderSetting("Ширина", 40.0F, 400.0F, 1.0F, "px", () -> plateWidth, val -> plateWidth = val);
    private final SliderSetting heightSetting = new SliderSetting("Высота", 20.0F, 250.0F, 1.0F, "px", () -> plateHeight, val -> plateHeight = val);
    private final SliderSetting radiusSetting = new SliderSetting("Скругление", 0.0F, 40.0F, 1.0F, "px", () -> cornerRadius, val -> cornerRadius = val);
    private final SliderSetting distortionSetting = new SliderSetting("Искажение", 0.0F, 5.0F, 0.1F, "x", () -> distortion, val -> distortion = val);
    private final SliderSetting shineSetting = new SliderSetting("Блик", 0.0F, 3.0F, 0.1F, "", () -> shine, val -> shine = val);
    private final BooleanSetting innerBlurSetting = new BooleanSetting("Матовость (Blur)", () -> innerBlur, val -> innerBlur = val);
    private final ColorSetting tintSetting = new ColorSetting("Цвет оттенка", () -> tintArgb, val -> tintArgb = val);

    public TestHud() {
        super("TEST", "Пластина жидкого стекла с эффектом преломления пространства.", ModuleCategory.VISUALS);
    }

    public List<Setting<?>> getSettings() {
        return List.of(
                widthSetting,
                heightSetting,
                radiusSetting,
                distortionSetting,
                shineSetting,
                innerBlurSetting,
                tintSetting
        );
    }

    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client == null || client.world == null) {
            return;
        }

        float w = plateWidth;
        float h = plateHeight;
        float r = Math.min(cornerRadius, Math.min(w, h) * 0.5F);
        float dist = distortion;
        float sh = shine;
        boolean blur = innerBlur;

        int a = (tintArgb >>> 24) & 0xFF;
        int rgb = tintArgb & 0x00FFFFFF;
        Color tint = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);

        // Render pure physical liquid glass with world refraction
        BlurRenderer.drawLiquidGlass(
                context,
                x, y, w, h, r,
                tint, 1.0F,
                dist, 2.0F, sh,
                1.0F, dist * 0.7F, blur
        );

        // In ChatScreen, show drag border & indicator
        if (client.currentScreen instanceof ChatScreen) {
            Render2D.drawRoundOutline(context, x, y, w, h, r, 1.0F, 0x40FFFFFF);
            ModernFont.drawCentered(context, "TEST GLASS", x + w * 0.5F, y + (h - 9.0F) * 0.5F, 9.0F, 0x80FFFFFF, ModernFont.Type.INTER_MEDIUM);
        }
    }

    public boolean handleMouse(MinecraftClient client, double mouseX, double mouseY, int button, int action) {
        if (!isEnabled() || client == null || !(client.currentScreen instanceof ChatScreen)) {
            dragging = false;
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        float scaledX = (float) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        float scaledY = (float) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
        float w = plateWidth;
        float h = plateHeight;

        if (action == GLFW.GLFW_RELEASE) {
            dragging = false;
            return false;
        }
        if (action == GLFW.GLFW_PRESS && scaledX >= x && scaledX <= x + w && scaledY >= y && scaledY <= y + h) {
            dragging = true;
            dragOffsetX = scaledX - x;
            dragOffsetY = scaledY - y;
            return true;
        }
        return false;
    }

    public void handleMouseMove(MinecraftClient client, double mouseX, double mouseY) {
        if (!dragging || client == null || !(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        float scaledX = (float) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        float scaledY = (float) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
        this.x = scaledX - dragOffsetX;
        this.y = scaledY - dragOffsetY;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
