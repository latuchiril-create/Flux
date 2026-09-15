package dev.fuga.fluxvisuals.render.font;

/**
 * Global repository of loaded MSDF font weights.
 */
public final class Fonts {
    public static final MsdfFont REGULAR = new MsdfFont("regular");
    public static final MsdfFont MEDIUM = new MsdfFont("medium");
    public static final MsdfFont SEMIBOLD = new MsdfFont("semibold");
    public static final MsdfFont BOLD = new MsdfFont("bold");
    public static final MsdfFont ROUND_BOLD = new MsdfFont("roundbold");
    /** Vector icon atlases imported from Velka with their original MSDF offsets. */
    public static final MsdfFont VELKA_ICONS = new MsdfFont("velka_icons");
    public static final MsdfFont VELKA_CONFIG_ICONS = new MsdfFont("config_icons");
    public static final MsdfFont VELKA_BOT_ICONS = new MsdfFont("bot_icons");

    private Fonts() {
    }

    public static void initAll() {
        REGULAR.init();
        MEDIUM.init();
        SEMIBOLD.init();
        BOLD.init();
        ROUND_BOLD.init();
        VELKA_ICONS.init();
        VELKA_CONFIG_ICONS.init();
        VELKA_BOT_ICONS.init();
    }
}
