package dev.fuga.fluxvisuals.modules;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.FullBright;
import dev.fuga.fluxvisuals.modules.visual.Animations;
import dev.fuga.fluxvisuals.modules.visual.AutoResell;
import dev.fuga.fluxvisuals.modules.visual.AutoResellAFK;
import dev.fuga.fluxvisuals.modules.visual.AHHelper;
import dev.fuga.fluxvisuals.modules.visual.AutoBuy;
import dev.fuga.fluxvisuals.modules.visual.AutoSwap;
import dev.fuga.fluxvisuals.modules.visual.AutoSprint;
import dev.fuga.fluxvisuals.modules.visual.FakePlayer;
import dev.fuga.fluxvisuals.modules.visual.FreeLook;
import dev.fuga.fluxvisuals.modules.visual.ElytraSwap;
import dev.fuga.fluxvisuals.modules.visual.HitboxCustomizer;
import dev.fuga.fluxvisuals.modules.visual.BlockOverlay;
import dev.fuga.fluxvisuals.modules.visual.ChinaHat;
import dev.fuga.fluxvisuals.captcha.CaptchaSolver;
import dev.fuga.fluxvisuals.modules.visual.AspectRatio;
import dev.fuga.fluxvisuals.modules.visual.Crosshair;
import dev.fuga.fluxvisuals.modules.visual.HitColor;
import dev.fuga.fluxvisuals.modules.visual.ItemResorter;
import dev.fuga.fluxvisuals.modules.visual.ItemCrafter;
import dev.fuga.fluxvisuals.modules.visual.Particles;
import dev.fuga.fluxvisuals.modules.visual.ItemRadius;
import dev.fuga.fluxvisuals.modules.visual.JumpCircles;
import dev.fuga.fluxvisuals.modules.visual.Macros;
import dev.fuga.fluxvisuals.modules.visual.NameBind;
import dev.fuga.fluxvisuals.modules.visual.Removals;
import dev.fuga.fluxvisuals.modules.visual.SafeNametag;
import dev.fuga.fluxvisuals.modules.visual.TabCustomizer;
import dev.fuga.fluxvisuals.modules.visual.TapeMouse;
import dev.fuga.fluxvisuals.modules.visual.TargetHud;
import dev.fuga.fluxvisuals.modules.visual.Telegram;
import dev.fuga.fluxvisuals.modules.visual.DiscordRPC;
import dev.fuga.fluxvisuals.modules.visual.TargetEsp;
import dev.fuga.fluxvisuals.modules.visual.Trails;
import dev.fuga.fluxvisuals.modules.visual.TrapTracker;
import dev.fuga.fluxvisuals.modules.visual.AnarchySwitcher;
import dev.fuga.fluxvisuals.modules.visual.Watermark;
import dev.fuga.fluxvisuals.modules.visual.WorldCustomizer;
import dev.fuga.fluxvisuals.modules.visual.Zoom;
import dev.fuga.fluxvisuals.modules.visual.Wings;
import dev.fuga.fluxvisuals.modules.visual.NameProtect;
import dev.fuga.fluxvisuals.modules.visual.ItemScroller;
import dev.fuga.fluxvisuals.modules.visual.Menu;
import dev.fuga.fluxvisuals.modules.combat.AimBot;
import dev.fuga.fluxvisuals.modules.combat.TriggerBot;
import dev.fuga.fluxvisuals.modules.combat.AutoMace;
import dev.fuga.fluxvisuals.modules.combat.NoJumpDelay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final FullBright fullBright = new FullBright();
    private final ChinaHat chinaHat = new ChinaHat();
    private final Wings wings = new Wings();
    private final AspectRatio aspectRatio = new AspectRatio();
    private final Particles particles = new Particles();
    private final JumpCircles jumpCircles = new JumpCircles();
    private final Trails trails = new Trails();
    private final Removals removals = new Removals();
    private final WorldCustomizer worldCustomizer = new WorldCustomizer();
    private final SafeNametag safeNametag = new SafeNametag();
    private final HitColor hitColor = new HitColor();
    private final HitboxCustomizer hitboxCustomizer = new HitboxCustomizer();
    private final BlockOverlay blockOverlay = new BlockOverlay();
    private final TargetEsp targetEsp = new TargetEsp();
    private final TargetHud targetHud = new TargetHud();
    private final ItemRadius itemRadius = new ItemRadius();
    private final Animations animations = new Animations();
    private final TabCustomizer tabCustomizer = new TabCustomizer();
    private final FakePlayer fakePlayer = new FakePlayer();
    private final AutoSprint autoSprint = new AutoSprint();
    private final AutoSwap autoSwap = new AutoSwap();
    private final ElytraSwap elytraSwap = new ElytraSwap();
    private final ItemResorter itemResorter = new ItemResorter();
    private final TrapTracker trapTracker = new TrapTracker();
    private final FreeLook freeLook = new FreeLook();
    private final Zoom zoom = new Zoom();
    private final Crosshair crosshair = new Crosshair();
    private final Watermark watermark = new Watermark();
    private final AnarchySwitcher anarchySwitcher = new AnarchySwitcher();
    private final NameBind nameBind = new NameBind();
    private final Macros macros = new Macros();
    private final TapeMouse tapeMouse = new TapeMouse();
    private final AutoResell autoResell = new AutoResell();
    private final AutoResellAFK autoResellAFK = new AutoResellAFK();
    private final AHHelper ahHelper = new AHHelper();
    private final AutoBuy autoBuy = new AutoBuy();
    private final Telegram telegram = new Telegram();
    private final DiscordRPC discordRPC = new DiscordRPC();
    private final NameProtect nameProtect = new NameProtect();
    private final ItemScroller itemScroller = new ItemScroller();
    private final Menu menu = new Menu();
    private final AimBot aimBot = new AimBot();
    private final TriggerBot triggerBot = new TriggerBot();
    private final AutoMace autoMace = new AutoMace();
    private final ItemCrafter itemCrafter = new ItemCrafter();
    private final CaptchaSolver captchaSolver = new CaptchaSolver();
    private final NoJumpDelay noJumpDelay = new NoJumpDelay();

    public ModuleManager() {
        register(fullBright);
        register(chinaHat);
        register(wings);
        register(aspectRatio);
        register(particles);
        register(jumpCircles);
        register(trails);
        register(removals);
        register(worldCustomizer);
        register(safeNametag);
        register(hitColor);
        register(hitboxCustomizer);
        register(blockOverlay);
        register(targetEsp);
        register(targetHud);
        register(itemRadius);
        register(animations);
        register(tabCustomizer);
        register(fakePlayer);
        register(autoSprint);
        register(autoSwap);
        register(elytraSwap);
        register(itemResorter);
        register(trapTracker);
        register(freeLook);
        register(zoom);
        register(crosshair);
        register(watermark);
        register(anarchySwitcher);
        register(nameBind);
        register(macros);
        register(tapeMouse);
        register(autoResell);
        register(autoResellAFK);
        register(ahHelper);
        register(telegram);
        register(autoBuy);
        register(itemCrafter);
        register(captchaSolver);
        register(discordRPC);
        register(nameProtect);
        register(itemScroller);
        register(menu);
        register(aimBot);
        register(triggerBot);
        register(autoMace);
        register(noJumpDelay);
    }

    private void register(Module module) {
        modules.add(module);
    }

    public void onTick(MinecraftClient client) {
        boolean skipGlobalAutomation = FluxVisualsClient.MULTI_BOT_MANAGER.shouldSkipGlobalAutomation();
        for (Module module : modules) {
            if (module == telegram) {
                FluxVisualsClient.MULTI_BOT_MANAGER.tickTelegram(client, telegram);
                continue;
            }
            // While a bot is controlled, per-session automation copies run
            // instead of the globals (see tickBotAutomation). Letting the
            // global crafter run here too would craft on the wrong account
            // and double-drive the same workbench/AH screens.
            if (skipGlobalAutomation && (module == autoBuy || module == autoResellAFK || module == itemCrafter)) {
                continue;
            }
            module.onTick(client);
        }
    }

    public void onFrame(MinecraftClient client, float deltaSeconds) {
        for (Module module : modules) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onFrame(client, deltaSeconds);
            } catch (Exception ignored) {
            }
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModules(ModuleCategory category) {
        return modules.stream()
                .filter(module -> module.getCategory() == category)
                .toList();
    }

    public List<Module> getStatefulModules() {
        return List.of(
                fullBright, chinaHat, wings, aspectRatio, particles, jumpCircles, trails, removals,
                worldCustomizer, safeNametag, hitColor, hitboxCustomizer, blockOverlay, targetEsp,
                targetHud, itemRadius, animations, tabCustomizer, fakePlayer, autoSprint, autoSwap,
                elytraSwap, itemResorter, trapTracker, freeLook, zoom, crosshair, watermark,
                anarchySwitcher, nameBind, macros, tapeMouse, autoResell, autoResellAFK, ahHelper,
                autoBuy, itemCrafter, telegram, aimBot, triggerBot, autoMace, noJumpDelay,
                captchaSolver
        );
    }

    public FullBright getFullBright() {
        return fullBright;
    }

    public ChinaHat getChinaHat() {
        return chinaHat;
    }

    public Wings getWings() {
        return wings;
    }

    public AspectRatio getAspectRatio() {
        return aspectRatio;
    }

    public Particles getParticles() {
        return particles;
    }

    public JumpCircles getJumpCircles() {
        return jumpCircles;
    }

    public Trails getTrails() {
        return trails;
    }

    public Removals getRemovals() {
        return removals;
    }

    public WorldCustomizer getWorldCustomizer() {
        return worldCustomizer;
    }

    public SafeNametag getSafeNametag() {
        return safeNametag;
    }

    public HitColor getHitColor() {
        return hitColor;
    }

    public HitboxCustomizer getHitboxCustomizer() {
        return hitboxCustomizer;
    }

    public BlockOverlay getBlockOverlay() {
        return blockOverlay;
    }

    public TargetEsp getTargetEsp() {
        return targetEsp;
    }

    public TargetHud getTargetHud() {
        return targetHud;
    }

    public ItemRadius getItemRadius() {
        return itemRadius;
    }

    public Animations getAnimations() {
        return animations;
    }

    public TabCustomizer getTabCustomizer() {
        return tabCustomizer;
    }

    public FakePlayer getFakePlayer() {
        return fakePlayer;
    }

    public AutoSprint getAutoSprint() {
        return autoSprint;
    }

    public AutoSwap getAutoSwap() {
        return autoSwap;
    }

    public ElytraSwap getElytraSwap() {
        return elytraSwap;
    }

    public ItemResorter getItemResorter() {
        return itemResorter;
    }

    public TrapTracker getTrapTracker() {
        return trapTracker;
    }

    public FreeLook getFreeLook() {
        return freeLook;
    }

    public Zoom getZoom() {
        return zoom;
    }

    public Crosshair getCrosshair() {
        return crosshair;
    }

    public Watermark getWatermark() {
        return watermark;
    }

    public AnarchySwitcher getAnarchySwitcher() {
        return anarchySwitcher;
    }

    public NameBind getNameBind() {
        return nameBind;
    }

    public Macros getMacros() {
        return macros;
    }

    public TapeMouse getTapeMouse() {
        return tapeMouse;
    }

    public AutoResell getAutoResell() {
        return autoResell;
    }

    public AutoResellAFK getAutoResellAFK() {
        return autoResellAFK;
    }

    public AHHelper getAHHelper() {
        return ahHelper;
    }

    public AutoBuy getAutoBuy() {
        return autoBuy;
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public DiscordRPC getDiscordRPC() {
        return discordRPC;
    }

    public NameProtect getNameProtect() {
        return nameProtect;
    }

    public ItemScroller getItemScroller() {
        return itemScroller;
    }

    public Menu getMenu() {
        return menu;
    }

    public AimBot getAimBot() {
        return aimBot;
    }

    public TriggerBot getTriggerBot() {
        return triggerBot;
    }

    public AutoMace getAutoMace() {
        return autoMace;
    }

    public ItemCrafter getItemCrafter() {
        return itemCrafter;
    }

    public CaptchaSolver getCaptchaSolver() {
        return captchaSolver;
    }

    public NoJumpDelay getNoJumpDelay() {
        return noJumpDelay;
    }
}
