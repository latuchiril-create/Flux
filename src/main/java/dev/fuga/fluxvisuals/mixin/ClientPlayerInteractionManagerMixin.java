package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (MultiBotManager.isBotContext()) {
            return;
        }
        FluxVisualsClient.MODULE_MANAGER.getTargetEsp().forceTarget(target);
        if (FluxVisualsClient.MODULE_MANAGER.getFakePlayer().handleAttack(player, target)) {
            ci.cancel();
            return;
        }
        FluxVisualsClient.MODULE_MANAGER.getHitColor().markHit(target);
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$onInteractEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (MultiBotManager.isBotContext()) {
            return;
        }
        ActionResult result = FluxVisualsClient.MODULE_MANAGER.getFakePlayer().handleInteract(player, entity, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$onInteractEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult,
                                                        Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (MultiBotManager.isBotContext()) {
            return;
        }
        ActionResult result = FluxVisualsClient.MODULE_MANAGER.getFakePlayer().handleInteract(player, entity, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void fluxvisuals$traceClickSlot(int syncId, int slotId, int button, SlotActionType actionType,
                                            PlayerEntity player, CallbackInfo ci) {
        // Trace-only diagnostic: never cancels. Every inventory click is logged
        // with the calling flux module (if any) so vanished icons can be traced
        // to the exact automation. File log only, never chat.
        try {
            String caller = "vanilla/player";
            for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                String cls = el.getClassName();
                if (cls.startsWith("dev.fuga.fluxvisuals.")
                        && !cls.contains(".mixin.")
                        && !cls.contains("BotDebug")) {
                    caller = cls.substring("dev.fuga.fluxvisuals.".length())
                            + "." + el.getMethodName() + ":" + el.getLineNumber();
                    break;
                }
            }
            MinecraftClient client = MinecraftClient.getInstance();
            String screen = "none";
            try {
                if (client != null && client.currentScreen != null) {
                    screen = client.currentScreen.getClass().getSimpleName()
                            + " [" + client.currentScreen.getTitle().getString() + "]";
                }
            } catch (Exception ignored) {
            }
            String stack = "?";
            try {
                if (player != null && player.currentScreenHandler != null) {
                    stack = String.valueOf(player.currentScreenHandler.getSlot(slotId).getStack());
                }
            } catch (Exception ignored) {
            }
            BotDebug.trace("CLICK_SLOT", null, "sync=" + syncId + " slot=" + slotId
                    + " btn=" + button + " action=" + actionType + " caller=" + caller
                    + " screen=" + screen + " stack=" + stack);
        } catch (Exception ignored) {
        }
    }
}
