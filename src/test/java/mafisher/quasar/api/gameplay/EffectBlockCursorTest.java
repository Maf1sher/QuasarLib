package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.gameplay.EffectBlockCursor;
import mafisher.quasar.api.gameplay.EffectBlockCursorResult;
import mafisher.quasar.api.gameplay.EffectBlockVolume;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectBlockCursorTest {
    private ServerLevel level;
    private ServerChunkCache chunkSource;
    private LevelChunk chunk;
    private LevelChunkSection section;
    private GameplayEffectContext context;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        this.chunkSource = mock(ServerChunkCache.class);
        this.chunk = mock(LevelChunk.class);
        this.section = mock(LevelChunkSection.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(0L);
        when(this.level.getMinBuildHeight()).thenReturn(0);
        when(this.level.getHeight()).thenReturn(16);
        when(this.level.getMinSection()).thenReturn(0);
        when(this.level.getMaxSection()).thenReturn(1);
        when(this.level.getSectionIndexFromSectionY(0)).thenReturn(0);
        when(this.level.getChunkSource()).thenReturn(this.chunkSource);
        when(this.chunkSource.getChunkNow(0, 0)).thenReturn(this.chunk);
        when(this.chunk.getSection(0)).thenReturn(this.section);
        when(this.section.hasOnlyAir()).thenReturn(false);
        when(this.section.getBlockState(anyInt(), anyInt(), anyInt()))
            .thenReturn(Blocks.STONE.defaultBlockState());
        this.context = context();
    }

    @AfterEach
    void tearDown() {
        this.context.close();
    }

    @Test
    void visitsSphereBlocksAndResumesInsideTheSameSection() {
        EffectBlockCursor cursor = this.context.world().cursor(
            EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 2.5)
        );
        List<BlockPos> first = new ArrayList<>();
        EffectBlockCursorResult partial = cursor.visit(
            5, state -> !state.isAir(), (position, state) -> first.add(position)
        );
        assertTrue(partial.truncated());
        assertFalse(cursor.finished());
        assertEquals(5, first.size());

        EffectBlockCursor resumed = EffectBlockCursor.restore(
            this.context.world(),
            EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 2.5),
            cursor.save()
        );
        List<BlockPos> rest = new ArrayList<>();
        while (!resumed.finished()) {
            resumed.visit(1_000, state -> !state.isAir(), (position, state) -> rest.add(position));
        }
        assertFalse(rest.isEmpty());
        assertTrue(first.stream().allMatch(position -> position.distToCenterSqr(8.0, 8.0, 8.0) <= 6.25));
        assertTrue(rest.stream().allMatch(position -> position.distToCenterSqr(8.0, 8.0, 8.0) <= 6.25));
    }

    @Test
    void skipsAnAllAirSection() {
        when(this.section.hasOnlyAir()).thenReturn(true);
        EffectBlockCursor cursor = this.context.world().cursor(
            EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 2.5)
        );
        List<BlockPos> positions = new ArrayList<>();
        EffectBlockCursorResult result = cursor.visit(
            1_000, state -> !state.isAir(), (position, state) -> positions.add(position)
        );
        assertTrue(result.finished());
        assertTrue(positions.isEmpty());
    }

    @Test
    void pausesInsteadOfLoadingAnUnloadedChunk() {
        when(this.chunkSource.getChunkNow(0, 0)).thenReturn(null);
        EffectBlockCursor cursor = this.context.world().cursor(
            EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 2.5)
        );
        EffectBlockCursorResult result = cursor.visit(1_000, state -> true, (position, state) -> {
        });
        assertTrue(result.paused());
        assertTrue(result.truncated());
        assertFalse(result.finished());
    }

    private GameplayEffectContext context() {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "cursor_test"),
            UUID.randomUUID(),
            new net.minecraft.world.phys.Vec3(8.0, 8.0, 8.0),
            EffectPhasePlan.of(new EffectPhase("run", 100)),
            7L,
            new EffectTickBudget(0, 0, 0, 0, 10_000, 0, 0),
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
