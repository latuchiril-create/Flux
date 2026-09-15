package dev.fuga.fluxvisuals.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import dev.fuga.fluxvisuals.command.DotCommandSuggestor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow @Final TextFieldWidget textField;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private ChatInputSuggestor.SuggestionWindow window;
    @Shadow private boolean completingSuggestions;
    @Shadow @Final private List<OrderedText> messages;

    /** Public in 1.21.8; unlike showCommandSuggestions(), this bypasses the
     * vanilla "auto suggestions" option and creates the window immediately. */
    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    /**
     * Some screen updates can clear the suggestion window after refresh has
     * completed (for example when the vanilla auto-suggest option is off).
     * Recreate our already-computed dot-command window at render time so the
     * list is visible without requiring a Tab key press.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void fluxvisuals$ensureDotSuggestions(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        String text = this.textField.getText();
        if (text != null && text.startsWith(".")
                && this.window == null
                && !this.completingSuggestions
                && this.pendingSuggestions != null
                && this.pendingSuggestions.isDone()
                && !this.pendingSuggestions.isCompletedExceptionally()) {
            this.show(false);
        }
    }

    /**
     * Keep the normal suggestion-window keyboard behavior for dot commands.
     * The vanilla window already implements Tab/Shift+Tab cycling; delegating
     * here also makes cycling work when the game's auto-suggestion option is
     * disabled.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cycleDotSuggestions(int keyCode, int scanCode, int modifiers,
                                                   CallbackInfoReturnable<Boolean> cir) {
        String text = this.textField.getText();
        if (text == null || !text.startsWith(".")) {
            return;
        }

        // Escape belongs to ChatScreen.  Let it close the whole chat instead
        // of allowing the suggestion window to consume the key first.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }

        if (this.window == null
                && !this.completingSuggestions
                && this.pendingSuggestions != null
                && this.pendingSuggestions.isDone()
                && !this.pendingSuggestions.isCompletedExceptionally()) {
            this.show(false);
        }

        if (this.window != null && this.window.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$onRefresh(CallbackInfo ci) {
        String text = this.textField.getText();
        if (text != null && text.startsWith(".")) {
            this.pendingSuggestions = null;
            if (!this.completingSuggestions) {
                this.textField.setSuggestion(null);
                this.window = null;
            }
            this.messages.clear();

            int cursor = this.textField.getCursor();
            CompletableFuture<Suggestions> future = DotCommandSuggestor.getSuggestions(text, cursor);

            if (future != null) {
                this.pendingSuggestions = future;
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions != null
                            && this.pendingSuggestions.isDone()
                            && !this.completingSuggestions
                            && this.window == null) {
                        // The old call went through vanilla's command path,
                        // which only opens the window when the game's auto
                        // suggestions option is enabled (and often after
                        // Tab).  Our dot-command provider is already ready,
                        // so open it directly on every text refresh.
                        this.show(false);
                    }
                });
            }

            ci.cancel();
        }
    }
}
