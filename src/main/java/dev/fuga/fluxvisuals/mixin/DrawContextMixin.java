package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.NameProtect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @ModifyVariable(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String fluxvisuals$protectShadowString(String value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String fluxvisuals$protectString(String value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text fluxvisuals$protectShadowText(Text value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text fluxvisuals$protectText(Text value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText fluxvisuals$protectShadowOrdered(OrderedText value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText fluxvisuals$protectOrdered(OrderedText value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    private static NameProtect nameProtect() {
        try {
            return FluxVisualsClient.MODULE_MANAGER == null ? null : FluxVisualsClient.MODULE_MANAGER.getNameProtect();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
