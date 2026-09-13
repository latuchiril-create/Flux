package dev.fuga.fluxvisuals.gui.imgui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiButtonFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ImGuiClickGui {
    private static final float BASE_WIDTH = 1178.0F;
    private static final float BASE_HEIGHT = 640.0F;
    private static final long OPEN_ANIMATION_NS = 180_000_000L;

    private static final int ROUND_ALL = ImDrawFlags.RoundCornersAll;

    private final List<VisualModule> modules = new ArrayList<>();
    private final Map<VisualCategory, Float> tabHover = new EnumMap<>(VisualCategory.class);
    private final Map<String, VisualSlider> sliders = new HashMap<>();
    private final ImString searchText = new ImString(48);

    private VisualCategory selectedCategory = VisualCategory.VISUALS;
    private String draggingSlider;
    private boolean searchOpen;
    private boolean showFullBrightSettings = true;
    private boolean showTargetEspSettings = true;
    private float searchProgress;
    private long openedAt = System.nanoTime();

    public ImGuiClickGui() {
        for (VisualCategory category : VisualCategory.values()) {
            tabHover.put(category, 0.0F);
        }

        modules.add(new VisualModule("FullBright", "\u0423\u0432\u0435\u043b\u0438\u0447\u0438\u0432\u0430\u0435\u0442 \u0433\u0430\u043c\u043c\u0443 \u0432 \u0438\u0433\u0440\u0435", VisualCategory.VISUALS, true));
        modules.add(new VisualModule("Particles", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0447\u0430\u0441\u0442\u0438\u0446", VisualCategory.VISUALS, true));
        modules.add(new VisualModule("Trails", "\u0421\u043b\u0435\u0434 \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u043e\u043c", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("Removals", "\u0423\u0434\u0430\u043b\u044f\u0435\u0442 \u043b\u0438\u0448\u043d\u0438\u0435 \u044d\u0444\u0444\u0435\u043a\u0442\u044b", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("China Hat", "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u0430\u044f \u0448\u043b\u044f\u043f\u0430", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("Aspect Ratio", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u0440\u043e\u043f\u043e\u0440\u0446\u0438\u0439", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("TargetEsp", "\u041f\u043e\u0434\u0441\u0432\u0435\u0442\u043a\u0430 \u0446\u0435\u043b\u0438", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("World Customazer", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043c\u0438\u0440\u0430", VisualCategory.VISUALS, false));
        modules.add(new VisualModule("Hit Color", "\u0426\u0432\u0435\u0442 \u0443\u0434\u0430\u0440\u0430", VisualCategory.VISUALS, true));
        modules.add(new VisualModule("Hitbox Customizer", "\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0435 \u0445\u0438\u0442\u0431\u043e\u043a\u0441\u044b", VisualCategory.VISUALS, false));

    }

    public void open() {
        openedAt = System.nanoTime();
    }

    public void render(ImFont font, ImGuiRenderer.Icons icons) {
        ImGuiIO io = ImGui.getIO();
        float open = openingProgress();
        float fit = Math.min(io.getDisplaySizeX() / BASE_WIDTH, io.getDisplaySizeY() / BASE_HEIGHT);
        float scale = fit * (0.965F + 0.035F * open);
        float originX = (io.getDisplaySizeX() - BASE_WIDTH * scale) * 0.5F;
        float originY = (io.getDisplaySizeY() - BASE_HEIGHT * scale) * 0.5F;
        Layout layout = new Layout(originX, originY, scale, open);

        ImGui.setNextWindowPos(0.0F, 0.0F);
        ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY());
        ImGui.begin("##fluxvisuals_clickgui_root",
                ImGuiWindowFlags.NoDecoration
                        | ImGuiWindowFlags.NoBackground
                        | ImGuiWindowFlags.NoSavedSettings
                        | ImGuiWindowFlags.NoMove
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse);

        ImDrawList draw = ImGui.getWindowDrawList();
        float dt = Math.max(1.0F / 240.0F, io.getDeltaTime());
        if (font != null) {
            ImGui.pushFont(font);
        }

        drawMainPanel(draw, font, icons, layout, dt);
        drawSideCards(draw, font, icons, layout, dt);

        if (font != null) {
            ImGui.popFont();
        }
        ImGui.end();
    }

    private void drawMainPanel(ImDrawList draw, ImFont font, ImGuiRenderer.Icons icons, Layout layout, float dt) {
        float x = layout.x(271.0F);
        float y = layout.y(177.0F);
        float w = layout.v(632.0F);
        float h = layout.v(370.0F);
        float r = layout.v(20.0F);

        glow(draw, x, y, w, h, r, rgba(123, 44, 191, 0.12F * layout.alpha), layout.v(14.0F));
        shadow(draw, x, y, w, h, r, layout);
        rect(draw, x, y, w, h, r, rgba(10, 10, 12, 0.86F * layout.alpha));
        stroke(draw, x, y, w, h, r, rgba(255, 255, 255, 0.055F * layout.alpha), layout.v(0.8F));
        line(draw, 291.0F, 230.0F, 882.0F, 230.0F, rgba(255, 255, 255, 0.075F * layout.alpha), layout.v(1.0F), layout);

        drawTabs(draw, font, layout, dt);
        drawSearch(draw, font, icons, layout, dt);
        drawModules(draw, font, layout, dt);
    }

    private void drawTabs(ImDrawList draw, ImFont font, Layout layout, float dt) {
        drawTab(draw, font, layout, dt, VisualCategory.VISUALS, 302.0F, 191.0F);
        drawTab(draw, font, layout, dt, VisualCategory.HUD, 470.0F, 191.0F);
        drawTab(draw, font, layout, dt, VisualCategory.UTILS, 638.0F, 191.0F);
    }

    private void drawTab(ImDrawList draw, ImFont font, Layout layout, float dt, VisualCategory category, float bx, float by) {
        float x = layout.x(bx);
        float y = layout.y(by);
        float w = layout.v(126.0F);
        float h = layout.v(29.0F);
        float r = h * 0.5F;

        invisibleButton("##tab_" + category.name(), x, y, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            selectedCategory = category;
        }

        float hover = approach(tabHover.get(category), hovered ? 1.0F : 0.0F, dt, 14.0F);
        tabHover.put(category, hover);
        boolean selected = selectedCategory == category;
        float active = selected ? 1.0F : hover * 0.45F;

        rect(draw, x, y, w, h, r, mix(rgba(59, 59, 60, 0.72F * layout.alpha), rgba(83, 76, 90, 0.82F * layout.alpha), active));
        if (selected) {
            stroke(draw, x, y, w, h, r, rgba(255, 255, 255, 0.07F * layout.alpha), layout.v(0.75F));
        }
        textCentered(draw, font, category.label, x, y - layout.v(0.5F), w, h, layout.v(20.0F),
                selected ? rgba(255, 255, 255, 0.96F * layout.alpha) : rgba(168, 164, 169, (0.78F + hover * 0.2F) * layout.alpha));
    }

    private void drawSearch(ImDrawList draw, ImFont font, ImGuiRenderer.Icons icons, Layout layout, float dt) {
        float x = layout.x(844.0F);
        float y = layout.y(190.0F);
        float size = layout.v(32.0F);

        invisibleButton("##search_toggle", x, y, size, size);
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            searchOpen = !searchOpen;
            if (!searchOpen) {
                searchText.clear();
            }
        }
        searchProgress = approach(searchProgress, searchOpen ? 1.0F : 0.0F, dt, 14.0F);

        rect(draw, x, y, size, size, size * 0.5F, rgba(34 + (hovered ? 8 : 0), 34 + (hovered ? 8 : 0), 36 + (hovered ? 8 : 0), 0.84F * layout.alpha));
        stroke(draw, x, y, size, size, size * 0.5F, rgba(255, 255, 255, 0.055F * layout.alpha), layout.v(0.75F));
        drawIcon(draw, icons.searchTexture(), x + layout.v(8.0F), y + layout.v(8.0F), layout.v(16.0F), rgba(10, 10, 11, layout.alpha));

        if (searchProgress > 0.01F) {
            float inputW = layout.v(154.0F * searchProgress);
            float ix = x - inputW - layout.v(8.0F);
            rect(draw, ix, y, inputW, size, layout.v(13.0F), rgba(13, 13, 16, 0.9F * layout.alpha * searchProgress));
            stroke(draw, ix, y, inputW, size, layout.v(13.0F), rgba(157, 78, 221, 0.23F * layout.alpha * searchProgress), layout.v(0.8F));
            ImGui.setCursorScreenPos(ix + layout.v(10.0F), y + layout.v(5.5F));
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0, 0, 0, 0);
            ImGui.pushStyleColor(ImGuiCol.Text, 230, 226, 234, Math.round(255.0F * layout.alpha));
            ImGui.pushStyleColor(ImGuiCol.TextDisabled, 128, 122, 132, Math.round(220.0F * layout.alpha));
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0.0F, 0.0F);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0F);
            ImGui.setNextItemWidth(Math.max(1.0F, inputW - layout.v(20.0F)));
            ImGui.inputTextWithHint("##module_search", "Search", searchText, ImGuiInputTextFlags.NoHorizontalScroll);
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(3);
        }
    }

    private void drawModules(ImDrawList draw, ImFont font, Layout layout, float dt) {
        List<VisualModule> visible = modules.stream()
                .filter(module -> module.category == selectedCategory)
                .filter(module -> searchText.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(searchText.get().toLowerCase(Locale.ROOT)))
                .toList();

        VisualModule hovered = null;
        int rendered = Math.min(10, visible.size());
        if (rendered == 0) {
            textCentered(draw, font, "NoModuels", layout.x(271.0F), layout.y(255.0F), layout.v(632.0F), layout.v(180.0F),
                    layout.v(22.0F), rgba(128, 122, 132, 0.84F * layout.alpha));
            return;
        }
        for (int i = 0; i < rendered; i++) {
            VisualModule module = visible.get(i);
            float x = i < 5 ? 302.0F : 598.0F;
            float y = 244.0F + (i % 5) * 59.0F;
            drawModuleCard(draw, font, layout, dt, module, x, y);
            if (module.hover > 0.12F) {
                hovered = module;
            }
        }

        if (hovered != null) {
            drawTooltip(draw, font, layout, hovered.hint, hovered.hover);
        }
    }

    private void drawModuleCard(ImDrawList draw, ImFont font, Layout layout, float dt, VisualModule module, float bx, float by) {
        float x = layout.x(bx);
        float y = layout.y(by);
        float w = layout.v(285.0F);
        float h = layout.v(47.0F);
        float r = layout.v(18.5F);

        invisibleButton("##module_" + module.name, x, y, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            module.active = !module.active;
        }
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            if ("FullBright".equals(module.name)) {
                showFullBrightSettings = !showFullBrightSettings;
            } else if ("TargetEsp".equals(module.name)) {
                showTargetEspSettings = !showTargetEspSettings;
            }
        }

        module.hover = approach(module.hover, hovered ? 1.0F : 0.0F, dt, 16.0F);
        module.toggle = approach(module.toggle, module.active ? 1.0F : 0.0F, dt, 14.0F);

        float hover = module.hover;
        rect(draw, x, y, w, h, r, rgba(18 + hover * 5.0F, 17 + hover * 5.0F, 19 + hover * 6.0F, 0.82F * layout.alpha));
        stroke(draw, x, y, w, h, r,
                module.active
                        ? rgba(123, 44, 191, (0.72F + hover * 0.18F) * layout.alpha)
                        : rgba(142, 139, 148, (0.38F + hover * 0.16F) * layout.alpha),
                layout.v(0.85F));
        if (module.active) {
            stroke(draw, x + layout.v(1.2F), y + layout.v(1.2F), w - layout.v(2.4F), h - layout.v(2.4F), r - layout.v(1.2F),
                    rgba(157, 78, 221, 0.08F * layout.alpha), layout.v(0.7F));
        }

        text(draw, font, module.name, x + layout.v(18.0F), y + layout.v(14.0F), layout.v(16.0F),
                rgba(151 + hover * 35.0F, 146 + hover * 35.0F, 153 + hover * 35.0F, layout.alpha));
        drawToggle(draw, layout, x + w - layout.v(63.0F), y + layout.v(14.0F), module.toggle, hover);
    }

    private void drawToggle(ImDrawList draw, Layout layout, float x, float y, float active, float hover) {
        float w = layout.v(42.0F);
        float h = layout.v(18.0F);
        float r = h * 0.5F;

        if (active > 0.02F) {
            glow(draw, x, y, w, h, r, rgba(157, 78, 221, 0.16F * active * layout.alpha), layout.v(5.0F));
        }
        rect(draw, x, y, w, h, r,
                mix(rgba(43, 40, 50, (0.92F + hover * 0.06F) * layout.alpha), rgba(106, 27, 154, 0.95F * layout.alpha), active));
        stroke(draw, x, y, w, h, r, rgba(255, 255, 255, 0.055F * layout.alpha), layout.v(0.6F));

        float knob = layout.v(14.5F);
        float knobX = x + layout.v(2.0F) + (w - knob - layout.v(4.0F)) * active;
        rect(draw, knobX, y + layout.v(1.75F), knob, knob, knob * 0.5F, rgba(232, 230, 236, layout.alpha));
    }

    private void drawTooltip(ImDrawList draw, ImFont font, Layout layout, String hint, float hover) {
        float alpha = Math.min(1.0F, hover * 1.45F) * layout.alpha;
        float x = layout.x(456.0F);
        float y = layout.y(121.0F);
        float w = layout.v(281.0F);
        float h = layout.v(35.0F);
        rect(draw, x, y, w, h, h * 0.5F, rgba(24, 24, 28, 0.9F * alpha));
        stroke(draw, x, y, w, h, h * 0.5F, rgba(255, 255, 255, 0.055F * alpha), layout.v(0.75F));
        textCentered(draw, font, hint, x, y - layout.v(0.5F), w, h, layout.v(17.0F), rgba(255, 255, 255, alpha));
    }

    private void drawSideCards(ImDrawList draw, ImFont font, ImGuiRenderer.Icons icons, Layout layout, float dt) {
        if (showFullBrightSettings) {
            drawFullBrightCard(draw, font, icons, layout, dt);
        }
        if (showTargetEspSettings) {
            drawTargetEspCard(draw, font, icons, layout, dt);
        }
    }

    private void drawFullBrightCard(ImDrawList draw, ImFont font, ImGuiRenderer.Icons icons, Layout layout, float dt) {
        float x = layout.x(76.0F);
        float y = layout.y(244.0F);
        float w = layout.v(188.0F);
        float h = layout.v(94.0F);
        float r = layout.v(17.0F);
        sideCard(draw, x, y, w, h, r, layout);

        textCentered(draw, font, "FullBright", x + layout.v(20.0F), y + layout.v(9.0F), layout.v(140.0F), layout.v(22.0F),
                layout.v(19.0F), rgba(255, 255, 255, layout.alpha));
        drawIcon(draw, icons.settingsTexture(), x + layout.v(160.0F), y + layout.v(12.5F), layout.v(18.0F), rgba(190, 190, 194, layout.alpha));
        textCentered(draw, font, "\u0423\u0432\u0435\u043b\u0438\u0447\u0435\u043d\u0438\u0435 \u0433\u0430\u043c\u043c\u044b", x + layout.v(29.0F), y + layout.v(35.0F), layout.v(130.0F), layout.v(15.0F),
                layout.v(12.5F), rgba(255, 255, 255, 0.88F * layout.alpha));
        drawVisualSlider(draw, font, layout, "fullbright_gamma", x + layout.v(23.0F), y + layout.v(58.0F), layout.v(140.0F),
                0.0F, new String[]{"0", "10", "20"}, dt);
    }

    private void drawTargetEspCard(ImDrawList draw, ImFont font, ImGuiRenderer.Icons icons, Layout layout, float dt) {
        float x = layout.x(914.0F);
        float y = layout.y(214.0F);
        float w = layout.v(201.0F);
        float h = layout.v(201.0F);
        float r = layout.v(17.0F);
        sideCard(draw, x, y, w, h, r, layout);

        textCentered(draw, font, "TargetEsp", x + layout.v(28.0F), y + layout.v(9.0F), layout.v(142.0F), layout.v(24.0F),
                layout.v(19.0F), rgba(255, 255, 255, layout.alpha));
        drawIcon(draw, icons.settingsTexture(), x + layout.v(169.0F), y + layout.v(13.0F), layout.v(18.0F), rgba(190, 190, 194, layout.alpha));
        text(draw, font, "\u0420\u0435\u0436\u0438\u043c", x + layout.v(12.0F), y + layout.v(41.0F), layout.v(18.0F), rgba(255, 255, 255, 0.94F * layout.alpha));
        rect(draw, x + layout.v(102.0F), y + layout.v(45.0F), layout.v(84.0F), layout.v(17.0F), layout.v(8.5F), rgba(72, 35, 84, 0.82F * layout.alpha));
        textCentered(draw, font, "\u041f\u0440\u0438\u0437\u0440\u0430\u043a\u0438  >", x + layout.v(106.0F), y + layout.v(45.0F), layout.v(76.0F), layout.v(17.0F),
                layout.v(12.5F), rgba(255, 255, 255, 0.95F * layout.alpha));

        text(draw, font, "\u0414\u043b\u0438\u043d\u0430 \u0442\u0430\u0440\u0433\u0435\u0442\u0430", x + layout.v(7.0F), y + layout.v(90.0F), layout.v(14.5F), rgba(255, 255, 255, 0.93F * layout.alpha));
        drawVisualSlider(draw, font, layout, "target_length", x + layout.v(17.0F), y + layout.v(116.0F), layout.v(158.0F),
                0.78F, new String[]{"2", "4", "6"}, dt);

        text(draw, font, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0438", x + layout.v(7.0F), y + layout.v(141.0F), layout.v(13.5F), rgba(255, 255, 255, 0.93F * layout.alpha));
        drawVisualSlider(draw, font, layout, "target_speed", x + layout.v(17.0F), y + layout.v(166.0F), layout.v(158.0F),
                0.99F, new String[]{"1", "50", "100"}, dt);
    }

    private void sideCard(ImDrawList draw, float x, float y, float w, float h, float r, Layout layout) {
        shadow(draw, x, y, w, h, r, layout);
        rect(draw, x, y, w, h, r, rgba(10, 10, 12, 0.9F * layout.alpha));
        stroke(draw, x, y, w, h, r, rgba(255, 255, 255, 0.052F * layout.alpha), layout.v(0.8F));
    }

    private void drawVisualSlider(ImDrawList draw, ImFont font, Layout layout, String id, float x, float y, float w,
                                  float defaultProgress, String[] labels, float dt) {
        VisualSlider slider = sliders.computeIfAbsent(id, ignored -> new VisualSlider(defaultProgress));
        invisibleButton("##slider_" + id, x - layout.v(4.0F), y - layout.v(7.0F), w + layout.v(8.0F), layout.v(20.0F));
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            draggingSlider = id;
        }
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left) && id.equals(draggingSlider)) {
            draggingSlider = null;
        }
        if (id.equals(draggingSlider)) {
            slider.progress = clamp((ImGui.getMousePosX() - x) / w);
        }

        slider.hover = approach(slider.hover, hovered || id.equals(draggingSlider) ? 1.0F : 0.0F, dt, 16.0F);
        slider.animatedProgress = approach(slider.animatedProgress, slider.progress, dt, 14.0F);

        float trackH = layout.v(2.0F);
        rect(draw, x, y, w, trackH, trackH * 0.5F, rgba(66, 35, 74, 0.82F * layout.alpha));
        rect(draw, x, y, w * slider.animatedProgress, trackH, trackH * 0.5F, rgba(157, 78, 221, 0.92F * layout.alpha));

        float knob = layout.v(6.5F + slider.hover * 1.3F);
        rect(draw, x + w * slider.animatedProgress - knob * 0.5F, y + trackH * 0.5F - knob * 0.5F,
                knob, knob, knob * 0.5F, rgba(226, 223, 230, layout.alpha));

        float labelY = y + layout.v(8.0F);
        text(draw, font, labels[0], x - layout.v(1.0F), labelY, layout.v(11.5F), rgba(255, 255, 255, 0.93F * layout.alpha));
        textCentered(draw, font, labels[1], x + w * 0.5F - layout.v(20.0F), labelY, layout.v(40.0F), layout.v(12.0F),
                layout.v(11.5F), rgba(255, 255, 255, 0.93F * layout.alpha));
        text(draw, font, labels[2], x + w - layout.v(labels[2].length() > 2 ? 21.0F : 4.0F), labelY,
                layout.v(11.5F), rgba(255, 255, 255, 0.93F * layout.alpha));
    }

    private static void drawIcon(ImDrawList draw, int texture, float x, float y, float size, int color) {
        if (texture != 0) {
            draw.addImage(texture, x, y, x + size, y + size, 0.0F, 0.0F, 1.0F, 1.0F, color);
        }
    }

    private static void invisibleButton(String id, float x, float y, float w, float h) {
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton(id, Math.max(1.0F, w), Math.max(1.0F, h),
                ImGuiButtonFlags.MouseButtonLeft | ImGuiButtonFlags.MouseButtonRight);
    }

    private static void shadow(ImDrawList draw, float x, float y, float w, float h, float radius, Layout layout) {
        rect(draw, x + layout.v(2.0F), y + layout.v(5.0F), w, h, radius, rgba(0, 0, 0, 0.24F * layout.alpha));
        rect(draw, x + layout.v(1.0F), y + layout.v(2.0F), w, h, radius, rgba(0, 0, 0, 0.16F * layout.alpha));
    }

    private static void glow(ImDrawList draw, float x, float y, float w, float h, float radius, int color, float spread) {
        rect(draw, x - spread, y - spread, w + spread * 2.0F, h + spread * 2.0F, radius + spread, color);
    }

    private static void rect(ImDrawList draw, float x, float y, float w, float h, float radius, int color) {
        draw.addRectFilled(x, y, x + w, y + h, color, radius, ROUND_ALL);
    }

    private static void stroke(ImDrawList draw, float x, float y, float w, float h, float radius, int color, float thickness) {
        draw.addRect(x, y, x + w, y + h, color, radius, ROUND_ALL, thickness);
    }

    private static void line(ImDrawList draw, float x1, float y1, float x2, float y2, int color, float thickness, Layout layout) {
        draw.addLine(layout.x(x1), layout.y(y1), layout.x(x2), layout.y(y2), color, thickness);
    }

    private static void text(ImDrawList draw, ImFont font, String value, float x, float y, float size, int color) {
        draw.addText(font, Math.max(1.0F, size), x, y, color, value);
    }

    private static void textCentered(ImDrawList draw, ImFont font, String value, float x, float y, float w, float h, float size, int color) {
        ImVec2 measured = ImGui.calcTextSize(value);
        float textW = measured.x * (size / 18.0F);
        float textH = measured.y * (size / 18.0F);
        draw.addText(font, Math.max(1.0F, size), x + (w - textW) * 0.5F, y + (h - textH) * 0.5F - size * 0.06F, color, value);
    }

    private static int rgba(float r, float g, float b, float a) {
        return ImGui.getColorU32(clamp(r / 255.0F), clamp(g / 255.0F), clamp(b / 255.0F), clamp(a));
    }

    private static int mix(int from, int to, float progress) {
        float t = clamp(progress);
        int fr = from & 255;
        int fg = from >>> 8 & 255;
        int fb = from >>> 16 & 255;
        int fa = from >>> 24 & 255;
        int tr = to & 255;
        int tg = to >>> 8 & 255;
        int tb = to >>> 16 & 255;
        int ta = to >>> 24 & 255;
        return rgba(fr + (tr - fr) * t, fg + (tg - fg) * t, fb + (tb - fb) * t, (fa + (ta - fa) * t) / 255.0F);
    }

    private static float approach(float current, float target, float dt, float speed) {
        float factor = 1.0F - (float) Math.exp(-speed * dt);
        return current + (target - current) * factor;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private float openingProgress() {
        float linear = clamp((System.nanoTime() - openedAt) / (float) OPEN_ANIMATION_NS);
        return 1.0F - (float) Math.pow(1.0F - linear, 3.0D);
    }

    private enum VisualCategory {
        VISUALS("Visuals"),
        HUD("Hud"),
        UTILS("Utils");

        private final String label;

        VisualCategory(String label) {
            this.label = label;
        }
    }

    private static final class VisualModule {
        private final String name;
        private final String hint;
        private final VisualCategory category;
        private boolean active;
        private float hover;
        private float toggle;

        private VisualModule(String name, String hint, VisualCategory category, boolean active) {
            this.name = name;
            this.hint = hint;
            this.category = category;
            this.active = active;
            this.toggle = active ? 1.0F : 0.0F;
        }
    }

    private static final class VisualSlider {
        private float progress;
        private float animatedProgress;
        private float hover;

        private VisualSlider(float value) {
            this.progress = value;
            this.animatedProgress = value;
        }
    }

    private record Layout(float originX, float originY, float scale, float alpha) {
        private float x(float value) {
            return originX + value * scale;
        }

        private float y(float value) {
            return originY + value * scale;
        }

        private float v(float value) {
            return value * scale;
        }
    }
}

