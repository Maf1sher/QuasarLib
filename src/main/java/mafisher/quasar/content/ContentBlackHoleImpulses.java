package mafisher.quasar.content;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.QuasarClientConfig;
import mafisher.quasar.client.effect.CameraShakeImpulse;
import mafisher.quasar.client.effect.ClientEffectImpulses;
import mafisher.quasar.client.effect.EffectRenderContext;
import net.minecraft.world.phys.Vec3;

/**
 * Content-owned cinematic impulse beats. Impulses are driven by effect progress
 * rather than network payloads, so they stay a pure content concern.
 */
final class ContentBlackHoleImpulses {
    private static final double SHAKE_RADIUS = 512.0;
    private static final int MAX_TRACKED = 64;
    private static final Milestone[] MILESTONES = {
        new Milestone(ContentBlackHoleCinematics.IGNITION, Beat.IGNITION),
        new Milestone(ContentBlackHoleCinematics.IGNITION + 0.010F, Beat.SHOCKWAVE),
        new Milestone(ContentBlackHoleCinematics.DESTABILISE + 0.010F, Beat.COLLAPSE),
        new Milestone(ContentBlackHoleCinematics.IMPLOSION, Beat.IMPLOSION)
    };
    private static final Map<UUID, Tracker> TRACKERS = new LinkedHashMap<>();

    private ContentBlackHoleImpulses() {
    }

    static void tick(EffectRenderContext context) {
        if (!QuasarClientConfig.cinematicImpulses()) {
            return;
        }
        UUID instanceId = context.effect().instanceId();
        float progress = context.progress();
        Tracker tracker = TRACKERS.get(instanceId);
        if (tracker == null) {
            if (TRACKERS.size() >= MAX_TRACKED) {
                Iterator<Map.Entry<UUID, Tracker>> iterator = TRACKERS.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
            TRACKERS.put(instanceId, new Tracker(progress));
            return;
        }
        if (progress < tracker.lastProgress - 0.05F) {
            tracker.reset(progress);
            return;
        }
        Vec3 position = new Vec3(
            context.transform().position().x(),
            context.transform().position().y(),
            context.transform().position().z()
        );
        while (tracker.next < MILESTONES.length
            && progress >= MILESTONES[tracker.next].progress()) {
            fire(position, MILESTONES[tracker.next].beat());
            tracker.next++;
        }
        tracker.lastProgress = progress;
    }

    static void clear() {
        TRACKERS.clear();
    }

    private static void fire(Vec3 position, Beat beat) {
        switch (beat) {
            case IGNITION -> shake(position, 0.28F, 20, 0.60F, 1.0F);
            case SHOCKWAVE -> shake(position, 0.14F, 30, 0.90F, 0.6F);
            case COLLAPSE -> shake(position, 0.22F, 45, 0.45F, 0.7F);
            case IMPLOSION -> shake(position, 0.55F, 18, 1.80F, 1.8F);
            default -> throw new IllegalStateException("Unknown black-hole beat: " + beat);
        }
    }

    private static void shake(Vec3 position, float intensity, int durationTicks,
        float frequencyHz, float maxAngleDegrees) {
        ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
            position, SHAKE_RADIUS, intensity, durationTicks, frequencyHz, maxAngleDegrees
        ));
    }

    private static int firstMilestoneAfter(float progress) {
        for (int index = 0; index < MILESTONES.length; index++) {
            if (MILESTONES[index].progress() > progress) {
                return index;
            }
        }
        return MILESTONES.length;
    }

    private enum Beat {
        IGNITION,
        SHOCKWAVE,
        COLLAPSE,
        IMPLOSION
    }

    private record Milestone(float progress, Beat beat) {
    }

    private static final class Tracker {
        private float lastProgress;
        private int next;

        private Tracker(float progress) {
            this.lastProgress = progress;
            this.next = firstMilestoneAfter(progress);
        }

        private void reset(float progress) {
            this.lastProgress = progress;
            this.next = firstMilestoneAfter(progress);
        }
    }
}
