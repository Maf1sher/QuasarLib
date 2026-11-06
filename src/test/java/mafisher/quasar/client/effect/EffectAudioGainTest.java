package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EffectAudioGainTest {
    @Test
    void appliesLinearDistanceFalloff() {
        assertEquals(1.0F, EffectAudioGain.distanceGain(0.0, 64.0F));
        assertEquals(0.5F, EffectAudioGain.distanceGain(32.0, 64.0F));
        assertEquals(0.0F, EffectAudioGain.distanceGain(64.0, 64.0F));
        assertEquals(0.0F, EffectAudioGain.distanceGain(128.0, 64.0F));
        assertEquals(1.0F, EffectAudioGain.distanceGain(128.0, 0.0F));
    }

    @Test
    void appliesDirectionalConeGain() {
        assertEquals(1.0F, EffectAudioGain.directionGain(0.0, 30.0F, 90.0F, 0.25F));
        assertEquals(1.0F, EffectAudioGain.directionGain(30.0, 30.0F, 90.0F, 0.25F));
        assertEquals(0.25F, EffectAudioGain.directionGain(90.0, 30.0F, 90.0F, 0.25F));
        assertEquals(0.25F, EffectAudioGain.directionGain(180.0, 30.0F, 90.0F, 0.25F));
        assertEquals(0.625F, EffectAudioGain.directionGain(60.0, 30.0F, 90.0F, 0.25F), 1.0E-6F);
        assertEquals(0.25F, EffectAudioGain.directionGain(31.0, 30.0F, 30.0F, 0.25F));
    }

    @Test
    void combinesAndClampsGains() {
        assertEquals(0.25F, EffectAudioGain.combined(0.5F, 0.5F));
        assertEquals(0.0F, EffectAudioGain.combined(-1.0F, 1.0F));
        assertEquals(1.0F, EffectAudioGain.combined(2.0F, 2.0F));
    }
}
