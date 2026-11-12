package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectSoundValidationTest {
    @Test
    void acceptsFinitePositionAndValidGainParameters() {
        assertDoesNotThrow(() -> EffectSoundValidation.requirePlayable(
            new Vec3(1.0, 2.0, 3.0), 0.0F, 0.1F, "invalid"
        ));
    }

    @Test
    void rejectsNonFinitePositionsVolumeAndPitch() {
        assertThrows(IllegalArgumentException.class, () -> EffectSoundValidation.requirePlayable(
            new Vec3(Double.NaN, 0.0, 0.0), 1.0F, 1.0F, "invalid"
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectSoundValidation.requirePlayable(
            Vec3.ZERO, Float.NaN, 1.0F, "invalid"
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectSoundValidation.requirePlayable(
            Vec3.ZERO, -1.0F, 1.0F, "invalid"
        ));
        assertThrows(IllegalArgumentException.class, () -> EffectSoundValidation.requirePlayable(
            Vec3.ZERO, 1.0F, 0.0F, "invalid"
        ));
    }
}
