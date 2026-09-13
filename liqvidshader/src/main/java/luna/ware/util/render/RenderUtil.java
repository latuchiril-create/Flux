package luna.ware.util.render;

import java.awt.Color;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Isolated liquid-glass facade. Copy this class into the project's RenderUtil
 * (or call it directly) together with the remaining files in this package.
 */
public final class RenderUtil {
    private static final float RECT_RADIUS_SCALE = 0.5F;

    private RenderUtil() {
    }

    /** Draws the default liquid-glass panel. */
    public static void glass(
            float x, float y, float width, float height, float radius,
            MatrixStack matrices
    ) {
        glass(x, y, width, height, radius,
                new Color(255, 255, 255, 18), 1.0F,
                4.0F, 3.5F, 1.0F, matrices);
    }

    /** Draws default liquid glass with an explicit background blur strength. */
    public static void glass(
            float x, float y, float width, float height, float radius,
            float blurStrength, MatrixStack matrices
    ) {
        glass(x, y, width, height, radius,
                new Color(255, 255, 255, 18), 1.0F,
                4.0F, 3.5F, 1.0F, blurStrength, matrices);
    }

    public static void glass(
            float x, float y, float width, float height, float radius,
            Color tint, float alpha, float blurStrength, MatrixStack matrices
    ) {
        glass(x, y, width, height, radius, tint, alpha,
                4.0F, 3.5F, 1.0F, blurStrength, matrices);
    }

    public static void glass(
            float x, float y, float width, float height, float radius,
            Color tint, float alpha, MatrixStack matrices
    ) {
        glass(x, y, width, height, radius, tint, alpha,
                4.0F, 3.5F, 1.0F, matrices);
    }

    /**
     * Full liquid-glass control.
     *
     * @param distortion refraction strength in GUI pixels
     * @param edgeLight soft-perimeter width in GUI pixels
     * @param shine translucent highlight strength
     * @param blurStrength background blur strength; 1.0 is normal
     */
    public static void glass(
            float x, float y, float width, float height, float radius,
            Color tint, float alpha, float distortion, float edgeLight,
            float shine, float blurStrength, MatrixStack matrices
    ) {
        glass(x, y, width, height, radius, tint, alpha,
                distortion, edgeLight, shine, blurStrength,
                distortion, true, matrices);
    }

    public static void glass(
            float x, float y, float width, float height, float radius,
            Color tint, float alpha, float distortion, float edgeLight,
            float shine, float blurStrength, float innerDistortion,
            boolean innerBlur, MatrixStack matrices
    ) {
        if (RenderPhase.isRecording() || tint == null || alpha <= 0.0F) {
            return;
        }

        BlurRenderer.drawLiquidGlass(
                x, y, width, height, radius * RECT_RADIUS_SCALE,
                tint, alpha, distortion, edgeLight, shine,
                blurStrength, innerDistortion, innerBlur
        );
    }

    public static void glass(
            float x, float y, float width, float height, float radius,
            Color tint, float alpha, float distortion, float edgeLight,
            float shine, MatrixStack matrices
    ) {
        glass(x, y, width, height, radius, tint, alpha,
                distortion, edgeLight, shine, 1.0F, matrices);
    }

    /** Refreshes the backdrop for glass drawn later in the same deferred pass. */
    public static void refreshGlassBackground(float blurStrength) {
        BlurRenderer.refreshBackgroundBlur(blurStrength);
    }
}
