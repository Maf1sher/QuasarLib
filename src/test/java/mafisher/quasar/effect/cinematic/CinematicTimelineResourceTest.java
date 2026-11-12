package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CinematicTimelineResourceTest {
    @Test
    void shippedRuneRiseTimelineParses() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
            "/data/quasar_content/quasar/cinematics/rune_rise.json"
        )) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            CinematicTimeline timeline = CinematicTimelineJson.parse(
                ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_rise"),
                root
            );

            assertEquals(240, timeline.durationTicks());
            assertEquals(CinematicAudioMode.CLIENT, timeline.audioMode());
            assertEquals(4, timeline.cueCount());
            assertEquals(1, timeline.tracks().size());
        }
    }
}
