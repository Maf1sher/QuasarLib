package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CinematicTimelineRegistryTest {
    private static final ResourceLocation VALID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "valid");
    private static final ResourceLocation INVALID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "invalid");

    @AfterEach
    void tearDown() {
        CinematicTimelineRegistry.clearForTesting();
    }

    @Test
    void loadsValidFilesAndSkipsBrokenOnes() {
        CinematicTimelineRegistry.reload(Map.of(
            VALID, json("""
                {
                  "duration_ticks": 100,
                  "cues": [
                    {
                      "type": "visual",
                      "tick": 0,
                      "key": "ring",
                      "effect": "quasar_content:rune_circle",
                      "position": [0, 0, 0],
                      "duration_ticks": 100,
                      "audience": {"type": "nearby", "radius": 64}
                    }
                  ]
                }
                """),
            INVALID, json("""
                {"duration_ticks": 100}
                """)
        ));

        assertEquals(1, CinematicTimelineRegistry.size());
        assertTrue(CinematicTimelineRegistry.find(VALID).isPresent());
        assertFalse(CinematicTimelineRegistry.find(INVALID).isPresent());
    }

    @Test
    void replaceIsAtomicAcrossReloads() {
        CinematicTimelineRegistry.reload(Map.of(VALID, valid()));
        assertTrue(CinematicTimelineRegistry.find(VALID).isPresent());

        CinematicTimelineRegistry.reload(Map.of());
        assertEquals(0, CinematicTimelineRegistry.size());
        assertFalse(CinematicTimelineRegistry.find(VALID).isPresent());
    }

    private static JsonElement valid() {
        return json("""
            {
              "duration_ticks": 100,
              "cues": [
                {
                  "type": "visual",
                  "tick": 0,
                  "key": "ring",
                  "effect": "quasar_content:rune_circle",
                  "position": [0, 0, 0],
                  "duration_ticks": 100,
                  "audience": {"type": "nearby", "radius": 64}
                }
              ]
            }
            """);
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
