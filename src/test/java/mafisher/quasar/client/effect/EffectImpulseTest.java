package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectImpulseTest {
    private static final Vec3 ORIGIN = new Vec3(0.0, 64.0, 0.0);

    @BeforeEach
    void clearImpulses() {
        ClientEffectImpulses.clear();
    }

    @Test
    void validatesShakeRanges() {
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            new Vec3(Double.NaN, 0.0, 0.0), 16.0, 1.0F, 20, 4.0F, 5.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            ORIGIN, 0.0, 1.0F, 20, 4.0F, 5.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            ORIGIN, 16.0, 1.1F, 20, 4.0F, 5.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            ORIGIN, 16.0, 1.0F, 0, 4.0F, 5.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            ORIGIN, 16.0, 1.0F, 20, 0.0F, 5.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraShakeImpulse(
            ORIGIN, 16.0, 1.0F, 20, 4.0F, 31.0F));
    }

    @Test
    void validatesFlashRanges() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenFlashImpulse(
            ORIGIN, -1.0, 1.0F, 20, 1.0F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new ScreenFlashImpulse(
            ORIGIN, 16.0, -0.1F, 20, 1.0F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new ScreenFlashImpulse(
            ORIGIN, 16.0, 1.0F, 20, 1.0F, 1.1F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new ScreenFlashImpulse(
            ORIGIN, 16.0, 1.0F, 72_001, 1.0F, 1.0F, 1.0F));
    }

    @Test
    void shakeAttenuatesWithDistance() {
        CameraShakeImpulse shake = new CameraShakeImpulse(ORIGIN, 32.0, 1.0F, 20, 4.0F, 10.0F);

        assertEquals(1.0F, shake.attenuationAt(ORIGIN), 0.0001F);
        assertEquals(0.5F, shake.attenuationAt(new Vec3(16.0, 64.0, 0.0)), 0.0001F);
        assertEquals(0.0F, shake.attenuationAt(new Vec3(32.0, 64.0, 0.0)), 0.0001F);
        assertEquals(0.0F, shake.attenuationAt(new Vec3(100.0, 64.0, 0.0)), 0.0001F);
    }

    @Test
    void shakeEnvelopeRisesAndDecays() {
        CameraShakeImpulse shake = new CameraShakeImpulse(ORIGIN, 32.0, 1.0F, 20, 4.0F, 10.0F);

        assertEquals(0.0F, shake.envelopeAt(0.0F), 0.0001F);
        assertTrue(shake.envelopeAt(1.0F) > 0.0F);
        assertTrue(shake.envelopeAt(10.0F) < shake.envelopeAt(1.0F));
        assertEquals(0.0F, shake.envelopeAt(20.0F), 0.0001F);
    }

    @Test
    void shakeOffsetsAreDeterministicAndBounded() {
        CameraShakeImpulse shake = new CameraShakeImpulse(ORIGIN, 32.0, 1.0F, 20, 4.0F, 10.0F);

        Vector3f first = shake.angleOffsetsDegrees(ORIGIN, 5.0F);
        Vector3f second = shake.angleOffsetsDegrees(ORIGIN, 5.0F);

        assertEquals(first, second);
        assertTrue(Math.abs(first.x) <= 10.0F + 0.0001F);
        assertTrue(Math.abs(first.y) <= 10.0F + 0.0001F);
        assertTrue(Math.abs(first.z) <= 10.0F + 0.0001F);
        assertEquals(0.0F, shake.angleOffsetsDegrees(new Vec3(100.0, 64.0, 0.0), 5.0F)
            .length(), 0.0001F);
    }

    @Test
    void flashAttenuatesAndDecays() {
        ScreenFlashImpulse flash = new ScreenFlashImpulse(
            ORIGIN, 16.0, 1.0F, 20, 1.0F, 0.5F, 0.25F);

        assertEquals(1.0F, flash.strengthAt(ORIGIN, 0.0F), 0.0001F);
        assertEquals(0.5F, flash.strengthAt(new Vec3(8.0, 64.0, 0.0), 0.0F), 0.0001F);
        assertEquals(0.0F, flash.strengthAt(new Vec3(16.0, 64.0, 0.0), 0.0F), 0.0001F);
        assertTrue(flash.strengthAt(ORIGIN, 10.0F) < flash.strengthAt(ORIGIN, 0.0F));
        assertEquals(0.0F, flash.strengthAt(ORIGIN, 20.0F), 0.0001F);
    }

    @Test
    void storeAgesAndDropsExpiredImpulses() {
        ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
            ORIGIN, 32.0, 1.0F, 2, 4.0F, 10.0F));
        ClientEffectImpulses.screenFlash(new ScreenFlashImpulse(
            ORIGIN, 32.0, 1.0F, 2, 1.0F, 1.0F, 1.0F));

        assertEquals(1, ClientEffectImpulses.activeShakeCount());
        assertEquals(1, ClientEffectImpulses.activeFlashCount());
        ClientEffectImpulses.tick();
        assertTrue(ClientEffectImpulses.hasShake());
        assertTrue(ClientEffectImpulses.hasFlash());
        ClientEffectImpulses.tick();
        assertEquals(0, ClientEffectImpulses.activeShakeCount());
        assertEquals(0, ClientEffectImpulses.activeFlashCount());
        assertFalse(ClientEffectImpulses.hasShake());
        assertFalse(ClientEffectImpulses.hasFlash());
    }

    @Test
    void storeDropsTheOldestImpulseWhenFull() {
        ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
            ORIGIN, 32.0, 1.0F, 1, 4.0F, 10.0F));
        for (int index = 0; index < ClientEffectImpulses.MAX_SHAKE_IMPULSES; index++) {
            ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
                ORIGIN, 32.0, 1.0F, 100, 4.0F, 10.0F));
        }

        assertEquals(ClientEffectImpulses.MAX_SHAKE_IMPULSES,
            ClientEffectImpulses.activeShakeCount());
        ClientEffectImpulses.tick();
        assertEquals(ClientEffectImpulses.MAX_SHAKE_IMPULSES,
            ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void storeSumsShakesAndClampsTheTotal() {
        for (int index = 0; index < 8; index++) {
            ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
                ORIGIN, 32.0, 1.0F, 100, 4.0F, 30.0F));
        }
        ClientEffectImpulses.tick();

        Vector3f offsets = ClientEffectImpulses.shakeOffsets(ORIGIN, 0.0F);
        assertTrue(Math.abs(offsets.x)
            <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES + 0.0001F);
        assertTrue(Math.abs(offsets.y)
            <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES + 0.0001F);
        assertTrue(Math.abs(offsets.z)
            <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES + 0.0001F);
    }

    @Test
    void storeBlendsFlashColorsAndClampsStrength() {
        ClientEffectImpulses.screenFlash(new ScreenFlashImpulse(
            ORIGIN, 32.0, 1.0F, 100, 1.0F, 0.0F, 0.0F));
        ClientEffectImpulses.screenFlash(new ScreenFlashImpulse(
            ORIGIN, 32.0, 1.0F, 100, 0.0F, 1.0F, 0.0F));

        ClientEffectImpulses.FlashSample sample =
            ClientEffectImpulses.sampleFlash(ORIGIN, 0.0F);
        assertEquals(1.0F, sample.strength(), 0.0001F);
        assertEquals(0.5F, sample.red(), 0.0001F);
        assertEquals(0.5F, sample.green(), 0.0001F);
        assertEquals(0.0F, sample.blue(), 0.0001F);
    }

    @Test
    void clearRemovesEverything() {
        ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
            ORIGIN, 32.0, 1.0F, 100, 4.0F, 10.0F));
        ClientEffectImpulses.screenFlash(new ScreenFlashImpulse(
            ORIGIN, 32.0, 1.0F, 100, 1.0F, 1.0F, 1.0F));

        ClientEffectImpulses.clear();

        assertEquals(0, ClientEffectImpulses.activeShakeCount());
        assertEquals(0, ClientEffectImpulses.activeFlashCount());
        assertEquals(0.0F, ClientEffectImpulses.sampleFlash(ORIGIN, 0.0F).strength(), 0.0001F);
    }
}
