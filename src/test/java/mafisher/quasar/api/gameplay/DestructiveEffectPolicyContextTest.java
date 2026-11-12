package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import mafisher.quasar.api.gameplay.DestructiveEffectPolicyContext;
import mafisher.quasar.api.persistence.EffectCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DestructiveEffectPolicyContextTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_policy_effect");

    @Test
    void storesFieldsAndAllowsAMissingOwner() {
        ServerLevel level = mock(ServerLevel.class);
        EffectCapabilities capabilities = EffectCapabilities.destructiveCapability();

        DestructiveEffectPolicyContext context = new DestructiveEffectPolicyContext(
            level, TYPE, null, new Vec3(1.0, 64.0, -2.0), capabilities
        );

        assertEquals(level, context.level());
        assertEquals(TYPE, context.typeId());
        assertNull(context.ownerId());
        assertEquals(new Vec3(1.0, 64.0, -2.0), context.anchor());
        assertEquals(capabilities, context.capabilities());
    }

    @Test
    void rejectsInvalidContexts() {
        ServerLevel level = mock(ServerLevel.class);
        EffectCapabilities destructive = EffectCapabilities.destructiveCapability();

        assertThrows(NullPointerException.class, () -> new DestructiveEffectPolicyContext(
            null, TYPE, null, Vec3.ZERO, destructive
        ));
        assertThrows(NullPointerException.class, () -> new DestructiveEffectPolicyContext(
            level, null, null, Vec3.ZERO, destructive
        ));
        assertThrows(NullPointerException.class, () -> new DestructiveEffectPolicyContext(
            level, TYPE, null, null, destructive
        ));
        assertThrows(NullPointerException.class, () -> new DestructiveEffectPolicyContext(
            level, TYPE, null, Vec3.ZERO, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new DestructiveEffectPolicyContext(
            level, TYPE, UUID.randomUUID(), Vec3.ZERO, EffectCapabilities.NONE
        ));
        assertThrows(IllegalArgumentException.class, () -> new DestructiveEffectPolicyContext(
            level, TYPE, null, new Vec3(Double.NaN, 0.0, 0.0), destructive
        ));
    }
}
