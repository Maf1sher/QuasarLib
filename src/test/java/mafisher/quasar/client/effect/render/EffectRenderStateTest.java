package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectRenderStateTest {
    @Test
    void forcesAPositiveCacheBustingViewportPair() {
        List<int[]> calls = new ArrayList<>();

        EffectRenderState.forceViewport(3, 4, 640, 360, (x, y, width, height) ->
            calls.add(new int[]{x, y, width, height}));

        assertEquals(2, calls.size());
        for (int[] call : calls) {
            assertEquals(3, call[0]);
            assertEquals(4, call[1]);
            assertEquals(640, call[2]);
            assertTrue(call[3] > 0);
        }
        assertTrue(calls.get(0)[3] != calls.get(1)[3]);
        assertEquals(360, calls.get(1)[3]);
    }

    @Test
    void keepsTheProbeHeightPositiveAtTheIntegerLimit() {
        List<int[]> calls = new ArrayList<>();

        EffectRenderState.forceViewport(0, 0, 1, Integer.MAX_VALUE, (x, y, width, height) ->
            calls.add(new int[]{x, y, width, height}));

        assertEquals(2, calls.size());
        assertTrue(calls.get(0)[3] > 0);
        assertEquals(Integer.MAX_VALUE, calls.get(1)[3]);
    }

    @Test
    void ignoresAnInvalidViewport() {
        List<int[]> calls = new ArrayList<>();

        EffectRenderState.forceViewport(0, 0, 0, 360, (x, y, width, height) ->
            calls.add(new int[]{x, y, width, height}));
        EffectRenderState.forceViewport(0, 0, 640, 0, (x, y, width, height) ->
            calls.add(new int[]{x, y, width, height}));

        assertTrue(calls.isEmpty());
    }
}
