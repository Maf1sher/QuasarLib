package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplayEffectServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ServerLevel level;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        GameplayEffectService.resetBudgetForTesting();
    }

    @Test
    void defersASecondOwnerStartToTheNextTick() {
        start(OWNER, OwnerPolicy.CONTINUE, new RecordingController());

        GameplayEffectHandle deferred = start(
            OWNER, OwnerPolicy.CONTINUE, new RecordingController()
        );

        assertEquals(1, GameplayEffectService.activeCount(this.level));
        assertEquals(1, GameplayEffectService.pendingCount(this.level));
        assertTrue(GameplayEffectService.isPending(this.level, deferred));
        assertFalse(GameplayEffectService.isActive(this.level, deferred));

        this.gameTime = 1;
        GameplayEffectService.tick(this.level);

        assertTrue(GameplayEffectService.isActive(this.level, deferred));
        assertEquals(2, GameplayEffectService.activeCount(this.level));
        assertEquals(0, GameplayEffectService.pendingCount(this.level));
    }

    @Test
    void defersStartsAboveTheOwnerSessionBudget() {
        List<GameplayEffectHandle> handles = new ArrayList<>();
        for (int tick = 0; tick < GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER; tick++) {
            this.gameTime = tick;
            handles.add(start(OWNER, OwnerPolicy.CONTINUE, new RecordingController()));
        }
        this.gameTime = GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER;

        GameplayEffectHandle deferred = start(
            OWNER, OwnerPolicy.CONTINUE, new RecordingController()
        );

        assertEquals(
            GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER,
            GameplayEffectService.activeCount(this.level)
        );
        assertEquals(1, GameplayEffectService.pendingCount(this.level));

        GameplayEffectService.stop(this.level, handles.getFirst(), GameplayEffectEndReason.CANCELLED);
        this.gameTime++;
        GameplayEffectService.tick(this.level);

        assertTrue(GameplayEffectService.isActive(this.level, deferred));
        assertEquals(
            GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER,
            GameplayEffectService.activeCount(this.level)
        );
        assertEquals(0, GameplayEffectService.pendingCount(this.level));
    }

    @Test
    void defersWhenTheLevelBudgetIsFull() {
        GameplayEffectService.overrideBudgetForTesting(1, 8, 8);
        UUID otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000003");
        GameplayEffectHandle first = start(
            OWNER, OwnerPolicy.CONTINUE, new RecordingController()
        );
        GameplayEffectHandle deferred = start(
            otherOwner, OwnerPolicy.CONTINUE, new RecordingController()
        );

        assertEquals(1, GameplayEffectService.activeCount(this.level));
        assertTrue(GameplayEffectService.isPending(this.level, deferred));

        GameplayEffectService.stop(this.level, first, GameplayEffectEndReason.CANCELLED);
        this.gameTime++;
        GameplayEffectService.tick(this.level);

        assertTrue(GameplayEffectService.isActive(this.level, deferred));
        assertEquals(1, GameplayEffectService.activeCount(this.level));
        assertEquals(0, GameplayEffectService.pendingCount(this.level));
    }

    @Test
    void ownerDeathEndsTheSession() {
        RecordingController controller = new RecordingController();
        start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, controller);
        when(this.level.getEntity(OWNER)).thenReturn(null);

        GameplayEffectService.tick(this.level);

        assertEquals(0, GameplayEffectService.activeCount(this.level));
        assertEquals(GameplayEffectEndReason.OWNER_UNAVAILABLE, controller.endReason);
    }

    @Test
    void ownerDimensionChangeEndsTheSession() {
        RecordingController controller = new RecordingController();
        start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, controller);
        Entity owner = mock(Entity.class);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(owner.level()).thenReturn(mock(ServerLevel.class));
        when(this.level.getEntity(OWNER)).thenReturn(owner);

        GameplayEffectService.tick(this.level);

        assertEquals(0, GameplayEffectService.activeCount(this.level));
        assertEquals(GameplayEffectEndReason.OWNER_UNAVAILABLE, controller.endReason);
    }

    @Test
    void runsContextCleanupWhenOnEndThrows() {
        AtomicBoolean cleaned = new AtomicBoolean();
        GameplayEffectHandle handle = start(
            OWNER, OwnerPolicy.CONTINUE, new ThrowingEndController(cleaned)
        );

        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);

        assertTrue(cleaned.get());
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void reservesPartOfTheBlockBudgetForRollback() {
        assertEquals(0, GameplayEffectService.rollbackReserve(0, 1));
        assertEquals(0, GameplayEffectService.rollbackReserve(8, 0));
        assertEquals(1, GameplayEffectService.rollbackReserve(1, 1));
        assertEquals(2, GameplayEffectService.rollbackReserve(8, 1));
    }

    @Test
    void continuePolicyKeepsTheSessionAfterOwnerLoss() {
        start(OWNER, OwnerPolicy.CONTINUE, new RecordingController());
        when(this.level.getEntity(OWNER)).thenReturn(null);

        GameplayEffectService.tick(this.level);

        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void levelUnloadStopsEverySessionWithTheUnloadReason() {
        RecordingController controller = new RecordingController();
        start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, controller);

        int stopped = GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.LEVEL_UNLOAD);

        assertEquals(1, stopped);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
        assertEquals(GameplayEffectEndReason.LEVEL_UNLOAD, controller.endReason);
    }

    @Test
    void serverShutdownStopsEverySessionWithTheShutdownReason() {
        RecordingController controller = new RecordingController();
        start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, controller);

        int stopped = GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.SERVER_STOPPING);

        assertEquals(1, stopped);
        assertEquals(0, GameplayEffectService.activeCount(this.level));
        assertEquals(GameplayEffectEndReason.SERVER_STOPPING, controller.endReason);
    }

    private GameplayEffectHandle start(UUID owner, OwnerPolicy policy, GameplayEffectController controller) {
        return GameplayEffectService.start(this.level, spec(owner, policy), controller);
    }

    private static GameplayEffectSpec spec(UUID owner, OwnerPolicy policy) {
        return new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_gameplay"),
            owner,
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", 100)),
            7L,
            new EffectTickBudget(0, 0, 0),
            policy
        );
    }

    private static final class ThrowingEndController implements GameplayEffectController {
        private final AtomicBoolean cleaned;

        private ThrowingEndController(AtomicBoolean cleaned) {
            this.cleaned = cleaned;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            context.onClose(() -> this.cleaned.set(true));
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }

        @Override
        public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
            throw new RuntimeException("onEnd failure");
        }
    }

    private static final class RecordingController implements GameplayEffectController {
        private GameplayEffectEndReason endReason;

        @Override
        public void onStart(GameplayEffectContext context) {
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }

        @Override
        public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
            this.endReason = reason;
        }
    }
}
