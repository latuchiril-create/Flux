package dev.fuga.fluxvisuals.gui;

import dev.fuga.fluxvisuals.modules.ModuleManager;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends Screen {
    private static PremiumClickGuiRenderer sessionRenderer;
    private final PremiumClickGuiRenderer renderer;
    private boolean initialized;

    public ClickGuiScreen(ModuleManager ignoredModuleManager) {
        super(Text.literal("FluxVisuals"));
        if (sessionRenderer == null) sessionRenderer = new PremiumClickGuiRenderer();
        renderer = sessionRenderer;
    }

    @Override
    protected void init() {
        if (!initialized) {
            renderer.open();
            initialized = true;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do not draw vanilla deferred overlay
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderer.render(context, width, height, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return renderer.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return renderer.mouseDragged(mouseX, mouseY, button) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        renderer.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return renderer.mouseScrolled(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return renderer.charTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renderer.keyPressed(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSLASH) {
            FluxVisualsClient.suppressOpenGuiUntilRightShiftRelease();
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        initialized = false;
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
