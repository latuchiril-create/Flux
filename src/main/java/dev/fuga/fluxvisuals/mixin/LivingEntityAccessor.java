package dev.fuga.fluxvisuals.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumpingCooldown")
    int fluxvisuals$getJumpingCooldown();

    @Accessor("jumpingCooldown")
    void fluxvisuals$setJumpingCooldown(int cooldown);
}
