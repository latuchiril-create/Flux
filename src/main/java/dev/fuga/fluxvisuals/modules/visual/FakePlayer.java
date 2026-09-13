package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.authlib.GameProfile;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public final class FakePlayer extends Module {
    private static int nextEntityId = -42000;

    private OtherClientPlayerEntity fakePlayer;

    public FakePlayer() {
        super("FakePlayer", "Spawns a local client-side player copy.", ModuleCategory.UTILS);
    }

    @Override
    protected void onEnable(MinecraftClient client) {
        spawn(client);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        remove(client);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null) {
            remove(client);
            return;
        }

        if (fakePlayer != null && (fakePlayer.isRemoved() || fakePlayer.clientWorld != client.world)) {
            fakePlayer = null;
        }

        if (fakePlayer != null) {
            keepAlive();
        }

        if (fakePlayer == null) {
            spawn(client);
        }
    }

    public boolean handleAttack(PlayerEntity attacker, Entity target) {
        if (!isFakePlayer(target) || fakePlayer == null || attacker == null) {
            return false;
        }

        boolean critical = isCriticalHit(attacker);
        float damage = attackDamage(attacker);
        playAttackSound(attacker, damage, critical);
        attacker.swingHand(Hand.MAIN_HAND);
        attacker.resetLastAttackedTicks();
        FluxVisualsClient.MODULE_MANAGER.getHitColor().markHit(fakePlayer);
        if (critical && FluxVisualsClient.MODULE_MANAGER.getParticles().isModeEnabled(Particles.SpawnMode.ON_CRIT)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().spawnFakeHitBurst(fakePlayer, hitPos(attacker, fakePlayer));
        }
        float nextHealth = fakePlayer.getHealth() - damage;
        if (nextHealth <= 1.0F) {
            consumeTotem();
            return true;
        }

        fakePlayer.setHealth(Math.max(0.0F, nextHealth));
        fakePlayer.animateDamage(attacker.getYaw());
        fakePlayer.hurtTime = 10;
        fakePlayer.maxHurtTime = 10;
        playSound(SoundEvents.ENTITY_PLAYER_HURT, 0.75F, 0.92F + attacker.getRandom().nextFloat() * 0.14F);
        knockBack(attacker);
        return true;
    }

    public ActionResult handleInteract(PlayerEntity player, Entity target, Hand hand) {
        if (!isFakePlayer(target) || fakePlayer == null) {
            return null;
        }

        fakePlayer.swingHand(hand == null ? Hand.MAIN_HAND : hand);
        FluxVisualsClient.MODULE_MANAGER.getHitColor().markHit(fakePlayer);
        return ActionResult.PASS;
    }

    public boolean isFakePlayer(Entity entity) {
        return entity != null && entity == fakePlayer;
    }

    public Entity getEntity() {
        return fakePlayer;
    }

    private void spawn(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || fakePlayer != null) {
            return;
        }

        ClientWorld world = client.world;
        GameProfile profile = new GameProfile(UUID.randomUUID(), client.player.getGameProfile().getName());
        profile.getProperties().putAll(client.player.getGameProfile().getProperties());
        OtherClientPlayerEntity player = new LocalFakePlayerEntity(world, profile);
        player.setId(nextEntityId--);
        player.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), client.player.getYaw(), client.player.getPitch());
        player.lastYaw = client.player.lastYaw;
        player.lastPitch = client.player.lastPitch;
        player.setBodyYaw(client.player.getBodyYaw());
        player.setHeadYaw(client.player.getHeadYaw());
        player.noClip = false;
        player.setNoGravity(false);
        player.setHealth(20.0F);
        refillTotem();
        world.addEntity(player);
        fakePlayer = player;
        refillTotem();
    }

    private void remove(MinecraftClient client) {
        if (fakePlayer == null) {
            return;
        }

        if (client != null && client.world != null && client.world.getEntityById(fakePlayer.getId()) == fakePlayer) {
            client.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        } else {
            fakePlayer.remove(Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
    }

    private void consumeTotem() {
        fakePlayer.setHealth(20.0F);
        fakePlayer.setAbsorptionAmount(4.0F);
        fakePlayer.hurtTime = 10;
        fakePlayer.maxHurtTime = 10;
        refillTotem();
        if (FluxVisualsClient.MODULE_MANAGER.getParticles().isModeEnabled(Particles.SpawnMode.ON_TOTEM_POP)) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().spawnTotemBurst(fakePlayer);
        }
        FluxVisualsClient.MODULE_MANAGER.getHitColor().markHit(fakePlayer);
        playSound(SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
    }

    private void keepAlive() {
        if (fakePlayer.getHealth() <= 1.0F) {
            fakePlayer.setHealth(20.0F);
        }
        fakePlayer.deathTime = 0;
        refillTotem();
    }

    private void refillTotem() {
        if (fakePlayer != null && !fakePlayer.getStackInHand(Hand.OFF_HAND).isOf(Items.TOTEM_OF_UNDYING)) {
            fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    private static float attackDamage(PlayerEntity attacker) {
        double base = attacker.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE);
        double[] additions = {0.0D};
        double[] multipliedBase = {0.0D};
        double[] multipliedTotal = {1.0D};
        attacker.getStackInHand(Hand.MAIN_HAND).applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.matches(EntityAttributes.ATTACK_DAMAGE)) {
                return;
            }

            if (modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                additions[0] += modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                multipliedBase[0] += base * modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                multipliedTotal[0] *= 1.0D + modifier.value();
            }
        });

        float baseDamage = (float) Math.max(1.0D, (base + additions[0] + multipliedBase[0]) * multipliedTotal[0]);
        float cooldown = attacker.getAttackCooldownProgress(0.5F);
        float damage = baseDamage * (0.2F + cooldown * cooldown * 0.8F);
        if (isCriticalHit(attacker)) {
            damage *= 1.5F;
        }
        return Math.max(1.0F, damage);
    }

    private static boolean isCriticalHit(PlayerEntity attacker) {
        return attacker.fallDistance > 0.0F
                && !attacker.isOnGround()
                && !attacker.isTouchingWater()
                && attacker.getAttackCooldownProgress(0.5F) > 0.84F;
    }

    private void playAttackSound(PlayerEntity attacker, float damage, boolean critical) {
        if (critical) {
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
        } else if (attacker.getAttackCooldownProgress(0.5F) > 0.84F) {
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
        } else if (damage <= 1.1F) {
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.75F, 1.0F);
        } else {
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 0.85F, 1.0F);
        }
    }

    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || fakePlayer == null) {
            return;
        }

        client.world.playSoundClient(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), sound, SoundCategory.PLAYERS, volume, pitch, false);
    }

    private static Vec3d hitPos(PlayerEntity attacker, Entity target) {
        Vec3d start = attacker.getEyePos();
        Vec3d end = start.add(attacker.getRotationVec(1.0F).multiply(6.0D));
        return target.getBoundingBox().expand(0.1D).raycast(start, end)
                .orElse(target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D));
    }

    private void knockBack(PlayerEntity attacker) {
        Vec3d delta = fakePlayer.getPos().subtract(attacker.getPos());
        double length = Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        fakePlayer.addVelocity(delta.x / length * 0.28D, 0.12D, delta.z / length * 0.28D);
    }

    private static final class LocalFakePlayerEntity extends OtherClientPlayerEntity {
        private LocalFakePlayerEntity(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public boolean canHit() {
            return true;
        }

        @Override
        public boolean isAttackable() {
            return true;
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean collidesWith(Entity other) {
            return false;
        }

        @Override
        public boolean isCollidable(Entity entity) {
            return false;
        }

        @Override
        public boolean shouldRender(double distance) {
            return true;
        }
    }
}
