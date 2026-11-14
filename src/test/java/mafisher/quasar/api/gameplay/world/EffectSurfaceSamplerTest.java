package mafisher.quasar.api.gameplay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectAccess;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class EffectSurfaceSamplerTest {
    private static final int DURATION = 200;
    private static final Vec3 CENTER = new Vec3(8.0, 8.0, 8.0);
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "surface_sampler_test");
    private static final Heightmap.Types TYPE_MAP = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;

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
    void rejectsInvalidArguments() {
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleArea(0, 0, -1, TYPE_MAP, 1));
            assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleArea(0, 0, EffectSurfaceSampler.MAX_AREA_RADIUS + 1, TYPE_MAP, 1));
            assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleArea(0, 0, 1, TYPE_MAP, 0));
            assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleArea(0, 0, 1, TYPE_MAP,
                    EffectSurfaceSampler.MAX_AREA_COLUMNS + 1));
            assertThrows(IllegalArgumentException.class,
                () -> sampler.invalidate(2, 0, 1, 0));
            assertThrows(NullPointerException.class,
                () -> new EffectSurfaceSampler(null));
            assertThrows(NullPointerException.class,
                () -> sampler.sampleColumn(0, 0, null));
        } finally {
            close(context);
        }
    }

    @Test
    void samplesLoadedColumnsAndCachesThem() {
        this.world.setBlock(new BlockPos(1, 14, 2), Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            EffectSurfaceSampler.Column first = sampler.sampleColumn(1, 2, TYPE_MAP);

            assertEquals(EffectSurfaceSampler.Status.LOADED, first.status());
            assertEquals(15, first.height());
            assertEquals(14, first.surfaceY());
            assertEquals(Blocks.STONE.defaultBlockState(), first.surface());
            assertTrue(first.fluid().isEmpty());

            EffectSurfaceSampler.Column second = sampler.sampleColumn(1, 2, TYPE_MAP);
            assertSame(first, second);
            assertEquals(1, sampler.cachedColumns());
            verify(this.world.level(), times(1)).getHeight(eq(TYPE_MAP), eq(1), eq(2));
            verify(this.world.level(), times(1))
                .getBlockState(new BlockPos(1, 14, 2));
            verify(this.world.level(), never()).getChunkAt(any());
        } finally {
            close(context);
        }
    }

    @Test
    void unloadedColumnsConsumeNoSamplesAndAreNotCached() {
        when(this.world.level().hasChunkAt(5, 5)).thenReturn(false);
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            EffectSurfaceSampler.Column column = sampler.sampleColumn(5, 5, TYPE_MAP);

            assertEquals(EffectSurfaceSampler.Status.UNLOADED, column.status());
            assertEquals(this.world.level().getMinBuildHeight(), column.height());
            assertNull(column.surface());
            assertNull(column.fluid());
            assertEquals(0, sampler.cachedColumns());
            verify(this.world.level(), never()).getHeight(any(), eq(5), eq(5));
            verify(this.world.level(), never()).getChunkAt(any());
        } finally {
            close(context);
        }
    }

    @Test
    void emptyColumnsAreLoadedWithoutASurface() {
        int minY = this.world.level().getMinBuildHeight();
        when(this.world.level().getHeight(TYPE_MAP, 4, 4)).thenReturn(minY);
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            EffectSurfaceSampler.Column column = sampler.sampleColumn(4, 4, TYPE_MAP);

            assertEquals(EffectSurfaceSampler.Status.LOADED, column.status());
            assertEquals(minY, column.height());
            assertNull(column.surface());
            assertNull(column.fluid());
        } finally {
            close(context);
        }
    }

    @Test
    void budgetExhaustionIsSoftAndRecoversOnTheNextTick() {
        GameplayEffectContext context = context(0, 2);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            assertEquals(EffectSurfaceSampler.Status.LOADED,
                sampler.sampleColumn(1, 1, TYPE_MAP).status());

            EffectSurfaceSampler.Column exhausted = sampler.sampleColumn(2, 2, TYPE_MAP);
            assertEquals(EffectSurfaceSampler.Status.BUDGET_EXHAUSTED, exhausted.status());
            assertEquals(1, sampler.cachedColumns());

            context.beginTick(1);
            assertEquals(EffectSurfaceSampler.Status.LOADED,
                sampler.sampleColumn(2, 2, TYPE_MAP).status());
        } finally {
            close(context);
        }
    }

    @Test
    void appliedEditsInvalidateCachedSurfaces() {
        this.world.setBlock(new BlockPos(1, 14, 2), Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            assertEquals(15, sampler.sampleColumn(1, 2, TYPE_MAP).height());
            long before = context.blockEditVersion();

            context.queueBlockChange(
                new BlockPos(1, 14, 2), Blocks.AIR.defaultBlockState()
            );
            GameplayEffectAccess.applyBlockChanges(context, 8);

            assertTrue(context.blockEditVersion() > before);
            this.world.setBlock(new BlockPos(1, 12, 2), Blocks.DIRT.defaultBlockState());
            when(this.world.level().getHeight(TYPE_MAP, 1, 2)).thenReturn(13);

            EffectSurfaceSampler.Column updated = sampler.sampleColumn(1, 2, TYPE_MAP);
            assertEquals(13, updated.height());
            assertEquals(Blocks.DIRT.defaultBlockState(), updated.surface());
            verify(this.world.level(), times(2)).getHeight(eq(TYPE_MAP), eq(1), eq(2));
        } finally {
            close(context);
        }
    }

    @Test
    void explicitInvalidationDropsOnlyMatchingColumns() {
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            sampler.sampleColumn(1, 2, TYPE_MAP);
            sampler.sampleColumn(2, 2, TYPE_MAP);
            sampler.sampleColumn(3, 2, TYPE_MAP);

            sampler.invalidate(2, 2, 2, 2);
            assertEquals(2, sampler.cachedColumns());

            sampler.sampleColumn(2, 2, TYPE_MAP);
            verify(this.world.level(), times(2)).getHeight(eq(TYPE_MAP), eq(2), eq(2));

            sampler.invalidateAll();
            assertEquals(0, sampler.cachedColumns());
        } finally {
            close(context);
        }
    }

    @Test
    void areaSamplingIsCenterOutDeterministicAndBounded() {
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            EffectSurfaceSampler.Area area = sampler.sampleArea(10, 10, 1, TYPE_MAP, 9);

            assertEquals(9, area.columns().size());
            assertFalse(area.truncated());
            assertEquals(10, area.columns().get(0).x());
            assertEquals(10, area.columns().get(0).z());
            Set<String> positions = new HashSet<>();
            for (EffectSurfaceSampler.Column column : area.columns()) {
                positions.add(column.x() + ":" + column.z());
            }
            assertEquals(9, positions.size());

            EffectSurfaceSampler.Area limited = sampler.sampleArea(10, 10, 2, TYPE_MAP, 4);
            assertEquals(4, limited.columns().size());
            assertTrue(limited.truncated());
        } finally {
            close(context);
        }
    }

    @Test
    void areaSamplingKeepsUnloadedStatusColumns() {
        when(this.world.level().hasChunkAt(5, 5)).thenReturn(false);
        GameplayEffectContext context = context(0, 64);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            EffectSurfaceSampler.Area area = sampler.sampleArea(5, 5, 0, TYPE_MAP, 1);

            assertEquals(1, area.columns().size());
            assertFalse(area.truncated());
            assertEquals(EffectSurfaceSampler.Status.UNLOADED, area.columns().get(0).status());
        } finally {
            close(context);
        }
    }

    @Test
    void cacheStaysBounded() {
        GameplayEffectContext context = context(0, 100_000);
        EffectSurfaceSampler sampler = new EffectSurfaceSampler(context);
        try {
            for (int x = 0; x <= EffectSurfaceSampler.MAX_CACHED_COLUMNS; x++) {
                sampler.sampleColumn(x, 0, TYPE_MAP);
            }

            assertEquals(EffectSurfaceSampler.MAX_CACHED_COLUMNS, sampler.cachedColumns());
        } finally {
            close(context);
        }
    }

    @Test
    void columnRejectsMismatchedSurfacePayloads() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceSampler.Column(
                0, 0, EffectSurfaceSampler.Status.LOADED, 1,
                Blocks.STONE.defaultBlockState(), null
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectSurfaceSampler.Column(
                0, 0, EffectSurfaceSampler.Status.UNLOADED, 1,
                Blocks.STONE.defaultBlockState(), null
            ));
    }

    private GameplayEffectContext context(int ageTicks, int maxWorldSamples) {
        EffectPhasePlan phases = EffectPhasePlan.of(new EffectPhase("sample", DURATION));
        GameplayEffectSpec spec = new GameplayEffectSpec(
            TYPE,
            null,
            CENTER,
            phases,
            7L,
            new EffectTickBudget(0, 0, 0, 64, maxWorldSamples, 0, 0, 0),
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

    private static void close(GameplayEffectContext context) {
        context.close();
        context.scope().close();
    }
}
