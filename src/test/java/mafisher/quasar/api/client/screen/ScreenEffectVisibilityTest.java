package mafisher.quasar.api.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.shader.GlslConstants;
import org.junit.jupiter.api.Test;

class ScreenEffectVisibilityTest {
    private static final String INCLUDE =
        "quasar_lib:shaders/include/screen_visibility.glsl";

    @Test
    void layerScalesMatchTheShaderRange() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);
        Map<String, Double> constants = GlslConstants.load(INCLUDE);
        float factor = (float)GlslConstants.require(constants, "QUASAR_VISIBILITY_RANGE_FACTOR");

        for (ScreenEffectLayer layer : ScreenEffectLayer.values()) {
            assertEquals(factor, layer.rangeFactor(), 1.0E-6F);
            assertEquals(160.0F + factor * 10.0F, visibility.layerScale(layer), 1.0E-5F);
        }
        assertEquals(1.0F, new ScreenEffectVisibility(0.0F, 0.0F)
            .layerScale(ScreenEffectLayer.FILL), 1.0E-6F);
    }

    @Test
    void encodeDecodeRoundTripsNearAndFar() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);
        float scale = visibility.layerScale(ScreenEffectLayer.FILL);

        for (ScreenEffectLayer layer : ScreenEffectLayer.values()) {
            for (float entry : new float[] {0.0F, 0.5F, 1.0F, 10.0F, 100.0F,
                Math.nextDown(scale), scale, scale * 2.0F}) {
                float encoded = visibility.encode(layer, entry);
                assertTrue(encoded >= 0.0F && encoded <= 1.0F);
                float decoded = visibility.decode(layer, encoded);
                assertEquals(Math.min(entry, scale), decoded,
                    Math.max(0.01F, entry * 0.001F));
            }
        }
    }

    @Test
    void quantizedAlphaKeepsHorizonPrecision() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(320.0F, 40.0F);

        for (float entry : new float[] {1.0F, 10.0F, 100.0F, 300.0F, 400.0F}) {
            float encoded = Math.round(visibility.encode(ScreenEffectLayer.FILL, entry) * 255.0F)
                / 255.0F;
            float decoded = visibility.decode(ScreenEffectLayer.FILL, encoded);
            assertTrue(Math.abs(decoded - Math.min(entry, 400.0F)) <= Math.max(0.75F, entry * 0.01F),
                "entry " + entry + " decoded to " + decoded);
        }
    }

    @Test
    void encodeIsMonotonicForEveryLayer() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(120.0F, 12.0F);

        for (ScreenEffectLayer layer : ScreenEffectLayer.values()) {
            float previous = -1.0F;
            for (float entry = 0.0F; entry <= 160.0F; entry += 1.0F) {
                float encoded = visibility.encode(layer, entry);
                assertTrue(encoded >= previous, layer + " decreased at " + entry);
                previous = encoded;
            }
        }
    }

    @Test
    void opaqueDepthHidesOnlyPixelsInFront() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);

        assertFalse(visibility.visible(ScreenEffectLayer.FILL, 145.0F, 140.0F, 0.1F));
        assertTrue(visibility.visible(ScreenEffectLayer.FILL, 145.0F, 150.0F, 0.1F));
        assertTrue(visibility.visible(ScreenEffectLayer.FILL, 145.0F, 140.0F, 5.0F));
        assertTrue(visibility.visible(ScreenEffectLayer.SHADOW, 0.0F, 0.1F, 0.1F));
        assertFalse(visibility.visible(ScreenEffectLayer.EDGE, Float.NaN, 10.0F, 0.1F));
        assertFalse(visibility.visible(ScreenEffectLayer.EDGE, 10.0F, Float.NaN, 0.1F));
        assertTrue(visibility.visible(ScreenEffectLayer.EDGE, 10.0F,
            Float.POSITIVE_INFINITY, 0.1F));
    }

    @Test
    void foregroundGatesOnlyTheLensLayer() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);

        for (ScreenEffectLayer layer : ScreenEffectLayer.values()) {
            boolean visible = visibility.visible(
                layer, 145.0F, 200.0F, 100.0F, true, false, 0.1F);
            assertEquals(layer != ScreenEffectLayer.LENS, visible, layer.name());

            boolean deltaVisible = visibility.visible(
                layer, 145.0F, 200.0F, Float.NaN, false, true, 0.1F);
            assertEquals(layer != ScreenEffectLayer.LENS, deltaVisible, layer.name() + " delta");
        }
    }

    @Test
    void missingForegroundFallsBackToOpaqueDepthAndColorDelta() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);

        assertTrue(visibility.visible(ScreenEffectLayer.LENS, 145.0F, 200.0F,
            Float.NaN, false, false, 0.1F));
        assertFalse(visibility.visible(ScreenEffectLayer.LENS, 145.0F, 200.0F,
            Float.NaN, false, true, 0.1F));
        assertTrue(visibility.visible(ScreenEffectLayer.EMISSION, 145.0F, 200.0F,
            Float.NaN, false, true, 0.1F));
    }

    @Test
    void perPixelEntriesAreIndependent() {
        ScreenEffectVisibility visibility = new ScreenEffectVisibility(160.0F, 10.0F);

        assertFalse(visibility.visible(ScreenEffectLayer.FILL, 165.0F, 150.0F, 0.1F));
        assertTrue(visibility.visible(ScreenEffectLayer.FILL, 140.0F, 150.0F, 0.1F));
    }

    @Test
    void rejectsInvalidDistanceAndRadius() {
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectVisibility(-1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectVisibility(1.0F, -1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectVisibility(Float.NaN, 1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectVisibility(1.0F, Float.POSITIVE_INFINITY));
    }

    @Test
    void buildsDistanceFromTheSharedEffectSpace() {
        EffectSpace space = new EffectSpace(
            new EffectPoint(10.0, 20.0, 30.0),
            new EffectPoint(7.0, 16.0, 30.0),
            EffectSpace.Basis.fromEuler(0.0F, 0.0F, 0.0F),
            2.0F
        );

        ScreenEffectVisibility visibility = ScreenEffectVisibility.from(space, 4.0F);

        assertEquals(5.0F, visibility.distance(), 1.0E-5F);
        assertEquals(4.0F, visibility.radius(), 1.0E-6F);
    }
}
