package dev.fuga.fluxvisuals.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerBotTimingTest {
    @Test
    void releasesAttackAfterExactlyTwoPreparationTicks() {
        TriggerBotTiming timing = new TriggerBotTiming();

        timing.beginSprintDrop(40L, 2);

        assertTrue(timing.isHoldingSprint());
        assertFalse(timing.isAttackReady(41L));
        assertTrue(timing.isAttackReady(42L));
    }

    @Test
    void releasesAttackAfterExactlyThreePreparationTicks() {
        TriggerBotTiming timing = new TriggerBotTiming();

        timing.beginSprintDrop(80L, 3);

        assertFalse(timing.isAttackReady(82L));
        assertTrue(timing.isAttackReady(83L));
        timing.finishAttack();
        assertFalse(timing.isHoldingSprint());
    }

    @Test
    void startsPreparationBeforeServerCooldownCompletes() {
        assertFalse(TriggerBotTiming.shouldBeginSprintDrop(0.70F, 10.0F, 2));
        assertTrue(TriggerBotTiming.shouldBeginSprintDrop(0.72F, 10.0F, 2));
        assertTrue(TriggerBotTiming.shouldBeginSprintDrop(0.67F, 12.0F, 3));
    }

    @Test
    void serverCooldownIsReadyWithoutWaitingForAnExtraTick() {
        assertFalse(TriggerBotTiming.isServerCooldownReady(0.91F));
        assertTrue(TriggerBotTiming.isServerCooldownReady(0.92F));
    }

    @Test
    void waitsForFallWhenCriticalTimingRollSucceeds() {
        assertTrue(TriggerBotTiming.shouldWaitForCritical(true, false, 34.0F, 100.0F));
        assertFalse(TriggerBotTiming.shouldWaitForCritical(true, true, 34.0F, 100.0F));
        assertFalse(TriggerBotTiming.shouldWaitForCritical(false, false, 34.0F, 100.0F));
    }

    @Test
    void failedCriticalTimingRollProducesNormalHitInsteadOfSkippingAttack() {
        assertFalse(TriggerBotTiming.shouldWaitForCritical(true, false, 85.0F, 80.0F));
    }

    @Test
    void witherDamageNeverBlocksReadyAttackForCriticalTiming() {
        assertFalse(TriggerBotTiming.shouldWaitForCritical(true, false, true, 0.0F, 100.0F));
    }

    @Test
    void vanillaHitboxResultIsNotRejectedByDuplicateRaycasts() {
        assertTrue(TriggerBotTiming.acceptsVanillaHit(true, true, 8.999D));
        assertFalse(TriggerBotTiming.acceptsVanillaHit(true, true, 9.001D));
        assertFalse(TriggerBotTiming.acceptsVanillaHit(false, true, 1.0D));
    }
}
