package dev.fuga.fluxvisuals.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int fluxvisuals$getX();

    @Accessor("y")
    int fluxvisuals$getY();

    @Accessor("backgroundWidth")
    int fluxvisuals$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int fluxvisuals$getBackgroundHeight();

    @Accessor("focusedSlot")
    net.minecraft.screen.slot.Slot fluxvisuals$getFocusedSlot();

    @Invoker("getSlotAt")
    net.minecraft.screen.slot.Slot fluxvisuals$getSlotAt(double x, double y);

}
