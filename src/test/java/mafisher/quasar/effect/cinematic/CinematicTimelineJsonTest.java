package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mafisher.quasar.api.cinematic.CinematicCue;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.scale.SegmentHeightSource;
import mafisher.quasar.api.scale.SegmentTerrainConform;
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
    void parsesLayeredAndTerrainConformingSegmentedCues() {
        CinematicTimeline timeline = parse("""
            {
              "duration_ticks": 20,
              "cues": [
                {
                  "type": "segmented",
                  "tick": 0,
                  "key": "field",
                  "effect": "quasar_content:rune_circle",
                  "center": [0, 64, 0],
                  "columns": 2,
                  "rows": 2,
                  "spacing": 4,
                  "duration_ticks": 10,
                  "audience": {"type": "nearby", "radius": 32},
                  "layers": 3,
                  "layer_spacing": 5,
                  "terrain": {"source": "world_surface", "offset": 1.5}
                }
              ]
            }
            """);

        CinematicCue.SegmentedStartCue cue = (CinematicCue.SegmentedStartCue)timeline.cues().getFirst();
        assertEquals(3, cue.spec().layers());
        assertEquals(5.0, cue.spec().layerSpacing());
        assertEquals(
            new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 1.5),
            cue.spec().terrain()
        );
    }

    @Test
    void rejectsUnknownTerrainSources() {
        assertThrows(RuntimeException.class, () -> parse("""
            {
              "duration_ticks": 20,
              "cues": [
                {
                  "type": "segmented",
                  "tick": 0,
                  "key": "field",
                  "effect": "quasar_content:rune_circle",
                  "center": [0, 64, 0],
                  "columns": 1,
                  "rows": 1,
                  "spacing": 1,
                  "duration_ticks": 10,
                  "audience": {"type": "nearby", "radius": 32},
                  "terrain": {"source": "moon_rocks"}
                }
              ]
            }
            """));
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
