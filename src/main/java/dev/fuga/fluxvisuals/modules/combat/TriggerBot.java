package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

/**
 * Legit-style trigger bot: attacks whatever is under crosshair, mirroring
 * vanilla {@code MinecraftClient#doAttack} (attackEntity + swingHand).
 *
 * <p>Pacing follows the server cooldown, plus an optional 50-150 ms jitter.
 * Sprint is released 2-3 ticks before the scheduled hit and handed back to
 * AutoSprint immediately afterwards.
 *
 * <p>Critical hits are produced naturally by vanilla movement state. The
 * trigger never stalls an otherwise ready hit while waiting for a crit.
 */
public final class TriggerBot extends Module {
    public enum Targets {
        PLAYERS("Игроки"),
        MOBS("Мобы"),
        ALL("Все");

        private final String label;
        Targets(String label) { this.label = label; }
        public String label() { return label; }
    }

    // Shared with AutoSprint: while true, TriggerBot owns sprint for the
    // 2-3 tick pre-hit window.
    private static volatile boolean sprintHeld;

    public static boolean isSprintHeld() {
        return sprintHeld;
    }

    private float chance = 100.0F; // %
    private boolean cooldownJitter = false;
    private boolean maceNoCooldown = true;
    private boolean onlyOnAttackKey = false;
    private boolean noUseHit = true;
    private boolean jumpForCrit = false;
    private Targets targets = Targets.ALL;

    private long tickCounter = 0L;
    private long cooldownJitterUntilMs = 0L;
    private int currentJitterMs = 0;
    private boolean jitterArmed = true;
    private int trackedTargetId = Integer.MIN_VALUE;
    private float criticalTimingRoll = Float.NaN;
    private final TriggerBotTiming timing = new TriggerBotTiming();
    private int sprintLeadTicks = 2;
    private final java.util.Random random = new java.util.Random();

    public TriggerBot() {
        super("TriggerBot", "Auto-attacks entity under crosshair.", ModuleCategory.COMBAT);
        sprintLeadTicks = 2 + random.nextInt(2);
    }

    @Override
    public void onTick(MinecraftClient client) {
        tickCounter++;
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player)
                || client.world == null || client.interactionManager == null) {
            releaseSprintHold();
            return;
        }
        if (client.currentScreen != null) {
            resetTracking();
            return;
        }
        if (onlyOnAttackKey && !client.options.attackKey.isPressed()) {
            resetTracking();
            return;
        }
        if (!(client.crosshairTarget instanceof EntityHitResult crosshairHit)) {
            resetTracking();
            return;
        }
        Entity target = crosshairHit.getEntity();
        double hitDistanceSquared = player.getCameraPosVec(1.0F).squaredDistanceTo(crosshairHit.getPos());
        if (!TriggerBotTiming.acceptsVanillaHit(true, allows(player, target), hitDistanceSquared)) {
            resetTracking();
            return;
        }
        long now = System.currentTimeMillis();
        if (player.isUsingItem() || player.isBlocking()) {
            if (noUseHit) {
                resetTracking();
                return;
            }
        }
        if (jumpForCrit && player.isOnGround() && !player.isTouchingWater() && !player.isInLava()
                && !player.isSneaking() && !player.isUsingItem() && !player.hasVehicle()) {
            // hop immediately so the arc lines up with the coming cooldown;
            // NoJumpDelay makes re-hops instant
            player.jump();
        }

        if (target.getId() != trackedTargetId) {
            releaseSprintHold();
            trackedTargetId = target.getId();
            jitterArmed = true;
            cooldownJitterUntilMs = 0L;
            sprintLeadTicks = 2 + random.nextInt(2);
            criticalTimingRoll = Float.NaN;
        }

        // mace smash does not care about swing cooldown: hit the moment it is there
        boolean maceBypass = maceNoCooldown && player.getMainHandStack().isOf(net.minecraft.item.Items.MACE);
        float cooldownProgress = player.getAttackCooldownProgress(0.5F);
        float cooldownPeriodTicks = player.getAttackCooldownProgressPerTick();
        if (!maceBypass && cooldownJitter && jitterArmed) {
            jitterArmed = false;
            currentJitterMs = 50 + random.nextInt(101);
        }
        if (!maceBypass && !cooldownJitter && !timing.isHoldingSprint()
                && TriggerBotTiming.shouldBeginSprintDrop(
                        cooldownProgress, cooldownPeriodTicks, sprintLeadTicks)) {
            beginSprintDrop(player);
        }
        if (!maceBypass && !TriggerBotTiming.isServerCooldownReady(cooldownProgress)) {
            if (cooldownJitter && !timing.isHoldingSprint()) {
                float remainingMs = (TriggerBotTiming.SERVER_COOLDOWN_THRESHOLD - cooldownProgress)
                        * cooldownPeriodTicks * 50.0F
                        + currentJitterMs;
                if (remainingMs <= sprintLeadTicks * 50.0F) {
                    beginSprintDrop(player);
                }
            }
            cooldownJitterUntilMs = 0L;
            return;
        }
        if (!maceBypass && cooldownJitter) {
            if (cooldownJitterUntilMs == 0L) {
                cooldownJitterUntilMs = now + currentJitterMs;
            }
            if (now < cooldownJitterUntilMs) {
                long remainingMs = cooldownJitterUntilMs - now;
                if (!timing.isHoldingSprint() && remainingMs <= sprintLeadTicks * 50L) {
                    beginSprintDrop(player);
                }
                return;
            }
        }

        if (!timing.isHoldingSprint()) {
            beginSprintDrop(player);
            return;
        }
        setSprint(player, false);
        if (!timing.isAttackReady(tickCounter)) {
            return;
        }

        boolean airborne = isCleanJumpArc(player);
        boolean criticalFall = isCriticalFall(player);
        if (airborne && Float.isNaN(criticalTimingRoll)) {
            criticalTimingRoll = random.nextFloat() * 100.0F;
        }
        // Chance controls crit timing, not whether an attack disappears. At
        // 100% an ascending jump always waits for the first falling tick. A
        // failed roll intentionally produces an occasional normal early hit.
        if (TriggerBotTiming.shouldWaitForCritical(
                airborne, criticalFall,
                player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER),
                criticalTimingRoll, chance)) {
            return;
        }

        // Vanilla left-click (LMB) simulation via client.doAttack():
        // This executes the exact client attack pipeline (item cooldown, particle effects, hit registration, vanilla packets)
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        releaseSprintHold();
        jitterArmed = true;
        cooldownJitterUntilMs = 0L;
        currentJitterMs = 0;
        sprintLeadTicks = 2 + random.nextInt(2);
        criticalTimingRoll = Float.NaN;
    }

    private void resetTracking() {
        trackedTargetId = Integer.MIN_VALUE;
        jitterArmed = true;
        cooldownJitterUntilMs = 0L;
        currentJitterMs = 0;
        criticalTimingRoll = Float.NaN;
        releaseSprintHold();
    }

    private void beginSprintDrop(ClientPlayerEntity player) {
        timing.beginSprintDrop(tickCounter, sprintLeadTicks);
        sprintHeld = true;
        if (player.isSprinting()) {
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                    player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            player.setSprinting(false);
        }
    }

    private void releaseSprintHold() {
        timing.finishAttack();
        sprintHeld = false;
    }

    private static void setSprint(ClientPlayerEntity player, boolean sprint) {
        if (player.isSprinting() != sprint) {
            player.setSprinting(sprint);
        }
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        releaseSprintHold();
    }

    private boolean allows(ClientPlayerEntity player, Entity e) {
        if (e == null || e == player || e.isRemoved() || !e.canHit()) return false;
        if (e instanceof LivingEntity living && !living.isAlive()) return false;
        if (e instanceof PlayerEntity) {
            if (targets == Targets.MOBS) return false;
        } else if (e instanceof MobEntity) {
            if (targets == Targets.PLAYERS) return false;
        } else if (!(e instanceof LivingEntity)) {
            return false;
        }
        return true;
    }

    private static boolean isCleanJumpArc(ClientPlayerEntity player) {
        return !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.isInLava()
                && !player.hasVehicle()
                && !player.isGliding();
    }

    private static boolean isCriticalFall(ClientPlayerEntity player) {
        return isCleanJumpArc(player)
                && player.fallDistance > 0.0F
                && !player.isSprinting()
                && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
    }

    // ---- getters/setters ----
    public float getChance() { return chance; }
    public void setChance(float v) { chance = MathHelper.clamp(v, 1.0F, 100.0F); FluxVisualsClient.requestConfigSave(); }
    public boolean isCooldownJitter() { return cooldownJitter; }
    public void setCooldownJitter(boolean v) { cooldownJitter = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isMaceNoCooldown() { return maceNoCooldown; }
    public void setMaceNoCooldown(boolean v) { maceNoCooldown = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isOnlyOnAttackKey() { return onlyOnAttackKey; }
    public void setOnlyOnAttackKey(boolean v) { onlyOnAttackKey = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isNoUseHit() { return noUseHit; }
    public void setNoUseHit(boolean v) { noUseHit = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isJumpForCrit() { return jumpForCrit; }
    public void setJumpForCrit(boolean v) { jumpForCrit = v; FluxVisualsClient.requestConfigSave(); }
    public Targets getTargets() { return targets; }
    public void setTargets(Targets v) { targets = v == null ? Targets.PLAYERS : v; FluxVisualsClient.requestConfigSave(); }
    public String getTargetsName() { return targets == null ? "Игроки" : targets.label(); }
    public void setTargetsName(String name) {
        if (name == null) return;
        for (Targets t : Targets.values()) {
            if (t.label().equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) { setTargets(t); return; }
        }
    }
}
