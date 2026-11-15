package mafisher.quasar.client.effect.inspect;

import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.client.effect.audio.EffectAudioDiagnostics;
import mafisher.quasar.client.effect.audio.EffectAudioGain;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Copies one active audio plan and its visually useful cues into bounded inspector slots. */
@ApiStatus.Internal
public final class InspectAudioCapture {
    private static final int MAX_CUES_PER_PLAN = 8;

    private InspectAudioCapture() {
    }

    public static void capture(InspectFrame frame, EffectAudioDiagnostics diagnostics, Vec3 listener) {
        InspectFrame.AudioPlanSlot plan = frame.addAudioPlan();
        if (plan == null) {
            return;
        }
        plan.effectId = diagnostics.plan().effectId();
        plan.cueCount = diagnostics.plan().cueCount();
        plan.triggerCount = diagnostics.plan().triggerCount();
        plan.activeLoops = diagnostics.activeLoops();
        plan.activeInstances = diagnostics.activeInstances();
        plan.nextCueIndex = diagnostics.nextCueIndex();
        plan.startGameTime = diagnostics.plan().startGameTime();
        plan.durationTicks = diagnostics.plan().durationTicks();
        plan.ageTicks = diagnostics.ageTicks();
        plan.centerX = diagnostics.plan().center().x;
        plan.centerY = diagnostics.plan().center().y;
        plan.centerZ = diagnostics.plan().center().z;
        plan.translationX = diagnostics.effectTranslation().x;
        plan.translationY = diagnostics.effectTranslation().y;
        plan.translationZ = diagnostics.effectTranslation().z;

        int added = 0;
        for (EffectAudioCue cue : diagnostics.plan().cues()) {
            if (added >= MAX_CUES_PER_PLAN) {
                return;
            }
            if (!(cue.attenuation() instanceof EffectAudioAttenuation.Directional)
                && !(cue.attenuation() instanceof EffectAudioAttenuation.Distance)) {
                continue;
            }
            InspectFrame.AudioCueSlot slot = frame.addAudioCue();
            if (slot == null) {
                return;
            }
            fillCue(slot, cue, diagnostics, listener);
            added++;
        }
    }

    private static void fillCue(InspectFrame.AudioCueSlot slot, EffectAudioCue cue,
        EffectAudioDiagnostics diagnostics, Vec3 listener) {
        Vec3 position = cue.motion().resolve(
            cue.position(), diagnostics.effectTranslation(), diagnostics.ageTicks()
        );
        slot.effectId = diagnostics.plan().effectId();
        slot.cueTick = cue.tick();
        slot.x = position.x;
        slot.y = position.y;
        slot.z = position.z;
        slot.looping = cue.envelope().looping();
        slot.played = cue.tick() <= diagnostics.ageTicks();

        float distanceGain = 1.0F;
        float directionGain = 1.0F;
        if (cue.attenuation() instanceof EffectAudioAttenuation.Distance distance) {
            slot.maxDistance = distance.maxDistance();
            distanceGain = EffectAudioGain.distanceGain(listener.distanceTo(position),
                distance.maxDistance());
        } else if (cue.attenuation() instanceof EffectAudioAttenuation.Directional directional) {
            slot.directional = true;
            slot.directionX = directional.direction().x;
            slot.directionY = directional.direction().y;
            slot.directionZ = directional.direction().z;
            slot.innerDegrees = directional.innerAngleDegrees();
            slot.outerDegrees = directional.outerAngleDegrees();
            slot.outsideGain = directional.outsideGain();
            slot.maxDistance = directional.maxDistance();
            distanceGain = EffectAudioGain.distanceGain(listener.distanceTo(position),
                directional.maxDistance());
            directionGain = directionGain(directional, position, listener);
        }
        slot.listenerGain = EffectAudioGain.combined(distanceGain, directionGain);
    }

    private static float directionGain(EffectAudioAttenuation.Directional directional, Vec3 source,
        Vec3 listener) {
        Vec3 toListener = listener.subtract(source);
        double length = toListener.length();
        if (length < 1.0E-6D) {
            return 1.0F;
        }
        double dot = toListener.x / length * directional.direction().x
            + toListener.y / length * directional.direction().y
            + toListener.z / length * directional.direction().z;
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
        return EffectAudioGain.directionGain(angle, directional.innerAngleDegrees(),
            directional.outerAngleDegrees(), directional.outsideGain());
    }
}
