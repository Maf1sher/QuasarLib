package mafisher.quasar.content.orbital;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.client.impulse.CameraShakeImpulse;
import mafisher.quasar.api.client.impulse.ClientEffectImpulses;
import mafisher.quasar.api.client.render.EffectRenderContext;
import net.minecraft.world.phys.Vec3;

/**
 * Content-owned rumble beats for the orbital-laser show.
 *
 * <p>The impact is one strong decaying impulse. The sustained beam adds a long low bed and
 * overlapping pulses instead of a dedicated sustained-shake primitive; the composition stays
 * inside the existing impulse cap and is evaluated by the content tests. Impulses are driven by
 * effect age, so they remain a pure content concern and never touch server state.</p>
 */
public final class ContentOrbitalLaserImpulses {
    private static final double IMPACT_RADIUS = 512.0;
    private static final double SUSTAIN_RADIUS = 384.0;
    private static final double PULSE_RADIUS = 320.0;
    private static final double COLLAPSE_RADIUS = 384.0;
    private static final double AFTERMATH_RADIUS = 256.0;

    private static final float IMPACT_INTENSITY = 0.42F;
    private static final int IMPACT_DURATION = 18;
    private static final float IMPACT_FREQUENCY = 1.6F;
    private static final float IMPACT_ANGLE = 1.2F;

    private static final float BED_INTENSITY = 0.08F;
    private static final int BED_DURATION = ContentOrbitalLaserSettings.SUSTAIN_TICKS;
    private static final float BED_FREQUENCY = 0.7F;
    private static final float BED_ANGLE = 0.25F;

    private static final int PULSE_INTERVAL = 16;
    private static final float PULSE_INTENSITY = 0.05F;
    private static final int PULSE_DURATION = 24;
    private static final float PULSE_ANGLE = 0.15F;
    private static final float PULSE_FREQUENCY_A = 0.9F;
    private static final float PULSE_FREQUENCY_B = 1.1F;

    private static final float COLLAPSE_INTENSITY = 0.16F;
    private static final int COLLAPSE_DURATION = 36;
    private static final float COLLAPSE_FREQUENCY = 0.5F;
    private static final float COLLAPSE_ANGLE = 0.45F;

    private static final float AFTERMATH_INTENSITY = 0.06F;
    private static final int AFTERMATH_DURATION = 30;
    private static final float AFTERMATH_FREQUENCY = 0.4F;
    private static final float AFTERMATH_ANGLE = 0.18F;

    private static final int GRACE_TICKS = 2;
    static final int MAX_TRACKED = 64;
    private static final ShakeBeat[] BEATS = buildBeats();
    private static final Map<UUID, Tracker> TRACKERS = new LinkedHashMap<>();

    private ContentOrbitalLaserImpulses() {
    }

    static void tick(EffectRenderContext context) {
        UUID instanceId = context.effect().instanceId();
        int age = (int)Math.floor(context.ageTicks());
        Tracker tracker = TRACKERS.get(instanceId);
        if (tracker == null) {
            if (TRACKERS.size() >= MAX_TRACKED) {
                Iterator<Map.Entry<UUID, Tracker>> iterator = TRACKERS.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
            tracker = new Tracker(age);
            TRACKERS.put(instanceId, tracker);
        } else if (age < tracker.lastAge) {
            tracker.reset(age);
        }

        while (tracker.next < BEATS.length && BEATS[tracker.next].tick() <= age) {
            ShakeBeat beat = BEATS[tracker.next++];
            if (age - beat.tick() <= GRACE_TICKS) {
                fire(context, beat);
            }
        }
        tracker.lastAge = age;
    }

    /** Removes every tracker; used on client level unload. */
    public static void clear() {
        TRACKERS.clear();
    }

    private static void fire(EffectRenderContext context, ShakeBeat beat) {
        Vec3 position = new Vec3(
            context.transform().position().x(),
            context.transform().position().y(),
            context.transform().position().z()
        );
        ClientEffectImpulses.cameraShake(new CameraShakeImpulse(
            position,
            beat.radius(),
            beat.intensity(),
            beat.durationTicks(),
            beat.frequencyHz(),
            beat.maxAngleDegrees()
        ));
    }

    private static ShakeBeat[] buildBeats() {
        int impact = phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT);
        int sustain = phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN);
        int collapse = phaseStart(ContentOrbitalLaserSettings.PHASE_COLLAPSE);
        int aftermath = phaseStart(ContentOrbitalLaserSettings.PHASE_AFTERMATH);
        int pulseCount = (collapse - sustain + PULSE_INTERVAL - 1) / PULSE_INTERVAL;
        ShakeBeat[] beats = new ShakeBeat[pulseCount + 4];
        int index = 0;
        beats[index++] = new ShakeBeat(
            impact, IMPACT_INTENSITY, IMPACT_DURATION, IMPACT_FREQUENCY, IMPACT_ANGLE,
            IMPACT_RADIUS
        );
        beats[index++] = new ShakeBeat(
            sustain, BED_INTENSITY, BED_DURATION, BED_FREQUENCY, BED_ANGLE, SUSTAIN_RADIUS
        );
        for (int pulse = 0; pulse < pulseCount; pulse++) {
            float frequency = pulse % 2 == 0 ? PULSE_FREQUENCY_A : PULSE_FREQUENCY_B;
            beats[index++] = new ShakeBeat(
                sustain + pulse * PULSE_INTERVAL,
                PULSE_INTENSITY,
                PULSE_DURATION,
                frequency,
                PULSE_ANGLE,
                PULSE_RADIUS
            );
        }
        beats[index++] = new ShakeBeat(
            collapse, COLLAPSE_INTENSITY, COLLAPSE_DURATION, COLLAPSE_FREQUENCY,
            COLLAPSE_ANGLE, COLLAPSE_RADIUS
        );
        beats[index] = new ShakeBeat(
            aftermath, AFTERMATH_INTENSITY, AFTERMATH_DURATION, AFTERMATH_FREQUENCY,
            AFTERMATH_ANGLE, AFTERMATH_RADIUS
        );
        return beats;
    }

    private static int phaseStart(int phaseIndex) {
        var phases = ContentOrbitalLaserSettings.phasePlan().phases();
        int tick = 0;
        for (int index = 0; index < phaseIndex; index++) {
            tick += phases.get(index).durationTicks();
        }
        return tick;
    }

    private static int firstBeatAtOrAfter(int age) {
        for (int index = 0; index < BEATS.length; index++) {
            if (BEATS[index].tick() >= age) {
                return index;
            }
        }
        return BEATS.length;
    }

    private record ShakeBeat(
        int tick,
        float intensity,
        int durationTicks,
        float frequencyHz,
        float maxAngleDegrees,
        double radius
    ) {
    }

    private static final class Tracker {
        private int lastAge;
        private int next;

        private Tracker(int age) {
            this.lastAge = age;
            this.next = firstBeatAtOrAfter(Math.max(0, age - GRACE_TICKS));
        }

        private void reset(int age) {
            this.lastAge = age;
            this.next = firstBeatAtOrAfter(Math.max(0, age - GRACE_TICKS));
        }
    }
}
