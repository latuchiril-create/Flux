package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class Setting<T> {
    private final String name;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private BooleanSupplier visibleSupplier = () -> true;

    protected Setting(String name, Supplier<T> getter, Consumer<T> setter) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() {
        return name;
    }

    public T get() {
        return getter.get();
    }

    public void set(T value) {
        setter.accept(value);
    }

    public boolean isVisible() {
        return visibleSupplier == null || visibleSupplier.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visible(BooleanSupplier visibleSupplier) {
        this.visibleSupplier = visibleSupplier;
        return (S) this;
    }
}
