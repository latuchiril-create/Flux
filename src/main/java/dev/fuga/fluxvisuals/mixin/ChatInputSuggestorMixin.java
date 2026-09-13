package dev.fuga.fluxvisuals.mixin;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Redirect(
            method = "refresh",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;getText()Ljava/lang/String;"
            )
    )
    private String fluxvisuals$treatDotBotAsVanillaCommand(TextFieldWidget field) {
        String input = field.getText();
        if (input.regionMatches(true, 0, ".bot", 0, 4)) {
            return "/" + input.substring(1);
        }
        return input;
    }
}
