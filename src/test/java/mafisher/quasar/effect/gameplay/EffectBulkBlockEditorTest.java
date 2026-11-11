package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.effect.EffectBounds;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.block.BlockProtectionService;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectBulkBlockEditorTest {
    private ServerLevel level;
    private ServerChunkCache chunkSource;
    private LevelChunk chunk;
    private LevelChunkSection section;
    private LevelChunkSection[] sections;
    private GameplayEffectContext context;
    private GameplayEffectSpec spec;
    private final ChunkPos chunkPos = new ChunkPos(0, 0);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.level = mock(ServerLevel.class);
        this.chunkSource = mock(ServerChunkCache.class);
        this.chunk = mock(LevelChunk.class);
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        PalettedContainerRO<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
            mock(PalettedContainerRO.class);
        this.section = new LevelChunkSection(states, biomes);
        this.section.setBlockState(8, 8, 8, Blocks.STONE.defaultBlockState(), false);
        this.section.setBlockState(0, 0, 0, Blocks.DIRT.defaultBlockState(), false);
        this.sections = new LevelChunkSection[]{this.section};

        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getMinBuildHeight()).thenReturn(-16);
        when(this.level.getMaxBuildHeight()).thenReturn(16);
        when(this.level.getMinSection()).thenReturn(-1);
        when(this.level.getMaxSection()).thenReturn(1);
        when(this.level.getSectionIndexFromSectionY(0)).thenReturn(0);
        when(this.level.getChunkSource()).thenReturn(this.chunkSource);
        when(this.level.players()).thenReturn(List.of());
        when(this.chunkSource.getChunkNow(0, 0)).thenReturn(this.chunk);
        when(this.chunk.getPos()).thenReturn(this.chunkPos);
        when(this.chunk.getSection(0)).thenAnswer(ignored -> this.sections[0]);
        when(this.chunk.getBlockEntities()).thenReturn(Map.of());
        when(this.chunk.getSections()).thenReturn(this.sections);

        this.spec = mock(GameplayEffectSpec.class);
        when(this.spec.typeId()).thenReturn(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "bulk_editor_test")
        );
        when(this.spec.ownerId()).thenReturn(null);
        this.context = mock(GameplayEffectContext.class);
        when(this.context.level()).thenReturn(this.level);
        when(this.context.spec()).thenReturn(this.spec);
        when(this.context.sessionId()).thenReturn(UUID.randomUUID());
        when(this.context.tryAcquireSectionEdit()).thenReturn(true);
        when(this.context.blockJournal()).thenReturn(new BlockJournal());
    }

    @AfterEach
    void tearDown() {
        TerrainEditService.clear(this.level);
    }

    @Test
    void clearsInsideTheVolumeAndLeavesOutsideBlocks() {
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 4.0),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                true
            )
        );

        editor.tick();

        assertTrue(this.section.getBlockState(8, 8, 8).isAir());
        assertEquals(Blocks.DIRT.defaultBlockState(), this.section.getBlockState(0, 0, 0));
        assertTrue(editor.progress().finished());
        assertEquals(1, editor.progress().clearedBlocks());
        assertFalse(editor.clearedStates().isEmpty());
        verify(this.chunk).setUnsaved(true);
        editor.close(false);
    }

    @Test
    void consultsContextProtectionBeforeTheWholeSectionFastPath() {
        when(this.context.hasBlockProtection()).thenReturn(true);
        when(this.context.allowsBlockChange(any(BlockChange.class), any(BlockState.class))).thenReturn(false);
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.box(new EffectBounds(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                true
            )
        );

        editor.tick();

        assertEquals(Blocks.STONE.defaultBlockState(), this.section.getBlockState(8, 8, 8));
        assertEquals(Blocks.DIRT.defaultBlockState(), this.section.getBlockState(0, 0, 0));
        assertEquals(0, editor.progress().clearedBlocks());
        editor.close(false);
    }

    @Test
    void persistsAndRestoresAnIncompleteCursor() {
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 4.0),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                false
            )
        );
        editor.tick();
        var tag = editor.save();
        editor.close(false);

        EffectBulkBlockEditor restored = EffectBulkBlockEditor.restore(this.context, tag);
        assertEquals(editor.progress(), restored.progress());
        restored.close(false);
    }

    @Test
    void clearsAFullSectionThroughThePalettePath() {
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.box(new mafisher.quasar.effect.EffectBounds(
                    0.0, 0.0, 0.0, 16.0, 16.0, 16.0
                )),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                false
            )
        );

        editor.tick();

        assertTrue(this.sections[0].hasOnlyAir());
        assertEquals(2, editor.progress().clearedBlocks());
        editor.close(false);
    }

    @Test
    void replacesAFullyCoveredSectionWithoutRewritingEveryBlock() {
        assumeTrue(!BlockProtectionService.hasProtections());
        for (int index = 0; index < 16 * 16 * 16; index++) {
            this.section.setBlockState(
                index & 15,
                (index >> 4) & 15,
                (index >> 8) & 15,
                Blocks.STONE.defaultBlockState(),
                false
            );
        }
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.box(new mafisher.quasar.effect.EffectBounds(
                    0.0, 0.0, 0.0, 16.0, 16.0, 16.0
                )),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                false
            )
        );

        editor.tick();

        assertNotSame(this.section, this.sections[0]);
        assertTrue(this.sections[0].hasOnlyAir());
        assertEquals(16 * 16 * 16, editor.progress().clearedBlocks());
        editor.close(false);
    }

    @Test
    void ellipsoidAndCylinderBoundsAndContainmentAreConsistent() {
        EffectBlockVolume ellipsoid = EffectBlockVolume.ellipsoid(
            new EffectPoint(0.0, 0.0, 0.0), 4.0, 2.0, 3.0
        );
        assertTrue(ellipsoid.contains(0.5, 0.5, 0.5));
        assertFalse(ellipsoid.contains(4.5, 0.5, 0.5));
        assertTrue(ellipsoid.intersectsSection(-1, -1, -1));
        assertFalse(ellipsoid.intersectsSection(2, 0, 0));

        EffectBlockVolume cylinder = EffectBlockVolume.cylinder(
            new EffectPoint(0.0, 0.0, 0.0), 4.0, 0.0, 10.0
        );
        assertTrue(cylinder.contains(1.0, 5.0, 1.0));
        assertFalse(cylinder.contains(1.0, 11.0, 1.0));
        assertTrue(cylinder.intersectsSection(-1, 0, -1));
        assertFalse(cylinder.intersectsSection(2, 0, 0));
    }

    @Test
    void centerOutOrderingStartsAtTheMiddleSection() {
        LevelHeightAccessor height = mock(LevelHeightAccessor.class);
        when(height.getMinSection()).thenReturn(-4);
        when(height.getMaxSection()).thenReturn(4);
        EffectBlockVolume sphere = EffectBlockVolume.sphere(
            new EffectPoint(8.0, 8.0, 8.0), 40.0
        );

        List<EffectBulkBlockEditor.SectionKey> centered = EffectBulkBlockEditor.orderedSections(
            sphere, height, EffectBulkBlockEditor.Ordering.CENTER_OUT
        );
        List<EffectBulkBlockEditor.SectionKey> topDown = EffectBulkBlockEditor.orderedSections(
            sphere, height, EffectBulkBlockEditor.Ordering.TOP_DOWN
        );

        assertEquals(new EffectBulkBlockEditor.SectionKey(0, 0, 0), centered.get(0));
        assertNotEquals(topDown.get(0), centered.get(0));
        assertTrue(centered.size() > 1);
    }

    @Test
    void tickToScaleStopsAtTheRequestedRadialFront() {
        when(this.chunkSource.getChunkNow(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(this.chunk);
        when(this.level.getMaxSection()).thenReturn(2);
        when(this.level.getMaxBuildHeight()).thenReturn(32);
        when(this.level.getSectionIndexFromSectionY(1)).thenReturn(1);
        LevelChunkSection second = new LevelChunkSection(
            new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
            ),
            this.section.getBiomes()
        );
        second.setBlockState(8, 8, 8, Blocks.STONE.defaultBlockState(), false);
        this.sections = new LevelChunkSection[]{this.section, second};
        when(this.chunk.getSection(1)).thenAnswer(ignored -> this.sections[1]);
        when(this.chunk.getSections()).thenReturn(this.sections);

        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 40.0),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
                0.0,
                false
            ),
            EffectBulkBlockEditor.Ordering.CENTER_OUT
        );

        editor.tickToScale(0.1, 10);
        assertEquals(1, editor.progress().completedSections());
        assertEquals(0, editor.dirtyChunkCount());
        assertEquals(0.0, editor.frontScale(), 1.0E-6);

        editor.tickToScale(0.25, 10);
        assertTrue(editor.progress().completedSections() > 1);
        assertTrue(editor.frontScale() > 0.0);
        editor.close(false);
    }

    @Test
    void unloadedChunkPausesTheFrontWithoutSkippingSections() {
        when(this.chunkSource.getChunkNow(0, 0)).thenReturn(null);
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 4.0),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
                0.0,
                false
            ),
            EffectBulkBlockEditor.Ordering.CENTER_OUT
        );

        editor.tickToScale(1.0, 10);
        assertEquals(0, editor.progress().completedSections());
        assertFalse(editor.progress().finished());

        when(this.chunkSource.getChunkNow(0, 0)).thenReturn(this.chunk);
        editor.tickToScale(1.0, 10);
        assertEquals(1, editor.progress().completedSections());
        assertTrue(editor.progress().finished());
        editor.close(false);
    }

    @Test
    void tickToScaleRejectsNonRadialOrdering() {
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.sphere(new EffectPoint(8.0, 8.0, 8.0), 4.0),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                false
            )
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> editor.tickToScale(0.5, 1)
        );
        editor.close(false);
    }

    @Test
    void deferredTicketReleaseWaitsForQueuedReveals() {
        TerrainEditService.acquireTickets(this.level, List.of(this.chunkPos));
        TerrainEditService.releaseTicketsWhenSettled(this.level, List.of(this.chunkPos));

        verify(this.chunkSource, org.mockito.Mockito.never()).removeRegionTicket(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any()
        );

        TerrainEditService.tick(this.level);

        verify(this.chunkSource).removeRegionTicket(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void keepsTheBottomLayerToAvoidAVoidFloor() {
        when(this.level.getMinBuildHeight()).thenReturn(0);
        when(this.level.getMinSection()).thenReturn(0);
        for (int index = 0; index < 16 * 16 * 16; index++) {
            this.section.setBlockState(
                index & 15,
                (index >> 4) & 15,
                (index >> 8) & 15,
                Blocks.STONE.defaultBlockState(),
                false
            );
        }
        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.box(new mafisher.quasar.effect.EffectBounds(
                    0.0, 0.0, 0.0, 15.999, 15.999, 15.999
                )),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.MANUAL,
                0.0,
                false
            )
        );

        editor.tick();

        assertEquals(Blocks.STONE.defaultBlockState(), this.section.getBlockState(8, 0, 8));
        assertTrue(this.section.getBlockState(8, 1, 8).isAir());
        assertEquals(16 * 16 * 15, editor.progress().clearedBlocks());
        editor.close(false);
    }

    @Test
    void progressiveSyncWaitsForTheLastSectionInAChunk() {
        when(this.level.getMaxSection()).thenReturn(2);
        when(this.level.getMaxBuildHeight()).thenReturn(32);
        when(this.level.getSectionIndexFromSectionY(1)).thenReturn(1);
        LevelChunkSection second = new LevelChunkSection(
            new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
            ),
            this.section.getBiomes()
        );
        second.setBlockState(8, 0, 8, Blocks.STONE.defaultBlockState(), false);
        this.sections = new LevelChunkSection[]{this.section, second};
        when(this.chunk.getSection(1)).thenAnswer(ignored -> this.sections[1]);
        when(this.chunk.getSections()).thenReturn(this.sections);

        EffectBulkBlockEditor editor = EffectBulkBlockEditor.create(
            this.context,
            new EffectBulkBlockEditor.Spec(
                EffectBlockVolume.box(new mafisher.quasar.effect.EffectBounds(
                    0.0, 0.0, 0.0, 15.999, 31.999, 15.999
                )),
                Blocks.AIR.defaultBlockState(),
                true,
                EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
                0.0,
                false
            ),
            EffectBulkBlockEditor.Ordering.CENTER_OUT
        );

        editor.tick(1);
        assertEquals(1, editor.progress().completedSections());
        assertEquals(1, editor.editedSections());
        assertEquals(1, editor.dirtyChunkCount());
        assertTrue(editor.hasPendingSolidSections());

        editor.tick(1);
        assertEquals(2, editor.progress().completedSections());
        assertEquals(2, editor.editedSections());
        assertEquals(0, editor.dirtyChunkCount());
        assertFalse(editor.hasPendingSolidSections());
        editor.close(false);
    }
}
