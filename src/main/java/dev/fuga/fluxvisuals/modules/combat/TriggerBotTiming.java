package dev.fuga.fluxvisuals.modules.combat;

/** Pure tick gate for TriggerBot's pre-hit sprint reset. */
final class TriggerBotTiming {
    static final float SERVER_COOLDOWN_THRESHOLD = 0.92F;
    private long sprintDropTick = Long.MIN_VALUE;
    private int sprintDropTicks;

    void beginSprintDrop(long tick, int ticks) {
        if (isHoldingSprint()) {
            return;
        }
        sprintDropTick = tick;
        sprintDropTicks = Math.max(2, Math.min(3, ticks));
    }

    boolean isHoldingSprint() {
        return sprintDropTick != Long.MIN_VALUE;
    }

    boolean isAttackReady(long tick) {
        return isHoldingSprint() && tick - sprintDropTick >= sprintDropTicks;
    }

    void finishAttack() {
        sprintDropTick = Long.MIN_VALUE;
        sprintDropTicks = 0;
    }

    static boolean shouldBeginSprintDrop(float cooldownProgress, float cooldownPeriodTicks, int leadTicks) {
        float progress = Math.max(0.0F, Math.min(SERVER_COOLDOWN_THRESHOLD, cooldownProgress));
        float remainingTicks = (SERVER_COOLDOWN_THRESHOLD - progress) * Math.max(1.0F, cooldownPeriodTicks);
        return remainingTicks <= Math.max(2, Math.min(3, leadTicks)) + 0.0001F;
    }

    static boolean isServerCooldownReady(float cooldownProgress) {
        return cooldownProgress >= SERVER_COOLDOWN_THRESHOLD;
    }

    static boolean shouldWaitForCritical(boolean airborne, boolean criticalFall,
                                         float rollPercent, float criticalChance) {
        return shouldWaitForCritical(airborne, criticalFall, false, rollPercent, criticalChance);
    }

    static boolean shouldWaitForCritical(boolean airborne, boolean criticalFall, boolean forceImmediate,
                                         float rollPercent, float criticalChance) {
        return !forceImmediate && airborne && !criticalFall && rollPercent < criticalChance;
    }

    static boolean acceptsVanillaHit(boolean entityHit, boolean allowedTarget, double hitDistanceSquared) {
        return entityHit && allowedTarget && hitDistanceSquared <= 9.0D;
    }
}
