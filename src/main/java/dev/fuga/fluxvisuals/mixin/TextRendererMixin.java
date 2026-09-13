package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.NameProtect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @ModifyVariable(
            method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String fluxvisuals$protectDrawString(String value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(
            method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text fluxvisuals$protectDrawText(Text value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(
            method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private OrderedText fluxvisuals$protectDrawOrderedText(OrderedText value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String fluxvisuals$protectWidthString(String value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable fluxvisuals$protectWidthVisitable(StringVisitable value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText fluxvisuals$protectWidthOrderedText(OrderedText value) {
        NameProtect protect = nameProtect();
        return protect == null ? value : protect.protect(value);
    }

    private static NameProtect nameProtect() {
        try {
            if (FluxVisualsClient.MODULE_MANAGER == null) {
                return null;
            }
            return FluxVisualsClient.MODULE_MANAGER.getNameProtect();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
