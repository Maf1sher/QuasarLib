package mafisher.quasar.api.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectTimeline;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable client-side sound schedule for one effect instance. Cues are sorted by tick and
 * capped so one plan payload stays bounded. {@code startGameTime} is absolute server game time,
 * which lets a late joiner skip cues that already happened. Trigger definitions are carried with
 * the plan so runtime events resolve without extra sound metadata on the wire.
 */
public final class EffectAudioPlan {
    public static final int MAX_CUES = EffectAudioSpec.MAX_CUES;
    public static final int MAX_TRIGGERS = EffectAudioSpec.MAX_TRIGGERS;
    public static final int MAX_DURATION_TICKS = EffectTimeline.MAX_DURATION_TICKS;
    /** Late-join and lag tolerance for cues that are already slightly in the past. */
    public static final int GRACE_TICKS = 2;

    private final UUID effectId;
    private final ResourceLocation dimensionId;
    private final Vec3 center;
    private final long startGameTime;
    private final int durationTicks;
    private final List<EffectAudioCue> cues;
    private final List<EffectAudioTrigger> triggers;
    private final Map<String, EffectAudioTrigger> triggersById;

    public EffectAudioPlan(
        UUID effectId,
        ResourceLocation dimensionId,
        Vec3 center,
        long startGameTime,
        int durationTicks,
        List<EffectAudioCue> cues
    ) {
        this(effectId, dimensionId, center, startGameTime, durationTicks, cues, List.of());
    }

    public EffectAudioPlan(
        UUID effectId,
        ResourceLocation dimensionId,
        Vec3 center,
        long startGameTime,
        int durationTicks,
        List<EffectAudioCue> cues,
        List<EffectAudioTrigger> triggers
    ) {
        this.effectId = Objects.requireNonNull(effectId, "effectId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.center = Objects.requireNonNull(center, "center");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            throw new IllegalArgumentException("Effect audio center must be finite");
        }
        if (!EffectTimeline.isValidDuration(durationTicks)) {
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
            cue.envelope().requireValidFor(cue.tick(), durationTicks);
        }
        sorted.sort(Comparator.comparingInt(EffectAudioCue::tick));
        this.cues = List.copyOf(sorted);

        Objects.requireNonNull(triggers, "triggers");
        if (triggers.size() > MAX_TRIGGERS) {
            throw new IllegalArgumentException(
                "An effect audio plan supports at most " + MAX_TRIGGERS + " triggers"
            );
        }
        List<EffectAudioTrigger> copiedTriggers = new ArrayList<>(triggers.size());
        Map<String, EffectAudioTrigger> byId = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (EffectAudioTrigger trigger : triggers) {
            Objects.requireNonNull(trigger, "trigger");
            if (!ids.add(trigger.id())) {
                throw new IllegalArgumentException("Duplicate effect audio trigger ID: " + trigger.id());
            }
            copiedTriggers.add(trigger);
            byId.put(trigger.id(), trigger);
        }
        this.triggers = List.copyOf(copiedTriggers);
        this.triggersById = Collections.unmodifiableMap(byId);
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
        return new EffectAudioPlan(
            effectId,
            dimensionId,
            center,
            startGameTime,
            durationTicks,
            spec.cues(),
            spec.triggers()
        );
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

    public List<EffectAudioTrigger> triggers() {
        return this.triggers;
    }

    public int triggerCount() {
        return this.triggers.size();
    }

    /** Returns the trigger declaration with {@code id}, or null when absent. */
    public EffectAudioTrigger triggerOrNull(String id) {
        return this.triggersById.get(Objects.requireNonNull(id, "id"));
    }

    /**
     * Index of the first cue that is still relevant at {@code ageTicks}: a due cue within the
     * grace window, or an active loop that started earlier and has not stopped yet.
     */
    public int firstCueIndex(int ageTicks, int graceTicks) {
        if (ageTicks < 0 || graceTicks < 0) {
            throw new IllegalArgumentException("Effect audio age and grace must be non-negative");
        }
        int earliest = Math.max(0, ageTicks - graceTicks);
        for (int index = 0; index < this.cues.size(); index++) {
            EffectAudioCue cue = this.cues.get(index);
            if (cue.envelope().looping()) {
                if (ageTicks < cue.envelope().stopTickOr(this.durationTicks)) {
                    return index;
                }
                continue;
            }
            if (cue.tick() >= earliest) {
                return index;
            }
        }
        return this.cues.size();
    }

    /** True when at least one cue or trigger can still play at {@code ageTicks}. */
    public boolean hasPlayableCues(int ageTicks, int graceTicks) {
        return (this.triggerCount() > 0 && ageTicks < this.durationTicks)
            || this.firstCueIndex(ageTicks, graceTicks) < this.cues.size();
    }
}
