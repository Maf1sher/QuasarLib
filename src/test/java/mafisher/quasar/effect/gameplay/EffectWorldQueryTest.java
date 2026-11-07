package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.OptionalInt;
import java.util.UUID;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectWorldQueryTest {
    private static final BlockPos STONE_POS = new BlockPos(0, 64, 0);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ServerLevel level;
    private GameplayEffectContext context;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(0L);
        when(this.level.isInWorldBounds(any(BlockPos.class))).thenReturn(true);
        when(this.level.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(this.level.hasChunkAt(anyInt(), anyInt())).thenReturn(true);
        when(this.level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return STONE_POS.equals(position) ? STONE : AIR;
        });
        this.context = context(1_000);
    }

    @AfterEach
    void tearDown() {
        this.context.close();
    }

    @Test
    void readsHeightmapValuesAndCountsOneSample() {
        when(this.level.getHeight(eq(Heightmap.Types.WORLD_SURFACE), eq(3), eq(4))).thenReturn(70);

        assertEquals(OptionalInt.of(70), this.context.world().height(Heightmap.Types.WORLD_SURFACE, 3, 4));
        assertEquals(1, this.context.counters().worldSamples());
    }

    @Test
    void returnsEmptyForUnloadedChunksWithoutReadingThem() {
        when(this.level.hasChunkAt(anyInt(), anyInt())).thenReturn(false);

        assertTrue(this.context.world().height(Heightmap.Types.WORLD_SURFACE, 3, 4).isEmpty());
        verify(this.level, never()).getHeight(any(), anyInt(), anyInt());
        assertEquals(0, this.context.counters().worldSamples());
    }

    @Test
    void returnsBlockStateOnlyForLoadedInBoundsPositions() {
        assertTrue(this.context.world().blockState(STONE_POS).isPresent());
        assertEquals(STONE, this.context.world().blockState(STONE_POS).orElseThrow());
        assertEquals(2, this.context.counters().worldSamples());

        when(this.level.isInWorldBounds(any(BlockPos.class))).thenReturn(false);
        assertTrue(this.context.world().blockState(STONE_POS).isEmpty());
    }

    @Test
    void reportsEmptyBlockStateWhenTheSampleBudgetIsSpent() {
        this.context.close();
        this.context = context(1);

        assertTrue(this.context.world().blockState(STONE_POS).isPresent());
        assertTrue(this.context.world().blockState(STONE_POS).isEmpty());
        assertEquals(1, this.context.counters().worldSamples());
    }

    @Test
    void raycastHitsTheNearestFilteredBlock() {
        EffectRaycastResult result = this.context.world().raycast(
            new Vec3(0.5, 100.5, 0.5),
            new Vec3(0.5, 60.5, 0.5)
        );

        assertTrue(result.isHit());
        assertEquals(EffectRaycastStatus.HIT, result.status());
        EffectBlockHit hit = result.hit().orElseThrow();
        assertEquals(STONE_POS, hit.position());
        assertEquals(STONE, hit.state());
        assertEquals(65.0, hit.location().y, 1.0E-6);
        assertEquals(net.minecraft.core.Direction.UP, hit.face());
        assertEquals(35.5, hit.distance(), 1.0E-6);
    }

    @Test
    void raycastReturnsMissWhenNothingMatches() {
        EffectRaycastResult result = this.context.world().raycast(
            new Vec3(0.5, 100.5, 0.5),
            new Vec3(0.5, 90.5, 0.5)
        );

        assertEquals(EffectRaycastStatus.MISS, result.status());
        assertFalse(result.hit().isPresent());
        assertEquals(11, this.context.counters().worldSamples());
    }

    @Test
    void raycastStopsAtUnloadedChunksWithoutReadingBlocks() {
        when(this.level.hasChunkAt(any(BlockPos.class))).thenReturn(false);

        EffectRaycastResult result = this.context.world().raycast(
            new Vec3(0.5, 100.5, 0.5),
            new Vec3(0.5, 60.5, 0.5)
        );

        assertEquals(EffectRaycastStatus.UNLOADED, result.status());
        verify(this.level, never()).getBlockState(any(BlockPos.class));
        assertEquals(1, this.context.counters().worldSamples());
    }

    @Test
    void raycastStopsWhenTheSampleBudgetIsExhausted() {
        this.context.close();
        this.context = context(2);

        EffectRaycastResult result = this.context.world().raycast(
            new Vec3(0.5, 100.5, 0.5),
            new Vec3(0.5, 60.5, 0.5)
        );

        assertEquals(EffectRaycastStatus.BUDGET_EXHAUSTED, result.status());
        assertEquals(2, this.context.counters().worldSamples());
    }

    @Test
    void rejectsRaysBeyondTheDistanceLimitWithoutSpendingSamples() {
        EffectRaycastResult result = this.context.world().raycast(
            new Vec3(0.5, 100.5, 0.5),
            new Vec3(0.5, -1000.5, 0.5)
        );

        assertEquals(EffectRaycastStatus.TOO_LONG, result.status());
        assertEquals(0, this.context.counters().worldSamples());
        verify(this.level, never()).getBlockState(any(BlockPos.class));
    }

    @Test
    void raycastRejectsNonFiniteEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> this.context.world().raycast(
            new Vec3(Double.NaN, 0.0, 0.0),
            Vec3.ZERO
        ));
        assertThrows(IllegalArgumentException.class, () -> this.context.world().raycast(
            Vec3.ZERO,
            new Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)
        ));
    }

    @Test
    void scansAClosedRangeWithinTheBudget() {
        when(this.level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return position.getX() == 0 && position.getZ() == 0 ? STONE : AIR;
        });

        EffectScanResult result = this.context.world().scan(
            BlockPos.ZERO,
            new BlockPos(1, 0, 1),
            state -> state.is(Blocks.STONE),
            10
        );

        assertEquals(4, result.examined());
        assertEquals(1, result.samples().size());
        assertEquals(BlockPos.ZERO, result.samples().getFirst().position());
        assertFalse(result.truncated());
    }

    @Test
    void scanTruncatesAtTheResultCapAndTheSampleBudget() {
        when(this.level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return position.getX() == 0 && position.getZ() == 0 ? STONE : AIR;
        });

        EffectScanResult capped = this.context.world().scan(
            BlockPos.ZERO,
            new BlockPos(1, 0, 1),
            state -> state.is(Blocks.STONE),
            1
        );
        assertEquals(1, capped.samples().size());
        assertTrue(capped.truncated());

        this.context.close();
        this.context = context(2);
        EffectScanResult budgeted = this.context.world().scan(
            BlockPos.ZERO,
            new BlockPos(1, 0, 1),
            state -> state.is(Blocks.STONE),
            10
        );
        assertEquals(2, budgeted.examined());
        assertTrue(budgeted.truncated());
    }

    @Test
    void scanRejectsInvalidRangesAndResultCaps() {
        EffectWorldQuery world = this.context.world();

        assertThrows(IllegalArgumentException.class, () -> world.scan(
            new BlockPos(1, 0, 0), BlockPos.ZERO, 10
        ));
        assertThrows(IllegalArgumentException.class, () -> world.scan(
            BlockPos.ZERO, new BlockPos(16, 16, 16), 10
        ));
        assertThrows(IllegalArgumentException.class, () -> world.scan(
            BlockPos.ZERO, new BlockPos(1, 0, 1), 0
        ));
        assertThrows(IllegalArgumentException.class, () -> world.scan(
            new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
            new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
            10
        ));
    }

    @Test
    void skipsUnloadedChunksDuringScans() {
        when(this.level.hasChunkAt(any(BlockPos.class))).thenReturn(false);

        EffectScanResult result = this.context.world().scan(
            BlockPos.ZERO, new BlockPos(1, 0, 1), 10
        );

        assertEquals(4, result.examined());
        assertTrue(result.samples().isEmpty());
        verify(this.level, never()).getBlockState(any(BlockPos.class));
    }

    @Test
    void sampleCounterResetsAtTheNextTick() {
        this.context.world().blockState(STONE_POS);

        this.context.beginTick(1);

        assertEquals(0, this.context.counters().worldSamples());
    }

    @Test
    void queryRejectsUseAfterTheSessionCloses() {
        EffectWorldQuery world = this.context.world();

        this.context.close();

        assertThrows(IllegalStateException.class, () -> world.blockState(STONE_POS));
        assertThrows(IllegalStateException.class, () -> world.height(Heightmap.Types.WORLD_SURFACE, 0, 0));
        assertThrows(IllegalStateException.class, () -> world.raycast(Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalStateException.class, () -> world.scan(BlockPos.ZERO, BlockPos.ZERO, 1));
        assertThrows(IllegalStateException.class, this.context::world);
    }

    private GameplayEffectContext context(int maxWorldSamples) {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "world_query_test"),
            null,
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", 100)),
            7L,
            new EffectTickBudget(0, 0, 0, 0, maxWorldSamples),
            OwnerPolicy.CONTINUE
        );
        return new GameplayEffectContext(
            this.level,
            new EffectScope(this.level),
            spec,
            UUID.randomUUID(),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }
}
