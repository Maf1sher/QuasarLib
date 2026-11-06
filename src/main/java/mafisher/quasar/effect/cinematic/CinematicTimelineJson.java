package mafisher.quasar.effect.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Locale;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

/** Parses a datapack JSON timeline into the runtime model. Positions are relative to the play anchor. */
final class CinematicTimelineJson {
    private CinematicTimelineJson() {
    }

    static CinematicTimeline parse(ResourceLocation id, JsonObject root) {
        int durationTicks = GsonHelper.getAsInt(root, "duration_ticks");
        long seed = GsonHelper.getAsLong(root, "seed", 0L);
        int updateInterval = GsonHelper.getAsInt(root, "transform_update_interval_ticks", 1);
        CinematicTimeline.Builder builder = CinematicTimeline.builder(id, durationTicks, seed)
            .transformUpdateIntervalTicks(updateInterval);

        JsonArray cues = GsonHelper.getAsJsonArray(root, "cues");
        if (cues.isEmpty()) {
            throw new JsonParseException("Cinematic " + id + " needs at least one cue");
        }
        for (JsonElement element : cues) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Cinematic " + id + " has a non-object cue");
            }
            parseCue(id, builder, element.getAsJsonObject());
        }

        if (root.has("tracks")) {
            JsonArray tracks = GsonHelper.getAsJsonArray(root, "tracks");
            for (JsonElement element : tracks) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Cinematic " + id + " has a non-object track");
                }
                parseTrack(id, builder, element.getAsJsonObject());
            }
        }
        return builder.build();
    }

    private static void parseCue(ResourceLocation id, CinematicTimeline.Builder builder, JsonObject cue) {
        String type = GsonHelper.getAsString(cue, "type");
        int tick = GsonHelper.getAsInt(cue, "tick");
        switch (type) {
            case "sound" -> builder.sound(
                tick,
                position(cue, "position"),
                sound(cue),
                soundSource(cue),
                GsonHelper.getAsFloat(cue, "volume", 1.0F),
                GsonHelper.getAsFloat(cue, "pitch", 1.0F)
            );
            case "visual" -> builder.visual(
                GsonHelper.getAsString(cue, "key"),
                tick,
                new VisualEffectSpec(
                    resourceLocation(cue, "effect"),
                    position(cue, "position"),
                    GsonHelper.getAsFloat(cue, "yaw", 0.0F),
                    GsonHelper.getAsFloat(cue, "pitch", 0.0F),
                    GsonHelper.getAsFloat(cue, "roll", 0.0F),
                    GsonHelper.getAsFloat(cue, "scale", 1.0F),
                    GsonHelper.getAsInt(cue, "duration_ticks"),
                    GsonHelper.getAsLong(cue, "seed", 0L),
                    audience(cue)
                )
            );
            case "visual_stop" -> builder.stopVisual(GsonHelper.getAsString(cue, "key"), tick);
            case "segmented" -> builder.segmented(
                GsonHelper.getAsString(cue, "key"),
                tick,
                new SegmentedVisualEffectSpec(
                    resourceLocation(cue, "effect"),
                    position(cue, "center"),
                    GsonHelper.getAsFloat(cue, "yaw", 0.0F),
                    GsonHelper.getAsFloat(cue, "pitch", 0.0F),
                    GsonHelper.getAsFloat(cue, "roll", 0.0F),
                    GsonHelper.getAsFloat(cue, "segment_scale", 1.0F),
                    GsonHelper.getAsInt(cue, "columns"),
                    GsonHelper.getAsInt(cue, "rows"),
                    GsonHelper.getAsDouble(cue, "spacing"),
                    GsonHelper.getAsInt(cue, "duration_ticks"),
                    GsonHelper.getAsLong(cue, "seed", 0L),
                    audience(cue)
                )
            );
            case "segmented_stop" -> builder.stopSegmented(GsonHelper.getAsString(cue, "key"), tick);
            default -> throw new JsonParseException("Cinematic " + id + " has an unknown cue type: " + type);
        }
    }

    private static void parseTrack(ResourceLocation id, CinematicTimeline.Builder builder, JsonObject track) {
        String key = GsonHelper.getAsString(track, "key");
        JsonArray keyframes = GsonHelper.getAsJsonArray(track, "keyframes");
        if (keyframes.isEmpty()) {
            throw new JsonParseException("Cinematic " + id + " has an empty track: " + key);
        }
        for (JsonElement element : keyframes) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Cinematic " + id + " has a non-object keyframe");
            }
            JsonObject keyframe = element.getAsJsonObject();
            builder.transform(
                key,
                GsonHelper.getAsInt(keyframe, "tick"),
                transform(keyframe),
                easing(keyframe)
            );
        }
    }

    private static EffectTransform transform(JsonObject json) {
        Vec3 position = position(json, "position");
        return new EffectTransform(
            new EffectPoint(position.x, position.y, position.z),
            GsonHelper.getAsFloat(json, "yaw", 0.0F),
            GsonHelper.getAsFloat(json, "pitch", 0.0F),
            GsonHelper.getAsFloat(json, "roll", 0.0F)
        );
    }

    private static EffectEasing easing(JsonObject json) {
        String value = GsonHelper.getAsString(json, "easing", "linear").toUpperCase(Locale.ROOT);
        try {
            return EffectEasing.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown cinematic easing: " + value, exception);
        }
    }

    private static SoundEvent sound(JsonObject json) {
        ResourceLocation id = resourceLocation(json, "sound");
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound == null) {
            throw new JsonParseException("Unknown cinematic sound event: " + id);
        }
        return sound;
    }

    private static SoundSource soundSource(JsonObject json) {
        String value = GsonHelper.getAsString(json, "source", "master").toUpperCase(Locale.ROOT);
        try {
            return SoundSource.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown cinematic sound source: " + value, exception);
        }
    }

    private static EffectAudience audience(JsonObject json) {
        JsonObject audience = GsonHelper.getAsJsonObject(json, "audience");
        String type = GsonHelper.getAsString(audience, "type");
        return switch (type) {
            case "nearby" -> EffectAudience.nearby(GsonHelper.getAsDouble(audience, "radius"));
            case "dimension_wide" -> EffectAudience.dimensionWide();
            default -> throw new JsonParseException("Unknown cinematic audience type: " + type);
        };
    }

    private static Vec3 position(JsonObject json, String name) {
        JsonArray array = GsonHelper.getAsJsonArray(json, name);
        if (array.size() != 3) {
            throw new JsonParseException("Cinematic position '" + name + "' must have exactly three components");
        }
        return new Vec3(
            GsonHelper.convertToDouble(array.get(0), name),
            GsonHelper.convertToDouble(array.get(1), name),
            GsonHelper.convertToDouble(array.get(2), name)
        );
    }

    private static ResourceLocation resourceLocation(JsonObject json, String name) {
        String value = GsonHelper.getAsString(json, name);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid cinematic resource location in '" + name + "': " + value);
        }
        return id;
    }
}
