package dev.fuga.fluxvisuals.gui;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.DiscordRPC;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small vanilla screen for editing Discord RPC button settings. */
public final class DiscordRPCSettingsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget labelField;
    private TextFieldWidget urlField;
    private ButtonWidget toggleButton;
    private boolean customEnabled;

    public DiscordRPCSettingsScreen(Screen parent) {
        super(Text.literal("Discord RPC"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DiscordRPC rpc = FluxVisualsClient.MODULE_MANAGER.getDiscordRPC();
        customEnabled = rpc.isCustomButtonEnabled();
        labelField = new TextFieldWidget(textRenderer, width / 2 - 140, height / 2 - 60, 280, 20,
                Text.literal("Название кнопки"));
        labelField.setMaxLength(32);
        labelField.setText(rpc.getCustomButtonLabel());
        urlField = new TextFieldWidget(textRenderer, width / 2 - 140, height / 2 - 10, 280, 20,
                Text.literal("Ссылка"));
        urlField.setMaxLength(512);
        urlField.setText(rpc.getCustomButtonUrl());
        addDrawableChild(labelField);
        addDrawableChild(urlField);
        toggleButton = addDrawableChild(ButtonWidget.builder(toggleText(rpc), button -> {
            customEnabled = !customEnabled;
            button.setMessage(Text.literal("Кастомная кнопка: " + (customEnabled ? "ВКЛ" : "ВЫКЛ")));
        }).dimensions(width / 2 - 140, height / 2 - 100, 280, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), button -> {
            rpc.setCustomButtonEnabled(customEnabled);
            rpc.setCustomButtonLabel(labelField.getText());
            rpc.setCustomButtonUrl(urlField.getText());
            FluxVisualsClient.requestConfigSave();
            close();
        }).dimensions(width / 2 - 140, height / 2 + 30, 135, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), button -> close())
                .dimensions(width / 2 + 5, height / 2 + 30, 135, 20).build());
    }

    private static Text toggleText(DiscordRPC rpc) {
        return Text.literal("Кастомная кнопка: " + (rpc.isCustomButtonEnabled() ? "ВКЛ" : "ВЫКЛ"));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid Screen.renderBackground here: on 1.21.8 it starts the global
        // blur pass, while the parent screen may already have started it.
        context.fill(0, 0, width, height, 0xD0101018);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 130, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Название кнопки"), width / 2 - 140, height / 2 - 76, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Ссылка (http:// или https://)"), width / 2 - 140, height / 2 - 26, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
