package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientEffectClockTest {
    @BeforeEach
    void resetBefore() {
        ClientEffectClock.reset();
    }

    @AfterEach
    void resetAfter() {
        ClientEffectClock.reset();
    }

    @Test
    void acceptsServerTimestampsWhileTheClientClockRunsAhead() {
        ClientEffectClock.observe(100L, 200L);

        assertEquals(100L, ClientEffectClock.estimate(200L));
        assertEquals(105L, ClientEffectClock.estimate(205L));
    }

    @Test
    void acceptsServerTimestampsWhileTheClientClockRunsBehind() {
        ClientEffectClock.observe(1_000L, 400L);

        assertEquals(1_000L, ClientEffectClock.estimate(400L));
        assertEquals(1_010L, ClientEffectClock.estimate(410L));
    }

    @Test
    void neverMovesTheTimelineBackwardsAfterAClientClockCorrection() {
        ClientEffectClock.observe(100L, 5L);
        assertEquals(100L, ClientEffectClock.estimate(5L));

        // The client clock jumps far ahead of the server; the estimate must not rewind.
        ClientEffectClock.observe(100L, 300L);
        assertEquals(100L, ClientEffectClock.estimate(300L));

        assertEquals(105L, ClientEffectClock.estimate(305L));
    }

    @Test
    void usesClientTimeUntilTheFirstServerTimestampArrives() {
        assertEquals(500L, ClientEffectClock.estimate(500L));

        ClientEffectClock.observe(1_000L, 500L);

        assertEquals(1_000L, ClientEffectClock.estimate(500L));
    }

    @Test
    void resetClearsTheSampledOffset() {
        ClientEffectClock.observe(1_000L, 500L);
        ClientEffectClock.reset();

        assertEquals(900L, ClientEffectClock.estimate(900L));
        assertTrue(ClientEffectClock.estimate(901L) >= 900L);
    }
}
