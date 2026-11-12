package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectTimeline;
import org.junit.jupiter.api.Test;

class EffectTimelineTest {
    @Test
    void computesClampedAgeAndProgressFromWorldTime() {
        assertEquals(0.0, EffectTimeline.ageTicks(98L, 0.5F, 100L));
        assertEquals(5.5, EffectTimeline.ageTicks(105L, 0.5F, 100L));
        assertEquals(0.55F, EffectTimeline.progress(105L, 0.5F, 100L, 10), 1.0E-6F);
        assertEquals(1.0F, EffectTimeline.progress(120L, 0.0F, 100L, 10));
    }

    @Test
    void detectsNaturalExpiryAtTheConfiguredTick() {
        assertFalse(EffectTimeline.isFinished(109L, 100L, 10));
        assertTrue(EffectTimeline.isFinished(110L, 100L, 10));
    }

    @Test
    void clampsWholeAgeAndValidatesTheSharedDurationRange() {
        assertEquals(0, EffectTimeline.ageTicks(99L, 100L));
        assertEquals(0, EffectTimeline.ageTicks(100L, 100L));
        assertEquals(5, EffectTimeline.ageTicks(105L, 100L));
        assertEquals(Integer.MAX_VALUE, EffectTimeline.ageTicks(Long.MAX_VALUE, 0L));
        assertFalse(EffectTimeline.isValidDuration(0));
        assertTrue(EffectTimeline.isValidDuration(1));
        assertTrue(EffectTimeline.isValidDuration(EffectTimeline.MAX_DURATION_TICKS));
        assertFalse(EffectTimeline.isValidDuration(EffectTimeline.MAX_DURATION_TICKS + 1));
    }

    @Test
    void calculatesFadeInAndFadeOutEnvelope() {
        assertEquals(0.5F, EffectTimeline.fadeEnvelope(0.05F, 0.10F, 0.25F), 1.0E-6F);
        assertEquals(1.0F, EffectTimeline.fadeEnvelope(0.5F, 0.10F, 0.25F));
        assertEquals(0.4F, EffectTimeline.fadeEnvelope(0.9F, 0.10F, 0.25F), 1.0E-6F);
        assertEquals(0.0F, EffectTimeline.fadeEnvelope(1.0F, 0.10F, 0.25F));
    }

    @Test
    void easesInAndRejectsInvalidDurations() {
        assertEquals(0.0F, EffectTimeline.easeOutCubic(0.0F));
        assertEquals(0.875F, EffectTimeline.easeOutCubic(0.5F));
        assertEquals(1.0F, EffectTimeline.easeOutCubic(1.0F));
        assertThrows(IllegalArgumentException.class, () -> EffectTimeline.progress(0L, 0.0F, 0L, 0));
    }
}
