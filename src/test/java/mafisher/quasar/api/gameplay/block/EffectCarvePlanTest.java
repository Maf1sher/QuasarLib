package mafisher.quasar.api.gameplay.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class EffectCarvePlanTest {
    private static final int DURATION = 1_200;
    private static final double FLOOR = -64.0;
    private static final double SURFACE = 64.0;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
    }

    @AfterEach
    void tearDown() {
        TestConfig.unload();
    }

    @Test
    void sphereFitsTheAuthoredVolumeWhenBudgetsAllowIt() {
        EffectCarvePlan plan = plan(sphere(10.0), 64, 64);

        assertEquals(1.0, plan.scale(), 1.0E-6);
        assertTrue(plan.intersectsTerrain());
        assertTrue(plan.estimatedSections() <= EffectBulkBlockEditor.maxSectionsPerEdit());
        assertTrue(plan.estimatedTickets() <= EffectBulkBlockEditor.maxTicketChunks());
        assertTrue(plan.sectionsPerTick() <= plan.sectionBudget());
        assertFalse(plan.budgetClamped());
    }

    @Test
    void cylinderUsesTheSamePlanContract() {
        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 50.0, 0.0), 8.0, 40.0, 60.0
        );
        EffectCarvePlan plan = plan(cylinder, 64, 64);

        assertEquals(EffectBulkBlockEditor.Ordering.CENTER_OUT, plan.ordering());
        assertEquals(1.0, plan.scale(), 1.0E-6);
        assertTrue(plan.intersectsTerrain());
        assertTrue(plan.estimatedSections() <= EffectBulkBlockEditor.maxSectionsPerEdit());
        assertTrue(plan.estimatedTickets() <= EffectBulkBlockEditor.maxTicketChunks());
    }

    @Test
    void oversizedVolumesShrinkUniformlyToFitAllCaps() {
        EffectCarvePlan plan = plan(sphere(200.0), 200, 1);

        assertTrue(plan.scale() < 1.0);
        assertTrue(plan.budgetClamped());
        assertTrue(plan.estimatedSections() <= EffectBulkBlockEditor.maxSectionsPerEdit());
        assertTrue(plan.estimatedTickets() <= EffectBulkBlockEditor.maxTicketChunks());
        assertTrue(plan.sectionsPerTick() <= plan.sectionBudget());
        assertEquals(
            ((EffectBlockVolume.Sphere)plan.requestedVolume()).radius() * plan.scale(),
            ((EffectBlockVolume.Sphere)plan.volume()).radius(),
            1.0E-6
        );
    }

    @Test
    void shortWindowsKeepARevealReserve() {
        EffectCarvePlan plan = plan(sphere(12.0), 300, 64, 0.085F, 0.860F);

        assertEquals(0.860F - 40.0F / 300.0F, plan.workEnd(), 1.0E-6);
        assertTrue(plan.workEnd() > plan.growthStart());
        assertEquals(
            0.860F - 0.10F,
            plan(sphere(12.0), DURATION, 64, 0.085F, 0.860F).workEnd(),
            1.0E-6
        );
    }

    @Test
    void scaleAtAdvancesByRemovedVolume() {
        EffectCarvePlan plan = plan(sphere(10.0), 64, 64);
        float middle = (plan.growthStart() + plan.workEnd()) * 0.5F;

        double startScale = plan.scaleAt(plan.growthStart());
        double startVolume = startScale <= 0.0
            ? 0.0
            : CarveVolumeMath.pacingVolume(
                CarveVolumeMath.scale(plan.volume(), startScale),
                SURFACE,
                FLOOR
            );
        double endVolume = CarveVolumeMath.pacingVolume(
            plan.volume(), SURFACE, FLOOR
        );
        double middleVolume = CarveVolumeMath.pacingVolume(
            CarveVolumeMath.scale(plan.volume(), plan.scaleAt(middle)),
            SURFACE, FLOOR
        );

        assertEquals(
            startVolume + (endVolume - startVolume) * 0.5,
            middleVolume,
            Math.max(1.0, (endVolume - startVolume) * 1.0E-3)
        );
        assertEquals(1.0, plan.scaleAt(plan.workEnd()), 1.0E-6);
    }

    @Test
    void scaleAtStartsAtTheEffectiveCoreSize() {
        EffectCarveSpec spec = spec(
            sphere(10.0), sphere(2.0), 64, 64, 0.085F, 0.860F
        );
        EffectCarvePlan plan = EffectCarvePlan.plan(context(64, 64), spec);

        assertEquals(0.2, plan.scaleAt(plan.growthStart()), 1.0E-6);
        assertTrue(plan.scaleAt(plan.workEnd()) > plan.scaleAt(plan.growthStart()));
    }

    @Test
    void noTerrainStillProducesMonotonicFront() {
        EffectBlockVolume aboveGround = EffectBlockVolume.sphere(
            new EffectPoint(0.0, 300.0, 0.0), 10.0
        );
        EffectCarvePlan plan = EffectCarvePlan.plan(
            context(300, 64),
            spec(aboveGround, null, SURFACE, FLOOR, 0.085F, 0.860F)
        );

        assertFalse(plan.intersectsTerrain());
        assertEquals(0.0, plan.scaleAt(plan.growthStart()), 1.0E-6);
        double previous = 0.0;
        for (int step = 0; step <= 20; step++) {
            float progress = plan.growthStart()
                + (plan.workEnd() - plan.growthStart()) * step / 20.0F;
            double scale = plan.scaleAt(progress);
            assertTrue(scale >= previous - 1.0E-6);
            previous = scale;
        }
    }

    @Test
    void planRoundTripsThroughItsPersistedState() {
        EffectCarvePlan plan = plan(sphere(80.0), 200, 2);
        CompoundTag tag = new CompoundTag();
        CarvePlanNbt.write(plan, tag);

        EffectCarvePlan restored = CarvePlanNbt.restore(
            context(200, 2), planSpec(sphere(80.0), 200, 2), tag
        );

        assertEquals(plan.scale(), restored.scale(), 1.0E-6);
        assertEquals(plan.windowStart(), restored.windowStart(), 1.0E-6);
        assertEquals(plan.growthStart(), restored.growthStart(), 1.0E-6);
        assertEquals(plan.workEnd(), restored.workEnd(), 1.0E-6);
        assertEquals(plan.estimatedSections(), restored.estimatedSections());
        assertEquals(plan.estimatedTickets(), restored.estimatedTickets());
        assertEquals(plan.volume().bounds(), restored.volume().bounds());

        tag.remove("ordering");
        EffectCarvePlan legacy = CarvePlanNbt.restore(
            context(200, 2), planSpec(sphere(80.0), 200, 2), tag
        );
        assertEquals(EffectBulkBlockEditor.Ordering.CENTER_OUT, legacy.ordering());
    }

    @Test
    void pacingVolumeSupportsSpheresAndCylinders() {
        EffectBlockVolume sphere = EffectBlockVolume.sphere(
            new EffectPoint(0.0, 0.0, 0.0), 10.0
        );
        assertEquals(
            (2.0 / 3.0) * Math.PI * 1_000.0,
            CarveVolumeMath.pacingVolume(sphere, 0.0, -100.0),
            1.0E-6
        );

        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 5.0, 0.0), 2.0, 0.0, 10.0
        );
        assertEquals(
            20.0 * Math.PI,
            CarveVolumeMath.pacingVolume(cylinder, 5.0, -10.0),
            1.0E-6
        );
    }

    @Test
    void topDownCylinderKeepsItsAuthoredDepthWhenTheRadiusShrinks() {
        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 32.0, 0.0), 200.0, 0.0, SURFACE
        );
        EffectCarvePlan plan = topDownPlan(cylinder, 200, 1);

        assertTrue(plan.scale() < 1.0);
        assertTrue(plan.budgetClamped());
        assertEquals(EffectBulkBlockEditor.Ordering.TOP_DOWN, plan.ordering());
        EffectBlockVolume.Cylinder effective = (EffectBlockVolume.Cylinder)plan.volume();
        assertEquals(0.0, effective.minY(), 1.0E-6);
        assertEquals(SURFACE, effective.maxY(), 1.0E-6);
        assertEquals(200.0 * plan.scale(), effective.radius(), 1.0E-6);
    }

    @Test
    void topDownFrontDescendsFromSurfaceToProtectedFloor() {
        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 32.0, 0.0), 8.0, 0.0, SURFACE
        );
        EffectCarvePlan plan = topDownPlan(cylinder, DURATION, 64);

        assertEquals(SURFACE, plan.frontTopY(), 1.0E-6);
        assertEquals(0.0, plan.frontBottomY(), 1.0E-6);
        assertEquals(SURFACE, plan.frontYAt(plan.growthStart()), 1.0E-6);
        assertEquals(0.0, plan.frontYAt(plan.workEnd()), 1.0E-6);

        double middle = plan.frontYAt((plan.growthStart() + plan.workEnd()) * 0.5F);
        assertEquals(SURFACE * 0.5, middle, 0.5);
    }

    @Test
    void frontAccessorsRejectTheWrongOrdering() {
        EffectCarvePlan radial = plan(sphere(10.0), 64, 64);
        EffectCarvePlan topDown = topDownPlan(
            EffectBlockVolume.cylinder(new EffectPoint(0.0, 32.0, 0.0), 8.0, 0.0, SURFACE),
            64,
            64
        );

        assertThrows(IllegalStateException.class, () -> radial.frontYAt(0.5F));
        assertThrows(IllegalStateException.class, () -> topDown.scaleAt(0.5F));
    }

    @Test
    void topDownPlanRoundTripsThroughItsPersistedState() {
        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 32.0, 0.0), 8.0, 0.0, SURFACE
        );
        EffectCarveSpec spec = spec(
            cylinder, null, SURFACE, FLOOR, 0.085F, 0.860F,
            EffectBulkBlockEditor.Ordering.TOP_DOWN
        );
        EffectCarvePlan plan = EffectCarvePlan.plan(context(DURATION, 64), spec);
        CompoundTag tag = new CompoundTag();
        CarvePlanNbt.write(plan, tag);

        EffectCarvePlan restored = CarvePlanNbt.restore(
            context(DURATION, 64), spec, tag
        );

        assertEquals(EffectBulkBlockEditor.Ordering.TOP_DOWN, restored.ordering());
        assertEquals(plan.frontTopY(), restored.frontTopY(), 1.0E-6);
        assertEquals(plan.frontBottomY(), restored.frontBottomY(), 1.0E-6);
        assertEquals(plan.frontYAt(0.5F), restored.frontYAt(0.5F), 1.0E-6);
    }

    @Test
    void topDownSpecRequiresACylinderWithoutACore() {
        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 32.0, 0.0), 4.0, 0.0, SURFACE
        );
        EffectBlockVolume core = EffectBlockVolume.sphere(
            new EffectPoint(0.0, 32.0, 0.0), 1.0
        );

        assertThrows(IllegalArgumentException.class, () -> new EffectCarveSpec(
            sphere(4.0),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.1F,
            0.8F,
            null,
            0.0F,
            SURFACE,
            EffectBulkBlockEditor.Ordering.TOP_DOWN
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectCarveSpec(
            cylinder,
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.1F,
            0.8F,
            core,
            0.0F,
            SURFACE,
            EffectBulkBlockEditor.Ordering.TOP_DOWN
        ));
    }

    @Test
    void invalidSpecsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EffectCarveSpec(
            sphere(4.0),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.8F,
            0.2F,
            null,
            0.0F,
            null
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectCarveSpec(
            sphere(4.0),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.1F,
            0.8F,
            sphere(8.0),
            0.0F,
            null
        ));
    }

    private EffectCarvePlan plan(
        EffectBlockVolume volume,
        int duration,
        int sectionBudget
    ) {
        return plan(volume, duration, sectionBudget, 0.085F, 0.860F);
    }

    private EffectCarvePlan topDownPlan(
        EffectBlockVolume volume,
        int duration,
        int sectionBudget
    ) {
        return EffectCarvePlan.plan(
            context(duration, sectionBudget),
            spec(
                volume, null, SURFACE, FLOOR, 0.085F, 0.860F,
                EffectBulkBlockEditor.Ordering.TOP_DOWN
            )
        );
    }

    private EffectCarvePlan plan(
        EffectBlockVolume volume,
        int duration,
        int sectionBudget,
        float windowStart,
        float windowEnd
    ) {
        return EffectCarvePlan.plan(
            context(duration, sectionBudget),
            spec(volume, null, SURFACE, FLOOR, windowStart, windowEnd)
        );
    }

    private EffectCarveSpec planSpec(
        EffectBlockVolume volume,
        int duration,
        int sectionBudget
    ) {
        return spec(volume, null, SURFACE, FLOOR, 0.085F, 0.860F);
    }

    private static EffectCarveSpec spec(
        EffectBlockVolume volume,
        EffectBlockVolume core,
        double surfaceY,
        double floorY,
        float windowStart,
        float windowEnd
    ) {
        return spec(
            volume, core, surfaceY, floorY, windowStart, windowEnd,
            EffectBulkBlockEditor.Ordering.CENTER_OUT
        );
    }

    private static EffectCarveSpec spec(
        EffectBlockVolume volume,
        EffectBlockVolume core,
        double surfaceY,
        double floorY,
        float windowStart,
        float windowEnd,
        EffectBulkBlockEditor.Ordering ordering
    ) {
        return new EffectCarveSpec(
            volume,
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            windowStart,
            windowEnd,
            core,
            0.07F,
            surfaceY,
            ordering
        );
    }

    private static GameplayEffectContext context(int duration, int sectionBudget) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinBuildHeight()).thenReturn((int)FLOOR);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getMinSection()).thenReturn(-4);
        when(level.getMaxSection()).thenReturn(20);

        GameplayEffectSpec spec = mock(GameplayEffectSpec.class);
        when(spec.phases()).thenReturn(EffectPhasePlan.of(
            new EffectPhase("open", duration)
        ));
        when(spec.budget()).thenReturn(new EffectTickBudget(
            0, 0, 0, 4_096, 16_384, 0, 0, sectionBudget
        ));

        GameplayEffectContext context = mock(GameplayEffectContext.class);
        when(context.level()).thenReturn(level);
        when(context.spec()).thenReturn(spec);
        return context;
    }

    private static EffectBlockVolume sphere(double radius) {
        return EffectBlockVolume.sphere(new EffectPoint(0.0, SURFACE, 0.0), radius);
    }
}
