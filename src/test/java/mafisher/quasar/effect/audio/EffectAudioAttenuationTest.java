package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioAttenuationTest {
    @Test
    void validatesDistanceAttenuation() {
        EffectAudioAttenuation.Distance distance = EffectAudioAttenuation.distance(64.0F);

        assertEquals(64.0F, distance.maxDistance());
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.distance(0.0F));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.distance(-1.0F));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.distance(Float.NaN));
    }

    @Test
    void normalizesDirectionalAttenuation() {
        EffectAudioAttenuation.Directional directional = EffectAudioAttenuation.directional(
            new Vec3(0.0, 0.0, 5.0),
            30.0F,
            90.0F,
            0.25F,
            64.0F
        );

        assertEquals(1.0, directional.direction().z, 1.0E-9);
        assertEquals(30.0F, directional.innerAngleDegrees());
        assertEquals(90.0F, directional.outerAngleDegrees());
        assertEquals(0.25F, directional.outsideGain());
    }

    @Test
    void rejectsInvalidDirectionalAttenuation() {
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.directional(
            Vec3.ZERO, 30.0F, 90.0F, 0.25F, 64.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.directional(
            new Vec3(0.0, 1.0, 0.0), 90.0F, 30.0F, 0.25F, 64.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.directional(
            new Vec3(0.0, 1.0, 0.0), 30.0F, 90.0F, 1.5F, 64.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioAttenuation.directional(
            new Vec3(0.0, 1.0, 0.0), 30.0F, 90.0F, 0.25F, -1.0F
        ));
    }

    @Test
    void exposesVanillaAttenuation() {
        assertInstanceOf(EffectAudioAttenuation.Vanilla.class, EffectAudioAttenuation.VANILLA);
    }
}
