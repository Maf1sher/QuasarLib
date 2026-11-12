package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import mafisher.quasar.api.gameplay.DestructiveEffectPolicy;
import mafisher.quasar.api.gameplay.DestructiveEffectPolicyContext;
import mafisher.quasar.api.gameplay.RegisterDestructiveEffectPoliciesEvent;
import mafisher.quasar.api.persistence.EffectCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public policy surface. */
class PublicPolicyApiVisibilityTest {
    @Test
    void exposesTheDestructiveEffectPolicyContract() {
        DestructiveEffectPolicy policy = context -> true;
        DestructiveEffectPolicyContext context = new DestructiveEffectPolicyContext(
            mock(ServerLevel.class),
            ResourceLocation.fromNamespaceAndPath("test", "policy_effect"),
            null,
            Vec3.ZERO,
            EffectCapabilities.destructiveCapability()
        );

        assertTrue(policy.allows(context));
        assertNotNull(RegisterDestructiveEffectPoliciesEvent.class);
        assertEquals(5, DestructiveEffectPolicyContext.class.getRecordComponents().length);
    }
}
