package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** NBT codec for an effect's audio declaration. Stored in descriptor state, not in a separate save section. */
@ApiStatus.Internal
public final class EffectAudioNbt {
    /** Reserved engine key inside descriptor state. */
    public static final String KEY = "quasar_lib:audio";

    private EffectAudioNbt() {
    }

    public static void saveInto(CompoundTag parent, EffectAudioSpec spec) {
        if (spec == null || spec.isEmpty()) {
            parent.remove(KEY);
            return;
        }
        parent.put(KEY, save(spec));
    }

    public static EffectAudioSpec loadFrom(CompoundTag parent) {
        if (!parent.contains(KEY, Tag.TAG_COMPOUND)) {
            return EffectAudioSpec.NONE;
        }
        return load(parent.getCompound(KEY));
    }

    public static CompoundTag save(EffectAudioSpec spec) {
        CompoundTag tag = new CompoundTag();
        ListTag cues = new ListTag();
        for (EffectAudioCue cue : spec.cues()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("tick", cue.tick());
            entry.putDouble("x", cue.position().x);
            entry.putDouble("y", cue.position().y);
            entry.putDouble("z", cue.position().z);
            entry.putString("sound", cue.soundId().toString());
            entry.putString("source", cue.source().name());
            entry.putFloat("volume", cue.volume());
            entry.putFloat("pitch", cue.pitch());
            entry.put("attenuation", saveAttenuation(cue.attenuation()));
            entry.putBoolean("looping", cue.envelope().looping());
            entry.putInt("fade_in", cue.envelope().fadeInTicks());
            entry.putInt("fade_out", cue.envelope().fadeOutTicks());
            entry.putInt("stop_tick", cue.envelope().stopTick());
            entry.putString(
                "motion",
                cue.motion() instanceof EffectAudioMotion.FollowEffect ? "follow_effect" : "anchored"
            );
            cues.add(entry);
        }
        tag.put("cues", cues);

        ListTag triggers = new ListTag();
        for (EffectAudioTrigger trigger : spec.triggers()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", trigger.id());
            entry.putDouble("x", trigger.position().x);
            entry.putDouble("y", trigger.position().y);
            entry.putDouble("z", trigger.position().z);
            entry.putString("sound", trigger.soundId().toString());
            entry.putString("source", trigger.source().name());
            entry.putFloat("volume", trigger.volume());
            entry.putFloat("pitch", trigger.pitch());
            entry.put("attenuation", saveAttenuation(trigger.attenuation()));
            triggers.add(entry);
        }
        tag.put("triggers", triggers);
        return tag;
    }

    public static EffectAudioSpec load(CompoundTag tag) {
        try {
            ListTag stored = tag.getList("cues", Tag.TAG_COMPOUND);
            List<EffectAudioCue> cues = new ArrayList<>(stored.size());
            for (int index = 0; index < stored.size(); index++) {
                CompoundTag entry = stored.getCompound(index);
                ResourceLocation soundId = ResourceLocation.tryParse(entry.getString("sound"));
                if (soundId == null) {
                    throw new IllegalArgumentException("Invalid effect audio sound ID");
                }
                boolean looping = entry.getBoolean("looping");
                EffectAudioEnvelope envelope = looping
                    ? new EffectAudioEnvelope(
                        true,
                        entry.getInt("fade_in"),
                        entry.getInt("fade_out"),
                        entry.contains("stop_tick")
                            ? entry.getInt("stop_tick")
                            : EffectAudioEnvelope.END_OF_EFFECT
                    )
                    : EffectAudioEnvelope.ONE_SHOT;
                EffectAudioMotion motion = "follow_effect".equals(entry.getString("motion"))
                    ? EffectAudioMotion.FOLLOW_EFFECT
                    : EffectAudioMotion.ANCHORED;
                cues.add(new EffectAudioCue(
                    entry.getInt("tick"),
                    new Vec3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
                    soundId,
                    SoundSource.valueOf(entry.getString("source")),
                    entry.getFloat("volume"),
                    entry.getFloat("pitch"),
                    loadAttenuation(entry.getCompound("attenuation")),
                    envelope,
                    motion
                ));
            }

            ListTag storedTriggers = tag.getList("triggers", Tag.TAG_COMPOUND);
            List<EffectAudioTrigger> triggers = new ArrayList<>(storedTriggers.size());
            for (int index = 0; index < storedTriggers.size(); index++) {
                CompoundTag entry = storedTriggers.getCompound(index);
                ResourceLocation soundId = ResourceLocation.tryParse(entry.getString("sound"));
                if (soundId == null) {
                    throw new IllegalArgumentException("Invalid effect audio trigger sound ID");
                }
                triggers.add(new EffectAudioTrigger(
                    entry.getString("id"),
                    new Vec3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
                    soundId,
                    SoundSource.valueOf(entry.getString("source")),
                    entry.getFloat("volume"),
                    entry.getFloat("pitch"),
                    loadAttenuation(entry.getCompound("attenuation"))
                ));
            }
            return EffectAudioSpec.of(cues, triggers);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid effect audio data", exception);
        }
    }

    private static CompoundTag saveAttenuation(EffectAudioAttenuation attenuation) {
        CompoundTag tag = new CompoundTag();
        if (attenuation instanceof EffectAudioAttenuation.Vanilla) {
            tag.putString("type", "vanilla");
        } else if (attenuation instanceof EffectAudioAttenuation.Distance distance) {
            tag.putString("type", "distance");
            tag.putFloat("max_distance", distance.maxDistance());
        } else if (attenuation instanceof EffectAudioAttenuation.Directional directional) {
            tag.putString("type", "directional");
            tag.putDouble("direction_x", directional.direction().x);
            tag.putDouble("direction_y", directional.direction().y);
            tag.putDouble("direction_z", directional.direction().z);
            tag.putFloat("inner_angle", directional.innerAngleDegrees());
            tag.putFloat("outer_angle", directional.outerAngleDegrees());
            tag.putFloat("outside_gain", directional.outsideGain());
            tag.putFloat("max_distance", directional.maxDistance());
        } else {
            throw new IllegalArgumentException("Unknown effect audio attenuation type");
        }
        return tag;
    }

    private static EffectAudioAttenuation loadAttenuation(CompoundTag tag) {
        return switch (tag.getString("type")) {
            case "vanilla" -> EffectAudioAttenuation.VANILLA;
            case "distance" -> EffectAudioAttenuation.distance(tag.getFloat("max_distance"));
            case "directional" -> EffectAudioAttenuation.directional(
                new Vec3(
                    tag.getDouble("direction_x"),
                    tag.getDouble("direction_y"),
                    tag.getDouble("direction_z")
                ),
                tag.getFloat("inner_angle"),
                tag.getFloat("outer_angle"),
                tag.getFloat("outside_gain"),
                tag.getFloat("max_distance")
            );
            default -> throw new IllegalArgumentException("Unknown effect audio attenuation type");
        };
    }
}
