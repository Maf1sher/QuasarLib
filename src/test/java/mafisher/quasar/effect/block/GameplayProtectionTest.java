package mafisher.quasar.effect.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class GameplayProtectionTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "protected_effect");
    private static final ResourceLocation PROTECTION =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_protection");
    private static final ResourceLocation SECOND_PROTECTION =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "second_protection");
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);
    private static final BlockPos SECOND = new BlockPos(1, 64, 0);

    private ServerLevel level;
    private BlockState state;
    private BlockState currentState;
    private long gameTime;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        BlockProtectionService.clearForTesting();
        this.level = mock(ServerLevel.class);
        this.state = mock(BlockState.class);
        this.currentState = mock(BlockState.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.isInWorldBounds(any())).thenReturn(true);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.getBlockState(any())).thenReturn(this.currentState);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        BlockProtectionService.clearForTesting();
        TestConfig.unload();
    }

    @Test
    void vetoesOneChangeAndContinuesWithTheRest() {
        BlockProtectionService.register(PROTECTION, context -> !context.position().equals(FIRST));
        QueueController controller = new QueueController(changes(FIRST, SECOND));
        start(controller, 2);

        GameplayEffectService.tick(this.level);

        verify(this.level).setBlockAndUpdate(SECOND, this.state);
        verify(this.level, never()).setBlockAndUpdate(FIRST, this.state);
        assertEquals(1, controller.context.counters().blockChangesApplied());
        assertEquals(1, controller.context.counters().blockChangesVetoed());
        assertEquals(0, controller.context.pendingBlockChanges());
    }

    @Test
    void providesEffectAndWorldMetadata() {
        AtomicReference<BlockProtectionContext> seen = new AtomicReference<>();
        BlockProtectionService.register(PROTECTION, context -> {
            seen.set(context);
            return true;
        });
        QueueController controller = new QueueController(changes(FIRST));
        GameplayEffectHandle handle = start(controller, 1);

        GameplayEffectService.tick(this.level);

        BlockProtectionContext context = seen.get();
        assertNotNull(context);
        assertEquals(this.level, context.level());
        assertEquals(TYPE, context.effectTypeId());
        assertEquals(handle.sessionId(), context.effectSessionId());
        assertNull(context.ownerId());
        assertEquals(FIRST, context.position());
        assertEquals(this.currentState, context.currentState());
        assertEquals(this.state, context.newState());
    }

    @Test
    void protectionFailureVetoesWithoutEndingTheEffect() {
        BlockProtectionService.register(PROTECTION, context -> {
            throw new RuntimeException("test failure");
        });
        QueueController controller = new QueueController(changes(FIRST));
        start(controller, 1);

        GameplayEffectService.tick(this.level);

        verify(this.level, never()).setBlockAndUpdate(any(), any());
        assertEquals(1, GameplayEffectService.activeCount(this.level));
        assertEquals(1, controller.context.counters().blockChangesVetoed());
    }

    @Test
    void requiresEveryProtectionToAllow() {
        BlockProtectionService.register(PROTECTION, context -> true);
        BlockProtectionService.register(SECOND_PROTECTION, context -> false);
        QueueController controller = new QueueController(changes(FIRST));
        start(controller, 1);

        GameplayEffectService.tick(this.level);

        verify(this.level, never()).setBlockAndUpdate(any(), any());
        assertEquals(1, controller.context.counters().blockChangesVetoed());
    }

    private GameplayEffectHandle start(GameplayEffectController controller, int maxBlockChanges) {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            TYPE,
            null,
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", 20)),
            3L,
            new EffectTickBudget(0, 0, 0, maxBlockChanges),
            OwnerPolicy.CONTINUE
        );
        return GameplayEffectService.start(
            this.level,
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(EffectCapabilities.destructiveCapability())
        );
    }

    private List<BlockChange> changes(BlockPos... positions) {
        return java.util.Arrays.stream(positions)
            .map(position -> new BlockChange(position, this.state))
            .toList();
    }

    private static final class QueueController implements GameplayEffectController {
        private final List<BlockChange> changes;
        private GameplayEffectContext context;

        private QueueController(List<BlockChange> changes) {
            this.changes = changes;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            this.context = context;
            for (BlockChange change : this.changes) {
                context.queueBlockChange(change);
            }
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }
}
