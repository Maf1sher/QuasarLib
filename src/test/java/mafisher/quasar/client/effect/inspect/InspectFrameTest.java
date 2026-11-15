package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class InspectFrameTest {
    @Test
    void capsEverySectionWithoutAllocatingNewSlots() {
        InspectFrame frame = new InspectFrame();
        frame.begin(100L, 20L, 30L);
        for (int index = 0; index < InspectFrame.MAX_EFFECTS; index++) {
            assertNotNull(frame.addEffect());
        }
        assertNull(frame.addEffect());
        assertEquals(InspectFrame.MAX_EFFECTS, frame.effectCount());

        for (int index = 0; index < InspectFrame.MAX_DRAWS; index++) {
            assertNotNull(frame.addDraw());
        }
        assertNull(frame.addDraw());
        assertEquals(InspectFrame.MAX_DRAWS, frame.drawCount());
    }

    @Test
    void beginClearsLiveCountsButKeepsAudioAcrossNonRefreshFrames() {
        InspectFrame frame = new InspectFrame();
        frame.begin(1L, 2L, 3L);
        assertNotNull(frame.addEffect());
        assertNotNull(frame.addAudioPlan());
        assertNotNull(frame.addAudioCue());

        frame.begin(4L, 5L, 6L, false);

        assertEquals(0, frame.effectCount());
        assertEquals(0, frame.drawCount());
        assertEquals(1, frame.audioPlanCount());
        assertEquals(1, frame.audioCueCount());

        frame.begin(7L, 8L, 9L, true);
        assertEquals(0, frame.audioPlanCount());
        assertEquals(0, frame.audioCueCount());
    }

    @Test
    void slotsAreReused() {
        InspectFrame frame = new InspectFrame();
        frame.begin(0L, 0L, 1L);
        InspectFrame.EffectSlot first = frame.addEffect();
        assertSame(first, frame.effect(0));

        frame.begin(0L, 1L, 2L);
        assertSame(first, frame.addEffect());
    }

    @Test
    void countsAreStored() {
        InspectFrame frame = new InspectFrame();
        frame.counts(2, 3, 4, 5);

        assertEquals(2, frame.standaloneEffects());
        assertEquals(3, frame.segmentedGroups());
        assertEquals(4, frame.particleDraws());
        assertEquals(5, frame.debrisPieces());
    }
}
