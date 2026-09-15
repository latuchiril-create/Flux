package dev.fuga.fluxvisuals.render.font;

import net.minecraft.client.gui.DrawContext;

/**
 * Semantic access to the imported Velka icon atlases.  The glyphs stay in
 * their native MSDF atlases, so they keep a clean edge at every GUI scale.
 */
public final class MsdfIcons {
    public enum Icon {
        COMBAT(Fonts.VELKA_ICONS, "a"),
        MOVEMENT(Fonts.VELKA_ICONS, "m"),
        PLAYER(Fonts.VELKA_ICONS, "b"),
        VISUALS(Fonts.VELKA_ICONS, "p"),
        AUTOBUY(Fonts.VELKA_BOT_ICONS, "\uEB13"),
        SETTINGS(Fonts.VELKA_ICONS, "B"),
        WRENCH(Fonts.VELKA_BOT_ICONS, "\uEB06"),
        SEARCH(Fonts.VELKA_ICONS, "g"),
        DOTS(Fonts.VELKA_BOT_ICONS, "\uEB14"),
        CHEVRON(Fonts.VELKA_ICONS, "w"),
        USER(Fonts.VELKA_ICONS, "b"),
        PIPETTE(Fonts.VELKA_ICONS, "s"),
        RESET(Fonts.VELKA_BOT_ICONS, "\uEB0B"),
        SORT(Fonts.VELKA_BOT_ICONS, "\uEB04"),
        INFO(Fonts.VELKA_BOT_ICONS, "\uEB1E"),
        COPY(Fonts.VELKA_BOT_ICONS, "\uEB06"),
        PASTE(Fonts.VELKA_BOT_ICONS, "\uEB10"),
        PIN(Fonts.VELKA_ICONS, "s"),
        NO_SETTINGS(Fonts.VELKA_ICONS, "l"),
        SLIDERS(Fonts.VELKA_ICONS, "l"),
        ZAP(Fonts.VELKA_ICONS, "A"),
        KEYBOARD(Fonts.VELKA_BOT_ICONS, "\uEB02");

        private final MsdfFont font;
        private final String glyph;

        Icon(MsdfFont font, String glyph) {
            this.font = font;
            this.glyph = glyph;
        }
    }

    private MsdfIcons() {
    }

    /** Draws a glyph centered in the supplied rectangle without raster scaling. */
    public static void draw(DrawContext context, Icon icon, float x, float y, float width, float height, int color) {
        if (context == null || icon == null || width <= 0.0F || height <= 0.0F || (color >>> 24) == 0) {
            return;
        }
        float size = Math.max(0.5F, Math.min(width, height) * 1.05F);
        float glyphWidth = icon.font.getWidth(icon.glyph, size);
        float glyphHeight = icon.font.getHeight(size);
        icon.font.drawString(context, icon.glyph,
                x + (width - glyphWidth) * 0.5F,
                y + (height - glyphHeight) * 0.5F,
                size, color);
    }
}
