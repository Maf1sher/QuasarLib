package mafisher.quasar.effect.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicCue;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.cinematic.CinematicEffectStartResult;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.cinematic.CinematicTransformTrack;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.cinematic.CinematicEffectNbt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level cinematic session registry. A session executes each cue at most once, samples
 * transform tracks, and owns one {@link EffectScope}.
 */
@ApiStatus.Internal
public final class CinematicEffectRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CinematicEffectRuntime.class);

    private final ServerLevel level;
    private final EffectStore store;
    private final EffectBudgetSource budgets;
    private final VisualEffectRuntime visuals;
    private final SegmentedEffectRuntime segmented;
    private final EffectAudioRuntime audio;
    private final Map<UUID, ActiveCinematic> sessions = new LinkedHashMap<>();

    public CinematicEffectRuntime(
        ServerLevel level,
        EffectStore store,
        EffectBudgetSource budgets,
        VisualEffectRuntime visuals,
        SegmentedEffectRuntime segmented,
        EffectAudioRuntime audio
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.store = Objects.requireNonNull(store, "store");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.segmented = Objects.requireNonNull(segmented, "segmented");
        this.audio = Objects.requireNonNull(audio, "audio");
    }

    /**
     * Starts a relative timeline at a world anchor. JSON-authored timelines use this overload;
     * code-authored timelines with absolute positions use {@link #start(CinematicTimeline)}.
     */
    public CinematicEffectStartResult start(
        CinematicTimeline timeline,
        Vec3 anchor,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(options, "options");
        CinematicTimeline anchored = timeline.anchored(anchor);
        return startInternal(
            anchored, anchor, UUID.randomUUID(), this.level.getGameTime(), options, null
        );
    }

    public CinematicEffectStartResult start(CinematicTimeline timeline) {
        return start(timeline, Vec3.ZERO, EffectPersistenceOptions.DEFAULT);
    }

    /** Restores a cinematic session and fast-forwards it to the current age without replaying past cues. */
    public CinematicEffectStartResult restore(
        CinematicTimeline timeline,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(descriptor, "descriptor");
        Vec3 anchor = CinematicEffectNbt.anchorOrNull(descriptor);
        CinematicTimeline restoredTimeline = anchor == null ? timeline : timeline.anchored(anchor);
        return startInternal(restoredTimeline,
            new Vec3(
                descriptor.transform().position().x(),
                descriptor.transform().position().y(),
                descriptor.transform().position().z()
            ),
            sessionId, startGameTime, EffectPersistenceOptions.DEFAULT, descriptor);
    }

    private CinematicEffectStartResult startInternal(
        CinematicTimeline timeline,
        Vec3 anchor,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options,
        EffectDescriptor restoredDescriptor
    ) {
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Cinematic effects are server-authoritative");
        }

        if (this.sessions.size() >= budget() && !admitByPriority(this.sessions, timeline.priority())) {
            EffectBudgetService.recordCull(this.level);
            LOGGER.info(
                "Culled cinematic {} in {}: its priority {} is below every active session",
                timeline.id(),
                this.level.dimension().location(),
                timeline.priority()
            );
            return new CinematicEffectStartResult(
                new CinematicEffectHandle(sessionId),
                startStatus(restoredDescriptor, false)
            );
        }

        EffectScope scope = new EffectScope(this.level, sessionId);
        EffectDescriptor descriptor = restoredDescriptor == null
            ? descriptor(timeline, anchor, sessionId, startGameTime, options)
            : restoredDescriptor;
        ActiveCinematic session = new ActiveCinematic(
            timeline, scope, this.level, startGameTime, descriptor
        );
        try {
            if (restoredDescriptor != null) {
                session.restoreTo(this.level, this.level.getGameTime());
            } else {
                session.advanceTo(this.level, this.level.getGameTime());
            }
        } catch (RuntimeException | Error exception) {
            session.close(this.level, EffectRemovalPolicy.KEEP_FOR_RESTORE);
            throw exception;
        }

        if (!session.closed) {
            this.sessions.put(sessionId, session);
            this.store.recordStart(descriptor);
            if (session.audioPlan != null) {
                this.audio.start(
                    session.audioPlan, EffectAudience.dimensionWide(), session.timeline.priority()
                );
            }
        }
        return new CinematicEffectStartResult(
            new CinematicEffectHandle(sessionId),
            startStatus(restoredDescriptor, !session.closed)
        );
    }

    /** Advances one level's cinematic sessions. A failed session is logged, closed, and removed. */
    public void tick() {
        if (this.sessions.isEmpty()) {
            return;
        }

        long gameTime = this.level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveCinematic>> iterator = this.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveCinematic session = iterator.next().getValue();
            try {
                if (!session.advanceTo(this.level, gameTime)) {
                    iterator.remove();
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Cinematic session {} failed in {}", session.timeline.id(),
                    this.level.dimension().location(), exception);
                session.close(this.level, EffectRemovalPolicy.DELETE);
                iterator.remove();
            }
        }
    }

    /** Stops one session; unknown handles are no-ops and repeated stops are safe. */
    public void stop(CinematicEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveCinematic session = this.sessions.remove(handle.sessionId());
        if (session != null) {
            session.close(this.level, EffectRemovalPolicy.DELETE);
        }
    }

    /** Stops every cinematic session in one level and returns how many were active. */
    public int stopAll(EffectRemovalPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        int count = this.sessions.size();
        List<ActiveCinematic> active = new ArrayList<>(this.sessions.values());
        this.sessions.clear();
        for (ActiveCinematic session : active) {
            session.close(this.level, policy);
        }
        return count;
    }

    public int activeCount() {
        return this.sessions.size();
    }

    /** Whether one cinematic session is currently active; culled or finished handles return {@code false}. */
    public boolean isActive(CinematicEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveCinematic session = this.sessions.get(handle.sessionId());
        return session != null
            && !session.closed
            && !EffectTimeline.isFinished(
                this.level.getGameTime(), session.startGameTime, session.timeline.durationTicks()
            );
    }

    /** Drops transient session state without closing scopes or sending packets. */
    public void clear() {
        this.sessions.clear();
    }

    private int budget() {
        return this.budgets.cinematicSessions();
    }

    private static EffectStartStatus startStatus(
        EffectDescriptor restoredDescriptor,
        boolean active
    ) {
        if (restoredDescriptor == null) {
            return active ? EffectStartStatus.ACTIVE : EffectStartStatus.CULLED_ON_START;
        }
        return active ? EffectStartStatus.RESTORED_ACTIVE : EffectStartStatus.RESTORED_INACTIVE;
    }

    private EffectDescriptor descriptor(
        CinematicTimeline timeline,
        Vec3 anchor,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        net.minecraft.nbt.CompoundTag state = options.state();
        CinematicEffectNbt.saveAnchor(state, anchor);
        return EffectDescriptor.builder(
                sessionId, EffectKind.CINEMATIC, timeline.id(), this.level.dimension().location()
            )
            .placement(new EffectTransform(new EffectPoint(anchor.x, anchor.y, anchor.z), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(timeline.seed(), startGameTime, timeline.durationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(options.capabilities())
            .persistence(options.persistence())
            .state(state)
            .build();
    }

    /**
     * Makes room for a newcomer. Returns {@code false} when the newcomer is strictly lower priority
     * than every active session; otherwise the lowest-priority (oldest on ties) session is closed.
     */
    private boolean admitByPriority(
        Map<UUID, ActiveCinematic> activeSessions,
        EffectPriority newcomerPriority
    ) {
        UUID victimId = EffectBudgetService.lowestPriority(
            activeSessions, session -> session.timeline.priority().weight(), null
        );
        if (victimId == null) {
            return true;
        }
        ActiveCinematic victimSession = activeSessions.get(victimId);
        if (newcomerPriority.weight() < victimSession.timeline.priority().weight()) {
            return false;
        }
        activeSessions.remove(victimId);
        victimSession.close(this.level, EffectRemovalPolicy.DELETE);
        EffectBudgetService.recordCull(this.level);
        LOGGER.info(
            "Closed cinematic {} in {} to admit a priority {} cinematic under the soft budget of {}",
            victimId,
            this.level.dimension().location(),
            newcomerPriority,
            budget()
        );
        return true;
    }

    private final class ActiveCinematic {
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
            int age = EffectTimeline.ageTicks(gameTime, this.startGameTime);
            if (age >= this.timeline.durationTicks()) {
                this.close(level, EffectRemovalPolicy.DELETE);
                return;
            }

            while (this.nextCueIndex < this.timeline.cues().size()
                && this.timeline.cues().get(this.nextCueIndex).tick() <= age) {
                restoreCue(level, this.timeline.cues().get(this.nextCueIndex++), age);
            }
            this.applyTracks(age);
            this.lastTransformAge = age;
        }

        private void restoreCue(ServerLevel level, CinematicCue cue, int age) {
            if (cue instanceof CinematicCue.VisualStartCue start) {
                if (start.tick() + start.spec().durationTicks() > age) {
                    UUID visualId = visualId(this.scope.sessionId(), start.key());
                    var result = CinematicEffectRuntime.this.visuals.restore(
                        start.spec(),
                        visualId,
                        this.startGameTime + start.tick(),
                        null
                    );
                    if (result.active()) {
                        this.scope.adoptVisual(visualId, result.handle());
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
                    SegmentedVisualEffectHandle handle = CinematicEffectRuntime.this.segmented.restore(
                        start.spec(),
                        coordinatorId,
                        this.startGameTime + start.tick(),
                        null,
                        null,
                        EffectPersistenceOptions.cosmetic()
                    );
                    this.segmented.put(start.key(), handle);
                }
            } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                SegmentedVisualEffectHandle handle = this.segmented.remove(stop.key());
                if (handle != null) {
                    CinematicEffectRuntime.this.segmented.stop(handle);
                }
            }
            // Sound cues are intentionally skipped: they already happened before the restart.
        }

        /** Executes due cues and samples tracks. Returns false when the timeline has finished and closed. */
        private boolean advanceTo(ServerLevel level, long gameTime) {
            if (this.closed) {
                return false;
            }

            int age = EffectTimeline.ageTicks(gameTime, this.startGameTime);
            if (age >= this.timeline.durationTicks()) {
                this.close(level, EffectRemovalPolicy.DELETE);
                return false;
            }
            age = Math.max(age, this.lastTransformAge);

            while (this.nextCueIndex < this.timeline.cues().size()
                && this.timeline.cues().get(this.nextCueIndex).tick() <= age) {
                this.executeCue(level, this.timeline.cues().get(this.nextCueIndex++));
            }

            if (this.lastTransformAge < 0
                || age - this.lastTransformAge >= this.timeline.transformUpdateIntervalTicks()) {
                this.applyTracks(age);
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
                var result = CinematicEffectRuntime.this.visuals.startWithId(
                    start.spec(),
                    visualId,
                    this.startGameTime + start.tick(),
                    EffectPersistenceOptions.cosmetic(),
                    null
                );
                if (result.active()) {
                    this.scope.adoptVisual(visualId, result.handle());
                    this.visuals.put(start.key(), visualId);
                }
            } else if (cue instanceof CinematicCue.VisualStopCue stop) {
                UUID visualId = this.visuals.remove(stop.key());
                if (visualId != null) {
                    this.scope.stopVisual(visualId);
                }
            } else if (cue instanceof CinematicCue.SegmentedStartCue start) {
                UUID coordinatorId = segmentedId(this.scope.sessionId(), start.key());
                SegmentedVisualEffectHandle handle = CinematicEffectRuntime.this.segmented.startWithId(
                    start.spec(),
                    coordinatorId,
                    this.startGameTime + start.tick(),
                    EffectPersistenceOptions.cosmetic()
                );
                this.segmented.put(start.key(), handle);
            } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                SegmentedVisualEffectHandle handle = this.segmented.remove(stop.key());
                if (handle != null) {
                    CinematicEffectRuntime.this.segmented.stop(handle);
                }
            }
        }

        private void applyTracks(int age) {
            for (CinematicTransformTrack track : this.timeline.tracks()) {
                UUID visualId = this.visuals.get(track.key());
                if (visualId != null) {
                    this.scope.updateVisual(visualId, track.sample(age));
                    continue;
                }
                SegmentedVisualEffectHandle segmentedHandle = this.segmented.get(track.key());
                if (segmentedHandle != null) {
                    CinematicEffectRuntime.this.segmented.update(
                        segmentedHandle, track.sample(age)
                    );
                }
            }
        }

        private void close(ServerLevel level, EffectRemovalPolicy policy) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.audioPlan != null) {
                CinematicEffectRuntime.this.audio.stop(this.descriptor.instanceId());
            }
            for (SegmentedVisualEffectHandle handle : this.segmented.values()) {
                CinematicEffectRuntime.this.segmented.stop(handle);
            }
            this.segmented.clear();
            this.visuals.clear();
            this.scope.close(policy);
            if (policy.deletesDescriptor()) {
                CinematicEffectRuntime.this.store.recordStop(this.descriptor.instanceId());
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
