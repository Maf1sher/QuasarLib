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
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.scale.SegmentHeightSource;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectSavedData;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    private DimensionDataStorage storage;
    private EffectSavedData store;
    private long gameTime;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.level = mock(ServerLevel.class);
        this.storage = mock(DimensionDataStorage.class);
        this.store = new EffectSavedData();
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.getDataStorage()).thenReturn(this.storage);
        doReturn(this.store).when(this.storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );
        TestEffectRuntime.install(
            this.level,
            new SavedDataEffectStore(this.level),
            new RecordingPacketSink(),
            new TestBudgetSource()
        );
        this.gameTime = 40L;
    }

    @AfterEach
    void tearDown() {
        TestEffectRuntime.uninstall(this.level);
        TestConfig.unload();
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
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.nearby(64.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(2, 2)
            .spacing(4.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
        SegmentedVisualEffectService.start(this.level, spec);
        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
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
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.nearby(64.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(2, 2)
            .spacing(4.0)
            .layers(4, 6.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 0.0))
            .build();
        SegmentedVisualEffectService.start(this.level, spec);
        assertEquals(16, SegmentedVisualEffectService.activeSegmentCount(this.level));

        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(16, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void restoreReusesBakedTerrainHeights() {
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(1, 1)
            .spacing(0.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 0.0))
            .build();
        SegmentedVisualEffectService.start(this.level, spec);
        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
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

        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
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
        this.store.put(EffectDescriptor.builder(UUID.randomUUID(), EffectKind.GAMEPLAY, EFFECT, DIMENSION)
            .placement(new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(3L, 0L, 100)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build());

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
        EffectDescriptor descriptor = EffectDescriptor.builder(instanceId, EffectKind.GAMEPLAY, EFFECT, DIMENSION)
            .placement(new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(3L, 0L, 100)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.destructiveCapability())
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build();
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

    @Test
    void restoringAVisualDoesNotDirtyAnUnchangedStore() {
        this.store.put(visualDescriptor(0L, 200, DIMENSION));
        this.store.setDirty(false);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertFalse(this.store.isDirty());
    }

    @Test
    void restoringSegmentedDoesNotDirtyAnUnchangedStore() {
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(2, 2)
            .spacing(4.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
        SegmentedVisualEffectService.start(this.level, spec);
        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;
        this.store.setDirty(false);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertFalse(this.store.isDirty());
    }

    @Test
    void reportsANewerSchemaWithoutTouchingTheStore() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 99);
        source.putString("future", "data");
        this.store = EffectSavedData.load(source, null);
        doReturn(this.store).when(this.storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.messages().size());
        assertTrue(report.messages().getFirst().contains("newer"));
        assertFalse(this.store.isDirty());
        assertEquals(99, this.store.save(new CompoundTag(), null).getInt("schema_version"));
    }

    @Test
    void reportsRemovedOwnedChildren() {
        UUID parentId = UUID.randomUUID();
        EffectDescriptor parent = gameplayDescriptor(parentId, null);
        EffectDescriptor child = visualDescriptor(0L, 200, DIMENSION).withOwnerSessionId(parentId);
        this.store.put(parent);
        this.store.put(child);
        BlockJournal journal = new BlockJournal();
        journal.record(
            this.level,
            new BlockChange(new BlockPos(0, 64, 0), Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        );
        journal.beginRollback();
        this.store.putJournal(parentId, journal);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.rollbacks());
        assertTrue(report.messages().stream().anyMatch(message -> message.contains("Removed 1 child effect")));
        assertTrue(this.store.contains(parentId));
        assertFalse(this.store.contains(child.instanceId()));
    }

    @Test
    void quarantinesSegmentedEffectWithInvalidSegmentIds() {
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(2, 2)
            .spacing(4.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
        UUID coordinatorId = SegmentedVisualEffectService.start(this.level, spec).coordinatorId();
        SegmentedVisualEffectService.stopAll(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;
        EffectDescriptor descriptor = this.store.find(coordinatorId);
        CompoundTag state = descriptor.state();
        ListTag segmentIds = state.getList("segment_ids", Tag.TAG_STRING);
        segmentIds.set(0, StringTag.valueOf(UUID.randomUUID().toString()));
        this.store.put(descriptor.withState(state));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.quarantined());
        assertFalse(this.store.contains(coordinatorId));
        assertEquals(1, this.store.quarantineCount());
    }

    private static EffectDescriptor gameplayDescriptor(UUID instanceId, UUID ownerSessionId) {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            EFFECT,
            null,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("strike", 100)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE
        );
        return EffectDescriptor.builder(instanceId, EffectKind.GAMEPLAY, EFFECT, DIMENSION)
            .placement(new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(3L, 0L, 100)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .ownerSessionId(ownerSessionId)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build();
    }

    private static EffectDescriptor visualDescriptor(long startGameTime, int durationTicks,
        ResourceLocation dimension) {
        return EffectDescriptor.builder(UUID.randomUUID(), EffectKind.VISUAL, EFFECT, dimension)
            .placement(new EffectTransform(new EffectPoint(1.0, 64.0, 2.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(11L, startGameTime, durationTicks)
            .audience(EffectAudience.nearby(64.0))
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(new CompoundTag())
            .build();
    }

    private static SegmentedVisualEffectSpec segmentedSpecWithAudio() {
        EffectAudioSpec audio = EffectAudioSpec.builder(200)
            .cue(EffectAudioCue.at(
                0,
                new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            ))
            .build();
        return SegmentedVisualEffectSpec.builder(EFFECT, new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0), 200, 7L, EffectAudience.nearby(64.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(1.0F)
            .grid(2, 2)
            .spacing(4.0)
            .layers(1, 0.0)
            .audio(audio)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }

    private static EffectDescriptor visualDescriptorWithAudio() {
        EffectAudioSpec audio = EffectAudioSpec.builder(200)
            .cue(EffectAudioCue.at(
                10,
                new net.minecraft.world.phys.Vec3(1.0, 64.0, 2.0),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            ))
            .build();
        CompoundTag state = new CompoundTag();
        EffectAudioNbt.saveInto(state, audio);
        return EffectDescriptor.builder(UUID.randomUUID(), EffectKind.VISUAL, EFFECT, DIMENSION)
            .placement(new EffectTransform(new EffectPoint(1.0, 64.0, 2.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(11L, 0L, 200)
            .audience(EffectAudience.nearby(64.0))
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(state)
            .build();
    }
}
