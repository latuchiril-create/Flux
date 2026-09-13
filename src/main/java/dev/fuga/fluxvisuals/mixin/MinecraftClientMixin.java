package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.render.liqvid.RenderPhase;
import dev.fuga.fluxvisuals.mixin.WorldRendererAccessor;
import dev.fuga.fluxvisuals.multibot.MinecraftClientStateAccess;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen.WorldEntryReason;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements MinecraftClientStateAccess {
    @Shadow
    @Final
    @Mutable
    public InGameHud inGameHud;

    @Override
    public InGameHud fluxvisuals$getInGameHud() {
        return this.inGameHud;
    }

    @Override
    public void fluxvisuals$setInGameHud(InGameHud hud) {
        // Direct field write. Must NOT delegate to MinecraftClientAccessor:
        // the @Accessor with the same method name merges into this method
        // and recurses infinitely (StackOverflowError).
        this.inGameHud = hud;
    }

    @Override
    public ClientPlayNetworkHandler fluxvisuals$getNetworkHandler() {
        MinecraftClient client = (MinecraftClient) (Object) this;
        return client.player == null ? null : client.player.networkHandler;
    }


    @Inject(method = "joinWorld", at = @At("TAIL"))
    private void fluxvisuals$repairActiveWorldRenderState(
            ClientWorld world, WorldEntryReason reason, CallbackInfo ci
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            return;
        }
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world != world || world == null) {
            return;
        }
        WorldRendererAccessor renderer = (WorldRendererAccessor) client.worldRenderer;
        if (renderer.fluxvisuals$getWorld() != world) {
            client.worldRenderer.setWorld(world);
        }
        client.particleManager.setWorld(world);
        client.gameRenderer.setWorld(world);
        client.getBlockEntityRenderDispatcher().setWorld(world);
        client.getEntityRenderDispatcher().setWorld(world);
    }

    @Inject(method = "enterReconfiguration(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$beginBotReconfiguration(Screen screen, CallbackInfo ci) {
        FluxVisualsClient.MULTI_BOT_MANAGER.beginBotReconfiguration(screen);
        // A bot's play -> configuration transition is applied inside a
        // temporary MinecraftClient context. Vanilla clears the singleton
        // world/player/renderer here, which leaks out of that context and
        // makes the visible account appear disconnected or show the title
        // screen. The connection handler still changes its protocol after
        // this method returns, so only the destructive client reset is
        // suppressed. GameJoin will install the bot's next world normally.
        if (MultiBotManager.isCurrentBotContext()) {
            // Preserve shared renderer/audio singletons, but perform the
            // lifecycle-critical part of vanilla's reset inside the temporary
            // bot context. BotSession was cleared by beginBotReconfiguration;
            // clearing these globals as well prevents runWithContext from
            // capturing the stale player back into that session.
            MinecraftClient client = (MinecraftClient) (Object) this;
            client.world = null;
            client.player = null;
            client.interactionManager = null;
            client.cameraEntity = null;
            client.crosshairTarget = null;
            // The cancelled vanilla body will not reach the TAIL injector.
            // Complete the manager-side transition explicitly so its guard
            // cannot remain stuck on a background session.
            FluxVisualsClient.MULTI_BOT_MANAGER.finishBotReconfiguration();
            ci.cancel();
        }
    }

    @Inject(method = "enterReconfiguration(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("TAIL"))
    private void fluxvisuals$finishBotReconfiguration(Screen screen, CallbackInfo ci) {
        FluxVisualsClient.MULTI_BOT_MANAGER.finishBotReconfiguration();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$stopBotSessionsBeforeDisconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        // Vanilla uses disconnect(..., transferring=true) as part of the
        // normal play -> configuration -> play world transfer. Cancelling
        // that call leaves the old world installed while the server has
        // already switched protocols. Only suppress a real non-transfer bot
        // disconnect from tearing down the shared MinecraftClient.
        if ((MultiBotManager.isBackgroundContext() && !transferring)
                || (!transferring && (MultiBotManager.isCurrentBotContext()
                || FluxVisualsClient.MULTI_BOT_MANAGER.isActiveBotLifecycle()))) {
            ci.cancel();
            return;
        }
        // World/protocol transfers also call disconnect. They must not clear
        // every bot session or expose the title screen during hub switches.
        if (!transferring && !MultiBotManager.isBackgroundContext()
                && FluxVisualsClient.MULTI_BOT_MANAGER.hasManagedSessions()) {
            FluxVisualsClient.MULTI_BOT_MANAGER.shutdown();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateBackgroundScreen(Screen screen, CallbackInfo ci) {
        // A bot session must never suppress screens for the primary client.
        // The old lifecycle-wide TitleScreen guard also matched the main
        // connection while any bot was alive, leaving it without a usable
        // login/play transition on FunTime. Only a screen created while
        // applying a background packet is isolated.
        if (MultiBotManager.isBackgroundContext()) {
            FluxVisualsClient.MULTI_BOT_MANAGER.captureBackgroundScreen(screen);
            ci.cancel();
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipInputWhileActiveBotReconfigures(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        // Vanilla enterReconfiguration installs a ReconfiguringScreen, which
        // prevents handleInputEvents from running until GameJoin has created a
        // new player. Background sessions keep that screen isolated so the
        // visible account never flashes. Mirror vanilla's effective guard:
        // do not process game hotkeys while the active bot deliberately has
        // no player/world between CONFIGURATION and its next GameJoin.
        if (client.player == null && FluxVisualsClient.MULTI_BOT_MANAGER != null) {
            var active = FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession();
            if (active != null && !active.isMain() && active.getState()
                    == dev.fuga.fluxvisuals.multibot.BotSession.State.LOGGING_IN) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipForcedBackgroundRender(boolean tick, CallbackInfo ci) {
        if (!MultiBotManager.isCurrentBotContext()
                && FluxVisualsClient.MULTI_BOT_MANAGER != null) {
            MultiBotManager.restoreActiveRenderer((MinecraftClient) (Object) this,
                    FluxVisualsClient.MULTI_BOT_MANAGER.getActiveSession());
        }
        // reset()/joinWorld() force an immediate render from inside packet
        // handling. Rendering while any bot context has temporarily replaced
        // client globals exposes a blank/title frame before restoration.
        if (MultiBotManager.isCurrentBotContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void fluxvisuals$frameModules(boolean tick, CallbackInfo ci) {
        try {
            FluxVisualsClient.onFrame((MinecraftClient) (Object) this);
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fluxvisuals$advanceLiqvidFrame(boolean tick, CallbackInfo ci) {
        // Frame-cached data (BlurRenderer snapshot) is invalidated once per
        // rendered frame so the blur follows world movement.
        RenderPhase.beginFrame();
        if (FluxVisualsClient.MULTI_BOT_MANAGER != null) {
            FluxVisualsClient.MULTI_BOT_MANAGER.onFrameRendered((MinecraftClient) (Object) this);
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void fluxvisuals$onClientClose(CallbackInfo ci) {
        try {
            ProcessHandle current = ProcessHandle.current();
            ProcessHandle parent = current.parent().orElse(null);
            while (parent != null) {
                String cmd = parent.info().command().orElse("").toLowerCase(java.util.Locale.ROOT);
                if (cmd.contains("cmd.exe") || cmd.contains("powershell.exe") || cmd.contains("conhost.exe")) {
                    parent.destroyForcibly();
                }
                parent = parent.parent().orElse(null);
            }
        } catch (Throwable ignored) {
        }

        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {}
            Runtime.getRuntime().halt(0);
        }, "FluxVisuals-ExitGuard");
        exitThread.setDaemon(true);
        exitThread.start();
    }
}
