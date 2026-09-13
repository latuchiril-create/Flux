package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Places and breaks crystals only when the blast position is safely far from the owner. */
public final class CrystalAura extends Module {
    private float targetRange = 4.5F;
    private float ownerSafeRange = 3.5F;
    private long nextActionAt;

    public CrystalAura() { super("CrystalAura", "Safe-range crystal placement and detonation.", ModuleCategory.COMBAT); }

    @Override public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player) || client.world == null || client.interactionManager == null || client.currentScreen != null) return;
        long now = System.currentTimeMillis(); if (now < nextActionAt) return;
        PlayerEntity target = nearest(player, client);
        if (target == null) return;
        for (Entity entity : client.world.getEntities()) if (entity instanceof EndCrystalEntity crystal
                && crystal.squaredDistanceTo(target) <= 25.0D && crystal.squaredDistanceTo(player) >= ownerSafeRange * ownerSafeRange) {
            client.interactionManager.attackEntity(player, crystal); player.swingHand(Hand.MAIN_HAND); nextActionAt = now + 120L; return;
        }
        BlockPos base = target.getBlockPos().down();
        boolean localFake = dev.fuga.fluxvisuals.FluxVisualsClient.MODULE_MANAGER.getFakePlayer().isFakePlayer(target);
        if ((!localFake && player.squaredDistanceTo(Vec3d.ofCenter(base)) < ownerSafeRange * ownerSafeRange) || !client.world.getBlockState(base.up()).isReplaceable()) return;
        int obsidian = slot(player, Items.OBSIDIAN), crystal = slot(player, Items.END_CRYSTAL);
        if (obsidian < 0 || crystal < 0) return;
        int restore = player.getInventory().getSelectedSlot();
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(base), Direction.UP, base, false);
        if (!client.world.getBlockState(base).isOf(net.minecraft.block.Blocks.OBSIDIAN)) {
            select(player, obsidian); client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit); select(player, restore); nextActionAt = now + 180L; return;
        }
        select(player, crystal); client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit); select(player, restore); nextActionAt = now + 180L;
    }
    private PlayerEntity nearest(ClientPlayerEntity self, MinecraftClient c) { PlayerEntity best=null; double d=targetRange*targetRange; for(Entity e:c.world.getEntities()) if(e instanceof PlayerEntity p && p!=self&&!p.isSpectator()&&p.isAlive()&&self.squaredDistanceTo(p)<d){d=self.squaredDistanceTo(p);best=p;} return best; }
    private static int slot(ClientPlayerEntity p, net.minecraft.item.Item item) { for(int i=0;i<9;i++){ItemStack s=p.getInventory().getStack(i);if(s.isOf(item))return i;}return -1; }
    private static void select(ClientPlayerEntity p,int slot){p.getInventory().setSelectedSlot(slot);if(p.networkHandler!=null)p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));}
    public float getTargetRange() { return targetRange; }
    public void setTargetRange(float value) { targetRange = net.minecraft.util.math.MathHelper.clamp(value, 2F, 4.5F); }
    public float getOwnerSafeRange() { return ownerSafeRange; }
    public void setOwnerSafeRange(float value) { ownerSafeRange = net.minecraft.util.math.MathHelper.clamp(value, 2F, 5F); }
}
