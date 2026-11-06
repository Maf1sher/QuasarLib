package mafisher.quasar.effect.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable client-side sound schedule for one effect instance. Cues are sorted by tick and
 * capped so one plan payload stays bounded. {@code startGameTime} is absolute server game time,
 * which lets a late joiner skip cues that already happened.
 */
public final class EffectAudioPlan {
    public static final int MAX_CUES = EffectAudioSpec.MAX_CUES;
    public static final int MAX_DURATION_TICKS = 72_000;
    /** Late-join and lag tolerance for cues that are already slightly in the past. */
    public static final int GRACE_TICKS = 2;

    private final UUID effectId;
    private final ResourceLocation dimensionId;
    private final Vec3 center;
    private final long startGameTime;
    private final int durationTicks;
    private final List<EffectAudioCue> cues;

    public EffectAudioPlan(
        UUID effectId,
        ResourceLocation dimensionId,
        Vec3 center,
        long startGameTime,
        int durationTicks,
        List<EffectAudioCue> cues
    ) {
        this.effectId = Objects.requireNonNull(effectId, "effectId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.center = Objects.requireNonNull(center, "center");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            throw new IllegalArgumentException("Effect audio center must be finite");
        }
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Invalid effect audio plan duration");
        }
        this.startGameTime = startGameTime;
        this.durationTicks = durationTicks;

        Objects.requireNonNull(cues, "cues");
        if (cues.size() > MAX_CUES) {
            throw new IllegalArgumentException("An effect audio plan supports at most " + MAX_CUES + " cues");
        }
        List<EffectAudioCue> sorted = new ArrayList<>(cues);
        for (EffectAudioCue cue : sorted) {
            Objects.requireNonNull(cue, "cue");
            if (cue.tick() >= durationTicks) {
                throw new IllegalArgumentException(
                    "Effect audio cue at tick " + cue.tick() + " must be before duration " + durationTicks
                );
            }
        }
        sorted.sort(Comparator.comparingInt(EffectAudioCue::tick));
        this.cues = List.copyOf(sorted);
    }

    public static EffectAudioPlan from(
        UUID effectId,
        ResourceLocation dimensionId,
        Vec3 center,
        long startGameTime,
        int durationTicks,
        EffectAudioSpec spec
    ) {
        Objects.requireNonNull(spec, "spec");
        return new EffectAudioPlan(effectId, dimensionId, center, startGameTime, durationTicks, spec.cues());
    }

    public UUID effectId() {
        return this.effectId;
    }

    public ResourceLocation dimensionId() {
        return this.dimensionId;
    }

    public Vec3 center() {
        return this.center;
    }

    public long startGameTime() {
        return this.startGameTime;
    }

    public int durationTicks() {
        return this.durationTicks;
    }

    public List<EffectAudioCue> cues() {
        return this.cues;
    }

    public int cueCount() {
        return this.cues.size();
    }

    /** Index of the first cue that is still close enough to play; older cues are skipped. */
    public int firstCueIndex(int ageTicks, int graceTicks) {
        if (ageTicks < 0 || graceTicks < 0) {
            throw new IllegalArgumentException("Effect audio age and grace must be non-negative");
        }
        int earliest = Math.max(0, ageTicks - graceTicks);
        for (int index = 0; index < this.cues.size(); index++) {
            if (this.cues.get(index).tick() >= earliest) {
                return index;
            }
        }
        return this.cues.size();
    }

    /** True when at least one cue can still be played at {@code ageTicks} within the grace window. */
    public boolean hasPlayableCues(int ageTicks, int graceTicks) {
        return this.firstCueIndex(ageTicks, graceTicks) < this.cues.size();
    }
}
