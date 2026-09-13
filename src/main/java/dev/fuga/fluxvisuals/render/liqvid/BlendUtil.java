package dev.fuga.fluxvisuals.render.liqvid;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class BlendUtil {
    private BlendUtil() {
    }

    public static void runBlended(Runnable action) {
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        try {
            action.run();
        } finally {
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
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

