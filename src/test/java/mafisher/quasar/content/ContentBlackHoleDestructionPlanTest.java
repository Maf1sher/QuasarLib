package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentBlackHoleDestructionPlanTest {
    private static final double GROUND_Y = 64.0;
    private static final double MIN_BUILD_HEIGHT = -64.0;

    @Test
    void requestedSizeScalesWithTheHorizon() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(settings.craterRadius(12.0F), plan.requestedRadius(), 1.0E-6);
        assertEquals(127.0, plan.requestedDepth(), 1.0E-6);
        assertEquals(plan.requestedRadius(), plan.radius(), 1.0E-6);
        assertEquals(plan.requestedDepth(), plan.depth(), 1.0E-6);
        assertTrue(plan.startProgress() >= ContentBlackHoleCinematics.HORIZON_END);
        assertTrue(plan.workEndProgress() < ContentBlackHoleCinematics.DESTABILISE);
        assertEquals(
            ContentBlackHoleCinematics.DESTABILISE - 0.10F,
            plan.workEndProgress(),
            1.0E-6
        );
    }

    @Test
    void destructionEndsWithARevealReserveBeforeClosing() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan shortPlan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 300, 8, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        ContentBlackHoleDestructionPlan.Plan longPlan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 8, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(
            ContentBlackHoleCinematics.DESTABILISE - 40.0F / 300.0F,
            shortPlan.workEndProgress(),
            1.0E-6
        );
        assertEquals(
            ContentBlackHoleCinematics.DESTABILISE - 0.10F,
            longPlan.workEndProgress(),
            1.0E-6
        );
        assertTrue(shortPlan.workEndProgress() < ContentBlackHoleCinematics.DESTABILISE);
        assertTrue(longPlan.workEndProgress() < ContentBlackHoleCinematics.DESTABILISE);
    }

    @Test
    void requiredSectionsPerTickCatchesUpBeforeTheDeadline() {
        assertEquals(0, ContentBlackHoleDestructionPlan.requiredSectionsPerTick(0, 10));
        assertEquals(1, ContentBlackHoleDestructionPlan.requiredSectionsPerTick(1, 10));
        assertEquals(5, ContentBlackHoleDestructionPlan.requiredSectionsPerTick(50, 10));
        assertEquals(50, ContentBlackHoleDestructionPlan.requiredSectionsPerTick(50, 1));
        assertEquals(10, ContentBlackHoleDestructionPlan.requiredSectionsPerTick(10, 0));
    }

    @Test
    void largerHorizonsRequestLargerCraters() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan small = ContentBlackHoleDestructionPlan.plan(
            settings, 6.0F, 2_400, 64, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        ContentBlackHoleDestructionPlan.Plan large = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 2_400, 64, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(small.requestedRadius() * 2.0, large.requestedRadius(), 1.0E-6);
        assertEquals(small.radius() * 2.0, large.radius(), 1.0E-6);
        assertTrue(small.sectionsPerTick() < small.sectionBudget());
        assertTrue(large.sectionsPerTick() < large.sectionBudget());
    }

    @Test
    void oversizedCratersAreShrunkToFitTheWindow() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 64.0F, 200, 8, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertTrue(plan.budgetClamped());
        assertTrue(plan.radius() < plan.requestedRadius());
        assertTrue(plan.depth() < plan.requestedDepth());
        assertTrue(plan.estimatedSections() > 0);
        assertTrue(plan.sectionsPerTick() >= 1);
    }

    @Test
    void ticketCapLimitsRadiusWithoutCollapsingDepth() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 64.0F, 2_400, 64, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertTrue(plan.budgetClamped());
        assertTrue(plan.radius() < plan.requestedRadius());
        assertEquals(plan.requestedDepth(), plan.depth(), 1.0E-6);
        assertTrue(plan.depth() > plan.radius() * 0.25);
    }

    @Test
    void frontAndTargetSectionsFollowElapsedVolume() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        float middle = (plan.startProgress() + plan.workEndProgress()) * 0.5F;

        assertEquals(0.0, plan.fractionAt(plan.startProgress()), 1.0E-6);
        assertEquals(1.0, plan.fractionAt(plan.workEndProgress()), 1.0E-6);
        assertEquals(Math.cbrt(0.5), plan.frontScaleAt(middle), 1.0E-6);
        assertTrue(Math.abs(plan.targetSectionsAt(middle, 1_000) - 500) <= 1);
    }

    @Test
    void depthIsClampedToTheGroundButRadiusStillScales() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, 20.0, 20.0, MIN_BUILD_HEIGHT
        );

        assertEquals(20.0 - MIN_BUILD_HEIGHT - 1.0, plan.requestedDepth(), 1.0E-6);
        assertTrue(plan.depth() <= plan.requestedDepth());
        assertTrue(plan.radius() > 0.0);
    }

    @Test
    void restoredPlansKeepTheSectionTargets() {
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.restored(
            100.0, 50.0, 0.1F, 0.8F, 8, 123, 456
        );

        assertEquals(123, plan.solidSections());
        assertEquals(456, plan.estimatedSections());
        assertEquals(1.0, plan.fractionAt(0.8F), 1.0E-6);
        assertEquals(123, plan.targetSectionsAt(0.8F, plan.solidSections()));
    }

    @Test
    void sectionsAlwaysFitTheAvailableSectionBudget() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        for (float scale : new float[]{2.0F, 6.0F, 12.0F, 32.0F, 64.0F}) {
            ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
                settings, scale, 600, 8, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
            );
            assertTrue(plan.sectionsPerTick() <= plan.sectionBudget() || plan.budgetClamped(),
                "scale " + scale);
        }
    }
}
