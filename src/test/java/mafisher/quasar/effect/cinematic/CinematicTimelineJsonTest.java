package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CinematicTimelineJsonTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "json_test");

    @Test
    void parsesVisualStopAndTransformTrack() {
        CinematicTimeline timeline = parse("""
            {
              "duration_ticks": 200,
              "seed": 7,
              "transform_update_interval_ticks": 2,
              "cues": [
                {
                  "type": "visual",
                  "tick": 0,
                  "key": "ring",
                  "effect": "quasar_content:rune_circle",
                  "position": [1, 2, 3],
                  "yaw": 45,
                  "scale": 5,
                  "duration_ticks": 180,
                  "seed": 9,
                  "audience": {"type": "nearby", "radius": 64}
                },
                {"type": "visual_stop", "tick": 100, "key": "ring"}
              ],
              "tracks": [
                {
                  "key": "ring",
                  "keyframes": [
                    {"tick": 0, "position": [1, 2, 3], "easing": "ease_out_cubic"},
                    {"tick": 50, "position": [1, 5, 3], "yaw": 180, "easing": "linear"}
                  ]
                }
              ]
            }
            """);

        assertEquals(200, timeline.durationTicks());
        assertEquals(7L, timeline.seed());
        assertEquals(2, timeline.transformUpdateIntervalTicks());
        assertEquals(2, timeline.cueCount());
        assertTrue(timeline.cues().getFirst() instanceof CinematicCue.VisualStartCue);
        assertEquals(2, timeline.tracks().iterator().next().keyframes().size());
    }

    @Test
    void rejectsMissingCuesAndInvalidPositions() {
        assertThrows(RuntimeException.class, () -> parse("""
            {"duration_ticks": 20, "cues": []}
            """));
        assertThrows(RuntimeException.class, () -> parse("""
            {
              "duration_ticks": 20,
              "cues": [
                {
                  "type": "visual",
                  "tick": 0,
                  "key": "ring",
                  "effect": "quasar_content:rune_circle",
                  "position": [1, 2],
                  "duration_ticks": 10,
                  "audience": {"type": "nearby", "radius": 64}
                }
              ]
            }
            """));
    }

    @Test
    void rejectsUnknownTypesEasingAndStopTargets() {
        assertThrows(RuntimeException.class, () -> parse("""
            {"duration_ticks": 20, "cues": [{"type": "unknown", "tick": 0}]}
            """));
        assertThrows(RuntimeException.class, () -> parse("""
            {
              "duration_ticks": 20,
              "cues": [
                {
                  "type": "visual",
                  "tick": 0,
                  "key": "ring",
                  "effect": "quasar_content:rune_circle",
                  "position": [0, 0, 0],
                  "duration_ticks": 10,
                  "audience": {"type": "nearby", "radius": 64}
                }
              ],
              "tracks": [
                {"key": "ring", "keyframes": [{"tick": 0, "position": [0, 0, 0], "easing": "bogus"}]}
              ]
            }
            """));
        assertThrows(RuntimeException.class, () -> parse("""
            {"duration_ticks": 20, "cues": [{"type": "visual_stop", "tick": 0, "key": "ghost"}]}
            """));
    }

    private static CinematicTimeline parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return CinematicTimelineJson.parse(ID, root);
    }
}
