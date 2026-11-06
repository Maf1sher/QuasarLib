package mafisher.quasar.effect.cinematic;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.network.CinematicAudioPlanPayload;
import mafisher.quasar.effect.network.StopCinematicAudioPayload;
import mafisher.quasar.effect.scale.SegmentedVisualEffectHandle;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for cinematic timelines. A session executes each cue at most once, samples transform tracks,
 * and owns one {@link EffectScope}. Sessions are transient and never save.
 */
public final class CinematicEffectService {
    /** Provisional safety cap; it is not a measured balance budget. */
    public static final int MAX_ACTIVE_SESSIONS_PER_LEVEL = 16;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveCinematic>> ACTIVE_SESSIONS = new WeakHashMap<>();

    private CinematicEffectService() {
    }

    /**
     * Starts a relative timeline at a world anchor. JSON-authored timelines use this overload; code-authored
     * timelines with absolute positions use {@link #start(ServerLevel, CinematicTimeline)}.
     */
    public static CinematicEffectHandle start(ServerLevel level, CinematicTimeline timeline, Vec3 anchor) {
        Objects.requireNonNull(timeline, "timeline");
        return start(level, timeline.anchored(anchor));
    }

    /**
     * Starts a timeline, executes its tick-zero cues immediately, and applies tick-zero transform tracks. Start is
     * rejected instead of evicting another cinematic when the level cap is reached.
     */
    public static CinematicEffectHandle start(ServerLevel level, CinematicTimeline timeline) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        if (level.isClientSide()) {
            throw new IllegalStateException("Cinematic effects are server-authoritative");
        }

        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions != null && sessions.size() >= MAX_ACTIVE_SESSIONS_PER_LEVEL) {
            LOGGER.warn(
                "Rejected cinematic {} in {}: active session cap {} reached",
                timeline.id(),
                level.dimension().location(),
                MAX_ACTIVE_SESSIONS_PER_LEVEL
            );
            throw new IllegalStateException("Too many active cinematic effects in this level");
        }

        UUID sessionId = UUID.randomUUID();
        EffectScope scope = new EffectScope(level, sessionId);
        ActiveCinematic session = new ActiveCinematic(timeline, scope, level, level.getGameTime());
        try {
            session.advanceTo(level, level.getGameTime());
        } catch (RuntimeException | Error exception) {
            session.close(level);
            if (sessions != null && sessions.isEmpty()) {
                ACTIVE_SESSIONS.remove(level);
            }
            throw exception;
        }

        activeSessions(level).put(sessionId, session);
        return new CinematicEffectHandle(sessionId);
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
                session.close(level);
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
            session.close(level);
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    /** Stops every cinematic session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.remove(level);
        if (sessions == null) {
            return 0;
        }
        for (ActiveCinematic session : sessions.values()) {
            session.close(level);
        }
        return sessions.size();
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveCinematic> sessions = ACTIVE_SESSIONS.get(level);
        return sessions == null ? 0 : sessions.size();
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
        private final CinematicAudioPlan audioPlan;
        private final Set<UUID> audioRecipients = new HashSet<>();
        private int nextCueIndex;
        private int lastTransformAge = -1;
        private boolean closed;

        private ActiveCinematic(
            CinematicTimeline timeline,
            EffectScope scope,
            ServerLevel level,
            long startGameTime
        ) {
            this.timeline = timeline;
            this.scope = scope;
            this.startGameTime = startGameTime;
            this.audioPlan = timeline.audioMode() == CinematicAudioMode.CLIENT
                ? CinematicAudioPlan.from(scope.sessionId(), timeline, level.dimension().location(), startGameTime)
                : null;
        }

        /** Executes due cues and samples tracks. Returns false when the timeline has finished and closed. */
        private boolean advanceTo(ServerLevel level, long gameTime) {
            if (this.closed) {
                return false;
            }

            this.syncAudioAudience(level);
            long rawAge = Math.max(0L, gameTime - this.startGameTime);
            int age = (int)Math.min(Integer.MAX_VALUE, rawAge);
            if (age >= this.timeline.durationTicks()) {
                this.close(level);
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
                if (this.audioPlan == null) {
                    this.scope.playSound(
                        sound.position(), sound.sound(), sound.source(), sound.volume(), sound.pitch()
                    );
                }
            } else if (cue instanceof CinematicCue.VisualStartCue start) {
                this.visuals.put(start.key(), this.scope.playVisual(start.spec()));
            } else if (cue instanceof CinematicCue.VisualStopCue stop) {
                UUID visualId = this.visuals.remove(stop.key());
                if (visualId != null) {
                    this.scope.stopVisual(visualId);
                }
            } else if (cue instanceof CinematicCue.SegmentedStartCue start) {
                this.segmented.put(start.key(), SegmentedVisualEffectService.start(level, start.spec()));
            } else if (cue instanceof CinematicCue.SegmentedStopCue stop) {
                SegmentedVisualEffectHandle handle = this.segmented.remove(stop.key());
                if (handle != null) {
                    SegmentedVisualEffectService.stop(level, handle);
                }
            }
        }

        private void syncAudioAudience(ServerLevel level) {
            if (this.audioPlan == null || this.audioPlan.cueCount() == 0) {
                return;
            }
            Set<UUID> present = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                present.add(player.getUUID());
            }
            this.audioRecipients.retainAll(present);
            for (ServerPlayer player : level.players()) {
                if (this.audioRecipients.add(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, new CinematicAudioPlanPayload(this.audioPlan));
                }
            }
        }

        private void sendAudioStop(ServerLevel level) {
            if (this.audioPlan == null || this.audioRecipients.isEmpty()) {
                return;
            }
            if (level.getServer() != null) {
                StopCinematicAudioPayload payload = new StopCinematicAudioPayload(
                    this.audioPlan.sessionId(),
                    this.audioPlan.dimensionId()
                );
                for (UUID recipient : this.audioRecipients) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(recipient);
                    if (player != null && player.level() == level) {
                        PacketDistributor.sendToPlayer(player, payload);
                    }
                }
            }
            this.audioRecipients.clear();
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

        private void close(ServerLevel level) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.sendAudioStop(level);
            for (SegmentedVisualEffectHandle handle : this.segmented.values()) {
                SegmentedVisualEffectService.stop(level, handle);
            }
            this.segmented.clear();
            this.visuals.clear();
            this.scope.close();
        }
    }
}
