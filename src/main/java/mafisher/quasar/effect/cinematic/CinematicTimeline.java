package mafisher.quasar.effect.cinematic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable cinematic plan: an ordered cue list plus optional transform tracks. Cue ticks and keyframes are relative
 * to the timeline start; the service never re-executes a cue and never replays already-finished cues to late joiners.
 */
public final class CinematicTimeline {
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final int MAX_CUES = 512;
    public static final int MAX_SOUND_CUES = 128;
    public static final int MAX_TRACKS = 128;
    public static final int MIN_TRANSFORM_UPDATE_INTERVAL_TICKS = 1;
    public static final int MAX_TRANSFORM_UPDATE_INTERVAL_TICKS = 20;

    private final ResourceLocation id;
    private final long seed;
    private final int durationTicks;
    private final int transformUpdateIntervalTicks;
    private final CinematicAudioMode audioMode;
    private final List<CinematicCue> cues;
    private final Map<String, CinematicTransformTrack> tracks;

    private CinematicTimeline(
        ResourceLocation id,
        long seed,
        int durationTicks,
        int transformUpdateIntervalTicks,
        CinematicAudioMode audioMode,
        List<CinematicCue> cues,
        Map<String, CinematicTransformTrack> tracks
    ) {
        this.id = id;
        this.seed = seed;
        this.durationTicks = durationTicks;
        this.transformUpdateIntervalTicks = transformUpdateIntervalTicks;
        this.audioMode = audioMode;
        this.cues = cues;
        this.tracks = tracks;
    }

    public static Builder builder(ResourceLocation id, int durationTicks, long seed) {
        return new Builder(id, durationTicks, seed);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public long seed() {
        return this.seed;
    }

    public int durationTicks() {
        return this.durationTicks;
    }

    public int transformUpdateIntervalTicks() {
        return this.transformUpdateIntervalTicks;
    }

    public CinematicAudioMode audioMode() {
        return this.audioMode;
    }

    /** Cues sorted by tick; cues with the same tick keep their builder insertion order. */
    public List<CinematicCue> cues() {
        return this.cues;
    }

    public Collection<CinematicTransformTrack> tracks() {
        return this.tracks.values();
    }

    public int cueCount() {
        return this.cues.size();
    }

    /**
     * Returns a copy whose cue and keyframe positions are offset by {@code anchor}. JSON timelines are authored
     * relative to the play anchor; callers with absolute positions can ignore this method.
     */
    public CinematicTimeline anchored(Vec3 anchor) {
        Objects.requireNonNull(anchor, "anchor");
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
            throw new IllegalArgumentException("Cinematic anchor must be finite");
        }
        if (anchor.equals(Vec3.ZERO)) {
            return this;
        }

        List<CinematicCue> translatedCues = new ArrayList<>(this.cues.size());
        for (CinematicCue cue : this.cues) {
            translatedCues.add(translate(cue, anchor));
        }
        Map<String, CinematicTransformTrack> translatedTracks = new LinkedHashMap<>();
        for (CinematicTransformTrack track : this.tracks.values()) {
            List<CinematicKeyframe> keyframes = new ArrayList<>(track.keyframes().size());
            for (CinematicKeyframe keyframe : track.keyframes()) {
                keyframes.add(new CinematicKeyframe(
                    keyframe.tick(),
                    translate(keyframe.transform(), anchor),
                    keyframe.easing()
                ));
            }
            translatedTracks.put(track.key(), new CinematicTransformTrack(track.key(), keyframes));
        }
        return new CinematicTimeline(
            this.id,
            this.seed,
            this.durationTicks,
            this.transformUpdateIntervalTicks,
            this.audioMode,
            List.copyOf(translatedCues),
            Collections.unmodifiableMap(translatedTracks)
        );
    }

    private static CinematicCue translate(CinematicCue cue, Vec3 anchor) {
        if (cue instanceof CinematicCue.SoundCue sound) {
            return new CinematicCue.SoundCue(
                sound.tick(),
                sound.position().add(anchor),
                sound.sound(),
                sound.source(),
                sound.volume(),
                sound.pitch()
            );
        }
        if (cue instanceof CinematicCue.VisualStartCue start) {
            VisualEffectSpec spec = start.spec();
            return new CinematicCue.VisualStartCue(
                start.tick(),
                start.key(),
                new VisualEffectSpec(
                    spec.effectId(),
                    spec.position().add(anchor),
                    spec.yaw(),
                    spec.pitch(),
                    spec.roll(),
                    spec.scale(),
                    spec.durationTicks(),
                    spec.seed(),
                    spec.audience()
                )
            );
        }
        if (cue instanceof CinematicCue.SegmentedStartCue start) {
            SegmentedVisualEffectSpec spec = start.spec();
            return new CinematicCue.SegmentedStartCue(
                start.tick(),
                start.key(),
                new SegmentedVisualEffectSpec(
                    spec.effectId(),
                    spec.center().add(anchor),
                    spec.yaw(),
                    spec.pitch(),
                    spec.roll(),
                    spec.segmentScale(),
                    spec.columns(),
                    spec.rows(),
                    spec.spacing(),
                    spec.durationTicks(),
                    spec.seed(),
                    spec.audience()
                )
            );
        }
        return cue;
    }

    private static EffectTransform translate(EffectTransform transform, Vec3 anchor) {
        EffectPoint position = transform.position();
        return new EffectTransform(
            new EffectPoint(
                position.x() + anchor.x,
                position.y() + anchor.y,
                position.z() + anchor.z
            ),
            transform.yaw(),
            transform.pitch(),
            transform.roll()
        );
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final int durationTicks;
        private final long seed;
        private final List<CinematicCue> cues = new ArrayList<>();
        private final Map<String, List<CinematicKeyframe>> trackKeyframes = new LinkedHashMap<>();
        private final Map<String, Integer> visualStarts = new LinkedHashMap<>();
        private final Map<String, Integer> segmentedStarts = new LinkedHashMap<>();
        private int transformUpdateIntervalTicks = 1;
        private CinematicAudioMode audioMode = CinematicAudioMode.SERVER;

        private Builder(ResourceLocation id, int durationTicks, long seed) {
            this.id = Objects.requireNonNull(id, "id");
            if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
                throw new IllegalArgumentException(
                    "A cinematic timeline duration must be between 1 and " + MAX_DURATION_TICKS + " ticks"
                );
            }
            this.durationTicks = durationTicks;
            this.seed = seed;
        }

        /** Sets how often transform tracks send updates. Higher values reduce packet fan-out and motion smoothness. */
        public Builder transformUpdateIntervalTicks(int intervalTicks) {
            if (intervalTicks < MIN_TRANSFORM_UPDATE_INTERVAL_TICKS
                || intervalTicks > MAX_TRANSFORM_UPDATE_INTERVAL_TICKS) {
                throw new IllegalArgumentException(
                    "Transform update interval must be between "
                        + MIN_TRANSFORM_UPDATE_INTERVAL_TICKS + " and " + MAX_TRANSFORM_UPDATE_INTERVAL_TICKS
                );
            }
            this.transformUpdateIntervalTicks = intervalTicks;
            return this;
        }

        /** Chooses whether sound cues play on the server or are scheduled once on the client. */
        public Builder audioMode(CinematicAudioMode audioMode) {
            this.audioMode = Objects.requireNonNull(audioMode, "audioMode");
            return this;
        }

        public Builder sound(
            int tick,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.addCue(new CinematicCue.SoundCue(tick, position, sound, source, volume, pitch));
        }

        public Builder visual(String key, int tick, VisualEffectSpec spec) {
            String validatedKey = CinematicCue.requireKey(key);
            requireAvailableKey(validatedKey);
            this.visualStarts.put(validatedKey, tick);
            return this.addCue(new CinematicCue.VisualStartCue(tick, validatedKey, spec));
        }

        public Builder stopVisual(String key, int tick) {
            return this.addCue(new CinematicCue.VisualStopCue(tick, key));
        }

        public Builder segmented(String key, int tick, SegmentedVisualEffectSpec spec) {
            String validatedKey = CinematicCue.requireKey(key);
            requireAvailableKey(validatedKey);
            this.segmentedStarts.put(validatedKey, tick);
            return this.addCue(new CinematicCue.SegmentedStartCue(tick, validatedKey, spec));
        }

        public Builder stopSegmented(String key, int tick) {
            return this.addCue(new CinematicCue.SegmentedStopCue(tick, key));
        }

        public Builder transform(String key, int tick, EffectTransform transform, EffectEasing easing) {
            String validatedKey = CinematicCue.requireKey(key);
            this.trackKeyframes.computeIfAbsent(validatedKey, ignored -> new ArrayList<>())
                .add(new CinematicKeyframe(tick, transform, easing));
            return this;
        }

        public CinematicTimeline build() {
            if (this.cues.isEmpty()) {
                throw new IllegalStateException("A cinematic timeline needs at least one cue");
            }
            if (this.cues.size() > MAX_CUES) {
                throw new IllegalStateException("A cinematic timeline supports at most " + MAX_CUES + " cues");
            }
            if (this.trackKeyframes.size() > MAX_TRACKS) {
                throw new IllegalStateException("A cinematic timeline supports at most " + MAX_TRACKS + " tracks");
            }

            List<CinematicCue> sortedCues = new ArrayList<>(this.cues);
            sortedCues.sort(Comparator.comparingInt(CinematicCue::tick));
            int soundCues = 0;
            Map<String, Integer> stopTicks = new LinkedHashMap<>();
            for (CinematicCue cue : sortedCues) {
                if (cue.tick() >= this.durationTicks) {
                    throw new IllegalArgumentException(
                        "Cinematic cue at tick " + cue.tick() + " must be before duration " + this.durationTicks
                    );
                }
                if (cue instanceof CinematicCue.SoundCue) {
                    soundCues++;
                } else if (cue instanceof CinematicCue.VisualStopCue stop) {
                    validateStop(stop.key(), stop.tick(), this.visualStarts, "visual", stopTicks);
                } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                    validateStop(stop.key(), stop.tick(), this.segmentedStarts, "segmented", stopTicks);
                }
            }
            if (soundCues > MAX_SOUND_CUES) {
                throw new IllegalStateException(
                    "A cinematic timeline supports at most " + MAX_SOUND_CUES + " sound cues"
                );
            }

            Map<String, CinematicTransformTrack> tracks = new LinkedHashMap<>();
            for (Map.Entry<String, List<CinematicKeyframe>> entry : this.trackKeyframes.entrySet()) {
                String key = entry.getKey();
                if (!this.visualStarts.containsKey(key) && !this.segmentedStarts.containsKey(key)) {
                    throw new IllegalArgumentException("Cinematic track targets an unknown key: " + key);
                }
                for (CinematicKeyframe keyframe : entry.getValue()) {
                    if (keyframe.tick() >= this.durationTicks) {
                        throw new IllegalArgumentException(
                            "Cinematic keyframe at tick " + keyframe.tick()
                                + " must be before duration " + this.durationTicks
                        );
                    }
                }
                tracks.put(key, new CinematicTransformTrack(key, entry.getValue()));
            }

            return new CinematicTimeline(
                this.id,
                this.seed,
                this.durationTicks,
                this.transformUpdateIntervalTicks,
                this.audioMode,
                List.copyOf(sortedCues),
                Collections.unmodifiableMap(tracks)
            );
        }

        private Builder addCue(CinematicCue cue) {
            if (this.cues.size() >= MAX_CUES) {
                throw new IllegalStateException("A cinematic timeline supports at most " + MAX_CUES + " cues");
            }
            this.cues.add(Objects.requireNonNull(cue, "cue"));
            return this;
        }

        private void requireAvailableKey(String key) {
            if (this.visualStarts.containsKey(key) || this.segmentedStarts.containsKey(key)) {
                throw new IllegalArgumentException("Cinematic key is already declared: " + key);
            }
        }

        private static void validateStop(
            String key,
            int tick,
            Map<String, Integer> starts,
            String kind,
            Map<String, Integer> stopTicks
        ) {
            Integer startTick = starts.get(key);
            if (startTick == null) {
                throw new IllegalArgumentException("Cinematic " + kind + " stop targets an unknown key: " + key);
            }
            if (stopTicks.containsKey(key)) {
                throw new IllegalArgumentException("Cinematic key is stopped more than once: " + key);
            }
            if (tick < startTick) {
                throw new IllegalArgumentException(
                    "Cinematic " + kind + " stop at tick " + tick + " is before its start at " + startTick
                );
            }
            stopTicks.put(key, tick);
        }
    }
}
