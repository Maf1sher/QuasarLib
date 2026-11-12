package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(plan.requestedRadius(), plan.radius(), 1.0E-6);
        assertEquals(12.0, plan.coreRadius(), 1.0E-6);
        assertTrue(plan.startProgress() >= ContentBlackHoleCinematics.HORIZON_END);
        assertTrue(plan.workEndProgress() < ContentBlackHoleCinematics.DESTABILISE);
        assertEquals(
            ContentBlackHoleCinematics.DESTABILISE - 0.10F,
            plan.workEndProgress(),
            1.0E-6
        );
    }

    @Test
    void lateCoreFinishShiftsTheGrowthWindow() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.30F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(0.30F, plan.startProgress(), 1.0E-6);
        assertTrue(plan.workEndProgress() > plan.startProgress());
        assertEquals(0.0, plan.fractionAt(plan.startProgress()), 1.0E-6);
        assertEquals(plan.coreRadius(), plan.radiusAt(plan.startProgress()), 1.0E-6);
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
        assertTrue(small.sectionsPerTick() <= small.sectionBudget() || small.budgetClamped());
        assertTrue(large.sectionsPerTick() <= large.sectionBudget() || large.budgetClamped());
    }

    @Test
    void oversizedCratersAreShrunkUniformly() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 64.0F, 200, 8, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertTrue(plan.budgetClamped());
        assertTrue(plan.radius() < plan.requestedRadius());
        assertTrue(plan.radius() > plan.coreRadius());
        assertTrue(plan.estimatedSections() > 0);
        assertTrue(plan.sectionsPerTick() >= 1);
    }

    @Test
    void ticketAndSectionCapsLimitTheRadiusWithoutChangingTheShape() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 64.0F, 2_400, 64, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertTrue(plan.budgetClamped());
        assertTrue(plan.radius() < plan.requestedRadius());
        assertTrue(plan.estimatedSections() <= 16_384);
    }

    @Test
    void terrainVolumeAdvancesLinearlyWithTheFront() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        float middle = (plan.startProgress() + plan.workEndProgress()) * 0.5F;

        double startVolume = ContentBlackHoleDestructionPlan.terrainVolume(
            plan.coreRadius(), GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        double endVolume = ContentBlackHoleDestructionPlan.terrainVolume(
            plan.radius(), GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );
        double middleVolume = ContentBlackHoleDestructionPlan.terrainVolume(
            plan.radiusAt(middle), GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(
            startVolume + (endVolume - startVolume) * 0.5,
            middleVolume,
            Math.max(1.0, (endVolume - startVolume) * 1.0E-4)
        );
    }

    @Test
    void radiusStartsAtTheCoreAndEndsAtTheFinalSphere() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(plan.coreRadius(), plan.radiusAt(plan.startProgress()), 1.0E-6);
        assertEquals(plan.radius(), plan.radiusAt(plan.workEndProgress()), 1.0E-6);
        double previous = 0.0;
        for (int step = 0; step <= 100; step++) {
            float progress = plan.startProgress()
                + (plan.workEndProgress() - plan.startProgress()) * step / 100.0F;
            double radius = plan.radiusAt(progress);
            assertTrue(radius >= previous - 1.0E-6, "progress " + progress);
            previous = radius;
        }
    }

    @Test
    void elevatedCoresStillUseOneRoundSphere() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.plan(
            settings, 12.0F, 1_200, 16, 0.1F, 100.0, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(settings.craterRadius(12.0F), plan.radius(), 1.0E-6);
        assertTrue(ContentBlackHoleDestructionPlan.breaksSurface(
            100.0, plan.radius(), GROUND_Y
        ));
        assertTrue(plan.radiusAt(plan.workEndProgress()) > plan.coreRadius());
    }

    @Test
    void fullyUndergroundSpheresDoNotBreakTheSurface() {
        assertTrue(ContentBlackHoleDestructionPlan.intersectsTerrain(
            -100.0, 72.0, GROUND_Y, MIN_BUILD_HEIGHT
        ));
        assertFalse(ContentBlackHoleDestructionPlan.breaksSurface(
            -100.0, 72.0, GROUND_Y
        ));
        assertTrue(ContentBlackHoleDestructionPlan.breaksSurface(
            0.0, 72.0, GROUND_Y
        ));
        assertFalse(ContentBlackHoleDestructionPlan.intersectsTerrain(
            200.0, 72.0, GROUND_Y, MIN_BUILD_HEIGHT
        ));
        assertFalse(ContentBlackHoleDestructionPlan.breaksSurface(
            200.0, 72.0, GROUND_Y
        ));
    }

    @Test
    void terrainVolumeUsesTheSphericalCapBelowTheSurfaceAndFloor() {
        double full = ContentBlackHoleDestructionPlan.volumeBelowPlane(
            10.0, 100.0, 200.0
        );
        assertEquals((4.0 / 3.0) * Math.PI * 1_000.0, full, 1.0E-6);
        assertEquals(0.0, ContentBlackHoleDestructionPlan.volumeBelowPlane(
            10.0, 100.0, 50.0
        ), 1.0E-6);
        double half = ContentBlackHoleDestructionPlan.volumeBelowPlane(
            10.0, 100.0, 100.0
        );
        assertEquals((2.0 / 3.0) * Math.PI * 1_000.0, half, 1.0E-6);
    }

    @Test
    void restoredPlansKeepTheRadiusAndWindow() {
        ContentBlackHoleDestructionPlan.Plan plan = ContentBlackHoleDestructionPlan.restored(
            100.0, 10.0, 0.1F, 0.8F, 8, 123, 456,
            GROUND_Y, GROUND_Y, MIN_BUILD_HEIGHT
        );

        assertEquals(100.0, plan.radius(), 1.0E-6);
        assertEquals(123, plan.solidSections());
        assertEquals(456, plan.estimatedSections());
        assertEquals(1.0, plan.fractionAt(0.8F), 1.0E-6);
        assertEquals(100.0, plan.radiusAt(0.8F), 1.0E-6);
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
