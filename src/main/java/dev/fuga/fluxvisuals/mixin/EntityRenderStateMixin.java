package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.util.FluxEntityRenderState;
import dev.fuga.fluxvisuals.util.FluxNametagState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements FluxEntityRenderState, FluxNametagState {
    private int fluxvisuals$entityId = -1;
    private Text fluxvisuals$belowNametag;

    @Override
    public int fluxvisuals$getEntityId() {
        return fluxvisuals$entityId;
    }

    @Override
    public void fluxvisuals$setEntityId(int entityId) {
        fluxvisuals$entityId = entityId;
    }

    @Override
    public Text fluxvisuals$getBelowNametag() {
        return fluxvisuals$belowNametag;
    }

    @Override
    public void fluxvisuals$setBelowNametag(Text text) {
        fluxvisuals$belowNametag = text;
    }
}
