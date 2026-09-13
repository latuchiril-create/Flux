package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Builds a TNT-minecart setup only outside the configured owner-safe radius. */
public final class AutoCart extends Module {
    private float targetRange = 4.5F, ownerSafeRange = 3.5F;
    private long nextActionAt;
    public AutoCart(){super("AutoCart","Places and ignites TNT minecarts at a safe distance.",ModuleCategory.COMBAT);}
    @Override public void onTick(MinecraftClient c){
        if(!isEnabled()||c==null||!(c.player instanceof ClientPlayerEntity p)||c.world==null||c.interactionManager==null||c.currentScreen!=null)return;
        long now=System.currentTimeMillis();if(now<nextActionAt)return; PlayerEntity t=nearest(p,c);if(t==null)return;
        for(Entity e:c.world.getEntities())if(e instanceof TntMinecartEntity cart&&cart.squaredDistanceTo(t)<16D&&cart.squaredDistanceTo(p)>=ownerSafeRange*ownerSafeRange){int flint=slot(p,Items.FLINT_AND_STEEL);if(flint>=0){int old=p.getInventory().getSelectedSlot();select(p,flint);c.interactionManager.interactEntity(p,cart,Hand.MAIN_HAND);p.swingHand(Hand.MAIN_HAND);select(p,old);nextActionAt=now+250L;}return;}
        BlockPos pos=t.getBlockPos();int rail=slot(p,Items.RAIL),cart=slot(p,Items.TNT_MINECART);boolean localFake=dev.fuga.fluxvisuals.FluxVisualsClient.MODULE_MANAGER.getFakePlayer().isFakePlayer(t);if((!localFake&&p.squaredDistanceTo(Vec3d.ofCenter(pos))<ownerSafeRange*ownerSafeRange)||(!c.world.getBlockState(pos).isReplaceable()&&!c.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.RAIL)))return;if(rail<0||cart<0)return;int old=p.getInventory().getSelectedSlot();BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(pos.down()),Direction.UP,pos.down(),false);if(!c.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.RAIL)){select(p,rail);c.interactionManager.interactBlock(p,Hand.MAIN_HAND,hit);select(p,old);nextActionAt=now+180L;return;}select(p,cart);c.interactionManager.interactBlock(p,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false));select(p,old);nextActionAt=now+220L;
    }
    private PlayerEntity nearest(ClientPlayerEntity self,MinecraftClient c){PlayerEntity out=null;double d=targetRange*targetRange;for(Entity e:c.world.getEntities())if(e instanceof PlayerEntity p&&p!=self&&!p.isSpectator()&&p.isAlive()&&self.squaredDistanceTo(p)<d){d=self.squaredDistanceTo(p);out=p;}return out;}
    private static int slot(ClientPlayerEntity p,net.minecraft.item.Item item){for(int i=0;i<9;i++){ItemStack s=p.getInventory().getStack(i);if(s.isOf(item))return i;}return -1;}
    private static void select(ClientPlayerEntity p,int s){p.getInventory().setSelectedSlot(s);if(p.networkHandler!=null)p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(s));}
    public float getTargetRange(){return targetRange;} public void setTargetRange(float value){targetRange=net.minecraft.util.math.MathHelper.clamp(value,2F,4.5F);} public float getOwnerSafeRange(){return ownerSafeRange;} public void setOwnerSafeRange(float value){ownerSafeRange=net.minecraft.util.math.MathHelper.clamp(value,2F,5F);}
}
