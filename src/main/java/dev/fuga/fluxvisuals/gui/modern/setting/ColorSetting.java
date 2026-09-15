package dev.fuga.fluxvisuals.gui.modern.setting;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.Menu;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ColorSetting extends Setting<Integer> {
    public static final Set<String> SYNCED_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<ColorSetting> ALL_INSTANCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private final String id;
    public boolean pickerOpen;
    public float hue = 0.65F;
    public float saturation = 0.8F;
    public float brightness = 0.9F;

    public ColorSetting(String id, String name, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(name, getter, setter);
        this.id = id;
        ALL_INSTANCES.add(this);
        int color = getter.get();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float[] hsb = java.awt.Color.RGBtoHSB(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public ColorSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
        this(name, name, getter, setter);
    }

    public String getId() {
        return id;
    }

    public boolean isSyncedWithTheme() {
        return SYNCED_KEYS.contains(id);
    }

    public void setSyncedWithTheme(boolean synced) {
        if (synced) {
            SYNCED_KEYS.add(id);
            updateFromTheme();
        } else {
            SYNCED_KEYS.remove(id);
        }
    }

    public void updateFromTheme() {
        if (FluxVisualsClient.MODULE_MANAGER != null) {
            Menu menu = FluxVisualsClient.MODULE_MANAGER.getMenu();
            if (menu != null) {
                int themeRgb = menu.getLiveColor(0.0F);
                int currentAlpha = (get() >>> 24);
                if (currentAlpha == 0 && (get() & 0x00FFFFFF) == 0) currentAlpha = 255;
                set((currentAlpha << 24) | (themeRgb & 0x00FFFFFF));
                float r = ((themeRgb >> 16) & 0xFF) / 255.0F;
                float g = ((themeRgb >> 8) & 0xFF) / 255.0F;
                float b = (themeRgb & 0xFF) / 255.0F;
                float[] hsb = java.awt.Color.RGBtoHSB(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), null);
                this.hue = hsb[0];
                this.saturation = hsb[1];
                this.brightness = hsb[2];
            }
        }
    }

    public void updateFromHsb() {
        setSyncedWithTheme(false);
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        int alpha = (get() >>> 24);
        set((alpha << 24) | rgb);
    }

    public static void updateAllSyncedColors() {
        if (FluxVisualsClient.MODULE_MANAGER == null) return;
        Menu menu = FluxVisualsClient.MODULE_MANAGER.getMenu();
        if (menu == null) return;
        int themeRgb = menu.getLiveColor(0.0F);

        ColorSetting[] instances;
        synchronized (ALL_INSTANCES) {
            instances = ALL_INSTANCES.toArray(new ColorSetting[0]);
        }
        for (ColorSetting cs : instances) {
            if (cs != null && cs.isSyncedWithTheme()) {
                int currentAlpha = (cs.get() >>> 24);
                if (currentAlpha == 0 && (cs.get() & 0x00FFFFFF) == 0) currentAlpha = 255;
                cs.set((currentAlpha << 24) | (themeRgb & 0x00FFFFFF));
                float r = ((themeRgb >> 16) & 0xFF) / 255.0F;
                float g = ((themeRgb >> 8) & 0xFF) / 255.0F;
                float b = (themeRgb & 0xFF) / 255.0F;
                float[] hsb = java.awt.Color.RGBtoHSB(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), null);
                cs.hue = hsb[0];
                cs.saturation = hsb[1];
                cs.brightness = hsb[2];
            }
        }
    }

    private static volatile boolean isSyncing = false;

    public static void syncAllToTheme() {
        if (isSyncing) return;
        if (FluxVisualsClient.MODULE_MANAGER == null) return;
        Menu menu = FluxVisualsClient.MODULE_MANAGER.getMenu();
        if (menu == null) return;

        isSyncing = true;
        try {
            ColorSetting[] instances;
            synchronized (ALL_INSTANCES) {
                instances = ALL_INSTANCES.toArray(new ColorSetting[0]);
            }
            for (ColorSetting cs : instances) {
                if (cs != null && !cs.getId().toLowerCase(java.util.Locale.ROOT).startsWith("menu:")) {
                    cs.setSyncedWithTheme(true);
                }
            }
        } finally {
            isSyncing = false;
        }
    }
}
