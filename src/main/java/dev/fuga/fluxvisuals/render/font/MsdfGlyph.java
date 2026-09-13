package dev.fuga.fluxvisuals.render.font;

/**
 * Represents a single character glyph in an MSDF font atlas.
 */
public final class MsdfGlyph {
    private final int unicode;
    private final float advance;
    private final boolean hasBounds;

    private final float planeLeft;
    private final float planeBottom;
    private final float planeRight;
    private final float planeTop;

    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    public MsdfGlyph(int unicode, float advance, boolean hasBounds,
                     float planeLeft, float planeBottom, float planeRight, float planeTop,
                     float atlasLeft, float atlasBottom, float atlasRight, float atlasTop,
                     float atlasWidth, float atlasHeight) {
        this.unicode = unicode;
        this.advance = advance;
        this.hasBounds = hasBounds;

        this.planeLeft = planeLeft;
        this.planeBottom = planeBottom;
        this.planeRight = planeRight;
        this.planeTop = planeTop;

        if (hasBounds && atlasWidth > 0.0F && atlasHeight > 0.0F) {
            // Note: MSDF json yOrigin is "bottom", so atlasBottom is from bottom of texture.
            // In OpenGL texture UV, v=0 is top, v=1 is bottom (or standard texture space).
            // Convert to UV where top is (atlasHeight - atlasTop) / atlasHeight and bottom is (atlasHeight - atlasBottom) / atlasHeight:
            this.u0 = atlasLeft / atlasWidth;
            this.u1 = atlasRight / atlasWidth;
            this.v0 = (atlasHeight - atlasTop) / atlasHeight;
            this.v1 = (atlasHeight - atlasBottom) / atlasHeight;
        } else {
            this.u0 = 0.0F;
            this.v0 = 0.0F;
            this.u1 = 0.0F;
            this.v1 = 0.0F;
        }
    }

    public int getUnicode() {
        return unicode;
    }

    public float getAdvance() {
        return advance;
    }

    public boolean hasBounds() {
        return hasBounds;
    }

    public float getPlaneLeft() {
        return planeLeft;
    }

    public float getPlaneBottom() {
        return planeBottom;
    }

    public float getPlaneRight() {
        return planeRight;
    }

    public float getPlaneTop() {
        return planeTop;
    }

    public float getU0() {
        return u0;
    }

    public float getV0() {
        return v0;
    }

    public float getU1() {
        return u1;
    }

    public float getV1() {
        return v1;
    }
}
