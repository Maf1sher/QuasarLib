package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        assertNull(GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec), new DummyController()
        ));
        assertEquals(0, GameplayEffectService.activeCount(this.level));
    }

    @Test
    void restoresDestructiveEffectsWhenPolicyAllows() {
        policy(true);
        GameplayEffectSpec spec = spec();

        GameplayEffectHandle handle = GameplayEffectService.restore(
            this.level, spec, SESSION, 0L, descriptor(spec), new DummyController()
        );

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
        return new EffectDescriptor(
            SESSION,
            EffectKind.GAMEPLAY,
            TYPE,
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

    private static final class DummyController implements GameplayEffectController {
        @Override
        public void onStart(GameplayEffectContext context) {
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }
}
