package mafisher.quasar.effect.cinematic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side registry of datapack cinematic timelines, replaced atomically on resource reload. */
public final class CinematicTimelineRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static volatile Map<ResourceLocation, CinematicTimeline> timelines = Map.of();

    private CinematicTimelineRegistry() {
    }

    public static Optional<CinematicTimeline> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(timelines.get(id));
    }

    public static Set<ResourceLocation> ids() {
        return timelines.keySet();
    }

    public static int size() {
        return timelines.size();
    }

    static void reload(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, CinematicTimeline> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.error("Could not load cinematic {}: root must be a JSON object", entry.getKey());
                continue;
            }
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                parsed.put(entry.getKey(), CinematicTimelineJson.parse(entry.getKey(), root));
            } catch (RuntimeException exception) {
                LOGGER.error("Could not load cinematic {}", entry.getKey(), exception);
            }
        }
        timelines = Map.copyOf(parsed);
    }

    static void clearForTesting() {
        timelines = Map.of();
    }
}
