package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.effect.runtime.GameplayEffectRuntime;
import mafisher.quasar.testing.GameplayEffectFixtures;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class GameplayEffectLifecyclePersistenceTest {
    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.installRuntime();
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.world.level(), GameplayEffectEndReason.CANCELLED);
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void resumesAtTheRestoredAgeWithoutCallingOnStart() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec();
        this.world.setGameTime(15L);
        CompoundTag initialState = new CompoundTag();
        initialState.putInt("counter", 5);
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, initialState);

        GameplayEffectFixtures.RecordingController controller =
            new GameplayEffectFixtures.RecordingController();
        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.world.level(), spec, GameplayEffectFixtures.SESSION, 0L, descriptor, controller
        ).orElseThrow();

        assertEquals(GameplayEffectFixtures.SESSION, handle.sessionId());
        assertEquals(0, controller.starts);
        assertEquals(1, controller.resumes);
        assertEquals(1, controller.resumedPhase);
    }

    @Test
    void duplicateRestoreKeepsTheExistingSessionAndScope() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec();
        this.world.setGameTime(5L);
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());
        GameplayEffectFixtures.RecordingController first =
            new GameplayEffectFixtures.RecordingController();
        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.world.level(), spec, GameplayEffectFixtures.SESSION, 0L, descriptor, first
        ).orElseThrow();
        EffectScope scope = GameplayEffectService.scope(this.world.level(), handle);

        GameplayEffectFixtures.RecordingController second =
            new GameplayEffectFixtures.RecordingController();
        GameplayEffectHandle duplicate = GameplayEffectService.restore(
            this.world.level(), spec, GameplayEffectFixtures.SESSION, 0L, descriptor, second
        ).orElseThrow();

        assertEquals(GameplayEffectFixtures.SESSION, duplicate.sessionId());
        assertEquals(1, GameplayEffectService.activeCount(this.world.level()));
        assertEquals(1, first.resumes);
        assertEquals(0, second.resumes);
        assertSame(scope, GameplayEffectService.scope(this.world.level(), handle));
    }

    @Test
    void persistsConsumerStateChangedDuringATick() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec();
        this.world.setGameTime(0L);
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());
        this.world.store().put(descriptor);

        GameplayEffectService.restore(
            this.world.level(),
            spec,
            GameplayEffectFixtures.SESSION,
            0L,
            descriptor,
            new GameplayEffectFixtures.RecordingController()
        );
        this.world.setGameTime(1L);
        GameplayEffectService.tick(this.world.level());

        EffectDescriptor updated = this.world.store().find(GameplayEffectFixtures.SESSION);
        assertEquals(1, updated.state().getCompound("state").getInt("counter"));
    }

    @Test
    void restoredSessionsWaitForTheirOwnerDuringTheReconnectGrace() {
        UUID ownerId = UUID.randomUUID();
        GameplayEffectSpec spec = GameplayEffectFixtures.ownedSpec(ownerId, 20_000);
        this.world.setGameTime(0L);

        GameplayEffectService.restore(
            this.world.level(),
            spec,
            GameplayEffectFixtures.SESSION,
            0L,
            GameplayEffectFixtures.descriptor(spec, new CompoundTag()),
            new GameplayEffectFixtures.RecordingController()
        );
        assertEquals(1, GameplayEffectService.activeCount(this.world.level()));

        this.world.setGameTime(GameplayEffectRuntime.RESTORED_OWNER_GRACE_TICKS - 1L);
        GameplayEffectService.tick(this.world.level());
        assertEquals(1, GameplayEffectService.activeCount(this.world.level()));

        this.world.setGameTime(GameplayEffectRuntime.RESTORED_OWNER_GRACE_TICKS);
        GameplayEffectService.tick(this.world.level());
        assertEquals(0, GameplayEffectService.activeCount(this.world.level()));
    }

    @Test
    void ownerLossAfterAReconnectCancelsImmediately() {
        UUID ownerId = UUID.randomUUID();
        GameplayEffectSpec spec = GameplayEffectFixtures.ownedSpec(ownerId, 20_000);
        this.world.setGameTime(0L);
        GameplayEffectService.restore(
            this.world.level(),
            spec,
            GameplayEffectFixtures.SESSION,
            0L,
            GameplayEffectFixtures.descriptor(spec, new CompoundTag()),
            new GameplayEffectFixtures.RecordingController()
        );

        Entity owner = mock(Entity.class);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(owner.level()).thenReturn(this.world.level());
        this.world.putEntity(ownerId, owner);

        this.world.setGameTime(1L);
        GameplayEffectService.tick(this.world.level());
        assertEquals(1, GameplayEffectService.activeCount(this.world.level()));

        this.world.removeEntity(ownerId);
        this.world.setGameTime(2L);
        GameplayEffectService.tick(this.world.level());
        assertEquals(0, GameplayEffectService.activeCount(this.world.level()));
    }

    @Test
    void keepsScopeOwnedVisualDescriptorsOnUnload() {
        this.world.setGameTime(0L);
        GameplayEffectService.start(
            this.world.level(),
            GameplayEffectFixtures.spec(),
            new GameplayEffectFixtures.VisualController()
        );
        assertEquals(2, this.world.store().descriptors().size());

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );

        assertEquals(2, this.world.store().descriptors().size());
        assertTrue(this.world.store().descriptors().stream().anyMatch(
            descriptor -> descriptor.kind() == EffectKind.VISUAL
        ));
    }
}
