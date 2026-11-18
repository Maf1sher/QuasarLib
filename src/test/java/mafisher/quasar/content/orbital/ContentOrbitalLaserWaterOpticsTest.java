package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import mafisher.quasar.api.client.screen.ScreenEffectMedium;
import mafisher.quasar.api.client.screen.ScreenEffectMediumPath;
import mafisher.quasar.api.client.screen.ScreenEffectMediumSpan;
import mafisher.quasar.shader.GlslConstants;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserWaterOpticsTest {
    private static final String OPTICS_INCLUDE =
        "quasar_content:shaders/include/orbital_laser_optics.glsl";
    private static final float EPSILON = 1.0E-6F;

    @Test
    void cpuMirrorMatchesTheShippedOpticsConstants() {
        Map<String, Double> constants = GlslConstants.load(OPTICS_INCLUDE);

        assertEquals(ContentOrbitalLaserWaterOptics.ABSORPTION,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_ABSORPTION"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.MIN_TRANSMISSION,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_MIN_TRANSMISSION"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.MAX_PATH,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_MAX_PATH"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.DEFAULT_PATH,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_DEFAULT_PATH"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.SURFACE_TRANSMISSION,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_SURFACE_TRANSMISSION"),
            EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.RIPPLE_STRENGTH,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_RIPPLE_STRENGTH"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.MAX_LUMINANCE,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_MAX_LUMINANCE"), EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.FOREGROUND_SCALE,
            GlslConstants.require(constants, "QUASAR_ORBITAL_FOREGROUND_EMISSION_SCALE"),
            EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.FOREGROUND_MAX_LUMINANCE,
            GlslConstants.require(constants, "QUASAR_ORBITAL_FOREGROUND_MAX_LUMINANCE"),
            EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.BLOOM_ABSORPTION,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_BLOOM_ABSORPTION"),
            EPSILON);
        assertEquals(ContentOrbitalLaserWaterOptics.BLOOM_FLOOR,
            GlslConstants.require(constants, "QUASAR_ORBITAL_WATER_BLOOM_FLOOR"), EPSILON);
    }

    @Test
    void waterPathSumsOnlyWaterSpans() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 4.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 4.0, 14.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.LAVA, 14.0, 18.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 18.0, 22.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 22.0, 30.0)
        ), List.of());

        assertEquals(14.0F, ContentOrbitalLaserWaterOptics.pathLength(path), EPSILON);
        assertEquals(0.0F, ContentOrbitalLaserWaterOptics.pathLength(
            new ScreenEffectMediumPath(List.of(
                new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0)
            ), List.of())), EPSILON);
    }

    @Test
    void transmissionFallsWithWaterDepthAndStopsAtTheFloor() {
        assertEquals(1.0F, ContentOrbitalLaserWaterOptics.transmission(0.0F), EPSILON);
        assertEquals(1.0F, ContentOrbitalLaserWaterOptics.transmission(-1.0F), EPSILON);
        assertEquals(1.0F, ContentOrbitalLaserWaterOptics.transmission(Float.NaN), EPSILON);

        float previous = 1.0F;
        for (float path = 0.5F; path <= 30.0F; path += 0.5F) {
            float transmission = ContentOrbitalLaserWaterOptics.transmission(path);
            assertTrue(transmission <= previous + EPSILON, "transmission rose at " + path);
            assertTrue(transmission >= ContentOrbitalLaserWaterOptics.MIN_TRANSMISSION
                - EPSILON, "transmission fell below the floor at " + path);
            previous = transmission;
        }
        assertEquals(ContentOrbitalLaserWaterOptics.MIN_TRANSMISSION,
            ContentOrbitalLaserWaterOptics.transmission(1_000.0F), EPSILON);
    }

    @Test
    void bloomScaleFallsWithWaterDepthButKeepsTheEffectReadable() {
        assertEquals(1.0F, ContentOrbitalLaserWaterOptics.bloomScale(0.0F), EPSILON);
        float shallow = ContentOrbitalLaserWaterOptics.bloomScale(4.0F);
        float deep = ContentOrbitalLaserWaterOptics.bloomScale(30.0F);

        assertTrue(shallow < 1.0F);
        assertTrue(deep <= shallow);
        assertTrue(deep >= ContentOrbitalLaserWaterOptics.BLOOM_FLOOR - EPSILON);
    }
}
