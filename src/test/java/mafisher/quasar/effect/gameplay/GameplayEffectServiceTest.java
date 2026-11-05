package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
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
    }

    @Test
    void rejectsASecondOwnerStartInTheSameTick() {
        start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, new RecordingController());

        assertThrows(IllegalStateException.class,
            () -> start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, new RecordingController()));
        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void rejectsStartsAboveTheOwnerSessionCap() {
        for (int tick = 0; tick < GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER; tick++) {
            this.gameTime = tick;
            start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, new RecordingController());
        }
        this.gameTime = GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER;

        assertThrows(IllegalStateException.class,
            () -> start(OWNER, OwnerPolicy.STOP_WHEN_UNAVAILABLE, new RecordingController()));
        assertEquals(
            GameplayEffectService.MAX_ACTIVE_SESSIONS_PER_OWNER,
            GameplayEffectService.activeCount(this.level)
        );
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

    private GameplayEffectHandle start(UUID owner, OwnerPolicy policy, RecordingController controller) {
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
