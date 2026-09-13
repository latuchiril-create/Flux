package dev.fuga.fluxvisuals.gui.modern;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ModernClickGuiScreen extends Screen {
    private final ModernClickGuiRenderer renderer;

    public ModernClickGuiScreen(ModuleManager moduleManager) {
        super(Text.literal("FLUX"));
        this.renderer = new ModernClickGuiRenderer(moduleManager);
    }

    @Override
    protected void init() {
        renderer.open();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Leave empty so no deferred vanilla overlay interferes with our shaders
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        renderer.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return renderer.mouseDragged(mouseX, mouseY, button) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
    public boolean shouldPause() {
        return false;
    }
}
