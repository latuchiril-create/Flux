package dev.fuga.fluxvisuals.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private PlayerListHud playerListHud;
    @Shadow @Final private static Identifier HOTBAR_SELECTION_TEXTURE;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBackgroundHudRender(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        // A detached session keeps its own HUD state so it is immediately
        // available after an account switch, but it must never submit hotbar,
        // boss-bar, scoreboard, tab-list or overlay draw calls while hidden.
        if (MultiBotManager.isBackgroundContext()
                || (client != null && client.currentScreen != null
                        && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)
                        && !(client.currentScreen instanceof HandledScreen))) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fluxvisuals$renderWatermark(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        FluxVisualsClient.MODULE_MANAGER.getTargetHud().render(context, client);
        FluxVisualsClient.MODULE_MANAGER.getTestHud().render(context, client);
        FluxVisualsClient.MODULE_MANAGER.getWatermark().render(context, client);
        if (dev.fuga.fluxvisuals.gui.modern.ModernGui2Renderer.INSTANCE != null
                && dev.fuga.fluxvisuals.gui.modern.ModernGui2Renderer.INSTANCE.isClosing()
                && !dev.fuga.fluxvisuals.gui.modern.ModernGui2Renderer.INSTANCE.isClosed()) {
            dev.fuga.fluxvisuals.gui.modern.ModernGui2Renderer.INSTANCE.render(
                    context,
                    client.getWindow().getScaledWidth(),
                    client.getWindow().getScaledHeight(),
                    -1,
                    -1,
                    tickCounter.getTickProgress(false)
            );
        }
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$renderAnimatedPlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var animations = FluxVisualsClient.MODULE_MANAGER.getAnimations();
        var tabCustomizer = FluxVisualsClient.MODULE_MANAGER.getTabCustomizer();
        boolean customScale = tabCustomizer.isEnabled() && Math.abs(tabCustomizer.getScale() - 1.0F) > 0.001F;
        if (!animations.shouldAnimateTab() && !customScale) {
            return;
        }
        if (client == null || client.world == null || client.player == null) {
            return;
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        boolean pressed = client.options.playerListKey.isPressed();
        boolean visible = pressed && (!client.isInSingleplayer()
                || client.player.networkHandler.getListedPlayerListEntries().size() > 1
                || objective != null);
        float progress = animations.tabProgress(visible);
        if (!animations.shouldRenderTab(visible)) {
            playerListHud.setVisible(false);
            ci.cancel();
            return;
        }

        playerListHud.setVisible(true);
        float scale = tabCustomizer.isEnabled() ? tabCustomizer.getScale() : 1.0F;
        float slideY = animations.shouldAnimateTab() ? (1.0F - progress) * -44.0F : 0.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(context.getScaledWindowWidth() * 0.5F, slideY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-context.getScaledWindowWidth() * 0.5F, 0.0F);
        playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        context.getMatrices().popMatrix();
        ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$renderCustomCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (FluxVisualsClient.MODULE_MANAGER.getCrosshair().shouldReplaceVanilla(client)) {
            FluxVisualsClient.MODULE_MANAGER.getCrosshair().render(context, client);
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V")
    )
    private void fluxvisuals$renderSmoothHotbarSelection(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                                         int x, int y, int width, int height) {
        if (HOTBAR_SELECTION_TEXTURE.equals(texture)) {
            x = FluxVisualsClient.MODULE_MANAGER.getAnimations().hotbarSelectionX(x);
        }
        context.drawGuiTexture(pipeline, texture, x, y, width, height);
    }
}
