package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;

public final class TabCustomizer extends Module {
    private int columns = 4;
    private int playersPerColumn = 20;
    private float scale = 1.0F;

    public TabCustomizer() {
        super("TabCustomizer", "Controls player list columns and density.", ModuleCategory.VISUALS);
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        int next = Math.max(1, Math.min(8, columns));
        if (this.columns == next) {
            return;
        }
        this.columns = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getPlayersPerColumn() {
        return playersPerColumn;
    }

    public void setPlayersPerColumn(int playersPerColumn) {
        int next = Math.max(1, Math.min(30, playersPerColumn));
        if (this.playersPerColumn == next) {
            return;
        }
        this.playersPerColumn = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        float next = 0.75F + clamp01((scale - 0.75F) / 0.5F) * 0.5F;
        if (Math.abs(this.scale - next) < 0.001F) {
            return;
        }
        this.scale = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int effectiveRows(int playerCount) {
        if (!isEnabled()) {
            return 20;
        }
        int rowsForColumns = Math.max(1, (playerCount + columns - 1) / columns);
        return Math.max(1, Math.min(playersPerColumn, rowsForColumns));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
