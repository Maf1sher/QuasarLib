package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.audio.EffectAudioEnvelope;
import org.junit.jupiter.api.Test;

class EffectAudioEnvelopeTest {
    @Test
    void oneShotAlwaysHasFullGain() {
        assertEquals(1.0F, EffectAudioEnvelope.ONE_SHOT.gainAt(0, 100));
        assertEquals(1.0F, EffectAudioEnvelope.ONE_SHOT.gainAt(50, 100));
        assertEquals(1.0F, EffectAudioEnvelope.ONE_SHOT.gainAt(100, 100));
        assertTrue(EffectAudioEnvelope.ONE_SHOT.isValidFor(0, 100));
    }

    @Test
    void fadesInAndOutLinearly() {
        EffectAudioEnvelope envelope = EffectAudioEnvelope.loopUntil(100, 10, 20);

        assertEquals(0.0F, envelope.gainAt(0, 100));
        assertEquals(0.5F, envelope.gainAt(5, 100));
        assertEquals(1.0F, envelope.gainAt(10, 100));
        assertEquals(1.0F, envelope.gainAt(79, 100));
        assertEquals(1.0F, envelope.gainAt(80, 100));
        assertEquals(0.5F, envelope.gainAt(90, 100));
        assertEquals(0.0F, envelope.gainAt(100, 100));
    }

    @Test
    void overlappingFadesUseTheSmallerGain() {
        EffectAudioEnvelope envelope = EffectAudioEnvelope.loopUntil(10, 8, 8);

        assertEquals(0.5F, envelope.gainAt(4, 10));
        assertEquals(0.25F, envelope.gainAt(2, 10));
    }

    @Test
    void endOfEffectUsesThePlanDuration() {
        EffectAudioEnvelope envelope = EffectAudioEnvelope.loop(5, 5);

        assertEquals(100, envelope.stopTickOr(100));
        assertEquals(1.0F, envelope.gainAt(50, envelope.stopTickOr(100)));
        assertTrue(envelope.isValidFor(0, 100));
        assertFalse(envelope.isValidFor(0, 0));
    }

    @Test
    void rejectsInvalidWindows() {
        assertThrows(IllegalArgumentException.class, () -> new EffectAudioEnvelope(false, 1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectAudioEnvelope(true, -1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectAudioEnvelope(true, 0, 0, -2));
        assertThrows(IllegalArgumentException.class,
            () -> EffectAudioEnvelope.loopUntil(5, 0, 0).requireValidFor(5, 10));
        assertThrows(IllegalArgumentException.class,
            () -> EffectAudioEnvelope.loopUntil(20, 0, 0).requireValidFor(0, 10));
    }
}
