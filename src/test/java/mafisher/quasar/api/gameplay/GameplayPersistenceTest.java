package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.persistence.EffectDescriptorNbt;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import mafisher.quasar.effect.persistence.EffectSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameplayPersistenceTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_gameplay");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);

    private ServerLevel level;
    private EffectSavedData store;
    private long gameTime;
    private BlockState oldState;
    private BlockState newState;
    private AtomicReference<BlockState> worldState;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.level = mock(ServerLevel.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        this.store = new EffectSavedData();
        this.oldState = Blocks.STONE.defaultBlockState();
        this.newState = Blocks.AIR.defaultBlockState();
        this.worldState = new AtomicReference<>(this.oldState);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.getDataStorage()).thenReturn(storage);
        when(this.level.isInWorldBounds(any())).thenReturn(true);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.getBlockState(any())).thenAnswer(ignored -> this.worldState.get());
        when(this.level.setBlockAndUpdate(any(), any())).thenAnswer(invocation -> {
            this.worldState.set(invocation.getArgument(1));
            return true;
        });
        doReturn(this.store).when(storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        BlockRollbackService.clear(this.level);
        TestConfig.unload();
    }

    @Test
    void resumesAtTheRestoredAgeWithoutCallingOnStart() {
        GameplayEffectSpec spec = spec();
        this.gameTime = 15L;
        CompoundTag initialState = new CompoundTag();
        initialState.putInt("counter", 5);
        EffectDescriptor descriptor = descriptor(spec, initialState);

        RecordingController controller = new RecordingController();
        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor, controller
        ).orElseThrow();

        assertEquals(SESSION, handle.sessionId());
        assertEquals(0, controller.starts);
        assertEquals(1, controller.resumes);
        assertEquals(1, controller.resumedPhase);
    }

    @Test
    void duplicateRestoreKeepsTheExistingSessionAndScope() {
        GameplayEffectSpec spec = spec();
        this.gameTime = 5L;
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());
        RecordingController first = new RecordingController();
        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor, first
        ).orElseThrow();
        EffectScope scope = GameplayEffectService.scope(this.level, handle);

        RecordingController second = new RecordingController();
        GameplayEffectHandle duplicate = GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor, second
        ).orElseThrow();

        assertEquals(SESSION, duplicate.sessionId());
        assertEquals(1, GameplayEffectService.activeCount(this.level));
        assertEquals(1, first.resumes);
        assertEquals(0, second.resumes);
        assertSame(scope, GameplayEffectService.scope(this.level, handle));
    }

    @Test
    void persistsConsumerStateChangedDuringATick() {
        GameplayEffectSpec spec = spec();
        this.gameTime = 0L;
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());
        this.store.put(descriptor);

        GameplayEffectService.restore(this.level, spec, SESSION, 0L, descriptor, new RecordingController());
        this.gameTime = 1L;
        GameplayEffectService.tick(this.level);

        EffectDescriptor updated = this.store.find(SESSION);
        assertEquals(1, updated.state().getCompound("state").getInt("counter"));
    }

    @Test
    void restoredSessionsWaitForTheirOwnerDuringTheReconnectGrace() {
        UUID ownerId = UUID.randomUUID();
        GameplayEffectSpec spec = ownedSpec(ownerId, 20_000);
        this.gameTime = 0L;

        GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec, new CompoundTag()), new RecordingController()
        );
        assertEquals(1, GameplayEffectService.activeCount(this.level));

        this.gameTime = GameplayEffectService.RESTORED_OWNER_GRACE_TICKS - 1L;
        GameplayEffectService.tick(this.level);
        assertEquals(1, GameplayEffectService.activeCount(this.level));

        this.gameTime = GameplayEffectService.RESTORED_OWNER_GRACE_TICKS;
        GameplayEffectService.tick(this.level);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void ownerLossAfterAReconnectCancelsImmediately() {
        UUID ownerId = UUID.randomUUID();
        GameplayEffectSpec spec = ownedSpec(ownerId, 20_000);
        this.gameTime = 0L;
        GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec, new CompoundTag()), new RecordingController()
        );

        Entity owner = mock(Entity.class);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(owner.level()).thenReturn(this.level);
        when(this.level.getEntity(ownerId)).thenReturn(owner);

        this.gameTime = 1L;
        GameplayEffectService.tick(this.level);
        assertEquals(1, GameplayEffectService.activeCount(this.level));

        when(this.level.getEntity(ownerId)).thenReturn(null);
        this.gameTime = 2L;
        GameplayEffectService.tick(this.level);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void keepsScopeOwnedVisualDescriptorsOnUnload() {
        this.gameTime = 0L;
        GameplayEffectService.start(this.level, spec(), new VisualController());
        assertEquals(2, this.store.descriptors().size());

        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE);

        assertEquals(2, this.store.descriptors().size());
        assertTrue(this.store.descriptors().stream().anyMatch(
            descriptor -> descriptor.kind() == EffectKind.VISUAL
        ));
    }

    @Test
    void rollsBackAppliedBlockChangesWhenCancelled() {
        GameplayEffectSpec spec = destroyerSpec(4, 20);
        QueueController controller = new QueueController(new BlockChange(FIRST, this.newState));
        GameplayEffectHandle handle = startDestroyer(spec, controller);

        GameplayEffectService.tick(this.level);

        BlockJournal journal = this.store.journal(handle.sessionId());
        assertNotNull(journal);
        assertEquals(1, journal.size());
        verify(this.level).setBlockAndUpdate(FIRST, this.newState);

        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);

        assertTrue(this.store.contains(handle.sessionId()));
        assertTrue(this.store.journal(handle.sessionId()).isRollbackPending());

        BlockRollbackService.tick(this.level, 10);

        verify(this.level).setBlockAndUpdate(FIRST, this.oldState);
        assertFalse(this.store.contains(handle.sessionId()));
        assertNull(this.store.journal(handle.sessionId()));
    }

    @Test
    void keepsTheJournalDirtyWhenTheStoreRejectsTheWrite() {
        EffectSavedData rejecting = mock(EffectSavedData.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        when(this.level.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)))
            .thenReturn(rejecting);
        when(rejecting.putJournal(any(), any())).thenReturn(false);

        GameplayEffectSpec spec = destroyerSpec(4, 20);
        GameplayEffectHandle handle = startDestroyer(
            spec, new QueueController(new BlockChange(FIRST, this.newState))
        );
        GameplayEffectService.tick(this.level);

        ArgumentCaptor<BlockJournal> captor = ArgumentCaptor.forClass(BlockJournal.class);
        verify(rejecting, atLeastOnce()).putJournal(eq(handle.sessionId()), captor.capture());
        assertTrue(captor.getValue().isDirty());
    }

    @Test
    void keepsAFailedRollbackPersistedUntilItSucceeds() {
        GameplayEffectSpec spec = destroyerSpec(4, 20);
        QueueController controller = new QueueController(new BlockChange(FIRST, this.newState));
        GameplayEffectHandle handle = startDestroyer(spec, controller);
        GameplayEffectService.tick(this.level);
        verify(this.level).setBlockAndUpdate(FIRST, this.newState);

        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);
        doReturn(false).when(this.level).setBlockAndUpdate(FIRST, this.oldState);

        BlockRollbackService.tick(this.level, 10);

        assertTrue(this.store.contains(handle.sessionId()));
        BlockJournal journal = this.store.journal(handle.sessionId());
        assertNotNull(journal);
        assertTrue(journal.isRollbackPending());

        when(this.level.setBlockAndUpdate(FIRST, this.oldState)).thenAnswer(invocation -> {
            this.worldState.set(this.oldState);
            return true;
        });
        BlockRollbackService.tick(this.level, 10);

        assertFalse(this.store.contains(handle.sessionId()));
        assertNull(this.store.journal(handle.sessionId()));
    }

    @Test
    void expiryKeepsBlockChangesAndDropsTheJournal() {
        GameplayEffectSpec spec = destroyerSpec(4, 1);
        QueueController controller = new QueueController(new BlockChange(FIRST, this.newState));
        GameplayEffectHandle handle = startDestroyer(spec, controller);

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(FIRST, this.newState);
        assertFalse(this.store.contains(handle.sessionId()));
        assertNull(this.store.journal(handle.sessionId()));
    }

    @Test
    void unloadPreservesTheJournalAndRestoreCanRollBack() {
        GameplayEffectSpec spec = destroyerSpec(4, 20);
        QueueController controller = new QueueController(new BlockChange(FIRST, this.newState));
        GameplayEffectHandle handle = startDestroyer(spec, controller);
        GameplayEffectService.tick(this.level);
        verify(this.level).setBlockAndUpdate(FIRST, this.newState);

        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE);

        assertTrue(this.store.contains(handle.sessionId()));
        BlockJournal journal = this.store.journal(handle.sessionId());
        assertNotNull(journal);
        assertEquals(1, journal.size());
        assertFalse(journal.isRollbackPending());

        EffectDescriptor descriptor = this.store.find(handle.sessionId());
        GameplayEffectService.restore(
            this.level, spec, handle.sessionId(), 0L, descriptor, new RecordingController()
        );
        GameplayEffectService.stop(
            this.level, new GameplayEffectHandle(handle.sessionId()), GameplayEffectEndReason.CANCELLED
        );

        BlockRollbackService.tick(this.level, 10);

        verify(this.level).setBlockAndUpdate(FIRST, this.oldState);
        assertFalse(this.store.contains(handle.sessionId()));
    }

    @Test
    void preservesTheBlockChangeBudgetInDescriptorNbt() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32));
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(32, GameplayEffectNbt.loadSpec(loaded).budget().maxBlockChanges());
    }

    @Test
    void defaultsTheBlockChangeBudgetForOlderDescriptors() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32));
        CompoundTag tag = EffectDescriptorNbt.save(descriptor(spec, new CompoundTag()));
        tag.getCompound("state").getCompound("spec").getCompound("budget").remove("max_block_changes");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxBlockChanges());
    }

    @Test
    void preservesTheWorldSampleBudgetInDescriptorNbt() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32, 64));
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(64, GameplayEffectNbt.loadSpec(loaded).budget().maxWorldSamples());
    }

    @Test
    void preservesTheForceAndDebrisBudgetsInDescriptorNbt() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32, 64, 16, 24));
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(16, GameplayEffectNbt.loadSpec(loaded).budget().maxForceTargets());
        assertEquals(24, GameplayEffectNbt.loadSpec(loaded).budget().maxDebrisPieces());
    }

    @Test
    void defaultsTheForceAndDebrisBudgetsForOlderDescriptors() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32, 64, 16, 24));
        CompoundTag tag = EffectDescriptorNbt.save(descriptor(spec, new CompoundTag()));
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_force_targets");
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_debris_pieces");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxForceTargets());
        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxDebrisPieces());
    }


    @Test
    void defaultsTheWorldSampleBudgetForOlderDescriptors() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(1, 2, 3, 32, 64));
        CompoundTag tag = EffectDescriptorNbt.save(descriptor(spec, new CompoundTag()));
        tag.getCompound("state").getCompound("spec").getCompound("budget").remove("max_world_samples");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxWorldSamples());
    }

    @Test
    void preservesPriorityInDescriptorNbt() {
        GameplayEffectSpec spec = specWithPriority(EffectPriority.HIGH);
        EffectDescriptor descriptor = descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(EffectPriority.HIGH, GameplayEffectNbt.loadSpec(loaded).priority());
    }

    @Test
    void defaultsPriorityForOlderDescriptors() {
        GameplayEffectSpec spec = specWithPriority(EffectPriority.HIGH);
        CompoundTag tag = EffectDescriptorNbt.save(descriptor(spec, new CompoundTag()));
        tag.getCompound("state").getCompound("spec").remove("priority");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(EffectPriority.NORMAL, GameplayEffectNbt.loadSpec(loaded).priority());
    }

    private static GameplayEffectSpec ownedSpec(UUID ownerId, int durationTicks) {
        return new GameplayEffectSpec(
            TYPE,
            ownerId,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
    }

    private static GameplayEffectSpec spec() {
        return spec(EffectTickBudget.unlimited());
    }

    private static GameplayEffectSpec destroyerSpec(int maxBlockChanges, int durationTicks) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            3L,
            new EffectTickBudget(0, 0, 0, maxBlockChanges),
            OwnerPolicy.CONTINUE
        );
    }

    private GameplayEffectHandle startDestroyer(
        GameplayEffectSpec spec,
        GameplayEffectController controller
    ) {
        return GameplayEffectService.start(
            this.level,
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(
                EffectCapabilities.destructiveCapability()
            )
        );
    }

    private static GameplayEffectSpec spec(EffectTickBudget budget) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("telegraph", 10), new EffectPhase("strike", 10)),
            3L,
            budget,
            OwnerPolicy.CONTINUE
        );
    }

    private static GameplayEffectSpec specWithPriority(EffectPriority priority) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("telegraph", 10), new EffectPhase("strike", 10)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE,
            priority
        );
    }

    private static EffectDescriptor descriptor(GameplayEffectSpec spec, CompoundTag consumerState) {
        return EffectDescriptor.builder(SESSION, EffectKind.GAMEPLAY, TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            .placement(new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(spec.seed(), 0L, spec.phases().totalDurationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, consumerState))
            .build();
    }

    private static final class QueueController implements GameplayEffectController {
        private final BlockChange change;

        private QueueController(BlockChange change) {
            this.change = change;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            context.queueBlockChange(this.change);
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    private static final class VisualController implements GameplayEffectController {
        @Override
        public void onStart(GameplayEffectContext context) {
            context.scope().playVisual(VisualEffectSpec.builder(TYPE, Vec3.ZERO, 100, 1L, EffectAudience.nearby(64.0))
                .rotation(0.0F, 0.0F, 0.0F)
                .scale(1.0F)
                .build());
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    private static final class RecordingController implements GameplayEffectController {
        private int starts;
        private int resumes;
        private int resumedPhase;

        @Override
        public void onStart(GameplayEffectContext context) {
            this.starts++;
        }

        @Override
        public void onResume(GameplayEffectContext context) {
            this.resumes++;
            this.resumedPhase = context.phaseIndex();
            context.updatePersistentState(state -> state.putInt("counter", state.getInt("counter") + 1));
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            context.updatePersistentState(state -> state.putInt("counter", 1));
        }
    }
}
