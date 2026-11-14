package mafisher.quasar.api.gameplay.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.debris.EffectDebrisSpawn;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectAccess;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.world.EffectBlockSample;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.api.gameplay.world.EffectRaycastStatus;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@GlobalStateTest
class EffectSurfaceTearTest {
    private static final Vec3 CENTER = new Vec3(8.0, 8.0, 8.0);
    private static final double CRATER_RADIUS = 7.0;
    private static final int DURATION = 200;
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "surface_tear_test");

    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.installRuntime();
    }

    @AfterEach
    void tearDown() {
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void settingsRejectInvalidCaps() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Settings(-1.0, 8, 2.0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Settings(2.0, 513, 2.0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Settings(2.0, 8, -1.0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Settings(2.0, 8, 2.0, 4));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Geometry(sphere(), CENTER, 2.0, 1_025.0));
    }

    @Test
    void debrisSettingsRejectInvalidCaps() {
        EffectAudience audience = EffectAudience.nearby(64.0);
        EffectDebrisAttractor attractor = attractor();
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Debris(
                audience, attractor, null, 0, 1.0, 10, 120, 1.0F, 1, 1.0F
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Debris(
                audience, attractor, null, 8, 0.0, 10, 120, 1.0F, 1, 1.0F
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Debris(
                audience, attractor, null, 8, 1.0, 120, 10, 1.0F, 1, 1.0F
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Debris(
                audience, attractor, null, 8, 1.0, 10, 120, 9.0F, 1, 1.0F
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceTear.Debris(
                audience, attractor, null, 8, 1.0, 10, 120, 1.0F, 17, 1.0F
            ));
    }

    @Test
    void ringRadiusTracksTheFrontAndStaysInsideTheCrater() {
        double craterRadius = 144.0;
        double band = 6.0;
        assertEquals(14.0, EffectSurfaceTear.ringRadius(8.0, craterRadius, band), 1.0E-6);
        assertEquals(craterRadius,
            EffectSurfaceTear.ringRadius(craterRadius, craterRadius, band), 1.0E-6);

        double previous = 0.0;
        for (double front = 0.0; front <= craterRadius; front += 4.0) {
            double radius = EffectSurfaceTear.ringRadius(front, craterRadius, band);
            assertTrue(radius >= previous - 1.0E-6, "front " + front);
            assertTrue(radius <= craterRadius + 1.0E-6, "front " + front);
            previous = radius;
        }
    }

    @Test
    void directionsAreDeterministicUnitLengthAndCoverEveryOctant() {
        Vec3 first = EffectSurfaceTear.direction(7L, 10, 3, 16);
        assertEquals(first, EffectSurfaceTear.direction(7L, 10, 3, 16));
        assertEquals(1.0, first.length(), 1.0E-9);
        assertNotEquals(first, EffectSurfaceTear.direction(7L, 10, 4, 16));
        assertNotEquals(first, EffectSurfaceTear.direction(7L, 11, 3, 16));

        Set<Integer> octants = new HashSet<>();
        for (int tick = 0; tick < 32; tick++) {
            for (int index = 0; index < 16; index++) {
                Vec3 direction = EffectSurfaceTear.direction(7L, tick, index, 16);
                octants.add(octant(direction));
            }
        }
        assertEquals(8, octants.size());
    }

    @Test
    void oneTickUsesDistinctLatitudeStrata() {
        Set<Double> latitudes = new HashSet<>();
        for (int index = 0; index < 16; index++) {
            Vec3 direction = EffectSurfaceTear.direction(7L, 0, index, 16);
            assertTrue(Math.abs(direction.length() - 1.0) < 1.0E-9);
            latitudes.add(direction.y);
        }
        assertEquals(16, latitudes.size());
    }

    @Test
    void structuralRulesRejectFloorVolumeAirAndIndestructibleBlocks() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        EffectBlockVolume volume = sphere();

        assertTrue(BlockEditRules.canEdit(
            this.world.level(), volume, new BlockPos(8, 8, 8), stone, air
        ));
        assertFalse(BlockEditRules.canEdit(
            this.world.level(), volume, new BlockPos(8, -16, 8), stone, air
        ));
        assertFalse(BlockEditRules.canEdit(
            this.world.level(), volume, new BlockPos(15, 8, 8), stone, air
        ));
        assertFalse(BlockEditRules.canEdit(
            this.world.level(), volume, new BlockPos(8, 8, 8),
            Blocks.BEDROCK.defaultBlockState(), air
        ));
        assertFalse(BlockEditRules.canEdit(
            this.world.level(), volume, new BlockPos(8, 8, 8), air, air
        ));
    }

    @Test
    void raysStopWhenWorldSamplesRunOut() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 5, 4_096, 0);
        try {
            EffectSurfaceTear.TickResult result = tear(16, 1).tick(
                context, geometry(5.0), EffectWriteFeedback.NONE
            );

            assertEquals(EffectRaycastStatus.BUDGET_EXHAUSTED, result.stoppedBy());
            assertTrue(result.raysCast() > 0);
            assertTrue(result.raysCast() < 16);
        } finally {
            close(context);
        }
    }

    @Test
    void raysStopWhenAChunkIsUnloaded() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        when(this.world.level().hasChunkAt(any(BlockPos.class))).thenReturn(false);
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        try {
            EffectSurfaceTear.TickResult result = tear(16, 1).tick(
                context, geometry(5.0), EffectWriteFeedback.NONE
            );

            assertEquals(EffectRaycastStatus.UNLOADED, result.stoppedBy());
            assertEquals(0, result.queuedBlocks());
        } finally {
            close(context);
        }
    }

    @Test
    void clusterIsBoundedByItsRadius() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        try {
            EffectSurfaceTear.TickResult result = tear(8, 1).tick(
                context, geometry(5.0), EffectWriteFeedback.NONE
            );

            assertTrue(result.hits() > 0);
            assertTrue(result.queuedBlocks() <= 8 * 27);
            assertTrue(result.queuedBlocks() > result.hits());
        } finally {
            applyAndClose(context);
        }
    }

    @Test
    void appliedClusterStaysInsideTheVolumeAndAboveTheFloor() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        EffectBlockVolume volume = sphere();
        try {
            tear(8, 1).tick(context, geometry(5.0), EffectWriteFeedback.NONE);
            GameplayEffectAccess.applyBlockChanges(context, 4_096);

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos position = new BlockPos(x, y, z);
                        if (this.world.blockState(position).isAir()) {
                            assertTrue(
                                BlockEditRules.insideVolume(volume, position),
                                "edited outside the volume: " + position
                            );
                            assertTrue(y > this.world.level().getMinBuildHeight());
                        }
                    }
                }
            }
        } finally {
            close(context);
        }
    }

    @Test
    void rayCapIsNeverExceeded() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        try {
            EffectSurfaceTear.TickResult result = new EffectSurfaceTear(
                new EffectSurfaceTear.Settings(2.0, 4, 0.0, 0)
            ).tick(context, geometry(5.0), EffectWriteFeedback.NONE);

            assertTrue(result.raysCast() <= 4);
        } finally {
            applyAndClose(context);
        }
    }

    @Test
    void radiusZeroQueuesOnlyTheStruckBlock() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        try {
            EffectSurfaceTear.TickResult result = new EffectSurfaceTear(
                new EffectSurfaceTear.Settings(2.0, 8, 2.0, 0)
            ).tick(context, geometry(5.0), EffectWriteFeedback.NONE);

            assertTrue(result.hits() > 0);
            assertTrue(result.queuedBlocks() > 0);
            assertTrue(result.queuedBlocks() <= result.hits());
        } finally {
            applyAndClose(context);
        }
    }

    @Test
    void feedbackRunsOnlyAfterAnAppliedWrite() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 16_384, 4_096, 0);
        List<EffectBlockSample> written = new ArrayList<>();
        try {
            EffectSurfaceTear.TickResult result = tear(8, 1).tick(
                context, geometry(5.0), written::add
            );

            assertTrue(result.queuedBlocks() > 0);
            assertTrue(written.isEmpty());
            GameplayEffectAccess.applyBlockChanges(context, 4_096);

            assertFalse(written.isEmpty());
            for (EffectBlockSample sample : written) {
                assertFalse(sample.state().isAir());
                assertTrue(this.world.blockState(sample.position()).isAir());
            }
        } finally {
            close(context);
        }
    }

    @Test
    void fallLifetimeIsDistancePacedAndClamped() {
        assertEquals(10, EffectSurfaceTear.fallLifetimeTicks(1.0, 1.0, 10, 120));
        assertEquals(64, EffectSurfaceTear.fallLifetimeTicks(64.0, 1.0, 10, 120));
        assertEquals(120, EffectSurfaceTear.fallLifetimeTicks(1_000.0, 1.0, 10, 120));
        assertEquals(10, EffectSurfaceTear.fallLifetimeTicks(-5.0, 1.0, 10, 120));
    }

    @Test
    void debrisFeedbackCapsBurstsAndUsesTheRemovedState() {
        GameplayEffectContext context = mock(GameplayEffectContext.class);
        AtomicInteger age = new AtomicInteger(10);
        when(context.ageTicks()).thenAnswer(ignored -> age.get());
        GameplayEffectSpec spec = mock(GameplayEffectSpec.class);
        when(context.spec()).thenReturn(spec);
        when(spec.phases()).thenReturn(EffectPhasePlan.of(new EffectPhase("tear", DURATION)));
        when(context.spawnDebris(any(EffectDebrisSpec.class))).thenReturn(8);

        EffectSurfaceTear.Debris settings = new EffectSurfaceTear.Debris(
            EffectAudience.nearby(64.0),
            attractor(),
            new EffectDebrisSpawn.Point(),
            8,
            1.0,
            10,
            120,
            2.0F,
            2,
            0.5F
        );
        EffectWriteFeedback feedback = EffectSurfaceTear.debrisFeedback(context, settings);
        EffectBlockSample sample = new EffectBlockSample(
            new BlockPos(20, 8, 8), Blocks.STONE.defaultBlockState()
        );

        feedback.onWritten(sample);
        feedback.onWritten(sample);
        feedback.onWritten(sample);
        verify(context, times(2)).spawnDebris(any(EffectDebrisSpec.class));

        age.set(11);
        feedback.onWritten(sample);
        verify(context, times(3)).spawnDebris(any(EffectDebrisSpec.class));

        ArgumentCaptor<EffectDebrisSpec> captor =
            ArgumentCaptor.forClass(EffectDebrisSpec.class);
        verify(context, times(3)).spawnDebris(captor.capture());
        EffectDebrisSpec sent = captor.getValue();
        assertEquals(Blocks.STONE.defaultBlockState(), sent.state());
        assertEquals(new Vec3(20.5, 8.5, 8.5), sent.origin());
        assertEquals(8, sent.pieces());
        assertEquals(13, sent.lifetimeTicks());
        assertEquals(2.0F, sent.scale(), 1.0E-6F);
        assertEquals(settings.attractor(), sent.attractor());
    }

    @Test
    void debrisFeedbackStopsBeforeThePhaseRemainingWindow() {
        GameplayEffectContext context = mock(GameplayEffectContext.class);
        AtomicInteger age = new AtomicInteger(95);
        when(context.ageTicks()).thenAnswer(ignored -> age.get());
        GameplayEffectSpec spec = mock(GameplayEffectSpec.class);
        when(context.spec()).thenReturn(spec);
        when(spec.phases()).thenReturn(EffectPhasePlan.of(new EffectPhase("tear", DURATION)));
        when(context.spawnDebris(any(EffectDebrisSpec.class))).thenReturn(8);

        EffectWriteFeedback feedback = EffectSurfaceTear.debrisFeedback(
            context,
            new EffectSurfaceTear.Debris(
                EffectAudience.nearby(64.0),
                attractor(),
                null,
                8,
                1.0,
                10,
                120,
                1.0F,
                4,
                0.5F
            )
        );

        feedback.onWritten(new EffectBlockSample(
            new BlockPos(20, 8, 8), Blocks.STONE.defaultBlockState()
        ));
        verify(context, times(0)).spawnDebris(any(EffectDebrisSpec.class));
    }

    private EffectSurfaceTear tear(int rays, int clusterRadius) {
        return new EffectSurfaceTear(
            new EffectSurfaceTear.Settings(2.0, rays, 2.0, clusterRadius)
        );
    }

    private static EffectSurfaceTear.Geometry geometry(double frontRadius) {
        return new EffectSurfaceTear.Geometry(
            sphere(), CENTER, frontRadius, CRATER_RADIUS
        );
    }

    private static EffectBlockVolume sphere() {
        return EffectBlockVolume.sphere(
            new EffectPoint(CENTER.x, CENTER.y, CENTER.z), CRATER_RADIUS
        );
    }

    private static EffectDebrisAttractor attractor() {
        return new EffectDebrisAttractor(
            CENTER, new Vec3(0.0, 1.0, 0.0), 2.0, 1.0, 0.0
        );
    }

    private static int octant(Vec3 direction) {
        int octant = direction.x >= 0.0 ? 1 : 0;
        octant |= direction.y >= 0.0 ? 2 : 0;
        octant |= direction.z >= 0.0 ? 4 : 0;
        return octant;
    }

    private GameplayEffectContext context(
        int ageTicks,
        int maxWorldSamples,
        int maxBlockChanges,
        int maxDebrisPieces
    ) {
        EffectPhasePlan phases = EffectPhasePlan.of(new EffectPhase("tear", DURATION));
        GameplayEffectSpec spec = new GameplayEffectSpec(
            TYPE,
            null,
            CENTER,
            phases,
            7L,
            new EffectTickBudget(
                0, 0, 0, maxBlockChanges, maxWorldSamples, 0, maxDebrisPieces, 0
            ),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE,
            EffectPriority.NORMAL,
            1.0F
        );
        UUID sessionId = UUID.randomUUID();
        return GameplayEffectAccess.restore(
            this.world.level(),
            new EffectScope(this.world.level(), sessionId),
            spec,
            sessionId,
            EffectCapabilities.destructiveCapability(),
            new BlockJournal(),
            ageTicks,
            new CompoundTag()
        );
    }

    private static void applyAndClose(GameplayEffectContext context) {
        GameplayEffectAccess.applyBlockChanges(context, 4_096);
        close(context);
    }

    private static void close(GameplayEffectContext context) {
        context.close();
        context.scope().close();
    }
}
