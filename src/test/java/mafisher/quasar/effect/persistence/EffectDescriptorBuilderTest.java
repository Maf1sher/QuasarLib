package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectDescriptorBuilderTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

    @Test
    void builderProvidesSafeOwnershipAndStateDefaults() {
        UUID instanceId = UUID.randomUUID();
        EffectDescriptor descriptor = EffectDescriptor.builder(
                instanceId, EffectKind.VISUAL, TYPE, DIMENSION
            )
            .placement(new EffectTransform(EffectPoint.ZERO, 0.0F, 0.0F, 0.0F), 2.0F)
            .timing(7L, 100L, 200)
            .audience(EffectAudience.dimensionWide())
            .build();

        assertEquals(instanceId, descriptor.instanceId());
        assertEquals(2.0F, descriptor.scale());
        assertEquals(200, descriptor.durationTicks());
        assertNull(descriptor.ownerSessionId());
        assertTrue(descriptor.state().isEmpty());
        assertEquals(EffectCapabilities.NONE, descriptor.capabilities());
        assertEquals(EffectPersistence.COSMETIC, descriptor.persistence());
    }

    @Test
    void builderRequiresTiming() {
        assertThrows(IllegalStateException.class, () -> EffectDescriptor.builder(
                UUID.randomUUID(), EffectKind.VISUAL, TYPE, DIMENSION
            )
            .placement(new EffectTransform(EffectPoint.ZERO, 0.0F, 0.0F, 0.0F), 1.0F)
            .build());
    }
}
