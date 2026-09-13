package luna.ware.util.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class BlendUtil {
    private BlendUtil() {
    }

    public static void runBlended(Runnable action) {
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        try {
            action.run();
        } finally {
            GlStateManager._depthMask(depthWriteWasEnabled);
            if (depthWasEnabled) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }
            if (blendWasEnabled) {
                GlStateManager._enableBlend();
            } else {
                GlStateManager._disableBlend();
            }
        }
    }

    public static void runAdditive(Runnable action) {
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        try {
            action.run();
        } finally {
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager._depthMask(depthWriteWasEnabled);
            if (depthWasEnabled) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }
            if (blendWasEnabled) {
                GlStateManager._enableBlend();
            } else {
                GlStateManager._disableBlend();
            }
        }
    }
}
