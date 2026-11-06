package mafisher.quasar.effect.cinematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable client-side sound schedule for one cinematic session. Cues are sorted by tick and capped so the start
 * payload stays bounded.
 */
public final class CinematicAudioPlan {
    public static final int MAX_CUES = 128;

    private final UUID sessionId;
    private final ResourceLocation timelineId;
    private final ResourceLocation dimensionId;
    private final long startGameTime;
    private final int durationTicks;
    private final List<CinematicAudioCue> cues;

    public CinematicAudioPlan(
        UUID sessionId,
        ResourceLocation timelineId,
        ResourceLocation dimensionId,
        long startGameTime,
        int durationTicks,
        List<CinematicAudioCue> cues
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.timelineId = Objects.requireNonNull(timelineId, "timelineId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        if (durationTicks < 1 || durationTicks > CinematicTimeline.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Invalid cinematic audio plan duration");
        }
        this.startGameTime = startGameTime;
        this.durationTicks = durationTicks;

        Objects.requireNonNull(cues, "cues");
        if (cues.size() > MAX_CUES) {
            throw new IllegalArgumentException("A cinematic audio plan supports at most " + MAX_CUES + " cues");
        }
        List<CinematicAudioCue> sorted = new ArrayList<>(cues);
        for (CinematicAudioCue cue : sorted) {
            Objects.requireNonNull(cue, "cue");
        }
        sorted.sort(Comparator.comparingInt(CinematicAudioCue::tick));
        this.cues = List.copyOf(sorted);
    }

    public static CinematicAudioPlan from(
        UUID sessionId,
        CinematicTimeline timeline,
        ResourceLocation dimensionId,
        long startGameTime
    ) {
        List<CinematicAudioCue> cues = new ArrayList<>();
        for (CinematicCue cue : timeline.cues()) {
            if (cue instanceof CinematicCue.SoundCue sound) {
                cues.add(new CinematicAudioCue(
                    sound.tick(),
                    sound.position(),
                    sound.sound().getLocation(),
                    sound.source(),
                    sound.volume(),
                    sound.pitch()
                ));
            }
        }
        return new CinematicAudioPlan(
            sessionId,
            timeline.id(),
            dimensionId,
            startGameTime,
            timeline.durationTicks(),
            cues
        );
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public ResourceLocation timelineId() {
        return this.timelineId;
    }

    public ResourceLocation dimensionId() {
        return this.dimensionId;
    }

    public long startGameTime() {
        return this.startGameTime;
    }

    public int durationTicks() {
        return this.durationTicks;
    }

    public List<CinematicAudioCue> cues() {
        return this.cues;
    }

    public int cueCount() {
        return this.cues.size();
    }

    /** Index of the first cue that is still close enough to play; older cues are skipped. */
    public int firstCueIndex(int ageTicks, int graceTicks) {
        if (ageTicks < 0 || graceTicks < 0) {
            throw new IllegalArgumentException("Cinematic audio age and grace must be non-negative");
        }
        int earliest = Math.max(0, ageTicks - graceTicks);
        for (int index = 0; index < this.cues.size(); index++) {
            if (this.cues.get(index).tick() >= earliest) {
                return index;
            }
        }
        return this.cues.size();
    }
}
