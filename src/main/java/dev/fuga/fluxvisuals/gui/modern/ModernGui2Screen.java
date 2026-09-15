package dev.fuga.fluxvisuals.gui.modern;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Second bindable entry point using the stable modern ClickGUI renderer. */
public final class ModernGui2Screen extends Screen {
    private static ModernGui2Renderer sessionRenderer;
    private final ModernGui2Renderer renderer;
    private boolean initialized;

    public ModernGui2Screen(ModuleManager moduleManager) {
        super(Text.literal("FLUX ModernGui2"));
        if (sessionRenderer == null || sessionRenderer.getModuleManager() != moduleManager) {
            sessionRenderer = new ModernGui2Renderer(moduleManager);
        }
        this.renderer = sessionRenderer;
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
        // Leave empty so no deferred vanilla overlay blurs the GUI
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (renderer.isClosed()) {
            super.close();
            return;
        }
        renderer.render(context, width, height, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renderer.isClosing()) return true;
        return renderer.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (renderer.isClosing()) return true;
        renderer.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (renderer.isClosing()) return true;
        return renderer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (renderer.isClosing()) return true;
        return renderer.mouseScrolled(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renderer.isClosing()) return true;
        return renderer.charTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renderer.isClosing()) return true;
        if (renderer.keyPressed(keyCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        if (FluxVisualsClient.MODULE_MANAGER != null && FluxVisualsClient.MODULE_MANAGER.getMenu() != null) {
            int menuKey = FluxVisualsClient.MODULE_MANAGER.getMenu().getKeyBind();
            if (menuKey != 0 && menuKey != GLFW.GLFW_KEY_UNKNOWN && keyCode == menuKey) {
                close();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        FluxVisualsClient.onGuiClosed();
        if (renderer != null && !renderer.isClosing() && !renderer.isClosed()) {
            renderer.startClosing();
            return;
        }
        super.close();
    }

    @Override
    public void removed() {
        FluxVisualsClient.onGuiClosed();
        renderer.removed();
        initialized = false;
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
