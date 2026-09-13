package dev.fuga.fluxvisuals.gui.imgui;

import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.stb.STBImage;

public final class ImGuiRenderer {
    private static final Identifier FONT_ID = Identifier.of("fluxvisuals", "font/sfprodisplaymedium.ttf");
    private static final Identifier SEARCH_ID = Identifier.of("fluxvisuals", "icons/search.png");
    private static final Identifier SETTINGS_ID = Identifier.of("fluxvisuals", "icons/settings.png");

    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private byte[] fontBytes;
    private ImFont font;
    private Icons icons = Icons.EMPTY;
    private boolean initialized;

    public void render(ImGuiClickGui clickGui) {
        if (!initialized) {
            initialize();
        }

        glfw.newFrame();
        ImGui.newFrame();
        clickGui.render(font, icons);
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void initialize() {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow().getHandle();

        ImGui.createContext();
        configureIO();
        configureStyle();
        loadFont(client);

        glfw.init(windowHandle, false);
        gl3.init("#version 150");
        gl3.updateFontsTexture();
        icons = new Icons(loadTexture(client, SEARCH_ID), loadTexture(client, SETTINGS_ID));
        initialized = true;
    }

    private void configureIO() {
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setLogFilename(null);
    }

    private void configureStyle() {
        ImGuiStyle style = ImGui.getStyle();
        style.setAntiAliasedFill(true);
        style.setAntiAliasedLines(true);
        style.setAntiAliasedLinesUseTex(true);
        style.setCurveTessellationTol(0.55F);
        style.setCircleTessellationMaxError(0.08F);
        style.setWindowPadding(0.0F, 0.0F);
        style.setFramePadding(0.0F, 0.0F);
        style.setItemSpacing(0.0F, 0.0F);
        style.setWindowBorderSize(0.0F);
    }

    private void loadFont(MinecraftClient client) {
        try (InputStream stream = client.getResourceManager().open(FONT_ID)) {
            fontBytes = stream.readAllBytes();
            ImFontConfig config = new ImFontConfig();
            config.setPixelSnapH(false);
            config.setOversampleH(3);
            config.setOversampleV(2);
            font = ImGui.getIO().getFonts().addFontFromMemoryTTF(
                    fontBytes,
                    18.0F,
                    config,
                    ImGui.getIO().getFonts().getGlyphRangesCyrillic()
            );
            config.destroy();
        } catch (IOException | RuntimeException exception) {
            font = ImGui.getIO().getFonts().addFontDefault();
        }
    }

    private static int loadTexture(MinecraftClient client, Identifier id) {
        try (InputStream stream = client.getResourceManager().open(id)) {
            byte[] bytes = stream.readAllBytes();
            ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
            encoded.put(bytes);
            encoded.flip();

            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (image == null) {
                return 0;
            }

            int texture = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, width.get(0), height.get(0), 0,
                    GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, image);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
            STBImage.stbi_image_free(image);
            return texture;
        } catch (IOException | RuntimeException exception) {
            return 0;
        }
    }

    public record Icons(int searchTexture, int settingsTexture) {
        private static final Icons EMPTY = new Icons(0, 0);
    }
}
