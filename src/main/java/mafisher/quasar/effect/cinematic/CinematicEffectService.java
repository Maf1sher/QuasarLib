package mafisher.quasar.effect.cinematic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectHandle;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for cinematic timelines. A session executes each cue at most once, samples transform tracks,
 * and owns one {@link EffectScope}. The session budget is soft: a full level closes the lowest-priority timeline or
 * culls a strictly lower-priority newcomer.
 */
public final class CinematicEffectService {
    /** Default soft budget; it is a quality threshold, not a hard capability gate. */
    public static final int MAX_ACTIVE_SESSIONS_PER_LEVEL = 16;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveCinematic>> ACTIVE_SESSIONS = new WeakHashMap<>();
    private static Integer budgetOverride;

    private CinematicEffectService() {
    }

    /**
     * Starts a relative timeline at a world anchor. JSON-authored timelines use this overload; code-authored
     * timelines with absolute positions use {@link #start(ServerLevel, CinematicTimeline)}.
     */
    public static CinematicEffectHandle start(ServerLevel level, CinematicTimeline timeline, Vec3 anchor) {
        Objects.requireNonNull(timeline, "timeline");
        return start(level, timeline, anchor, EffectPersistenceOptions.DEFAULT);
    }

    public static CinematicEffectHandle start(
        ServerLevel level,
        CinematicTimeline timeline,
        Vec3 anchor,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(options, "options");
        CinematicTimeline anchored = timeline.anchored(anchor);
        return startInternal(
            level, anchored, anchor, UUID.randomUUID(), level.getGameTime(), options, null
        );
    }

    /**
     * Starts a timeline, executes its tick-zero cues immediately, and applies tick-zero transform tracks. When the
     * level budget is full, the lowest-priority cinematic is closed first; a lower-priority newcomer returns an
     * inactive handle instead of throwing.
     */
    public static CinematicEffectHandle start(ServerLevel level, CinematicTimeline timeline) {
        Objects.requireNonNull(timeline, "timeline");
        return start(level, timeline, Vec3.ZERO, EffectPersistenceOptions.DEFAULT);
    }

    public static CinematicEffectHandle start(
        ServerLevel level,
        CinematicTimeline timeline,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(timeline, "timeline");
        return start(level, timeline, Vec3.ZERO, options);
    }

    /** Restores a cinematic session and fast-forwards it to the current age without replaying past cues. */
    public static CinematicEffectHandle restore(
        ServerLevel level,
        CinematicTimeline timeline,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(descriptor, "descriptor");
        return startInternal(level, timeline,
            new Vec3(
                descriptor.transform().position().x(),
                descriptor.transform().position().y(),
                descriptor.transform().position().z()
            ),
            sessionId, startGameTime, EffectPersistenceOptions.DEFAULT, descriptor);
    }

    private static CinematicEffectHandle startInternal(
        ServerLevel level,
        CinematicTimeline timeline,
        Vec3 anchor,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options,
        EffectDescriptor restoredDescriptor
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        if (level.isClientSide()) {
            throw new IllegalStateException("Cinematic effects are server-authoritative");
        }

        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions != null && sessions.size() >= budget()
            && !admitByPriority(level, sessions, timeline.priority())) {
            EffectBudgetService.recordCull(level);
            LOGGER.info(
                "Culled cinematic {} in {}: its priority {} is below every active session",
                timeline.id(),
                level.dimension().location(),
                timeline.priority()
            );
            return new CinematicEffectHandle(sessionId);
        }

        EffectScope scope = new EffectScope(level, sessionId);
        EffectDescriptor descriptor = restoredDescriptor == null
            ? descriptor(level, timeline, anchor, sessionId, startGameTime, options)
            : restoredDescriptor;
        ActiveCinematic session = new ActiveCinematic(timeline, scope, level, startGameTime, descriptor);
        try {
            if (restoredDescriptor != null) {
                session.restoreTo(level, level.getGameTime());
            } else {
                session.advanceTo(level, level.getGameTime());
            }
        } catch (RuntimeException | Error exception) {
            session.close(level, false);
            if (sessions != null && sessions.isEmpty()) {
                ACTIVE_SESSIONS.remove(level);
            }
            throw exception;
        }

        if (!session.closed) {
            activeSessions(level).put(sessionId, session);
            EffectPersistenceService.recordStart(level, descriptor);
            if (session.audioPlan != null) {
                EffectAudioService.start(level, session.audioPlan, EffectAudience.dimensionWide());
            }
        }
        return new CinematicEffectHandle(sessionId);
    }

    private static EffectDescriptor descriptor(
        ServerLevel level,
        CinematicTimeline timeline,
        Vec3 anchor,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        net.minecraft.nbt.CompoundTag state = options.state();
        state.putBoolean("anchored", !anchor.equals(Vec3.ZERO));
        state.putDouble("anchor_x", anchor.x);
        state.putDouble("anchor_y", anchor.y);
        state.putDouble("anchor_z", anchor.z);
        return new EffectDescriptor(
            sessionId,
            EffectKind.CINEMATIC,
            timeline.id(),
            level.dimension().location(),
            new EffectTransform(new EffectPoint(anchor.x, anchor.y, anchor.z), 0.0F, 0.0F, 0.0F),
            1.0F,
            timeline.seed(),
            startGameTime,
            timeline.durationTicks(),
            mafisher.quasar.effect.EffectAudience.dimensionWide(),
            options.capabilities(),
            options.persistence(),
            null,
            state
        );
    }

    /** Advances one level's cinematic sessions. A failed session is logged, closed, and removed. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null || sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveCinematic>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveCinematic session = iterator.next().getValue();
            try {
                if (!session.advanceTo(level, gameTime)) {
                    iterator.remove();
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Cinematic session {} failed in {}", session.timeline.id(),
                    level.dimension().location(), exception);
                session.close(level, true);
                iterator.remove();
            }
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    /** Stops one session; unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, CinematicEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null) {
            return;
        }
        ActiveCinematic session = sessions.remove(handle.sessionId());
        if (session != null) {
            session.close(level, true);
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    /** Stops every cinematic session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        return stopAll(level, true);
    }

    /**
     * Stops every cinematic session in one level. Level unload and shutdown pass {@code false}
     * to preserve descriptors for restore.
     */
    public static int stopAll(ServerLevel level, boolean persistRemoval) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.remove(level);
        if (sessions == null) {
            return 0;
        }
        for (ActiveCinematic session : sessions.values()) {
            session.close(level, persistRemoval);
        }
        return sessions.size();
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        return sessions == null ? 0 : sessions.size();
    }

    /** Whether one cinematic session is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, CinematicEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        ActiveCinematic session = sessions == null ? null : sessions.get(handle.sessionId());
        return session != null
            && !session.closed
            && level.getGameTime() - session.startGameTime < session.timeline.durationTicks();
    }

    static void overrideBudgetForTesting(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("Cinematic budget must be positive");
        }
        budgetOverride = budget;
    }

    static void resetBudgetForTesting() {
        budgetOverride = null;
    }

    private static int budget() {
        return budgetOverride == null ? EffectBudgets.cinematicSessions() : budgetOverride;
    }

    /**
     * Makes room for a newcomer. Returns {@code false} when the newcomer is strictly lower priority than
     * every active session; otherwise the lowest-priority (oldest on ties) session is closed.
     */
    private static boolean admitByPriority(
        ServerLevel level,
        Map<UUID, ActiveCinematic> sessions,
        EffectPriority newcomerPriority
    ) {
        Map.Entry<UUID, ActiveCinematic> victim = null;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<UUID, ActiveCinematic> entry : sessions.entrySet()) {
            int weight = entry.getValue().timeline.priority().weight();
            if (weight < lowest) {
                lowest = weight;
                victim = entry;
            }
        }
        if (victim == null) {
            return true;
        }
        if (newcomerPriority.weight() < lowest) {
            return false;
        }
        UUID victimId = victim.getKey();
        ActiveCinematic victimSession = sessions.remove(victimId);
        if (victimSession != null) {
            victimSession.close(level, true);
        }
        EffectBudgetService.recordCull(level);
        LOGGER.info(
            "Closed cinematic {} in {} to admit a priority {} cinematic under the soft budget of {}",
            victimId,
            level.dimension().location(),
            newcomerPriority,
            budget()
        );
        return true;
    }

    private static Map<UUID, ActiveCinematic> activeSessions(ServerLevel level) {
        return ACTIVE_SESSIONS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static final class ActiveCinematic {
        private final CinematicTimeline timeline;
        private final EffectScope scope;
        private final long startGameTime;
        private final Map<String, UUID> visuals = new LinkedHashMap<>();
        private final Map<String, SegmentedVisualEffectHandle> segmented = new LinkedHashMap<>();
        private final EffectAudioPlan audioPlan;
        private EffectDescriptor descriptor;
        private int nextCueIndex;
        private int lastTransformAge = -1;
        private boolean closed;

        private ActiveCinematic(
            CinematicTimeline timeline,
            EffectScope scope,
            ServerLevel level,
            long startGameTime,
            EffectDescriptor descriptor
        ) {
            this.timeline = timeline;
            this.scope = scope;
            this.startGameTime = startGameTime;
            this.descriptor = descriptor;
            this.audioPlan = timeline.audioMode() == CinematicAudioMode.CLIENT
                ? buildAudioPlan(scope.sessionId(), timeline, level, startGameTime, descriptor)
                : null;
        }

        /**
         * Rebuilds only the visuals that should still be alive at the current age, then jumps the
         * cue cursor past everything already executed. Sounds and finished visuals are not replayed.
         */
        private void restoreTo(ServerLevel level, long gameTime) {
            if (this.closed) {
                return;
            }
            long rawAge = Math.max(0L, gameTime - this.startGameTime);
            int age = (int)Math.min(Integer.MAX_VALUE, rawAge);
            if (age >= this.timeline.durationTicks()) {
                this.close(level, true);
                return;
            }

            while (this.nextCueIndex < this.timeline.cues().size()
                && this.timeline.cues().get(this.nextCueIndex).tick() <= age) {
                restoreCue(level, this.timeline.cues().get(this.nextCueIndex++), age);
            }
            this.applyTracks(level, age);
            this.lastTransformAge = age;
        }

        private void restoreCue(ServerLevel level, CinematicCue cue, int age) {
            if (cue instanceof CinematicCue.VisualStartCue start) {
                if (start.tick() + start.spec().durationTicks() > age) {
                    UUID visualId = visualId(this.scope.sessionId(), start.key());
                    var handle = VisualEffectService.restore(
                        level,
                        start.spec(),
                        visualId,
                        this.startGameTime + start.tick(),
                        null
                    );
                    if (handle != null) {
                        this.scope.adoptVisual(visualId, handle);
                        this.visuals.put(start.key(), visualId);
                    }
                }
            } else if (cue instanceof CinematicCue.VisualStopCue stop) {
                UUID visualId = this.visuals.remove(stop.key());
                if (visualId != null) {
                    this.scope.stopVisual(visualId);
                }
            } else if (cue instanceof CinematicCue.SegmentedStartCue start) {
                if (start.tick() + start.spec().durationTicks() > age) {
                    UUID coordinatorId = segmentedId(this.scope.sessionId(), start.key());
                    SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.restore(
                        level,
                        start.spec(),
                        coordinatorId,
                        this.startGameTime + start.tick(),
                        null,
                        null,
                        EffectPersistenceOptions.cosmetic()
                    );
                    if (handle != null) {
                        this.segmented.put(start.key(), handle);
                    }
                }
            } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                SegmentedVisualEffectHandle handle = this.segmented.remove(stop.key());
                if (handle != null) {
                    SegmentedVisualEffectService.stop(level, handle);
                }
            }
            // Sound cues are intentionally skipped: they already happened before the restart.
        }

        /** Executes due cues and samples tracks. Returns false when the timeline has finished and closed. */
        private boolean advanceTo(ServerLevel level, long gameTime) {
            if (this.closed) {
                return false;
            }

            long rawAge = Math.max(0L, gameTime - this.startGameTime);
            int age = (int)Math.min(Integer.MAX_VALUE, rawAge);
            if (age >= this.timeline.durationTicks()) {
                this.close(level, true);
                return false;
            }
            age = Math.max(age, this.lastTransformAge);

            while (this.nextCueIndex < this.timeline.cues().size()
                && this.timeline.cues().get(this.nextCueIndex).tick() <= age) {
                this.executeCue(level, this.timeline.cues().get(this.nextCueIndex++));
            }

            if (this.lastTransformAge < 0
                || age - this.lastTransformAge >= this.timeline.transformUpdateIntervalTicks()) {
                this.applyTracks(level, age);
                this.lastTransformAge = age;
            }
            return true;
        }

        private void executeCue(ServerLevel level, CinematicCue cue) {
            if (cue instanceof CinematicCue.SoundCue sound) {
                if (this.timeline.audioMode() == CinematicAudioMode.SERVER) {
                    this.scope.playSound(
                        sound.position(), sound.sound(), sound.source(), sound.volume(), sound.pitch()
                    );
                }
            } else if (cue instanceof CinematicCue.VisualStartCue start) {
                UUID visualId = visualId(this.scope.sessionId(), start.key());
                var handle = VisualEffectService.startWithId(
                    level,
                    start.spec(),
                    visualId,
                    this.startGameTime + start.tick(),
                    EffectPersistenceOptions.cosmetic()
                );
                if (handle != null) {
                    this.scope.adoptVisual(visualId, handle);
                    this.visuals.put(start.key(), visualId);
                }
            } else if (cue instanceof CinematicCue.VisualStopCue stop) {
                UUID visualId = this.visuals.remove(stop.key());
                if (visualId != null) {
                    this.scope.stopVisual(visualId);
                }
            } else if (cue instanceof CinematicCue.SegmentedStartCue start) {
                UUID coordinatorId = segmentedId(this.scope.sessionId(), start.key());
                SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.startWithId(
                    level,
                    start.spec(),
                    coordinatorId,
                    this.startGameTime + start.tick(),
                    EffectPersistenceOptions.cosmetic()
                );
                if (handle == null) {
                    EffectBudgetService.recordCull(level);
                    LOGGER.warn("Skipped segmented cinematic cue {}: no coordinator was created", start.key());
                    return;
                }
                this.segmented.put(start.key(), handle);
            } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                SegmentedVisualEffectHandle handle = this.segmented.remove(stop.key());
                if (handle != null) {
                    SegmentedVisualEffectService.stop(level, handle);
                }
            }
        }

        private void applyTracks(ServerLevel level, int age) {
            for (CinematicTransformTrack track : this.timeline.tracks()) {
                UUID visualId = this.visuals.get(track.key());
                if (visualId != null) {
                    this.scope.updateVisual(visualId, track.sample(age));
                    continue;
                }
                SegmentedVisualEffectHandle segmentedHandle = this.segmented.get(track.key());
                if (segmentedHandle != null) {
                    SegmentedVisualEffectService.update(level, segmentedHandle, track.sample(age));
                }
            }
        }

        private void close(ServerLevel level, boolean persistRemoval) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.audioPlan != null) {
                EffectAudioService.stop(level, this.descriptor.instanceId());
            }
            for (SegmentedVisualEffectHandle handle : this.segmented.values()) {
                SegmentedVisualEffectService.stop(level, handle);
            }
            this.segmented.clear();
            this.visuals.clear();
            this.scope.close(persistRemoval);
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, this.descriptor.instanceId());
            }
        }
    }

    private static EffectAudioPlan buildAudioPlan(
        UUID sessionId,
        CinematicTimeline timeline,
        ServerLevel level,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        List<EffectAudioCue> cues = new ArrayList<>();
        for (CinematicCue cue : timeline.cues()) {
            if (cue instanceof CinematicCue.SoundCue sound) {
                cues.add(new EffectAudioCue(
                    sound.tick(),
                    sound.position(),
                    sound.sound().getLocation(),
                    sound.source(),
                    sound.volume(),
                    sound.pitch(),
                    EffectAudioAttenuation.VANILLA
                ));
            }
        }
        if (cues.isEmpty()) {
            return null;
        }
        EffectPoint center = descriptor.transform().position();
        return new EffectAudioPlan(
            sessionId,
            level.dimension().location(),
            new Vec3(center.x(), center.y(), center.z()),
            startGameTime,
            timeline.durationTicks(),
            cues
        );
    }

    /** Stable child visual ID derived from the session and timeline-local key. */
    private static UUID visualId(UUID sessionId, String key) {
        return UUID.nameUUIDFromBytes(
            ("quasar:visual:" + sessionId + ":" + key).getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Stable segmented coordinator ID derived from the session and timeline-local key. */
    private static UUID segmentedId(UUID sessionId, String key) {
        return UUID.nameUUIDFromBytes(
            ("quasar:segmented:" + sessionId + ":" + key).getBytes(StandardCharsets.UTF_8)
        );
    }
}
