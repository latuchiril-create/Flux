package dev.fuga.fluxvisuals.mixin;

import java.util.UUID;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BossBarS2CPacket.class)
public interface BossBarPacketAccessor {
    @Accessor("uuid")
    UUID fluxvisuals$getUuid();

}
