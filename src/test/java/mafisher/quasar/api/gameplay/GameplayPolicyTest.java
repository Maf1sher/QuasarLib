package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
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
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class GameplayPolicyTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "policy_effect");
    private static final ResourceLocation POLICY =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_policy");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000030");

    private ServerLevel level;

    @BeforeEach
    void setUp() {
        DestructiveEffectPolicies.clearForTesting();
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        DestructiveEffectPolicies.clearForTesting();
    }

    @Test
    void rejectsDestructiveStartWhenPolicyDenies() {
        policy(false);

        assertThrows(IllegalStateException.class, () -> start(destructiveOptions()));
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void startsNonDestructiveEffectsWhenPolicyDenies() {
        policy(false);

        start(EffectPersistenceOptions.DEFAULT);

        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void startsDestructiveEffectsWhenPolicyAllows() {
        policy(true);

        start(destructiveOptions());

        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void defersDestructiveRestoreWhenPolicyDenies() {
        policy(false);
        GameplayEffectSpec spec = spec();

        assertTrue(GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec), new DummyController()
        ).isEmpty());
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void restoresDestructiveEffectsWhenPolicyAllows() {
        policy(true);
        GameplayEffectSpec spec = spec();

        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec), new DummyController()
        ).orElseThrow();

        assertNotNull(handle);
        assertEquals(1, GameplayEffectService.activeCount(this.level));
    }

    private GameplayEffectHandle start(EffectPersistenceOptions options) {
        return GameplayEffectService.start(this.level, spec(), new DummyController(), options);
    }

    private static EffectPersistenceOptions destructiveOptions() {
        return EffectPersistenceOptions.DEFAULT.withCapabilities(EffectCapabilities.destructiveCapability());
    }

    private static void policy(boolean allow) {
        DestructiveEffectPolicies.register(POLICY, context -> allow);
    }

    private static GameplayEffectSpec spec() {
        return new GameplayEffectSpec(
            TYPE,
            null,
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", 20)),
            3L,
            new EffectTickBudget(0, 0, 0, 1),
            OwnerPolicy.CONTINUE
        );
    }

    private static EffectDescriptor descriptor(GameplayEffectSpec spec) {
        return EffectDescriptor.builder(SESSION, EffectKind.GAMEPLAY, TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            .placement(new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(spec.seed(), 0L, spec.phases().totalDurationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.destructiveCapability())
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build();
    }

    private static final class DummyController implements GameplayEffectController {
        @Override
        public void onStart(GameplayEffectContext context) {
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }
}
