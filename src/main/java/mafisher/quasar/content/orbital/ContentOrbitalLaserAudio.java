package mafisher.quasar.content.orbital;

import java.util.Objects;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.content.ContentSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Content-owned phase score for the orbital-laser show.
 *
 * <p>Every cue is anchored to {@link ContentOrbitalLaserSettings#phasePlan()}, so phase edits
 * move the score with the session. Platform cues sit on the hub at
 * {@link ContentOrbitalLaserPlatformSettings#PLATFORM_ALTITUDE} with a wide radius, so the
 * warp-in, charge, and shot carry to the ground below. The descent source is a looping path from
 * the beam top to the target column; distance attenuation makes it louder as it approaches
 * listeners near the impact. The score never needs per-tick packets: the client resolves the path
 * from the effect age.</p>
 */
public final class ContentOrbitalLaserAudio {
    private static final float WARNING_RADIUS = 512.0F;
    private static final float PLATFORM_CUE_RADIUS = 4096.0F;
    private static final float DESCENT_RADIUS = 2048.0F;
    private static final float IMPACT_RADIUS = 1024.0F;
    private static final float DRONE_RADIUS = 512.0F;
    private static final float CRACKLE_RADIUS = 384.0F;
    private static final float COLLAPSE_RADIUS = 512.0F;
    private static final float AFTERMATH_RADIUS = 384.0F;

    private static final float WARNING_VOLUME = 0.55F;
    private static final float ARRIVAL_VOLUME = 0.60F;
    private static final float CHARGE_VOLUME = 0.70F;
    private static final float FIRE_VOLUME = 1.0F;
    private static final float DESCENT_VOLUME = 0.75F;
    private static final float IMPACT_VOLUME = 1.0F;
    private static final float DRONE_VOLUME = 0.55F;
    private static final float CRACKLE_VOLUME = 0.35F;
    private static final float COLLAPSE_VOLUME = 0.80F;
    private static final float AFTERMATH_VOLUME = 0.40F;

    private static final int WARNING_FADE_IN = 4;
    private static final int WARNING_FADE_OUT = 8;
    private static final int DESCENT_FADE_IN = 6;
    private static final int DRONE_FADE_IN = 4;
    private static final int DRONE_FADE_OUT = 16;
    private static final int CRACKLE_FADE_IN = 8;
    private static final int CRACKLE_FADE_OUT = 12;

    private static final Vec3 DESCENT_START = new Vec3(
        0.0, ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT, 0.0
    );
    private static final Vec3 PLATFORM_OFFSET = new Vec3(
        0.0, ContentOrbitalLaserPlatformSettings.PLATFORM_ALTITUDE, 0.0
    );

    private ContentOrbitalLaserAudio() {
    }

    /** Builds the ten-cue score for one target column centre. */
    public static EffectAudioSpec plan(Vec3 targetCenter) {
        Objects.requireNonNull(targetCenter, "targetCenter");
        EffectAudioSpec.Builder builder = EffectAudioSpec.builder(
            ContentOrbitalLaserSettings.phasePlan()
        );
        addWarning(builder, targetCenter);
        Vec3 platform = targetCenter.add(PLATFORM_OFFSET);
        builder.phaseCue("arrival", 0, oneShot(
            platform,
            ContentSounds.ORBITAL_LASER_ARRIVAL.get(),
            ARRIVAL_VOLUME,
            1.0F,
            PLATFORM_CUE_RADIUS
        ));
        builder.phaseCue("charge", 0, oneShot(
            platform,
            ContentSounds.ORBITAL_LASER_CHARGE.get(),
            CHARGE_VOLUME,
            1.0F,
            PLATFORM_CUE_RADIUS
        ));
        builder.phaseCue("descent", 0, oneShot(
            platform,
            ContentSounds.ORBITAL_LASER_FIRE.get(),
            FIRE_VOLUME,
            1.0F,
            PLATFORM_CUE_RADIUS
        ));
        addDescent(builder, targetCenter);
        addImpactAndDrone(builder, targetCenter);
        addCrackle(builder, targetCenter);
        addCollapse(builder, targetCenter);
        addAftermath(builder, targetCenter);
        return builder.build();
    }

    private static void addWarning(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("target", 0, loop(
            targetCenter,
            ContentSounds.ORBITAL_LASER_WARNING.get(),
            WARNING_VOLUME,
            1.0F,
            WARNING_RADIUS,
            EffectAudioEnvelope.loopUntil(
                ContentOrbitalLaserSettings.TARGET_TICKS,
                WARNING_FADE_IN,
                WARNING_FADE_OUT
            ),
            EffectAudioMotion.ANCHORED
        ));
    }

    private static void addDescent(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("descent", 0, loop(
            targetCenter,
            ContentSounds.ORBITAL_LASER_DESCENT.get(),
            DESCENT_VOLUME,
            1.0F,
            DESCENT_RADIUS,
            EffectAudioEnvelope.loopUntil(
                ContentOrbitalLaserSettings.DESCENT_TICKS,
                DESCENT_FADE_IN,
                0
            ),
            new EffectAudioMotion.Path(
                DESCENT_START, Vec3.ZERO, 0, ContentOrbitalLaserSettings.DESCENT_TICKS
            )
        ));
    }

    private static void addImpactAndDrone(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("impact", 0, oneShot(
            targetCenter,
            ContentSounds.ORBITAL_LASER_IMPACT.get(),
            IMPACT_VOLUME,
            1.0F,
            IMPACT_RADIUS
        ));
        builder.phaseCue("impact", 0, loop(
            targetCenter,
            ContentSounds.ORBITAL_LASER_DRONE.get(),
            DRONE_VOLUME,
            1.0F,
            DRONE_RADIUS,
            EffectAudioEnvelope.loopUntil(
                ContentOrbitalLaserSettings.IMPACT_TICKS
                    + ContentOrbitalLaserSettings.SUSTAIN_TICKS,
                DRONE_FADE_IN,
                DRONE_FADE_OUT
            ),
            EffectAudioMotion.ANCHORED
        ));
    }

    private static void addCrackle(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("sustain", 0, loop(
            targetCenter,
            ContentSounds.ORBITAL_LASER_CRACKLE.get(),
            CRACKLE_VOLUME,
            1.0F,
            CRACKLE_RADIUS,
            EffectAudioEnvelope.loopUntil(
                ContentOrbitalLaserSettings.SUSTAIN_TICKS,
                CRACKLE_FADE_IN,
                CRACKLE_FADE_OUT
            ),
            EffectAudioMotion.ANCHORED
        ));
    }

    private static void addCollapse(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("collapse", 0, oneShot(
            targetCenter,
            ContentSounds.ORBITAL_LASER_COLLAPSE.get(),
            COLLAPSE_VOLUME,
            0.95F,
            COLLAPSE_RADIUS
        ));
    }

    private static void addAftermath(EffectAudioSpec.Builder builder, Vec3 targetCenter) {
        builder.phaseCue("aftermath", 0, oneShot(
            targetCenter,
            ContentSounds.ORBITAL_LASER_AFTERMATH.get(),
            AFTERMATH_VOLUME,
            0.9F,
            AFTERMATH_RADIUS
        ));
    }

    private static EffectAudioCue loop(
        Vec3 position,
        SoundEvent sound,
        float volume,
        float pitch,
        float radius,
        EffectAudioEnvelope envelope,
        EffectAudioMotion motion
    ) {
        return new EffectAudioCue(
            0,
            position,
            sound.getLocation(),
            SoundSource.AMBIENT,
            volume,
            pitch,
            EffectAudioAttenuation.distance(radius),
            envelope,
            motion
        );
    }

    private static EffectAudioCue oneShot(
        Vec3 position,
        SoundEvent sound,
        float volume,
        float pitch,
        float radius
    ) {
        return EffectAudioCue.at(
            0,
            position,
            sound,
            SoundSource.AMBIENT,
            volume,
            pitch,
            EffectAudioAttenuation.distance(radius)
        );
    }
}
