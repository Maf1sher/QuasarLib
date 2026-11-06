package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplayBlockOperationsTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_block_effect");
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);
    private static final BlockPos SECOND = new BlockPos(1, 64, 0);
    private static final BlockPos THIRD = new BlockPos(2, 64, 0);
    private static final BlockPos FOURTH = new BlockPos(3, 64, 0);

    private ServerLevel level;
    private BlockState state;
    private long gameTime;

    @BeforeEach
    void setUp() {
        DestructiveEffectPolicies.clearForTesting();
        this.level = mock(ServerLevel.class);
        this.state = mock(BlockState.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.isInWorldBounds(any())).thenReturn(true);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        DestructiveEffectPolicies.clearForTesting();
    }

    @Test
    void queuesAndDefersChangesAcrossTicks() {
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST, SECOND));
        start(controller, 1);

        assertEquals(2, controller.accepted);
        assertEquals(2, controller.context.pendingBlockChanges());

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(FIRST, this.state);
        verify(this.level, never()).setBlockAndUpdate(SECOND, this.state);
        assertEquals(1, controller.context.pendingBlockChanges());

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(SECOND, this.state);
        assertEquals(0, controller.context.pendingBlockChanges());
    }

    @Test
    void appliesQueuedChangesOnTheExpiryTick() {
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST));
        GameplayEffectSpec spec = spec(new EffectTickBudget(0, 0, 0, 1), 1);
        GameplayEffectService.start(
            this.level,
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(EffectCapabilities.destructiveCapability())
        );

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(FIRST, this.state);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void requiresTheDestructiveCapability() {
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST));

        assertThrows(
            IllegalStateException.class,
            () -> start(controller, new EffectTickBudget(0, 0, 0, 1), EffectCapabilities.NONE)
        );
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void returnsFalseWhenTheBlockBudgetIsZero() {
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST));

        start(controller, new EffectTickBudget(0, 0, 0, 0), EffectCapabilities.destructiveCapability());

        assertEquals(0, controller.accepted);
        assertEquals(0, controller.context.pendingBlockChanges());
        GameplayEffectService.tick(this.level);
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void rejectsPositionsOutsideTheLevel() {
        BlockPos outside = new BlockPos(0, -1_000, 0);
        when(this.level.isInWorldBounds(outside)).thenReturn(false);
        QueueOnStartController controller = new QueueOnStartController(changes(outside));

        assertThrows(IllegalArgumentException.class, () -> start(controller, 1));
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void skipsChangesInUnloadedChunks() {
        when(this.level.hasChunkAt(FIRST)).thenReturn(false);
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST));
        start(controller, 1);

        GameplayEffectService.tick(this.level);

        verify(this.level, never()).setBlockAndUpdate(any(), any());
        assertEquals(0, controller.context.pendingBlockChanges());
    }

    @Test
    void restoredSessionsKeepTheDestructiveCapability() {
        GameplayEffectSpec spec = spec(new EffectTickBudget(0, 0, 0, 1));
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        EffectDescriptor descriptor = descriptor(spec, sessionId);
        ResumeQueueController controller = new ResumeQueueController(change(FIRST));

        GameplayEffectService.restore(this.level, spec, sessionId, 0L, descriptor, controller);
        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(FIRST, this.state);
        assertEquals(0, controller.context.pendingBlockChanges());
    }

    @Test
    void continuesAfterAnIndividualBlockUpdateFailure() {
        when(this.level.setBlockAndUpdate(FIRST, this.state))
            .thenThrow(new RuntimeException("test failure"));
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST, SECOND));
        start(controller, 2);

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(SECOND, this.state);
        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void levelBudgetCapsAllSessions() {
        QueueOnStartController first = new QueueOnStartController(changes(FIRST, SECOND));
        QueueOnStartController second = new QueueOnStartController(changes(THIRD, FOURTH));
        start(first, new EffectTickBudget(0, 0, 0, 2), EffectCapabilities.destructiveCapability());
        start(second, new EffectTickBudget(0, 0, 0, 2), EffectCapabilities.destructiveCapability());

        GameplayEffectService.tick(this.level, 3);

        verify(this.level, times(3)).setBlockAndUpdate(any(), any());
        assertEquals(0, first.context.pendingBlockChanges());
        assertEquals(1, second.context.pendingBlockChanges());
    }

    @Test
    void appliesQueuedChangesBeforeStop() {
        StopAfterQueueController controller = new StopAfterQueueController(change(FIRST));
        start(controller, 1);

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(FIRST, this.state);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
        assertEquals(GameplayEffectEndReason.CANCELLED, controller.endReason);
    }

    @Test
    void discardsPendingChangesWhenSessionEnds() {
        QueueOnStartController controller = new QueueOnStartController(changes(FIRST, SECOND));
        GameplayEffectHandle handle = start(controller, 1);
        GameplayEffectService.tick(this.level);
        assertEquals(1, controller.context.pendingBlockChanges());

        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);

        assertEquals(0, controller.context.pendingBlockChanges());
    }

    @Test
    void countersReportQueuedAndAppliedWork() {
        QueueOnTickController controller = new QueueOnTickController(change(FIRST));
        start(controller, 1);

        GameplayEffectService.tick(this.level);

        assertEquals(1, controller.context.counters().blockChangesQueued());
        assertEquals(1, controller.context.counters().blockChangesApplied());
        assertEquals(0, controller.context.pendingBlockChanges());
    }

    private GameplayEffectHandle start(GameplayEffectController controller, int maxBlockChanges) {
        return start(
            controller,
            new EffectTickBudget(0, 0, 0, maxBlockChanges),
            EffectCapabilities.destructiveCapability()
        );
    }

    private GameplayEffectHandle start(
        GameplayEffectController controller,
        EffectTickBudget budget,
        EffectCapabilities capabilities
    ) {
        GameplayEffectSpec spec = spec(budget);
        return GameplayEffectService.start(
            this.level,
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(capabilities)
        );
    }

    private GameplayEffectSpec spec(EffectTickBudget budget) {
        return spec(budget, 20);
    }

    private GameplayEffectSpec spec(EffectTickBudget budget, int durationTicks) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            3L,
            budget,
            OwnerPolicy.CONTINUE
        );
    }

    private EffectDescriptor descriptor(GameplayEffectSpec spec, UUID sessionId) {
        return new EffectDescriptor(
            sessionId,
            EffectKind.GAMEPLAY,
            spec.typeId(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            spec.seed(),
            0L,
            spec.phases().totalDurationTicks(),
            EffectAudience.dimensionWide(),
            EffectCapabilities.destructiveCapability(),
            EffectPersistence.RESUMABLE,
            null,
            GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag())
        );
    }

    private List<BlockChange> changes(BlockPos... positions) {
        return java.util.Arrays.stream(positions)
            .map(position -> new BlockChange(position, this.state))
            .toList();
    }

    private BlockChange change(BlockPos position) {
        return new BlockChange(position, this.state);
    }

    private static final class ResumeQueueController implements GameplayEffectController {
        private final BlockChange change;
        private GameplayEffectContext context;

        private ResumeQueueController(BlockChange change) {
            this.change = change;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            throw new AssertionError("onStart must not run during restore");
        }

        @Override
        public void onResume(GameplayEffectContext context) {
            this.context = context;
            context.queueBlockChange(this.change);
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    private static final class QueueOnStartController implements GameplayEffectController {
        private final List<BlockChange> changes;
        private GameplayEffectContext context;
        private int accepted;

        private QueueOnStartController(List<BlockChange> changes) {
            this.changes = changes;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            this.context = context;
            for (BlockChange change : this.changes) {
                if (context.queueBlockChange(change)) {
                    this.accepted++;
                }
            }
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    private static final class QueueOnTickController implements GameplayEffectController {
        private final BlockChange change;
        private GameplayEffectContext context;

        private QueueOnTickController(BlockChange change) {
            this.change = change;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            this.context = context;
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            context.queueBlockChange(this.change);
        }
    }

    private static final class StopAfterQueueController implements GameplayEffectController {
        private final BlockChange change;
        private GameplayEffectEndReason endReason;

        private StopAfterQueueController(BlockChange change) {
            this.change = change;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            context.queueBlockChange(this.change);
            context.requestStop();
        }

        @Override
        public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
            this.endReason = reason;
        }
    }
}
