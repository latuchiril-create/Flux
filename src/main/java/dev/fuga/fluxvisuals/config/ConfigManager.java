package dev.fuga.fluxvisuals.config;

import dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer;
import dev.fuga.fluxvisuals.modules.ModuleManager;
import dev.fuga.fluxvisuals.modules.visual.AspectRatio;
import dev.fuga.fluxvisuals.modules.visual.Animations;
import dev.fuga.fluxvisuals.modules.visual.AutoSwap;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.AutoSprint;
import dev.fuga.fluxvisuals.modules.visual.ElytraSwap;
import dev.fuga.fluxvisuals.modules.visual.BlockOverlay;
import dev.fuga.fluxvisuals.modules.visual.ChinaHat;
import dev.fuga.fluxvisuals.modules.visual.Wings;
import dev.fuga.fluxvisuals.modules.visual.Crosshair;
import dev.fuga.fluxvisuals.modules.visual.FakePlayer;
import dev.fuga.fluxvisuals.modules.visual.FreeLook;
import dev.fuga.fluxvisuals.modules.visual.HitColor;
import dev.fuga.fluxvisuals.modules.visual.HitboxCustomizer;
import dev.fuga.fluxvisuals.modules.visual.ItemRadius;
import dev.fuga.fluxvisuals.modules.visual.ItemResorter;
import dev.fuga.fluxvisuals.modules.visual.ItemCrafter;
import dev.fuga.fluxvisuals.modules.visual.JumpCircles;
import dev.fuga.fluxvisuals.modules.visual.Particles;
import dev.fuga.fluxvisuals.modules.visual.Removals;
import dev.fuga.fluxvisuals.modules.visual.SafeNametag;
import dev.fuga.fluxvisuals.modules.visual.TabCustomizer;
import dev.fuga.fluxvisuals.modules.visual.Telegram;
import dev.fuga.fluxvisuals.modules.visual.TargetHud;
import dev.fuga.fluxvisuals.modules.visual.TargetEsp;
import dev.fuga.fluxvisuals.modules.visual.Trails;
import dev.fuga.fluxvisuals.modules.visual.Watermark;
import dev.fuga.fluxvisuals.modules.visual.WorldCustomizer;
import dev.fuga.fluxvisuals.modules.visual.Zoom;
import dev.fuga.fluxvisuals.modules.visual.Macros;
import dev.fuga.fluxvisuals.modules.visual.NameBind;
import dev.fuga.fluxvisuals.modules.combat.AimBot;
import dev.fuga.fluxvisuals.modules.combat.TriggerBot;
import dev.fuga.fluxvisuals.modules.combat.AutoMace;
import dev.fuga.fluxvisuals.modules.combat.NoJumpDelay;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
    private static final long SAVE_DELAY_MS = 750L;

    private final ModuleManager moduleManager;
    private final Path path;
    private boolean dirty;
    private boolean loading;
    private boolean saveAfterLoad;
    private long dirtyAt;

    public ConfigManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.path = FabricLoader.getInstance().getConfigDir().resolve("fluxvisuals.properties");
    }

    public void load() {
        if (!Files.exists(path)) {
            return;
        }

        Properties properties = new Properties();
        loading = true;
        saveAfterLoad = false;
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
            moduleManager.getFullBright().setEnabled(bool(properties, "module.fullbright.enabled", moduleManager.getFullBright().isEnabled()));
            moduleManager.getChinaHat().setEnabled(bool(properties, "module.china_hat.enabled", moduleManager.getChinaHat().isEnabled()));
            moduleManager.getWings().setEnabled(bool(properties, "module.wings.enabled", moduleManager.getWings().isEnabled()));
            moduleManager.getAspectRatio().setEnabled(bool(properties, "module.aspect_ratio.enabled", moduleManager.getAspectRatio().isEnabled()));
            moduleManager.getParticles().setEnabled(bool(properties, "module.particles.enabled", moduleManager.getParticles().isEnabled()));
            moduleManager.getJumpCircles().setEnabled(bool(properties, "module.jump_circles.enabled", moduleManager.getJumpCircles().isEnabled()));
            moduleManager.getTrails().setEnabled(bool(properties, "module.trails.enabled", moduleManager.getTrails().isEnabled()));
            moduleManager.getRemovals().setEnabled(bool(properties, "module.removals.enabled", moduleManager.getRemovals().isEnabled()));
            moduleManager.getWorldCustomizer().setEnabled(bool(properties, "module.world_customizer.enabled", moduleManager.getWorldCustomizer().isEnabled()));
            moduleManager.getSafeNametag().setEnabled(bool(properties, "module.safe_nametag.enabled", moduleManager.getSafeNametag().isEnabled()));
            moduleManager.getHitColor().setEnabled(bool(properties, "module.hit_color.enabled", moduleManager.getHitColor().isEnabled()));
            moduleManager.getHitboxCustomizer().setEnabled(bool(properties, "module.hitbox_customizer.enabled", moduleManager.getHitboxCustomizer().isEnabled()));
            moduleManager.getBlockOverlay().setEnabled(bool(properties, "module.block_overlay.enabled", moduleManager.getBlockOverlay().isEnabled()));
            moduleManager.getTargetEsp().setEnabled(bool(properties, "module.target_esp.enabled", moduleManager.getTargetEsp().isEnabled()));
            moduleManager.getItemRadius().setEnabled(bool(properties, "module.item_radius.enabled", moduleManager.getItemRadius().isEnabled()));
            moduleManager.getAnimations().setEnabled(bool(properties, "module.animations.enabled", moduleManager.getAnimations().isEnabled()));
            moduleManager.getTabCustomizer().setEnabled(bool(properties, "module.tab_customizer.enabled", moduleManager.getTabCustomizer().isEnabled()));
            moduleManager.getFakePlayer().setEnabled(bool(properties, "module.fake_player.enabled", moduleManager.getFakePlayer().isEnabled()));
            moduleManager.getAutoSprint().setEnabled(bool(properties, "module.auto_sprint.enabled", moduleManager.getAutoSprint().isEnabled()));
            moduleManager.getAutoSwap().setEnabled(bool(properties, "module.auto_swap.enabled", moduleManager.getAutoSwap().isEnabled()));
            moduleManager.getElytraSwap().setEnabled(bool(properties, "module.elytra_swap.enabled", moduleManager.getElytraSwap().isEnabled()));
            moduleManager.getItemResorter().setEnabled(bool(properties, "module.item_resorter.enabled", moduleManager.getItemResorter().isEnabled()));
            moduleManager.getNameProtect().setEnabled(bool(properties, "module.name_protect.enabled", moduleManager.getNameProtect().isEnabled()));
            moduleManager.getItemScroller().setEnabled(bool(properties, "module.item_scroller.enabled", moduleManager.getItemScroller().isEnabled()));
            moduleManager.getTrapTracker().setEnabled(bool(properties, "module.trap_tracker.enabled", moduleManager.getTrapTracker().isEnabled()));
            moduleManager.getFreeLook().setEnabled(bool(properties, "module.free_look.enabled", moduleManager.getFreeLook().isEnabled()));
            moduleManager.getZoom().setEnabled(bool(properties, "module.zoom.enabled", moduleManager.getZoom().isEnabled()));
            moduleManager.getCrosshair().setEnabled(bool(properties, "module.crosshair.enabled", moduleManager.getCrosshair().isEnabled()));
            moduleManager.getWatermark().setEnabled(bool(properties, "module.watermark.enabled", moduleManager.getWatermark().isEnabled()));
            moduleManager.getTargetHud().setEnabled(bool(properties, "module.target_hud.enabled", moduleManager.getTargetHud().isEnabled()));
            moduleManager.getAnarchySwitcher().setEnabled(bool(properties, "module.anarchy_switcher.enabled", moduleManager.getAnarchySwitcher().isEnabled()));
            moduleManager.getNameBind().setEnabled(bool(properties, "module.name_bind.enabled", moduleManager.getNameBind().isEnabled()));
            moduleManager.getMacros().setEnabled(bool(properties, "module.macros.enabled", moduleManager.getMacros().isEnabled()));
            moduleManager.getAutoResell().setEnabled(bool(properties, "module.auto_resell.enabled", moduleManager.getAutoResell().isEnabled()));
            moduleManager.getAutoResellAFK().setEnabled(bool(properties, "module.auto_resell_afk.enabled", moduleManager.getAutoResellAFK().isEnabled()));
            moduleManager.getAutoResellAFK().setChatEnabled(bool(properties, "auto_resell_afk.chat.enabled", moduleManager.getAutoResellAFK().isChatEnabled()));
            moduleManager.getAutoResellAFK().setChatMessage(properties.getProperty("auto_resell_afk.chat.message", moduleManager.getAutoResellAFK().getChatMessage()));
            moduleManager.getAutoResellAFK().setChatIntervalMs(longNumber(properties, "auto_resell_afk.chat.interval_ms", moduleManager.getAutoResellAFK().getChatIntervalMs()));
            moduleManager.getAutoResellAFK().setSellPurchasedSwords(bool(properties, "auto_resell_afk.sell.enabled", moduleManager.getAutoResellAFK().isSellPurchasedSwords()));
            moduleManager.getAutoResellAFK().setSellPrice(properties.getProperty("auto_resell_afk.sell.price", moduleManager.getAutoResellAFK().getSellPrice()));
            moduleManager.getAHHelper().setEnabled(bool(properties, "module.ah_helper.enabled", moduleManager.getAHHelper().isEnabled()));
            moduleManager.getAutoBuy().setEnabled(bool(properties, "module.auto_buy.enabled", moduleManager.getAutoBuy().isEnabled()));
            ItemCrafter itemCrafter = moduleManager.getItemCrafter();
            itemCrafter.setEnabled(bool(properties, "module.item_crafter.enabled", itemCrafter.isEnabled()));
            itemCrafter.setMode(itemCrafterMode(properties.getProperty("item_crafter.mode"), itemCrafter.getMode()));
            itemCrafter.setBatchCount(integer(properties, "item_crafter.batch_count", itemCrafter.getBatchCount()));
            itemCrafter.setSellPrice(properties.getProperty("item_crafter.sell_price", itemCrafter.getSellPrice()));
            itemCrafter.setMaxLots(integer(properties, "item_crafter.max_lots", itemCrafter.getMaxLots()));
            Telegram telegram = moduleManager.getTelegram();
            telegram.setEnabled(bool(properties, "telegram.enabled", telegram.isEnabled()));
            telegram.setBotToken(properties.getProperty("telegram.bot_token", telegram.getBotToken()));
            telegram.setChatId(properties.getProperty("telegram.chat_id", telegram.getChatId()));
            telegram.setLogsEnabled(bool(properties, "telegram.logs_enabled", telegram.isLogsEnabled()));
            telegram.setNotificationsEnabled(bool(properties, "telegram.notifications_enabled", telegram.isNotificationsEnabled()));
            telegram.setAutoSellPrice(properties.getProperty("telegram.auto_sell_price", telegram.getAutoSellPrice()));
            telegram.setAutoSellEnabled(bool(properties, "telegram.auto_sell_enabled", telegram.isAutoSellEnabled()));
            telegram.restoreRuntimeStateJson(properties.getProperty("telegram.runtime_state", ""));
            moduleManager.getAimBot().setEnabled(bool(properties, "module.aim_bot.enabled", moduleManager.getAimBot().isEnabled()));
            moduleManager.getTriggerBot().setEnabled(bool(properties, "module.trigger_bot.enabled", moduleManager.getTriggerBot().isEnabled()));
            moduleManager.getAutoMace().setEnabled(bool(properties, "module.auto_mace.enabled", moduleManager.getAutoMace().isEnabled()));
            moduleManager.getNoJumpDelay().setEnabled(bool(properties, "module.no_jump_delay.enabled", moduleManager.getNoJumpDelay().isEnabled()));
            moduleManager.getDiscordRPC().setEnabled(
                    bool(properties, "module.discord_rpc.enabled", true));
            moduleManager.getDiscordRPC().setCustomButtonEnabled(
                    bool(properties, "discord_rpc.custom_button.enabled", false));
            moduleManager.getDiscordRPC().setCustomButtonLabel(
                    properties.getProperty("discord_rpc.custom_button.label", ""));
            moduleManager.getDiscordRPC().setCustomButtonUrl(
                    properties.getProperty("discord_rpc.custom_button.url", ""));
            moduleManager.getWatermark().setMusicEnabled(bool(properties, "module.watermark.music.enabled", moduleManager.getWatermark().isMusicEnabled()));
            moduleManager.getWatermark().setNotificationsEnabled(bool(properties, "module.watermark.notifications.enabled", moduleManager.getWatermark().isNotificationsEnabled()));

            ChinaHat chinaHat = moduleManager.getChinaHat();
            chinaHat.setSize(number(properties, "china_hat.size", chinaHat.getSize()));
            chinaHat.setFillMode(chinaHatFillMode(properties.getProperty("china_hat.fill_mode"), chinaHat.getFillMode()));
            chinaHat.setShaderType(blockOverlayShader(properties.getProperty("china_hat.shader"), chinaHat.getShaderType()));
            chinaHat.setShaderAlpha(number(properties, "china_hat.shader_alpha", chinaHat.getShaderAlpha()));
            chinaHat.setColor(
                    number(properties, "china_hat.hue", chinaHat.getHue()),
                    number(properties, "china_hat.saturation", chinaHat.getSaturation()),
                    number(properties, "china_hat.value", chinaHat.getValue())
            );
            chinaHat.setHatAlpha(number(properties, "china_hat.alpha", chinaHat.getHatAlpha()));
            chinaHat.setRenderSelf(bool(properties, "china_hat.target.self", chinaHat.isRenderSelf()));
            chinaHat.setRenderPlayers(bool(properties, "china_hat.target.players", chinaHat.isRenderPlayers()));
            chinaHat.setRenderBots(bool(properties, "china_hat.target.bots", chinaHat.isRenderBots()));
            Wings wings = moduleManager.getWings();
            wings.setWingType(wingType(properties.getProperty("wings.type"), wings.getWingType()));
            wings.setShaderFill(bool(properties, "wings.shader_fill", wings.isShaderFill()));
            wings.setRenderSelf(bool(properties, "wings.target.self", wings.isRenderSelf()));
            wings.setRenderPlayers(bool(properties, "wings.target.players", wings.isRenderPlayers()));
            wings.setRenderBots(bool(properties, "wings.target.bots", wings.isRenderBots()));
            wings.setScale(number(properties, "wings.scale", wings.getScale()));
            wings.setFlapStrength(number(properties, "wings.flap_strength", wings.getFlapStrength()));
            wings.setFlapSpeed(number(properties, "wings.flap_speed", wings.getFlapSpeed()));
            wings.setFlapping(bool(properties, "wings.flapping", wings.isFlapping()));
            wings.setArgbColor(integer(properties, "wings.color", wings.getArgbColor()));

            AspectRatio aspectRatio = moduleManager.getAspectRatio();
            aspectRatio.setPreset(aspectPreset(properties.getProperty("aspect_ratio.preset"), aspectRatio.getPreset()));
            aspectRatio.setCustomRatio(number(properties, "aspect_ratio.custom", aspectRatio.getCustomRatio()));

            Particles particles = moduleManager.getParticles();
            particles.setPreviewTexture(particleTexture(properties.getProperty("particles.preview"), particles.getPreviewTexture()));
            particles.setEnabledModeNames(properties.getProperty("particles.modes", properties.getProperty("particles.enabled_textures", particles.enabledModeNames())));
            particles.setAmount(integer(properties, "particles.amount", particles.getAmount()));
            particles.setLifeSeconds(number(properties, "particles.life", particles.getLifeSeconds()));
            particles.setSize(number(properties, "particles.size", particles.getSize()));
            particles.setOutlineEnabled(bool(properties, "particles.outline",
                    bool(properties, "particles.glow", particles.isOutlineEnabled())));
            particles.setColor(
                    number(properties, "particles.hue", particles.getHue()),
                    number(properties, "particles.saturation", particles.getSaturation()),
                    number(properties, "particles.value", particles.getValue())
            );
            particles.setParticleAlpha(number(properties, "particles.alpha", particles.getParticleAlpha()));

            JumpCircles jumpCircles = moduleManager.getJumpCircles();
            jumpCircles.setMode(jumpCircleMode(properties.getProperty("jump_circles.mode"), jumpCircles.getMode()));
            jumpCircles.setTextureType(jumpCircleTexture(properties.getProperty("jump_circles.texture"), jumpCircles.getTextureType()));
            jumpCircles.setParticleTexture(particleTexture(properties.getProperty("jump_circles.particle_texture"), jumpCircles.getParticleTexture()));
            jumpCircles.setParticlesEnabled(bool(properties, "jump_circles.particles", jumpCircles.isParticlesEnabled()));
            jumpCircles.setAmount(integer(properties, "jump_circles.amount", jumpCircles.getAmount()));
            jumpCircles.setLifeSeconds(number(properties, "jump_circles.life", jumpCircles.getLifeSeconds()));
            jumpCircles.setSize(number(properties, "jump_circles.size", jumpCircles.getSize()));
            jumpCircles.setParticleSize(number(properties, "jump_circles.particle_size", jumpCircles.getParticleSize()));
            jumpCircles.setSpread(number(properties, "jump_circles.spread", jumpCircles.getSpread()));
            jumpCircles.setColor(
                    number(properties, "jump_circles.hue", jumpCircles.getHue()),
                    number(properties, "jump_circles.saturation", jumpCircles.getSaturation()),
                    number(properties, "jump_circles.value", jumpCircles.getValue())
            );
            jumpCircles.setCircleAlpha(number(properties, "jump_circles.alpha", jumpCircles.getCircleAlpha()));

            Trails trails = moduleManager.getTrails();
            trails.setMaxLength(number(properties, "trails.max_length", trails.getMaxLength()));
            trails.setAlpha(number(properties, "trails.alpha", trails.getAlpha()));
            trails.setColor(
                    number(properties, "trails.hue", trails.getHue()),
                    number(properties, "trails.saturation", trails.getSaturation()),
                    number(properties, "trails.value", trails.getValue())
            );

            Removals removals = moduleManager.getRemovals();
            removals.setFireOverlay(bool(properties, "removals.fire_overlay", removals.isFireOverlay()));
            removals.setEntityGlowing(bool(properties, "removals.entity_glowing", removals.isEntityGlowing()));
            removals.setBadWeather(bool(properties, "removals.bad_weather", removals.isBadWeather()));
            removals.setHurtCamera(bool(properties, "removals.hurt_camera", removals.isHurtCamera()));
            removals.setSprintFov(bool(properties, "removals.sprint_fov", removals.isSprintFov()));
            removals.setSoulSandBubbles(bool(properties, "removals.soul_sand_bubbles", removals.isSoulSandBubbles()));
            removals.setEnabledNoFluidTypeNames(properties.getProperty("removals.no_fluid_types", removals.enabledNoFluidTypeNames()));

            WorldCustomizer worldCustomizer = moduleManager.getWorldCustomizer();
            worldCustomizer.setTimePreset(worldPreset(properties.getProperty("world_customizer.time_preset"), worldCustomizer.getTimePreset()));
            worldCustomizer.setCustomTime(longNumber(properties, "world_customizer.custom_time", worldCustomizer.getCustomTime()));
            worldCustomizer.setCustomFogEnabled(bool(properties, "world_customizer.custom_fog_enabled", worldCustomizer.isCustomFogEnabled()));
            worldCustomizer.setFogDistance(number(properties, "world_customizer.fog_distance", worldCustomizer.getFogDistance()));
            worldCustomizer.setFogColor(
                    number(properties, "world_customizer.fog_hue", worldCustomizer.getFogHue()),
                    number(properties, "world_customizer.fog_saturation", worldCustomizer.getFogSaturation()),
                    number(properties, "world_customizer.fog_value", worldCustomizer.getFogValue())
            );

            HitColor hitColor = moduleManager.getHitColor();
            hitColor.setArmorTintEnabled(bool(properties, "hit_color.armor", hitColor.isArmorTintEnabled()));
            hitColor.setAlpha(number(properties, "hit_color.alpha", hitColor.getAlpha()));
            hitColor.setColor(
                    number(properties, "hit_color.hue", hitColor.getHue()),
                    number(properties, "hit_color.saturation", hitColor.getSaturation()),
                    number(properties, "hit_color.value", hitColor.getValue())
            );

            HitboxCustomizer hitboxCustomizer = moduleManager.getHitboxCustomizer();
            hitboxCustomizer.setAlwaysShow(bool(properties, "hitbox.always_show", hitboxCustomizer.isAlwaysShow()));
            hitboxCustomizer.setFillEnabled(bool(properties, "hitbox.fill", hitboxCustomizer.isFillEnabled()));
            hitboxCustomizer.setShowLookVector(bool(properties, "hitbox.look_vector", hitboxCustomizer.isShowLookVector()));
            hitboxCustomizer.setPlayersOnly(migratedHitboxPlayersOnly(properties, hitboxCustomizer));
            hitboxCustomizer.setEnabledTargetNames(properties.getProperty("hitbox.targets"));
            hitboxCustomizer.setIncludeInvisible(bool(properties, "hitbox.invisible", hitboxCustomizer.isIncludeInvisible()));
            hitboxCustomizer.setIncludeSelf(bool(properties, "hitbox.self", hitboxCustomizer.isIncludeSelf()));
            hitboxCustomizer.setCornersOnly(bool(properties, "hitbox.corners_only", hitboxCustomizer.isCornersOnly()));
            hitboxCustomizer.setLineThickness(number(properties, "hitbox.line_thickness", hitboxCustomizer.getLineThickness()));
            hitboxCustomizer.setFillAlpha(number(properties, "hitbox.fill_alpha", hitboxCustomizer.getFillAlpha()));
            hitboxCustomizer.setOutlineColor(
                    number(properties, "hitbox.outline_hue", hitboxCustomizer.getOutlineHue()),
                    number(properties, "hitbox.outline_saturation", hitboxCustomizer.getOutlineSaturation()),
                    number(properties, "hitbox.outline_value", hitboxCustomizer.getOutlineValue())
            );
            hitboxCustomizer.setOutlineAlpha(number(properties, "hitbox.outline_alpha", hitboxCustomizer.getOutlineAlpha()));
            hitboxCustomizer.setFillColor(
                    number(properties, "hitbox.fill_hue", hitboxCustomizer.getFillHue()),
                    number(properties, "hitbox.fill_saturation", hitboxCustomizer.getFillSaturation()),
                    number(properties, "hitbox.fill_value", hitboxCustomizer.getFillValue())
            );

            BlockOverlay blockOverlay = moduleManager.getBlockOverlay();
            blockOverlay.setFillEnabled(bool(properties, "block_overlay.fill", blockOverlay.isFillEnabled()));
            blockOverlay.setThroughWalls(bool(properties, "block_overlay.through_walls", blockOverlay.isThroughWalls()));
            blockOverlay.setSmoothSwitch(bool(properties, "block_overlay.smooth_switch", blockOverlay.isSmoothSwitch()));
            blockOverlay.setFillMode(blockOverlayFillMode(properties.getProperty("block_overlay.fill_mode"), blockOverlay.getFillMode()));
            blockOverlay.setShaderType(blockOverlayShader(properties.getProperty("block_overlay.shader"), blockOverlay.getShaderType()));
            blockOverlay.setLineThickness(number(properties, "block_overlay.line_thickness", blockOverlay.getLineThickness()));
            blockOverlay.setAnimationSpeed(number(properties, "block_overlay.animation_speed", blockOverlay.getAnimationSpeed()));
            blockOverlay.setFillAlpha(number(properties, "block_overlay.fill_alpha", blockOverlay.getFillAlpha()));
            blockOverlay.setOutlineColor(
                    number(properties, "block_overlay.outline_hue", blockOverlay.getOutlineHue()),
                    number(properties, "block_overlay.outline_saturation", blockOverlay.getOutlineSaturation()),
                    number(properties, "block_overlay.outline_value", blockOverlay.getOutlineValue())
            );
            blockOverlay.setOutlineAlpha(number(properties, "block_overlay.outline_alpha", blockOverlay.getOutlineAlpha()));
            blockOverlay.setFillColor(
                    number(properties, "block_overlay.fill_hue", blockOverlay.getFillHue()),
                    number(properties, "block_overlay.fill_saturation", blockOverlay.getFillSaturation()),
                    number(properties, "block_overlay.fill_value", blockOverlay.getFillValue())
            );

            TargetEsp targetEsp = moduleManager.getTargetEsp();
            targetEsp.setStyle(targetEspStyle(properties.getProperty("target_esp.style"), targetEsp.getStyle()));
            targetEsp.setTargetFilter(targetEspFilter(properties.getProperty("target_esp.filter"), targetEsp.getTargetFilter()));
            targetEsp.setMaxDistance(number(properties, "target_esp.distance", targetEsp.getMaxDistance()));
            targetEsp.setLostDelaySeconds(number(properties, "target_esp.lost_delay", targetEsp.getLostDelaySeconds()));
            targetEsp.setAnimationSpeed(number(properties, "target_esp.animation_speed", targetEsp.getAnimationSpeed()));
            targetEsp.setNormalSize(number(properties, "target_esp.normal_size", targetEsp.getNormalSize()));
            targetEsp.setGhostSize(number(properties, "target_esp.ghost_size", targetEsp.getGhostSize()));
            targetEsp.setGhostCount(integer(properties, "target_esp.ghost_count", targetEsp.getGhostCount()));
            targetEsp.setGhostLength(number(properties, "target_esp.ghost_length", targetEsp.getGhostLength()));
            targetEsp.setGhostSpeed(number(properties, "target_esp.ghost_speed", targetEsp.getGhostSpeed()));
            targetEsp.setCrystalSize(number(properties, "target_esp.crystal_size", targetEsp.getCrystalSize()));
            targetEsp.setCrystalCount(integer(properties, "target_esp.crystal_count", targetEsp.getCrystalCount()));
            targetEsp.setCrystalSpeed(number(properties, "target_esp.crystal_speed", targetEsp.getCrystalSpeed()));
            targetEsp.setCrystalRadius(number(properties, "target_esp.crystal_radius", targetEsp.getCrystalRadius()));
            targetEsp.setRedOnHit(bool(properties, "target_esp.red_on_hit", targetEsp.isRedOnHit()));
            targetEsp.setColor(
                    number(properties, "target_esp.hue", targetEsp.getHue()),
                    number(properties, "target_esp.saturation", targetEsp.getSaturation()),
                    number(properties, "target_esp.value", targetEsp.getValue())
            );
            targetEsp.setAlpha(number(properties, "target_esp.alpha", targetEsp.getAlpha()));

            TargetHud targetHud = moduleManager.getTargetHud();
            targetHud.setPosition(
                    number(properties, "target_hud.x", targetHud.getX()),
                    number(properties, "target_hud.y", targetHud.getY())
            );
            targetHud.setRedOnDamage(bool(properties, "target_hud.red_on_damage", targetHud.isRedOnDamage()));
            targetHud.setHitParticles(bool(properties, "target_hud.hit_particles", targetHud.isHitParticles()));

            ItemRadius itemRadius = moduleManager.getItemRadius();
            itemRadius.setEnabledItemNames(properties.getProperty("item_radius.items", itemRadius.enabledItemNames()));
            itemRadius.setDraconicTrap(bool(properties, "item_radius.draconic_trap", itemRadius.isDraconicTrap()));

            FreeLook freeLook = moduleManager.getFreeLook();
            freeLook.setActivationMode(freeLookMode(properties.getProperty("freelook.mode"), freeLook.getActivationMode()));
            freeLook.setCameraDistance(number(properties, "freelook.camera_distance", freeLook.getCameraDistance()));

            Animations animations = moduleManager.getAnimations();
            animations.setTabEnabled(bool(properties, "animations.tab", animations.isTabEnabled()));
            animations.setThirdPersonEnabled(bool(properties, "animations.third_person", animations.isThirdPersonEnabled()));
            animations.setHotbarEnabled(bool(properties, "animations.hotbar", animations.isHotbarEnabled()));
            animations.setInventoryEnabled(bool(properties, "animations.inventory", animations.isInventoryEnabled()));

            TabCustomizer tabCustomizer = moduleManager.getTabCustomizer();
            tabCustomizer.setColumns(integer(properties, "tab_customizer.columns", tabCustomizer.getColumns()));
            tabCustomizer.setPlayersPerColumn(integer(properties, "tab_customizer.players_per_column", tabCustomizer.getPlayersPerColumn()));
            tabCustomizer.setScale(number(properties, "tab_customizer.scale", tabCustomizer.getScale()));

            Zoom zoom = moduleManager.getZoom();
            zoom.setSmoothness(number(properties, "zoom.smoothness", zoom.getSmoothness()));
            zoom.setWheelZoomEnabled(bool(properties, "zoom.wheel", zoom.isWheelZoomEnabled()));

            AutoSwap autoSwap = moduleManager.getAutoSwap();
            autoSwap.setFirstItem(autoSwapItem(properties.getProperty("auto_swap.first"), autoSwap.getFirstItem()));
            autoSwap.setSecondItem(autoSwapItem(properties.getProperty("auto_swap.second"), autoSwap.getSecondItem()));

            ItemResorter itemResorter = moduleManager.getItemResorter();
            itemResorter.setEnchantNeeded(stringList(properties, "item_resorter.enchant_needed"));
            itemResorter.setEnchantIgnored(stringList(properties, "item_resorter.enchant_ignored"));
            itemResorter.setBuffNeeded(stringList(properties, "item_resorter.buff_needed"));
            itemResorter.setBuffIgnored(stringList(properties, "item_resorter.buff_ignored"));
            itemResorter.setBuffExplosiveOnly(bool(properties, "item_resorter.buff_explosive_only", itemResorter.isBuffExplosiveOnly()));
            itemResorter.setEnchantColor(
                    number(properties, "item_resorter.enchant_hue", itemResorter.getEnchantHue()),
                    number(properties, "item_resorter.enchant_saturation", itemResorter.getEnchantSaturation()),
                    number(properties, "item_resorter.enchant_value", itemResorter.getEnchantValue())
            );
            itemResorter.setBuffColor(
                    number(properties, "item_resorter.buff_hue", itemResorter.getBuffHue()),
                    number(properties, "item_resorter.buff_saturation", itemResorter.getBuffSaturation()),
                    number(properties, "item_resorter.buff_value", itemResorter.getBuffValue())
            );
            itemResorter.setPriceFilterEnabled(bool(properties, "item_resorter.price_filter", itemResorter.isPriceFilterEnabled()));
            itemResorter.setMaxPrice(longNumber(properties, "item_resorter.max_price", itemResorter.getMaxPrice()));
            itemResorter.setDurabilityFilterEnabled(bool(properties, "item_resorter.durability_filter", itemResorter.isDurabilityFilterEnabled()));
            itemResorter.setMinDurabilityPercent(integer(properties, "item_resorter.min_durability_percent", itemResorter.getMinDurabilityPercent()));
            moduleManager.getNameProtect().setReplacement(properties.getProperty("name_protect.replacement", moduleManager.getNameProtect().getReplacement()));
            moduleManager.getNameProtect().setAnarchyReplacement(properties.getProperty("name_protect.anarchy_replacement", moduleManager.getNameProtect().getAnarchyReplacement()));
            moduleManager.getNameProtect().setProtectAnarchy(bool(properties, "name_protect.protect_anarchy", moduleManager.getNameProtect().isProtectAnarchy()));
            moduleManager.getItemScroller().setDelayMs(integer(properties, "item_scroller.delay_ms", moduleManager.getItemScroller().getDelayMs()));

            moduleManager.getAnarchySwitcher().setDelaySeconds(number(properties, "anarchy_switcher.delay", moduleManager.getAnarchySwitcher().getDelaySeconds()));
            moduleManager.getAnarchySwitcher().setAnarchyIds(stringList(properties, "anarchy_switcher.list"));
            moduleManager.getAnarchySwitcher().setAdEnabled(bool(properties, "anarchy_switcher.ad_enabled", moduleManager.getAnarchySwitcher().isAdEnabled()));
            moduleManager.getAnarchySwitcher().setAdText(properties.getProperty("anarchy_switcher.ad_text", moduleManager.getAnarchySwitcher().getAdText()));
            moduleManager.getNameBind().setEntries(nameBindList(properties, "name_bind.entries"));
            moduleManager.getMacros().setEntries(macrosList(properties, "macros.entries"));
            AutoBuy autoBuy = moduleManager.getAutoBuy();
            autoBuy.setAnarchySwitchEnabled(bool(properties, "auto_buy.anarchy_switch", autoBuy.isAnarchySwitchEnabled()));
            autoBuy.setAnarchyIds(stringList(properties, "auto_buy.anarchy_list"));
            autoBuy.setAnarchyAdEnabled(bool(properties, "auto_buy.anarchy_ad_enabled", moduleManager.getAnarchySwitcher().isAdEnabled()));
            autoBuy.setAnarchyAdText(properties.getProperty("auto_buy.anarchy_ad_text", moduleManager.getAnarchySwitcher().getAdText()));
            autoBuy.setNameEnabled(bool(properties, "auto_buy.name_enabled", autoBuy.isNameEnabled()));
            autoBuy.setNameText(properties.getProperty("auto_buy.name_text", autoBuy.getNameText()));
            autoBuy.setAutoResellEnabled(bool(properties, "auto_buy.auto_resell", autoBuy.isAutoResellEnabled()));
            autoBuy.setRentalSlotsEnabled(bool(properties, "auto_buy.rental_slots", autoBuy.isRentalSlotsEnabled()));
            autoBuy.setLowBalanceGuardEnabled(bool(
                    properties,
                    "auto_buy.low_balance_guard",
                    autoBuy.isLowBalanceGuardEnabled()
            ));
            autoBuy.setBannedSellers(stringList(properties, "auto_buy.banned_sellers"));

            Crosshair crosshair = moduleManager.getCrosshair();
            crosshair.setPresetDirect(crosshairPreset(properties.getProperty("crosshair.preset"), crosshair.getPreset()));
            crosshair.setShapeName(properties.getProperty("crosshair.shape", crosshair.getShapeName()));
            crosshair.setColor(
                    number(properties, "crosshair.hue", crosshair.getHue()),
                    number(properties, "crosshair.saturation", crosshair.getSaturation()),
                    number(properties, "crosshair.value", crosshair.getValue())
            );
            crosshair.setSize(number(properties, "crosshair.size", crosshair.getSize()));
            crosshair.setGap(number(properties, "crosshair.gap", crosshair.getGap()));
            crosshair.setThickness(number(properties, "crosshair.thickness", crosshair.getThickness()));
            crosshair.setOpacity(number(properties, "crosshair.opacity", crosshair.getOpacity()));
            crosshair.setDot(bool(properties, "crosshair.dot", crosshair.isDot()));
            crosshair.setOutline(bool(properties, "crosshair.outline", crosshair.isOutline()));
            crosshair.setRedOnTarget(bool(properties, "crosshair.red_on_target", crosshair.isRedOnTarget()));
            crosshair.setShowInThirdPerson(bool(properties, "crosshair.show_in_third_person", crosshair.isShowInThirdPerson()));
            crosshair.setPresetDirect(crosshairPreset(properties.getProperty("crosshair.preset"), crosshair.getPreset()));

            AimBot aimBot = moduleManager.getAimBot();
            aimBot.setFov(number(properties, "aim_bot.fov", aimBot.getFov()));
            aimBot.setDistance(number(properties, "aim_bot.distance", aimBot.getDistance()));
            aimBot.setYawSpeed(number(properties, "aim_bot.yaw_speed", aimBot.getYawSpeed()));
            aimBot.setPitchSpeed(number(properties, "aim_bot.pitch_speed", aimBot.getPitchSpeed()));
            aimBot.setHumanize(number(properties, "aim_bot.humanize", aimBot.getHumanize()));
            aimBot.setMaxSpeed(number(properties, "aim_bot.max_speed", aimBot.getMaxSpeed()));
            aimBot.setDeadzone(number(properties, "aim_bot.deadzone", aimBot.getDeadzone()));
            aimBot.setReactionMs(number(properties, "aim_bot.reaction_ms", aimBot.getReactionMs()));
            aimBot.setAimYaw(bool(properties, "aim_bot.aim_yaw", aimBot.isAimYaw()));
            aimBot.setAimPitch(bool(properties, "aim_bot.aim_pitch", aimBot.isAimPitch()));
            aimBot.setCheckWalls(bool(properties, "aim_bot.check_walls", aimBot.isCheckWalls()));
            aimBot.setOnlyOnAttack(bool(properties, "aim_bot.only_on_attack", aimBot.isOnlyOnAttack()));
            aimBot.setRequireAimKey(bool(properties, "aim_bot.require_key", aimBot.isRequireAimKey()));
            aimBot.setAimKey(integer(properties, "aim_bot.aim_key", aimBot.getAimKey()));
            aimBot.setStickyTarget(bool(properties, "aim_bot.sticky", aimBot.isStickyTarget()));
            aimBot.setComboEnabled(bool(properties, "aim_bot.combo_enabled", aimBot.isComboEnabled()));
            aimBot.setComboKey(integer(properties, "aim_bot.combo_key", aimBot.getComboKey()));
            aimBot.setTargetsName(properties.getProperty("aim_bot.targets", aimBot.getTargetsName()));
            String mp = properties.getProperty("aim_bot.multipoints");
            if (mp != null) {
                java.util.Set<String> set = new java.util.HashSet<>();
                for (String s : mp.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) set.add(s);
                }
                if (!set.isEmpty()) aimBot.setMultipoints(set);
            }

            TriggerBot triggerBot = moduleManager.getTriggerBot();
            triggerBot.setChance(number(properties, "trigger_bot.chance", triggerBot.getChance()));
            triggerBot.setCooldownJitter(bool(properties, "trigger_bot.cooldown_jitter", triggerBot.isCooldownJitter()));
            triggerBot.setMaceNoCooldown(bool(properties, "trigger_bot.mace_no_cooldown", triggerBot.isMaceNoCooldown()));
            triggerBot.setOnlyOnAttackKey(bool(properties, "trigger_bot.only_lmb", triggerBot.isOnlyOnAttackKey()));
            triggerBot.setNoUseHit(bool(properties, "trigger_bot.no_use_hit", triggerBot.isNoUseHit()));
            triggerBot.setJumpForCrit(bool(properties, "trigger_bot.jump_for_crit", triggerBot.isJumpForCrit()));
            triggerBot.setTargetsName(properties.getProperty("trigger_bot.targets", triggerBot.getTargetsName()));

            AutoMace autoMace = moduleManager.getAutoMace();
            autoMace.setRequireTrigger(bool(properties, "auto_mace.require_trigger", autoMace.isRequireTrigger()));
            autoMace.setRestoreItem(bool(properties, "auto_mace.restore_item", autoMace.isRestoreItem()));
            autoMace.setTimeoutMs(number(properties, "auto_mace.timeout", autoMace.getTimeoutMs()));
            NoJumpDelay noJumpDelay = moduleManager.getNoJumpDelay();
            noJumpDelay.setJumpRandomize(bool(properties, "no_jump_delay.randomize", noJumpDelay.isJumpRandomize()));

            itemCrafter.setAutoBuyIngredients(bool(properties, "item_crafter.auto_buy", itemCrafter.isAutoBuyIngredients()));
            itemCrafter.setGoldBlockPricePerStack(integer(properties, "item_crafter.gold_block_price", itemCrafter.getGoldBlockPricePerStack()));
            itemCrafter.setGoldIngotPricePerStack(integer(properties, "item_crafter.gold_ingot_price", itemCrafter.getGoldIngotPricePerStack()));
            itemCrafter.setApplePricePerStack(integer(properties, "item_crafter.apple_price", itemCrafter.getApplePricePerStack()));
            itemCrafter.setSellWaitSeconds(integer(properties, "item_crafter.sell_wait_seconds", itemCrafter.getSellWaitSeconds()));

            PremiumClickGuiRenderer.loadConfig(properties);
            dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.loadBinds(properties);
        } catch (IOException | RuntimeException ignored) {
            // Keep defaults when the config is missing or partially broken.
        } finally {
            loading = false;
            dirty = saveAfterLoad;
            if (dirty) {
                dirtyAt = System.currentTimeMillis();
            }
        }
    }

    public void requestSave() {
        if (loading) {
            return;
        }

        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    public void tick() {
        if (dirty && System.currentTimeMillis() - dirtyAt >= SAVE_DELAY_MS) {
            saveNow();
        }
    }

    public boolean isLoading() {
        return loading;
    }

    public void saveNow() {
        if (loading) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("module.fullbright.enabled", Boolean.toString(moduleManager.getFullBright().isEnabled()));
        properties.setProperty("module.china_hat.enabled", Boolean.toString(moduleManager.getChinaHat().isEnabled()));
        properties.setProperty("module.wings.enabled", Boolean.toString(moduleManager.getWings().isEnabled()));
        properties.setProperty("module.aspect_ratio.enabled", Boolean.toString(moduleManager.getAspectRatio().isEnabled()));
        properties.setProperty("module.particles.enabled", Boolean.toString(moduleManager.getParticles().isEnabled()));
        properties.setProperty("module.jump_circles.enabled", Boolean.toString(moduleManager.getJumpCircles().isEnabled()));
        properties.setProperty("module.trails.enabled", Boolean.toString(moduleManager.getTrails().isEnabled()));
        properties.setProperty("module.removals.enabled", Boolean.toString(moduleManager.getRemovals().isEnabled()));
        properties.setProperty("module.world_customizer.enabled", Boolean.toString(moduleManager.getWorldCustomizer().isEnabled()));
        properties.setProperty("module.safe_nametag.enabled", Boolean.toString(moduleManager.getSafeNametag().isEnabled()));
        properties.setProperty("module.hit_color.enabled", Boolean.toString(moduleManager.getHitColor().isEnabled()));
        properties.setProperty("module.hitbox_customizer.enabled", Boolean.toString(moduleManager.getHitboxCustomizer().isEnabled()));
        properties.setProperty("module.block_overlay.enabled", Boolean.toString(moduleManager.getBlockOverlay().isEnabled()));
        properties.setProperty("module.target_esp.enabled", Boolean.toString(moduleManager.getTargetEsp().isEnabled()));
        properties.setProperty("module.item_radius.enabled", Boolean.toString(moduleManager.getItemRadius().isEnabled()));
        properties.setProperty("module.animations.enabled", Boolean.toString(moduleManager.getAnimations().isEnabled()));
        properties.setProperty("module.tab_customizer.enabled", Boolean.toString(moduleManager.getTabCustomizer().isEnabled()));
        properties.setProperty("module.fake_player.enabled", Boolean.toString(moduleManager.getFakePlayer().isEnabled()));
        properties.setProperty("module.auto_sprint.enabled", Boolean.toString(moduleManager.getAutoSprint().isEnabled()));
        properties.setProperty("module.auto_swap.enabled", Boolean.toString(moduleManager.getAutoSwap().isEnabled()));
        properties.setProperty("module.elytra_swap.enabled", Boolean.toString(moduleManager.getElytraSwap().isEnabled()));
        properties.setProperty("module.item_resorter.enabled", Boolean.toString(false));
        properties.setProperty("module.trap_tracker.enabled", Boolean.toString(moduleManager.getTrapTracker().isEnabled()));
        properties.setProperty("module.free_look.enabled", Boolean.toString(moduleManager.getFreeLook().isEnabled()));
        properties.setProperty("module.zoom.enabled", Boolean.toString(moduleManager.getZoom().isEnabled()));
        properties.setProperty("module.crosshair.enabled", Boolean.toString(moduleManager.getCrosshair().isEnabled()));
        properties.setProperty("module.watermark.enabled", Boolean.toString(moduleManager.getWatermark().isEnabled()));
        properties.setProperty("module.item_resorter.enabled", Boolean.toString(moduleManager.getItemResorter().isEnabled()));
        properties.setProperty("module.name_protect.enabled", Boolean.toString(moduleManager.getNameProtect().isEnabled()));
        properties.setProperty("module.item_scroller.enabled", Boolean.toString(moduleManager.getItemScroller().isEnabled()));
        properties.setProperty("module.target_hud.enabled", Boolean.toString(moduleManager.getTargetHud().isEnabled()));
        properties.setProperty("module.anarchy_switcher.enabled", Boolean.toString(moduleManager.getAnarchySwitcher().isEnabled()));
        properties.setProperty("module.name_bind.enabled", Boolean.toString(moduleManager.getNameBind().isEnabled()));
        properties.setProperty("module.macros.enabled", Boolean.toString(moduleManager.getMacros().isEnabled()));
        properties.setProperty("module.auto_resell.enabled", Boolean.toString(moduleManager.getAutoResell().isEnabled()));
        properties.setProperty("module.auto_resell_afk.enabled", Boolean.toString(moduleManager.getAutoResellAFK().isEnabled()));
        properties.setProperty("auto_resell_afk.chat.enabled", Boolean.toString(moduleManager.getAutoResellAFK().isChatEnabled()));
        properties.setProperty("auto_resell_afk.chat.message", moduleManager.getAutoResellAFK().getChatMessage());
        properties.setProperty("auto_resell_afk.chat.interval_ms", Long.toString(moduleManager.getAutoResellAFK().getChatIntervalMs()));
        properties.setProperty("auto_resell_afk.sell.enabled", Boolean.toString(moduleManager.getAutoResellAFK().isSellPurchasedSwords()));
        properties.setProperty("auto_resell_afk.sell.price", moduleManager.getAutoResellAFK().getSellPrice());
        properties.setProperty("module.ah_helper.enabled", Boolean.toString(moduleManager.getAHHelper().isEnabled()));
        properties.setProperty("module.auto_buy.enabled", Boolean.toString(moduleManager.getAutoBuy().isEnabled()));
        ItemCrafter itemCrafter = moduleManager.getItemCrafter();
        properties.setProperty("module.item_crafter.enabled", Boolean.toString(itemCrafter.isEnabled()));
        properties.setProperty("item_crafter.mode", itemCrafter.getMode().name());
        properties.setProperty("item_crafter.batch_count", Integer.toString(itemCrafter.getBatchCount()));
        properties.setProperty("item_crafter.sell_price", itemCrafter.getSellPrice());
        properties.setProperty("item_crafter.max_lots", Integer.toString(itemCrafter.getMaxLots()));
        properties.setProperty("item_crafter.auto_buy", Boolean.toString(itemCrafter.isAutoBuyIngredients()));
        properties.setProperty("item_crafter.gold_block_price", Integer.toString(itemCrafter.getGoldBlockPricePerStack()));
        properties.setProperty("item_crafter.gold_ingot_price", Integer.toString(itemCrafter.getGoldIngotPricePerStack()));
        properties.setProperty("item_crafter.apple_price", Integer.toString(itemCrafter.getApplePricePerStack()));
        properties.setProperty("item_crafter.sell_wait_seconds", Integer.toString(itemCrafter.getSellWaitSeconds()));
        properties.setProperty("module.aim_bot.enabled", Boolean.toString(moduleManager.getAimBot().isEnabled()));
        properties.setProperty("module.trigger_bot.enabled", Boolean.toString(moduleManager.getTriggerBot().isEnabled()));
        properties.setProperty("module.auto_mace.enabled", Boolean.toString(moduleManager.getAutoMace().isEnabled()));
        properties.setProperty("module.no_jump_delay.enabled", Boolean.toString(moduleManager.getNoJumpDelay().isEnabled()));
        properties.setProperty("module.discord_rpc.enabled", Boolean.toString(moduleManager.getDiscordRPC().isEnabled()));
        properties.setProperty("discord_rpc.custom_button.enabled",
                Boolean.toString(moduleManager.getDiscordRPC().isCustomButtonEnabled()));
        properties.setProperty("discord_rpc.custom_button.label", moduleManager.getDiscordRPC().getCustomButtonLabel());
        properties.setProperty("discord_rpc.custom_button.url", moduleManager.getDiscordRPC().getCustomButtonUrl());
        properties.setProperty("module.watermark.music.enabled", Boolean.toString(moduleManager.getWatermark().isMusicEnabled()));
        properties.setProperty("module.watermark.notifications.enabled", Boolean.toString(moduleManager.getWatermark().isNotificationsEnabled()));

        ChinaHat chinaHat = moduleManager.getChinaHat();
        properties.setProperty("china_hat.size", number(chinaHat.getSize()));
        properties.setProperty("china_hat.fill_mode", chinaHat.getFillMode().name());
        properties.setProperty("china_hat.shader", chinaHat.getShaderType().name());
        properties.setProperty("china_hat.shader_alpha", number(chinaHat.getShaderAlpha()));
        properties.setProperty("china_hat.hue", number(chinaHat.getHue()));
        properties.setProperty("china_hat.saturation", number(chinaHat.getSaturation()));
        properties.setProperty("china_hat.value", number(chinaHat.getValue()));
        properties.setProperty("china_hat.alpha", number(chinaHat.getHatAlpha()));
        properties.setProperty("china_hat.target.self", Boolean.toString(chinaHat.isRenderSelf()));
        properties.setProperty("china_hat.target.players", Boolean.toString(chinaHat.isRenderPlayers()));
        properties.setProperty("china_hat.target.bots", Boolean.toString(chinaHat.isRenderBots()));

        Wings wings = moduleManager.getWings();
        properties.setProperty("wings.type", wings.getWingType().name());
        properties.setProperty("wings.shader_fill", Boolean.toString(wings.isShaderFill()));
        properties.setProperty("wings.target.self", Boolean.toString(wings.isRenderSelf()));
        properties.setProperty("wings.target.players", Boolean.toString(wings.isRenderPlayers()));
        properties.setProperty("wings.target.bots", Boolean.toString(wings.isRenderBots()));
        properties.setProperty("wings.scale", number(wings.getScale()));
        properties.setProperty("wings.flap_strength", number(wings.getFlapStrength()));
        properties.setProperty("wings.flap_speed", number(wings.getFlapSpeed()));
        properties.setProperty("wings.flapping", Boolean.toString(wings.isFlapping()));
        properties.setProperty("wings.color", Integer.toString(wings.getArgbColor()));

        AspectRatio aspectRatio = moduleManager.getAspectRatio();
        properties.setProperty("aspect_ratio.preset", aspectRatio.getPreset().name());
        properties.setProperty("aspect_ratio.custom", number(aspectRatio.getCustomRatio()));

        Particles particles = moduleManager.getParticles();
        properties.setProperty("particles.preview", particles.getPreviewTexture().name());
        properties.setProperty("particles.modes", particles.enabledModeNames());
        properties.setProperty("particles.amount", Integer.toString(particles.getAmount()));
        properties.setProperty("particles.life", number(particles.getLifeSeconds()));
        properties.setProperty("particles.size", number(particles.getSize()));
        properties.setProperty("particles.outline", Boolean.toString(particles.isOutlineEnabled()));
        properties.setProperty("particles.glow", Boolean.toString(particles.isOutlineEnabled()));
        properties.setProperty("particles.hue", number(particles.getHue()));
        properties.setProperty("particles.saturation", number(particles.getSaturation()));
        properties.setProperty("particles.value", number(particles.getValue()));
        properties.setProperty("particles.alpha", number(particles.getParticleAlpha()));

        JumpCircles jumpCircles = moduleManager.getJumpCircles();
        properties.setProperty("jump_circles.mode", jumpCircles.getMode().name());
        properties.setProperty("jump_circles.texture", jumpCircles.getTextureType().name());
        properties.setProperty("jump_circles.particle_texture", jumpCircles.getParticleTexture().name());
        properties.setProperty("jump_circles.particles", Boolean.toString(jumpCircles.isParticlesEnabled()));
        properties.setProperty("jump_circles.amount", Integer.toString(jumpCircles.getAmount()));
        properties.setProperty("jump_circles.life", number(jumpCircles.getLifeSeconds()));
        properties.setProperty("jump_circles.size", number(jumpCircles.getSize()));
        properties.setProperty("jump_circles.particle_size", number(jumpCircles.getParticleSize()));
        properties.setProperty("jump_circles.spread", number(jumpCircles.getSpread()));
        properties.setProperty("jump_circles.hue", number(jumpCircles.getHue()));
        properties.setProperty("jump_circles.saturation", number(jumpCircles.getSaturation()));
        properties.setProperty("jump_circles.value", number(jumpCircles.getValue()));
        properties.setProperty("jump_circles.alpha", number(jumpCircles.getCircleAlpha()));

        Trails trails = moduleManager.getTrails();
        properties.setProperty("trails.max_length", number(trails.getMaxLength()));
        properties.setProperty("trails.alpha", number(trails.getAlpha()));
        properties.setProperty("trails.hue", number(trails.getHue()));
        properties.setProperty("trails.saturation", number(trails.getSaturation()));
        properties.setProperty("trails.value", number(trails.getValue()));

        Removals removals = moduleManager.getRemovals();
        properties.setProperty("removals.fire_overlay", Boolean.toString(removals.isFireOverlay()));
        properties.setProperty("removals.entity_glowing", Boolean.toString(removals.isEntityGlowing()));
        properties.setProperty("removals.bad_weather", Boolean.toString(removals.isBadWeather()));
        properties.setProperty("removals.hurt_camera", Boolean.toString(removals.isHurtCamera()));
        properties.setProperty("removals.sprint_fov", Boolean.toString(removals.isSprintFov()));
        properties.setProperty("removals.soul_sand_bubbles", Boolean.toString(removals.isSoulSandBubbles()));
        properties.setProperty("removals.no_fluid_types", removals.enabledNoFluidTypeNames());

        WorldCustomizer worldCustomizer = moduleManager.getWorldCustomizer();
        properties.setProperty("world_customizer.time_preset", worldCustomizer.getTimePreset().name());
        properties.setProperty("world_customizer.custom_time", Long.toString(worldCustomizer.getCustomTime()));
        properties.setProperty("world_customizer.custom_fog_enabled", Boolean.toString(worldCustomizer.isCustomFogEnabled()));
        properties.setProperty("world_customizer.fog_distance", number(worldCustomizer.getFogDistance()));
        properties.setProperty("world_customizer.fog_hue", number(worldCustomizer.getFogHue()));
        properties.setProperty("world_customizer.fog_saturation", number(worldCustomizer.getFogSaturation()));
        properties.setProperty("world_customizer.fog_value", number(worldCustomizer.getFogValue()));

        HitColor hitColor = moduleManager.getHitColor();
        properties.setProperty("hit_color.armor", Boolean.toString(hitColor.isArmorTintEnabled()));
        properties.setProperty("hit_color.alpha", number(hitColor.getAlpha()));
        properties.setProperty("hit_color.hue", number(hitColor.getHue()));
        properties.setProperty("hit_color.saturation", number(hitColor.getSaturation()));
        properties.setProperty("hit_color.value", number(hitColor.getValue()));

        HitboxCustomizer hitboxCustomizer = moduleManager.getHitboxCustomizer();
        properties.setProperty("hitbox.always_show", Boolean.toString(hitboxCustomizer.isAlwaysShow()));
        properties.setProperty("hitbox.fill", Boolean.toString(hitboxCustomizer.isFillEnabled()));
        properties.setProperty("hitbox.look_vector", Boolean.toString(hitboxCustomizer.isShowLookVector()));
        properties.setProperty("hitbox.players_only_migrated", Boolean.toString(true));
        properties.setProperty("hitbox.players_only", Boolean.toString(hitboxCustomizer.isPlayersOnly()));
        properties.setProperty("hitbox.targets", hitboxCustomizer.enabledTargetNames());
        properties.setProperty("hitbox.invisible", Boolean.toString(hitboxCustomizer.isIncludeInvisible()));
        properties.setProperty("hitbox.self", Boolean.toString(hitboxCustomizer.isIncludeSelf()));
        properties.setProperty("hitbox.corners_only", Boolean.toString(hitboxCustomizer.isCornersOnly()));
        properties.setProperty("hitbox.line_thickness", number(hitboxCustomizer.getLineThickness()));
        properties.setProperty("hitbox.fill_alpha", number(hitboxCustomizer.getFillAlpha()));
        properties.setProperty("hitbox.outline_hue", number(hitboxCustomizer.getOutlineHue()));
        properties.setProperty("hitbox.outline_saturation", number(hitboxCustomizer.getOutlineSaturation()));
        properties.setProperty("hitbox.outline_value", number(hitboxCustomizer.getOutlineValue()));
        properties.setProperty("hitbox.outline_alpha", number(hitboxCustomizer.getOutlineAlpha()));
        properties.setProperty("hitbox.fill_hue", number(hitboxCustomizer.getFillHue()));
        properties.setProperty("hitbox.fill_saturation", number(hitboxCustomizer.getFillSaturation()));
        properties.setProperty("hitbox.fill_value", number(hitboxCustomizer.getFillValue()));

        BlockOverlay blockOverlay = moduleManager.getBlockOverlay();
        properties.setProperty("block_overlay.fill", Boolean.toString(blockOverlay.isFillEnabled()));
        properties.setProperty("block_overlay.through_walls", Boolean.toString(blockOverlay.isThroughWalls()));
        properties.setProperty("block_overlay.smooth_switch", Boolean.toString(blockOverlay.isSmoothSwitch()));
        properties.setProperty("block_overlay.fill_mode", blockOverlay.getFillMode().name());
        properties.setProperty("block_overlay.shader", blockOverlay.getShaderType().name());
        properties.setProperty("block_overlay.line_thickness", number(blockOverlay.getLineThickness()));
        properties.setProperty("block_overlay.animation_speed", number(blockOverlay.getAnimationSpeed()));
        properties.setProperty("block_overlay.fill_alpha", number(blockOverlay.getFillAlpha()));
        properties.setProperty("block_overlay.outline_hue", number(blockOverlay.getOutlineHue()));
        properties.setProperty("block_overlay.outline_saturation", number(blockOverlay.getOutlineSaturation()));
        properties.setProperty("block_overlay.outline_value", number(blockOverlay.getOutlineValue()));
        properties.setProperty("block_overlay.outline_alpha", number(blockOverlay.getOutlineAlpha()));
        properties.setProperty("block_overlay.fill_hue", number(blockOverlay.getFillHue()));
        properties.setProperty("block_overlay.fill_saturation", number(blockOverlay.getFillSaturation()));
        properties.setProperty("block_overlay.fill_value", number(blockOverlay.getFillValue()));

        TargetEsp targetEsp = moduleManager.getTargetEsp();
        properties.setProperty("target_esp.style", targetEsp.getStyle().name());
        properties.setProperty("target_esp.filter", targetEsp.getTargetFilter().name());
        properties.setProperty("target_esp.distance", number(targetEsp.getMaxDistance()));
        properties.setProperty("target_esp.lost_delay", number(targetEsp.getLostDelaySeconds()));
        properties.setProperty("target_esp.animation_speed", number(targetEsp.getAnimationSpeed()));
        properties.setProperty("target_esp.normal_size", number(targetEsp.getNormalSize()));
        properties.setProperty("target_esp.ghost_size", number(targetEsp.getGhostSize()));
        properties.setProperty("target_esp.ghost_count", Integer.toString(targetEsp.getGhostCount()));
        properties.setProperty("target_esp.ghost_length", number(targetEsp.getGhostLength()));
        properties.setProperty("target_esp.ghost_speed", number(targetEsp.getGhostSpeed()));
        properties.setProperty("target_esp.crystal_size", number(targetEsp.getCrystalSize()));
        properties.setProperty("target_esp.crystal_count", Integer.toString(targetEsp.getCrystalCount()));
        properties.setProperty("target_esp.crystal_speed", number(targetEsp.getCrystalSpeed()));
        properties.setProperty("target_esp.crystal_radius", number(targetEsp.getCrystalRadius()));
        properties.setProperty("target_esp.red_on_hit", Boolean.toString(targetEsp.isRedOnHit()));
        properties.setProperty("target_esp.hue", number(targetEsp.getHue()));
        properties.setProperty("target_esp.saturation", number(targetEsp.getSaturation()));
        properties.setProperty("target_esp.value", number(targetEsp.getValue()));
        properties.setProperty("target_esp.alpha", number(targetEsp.getAlpha()));

        TargetHud targetHud = moduleManager.getTargetHud();
        properties.setProperty("target_hud.x", number(targetHud.getX()));
        properties.setProperty("target_hud.y", number(targetHud.getY()));
        properties.setProperty("target_hud.red_on_damage", Boolean.toString(targetHud.isRedOnDamage()));
        properties.setProperty("target_hud.hit_particles", Boolean.toString(targetHud.isHitParticles()));

        ItemRadius itemRadius = moduleManager.getItemRadius();
        properties.setProperty("item_radius.items", itemRadius.enabledItemNames());
        properties.setProperty("item_radius.draconic_trap", Boolean.toString(itemRadius.isDraconicTrap()));

        FreeLook freeLook = moduleManager.getFreeLook();
        properties.setProperty("freelook.mode", freeLook.getActivationMode().name());
        properties.setProperty("freelook.camera_distance", number(freeLook.getCameraDistance()));

        Animations animations = moduleManager.getAnimations();
        properties.setProperty("animations.tab", Boolean.toString(animations.isTabEnabled()));
        properties.setProperty("animations.third_person", Boolean.toString(animations.isThirdPersonEnabled()));
        properties.setProperty("animations.hotbar", Boolean.toString(animations.isHotbarEnabled()));
        properties.setProperty("animations.inventory", Boolean.toString(animations.isInventoryEnabled()));

        TabCustomizer tabCustomizer = moduleManager.getTabCustomizer();
        properties.setProperty("tab_customizer.columns", Integer.toString(tabCustomizer.getColumns()));
        properties.setProperty("tab_customizer.players_per_column", Integer.toString(tabCustomizer.getPlayersPerColumn()));
        properties.setProperty("tab_customizer.scale", number(tabCustomizer.getScale()));

        Zoom zoom = moduleManager.getZoom();
        properties.setProperty("zoom.smoothness", number(zoom.getSmoothness()));
        properties.setProperty("zoom.wheel", Boolean.toString(zoom.isWheelZoomEnabled()));

        AutoSwap autoSwap = moduleManager.getAutoSwap();
        properties.setProperty("auto_swap.first", autoSwap.getFirstItem().name());
        properties.setProperty("auto_swap.second", autoSwap.getSecondItem().name());

        ItemResorter itemResorter = moduleManager.getItemResorter();
        properties.setProperty("item_resorter.enchant_needed", stringList(itemResorter.getEnchantNeeded()));
        properties.setProperty("item_resorter.enchant_ignored", stringList(itemResorter.getEnchantIgnored()));
        properties.setProperty("item_resorter.buff_needed", stringList(itemResorter.getBuffNeeded()));
        properties.setProperty("item_resorter.buff_ignored", stringList(itemResorter.getBuffIgnored()));
        properties.setProperty("item_resorter.buff_explosive_only", Boolean.toString(itemResorter.isBuffExplosiveOnly()));
        properties.setProperty("item_resorter.enchant_hue", number(itemResorter.getEnchantHue()));
        properties.setProperty("item_resorter.enchant_saturation", number(itemResorter.getEnchantSaturation()));
        properties.setProperty("item_resorter.enchant_value", number(itemResorter.getEnchantValue()));
        properties.setProperty("item_resorter.buff_hue", number(itemResorter.getBuffHue()));
        properties.setProperty("item_resorter.buff_saturation", number(itemResorter.getBuffSaturation()));
        properties.setProperty("item_resorter.buff_value", number(itemResorter.getBuffValue()));
        properties.setProperty("item_resorter.price_filter", Boolean.toString(itemResorter.isPriceFilterEnabled()));
        properties.setProperty("item_resorter.max_price", Long.toString(itemResorter.getMaxPrice()));
        properties.setProperty("item_resorter.durability_filter", Boolean.toString(itemResorter.isDurabilityFilterEnabled()));
        properties.setProperty("item_resorter.min_durability_percent", Integer.toString(itemResorter.getMinDurabilityPercent()));
        properties.setProperty("name_protect.replacement", moduleManager.getNameProtect().getReplacement());
        properties.setProperty("name_protect.anarchy_replacement", moduleManager.getNameProtect().getAnarchyReplacement());
        properties.setProperty("name_protect.protect_anarchy", Boolean.toString(moduleManager.getNameProtect().isProtectAnarchy()));
        properties.setProperty("item_scroller.delay_ms", Integer.toString(moduleManager.getItemScroller().getDelayMs()));
        properties.setProperty("anarchy_switcher.delay", number(moduleManager.getAnarchySwitcher().getDelaySeconds()));
        properties.setProperty("anarchy_switcher.list", stringList(moduleManager.getAnarchySwitcher().getAnarchyIds()));
        properties.setProperty("anarchy_switcher.ad_enabled", Boolean.toString(moduleManager.getAnarchySwitcher().isAdEnabled()));
        properties.setProperty("anarchy_switcher.ad_text", moduleManager.getAnarchySwitcher().getAdText());
        properties.setProperty("name_bind.entries", nameBindList(moduleManager.getNameBind().getEntries()));
        properties.setProperty("macros.entries", macrosList(moduleManager.getMacros().getEntries()));
        AutoBuy autoBuy = moduleManager.getAutoBuy();
        properties.setProperty("auto_buy.anarchy_switch", Boolean.toString(autoBuy.isAnarchySwitchEnabled()));
        properties.setProperty("auto_buy.anarchy_list", stringList(autoBuy.getAnarchyIds()));
        properties.setProperty("auto_buy.anarchy_ad_enabled", Boolean.toString(autoBuy.isAnarchyAdEnabled()));
        properties.setProperty("auto_buy.anarchy_ad_text", autoBuy.getAnarchyAdText());
        properties.setProperty("auto_buy.name_enabled", Boolean.toString(autoBuy.isNameEnabled()));
        properties.setProperty("auto_buy.name_text", autoBuy.getNameText());
        properties.setProperty("auto_buy.auto_resell", Boolean.toString(autoBuy.isAutoResellEnabled()));
        properties.setProperty("auto_buy.rental_slots", Boolean.toString(autoBuy.isRentalSlotsEnabled()));
        properties.setProperty("auto_buy.low_balance_guard", Boolean.toString(autoBuy.isLowBalanceGuardEnabled()));
        properties.setProperty("auto_buy.banned_sellers", stringList(autoBuy.getBannedSellers()));
        Telegram telegram = moduleManager.getTelegram();
        properties.setProperty("telegram.enabled", Boolean.toString(telegram.isEnabled()));
        properties.setProperty("telegram.bot_token", telegram.getBotToken());
        properties.setProperty("telegram.chat_id", telegram.getChatId());
        properties.setProperty("telegram.logs_enabled", Boolean.toString(telegram.isLogsEnabled()));
        properties.setProperty("telegram.notifications_enabled", Boolean.toString(telegram.isNotificationsEnabled()));
        properties.setProperty("telegram.auto_sell_price", telegram.getAutoSellPrice());
        properties.setProperty("telegram.auto_sell_enabled", Boolean.toString(telegram.isAutoSellEnabled()));
        properties.setProperty("telegram.runtime_state", telegram.getRuntimeStateJson());
        properties.setProperty("module.discord_rpc.enabled",
                Boolean.toString(moduleManager.getDiscordRPC().isEnabled()));

        AimBot aimBotSave = moduleManager.getAimBot();
        properties.setProperty("aim_bot.fov", number(aimBotSave.getFov()));
        properties.setProperty("aim_bot.distance", number(aimBotSave.getDistance()));
        properties.setProperty("aim_bot.yaw_speed", number(aimBotSave.getYawSpeed()));
        properties.setProperty("aim_bot.pitch_speed", number(aimBotSave.getPitchSpeed()));
        properties.setProperty("aim_bot.humanize", number(aimBotSave.getHumanize()));
        properties.setProperty("aim_bot.max_speed", number(aimBotSave.getMaxSpeed()));
        properties.setProperty("aim_bot.deadzone", number(aimBotSave.getDeadzone()));
        properties.setProperty("aim_bot.reaction_ms", number(aimBotSave.getReactionMs()));
        properties.setProperty("aim_bot.aim_yaw", Boolean.toString(aimBotSave.isAimYaw()));
        properties.setProperty("aim_bot.aim_pitch", Boolean.toString(aimBotSave.isAimPitch()));
        properties.setProperty("aim_bot.check_walls", Boolean.toString(aimBotSave.isCheckWalls()));
        properties.setProperty("aim_bot.only_on_attack", Boolean.toString(aimBotSave.isOnlyOnAttack()));
        properties.setProperty("aim_bot.require_key", Boolean.toString(aimBotSave.isRequireAimKey()));
        properties.setProperty("aim_bot.aim_key", Integer.toString(aimBotSave.getAimKey()));
        properties.setProperty("aim_bot.sticky", Boolean.toString(aimBotSave.isStickyTarget()));
        properties.setProperty("aim_bot.combo_enabled", Boolean.toString(aimBotSave.isComboEnabled()));
        properties.setProperty("aim_bot.combo_key", Integer.toString(aimBotSave.getComboKey()));
        properties.setProperty("aim_bot.targets", aimBotSave.getTargetsName());
        properties.setProperty("aim_bot.multipoints", String.join(",", aimBotSave.getMultipoints()));

        TriggerBot triggerBotSave = moduleManager.getTriggerBot();
        properties.setProperty("trigger_bot.chance", number(triggerBotSave.getChance()));
        properties.setProperty("trigger_bot.cooldown_jitter", Boolean.toString(triggerBotSave.isCooldownJitter()));
        properties.setProperty("trigger_bot.mace_no_cooldown", Boolean.toString(triggerBotSave.isMaceNoCooldown()));
        properties.setProperty("trigger_bot.only_lmb", Boolean.toString(triggerBotSave.isOnlyOnAttackKey()));
        properties.setProperty("trigger_bot.no_use_hit", Boolean.toString(triggerBotSave.isNoUseHit()));
        properties.setProperty("trigger_bot.jump_for_crit", Boolean.toString(triggerBotSave.isJumpForCrit()));
        properties.setProperty("trigger_bot.targets", triggerBotSave.getTargetsName());

        AutoMace autoMaceSave = moduleManager.getAutoMace();
        properties.setProperty("auto_mace.require_trigger", Boolean.toString(autoMaceSave.isRequireTrigger()));
        properties.setProperty("auto_mace.restore_item", Boolean.toString(autoMaceSave.isRestoreItem()));
        properties.setProperty("auto_mace.timeout", number(autoMaceSave.getTimeoutMs()));
        NoJumpDelay noJumpDelaySave = moduleManager.getNoJumpDelay();
        properties.setProperty("no_jump_delay.randomize", Boolean.toString(noJumpDelaySave.isJumpRandomize()));

        Crosshair crosshair = moduleManager.getCrosshair();
        properties.setProperty("crosshair.preset", crosshair.getPreset().name());
        properties.setProperty("crosshair.shape", crosshair.getShapeName());
        properties.setProperty("crosshair.hue", number(crosshair.getHue()));
        properties.setProperty("crosshair.saturation", number(crosshair.getSaturation()));
        properties.setProperty("crosshair.value", number(crosshair.getValue()));
        properties.setProperty("crosshair.size", number(crosshair.getSize()));
        properties.setProperty("crosshair.gap", number(crosshair.getGap()));
        properties.setProperty("crosshair.thickness", number(crosshair.getThickness()));
        properties.setProperty("crosshair.opacity", number(crosshair.getOpacity()));
        properties.setProperty("crosshair.dot", Boolean.toString(crosshair.isDot()));
        properties.setProperty("crosshair.outline", Boolean.toString(crosshair.isOutline()));
        properties.setProperty("crosshair.red_on_target", Boolean.toString(crosshair.isRedOnTarget()));
        properties.setProperty("crosshair.show_in_third_person", Boolean.toString(crosshair.isShowInThirdPerson()));

        PremiumClickGuiRenderer.saveConfig(properties);
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.saveBinds(properties);

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream stream = Files.newOutputStream(path)) {
                properties.store(stream, "FluxVisuals config");
            }
            dirty = false;
        } catch (IOException ignored) {
            dirty = true;
            dirtyAt = System.currentTimeMillis();
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private boolean migratedHitboxPlayersOnly(Properties properties, HitboxCustomizer hitboxCustomizer) {
        if (bool(properties, "hitbox.players_only_migrated", false)) {
            return bool(properties, "hitbox.players_only", hitboxCustomizer.isPlayersOnly());
        }

        saveAfterLoad = true;
        return false;
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

    private static List<String> stringList(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return List.of();
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        List<String> result = new ArrayList<>();
        for (String token : value.split(";")) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            try {
                String decoded = new String(decoder.decode(token), StandardCharsets.UTF_8).trim();
                if (!decoded.isEmpty()) {
                    result.add(decoded);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore broken entries and keep the rest of the list usable.
            }
        }
        return result;
    }

    private static String stringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(encoder.encodeToString(value.trim().getBytes(StandardCharsets.UTF_8)));
        }
        return builder.toString();
    }

    private static List<NameBind.EntryView> nameBindList(Properties properties, String key) {
        List<String> rows = stringList(properties, key);
        List<NameBind.EntryView> out = new ArrayList<>();
        for (String row : rows) {
            int split = row.indexOf('|');
            if (split <= 0 || split >= row.length() - 1) {
                continue;
            }
            try {
                int keyCode = Integer.parseInt(row.substring(0, split));
                String text = row.substring(split + 1).trim();
                if (!text.isEmpty()) {
                    out.add(new NameBind.EntryView(text, keyCode));
                }
            } catch (NumberFormatException ignored) {
                // Ignore broken rows.
            }
        }
        return out;
    }

    private static String nameBindList(List<NameBind.EntryView> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        List<String> packed = new ArrayList<>();
        for (NameBind.EntryView entry : entries) {
            if (entry == null || entry.text() == null || entry.text().isBlank()) {
                continue;
            }
            packed.add(entry.keyCode() + "|" + entry.text().trim());
        }
        return stringList(packed);
    }

    private static List<Macros.EntryView> macrosList(Properties properties, String key) {
        List<String> rows = stringList(properties, key);
        List<Macros.EntryView> out = new ArrayList<>();
        for (String row : rows) {
            int split = row.indexOf('|');
            if (split <= 0 || split >= row.length() - 1) {
                continue;
            }
            try {
                int keyCode = Integer.parseInt(row.substring(0, split));
                String text = row.substring(split + 1).trim();
                if (!text.isEmpty()) {
                    out.add(new Macros.EntryView(text, keyCode));
                }
            } catch (NumberFormatException ignored) {
                // Ignore broken rows.
            }
        }
        return out;
    }

    private static String macrosList(List<Macros.EntryView> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        List<String> packed = new ArrayList<>();
        for (Macros.EntryView entry : entries) {
            if (entry == null || entry.text() == null || entry.text().isBlank()) {
                continue;
            }
            packed.add(entry.keyCode() + "|" + entry.text().trim());
        }
        return stringList(packed);
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long longNumber(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static AspectRatio.Preset aspectPreset(String value, AspectRatio.Preset fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return AspectRatio.Preset.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static Particles.TextureType particleTexture(String value, Particles.TextureType fallback) {
        if (value == null) {
            return fallback;
        }

        for (Particles.TextureType type : Particles.TextureType.values()) {
            if (type.name().equalsIgnoreCase(value) || type.label().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return fallback;
    }

    private static WorldCustomizer.TimePreset worldPreset(String value, WorldCustomizer.TimePreset fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return WorldCustomizer.TimePreset.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static JumpCircles.TextureType jumpCircleTexture(String value, JumpCircles.TextureType fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return JumpCircles.TextureType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static JumpCircles.Mode jumpCircleMode(String value, JumpCircles.Mode fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return JumpCircles.Mode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static FreeLook.ActivationMode freeLookMode(String value, FreeLook.ActivationMode fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return FreeLook.ActivationMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static AutoSwap.SwapItem autoSwapItem(String value, AutoSwap.SwapItem fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return AutoSwap.SwapItem.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static BlockOverlay.FillMode blockOverlayFillMode(String value, BlockOverlay.FillMode fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return BlockOverlay.FillMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static ChinaHat.FillMode chinaHatFillMode(String value, ChinaHat.FillMode fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return ChinaHat.FillMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static Wings.WingType wingType(String value, Wings.WingType fallback) {
        if (value == null) return fallback;
        try { return Wings.WingType.valueOf(value); } catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static BlockOverlay.ShaderType blockOverlayShader(String value, BlockOverlay.ShaderType fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return BlockOverlay.ShaderType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static TargetEsp.Style targetEspStyle(String value, TargetEsp.Style fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return TargetEsp.Style.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static TargetEsp.TargetFilter targetEspFilter(String value, TargetEsp.TargetFilter fallback) {
        if (value == null) {
            return fallback;
        }
        if ("LIVING".equals(value)) {
            return TargetEsp.TargetFilter.ALL;
        }

        try {
            return TargetEsp.TargetFilter.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static ItemCrafter.Mode itemCrafterMode(String value, ItemCrafter.Mode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return ItemCrafter.Mode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static Crosshair.Preset crosshairPreset(String value, Crosshair.Preset fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Crosshair.Preset.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

}
