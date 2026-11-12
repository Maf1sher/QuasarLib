package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VisualEffectSpecTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "spec_test");

    @Test
    void builderDefaultsOptionalFields() {
        VisualEffectSpec spec = VisualEffectSpec.builder(
            EFFECT_ID, new Vec3(1.0, 64.0, 2.0), 100, 7L, EffectAudience.dimensionWide()
        ).build();

        assertEquals(0.0F, spec.yaw());
        assertEquals(0.0F, spec.pitch());
        assertEquals(0.0F, spec.roll());
        assertEquals(1.0F, spec.scale());
        assertEquals(EffectAudioSpec.NONE, spec.audio());
        assertEquals(EffectPriority.NORMAL, spec.priority());
    }

    @Test
    void builderStillValidatesAuthoredValues() {
        assertThrows(IllegalArgumentException.class, () -> VisualEffectSpec.builder(
            EFFECT_ID, new Vec3(1.0, 64.0, 2.0), 100, 7L, EffectAudience.dimensionWide()
        ).scale(0.0F).build());
        assertThrows(IllegalArgumentException.class, () -> VisualEffectSpec.builder(
            EFFECT_ID, Vec3.ZERO, 0, 7L, EffectAudience.dimensionWide()
        ).build());
    }
}
