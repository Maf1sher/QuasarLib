package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import mafisher.quasar.effect.persistence.EffectCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DestructiveEffectPoliciesTest {
    private static final ResourceLocation POLICY =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_policy");
    private static final ResourceLocation SECOND_POLICY =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "second_policy");

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        DestructiveEffectPolicies.clearForTesting();
    }

    @Test
    void allowsWhenNoPoliciesAreRegistered() {
        assertTrue(DestructiveEffectPolicies.allows(context()));
    }

    @Test
    void requiresEveryPolicyToAllow() {
        DestructiveEffectPolicies.register(POLICY, ignored -> true);
        DestructiveEffectPolicies.register(SECOND_POLICY, ignored -> false);

        assertFalse(DestructiveEffectPolicies.allows(context()));
    }

    @Test
    void deniesWhenAPolicyThrows() {
        DestructiveEffectPolicies.register(POLICY, ignored -> {
            throw new RuntimeException("test failure");
        });

        assertFalse(DestructiveEffectPolicies.allows(context()));
    }

    @Test
    void rejectsDuplicateIds() {
        DestructiveEffectPolicies.register(POLICY, ignored -> true);

        assertThrows(
            IllegalStateException.class,
            () -> DestructiveEffectPolicies.register(POLICY, ignored -> true)
        );
    }

    @Test
    void freezesRegistration() {
        DestructiveEffectPolicies.freeze();

        assertThrows(
            IllegalStateException.class,
            () -> DestructiveEffectPolicies.register(POLICY, ignored -> true)
        );
    }

    @Test
    void registersTheBuiltInConfigPolicy() {
        DestructiveEffectPolicies.registerConfigPolicy();

        assertEquals(1, DestructiveEffectPolicies.size());
    }

    @Test
    void registersThroughTheEvent() {
        new RegisterDestructiveEffectPoliciesEvent().register(POLICY, ignored -> false);

        assertEquals(1, DestructiveEffectPolicies.size());
        assertFalse(DestructiveEffectPolicies.allows(context()));
    }

    private static DestructiveEffectPolicyContext context() {
        return new DestructiveEffectPolicyContext(
            mock(ServerLevel.class),
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "policy_effect"),
            null,
            Vec3.ZERO,
            EffectCapabilities.destructiveCapability()
        );
    }
}
