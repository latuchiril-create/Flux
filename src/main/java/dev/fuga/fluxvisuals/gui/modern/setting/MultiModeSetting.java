package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MultiModeSetting extends Setting<Set<String>> {
    private final List<String> allOptions;
    public boolean expanded;

    public MultiModeSetting(String name, List<String> allOptions, Supplier<Set<String>> getter, Consumer<Set<String>> setter) {
        super(name, getter, setter);
        this.allOptions = allOptions;
    }

    public List<String> getAllOptions() {
        return allOptions;
    }

    public boolean isSelected(String option) {
        Set<String> current = get();
        return current != null && current.contains(option);
    }

    public void toggleOption(String option) {
        Set<String> raw = get();
        Set<String> current = (raw != null) ? new java.util.LinkedHashSet<>(raw) : new java.util.LinkedHashSet<>();
        if (current.contains(option)) {
            current.remove(option);
        } else {
            current.add(option);
        }
        set(current);
    }

    public String getDisplaySummary() {
        return getDisplaySummary(false);
    }

    public String getDisplaySummary(boolean isEn) {
        Set<String> current = get();
        if (current == null || current.isEmpty()) {
            return isEn ? "None" : "Не выбрано";
        }
        if (current.size() == allOptions.size()) {
            return (isEn ? "All (" : "Все (") + allOptions.size() + ")";
        }
        List<String> selected = new ArrayList<>();
        for (String opt : allOptions) {
            if (current.contains(opt)) {
                selected.add(opt);
            }
        }
        String summary = String.join(", ", selected);
        return summary.length() > 14 ? (isEn ? (current.size() + " Selected") : (current.size() + " выбр.")) : summary;
    }
}
