package dev.fuga.fluxvisuals.gui;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.LicenseManager;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import dev.fuga.fluxvisuals.modules.visual.Animations;
import dev.fuga.fluxvisuals.modules.visual.AnarchySwitcher;
import dev.fuga.fluxvisuals.modules.visual.AspectRatio;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.AutoResellAFK;
import dev.fuga.fluxvisuals.modules.visual.AutoSwap;
import dev.fuga.fluxvisuals.modules.visual.BlockOverlay;
import dev.fuga.fluxvisuals.modules.visual.ChinaHat;
import dev.fuga.fluxvisuals.modules.visual.Crosshair;
import dev.fuga.fluxvisuals.modules.visual.ElytraSwap;
import dev.fuga.fluxvisuals.modules.visual.FreeLook;
import dev.fuga.fluxvisuals.modules.visual.HitColor;
import dev.fuga.fluxvisuals.modules.visual.HitboxCustomizer;
import dev.fuga.fluxvisuals.modules.visual.ItemRadius;
import dev.fuga.fluxvisuals.modules.visual.ItemResorter;
import dev.fuga.fluxvisuals.modules.visual.JumpCircles;
import dev.fuga.fluxvisuals.modules.visual.Macros;
import dev.fuga.fluxvisuals.modules.visual.NameBind;
import dev.fuga.fluxvisuals.modules.visual.Particles;
import dev.fuga.fluxvisuals.modules.visual.Removals;
import dev.fuga.fluxvisuals.modules.visual.TabCustomizer;
import dev.fuga.fluxvisuals.modules.visual.TapeMouse;
import dev.fuga.fluxvisuals.modules.visual.TargetEsp;
import dev.fuga.fluxvisuals.modules.visual.TargetHud;
import dev.fuga.fluxvisuals.modules.visual.Trails;
import dev.fuga.fluxvisuals.modules.visual.Telegram;
import dev.fuga.fluxvisuals.modules.visual.WorldCustomizer;
import dev.fuga.fluxvisuals.modules.visual.Zoom;
import dev.fuga.fluxvisuals.modules.visual.Wings;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PremiumClickGuiRenderer {
    private static final String HITBOX_ALWAYS_SHOW_BIND_KEY = "hitbox_always_show";
    private static final int MOUSE_BIND_OFFSET = -1000;
    private static final Identifier SEARCH_ICON = Identifier.of("fluxvisuals", "icons/search.png");
    private static final Identifier SETTINGS_ICON = Identifier.of("fluxvisuals", "icons/settings.png");
    private static final float BASE_WIDTH = 1178.0F;
    private static final float BASE_HEIGHT = 640.0F;
    private static final float CENTER_Y_OFFSET = -64.0F;
    private static final float MAIN_X = 319.0F;
    private static final float MAIN_Y = 202.0F;
    private static final float MAIN_W = 540.0F;
    private static final float MAIN_H = 320.0F;
    private static final float MODULE_W = 238.0F;
    private static final float MODULE_H = 41.0F;
    private static final float MODULE_GAP_Y = 51.0F;
    private static final float MODULE_LIST_X = 345.0F;
    private static final float MODULE_LIST_Y = 264.0F;
    private static final float MODULE_LIST_W = 501.0F;
    private static final float MODULE_LIST_H = 245.0F;
    private static final float ITEM_RESORTER_CARD_W = 340.0F;
    private static final float ITEM_RESORTER_INPUT_W = 216.0F;
    private static final float ITEM_RESORTER_PANEL_W = 314.0F;
    private static final float ITEM_RESORTER_CHIP_H = 22.0F;
    private static final float ITEM_RESORTER_CHIP_GAP = 6.0F;
    private static final float CLICKGUI_MIN_SCALE = 0.78F;
    private static final float CLICKGUI_MAX_SCALE = 1.12F;
    private static final long OPEN_NS = 180_000_000L;
    private static final int AA_SAMPLES = 5;
    private static final int TEXT_OVERSAMPLE = 2;
    private static final ColorToken APP_BACKGROUND = new ColorToken(4, 5, 8);
    private static final ColorToken PANEL = new ColorToken(10, 11, 17);
    private static final ColorToken MODULE_CARD = new ColorToken(16, 17, 25);
    private static final ColorToken MODULE_HOVER = new ColorToken(24, 25, 36);
    private static final ColorToken PURPLE = new ColorToken(176, 102, 255);
    private static final ColorToken PURPLE_DARK = new ColorToken(91, 55, 170);
    private static final ColorToken PURPLE_GLOW = new ColorToken(203, 142, 255);
    private static final ColorToken VIOLET_DEEP = new ColorToken(53, 46, 96);
    private static final ColorToken OUTLINE = new ColorToken(47, 49, 68);
    private static final ColorToken OUTLINE_ACTIVE = new ColorToken(166, 102, 255);
    private static final ColorToken OUTLINE_GLOW = new ColorToken(206, 151, 255);
    private static final ColorToken TEXT_MAIN = new ColorToken(245, 245, 247);
    private static final ColorToken TEXT_SECONDARY = new ColorToken(161, 161, 170);
    private static final ColorToken TEXT_DIM = new ColorToken(107, 114, 128);
    private static final ColorToken TOGGLE_OFF = new ColorToken(42, 42, 54);
    private static final ColorToken TOGGLE_ON = new ColorToken(123, 44, 191);
    private static final ColorToken TOGGLE_KNOB = new ColorToken(255, 255, 255);
    private static final Map<RoundKey, Identifier> ROUND_TEXTURES = new HashMap<>();
    private static final Map<Integer, Identifier> PALETTE_TEXTURES = new HashMap<>();
    private static final Map<Identifier, Identifier> WHITE_ICONS = new HashMap<>();
    private static final Map<TextKey, TextTexture> TEXT_TEXTURES = new HashMap<>();
    private static final Map<FontRole, Font> FONTS = new EnumMap<>(FontRole.class);
    private static final Map<String, Boolean> REMEMBERED_MODULE_STATES = new HashMap<>();
    private static final Map<String, Integer> REMEMBERED_BINDS = new HashMap<>();
    private static final Map<Integer, Boolean> BIND_KEY_DOWN = new HashMap<>();

    static {
        REMEMBERED_BINDS.put(FreeLook.PERSPECTIVE_BIND_KEY, GLFW.GLFW_KEY_LEFT_ALT);
        REMEMBERED_BINDS.put(Zoom.BIND_KEY, GLFW.GLFW_KEY_C);
        REMEMBERED_BINDS.put(AutoSwap.BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
        REMEMBERED_BINDS.put(ElytraSwap.BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
        REMEMBERED_BINDS.put(ElytraSwap.FIREWORK_BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
    }

    private static Identifier hueSliderTexture;
    private static boolean rememberedTargetEspCard = true;
    private static boolean rememberedChinaHatCard;
    private static boolean rememberedWingsCard;
    private static boolean rememberedAspectRatioCard;
    private static boolean rememberedRemovalsCard = true;
    private static boolean rememberedParticlesCard;
    private static boolean rememberedJumpCirclesCard;
    private static boolean rememberedTrailsCard;
    private static boolean rememberedWorldCustomizerCard;
    private static boolean rememberedHitColorCard;
    private static boolean rememberedHitboxCustomizerCard;
    private static boolean rememberedBlockOverlayCard;
    private static boolean rememberedItemRadiusCard;
    private static boolean rememberedFreeLookCard;
    private static boolean rememberedZoomCard;
    private static boolean rememberedAutoSwapCard;
    private static boolean rememberedElytraSwapCard;
    private static boolean rememberedItemResorterCard;
    private static boolean rememberedAutoBuyCard;
    private static boolean rememberedTelegramCard;
    private static boolean rememberedTargetHudCard = true;
    private static boolean rememberedTapeMouseCard;
    private static boolean rememberedAnarchySwitcherCard;
    private static boolean rememberedNameBinderCard;
    private static boolean rememberedMacrosCard;
    private static boolean rememberedCrosshairCard = true;
    private static boolean rememberedAnimationsCard;
    private static boolean rememberedTabCustomizerCard;
    private static float rememberedTargetX = 881.0F;
    private static float rememberedTargetY = 92.0F;
    private static float rememberedChinaX = 94.0F;
    private static float rememberedChinaY = 244.0F;
    private static float rememberedWingsX = 881.0F;
    private static float rememberedWingsY = 22.0F;
    private static float rememberedAspectX = 881.0F;
    private static float rememberedAspectY = 444.0F;
    private static float rememberedRemovalsX = 880.0F;
    private static float rememberedRemovalsY = 22.0F;
    private static float rememberedParticlesX = 94.0F;
    private static float rememberedParticlesY = 424.0F;
    private static float rememberedJumpCirclesX = 94.0F;
    private static float rememberedJumpCirclesY = 228.0F;
    private static float rememberedTrailsX = 881.0F;
    private static float rememberedTrailsY = 370.0F;
    private static float rememberedWorldCustomizerX = 94.0F;
    private static float rememberedWorldCustomizerY = 22.0F;
    private static float rememberedHitColorX = 94.0F;
    private static float rememberedHitColorY = 210.0F;
    private static float rememberedHitboxX = 881.0F;
    private static float rememberedHitboxY = 22.0F;
    private static float rememberedBlockOverlayX = 881.0F;
    private static float rememberedBlockOverlayY = 210.0F;
    private static float rememberedItemRadiusX = 881.0F;
    private static float rememberedItemRadiusY = 300.0F;
    private static float rememberedFreeLookX = 94.0F;
    private static float rememberedFreeLookY = 22.0F;
    private static float rememberedZoomX = 94.0F;
    private static float rememberedZoomY = 388.0F;
    private static float rememberedAutoSwapX = 74.0F;
    private static float rememberedAutoSwapY = 76.0F;
    private static float rememberedElytraSwapX = 74.0F;
    private static float rememberedElytraSwapY = 178.0F;
    private static float rememberedItemResorterX = 881.0F;
    private static float rememberedItemResorterY = 330.0F;
    private static float rememberedAutoBuyX = 94.0F;
    private static float rememberedAutoBuyY = 330.0F;
    private static float rememberedTelegramX = 94.0F;
    private static float rememberedTelegramY = 22.0F;
    private static float rememberedTargetHudX = 881.0F;
    private static float rememberedTargetHudY = 22.0F;
    private static float rememberedTapeMouseX = 94.0F;
    private static float rememberedTapeMouseY = 22.0F;
    private static float rememberedAnarchySwitcherX = 94.0F;
    private static float rememberedAnarchySwitcherY = 142.0F;
    private static float rememberedNameBinderX = 881.0F;
    private static float rememberedNameBinderY = 142.0F;
    private static float rememberedMacrosX = 881.0F;
    private static float rememberedMacrosY = 276.0F;
    private static float rememberedCrosshairX = 881.0F;
    private static float rememberedCrosshairY = 22.0F;
    private static float rememberedAnimationsX = 94.0F;
    private static float rememberedAnimationsY = 114.0F;
    private static float rememberedTabCustomizerX = 881.0F;
    private static float rememberedTabCustomizerY = 420.0F;

    private final List<VisualModule> modules = new ArrayList<>();
    private final Map<Category, Float> tabHover = new EnumMap<>(Category.class);
    private final Map<String, SliderState> sliders = new HashMap<>();
    private final StringBuilder search = new StringBuilder();

    private Category selectedCategory = Category.VISUALS;
    private String draggingSlider;
    private String draggingCard;
    private boolean searchOpen;
    private boolean showTargetEsp = rememberedTargetEspCard;
    private boolean showChinaHat = rememberedChinaHatCard;
    private boolean showWings = rememberedWingsCard;
    private boolean showAspectRatio = rememberedAspectRatioCard;
    private boolean showRemovals = rememberedRemovalsCard;
    private boolean showParticles = rememberedParticlesCard;
    private boolean showJumpCircles = rememberedJumpCirclesCard;
    private boolean showTrails = rememberedTrailsCard;
    private boolean showWorldCustomizer = rememberedWorldCustomizerCard;
    private boolean showHitColor = rememberedHitColorCard;
    private boolean showHitboxCustomizer = rememberedHitboxCustomizerCard;
    private boolean showBlockOverlay = rememberedBlockOverlayCard;
    private boolean showItemRadius = rememberedItemRadiusCard;
    private boolean showFreeLook = rememberedFreeLookCard;
    private boolean showZoom = rememberedZoomCard;
    private boolean showAutoSwap = rememberedAutoSwapCard;
    private boolean showElytraSwap = rememberedElytraSwapCard;
    private boolean showItemResorter = rememberedItemResorterCard;
    private boolean showAutoBuy = rememberedAutoBuyCard;
    private boolean showTelegram = rememberedTelegramCard;
    private boolean showAutoResellAFK;
    private boolean showCrosshair = rememberedCrosshairCard;
    private boolean showAnimations = rememberedAnimationsCard;
    private boolean showTabCustomizer = rememberedTabCustomizerCard;
    private float targetEspCardProgress = showTargetEsp ? 1.0F : 0.0F;
    private float chinaHatCardProgress = showChinaHat ? 1.0F : 0.0F;
    private float wingsCardProgress = showWings ? 1.0F : 0.0F;
    private float aspectRatioCardProgress = showAspectRatio ? 1.0F : 0.0F;
    private float removalsCardProgress = showRemovals ? 1.0F : 0.0F;
    private float particlesCardProgress = showParticles ? 1.0F : 0.0F;
    private float jumpCirclesCardProgress = showJumpCircles ? 1.0F : 0.0F;
    private float trailsCardProgress = showTrails ? 1.0F : 0.0F;
    private float worldCustomizerCardProgress = showWorldCustomizer ? 1.0F : 0.0F;
    private float hitColorCardProgress = showHitColor ? 1.0F : 0.0F;
    private float hitboxCustomizerCardProgress = showHitboxCustomizer ? 1.0F : 0.0F;
    private float blockOverlayCardProgress = showBlockOverlay ? 1.0F : 0.0F;
    private float itemRadiusCardProgress = showItemRadius ? 1.0F : 0.0F;
    private float freeLookCardProgress = showFreeLook ? 1.0F : 0.0F;
    private float zoomCardProgress = showZoom ? 1.0F : 0.0F;
    private float autoSwapCardProgress = showAutoSwap ? 1.0F : 0.0F;
    private float elytraSwapCardProgress = showElytraSwap ? 1.0F : 0.0F;
    private float itemResorterCardProgress;
    private float autoBuyCardProgress = showAutoBuy ? 1.0F : 0.0F;
    private float telegramCardProgress = showTelegram ? 1.0F : 0.0F;
    private float autoResellAfkCardProgress;
    private boolean showTargetHud = rememberedTargetHudCard;
    private boolean showTapeMouse = rememberedTapeMouseCard;
    private boolean showAnarchySwitcher = rememberedAnarchySwitcherCard;
    private boolean showNameBinder = rememberedNameBinderCard;
    private boolean showMacros = rememberedMacrosCard;
    private float targetHudCardProgress = showTargetHud ? 1.0F : 0.0F;
    private float tapeMouseCardProgress = showTapeMouse ? 1.0F : 0.0F;
    private float anarchySwitcherCardProgress = showAnarchySwitcher ? 1.0F : 0.0F;
    private float nameBinderCardProgress = showNameBinder ? 1.0F : 0.0F;
    private float macrosCardProgress = showMacros ? 1.0F : 0.0F;
    private float crosshairCardProgress = showCrosshair ? 1.0F : 0.0F;
    private float animationsCardProgress = showAnimations ? 1.0F : 0.0F;
    private float tabCustomizerCardProgress = showTabCustomizer ? 1.0F : 0.0F;
    private float aspectCustomProgress;
    private float searchProgress;
    private boolean chinaPaletteOpen;
    private boolean targetStyleOpen;
    private boolean targetFilterOpen;
    private boolean targetColorOpen;
    private boolean chinaFillModeOpen;
    private boolean wingsTypeOpen;
    private boolean chinaShaderOpen;
    private float chinaFillModeProgress;
    private float chinaShaderProgress;
    private boolean aspectPresetOpen;
    private float aspectPresetProgress;
    private float aspectPresetScroll;
    private boolean worldTimePresetOpen;
    private boolean worldFogPaletteOpen;
    private float worldTimePresetProgress;
    private float worldCustomTimeProgress;
    private boolean particleTextureOpen;
    private boolean particleMultiOpen;
    private boolean jumpCircleModeOpen;
    private boolean jumpCircleTextureOpen;
    private boolean jumpCircleParticleTextureOpen;
    private boolean jumpCircleColorOpen;
    private boolean particlesColorOpen;
    private boolean trailsColorOpen;
    private boolean hitColorPaletteOpen;
    private boolean hitboxOutlinePaletteOpen;
    private boolean hitboxFillPaletteOpen;
    private boolean hitboxTargetsOpen;
    private boolean blockOverlayOutlinePaletteOpen;
    private boolean blockOverlayFillPaletteOpen;
    private boolean blockOverlayFillModeOpen;
    private boolean blockOverlayShaderOpen;
    private boolean crosshairPresetOpen;
    private boolean crosshairColorOpen;
    private boolean freeLookModeOpen;
    private boolean autoSwapFirstOpen;
    private boolean autoSwapSecondOpen;
    private boolean itemResorterEnchantColorOpen;
    private boolean itemResorterBuffColorOpen;
    private boolean tapeMouseButtonOpen;
    private float crosshairPresetProgress;
    private float freeLookModeProgress;
    private float autoSwapFirstProgress;
    private float autoSwapSecondProgress;
    private float hitboxTargetsProgress;
    private float targetStyleProgress;
    private float targetFilterProgress;
    private float blockOverlayFillModeProgress;
    private float blockOverlayShaderProgress;
    private float particleTextureProgress;
    private float particleMultiProgress;
    private float jumpCircleModeProgress;
    private float jumpCircleTextureProgress;
    private float jumpCircleParticleTextureProgress;
    private float particlesOutlineProgress;
    private float particlesScroll;
    private float moduleScroll;
    private VisualModule bindingTarget;
    private String bindingOptionTarget;
    private float bindOverlayProgress;
    private float dragOffsetX;
    private float dragOffsetY;
    private float targetCardX = rememberedTargetX;
    private float targetCardY = rememberedTargetY;
    private float chinaCardX = rememberedChinaX;
    private float chinaCardY = rememberedChinaY;
    private float wingsCardX = rememberedWingsX;
    private float wingsCardY = rememberedWingsY;
    private float aspectCardX = rememberedAspectX;
    private float aspectCardY = rememberedAspectY;
    private float removalsCardX = rememberedRemovalsX;
    private float removalsCardY = rememberedRemovalsY;
    private float particlesCardX = rememberedParticlesX;
    private float particlesCardY = rememberedParticlesY;
    private float jumpCirclesCardX = rememberedJumpCirclesX;
    private float jumpCirclesCardY = rememberedJumpCirclesY;
    private float trailsCardX = rememberedTrailsX;
    private float trailsCardY = rememberedTrailsY;
    private float worldCustomizerCardX = rememberedWorldCustomizerX;
    private float worldCustomizerCardY = rememberedWorldCustomizerY;
    private float hitColorCardX = rememberedHitColorX;
    private float hitColorCardY = rememberedHitColorY;
    private float hitboxCustomizerCardX = rememberedHitboxX;
    private float hitboxCustomizerCardY = rememberedHitboxY;
    private float blockOverlayCardX = rememberedBlockOverlayX;
    private float blockOverlayCardY = rememberedBlockOverlayY;
    private float itemRadiusCardX = rememberedItemRadiusX;
    private float itemRadiusCardY = rememberedItemRadiusY;
    private float freeLookCardX = rememberedFreeLookX;
    private float freeLookCardY = rememberedFreeLookY;
    private float zoomCardX = rememberedZoomX;
    private float zoomCardY = rememberedZoomY;
    private float autoSwapCardX = rememberedAutoSwapX;
    private float autoSwapCardY = rememberedAutoSwapY;
    private float elytraSwapCardX = rememberedElytraSwapX;
    private float elytraSwapCardY = rememberedElytraSwapY;
    private float itemResorterCardX = rememberedItemResorterX;
    private float itemResorterCardY = rememberedItemResorterY;
    private float autoBuyCardX = rememberedAutoBuyX;
    private float autoBuyCardY = rememberedAutoBuyY;
    private float autoResellAfkCardX = 360.0F;
    private float autoResellAfkCardY = 142.0F;
    private float telegramCardX = rememberedTelegramX;
    private float telegramCardY = rememberedTelegramY;
    private float targetHudCardX = rememberedTargetHudX;
    private float targetHudCardY = rememberedTargetHudY;
    private float tapeMouseCardX = rememberedTapeMouseX;
    private float tapeMouseCardY = rememberedTapeMouseY;
    private float anarchySwitcherCardX = rememberedAnarchySwitcherX;
    private float anarchySwitcherCardY = rememberedAnarchySwitcherY;
    private float nameBinderCardX = rememberedNameBinderX;
    private float nameBinderCardY = rememberedNameBinderY;
    private float macrosCardX = rememberedMacrosX;
    private float macrosCardY = rememberedMacrosY;
    private float crosshairCardX = rememberedCrosshairX;
    private float crosshairCardY = rememberedCrosshairY;
    private ItemResorterTab itemResorterTab = ItemResorterTab.ENCHANTS;
    private ItemResorterInput itemResorterFocusedInput;
    private boolean itemResorterPriceFocused;
    private final StringBuilder itemResorterEnchantNeededDraft = new StringBuilder();
    private final StringBuilder itemResorterEnchantIgnoredDraft = new StringBuilder();
    private final StringBuilder itemResorterBuffNeededDraft = new StringBuilder();
    private final StringBuilder itemResorterBuffIgnoredDraft = new StringBuilder();
    private final StringBuilder itemResorterPriceDraft = new StringBuilder();
    private boolean anarchyInputFocused;
    private boolean anarchyAdFocused;
    private boolean autoBuyAnarchyInputFocused;
    private boolean autoBuyAnarchyAdFocused;
    private boolean autoBuySellerBanFocused;
    private boolean telegramTokenFocused;
    private boolean telegramChatFocused;
    private boolean nameBinderInputFocused;
    private boolean macrosInputFocused;
    private boolean autoBuyNameFocused;
    private boolean autoResellAfkChatFocused;
    private boolean autoResellAfkPriceFocused;
    private final StringBuilder anarchyDraft = new StringBuilder();
    private final StringBuilder anarchyAdDraft = new StringBuilder();
    private final StringBuilder autoBuyAnarchyDraft = new StringBuilder();
    private final StringBuilder autoBuyAnarchyAdDraft = new StringBuilder();
    private final StringBuilder autoBuySellerBanDraft = new StringBuilder();
    private final StringBuilder nameBinderDraft = new StringBuilder();
    private final StringBuilder macrosDraft = new StringBuilder();
    private final StringBuilder autoBuyNameDraft = new StringBuilder();
    private final StringBuilder autoResellAfkChatDraft = new StringBuilder();
    private final StringBuilder autoResellAfkPriceDraft = new StringBuilder();
    private final StringBuilder telegramTokenDraft = new StringBuilder();
    private final StringBuilder telegramChatDraft = new StringBuilder();
    private float animationsCardX = rememberedAnimationsX;
    private float animationsCardY = rememberedAnimationsY;
    private float tabCustomizerCardX = rememberedTabCustomizerX;
    private float tabCustomizerCardY = rememberedTabCustomizerY;
private float clickGuiCardX = MAIN_X;
private float clickGuiCardY = MAIN_Y;
    private long openedAt;
    private float originX;
    private float originY;
    private float scale = 1.0F;

    PremiumClickGuiRenderer() {
        for (Category category : Category.values()) {
            tabHover.put(category, 0.0F);
        }

        modules.add(new VisualModule("FullBright", "\u0414\u0430\u0451\u0442 Night Vision", Category.VISUALS, true));
        modules.add(new VisualModule("Particles", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0447\u0430\u0441\u0442\u0438\u0446", Category.VISUALS, true));
        modules.add(new VisualModule("JumpCircles", "\u041a\u0440\u0443\u0433\u0438 \u0438 \u0432\u043e\u043b\u043d\u044b \u043f\u0440\u0438 \u043f\u0440\u044b\u0436\u043a\u0435", Category.VISUALS, false));
        modules.add(new VisualModule("Trails", "\u0421\u043b\u0435\u0434 \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u043e\u043c", Category.VISUALS, false));
        modules.add(new VisualModule("Removals", "\u0423\u0434\u0430\u043b\u044f\u0435\u0442 \u043b\u0438\u0448\u043d\u0438\u0435 \u044d\u0444\u0444\u0435\u043a\u0442\u044b", Category.VISUALS, false));
        modules.add(new VisualModule("China Hat", "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u0430\u044f \u0448\u043b\u044f\u043f\u0430", Category.VISUALS, false));
        modules.add(new VisualModule("Wings", "Original shader wings", Category.VISUALS, false));
        modules.add(new VisualModule("Aspect Ratio", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u0440\u043e\u043f\u043e\u0440\u0446\u0438\u0439", Category.VISUALS, false));
        modules.add(new VisualModule("TargetEsp", "\u041f\u043e\u0434\u0441\u0432\u0435\u0442\u043a\u0430 \u0446\u0435\u043b\u0438", Category.VISUALS, false));
        modules.add(new VisualModule("World Customizer", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043c\u0438\u0440\u0430", Category.VISUALS, false));
        modules.add(new VisualModule("Hit Color", "\u0426\u0432\u0435\u0442 \u0443\u0434\u0430\u0440\u0430", Category.VISUALS, true));
        modules.add(new VisualModule("Hitbox Customizer", "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0435 \u0445\u0438\u0442\u0431\u043e\u043a\u0441\u044b", Category.VISUALS, false));
        modules.add(new VisualModule("BlockOverlay", "\u041e\u0431\u0432\u043e\u0434\u043a\u0430 \u043d\u0430\u0432\u0435\u0434\u0451\u043d\u043d\u043e\u0433\u043e \u0431\u043b\u043e\u043a\u0430", Category.VISUALS, false));
        modules.add(new VisualModule("ItemRadius", "\u0420\u0430\u0434\u0438\u0443\u0441\u044b \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432", Category.VISUALS, false));
        modules.add(new VisualModule("Animations", "\u041f\u043b\u0430\u0432\u043d\u044b\u0435 \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0438 UI \u0438 \u043a\u0430\u043c\u0435\u0440\u044b", Category.VISUALS, false));
        modules.add(new VisualModule("TabCustomizer", "\u041a\u043e\u043b\u043e\u043d\u043a\u0438 \u0438 \u043f\u043b\u043e\u0442\u043d\u043e\u0441\u0442\u044c Tab", Category.VISUALS, false));

        modules.add(new VisualModule("Watermark", "\u0412\u0435\u0440\u0445\u043d\u0438\u0439 FPS/Ping \u0431\u0435\u0439\u0434\u0436", Category.HUD, false));
        modules.add(new VisualModule("TargetHud", "Target hp hud", Category.HUD, true));
        modules.add(new VisualModule("Crosshair", "\u041a\u0430\u0441\u0442\u043e\u043c\u043d\u044b\u0439 \u043f\u0440\u0438\u0446\u0435\u043b", Category.VISUALS, true));
        modules.add(new VisualModule("FreeLook", "\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u0430\u044f \u043a\u0430\u043c\u0435\u0440\u0430", Category.VISUALS, false));
        modules.add(new VisualModule("Zoom", "\u041f\u043b\u0430\u0432\u043d\u043e\u0435 \u043f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435", Category.VISUALS, false));

        modules.add(new VisualModule("SafeNametag", "\u0412\u0430\u043d\u0438\u043b\u044c\u043d\u044b\u0435 \u043d\u0438\u043a\u0438 \u0438 \u0437\u0434\u043e\u0440\u043e\u0432\u044c\u0435", Category.VISUALS, false));
        modules.add(new VisualModule("FakePlayer", "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 \u0438\u0433\u0440\u043e\u043a-\u043c\u0430\u043d\u0435\u043a\u0435\u043d", Category.UTILS, false));
        modules.add(new VisualModule("AutoSprint", "\u041e\u0431\u044b\u0447\u043d\u044b\u0439 \u0430\u0432\u0442\u043e-\u0441\u043f\u0440\u0438\u043d\u0442", Category.UTILS, false));
        modules.add(new VisualModule("ItemSwap", "\u0421\u0432\u0430\u043f \u0441\u0444\u0435\u0440\u044b \u0438 \u0442\u0430\u043b\u0438\u0441\u043c\u0430\u043d\u0430 \u0432 \u043b\u0435\u0432\u0443\u044e \u0440\u0443\u043a\u0443", Category.UTILS, false));
        modules.add(new VisualModule("ElytraSwap", "\u0421\u0432\u0430\u043f \u044d\u043b\u0438\u0442\u0440 \u0438 \u043d\u0430\u0433\u0440\u0443\u0434\u043d\u0438\u043a\u0430 \u043f\u043e \u0431\u0438\u043d\u0434\u0443", Category.UTILS, false));
        modules.add(new VisualModule("TrapTracker", "\u0422\u0430\u0439\u043c\u0435\u0440 \u0442\u0440\u0430\u043f\u043a\u0438 \u043f\u043e \u043d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u043e\u043c\u0443 \u0441\u043a\u0440\u0430\u043f\u0443", Category.UTILS, false));
        modules.add(new VisualModule("ItemResorter", "Tooltip item sorter", Category.UTILS, false));
        modules.add(new VisualModule("NameProtect", "РЎРєСЂС‹РІР°РµС‚ РЅРёРє РёРіСЂРѕРєР° Рё Р±РѕС‚РѕРІ", Category.UTILS, false));
        modules.add(new VisualModule("ItemScroller", "Shift + РєРѕР»РµСЃРѕ РїРµСЂРµРјРµС‰Р°РµС‚ РїСЂРµРґРјРµС‚С‹", Category.UTILS, false));
        modules.add(new VisualModule("AutoBuy", "Auction sword buyer", Category.UTILS, false));
        modules.add(new VisualModule("AutoResellAFK", "Resell storage every 60 seconds", Category.UTILS, false));
        modules.add(new VisualModule("AHHelper", "Highlights the three cheapest priced items", Category.UTILS, false));
        modules.add(new VisualModule("TG", "\u0423\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f \u0432 Telegram", Category.UTILS, false));
        modules.add(new VisualModule("DiscordRPC", "Discord Rich Presence", Category.UTILS, true));
        modules.add(new VisualModule("AnarchySwitcher", "Cycle /an command", Category.UTILS, false));
        modules.add(new VisualModule("NameBinder", "Bind /name text", Category.UTILS, false));
        modules.add(new VisualModule("Macros", "Bind chat text", Category.UTILS, false));
        modules.add(new VisualModule("TapeMouse", "Auto click helper", Category.UTILS, false));
        modules.add(new VisualModule("AutoResell", "Refresh /ah resell", Category.UTILS, false));
        modules.add(new VisualModule("CaptchaSolver", "Р РµС€Р°РµС‚ РєР°РїС‡Сѓ СЃ РєР°СЂС‚РёРЅРєРё (YOLO)", Category.UTILS, false));
    }

    public static void loadConfig(Properties properties) {
        rememberedTargetEspCard = bool(properties, "gui.target_esp_card", rememberedTargetEspCard);
        rememberedChinaHatCard = bool(properties, "gui.china_hat_card", rememberedChinaHatCard);
        rememberedWingsCard = bool(properties, "gui.wings_card", rememberedWingsCard);
        rememberedAspectRatioCard = bool(properties, "gui.aspect_ratio_card", rememberedAspectRatioCard);
        rememberedRemovalsCard = bool(properties, "gui.removals_card", rememberedRemovalsCard);
        rememberedParticlesCard = bool(properties, "gui.particles_card", rememberedParticlesCard);
        rememberedJumpCirclesCard = bool(properties, "gui.jump_circles_card", rememberedJumpCirclesCard);
        rememberedTrailsCard = bool(properties, "gui.trails_card", rememberedTrailsCard);
        rememberedWorldCustomizerCard = bool(properties, "gui.world_customizer_card", rememberedWorldCustomizerCard);
        rememberedHitColorCard = bool(properties, "gui.hit_color_card", rememberedHitColorCard);
        rememberedHitboxCustomizerCard = bool(properties, "gui.hitbox_customizer_card", rememberedHitboxCustomizerCard);
        rememberedBlockOverlayCard = bool(properties, "gui.block_overlay_card", rememberedBlockOverlayCard);
        rememberedItemRadiusCard = bool(properties, "gui.item_radius_card", rememberedItemRadiusCard);
        rememberedFreeLookCard = bool(properties, "gui.free_look_card", rememberedFreeLookCard);
        rememberedZoomCard = bool(properties, "gui.zoom_card", rememberedZoomCard);
        rememberedAutoSwapCard = bool(properties, "gui.auto_swap_card", rememberedAutoSwapCard);
        rememberedElytraSwapCard = bool(properties, "gui.elytra_swap_card", rememberedElytraSwapCard);
        rememberedItemResorterCard = bool(properties, "gui.item_resorter_card", rememberedItemResorterCard);
        rememberedAutoBuyCard = bool(properties, "gui.auto_buy_card", rememberedAutoBuyCard);
        rememberedTelegramCard = bool(properties, "gui.tg_card", rememberedTelegramCard);
        rememberedTargetHudCard = bool(properties, "gui.target_hud_card", rememberedTargetHudCard);
        rememberedTapeMouseCard = bool(properties, "gui.tape_mouse_card", rememberedTapeMouseCard);
        rememberedAnarchySwitcherCard = bool(properties, "gui.anarchy_switcher_card", rememberedAnarchySwitcherCard);
        rememberedNameBinderCard = bool(properties, "gui.name_binder_card", rememberedNameBinderCard);
        rememberedMacrosCard = bool(properties, "gui.macros_card", rememberedMacrosCard);
        rememberedCrosshairCard = bool(properties, "gui.crosshair_card", rememberedCrosshairCard);
        rememberedAnimationsCard = bool(properties, "gui.animations_card", rememberedAnimationsCard);
        rememberedTabCustomizerCard = bool(properties, "gui.tab_customizer_card", rememberedTabCustomizerCard);
        rememberedTargetX = number(properties, "gui.target_x", rememberedTargetX);
        rememberedTargetY = number(properties, "gui.target_y", rememberedTargetY);
        rememberedChinaX = number(properties, "gui.china_x", rememberedChinaX);
        rememberedChinaY = number(properties, "gui.china_y", rememberedChinaY);
        rememberedWingsX = number(properties, "gui.wings_x", rememberedWingsX);
        rememberedWingsY = number(properties, "gui.wings_y", rememberedWingsY);
        rememberedAspectX = number(properties, "gui.aspect_x", rememberedAspectX);
        rememberedAspectY = number(properties, "gui.aspect_y", rememberedAspectY);
        rememberedRemovalsX = number(properties, "gui.removals_x", rememberedRemovalsX);
        rememberedRemovalsY = number(properties, "gui.removals_y", rememberedRemovalsY);
        rememberedParticlesX = number(properties, "gui.particles_x", rememberedParticlesX);
        rememberedParticlesY = number(properties, "gui.particles_y", rememberedParticlesY);
        rememberedJumpCirclesX = number(properties, "gui.jump_circles_x", rememberedJumpCirclesX);
        rememberedJumpCirclesY = number(properties, "gui.jump_circles_y", rememberedJumpCirclesY);
        rememberedTrailsX = number(properties, "gui.trails_x", rememberedTrailsX);
        rememberedTrailsY = number(properties, "gui.trails_y", rememberedTrailsY);
        rememberedWorldCustomizerX = number(properties, "gui.world_customizer_x", rememberedWorldCustomizerX);
        rememberedWorldCustomizerY = number(properties, "gui.world_customizer_y", rememberedWorldCustomizerY);
        rememberedHitColorX = number(properties, "gui.hit_color_x", rememberedHitColorX);
        rememberedHitColorY = number(properties, "gui.hit_color_y", rememberedHitColorY);
        rememberedHitboxX = number(properties, "gui.hitbox_x", rememberedHitboxX);
        rememberedHitboxY = number(properties, "gui.hitbox_y", rememberedHitboxY);
        rememberedBlockOverlayX = number(properties, "gui.block_overlay_x", rememberedBlockOverlayX);
        rememberedBlockOverlayY = number(properties, "gui.block_overlay_y", rememberedBlockOverlayY);
        rememberedItemRadiusX = number(properties, "gui.item_radius_x", rememberedItemRadiusX);
        rememberedItemRadiusY = number(properties, "gui.item_radius_y", rememberedItemRadiusY);
        rememberedFreeLookX = number(properties, "gui.free_look_x", rememberedFreeLookX);
        rememberedFreeLookY = number(properties, "gui.free_look_y", rememberedFreeLookY);
        rememberedZoomX = number(properties, "gui.zoom_x", rememberedZoomX);
        rememberedZoomY = number(properties, "gui.zoom_y", rememberedZoomY);
        rememberedAutoSwapX = number(properties, "gui.auto_swap_x", rememberedAutoSwapX);
        rememberedAutoSwapY = number(properties, "gui.auto_swap_y", rememberedAutoSwapY);
        rememberedElytraSwapX = number(properties, "gui.elytra_swap_x", rememberedElytraSwapX);
        rememberedElytraSwapY = number(properties, "gui.elytra_swap_y", rememberedElytraSwapY);
        rememberedItemResorterX = number(properties, "gui.item_resorter_x", rememberedItemResorterX);
        rememberedItemResorterY = number(properties, "gui.item_resorter_y", rememberedItemResorterY);
        rememberedAutoBuyX = number(properties, "gui.auto_buy_x", rememberedAutoBuyX);
        rememberedAutoBuyY = number(properties, "gui.auto_buy_y", rememberedAutoBuyY);
        rememberedTelegramX = number(properties, "gui.tg_x", rememberedTelegramX);
        rememberedTelegramY = number(properties, "gui.tg_y", rememberedTelegramY);
        rememberedTargetHudX = number(properties, "gui.target_hud_x", rememberedTargetHudX);
        rememberedTargetHudY = number(properties, "gui.target_hud_y", rememberedTargetHudY);
        rememberedTapeMouseX = number(properties, "gui.tape_mouse_x", rememberedTapeMouseX);
        rememberedTapeMouseY = number(properties, "gui.tape_mouse_y", rememberedTapeMouseY);
        rememberedAnarchySwitcherX = number(properties, "gui.anarchy_switcher_x", rememberedAnarchySwitcherX);
        rememberedAnarchySwitcherY = number(properties, "gui.anarchy_switcher_y", rememberedAnarchySwitcherY);
        rememberedNameBinderX = number(properties, "gui.name_binder_x", rememberedNameBinderX);
        rememberedNameBinderY = number(properties, "gui.name_binder_y", rememberedNameBinderY);
        rememberedMacrosX = number(properties, "gui.macros_x", rememberedMacrosX);
        rememberedMacrosY = number(properties, "gui.macros_y", rememberedMacrosY);
        rememberedCrosshairX = number(properties, "gui.crosshair_x", rememberedCrosshairX);
        rememberedCrosshairY = number(properties, "gui.crosshair_y", rememberedCrosshairY);
        rememberedAnimationsX = number(properties, "gui.animations_x", rememberedAnimationsX);
        rememberedAnimationsY = number(properties, "gui.animations_y", rememberedAnimationsY);
        rememberedTabCustomizerX = number(properties, "gui.tab_customizer_x", rememberedTabCustomizerX);
        rememberedTabCustomizerY = number(properties, "gui.tab_customizer_y", rememberedTabCustomizerY);
        REMEMBERED_MODULE_STATES.clear();
        REMEMBERED_BINDS.clear();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("gui.module.")) {
                REMEMBERED_MODULE_STATES.put(key.substring("gui.module.".length()), Boolean.parseBoolean(properties.getProperty(key)));
            } else if (key.startsWith("gui.bind.")) {
                try {
                    REMEMBERED_BINDS.put(key.substring("gui.bind.".length()), Integer.parseInt(properties.getProperty(key)));
                } catch (NumberFormatException ignored) {
                    // Ignore broken bind values and keep the menu usable.
                }
            }
        }
        REMEMBERED_BINDS.putIfAbsent(FreeLook.PERSPECTIVE_BIND_KEY, GLFW.GLFW_KEY_LEFT_ALT);
        REMEMBERED_BINDS.putIfAbsent(Zoom.BIND_KEY, GLFW.GLFW_KEY_C);
        REMEMBERED_BINDS.putIfAbsent(AutoSwap.BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
        REMEMBERED_BINDS.putIfAbsent(ElytraSwap.BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
        REMEMBERED_BINDS.putIfAbsent(ElytraSwap.FIREWORK_BIND_KEY, GLFW.GLFW_KEY_UNKNOWN);
    }

    public static void saveConfig(Properties properties) {
        properties.setProperty("gui.target_esp_card", Boolean.toString(rememberedTargetEspCard));
        properties.setProperty("gui.china_hat_card", Boolean.toString(rememberedChinaHatCard));
        properties.setProperty("gui.wings_card", Boolean.toString(rememberedWingsCard));
        properties.setProperty("gui.aspect_ratio_card", Boolean.toString(rememberedAspectRatioCard));
        properties.setProperty("gui.removals_card", Boolean.toString(rememberedRemovalsCard));
        properties.setProperty("gui.particles_card", Boolean.toString(rememberedParticlesCard));
        properties.setProperty("gui.jump_circles_card", Boolean.toString(rememberedJumpCirclesCard));
        properties.setProperty("gui.trails_card", Boolean.toString(rememberedTrailsCard));
        properties.setProperty("gui.world_customizer_card", Boolean.toString(rememberedWorldCustomizerCard));
        properties.setProperty("gui.hit_color_card", Boolean.toString(rememberedHitColorCard));
        properties.setProperty("gui.hitbox_customizer_card", Boolean.toString(rememberedHitboxCustomizerCard));
        properties.setProperty("gui.block_overlay_card", Boolean.toString(rememberedBlockOverlayCard));
        properties.setProperty("gui.item_radius_card", Boolean.toString(rememberedItemRadiusCard));
        properties.setProperty("gui.free_look_card", Boolean.toString(rememberedFreeLookCard));
        properties.setProperty("gui.zoom_card", Boolean.toString(rememberedZoomCard));
        properties.setProperty("gui.auto_swap_card", Boolean.toString(rememberedAutoSwapCard));
        properties.setProperty("gui.elytra_swap_card", Boolean.toString(rememberedElytraSwapCard));
        properties.setProperty("gui.item_resorter_card", Boolean.toString(rememberedItemResorterCard));
        properties.setProperty("gui.auto_buy_card", Boolean.toString(rememberedAutoBuyCard));
        properties.setProperty("gui.tg_card", Boolean.toString(rememberedTelegramCard));
        properties.setProperty("gui.target_hud_card", Boolean.toString(rememberedTargetHudCard));
        properties.setProperty("gui.tape_mouse_card", Boolean.toString(rememberedTapeMouseCard));
        properties.setProperty("gui.anarchy_switcher_card", Boolean.toString(rememberedAnarchySwitcherCard));
        properties.setProperty("gui.name_binder_card", Boolean.toString(rememberedNameBinderCard));
        properties.setProperty("gui.macros_card", Boolean.toString(rememberedMacrosCard));
        properties.setProperty("gui.crosshair_card", Boolean.toString(rememberedCrosshairCard));
        properties.setProperty("gui.animations_card", Boolean.toString(rememberedAnimationsCard));
        properties.setProperty("gui.tab_customizer_card", Boolean.toString(rememberedTabCustomizerCard));
        properties.setProperty("gui.target_x", number(rememberedTargetX));
        properties.setProperty("gui.target_y", number(rememberedTargetY));
        properties.setProperty("gui.china_x", number(rememberedChinaX));
        properties.setProperty("gui.china_y", number(rememberedChinaY));
        properties.setProperty("gui.wings_x", number(rememberedWingsX));
        properties.setProperty("gui.wings_y", number(rememberedWingsY));
        properties.setProperty("gui.aspect_x", number(rememberedAspectX));
        properties.setProperty("gui.aspect_y", number(rememberedAspectY));
        properties.setProperty("gui.removals_x", number(rememberedRemovalsX));
        properties.setProperty("gui.removals_y", number(rememberedRemovalsY));
        properties.setProperty("gui.particles_x", number(rememberedParticlesX));
        properties.setProperty("gui.particles_y", number(rememberedParticlesY));
        properties.setProperty("gui.jump_circles_x", number(rememberedJumpCirclesX));
        properties.setProperty("gui.jump_circles_y", number(rememberedJumpCirclesY));
        properties.setProperty("gui.trails_x", number(rememberedTrailsX));
        properties.setProperty("gui.trails_y", number(rememberedTrailsY));
        properties.setProperty("gui.world_customizer_x", number(rememberedWorldCustomizerX));
        properties.setProperty("gui.world_customizer_y", number(rememberedWorldCustomizerY));
        properties.setProperty("gui.hit_color_x", number(rememberedHitColorX));
        properties.setProperty("gui.hit_color_y", number(rememberedHitColorY));
        properties.setProperty("gui.hitbox_x", number(rememberedHitboxX));
        properties.setProperty("gui.hitbox_y", number(rememberedHitboxY));
        properties.setProperty("gui.block_overlay_x", number(rememberedBlockOverlayX));
        properties.setProperty("gui.block_overlay_y", number(rememberedBlockOverlayY));
        properties.setProperty("gui.item_radius_x", number(rememberedItemRadiusX));
        properties.setProperty("gui.item_radius_y", number(rememberedItemRadiusY));
        properties.setProperty("gui.free_look_x", number(rememberedFreeLookX));
        properties.setProperty("gui.free_look_y", number(rememberedFreeLookY));
        properties.setProperty("gui.zoom_x", number(rememberedZoomX));
        properties.setProperty("gui.zoom_y", number(rememberedZoomY));
        properties.setProperty("gui.auto_swap_x", number(rememberedAutoSwapX));
        properties.setProperty("gui.auto_swap_y", number(rememberedAutoSwapY));
        properties.setProperty("gui.elytra_swap_x", number(rememberedElytraSwapX));
        properties.setProperty("gui.elytra_swap_y", number(rememberedElytraSwapY));
        properties.setProperty("gui.item_resorter_x", number(rememberedItemResorterX));
        properties.setProperty("gui.item_resorter_y", number(rememberedItemResorterY));
        properties.setProperty("gui.auto_buy_x", number(rememberedAutoBuyX));
        properties.setProperty("gui.auto_buy_y", number(rememberedAutoBuyY));
        properties.setProperty("gui.tg_x", number(rememberedTelegramX));
        properties.setProperty("gui.tg_y", number(rememberedTelegramY));
        properties.setProperty("gui.target_hud_x", number(rememberedTargetHudX));
        properties.setProperty("gui.target_hud_y", number(rememberedTargetHudY));
        properties.setProperty("gui.tape_mouse_x", number(rememberedTapeMouseX));
        properties.setProperty("gui.tape_mouse_y", number(rememberedTapeMouseY));
        properties.setProperty("gui.anarchy_switcher_x", number(rememberedAnarchySwitcherX));
        properties.setProperty("gui.anarchy_switcher_y", number(rememberedAnarchySwitcherY));
        properties.setProperty("gui.name_binder_x", number(rememberedNameBinderX));
        properties.setProperty("gui.name_binder_y", number(rememberedNameBinderY));
        properties.setProperty("gui.macros_x", number(rememberedMacrosX));
        properties.setProperty("gui.macros_y", number(rememberedMacrosY));
        properties.setProperty("gui.crosshair_x", number(rememberedCrosshairX));
        properties.setProperty("gui.crosshair_y", number(rememberedCrosshairY));
        properties.setProperty("gui.animations_x", number(rememberedAnimationsX));
        properties.setProperty("gui.animations_y", number(rememberedAnimationsY));
        properties.setProperty("gui.tab_customizer_x", number(rememberedTabCustomizerX));
        properties.setProperty("gui.tab_customizer_y", number(rememberedTabCustomizerY));
        for (Map.Entry<String, Boolean> entry : REMEMBERED_MODULE_STATES.entrySet()) {
            properties.setProperty("gui.module." + entry.getKey(), Boolean.toString(entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : REMEMBERED_BINDS.entrySet()) {
            properties.setProperty("gui.bind." + entry.getKey(), Integer.toString(entry.getValue()));
        }
    }

    public static void handleBinds(MinecraftClient client) {
        if (client == null || client.getWindow() == null || REMEMBERED_BINDS.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getZoom().handleBindState(false);
            return;
        }

        boolean screenOpen = client.currentScreen != null;
        boolean clientGuiOpen = client.currentScreen instanceof ClickGuiScreen
                || client.currentScreen instanceof dev.fuga.fluxvisuals.gui.modern.ModernClickGuiScreen;
        long window = client.getWindow().getHandle();
        Map<Integer, Boolean> pressedNow = new HashMap<>();
        for (int bindCode : REMEMBERED_BINDS.values()) {
            if (bindCode != GLFW.GLFW_KEY_UNKNOWN) {
                pressedNow.put(bindCode, isBindDown(window, bindCode));
            }
        }

        for (Map.Entry<String, Integer> entry : REMEMBERED_BINDS.entrySet()) {
            if (FreeLook.PERSPECTIVE_BIND_KEY.equals(entry.getKey()) || Zoom.BIND_KEY.equals(entry.getKey())) {
                continue;
            }
            int bindCode = entry.getValue();
            boolean pressed = pressedNow.getOrDefault(bindCode, false);
            if (pressed && !BIND_KEY_DOWN.getOrDefault(bindCode, false)) {
                if (!screenOpen || clientGuiOpen) {
                    toggleBoundAction(entry.getKey());
                } else if ("AutoBuy".equals(entry.getKey())
                        && FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                        FluxVisualsClient.MODULE_MANAGER.getAutoBuy()).isEnabled()) {
                    setModuleState("AutoBuy", false);
                }
            }
        }

        int zoomBind = REMEMBERED_BINDS.getOrDefault(Zoom.BIND_KEY, GLFW.GLFW_KEY_C);
        FluxVisualsClient.MODULE_MANAGER.getZoom().handleBindState(LicenseManager.isLicensed && !screenOpen && pressedNow.getOrDefault(zoomBind, false));

        for (Map.Entry<Integer, Boolean> entry : pressedNow.entrySet()) {
            BIND_KEY_DOWN.put(entry.getKey(), entry.getValue());
        }
    }

    public static int getBind(String key, int fallback) {
        return REMEMBERED_BINDS.getOrDefault(key, fallback);
    }

    public static void setBind(String key, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0) {
            REMEMBERED_BINDS.remove(key);
        } else {
            REMEMBERED_BINDS.put(key, keyCode);
        }
    }

    public static boolean isBindDown(MinecraftClient client, int bindCode) {
        return client != null && client.getWindow() != null && isBindDown(client.getWindow().getHandle(), bindCode);
    }

    private static boolean isBindDown(long window, int bindCode) {
        if (bindCode == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        if (isMouseBind(bindCode)) {
            return GLFW.glfwGetMouseButton(window, mouseButton(bindCode)) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, bindCode) == GLFW.GLFW_PRESS;
    }

    void open() {
        openedAt = System.nanoTime();
    }

    void render(DrawContext context, int width, int height, int mouseX, int mouseY, float delta) {
        float open = openingProgress();
        float fit = Math.min(width / BASE_WIDTH, height / BASE_HEIGHT);
        float screenFit = Math.min(Math.max(0.1F, (width - 12.0F) / BASE_WIDTH), Math.max(0.1F, (height - 12.0F) / BASE_HEIGHT));
        scale = Math.min(fit * (0.965F + 0.035F * open), screenFit);
        originX = (width - BASE_WIDTH * scale) * 0.5F;
        originY = (height - BASE_HEIGHT * scale) * 0.5F + CENTER_Y_OFFSET * scale;
        originX = clamp(originX, 6.0F, width - BASE_WIDTH * scale - 6.0F);
        originY = clamp(originY, 6.0F, height - BASE_HEIGHT * scale - 6.0F);
        float dt = Math.max(1.0F / 240.0F, delta / 20.0F);
        syncModuleStates();

        drawLiquidGlassBackdrop(context, width, height, open);
        drawMain(context, mouseX, mouseY, open, dt);
        targetEspCardProgress = approach(targetEspCardProgress, showTargetEsp ? 1.0F : 0.0F, dt, 14.0F);
        chinaHatCardProgress = approach(chinaHatCardProgress, showChinaHat ? 1.0F : 0.0F, dt, 14.0F);
        wingsCardProgress = approach(wingsCardProgress, showWings ? 1.0F : 0.0F, dt, 14.0F);
        if (targetEspCardProgress > 0.02F) {
            drawTargetEsp(context, open * targetEspCardProgress, dt);
        }
        if (chinaHatCardProgress > 0.02F) {
            drawChinaHat(context, open * chinaHatCardProgress, dt);
        }
        if (wingsCardProgress > 0.02F) {
            drawWings(context, open * wingsCardProgress, dt);
        }
        aspectRatioCardProgress = approach(aspectRatioCardProgress, showAspectRatio ? 1.0F : 0.0F, dt, 14.0F);
        if (aspectRatioCardProgress > 0.02F) {
            drawAspectRatio(context, open * aspectRatioCardProgress, dt);
        }
        removalsCardProgress = approach(removalsCardProgress, showRemovals ? 1.0F : 0.0F, dt, 14.0F);
        if (removalsCardProgress > 0.02F) {
            drawRemovals(context, open * removalsCardProgress);
        }
        particlesCardProgress = approach(particlesCardProgress, showParticles ? 1.0F : 0.0F, dt, 14.0F);
        if (particlesCardProgress > 0.02F) {
            drawParticles(context, open * particlesCardProgress, dt);
        }
        jumpCirclesCardProgress = approach(jumpCirclesCardProgress, showJumpCircles ? 1.0F : 0.0F, dt, 14.0F);
        if (jumpCirclesCardProgress > 0.02F) {
            drawJumpCircles(context, open * jumpCirclesCardProgress, dt);
        }
        trailsCardProgress = approach(trailsCardProgress, showTrails ? 1.0F : 0.0F, dt, 14.0F);
        if (trailsCardProgress > 0.02F) {
            drawTrails(context, open * trailsCardProgress, dt);
        }
        worldCustomizerCardProgress = approach(worldCustomizerCardProgress, showWorldCustomizer ? 1.0F : 0.0F, dt, 14.0F);
        if (worldCustomizerCardProgress > 0.02F) {
            drawWorldCustomizer(context, open * worldCustomizerCardProgress, dt);
        }
        hitColorCardProgress = approach(hitColorCardProgress, showHitColor ? 1.0F : 0.0F, dt, 14.0F);
        if (hitColorCardProgress > 0.02F) {
            drawHitColor(context, open * hitColorCardProgress);
        }
        hitboxCustomizerCardProgress = approach(hitboxCustomizerCardProgress, showHitboxCustomizer ? 1.0F : 0.0F, dt, 14.0F);
        if (hitboxCustomizerCardProgress > 0.02F) {
            drawHitboxCustomizer(context, open * hitboxCustomizerCardProgress, dt);
        }
        blockOverlayCardProgress = approach(blockOverlayCardProgress, showBlockOverlay ? 1.0F : 0.0F, dt, 14.0F);
        if (blockOverlayCardProgress > 0.02F) {
            drawBlockOverlay(context, open * blockOverlayCardProgress, dt);
        }
        itemRadiusCardProgress = approach(itemRadiusCardProgress, showItemRadius ? 1.0F : 0.0F, dt, 14.0F);
        if (itemRadiusCardProgress > 0.02F) {
            drawItemRadius(context, open * itemRadiusCardProgress);
        }
        animationsCardProgress = approach(animationsCardProgress, showAnimations ? 1.0F : 0.0F, dt, 14.0F);
        if (animationsCardProgress > 0.02F) {
            drawAnimations(context, open * animationsCardProgress);
        }
        tabCustomizerCardProgress = approach(tabCustomizerCardProgress, showTabCustomizer ? 1.0F : 0.0F, dt, 14.0F);
        if (tabCustomizerCardProgress > 0.02F) {
            drawTabCustomizer(context, open * tabCustomizerCardProgress, dt);
        }
        freeLookCardProgress = approach(freeLookCardProgress, showFreeLook ? 1.0F : 0.0F, dt, 14.0F);
        if (freeLookCardProgress > 0.02F) {
            drawFreeLook(context, open * freeLookCardProgress, dt);
        }
        zoomCardProgress = approach(zoomCardProgress, showZoom ? 1.0F : 0.0F, dt, 14.0F);
        if (zoomCardProgress > 0.02F) {
            drawZoom(context, open * zoomCardProgress, dt);
        }
        autoSwapCardProgress = approach(autoSwapCardProgress, showAutoSwap ? 1.0F : 0.0F, dt, 14.0F);
        if (autoSwapCardProgress > 0.02F) {
            drawAutoSwap(context, open * autoSwapCardProgress, dt);
        }
        elytraSwapCardProgress = approach(elytraSwapCardProgress, showElytraSwap ? 1.0F : 0.0F, dt, 14.0F);
        if (elytraSwapCardProgress > 0.02F) {
            drawElytraSwap(context, open * elytraSwapCardProgress);
        }
        itemResorterCardProgress = approach(itemResorterCardProgress, showItemResorter ? 1.0F : 0.0F, dt, 14.0F);
        if (itemResorterCardProgress > 0.02F) {
            drawItemResorter(context, open * itemResorterCardProgress, dt);
        }
        autoBuyCardProgress = approach(autoBuyCardProgress, showAutoBuy ? 1.0F : 0.0F, dt, 14.0F);
        if (autoBuyCardProgress > 0.02F) {
            drawAutoBuy(context, open * autoBuyCardProgress, dt);
        }
        autoResellAfkCardProgress = approach(autoResellAfkCardProgress, showAutoResellAFK ? 1.0F : 0.0F, dt, 14.0F);
        if (autoResellAfkCardProgress > 0.02F) {
            drawAutoResellAFK(context, open * autoResellAfkCardProgress);
        }
        telegramCardProgress = approach(telegramCardProgress, showTelegram ? 1.0F : 0.0F, dt, 14.0F);
        if (telegramCardProgress > 0.02F) {
            drawTelegram(context, open * telegramCardProgress);
        }
        targetHudCardProgress = approach(targetHudCardProgress, showTargetHud ? 1.0F : 0.0F, dt, 14.0F);
        if (targetHudCardProgress > 0.02F) {
            drawTargetHud(context, open * targetHudCardProgress, dt);
        }
        tapeMouseCardProgress = approach(tapeMouseCardProgress, showTapeMouse ? 1.0F : 0.0F, dt, 14.0F);
        if (tapeMouseCardProgress > 0.02F) {
            drawTapeMouse(context, open * tapeMouseCardProgress, dt);
        }
        anarchySwitcherCardProgress = approach(anarchySwitcherCardProgress, showAnarchySwitcher ? 1.0F : 0.0F, dt, 14.0F);
        if (anarchySwitcherCardProgress > 0.02F) {
            drawAnarchySwitcher(context, open * anarchySwitcherCardProgress, dt);
        }
        nameBinderCardProgress = approach(nameBinderCardProgress, showNameBinder ? 1.0F : 0.0F, dt, 14.0F);
        if (nameBinderCardProgress > 0.02F) {
            drawNameBinder(context, open * nameBinderCardProgress);
        }
        macrosCardProgress = approach(macrosCardProgress, showMacros ? 1.0F : 0.0F, dt, 14.0F);
        if (macrosCardProgress > 0.02F) {
            drawMacros(context, open * macrosCardProgress);
        }
        crosshairCardProgress = approach(crosshairCardProgress, showCrosshair ? 1.0F : 0.0F, dt, 14.0F);
        if (crosshairCardProgress > 0.02F) {
            drawCrosshair(context, open * crosshairCardProgress, dt);
        }
        drawBindOverlay(context, open, dt);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindingTarget != null || bindingOptionTarget != null) {
            if (button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_8) {
                String bindKey = bindingTarget != null ? bindingTarget.name : bindingOptionTarget;
                int bindCode = mouseBind(button);
                REMEMBERED_BINDS.put(bindKey, bindCode);
                BIND_KEY_DOWN.put(bindCode, true);
                FluxVisualsClient.requestConfigSave();
                bindingTarget = null;
                bindingOptionTarget = null;
            }
            return true;
        }

        float bx = baseX(mouseX);
        float by = baseY(mouseY);

        AutoResellAFK autoResellAFK = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoResellAfkChatToggleAt(bx, by)) {
            autoResellAFK.setChatEnabled(!autoResellAFK.isChatEnabled());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoResellAfkChatInputAt(bx, by)) {
            autoResellAfkChatFocused = true;
            autoResellAfkPriceFocused = false;
            autoResellAfkChatDraft.setLength(0);
            autoResellAfkChatDraft.append(autoResellAFK.getChatMessage());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoResellAfkSellToggleAt(bx, by)) {
            autoResellAFK.setSellPurchasedSwords(!autoResellAFK.isSellPurchasedSwords());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoResellAfkSellPriceAt(bx, by)) {
            autoResellAfkPriceFocused = true;
            autoResellAfkChatFocused = false;
            autoResellAfkPriceDraft.setLength(0);
            autoResellAfkPriceDraft.append(autoResellAFK.getSellPrice());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && closeSettingsCardAt(bx, by)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showWings) {
            Wings wings = FluxVisualsClient.MODULE_MANAGER.getWings();
            if (inside(bx, by, wingsCardX + 82.0F, wingsCardY + 45.0F, 105.0F, 25.0F)) {
                wingsTypeOpen = !wingsTypeOpen;
                return true;
            }
            if (wingsTypeOpen) {
                float listY = wingsCardY + 45.0F + 29.0F;
                for (int i = 0; i < Wings.WingType.values().length; i++) {
                    if (inside(bx, by, wingsCardX + 82.0F, listY + 3.0F + i * 22.0F, 105.0F, 22.0F)) {
                        wings.setWingType(Wings.WingType.values()[i]);
                        wingsTypeOpen = false;
                        return true;
                    }
                }
            }
            if (inside(bx, by, wingsCardX + 174.0F, wingsCardY + 77.0F, 24.0F, 24.0F)) { wings.setShaderFill(!wings.isShaderFill()); return true; }
            if (inside(bx, by, wingsCardX + 174.0F, wingsCardY + 107.0F, 24.0F, 24.0F)) { wings.setRenderSelf(!wings.isRenderSelf()); return true; }
            if (inside(bx, by, wingsCardX + 174.0F, wingsCardY + 137.0F, 24.0F, 24.0F)) { wings.setRenderPlayers(!wings.isRenderPlayers()); return true; }
            if (inside(bx, by, wingsCardX + 174.0F, wingsCardY + 167.0F, 24.0F, 24.0F)) { wings.setRenderBots(!wings.isRenderBots()); return true; }
            if (inside(bx, by, wingsCardX + 174.0F, wingsCardY + 197.0F, 24.0F, 24.0F)) { wings.setFlapping(!wings.isFlapping()); return true; }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showChinaHat) {
            ChinaHat hat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
            float targetY = chinaColorY() + 40.0F;
            if (inside(bx, by, chinaCardX + 174.0F, targetY, 24.0F, 24.0F)) { hat.setRenderSelf(!hat.isRenderSelf()); return true; }
            if (inside(bx, by, chinaCardX + 174.0F, targetY + 30.0F, 24.0F, 24.0F)) { hat.setRenderPlayers(!hat.isRenderPlayers()); return true; }
            if (inside(bx, by, chinaCardX + 174.0F, targetY + 60.0F, 24.0F, 24.0F)) { hat.setRenderBots(!hat.isRenderBots()); return true; }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && searchHitbox(bx, by)) {
            searchOpen = true;
            return true;
        }

        Category tab = tabAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && tab != null) {
            selectedCategory = tab;
            moduleScroll = 0.0F;
            return true;
        }

        TargetEsp.Style targetStyle = targetStyleAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetStyle != null) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setStyle(targetStyle);
            targetStyleOpen = false;
            return true;
        }

        TargetEsp.TargetFilter targetFilter = targetFilterAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetFilter != null) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setTargetFilter(targetFilter);
            targetFilterOpen = false;
            return true;
        }

        Particles.TextureType earlyParticleTexture = particleTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && earlyParticleTexture != null) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setPreviewTexture(earlyParticleTexture);
            particleTextureOpen = false;
            return true;
        }

        Particles.SpawnMode earlyMultiMode = particleMultiTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && earlyMultiMode != null) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            particles.setModeEnabled(earlyMultiMode, !particles.isModeEnabled(earlyMultiMode));
            return true;
        }

        JumpCircles.Mode jumpMode = jumpCircleModeAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpMode != null) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setMode(jumpMode);
            jumpCircleModeOpen = false;
            jumpCircleTextureOpen = false;
            jumpCircleParticleTextureOpen = false;
            return true;
        }

        JumpCircles.TextureType jumpTexture = jumpCircleTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpTexture != null) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setTextureType(jumpTexture);
            jumpCircleTextureOpen = false;
            return true;
        }

        Particles.TextureType jumpParticleTexture = jumpCircleParticleTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpParticleTexture != null) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setParticleTexture(jumpParticleTexture);
            jumpCircleParticleTextureOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldFogPaletteOpen && worldFogPaletteAt(bx, by)) {
            draggingSlider = "world_fog_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && trailsColorOpen && trailsPaletteAt(bx, by)) {
            draggingSlider = "trails_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleColorOpen && jumpCirclePaletteAt(bx, by)) {
            draggingSlider = "jump_circles_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        Crosshair.Preset crosshairPreset = crosshairPresetAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairPreset != null) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().applyPreset(crosshairPreset);
            crosshairPresetOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairPresetBoxAt(bx, by)) {
            crosshairPresetOpen = !crosshairPresetOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetStyleBoxAt(bx, by)) {
            targetStyleOpen = !targetStyleOpen;
            targetFilterOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetFilterBoxAt(bx, by)) {
            targetFilterOpen = !targetFilterOpen;
            targetStyleOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetRedOnHitToggleAt(bx, by)) {
            TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
            targetEsp.setRedOnHit(!targetEsp.isRedOnHit());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetColorBoxAt(bx, by)) {
            targetColorOpen = !targetColorOpen;
            return true;
        }

        if (handleColorPickerOverlayClick(bx, by, button)) {
            return true;
        }

        ItemResorterTab resorterTab = itemResorterTabAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resorterTab != null) {
            itemResorterTab = resorterTab;
            itemResorterFocusedInput = null;
            itemResorterPriceFocused = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterEnchantColorBoxAt(bx, by)) {
            itemResorterEnchantColorOpen = !itemResorterEnchantColorOpen;
            itemResorterBuffColorOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterBuffColorBoxAt(bx, by)) {
            itemResorterBuffColorOpen = !itemResorterBuffColorOpen;
            itemResorterEnchantColorOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterBuffExplosiveToggleAt(bx, by)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setBuffExplosiveOnly(!itemResorter.isBuffExplosiveOnly());
            return true;
        }

        ItemResorterInput resorterInput = itemResorterInputAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resorterInput != null) {
            itemResorterFocusedInput = resorterInput;
            itemResorterPriceFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterPriceInputAt(bx, by)) {
            itemResorterFocusedInput = null;
            itemResorterPriceFocused = true;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterPriceToggleAt(bx, by)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setPriceFilterEnabled(!itemResorter.isPriceFilterEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemResorterDurabilityToggleAt(bx, by)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setDurabilityFilterEnabled(!itemResorter.isDurabilityFilterEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyToggleAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuy.setAnarchySwitchEnabled(!autoBuy.isAnarchySwitchEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyInputAt(bx, by)) {
            autoBuyAnarchyInputFocused = true;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyNameFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyAddAt(bx, by)) {
            submitAutoBuyAnarchyDraft();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyAdToggleAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuy.setAnarchyAdEnabled(!autoBuy.isAnarchyAdEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyAdInputAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = true;
            autoBuySellerBanFocused = false;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyNameFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            autoBuyAnarchyAdDraft.setLength(0);
            autoBuyAnarchyAdDraft.append(autoBuy.getAnarchyAdText());
            searchOpen = false;
            return true;
        }

        String autoBuyAnarchyRemove = autoBuyAnarchyRemoveAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyAnarchyRemove != null) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().removeAnarchyId(autoBuyAnarchyRemove);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyNameToggleAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuy.setNameEnabled(!autoBuy.isNameEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyNameInputAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuyNameDraft.setLength(0);
            autoBuyNameDraft.append(autoBuy.getNameText());
            autoBuyNameFocused = true;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyResellToggleAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuy.setAutoResellEnabled(!autoBuy.isAutoResellEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuyRentalToggleAt(bx, by)) {
            AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
            autoBuy.setRentalSlotsEnabled(!autoBuy.isRentalSlotsEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuySellerBanInputAt(bx, by)) {
            autoBuySellerBanFocused = true;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuyNameFocused = false;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuySellerBanAddAt(bx, by)) {
            submitAutoBuySellerBanDraft();
            return true;
        }

        String autoBuySellerBanRemove = autoBuySellerBanRemoveAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoBuySellerBanRemove != null) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().removeBannedSeller(autoBuySellerBanRemove);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && telegramLogsToggleAt(bx, by)) {
            Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
            telegram.setLogsEnabled(!telegram.isLogsEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && telegramTokenInputAt(bx, by)) {
            Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
            if (telegramChatFocused) {
                submitTelegramChatDraft();
            }
            telegramTokenDraft.setLength(0);
            telegramTokenDraft.append(telegram.getBotToken());
            telegramTokenFocused = true;
            telegramChatFocused = false;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            itemResorterFocusedInput = null;
            itemResorterPriceFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && telegramChatInputAt(bx, by)) {
            Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
            if (telegramTokenFocused) {
                submitTelegramTokenDraft();
            }
            telegramChatDraft.setLength(0);
            telegramChatDraft.append(telegram.getChatId());
            telegramTokenFocused = false;
            telegramChatFocused = true;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            itemResorterFocusedInput = null;
            itemResorterPriceFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && telegramTestAt(bx, by)) {
            if (telegramTokenFocused) {
                submitTelegramTokenDraft();
            }
            if (telegramChatFocused) {
                submitTelegramChatDraft();
            }
            FluxVisualsClient.MODULE_MANAGER.getTelegram().sendTestMessage();
            return true;
        }

        ItemResorterAddTarget addTarget = itemResorterAddTargetAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && addTarget != null) {
            submitItemResorterInput(addTarget.input());
            return true;
        }

        ItemResorterChipAction chipAction = itemResorterChipActionAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chipAction != null) {
            removeItemResorterEntry(chipAction);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetHudRedToggleAt(bx, by)) {
            TargetHud targetHud = FluxVisualsClient.MODULE_MANAGER.getTargetHud();
            targetHud.setRedOnDamage(!targetHud.isRedOnDamage());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && targetHudParticlesToggleAt(bx, by)) {
            TargetHud targetHud = FluxVisualsClient.MODULE_MANAGER.getTargetHud();
            targetHud.setHitParticles(!targetHud.isHitParticles());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && tapeMouseButtonToggleAt(bx, by)) {
            tapeMouseButtonOpen = !tapeMouseButtonOpen;
            return true;
        }

        TapeMouse.ButtonMode tapeMode = tapeMouseButtonOptionAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && tapeMode != null) {
            FluxVisualsClient.MODULE_MANAGER.getTapeMouse().setButtonMode(tapeMode);
            tapeMouseButtonOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anarchyInputAt(bx, by)) {
            anarchyInputFocused = true;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anarchyAddAt(bx, by)) {
            submitAnarchyDraft();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anarchyAdToggleAt(bx, by)) {
            AnarchySwitcher anarchy = FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher();
            anarchy.setAdEnabled(!anarchy.isAdEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anarchyAdInputAt(bx, by)) {
            AnarchySwitcher anarchy = FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher();
            anarchyInputFocused = false;
            anarchyAdFocused = true;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            nameBinderInputFocused = false;
            macrosInputFocused = false;
            anarchyAdDraft.setLength(0);
            anarchyAdDraft.append(anarchy.getAdText());
            searchOpen = false;
            return true;
        }

        String anarchyRemove = anarchyRemoveAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anarchyRemove != null) {
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().removeAnarchyId(anarchyRemove);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && nameBinderInputAt(bx, by)) {
            nameBinderInputFocused = true;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            macrosInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && nameBinderAddAt(bx, by)) {
            submitNameBinderDraft();
            return true;
        }

        int nameRemove = nameBinderRemoveAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && nameRemove >= 0) {
            FluxVisualsClient.MODULE_MANAGER.getNameBind().removeEntry(nameRemove);
            return true;
        }

        int nameBind = nameBinderBindAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && nameBind >= 0) {
            bindingOptionTarget = "namebinder:" + nameBind;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && macrosInputAt(bx, by)) {
            macrosInputFocused = true;
            anarchyInputFocused = false;
            anarchyAdFocused = false;
            autoBuyAnarchyInputFocused = false;
            autoBuyAnarchyAdFocused = false;
            autoBuySellerBanFocused = false;
            autoBuyNameFocused = false;
            nameBinderInputFocused = false;
            searchOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && macrosAddAt(bx, by)) {
            submitMacrosDraft();
            return true;
        }

        int macroRemove = macrosRemoveAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && macroRemove >= 0) {
            FluxVisualsClient.MODULE_MANAGER.getMacros().removeEntry(macroRemove);
            return true;
        }

        int macroBind = macrosBindAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && macroBind >= 0) {
            bindingOptionTarget = "macro:" + macroBind;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        String slider = sliderAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && slider != null) {
            resetSlider(slider);
            draggingSlider = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && slider != null) {
            draggingSlider = slider;
            updateSlider(slider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleModeBoxAt(bx, by)) {
            jumpCircleModeOpen = !jumpCircleModeOpen;
            jumpCircleTextureOpen = false;
            jumpCircleParticleTextureOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleTextureBoxAt(bx, by)) {
            jumpCircleTextureOpen = !jumpCircleTextureOpen;
            jumpCircleModeOpen = false;
            jumpCircleParticleTextureOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleParticleTextureBoxAt(bx, by)) {
            jumpCircleParticleTextureOpen = !jumpCircleParticleTextureOpen;
            jumpCircleModeOpen = false;
            jumpCircleTextureOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleParticlesToggleAt(bx, by)) {
            JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
            jumpCircles.setParticlesEnabled(!jumpCircles.isParticlesEnabled());
            if (!jumpCircles.isParticlesEnabled()) {
                jumpCircleParticleTextureOpen = false;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && jumpCircleColorBoxAt(bx, by)) {
            jumpCircleColorOpen = !jumpCircleColorOpen;
            return true;
        }

        ChinaHat.FillMode chinaFillMode = chinaFillModeAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaFillMode != null) {
            FluxVisualsClient.MODULE_MANAGER.getChinaHat().setFillMode(chinaFillMode);
            chinaFillModeOpen = false;
            chinaShaderOpen = false;
            return true;
        }

        BlockOverlay.ShaderType chinaShader = chinaShaderAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaShader != null) {
            FluxVisualsClient.MODULE_MANAGER.getChinaHat().setShaderType(chinaShader);
            chinaShaderOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaFillModeBoxAt(bx, by)) {
            chinaFillModeOpen = !chinaFillModeOpen;
            chinaShaderOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaShaderBoxAt(bx, by)) {
            chinaShaderOpen = !chinaShaderOpen;
            chinaFillModeOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaColorBoxAt(bx, by)) {
            chinaPaletteOpen = !chinaPaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chinaPaletteOpen && paletteAt(bx, by)) {
            draggingSlider = "china_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitColorPaletteOpen && hitColorPaletteAt(bx, by)) {
            draggingSlider = "hit_color_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitColorArmorToggleAt(bx, by)) {
            HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
            hitColor.setArmorTintEnabled(!hitColor.isArmorTintEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showHitColor && inside(bx, by, hitColorCardX + 126.0F, hitColorColorY(), 58.0F, 28.0F)) {
            hitColorPaletteOpen = !hitColorPaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxAlwaysShowBindButtonAt(bx, by)) {
            bindingOptionTarget = HITBOX_ALWAYS_SHOW_BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxAlwaysShowToggleAt(bx, by)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setAlwaysShow(!hitboxCustomizer.isAlwaysShow());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxOutlinePaletteOpen && hitboxOutlinePaletteAt(bx, by)) {
            draggingSlider = "hitbox_outline_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxFillPaletteOpen && hitboxFillPaletteAt(bx, by)) {
            draggingSlider = "hitbox_fill_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxFillToggleAt(bx, by)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            boolean enabled = !hitboxCustomizer.isFillEnabled();
            hitboxCustomizer.setFillEnabled(enabled);
            if (!enabled) {
                hitboxFillPaletteOpen = false;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxCornersOnlyToggleAt(bx, by)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setCornersOnly(!hitboxCustomizer.isCornersOnly());
            return true;
        }

        HitboxCustomizer.TargetType hitboxTarget = hitboxTargetAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxTarget != null) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setTargetEnabled(hitboxTarget, !hitboxCustomizer.isTargetEnabled(hitboxTarget));
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxTargetsBoxAt(bx, by)) {
            hitboxTargetsOpen = !hitboxTargetsOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxSelfToggleAt(bx, by)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setIncludeSelf(!hitboxCustomizer.isIncludeSelf());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayOutlinePaletteOpen && blockOverlayOutlinePaletteAt(bx, by)) {
            draggingSlider = "block_overlay_outline_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayFillPaletteOpen && blockOverlayFillPaletteAt(bx, by)) {
            draggingSlider = "block_overlay_fill_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        BlockOverlay.FillMode fillMode = blockOverlayFillModeAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && fillMode != null) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setFillMode(fillMode);
            blockOverlayFillModeOpen = false;
            return true;
        }

        BlockOverlay.ShaderType shaderType = blockOverlayShaderAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shaderType != null) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setShaderType(shaderType);
            blockOverlayShaderOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayFillToggleAt(bx, by)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            boolean enabled = !blockOverlay.isFillEnabled();
            blockOverlay.setFillEnabled(enabled);
            if (!enabled) {
                blockOverlayFillPaletteOpen = false;
                blockOverlayFillModeOpen = false;
                blockOverlayShaderOpen = false;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayThroughWallsToggleAt(bx, by)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            blockOverlay.setThroughWalls(!blockOverlay.isThroughWalls());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlaySmoothSwitchToggleAt(bx, by)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            blockOverlay.setSmoothSwitch(!blockOverlay.isSmoothSwitch());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayOutlineColorBoxAt(bx, by)) {
            blockOverlayOutlinePaletteOpen = !blockOverlayOutlinePaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayFillColorBoxAt(bx, by)) {
            blockOverlayFillPaletteOpen = !blockOverlayFillPaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayFillModeBoxAt(bx, by)) {
            blockOverlayFillModeOpen = !blockOverlayFillModeOpen;
            blockOverlayShaderOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blockOverlayShaderBoxAt(bx, by)) {
            blockOverlayShaderOpen = !blockOverlayShaderOpen;
            blockOverlayFillModeOpen = false;
            return true;
        }

        ItemRadius.RadiusItem radiusItem = itemRadiusItemToggleAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && radiusItem != null) {
            ItemRadius itemRadius = FluxVisualsClient.MODULE_MANAGER.getItemRadius();
            itemRadius.setItemEnabled(radiusItem, !itemRadius.isItemEnabledPublic(radiusItem));
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && itemRadiusDraconicToggleAt(bx, by)) {
            ItemRadius itemRadius = FluxVisualsClient.MODULE_MANAGER.getItemRadius();
            itemRadius.setDraconicTrap(!itemRadius.isDraconicTrap());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && animationsTabToggleAt(bx, by)) {
            Animations animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();
            animations.setTabEnabled(!animations.isTabEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && animationsF5ToggleAt(bx, by)) {
            Animations animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();
            animations.setThirdPersonEnabled(!animations.isThirdPersonEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && animationsHotbarToggleAt(bx, by)) {
            Animations animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();
            animations.setHotbarEnabled(!animations.isHotbarEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && animationsInventoryToggleAt(bx, by)) {
            Animations animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();
            animations.setInventoryEnabled(!animations.isInventoryEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && freeLookBindButtonAt(bx, by)) {
            bindingOptionTarget = FreeLook.PERSPECTIVE_BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        FreeLook.ActivationMode freeLookMode = freeLookModeAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && freeLookMode != null) {
            FluxVisualsClient.MODULE_MANAGER.getFreeLook().setActivationMode(freeLookMode);
            freeLookModeOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && freeLookModeButtonAt(bx, by)) {
            freeLookModeOpen = !freeLookModeOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && zoomBindButtonAt(bx, by)) {
            bindingOptionTarget = Zoom.BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && zoomWheelToggleAt(bx, by)) {
            Zoom zoom = FluxVisualsClient.MODULE_MANAGER.getZoom();
            zoom.setWheelZoomEnabled(!zoom.isWheelZoomEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoSwapBindButtonAt(bx, by)) {
            bindingOptionTarget = AutoSwap.BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && elytraSwapBindButtonAt(bx, by)) {
            bindingOptionTarget = ElytraSwap.BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && elytraSwapFireworkBindButtonAt(bx, by)) {
            bindingOptionTarget = ElytraSwap.FIREWORK_BIND_KEY;
            searchOpen = false;
            draggingSlider = null;
            draggingCard = null;
            return true;
        }

        AutoSwap.SwapItem autoSwapFirst = autoSwapFirstItemAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoSwapFirst != null) {
            FluxVisualsClient.MODULE_MANAGER.getAutoSwap().setFirstItem(autoSwapFirst);
            autoSwapFirstOpen = false;
            return true;
        }

        AutoSwap.SwapItem autoSwapSecond = autoSwapSecondItemAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoSwapSecond != null) {
            FluxVisualsClient.MODULE_MANAGER.getAutoSwap().setSecondItem(autoSwapSecond);
            autoSwapSecondOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoSwapFirstBoxAt(bx, by)) {
            autoSwapFirstOpen = !autoSwapFirstOpen;
            autoSwapSecondOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && autoSwapSecondBoxAt(bx, by)) {
            autoSwapSecondOpen = !autoSwapSecondOpen;
            autoSwapFirstOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairDotToggleAt(bx, by)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            crosshair.setDot(!crosshair.isDot());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairOutlineToggleAt(bx, by)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            crosshair.setOutline(!crosshair.isOutline());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairRedToggleAt(bx, by)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            crosshair.setRedOnTarget(!crosshair.isRedOnTarget());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairThirdPersonToggleAt(bx, by)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            crosshair.setShowInThirdPerson(!crosshair.isShowInThirdPerson());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairColorBoxAt(bx, by)) {
            crosshairColorOpen = !crosshairColorOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && crosshairColorOpen && crosshairPaletteAt(bx, by)) {
            draggingSlider = "crosshair_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hitboxOutlineColorBoxAt(bx, by)) {
            hitboxOutlinePaletteOpen = !hitboxOutlinePaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled()
                && hitboxFillColorBoxAt(bx, by)) {
            hitboxFillPaletteOpen = !hitboxFillPaletteOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && particlesColorOpen && particlesPaletteAt(bx, by)) {
            draggingSlider = "particles_palette";
            updateSlider(draggingSlider, bx, by);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showParticles && inside(bx, by, particlesCardX + 13.0F, particlesColorY() + 28.0F, 58.0F, 28.0F)) {
            particlesColorOpen = !particlesColorOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && trailsColorBoxAt(bx, by)) {
            trailsColorOpen = !trailsColorOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && particleOutlineToggleAt(bx, by)) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            particles.setOutlineEnabled(!particles.isOutlineEnabled());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showRemovals) {
            Removals removals = FluxVisualsClient.MODULE_MANAGER.getRemovals();
            if (removalsToggleAt(bx, by, 0)) {
                removals.setFireOverlay(!removals.isFireOverlay());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 1)) {
                removals.setEntityGlowing(!removals.isEntityGlowing());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 2)) {
                removals.setBadWeather(!removals.isBadWeather());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 3)) {
                removals.setHurtCamera(!removals.isHurtCamera());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 4)) {
                removals.setSprintFov(!removals.isSprintFov());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 5)) {
                removals.setSoulSandBubbles(!removals.isSoulSandBubbles());
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 6)) {
                removals.setNoFluidEnabled(Removals.FluidType.WATER, !removals.isNoFluidEnabled(Removals.FluidType.WATER));
                FluxVisualsClient.requestConfigSave();
                return true;
            }
            if (removalsToggleAt(bx, by, 7)) {
                removals.setNoFluidEnabled(Removals.FluidType.LAVA, !removals.isNoFluidEnabled(Removals.FluidType.LAVA));
                FluxVisualsClient.requestConfigSave();
                return true;
            }
        }

        AspectRatio.Preset aspectPreset = aspectPresetAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && aspectPreset != null) {
            FluxVisualsClient.MODULE_MANAGER.getAspectRatio().setPreset(aspectPreset);
            aspectPresetOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && aspectPresetBoxAt(bx, by)) {
            aspectPresetOpen = !aspectPresetOpen;
            return true;
        }

        WorldCustomizer.TimePreset worldPreset = worldTimePresetAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldPreset != null) {
            FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setTimePreset(worldPreset);
            worldTimePresetOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldTimePresetBoxAt(bx, by)) {
            worldTimePresetOpen = !worldTimePresetOpen;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldFogToggleAt(bx, by)) {
            WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
            boolean enabled = !worldCustomizer.isCustomFogEnabled();
            worldCustomizer.setCustomFogEnabled(enabled);
            if (!enabled) {
                worldFogPaletteOpen = false;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldFogColorBoxAt(bx, by)) {
            worldFogPaletteOpen = !worldFogPaletteOpen;
            return true;
        }

        Particles.TextureType particleTexture = particleTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && particleTexture != null) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setPreviewTexture(particleTexture);
            particleTextureOpen = false;
            return true;
        }

        Particles.SpawnMode multiMode = particleMultiTextureAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && multiMode != null) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            particles.setModeEnabled(multiMode, !particles.isModeEnabled(multiMode));
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && particleTextureBoxAt(bx, by)) {
            particleTextureOpen = !particleTextureOpen;
            particleMultiOpen = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && particleMultiBoxAt(bx, by)) {
            particleMultiOpen = !particleMultiOpen;
            particleTextureOpen = false;
            return true;
        }

        String card = draggableCardAt(bx, by);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && card != null) {
            draggingCard = card;
            dragOffsetX = bx - cardX(card);
            dragOffsetY = by - cardY(card);
            return true;
        }

        VisualModule module = moduleAt(bx, by);
        if (module != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                setModuleState(module.name, !module.active);
                module.active = isModuleActive(module.name, module.active);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if ("TargetEsp".equals(module.name)) {
                    showTargetEsp = !showTargetEsp;
                    rememberedTargetEspCard = showTargetEsp;
                    FluxVisualsClient.requestConfigSave();
                } else if ("China Hat".equals(module.name)) {
                    showChinaHat = !showChinaHat;
                    rememberedChinaHatCard = showChinaHat;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Aspect Ratio".equals(module.name)) {
                    showAspectRatio = !showAspectRatio;
                    rememberedAspectRatioCard = showAspectRatio;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Removals".equals(module.name)) {
                    showRemovals = !showRemovals;
                    rememberedRemovalsCard = showRemovals;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Particles".equals(module.name)) {
                    showParticles = !showParticles;
                    rememberedParticlesCard = showParticles;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Wings".equals(module.name)) {
                    showWings = !showWings;
                    rememberedWingsCard = showWings;
                    FluxVisualsClient.requestConfigSave();
                } else if ("JumpCircles".equals(module.name)) {
                    showJumpCircles = !showJumpCircles;
                    rememberedJumpCirclesCard = showJumpCircles;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Trails".equals(module.name)) {
                    showTrails = !showTrails;
                    rememberedTrailsCard = showTrails;
                    FluxVisualsClient.requestConfigSave();
                } else if ("World Customizer".equals(module.name)) {
                    showWorldCustomizer = !showWorldCustomizer;
                    rememberedWorldCustomizerCard = showWorldCustomizer;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Hit Color".equals(module.name)) {
                    showHitColor = !showHitColor;
                    rememberedHitColorCard = showHitColor;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Hitbox Customizer".equals(module.name)) {
                    showHitboxCustomizer = !showHitboxCustomizer;
                    rememberedHitboxCustomizerCard = showHitboxCustomizer;
                    FluxVisualsClient.requestConfigSave();
                } else if ("BlockOverlay".equals(module.name)) {
                    showBlockOverlay = !showBlockOverlay;
                    rememberedBlockOverlayCard = showBlockOverlay;
                    FluxVisualsClient.requestConfigSave();
                } else if ("ItemRadius".equals(module.name)) {
                    showItemRadius = !showItemRadius;
                    rememberedItemRadiusCard = showItemRadius;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Animations".equals(module.name)) {
                    showAnimations = !showAnimations;
                    rememberedAnimationsCard = showAnimations;
                    FluxVisualsClient.requestConfigSave();
                } else if ("TabCustomizer".equals(module.name)) {
                    showTabCustomizer = !showTabCustomizer;
                    rememberedTabCustomizerCard = showTabCustomizer;
                    FluxVisualsClient.requestConfigSave();
                } else if ("FreeLook".equals(module.name)) {
                    showFreeLook = !showFreeLook;
                    rememberedFreeLookCard = showFreeLook;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Zoom".equals(module.name)) {
                    showZoom = !showZoom;
                    rememberedZoomCard = showZoom;
                    FluxVisualsClient.requestConfigSave();
                } else if ("ItemSwap".equals(module.name)) {
                    showAutoSwap = !showAutoSwap;
                    rememberedAutoSwapCard = showAutoSwap;
                    FluxVisualsClient.requestConfigSave();
                } else if ("ElytraSwap".equals(module.name)) {
                    showElytraSwap = !showElytraSwap;
                    rememberedElytraSwapCard = showElytraSwap;
                    FluxVisualsClient.requestConfigSave();
                } else if ("ItemResorter".equals(module.name)) {
                    showItemResorter = !showItemResorter;
                    rememberedItemResorterCard = showItemResorter;
                    FluxVisualsClient.requestConfigSave();
                } else if ("AutoBuy".equals(module.name)) {
                    showAutoBuy = !showAutoBuy;
                    rememberedAutoBuyCard = showAutoBuy;
                    FluxVisualsClient.requestConfigSave();
                } else if ("AutoResellAFK".equals(module.name)) {
                    showAutoResellAFK = !showAutoResellAFK;
                    FluxVisualsClient.requestConfigSave();
                } else if ("TG".equals(module.name)) {
                    showTelegram = !showTelegram;
                    rememberedTelegramCard = showTelegram;
                    if (!showTelegram) {
                        closeCardControls();
                    }
                    FluxVisualsClient.requestConfigSave();
                } else if ("TargetHud".equals(module.name)) {
                    showTargetHud = !showTargetHud;
                    rememberedTargetHudCard = showTargetHud;
                    FluxVisualsClient.requestConfigSave();
                } else if ("TapeMouse".equals(module.name)) {
                    showTapeMouse = !showTapeMouse;
                    rememberedTapeMouseCard = showTapeMouse;
                    FluxVisualsClient.requestConfigSave();
                } else if ("AnarchySwitcher".equals(module.name)) {
                    showAnarchySwitcher = !showAnarchySwitcher;
                    rememberedAnarchySwitcherCard = showAnarchySwitcher;
                    FluxVisualsClient.requestConfigSave();
                } else if ("NameBinder".equals(module.name)) {
                    showNameBinder = !showNameBinder;
                    rememberedNameBinderCard = showNameBinder;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Macros".equals(module.name)) {
                    showMacros = !showMacros;
                    rememberedMacrosCard = showMacros;
                    FluxVisualsClient.requestConfigSave();
                } else if ("Crosshair".equals(module.name)) {
                    showCrosshair = !showCrosshair;
                    rememberedCrosshairCard = showCrosshair;
                    FluxVisualsClient.requestConfigSave();
                } else if ("DiscordRPC".equals(module.name)) {
                    MinecraftClient.getInstance().setScreen(
                            new DiscordRPCSettingsScreen(MinecraftClient.getInstance().currentScreen));
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                bindingTarget = module;
                searchOpen = false;
                draggingSlider = null;
                draggingCard = null;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && searchOpen) {
            searchOpen = false;
            search.setLength(0);
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingSlider != null) {
            updateSlider(draggingSlider, baseX(mouseX), baseY(mouseY));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingCard != null) {
            if ("target".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, targetEspCardHeight());
                targetCardX = pos[0];
                targetCardY = pos[1];
                rememberedTargetX = targetCardX;
                rememberedTargetY = targetCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("china".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 205.0F, chinaCardHeight());
                chinaCardX = pos[0];
                chinaCardY = pos[1];
                rememberedChinaX = chinaCardX;
                rememberedChinaY = chinaCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("wings".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 205.0F, 388.0F);
                wingsCardX = pos[0];
                wingsCardY = pos[1];
                rememberedWingsX = wingsCardX;
                rememberedWingsY = wingsCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("aspect".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 205.0F, aspectCardHeight());
                aspectCardX = pos[0];
                aspectCardY = pos[1];
                rememberedAspectX = aspectCardX;
                rememberedAspectY = aspectCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("removals".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, removalsCardHeight());
                removalsCardX = pos[0];
                removalsCardY = pos[1];
                rememberedRemovalsX = removalsCardX;
                rememberedRemovalsY = removalsCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("particles".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, particlesCardHeight());
                particlesCardX = pos[0];
                particlesCardY = pos[1];
                rememberedParticlesX = particlesCardX;
                rememberedParticlesY = particlesCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("jump_circles".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, jumpCirclesCardHeight());
                jumpCirclesCardX = pos[0];
                jumpCirclesCardY = pos[1];
                rememberedJumpCirclesX = jumpCirclesCardX;
                rememberedJumpCirclesY = jumpCirclesCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("trails".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, trailsCardHeight());
                trailsCardX = pos[0];
                trailsCardY = pos[1];
                rememberedTrailsX = trailsCardX;
                rememberedTrailsY = trailsCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("world_customizer".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, worldCustomizerCardHeight());
                worldCustomizerCardX = pos[0];
                worldCustomizerCardY = pos[1];
                rememberedWorldCustomizerX = worldCustomizerCardX;
                rememberedWorldCustomizerY = worldCustomizerCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("hit_color".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, hitColorCardHeight());
                hitColorCardX = pos[0];
                hitColorCardY = pos[1];
                rememberedHitColorX = hitColorCardX;
                rememberedHitColorY = hitColorCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("hitbox_customizer".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, hitboxCustomizerCardHeight());
                hitboxCustomizerCardX = pos[0];
                hitboxCustomizerCardY = pos[1];
                rememberedHitboxX = hitboxCustomizerCardX;
                rememberedHitboxY = hitboxCustomizerCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("block_overlay".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, blockOverlayCardHeight());
                blockOverlayCardX = pos[0];
                blockOverlayCardY = pos[1];
                rememberedBlockOverlayX = blockOverlayCardX;
                rememberedBlockOverlayY = blockOverlayCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("item_radius".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, itemRadiusCardHeight());
                itemRadiusCardX = pos[0];
                itemRadiusCardY = pos[1];
                rememberedItemRadiusX = itemRadiusCardX;
                rememberedItemRadiusY = itemRadiusCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("animations".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, animationsCardHeight());
                animationsCardX = pos[0];
                animationsCardY = pos[1];
                rememberedAnimationsX = animationsCardX;
                rememberedAnimationsY = animationsCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("tab_customizer".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, tabCustomizerCardHeight());
                tabCustomizerCardX = pos[0];
                tabCustomizerCardY = pos[1];
                rememberedTabCustomizerX = tabCustomizerCardX;
                rememberedTabCustomizerY = tabCustomizerCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("freelook".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, freeLookCardHeight());
                freeLookCardX = pos[0];
                freeLookCardY = pos[1];
                rememberedFreeLookX = freeLookCardX;
                rememberedFreeLookY = freeLookCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("zoom".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, zoomCardHeight());
                zoomCardX = pos[0];
                zoomCardY = pos[1];
                rememberedZoomX = zoomCardX;
                rememberedZoomY = zoomCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("autoswap".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 245.0F, autoSwapCardHeight());
                autoSwapCardX = pos[0];
                autoSwapCardY = pos[1];
                rememberedAutoSwapX = autoSwapCardX;
                rememberedAutoSwapY = autoSwapCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("elytra_swap".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 245.0F, elytraSwapCardHeight());
                elytraSwapCardX = pos[0];
                elytraSwapCardY = pos[1];
                rememberedElytraSwapX = elytraSwapCardX;
                rememberedElytraSwapY = elytraSwapCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("item_resorter".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, ITEM_RESORTER_CARD_W, itemResorterCardHeight());
                itemResorterCardX = pos[0];
                itemResorterCardY = pos[1];
                rememberedItemResorterX = itemResorterCardX;
                rememberedItemResorterY = itemResorterCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("auto_buy".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 245.0F, autoBuyCardHeight());
                autoBuyCardX = pos[0];
                autoBuyCardY = pos[1];
                rememberedAutoBuyX = autoBuyCardX;
                rememberedAutoBuyY = autoBuyCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("auto_resell_afk".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 260.0F, 330.0F);
                autoResellAfkCardX = pos[0];
                autoResellAfkCardY = pos[1];
                FluxVisualsClient.requestConfigSave();
            } else if ("telegram".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 245.0F, telegramCardHeight());
                telegramCardX = pos[0];
                telegramCardY = pos[1];
                rememberedTelegramX = telegramCardX;
                rememberedTelegramY = telegramCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("target_hud".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, targetHudCardHeight());
                targetHudCardX = pos[0];
                targetHudCardY = pos[1];
                rememberedTargetHudX = targetHudCardX;
                rememberedTargetHudY = targetHudCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("tape_mouse".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, tapeMouseCardHeight());
                tapeMouseCardX = pos[0];
                tapeMouseCardY = pos[1];
                rememberedTapeMouseX = tapeMouseCardX;
                rememberedTapeMouseY = tapeMouseCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("anarchy_switcher".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 245.0F, anarchySwitcherCardHeight());
                anarchySwitcherCardX = pos[0];
                anarchySwitcherCardY = pos[1];
                rememberedAnarchySwitcherX = anarchySwitcherCardX;
                rememberedAnarchySwitcherY = anarchySwitcherCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("name_binder".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 270.0F, nameBinderCardHeight());
                nameBinderCardX = pos[0];
                nameBinderCardY = pos[1];
                rememberedNameBinderX = nameBinderCardX;
                rememberedNameBinderY = nameBinderCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("macros".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 270.0F, macrosCardHeight());
                macrosCardX = pos[0];
                macrosCardY = pos[1];
                rememberedMacrosX = macrosCardX;
                rememberedMacrosY = macrosCardY;
                FluxVisualsClient.requestConfigSave();
            } else if ("crosshair".equals(draggingCard)) {
                float[] pos = avoidMainPanel(baseX(mouseX) - dragOffsetX, baseY(mouseY) - dragOffsetY, 225.0F, crosshairCardHeight());
                crosshairCardX = pos[0];
                crosshairCardY = pos[1];
                rememberedCrosshairX = crosshairCardX;
                rememberedCrosshairY = crosshairCardY;
                FluxVisualsClient.requestConfigSave();
            }
            return true;
        }
        return false;
    }

    void mouseReleased() {
        draggingSlider = null;
        draggingCard = null;
    }

    boolean charTyped(char chr) {
        if (bindingTarget != null || bindingOptionTarget != null) {
            return true;
        }
        if (anarchyInputFocused) {
            if (!Character.isISOControl(chr) && anarchyDraft.length() < 12) {
                anarchyDraft.append(chr);
            }
            return true;
        }
        if (telegramTokenFocused) {
            if (!Character.isISOControl(chr) && telegramTokenDraft.length() < 256) {
                telegramTokenDraft.append(chr);
            }
            return true;
        }
        if (telegramChatFocused) {
            if (!Character.isISOControl(chr) && telegramChatDraft.length() < 64) {
                telegramChatDraft.append(chr);
            }
            return true;
        }
        if (autoBuyAnarchyInputFocused) {
            if (!Character.isISOControl(chr) && autoBuyAnarchyDraft.length() < 12) {
                autoBuyAnarchyDraft.append(chr);
            }
            return true;
        }
        if (autoBuyNameFocused) {
            if (!Character.isISOControl(chr) && autoBuyNameDraft.length() < 96) {
                autoBuyNameDraft.append(chr);
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy().setNameText(autoBuyNameDraft.toString());
            }
            return true;
        }
        if (autoResellAfkChatFocused) {
            if (!Character.isISOControl(chr) && autoResellAfkChatDraft.length() < 180) autoResellAfkChatDraft.append(chr);
            return true;
        }
        if (autoResellAfkPriceFocused) {
            if (!Character.isISOControl(chr) && (Character.isDigit(chr) || chr == 'k' || chr == 'K' || chr == 'm' || chr == 'M' || chr == '.')) {
                autoResellAfkPriceDraft.append(chr);
            }
            return true;
        }
        if (autoBuySellerBanFocused) {
            if (!Character.isISOControl(chr) && autoBuySellerBanDraft.length() < 32) {
                autoBuySellerBanDraft.append(chr);
            }
            return true;
        }
        if (nameBinderInputFocused) {
            if (!Character.isISOControl(chr) && nameBinderDraft.length() < 96) {
                nameBinderDraft.append(chr);
            }
            return true;
        }
        if (macrosInputFocused) {
            if (!Character.isISOControl(chr) && macrosDraft.length() < 180) {
                macrosDraft.append(chr);
            }
            return true;
        }
        if (anarchyAdFocused) {
            if (!Character.isISOControl(chr) && anarchyAdDraft.length() < 180) {
                anarchyAdDraft.append(chr);
            }
            return true;
        }
        if (autoBuyAnarchyAdFocused) {
            if (!Character.isISOControl(chr) && autoBuyAnarchyAdDraft.length() < 180) {
                autoBuyAnarchyAdDraft.append(chr);
            }
            return true;
        }
        if (itemResorterFocusedInput != null) {
            if (Character.isISOControl(chr) || activeItemResorterDraft().length() >= 72) {
                return true;
            }
            activeItemResorterDraft().append(chr);
            return true;
        }
        if (itemResorterPriceFocused) {
            if (!Character.isISOControl(chr) && Character.isDigit(chr) && itemResorterPriceDraft.length() < 16) {
                itemResorterPriceDraft.append(chr);
            }
            return true;
        }
        if (!searchOpen || Character.isISOControl(chr) || search.length() >= 32) {
            return false;
        }
        search.append(chr);
        return true;
    }

    boolean keyPressed(int keyCode) {
        if (bindingTarget != null || bindingOptionTarget != null) {
            String bindKey = bindingTarget != null ? bindingTarget.name : bindingOptionTarget;
            if (bindKey.startsWith("namebinder:")) {
                int index = parseIndex(bindKey.substring("namebinder:".length()));
                int bind = (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE)
                        ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
                FluxVisualsClient.MODULE_MANAGER.getNameBind().updateBind(index, bind);
                bindingTarget = null;
                bindingOptionTarget = null;
                return true;
            }
            if (bindKey.startsWith("macro:")) {
                int index = parseIndex(bindKey.substring("macro:".length()));
                int bind = (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE)
                        ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
                FluxVisualsClient.MODULE_MANAGER.getMacros().updateBind(index, bind);
                bindingTarget = null;
                bindingOptionTarget = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (FreeLook.PERSPECTIVE_BIND_KEY.equals(bindKey) || Zoom.BIND_KEY.equals(bindKey)) {
                    REMEMBERED_BINDS.put(bindKey, GLFW.GLFW_KEY_UNKNOWN);
                } else {
                    REMEMBERED_BINDS.remove(bindKey);
                }
            } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                REMEMBERED_BINDS.put(bindKey, keyCode);
                BIND_KEY_DOWN.put(keyCode, true);
            }
            FluxVisualsClient.requestConfigSave();
            bindingTarget = null;
            bindingOptionTarget = null;
            return true;
        }

        if (anarchyInputFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(anarchyDraft, 12);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !anarchyDraft.isEmpty()) {
                anarchyDraft.deleteCharAt(anarchyDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAnarchyDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                anarchyInputFocused = false;
                return true;
            }
            return true;
        }
        if (telegramTokenFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(telegramTokenDraft, 256);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !telegramTokenDraft.isEmpty()) {
                telegramTokenDraft.deleteCharAt(telegramTokenDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitTelegramTokenDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                telegramTokenFocused = false;
                return true;
            }
            return true;
        }
        if (telegramChatFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(telegramChatDraft, 64);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !telegramChatDraft.isEmpty()) {
                telegramChatDraft.deleteCharAt(telegramChatDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitTelegramChatDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                telegramChatFocused = false;
                return true;
            }
            return true;
        }

        if (autoBuyAnarchyInputFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoBuyAnarchyDraft, 12);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoBuyAnarchyDraft.isEmpty()) {
                autoBuyAnarchyDraft.deleteCharAt(autoBuyAnarchyDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAutoBuyAnarchyDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                autoBuyAnarchyInputFocused = false;
                return true;
            }
            return true;
        }

        if (autoBuyNameFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoBuyNameDraft, 96);
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy().setNameText(autoBuyNameDraft.toString());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoBuyNameDraft.isEmpty()) {
                autoBuyNameDraft.deleteCharAt(autoBuyNameDraft.length() - 1);
                FluxVisualsClient.MODULE_MANAGER.getAutoBuy().setNameText(autoBuyNameDraft.toString());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                autoBuyNameFocused = false;
                return true;
            }
            return true;
        }
        if (autoResellAfkChatFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoResellAfkChatDraft, 180);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoResellAfkChatDraft.isEmpty()) {
                autoResellAfkChatDraft.deleteCharAt(autoResellAfkChatDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().setChatMessage(autoResellAfkChatDraft.toString());
                autoResellAfkChatFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                autoResellAfkChatFocused = false;
                return true;
            }
            return true;
        }
        if (autoResellAfkPriceFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoResellAfkPriceDraft, 32);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoResellAfkPriceDraft.isEmpty()) {
                autoResellAfkPriceDraft.deleteCharAt(autoResellAfkPriceDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().setSellPrice(autoResellAfkPriceDraft.toString());
                autoResellAfkPriceFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                autoResellAfkPriceFocused = false;
                return true;
            }
            return true;
        }

        if (autoBuySellerBanFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoBuySellerBanDraft, 32);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoBuySellerBanDraft.isEmpty()) {
                autoBuySellerBanDraft.deleteCharAt(autoBuySellerBanDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAutoBuySellerBanDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                autoBuySellerBanFocused = false;
                return true;
            }
            return true;
        }

        if (nameBinderInputFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(nameBinderDraft, 96);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameBinderDraft.isEmpty()) {
                nameBinderDraft.deleteCharAt(nameBinderDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitNameBinderDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameBinderInputFocused = false;
                return true;
            }
            return true;
        }

        if (macrosInputFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(macrosDraft, 180);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !macrosDraft.isEmpty()) {
                macrosDraft.deleteCharAt(macrosDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitMacrosDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                macrosInputFocused = false;
                return true;
            }
            return true;
        }

        if (anarchyAdFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(anarchyAdDraft, 180);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !anarchyAdDraft.isEmpty()) {
                anarchyAdDraft.deleteCharAt(anarchyAdDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAnarchyAdDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                submitAnarchyAdDraft();
                anarchyAdFocused = false;
                return true;
            }
            return true;
        }

        if (autoBuyAnarchyAdFocused) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteToDraft(autoBuyAnarchyAdDraft, 180);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !autoBuyAnarchyAdDraft.isEmpty()) {
                autoBuyAnarchyAdDraft.deleteCharAt(autoBuyAnarchyAdDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAutoBuyAnarchyAdDraft();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                submitAutoBuyAnarchyAdDraft();
                autoBuyAnarchyAdFocused = false;
                return true;
            }
            return true;
        }

        if (itemResorterFocusedInput != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                StringBuilder draft = activeItemResorterDraft();
                if (!draft.isEmpty()) {
                    draft.deleteCharAt(draft.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitFocusedItemResorterInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                itemResorterFocusedInput = null;
                return true;
            }
            return true;
        }
        if (itemResorterPriceFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                seedItemResorterPriceDraftForEdit();
                if (!itemResorterPriceDraft.isEmpty()) {
                    itemResorterPriceDraft.deleteCharAt(itemResorterPriceDraft.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                itemResorterPriceDraft.setLength(0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitItemResorterPrice();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                itemResorterPriceFocused = false;
                return true;
            }
            return true;
        }

        if (!searchOpen) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search.deleteCharAt(search.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchOpen = false;
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float bx = baseX(mouseX);
        float by = baseY(mouseY);
        if (aspectPresetListAt(bx, by)) {
            aspectPresetScroll = clamp(aspectPresetScroll - (float) amount * 18.0F, 0.0F, maxAspectPresetScroll());
            return true;
        }
        if (showParticles && inside(bx, by, particlesCardX, particlesCardY, 225.0F, particlesCardHeight())) {
            particlesScroll = clamp(particlesScroll - (float) amount * 22.0F, 0.0F, maxParticlesScroll());
            return true;
        }
        if (inside(bx, by, MODULE_LIST_X - 8.0F, MODULE_LIST_Y - 8.0F, MODULE_LIST_W + 16.0F, MODULE_LIST_H + 16.0F)) {
            moduleScroll = clamp(moduleScroll - (float) amount * 26.0F, 0.0F, maxModuleScroll());
            return maxModuleScroll() > 0.0F;
        }
        return false;
    }

    private void drawMain(DrawContext context, int mouseX, int mouseY, float alpha, float dt) {
        shadow(context, MAIN_X, MAIN_Y, MAIN_W, MAIN_H, 18.0F, alpha);
        rounded(context, MAIN_X, MAIN_Y, MAIN_W, MAIN_H, 18.0F, color(PANEL, 255, alpha));
        outline(context, MAIN_X, MAIN_Y, MAIN_W, MAIN_H, 18.0F, color(OUTLINE, 185, alpha), 1.0F);
        fill(context, MAIN_X + 20.0F, MAIN_Y + 52.0F, MAIN_W - 40.0F, 1.0F, color(OUTLINE, 185, alpha));

        drawTab(context, Category.VISUALS, 345.0F, 216.0F, mouseX, mouseY, alpha, dt);
        drawTab(context, Category.HUD, 478.0F, 216.0F, mouseX, mouseY, alpha, dt);
        drawTab(context, Category.UTILS, 611.0F, 216.0F, mouseX, mouseY, alpha, dt);
        drawSearch(context, mouseX, mouseY, alpha, dt);
        drawModules(context, mouseX, mouseY, alpha, dt);
    }

    private void drawLiquidGlassBackdrop(DrawContext context, int width, int height, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }

        BlurRenderer.drawBlur(0.0F, 0.0F, width, height, 0.0F, alpha);
        Render2D.drawRound(0.0F, 0.0F, width, height, 0.0F, argb(65, 7, 9, 14, alpha));
    }

    private void drawTab(DrawContext context, Category category, float x, float y, int mouseX, int mouseY, float alpha, float dt) {
        boolean hovered = inside(baseX(mouseX), baseY(mouseY), x, y, 106.0F, 27.0F);
        float hover = approach(tabHover.get(category), hovered ? 1.0F : 0.0F, dt, 14.0F);
        tabHover.put(category, hover);
        boolean active = selectedCategory == category;
        rounded(context, x, y, 106.0F, 27.0F, 13.5F,
                active ? mix(MODULE_HOVER, PURPLE_DARK, 0.18F, 226, alpha) : mix(TOGGLE_OFF, MODULE_HOVER, hover, 206, alpha));
        if (active) {
            outline(context, x, y, 106.0F, 27.0F, 13.5F, color(OUTLINE_GLOW, 38, alpha), 1.0F);
        }
        drawCenteredText(context, category.label, x, y - 0.5F, 106.0F, 27.0F, 1.28F,
                active ? color(TEXT_MAIN, 245, alpha) : color(TEXT_SECONDARY, 210, alpha), FontRole.INTER);
    }

    private void drawSearch(DrawContext context, int mouseX, int mouseY, float alpha, float dt) {
        searchProgress = approach(searchProgress, searchOpen ? 1.0F : 0.0F, dt, 14.0F);
        boolean hovered = searchHitbox(baseX(mouseX), baseY(mouseY));
        float x = 807.0F - 72.0F * searchProgress;
        float w = 30.0F + 72.0F * searchProgress;
        rounded(context, x, 215.0F, w, 30.0F, 15.0F, mix(TOGGLE_OFF, MODULE_HOVER, hovered ? 1.0F : 0.0F, 232, alpha));
        outline(context, x, 215.0F, w, 30.0F, 15.0F, searchOpen ? color(OUTLINE_ACTIVE, 66, alpha) : color(OUTLINE, 108, alpha), 1.0F);
        if (searchProgress < 0.98F) {
            float iconAlpha = alpha * (1.0F - searchProgress);
            drawIcon(context, SEARCH_ICON, 814.0F, 222.0F, 16.0F, 16.0F, color(TEXT_MAIN, 245, iconAlpha));
        }
        if (searchProgress > 0.02F) {
            drawLeftCenteredText(context, search.isEmpty() ? "Search" : search.toString(), x + 12.0F, 215.0F, w - 20.0F, 30.0F, 1.16F,
                    search.isEmpty() ? color(TEXT_DIM, 180, alpha * searchProgress) : color(TEXT_MAIN, 232, alpha * searchProgress));
        }
    }

    private void drawModules(DrawContext context, int mouseX, int mouseY, float alpha, float dt) {
        List<VisualModule> visible = visibleModules();
        moduleScroll = clamp(moduleScroll, 0.0F, maxModuleScroll(visible));
        VisualModule hovered = null;
        if (visible.isEmpty()) {
            drawCenteredText(context, "NoModuels", MAIN_X, 270.0F, MAIN_W, 170.0F, 1.85F, color(TEXT_DIM, 218, alpha), FontRole.OXANIUM);
            return;
        }

        int x1 = Math.round(sx(MODULE_LIST_X - 8.0F));
        int y1 = Math.round(sy(MODULE_LIST_Y - 8.0F));
        int x2 = Math.round(sx(MODULE_LIST_X + MODULE_LIST_W + 8.0F));
        int y2 = Math.round(sy(MODULE_LIST_Y + MODULE_LIST_H + 8.0F));
        context.enableScissor(x1, y1, x2, y2);
        for (int i = 0; i < visible.size(); i++) {
            float x = (i % 2 == 0) ? MODULE_LIST_X : 608.0F;
            float y = MODULE_LIST_Y + (i / 2) * MODULE_GAP_Y - moduleScroll;
            if (y + MODULE_H < MODULE_LIST_Y - 8.0F || y > MODULE_LIST_Y + MODULE_LIST_H + 8.0F) {
                continue;
            }
            VisualModule module = visible.get(i);
            if (drawModule(context, module, x, y, mouseX, mouseY, alpha, dt)) {
                hovered = module;
            }
        }
        context.disableScissor();

        float maxScroll = maxModuleScroll(visible);
        if (maxScroll > 0.5F) {
            float trackX = MAIN_X + MAIN_W - 20.0F;
            float trackY = MODULE_LIST_Y - 2.0F;
            float trackH = MODULE_LIST_H + 4.0F;
            float thumbH = Math.max(34.0F, trackH * (MODULE_LIST_H / (MODULE_LIST_H + maxScroll)));
            float thumbY = trackY + (trackH - thumbH) * (moduleScroll / maxScroll);
            rounded(context, trackX, trackY, 4.0F, trackH, 2.0F, color(APP_BACKGROUND, 108, alpha));
            rounded(context, trackX, thumbY, 4.0F, thumbH, 2.0F, color(PURPLE, 188, alpha));
        }

        if (hovered != null) {
            float tooltipW = 260.0F;
            float tooltipX = MAIN_X + (MAIN_W - tooltipW) * 0.5F;
            float tooltipY = MAIN_Y - 56.0F;
            rounded(context, tooltipX, tooltipY, tooltipW, 32.0F, 16.0F, color(MODULE_HOVER, 232, alpha * hovered.hover));
            outline(context, tooltipX, tooltipY, tooltipW, 32.0F, 16.0F, color(OUTLINE_GLOW, 44, alpha * hovered.hover), 1.0F);
            drawCenteredText(context, moduleHint(hovered.name), tooltipX, tooltipY - 1.0F, tooltipW, 32.0F, 1.32F, color(TEXT_MAIN, 245, alpha * hovered.hover));
        }
    }

    private boolean drawModule(DrawContext context, VisualModule module, float x, float y, int mouseX, int mouseY, float alpha, float dt) {
        if ("FullBright".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getFullBright().isEnabled();
        } else if ("China Hat".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getChinaHat().isEnabled();
        } else if ("Aspect Ratio".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getAspectRatio().isEnabled();
        } else if ("Particles".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getParticles().isEnabled();
        } else if ("Trails".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getTrails().isEnabled();
        } else if ("ItemRadius".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getItemRadius().isEnabled();
        } else if ("FreeLook".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getFreeLook().isEnabled();
        } else if ("Zoom".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getZoom().isEnabled();
        } else if ("ElytraSwap".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getElytraSwap().isEnabled();
        } else if ("Crosshair".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getCrosshair().isEnabled();
        } else if ("Watermark".equals(module.name)) {
            module.active = FluxVisualsClient.MODULE_MANAGER.getWatermark().isEnabled();
        }
        boolean hovered = inside(baseX(mouseX), baseY(mouseY), x, y, MODULE_W, MODULE_H);
        module.hover = approach(module.hover, hovered ? 1.0F : 0.0F, dt, 16.0F);
        module.toggle = approach(module.toggle, module.active ? 1.0F : 0.0F, dt, 14.0F);

        rounded(context, x, y, MODULE_W, MODULE_H, 14.0F, mix(MODULE_CARD, MODULE_HOVER, module.hover, 235, alpha));
        outline(context, x, y, MODULE_W, MODULE_H, 16.0F, color(OUTLINE, 116, alpha), 1.0F);
        drawLeftCenteredText(context, module.name, x + 16.0F, y, 144.0F, MODULE_H, 1.24F,
                module.hover > 0.01F || module.active ? mix(TEXT_SECONDARY, TEXT_MAIN, Math.max(module.hover, module.toggle * 0.75F), 238, alpha)
                        : color(TEXT_SECONDARY, 224, alpha));
        drawBindLabel(context, module, x + MODULE_W - 84.0F, y + 10.5F, alpha);
        drawToggle(context, x + MODULE_W - 58.0F, y + 11.5F, module.toggle, module.hover, alpha);
        return hovered;
    }

    private void drawBindLabel(DrawContext context, VisualModule module, float x, float y, float alpha) {
        int keyCode = REMEMBERED_BINDS.getOrDefault(module.name, GLFW.GLFW_KEY_UNKNOWN);
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        String label = keyLabel(keyCode);
        if (label.isEmpty()) {
            return;
        }

        drawCenteredText(context, label, x, y, 20.0F, 20.0F, label.length() > 1 ? 0.78F : 1.0F,
                color(TEXT_MAIN, 255, alpha), label.length() > 1 ? FontRole.INTER : FontRole.MONO);
    }

    private void drawToggle(DrawContext context, float x, float y, float active, float hover, float alpha) {
        rounded(context, x, y, 42.0F, 20.0F, 10.0F, mix(TOGGLE_OFF, TOGGLE_ON, active, 236, alpha));
        outline(context, x, y, 42.0F, 20.0F, 10.0F, mix(OUTLINE, OUTLINE_GLOW, active, 82, alpha), 1.0F);
        rounded(context, x + 2.0F + 22.0F * active, y + 2.0F, 16.0F, 16.0F, 8.0F, color(TOGGLE_KNOB, 255, alpha));
    }

    private void drawBindOverlay(DrawContext context, float open, float dt) {
        bindOverlayProgress = approach(bindOverlayProgress, (bindingTarget == null && bindingOptionTarget == null) ? 0.0F : 1.0F, dt, 16.0F);
        if (bindOverlayProgress <= 0.02F) {
            return;
        }

        float alpha = open * bindOverlayProgress;
        rounded(context, MAIN_X, MAIN_Y, MAIN_W, MAIN_H, 18.0F, color(APP_BACKGROUND, 168, alpha));
        rounded(context, MAIN_X + 1.0F, MAIN_Y + 1.0F, MAIN_W - 2.0F, MAIN_H - 2.0F, 17.0F, color(PANEL, 92, alpha));
        rounded(context, MAIN_X + 22.0F, MAIN_Y + 68.0F, MAIN_W - 44.0F, MAIN_H - 94.0F, 15.0F, color(TOGGLE_KNOB, 18, alpha));
        outline(context, MAIN_X, MAIN_Y, MAIN_W, MAIN_H, 18.0F, color(OUTLINE_GLOW, 58, alpha), 1.0F);
        drawCenteredText(context, "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u043b\u044e\u0431\u0443\u044e \u043a\u043b\u0430\u0432\u0438\u0448\u0443", MAIN_X, MAIN_Y - 2.0F, MAIN_W, MAIN_H, 2.0F,
                color(TEXT_MAIN, 255, alpha), FontRole.SF_PRO);
    }

    private void drawTargetEsp(DrawContext context, float alpha, float dt) {
        float x = targetCardX;
        float y = targetCardY;
        TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
        boolean ghosts = targetEsp.getStyle() == TargetEsp.Style.GHOSTS;
        boolean crystals = targetEsp.getStyle() == TargetEsp.Style.CRYSTALS;
        targetStyleProgress = approach(targetStyleProgress, targetStyleOpen ? 1.0F : 0.0F, dt, 14.0F);
        targetFilterProgress = approach(targetFilterProgress, targetFilterOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, targetEspCardHeight(), alpha);
        drawCenteredText(context, "TargetEsp", x + 31.0F, y + 9.0F, 145.0F, 22.0F, 1.36F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float rowY = targetContentY();
        drawText(context, "\u0422\u0438\u043f", x + 13.0F, rowY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        drawBlockOverlaySelect(context, targetEsp.getStyle().label(), x + 112.0F, rowY - 1.0F, 100.0F, targetStyleProgress, alpha);
        if (targetStyleProgress > 0.02F) {
            drawTargetStyleList(context, x + 112.0F, rowY + 28.0F, targetStyleProgress, alpha);
        }

        rowY += 34.0F + targetStyleProgress * targetStyleListHeight();
        drawText(context, "\u0426\u0435\u043b\u0438", x + 13.0F, rowY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        drawBlockOverlaySelect(context, targetEsp.getTargetFilter().label(), x + 112.0F, rowY - 1.0F, 100.0F, targetFilterProgress, alpha);
        if (targetFilterProgress > 0.02F) {
            drawTargetFilterList(context, x + 112.0F, rowY + 28.0F, targetFilterProgress, alpha);
        }

        rowY += 56.0F + targetFilterProgress * targetFilterListHeight();
        drawText(context, "\u0414\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044f", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "target_distance", x + 20.0F, rowY, 170.0F, (targetEsp.getMaxDistance() - 2.0F) / 30.0F,
                new String[]{"2", "17", "32"}, alpha, dt);

        rowY += 52.0F;
        drawText(context, "\u041f\u043e\u0442\u0435\u0440\u044f", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "target_lost_delay", x + 20.0F, rowY, 170.0F, (targetEsp.getLostDelaySeconds() - 0.2F) / 3.8F,
                new String[]{"0.2", "2.1", "4"}, alpha, dt);

        rowY += 52.0F;
        if (crystals) {
            drawText(context, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c \u043a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u043e\u0432", x + 13.0F, rowY - 27.0F, 0.96F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_crystal_speed", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getCrystalSpeed() - TargetEsp.CRYSTAL_SPEED_MIN) / TargetEsp.CRYSTAL_SPEED_RANGE,
                    new String[]{"0.2", "2.1", "4"}, alpha, dt);

            rowY += 52.0F;
            drawText(context, "\u041a\u043e\u043b-\u0432\u043e \u043a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u043e\u0432", x + 13.0F, rowY - 27.0F, 0.96F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_crystal_count", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getCrystalCount() - TargetEsp.CRYSTAL_COUNT_MIN) / (float) (TargetEsp.CRYSTAL_COUNT_MAX - TargetEsp.CRYSTAL_COUNT_MIN),
                    new String[]{"1", "6", "12"}, alpha, dt);

            rowY += 52.0F;
            drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440 \u043a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u043e\u0432", x + 13.0F, rowY - 27.0F, 0.96F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_crystal_size", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getCrystalSize() - TargetEsp.CRYSTAL_SIZE_MIN) / TargetEsp.CRYSTAL_SIZE_RANGE,
                    new String[]{"0.55", "1.4", "2.2"}, alpha, dt);

            rowY += 52.0F;
            drawText(context, "\u0420\u0430\u0434\u0438\u0443\u0441 \u043e\u0440\u0431\u0438\u0442\u044b", x + 13.0F, rowY - 27.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_crystal_radius", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getCrystalRadius() - TargetEsp.CRYSTAL_RADIUS_MIN) / TargetEsp.CRYSTAL_RADIUS_RANGE,
                    new String[]{"0.35", "1.0", "1.65"}, alpha, dt);
        } else if (!ghosts) {
            drawText(context, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_speed", x + 20.0F, rowY, 170.0F, (targetEsp.getAnimationSpeed() - 0.1F) / 3.9F,
                    new String[]{"0.1", "2", "4"}, alpha, dt);
            rowY += 52.0F;
        }

        if (ghosts) {
            drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440 \u0434\u0443\u0445\u043e\u0432", x + 13.0F, rowY - 27.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_ghost_size", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getGhostSize() - TargetEsp.GHOST_SIZE_MIN) / TargetEsp.GHOST_SIZE_RANGE,
                    new String[]{"0.45", "2", "3.5"}, alpha, dt);
        } else if (!crystals) {
            drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440 target", x + 13.0F, rowY - 27.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_size", x + 20.0F, rowY, 170.0F, (targetEsp.getNormalSize() - 0.45F) / 1.55F,
                    new String[]{"S", "M", "L"}, alpha, dt);
        }

        if (ghosts) {
            rowY += 52.0F;
            drawText(context, "\u041a\u043e\u043b-\u0432\u043e \u0434\u0443\u0445\u043e\u0432", x + 13.0F, rowY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_ghost_count", x + 20.0F, rowY, 170.0F, (targetEsp.getGhostCount() - 1.0F) / 11.0F,
                    new String[]{"1", "6", "12"}, alpha, dt);

            rowY += 52.0F;
            drawText(context, "\u0414\u043b\u0438\u043d\u0430 \u0434\u0443\u0445\u043e\u0432", x + 13.0F, rowY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_ghost_length", x + 20.0F, rowY, 170.0F,
                    (targetEsp.getGhostLength() - TargetEsp.GHOST_LENGTH_MIN) / TargetEsp.GHOST_LENGTH_RANGE,
                    new String[]{"0.4", "2.7", "5"}, alpha, dt);

            rowY += 52.0F;
            drawText(context, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
            slider(context, "target_ghost_speed", x + 20.0F, rowY, 170.0F, (targetEsp.getGhostSpeed() - 0.2F) / 4.8F,
                    new String[]{"0.2", "2.6", "5"}, alpha, dt);
        }

        rowY += 40.0F;
        drawBooleanToggleRow(context, "\u041a\u0440\u0430\u0441\u043d\u0435\u0442\u044c \u043f\u0440\u0438 \u0443\u0434\u0430\u0440\u0435", x + 13.0F, rowY, targetEsp.isRedOnHit() ? 1.0F : 0.0F, alpha);

        rowY += 38.0F;
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, rowY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, rowY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, rowY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | targetEsp.getColorRgb(), alpha));
        outline(context, x + 126.0F, rowY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (targetColorOpen) {
            drawTargetPalette(context, x + 15.0F, targetPaletteY(), alpha);
        }
    }

    private void drawChinaHat(DrawContext context, float alpha, float dt) {
        float x = chinaCardX;
        float y = chinaCardY;
        ChinaHat chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
        int activeColor = chinaHat.getColorRgb();
        chinaFillModeProgress = approach(chinaFillModeProgress, chinaFillModeOpen ? 1.0F : 0.0F, dt, 14.0F);
        chinaShaderProgress = approach(chinaShaderProgress, chinaShaderOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 205.0F, chinaCardHeight(), alpha);
        drawCenteredText(context, "China Hat", x + 27.0F, y + 9.0F, 140.0F, 22.0F, 1.5F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 171.0F, y + 12.0F, alpha);
        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440", x + 13.0F, y + 47.0F, 1.16F, color(TEXT_MAIN, 238, alpha));
        slider(context, "china_size", x + 14.0F, y + 74.0F, 138.0F, chinaHat.getSize(),
                new String[]{"S", "M", "L"}, alpha, dt);

        float fillModeY = chinaFillModeY();
        drawText(context, "\u0417\u0430\u043b\u0438\u0432\u043a\u0430", x + 13.0F, fillModeY + 4.0F, 1.06F, color(TEXT_MAIN, 238, alpha));
        drawBlockOverlaySelect(context, chinaHat.getFillMode().label(), x + 88.0F, fillModeY, 100.0F, chinaFillModeProgress, alpha);
        if (chinaFillModeProgress > 0.02F) {
            drawChinaFillModeList(context, x + 88.0F, fillModeY + 29.0F, chinaFillModeProgress, alpha);
        }

        if (chinaHat.getFillMode() == ChinaHat.FillMode.SHADER) {
            float shaderY = chinaShaderY();
            drawText(context, "\u0428\u0435\u0439\u0434\u0435\u0440", x + 13.0F, shaderY + 4.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            drawBlockOverlaySelect(context, chinaHat.getShaderType().label(), x + 88.0F, shaderY, 100.0F, chinaShaderProgress, alpha);
            if (chinaShaderProgress > 0.02F) {
                drawChinaShaderList(context, x + 88.0F, shaderY + 29.0F, chinaShaderProgress, alpha);
            }

            float shaderAlphaY = chinaShaderAlphaY();
            drawText(context, "\u041f\u0440\u043e\u0437\u0440. \u0448\u0435\u0439\u0434\u0435\u0440\u0430", x + 13.0F, shaderAlphaY - 27.0F, 0.98F, color(TEXT_MAIN, 238, alpha));
            slider(context, "china_shader_alpha", x + 17.0F, shaderAlphaY, 158.0F,
                    chinaHat.getShaderAlpha(), new String[]{"0%", "50%", "100%"}, alpha, dt);
        }

        float colorY = chinaColorY();
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, colorY + 4.0F, 1.16F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, colorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(activeColor, alpha));
        outline(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);

        if (chinaPaletteOpen) {
            drawPalette(context, x + 15.0F, chinaPaletteY(), alpha);
        }
        float targetY = colorY + 40.0F;
        ChinaHat hat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
        drawSimpleToggle(context, "Self", x + 13.0F, targetY, hat.isRenderSelf(), alpha);
        drawSimpleToggle(context, "Players", x + 13.0F, targetY + 30.0F, hat.isRenderPlayers(), alpha);
        drawSimpleToggle(context, "Bots", x + 13.0F, targetY + 60.0F, hat.isRenderBots(), alpha);
    }

    private void drawWings(DrawContext context, float alpha, float dt) {
        float x = wingsCardX;
        float y = wingsCardY;
        Wings wings = FluxVisualsClient.MODULE_MANAGER.getWings();
        sideCard(context, x, y, 205.0F, 388.0F, alpha);
        drawCenteredText(context, "Wings", x + 27.0F, y + 9.0F, 140.0F, 22.0F, 1.5F,
                color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 171.0F, y + 12.0F, alpha);

        float typeY = y + 45.0F;
        drawText(context, "\u0424\u043e\u0440\u043c\u0430", x + 13.0F, typeY + 4.0F, 1.05F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 82.0F, typeY, 105.0F, 25.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        outline(context, x + 82.0F, typeY, 105.0F, 25.0F, 8.0F, color(OUTLINE, 90, alpha), 1.0F);
        drawCenteredText(context, wings.getWingType().label(), x + 85.0F, typeY + 1.0F, 99.0F, 22.0F,
                0.92F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        if (wingsTypeOpen) {
            float listY = typeY + 29.0F;
            float listH = Wings.WingType.values().length * 22.0F + 6.0F;
            rounded(context, x + 82.0F, listY, 105.0F, listH, 8.0F, color(MODULE_HOVER, 248, alpha));
            outline(context, x + 82.0F, listY, 105.0F, listH, 8.0F, color(OUTLINE, 100, alpha), 1.0F);
            int i = 0;
            for (Wings.WingType type : Wings.WingType.values()) {
                float rowY = listY + 3.0F + i * 22.0F;
                if (type == wings.getWingType()) rounded(context, x + 85.0F, rowY + 2.0F, 99.0F, 18.0F, 6.0F, color(VIOLET_DEEP, 175, alpha));
                drawCenteredText(context, type.label(), x + 86.0F, rowY, 97.0F, 22.0F, 0.88F, color(TEXT_MAIN, 242, alpha), FontRole.OXANIUM);
                i++;
            }
        }

        drawSimpleToggle(context, "Shader", x + 13.0F, y + 77.0F, wings.isShaderFill(), alpha);
        drawSimpleToggle(context, "Self", x + 13.0F, y + 107.0F, wings.isRenderSelf(), alpha);
        drawSimpleToggle(context, "Players", x + 13.0F, y + 137.0F, wings.isRenderPlayers(), alpha);
        drawSimpleToggle(context, "Bots", x + 13.0F, y + 167.0F, wings.isRenderBots(), alpha);
        drawSimpleToggle(context, "\u0410\u043d\u0438\u043c\u0430\u0446\u0438\u044f", x + 13.0F, y + 197.0F, wings.isFlapping(), alpha);

        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440", x + 13.0F, y + 232.0F, 1.03F, color(TEXT_MAIN, 238, alpha));
        slider(context, "wings_scale", x + 20.0F, y + 254.0F, 170.0F,
                (wings.getScale() - 0.3F) / 2.7F, new String[]{"0.3", "1.65", "3.0"}, alpha, dt);
        drawText(context, "\u0421\u0438\u043b\u0430 \u0440\u0430\u0437\u043c\u0430\u0445\u0430", x + 13.0F, y + 278.0F, 1.03F, color(TEXT_MAIN, 238, alpha));
        slider(context, "wings_strength", x + 20.0F, y + 300.0F, 170.0F,
                (wings.getFlapStrength() - 5.0F) / 55.0F, new String[]{"5", "32", "60"}, alpha, dt);
        drawText(context, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", x + 13.0F, y + 324.0F, 1.03F, color(TEXT_MAIN, 238, alpha));
        slider(context, "wings_speed", x + 20.0F, y + 346.0F, 170.0F,
                (wings.getFlapSpeed() - 0.5F) / 7.5F, new String[]{"0.5", "4.25", "8.0"}, alpha, dt);
    }

    private void drawAspectRatio(DrawContext context, float alpha, float dt) {
        float x = aspectCardX;
        float y = aspectCardY;
        float w = 205.0F;
        float h = aspectCardHeight();
        AspectRatio aspectRatio = FluxVisualsClient.MODULE_MANAGER.getAspectRatio();
        aspectCustomProgress = approach(aspectCustomProgress, aspectRatio.getPreset() == AspectRatio.Preset.CUSTOM ? 1.0F : 0.0F, dt, 14.0F);
        aspectPresetProgress = approach(aspectPresetProgress, aspectPresetOpen ? 1.0F : 0.0F, dt, 14.0F);
        aspectPresetScroll = clamp(aspectPresetScroll, 0.0F, maxAspectPresetScroll());

        sideCard(context, x, y, w, h, alpha);
        drawCenteredText(context, "Aspect Ratio", x + 25.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 171.0F, y + 12.0F, alpha);

        float contentY = aspectContentY();
        drawText(context, "\u041f\u0440\u0435\u0441\u0435\u0442", x + 13.0F, contentY + 3.0F, 1.16F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 97.0F, contentY - 1.0F, 89.0F, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x + 97.0F, contentY - 1.0F, 89.0F, 24.0F, 9.0F,
                aspectPresetProgress > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, aspectRatio.getActiveLabel(), x + 101.0F, contentY, 59.0F, 22.0F, 1.0F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThickArrow(context, ">", x + 164.0F, contentY - 1.0F, alpha * (1.0F - aspectPresetProgress));
        drawThickArrow(context, "V", x + 164.0F, contentY - 1.0F, alpha * aspectPresetProgress);

        if (aspectPresetProgress > 0.02F) {
            float listY = contentY + 28.0F;
            float listH = aspectPresetListHeight() * aspectPresetProgress;
            rounded(context, x + 97.0F, listY, 89.0F, listH, 9.0F, color(MODULE_HOVER, 245, alpha * aspectPresetProgress));
            outline(context, x + 97.0F, listY, 89.0F, listH, 9.0F, color(OUTLINE, 96, alpha * aspectPresetProgress), 1.0F);

            int x1 = Math.round(sx(x + 98.0F));
            int y1 = Math.round(sy(listY + 1.0F));
            int x2 = Math.round(sx(x + 185.0F));
            int y2 = Math.round(sy(listY + Math.max(1.0F, listH - 1.0F)));
            context.enableScissor(x1, y1, x2, y2);
            int index = 0;
            for (AspectRatio.Preset preset : AspectRatio.Preset.values()) {
                float rowY = listY + 3.0F + index * 22.0F - aspectPresetScroll;
                if (preset == aspectRatio.getPreset()) {
                    rounded(context, x + 101.0F, rowY + 2.0F, 81.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 175, alpha * aspectPresetProgress));
                }
                drawCenteredText(context, preset.label(), x + 101.0F, rowY, 81.0F, 22.0F, 0.94F, color(TEXT_MAIN, 242, alpha * aspectPresetProgress), FontRole.OXANIUM);
                index++;
            }
            context.disableScissor();
        }

        if (aspectCustomProgress > 0.02F) {
            float customAlpha = alpha * aspectCustomProgress * (1.0F - aspectPresetProgress);
            float sliderY = contentY + 56.0F;
            drawText(context, "\u0420\u0430\u0441\u0442\u044f\u0433", x + 13.0F, sliderY - 27.0F, 1.12F, color(TEXT_MAIN, 238, customAlpha));
            float normalized = (aspectRatio.getCustomRatio() - 1.0F) / 1.5F;
            slider(context, "aspect_custom", x + 17.0F, sliderY, 158.0F, normalized,
                    new String[]{"1.00", "1.75", "2.50"}, customAlpha, dt);
        }

    }

    private void drawWorldCustomizer(DrawContext context, float alpha, float dt) {
        float x = worldCustomizerCardX;
        float y = worldCustomizerCardY;
        float w = 225.0F;
        float h = worldCustomizerCardHeight();
        WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        worldTimePresetProgress = approach(worldTimePresetProgress, worldTimePresetOpen ? 1.0F : 0.0F, dt, 14.0F);
        worldCustomTimeProgress = approach(worldCustomTimeProgress,
                worldCustomizer.getTimePreset() == WorldCustomizer.TimePreset.CUSTOM ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, w, h, alpha);
        drawCenteredText(context, "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043c\u0438\u0440\u0430", x + 23.0F, y + 9.0F, 160.0F, 22.0F, 1.36F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float contentY = worldCustomizerContentY();
        drawText(context, "\u0412\u0440\u0435\u043c\u044f", x + 13.0F, contentY + 3.0F, 1.1F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 104.0F, contentY - 1.0F, 108.0F, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x + 104.0F, contentY - 1.0F, 108.0F, 24.0F, 9.0F,
                worldTimePresetProgress > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, worldCustomizer.getTimePreset().label(), x + 108.0F, contentY, 78.0F, 22.0F, 0.82F, color(TEXT_MAIN, 245, alpha), FontRole.SF_PRO);
        drawThickArrow(context, ">", x + 191.0F, contentY - 1.0F, alpha * (1.0F - worldTimePresetProgress));
        drawThickArrow(context, "V", x + 191.0F, contentY - 1.0F, alpha * worldTimePresetProgress);

        if (worldTimePresetProgress > 0.02F) {
            float listY = contentY + 28.0F;
            float listH = worldTimePresetListHeight() * worldTimePresetProgress;
            rounded(context, x + 104.0F, listY, 108.0F, listH, 9.0F, color(MODULE_HOVER, 245, alpha * worldTimePresetProgress));
            outline(context, x + 104.0F, listY, 108.0F, listH, 9.0F, color(OUTLINE, 96, alpha * worldTimePresetProgress), 1.0F);

            int index = 0;
            for (WorldCustomizer.TimePreset preset : WorldCustomizer.TimePreset.values()) {
                float rowY = listY + 3.0F + index * 22.0F;
                if (preset == worldCustomizer.getTimePreset()) {
                    rounded(context, x + 108.0F, rowY + 2.0F, 100.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * worldTimePresetProgress));
                }
                drawCenteredText(context, preset.label(), x + 111.0F, rowY, 94.0F, 22.0F, 0.76F,
                        color(TEXT_MAIN, 242, alpha * worldTimePresetProgress), FontRole.SF_PRO);
                index++;
            }
        }

        float sliderY = worldCustomizerSliderBaseY();
        if (worldCustomTimeProgress > 0.02F) {
            float customAlpha = alpha * worldCustomTimeProgress;
            drawText(context, "\u041a\u0430\u0441\u0442\u043e\u043c\u043d\u043e\u0435 \u0432\u0440\u0435\u043c\u044f", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, customAlpha));
            slider(context, "world_custom_time", x + 20.0F, sliderY, 170.0F,
                    worldCustomizer.getCustomTime() / 23999.0F, new String[]{"0", "12000", "24000"}, customAlpha, dt);
        }

        float fogToggleY = worldCustomizerFogToggleY();
        boolean customFog = worldCustomizer.isCustomFogEnabled();
        float fogAlpha = alpha * (customFog ? 1.0F : 0.42F);
        drawBooleanToggleRow(context, "\u041a\u0430\u0441\u0442\u043e\u043c\u043d\u044b\u0439 \u0442\u0443\u043c\u0430\u043d", x + 13.0F, fogToggleY, customFog ? 1.0F : 0.0F, alpha);

        float fogDistanceY = worldCustomizerFogDistanceY();
        drawText(context, "\u0414\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044f \u0442\u0443\u043c\u0430\u043d\u0430", x + 13.0F, fogDistanceY - 27.0F, 1.08F, color(TEXT_MAIN, 238, fogAlpha));
        float fogDistanceValue = (worldCustomizer.getFogDistance() - 8.0F) / 142.0F;
        if (customFog) {
            slider(context, "world_fog_distance", x + 20.0F, fogDistanceY, 170.0F, fogDistanceValue,
                    new String[]{"8", "79", "150"}, alpha, dt);
        } else {
            disabledSlider(context, x + 20.0F, fogDistanceY, 170.0F, fogDistanceValue, new String[]{"8", "79", "150"}, fogAlpha);
        }

        float colorY = worldFogColorY();
        drawText(context, "\u0426\u0432\u0435\u0442 \u0442\u0443\u043c\u0430\u043d\u0430", x + 13.0F, colorY + 4.0F, 1.08F, color(TEXT_MAIN, 238, fogAlpha));
        rounded(context, x + 13.0F, colorY + 28.0F, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, fogAlpha));
        rounded(context, x + 18.0F, colorY + 33.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | worldCustomizer.getFogColorRgb(), fogAlpha));
        outline(context, x + 13.0F, colorY + 28.0F, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, fogAlpha), 1.0F);
        if (customFog && worldFogPaletteOpen) {
            drawWorldFogPalette(context, x + 15.0F, worldFogPaletteY(), alpha);
        }
    }

    private void drawParticles(DrawContext context, float alpha, float dt) {
        float x = particlesCardX;
        float y = particlesCardY;
        float w = 225.0F;
        float h = particlesCardHeight();
        Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
        particleTextureProgress = approach(particleTextureProgress, particleTextureOpen ? 1.0F : 0.0F, dt, 14.0F);
        particleMultiProgress = approach(particleMultiProgress, particleMultiOpen ? 1.0F : 0.0F, dt, 14.0F);
        particlesOutlineProgress = approach(particlesOutlineProgress, particles.isOutlineEnabled() ? 1.0F : 0.0F, dt, 16.0F);
        particlesScroll = clamp(particlesScroll, 0.0F, maxParticlesScroll());

        sideCard(context, x, y, w, h, alpha);
        drawCenteredText(context, "Particles", x + 31.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        int x1 = Math.round(sx(x + 1.0F));
        int y1 = Math.round(sy(y + 40.0F));
        int x2 = Math.round(sx(x + w - 1.0F));
        int y2 = Math.round(sy(y + h - 1.0F));
        context.enableScissor(x1, y1, x2, y2);

        float rowY = particlesContentY();
        drawText(context, "\u0422\u0435\u043a\u0441\u0442\u0443\u0440\u0430", x + 13.0F, rowY + 4.0F, 1.1F, color(TEXT_MAIN, 238, alpha));
        drawParticleSelect(context, particles.getPreviewTexture().label(), particles.getPreviewTexture().id(), x + 112.0F, rowY - 1.0F, 94.0F, particleTextureProgress, alpha);
        if (particleTextureProgress > 0.02F) {
            drawParticleTextureList(context, x + 112.0F, rowY + 28.0F, particleTextureProgress, alpha);
        }

        rowY += 36.0F + particleTextureProgress * 133.0F;
        drawText(context, "\u041f\u043e\u044f\u0432\u043b\u0435\u043d\u0438\u0435", x + 13.0F, rowY + 4.0F, 1.1F, color(TEXT_MAIN, 238, alpha));
        drawParticleModeSelect(context, primaryEnabledModeLabel(particles), x + 100.0F, rowY - 1.0F, 112.0F, particleMultiProgress, alpha);
        if (particleMultiProgress > 0.02F) {
            drawParticleMultiList(context, x + 100.0F, rowY + 28.0F, particleMultiProgress, alpha);
        }

        float outlineY = rowY + 38.0F + particleMultiProgress * particleMultiOpenHeight();
        drawBooleanToggleRow(context, "\u041e\u0431\u0432\u043e\u0434\u043a\u0430", x + 13.0F, outlineY, particlesOutlineProgress, alpha);

        float sliderY = rowY + 92.0F + particleMultiProgress * particleMultiOpenHeight();
        drawText(context, "\u041a\u043e\u043b-\u0432\u043e", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "particles_amount", x + 20.0F, sliderY, 170.0F, (particles.getAmount() - 1.0F) / 119.0F,
                new String[]{"1", "60", "120"}, alpha, dt);

        sliderY += 52.0F;
        drawText(context, "\u0416\u0438\u0437\u043d\u044c", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "particles_life", x + 20.0F, sliderY, 170.0F, (particles.getLifeSeconds() - 0.4F) / 5.6F,
                new String[]{"0.4", "3.2", "6"}, alpha, dt);

        sliderY += 52.0F;
        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "particles_size", x + 20.0F, sliderY, 170.0F, (particles.getSize() - 0.04F) / 0.51F,
                new String[]{"S", "M", "L"}, alpha, dt);

        float colorY = sliderY + 40.0F;
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, colorY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 13.0F, colorY + 28.0F, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 18.0F, colorY + 33.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | particles.getColorRgb(), alpha));
        outline(context, x + 13.0F, colorY + 28.0F, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (particlesColorOpen) {
            drawParticlesPalette(context, x + 15.0F, colorY + 63.0F, alpha);
        }

        context.disableScissor();
    }

    private void drawJumpCircles(DrawContext context, float alpha, float dt) {
        float x = jumpCirclesCardX;
        float y = jumpCirclesCardY;
        JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
        jumpCircleModeProgress = approach(jumpCircleModeProgress, jumpCircleModeOpen ? 1.0F : 0.0F, dt, 14.0F);
        jumpCircleTextureProgress = approach(jumpCircleTextureProgress,
                jumpCircleTextureOpen && jumpCircles.getMode() != JumpCircles.Mode.BLOCKS ? 1.0F : 0.0F, dt, 14.0F);
        jumpCircleParticleTextureProgress = approach(jumpCircleParticleTextureProgress, jumpCircleParticleTextureOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, jumpCirclesCardHeight(), alpha);
        drawCenteredText(context, "JumpCircles", x + 28.0F, y + 9.0F, 152.0F, 22.0F, 1.30F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float rowY = jumpCirclesContentY();
        drawText(context, "\u0420\u0435\u0436\u0438\u043c", x + 13.0F, rowY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        drawJumpCircleSelect(context, jumpCircles.getMode().label(), x + 112.0F, rowY - 1.0F, 100.0F, jumpCircleModeProgress, alpha);
        if (jumpCircleModeProgress > 0.02F) {
            drawJumpCircleModeList(context, x + 112.0F, rowY + 28.0F, jumpCircleModeProgress, alpha);
        }

        rowY += 36.0F + jumpCircleModeProgress * jumpCircleModeListHeight();
        if (jumpCircles.getMode() != JumpCircles.Mode.BLOCKS) {
            drawText(context, "\u0422\u0435\u043a\u0441\u0442\u0443\u0440\u0430", x + 13.0F, rowY + 4.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            drawJumpCircleSelect(context, jumpCircles.getTextureType().label(), x + 112.0F, rowY - 1.0F, 100.0F, jumpCircleTextureProgress, alpha);
            if (jumpCircleTextureProgress > 0.02F) {
                drawJumpCircleTextureList(context, x + 112.0F, rowY + 28.0F, jumpCircleTextureProgress, alpha);
            }
            rowY += 58.0F + jumpCircleTextureProgress * jumpCircleTextureListHeight();
        }

        drawBooleanToggleRow(context, "\u041f\u0430\u0440\u0442\u0438\u043a\u043b\u044b", x + 13.0F, rowY, jumpCircles.isParticlesEnabled() ? 1.0F : 0.0F, alpha);
        rowY += 42.0F;

        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440 \u043a\u0440\u0443\u0433\u0430", x + 13.0F, rowY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
        slider(context, "jump_circles_size", x + 20.0F, rowY, 170.0F, (jumpCircles.getSize() - 0.35F) / 2.65F,
                new String[]{"S", "M", "L"}, alpha, dt);
        rowY += 52.0F;

        if (jumpCircles.isParticlesEnabled()) {
            drawText(context, "\u0422\u0435\u043a\u0441\u0442\u0443\u0440\u0430", x + 13.0F, rowY + 4.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
            drawJumpCircleSelect(context, jumpCircles.getParticleTexture().label(), x + 112.0F, rowY - 1.0F, 100.0F, jumpCircleParticleTextureProgress, alpha);
            if (jumpCircleParticleTextureProgress > 0.02F) {
                drawJumpCircleParticleTextureList(context, x + 112.0F, rowY + 28.0F, jumpCircleParticleTextureProgress, alpha);
            }
            rowY += 58.0F + jumpCircleParticleTextureProgress * jumpCircleParticleTextureListHeight();
            drawText(context, "\u041a\u043e\u043b-\u0432\u043e", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
            slider(context, "jump_circles_amount", x + 20.0F, rowY, 170.0F, (jumpCircles.getAmount() - 1.0F) / 95.0F,
                    new String[]{"1", "48", "96"}, alpha, dt);
            rowY += 52.0F;
            drawText(context, "\u0416\u0438\u0437\u043d\u044c", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
            slider(context, "jump_circles_life", x + 20.0F, rowY, 170.0F, (jumpCircles.getLifeSeconds() - 0.25F) / 3.75F,
                    new String[]{"0.25", "2", "4"}, alpha, dt);
            rowY += 52.0F;
            drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440 \u0447\u0430\u0441\u0442\u0438\u0446", x + 13.0F, rowY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
            slider(context, "jump_circles_particle_size", x + 20.0F, rowY, 170.0F, (jumpCircles.getParticleSize() - 0.25F) / 2.25F,
                    new String[]{"S", "M", "L"}, alpha, dt);
            rowY += 52.0F;
            drawText(context, "\u0421\u0438\u043b\u0430 \u0440\u0430\u0437\u043b\u0435\u0442\u0430", x + 13.0F, rowY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
            slider(context, "jump_circles_spread", x + 20.0F, rowY, 170.0F, jumpCircles.getSpread(),
                    new String[]{"0", "50", "100"}, alpha, dt);
            rowY += 40.0F;
        }

        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, rowY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, rowY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, rowY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | jumpCircles.getColorRgb(), alpha));
        outline(context, x + 126.0F, rowY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (jumpCircleColorOpen) {
            drawJumpCirclesPalette(context, x + 15.0F, rowY + 43.0F, alpha);
        }
    }

    private void drawTrails(DrawContext context, float alpha, float dt) {
        float x = trailsCardX;
        float y = trailsCardY;
        Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();

        sideCard(context, x, y, 225.0F, trailsCardHeight(), alpha);
        drawCenteredText(context, "Trails", x + 31.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float sliderY = trailsMaxLengthY();
        drawText(context, "\u041c\u0430\u043a\u0441. \u0434\u043b\u0438\u043d\u0430", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "trails_max_length", x + 20.0F, sliderY, 170.0F,
                (trails.getMaxLength() - Trails.MIN_LENGTH) / (Trails.MAX_LENGTH - Trails.MIN_LENGTH),
                new String[]{"1", "3", "6"}, alpha, dt);

        float alphaY = trailsAlphaY();
        drawText(context, "\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", x + 13.0F, alphaY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
        slider(context, "trails_alpha", x + 20.0F, alphaY, 170.0F, trails.getAlpha() / Trails.MAX_ALPHA,
                new String[]{"0%", "50%", "100%"}, alpha, dt);

        float colorY = trailsColorY();
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, colorY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, colorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | trails.getColorRgb(), alpha));
        outline(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (trailsColorOpen) {
            drawTrailsPalette(context, x + 15.0F, trailsPaletteY(), alpha);
        }
    }

    private void drawRemovals(DrawContext context, float alpha) {
        float x = removalsCardX;
        float y = removalsCardY;
        float w = 225.0F;
        Removals removals = FluxVisualsClient.MODULE_MANAGER.getRemovals();

        sideCard(context, x, y, w, removalsCardHeight(), alpha);
        drawCenteredText(context, "Removals", x + 25.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float rowY = removalsContentY();
        drawBooleanToggleRow(context, "\u041e\u0433\u043e\u043d\u044c \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435", x + 13.0F, rowY, removals.isFireOverlay() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u0421\u0432\u0435\u0447\u0435\u043d\u0438\u0435 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439", x + 13.0F, rowY, removals.isEntityGlowing() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u041f\u043b\u043e\u0445\u0430\u044f \u043f\u043e\u0433\u043e\u0434\u0430", x + 13.0F, rowY, removals.isBadWeather() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u0422\u0440\u044f\u0441\u043a\u0430 \u044d\u043a\u0440\u0430\u043d\u0430", x + 13.0F, rowY, removals.isHurtCamera() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u0420\u0430\u0441\u0442\u044f\u0436\u0435\u043d\u0438\u0435 FOV", x + 13.0F, rowY, removals.isSprintFov() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u041f\u0443\u0437\u044b\u0440\u044c\u043a\u0438", x + 13.0F, rowY, removals.isSoulSandBubbles() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "NoFluid: \u0432\u043e\u0434\u0430", x + 13.0F, rowY, removals.isNoFluidEnabled(Removals.FluidType.WATER) ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "NoFluid: \u043b\u0430\u0432\u0430", x + 13.0F, rowY, removals.isNoFluidEnabled(Removals.FluidType.LAVA) ? 1.0F : 0.0F, alpha);
    }

    private void drawHitColor(DrawContext context, float alpha) {
        float x = hitColorCardX;
        float y = hitColorCardY;
        float h = hitColorCardHeight();
        HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();

        sideCard(context, x, y, 225.0F, h, alpha);
        drawCenteredText(context, "Hit Color", x + 25.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float contentY = y + 48.0F;
        drawBooleanToggleRow(context, "\u041a\u0440\u0430\u0441\u0438\u0442\u044c \u0431\u0440\u043e\u043d\u044e", x + 13.0F, contentY, hitColor.isArmorTintEnabled() ? 1.0F : 0.0F, alpha);
        float alphaY = hitColorAlphaSliderY();
        drawText(context, "\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", x + 13.0F, alphaY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
        slider(context, "hit_color_alpha", x + 20.0F, alphaY, 170.0F, hitColor.getAlpha(),
                new String[]{"0%", "50%", "100%"}, alpha, 1.0F / 60.0F);

        float colorY = hitColorColorY();
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, colorY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, colorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | hitColor.getColorRgb(), alpha));
        outline(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (hitColorPaletteOpen) {
            drawHitColorPalette(context, x + 15.0F, hitColorPaletteY(), alpha);
        }
    }

    private void drawHitboxCustomizer(DrawContext context, float alpha, float dt) {
        float x = hitboxCustomizerCardX;
        float y = hitboxCustomizerCardY;
        float h = hitboxCustomizerCardHeight();
        HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
        hitboxTargetsProgress = approach(hitboxTargetsProgress, hitboxTargetsOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, h, alpha);
        drawCenteredText(context, "Hitbox Customizer", x + 18.0F, y + 9.0F, 150.0F, 22.0F, 1.20F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);

        float targetsY = hitboxTargetsY();
        drawText(context, "\u0426\u0435\u043b\u0438", x + 13.0F, targetsY + 4.0F, 1.06F, color(TEXT_MAIN, 238, alpha));
        drawBlockOverlaySelect(context, hitboxCustomizer.targetSummary(), x + 104.0F, targetsY, 108.0F, hitboxTargetsProgress, alpha);
        if (hitboxTargetsProgress > 0.02F) {
            drawHitboxTargetList(context, x + 104.0F, targetsY + 29.0F, hitboxTargetsProgress, alpha);
        }

        float contentY = hitboxCustomizerContentY();
        drawText(context, "\u041b\u0438\u043d\u0438\u044f", x + 13.0F, contentY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, contentY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, contentY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | hitboxCustomizer.getOutlineColorRgb(), alpha));
        outline(context, x + 126.0F, contentY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);

        float sliderY = contentY + 54.0F;
        drawText(context, "\u0422\u043e\u043b\u0449\u0438\u043d\u0430", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "hitbox_line_thickness", x + 20.0F, sliderY, 170.0F,
                (hitboxCustomizer.getLineThickness() - 1.0F) / 4.0F, new String[]{"1", "3", "5"}, alpha, dt);

        float toggleY = hitboxAlwaysShowY();
        drawText(context, "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u0432\u0441\u0435\u0433\u0434\u0430", x + 13.0F, toggleY + 1.0F, 0.92F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, HITBOX_ALWAYS_SHOW_BIND_KEY, x + 148.0F, toggleY + 1.0F, 30.0F, 22.0F, alpha);
        rounded(context, x + 188.0F, toggleY, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, x + 188.0F, toggleY, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, hitboxCustomizer.isAlwaysShow() ? 1.0F : 0.0F, 84, alpha), 1.0F);
        drawCenteredText(context, "x", x + 188.0F, toggleY - 0.3F - (hitboxCustomizer.isAlwaysShow() ? 1.4F : 0.0F), 24.0F, 24.0F,
                1.0F - (hitboxCustomizer.isAlwaysShow() ? 0.1F : 0.0F), argb(Math.round(255.0F * (hitboxCustomizer.isAlwaysShow() ? 0.0F : 1.0F)), 228, 74, 74, alpha), FontRole.SF_PRO);
        drawCenteredText(context, "\u2713", x + 188.0F, toggleY + 0.8F - (hitboxCustomizer.isAlwaysShow() ? 0.0F : 1.4F), 24.0F, 24.0F,
                1.03F, argb(Math.round(255.0F * (hitboxCustomizer.isAlwaysShow() ? 1.0F : 0.0F)), 84, 214, 108, alpha), FontRole.SF_PRO);

        toggleY = hitboxCornersOnlyY();
        drawBooleanToggleRow(context, "\u0422\u043e\u043b\u044c\u043a\u043e \u0443\u0433\u043e\u043b\u043a\u0438", x + 13.0F, toggleY, hitboxCustomizer.isCornersOnly() ? 1.0F : 0.0F, alpha);

        toggleY = hitboxFillToggleY();
        drawBooleanToggleRow(context, "\u0417\u0430\u043b\u0438\u0432\u043a\u0430", x + 13.0F, toggleY, hitboxCustomizer.isFillEnabled() ? 1.0F : 0.0F, alpha);
        if (hitboxCustomizer.isFillEnabled()) {
            float fillColorY = hitboxFillColorY();
            drawText(context, "\u0426\u0432\u0435\u0442 \u0437\u0430\u043b\u0438\u0432\u043a\u0438", x + 13.0F, fillColorY + 4.0F, 1.01F, color(TEXT_MAIN, 238, alpha));
            rounded(context, x + 126.0F, fillColorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
            rounded(context, x + 131.0F, fillColorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(hitboxCustomizer.getFillArgbColor(), alpha));
            outline(context, x + 126.0F, fillColorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);

            float fillAlphaY = hitboxCustomizerFillAlphaY();
            drawText(context, "\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", x + 13.0F, fillAlphaY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
            slider(context, "hitbox_fill_alpha", x + 20.0F, fillAlphaY, 170.0F,
                    hitboxCustomizer.getFillAlpha(), new String[]{"0%", "50%", "100%"}, alpha, dt);
        }
        toggleY = hitboxSelfY();
        drawBooleanToggleRow(context, "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u0441\u0435\u0431\u044f", x + 13.0F, toggleY, hitboxCustomizer.isIncludeSelf() ? 1.0F : 0.0F, alpha);

        if (hitboxOutlinePaletteOpen) {
            drawHitboxOutlinePalette(context, x + 15.0F, contentY + 43.0F, alpha);
        }
        if (hitboxCustomizer.isFillEnabled() && hitboxFillPaletteOpen) {
            drawHitboxFillPalette(context, x + 15.0F, hitboxFillPaletteY(), alpha);
        }
    }

    private void drawBlockOverlay(DrawContext context, float alpha, float dt) {
        float x = blockOverlayCardX;
        float y = blockOverlayCardY;
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        blockOverlayFillModeProgress = approach(blockOverlayFillModeProgress, blockOverlayFillModeOpen ? 1.0F : 0.0F, dt, 14.0F);
        blockOverlayShaderProgress = approach(blockOverlayShaderProgress, blockOverlayShaderOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, blockOverlayCardHeight(), alpha);
        drawCenteredText(context, "BlockOverlay", x + 25.0F, y + 9.0F, 150.0F, 22.0F, 1.22F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);

        float contentY = blockOverlayContentY();
        drawText(context, "\u041b\u0438\u043d\u0438\u044f", x + 13.0F, contentY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, contentY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, contentY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | blockOverlay.getOutlineColorRgb(), alpha));
        outline(context, x + 126.0F, contentY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);

        float sliderY = blockOverlayThicknessY();
        drawText(context, "\u0422\u043e\u043b\u0449\u0438\u043d\u0430", x + 13.0F, sliderY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "block_overlay_line_thickness", x + 20.0F, sliderY, 170.0F,
                (blockOverlay.getLineThickness() - 1.0F) / 4.0F, new String[]{"1", "3", "5"}, alpha, dt);

        float toggleY = blockOverlayFillToggleY();
        drawBooleanToggleRow(context, "\u0417\u0430\u043b\u0438\u0432\u043a\u0430", x + 13.0F, toggleY, blockOverlay.isFillEnabled() ? 1.0F : 0.0F, alpha);
        toggleY = blockOverlayThroughWallsY();
        drawBooleanToggleRow(context, "\u0427\u0435\u0440\u0435\u0437 \u0441\u0442\u0435\u043d\u044b", x + 13.0F, toggleY, blockOverlay.isThroughWalls() ? 1.0F : 0.0F, alpha);
        toggleY = blockOverlaySmoothSwitchY();
        drawBooleanToggleRow(context, "\u041f\u043b\u0430\u0432\u043d\u044b\u0439 \u043f\u0435\u0440\u0435\u0445\u043e\u0434", x + 13.0F, toggleY, blockOverlay.isSmoothSwitch() ? 1.0F : 0.0F, alpha);

        if (blockOverlay.isFillEnabled()) {
            float modeY = blockOverlayFillModeY();
            drawText(context, "\u0422\u0438\u043f", x + 13.0F, modeY + 4.0F, 1.06F, color(TEXT_MAIN, 238, alpha));
            drawBlockOverlaySelect(context, blockOverlay.getFillMode().label(), x + 112.0F, modeY, 100.0F, blockOverlayFillModeProgress, alpha);
            if (blockOverlayFillModeProgress > 0.02F) {
                drawBlockOverlayFillModeList(context, x + 112.0F, modeY + 29.0F, blockOverlayFillModeProgress, alpha);
            }

            if (blockOverlay.getFillMode() == BlockOverlay.FillMode.SHADER) {
                float shaderY = blockOverlayShaderY();
                drawText(context, "\u0428\u0435\u0439\u0434\u0435\u0440", x + 13.0F, shaderY + 4.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
                drawBlockOverlaySelect(context, blockOverlay.getShaderType().label(), x + 112.0F, shaderY, 100.0F, blockOverlayShaderProgress, alpha);
                if (blockOverlayShaderProgress > 0.02F) {
                    drawBlockOverlayShaderList(context, x + 112.0F, shaderY + 29.0F, blockOverlayShaderProgress, alpha);
                }
            }

            if (blockOverlayShowsAnimationSpeed()) {
                float speedY = blockOverlayAnimationSpeedY();
                drawText(context, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0438", x + 13.0F, speedY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
                slider(context, "block_overlay_animation_speed", x + 20.0F, speedY, 170.0F,
                        (blockOverlay.getAnimationSpeed() - 0.1F) / 3.9F, new String[]{"0.1", "2", "4"}, alpha, dt);
            }

            float colorY = blockOverlayFillColorY();
            drawText(context, "\u0426\u0432\u0435\u0442 \u0437\u0430\u043b\u0438\u0432\u043a\u0438", x + 13.0F, colorY + 4.0F, 1.01F, color(TEXT_MAIN, 238, alpha));
            rounded(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
            rounded(context, x + 131.0F, colorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(blockOverlay.getFillArgbColor(), alpha));
            outline(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);

            float alphaY = blockOverlayFillAlphaY();
            drawText(context, "\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", x + 13.0F, alphaY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
            slider(context, "block_overlay_fill_alpha", x + 20.0F, alphaY, 170.0F,
                    blockOverlay.getFillAlpha(), new String[]{"0%", "50%", "100%"}, alpha, dt);
        }

        if (blockOverlayOutlinePaletteOpen) {
            drawBlockOverlayOutlinePalette(context, x + 15.0F, blockOverlayOutlinePaletteY(), alpha);
        }
        if (blockOverlay.isFillEnabled() && blockOverlayFillPaletteOpen) {
            drawBlockOverlayFillPalette(context, x + 15.0F, blockOverlayFillPaletteY(), alpha);
        }
    }

    private void drawItemRadius(DrawContext context, float alpha) {
        float x = itemRadiusCardX;
        float y = itemRadiusCardY;
        ItemRadius itemRadius = FluxVisualsClient.MODULE_MANAGER.getItemRadius();

        sideCard(context, x, y, 225.0F, itemRadiusCardHeight(), alpha);
        drawCenteredText(context, "ItemRadius", x + 30.0F, y + 9.0F, 145.0F, 22.0F, 1.30F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);

        float rowY = itemRadiusContentY();
        for (ItemRadius.RadiusItem item : ItemRadius.RadiusItem.values()) {
            drawBooleanToggleRow(context, item.label(), x + 13.0F, rowY,
                    itemRadius.isItemEnabledPublic(item) ? 1.0F : 0.0F, alpha);
            rowY += 36.0F;
        }
        drawBooleanToggleRow(context, "\u0414\u0440\u0430\u043a\u043e\u043d\u0438\u044f \u0422\u0440\u0430\u043f\u043a\u0430", x + 13.0F, rowY,
                itemRadius.isDraconicTrap() ? 1.0F : 0.0F, alpha);
    }

    private void drawAnimations(DrawContext context, float alpha) {
        float x = animationsCardX;
        float y = animationsCardY;
        Animations animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();

        sideCard(context, x, y, 225.0F, animationsCardHeight(), alpha);
        drawCenteredText(context, "Animations", x + 30.0F, y + 9.0F, 145.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);

        float rowY = animationsContentY();
        drawBooleanToggleRow(context, "Tab", x + 13.0F, rowY, animations.isTabEnabled() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "F5", x + 13.0F, rowY, animations.isThirdPersonEnabled() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u0421\u043b\u043e\u0442\u044b", x + 13.0F, rowY, animations.isHotbarEnabled() ? 1.0F : 0.0F, alpha);
        rowY += 36.0F;
        drawBooleanToggleRow(context, "\u0418\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c", x + 13.0F, rowY, animations.isInventoryEnabled() ? 1.0F : 0.0F, alpha);
    }

    private void drawTabCustomizer(DrawContext context, float alpha, float dt) {
        float x = tabCustomizerCardX;
        float y = tabCustomizerCardY;
        TabCustomizer tabCustomizer = FluxVisualsClient.MODULE_MANAGER.getTabCustomizer();

        sideCard(context, x, y, 225.0F, tabCustomizerCardHeight(), alpha);
        drawCenteredText(context, "TabCustomizer", x + 31.0F, y + 9.0F, 145.0F, 22.0F, 1.12F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);

        float rowY = tabCustomizerContentY();
        drawText(context, "\u0421\u0442\u043e\u043b\u0431\u0438\u043a\u0438", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "tab_customizer_columns", x + 20.0F, rowY, 170.0F,
                (tabCustomizer.getColumns() - 1.0F) / 7.0F, new String[]{"1", "4", "8"}, alpha, dt);

        rowY += 52.0F;
        drawText(context, "\u0418\u0433\u0440\u043e\u043a\u043e\u0432 \u0432 \u0441\u0442\u043e\u043b\u0431\u0438\u043a\u0435", x + 13.0F, rowY - 27.0F, 0.96F, color(TEXT_MAIN, 238, alpha));
        slider(context, "tab_customizer_players", x + 20.0F, rowY, 170.0F,
                (tabCustomizer.getPlayersPerColumn() - 1.0F) / 29.0F, new String[]{"1", "15", "30"}, alpha, dt);

        rowY += 52.0F;
        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440", x + 13.0F, rowY - 27.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        slider(context, "tab_customizer_scale", x + 20.0F, rowY, 170.0F,
                (tabCustomizer.getScale() - 0.75F) / 0.5F, new String[]{"75%", "100%", "125%"}, alpha, dt);
    }

    private void drawFreeLook(DrawContext context, float alpha, float dt) {
        float x = freeLookCardX;
        float y = freeLookCardY;
        FreeLook freeLook = FluxVisualsClient.MODULE_MANAGER.getFreeLook();
        freeLookModeProgress = approach(freeLookModeProgress, freeLookModeOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, freeLookCardHeight(), alpha);
        drawCenteredText(context, "FreeLook", x + 28.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float contentY = freeLookContentY();
        drawText(context, "\u0411\u0438\u043d\u0434 \u043f\u0435\u0440\u0441\u043f\u0435\u043a\u0442\u0438\u0432\u044b", x + 13.0F, contentY + 3.0F, 0.94F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, FreeLook.PERSPECTIVE_BIND_KEY, x + 164.0F, contentY - 1.0F, 48.0F, 24.0F, alpha);

        float modeY = contentY + 40.0F;
        drawText(context, "\u0420\u0435\u0436\u0438\u043c", x + 13.0F, modeY + 3.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, modeY - 1.0F, 86.0F, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x + 126.0F, modeY - 1.0F, 86.0F, 24.0F, 9.0F,
                freeLookModeProgress > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, freeLook.getActivationMode().label(), x + 130.0F, modeY, 58.0F, 22.0F, 0.96F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThinArrow(context, freeLookModeOpen ? "v" : ">", x + 193.0F, modeY + 0.4F, alpha);

        if (freeLookModeProgress > 0.02F) {
            float listY = freeLookModeListY();
            float listH = freeLookModeListHeight() * freeLookModeProgress;
            rounded(context, x + 126.0F, listY, 86.0F, listH, 9.0F, color(MODULE_HOVER, 245, alpha * freeLookModeProgress));
            outline(context, x + 126.0F, listY, 86.0F, listH, 9.0F, color(OUTLINE, 96, alpha * freeLookModeProgress), 1.0F);
            int index = 0;
            for (FreeLook.ActivationMode mode : FreeLook.ActivationMode.values()) {
                float itemY = listY + 3.0F + index * 22.0F;
                if (itemY + 22.0F > listY + listH) {
                    break;
                }
                if (mode == freeLook.getActivationMode()) {
                    rounded(context, x + 130.0F, itemY + 2.0F, 78.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * freeLookModeProgress));
                }
                drawCenteredText(context, mode.label(), x + 131.0F, itemY, 76.0F, 22.0F, 0.88F,
                        color(TEXT_MAIN, 242, alpha * freeLookModeProgress), FontRole.OXANIUM);
                index++;
            }
        }

        float distanceY = freeLookDistanceY();
        drawText(context, "\u0414\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c", x + 13.0F, distanceY - 27.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        slider(context, "freelook_distance", x + 20.0F, distanceY, 170.0F,
                (freeLook.getCameraDistance() - 2.0F) / 10.0F, new String[]{"2", "7", "12"}, alpha, dt);

        drawText(context, freeLook.isActive() ? "\u041a\u0430\u043c\u0435\u0440\u0430 \u0430\u043a\u0442\u0438\u0432\u043d\u0430" : "\u041d\u0430\u0436\u043c\u0438 \u0431\u0438\u043d\u0434 \u0432 \u0438\u0433\u0440\u0435",
                x + 13.0F, distanceY + 34.0F, 0.86F,
                freeLook.isActive() ? color(PURPLE_GLOW, 245, alpha) : color(TEXT_SECONDARY, 220, alpha));
    }

    private void drawZoom(DrawContext context, float alpha, float dt) {
        float x = zoomCardX;
        float y = zoomCardY;
        Zoom zoom = FluxVisualsClient.MODULE_MANAGER.getZoom();

        sideCard(context, x, y, 225.0F, zoomCardHeight(), alpha);
        drawCenteredText(context, "Zoom", x + 28.0F, y + 9.0F, 145.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float contentY = zoomContentY();
        drawText(context, "\u0411\u0438\u043d\u0434", x + 13.0F, contentY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, Zoom.BIND_KEY, x + 164.0F, contentY - 1.0F, 48.0F, 24.0F, alpha);

        float smoothY = zoomSmoothnessY();
        drawText(context, "\u041f\u043b\u0430\u0432\u043d\u043e\u0441\u0442\u044c", x + 13.0F, smoothY - 27.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        slider(context, "zoom_smoothness", x + 20.0F, smoothY, 170.0F, zoom.getSmoothness(),
                new String[]{"0", "50", "100"}, alpha, dt);

        float wheelY = zoomWheelY();
        drawBooleanToggleRow(context, "\u041a\u043e\u043b\u0435\u0441\u0438\u043a\u043e", x + 13.0F, wheelY, zoom.isWheelZoomEnabled() ? 1.0F : 0.0F, alpha);

        drawText(context, zoom.isActive() ? "\u041f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435 \u0430\u043a\u0442\u0438\u0432\u043d\u043e" : "\u041d\u0430\u0436\u043c\u0438 \u0431\u0438\u043d\u0434 \u0432 \u0438\u0433\u0440\u0435",
                x + 13.0F, wheelY + 38.0F, 0.86F,
                zoom.isActive() ? color(PURPLE_GLOW, 245, alpha) : color(TEXT_SECONDARY, 220, alpha));
    }

    private void drawAutoSwap(DrawContext context, float alpha, float dt) {
        float x = autoSwapCardX;
        float y = autoSwapCardY;
        AutoSwap autoSwap = FluxVisualsClient.MODULE_MANAGER.getAutoSwap();
        autoSwapFirstProgress = approach(autoSwapFirstProgress, autoSwapFirstOpen ? 1.0F : 0.0F, dt, 14.0F);
        autoSwapSecondProgress = approach(autoSwapSecondProgress, autoSwapSecondOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 245.0F, autoSwapCardHeight(), alpha);
        drawCenteredText(context, "ItemSwap", x + 28.0F, y + 9.0F, 165.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 210.0F, y + 12.0F, alpha);

        float firstY = autoSwapContentY();
        drawText(context, "\u041f\u0435\u0440\u0432\u044b\u0439", x + 13.0F, firstY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawAutoSwapSelect(context, autoSwap.getFirstItem(), x + 104.0F, firstY - 1.0F, autoSwapFirstOpen, alpha);
        if (autoSwapFirstProgress > 0.02F) {
            drawAutoSwapList(context, x + 104.0F, firstY + 28.0F, autoSwapFirstProgress, alpha, autoSwap.getFirstItem());
        }

        float secondY = autoSwapSecondY();
        drawText(context, "\u0412\u0442\u043e\u0440\u043e\u0439", x + 13.0F, secondY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawAutoSwapSelect(context, autoSwap.getSecondItem(), x + 104.0F, secondY - 1.0F, autoSwapSecondOpen, alpha);
        if (autoSwapSecondProgress > 0.02F) {
            drawAutoSwapList(context, x + 104.0F, secondY + 28.0F, autoSwapSecondProgress, alpha, autoSwap.getSecondItem());
        }

        float bindY = autoSwapBindY();
        drawText(context, "\u0411\u0438\u043d\u0434 \u0441\u0432\u0430\u043f\u0430", x + 13.0F, bindY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, AutoSwap.BIND_KEY, x + 178.0F, bindY - 1.0F, 54.0F, 24.0F, alpha);
        drawText(context, "\u0420\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u043f\u043e \u0431\u0438\u043d\u0434\u0443",
                x + 13.0F, bindY + 40.0F, 0.86F, color(TEXT_SECONDARY, 220, alpha));
    }

    private void drawElytraSwap(DrawContext context, float alpha) {
        float x = elytraSwapCardX;
        float y = elytraSwapCardY;

        sideCard(context, x, y, 245.0F, elytraSwapCardHeight(), alpha);
        drawCenteredText(context, "ElytraSwap", x + 28.0F, y + 9.0F, 165.0F, 22.0F, 1.45F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 210.0F, y + 12.0F, alpha);

        float swapY = elytraSwapContentY();
        drawText(context, "\u0411\u0438\u043d\u0434 \u0441\u0432\u0430\u043f\u0430", x + 13.0F, swapY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, ElytraSwap.BIND_KEY, x + 178.0F, swapY - 1.0F, 54.0F, 24.0F, alpha);

        float fireworkY = elytraSwapFireworkY();
        drawText(context, "\u0411\u0438\u043d\u0434 \u0444\u0435\u0439\u0435\u0440\u0432\u0435\u0440\u043a\u0430", x + 13.0F, fireworkY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        drawBindButton(context, ElytraSwap.FIREWORK_BIND_KEY, x + 178.0F, fireworkY - 1.0F, 54.0F, 24.0F, alpha);

        drawText(context, "\u041d\u0435\u0437\u0435\u0440\u0438\u0442 \u2192 \u0430\u043b\u043c\u0430\u0437 \u2192 \u0436\u0435\u043b\u0435\u0437\u043e",
                x + 13.0F, fireworkY + 38.0F, 0.86F, color(TEXT_SECONDARY, 220, alpha));
        drawText(context, "\u0421\u0432\u0430\u043f \u0438 \u0444\u0435\u0439\u0435\u0440\u0432\u0435\u0440\u043a \u043f\u043e \u0434\u0432\u0443\u043c \u043a\u043d\u043e\u043f\u043a\u0430\u043c",
                x + 13.0F, fireworkY + 54.0F, 0.86F, color(TEXT_DIM, 220, alpha));
    }

    private void drawAutoSwapSelect(DrawContext context, AutoSwap.SwapItem item, float x, float y, boolean open, float alpha) {
        rounded(context, x, y, 128.0F, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x, y, 128.0F, 24.0F, 9.0F, open ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, item.fullLabel(), x + 5.0F, y + 1.0F, 101.0F, 22.0F,
                autoSwapItemScale(item.fullLabel()), color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThinArrow(context, open ? "v" : ">", x + 113.0F, y + 1.4F, alpha);
    }

    private void drawAutoSwapList(DrawContext context, float x, float y, float progress, float alpha, AutoSwap.SwapItem selected) {
        float listH = autoSwapItemListHeight() * progress;
        rounded(context, x, y, 128.0F, listH, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 128.0F, listH, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        AutoSwap.SwapItem[] values = AutoSwap.SwapItem.values();
        for (int i = 0; i < values.length; i++) {
            AutoSwap.SwapItem item = values[i];
            float itemY = y + 3.0F + i * 22.0F;
            if (itemY + 22.0F > y + listH) {
                break;
            }
            if (item == selected) {
                rounded(context, x + 4.0F, itemY + 2.0F, 120.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, item.fullLabel(), x + 5.0F, itemY, 118.0F, 22.0F,
                    autoSwapItemScale(item.fullLabel()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
        }
    }

    private void drawItemResorter(DrawContext context, float alpha, float dt) {
        float x = itemResorterCardX;
        float y = itemResorterCardY;
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();

        sideCard(context, x, y, ITEM_RESORTER_CARD_W, itemResorterCardHeight(), alpha);
        drawCenteredText(context, "ItemResorter", x + 28.0F, y + 9.0F, 192.0F, 22.0F, 1.36F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + ITEM_RESORTER_CARD_W - 35.0F, y + 12.0F, alpha);

        float tabsY = itemResorterTabsY();
        float tabW = (ITEM_RESORTER_PANEL_W - 8.0F) * 0.5F;
        drawItemResorterTabButton(context, ItemResorterTab.ENCHANTS, x + 13.0F, tabsY, tabW, 24.0F, alpha);
        drawItemResorterTabButton(context, ItemResorterTab.BUFFS, x + 13.0F + tabW + 8.0F, tabsY, tabW, 24.0F, alpha);

        float firstInputY = itemResorterFirstInputY();
        if (itemResorterTab == ItemResorterTab.ENCHANTS) {
            drawItemResorterInputRow(context, ItemResorterInput.ENCHANT_NEEDED, x + 13.0F, firstInputY, alpha);
            drawItemResorterInputRow(context, ItemResorterInput.ENCHANT_IGNORED, x + 13.0F, itemResorterSecondInputY(), alpha);
            drawItemResorterColorRow(context, x + 13.0F, itemResorterColorRowY(), itemResorter.enchantColorRgb(), itemResorterEnchantColorOpen, alpha);
            drawItemResorterPriceRow(context, x + 13.0F, itemResorterPriceRowY(), alpha, itemResorter);
            drawItemResorterDurabilityRow(context, x + 13.0F, itemResorterDurabilityRowY(), alpha, dt, itemResorter);
            drawItemResorterEntrySection(context, "Нужные", itemResorter.getEnchantNeeded(), ItemResorterInput.ENCHANT_NEEDED,
                    x + 13.0F, itemResorterNeededSectionY(), ITEM_RESORTER_PANEL_W, alpha);
            drawItemResorterEntrySection(context, "Игнор", itemResorter.getEnchantIgnored(), ItemResorterInput.ENCHANT_IGNORED,
                    x + 13.0F, itemResorterIgnoredSectionY(), ITEM_RESORTER_PANEL_W, alpha);
            if (itemResorterEnchantColorOpen) {
                drawColorPalette(context, x + 15.0F, itemResorterEnchantPaletteY(), alpha,
                        itemResorter.getEnchantHue(), itemResorter.getEnchantSaturation(), itemResorter.getEnchantValue());
            }
        } else {
            drawItemResorterInputRow(context, ItemResorterInput.BUFF_NEEDED, x + 13.0F, firstInputY, alpha);
            drawItemResorterInputRow(context, ItemResorterInput.BUFF_IGNORED, x + 13.0F, itemResorterSecondInputY(), alpha);
            drawItemResorterExplosiveRow(context, x + 13.0F, itemResorterExplosiveRowY(), alpha, itemResorter.isBuffExplosiveOnly());
            drawItemResorterColorRow(context, x + 13.0F, itemResorterColorRowY(), itemResorter.buffColorRgb(), itemResorterBuffColorOpen, alpha);
            drawItemResorterPriceRow(context, x + 13.0F, itemResorterPriceRowY(), alpha, itemResorter);
            drawItemResorterDurabilityRow(context, x + 13.0F, itemResorterDurabilityRowY(), alpha, dt, itemResorter);
            drawItemResorterEntrySection(context, "Нужные", itemResorter.getBuffNeeded(), ItemResorterInput.BUFF_NEEDED,
                    x + 13.0F, itemResorterNeededSectionY(), ITEM_RESORTER_PANEL_W, alpha);
            drawItemResorterEntrySection(context, "Игнор", itemResorter.getBuffIgnored(), ItemResorterInput.BUFF_IGNORED,
                    x + 13.0F, itemResorterIgnoredSectionY(), ITEM_RESORTER_PANEL_W, alpha);
            if (itemResorterBuffColorOpen) {
                drawColorPalette(context, x + 15.0F, itemResorterBuffPaletteY(), alpha,
                        itemResorter.getBuffHue(), itemResorter.getBuffSaturation(), itemResorter.getBuffValue());
            }
        }
    }

    private void drawItemResorterTabButton(DrawContext context, ItemResorterTab tab, float x, float y, float w, float h, float alpha) {
        boolean active = itemResorterTab == tab;
        rounded(context, x, y, w, h, 9.0F, active ? color(VIOLET_DEEP, 228, alpha) : color(TOGGLE_OFF, 214, alpha));
        outline(context, x, y, w, h, 9.0F, active ? color(OUTLINE_GLOW, 90, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, tab.label, x, y - 0.3F, w, h, 0.95F, active ? color(TEXT_MAIN, 248, alpha) : color(TEXT_SECONDARY, 220, alpha), FontRole.SF_PRO);
    }

    private void drawItemResorterInputRow(DrawContext context, ItemResorterInput input, float x, float y, float alpha) {
        float boxX = x + 70.0F;
        float buttonX = boxX + ITEM_RESORTER_INPUT_W + 7.0F;
        boolean focused = itemResorterFocusedInput == input;

        drawText(context, input.rowLabel(), x, y + 4.0F, 0.98F, color(TEXT_MAIN, 232, alpha));
        rounded(context, boxX, y, ITEM_RESORTER_INPUT_W, 24.0F, 8.0F, color(TOGGLE_OFF, 228, alpha));
        outline(context, boxX, y, ITEM_RESORTER_INPUT_W, 24.0F, 8.0F,
                focused ? color(OUTLINE_GLOW, 102, alpha) : color(OUTLINE, 82, alpha), 1.0F);
        String draft = draftFor(input).toString().trim();
        if (draft.isEmpty()) {
            drawText(context, "Р”РѕР±Р°РІРёС‚СЊ", boxX + 8.0F, y + 5.0F, 0.82F, color(TEXT_DIM, 210, alpha));
        } else {
            drawScrollingValue(context, draft, boxX + 8.0F, y + 4.0F, ITEM_RESORTER_INPUT_W - 16.0F, 16.0F, 0.82F,
                    color(TEXT_MAIN, 244, alpha), FontRole.INTER, "input_" + input.name());
        }

        rounded(context, buttonX, y, 22.0F, 24.0F, 8.0F, color(VIOLET_DEEP, 228, alpha));
        outline(context, buttonX, y, 22.0F, 24.0F, 8.0F, color(OUTLINE_GLOW, 84, alpha), 1.0F);
        drawCenteredText(context, "+", buttonX, y - 0.2F, 22.0F, 24.0F, 1.02F, color(TEXT_MAIN, 248, alpha), FontRole.SF_PRO);
    }

    private void drawItemResorterExplosiveRow(DrawContext context, float x, float y, float alpha, boolean enabled) {
        drawText(context, "Р’Р·СЂС‹РІРЅРѕРµ", x, y + 4.0F, 0.98F, color(TEXT_MAIN, 232, alpha));
        float boxX = x + 222.0F;
        rounded(context, boxX, y, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, boxX, y, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, enabled ? 1.0F : 0.0F, 84, alpha), 1.0F);
        drawCenteredText(context, enabled ? "\u2713" : "x", boxX, y - (enabled ? 0.1F : 0.4F), 24.0F, 24.0F, 0.92F,
                enabled ? argb(255, 122, 232, 149, alpha) : argb(255, 236, 103, 103, alpha), FontRole.SF_PRO);
    }

    private void drawItemResorterColorRow(DrawContext context, float x, float y, int rgb, boolean open, float alpha) {
        drawText(context, "Р¦РІРµС‚", x, y + 4.0F, 0.98F, color(TEXT_MAIN, 232, alpha));
        float boxX = x + 165.0F;
        rounded(context, boxX, y, 81.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        outline(context, boxX, y, 81.0F, 28.0F, 8.0F, open ? color(OUTLINE_GLOW, 100, alpha) : color(OUTLINE, 82, alpha), 1.0F);
        rounded(context, boxX + 5.0F, y + 5.0F, 41.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | rgb, alpha));
        drawCenteredText(context, "HSV", boxX + 46.0F, y, 30.0F, 28.0F, 0.76F, color(TEXT_MAIN, 236, alpha), FontRole.OXANIUM);
    }

    private void drawItemResorterPriceRow(DrawContext context, float x, float y, float alpha, ItemResorter itemResorter) {
        drawText(context, "Price <=", x, y + 4.0F, 0.98F, color(TEXT_MAIN, 232, alpha));
        float boxX = x + 70.0F;
        rounded(context, boxX, y, 150.0F, 24.0F, 8.0F, color(TOGGLE_OFF, 228, alpha));
        outline(context, boxX, y, 150.0F, 24.0F, 8.0F,
                itemResorterPriceFocused ? color(OUTLINE_GLOW, 102, alpha) : color(OUTLINE, 82, alpha), 1.0F);
        String value = itemResorterPriceDraft.isEmpty()
                ? formatPrice(itemResorter.getMaxPrice())
                : formatPrice(itemResorterPriceDraft.toString());
        drawScrollingValue(context, value, boxX + 8.0F, y + 4.0F, 118.0F, 16.0F, 0.82F,
                color(TEXT_MAIN, 244, alpha), FontRole.INTER, "itemresorter_price");
        float toggleX = x + 226.0F;
        rounded(context, toggleX, y, 20.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, toggleX, y, 20.0F, 24.0F, 7.0F,
                mix(OUTLINE, OUTLINE_GLOW, itemResorter.isPriceFilterEnabled() ? 1.0F : 0.0F, 84, alpha), 1.0F);
        drawCenteredText(context, itemResorter.isPriceFilterEnabled() ? "\u2713" : "x", toggleX, y - 0.2F, 20.0F, 24.0F, 0.86F,
                itemResorter.isPriceFilterEnabled() ? argb(255, 122, 232, 149, alpha) : argb(255, 236, 103, 103, alpha), FontRole.SF_PRO);
    }

    private void drawItemResorterDurabilityRow(DrawContext context, float x, float y, float alpha, float dt, ItemResorter itemResorter) {
        drawText(context, "Durability >=", x, y + 4.0F, 0.92F, color(TEXT_MAIN, 232, alpha));
        float toggleX = x + 226.0F;
        rounded(context, toggleX, y, 20.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, toggleX, y, 20.0F, 24.0F, 7.0F,
                mix(OUTLINE, OUTLINE_GLOW, itemResorter.isDurabilityFilterEnabled() ? 1.0F : 0.0F, 84, alpha), 1.0F);
        drawCenteredText(context, itemResorter.isDurabilityFilterEnabled() ? "\u2713" : "x", toggleX, y - 0.2F, 20.0F, 24.0F, 0.86F,
                itemResorter.isDurabilityFilterEnabled() ? argb(255, 122, 232, 149, alpha) : argb(255, 236, 103, 103, alpha), FontRole.SF_PRO);
        if (itemResorter.isDurabilityFilterEnabled()) {
            slider(context, "item_resorter_durability", x + 70.0F, y + 42.0F, 150.0F,
                    (itemResorter.getMinDurabilityPercent() - 1.0F) / 99.0F, new String[]{"1", "50", "100"}, alpha, dt);
        }
    }

    private void drawItemResorterEntrySection(DrawContext context, String title, List<String> items, ItemResorterInput input,
                                              float x, float y, float w, float alpha) {
        float height = itemResorterSectionHeight(items);
        rounded(context, x, y, w, height, 12.0F, color(MODULE_CARD, 226, alpha));
        outline(context, x, y, w, height, 12.0F, color(OUTLINE, 84, alpha), 1.0F);
        drawText(context, title, x + 10.0F, y + 7.0F, 0.92F, color(TEXT_SECONDARY, 224, alpha), FontRole.SF_PRO);

        List<ItemResorterChipLayout> chips = itemResorterChipLayouts(input, items, x + 10.0F, y + 26.0F, w - 20.0F);
        if (chips.isEmpty()) {
            drawText(context, "РџСѓСЃС‚Рѕ", x + 10.0F, y + 30.0F, 0.82F, color(TEXT_DIM, 206, alpha));
            return;
        }

        for (ItemResorterChipLayout chip : chips) {
            rounded(context, chip.x, chip.y, chip.w, chip.h, 7.0F, color(TOGGLE_OFF, 236, alpha));
            outline(context, chip.x, chip.y, chip.w, chip.h, 7.0F, color(OUTLINE, 76, alpha), 1.0F);
            drawScrollingValue(context, chip.value, chip.x + 8.0F, chip.y + 3.0F, chip.w - 24.0F, 16.0F, 0.82F,
                    color(TEXT_MAIN, 240, alpha), FontRole.INTER, chip.value + "_" + chip.input.name());
            rounded(context, chip.x + chip.w - 16.0F, chip.y + 4.0F, 10.0F, 10.0F, 5.0F, color(MODULE_HOVER, 240, alpha));
            drawCenteredText(context, "-", chip.x + chip.w - 18.0F, chip.y + 0.6F, 14.0F, 16.0F, 0.82F, color(TEXT_MAIN, 238, alpha), FontRole.SF_PRO);
        }
    }

    private void drawTargetHud(DrawContext context, float alpha, float dt) {
        float x = targetHudCardX;
        float y = targetHudCardY;
        TargetHud targetHud = FluxVisualsClient.MODULE_MANAGER.getTargetHud();
        sideCard(context, x, y, 225.0F, targetHudCardHeight(), alpha);
        drawCenteredText(context, "TargetHud", x + 30.0F, y + 9.0F, 145.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawText(context, "Scale", x + 13.0F, y + 52.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        slider(context, "targethud_scale", x + 20.0F, y + 82.0F, 170.0F,
                (targetHud.getScale() - 0.75F) / 1.05F, new String[]{"0.75", "1.25", "1.8"}, alpha, dt);
        drawSimpleToggle(context, "Red head", x + 13.0F, y + 118.0F, targetHud.isRedOnDamage(), alpha);
        drawSimpleToggle(context, "Hit particles", x + 13.0F, y + 152.0F, targetHud.isHitParticles(), alpha);
        drawText(context, "Drag in chat", x + 13.0F, y + 187.0F, 0.82F, color(TEXT_DIM, 210, alpha), FontRole.INTER);
    }

    private void drawTapeMouse(DrawContext context, float alpha, float dt) {
        float x = tapeMouseCardX;
        float y = tapeMouseCardY;
        TapeMouse tapeMouse = FluxVisualsClient.MODULE_MANAGER.getTapeMouse();
        sideCard(context, x, y, 225.0F, tapeMouseCardHeight(), alpha);
        drawCenteredText(context, "TapeMouse", x + 30.0F, y + 9.0F, 145.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawText(context, "Button", x + 13.0F, y + 53.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 104.0F, y + 47.0F, 96.0F, 26.0F, 8.0F, color(VIOLET_DEEP, 228, alpha));
        outline(context, x + 104.0F, y + 47.0F, 96.0F, 26.0F, 8.0F, color(OUTLINE_GLOW, 84, alpha), 1.0F);
        drawCenteredText(context, tapeMouse.getButtonMode().label(), x + 104.0F, y + 47.0F, 96.0F, 26.0F, 0.92F, color(TEXT_MAIN, 245, alpha), FontRole.INTER);
        if (tapeMouseButtonOpen) {
            float listY = tapeMouseButtonListY();
            rounded(context, x + 104.0F, listY, 96.0F, 48.0F, 9.0F, color(MODULE_HOVER, 246, alpha));
            outline(context, x + 104.0F, listY, 96.0F, 48.0F, 9.0F, color(OUTLINE_GLOW, 78, alpha), 1.0F);
            for (TapeMouse.ButtonMode mode : TapeMouse.ButtonMode.values()) {
                float rowY = listY + (mode == TapeMouse.ButtonMode.LEFT ? 3.0F : 25.0F);
                boolean active = tapeMouse.getButtonMode() == mode;
                if (active) {
                    rounded(context, x + 108.0F, rowY, 88.0F, 20.0F, 7.0F, color(VIOLET_DEEP, 190, alpha));
                }
                drawCenteredText(context, mode.label(), x + 108.0F, rowY - 1.0F, 88.0F, 20.0F, 0.86F,
                        active ? color(TEXT_MAIN, 248, alpha) : color(TEXT_SECONDARY, 226, alpha), FontRole.INTER);
            }
        }
        drawText(context, "Delay", x + 13.0F, tapeMouseDelayLabelY(), 1.02F, color(TEXT_MAIN, 238, alpha));
        slider(context, "tapemouse_delay", x + 20.0F, tapeMouseDelaySliderY(), 170.0F,
                (tapeMouse.getDelayMs() - 10.0F) / 990.0F, new String[]{"10", "500", "1000"}, alpha, dt);
    }

    private void drawAnarchySwitcher(DrawContext context, float alpha, float dt) {
        float x = anarchySwitcherCardX;
        float y = anarchySwitcherCardY;
        AnarchySwitcher anarchy = FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher();
        sideCard(context, x, y, 245.0F, anarchySwitcherCardHeight(), alpha);
        drawCenteredText(context, "Anarchy", x + 32.0F, y + 9.0F, 145.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawText(context, "Delay", x + 13.0F, y + 52.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
        slider(context, "anarchy_cooldown", x + 20.0F, y + 82.0F, 170.0F,
                (anarchy.getDelaySeconds() - 0.25F) / 299.75F, new String[]{"0.25", "150", "300"}, alpha, dt);
        drawInput(context, anarchyDraft.toString(), anarchyInputFocused, "901", x + 13.0F, y + 118.0F, 174.0F, alpha);
        drawSmallButton(context, "+", x + 195.0F, y + 118.0F, 28.0F, 26.0F, alpha);
        drawSimpleToggle(context, "Write ad", x + 13.0F, y + 154.0F, anarchy.isAdEnabled(), alpha);
        if (anarchy.isAdEnabled()) {
            String adValue = anarchyAdFocused ? anarchyAdDraft.toString() : anarchy.getAdText();
            drawInput(context, adValue, anarchyAdFocused, "Text or /command", x + 13.0F, y + 188.0F, 210.0F, alpha);
        }
        drawChipList(context, anarchy.getAnarchyIds(), x + 13.0F, anarchyListY(), 210.0F, alpha);
    }

    private void drawAutoBuy(DrawContext context, float alpha, float dt) {
        float x = autoBuyCardX;
        float y = autoBuyCardY;
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        sideCard(context, x, y, 290.0F, autoBuyCardHeight(), alpha);
        drawCenteredText(context, "AutoBuy", x + 32.0F, y + 9.0F, 190.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        ItemResorter irAb = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        String enchantsStr = irAb.getEnchantNeeded().isEmpty() ? "не настроены" : String.join(", ", irAb.getEnchantNeeded());
        drawScrollingValue(context, "Чары: " + enchantsStr, x + 13.0F, y + 46.0F, 260.0F, 16.0F, 0.82F, color(TEXT_MAIN, 240, alpha), FontRole.INTER, "ab_enchants");
        drawSimpleToggle(context, "Anarchy switch", x + 13.0F, y + 72.0F, autoBuy.isAnarchySwitchEnabled(), alpha);
        if (autoBuy.isAnarchySwitchEnabled()) {
            drawInput(context, autoBuyAnarchyDraft.toString(), autoBuyAnarchyInputFocused, "901", x + 13.0F, y + 110.0F, 174.0F, alpha);
            drawSmallButton(context, "+", x + 195.0F, y + 110.0F, 28.0F, 26.0F, alpha);
            drawSimpleToggle(context, "Write ad", x + 13.0F, y + 146.0F, autoBuy.isAnarchyAdEnabled(), alpha);
            if (autoBuy.isAnarchyAdEnabled()) {
                String adValue = autoBuyAnarchyAdFocused ? autoBuyAnarchyAdDraft.toString() : autoBuy.getAnarchyAdText();
                drawInput(context, adValue, autoBuyAnarchyAdFocused, "Text or /command", x + 13.0F, y + 180.0F, 210.0F, alpha);
            }
            drawChipList(context, autoBuy.getAnarchyIds(), x + 13.0F, autoBuyAnarchyListY(), 210.0F, alpha);
        }
        drawSimpleToggle(context, "Name", x + 13.0F, autoBuyNameRowY(), autoBuy.isNameEnabled(), alpha);
        if (autoBuy.isNameEnabled()) {
            String nameValue = autoBuyNameFocused ? autoBuyNameDraft.toString() : autoBuy.getNameText();
            drawInput(context, nameValue, autoBuyNameFocused, "&eSword", x + 13.0F, autoBuyNameInputY(), 210.0F, alpha);
        }
        drawSimpleToggle(context, "AutoResell", x + 13.0F, autoBuyResellRowY(), autoBuy.isAutoResellEnabled(), alpha);
        drawSimpleToggle(context, "\u0410\u0440\u0435\u043d\u0434\u0430 \u0441\u043b\u043e\u0442\u043e\u0432", x + 13.0F,
                autoBuyRentalRowY(), autoBuy.isRentalSlotsEnabled(), alpha);
        drawText(context, "\u0411\u0430\u043d \u043f\u0440\u043e\u0434\u0430\u0432\u0446\u043e\u0432", x + 13.0F, autoBuySellerBanLabelY(), 0.94F, color(TEXT_MAIN, 232, alpha), FontRole.INTER);
        drawInput(context, autoBuySellerBanDraft.toString(), autoBuySellerBanFocused, "sanya2922", x + 13.0F, autoBuySellerBanInputY(), 174.0F, alpha);
        drawSmallButton(context, "+", x + 195.0F, autoBuySellerBanInputY(), 28.0F, 26.0F, alpha);
        drawChipList(context, autoBuy.getBannedSellers(), x + 13.0F, autoBuySellerBanListY(), 210.0F, alpha);
    }

    private void drawAutoResellAFK(DrawContext context, float alpha) {
        AutoResellAFK module = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        float x = Math.max(20.0F, Math.min(autoResellAfkCardX, 1060.0F));
        float y = Math.max(20.0F, Math.min(autoResellAfkCardY, 620.0F));
        sideCard(context, x, y, 260.0F, 330.0F, alpha);
        drawCenteredText(context, "AutoResellAFK", x + 28.0F, y + 9.0F, 204.0F, 22.0F, 1.12F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawSimpleToggle(context, "Chat message", x + 13.0F, y + 42.0F, module.isChatEnabled(), alpha);
        if (module.isChatEnabled()) {
            drawInput(context, autoResellAfkChatFocused ? autoResellAfkChatDraft.toString() : module.getChatMessage(),
                    autoResellAfkChatFocused, "Message", x + 13.0F, y + 72.0F, 234.0F, alpha);
            drawText(context, "Interval: " + (module.getChatIntervalMs() / 1000L) + "s", x + 13.0F, y + 107.0F, 0.82F, color(TEXT_SECONDARY, 224, alpha));
            slider(context, "auto_resell_afk_chat_interval", x + 20.0F, y + 130.0F, 210.0F,
                    (module.getChatIntervalMs() - 5_000L) / 995_000.0F, new String[]{"5s", "500s", "1000s"}, alpha, 0.0F);
        }
        float sellY = y + (module.isChatEnabled() ? 178.0F : 60.0F);
        drawSimpleToggle(context, "Sell purchased swords", x + 13.0F, sellY, module.isSellPurchasedSwords(), alpha);
        if (module.isSellPurchasedSwords()) {
            drawInput(context, autoResellAfkPriceFocused ? autoResellAfkPriceDraft.toString() : module.getSellPrice(),
                    autoResellAfkPriceFocused, "Sell price", x + 13.0F, sellY + 30.0F, 234.0F, alpha);
        }
        float helperY = sellY + (module.isSellPurchasedSwords() ? 68.0F : 34.0F);
    }

    private static String formatGuiMoney(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private void drawTelegram(DrawContext context, float alpha) {
        float x = telegramCardX;
        float y = telegramCardY;
        Telegram telegram = FluxVisualsClient.MODULE_MANAGER.getTelegram();
        sideCard(context, x, y, 245.0F, telegramCardHeight(), alpha);
        drawCenteredText(context, "TG", x + 32.0F, y + 9.0F, 145.0F, 22.0F, 1.35F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawSimpleToggle(context, "LOGS", x + 13.0F, y + 40.0F, telegram.isLogsEnabled(), alpha);
        drawText(context, "Bot token", x + 13.0F, y + 76.0F, 0.82F, color(TEXT_MAIN, 232, alpha), FontRole.INTER);
        String tokenValue = telegramTokenFocused ? telegramTokenDraft.toString() : telegram.getBotToken();
        drawInput(context, tokenValue, telegramTokenFocused, "Bot token", x + 13.0F, y + 96.0F, 210.0F, alpha);
        drawText(context, "Chat ID", x + 13.0F, y + 128.0F, 0.82F, color(TEXT_MAIN, 232, alpha), FontRole.INTER);
        String chatValue = telegramChatFocused ? telegramChatDraft.toString() : telegram.getChatId();
        drawInput(context, chatValue, telegramChatFocused, "Chat ID", x + 13.0F, y + 148.0F, 150.0F, alpha);
        drawSmallButton(context, "\u0422\u0435\u0441\u0442", x + 171.0F, y + 148.0F, 61.0F, 26.0F, alpha);
    }

    private void drawNameBinder(DrawContext context, float alpha) {
        float x = nameBinderCardX;
        float y = nameBinderCardY;
        NameBind nameBinder = FluxVisualsClient.MODULE_MANAGER.getNameBind();
        sideCard(context, x, y, 270.0F, nameBinderCardHeight(), alpha);
        drawCenteredText(context, "NameBinder", x + 42.0F, y + 9.0F, 145.0F, 22.0F, 1.25F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawInput(context, nameBinderDraft.toString(), nameBinderInputFocused, "&E&LFUGA", x + 13.0F, y + 52.0F, 210.0F, alpha);
        drawSmallButton(context, "+", x + 230.0F, y + 52.0F, 28.0F, 26.0F, alpha);
        List<NameBind.EntryView> entries = nameBinder.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            float rowY = y + 90.0F + i * 30.0F;
            rounded(context, x + 13.0F, rowY, 244.0F, 24.0F, 8.0F, color(TOGGLE_OFF, 224, alpha));
            outline(context, x + 13.0F, rowY, 244.0F, 24.0F, 8.0F, color(OUTLINE, 72, alpha), 1.0F);
            drawScrollingValue(context, entries.get(i).text(), x + 21.0F, rowY + 4.0F, 137.0F, 16.0F, 0.78F,
                    color(TEXT_MAIN, 242, alpha), FontRole.INTER, "namebinder_" + i);
            drawSmallButton(context, keyLabelOrDash(entries.get(i).keyCode()), x + 165.0F, rowY + 2.0F, 48.0F, 20.0F, alpha);
            drawSmallButton(context, "-", x + 220.0F, rowY + 2.0F, 24.0F, 20.0F, alpha);
        }
    }

    private void drawMacros(DrawContext context, float alpha) {
        float x = macrosCardX;
        float y = macrosCardY;
        Macros macros = FluxVisualsClient.MODULE_MANAGER.getMacros();
        sideCard(context, x, y, 270.0F, macrosCardHeight(), alpha);
        drawCenteredText(context, "Macros", x + 42.0F, y + 9.0F, 145.0F, 22.0F, 1.30F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawInput(context, macrosDraft.toString(), macrosInputFocused, "/spawn or hello", x + 13.0F, y + 52.0F, 210.0F, alpha);
        drawSmallButton(context, "+", x + 230.0F, y + 52.0F, 28.0F, 26.0F, alpha);
        List<Macros.EntryView> entries = macros.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            float rowY = y + 90.0F + i * 30.0F;
            rounded(context, x + 13.0F, rowY, 244.0F, 24.0F, 8.0F, color(TOGGLE_OFF, 224, alpha));
            outline(context, x + 13.0F, rowY, 244.0F, 24.0F, 8.0F, color(OUTLINE, 72, alpha), 1.0F);
            drawScrollingValue(context, entries.get(i).text(), x + 21.0F, rowY + 4.0F, 137.0F, 16.0F, 0.78F,
                    color(TEXT_MAIN, 242, alpha), FontRole.INTER, "macro_" + i);
            drawSmallButton(context, keyLabelOrDash(entries.get(i).keyCode()), x + 165.0F, rowY + 2.0F, 48.0F, 20.0F, alpha);
            drawSmallButton(context, "-", x + 220.0F, rowY + 2.0F, 24.0F, 20.0F, alpha);
        }
    }

    private void drawInput(DrawContext context, String value, boolean focused, String placeholder, float x, float y, float w, float alpha) {
        rounded(context, x, y, w, 26.0F, 8.0F, color(TOGGLE_OFF, 228, alpha));
        outline(context, x, y, w, 26.0F, 8.0F, focused ? color(OUTLINE_GLOW, 102, alpha) : color(OUTLINE, 82, alpha), 1.0F);
        String shown = value == null || value.isBlank() ? placeholder : value;
        drawScrollingValue(context, shown, x + 8.0F, y + 5.0F, w - 16.0F, 16.0F, 0.82F,
                value == null || value.isBlank() ? color(TEXT_DIM, 210, alpha) : color(TEXT_MAIN, 244, alpha),
                FontRole.INTER, "input_" + placeholder);
    }

    private void drawSmallButton(DrawContext context, String label, float x, float y, float w, float h, float alpha) {
        rounded(context, x, y, w, h, 7.0F, color(VIOLET_DEEP, 228, alpha));
        outline(context, x, y, w, h, 7.0F, color(OUTLINE_GLOW, 80, alpha), 1.0F);
        drawCenteredText(context, label, x, y - 0.3F, w, h, label.length() > 3 ? 0.68F : 0.88F, color(TEXT_MAIN, 248, alpha), FontRole.INTER);
    }

    private void drawSimpleToggle(DrawContext context, String label, float x, float y, boolean enabled, float alpha) {
        drawText(context, label, x, y + 4.0F, 0.94F, color(TEXT_MAIN, 232, alpha), FontRole.INTER);
        rounded(context, x + 174.0F, y, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, x + 174.0F, y, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, enabled ? 1.0F : 0.0F, 84, alpha), 1.0F);
        drawCenteredText(context, enabled ? "\u2713" : "x", x + 174.0F, y - 0.2F, 24.0F, 24.0F, 0.88F,
                enabled ? argb(255, 122, 232, 149, alpha) : argb(255, 236, 103, 103, alpha), FontRole.SF_PRO);
    }

    private void drawChipList(DrawContext context, List<String> values, float x, float y, float w, float alpha) {
        float cx = x;
        float cy = y;
        if (values.isEmpty()) {
            drawText(context, "Empty", x, y + 4.0F, 0.82F, color(TEXT_DIM, 206, alpha), FontRole.INTER);
            return;
        }
        for (String value : values) {
            float chipW = clamp(measureBaseTextWidth(value, 0.82F, FontRole.INTER) + 30.0F, 54.0F, w);
            if (cx > x && cx + chipW > x + w) {
                cx = x;
                cy += 28.0F;
            }
            rounded(context, cx, cy, chipW, 22.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
            drawScrollingValue(context, value, cx + 7.0F, cy + 3.0F, chipW - 24.0F, 16.0F, 0.82F,
                    color(TEXT_MAIN, 240, alpha), FontRole.INTER, "anarchy_" + value);
            drawCenteredText(context, "-", cx + chipW - 18.0F, cy, 14.0F, 18.0F, 0.82F, color(TEXT_MAIN, 238, alpha), FontRole.SF_PRO);
            cx += chipW + 6.0F;
        }
    }

    private void drawCrosshair(DrawContext context, float alpha, float dt) {
        float x = crosshairCardX;
        float y = crosshairCardY;
        Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
        crosshairPresetProgress = approach(crosshairPresetProgress, crosshairPresetOpen ? 1.0F : 0.0F, dt, 14.0F);

        sideCard(context, x, y, 225.0F, crosshairCardHeight(), alpha);
        drawCenteredText(context, "Crosshair", x + 28.0F, y + 9.0F, 145.0F, 22.0F, 1.42F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
        drawCardSettingsIcon(context, x + 190.0F, y + 12.0F, alpha);

        float previewY = crosshairContentY();
        rounded(context, x + 13.0F, previewY, 199.0F, 70.0F, 13.0F, argb(218, 34, 36, 45, alpha));
        rounded(context, x + 18.0F, previewY + 5.0F, 189.0F, 60.0F, 10.0F, argb(154, 58, 60, 70, alpha));
        outline(context, x + 13.0F, previewY, 199.0F, 70.0F, 13.0F, color(OUTLINE, 96, alpha), 1.0F);
        crosshair.draw(context, sx(x + 112.5F), sy(previewY + 35.0F), 1.0F, alpha);

        float rowY = previewY + 86.0F;
        drawText(context, "\u041f\u0440\u0435\u0441\u0435\u0442", x + 13.0F, rowY + 3.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 96.0F, rowY - 1.0F, 116.0F, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x + 96.0F, rowY - 1.0F, 116.0F, 24.0F, 9.0F,
                crosshairPresetProgress > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, crosshair.getPreset().label(), x + 100.0F, rowY, 86.0F, 22.0F, 0.72F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThinArrow(context, crosshairPresetOpen ? "v" : ">", x + 191.0F, rowY + 0.4F, alpha);

        if (crosshairPresetProgress > 0.02F) {
            float listY = rowY + 29.0F;
            float listH = crosshairPresetListHeight() * crosshairPresetProgress;
            rounded(context, x + 96.0F, listY, 116.0F, listH, 9.0F, color(MODULE_HOVER, 245, alpha * crosshairPresetProgress));
            outline(context, x + 96.0F, listY, 116.0F, listH, 9.0F, color(OUTLINE, 96, alpha * crosshairPresetProgress), 1.0F);
            int index = 0;
            for (Crosshair.Preset preset : Crosshair.Preset.values()) {
                float itemY = listY + 3.0F + index * 20.0F;
                if (itemY + 20.0F > listY + listH) {
                    break;
                }
                if (preset == crosshair.getPreset()) {
                    rounded(context, x + 100.0F, itemY + 2.0F, 108.0F, 16.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * crosshairPresetProgress));
                }
                drawCenteredText(context, preset.label(), x + 101.0F, itemY, 106.0F, 20.0F, 0.62F,
                        color(TEXT_MAIN, 242, alpha * crosshairPresetProgress), FontRole.OXANIUM);
                index++;
            }
        }

        float customShift = crosshairPresetProgress * crosshairPresetListHeight();
        float sliderY = rowY + 64.0F + customShift;
        drawText(context, "\u0420\u0430\u0437\u043c\u0435\u0440", x + 13.0F, sliderY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
        slider(context, "crosshair_size", x + 20.0F, sliderY, 170.0F, (crosshair.getSize() - 3.0F) / 15.0F,
                new String[]{"3", "10", "18"}, alpha, dt);

        sliderY += 52.0F;
        drawText(context, "\u041e\u0442\u0441\u0442\u0443\u043f", x + 13.0F, sliderY - 27.0F, 1.04F, color(TEXT_MAIN, 238, alpha));
        slider(context, "crosshair_gap", x + 20.0F, sliderY, 170.0F, crosshair.getGap() / 12.0F,
                new String[]{"0", "6", "12"}, alpha, dt);

        sliderY += 52.0F;
        drawText(context, "\u0422\u043e\u043b\u0449\u0438\u043d\u0430", x + 13.0F, sliderY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
        slider(context, "crosshair_thickness", x + 20.0F, sliderY, 170.0F, (crosshair.getThickness() - 1.0F) / 4.0F,
                new String[]{"1", "3", "5"}, alpha, dt);

        sliderY += 52.0F;
        drawText(context, "\u041f\u0440\u043e\u0437\u0440.", x + 13.0F, sliderY - 27.0F, 1.0F, color(TEXT_MAIN, 238, alpha));
        slider(context, "crosshair_opacity", x + 20.0F, sliderY, 170.0F, crosshair.getOpacity(),
                new String[]{"0", "50", "100"}, alpha, dt);

        float toggleY = sliderY + 40.0F;
        drawBooleanToggleRow(context, "\u0422\u043e\u0447\u043a\u0430", x + 13.0F, toggleY, crosshair.isDot() ? 1.0F : 0.0F, alpha);
        toggleY += 36.0F;
        drawBooleanToggleRow(context, "\u041e\u0431\u0432\u043e\u0434\u043a\u0430", x + 13.0F, toggleY, crosshair.isOutline() ? 1.0F : 0.0F, alpha);
        toggleY += 36.0F;
        drawBooleanToggleRow(context, "\u041a\u0440\u0430\u0441\u043d\u0435\u0442\u044c \u043f\u043e \u0446\u0435\u043b\u0438", x + 13.0F, toggleY, crosshair.isRedOnTarget() ? 1.0F : 0.0F, alpha);
        toggleY += 36.0F;
        drawBooleanToggleRow(context, "\u041e\u0442 F5", x + 13.0F, toggleY, crosshair.isShowInThirdPerson() ? 1.0F : 0.0F, alpha);

        float colorY = toggleY + 38.0F;
        drawText(context, "\u0426\u0432\u0435\u0442", x + 13.0F, colorY + 4.0F, 1.08F, color(TEXT_MAIN, 238, alpha));
        rounded(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(MODULE_HOVER, 230, alpha));
        rounded(context, x + 131.0F, colorY + 5.0F, 48.0F, 18.0F, 6.0F, applyAlpha(0xFF000000 | crosshair.getColorRgb(), alpha));
        outline(context, x + 126.0F, colorY, 58.0F, 28.0F, 8.0F, color(OUTLINE, 96, alpha), 1.0F);
        if (crosshairColorOpen) {
            drawCrosshairPalette(context, x + 15.0F, crosshairPaletteY(), alpha);
        }
    }

    private void drawParticleSelect(DrawContext context, String label, Identifier texture, float x, float y, float w, float open, float alpha) {
        rounded(context, x, y, w, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x, y, w, 24.0F, 9.0F, open > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(sx(x + 7.0F)), Math.round(sy(y + 5.0F)),
                0.0F, 0.0F, Math.max(1, Math.round(14.0F * scale)), Math.max(1, Math.round(14.0F * scale)),
                14, 14, 14, 14, argb(255, 255, 255, 255, alpha));
        drawCenteredText(context, label, x + 23.0F, y, w - 42.0F, 24.0F, 0.82F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThinArrow(context, ">", x + w - 20.0F, y + 0.4F, alpha * (1.0F - open));
        drawThinArrow(context, "v", x + w - 20.0F, y + 0.4F, alpha * open);
    }

    private void drawParticleModeSelect(DrawContext context, String label, float x, float y, float w, float open, float alpha) {
        rounded(context, x, y, w, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x, y, w, 24.0F, 9.0F, open > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, label, x + 8.0F, y, w - 26.0F, 24.0F, 0.58F, color(TEXT_MAIN, 245, alpha), FontRole.SF_PRO);
        drawThinArrow(context, ">", x + w - 20.0F, y + 0.4F, alpha * (1.0F - open));
        drawThinArrow(context, "v", x + w - 20.0F, y + 0.4F, alpha * open);
    }

    private void drawParticleTextureList(DrawContext context, float x, float y, float progress, float alpha) {
        float listH = Particles.TextureType.values().length * 22.0F + 6.0F;
        rounded(context, x, y, 94.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 94.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (Particles.TextureType texture : Particles.TextureType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture.id(), Math.round(sx(x + 6.0F)), Math.round(sy(rowY + 4.0F)),
                    0.0F, 0.0F, Math.max(1, Math.round(14.0F * scale)), Math.max(1, Math.round(14.0F * scale)),
                    14, 14, 14, 14, argb(255, 255, 255, 255, alpha * progress));
            drawCenteredText(context, texture.label(), x + 23.0F, rowY, 64.0F, 22.0F, 0.78F, color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawParticleMultiList(DrawContext context, float x, float y, float progress, float alpha) {
        Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
        float listH = Particles.SpawnMode.values().length * 22.0F + 6.0F;
        rounded(context, x, y, 112.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 112.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (Particles.SpawnMode mode : Particles.SpawnMode.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (particles.isModeEnabled(mode)) {
                rounded(context, x + 4.0F, rowY + 2.0F, 104.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, mode.label(), x + 6.0F, rowY, 100.0F, 22.0F, 0.58F, color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            index++;
        }
    }

    private void drawParticlesPalette(DrawContext context, float x, float y, float alpha) {
        Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
        shadow(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, alpha);
        rounded(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(PANEL, 252, alpha));
        rounded(context, x - 3.0F, y - 3.0F, 172.0F, 78.0F, 8.0F, color(APP_BACKGROUND, 62, alpha));
        outline(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(OUTLINE, 122, alpha), 1.0F);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, paletteTexture(particles.getHue()), Math.round(sx(x)), Math.round(sy(y)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(72.0F * scale)),
                166, 72, 166, 72, argb(255, 255, 255, 255, alpha));
        outline(context, x, y, 166.0F, 72.0F, 6.0F, color(OUTLINE, 96, alpha), 1.0F);

        float markerX = x + particles.getSaturation() * 166.0F;
        float markerY = y + (1.0F - particles.getValue()) * 72.0F;
        rounded(context, markerX - 4.0F, markerY - 4.0F, 8.0F, 8.0F, 4.0F, color(TOGGLE_KNOB, 248, alpha));
        outline(context, markerX - 5.0F, markerY - 5.0F, 10.0F, 10.0F, 5.0F, color(APP_BACKGROUND, 175, alpha), 1.0F);

        float hueY = y + 84.0F;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, hueTexture(), Math.round(sx(x)), Math.round(sy(hueY)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(10.0F * scale)),
                166, 10, 166, 10, argb(255, 255, 255, 255, alpha));
        outline(context, x, hueY, 166.0F, 10.0F, 5.0F, color(OUTLINE, 92, alpha), 1.0F);
        float hueX = x + particles.getHue() * 166.0F;
        rounded(context, hueX - 3.5F, hueY - 2.5F, 7.0F, 15.0F, 3.5F, color(TOGGLE_KNOB, 255, alpha));
        outline(context, hueX - 4.5F, hueY - 3.5F, 9.0F, 17.0F, 4.5F, color(APP_BACKGROUND, 165, alpha), 1.0F);
    }

    private void drawJumpCirclesPalette(DrawContext context, float x, float y, float alpha) {
        JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
        drawColorPalette(context, x, y, alpha, jumpCircles.getHue(), jumpCircles.getSaturation(), jumpCircles.getValue());
    }

    private void drawHitColorPalette(DrawContext context, float x, float y, float alpha) {
        HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
        shadow(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, alpha);
        rounded(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(PANEL, 252, alpha));
        rounded(context, x - 3.0F, y - 3.0F, 172.0F, 78.0F, 8.0F, color(APP_BACKGROUND, 62, alpha));
        outline(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(OUTLINE, 122, alpha), 1.0F);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, paletteTexture(hitColor.getHue()), Math.round(sx(x)), Math.round(sy(y)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(72.0F * scale)),
                166, 72, 166, 72, argb(255, 255, 255, 255, alpha));
        outline(context, x, y, 166.0F, 72.0F, 6.0F, color(OUTLINE, 96, alpha), 1.0F);

        float markerX = x + hitColor.getSaturation() * 166.0F;
        float markerY = y + (1.0F - hitColor.getValue()) * 72.0F;
        rounded(context, markerX - 4.0F, markerY - 4.0F, 8.0F, 8.0F, 4.0F, color(TOGGLE_KNOB, 248, alpha));
        outline(context, markerX - 5.0F, markerY - 5.0F, 10.0F, 10.0F, 5.0F, color(APP_BACKGROUND, 175, alpha), 1.0F);

        float hueY = y + 84.0F;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, hueTexture(), Math.round(sx(x)), Math.round(sy(hueY)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(10.0F * scale)),
                166, 10, 166, 10, argb(255, 255, 255, 255, alpha));
        outline(context, x, hueY, 166.0F, 10.0F, 5.0F, color(OUTLINE, 92, alpha), 1.0F);
        float hueX = x + hitColor.getHue() * 166.0F;
        rounded(context, hueX - 3.5F, hueY - 2.5F, 7.0F, 15.0F, 3.5F, color(TOGGLE_KNOB, 255, alpha));
        outline(context, hueX - 4.5F, hueY - 3.5F, 9.0F, 17.0F, 4.5F, color(APP_BACKGROUND, 165, alpha), 1.0F);
    }

    private void drawHitboxOutlinePalette(DrawContext context, float x, float y, float alpha) {
        HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
        drawColorPalette(context, x, y, alpha, hitboxCustomizer.getOutlineHue(), hitboxCustomizer.getOutlineSaturation(), hitboxCustomizer.getOutlineValue());
    }

    private void drawHitboxFillPalette(DrawContext context, float x, float y, float alpha) {
        HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
        drawColorPalette(context, x, y, alpha, hitboxCustomizer.getFillHue(), hitboxCustomizer.getFillSaturation(), hitboxCustomizer.getFillValue());
    }

    private void drawBlockOverlayOutlinePalette(DrawContext context, float x, float y, float alpha) {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        drawColorPalette(context, x, y, alpha, blockOverlay.getOutlineHue(), blockOverlay.getOutlineSaturation(), blockOverlay.getOutlineValue());
    }

    private void drawBlockOverlayFillPalette(DrawContext context, float x, float y, float alpha) {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        drawColorPalette(context, x, y, alpha, blockOverlay.getFillHue(), blockOverlay.getFillSaturation(), blockOverlay.getFillValue());
    }

    private void drawChinaFillModeList(DrawContext context, float x, float y, float progress, float alpha) {
        ChinaHat chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
        float listH = chinaFillModeListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (ChinaHat.FillMode mode : ChinaHat.FillMode.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (mode == chinaHat.getFillMode()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, mode.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(mode.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            index++;
        }
    }

    private void drawChinaShaderList(DrawContext context, float x, float y, float progress, float alpha) {
        ChinaHat chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
        float listH = chinaShaderListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (BlockOverlay.ShaderType shader : BlockOverlay.ShaderType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (shader == chinaHat.getShaderType()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, shader.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(shader.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawJumpCircleModeList(DrawContext context, float x, float y, float progress, float alpha) {
        JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
        float listH = jumpCircleModeListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        JumpCircles.Mode[] modes = {
                JumpCircles.Mode.NORMAL,
                JumpCircles.Mode.BLOCKS
        };
        for (JumpCircles.Mode mode : modes) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (mode == jumpCircles.getMode()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, mode.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(mode.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            index++;
        }
    }

    private void drawJumpCircleTextureList(DrawContext context, float x, float y, float progress, float alpha) {
        JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
        float listH = jumpCircleTextureListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (JumpCircles.TextureType texture : JumpCircles.TextureType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (texture == jumpCircles.getTextureType()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, texture.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(texture.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            index++;
        }
    }

    private void drawJumpCircleParticleTextureList(DrawContext context, float x, float y, float progress, float alpha) {
        JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
        float listH = jumpCircleParticleTextureListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (Particles.TextureType texture : Particles.TextureType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (texture == jumpCircles.getParticleTexture()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, texture.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(texture.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            index++;
        }
    }

    private void drawJumpCircleSelect(DrawContext context, String label, float x, float y, float w, float open, float alpha) {
        rounded(context, x, y, w, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x, y, w, 24.0F, 9.0F, open > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, label, x + 6.0F, y, w - 28.0F, 24.0F, blockOverlaySelectScale(label), color(TEXT_MAIN, 245, alpha), FontRole.SF_PRO);
        drawThinArrow(context, open > 0.5F ? "v" : ">", x + w - 20.0F, y + 0.4F, alpha);
    }

    private void drawBlockOverlaySelect(DrawContext context, String label, float x, float y, float w, float open, float alpha) {
        rounded(context, x, y, w, 24.0F, 9.0F, color(VIOLET_DEEP, 224, alpha));
        outline(context, x, y, w, 24.0F, 9.0F, open > 0.02F ? color(OUTLINE_GLOW, 82, alpha) : color(OUTLINE, 74, alpha), 1.0F);
        drawCenteredText(context, label, x + 6.0F, y, w - 28.0F, 24.0F, blockOverlaySelectScale(label), color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
        drawThinArrow(context, open > 0.5F ? "v" : ">", x + w - 20.0F, y + 0.4F, alpha);
    }

    private void drawTargetStyleList(DrawContext context, float x, float y, float progress, float alpha) {
        TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
        float listH = targetStyleListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (TargetEsp.Style style : TargetEsp.Style.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (style == targetEsp.getStyle()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, style.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(style.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawTargetFilterList(DrawContext context, float x, float y, float progress, float alpha) {
        TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
        float listH = targetFilterListHeight();
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (TargetEsp.TargetFilter filter : TargetEsp.TargetFilter.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (filter == targetEsp.getTargetFilter()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, filter.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(filter.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawHitboxTargetList(DrawContext context, float x, float y, float progress, float alpha) {
        HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
        float listH = hitboxTargetListHeight();
        rounded(context, x, y, 108.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 108.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (HitboxCustomizer.TargetType type : HitboxCustomizer.TargetType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            boolean selected = hitboxCustomizer.isTargetEnabled(type);
            if (selected) {
                rounded(context, x + 4.0F, rowY + 2.0F, 100.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, selected ? "\u2713" : "", x + 8.0F, rowY + 0.8F, 14.0F, 22.0F,
                    0.72F, color(TEXT_MAIN, 242, alpha * progress), FontRole.SF_PRO);
            drawCenteredText(context, type.label(), x + 22.0F, rowY, 76.0F, 22.0F,
                    blockOverlayListScale(type.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawBlockOverlayFillModeList(DrawContext context, float x, float y, float progress, float alpha) {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        float listH = BlockOverlay.FillMode.values().length * 22.0F + 6.0F;
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (BlockOverlay.FillMode mode : BlockOverlay.FillMode.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (mode == blockOverlay.getFillMode()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, mode.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(mode.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private void drawBlockOverlayShaderList(DrawContext context, float x, float y, float progress, float alpha) {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        float listH = BlockOverlay.ShaderType.values().length * 22.0F + 6.0F;
        rounded(context, x, y, 100.0F, listH * progress, 9.0F, color(MODULE_HOVER, 245, alpha * progress));
        outline(context, x, y, 100.0F, listH * progress, 9.0F, color(OUTLINE, 96, alpha * progress), 1.0F);
        int index = 0;
        for (BlockOverlay.ShaderType shader : BlockOverlay.ShaderType.values()) {
            float rowY = y + 3.0F + index * 22.0F;
            if (rowY + 22.0F > y + listH * progress) {
                break;
            }
            if (shader == blockOverlay.getShaderType()) {
                rounded(context, x + 4.0F, rowY + 2.0F, 92.0F, 18.0F, 7.0F, color(VIOLET_DEEP, 185, alpha * progress));
            }
            drawCenteredText(context, shader.label(), x + 6.0F, rowY, 88.0F, 22.0F, blockOverlayListScale(shader.label()), color(TEXT_MAIN, 242, alpha * progress), FontRole.OXANIUM);
            index++;
        }
    }

    private static float blockOverlaySelectScale(String label) {
        return label.length() > 9 ? 0.62F : 0.76F;
    }

    private static float blockOverlayListScale(String label) {
        return label.length() > 9 ? 0.58F : 0.72F;
    }

    private void drawWorldFogPalette(DrawContext context, float x, float y, float alpha) {
        WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        drawColorPalette(context, x, y, alpha, worldCustomizer.getFogHue(), worldCustomizer.getFogSaturation(), worldCustomizer.getFogValue());
    }

    private void drawTrailsPalette(DrawContext context, float x, float y, float alpha) {
        Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();
        drawColorPalette(context, x, y, alpha, trails.getHue(), trails.getSaturation(), trails.getValue());
    }

    private void drawCrosshairPalette(DrawContext context, float x, float y, float alpha) {
        Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
        drawColorPalette(context, x, y, alpha, crosshair.getHue(), crosshair.getSaturation(), crosshair.getValue());
    }

    private void drawTargetPalette(DrawContext context, float x, float y, float alpha) {
        TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
        drawColorPalette(context, x, y, alpha, targetEsp.getHue(), targetEsp.getSaturation(), targetEsp.getValue());
    }

    private void drawColorPalette(DrawContext context, float x, float y, float alpha, float hue, float saturation, float value) {
        shadow(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, alpha);
        rounded(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(PANEL, 252, alpha));
        rounded(context, x - 3.0F, y - 3.0F, 172.0F, 78.0F, 8.0F, color(APP_BACKGROUND, 62, alpha));
        outline(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(OUTLINE, 122, alpha), 1.0F);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, paletteTexture(hue), Math.round(sx(x)), Math.round(sy(y)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(72.0F * scale)),
                166, 72, 166, 72, argb(255, 255, 255, 255, alpha));
        outline(context, x, y, 166.0F, 72.0F, 6.0F, color(OUTLINE, 96, alpha), 1.0F);

        float markerX = x + saturation * 166.0F;
        float markerY = y + (1.0F - value) * 72.0F;
        rounded(context, markerX - 4.0F, markerY - 4.0F, 8.0F, 8.0F, 4.0F, color(TOGGLE_KNOB, 248, alpha));
        outline(context, markerX - 5.0F, markerY - 5.0F, 10.0F, 10.0F, 5.0F, color(APP_BACKGROUND, 175, alpha), 1.0F);

        float hueY = y + 84.0F;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, hueTexture(), Math.round(sx(x)), Math.round(sy(hueY)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(10.0F * scale)),
                166, 10, 166, 10, argb(255, 255, 255, 255, alpha));
        outline(context, x, hueY, 166.0F, 10.0F, 5.0F, color(OUTLINE, 92, alpha), 1.0F);
        float hueX = x + hue * 166.0F;
        rounded(context, hueX - 3.5F, hueY - 2.5F, 7.0F, 15.0F, 3.5F, color(TOGGLE_KNOB, 255, alpha));
        outline(context, hueX - 4.5F, hueY - 3.5F, 9.0F, 17.0F, 4.5F, color(APP_BACKGROUND, 165, alpha), 1.0F);
    }

    private void drawPalette(DrawContext context, float x, float y, float alpha) {
        var chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
        shadow(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, alpha);
        rounded(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(PANEL, 252, alpha));
        rounded(context, x - 3.0F, y - 3.0F, 172.0F, 78.0F, 8.0F, color(APP_BACKGROUND, 62, alpha));
        outline(context, x - 8.0F, y - 8.0F, 182.0F, 118.0F, 14.0F, color(OUTLINE, 122, alpha), 1.0F);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, paletteTexture(chinaHat.getHue()), Math.round(sx(x)), Math.round(sy(y)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(72.0F * scale)),
                166, 72, 166, 72, argb(255, 255, 255, 255, alpha));
        outline(context, x, y, 166.0F, 72.0F, 6.0F, color(OUTLINE, 96, alpha), 1.0F);

        float markerX = x + chinaHat.getSaturation() * 166.0F;
        float markerY = y + (1.0F - chinaHat.getValue()) * 72.0F;
        rounded(context, markerX - 4.0F, markerY - 4.0F, 8.0F, 8.0F, 4.0F, color(TOGGLE_KNOB, 248, alpha));
        outline(context, markerX - 5.0F, markerY - 5.0F, 10.0F, 10.0F, 5.0F, color(APP_BACKGROUND, 175, alpha), 1.0F);

        float hueY = y + 84.0F;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, hueTexture(), Math.round(sx(x)), Math.round(sy(hueY)),
                0.0F, 0.0F, Math.max(1, Math.round(166.0F * scale)), Math.max(1, Math.round(10.0F * scale)),
                166, 10, 166, 10, argb(255, 255, 255, 255, alpha));
        outline(context, x, hueY, 166.0F, 10.0F, 5.0F, color(OUTLINE, 92, alpha), 1.0F);
        float hueX = x + chinaHat.getHue() * 166.0F;
        rounded(context, hueX - 3.5F, hueY - 2.5F, 7.0F, 15.0F, 3.5F, color(TOGGLE_KNOB, 255, alpha));
        outline(context, hueX - 4.5F, hueY - 3.5F, 9.0F, 17.0F, 4.5F, color(APP_BACKGROUND, 165, alpha), 1.0F);
    }

    private void drawParticlesGlowToggle(DrawContext context, float x, float y, float active, float alpha) {
        drawText(context, "\u0421\u0432\u0435\u0447\u0435\u043d\u0438\u0435", x, y + 1.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        float boxX = x + 175.0F;
        rounded(context, boxX, y, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, boxX, y, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, active, 84, alpha), 1.0F);

        int crossColor = argb(Math.round(255.0F * (1.0F - active)), 228, 74, 74, alpha);
        int checkColor = argb(Math.round(255.0F * active), 84, 214, 108, alpha);
        drawCenteredText(context, "x", boxX, y - 0.3F - active * 1.4F, 24.0F, 24.0F, 1.0F - active * 0.1F, crossColor, FontRole.SF_PRO);
        drawCenteredText(context, "\u2713", boxX, y + 0.8F - (1.0F - active) * 1.4F, 24.0F, 24.0F, 1.03F, checkColor, FontRole.SF_PRO);
    }

    private void drawThinArrow(DrawContext context, String value, float x, float y, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }

        drawCenteredText(context, value, x, y, 18.0F, 22.0F, 0.94F, color(TEXT_MAIN, 250, alpha), FontRole.SF_PRO);
    }

    private void drawParticlesGlowToggleClean(DrawContext context, float x, float y, float active, float alpha) {
        drawText(context, "\u0421\u0432\u0435\u0447\u0435\u043d\u0438\u0435", x, y + 1.0F, 1.02F, color(TEXT_MAIN, 238, alpha));
        float boxX = x + 175.0F;
        rounded(context, boxX, y, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, boxX, y, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, active, 84, alpha), 1.0F);

        int crossColor = argb(Math.round(255.0F * (1.0F - active)), 228, 74, 74, alpha);
        int checkColor = argb(Math.round(255.0F * active), 84, 214, 108, alpha);
        drawCenteredText(context, "x", boxX, y - 0.3F - active * 1.4F, 24.0F, 24.0F, 1.0F - active * 0.1F, crossColor, FontRole.SF_PRO);
        drawCenteredText(context, "\u2713", boxX, y + 0.8F - (1.0F - active) * 1.4F, 24.0F, 24.0F, 1.03F, checkColor, FontRole.SF_PRO);
    }

    private void drawBooleanToggleRow(DrawContext context, String label, float x, float y, float active, float alpha) {
        drawText(context, label, x, y + 1.0F, 0.97F, color(TEXT_MAIN, 238, alpha));
        float boxX = x + 175.0F;
        rounded(context, boxX, y, 24.0F, 24.0F, 7.0F, color(TOGGLE_OFF, 236, alpha));
        outline(context, boxX, y, 24.0F, 24.0F, 7.0F, mix(OUTLINE, OUTLINE_GLOW, active, 84, alpha), 1.0F);

        int crossColor = argb(Math.round(255.0F * (1.0F - active)), 228, 74, 74, alpha);
        int checkColor = argb(Math.round(255.0F * active), 84, 214, 108, alpha);
        drawCenteredText(context, "x", boxX, y - 0.3F - active * 1.4F, 24.0F, 24.0F, 1.0F - active * 0.1F, crossColor, FontRole.SF_PRO);
        drawCenteredText(context, "\u2713", boxX, y + 0.8F - (1.0F - active) * 1.4F, 24.0F, 24.0F, 1.03F, checkColor, FontRole.SF_PRO);
    }

    private void drawThickArrow(DrawContext context, String value, float x, float y, float alpha) {
        drawThinArrow(context, "V".equals(value) ? "v" : value, x, y + 0.4F, alpha);
    }

    private void sideCard(DrawContext context, float x, float y, float w, float h, float alpha) {
        shadow(context, x, y, w, h, 16.0F, alpha);
        rounded(context, x, y, w, h, 15.0F, color(PANEL, 248, alpha));
        rounded(context, x + 1.0F, y + 1.0F, w - 2.0F, 31.0F, 14.0F, color(MODULE_CARD, 116, alpha));
        fill(context, x + 16.0F, y + 38.0F, w - 32.0F, 1.0F, color(OUTLINE, 54, alpha));
        outline(context, x, y, w, h, 15.0F, color(OUTLINE, 112, alpha), 1.0F);
        drawCardCloseButton(context, x, y, alpha);
    }

    private void drawCardCloseButton(DrawContext context, float x, float y, float alpha) {
        float closeX = x + 9.0F;
        float closeY = y + 8.0F;
        rounded(context, closeX, closeY, 18.0F, 18.0F, 5.0F, color(MODULE_HOVER, 224, alpha));
        outline(context, closeX, closeY, 18.0F, 18.0F, 5.0F, color(TEXT_MAIN, 92, alpha), 1.0F);
        drawCenteredText(context, "x", closeX, closeY - 0.6F, 18.0F, 18.0F, 0.95F, color(TEXT_MAIN, 255, alpha), FontRole.SF_PRO);
    }

    private void slider(DrawContext context, String id, float x, float y, float w, float initial, String[] labels, float alpha, float dt) {
        SliderState state = sliders.computeIfAbsent(id, ignored -> new SliderState(initial));
        if ("china_size".equals(id) && draggingSlider == null) {
            state.value = FluxVisualsClient.MODULE_MANAGER.getChinaHat().getSize();
        } else if ("china_shader_alpha".equals(id) && draggingSlider == null) {
            state.value = FluxVisualsClient.MODULE_MANAGER.getChinaHat().getShaderAlpha();
        } else if ("aspect_custom".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getAspectRatio().getCustomRatio() - 1.0F) / 1.5F;
        } else if (id.startsWith("world_") && draggingSlider == null) {
            WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
            if ("world_custom_time".equals(id)) {
                state.value = worldCustomizer.getCustomTime() / 23999.0F;
            } else if ("world_fog_distance".equals(id)) {
                state.value = (worldCustomizer.getFogDistance() - 8.0F) / 142.0F;
            }
        } else if (id.startsWith("target_") && draggingSlider == null) {
            TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
            if ("target_distance".equals(id)) {
                state.value = (targetEsp.getMaxDistance() - 2.0F) / 30.0F;
            } else if ("target_lost_delay".equals(id)) {
                state.value = (targetEsp.getLostDelaySeconds() - 0.2F) / 3.8F;
            } else if ("target_speed".equals(id)) {
                state.value = (targetEsp.getAnimationSpeed() - 0.1F) / 3.9F;
            } else if ("target_size".equals(id)) {
                state.value = (targetEsp.getNormalSize() - 0.45F) / 1.55F;
            } else if ("target_ghost_size".equals(id)) {
                state.value = (targetEsp.getGhostSize() - TargetEsp.GHOST_SIZE_MIN) / TargetEsp.GHOST_SIZE_RANGE;
            } else if ("target_ghost_count".equals(id)) {
                state.value = (targetEsp.getGhostCount() - 1.0F) / 11.0F;
            } else if ("target_ghost_length".equals(id)) {
                state.value = (targetEsp.getGhostLength() - TargetEsp.GHOST_LENGTH_MIN) / TargetEsp.GHOST_LENGTH_RANGE;
            } else if ("target_ghost_speed".equals(id)) {
                state.value = (targetEsp.getGhostSpeed() - 0.2F) / 4.8F;
            } else if ("target_crystal_speed".equals(id)) {
                state.value = (targetEsp.getCrystalSpeed() - TargetEsp.CRYSTAL_SPEED_MIN) / TargetEsp.CRYSTAL_SPEED_RANGE;
            } else if ("target_crystal_count".equals(id)) {
                state.value = (targetEsp.getCrystalCount() - TargetEsp.CRYSTAL_COUNT_MIN)
                        / (float) (TargetEsp.CRYSTAL_COUNT_MAX - TargetEsp.CRYSTAL_COUNT_MIN);
            } else if ("target_crystal_size".equals(id)) {
                state.value = (targetEsp.getCrystalSize() - TargetEsp.CRYSTAL_SIZE_MIN) / TargetEsp.CRYSTAL_SIZE_RANGE;
            } else if ("target_crystal_radius".equals(id)) {
                state.value = (targetEsp.getCrystalRadius() - TargetEsp.CRYSTAL_RADIUS_MIN) / TargetEsp.CRYSTAL_RADIUS_RANGE;
            }
        } else if (id.startsWith("particles_") && draggingSlider == null) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            if ("particles_amount".equals(id)) {
                state.value = (particles.getAmount() - 1.0F) / 119.0F;
            } else if ("particles_life".equals(id)) {
                state.value = (particles.getLifeSeconds() - 0.4F) / 5.6F;
            } else if ("particles_size".equals(id)) {
                state.value = (particles.getSize() - 0.04F) / 0.51F;
            }
        } else if (id.startsWith("jump_circles_") && draggingSlider == null) {
            JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
            if ("jump_circles_amount".equals(id)) {
                state.value = (jumpCircles.getAmount() - 1.0F) / 95.0F;
            } else if ("jump_circles_life".equals(id)) {
                state.value = (jumpCircles.getLifeSeconds() - 0.25F) / 3.75F;
            } else if ("jump_circles_size".equals(id)) {
                state.value = (jumpCircles.getSize() - 0.35F) / 2.65F;
            } else if ("jump_circles_particle_size".equals(id)) {
                state.value = (jumpCircles.getParticleSize() - 0.25F) / 2.25F;
            } else if ("jump_circles_spread".equals(id)) {
                state.value = jumpCircles.getSpread();
            }
        } else if (id.startsWith("trails_") && draggingSlider == null) {
            Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();
            if ("trails_max_length".equals(id)) {
                state.value = (trails.getMaxLength() - Trails.MIN_LENGTH) / (Trails.MAX_LENGTH - Trails.MIN_LENGTH);
            } else if ("trails_alpha".equals(id)) {
                state.value = trails.getAlpha() / Trails.MAX_ALPHA;
            }
        } else if ("auto_resell_afk_chat_interval".equals(id) && draggingSlider == null) {
            state.value = clamp((FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().getChatIntervalMs() - 5_000L) / 995_000.0F);
        } else if (id.startsWith("hitbox_") && draggingSlider == null) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            if ("hitbox_line_thickness".equals(id)) {
                state.value = (hitboxCustomizer.getLineThickness() - 1.0F) / 4.0F;
            } else if ("hitbox_fill_alpha".equals(id)) {
                state.value = hitboxCustomizer.getFillAlpha();
            }
        } else if (id.startsWith("block_overlay_") && draggingSlider == null) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            if ("block_overlay_line_thickness".equals(id)) {
                state.value = (blockOverlay.getLineThickness() - 1.0F) / 4.0F;
            } else if ("block_overlay_animation_speed".equals(id)) {
                state.value = (blockOverlay.getAnimationSpeed() - 0.1F) / 3.9F;
            } else if ("block_overlay_fill_alpha".equals(id)) {
                state.value = blockOverlay.getFillAlpha();
            }
        } else if ("hit_color_alpha".equals(id) && draggingSlider == null) {
            state.value = FluxVisualsClient.MODULE_MANAGER.getHitColor().getAlpha();
        } else if ("freelook_distance".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getFreeLook().getCameraDistance() - 2.0F) / 10.0F;
        } else if ("zoom_smoothness".equals(id) && draggingSlider == null) {
            state.value = FluxVisualsClient.MODULE_MANAGER.getZoom().getSmoothness();
        } else if ("targethud_scale".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getTargetHud().getScale() - 0.75F) / 1.05F;
        } else if ("tapemouse_delay".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getTapeMouse().getDelayMs() - 10.0F) / 990.0F;
        } else if ("anarchy_cooldown".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().getDelaySeconds() - 0.25F) / 299.75F;
        } else if ("item_resorter_durability".equals(id) && draggingSlider == null) {
            state.value = (FluxVisualsClient.MODULE_MANAGER.getItemResorter().getMinDurabilityPercent() - 1.0F) / 99.0F;
        } else if (id.startsWith("tab_customizer_") && draggingSlider == null) {
            TabCustomizer tabCustomizer = FluxVisualsClient.MODULE_MANAGER.getTabCustomizer();
            if ("tab_customizer_columns".equals(id)) {
                state.value = (tabCustomizer.getColumns() - 1.0F) / 7.0F;
            } else if ("tab_customizer_players".equals(id)) {
                state.value = (tabCustomizer.getPlayersPerColumn() - 1.0F) / 29.0F;
            } else if ("tab_customizer_scale".equals(id)) {
                state.value = (tabCustomizer.getScale() - 0.75F) / 0.5F;
            }
        } else if (id.startsWith("crosshair_") && draggingSlider == null) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            if ("crosshair_size".equals(id)) {
                state.value = (crosshair.getSize() - 3.0F) / 15.0F;
            } else if ("crosshair_gap".equals(id)) {
                state.value = crosshair.getGap() / 12.0F;
            } else if ("crosshair_thickness".equals(id)) {
                state.value = (crosshair.getThickness() - 1.0F) / 4.0F;
            } else if ("crosshair_opacity".equals(id)) {
                state.value = crosshair.getOpacity();
            }
        }
        state.visual = approach(state.visual, state.value, dt, 14.0F);
        rounded(context, x, y - 1.0F, w, 5.0F, 2.5F, color(APP_BACKGROUND, 125, alpha));
        rounded(context, x, y, w, 3.0F, 1.5F, color(VIOLET_DEEP, 205, alpha));
        rounded(context, x, y, w * state.visual, 3.0F, 1.5F, color(PURPLE, 242, alpha));
        rounded(context, x + w * state.visual - 3.6F, y - 2.6F, 7.2F, 7.2F, 3.6F, color(TOGGLE_KNOB, 255, alpha));
        outline(context, x + w * state.visual - 4.6F, y - 3.6F, 9.2F, 9.2F, 4.6F, color(APP_BACKGROUND, 140, alpha), 1.0F);
        float labelY = y + 10.0F;
        drawCenteredText(context, labels[0], x - 17.0F, labelY, 34.0F, 13.0F, 0.88F, color(TEXT_SECONDARY, 224, alpha), FontRole.OXANIUM);
        drawCenteredText(context, labels[1], x + w * 0.5F - 18.0F, labelY, 36.0F, 13.0F, 0.88F, color(TEXT_MAIN, 230, alpha), FontRole.OXANIUM);
        drawCenteredText(context, labels[2], x + w - 17.0F, labelY, 34.0F, 13.0F, 0.88F, color(TEXT_SECONDARY, 224, alpha), FontRole.OXANIUM);
        drawValuePill(context, sliderValueLabel(id, state.value), x + w - 40.0F, y - 27.0F, 44.0F, 19.0F, alpha);
    }

    private void disabledSlider(DrawContext context, float x, float y, float w, float value, String[] labels, float alpha) {
        float clamped = clamp(value);
        rounded(context, x, y - 1.0F, w, 5.0F, 2.5F, color(APP_BACKGROUND, 92, alpha));
        rounded(context, x, y, w, 3.0F, 1.5F, color(VIOLET_DEEP, 92, alpha));
        rounded(context, x, y, w * clamped, 3.0F, 1.5F, color(PURPLE, 110, alpha));
        rounded(context, x + w * clamped - 3.2F, y - 2.2F, 6.4F, 6.4F, 3.2F, color(TOGGLE_KNOB, 160, alpha));
        outline(context, x + w * clamped - 4.2F, y - 3.2F, 8.4F, 8.4F, 4.2F, color(APP_BACKGROUND, 104, alpha), 1.0F);
        float labelY = y + 10.0F;
        drawCenteredText(context, labels[0], x - 17.0F, labelY, 34.0F, 13.0F, 0.88F, color(TEXT_SECONDARY, 170, alpha), FontRole.OXANIUM);
        drawCenteredText(context, labels[1], x + w * 0.5F - 18.0F, labelY, 36.0F, 13.0F, 0.88F, color(TEXT_SECONDARY, 170, alpha), FontRole.OXANIUM);
        drawCenteredText(context, labels[2], x + w - 17.0F, labelY, 34.0F, 13.0F, 0.88F, color(TEXT_SECONDARY, 170, alpha), FontRole.OXANIUM);
    }

    private void drawValuePill(DrawContext context, String value, float x, float y, float w, float h, float alpha) {
        rounded(context, x, y, w, h, 6.0F, color(MODULE_HOVER, 232, alpha));
        outline(context, x, y, w, h, 6.0F, color(OUTLINE, 84, alpha), 1.0F);
        drawCenteredText(context, value, x, y - 0.5F, w, h, value.length() > 4 ? 0.76F : 0.88F, color(TEXT_MAIN, 245, alpha), FontRole.OXANIUM);
    }

    private void drawBindButton(DrawContext context, String moduleName, float x, float y, float w, float h, float alpha) {
        VisualModule module = moduleByName(moduleName);
        boolean listening = (bindingTarget != null && module != null && bindingTarget.name.equals(moduleName))
                || moduleName.equals(bindingOptionTarget);
        int fallback = FreeLook.PERSPECTIVE_BIND_KEY.equals(moduleName)
                ? GLFW.GLFW_KEY_LEFT_ALT
                : (Zoom.BIND_KEY.equals(moduleName) ? GLFW.GLFW_KEY_C : GLFW.GLFW_KEY_UNKNOWN);
        int keyCode = getBind(moduleName, fallback);
        String label = listening ? "..." : (keyCode == GLFW.GLFW_KEY_UNKNOWN ? "-" : keyLabel(keyCode));
        rounded(context, x, y, w, h, 8.0F, color(TOGGLE_OFF, 228, alpha));
        outline(context, x, y, w, h, 8.0F, listening ? color(OUTLINE_GLOW, 98, alpha) : color(OUTLINE, 84, alpha), 1.0F);
        drawCenteredText(context, label, x, y - 0.2F, w, h, label.length() > 2 ? 0.72F : 0.92F, color(TEXT_MAIN, 242, alpha),
                label.length() > 1 ? FontRole.INTER : FontRole.MONO);
    }

    private static String sliderValueLabel(String id, float value) {
        if ("target_distance".equals(id)) {
            return String.format(Locale.ROOT, "%.0f", 2.0F + value * 30.0F);
        }
        if ("target_lost_delay".equals(id)) {
            return String.format(Locale.ROOT, "%.1fs", 0.2F + value * 3.8F);
        }
        if ("target_speed".equals(id)) {
            return String.format(Locale.ROOT, "%.1fx", 0.1F + value * 3.9F);
        }
        if ("target_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 0.45F + value * 1.55F);
        }
        if ("target_ghost_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", TargetEsp.GHOST_SIZE_MIN + value * TargetEsp.GHOST_SIZE_RANGE);
        }
        if ("target_ghost_count".equals(id)) {
            return Integer.toString(Math.round(1.0F + value * 11.0F));
        }
        if ("target_ghost_length".equals(id)) {
            return String.format(Locale.ROOT, "%.1f", TargetEsp.GHOST_LENGTH_MIN + value * TargetEsp.GHOST_LENGTH_RANGE);
        }
        if ("target_ghost_speed".equals(id)) {
            return String.format(Locale.ROOT, "%.1fx", 0.2F + value * 4.8F);
        }
        if ("target_crystal_speed".equals(id)) {
            return String.format(Locale.ROOT, "%.1fx", TargetEsp.CRYSTAL_SPEED_MIN + value * TargetEsp.CRYSTAL_SPEED_RANGE);
        }
        if ("target_crystal_count".equals(id)) {
            return Integer.toString(Math.round(TargetEsp.CRYSTAL_COUNT_MIN
                    + value * (TargetEsp.CRYSTAL_COUNT_MAX - TargetEsp.CRYSTAL_COUNT_MIN)));
        }
        if ("target_crystal_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", TargetEsp.CRYSTAL_SIZE_MIN + value * TargetEsp.CRYSTAL_SIZE_RANGE);
        }
        if ("target_crystal_radius".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", TargetEsp.CRYSTAL_RADIUS_MIN + value * TargetEsp.CRYSTAL_RADIUS_RANGE);
        }
        if ("china_size".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("china_shader_alpha".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("aspect_custom".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 1.0F + value * 1.5F);
        }
        if ("particles_amount".equals(id)) {
            return Integer.toString(Math.round(1.0F + value * 119.0F));
        }
        if ("particles_life".equals(id)) {
            return String.format(Locale.ROOT, "%.1fs", 0.4F + value * 5.6F);
        }
        if ("particles_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 0.04F + value * 0.51F);
        }
        if ("jump_circles_amount".equals(id)) {
            return Integer.toString(Math.round(1.0F + value * 95.0F));
        }
        if ("jump_circles_life".equals(id)) {
            return String.format(Locale.ROOT, "%.1fs", 0.25F + value * 3.75F);
        }
        if ("jump_circles_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 0.35F + value * 2.65F);
        }
        if ("jump_circles_particle_size".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 0.25F + value * 2.25F);
        }
        if ("jump_circles_spread".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("trails_max_length".equals(id)) {
            return Integer.toString(Math.round(Trails.MIN_LENGTH + value * (Trails.MAX_LENGTH - Trails.MIN_LENGTH)));
        }
        if ("trails_alpha".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("world_custom_time".equals(id)) {
            return Long.toString(Math.round(value * 23999.0F));
        }
        if ("world_fog_distance".equals(id)) {
            return String.format(Locale.ROOT, "%.0f", 8.0F + value * 142.0F);
        }
        if ("zoom_smoothness".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("targethud_scale".equals(id)) {
            return String.format(Locale.ROOT, "%.2f", 0.75F + value * 1.05F);
        }
        if ("tapemouse_delay".equals(id)) {
            return Math.round(10.0F + value * 990.0F) + "ms";
        }
        if ("anarchy_cooldown".equals(id)) {
            return String.format(Locale.ROOT, "%.1fs", 0.25F + value * 299.75F);
        }
        if ("item_resorter_durability".equals(id)) {
            return Math.round(1.0F + value * 99.0F) + "%";
        }
        if ("freelook_distance".equals(id)) {
            return String.format(Locale.ROOT, "%.1f", 2.0F + value * 10.0F);
        }
        if ("tab_customizer_columns".equals(id)) {
            return Integer.toString(Math.round(1.0F + value * 7.0F));
        }
        if ("tab_customizer_players".equals(id)) {
            return Integer.toString(Math.round(1.0F + value * 29.0F));
        }
        if ("tab_customizer_scale".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", (0.75F + value * 0.5F) * 100.0F);
        }
        if ("hitbox_line_thickness".equals(id)) {
            return String.format(Locale.ROOT, "%.1f", 1.0F + value * 4.0F);
        }
        if ("hitbox_fill_alpha".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("block_overlay_animation_speed".equals(id)) {
            return String.format(Locale.ROOT, "%.1fx", 0.1F + value * 3.9F);
        }
        if ("block_overlay_line_thickness".equals(id)) {
            return String.format(Locale.ROOT, "%.1f", 1.0F + value * 4.0F);
        }
        if ("block_overlay_fill_alpha".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("hit_color_alpha".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        if ("crosshair_size".equals(id)) {
            return String.format(Locale.ROOT, "%.0f", 3.0F + value * 15.0F);
        }
        if ("crosshair_gap".equals(id)) {
            return String.format(Locale.ROOT, "%.0f", value * 12.0F);
        }
        if ("crosshair_thickness".equals(id)) {
            return String.format(Locale.ROOT, "%.1f", 1.0F + value * 4.0F);
        }
        if ("crosshair_opacity".equals(id)) {
            return String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Category tabAt(float x, float y) {
        if (inside(x, y, 345.0F, 216.0F, 106.0F, 27.0F)) return Category.VISUALS;
        if (inside(x, y, 478.0F, 216.0F, 106.0F, 27.0F)) return Category.HUD;
        if (inside(x, y, 611.0F, 216.0F, 106.0F, 27.0F)) return Category.UTILS;
        return null;
    }

    private boolean searchHitbox(float x, float y) {
        float progress = Math.max(searchProgress, searchOpen ? 1.0F : 0.0F);
        float boxX = 807.0F - 72.0F * progress;
        float boxW = 30.0F + 72.0F * progress;
        return inside(x, y, boxX, 215.0F, boxW, 30.0F);
    }

    private List<VisualModule> visibleModules() {
        String query = search.toString().toLowerCase(Locale.ROOT);
        return modules.stream()
                .filter(module -> module.category == selectedCategory)
                .filter(module -> query.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private float maxModuleScroll() {
        return maxModuleScroll(visibleModules());
    }

    private static float maxModuleScroll(List<VisualModule> visible) {
        int rows = (visible.size() + 1) / 2;
        float contentHeight = rows <= 0 ? 0.0F : rows * MODULE_GAP_Y - (MODULE_GAP_Y - MODULE_H);
        return Math.max(0.0F, contentHeight - MODULE_LIST_H);
    }

    private VisualModule moduleAt(float x, float y) {
        if (!inside(x, y, MODULE_LIST_X - 8.0F, MODULE_LIST_Y - 8.0F, MODULE_LIST_W + 16.0F, MODULE_LIST_H + 16.0F)) {
            return null;
        }
        List<VisualModule> visible = visibleModules();
        for (int i = 0; i < visible.size(); i++) {
            float bx = (i % 2 == 0) ? MODULE_LIST_X : 608.0F;
            float by = MODULE_LIST_Y + (i / 2) * MODULE_GAP_Y - moduleScroll;
            if (inside(x, y, bx, by, MODULE_W, MODULE_H)) {
                return visible.get(i);
            }
        }
        return null;
    }

    private String sliderAt(float x, float y) {
        if (showCrosshair && inside(x, y, crosshairCardX, crosshairCardY, 225.0F, crosshairCardHeight())) {
            if (sliderHit(x, y, crosshairCardX + 20.0F, crosshairSizeSliderY(), 170.0F)) return "crosshair_size";
            if (sliderHit(x, y, crosshairCardX + 20.0F, crosshairGapSliderY(), 170.0F)) return "crosshair_gap";
            if (sliderHit(x, y, crosshairCardX + 20.0F, crosshairThicknessSliderY(), 170.0F)) return "crosshair_thickness";
            if (sliderHit(x, y, crosshairCardX + 20.0F, crosshairOpacitySliderY(), 170.0F)) return "crosshair_opacity";
            if (crosshairColorOpen && sliderHit(x, y, crosshairCardX + 15.0F, crosshairPaletteY() + 84.0F, 166.0F)) return "crosshair_hue";
            return null;
        }
        if (showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX, hitboxCustomizerCardY, 225.0F, hitboxCustomizerCardHeight())) {
            if (sliderHit(x, y, hitboxCustomizerCardX + 20.0F, hitboxCustomizerSliderY(), 170.0F)) return "hitbox_line_thickness";
            if (FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled()
                    && sliderHit(x, y, hitboxCustomizerCardX + 20.0F, hitboxCustomizerFillAlphaY(), 170.0F)) return "hitbox_fill_alpha";
            if (FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled() && hitboxFillPaletteOpen
                    && sliderHit(x, y, hitboxCustomizerCardX + 15.0F, hitboxFillPaletteY() + 84.0F, 166.0F)) return "hitbox_fill_hue";
            if (hitboxOutlinePaletteOpen && sliderHit(x, y, hitboxCustomizerCardX + 15.0F, hitboxCustomizerOutlinePaletteY() + 84.0F, 166.0F)) return "hitbox_outline_hue";
            return null;
        }
        if (showBlockOverlay && inside(x, y, blockOverlayCardX, blockOverlayCardY, 225.0F, blockOverlayCardHeight())) {
            if (sliderHit(x, y, blockOverlayCardX + 20.0F, blockOverlayThicknessY(), 170.0F)) return "block_overlay_line_thickness";
            if (blockOverlayShowsAnimationSpeed()
                    && sliderHit(x, y, blockOverlayCardX + 20.0F, blockOverlayAnimationSpeedY(), 170.0F)) return "block_overlay_animation_speed";
            if (FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isFillEnabled()
                    && sliderHit(x, y, blockOverlayCardX + 20.0F, blockOverlayFillAlphaY(), 170.0F)) return "block_overlay_fill_alpha";
            if (blockOverlayFillPaletteOpen && sliderHit(x, y, blockOverlayCardX + 15.0F, blockOverlayFillPaletteY() + 84.0F, 166.0F)) return "block_overlay_fill_hue";
            if (blockOverlayOutlinePaletteOpen && sliderHit(x, y, blockOverlayCardX + 15.0F, blockOverlayOutlinePaletteY() + 84.0F, 166.0F)) return "block_overlay_outline_hue";
            return null;
        }
        if (showHitColor && inside(x, y, hitColorCardX, hitColorCardY, 225.0F, hitColorCardHeight())) {
            if (sliderHit(x, y, hitColorCardX + 20.0F, hitColorAlphaSliderY(), 170.0F)) return "hit_color_alpha";
            if (hitColorPaletteOpen && sliderHit(x, y, hitColorCardX + 15.0F, hitColorPaletteY() + 84.0F, 166.0F)) return "hit_color_hue";
            return null;
        }
        if (showWorldCustomizer && inside(x, y, worldCustomizerCardX, worldCustomizerCardY, 225.0F, worldCustomizerCardHeight())) {
            WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
            float baseY = worldCustomizerSliderBaseY();
            if (worldCustomizer.getTimePreset() == WorldCustomizer.TimePreset.CUSTOM
                    && sliderHit(x, y, worldCustomizerCardX + 20.0F, baseY, 170.0F)) return "world_custom_time";
            if (worldCustomizer.isCustomFogEnabled()
                    && sliderHit(x, y, worldCustomizerCardX + 20.0F, worldCustomizerFogDistanceY(), 170.0F)) return "world_fog_distance";
            if (worldCustomizer.isCustomFogEnabled() && worldFogPaletteOpen
                    && sliderHit(x, y, worldCustomizerCardX + 15.0F, worldFogPaletteY() + 84.0F, 166.0F)) return "world_fog_hue";
            return null;
        }
        if (showParticles && inside(x, y, particlesCardX, particlesCardY, 225.0F, particlesCardHeight())) {
            float baseY = particleSliderBaseY();
            if (sliderHit(x, y, particlesCardX + 20.0F, baseY, 170.0F)) return "particles_amount";
            if (sliderHit(x, y, particlesCardX + 20.0F, baseY + 52.0F, 170.0F)) return "particles_life";
            if (sliderHit(x, y, particlesCardX + 20.0F, baseY + 104.0F, 170.0F)) return "particles_size";
            if (particlesColorOpen && sliderHit(x, y, particlesCardX + 15.0F, particlesColorY() + 147.0F, 166.0F)) return "particles_hue";
            return null;
        }
        if (showJumpCircles && inside(x, y, jumpCirclesCardX, jumpCirclesCardY, 225.0F, jumpCirclesCardHeight())) {
            JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
            if (sliderHit(x, y, jumpCirclesCardX + 20.0F, jumpCircleSizeY(), 170.0F)) return "jump_circles_size";
            if (jumpCircles.isParticlesEnabled()) {
                if (sliderHit(x, y, jumpCirclesCardX + 20.0F, jumpCircleAmountY(), 170.0F)) return "jump_circles_amount";
                if (sliderHit(x, y, jumpCirclesCardX + 20.0F, jumpCircleLifeY(), 170.0F)) return "jump_circles_life";
                if (sliderHit(x, y, jumpCirclesCardX + 20.0F, jumpCircleParticleSizeY(), 170.0F)) return "jump_circles_particle_size";
                if (sliderHit(x, y, jumpCirclesCardX + 20.0F, jumpCircleSpreadY(), 170.0F)) return "jump_circles_spread";
            }
            if (jumpCircleColorOpen && sliderHit(x, y, jumpCirclesCardX + 15.0F, jumpCirclesPaletteY() + 84.0F, 166.0F)) return "jump_circles_hue";
            return null;
        }
        if (showTrails && inside(x, y, trailsCardX, trailsCardY, 225.0F, trailsCardHeight())) {
            if (sliderHit(x, y, trailsCardX + 20.0F, trailsMaxLengthY(), 170.0F)) return "trails_max_length";
            if (sliderHit(x, y, trailsCardX + 20.0F, trailsAlphaY(), 170.0F)) return "trails_alpha";
            if (trailsColorOpen && sliderHit(x, y, trailsCardX + 15.0F, trailsPaletteY() + 84.0F, 166.0F)) return "trails_hue";
            return null;
        }
        if (showZoom && inside(x, y, zoomCardX, zoomCardY, 225.0F, zoomCardHeight())) {
            if (sliderHit(x, y, zoomCardX + 20.0F, zoomSmoothnessY(), 170.0F)) return "zoom_smoothness";
            return null;
        }
        if (showItemResorter && inside(x, y, itemResorterCardX, itemResorterCardY, ITEM_RESORTER_CARD_W, itemResorterCardHeight())) {
            if (FluxVisualsClient.MODULE_MANAGER.getItemResorter().isDurabilityFilterEnabled()
                    && sliderHit(x, y, itemResorterCardX + 83.0F, itemResorterDurabilityRowY() + 42.0F, 150.0F)) return "item_resorter_durability";
            return null;
        }
        if (showTargetHud && inside(x, y, targetHudCardX, targetHudCardY, 225.0F, targetHudCardHeight())) {
            if (sliderHit(x, y, targetHudCardX + 20.0F, targetHudCardY + 82.0F, 170.0F)) return "targethud_scale";
            return null;
        }
        if (showTapeMouse && inside(x, y, tapeMouseCardX, tapeMouseCardY, 225.0F, tapeMouseCardHeight())) {
            if (sliderHit(x, y, tapeMouseCardX + 20.0F, tapeMouseDelaySliderY(), 170.0F)) return "tapemouse_delay";
            return null;
        }
        if (showAnarchySwitcher && inside(x, y, anarchySwitcherCardX, anarchySwitcherCardY, 245.0F, anarchySwitcherCardHeight())) {
            if (sliderHit(x, y, anarchySwitcherCardX + 20.0F, anarchySwitcherCardY + 82.0F, 170.0F)) return "anarchy_cooldown";
            return null;
        }
        if (showAutoResellAFK && inside(x, y, autoResellAfkCardX, autoResellAfkCardY, 260.0F, 330.0F)) {
            if (FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().isChatEnabled()
                    && inside(x, y, autoResellAfkCardX + 13.0F, autoResellAfkCardY + 118.0F, 234.0F, 48.0F)) {
                return "auto_resell_afk_chat_interval";
            }
            return null;
        }
        if (showFreeLook && inside(x, y, freeLookCardX, freeLookCardY, 225.0F, freeLookCardHeight())) {
            if (sliderHit(x, y, freeLookCardX + 20.0F, freeLookDistanceY(), 170.0F)) return "freelook_distance";
            return null;
        }
        if (showTabCustomizer && inside(x, y, tabCustomizerCardX, tabCustomizerCardY, 225.0F, tabCustomizerCardHeight())) {
            float baseY = tabCustomizerContentY();
            if (sliderHit(x, y, tabCustomizerCardX + 20.0F, baseY, 170.0F)) return "tab_customizer_columns";
            if (sliderHit(x, y, tabCustomizerCardX + 20.0F, baseY + 52.0F, 170.0F)) return "tab_customizer_players";
            if (sliderHit(x, y, tabCustomizerCardX + 20.0F, baseY + 104.0F, 170.0F)) return "tab_customizer_scale";
            return null;
        }
        if (showRemovals && inside(x, y, removalsCardX, removalsCardY, 225.0F, removalsCardHeight())) {
            return null;
        }
        if (showAspectRatio && inside(x, y, aspectCardX, aspectCardY, 205.0F, aspectCardHeight())) {
            if (aspectPresetProgress < 0.2F && FluxVisualsClient.MODULE_MANAGER.getAspectRatio().getPreset() == AspectRatio.Preset.CUSTOM
                    && sliderHit(x, y, aspectCardX + 17.0F, aspectContentY() + 56.0F, 158.0F)) return "aspect_custom";
            return null;
        }
        if (showWings && inside(x, y, wingsCardX, wingsCardY, 205.0F, 388.0F)) {
            if (sliderHit(x, y, wingsCardX + 20.0F, wingsCardY + 254.0F, 170.0F)) return "wings_scale";
            if (sliderHit(x, y, wingsCardX + 20.0F, wingsCardY + 300.0F, 170.0F)) return "wings_strength";
            if (sliderHit(x, y, wingsCardX + 20.0F, wingsCardY + 346.0F, 170.0F)) return "wings_speed";
            return null;
        }
        if (showChinaHat && inside(x, y, chinaCardX, chinaCardY, 205.0F, chinaCardHeight())) {
            if (sliderHit(x, y, chinaCardX + 14.0F, chinaCardY + 74.0F, 138.0F)) return "china_size";
            if (FluxVisualsClient.MODULE_MANAGER.getChinaHat().getFillMode() == ChinaHat.FillMode.SHADER
                    && sliderHit(x, y, chinaCardX + 17.0F, chinaShaderAlphaY(), 158.0F)) return "china_shader_alpha";
            if (chinaPaletteOpen && sliderHit(x, y, chinaCardX + 15.0F, chinaPaletteY() + 84.0F, 166.0F)) return "china_hue";
            return null;
        }
        if (showTargetEsp && inside(x, y, targetCardX, targetCardY, 225.0F, targetEspCardHeight())) {
            TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
            boolean ghosts = targetEsp.getStyle() == TargetEsp.Style.GHOSTS;
            boolean crystals = targetEsp.getStyle() == TargetEsp.Style.CRYSTALS;
            if (sliderHit(x, y, targetCardX + 20.0F, targetDistanceY(), 170.0F)) return "target_distance";
            if (sliderHit(x, y, targetCardX + 20.0F, targetLostDelayY(), 170.0F)) return "target_lost_delay";
            if (crystals && sliderHit(x, y, targetCardX + 20.0F, targetCrystalSpeedY(), 170.0F)) return "target_crystal_speed";
            if (crystals && sliderHit(x, y, targetCardX + 20.0F, targetCrystalCountY(), 170.0F)) return "target_crystal_count";
            if (crystals && sliderHit(x, y, targetCardX + 20.0F, targetCrystalSizeY(), 170.0F)) return "target_crystal_size";
            if (crystals && sliderHit(x, y, targetCardX + 20.0F, targetCrystalRadiusY(), 170.0F)) return "target_crystal_radius";
            if (!ghosts && !crystals && sliderHit(x, y, targetCardX + 20.0F, targetSpeedY(), 170.0F)) return "target_speed";
            if (ghosts && sliderHit(x, y, targetCardX + 20.0F, targetSizeY(), 170.0F)) return "target_ghost_size";
            if (!ghosts && !crystals && sliderHit(x, y, targetCardX + 20.0F, targetSizeY(), 170.0F)) return "target_size";
            if (ghosts && sliderHit(x, y, targetCardX + 20.0F, targetGhostCountY(), 170.0F)) return "target_ghost_count";
            if (ghosts && sliderHit(x, y, targetCardX + 20.0F, targetGhostLengthY(), 170.0F)) return "target_ghost_length";
            if (ghosts && sliderHit(x, y, targetCardX + 20.0F, targetGhostSpeedY(), 170.0F)) return "target_ghost_speed";
            if (targetColorOpen && sliderHit(x, y, targetCardX + 15.0F, targetPaletteY() + 84.0F, 166.0F)) return "target_hue";
            return null;
        }
        return null;
    }

    private static boolean sliderHit(float px, float py, float x, float y, float w) {
        return inside(px, py, x - 7.0F, y - 31.0F, w + 14.0F, 55.0F);
    }

    private boolean closeSettingsCardAt(float x, float y) {
        if (showTargetEsp && cardCloseButtonAt(x, y, targetCardX, targetCardY)) {
            showTargetEsp = false;
            rememberedTargetEspCard = false;
        } else if (showChinaHat && cardCloseButtonAt(x, y, chinaCardX, chinaCardY)) {
            showChinaHat = false;
            rememberedChinaHatCard = false;
        } else if (showWings && cardCloseButtonAt(x, y, wingsCardX, wingsCardY)) {
            showWings = false;
            rememberedWingsCard = false;
        } else if (showAspectRatio && cardCloseButtonAt(x, y, aspectCardX, aspectCardY)) {
            showAspectRatio = false;
            rememberedAspectRatioCard = false;
        } else if (showRemovals && cardCloseButtonAt(x, y, removalsCardX, removalsCardY)) {
            showRemovals = false;
            rememberedRemovalsCard = false;
        } else if (showParticles && cardCloseButtonAt(x, y, particlesCardX, particlesCardY)) {
            showParticles = false;
            rememberedParticlesCard = false;
        } else if (showJumpCircles && cardCloseButtonAt(x, y, jumpCirclesCardX, jumpCirclesCardY)) {
            showJumpCircles = false;
            rememberedJumpCirclesCard = false;
        } else if (showTrails && cardCloseButtonAt(x, y, trailsCardX, trailsCardY)) {
            showTrails = false;
            rememberedTrailsCard = false;
        } else if (showWorldCustomizer && cardCloseButtonAt(x, y, worldCustomizerCardX, worldCustomizerCardY)) {
            showWorldCustomizer = false;
            rememberedWorldCustomizerCard = false;
        } else if (showHitColor && cardCloseButtonAt(x, y, hitColorCardX, hitColorCardY)) {
            showHitColor = false;
            rememberedHitColorCard = false;
        } else if (showHitboxCustomizer && cardCloseButtonAt(x, y, hitboxCustomizerCardX, hitboxCustomizerCardY)) {
            showHitboxCustomizer = false;
            rememberedHitboxCustomizerCard = false;
        } else if (showBlockOverlay && cardCloseButtonAt(x, y, blockOverlayCardX, blockOverlayCardY)) {
            showBlockOverlay = false;
            rememberedBlockOverlayCard = false;
        } else if (showItemRadius && cardCloseButtonAt(x, y, itemRadiusCardX, itemRadiusCardY)) {
            showItemRadius = false;
            rememberedItemRadiusCard = false;
        } else if (showAnimations && cardCloseButtonAt(x, y, animationsCardX, animationsCardY)) {
            showAnimations = false;
            rememberedAnimationsCard = false;
        } else if (showTabCustomizer && cardCloseButtonAt(x, y, tabCustomizerCardX, tabCustomizerCardY)) {
            showTabCustomizer = false;
            rememberedTabCustomizerCard = false;
        } else if (showFreeLook && cardCloseButtonAt(x, y, freeLookCardX, freeLookCardY)) {
            showFreeLook = false;
            rememberedFreeLookCard = false;
        } else if (showZoom && cardCloseButtonAt(x, y, zoomCardX, zoomCardY)) {
            showZoom = false;
            rememberedZoomCard = false;
        } else if (showAutoSwap && cardCloseButtonAt(x, y, autoSwapCardX, autoSwapCardY)) {
            showAutoSwap = false;
            rememberedAutoSwapCard = false;
        } else if (showElytraSwap && cardCloseButtonAt(x, y, elytraSwapCardX, elytraSwapCardY)) {
            showElytraSwap = false;
            rememberedElytraSwapCard = false;
        } else if (showItemResorter && cardCloseButtonAt(x, y, itemResorterCardX, itemResorterCardY)) {
            showItemResorter = false;
            rememberedItemResorterCard = false;
        } else if (showAutoBuy && cardCloseButtonAt(x, y, autoBuyCardX, autoBuyCardY)) {
            showAutoBuy = false;
            rememberedAutoBuyCard = false;
        } else if (showAutoResellAFK && cardCloseButtonAt(x, y, autoResellAfkCardX, autoResellAfkCardY)) {
            showAutoResellAFK = false;
        } else if (showTelegram && cardCloseButtonAt(x, y, telegramCardX, telegramCardY)) {
            showTelegram = false;
            rememberedTelegramCard = false;
        } else if (showTargetHud && cardCloseButtonAt(x, y, targetHudCardX, targetHudCardY)) {
            showTargetHud = false;
            rememberedTargetHudCard = false;
        } else if (showTapeMouse && cardCloseButtonAt(x, y, tapeMouseCardX, tapeMouseCardY)) {
            showTapeMouse = false;
            rememberedTapeMouseCard = false;
        } else if (showAnarchySwitcher && cardCloseButtonAt(x, y, anarchySwitcherCardX, anarchySwitcherCardY)) {
            showAnarchySwitcher = false;
            rememberedAnarchySwitcherCard = false;
        } else if (showNameBinder && cardCloseButtonAt(x, y, nameBinderCardX, nameBinderCardY)) {
            showNameBinder = false;
            rememberedNameBinderCard = false;
        } else if (showMacros && cardCloseButtonAt(x, y, macrosCardX, macrosCardY)) {
            showMacros = false;
            rememberedMacrosCard = false;
        } else if (showCrosshair && cardCloseButtonAt(x, y, crosshairCardX, crosshairCardY)) {
            showCrosshair = false;
            rememberedCrosshairCard = false;
        } else {
            return false;
        }

        closeCardControls();
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    private static boolean cardCloseButtonAt(float x, float y, float cardX, float cardY) {
        return inside(x, y, cardX + 9.0F, cardY + 8.0F, 18.0F, 18.0F);
    }

    private void closeCardControls() {
        draggingSlider = null;
        draggingCard = null;
        targetStyleOpen = false;
        targetFilterOpen = false;
        targetColorOpen = false;
        chinaPaletteOpen = false;
        chinaFillModeOpen = false;
        chinaShaderOpen = false;
        aspectPresetOpen = false;
        worldTimePresetOpen = false;
        worldFogPaletteOpen = false;
        particleTextureOpen = false;
        particleMultiOpen = false;
        particlesColorOpen = false;
        jumpCircleModeOpen = false;
        jumpCircleTextureOpen = false;
        jumpCircleParticleTextureOpen = false;
        jumpCircleColorOpen = false;
        trailsColorOpen = false;
        hitColorPaletteOpen = false;
        hitboxOutlinePaletteOpen = false;
        hitboxFillPaletteOpen = false;
        hitboxTargetsOpen = false;
        blockOverlayOutlinePaletteOpen = false;
        blockOverlayFillPaletteOpen = false;
        blockOverlayFillModeOpen = false;
        blockOverlayShaderOpen = false;
        crosshairPresetOpen = false;
        crosshairColorOpen = false;
        freeLookModeOpen = false;
        autoSwapFirstOpen = false;
        autoSwapSecondOpen = false;
        itemResorterEnchantColorOpen = false;
        itemResorterBuffColorOpen = false;
        tapeMouseButtonOpen = false;
        itemResorterFocusedInput = null;
        itemResorterPriceFocused = false;
        autoBuyNameFocused = false;
        anarchyInputFocused = false;
        anarchyAdFocused = false;
        autoBuyAnarchyInputFocused = false;
        autoBuyAnarchyAdFocused = false;
        autoBuySellerBanFocused = false;
        telegramTokenFocused = false;
        telegramChatFocused = false;
        nameBinderInputFocused = false;
        macrosInputFocused = false;
    }

    private String draggableCardAt(float x, float y) {
        if (showTargetEsp && inside(x, y, targetCardX, targetCardY, 225.0F, 37.0F)) return "target";
        if (showChinaHat && inside(x, y, chinaCardX, chinaCardY, 205.0F, 37.0F)) return "china";
        if (showWings && inside(x, y, wingsCardX, wingsCardY, 205.0F, 37.0F)) return "wings";
        if (showAspectRatio && inside(x, y, aspectCardX, aspectCardY, 205.0F, 37.0F)) return "aspect";
        if (showRemovals && inside(x, y, removalsCardX, removalsCardY, 225.0F, 37.0F)) return "removals";
        if (showParticles && inside(x, y, particlesCardX, particlesCardY, 225.0F, 37.0F)) return "particles";
        if (showJumpCircles && inside(x, y, jumpCirclesCardX, jumpCirclesCardY, 225.0F, 37.0F)) return "jump_circles";
        if (showTrails && inside(x, y, trailsCardX, trailsCardY, 225.0F, 37.0F)) return "trails";
        if (showWorldCustomizer && inside(x, y, worldCustomizerCardX, worldCustomizerCardY, 225.0F, 37.0F)) return "world_customizer";
        if (showHitColor && inside(x, y, hitColorCardX, hitColorCardY, 225.0F, 37.0F)) return "hit_color";
        if (showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX, hitboxCustomizerCardY, 225.0F, 37.0F)) return "hitbox_customizer";
        if (showBlockOverlay && inside(x, y, blockOverlayCardX, blockOverlayCardY, 225.0F, 37.0F)) return "block_overlay";
        if (showItemRadius && inside(x, y, itemRadiusCardX, itemRadiusCardY, 225.0F, 37.0F)) return "item_radius";
        if (showAnimations && inside(x, y, animationsCardX, animationsCardY, 225.0F, 37.0F)) return "animations";
        if (showTabCustomizer && inside(x, y, tabCustomizerCardX, tabCustomizerCardY, 225.0F, 37.0F)) return "tab_customizer";
        if (showFreeLook && inside(x, y, freeLookCardX, freeLookCardY, 225.0F, 37.0F)) return "freelook";
        if (showZoom && inside(x, y, zoomCardX, zoomCardY, 225.0F, 37.0F)) return "zoom";
        if (showAutoSwap && inside(x, y, autoSwapCardX, autoSwapCardY, 245.0F, 37.0F)) return "autoswap";
        if (showElytraSwap && inside(x, y, elytraSwapCardX, elytraSwapCardY, 245.0F, 37.0F)) return "elytra_swap";
        if (showItemResorter && inside(x, y, itemResorterCardX, itemResorterCardY, ITEM_RESORTER_CARD_W, 37.0F)) return "item_resorter";
        if (showAutoBuy && inside(x, y, autoBuyCardX, autoBuyCardY, 245.0F, 37.0F)) return "auto_buy";
        if (showAutoResellAFK && inside(x, y, autoResellAfkCardX, autoResellAfkCardY, 260.0F, 37.0F)) return "auto_resell_afk";
        if (showTelegram && inside(x, y, telegramCardX, telegramCardY, 245.0F, 37.0F)) return "telegram";
        if (showTargetHud && inside(x, y, targetHudCardX, targetHudCardY, 225.0F, 37.0F)) return "target_hud";
        if (showTapeMouse && inside(x, y, tapeMouseCardX, tapeMouseCardY, 225.0F, 37.0F)) return "tape_mouse";
        if (showAnarchySwitcher && inside(x, y, anarchySwitcherCardX, anarchySwitcherCardY, 245.0F, 37.0F)) return "anarchy_switcher";
        if (showNameBinder && inside(x, y, nameBinderCardX, nameBinderCardY, 270.0F, 37.0F)) return "name_binder";
        if (showMacros && inside(x, y, macrosCardX, macrosCardY, 270.0F, 37.0F)) return "macros";
        if (showCrosshair && inside(x, y, crosshairCardX, crosshairCardY, 225.0F, 37.0F)) return "crosshair";
        return null;
    }

    private float targetContentY() {
        return targetCardY + 48.0F;
    }

    private float targetStyleListHeight() {
        return TargetEsp.Style.values().length * 22.0F + 6.0F;
    }

    private float targetFilterListHeight() {
        return TargetEsp.TargetFilter.values().length * 22.0F + 6.0F;
    }

    private float targetFilterY() {
        return targetContentY() + 34.0F + targetStyleProgress * targetStyleListHeight();
    }

    private float targetDistanceY() {
        return targetFilterY() + 56.0F + targetFilterProgress * targetFilterListHeight();
    }

    private float targetLostDelayY() {
        return targetDistanceY() + 52.0F;
    }

    private float targetSpeedY() {
        return targetLostDelayY() + 52.0F;
    }

    private float targetCrystalSpeedY() {
        return targetLostDelayY() + 52.0F;
    }

    private float targetCrystalCountY() {
        return targetCrystalSpeedY() + 52.0F;
    }

    private float targetCrystalSizeY() {
        return targetCrystalCountY() + 52.0F;
    }

    private float targetCrystalRadiusY() {
        return targetCrystalSizeY() + 52.0F;
    }

    private float targetSizeY() {
        return FluxVisualsClient.MODULE_MANAGER.getTargetEsp().getStyle() == TargetEsp.Style.GHOSTS
                ? targetLostDelayY() + 52.0F
                : targetSpeedY() + 52.0F;
    }

    private float targetGhostCountY() {
        return targetSizeY() + 52.0F;
    }

    private float targetGhostLengthY() {
        return targetGhostCountY() + 52.0F;
    }

    private float targetGhostSpeedY() {
        return targetGhostLengthY() + 52.0F;
    }

    private float targetRedOnHitY() {
        TargetEsp.Style style = FluxVisualsClient.MODULE_MANAGER.getTargetEsp().getStyle();
        if (style == TargetEsp.Style.CRYSTALS) {
            return targetCrystalRadiusY() + 40.0F;
        }
        return style == TargetEsp.Style.GHOSTS ? targetGhostSpeedY() + 40.0F : targetSizeY() + 40.0F;
    }

    private float targetColorY() {
        return targetRedOnHitY() + 38.0F;
    }

    private float targetPaletteY() {
        return targetColorY() + 43.0F;
    }

    private float targetEspCardHeight() {
        float bottom = targetColorY() + 28.0F;
        if (targetColorOpen) {
            bottom = Math.max(bottom, targetPaletteY() + 110.0F);
        }
        return bottom - targetCardY + 18.0F;
    }

    private boolean targetStyleBoxAt(float x, float y) {
        return showTargetEsp && inside(x, y, targetCardX + 112.0F, targetContentY() - 1.0F, 100.0F, 24.0F);
    }

    private TargetEsp.Style targetStyleAt(float x, float y) {
        if (!showTargetEsp || targetStyleProgress <= 0.12F) {
            return null;
        }
        float listX = targetCardX + 112.0F;
        float listY = targetContentY() + 28.0F;
        TargetEsp.Style[] values = TargetEsp.Style.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private boolean targetFilterBoxAt(float x, float y) {
        return showTargetEsp && inside(x, y, targetCardX + 112.0F, targetFilterY() - 1.0F, 100.0F, 24.0F);
    }

    private TargetEsp.TargetFilter targetFilterAt(float x, float y) {
        if (!showTargetEsp || targetFilterProgress <= 0.12F) {
            return null;
        }
        float listX = targetCardX + 112.0F;
        float listY = targetFilterY() + 28.0F;
        TargetEsp.TargetFilter[] values = TargetEsp.TargetFilter.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private boolean targetRedOnHitToggleAt(float x, float y) {
        return showTargetEsp && inside(x, y, targetCardX + 188.0F, targetRedOnHitY(), 24.0F, 24.0F);
    }

    private boolean targetColorBoxAt(float x, float y) {
        return showTargetEsp && inside(x, y, targetCardX + 126.0F, targetColorY(), 58.0F, 28.0F);
    }

    private boolean targetPaletteAt(float x, float y) {
        return showTargetEsp && inside(x, y, targetCardX + 15.0F, targetPaletteY(), 166.0F, 72.0F);
    }

    private boolean aspectPresetBoxAt(float x, float y) {
        return showAspectRatio && inside(x, y, aspectCardX + 97.0F, aspectContentY() + 1.0F, 89.0F, 21.0F);
    }

    private AspectRatio.Preset aspectPresetAt(float x, float y) {
        if (!aspectPresetListAt(x, y)) {
            return null;
        }

        float listX = aspectCardX + 97.0F;
        float listY = aspectContentY() + 28.0F;
        AspectRatio.Preset[] presets = AspectRatio.Preset.values();
        for (int i = 0; i < presets.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F - aspectPresetScroll, 89.0F, 22.0F)) {
                return presets[i];
            }
        }
        return null;
    }

    private float aspectContentY() {
        return aspectCardY + 47.0F;
    }

    private boolean aspectPresetListAt(float x, float y) {
        return showAspectRatio && aspectPresetProgress > 0.12F
                && inside(x, y, aspectCardX + 97.0F, aspectContentY() + 28.0F, 89.0F, aspectPresetListHeight() * aspectPresetProgress);
    }

    private float aspectPresetListHeight() {
        return 75.0F;
    }

    private float maxAspectPresetScroll() {
        return Math.max(0.0F, AspectRatio.Preset.values().length * 22.0F + 6.0F - aspectPresetListHeight());
    }

    private float aspectCardHeight() {
        return 116.0F + 38.0F * aspectCustomProgress;
    }

    private boolean worldTimePresetBoxAt(float x, float y) {
        return showWorldCustomizer && inside(x, y, worldCustomizerCardX + 104.0F, worldCustomizerContentY() + 1.0F, 108.0F, 21.0F);
    }

    private WorldCustomizer.TimePreset worldTimePresetAt(float x, float y) {
        if (!worldTimePresetListAt(x, y)) {
            return null;
        }

        float listX = worldCustomizerCardX + 104.0F;
        float listY = worldCustomizerContentY() + 28.0F;
        WorldCustomizer.TimePreset[] presets = WorldCustomizer.TimePreset.values();
        for (int i = 0; i < presets.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 108.0F, 22.0F)) {
                return presets[i];
            }
        }
        return null;
    }

    private boolean worldTimePresetListAt(float x, float y) {
        return showWorldCustomizer && worldTimePresetProgress > 0.12F
                && inside(x, y, worldCustomizerCardX + 104.0F, worldCustomizerContentY() + 28.0F, 108.0F,
                worldTimePresetListHeight() * worldTimePresetProgress);
    }

    private float worldTimePresetListHeight() {
        return WorldCustomizer.TimePreset.values().length * 22.0F + 6.0F;
    }

    private float worldCustomizerContentY() {
        return worldCustomizerCardY + 47.0F;
    }

    private float worldCustomizerSliderBaseY() {
        return worldCustomizerContentY() + 43.0F + worldTimePresetProgress * worldTimePresetListHeight();
    }

    private float worldCustomizerFogToggleY() {
        float y = worldCustomizerSliderBaseY() + 12.0F;
        if (FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().getTimePreset() == WorldCustomizer.TimePreset.CUSTOM) {
            y += 52.0F;
        }
        return y;
    }

    private float worldCustomizerFogDistanceY() {
        return worldCustomizerFogToggleY() + 52.0F;
    }

    private float worldFogColorY() {
        return worldCustomizerFogDistanceY() + 42.0F;
    }

    private float worldFogPaletteY() {
        return worldFogColorY() + 63.0F;
    }

    private float worldCustomizerCardHeight() {
        float bottom = worldFogColorY() + 56.0F;
        if (FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().isCustomFogEnabled() && worldFogPaletteOpen) {
            bottom = Math.max(bottom, worldFogPaletteY() + 110.0F);
        }
        return bottom - worldCustomizerCardY + 18.0F;
    }

    private boolean particleTextureBoxAt(float x, float y) {
        return showParticles && inside(x, y, particlesCardX + 112.0F, particlesContentY() - 1.0F, 94.0F, 24.0F);
    }

    private boolean particleMultiBoxAt(float x, float y) {
        float rowY = particlesContentY() + 35.0F + particleTextureProgress * 133.0F;
        return showParticles && inside(x, y, particlesCardX + 100.0F, rowY - 1.0F, 112.0F, 24.0F);
    }

    private Particles.TextureType particleTextureAt(float x, float y) {
        if (!showParticles || particleTextureProgress <= 0.12F) {
            return null;
        }

        float listX = particlesCardX + 112.0F;
        float listY = particlesContentY() + 28.0F;
        Particles.TextureType[] values = Particles.TextureType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 94.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private Particles.SpawnMode particleMultiTextureAt(float x, float y) {
        if (!showParticles || particleMultiProgress <= 0.12F) {
            return null;
        }

        float listX = particlesCardX + 100.0F;
        float listY = particlesContentY() + 63.0F + particleTextureProgress * 133.0F;
        Particles.SpawnMode[] values = Particles.SpawnMode.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 112.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private float particleSliderBaseY() {
        return particlesContentY() + 128.0F + particleTextureProgress * 133.0F + particleMultiProgress * particleMultiOpenHeight();
    }

    private float particlesColorY() {
        return particleSliderBaseY() + 144.0F;
    }

    private float trailsContentY() {
        return trailsCardY + 48.0F;
    }

    private float trailsMaxLengthY() {
        return trailsContentY() + 35.0F;
    }

    private float trailsColorY() {
        return trailsAlphaY() + 42.0F;
    }

    private float trailsAlphaY() {
        return trailsMaxLengthY() + 52.0F;
    }

    private float trailsPaletteY() {
        return trailsColorY() + 43.0F;
    }

    private float trailsCardHeight() {
        float bottom = trailsColorY() + 28.0F;
        if (trailsColorOpen) {
            bottom = Math.max(bottom, trailsPaletteY() + 110.0F);
        }
        return bottom - trailsCardY + 18.0F;
    }

    private float hitColorAlphaSliderY() {
        return hitColorCardY + 114.0F;
    }

    private float hitColorColorY() {
        return hitColorAlphaSliderY() + 42.0F;
    }

    private float hitColorPaletteY() {
        return hitColorColorY() + 43.0F;
    }

    private float hitColorCardHeight() {
        float bottom = hitColorColorY() + 28.0F;
        if (hitColorPaletteOpen) {
            bottom = Math.max(bottom, hitColorPaletteY() + 110.0F);
        }
        return bottom - hitColorCardY + 18.0F;
    }

    private float clickGuiContentY() {
        return clickGuiCardY + 48.0F;
    }

    private float clickGuiSizeSliderY() {
        return clickGuiContentY() + 75.0F;
    }

    private float clickGuiCardHeight() {
        return 158.0F;
    }

    private float freeLookContentY() {
        return freeLookCardY + 48.0F;
    }

    private float freeLookModeListY() {
        return freeLookContentY() + 68.0F;
    }

    private float freeLookModeListHeight() {
        return FreeLook.ActivationMode.values().length * 22.0F + 6.0F;
    }

    private float freeLookDistanceY() {
        return freeLookContentY() + 100.0F + freeLookModeProgress * freeLookModeListHeight();
    }

    private float freeLookCardHeight() {
        return 208.0F + freeLookModeProgress * freeLookModeListHeight();
    }

    private float itemRadiusContentY() {
        return itemRadiusCardY + 48.0F;
    }

    private float itemRadiusCardHeight() {
        return 350.0F;
    }

    private float animationsContentY() {
        return animationsCardY + 48.0F;
    }

    private float animationsCardHeight() {
        return 202.0F;
    }

    private float tabCustomizerContentY() {
        return tabCustomizerCardY + 76.0F;
    }

    private float tabCustomizerCardHeight() {
        return 226.0F;
    }

    private float zoomContentY() {
        return zoomCardY + 48.0F;
    }

    private float zoomSmoothnessY() {
        return zoomContentY() + 72.0F;
    }

    private float zoomWheelY() {
        return zoomSmoothnessY() + 42.0F;
    }

    private float zoomCardHeight() {
        return 194.0F;
    }

    private float autoSwapContentY() {
        return autoSwapCardY + 48.0F;
    }

    private float autoSwapItemListHeight() {
        return AutoSwap.SwapItem.values().length * 22.0F + 6.0F;
    }

    private float autoSwapSecondY() {
        return autoSwapContentY() + 40.0F + autoSwapFirstProgress * autoSwapItemListHeight();
    }

    private float autoSwapBindY() {
        return autoSwapSecondY() + 40.0F + autoSwapSecondProgress * autoSwapItemListHeight();
    }

    private float autoSwapCardHeight() {
        return autoSwapBindY() - autoSwapCardY + 74.0F;
    }

    private float elytraSwapContentY() {
        return elytraSwapCardY + 48.0F;
    }

    private float elytraSwapFireworkY() {
        return elytraSwapContentY() + 40.0F;
    }

    private float elytraSwapCardHeight() {
        return 152.0F;
    }

    private float crosshairContentY() {
        return crosshairCardY + 48.0F;
    }

    private float crosshairPresetListHeight() {
        return 143.0F;
    }

    private float crosshairSizeSliderY() {
        return crosshairContentY() + 150.0F + crosshairPresetProgress * crosshairPresetListHeight();
    }

    private float crosshairGapSliderY() {
        return crosshairSizeSliderY() + 52.0F;
    }

    private float crosshairThicknessSliderY() {
        return crosshairGapSliderY() + 52.0F;
    }

    private float crosshairOpacitySliderY() {
        return crosshairThicknessSliderY() + 52.0F;
    }

    private float crosshairDotToggleY() {
        return crosshairOpacitySliderY() + 40.0F;
    }

    private float crosshairOutlineToggleY() {
        return crosshairDotToggleY() + 36.0F;
    }

    private float crosshairRedToggleY() {
        return crosshairOutlineToggleY() + 36.0F;
    }

    private float crosshairThirdPersonToggleY() {
        return crosshairRedToggleY() + 36.0F;
    }

    private float crosshairColorY() {
        return crosshairThirdPersonToggleY() + 38.0F;
    }

    private float crosshairPaletteY() {
        return crosshairColorY() + 43.0F;
    }

    private float crosshairCardHeight() {
        float bottom = crosshairColorY() + 28.0F;
        if (crosshairColorOpen) {
            bottom = Math.max(bottom, crosshairPaletteY() + 110.0F);
        }
        return bottom - crosshairCardY + 18.0F;
    }

    private float chinaFillModeY() {
        return chinaCardY + 105.0F;
    }

    private float chinaShaderY() {
        return chinaFillModeY() + 34.0F + chinaFillModeProgress * chinaFillModeListHeight();
    }

    private float chinaShaderAlphaY() {
        return chinaShaderY() + 62.0F + chinaShaderProgress * chinaShaderListHeight();
    }

    private float chinaColorY() {
        float y = chinaFillModeY() + 34.0F + chinaFillModeProgress * chinaFillModeListHeight();
        if (FluxVisualsClient.MODULE_MANAGER.getChinaHat().getFillMode() == ChinaHat.FillMode.SHADER) {
            y = chinaShaderAlphaY() + 40.0F;
        }
        return y;
    }

    private float chinaPaletteY() {
        return chinaColorY() + 43.0F;
    }

    private float chinaCardHeight() {
        float bottom = chinaColorY() + 40.0F + 60.0F + 26.0F;
        if (chinaPaletteOpen) {
            bottom = Math.max(bottom, chinaPaletteY() + 110.0F);
        }
        return bottom - chinaCardY + 18.0F;
    }

    private float chinaFillModeListHeight() {
        return ChinaHat.FillMode.values().length * 22.0F + 6.0F;
    }

    private float chinaShaderListHeight() {
        return BlockOverlay.ShaderType.values().length * 22.0F + 6.0F;
    }

    private boolean chinaFillModeBoxAt(float x, float y) {
        return showChinaHat && inside(x, y, chinaCardX + 88.0F, chinaFillModeY(), 100.0F, 24.0F);
    }

    private boolean chinaShaderBoxAt(float x, float y) {
        return showChinaHat
                && FluxVisualsClient.MODULE_MANAGER.getChinaHat().getFillMode() == ChinaHat.FillMode.SHADER
                && inside(x, y, chinaCardX + 88.0F, chinaShaderY(), 100.0F, 24.0F);
    }

    private boolean chinaColorBoxAt(float x, float y) {
        return showChinaHat && inside(x, y, chinaCardX + 126.0F, chinaColorY(), 58.0F, 28.0F);
    }

    private ChinaHat.FillMode chinaFillModeAt(float x, float y) {
        if (!showChinaHat || chinaFillModeProgress <= 0.12F) {
            return null;
        }
        float listY = chinaFillModeY() + 29.0F;
        ChinaHat.FillMode[] values = ChinaHat.FillMode.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, chinaCardX + 88.0F, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private BlockOverlay.ShaderType chinaShaderAt(float x, float y) {
        if (!showChinaHat || chinaShaderProgress <= 0.12F) {
            return null;
        }
        float listY = chinaShaderY() + 29.0F;
        BlockOverlay.ShaderType[] values = BlockOverlay.ShaderType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, chinaCardX + 88.0F, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private float removalsContentY() {
        return removalsCardY + 52.0F;
    }

    private float removalsCardHeight() {
        return 353.0F;
    }

    private float particlesCardHeight() {
        return 340.0F;
    }

    private float particlesContentY() {
        return particlesCardY + 48.0F - particlesScroll;
    }

    private float maxParticlesScroll() {
        return Math.max(0.0F, particlesContentHeight() - (particlesCardHeight() - 48.0F));
    }

    private float particlesContentHeight() {
        return 362.0F + particleTextureProgress * 133.0F + particleMultiProgress * particleMultiOpenHeight() + (particlesColorOpen ? 118.0F : 0.0F);
    }

    private float jumpCirclesContentY() {
        return jumpCirclesCardY + 48.0F;
    }

    private float jumpCircleModeListHeight() {
        return 2.0F * 22.0F + 6.0F;
    }

    private float jumpCircleTextureListHeight() {
        return JumpCircles.TextureType.values().length * 22.0F + 6.0F;
    }

    private float jumpCircleParticleTextureListHeight() {
        return Particles.TextureType.values().length * 22.0F + 6.0F;
    }

    private float jumpCircleAfterModeY() {
        return jumpCirclesContentY() + 36.0F + jumpCircleModeProgress * jumpCircleModeListHeight();
    }

    private float jumpCircleTextureY() {
        return jumpCircleAfterModeY();
    }

    private float jumpCircleAfterTextureY() {
        if (FluxVisualsClient.MODULE_MANAGER.getJumpCircles().getMode() == JumpCircles.Mode.BLOCKS) {
            return jumpCircleAfterModeY();
        }
        return jumpCircleTextureY() + 58.0F + jumpCircleTextureProgress * jumpCircleTextureListHeight();
    }

    private float jumpCircleParticlesY() {
        return jumpCircleAfterTextureY();
    }

    private float jumpCircleSizeY() {
        return jumpCircleParticlesY() + 42.0F;
    }

    private float jumpCircleParticleTextureY() {
        return jumpCircleSizeY() + 52.0F;
    }

    private float jumpCircleAmountY() {
        return jumpCircleParticleTextureY() + 58.0F + jumpCircleParticleTextureProgress * jumpCircleParticleTextureListHeight();
    }

    private float jumpCircleLifeY() {
        return jumpCircleAmountY() + 52.0F;
    }

    private float jumpCircleParticleSizeY() {
        return jumpCircleLifeY() + 52.0F;
    }

    private float jumpCircleSpreadY() {
        return jumpCircleParticleSizeY() + 52.0F;
    }

    private float jumpCircleColorY() {
        return FluxVisualsClient.MODULE_MANAGER.getJumpCircles().isParticlesEnabled()
                ? jumpCircleSpreadY() + 40.0F
                : jumpCircleSizeY() + 52.0F;
    }

    private float jumpCirclesPaletteY() {
        return jumpCircleColorY() + 43.0F;
    }

    private float jumpCirclesCardHeight() {
        float bottom = jumpCircleColorY() + 28.0F;
        if (jumpCircleColorOpen) {
            bottom = Math.max(bottom, jumpCirclesPaletteY() + 110.0F);
        }
        return bottom - jumpCirclesCardY + 18.0F;
    }

    private boolean jumpCircleModeBoxAt(float x, float y) {
        return showJumpCircles && inside(x, y, jumpCirclesCardX + 112.0F, jumpCirclesContentY() - 1.0F, 100.0F, 24.0F);
    }

    private boolean jumpCircleTextureBoxAt(float x, float y) {
        return showJumpCircles
                && FluxVisualsClient.MODULE_MANAGER.getJumpCircles().getMode() != JumpCircles.Mode.BLOCKS
                && inside(x, y, jumpCirclesCardX + 112.0F, jumpCircleTextureY() - 1.0F, 100.0F, 24.0F);
    }

    private boolean jumpCircleParticleTextureBoxAt(float x, float y) {
        return showJumpCircles
                && FluxVisualsClient.MODULE_MANAGER.getJumpCircles().isParticlesEnabled()
                && inside(x, y, jumpCirclesCardX + 112.0F, jumpCircleParticleTextureY() - 1.0F, 100.0F, 24.0F);
    }

    private boolean jumpCircleParticlesToggleAt(float x, float y) {
        return showJumpCircles && inside(x, y, jumpCirclesCardX + 188.0F, jumpCircleParticlesY(), 24.0F, 24.0F);
    }

    private boolean jumpCircleColorBoxAt(float x, float y) {
        return showJumpCircles && inside(x, y, jumpCirclesCardX + 126.0F, jumpCircleColorY(), 58.0F, 28.0F);
    }

    private boolean jumpCirclePaletteAt(float x, float y) {
        return showJumpCircles && inside(x, y, jumpCirclesCardX + 15.0F, jumpCirclesPaletteY(), 166.0F, 72.0F);
    }

    private JumpCircles.Mode jumpCircleModeAt(float x, float y) {
        if (!showJumpCircles || jumpCircleModeProgress <= 0.12F) {
            return null;
        }
        float listX = jumpCirclesCardX + 112.0F;
        float listY = jumpCirclesContentY() + 28.0F;
        JumpCircles.Mode[] values = {
                JumpCircles.Mode.NORMAL,
                JumpCircles.Mode.BLOCKS
        };
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private JumpCircles.TextureType jumpCircleTextureAt(float x, float y) {
        if (!showJumpCircles || jumpCircleTextureProgress <= 0.12F
                || FluxVisualsClient.MODULE_MANAGER.getJumpCircles().getMode() == JumpCircles.Mode.BLOCKS) {
            return null;
        }
        float listX = jumpCirclesCardX + 112.0F;
        float listY = jumpCircleTextureY() + 28.0F;
        JumpCircles.TextureType[] values = JumpCircles.TextureType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private Particles.TextureType jumpCircleParticleTextureAt(float x, float y) {
        if (!showJumpCircles || jumpCircleParticleTextureProgress <= 0.12F
                || !FluxVisualsClient.MODULE_MANAGER.getJumpCircles().isParticlesEnabled()) {
            return null;
        }
        float listX = jumpCirclesCardX + 112.0F;
        float listY = jumpCircleParticleTextureY() + 28.0F;
        Particles.TextureType[] values = Particles.TextureType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private boolean particleOutlineToggleAt(float x, float y) {
        float outlineY = particlesContentY() + 74.0F + particleTextureProgress * 133.0F + particleMultiProgress * particleMultiOpenHeight();
        return showParticles && inside(x, y, particlesCardX + 188.0F, outlineY, 24.0F, 24.0F);
    }

    private float particleMultiOpenHeight() {
        return Particles.SpawnMode.values().length * 22.0F + 7.0F;
    }

    private static String primaryEnabledModeLabel(Particles particles) {
        int count = 0;
        for (Particles.SpawnMode mode : Particles.SpawnMode.values()) {
            if (particles.isModeEnabled(mode)) {
                count++;
            }
        }
        return "\u0412\u044b\u0431\u0440\u0430\u043d\u043e " + Math.max(1, count);
    }

    private static String moduleHint(String name) {
        return switch (name) {
            case "FullBright" -> "\u0414\u0430\u0451\u0442 Night Vision";
            case "Particles" -> "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0447\u0430\u0441\u0442\u0438\u0446";
            case "JumpCircles" -> "\u042d\u0444\u0444\u0435\u043a\u0442\u044b \u043f\u0440\u044b\u0436\u043a\u0430";
            case "Trails" -> "\u0421\u043b\u0435\u0434 \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u043e\u043c";
            case "Removals" -> "\u0423\u0434\u0430\u043b\u044f\u0435\u0442 \u043b\u0438\u0448\u043d\u0438\u0435 \u044d\u0444\u0444\u0435\u043a\u0442\u044b";
            case "China Hat" -> "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u0430\u044f \u0448\u043b\u044f\u043f\u0430";
            case "Aspect Ratio" -> "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u0440\u043e\u043f\u043e\u0440\u0446\u0438\u0439";
            case "TargetEsp" -> "\u041f\u043e\u0434\u0441\u0432\u0435\u0442\u043a\u0430 \u0446\u0435\u043b\u0438";
            case "World Customizer" -> "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043c\u0438\u0440\u0430";
            case "Hit Color" -> "\u0426\u0432\u0435\u0442 \u0443\u0434\u0430\u0440\u0430";
            case "Hitbox Customizer" -> "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0435 \u0445\u0438\u0442\u0431\u043e\u043a\u0441\u044b";
            case "BlockOverlay" -> "\u041e\u0431\u0432\u043e\u0434\u043a\u0430 \u0431\u043b\u043e\u043a\u0430";
            case "ItemRadius" -> "\u0420\u0430\u0434\u0438\u0443\u0441\u044b \u0434\u0435\u0437\u043a\u0438, \u0442\u0440\u0430\u043f\u043a\u0438, \u043f\u043b\u0430\u0441\u0442\u0430 \u0438 \u0441\u043d\u0435\u0436\u043a\u0430";
            case "Animations" -> "\u041f\u043b\u0430\u0432\u043d\u044b\u0439 Tab, F5, \u0441\u043b\u043e\u0442\u044b \u0438 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c";
            case "TabCustomizer" -> "\u0421\u0442\u043e\u043b\u0431\u0438\u043a\u0438 \u0438 \u043f\u043b\u043e\u0442\u043d\u043e\u0441\u0442\u044c Tab";
            case "Crosshair" -> "\u041a\u0430\u0441\u0442\u043e\u043c\u043d\u044b\u0439 \u043f\u0440\u0438\u0446\u0435\u043b";
            case "FreeLook" -> "\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u0430\u044f \u043a\u0430\u043c\u0435\u0440\u0430";
            case "Zoom" -> "\u041f\u043b\u0430\u0432\u043d\u043e\u0435 \u043f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435";
            case "SafeNametag" -> "\u0412\u0430\u043d\u0438\u043b\u044c\u043d\u044b\u0435 \u043d\u0438\u043a\u0438 \u0438 \u0437\u0434\u043e\u0440\u043e\u0432\u044c\u0435";
            case "FakePlayer" -> "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 \u0438\u0433\u0440\u043e\u043a \u0441 \u0442\u043e\u0442\u0435\u043c\u043e\u043c";
            case "AutoSprint" -> "\u0421\u0430\u043c \u0434\u0435\u0440\u0436\u0438\u0442 \u0441\u043f\u0440\u0438\u043d\u0442 \u043f\u0440\u0438 \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u0438";
            case "ItemSwap" -> "\u0421\u0432\u0430\u043f\u0430\u0435\u0442 \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442 \u0432 \u043b\u0435\u0432\u0443\u044e \u0440\u0443\u043a\u0443";
            case "ElytraSwap" -> "\u0421\u0432\u0430\u043f \u044d\u043b\u0438\u0442\u0440 \u0438 \u043d\u0430\u0433\u0440\u0443\u0434\u043d\u0438\u043a\u0430 \u0441 \u043f\u0440\u0438\u043e\u0440\u0438\u0442\u0435\u0442\u043e\u043c \u0431\u0440\u043e\u043d\u0438";
            case "ItemResorter" -> "\u0418\u0449\u0435\u0442 \u0447\u0430\u0440\u044b \u0438 \u0431\u0430\u0444\u0444\u044b \u0432 tooltip \u0438 \u043f\u043e\u0434\u0441\u0432\u0435\u0447\u0438\u0432\u0430\u0435\u0442 \u043d\u0443\u0436\u043d\u044b\u0435 \u0441\u043b\u043e\u0442\u044b";
            case "AutoBuy" -> "\u041f\u043e\u043a\u0443\u043f\u0430\u0435\u0442 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0435 \u043d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0435 \u043c\u0435\u0447\u0438 \u0441 /ah search";
            case "AutoResell" -> "\u0420\u0430\u0437 \u0432 60-65 \u0441\u0435\u043a\u0443\u043d\u0434 \u043e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u0442 /ah \u0438 \u043e\u0431\u043d\u043e\u0432\u043b\u044f\u0435\u0442 resell";
            case "CaptchaSolver" -> "Решает капчу с картинки через YOLO-модель";
            case "Macros" -> "\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0435\u0442 \u0442\u0435\u043a\u0441\u0442 \u0438\u043b\u0438 /command \u043f\u043e \u0431\u0438\u043d\u0434\u0443";
            case "TrapTracker" -> "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0442\u0430\u0439\u043c\u0435\u0440 \u0442\u0440\u0430\u043f\u043a\u0438";
            case "ClickGui" -> "\u0420\u0430\u0437\u043c\u0435\u0440 \u0438 Blur \u043c\u0435\u043d\u044e";
            default -> name;
        };
    }

    private float cardX(String card) {
        if ("target".equals(card)) return targetCardX;
        if ("china".equals(card)) return chinaCardX;
        if ("wings".equals(card)) return wingsCardX;
        if ("removals".equals(card)) return removalsCardX;
        if ("particles".equals(card)) return particlesCardX;
        if ("jump_circles".equals(card)) return jumpCirclesCardX;
        if ("trails".equals(card)) return trailsCardX;
        if ("world_customizer".equals(card)) return worldCustomizerCardX;
        if ("hit_color".equals(card)) return hitColorCardX;
        if ("hitbox_customizer".equals(card)) return hitboxCustomizerCardX;
        if ("block_overlay".equals(card)) return blockOverlayCardX;
        if ("item_radius".equals(card)) return itemRadiusCardX;
        if ("animations".equals(card)) return animationsCardX;
        if ("tab_customizer".equals(card)) return tabCustomizerCardX;
        if ("freelook".equals(card)) return freeLookCardX;
        if ("zoom".equals(card)) return zoomCardX;
        if ("autoswap".equals(card)) return autoSwapCardX;
        if ("elytra_swap".equals(card)) return elytraSwapCardX;
        if ("item_resorter".equals(card)) return itemResorterCardX;
        if ("auto_buy".equals(card)) return autoBuyCardX;
        if ("anarchy_switcher".equals(card)) return anarchySwitcherCardX;
        if ("name_binder".equals(card)) return nameBinderCardX;
        if ("macros".equals(card)) return macrosCardX;
        if ("crosshair".equals(card)) return crosshairCardX;
        if ("clickgui".equals(card)) return clickGuiCardX;
        return aspectCardX;
    }

    private float cardY(String card) {
        if ("target".equals(card)) return targetCardY;
        if ("china".equals(card)) return chinaCardY;
        if ("wings".equals(card)) return wingsCardY;
        if ("removals".equals(card)) return removalsCardY;
        if ("particles".equals(card)) return particlesCardY;
        if ("jump_circles".equals(card)) return jumpCirclesCardY;
        if ("trails".equals(card)) return trailsCardY;
        if ("world_customizer".equals(card)) return worldCustomizerCardY;
        if ("hit_color".equals(card)) return hitColorCardY;
        if ("hitbox_customizer".equals(card)) return hitboxCustomizerCardY;
        if ("block_overlay".equals(card)) return blockOverlayCardY;
        if ("item_radius".equals(card)) return itemRadiusCardY;
        if ("animations".equals(card)) return animationsCardY;
        if ("tab_customizer".equals(card)) return tabCustomizerCardY;
        if ("freelook".equals(card)) return freeLookCardY;
        if ("zoom".equals(card)) return zoomCardY;
        if ("autoswap".equals(card)) return autoSwapCardY;
        if ("elytra_swap".equals(card)) return elytraSwapCardY;
        if ("item_resorter".equals(card)) return itemResorterCardY;
        if ("auto_buy".equals(card)) return autoBuyCardY;
        if ("anarchy_switcher".equals(card)) return anarchySwitcherCardY;
        if ("name_binder".equals(card)) return nameBinderCardY;
        if ("macros".equals(card)) return macrosCardY;
        if ("crosshair".equals(card)) return crosshairCardY;
        if ("clickgui".equals(card)) return clickGuiCardY;
        return aspectCardY;
    }

    private float itemResorterTabsY() {
        return itemResorterCardY + 48.0F;
    }

    private float itemResorterFirstInputY() {
        return itemResorterTabsY() + 33.0F;
    }

    private float itemResorterSecondInputY() {
        return itemResorterFirstInputY() + 34.0F;
    }

    private float itemResorterExplosiveRowY() {
        return itemResorterSecondInputY() + 36.0F;
    }

    private float itemResorterColorRowY() {
        return itemResorterTab == ItemResorterTab.BUFFS ? itemResorterExplosiveRowY() + 38.0F : itemResorterSecondInputY() + 38.0F;
    }

    private float itemResorterPriceRowY() {
        boolean paletteOpen = itemResorterTab == ItemResorterTab.ENCHANTS ? itemResorterEnchantColorOpen : itemResorterBuffColorOpen;
        return itemResorterColorRowY() + (paletteOpen ? 158.0F : 38.0F);
    }

    private float itemResorterDurabilityRowY() {
        return itemResorterPriceRowY() + 36.0F;
    }

    private float itemResorterNeededSectionY() {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        return itemResorterDurabilityRowY() + (itemResorter.isDurabilityFilterEnabled() ? 82.0F : 38.0F);
    }

    private float itemResorterIgnoredSectionY() {
        return itemResorterNeededSectionY() + itemResorterSectionHeight(itemResorterPrimaryEntries()) + 10.0F;
    }

    private float itemResorterEnchantPaletteY() {
        return itemResorterColorRowY() + 38.0F;
    }

    private float itemResorterBuffPaletteY() {
        return itemResorterColorRowY() + 38.0F;
    }

    private float itemResorterCardHeight() {
        float bottom = itemResorterIgnoredSectionY() + itemResorterSectionHeight(itemResorterSecondaryEntries()) + 16.0F;
        if (itemResorterTab == ItemResorterTab.ENCHANTS && itemResorterEnchantColorOpen) {
            bottom = Math.max(bottom, itemResorterEnchantPaletteY() + 118.0F);
        }
        if (itemResorterTab == ItemResorterTab.BUFFS && itemResorterBuffColorOpen) {
            bottom = Math.max(bottom, itemResorterBuffPaletteY() + 118.0F);
        }
        return bottom - itemResorterCardY;
    }

    private List<String> itemResorterPrimaryEntries() {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        return itemResorterTab == ItemResorterTab.ENCHANTS ? itemResorter.getEnchantNeeded() : itemResorter.getBuffNeeded();
    }

    private List<String> itemResorterSecondaryEntries() {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        return itemResorterTab == ItemResorterTab.ENCHANTS ? itemResorter.getEnchantIgnored() : itemResorter.getBuffIgnored();
    }

    private float itemResorterSectionHeight(List<String> items) {
        List<ItemResorterChipLayout> layouts = itemResorterChipLayouts(ItemResorterInput.ENCHANT_NEEDED, items, 0.0F, 26.0F, ITEM_RESORTER_PANEL_W - 20.0F);
        if (layouts.isEmpty()) {
            return 56.0F;
        }
        float bottom = 0.0F;
        for (ItemResorterChipLayout layout : layouts) {
            bottom = Math.max(bottom, layout.y + layout.h);
        }
        return Math.max(56.0F, bottom + 10.0F);
    }

    private ItemResorterTab itemResorterTabAt(float x, float y) {
        if (!showItemResorter) {
            return null;
        }
        float tabsY = itemResorterTabsY();
        float tabW = (ITEM_RESORTER_PANEL_W - 8.0F) * 0.5F;
        if (inside(x, y, itemResorterCardX + 13.0F, tabsY, tabW, 24.0F)) {
            return ItemResorterTab.ENCHANTS;
        }
        if (inside(x, y, itemResorterCardX + 13.0F + tabW + 8.0F, tabsY, tabW, 24.0F)) {
            return ItemResorterTab.BUFFS;
        }
        return null;
    }

    private ItemResorterInput itemResorterInputAt(float x, float y) {
        if (!showItemResorter) {
            return null;
        }
        ItemResorterInput first = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_NEEDED : ItemResorterInput.BUFF_NEEDED;
        ItemResorterInput second = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_IGNORED : ItemResorterInput.BUFF_IGNORED;
        if (inside(x, y, itemResorterCardX + 83.0F, itemResorterFirstInputY(), ITEM_RESORTER_INPUT_W, 24.0F)) {
            return first;
        }
        if (inside(x, y, itemResorterCardX + 83.0F, itemResorterSecondInputY(), ITEM_RESORTER_INPUT_W, 24.0F)) {
            return second;
        }
        return null;
    }

    private ItemResorterAddTarget itemResorterAddTargetAt(float x, float y) {
        if (!showItemResorter) {
            return null;
        }
        ItemResorterInput first = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_NEEDED : ItemResorterInput.BUFF_NEEDED;
        ItemResorterInput second = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_IGNORED : ItemResorterInput.BUFF_IGNORED;
        float btnX = itemResorterCardX + 13.0F + 70.0F + ITEM_RESORTER_INPUT_W + 4.0F;
        if (inside(x, y, btnX, itemResorterFirstInputY(), 22.0F, 24.0F)) {
            return new ItemResorterAddTarget(first);
        }
        if (inside(x, y, btnX, itemResorterSecondInputY(), 22.0F, 24.0F)) {
            return new ItemResorterAddTarget(second);
        }
        return null;
    }

    private boolean itemResorterEnchantColorBoxAt(float x, float y) {
        return showItemResorter && itemResorterTab == ItemResorterTab.ENCHANTS
                && inside(x, y, itemResorterCardX + 178.0F, itemResorterColorRowY(), 81.0F, 28.0F);
    }

    private boolean itemResorterBuffColorBoxAt(float x, float y) {
        return showItemResorter && itemResorterTab == ItemResorterTab.BUFFS
                && inside(x, y, itemResorterCardX + 178.0F, itemResorterColorRowY(), 81.0F, 28.0F);
    }

    private boolean itemResorterBuffExplosiveToggleAt(float x, float y) {
        return showItemResorter && itemResorterTab == ItemResorterTab.BUFFS
                && inside(x, y, itemResorterCardX + 235.0F, itemResorterExplosiveRowY(), 24.0F, 24.0F);
    }

    private boolean itemResorterPriceInputAt(float x, float y) {
        return showItemResorter && inside(x, y, itemResorterCardX + 83.0F, itemResorterPriceRowY(), 150.0F, 24.0F);
    }

    private boolean itemResorterPriceToggleAt(float x, float y) {
        return showItemResorter && inside(x, y, itemResorterCardX + 239.0F, itemResorterPriceRowY(), 20.0F, 24.0F);
    }

    private boolean itemResorterDurabilityToggleAt(float x, float y) {
        return showItemResorter && inside(x, y, itemResorterCardX + 239.0F, itemResorterDurabilityRowY(), 20.0F, 24.0F);
    }

    private float targetHudCardHeight() {
        return 216.0F;
    }

    private float tapeMouseCardHeight() {
        return tapeMouseButtonOpen ? 214.0F : 170.0F;
    }

    private float tapeMouseButtonListY() {
        return tapeMouseCardY + 78.0F;
    }

    private float tapeMouseDelayLabelY() {
        return tapeMouseCardY + (tapeMouseButtonOpen ? 140.0F : 96.0F);
    }

    private float tapeMouseDelaySliderY() {
        return tapeMouseCardY + (tapeMouseButtonOpen ? 170.0F : 126.0F);
    }

    private float anarchySwitcherCardHeight() {
        int rows = Math.max(1, (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().getAnarchyIds().size() + 2) / 3);
        float listBase = anarchyListY() - anarchySwitcherCardY;
        return listBase + rows * 28.0F + 18.0F;
    }

    private float autoBuyCardHeight() {
        int sellerRows = Math.max(1, (FluxVisualsClient.MODULE_MANAGER.getAutoBuy().getBannedSellers().size() + 2) / 3);
        return autoBuySellerBanListY() - autoBuyCardY + sellerRows * 28.0F + 18.0F;
    }

    private float telegramCardHeight() {
        return 194.0F;
    }

    private float autoBuyAnarchyListY() {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        return autoBuyCardY + (autoBuy.isAnarchyAdEnabled() ? 216.0F : 180.0F);
    }

    private float autoBuyNameRowY() {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        if (!autoBuy.isAnarchySwitchEnabled()) {
            return autoBuyCardY + 104.0F;
        }
        int rows = Math.max(1, (autoBuy.getAnarchyIds().size() + 2) / 3);
        return autoBuyAnarchyListY() + rows * 28.0F + 12.0F;
    }

    private float autoBuyNameInputY() {
        return autoBuyNameRowY() + 34.0F;
    }

    private float autoBuyResellRowY() {
        return autoBuyNameRowY() + (FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isNameEnabled() ? 70.0F : 34.0F);
    }

    private float autoBuySellerBanLabelY() {
        return autoBuyRentalRowY() + 38.0F;
    }

    private float autoBuyRentalRowY() {
        return autoBuyResellRowY() + 34.0F;
    }

    private float autoBuySellerBanInputY() {
        return autoBuySellerBanLabelY() + 24.0F;
    }

    private float autoBuySellerBanListY() {
        return autoBuySellerBanInputY() + 36.0F;
    }

    private boolean autoBuyAnarchyToggleAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 187.0F, autoBuyCardY + 72.0F, 24.0F, 24.0F);
    }

    private boolean autoBuyNameToggleAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 187.0F, autoBuyNameRowY(), 24.0F, 24.0F);
    }

    private boolean autoBuyNameInputAt(float x, float y) {
        return showAutoBuy && FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isNameEnabled()
                && inside(x, y, autoBuyCardX + 13.0F, autoBuyNameInputY(), 210.0F, 26.0F);
    }

    private boolean autoBuyResellToggleAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 187.0F, autoBuyResellRowY(), 24.0F, 24.0F);
    }

    private boolean autoBuyRentalToggleAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 187.0F, autoBuyRentalRowY(), 24.0F, 24.0F);
    }

    private boolean autoResellAfkChatToggleAt(float x, float y) {
        return showAutoResellAFK && inside(x, y, autoResellAfkCardX + 187.0F, autoResellAfkCardY + 42.0F, 24.0F, 24.0F);
    }

    private boolean autoResellAfkChatInputAt(float x, float y) {
        return showAutoResellAFK && FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().isChatEnabled()
                && inside(x, y, autoResellAfkCardX + 13.0F, autoResellAfkCardY + 72.0F, 234.0F, 26.0F);
    }

    private boolean autoResellAfkSellToggleAt(float x, float y) {
        AutoResellAFK module = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        float sellY = autoResellAfkCardY + (module.isChatEnabled() ? 178.0F : 60.0F);
        return showAutoResellAFK && inside(x, y, autoResellAfkCardX + 187.0F, sellY, 24.0F, 24.0F);
    }

    private boolean autoResellAfkSellPriceAt(float x, float y) {
        AutoResellAFK module = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        float sellY = autoResellAfkCardY + (module.isChatEnabled() ? 178.0F : 60.0F);
        return showAutoResellAFK && module.isSellPurchasedSwords()
                && inside(x, y, autoResellAfkCardX + 13.0F, sellY + 30.0F, 234.0F, 26.0F);
    }

    private boolean autoResellAfkHelperToggleAt(float x, float y) {
        AutoResellAFK module = FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK();
        float sellY = autoBuyCardY + (module.isChatEnabled() ? 178.0F : 60.0F);
        float helperY = sellY + (module.isSellPurchasedSwords() ? 68.0F : 34.0F);
        return showAutoResellAFK && inside(x, y, autoResellAfkCardX + 187.0F, helperY, 24.0F, 24.0F);
    }

    private boolean autoBuySellerBanInputAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 13.0F, autoBuySellerBanInputY(), 174.0F, 26.0F);
    }

    private boolean autoBuySellerBanAddAt(float x, float y) {
        return showAutoBuy && inside(x, y, autoBuyCardX + 195.0F, autoBuySellerBanInputY(), 28.0F, 26.0F);
    }

    private boolean telegramTokenInputAt(float x, float y) {
        return showTelegram && inside(x, y, telegramCardX + 13.0F, telegramCardY + 96.0F, 210.0F, 26.0F);
    }

    private boolean telegramChatInputAt(float x, float y) {
        return showTelegram && inside(x, y, telegramCardX + 13.0F, telegramCardY + 148.0F, 150.0F, 26.0F);
    }

    private boolean telegramTestAt(float x, float y) {
        return showTelegram && inside(x, y, telegramCardX + 171.0F, telegramCardY + 148.0F, 61.0F, 26.0F);
    }

    private boolean telegramLogsToggleAt(float x, float y) {
        return showTelegram && inside(x, y, telegramCardX + 187.0F, telegramCardY + 40.0F, 24.0F, 24.0F);
    }

    private boolean autoBuyAnarchyInputAt(float x, float y) {
        return showAutoBuy && FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isAnarchySwitchEnabled()
                && inside(x, y, autoBuyCardX + 13.0F, autoBuyCardY + 110.0F, 174.0F, 26.0F);
    }

    private boolean autoBuyAnarchyAddAt(float x, float y) {
        return showAutoBuy && FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isAnarchySwitchEnabled()
                && inside(x, y, autoBuyCardX + 195.0F, autoBuyCardY + 110.0F, 28.0F, 26.0F);
    }

    private boolean autoBuyAnarchyAdToggleAt(float x, float y) {
        return showAutoBuy && FluxVisualsClient.MODULE_MANAGER.getAutoBuy().isAnarchySwitchEnabled()
                && inside(x, y, autoBuyCardX + 187.0F, autoBuyCardY + 146.0F, 24.0F, 24.0F);
    }

    private boolean autoBuyAnarchyAdInputAt(float x, float y) {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        return showAutoBuy && autoBuy.isAnarchySwitchEnabled() && autoBuy.isAnarchyAdEnabled()
                && inside(x, y, autoBuyCardX + 13.0F, autoBuyCardY + 180.0F, 210.0F, 26.0F);
    }

    private String autoBuyAnarchyRemoveAt(float x, float y) {
        AutoBuy autoBuy = FluxVisualsClient.MODULE_MANAGER.getAutoBuy();
        if (!showAutoBuy || !autoBuy.isAnarchySwitchEnabled()) {
            return null;
        }
        float cx = autoBuyCardX + 13.0F;
        float cy = autoBuyAnarchyListY();
        float limit = cx + 210.0F;
        for (String value : autoBuy.getAnarchyIds()) {
            float chipW = clamp(measureBaseTextWidth(value, 0.82F, FontRole.INTER) + 30.0F, 54.0F, 210.0F);
            if (cx > autoBuyCardX + 13.0F && cx + chipW > limit) {
                cx = autoBuyCardX + 13.0F;
                cy += 28.0F;
            }
            if (inside(x, y, cx + chipW - 20.0F, cy + 1.0F, 18.0F, 18.0F)) {
                return value;
            }
            cx += chipW + 6.0F;
        }
        return null;
    }

    private String autoBuySellerBanRemoveAt(float x, float y) {
        if (!showAutoBuy) {
            return null;
        }
        float cx = autoBuyCardX + 13.0F;
        float cy = autoBuySellerBanListY();
        float limit = cx + 210.0F;
        for (String value : FluxVisualsClient.MODULE_MANAGER.getAutoBuy().getBannedSellers()) {
            float chipW = clamp(measureBaseTextWidth(value, 0.82F, FontRole.INTER) + 30.0F, 54.0F, 210.0F);
            if (cx > autoBuyCardX + 13.0F && cx + chipW > limit) {
                cx = autoBuyCardX + 13.0F;
                cy += 28.0F;
            }
            if (inside(x, y, cx + chipW - 20.0F, cy + 1.0F, 18.0F, 18.0F)) {
                return value;
            }
            cx += chipW + 6.0F;
        }
        return null;
    }

    private float nameBinderCardHeight() {
        return 96.0F + Math.max(1, FluxVisualsClient.MODULE_MANAGER.getNameBind().getEntries().size()) * 30.0F;
    }

    private float macrosCardHeight() {
        return 96.0F + Math.max(1, FluxVisualsClient.MODULE_MANAGER.getMacros().getEntries().size()) * 30.0F;
    }

    private float anarchyListY() {
        return anarchySwitcherCardY + (FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isAdEnabled() ? 224.0F : 188.0F);
    }

    private boolean targetHudRedToggleAt(float x, float y) {
        return showTargetHud && inside(x, y, targetHudCardX + 187.0F, targetHudCardY + 118.0F, 24.0F, 24.0F);
    }

    private boolean targetHudParticlesToggleAt(float x, float y) {
        return showTargetHud && inside(x, y, targetHudCardX + 187.0F, targetHudCardY + 152.0F, 24.0F, 24.0F);
    }

    private boolean tapeMouseButtonToggleAt(float x, float y) {
        return showTapeMouse && inside(x, y, tapeMouseCardX + 104.0F, tapeMouseCardY + 47.0F, 96.0F, 26.0F);
    }

    private TapeMouse.ButtonMode tapeMouseButtonOptionAt(float x, float y) {
        if (!showTapeMouse || !tapeMouseButtonOpen) {
            return null;
        }
        float listX = tapeMouseCardX + 104.0F;
        float listY = tapeMouseButtonListY();
        if (inside(x, y, listX + 4.0F, listY + 3.0F, 88.0F, 20.0F)) {
            return TapeMouse.ButtonMode.LEFT;
        }
        if (inside(x, y, listX + 4.0F, listY + 25.0F, 88.0F, 20.0F)) {
            return TapeMouse.ButtonMode.RIGHT;
        }
        return null;
    }

    private boolean anarchyInputAt(float x, float y) {
        return showAnarchySwitcher && inside(x, y, anarchySwitcherCardX + 13.0F, anarchySwitcherCardY + 118.0F, 174.0F, 26.0F);
    }

    private boolean anarchyAddAt(float x, float y) {
        return showAnarchySwitcher && inside(x, y, anarchySwitcherCardX + 195.0F, anarchySwitcherCardY + 118.0F, 28.0F, 26.0F);
    }

    private boolean anarchyAdToggleAt(float x, float y) {
        return showAnarchySwitcher && inside(x, y, anarchySwitcherCardX + 187.0F, anarchySwitcherCardY + 154.0F, 24.0F, 24.0F);
    }

    private boolean anarchyAdInputAt(float x, float y) {
        return showAnarchySwitcher && FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isAdEnabled()
                && inside(x, y, anarchySwitcherCardX + 13.0F, anarchySwitcherCardY + 188.0F, 210.0F, 26.0F);
    }

    private String anarchyRemoveAt(float x, float y) {
        if (!showAnarchySwitcher) {
            return null;
        }
        float cx = anarchySwitcherCardX + 13.0F;
        float cy = anarchyListY();
        float limit = cx + 210.0F;
        for (String value : FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().getAnarchyIds()) {
            float chipW = clamp(measureBaseTextWidth(value, 0.82F, FontRole.INTER) + 30.0F, 54.0F, 210.0F);
            if (cx > anarchySwitcherCardX + 13.0F && cx + chipW > limit) {
                cx = anarchySwitcherCardX + 13.0F;
                cy += 28.0F;
            }
            if (inside(x, y, cx + chipW - 20.0F, cy + 1.0F, 18.0F, 18.0F)) {
                return value;
            }
            cx += chipW + 6.0F;
        }
        return null;
    }

    private boolean nameBinderInputAt(float x, float y) {
        return showNameBinder && inside(x, y, nameBinderCardX + 13.0F, nameBinderCardY + 52.0F, 210.0F, 26.0F);
    }

    private boolean nameBinderAddAt(float x, float y) {
        return showNameBinder && inside(x, y, nameBinderCardX + 230.0F, nameBinderCardY + 52.0F, 28.0F, 26.0F);
    }

    private int nameBinderRemoveAt(float x, float y) {
        if (!showNameBinder) {
            return -1;
        }
        int size = FluxVisualsClient.MODULE_MANAGER.getNameBind().getEntries().size();
        for (int i = 0; i < size; i++) {
            float rowY = nameBinderCardY + 90.0F + i * 30.0F;
            if (inside(x, y, nameBinderCardX + 220.0F, rowY + 2.0F, 24.0F, 20.0F)) {
                return i;
            }
        }
        return -1;
    }

    private int nameBinderBindAt(float x, float y) {
        if (!showNameBinder) {
            return -1;
        }
        int size = FluxVisualsClient.MODULE_MANAGER.getNameBind().getEntries().size();
        for (int i = 0; i < size; i++) {
            float rowY = nameBinderCardY + 90.0F + i * 30.0F;
            if (inside(x, y, nameBinderCardX + 165.0F, rowY + 2.0F, 48.0F, 20.0F)) {
                return i;
            }
        }
        return -1;
    }

    private boolean macrosInputAt(float x, float y) {
        return showMacros && inside(x, y, macrosCardX + 13.0F, macrosCardY + 52.0F, 210.0F, 26.0F);
    }

    private boolean macrosAddAt(float x, float y) {
        return showMacros && inside(x, y, macrosCardX + 230.0F, macrosCardY + 52.0F, 28.0F, 26.0F);
    }

    private int macrosRemoveAt(float x, float y) {
        if (!showMacros) {
            return -1;
        }
        int size = FluxVisualsClient.MODULE_MANAGER.getMacros().getEntries().size();
        for (int i = 0; i < size; i++) {
            float rowY = macrosCardY + 90.0F + i * 30.0F;
            if (inside(x, y, macrosCardX + 220.0F, rowY + 2.0F, 24.0F, 20.0F)) {
                return i;
            }
        }
        return -1;
    }

    private int macrosBindAt(float x, float y) {
        if (!showMacros) {
            return -1;
        }
        int size = FluxVisualsClient.MODULE_MANAGER.getMacros().getEntries().size();
        for (int i = 0; i < size; i++) {
            float rowY = macrosCardY + 90.0F + i * 30.0F;
            if (inside(x, y, macrosCardX + 165.0F, rowY + 2.0F, 48.0F, 20.0F)) {
                return i;
            }
        }
        return -1;
    }

    private ItemResorterChipAction itemResorterChipActionAt(float x, float y) {
        if (!showItemResorter) {
            return null;
        }

        ItemResorterInput first = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_NEEDED : ItemResorterInput.BUFF_NEEDED;
        List<String> firstItems = itemResorterTab == ItemResorterTab.ENCHANTS
                ? FluxVisualsClient.MODULE_MANAGER.getItemResorter().getEnchantNeeded()
                : FluxVisualsClient.MODULE_MANAGER.getItemResorter().getBuffNeeded();
        for (ItemResorterChipLayout chip : itemResorterChipLayouts(first, firstItems, itemResorterCardX + 23.0F, itemResorterNeededSectionY() + 26.0F, ITEM_RESORTER_PANEL_W - 20.0F)) {
            if (inside(x, y, chip.x + chip.w - 18.0F, chip.y + 3.0F, 14.0F, 14.0F)) {
                return new ItemResorterChipAction(chip.input, chip.value);
            }
        }

        ItemResorterInput second = itemResorterTab == ItemResorterTab.ENCHANTS ? ItemResorterInput.ENCHANT_IGNORED : ItemResorterInput.BUFF_IGNORED;
        List<String> secondItems = itemResorterTab == ItemResorterTab.ENCHANTS
                ? FluxVisualsClient.MODULE_MANAGER.getItemResorter().getEnchantIgnored()
                : FluxVisualsClient.MODULE_MANAGER.getItemResorter().getBuffIgnored();
        for (ItemResorterChipLayout chip : itemResorterChipLayouts(second, secondItems, itemResorterCardX + 23.0F, itemResorterIgnoredSectionY() + 26.0F, ITEM_RESORTER_PANEL_W - 20.0F)) {
            if (inside(x, y, chip.x + chip.w - 18.0F, chip.y + 3.0F, 14.0F, 14.0F)) {
                return new ItemResorterChipAction(chip.input, chip.value);
            }
        }

        return null;
    }

    private List<ItemResorterChipLayout> itemResorterChipLayouts(ItemResorterInput input, List<String> items, float startX, float startY, float maxWidth) {
        List<ItemResorterChipLayout> layouts = new ArrayList<>();
        float x = startX;
        float y = startY;
        float limit = startX + maxWidth;
        for (String value : items) {
            float chipWidth = clamp(measureBaseTextWidth(value, 0.82F, FontRole.INTER) + 28.0F, 62.0F, maxWidth);
            if (x > startX && x + chipWidth > limit) {
                x = startX;
                y += ITEM_RESORTER_CHIP_H + ITEM_RESORTER_CHIP_GAP;
            }
            layouts.add(new ItemResorterChipLayout(input, value, x, y, chipWidth, ITEM_RESORTER_CHIP_H));
            x += chipWidth + ITEM_RESORTER_CHIP_GAP;
        }
        return layouts;
    }

    private float measureBaseTextWidth(String value, float textScale, FontRole role) {
        TextTexture text = textTexture(value, Math.max(11, Math.round(9.5F * scale * textScale)), role);
        return text.width / scale;
    }

    private void drawScrollingValue(DrawContext context, String value, float x, float y, float w, float h,
                                    float textScale, int color, FontRole role, String seed) {
        float textWidth = measureBaseTextWidth(value, textScale, role);
        float textY = y + ((h - (textTexture(value, Math.max(11, Math.round(9.5F * scale * textScale)), role).height / scale)) * 0.5F) - 1.0F;
        if (textWidth <= w) {
            drawText(context, value, x, textY, textScale, color, role);
            return;
        }

        float overflow = textWidth - w;
        double time = System.currentTimeMillis() / 1000.0D;
        double phase = 0.5D + 0.5D * Math.sin(time * 0.85D + (seed.hashCode() & 1023) * 0.017D);
        float offset = (float) (overflow * phase);
        int x1 = Math.round(sx(x));
        int y1 = Math.round(sy(y - 1.0F));
        int x2 = Math.round(sx(x + w));
        int y2 = Math.round(sy(y + h));
        context.enableScissor(x1, y1, x2, y2);
        drawText(context, value, x - offset, textY, textScale, color, role);
        context.disableScissor();
    }

    private StringBuilder activeItemResorterDraft() {
        return draftFor(itemResorterFocusedInput);
    }

    private StringBuilder draftFor(ItemResorterInput input) {
        return switch (input) {
            case ENCHANT_NEEDED -> itemResorterEnchantNeededDraft;
            case ENCHANT_IGNORED -> itemResorterEnchantIgnoredDraft;
            case BUFF_NEEDED -> itemResorterBuffNeededDraft;
            case BUFF_IGNORED -> itemResorterBuffIgnoredDraft;
        };
    }

    private void submitFocusedItemResorterInput() {
        if (itemResorterFocusedInput != null) {
            submitItemResorterInput(itemResorterFocusedInput);
        }
    }

    private void submitAnarchyDraft() {
        String value = anarchyDraft.toString().trim();
        if (!value.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().addAnarchyId(value);
            anarchyDraft.setLength(0);
        }
        anarchyInputFocused = true;
    }

    private void submitAnarchyAdDraft() {
        FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setAdText(anarchyAdDraft.toString());
        anarchyAdFocused = false;
    }

    private void submitAutoBuyAnarchyDraft() {
        String value = autoBuyAnarchyDraft.toString().trim();
        if (!value.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().addAnarchyId(value);
            autoBuyAnarchyDraft.setLength(0);
        }
        autoBuyAnarchyInputFocused = true;
    }

    private void submitAutoBuyAnarchyAdDraft() {
        FluxVisualsClient.MODULE_MANAGER.getAutoBuy().setAnarchyAdText(autoBuyAnarchyAdDraft.toString());
        autoBuyAnarchyAdFocused = false;
    }

    private void submitAutoBuySellerBanDraft() {
        String value = autoBuySellerBanDraft.toString().trim();
        if (!value.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().addBannedSeller(value);
            autoBuySellerBanDraft.setLength(0);
        }
        autoBuySellerBanFocused = true;
    }

    private void submitTelegramTokenDraft() {
        String value = telegramTokenDraft.toString().trim();
        FluxVisualsClient.MODULE_MANAGER.getTelegram().setBotToken(value);
        telegramTokenDraft.setLength(0);
        telegramTokenFocused = false;
    }

    private void submitTelegramChatDraft() {
        String value = telegramChatDraft.toString().trim();
        FluxVisualsClient.MODULE_MANAGER.getTelegram().setChatId(value);
        telegramChatDraft.setLength(0);
        telegramChatFocused = false;
    }

    private void pasteToDraft(StringBuilder draft, int maxLength) {
        if (draft == null || maxLength <= 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.keyboard == null) {
            return;
        }
        String clipboard = client.keyboard.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) {
            return;
        }
        for (int i = 0; i < clipboard.length() && draft.length() < maxLength; i++) {
            char ch = clipboard.charAt(i);
            if (!Character.isISOControl(ch)) {
                draft.append(ch);
            }
        }
    }

    private void submitNameBinderDraft() {
        String value = nameBinderDraft.toString().trim();
        if (!value.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getNameBind().addEntry(value);
            nameBinderDraft.setLength(0);
        }
        nameBinderInputFocused = true;
    }

    private void submitMacrosDraft() {
        String value = macrosDraft.toString().trim();
        if (!value.isEmpty()) {
            FluxVisualsClient.MODULE_MANAGER.getMacros().addEntry(value);
            macrosDraft.setLength(0);
        }
        macrosInputFocused = true;
    }

    private static int parseIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String keyLabelOrDash(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_UNKNOWN ? "-" : keyLabel(keyCode);
    }

    private void submitItemResorterPrice() {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        String raw = itemResorterPriceDraft.toString().replaceAll("[^0-9]", "");
        if (!raw.isEmpty()) {
            try {
                itemResorter.setMaxPrice(Long.parseLong(raw));
                FluxVisualsClient.CONFIG_MANAGER.saveNow();
            } catch (NumberFormatException ignored) {
                // Keep previous value.
            }
        }
        itemResorterPriceDraft.setLength(0);
        itemResorterPriceFocused = true;
    }

    private void seedItemResorterPriceDraftForEdit() {
        if (!itemResorterPriceDraft.isEmpty()) {
            return;
        }
        long value = FluxVisualsClient.MODULE_MANAGER.getItemResorter().getMaxPrice();
        if (value > 0L) {
            itemResorterPriceDraft.append(value);
        }
    }

    private static String formatPrice(long value) {
        return formatPrice(Long.toString(Math.max(0L, value)));
    }

    private static String formatPrice(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder(digits.length() + digits.length() / 3);
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        formatted.append(digits, 0, firstGroup);
        for (int i = firstGroup; i < digits.length(); i += 3) {
            formatted.append(',').append(digits, i, Math.min(i + 3, digits.length()));
        }
        return formatted.toString();
    }

    private void submitItemResorterInput(ItemResorterInput input) {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        StringBuilder draft = draftFor(input);
        String value = draft.toString().trim();
        if (value.isEmpty()) {
            itemResorterFocusedInput = input;
            return;
        }

        switch (input) {
            case ENCHANT_NEEDED -> itemResorter.addEnchantNeeded(value);
            case ENCHANT_IGNORED -> itemResorter.addEnchantIgnored(value);
            case BUFF_NEEDED -> itemResorter.addBuffNeeded(value);
            case BUFF_IGNORED -> itemResorter.addBuffIgnored(value);
        }
        draft.setLength(0);
        itemResorterFocusedInput = input;
    }

    private void removeItemResorterEntry(ItemResorterChipAction action) {
        ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
        switch (action.input) {
            case ENCHANT_NEEDED -> itemResorter.removeEnchantNeeded(action.value);
            case ENCHANT_IGNORED -> itemResorter.removeEnchantIgnored(action.value);
            case BUFF_NEEDED -> itemResorter.removeBuffNeeded(action.value);
            case BUFF_IGNORED -> itemResorter.removeBuffIgnored(action.value);
        }
    }

    private boolean removalsToggleAt(float x, float y, int index) {
        float rowY = removalsContentY() + index * 36.0F;
        return inside(x, y, removalsCardX + 188.0F, rowY, 24.0F, 24.0F);
    }

    private static float[] avoidMainPanel(float x, float y, float w, float h) {
        float nx = clamp(x, 0.0F, BASE_WIDTH - w);
        float ny = clamp(y, 0.0F, BASE_HEIGHT - h);
        float mainX = MAIN_X;
        float mainY = MAIN_Y;
        float mainW = MAIN_W;
        float mainH = MAIN_H;
        if (nx < mainX + mainW && nx + w > mainX && ny < mainY + mainH && ny + h > mainY) {
            float left = Math.max(0.0F, mainX - w - 12.0F);
            float right = Math.min(BASE_WIDTH - w, mainX + mainW + 12.0F);
            float top = Math.max(0.0F, mainY - h - 12.0F);
            float bottom = Math.min(BASE_HEIGHT - h, mainY + mainH + 12.0F);
            float cx = nx + w * 0.5F;
            float cy = ny + h * 0.5F;
            float dx = Math.abs(cx - (mainX + mainW * 0.5F));
            float dy = Math.abs(cy - (mainY + mainH * 0.5F));
            if (dx >= dy) {
                nx = cx < mainX + mainW * 0.5F ? left : right;
            } else {
                ny = cy < mainY + mainH * 0.5F ? top : bottom;
            }
        }
        return new float[]{nx, ny};
    }

    private boolean paletteAt(float x, float y) {
        return showChinaHat && inside(x, y, chinaCardX + 15.0F, chinaPaletteY(), 166.0F, 72.0F);
    }

    private boolean handleColorPickerOverlayClick(float x, float y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return false;
        }

        if (targetColorOpen && handlePaletteClick(x, y, targetCardX + 15.0F, targetPaletteY(), "target_palette", "target_hue", button)) return true;
        if (chinaPaletteOpen && handlePaletteClick(x, y, chinaCardX + 15.0F, chinaPaletteY(), "china_palette", "china_hue", button)) return true;
        if (particlesColorOpen && handlePaletteClick(x, y, particlesCardX + 15.0F, particlesColorY() + 63.0F, "particles_palette", "particles_hue", button)) return true;
        if (jumpCircleColorOpen && handlePaletteClick(x, y, jumpCirclesCardX + 15.0F, jumpCirclesPaletteY(), "jump_circles_palette", "jump_circles_hue", button)) return true;
        if (trailsColorOpen && handlePaletteClick(x, y, trailsCardX + 15.0F, trailsPaletteY(), "trails_palette", "trails_hue", button)) return true;
        if (hitColorPaletteOpen && handlePaletteClick(x, y, hitColorCardX + 15.0F, hitColorPaletteY(), "hit_color_palette", "hit_color_hue", button)) return true;
        if (worldFogPaletteOpen && handlePaletteClick(x, y, worldCustomizerCardX + 15.0F, worldFogPaletteY(), "world_fog_palette", "world_fog_hue", button)) return true;
        if (hitboxOutlinePaletteOpen && handlePaletteClick(x, y, hitboxCustomizerCardX + 15.0F, hitboxCustomizerOutlinePaletteY(), "hitbox_outline_palette", "hitbox_outline_hue", button)) return true;
        if (hitboxFillPaletteOpen && handlePaletteClick(x, y, hitboxCustomizerCardX + 15.0F, hitboxFillPaletteY(), "hitbox_fill_palette", "hitbox_fill_hue", button)) return true;
        if (blockOverlayOutlinePaletteOpen && handlePaletteClick(x, y, blockOverlayCardX + 15.0F, blockOverlayOutlinePaletteY(), "block_overlay_outline_palette", "block_overlay_outline_hue", button)) return true;
        if (blockOverlayFillPaletteOpen && handlePaletteClick(x, y, blockOverlayCardX + 15.0F, blockOverlayFillPaletteY(), "block_overlay_fill_palette", "block_overlay_fill_hue", button)) return true;
        if (crosshairColorOpen && handlePaletteClick(x, y, crosshairCardX + 15.0F, crosshairPaletteY(), "crosshair_palette", "crosshair_hue", button)) return true;
        if (itemResorterEnchantColorOpen && handlePaletteClick(x, y, itemResorterCardX + 15.0F, itemResorterEnchantPaletteY(), "item_resorter_enchant_palette", "item_resorter_enchant_hue", button)) return true;
        if (itemResorterBuffColorOpen && handlePaletteClick(x, y, itemResorterCardX + 15.0F, itemResorterBuffPaletteY(), "item_resorter_buff_palette", "item_resorter_buff_hue", button)) return true;

        return false;
    }

    private boolean handlePaletteClick(float x, float y, float paletteX, float paletteY, String paletteId, String hueId, int button) {
        if (!inside(x, y, paletteX - 8.0F, paletteY - 8.0F, 182.0F, 118.0F)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            draggingSlider = null;
            return true;
        }
        if (inside(x, y, paletteX, paletteY, 166.0F, 72.0F)) {
            draggingSlider = paletteId;
            updateSlider(draggingSlider, x, y);
            return true;
        }
        if (sliderHit(x, y, paletteX, paletteY + 84.0F, 166.0F)) {
            draggingSlider = hueId;
            updateSlider(draggingSlider, x, y);
            return true;
        }
        draggingSlider = null;
        return true;
    }

    private boolean particlesPaletteAt(float x, float y) {
        return showParticles && inside(x, y, particlesCardX + 15.0F, particlesColorY() + 63.0F, 166.0F, 72.0F);
    }

    private boolean trailsPaletteAt(float x, float y) {
        return showTrails && inside(x, y, trailsCardX + 15.0F, trailsPaletteY(), 166.0F, 72.0F);
    }

    private boolean trailsColorBoxAt(float x, float y) {
        return showTrails && inside(x, y, trailsCardX + 126.0F, trailsColorY(), 58.0F, 28.0F);
    }

    private boolean hitColorPaletteAt(float x, float y) {
        return showHitColor && inside(x, y, hitColorCardX + 15.0F, hitColorPaletteY(), 166.0F, 72.0F);
    }

    private boolean worldFogPaletteAt(float x, float y) {
        return showWorldCustomizer && FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().isCustomFogEnabled()
                && inside(x, y, worldCustomizerCardX + 15.0F, worldFogPaletteY(), 166.0F, 72.0F);
    }

    private boolean worldFogToggleAt(float x, float y) {
        return showWorldCustomizer && inside(x, y, worldCustomizerCardX + 188.0F, worldCustomizerFogToggleY(), 24.0F, 24.0F);
    }

    private boolean worldFogColorBoxAt(float x, float y) {
        return showWorldCustomizer && FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().isCustomFogEnabled()
                && inside(x, y, worldCustomizerCardX + 13.0F, worldFogColorY() + 28.0F, 58.0F, 28.0F);
    }

    private boolean hitColorArmorToggleAt(float x, float y) {
        return showHitColor && inside(x, y, hitColorCardX + 188.0F, hitColorCardY + 48.0F, 24.0F, 24.0F);
    }

    private float hitboxCustomizerContentY() {
        return hitboxTargetsY() + 40.0F + hitboxTargetsProgress * hitboxTargetListHeight();
    }

    private float hitboxCustomizerSliderY() {
        return hitboxCustomizerContentY() + 54.0F;
    }

    private float hitboxAlwaysShowY() {
        return hitboxCustomizerSliderY() + 40.0F;
    }

    private float hitboxCornersOnlyY() {
        return hitboxAlwaysShowY() + 36.0F;
    }

    private float hitboxFillToggleY() {
        return hitboxCornersOnlyY() + 36.0F;
    }

    private float hitboxFillColorY() {
        return hitboxFillToggleY() + 40.0F;
    }

    private float hitboxCustomizerFillAlphaY() {
        return hitboxFillColorY() + 56.0F;
    }

    private float hitboxTargetsY() {
        return hitboxCustomizerCardY + 48.0F;
    }

    private float hitboxTargetListHeight() {
        return HitboxCustomizer.TargetType.values().length * 22.0F + 6.0F;
    }

    private float hitboxSelfY() {
        return FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled()
                ? hitboxCustomizerFillAlphaY() + 40.0F
                : hitboxFillToggleY() + 40.0F;
    }

    private float hitboxCustomizerOutlinePaletteY() {
        return hitboxCustomizerContentY() + 43.0F;
    }

    private float hitboxFillPaletteY() {
        return hitboxFillColorY() + 43.0F;
    }

    private float hitboxCustomizerCardHeight() {
        float bottom = hitboxSelfY() + 24.0F;
        if (hitboxOutlinePaletteOpen) {
            bottom = Math.max(bottom, hitboxCustomizerOutlinePaletteY() + 110.0F);
        }
        if (FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled() && hitboxFillPaletteOpen) {
            bottom = Math.max(bottom, hitboxFillPaletteY() + 110.0F);
        }
        return bottom - hitboxCustomizerCardY + 18.0F;
    }

    private boolean hitboxAlwaysShowBindButtonAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 148.0F, hitboxAlwaysShowY() + 1.0F, 30.0F, 22.0F);
    }

    private boolean hitboxAlwaysShowToggleAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 188.0F, hitboxAlwaysShowY(), 24.0F, 24.0F);
    }

    private boolean hitboxOutlineColorBoxAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 126.0F, hitboxCustomizerContentY(), 58.0F, 28.0F);
    }

    private boolean hitboxFillColorBoxAt(float x, float y) {
        return showHitboxCustomizer && FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled()
                && inside(x, y, hitboxCustomizerCardX + 126.0F, hitboxFillColorY(), 58.0F, 28.0F);
    }

    private boolean hitboxOutlinePaletteAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 15.0F, hitboxCustomizerOutlinePaletteY(), 166.0F, 72.0F);
    }

    private boolean hitboxFillPaletteAt(float x, float y) {
        return showHitboxCustomizer && FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isFillEnabled()
                && inside(x, y, hitboxCustomizerCardX + 15.0F, hitboxFillPaletteY(), 166.0F, 72.0F);
    }

    private boolean hitboxFillToggleAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 188.0F, hitboxFillToggleY(), 24.0F, 24.0F);
    }

    private boolean hitboxCornersOnlyToggleAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 188.0F, hitboxCornersOnlyY(), 24.0F, 24.0F);
    }

    private boolean hitboxTargetsBoxAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 104.0F, hitboxTargetsY(), 108.0F, 24.0F);
    }

    private HitboxCustomizer.TargetType hitboxTargetAt(float x, float y) {
        if (!showHitboxCustomizer || hitboxTargetsProgress <= 0.12F) {
            return null;
        }
        float listX = hitboxCustomizerCardX + 104.0F;
        float listY = hitboxTargetsY() + 29.0F;
        HitboxCustomizer.TargetType[] values = HitboxCustomizer.TargetType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 108.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private boolean hitboxSelfToggleAt(float x, float y) {
        return showHitboxCustomizer && inside(x, y, hitboxCustomizerCardX + 188.0F, hitboxSelfY(), 24.0F, 24.0F);
    }

    private float blockOverlayContentY() {
        return blockOverlayCardY + 48.0F;
    }

    private float blockOverlayThicknessY() {
        return blockOverlayContentY() + 54.0F;
    }

    private float blockOverlayFillToggleY() {
        return blockOverlayThicknessY() + 40.0F;
    }

    private float blockOverlayThroughWallsY() {
        return blockOverlayFillToggleY() + 36.0F;
    }

    private float blockOverlaySmoothSwitchY() {
        return blockOverlayThroughWallsY() + 36.0F;
    }

    private float blockOverlayFillModeY() {
        return blockOverlaySmoothSwitchY() + 38.0F;
    }

    private float blockOverlayShaderY() {
        return blockOverlayFillModeY() + 34.0F + blockOverlayFillModeProgress * blockOverlayFillModeListHeight();
    }

    private float blockOverlayAnimationSpeedY() {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        float y = blockOverlayFillModeY() + 34.0F + blockOverlayFillModeProgress * blockOverlayFillModeListHeight();
        if (blockOverlay.getFillMode() == BlockOverlay.FillMode.SHADER) {
            y += 34.0F + blockOverlayShaderProgress * blockOverlayShaderListHeight();
        }
        return y + 21.0F;
    }

    private float blockOverlayFillColorY() {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        float y = blockOverlayFillModeY() + 34.0F + blockOverlayFillModeProgress * blockOverlayFillModeListHeight();
        if (blockOverlay.getFillMode() == BlockOverlay.FillMode.SHADER) {
            y += 34.0F + blockOverlayShaderProgress * blockOverlayShaderListHeight();
        }
        if (blockOverlayShowsAnimationSpeed()) {
            y += 61.0F;
        }
        return y;
    }

    private boolean blockOverlayShowsAnimationSpeed() {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        return blockOverlay.isFillEnabled() && blockOverlay.getFillMode() == BlockOverlay.FillMode.SHADER;
    }

    private float blockOverlayFillAlphaY() {
        return blockOverlayFillColorY() + 56.0F;
    }

    private float blockOverlayOutlinePaletteY() {
        return blockOverlayContentY() + 43.0F;
    }

    private float blockOverlayFillPaletteY() {
        return blockOverlayFillColorY() + 43.0F;
    }

    private float blockOverlayCardHeight() {
        float bottom = blockOverlaySmoothSwitchY() + 24.0F;
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        if (blockOverlay.isFillEnabled()) {
            bottom = Math.max(bottom, blockOverlayFillAlphaY() + 24.0F);
        }
        if (blockOverlayOutlinePaletteOpen) {
            bottom = Math.max(bottom, blockOverlayOutlinePaletteY() + 110.0F);
        }
        if (blockOverlay.isFillEnabled() && blockOverlayFillPaletteOpen) {
            bottom = Math.max(bottom, blockOverlayFillPaletteY() + 110.0F);
        }
        return bottom - blockOverlayCardY + 18.0F;
    }

    private float blockOverlayFillModeListHeight() {
        return BlockOverlay.FillMode.values().length * 22.0F + 6.0F;
    }

    private float blockOverlayShaderListHeight() {
        return BlockOverlay.ShaderType.values().length * 22.0F + 6.0F;
    }

    private boolean blockOverlayOutlineColorBoxAt(float x, float y) {
        return showBlockOverlay && inside(x, y, blockOverlayCardX + 126.0F, blockOverlayContentY(), 58.0F, 28.0F);
    }

    private boolean blockOverlayFillColorBoxAt(float x, float y) {
        return showBlockOverlay && FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isFillEnabled()
                && inside(x, y, blockOverlayCardX + 126.0F, blockOverlayFillColorY(), 58.0F, 28.0F);
    }

    private boolean blockOverlayOutlinePaletteAt(float x, float y) {
        return showBlockOverlay && inside(x, y, blockOverlayCardX + 15.0F, blockOverlayOutlinePaletteY(), 166.0F, 72.0F);
    }

    private boolean blockOverlayFillPaletteAt(float x, float y) {
        return showBlockOverlay && FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isFillEnabled()
                && inside(x, y, blockOverlayCardX + 15.0F, blockOverlayFillPaletteY(), 166.0F, 72.0F);
    }

    private boolean blockOverlayFillToggleAt(float x, float y) {
        return showBlockOverlay && inside(x, y, blockOverlayCardX + 188.0F, blockOverlayFillToggleY(), 24.0F, 24.0F);
    }

    private boolean blockOverlayThroughWallsToggleAt(float x, float y) {
        return showBlockOverlay && inside(x, y, blockOverlayCardX + 188.0F, blockOverlayThroughWallsY(), 24.0F, 24.0F);
    }

    private boolean blockOverlaySmoothSwitchToggleAt(float x, float y) {
        return showBlockOverlay && inside(x, y, blockOverlayCardX + 188.0F, blockOverlaySmoothSwitchY(), 24.0F, 24.0F);
    }

    private boolean blockOverlayFillModeBoxAt(float x, float y) {
        return showBlockOverlay && FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isFillEnabled()
                && inside(x, y, blockOverlayCardX + 112.0F, blockOverlayFillModeY(), 100.0F, 24.0F);
    }

    private boolean blockOverlayShaderBoxAt(float x, float y) {
        BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
        return showBlockOverlay && blockOverlay.isFillEnabled() && blockOverlay.getFillMode() == BlockOverlay.FillMode.SHADER
                && inside(x, y, blockOverlayCardX + 112.0F, blockOverlayShaderY(), 100.0F, 24.0F);
    }

    private BlockOverlay.FillMode blockOverlayFillModeAt(float x, float y) {
        if (!showBlockOverlay || blockOverlayFillModeProgress <= 0.12F) {
            return null;
        }
        float listX = blockOverlayCardX + 112.0F;
        float listY = blockOverlayFillModeY() + 29.0F;
        BlockOverlay.FillMode[] values = BlockOverlay.FillMode.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private BlockOverlay.ShaderType blockOverlayShaderAt(float x, float y) {
        if (!showBlockOverlay || blockOverlayShaderProgress <= 0.12F) {
            return null;
        }
        float listX = blockOverlayCardX + 112.0F;
        float listY = blockOverlayShaderY() + 29.0F;
        BlockOverlay.ShaderType[] values = BlockOverlay.ShaderType.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 100.0F, 22.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private ItemRadius.RadiusItem itemRadiusItemToggleAt(float x, float y) {
        if (!showItemRadius) {
            return null;
        }
        ItemRadius.RadiusItem[] values = ItemRadius.RadiusItem.values();
        for (int i = 0; i < values.length; i++) {
            if (inside(x, y, itemRadiusCardX + 188.0F, itemRadiusContentY() + i * 36.0F, 24.0F, 24.0F)) {
                return values[i];
            }
        }
        return null;
    }

    private boolean itemRadiusDraconicToggleAt(float x, float y) {
        return showItemRadius && inside(x, y, itemRadiusCardX + 188.0F,
                itemRadiusContentY() + ItemRadius.RadiusItem.values().length * 36.0F, 24.0F, 24.0F);
    }

    private boolean animationsTabToggleAt(float x, float y) {
        return showAnimations && inside(x, y, animationsCardX + 188.0F, animationsContentY(), 24.0F, 24.0F);
    }

    private boolean animationsF5ToggleAt(float x, float y) {
        return showAnimations && inside(x, y, animationsCardX + 188.0F, animationsContentY() + 36.0F, 24.0F, 24.0F);
    }

    private boolean animationsHotbarToggleAt(float x, float y) {
        return showAnimations && inside(x, y, animationsCardX + 188.0F, animationsContentY() + 72.0F, 24.0F, 24.0F);
    }

    private boolean animationsInventoryToggleAt(float x, float y) {
        return showAnimations && inside(x, y, animationsCardX + 188.0F, animationsContentY() + 108.0F, 24.0F, 24.0F);
    }

    private boolean freeLookBindButtonAt(float x, float y) {
        return showFreeLook && inside(x, y, freeLookCardX + 164.0F, freeLookContentY() - 1.0F, 48.0F, 24.0F);
    }

    private boolean zoomBindButtonAt(float x, float y) {
        return showZoom && inside(x, y, zoomCardX + 164.0F, zoomContentY() - 1.0F, 48.0F, 24.0F);
    }

    private boolean zoomWheelToggleAt(float x, float y) {
        return showZoom && inside(x, y, zoomCardX + 188.0F, zoomWheelY(), 24.0F, 24.0F);
    }

    private boolean autoSwapBindButtonAt(float x, float y) {
        return showAutoSwap && inside(x, y, autoSwapCardX + 178.0F, autoSwapBindY() - 1.0F, 54.0F, 24.0F);
    }

    private boolean elytraSwapBindButtonAt(float x, float y) {
        return showElytraSwap && inside(x, y, elytraSwapCardX + 178.0F, elytraSwapContentY() - 1.0F, 54.0F, 24.0F);
    }

    private boolean elytraSwapFireworkBindButtonAt(float x, float y) {
        return showElytraSwap && inside(x, y, elytraSwapCardX + 178.0F, elytraSwapFireworkY() - 1.0F, 54.0F, 24.0F);
    }

    private boolean autoSwapFirstBoxAt(float x, float y) {
        return showAutoSwap && inside(x, y, autoSwapCardX + 104.0F, autoSwapContentY() - 1.0F, 128.0F, 24.0F);
    }

    private boolean autoSwapSecondBoxAt(float x, float y) {
        return showAutoSwap && inside(x, y, autoSwapCardX + 104.0F, autoSwapSecondY() - 1.0F, 128.0F, 24.0F);
    }

    private AutoSwap.SwapItem autoSwapFirstItemAt(float x, float y) {
        if (!showAutoSwap || autoSwapFirstProgress <= 0.12F) {
            return null;
        }
        return autoSwapItemAt(x, y, autoSwapCardX + 104.0F, autoSwapContentY() + 28.0F);
    }

    private AutoSwap.SwapItem autoSwapSecondItemAt(float x, float y) {
        if (!showAutoSwap || autoSwapSecondProgress <= 0.12F) {
            return null;
        }
        return autoSwapItemAt(x, y, autoSwapCardX + 104.0F, autoSwapSecondY() + 28.0F);
    }

    private AutoSwap.SwapItem autoSwapItemAt(float x, float y, float listX, float listY) {
        AutoSwap.SwapItem[] items = AutoSwap.SwapItem.values();
        for (int i = 0; i < items.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 128.0F, 22.0F)) {
                return items[i];
            }
        }
        return null;
    }

    private static float autoSwapItemScale(String label) {
        return label.length() > 13 ? 0.66F : 0.78F;
    }

    private boolean freeLookModeButtonAt(float x, float y) {
        return showFreeLook && inside(x, y, freeLookCardX + 126.0F, freeLookContentY() + 39.0F, 86.0F, 24.0F);
    }

    private FreeLook.ActivationMode freeLookModeAt(float x, float y) {
        if (!showFreeLook || freeLookModeProgress <= 0.12F) {
            return null;
        }

        float listX = freeLookCardX + 126.0F;
        float listY = freeLookModeListY();
        FreeLook.ActivationMode[] modes = FreeLook.ActivationMode.values();
        for (int i = 0; i < modes.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 22.0F, 86.0F, 22.0F)) {
                return modes[i];
            }
        }
        return null;
    }

    private boolean crosshairPresetBoxAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 96.0F, crosshairContentY() + 85.0F, 116.0F, 24.0F);
    }

    private Crosshair.Preset crosshairPresetAt(float x, float y) {
        if (!showCrosshair || crosshairPresetProgress <= 0.12F) {
            return null;
        }

        float listX = crosshairCardX + 96.0F;
        float listY = crosshairContentY() + 114.0F;
        Crosshair.Preset[] presets = Crosshair.Preset.values();
        for (int i = 0; i < presets.length; i++) {
            if (inside(x, y, listX, listY + 3.0F + i * 20.0F, 116.0F, 20.0F)) {
                return presets[i];
            }
        }
        return null;
    }

    private boolean crosshairDotToggleAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 188.0F, crosshairDotToggleY(), 24.0F, 24.0F);
    }

    private boolean crosshairOutlineToggleAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 188.0F, crosshairOutlineToggleY(), 24.0F, 24.0F);
    }

    private boolean crosshairRedToggleAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 188.0F, crosshairRedToggleY(), 24.0F, 24.0F);
    }

    private boolean crosshairThirdPersonToggleAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 188.0F, crosshairThirdPersonToggleY(), 24.0F, 24.0F);
    }

    private boolean crosshairColorBoxAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 126.0F, crosshairColorY(), 58.0F, 28.0F);
    }

    private boolean crosshairPaletteAt(float x, float y) {
        return showCrosshair && inside(x, y, crosshairCardX + 15.0F, crosshairPaletteY(), 166.0F, 72.0F);
    }

    private void resetSlider(String id) {
        sliders.remove(id);
        switch (id) {
            case "china_size" -> FluxVisualsClient.MODULE_MANAGER.getChinaHat().setSize(0.9F);
            case "china_shader_alpha" -> FluxVisualsClient.MODULE_MANAGER.getChinaHat().setShaderAlpha(0.82F);
            case "china_hue" -> {
                var chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
                chinaHat.setColor(0.78F, chinaHat.getSaturation(), chinaHat.getValue());
            }
            case "aspect_custom" -> FluxVisualsClient.MODULE_MANAGER.getAspectRatio().setCustomRatio(16.0F / 9.0F);
            case "world_custom_time" -> FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setCustomTime(1000L);
            case "world_fog_distance" -> FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setFogDistance(128.0F);
            case "world_fog_hue" -> {
                WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
                worldCustomizer.setFogColor(0.58F, worldCustomizer.getFogSaturation(), worldCustomizer.getFogValue());
            }
            case "particles_amount" -> FluxVisualsClient.MODULE_MANAGER.getParticles().setAmount(32);
            case "particles_life" -> FluxVisualsClient.MODULE_MANAGER.getParticles().setLifeSeconds(2.4F);
            case "particles_size" -> FluxVisualsClient.MODULE_MANAGER.getParticles().setSize(0.18F);
            case "particles_hue" -> {
                Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
                particles.setColor(0.0F, particles.getSaturation(), particles.getValue());
            }
            case "jump_circles_amount" -> FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setAmount(22);
            case "jump_circles_life" -> FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setLifeSeconds(1.15F);
            case "jump_circles_size" -> FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setSize(1.45F);
            case "jump_circles_particle_size" -> FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setParticleSize(1.0F);
            case "jump_circles_spread" -> FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setSpread(0.55F);
            case "jump_circles_hue" -> {
                JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
                jumpCircles.setColor(0.58F, jumpCircles.getSaturation(), jumpCircles.getValue());
            }
            case "trails_max_length" -> FluxVisualsClient.MODULE_MANAGER.getTrails().setMaxLength(Trails.MAX_LENGTH);
            case "trails_alpha" -> FluxVisualsClient.MODULE_MANAGER.getTrails().setAlpha(Trails.DEFAULT_ALPHA);
            case "trails_hue" -> {
                Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();
                trails.setColor(0.78F, trails.getSaturation(), trails.getValue());
            }
            case "hit_color_hue" -> {
                HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
                hitColor.setColor(0.0F, hitColor.getSaturation(), hitColor.getValue());
            }
            case "hit_color_alpha" -> FluxVisualsClient.MODULE_MANAGER.getHitColor().setAlpha(0.5F);
            case "hitbox_line_thickness" -> FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().setLineThickness(1.8F);
            case "hitbox_fill_alpha" -> FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().setFillAlpha(0.18F);
            case "hitbox_outline_hue" -> {
                HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
                hitboxCustomizer.setOutlineColor(0.02F, hitboxCustomizer.getOutlineSaturation(), hitboxCustomizer.getOutlineValue());
            }
            case "hitbox_fill_hue" -> {
                HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
                hitboxCustomizer.setFillColor(0.02F, hitboxCustomizer.getFillSaturation(), hitboxCustomizer.getFillValue());
            }
            case "block_overlay_line_thickness" -> FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setLineThickness(1.6F);
            case "block_overlay_animation_speed" -> FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setAnimationSpeed(1.0F);
            case "block_overlay_fill_alpha" -> FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setFillAlpha(0.18F);
            case "freelook_distance" -> FluxVisualsClient.MODULE_MANAGER.getFreeLook().setCameraDistance(4.0F);
            case "tab_customizer_columns" -> FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setColumns(4);
            case "tab_customizer_players" -> FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setPlayersPerColumn(20);
            case "tab_customizer_scale" -> FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setScale(1.0F);
            case "block_overlay_outline_hue" -> {
                BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
                blockOverlay.setOutlineColor(0.57F, blockOverlay.getOutlineSaturation(), blockOverlay.getOutlineValue());
            }
            case "block_overlay_fill_hue" -> {
                BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
                blockOverlay.setFillColor(0.57F, blockOverlay.getFillSaturation(), blockOverlay.getFillValue());
            }
            case "crosshair_size" -> FluxVisualsClient.MODULE_MANAGER.getCrosshair().setSize(8.0F);
            case "crosshair_gap" -> FluxVisualsClient.MODULE_MANAGER.getCrosshair().setGap(4.0F);
            case "crosshair_thickness" -> FluxVisualsClient.MODULE_MANAGER.getCrosshair().setThickness(2.0F);
            case "crosshair_opacity" -> FluxVisualsClient.MODULE_MANAGER.getCrosshair().setOpacity(0.92F);
            case "crosshair_hue" -> {
                Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
                crosshair.setColor(0.78F, crosshair.getSaturation(), crosshair.getValue());
            }
            case "item_resorter_enchant_hue" -> {
                ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
                itemResorter.setEnchantColor(0.76F, itemResorter.getEnchantSaturation(), itemResorter.getEnchantValue());
            }
            case "item_resorter_buff_hue" -> {
                ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
                itemResorter.setBuffColor(0.08F, itemResorter.getBuffSaturation(), itemResorter.getBuffValue());
            }
            case "item_resorter_durability" -> FluxVisualsClient.MODULE_MANAGER.getItemResorter().setMinDurabilityPercent(80);
            case "target_distance" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setMaxDistance(8.0F);
            case "target_lost_delay" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setLostDelaySeconds(1.2F);
            case "target_speed" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setAnimationSpeed(1.0F);
            case "target_size" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setNormalSize(1.0F);
            case "target_ghost_size" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostSize(1.0F);
            case "target_ghost_count" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostCount(5);
            case "target_ghost_length" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostLength(1.0F);
            case "target_ghost_speed" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostSpeed(1.0F);
            case "target_crystal_speed" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalSpeed(1.0F);
            case "target_crystal_count" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalCount(6);
            case "target_crystal_size" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalSize(1.0F);
            case "target_crystal_radius" -> FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalRadius(0.9F);
            case "target_hue" -> {
                TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
                targetEsp.setColor(0.56F, targetEsp.getSaturation(), targetEsp.getValue());
            }
            default -> {
            }
        }
    }

    private void updateSlider(String id, float mouseX, float mouseY) {
        SliderState state = sliders.computeIfAbsent(id, ignored -> new SliderState(0.0F));
        if ("target_palette".equals(id)) {
            TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
            float saturation = clamp((mouseX - (targetCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - targetPaletteY()) / 72.0F);
            targetEsp.setColor(targetEsp.getHue(), saturation, value);
            return;
        }
        if ("china_palette".equals(id)) {
            var chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
            float saturation = clamp((mouseX - (chinaCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - chinaPaletteY()) / 72.0F);
            chinaHat.setColor(chinaHat.getHue(), saturation, value);
            return;
        }
        if ("particles_palette".equals(id)) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            float saturation = clamp((mouseX - (particlesCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - (particlesColorY() + 63.0F)) / 72.0F);
            particles.setColor(particles.getHue(), saturation, value);
            return;
        }
        if ("jump_circles_palette".equals(id)) {
            JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
            float saturation = clamp((mouseX - (jumpCirclesCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - jumpCirclesPaletteY()) / 72.0F);
            jumpCircles.setColor(jumpCircles.getHue(), saturation, value);
            return;
        }
        if ("trails_palette".equals(id)) {
            Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();
            float saturation = clamp((mouseX - (trailsCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - trailsPaletteY()) / 72.0F);
            trails.setColor(trails.getHue(), saturation, value);
            return;
        }
        if ("hit_color_palette".equals(id)) {
            HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
            float saturation = clamp((mouseX - (hitColorCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - hitColorPaletteY()) / 72.0F);
            hitColor.setColor(hitColor.getHue(), saturation, value);
            return;
        }
        if ("world_fog_palette".equals(id)) {
            WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
            float saturation = clamp((mouseX - (worldCustomizerCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - worldFogPaletteY()) / 72.0F);
            worldCustomizer.setFogColor(worldCustomizer.getFogHue(), saturation, value);
            return;
        }
        if ("hitbox_outline_palette".equals(id)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            float saturation = clamp((mouseX - (hitboxCustomizerCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - hitboxCustomizerOutlinePaletteY()) / 72.0F);
            hitboxCustomizer.setOutlineColor(hitboxCustomizer.getOutlineHue(), saturation, value);
            return;
        }
        if ("hitbox_fill_palette".equals(id)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            float saturation = clamp((mouseX - (hitboxCustomizerCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - hitboxFillPaletteY()) / 72.0F);
            hitboxCustomizer.setFillColor(hitboxCustomizer.getFillHue(), saturation, value);
            return;
        }
        if ("block_overlay_outline_palette".equals(id)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            float saturation = clamp((mouseX - (blockOverlayCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - blockOverlayOutlinePaletteY()) / 72.0F);
            blockOverlay.setOutlineColor(blockOverlay.getOutlineHue(), saturation, value);
            return;
        }
        if ("block_overlay_fill_palette".equals(id)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            float saturation = clamp((mouseX - (blockOverlayCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - blockOverlayFillPaletteY()) / 72.0F);
            blockOverlay.setFillColor(blockOverlay.getFillHue(), saturation, value);
            return;
        }
        if ("crosshair_palette".equals(id)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            float saturation = clamp((mouseX - (crosshairCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - crosshairPaletteY()) / 72.0F);
            crosshair.setColor(crosshair.getHue(), saturation, value);
            return;
        }
        if ("item_resorter_enchant_palette".equals(id)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            float saturation = clamp((mouseX - (itemResorterCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - itemResorterEnchantPaletteY()) / 72.0F);
            itemResorter.setEnchantColor(itemResorter.getEnchantHue(), saturation, value);
            return;
        }
        if ("item_resorter_buff_palette".equals(id)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            float saturation = clamp((mouseX - (itemResorterCardX + 15.0F)) / 166.0F);
            float value = 1.0F - clamp((mouseY - itemResorterBuffPaletteY()) / 72.0F);
            itemResorter.setBuffColor(itemResorter.getBuffHue(), saturation, value);
            return;
        }

        float start = (id.startsWith("target")) ? targetCardX + 20.0F : chinaCardX + 14.0F;
        float width = (id.startsWith("target")) ? 170.0F : 138.0F;
        if ("target_hue".equals(id)) {
            start = targetCardX + 15.0F;
            width = 166.0F;
        } else if ("china_hue".equals(id)) {
            start = chinaCardX + 15.0F;
            width = 166.0F;
        } else if ("china_shader_alpha".equals(id)) {
            start = chinaCardX + 17.0F;
            width = 158.0F;
        } else if ("particles_hue".equals(id)) {
            start = particlesCardX + 15.0F;
            width = 166.0F;
        } else if ("jump_circles_hue".equals(id)) {
            start = jumpCirclesCardX + 15.0F;
            width = 166.0F;
        } else if ("trails_hue".equals(id)) {
            start = trailsCardX + 15.0F;
            width = 166.0F;
        } else if ("trails_alpha".equals(id)) {
            start = trailsCardX + 20.0F;
            width = 170.0F;
        } else if ("aspect_custom".equals(id)) {
            start = aspectCardX + 17.0F;
            width = 158.0F;
        } else if ("world_custom_time".equals(id)) {
            start = worldCustomizerCardX + 20.0F;
            width = 170.0F;
        } else if ("world_fog_distance".equals(id)) {
            start = worldCustomizerCardX + 20.0F;
            width = 170.0F;
        } else if ("world_fog_hue".equals(id)) {
            start = worldCustomizerCardX + 15.0F;
            width = 166.0F;
        } else if ("hit_color_hue".equals(id)) {
            start = hitColorCardX + 15.0F;
            width = 166.0F;
        } else if ("hit_color_alpha".equals(id)) {
            start = hitColorCardX + 20.0F;
            width = 170.0F;
        } else if ("hitbox_outline_hue".equals(id) || "hitbox_fill_hue".equals(id)) {
            start = hitboxCustomizerCardX + 15.0F;
            width = 166.0F;
        } else if ("block_overlay_outline_hue".equals(id) || "block_overlay_fill_hue".equals(id)) {
            start = blockOverlayCardX + 15.0F;
            width = 166.0F;
        } else if ("crosshair_hue".equals(id)) {
            start = crosshairCardX + 15.0F;
            width = 166.0F;
        } else if ("item_resorter_enchant_hue".equals(id) || "item_resorter_buff_hue".equals(id)) {
            start = itemResorterCardX + 15.0F;
            width = 166.0F;
        } else if ("item_resorter_durability".equals(id)) {
            start = itemResorterCardX + 83.0F;
            width = 150.0F;
        } else if (id.startsWith("crosshair_")) {
            start = crosshairCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("particles_")) {
            start = particlesCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("jump_circles_")) {
            start = jumpCirclesCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("trails_")) {
            start = trailsCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("zoom_")) {
            start = zoomCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("freelook_")) {
            start = freeLookCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("hitbox_")) {
            start = hitboxCustomizerCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("block_overlay_")) {
            start = blockOverlayCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("tab_customizer_")) {
            start = tabCustomizerCardX + 20.0F;
            width = 170.0F;
        } else if (id.startsWith("wings_")) {
            start = wingsCardX + 20.0F;
            width = 170.0F;
        } else if ("targethud_scale".equals(id)) {
            start = targetHudCardX + 20.0F;
            width = 170.0F;
        } else if ("tapemouse_delay".equals(id)) {
            start = tapeMouseCardX + 20.0F;
            width = 170.0F;
        } else if ("anarchy_cooldown".equals(id)) {
            start = anarchySwitcherCardX + 20.0F;
            width = 170.0F;
        } else if ("auto_resell_afk_chat_interval".equals(id)) {
            start = autoResellAfkCardX + 20.0F;
            width = 210.0F;
        }
        state.value = clamp((mouseX - start) / width);
        if ("target_distance".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setMaxDistance(2.0F + state.value * 30.0F);
        } else if ("target_lost_delay".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setLostDelaySeconds(0.2F + state.value * 3.8F);
        } else if ("target_speed".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setAnimationSpeed(0.1F + state.value * 3.9F);
        } else if ("target_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setNormalSize(0.45F + state.value * 1.55F);
        } else if ("target_ghost_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostSize(
                    TargetEsp.GHOST_SIZE_MIN + state.value * TargetEsp.GHOST_SIZE_RANGE);
        } else if ("target_ghost_count".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostCount(Math.round(1.0F + state.value * 11.0F));
        } else if ("target_ghost_length".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostLength(
                    TargetEsp.GHOST_LENGTH_MIN + state.value * TargetEsp.GHOST_LENGTH_RANGE);
        } else if ("target_ghost_speed".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setGhostSpeed(0.2F + state.value * 4.8F);
        } else if ("target_crystal_speed".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalSpeed(
                    TargetEsp.CRYSTAL_SPEED_MIN + state.value * TargetEsp.CRYSTAL_SPEED_RANGE);
        } else if ("target_crystal_count".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalCount(Math.round(TargetEsp.CRYSTAL_COUNT_MIN
                    + state.value * (TargetEsp.CRYSTAL_COUNT_MAX - TargetEsp.CRYSTAL_COUNT_MIN)));
        } else if ("target_crystal_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalSize(
                    TargetEsp.CRYSTAL_SIZE_MIN + state.value * TargetEsp.CRYSTAL_SIZE_RANGE);
        } else if ("target_crystal_radius".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setCrystalRadius(
                    TargetEsp.CRYSTAL_RADIUS_MIN + state.value * TargetEsp.CRYSTAL_RADIUS_RANGE);
        } else if ("target_hue".equals(id)) {
            TargetEsp targetEsp = FluxVisualsClient.MODULE_MANAGER.getTargetEsp();
            targetEsp.setColor(state.value, targetEsp.getSaturation(), targetEsp.getValue());
        } else if ("china_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getChinaHat().setSize(state.value);
        } else if ("china_shader_alpha".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getChinaHat().setShaderAlpha(state.value);
        } else if ("china_hue".equals(id)) {
            var chinaHat = FluxVisualsClient.MODULE_MANAGER.getChinaHat();
            chinaHat.setColor(state.value, chinaHat.getSaturation(), chinaHat.getValue());
        } else if ("wings_scale".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getWings().setScale(0.3F + state.value * 2.7F);
        } else if ("wings_strength".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getWings().setFlapStrength(5.0F + state.value * 55.0F);
        } else if ("wings_speed".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getWings().setFlapSpeed(0.5F + state.value * 7.5F);
        } else if ("particles_hue".equals(id)) {
            Particles particles = FluxVisualsClient.MODULE_MANAGER.getParticles();
            particles.setColor(state.value, particles.getSaturation(), particles.getValue());
        } else if ("trails_hue".equals(id)) {
            Trails trails = FluxVisualsClient.MODULE_MANAGER.getTrails();
            trails.setColor(state.value, trails.getSaturation(), trails.getValue());
        } else if ("trails_alpha".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTrails().setAlpha(state.value * Trails.MAX_ALPHA);
        } else if ("aspect_custom".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getAspectRatio().setCustomRatio(1.0F + state.value * 1.5F);
        } else if ("world_custom_time".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setCustomTime(Math.round(state.value * 23999.0F));
        } else if ("world_fog_distance".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setFogDistance(8.0F + state.value * 142.0F);
        } else if ("world_fog_hue".equals(id)) {
            WorldCustomizer worldCustomizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
            worldCustomizer.setFogColor(state.value, worldCustomizer.getFogSaturation(), worldCustomizer.getFogValue());
        } else if ("hit_color_hue".equals(id)) {
            HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
            hitColor.setColor(state.value, hitColor.getSaturation(), hitColor.getValue());
        } else if ("hit_color_alpha".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getHitColor().setAlpha(state.value);
        } else if ("freelook_distance".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getFreeLook().setCameraDistance(2.0F + state.value * 10.0F);
        } else if ("tab_customizer_columns".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setColumns(Math.round(1.0F + state.value * 7.0F));
        } else if ("tab_customizer_players".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setPlayersPerColumn(Math.round(1.0F + state.value * 29.0F));
        } else if ("tab_customizer_scale".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setScale(0.75F + state.value * 0.5F);
        } else if ("hitbox_outline_hue".equals(id)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setOutlineColor(state.value, hitboxCustomizer.getOutlineSaturation(), hitboxCustomizer.getOutlineValue());
        } else if ("hitbox_fill_hue".equals(id)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setFillColor(state.value, hitboxCustomizer.getFillSaturation(), hitboxCustomizer.getFillValue());
        } else if ("hitbox_line_thickness".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().setLineThickness(1.0F + state.value * 4.0F);
        } else if ("hitbox_fill_alpha".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().setFillAlpha(state.value);
        } else if ("block_overlay_outline_hue".equals(id)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            blockOverlay.setOutlineColor(state.value, blockOverlay.getOutlineSaturation(), blockOverlay.getOutlineValue());
        } else if ("block_overlay_fill_hue".equals(id)) {
            BlockOverlay blockOverlay = FluxVisualsClient.MODULE_MANAGER.getBlockOverlay();
            blockOverlay.setFillColor(state.value, blockOverlay.getFillSaturation(), blockOverlay.getFillValue());
        } else if ("block_overlay_line_thickness".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setLineThickness(1.0F + state.value * 4.0F);
        } else if ("block_overlay_animation_speed".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setAnimationSpeed(0.1F + state.value * 3.9F);
        } else if ("block_overlay_fill_alpha".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setFillAlpha(state.value);
        } else if ("crosshair_hue".equals(id)) {
            Crosshair crosshair = FluxVisualsClient.MODULE_MANAGER.getCrosshair();
            crosshair.setColor(state.value, crosshair.getSaturation(), crosshair.getValue());
        } else if ("item_resorter_enchant_hue".equals(id)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setEnchantColor(state.value, itemResorter.getEnchantSaturation(), itemResorter.getEnchantValue());
        } else if ("item_resorter_buff_hue".equals(id)) {
            ItemResorter itemResorter = FluxVisualsClient.MODULE_MANAGER.getItemResorter();
            itemResorter.setBuffColor(state.value, itemResorter.getBuffSaturation(), itemResorter.getBuffValue());
        } else if ("item_resorter_durability".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getItemResorter().setMinDurabilityPercent(Math.round(1.0F + state.value * 99.0F));
        } else if ("crosshair_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().setSize(3.0F + state.value * 15.0F);
        } else if ("crosshair_gap".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().setGap(state.value * 12.0F);
        } else if ("crosshair_thickness".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().setThickness(1.0F + state.value * 4.0F);
        } else if ("crosshair_opacity".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().setOpacity(state.value);
        } else if ("particles_amount".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setAmount(Math.round(1.0F + state.value * 119.0F));
        } else if ("particles_life".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setLifeSeconds(0.4F + state.value * 5.6F);
        } else if ("particles_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setSize(0.04F + state.value * 0.51F);
        } else if ("jump_circles_hue".equals(id)) {
            JumpCircles jumpCircles = FluxVisualsClient.MODULE_MANAGER.getJumpCircles();
            jumpCircles.setColor(state.value, jumpCircles.getSaturation(), jumpCircles.getValue());
        } else if ("jump_circles_amount".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setAmount(Math.round(1.0F + state.value * 95.0F));
        } else if ("jump_circles_life".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setLifeSeconds(0.25F + state.value * 3.75F);
        } else if ("jump_circles_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setSize(0.35F + state.value * 2.65F);
        } else if ("jump_circles_particle_size".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setParticleSize(0.25F + state.value * 2.25F);
        } else if ("jump_circles_spread".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setSpread(state.value);
        } else if ("trails_max_length".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTrails().setMaxLength(Trails.MIN_LENGTH + state.value * (Trails.MAX_LENGTH - Trails.MIN_LENGTH));
        } else if ("zoom_smoothness".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getZoom().setSmoothness(state.value);
        } else if ("targethud_scale".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetHud().setScale(0.75F + state.value * 1.05F);
        } else if ("tapemouse_delay".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getTapeMouse().setDelayMs(Math.round(10.0F + state.value * 990.0F));
        } else if ("anarchy_cooldown".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setDelaySeconds(0.25F + state.value * 299.75F);
        } else if ("auto_resell_afk_chat_interval".equals(id)) {
            FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK().setChatIntervalMs(5_000L + Math.round(state.value * 995_000L));
        }
    }

    private void shadow(DrawContext context, float x, float y, float w, float h, float r, float alpha) {
        if (alpha <= 0.0F || w <= 0.0F || h <= 0.0F) {
            return;
        }
        Render2D.drawShadow(context, sx(x), sy(y) + 2.0F * scale, w * scale, h * scale, r * scale, 8.0F * scale, color(APP_BACKGROUND, 120, alpha));
    }

    private void glow(DrawContext context, float x, float y, float w, float h, float r, int color) {
        int alpha = color >>> 24;
        if (alpha == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }
        Render2D.drawGlow(context, sx(x), sy(y), w * scale, h * scale, r * scale, 9.0F * scale, color);
    }

    private void outline(DrawContext context, float x, float y, float w, float h, float r, int color, float thickness) {
        int alpha = color >>> 24;
        if (alpha == 0 || w <= 0.0F || h <= 0.0F || thickness <= 0.0F) {
            return;
        }
        Render2D.drawRoundOutline(context, sx(x), sy(y), w * scale, h * scale, r * scale, thickness * scale, color);
    }

    private void rounded(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        int alpha = color >>> 24;
        if (alpha == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }
        Render2D.drawRound(context, sx(x), sy(y), w * scale, h * scale, radius * scale, color);
    }

    private static Identifier paletteTexture(float hue) {
        int key = Math.round(clamp(hue) * 360.0F);
        Identifier cached = PALETTE_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        int width = 166;
        int height = 72;
        int radius = 6;
        NativeImage image = new NativeImage(width, height, false);
        float baseHue = key / 360.0F;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float saturation = x / (float) (width - 1);
                float brightness = 1.0F - y / (float) (height - 1);
                int rgb = java.awt.Color.HSBtoRGB(baseHue, saturation, brightness);
                int alpha = Math.round(coverage(width, height, radius, 0, x, y) * 255.0F);
                image.setColorArgb(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/palette_" + key);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        PALETTE_TEXTURES.put(key, id);
        return id;
    }

    private static Identifier hueTexture() {
        if (hueSliderTexture != null) {
            return hueSliderTexture;
        }

        int width = 166;
        int height = 10;
        int radius = 5;
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float hue = x / (float) (width - 1);
                int rgb = java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F);
                int alpha = Math.round(coverage(width, height, radius, 0, x, y) * 255.0F);
                image.setColorArgb(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/hue_slider");
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        hueSliderTexture = id;
        return id;
    }

    private static Identifier roundedTexture(int width, int height, int radius, int outline) {
        RoundKey key = new RoundKey(width, height, radius, outline);
        Identifier cached = ROUND_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float coverage = coverage(width, height, radius, outline, x, y);
                int alpha = Math.round(coverage * 255.0F);
                image.setColorArgb(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/round_" + width + "_" + height + "_" + radius + "_" + outline);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        ROUND_TEXTURES.put(key, id);
        return id;
    }

    private static float coverage(int width, int height, int radius, int outline, int x, int y) {
        int hits = 0;
        int total = AA_SAMPLES * AA_SAMPLES;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                float px = x + (sx + 0.5F) / AA_SAMPLES;
                float py = y + (sy + 0.5F) / AA_SAMPLES;
                boolean outer = insideRound(width, height, radius, px, py);
                boolean inner = outline <= 0 || !insideRound(width - outline * 2, height - outline * 2,
                        Math.max(0, radius - outline), px - outline, py - outline);
                if (outer && inner) {
                    hits++;
                }
            }
        }
        return hits / (float) total;
    }

    private static boolean insideRound(int width, int height, int radius, float x, float y) {
        if (width <= 0 || height <= 0 || x < 0.0F || y < 0.0F || x >= width || y >= height) {
            return false;
        }
        if (radius <= 0) {
            return true;
        }
        float cx = clamp(x, radius, width - radius);
        float cy = clamp(y, radius, height - radius);
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void fill(DrawContext context, float x, float y, float w, float h, int color) {
        int alpha = color >>> 24;
        if (alpha == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }
        Render2D.drawRound(context, sx(x), sy(y), w * scale, h * scale, 0.0F, color);
    }

    private void drawCardSettingsIcon(DrawContext context, float x, float y, float alpha) {
        rounded(context, x - 2.0F, y - 2.0F, 22.0F, 22.0F, 5.0F, applyAlpha(0xFF3B4261, alpha));
        outline(context, x - 2.0F, y - 2.0F, 22.0F, 22.0F, 5.0F, applyAlpha(0xFF8B5CF6, alpha), 1.0F);
        drawIcon(context, SETTINGS_ICON, x, y, 18.0F, 18.0F, applyAlpha(0xFFFFFFFF, alpha));
    }

    private void drawIcon(DrawContext context, Identifier id, float x, float y, float w, float h, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, whiteIcon(id), Math.round(sx(x)), Math.round(sy(y)), 0.0F, 0.0F,
                Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), 24, 24, 24, 24, color);
    }

    private static Identifier whiteIcon(Identifier source) {
        Identifier cached = WHITE_ICONS.get(source);
        if (cached != null) {
            return cached;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        try (InputStream stream = client.getResourceManager().open(source);
             NativeImage image = NativeImage.read(stream)) {
            NativeImage white = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = image.getColorArgb(x, y) >>> 24;
                    white.setColorArgb(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }

            Identifier id = Identifier.of("fluxvisuals", "dynamic/white_" + source.getPath().replace('/', '_'));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), white);
            client.getTextureManager().registerTexture(id, texture);
            texture.upload();
            WHITE_ICONS.put(source, id);
            return id;
        } catch (IOException | RuntimeException exception) {
            return source;
        }
    }

    private void drawText(DrawContext context, String value, float x, float y, float textScale, int color) {
        drawText(context, value, x, y, textScale, color, FontRole.INTER);
    }

    private void drawText(DrawContext context, String value, float x, float y, float textScale, int color, FontRole role) {
        TextTexture text = textTexture(value, Math.max(11, Math.round(9.5F * scale * textScale)), role);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, text.id, Math.round(sx(x)), Math.round(sy(y)),
                0.0F, 0.0F, text.width, text.height, text.textureWidth, text.textureHeight,
                text.textureWidth, text.textureHeight, color);
    }

    private void drawCenteredText(DrawContext context, String value, float x, float y, float w, float h, float textScale, int color) {
        drawCenteredText(context, value, x, y, w, h, textScale, color, FontRole.INTER);
    }

    private void drawCenteredText(DrawContext context, String value, float x, float y, float w, float h, float textScale, int color, FontRole role) {
        TextTexture text = textTexture(value, Math.max(11, Math.round(9.5F * scale * textScale)), role);
        drawText(context, value, x + ((w * scale - text.width) / scale) * 0.5F,
                y + ((h * scale - text.height) / scale) * 0.5F, textScale, color, role);
    }

    private void drawLeftCenteredText(DrawContext context, String value, float x, float y, float w, float h, float textScale, int color) {
        TextTexture text = textTexture(value, Math.max(11, Math.round(9.5F * scale * textScale)), FontRole.INTER);
        drawText(context, value, x, y + ((h * scale - text.height) / scale) * 0.5F - 2.0F, textScale, color, FontRole.INTER);
    }

    private void syncModuleStates() {
        for (VisualModule module : modules) {
            module.active = isModuleActive(module.name, module.active);
        }
    }

    private VisualModule moduleByName(String name) {
        for (VisualModule module : modules) {
            if (module.name.equals(name)) {
                return module;
            }
        }
        return null;
    }

    private static void toggleModuleState(String name) {
        setModuleState(name, !isModuleActive(name, defaultModuleState(name)));
    }

    private static void toggleBoundAction(String key) {
        // Without a license functions stay off and the player is told why (once per cooldown).
        if (!LicenseManager.isLicensed) {
            LicenseManager.canOpenGui(MinecraftClient.getInstance());
            return;
        }
        if (HITBOX_ALWAYS_SHOW_BIND_KEY.equals(key)) {
            HitboxCustomizer hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
            hitboxCustomizer.setAlwaysShow(!hitboxCustomizer.isAlwaysShow());
            return;
        }
        if (AutoSwap.BIND_KEY.equals(key)) {
            FluxVisualsClient.MODULE_MANAGER.getAutoSwap().triggerSwap();
            return;
        }
        if (ElytraSwap.BIND_KEY.equals(key)) {
            FluxVisualsClient.MODULE_MANAGER.getElytraSwap().triggerSwap();
            return;
        }
        if (ElytraSwap.FIREWORK_BIND_KEY.equals(key)) {
            FluxVisualsClient.MODULE_MANAGER.getElytraSwap().triggerFirework();
            return;
        }
        toggleModuleState(key);
    }

    private static void setModuleState(String name, boolean active) {
        REMEMBERED_MODULE_STATES.put(name, active);
        if ("FullBright".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getFullBright().setEnabled(active);
        } else if ("China Hat".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getChinaHat().setEnabled(active);
        } else if ("Wings".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getWings().setEnabled(active);
        } else if ("Aspect Ratio".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAspectRatio().setEnabled(active);
        } else if ("Removals".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getRemovals().setEnabled(active);
        } else if ("Particles".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().setEnabled(active);
        } else if ("JumpCircles".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getJumpCircles().setEnabled(active);
        } else if ("Trails".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTrails().setEnabled(active);
        } else if ("World Customizer".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().setEnabled(active);
        } else if ("Hit Color".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getHitColor().setEnabled(active);
        } else if ("Hitbox Customizer".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().setEnabled(active);
        } else if ("BlockOverlay".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().setEnabled(active);
        } else if ("TargetEsp".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetEsp().setEnabled(active);
        } else if ("ItemRadius".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getItemRadius().setEnabled(active);
        } else if ("Animations".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAnimations().setEnabled(active);
        } else if ("TabCustomizer".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().setEnabled(active);
        } else if ("SafeNametag".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getSafeNametag().setEnabled(active);
        } else if ("FakePlayer".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getFakePlayer().setEnabled(active);
        } else if ("AutoSprint".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAutoSprint().setEnabled(active);
        } else if ("ItemSwap".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAutoSwap().setEnabled(active);
        } else if ("ElytraSwap".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getElytraSwap().setEnabled(active);
        } else if ("ItemResorter".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getItemResorter().setEnabled(active);
        } else if ("NameProtect".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getNameProtect().setEnabled(active);
        } else if ("ItemScroller".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getItemScroller().setEnabled(active);
        } else if ("TargetHud".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTargetHud().setEnabled(active);
        } else if ("TapeMouse".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTapeMouse().setEnabled(active);
        } else if ("AutoResell".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAutoResell().setEnabled(active);
        } else if ("AutoResellAFK".equals(name)) {
            FluxVisualsClient.MULTI_BOT_MANAGER.getAutoResellAFKForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK()).setEnabled(active);
        } else if ("AHHelper".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAHHelper().setEnabled(active);
        } else if ("AutoBuy".equals(name)) {
            FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getAutoBuy()).setEnabled(active);
        } else if ("ItemCrafter".equals(name)) {
            FluxVisualsClient.MULTI_BOT_MANAGER.getItemCrafterForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getItemCrafter()).setEnabled(active);
        } else if ("CaptchaSolver".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver().setEnabled(active);
        } else if ("TG".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTelegram().setEnabled(active);
        } else if ("DiscordRPC".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getDiscordRPC().setEnabled(active);
        } else if ("AnarchySwitcher".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().setEnabled(active);
        } else if ("NameBinder".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getNameBind().setEnabled(active);
        } else if ("Macros".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getMacros().setEnabled(active);
        } else if ("TrapTracker".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getTrapTracker().setEnabled(active);
        } else if ("FreeLook".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getFreeLook().setEnabled(active);
        } else if ("Zoom".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getZoom().setEnabled(active);
        } else if ("Crosshair".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().setEnabled(active);
        } else if ("Watermark".equals(name)) {
            FluxVisualsClient.MODULE_MANAGER.getWatermark().setEnabled(active);
        } else {
            FluxVisualsClient.requestConfigSave();
        }
    }

    private static boolean isModuleActive(String name, boolean fallback) {
        if ("FullBright".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getFullBright().isEnabled();
        }
        if ("China Hat".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getChinaHat().isEnabled();
        }
        if ("Wings".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getWings().isEnabled();
        }
        if ("Aspect Ratio".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAspectRatio().isEnabled();
        }
        if ("Removals".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getRemovals().isEnabled();
        }
        if ("Particles".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getParticles().isEnabled();
        }
        if ("JumpCircles".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getJumpCircles().isEnabled();
        }
        if ("Trails".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTrails().isEnabled();
        }
        if ("World Customizer".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer().isEnabled();
        }
        if ("Hit Color".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getHitColor().isEnabled();
        }
        if ("Hitbox Customizer".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer().isEnabled();
        }
        if ("BlockOverlay".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getBlockOverlay().isEnabled();
        }
        if ("TargetEsp".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTargetEsp().isEnabled();
        }
        if ("ItemRadius".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getItemRadius().isEnabled();
        }
        if ("Animations".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAnimations().isEnabled();
        }
        if ("TabCustomizer".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTabCustomizer().isEnabled();
        }
        if ("SafeNametag".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getSafeNametag().isEnabled();
        }
        if ("FakePlayer".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getFakePlayer().isEnabled();
        }
        if ("AutoSprint".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAutoSprint().isEnabled();
        }
        if ("ItemSwap".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAutoSwap().isEnabled();
        }
        if ("ElytraSwap".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getElytraSwap().isEnabled();
        }
        if ("ItemResorter".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getItemResorter().isEnabled();
        }
        if ("TargetHud".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTargetHud().isEnabled();
        }
        if ("TapeMouse".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTapeMouse().isEnabled();
        }
        if ("AutoResell".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAutoResell().isEnabled();
        }
        if ("AutoResellAFK".equals(name)) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoResellAFKForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getAutoResellAFK()).isEnabled();
        }
        if ("AHHelper".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAHHelper().isEnabled();
        }
        if ("AutoBuy".equals(name)) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getAutoBuyForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getAutoBuy()).isEnabled();
        }
        if ("ItemCrafter".equals(name)) {
            return FluxVisualsClient.MULTI_BOT_MANAGER.getItemCrafterForCurrentSession(
                    FluxVisualsClient.MODULE_MANAGER.getItemCrafter()).isEnabled();
        }
        if ("CaptchaSolver".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getCaptchaSolver().isEnabled();
        }
        if ("TG".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTelegram().isEnabled();
        }
        if ("DiscordRPC".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getDiscordRPC().isEnabled();
        }
        if ("AnarchySwitcher".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getAnarchySwitcher().isEnabled();
        }
        if ("NameBinder".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getNameBind().isEnabled();
        }
        if ("Macros".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getMacros().isEnabled();
        }
        if ("TrapTracker".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getTrapTracker().isEnabled();
        }
        if ("FreeLook".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getFreeLook().isEnabled();
        }
        if ("Zoom".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getZoom().isEnabled();
        }
        if ("Crosshair".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getCrosshair().isEnabled();
        }
        if ("Watermark".equals(name)) {
            return FluxVisualsClient.MODULE_MANAGER.getWatermark().isEnabled();
        }
        return REMEMBERED_MODULE_STATES.getOrDefault(name, fallback);
    }

    private static boolean defaultModuleState(String name) {
        return switch (name) {
            case "FullBright", "Particles", "Hit Color", "Crosshair" -> true;
            default -> false;
        };
    }

    private static String keyLabel(int keyCode) {
        if (isMouseBind(keyCode)) {
            return "M" + (mouseButton(keyCode) + 1);
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('A' + keyCode - GLFW.GLFW_KEY_A));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) ('0' + keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPC";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENT";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "ALT";
            case GLFW.GLFW_KEY_BACKSPACE -> "BSP";
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RGHT";
            default -> {
                String name = GLFW.glfwGetKeyName(keyCode, 0);
                yield name == null ? "" : name.toUpperCase(Locale.ROOT);
            }
        };
    }

    private static int mouseBind(int button) {
        return MOUSE_BIND_OFFSET - button;
    }

    private static boolean isMouseBind(int code) {
        return code <= MOUSE_BIND_OFFSET - GLFW.GLFW_MOUSE_BUTTON_1
                && code >= MOUSE_BIND_OFFSET - GLFW.GLFW_MOUSE_BUTTON_8;
    }

    private static int mouseButton(int code) {
        return MOUSE_BIND_OFFSET - code;
    }

    private static TextTexture textTexture(String value, int pixelSize, FontRole role) {
        String safeValue = value == null || value.isEmpty() ? " " : value;
        TextKey key = new TextKey(safeValue, pixelSize, role);
        TextTexture cached = TEXT_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        int renderSize = pixelSize * TEXT_OVERSAMPLE;
        Font font = font(role);
        if (font.canDisplayUpTo(safeValue) != -1) {
            font = new Font(role.fallbackFamily, role.style, renderSize);
        } else {
            font = font.deriveFont(role.style, renderSize);
        }
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measure.createGraphics();
        measureGraphics.setFont(font);
        measureGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measureGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int highWidth = Math.max(1, metrics.stringWidth(safeValue) + 8);
        int highHeight = Math.max(1, metrics.getAscent() + metrics.getDescent() + 8);
        int ascent = metrics.getAscent();
        measureGraphics.dispose();

        BufferedImage highImage = new BufferedImage(highWidth, highHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highImage.createGraphics();
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.drawString(safeValue, 4, ascent + 4);
        graphics.dispose();

        int width = Math.max(1, Math.round(highWidth / (float) TEXT_OVERSAMPLE));
        int height = Math.max(1, Math.round(highHeight / (float) TEXT_OVERSAMPLE));

        NativeImage nativeImage = new NativeImage(highWidth, highHeight, false);
        for (int y = 0; y < highHeight; y++) {
            for (int x = 0; x < highWidth; x++) {
                nativeImage.setColorArgb(x, y, highImage.getRGB(x, y));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "dynamic/text_" + role.name().toLowerCase(Locale.ROOT) + "_"
                + Integer.toHexString(safeValue.hashCode()) + "_" + pixelSize);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        texture.setFilter(false, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        TextTexture text = new TextTexture(id, width, height, highWidth, highHeight);
        TEXT_TEXTURES.put(key, text);
        return text;
    }

    private static Font font(FontRole role) {
        Font cached = FONTS.get(role);
        if (cached != null) {
            return cached;
        }

        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(role.id())) {
            cached = Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (FontFormatException | IOException | RuntimeException exception) {
            cached = new Font(role.fallbackFamily, role.style, 16);
        }
        FONTS.put(role, cached);
        return cached;
    }

    private float sx(float x) {
        return originX + x * scale;
    }

    private float sy(float y) {
        return originY + y * scale;
    }

    private float baseX(double x) {
        return (float) ((x - originX) / scale);
    }

    private float baseY(double y) {
        return (float) ((y - originY) / scale);
    }

    private float openingProgress() {
        return 1.0F - (float) Math.pow(1.0F - clamp((System.nanoTime() - openedAt) / (float) OPEN_NS), 3.0D);
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && py >= y && px <= x + w && py <= y + h;
    }

    private static int argb(int a, int r, int g, int b, float alpha) {
        return (Math.round(clamp(a / 255.0F * alpha) * 255.0F) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int color(ColorToken token, int a, float alpha) {
        return argb(a, token.r, token.g, token.b, alpha);
    }

    private static int mix(ColorToken from, ColorToken to, float amount, int a, float alpha) {
        float t = clamp(amount);
        int r = Math.round(from.r + (to.r - from.r) * t);
        int g = Math.round(from.g + (to.g - from.g) * t);
        int b = Math.round(from.b + (to.b - from.b) * t);
        return argb(a, r, g, b, alpha);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 255) * clamp(alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * dt));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float number(Properties properties, String key, float fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private enum FontRole {
        INTER("font/inter_18pt-medium.ttf", Font.PLAIN, "SansSerif"),
        SF_PRO("font/sfprodisplaymedium.ttf", Font.BOLD, "SansSerif"),
        MONO("font/jetbrainsmono-medium.ttf", Font.PLAIN, "Monospaced"),
        OXANIUM("font/oxanium-semibold.ttf", Font.PLAIN, "SansSerif");

        private final String path;
        private final int style;
        private final String fallbackFamily;

        FontRole(String path, int style, String fallbackFamily) {
            this.path = path;
            this.style = style;
            this.fallbackFamily = fallbackFamily;
        }

        private Identifier id() {
            return Identifier.of("fluxvisuals", path);
        }
    }

    private enum Category {
        VISUALS("Visuals"),
        HUD("Hud"),
        UTILS("Utils");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private static final class VisualModule {
        private final String name;
        private final String hint;
        private final Category category;
        private boolean active;
        private float hover;
        private float toggle;

        private VisualModule(String name, String hint, Category category, boolean active) {
            this.name = name;
            this.hint = hint;
            this.category = category;
            this.active = REMEMBERED_MODULE_STATES.getOrDefault(name, active);
            this.toggle = this.active ? 1.0F : 0.0F;
        }
    }

    private static final class SliderState {
        private float value;
        private float visual;

        private SliderState(float value) {
            this.value = value;
            this.visual = value;
        }
    }

    private record RoundKey(int width, int height, int radius, int outline) {
    }

    private record TextKey(String value, int pixelSize, FontRole role) {
    }

    private record ColorToken(int r, int g, int b) {
    }

    private enum ItemResorterTab {
        ENCHANTS("Р§Р°СЂС‹"),
        BUFFS("Р‘Р°С„С„С‹");

        private final String label;

        ItemResorterTab(String label) {
            this.label = label;
        }
    }

    private enum ItemResorterInput {
        ENCHANT_NEEDED("Нужные"),
        ENCHANT_IGNORED("Игнор"),
        BUFF_NEEDED("Нужные"),
        BUFF_IGNORED("Игнор");

        private final String rowLabel;

        ItemResorterInput(String rowLabel) {
            this.rowLabel = rowLabel;
        }

        private String rowLabel() {
            return rowLabel;
        }
    }

    private record ItemResorterAddTarget(ItemResorterInput input) {
    }

    private record ItemResorterChipAction(ItemResorterInput input, String value) {
    }

    private record ItemResorterChipLayout(ItemResorterInput input, String value, float x, float y, float w, float h) {
    }

    private record TextTexture(Identifier id, int width, int height, int textureWidth, int textureHeight) {
    }
}
