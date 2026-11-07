package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.gameplay.EffectPhase;
import mafisher.quasar.effect.gameplay.EffectPhasePlan;
import mafisher.quasar.effect.gameplay.EffectTickBudget;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.gameplay.GameplayEffectSpec;
import mafisher.quasar.effect.gameplay.OwnerPolicy;
import mafisher.quasar.effect.network.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistence;
import mafisher.quasar.effect.scale.SegmentHeightSource;
import mafisher.quasar.effect.scale.SegmentTerrainConform;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectRestoreServiceTest {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("quasar_lib", "effect");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    private ServerLevel level;
    private EffectSavedData store;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        this.store = new EffectSavedData();
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.getDataStorage()).thenReturn(storage);
        doReturn(this.store).when(storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );
        this.gameTime = 40L;
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        SegmentedVisualEffectService.stopAll(this.level);
        BlockRollbackService.clearForTesting();
    }

    @Test
    void restoresAVisualAtItsOriginalAge() {
        EffectDescriptor descriptor = visualDescriptor(0L, 200, DIMENSION);
        this.store.put(descriptor);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, VisualEffectService.activeCount(this.level));
        assertEquals(1, this.store.descriptors().size());
    }

    @Test
    void dropsExpiredDescriptors() {
        this.store.put(visualDescriptor(0L, 40, DIMENSION));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.expired());
        assertEquals(0, VisualEffectService.activeCount(this.level));
        assertTrue(this.store.descriptors().isEmpty());
    }

    @Test
    void keepsRestoredDescriptorsAndAudioForTheNextRestart() {
        EffectDescriptor descriptor = visualDescriptorWithAudio();
        this.store.put(descriptor);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertTrue(this.store.contains(descriptor.instanceId()));

        CompoundTag saved = this.store.save(new CompoundTag(), null);
        EffectSavedData reloaded = EffectSavedData.load(saved, null);

        assertTrue(reloaded.contains(descriptor.instanceId()));
        assertEquals(1, EffectAudioNbt.loadFrom(reloaded.find(descriptor.instanceId()).state()).cueCount());
    }

    @Test
    void quarantinesDescriptorsForAnotherDimension() {
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        this.store.put(visualDescriptor(0L, 200, nether));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.quarantined());
        assertEquals(1, this.store.quarantineCount());
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void restoresSegmentedCoordinatorsWithEverySegment() {
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            EFFECT,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            0.0F, 0.0F, 0.0F,
            1.0F, 2, 2, 4.0,
            200,
            7L,
            EffectAudience.nearby(64.0)
        );
        SegmentedVisualEffectService.start(this.level, spec);
        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stopAll(this.level, false);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void restoresLayeredAndTerrainAlignedCoordinators() {
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            EFFECT,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            0.0F, 0.0F, 0.0F,
            1.0F, 2, 2, 4.0,
            200,
            7L,
            EffectAudience.nearby(64.0),
            EffectAudioSpec.NONE,
            EffectPriority.NORMAL,
            4,
            6.0,
            new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 0.0)
        );
        SegmentedVisualEffectService.start(this.level, spec);
        assertEquals(16, SegmentedVisualEffectService.activeSegmentCount(this.level));

        SegmentedVisualEffectService.stopAll(this.level, false);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(16, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void restoreReusesBakedTerrainHeights() {
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            EFFECT,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            0.0F, 0.0F, 0.0F,
            1.0F, 1, 1, 0.0,
            200,
            7L,
            EffectAudience.dimensionWide(),
            EffectAudioSpec.NONE,
            EffectPriority.NORMAL,
            1,
            0.0,
            new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 0.0)
        );
        SegmentedVisualEffectService.start(this.level, spec);
        SegmentedVisualEffectService.stopAll(this.level, false);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;
        when(this.level.hasChunkAt(anyInt(), anyInt())).thenReturn(true);
        when(this.level.getHeight(eq(Heightmap.Types.WORLD_SURFACE), anyInt(), anyInt())).thenReturn(120);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(this.level.players()).thenReturn(List.of(player));
        StartSegmentedVisualEffectPayload payload =
            SegmentedVisualEffectService.resumePayloads(this.level, player).getFirst();
        assertEquals(1, report.restored());
        assertEquals(64.0, payload.heights().y(0, 0));
    }

    @Test
    void restoresSegmentedAudioPlan() {
        SegmentedVisualEffectSpec spec = segmentedSpecWithAudio();
        SegmentedVisualEffectService.start(this.level, spec);

        SegmentedVisualEffectService.stopAll(this.level, false);
        VisualEffectService.clear(this.level);
        this.gameTime = 20L;

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(1, EffectAudioService.activeCount(this.level));
    }

    @Test
    void quarantinesGameplayWithoutARegisteredController() {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            EFFECT,
            null,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("strike", 100)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE
        );
        this.store.put(new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.GAMEPLAY,
            EFFECT,
            DIMENSION,
            new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            3L,
            0L,
            100,
            EffectAudience.dimensionWide(),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag())
        ));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.quarantined());
        assertEquals(1, this.store.quarantineCount());
    }

    @Test
    void schedulesPendingRollbacksBeforeRestoring() {
        UUID instanceId = UUID.randomUUID();
        BlockPos position = new BlockPos(0, 64, 0);
        BlockState oldState = Blocks.STONE.defaultBlockState();
        BlockState newState = Blocks.AIR.defaultBlockState();
        BlockJournal journal = new BlockJournal();
        journal.record(this.level, new BlockChange(position, newState), oldState);
        journal.beginRollback();

        GameplayEffectSpec spec = new GameplayEffectSpec(
            EFFECT,
            null,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("strike", 100)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE
        );
        EffectDescriptor descriptor = new EffectDescriptor(
            instanceId,
            EffectKind.GAMEPLAY,
            EFFECT,
            DIMENSION,
            new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            3L,
            0L,
            100,
            EffectAudience.dimensionWide(),
            EffectCapabilities.destructiveCapability(),
            EffectPersistence.RESUMABLE,
            null,
            GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag())
        );
        this.store.put(descriptor);
        this.store.putJournal(instanceId, journal);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.getBlockState(position)).thenReturn(newState);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.rollbacks());
        assertTrue(this.store.contains(instanceId));

        BlockRollbackService.tick(this.level, 10);

        verify(this.level).setBlockAndUpdate(position, oldState);
        assertFalse(this.store.contains(instanceId));
    }

    private static EffectDescriptor visualDescriptor(long startGameTime, int durationTicks,
        ResourceLocation dimension) {
        return new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.VISUAL,
            EFFECT,
            dimension,
            new EffectTransform(new EffectPoint(1.0, 64.0, 2.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            11L,
            startGameTime,
            durationTicks,
            EffectAudience.nearby(64.0),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            new CompoundTag()
        );
    }

    private static SegmentedVisualEffectSpec segmentedSpecWithAudio() {
        EffectAudioSpec audio = EffectAudioSpec.builder(200)
            .cue(
                0,
                new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            )
            .build();
        return new SegmentedVisualEffectSpec(
            EFFECT,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            0.0F, 0.0F, 0.0F,
            1.0F, 2, 2, 4.0,
            200,
            7L,
            EffectAudience.nearby(64.0),
            audio
        );
    }

    private static EffectDescriptor visualDescriptorWithAudio() {
        EffectAudioSpec audio = EffectAudioSpec.builder(200)
            .cue(
                10,
                new net.minecraft.world.phys.Vec3(1.0, 64.0, 2.0),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            )
            .build();
        CompoundTag state = new CompoundTag();
        EffectAudioNbt.saveInto(state, audio);
        return new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.VISUAL,
            EFFECT,
            DIMENSION,
            new EffectTransform(new EffectPoint(1.0, 64.0, 2.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            11L,
            0L,
            200,
            EffectAudience.nearby(64.0),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            state
        );
    }
}
