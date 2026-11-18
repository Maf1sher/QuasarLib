package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.client.effect.audio.EffectAudioGain;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentSounds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserAudioTest {
    private static final TargetColumn TARGET = new TargetColumn(10, -5, 64);
    private static final Vec3 CENTER = TARGET.center();

    @Test
    void planFitsTheShowDurationAndUsesContentSounds() {
        EffectAudioSpec audio = ContentOrbitalLaserAudio.plan(CENTER);

        assertFalse(audio.isEmpty());
        assertTrue(audio.fitsWithin(ContentOrbitalLaserSettings.TOTAL_TICKS));
        assertEquals(7, audio.cueCount());
        for (EffectAudioCue cue : audio.cues()) {
            assertEquals(ContentBootstrap.NAMESPACE, cue.soundId().getNamespace(),
                cue.soundId().toString());
        }
    }

    @Test
    void cueTicksFollowThePhasePlan() {
        EffectAudioSpec audio = ContentOrbitalLaserAudio.plan(CENTER);

        assertEquals(0, cue(audio, ContentSounds.ORBITAL_LASER_WARNING).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT),
            cue(audio, ContentSounds.ORBITAL_LASER_DESCENT).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT),
            cue(audio, ContentSounds.ORBITAL_LASER_IMPACT).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT),
            cue(audio, ContentSounds.ORBITAL_LASER_DRONE).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN),
            cue(audio, ContentSounds.ORBITAL_LASER_CRACKLE).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_COLLAPSE),
            cue(audio, ContentSounds.ORBITAL_LASER_COLLAPSE).tick());
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_AFTERMATH),
            cue(audio, ContentSounds.ORBITAL_LASER_AFTERMATH).tick());
    }

    @Test
    void descentLoopsFromTheBeamTopToTheTarget() {
        EffectAudioCue descent = cue(
            ContentOrbitalLaserAudio.plan(CENTER), ContentSounds.ORBITAL_LASER_DESCENT
        );

        assertTrue(descent.envelope().looping());
        assertTrue(descent.motion() instanceof EffectAudioMotion.Path);
        EffectAudioMotion.Path path = (EffectAudioMotion.Path)descent.motion();
        assertEquals(phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT), path.startTick());
        assertEquals(
            phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT),
            path.endTick()
        );
        assertEquals(CENTER.add(0.0, ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT, 0.0),
            path.resolve(CENTER, Vec3.ZERO, path.startTick()));
        assertEquals(CENTER, path.resolve(CENTER, Vec3.ZERO, path.endTick()));
    }

    @Test
    void descentGetsLouderAsItApproachesNearbyListeners() {
        EffectAudioCue descent = cue(
            ContentOrbitalLaserAudio.plan(CENTER), ContentSounds.ORBITAL_LASER_DESCENT
        );
        EffectAudioAttenuation.Distance attenuation =
            (EffectAudioAttenuation.Distance)descent.attenuation();
        int start = phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT);
        int end = phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT);
        int middle = (start + end) / 2;

        for (Vec3 listener : List.of(
            CENTER,
            CENTER.add(64.0, 0.0, 0.0),
            CENTER.add(0.0, 0.0, 200.0)
        )) {
            float atStart = gain(descent, attenuation, listener, start);
            float atMiddle = gain(descent, attenuation, listener, middle);
            float atEnd = gain(descent, attenuation, listener, end);

            assertTrue(atStart < atMiddle, "start vs middle at " + listener);
            assertTrue(atMiddle < atEnd, "middle vs end at " + listener);
        }
        assertEquals(0.0F,
            gain(descent, attenuation, CENTER.add(600.0, 0.0, 0.0), start), 1.0E-6F);
        assertEquals(0.0F,
            gain(descent, attenuation, CENTER.add(600.0, 0.0, 0.0), end), 1.0E-6F);
    }

    @Test
    void loopsStayInsideTheirPhaseWindowsAndUnderTheLoopCap() {
        EffectAudioSpec audio = ContentOrbitalLaserAudio.plan(CENTER);
        int descent = phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT);
        int impact = phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT);
        int sustain = phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN);
        int collapse = phaseStart(ContentOrbitalLaserSettings.PHASE_COLLAPSE);

        assertEquals(ContentOrbitalLaserSettings.TARGET_TICKS,
            loopStop(audio, ContentSounds.ORBITAL_LASER_WARNING));
        assertEquals(impact,
            loopStop(audio, ContentSounds.ORBITAL_LASER_DESCENT));
        assertEquals(collapse,
            loopStop(audio, ContentSounds.ORBITAL_LASER_DRONE));
        assertEquals(collapse,
            loopStop(audio, ContentSounds.ORBITAL_LASER_CRACKLE));

        assertEquals(1, activeLoops(audio, 0));
        assertEquals(0, activeLoops(audio, ContentOrbitalLaserSettings.TARGET_TICKS));
        assertEquals(0, activeLoops(audio,
            phaseStart(ContentOrbitalLaserSettings.PHASE_ARRIVAL)));
        assertEquals(0, activeLoops(audio,
            phaseStart(ContentOrbitalLaserSettings.PHASE_CHARGE)));
        assertEquals(1, activeLoops(audio, descent));
        assertEquals(1, activeLoops(audio, impact + 1));
        assertEquals(2, activeLoops(audio, sustain));
        assertEquals(0, activeLoops(audio, collapse));
        for (int age = 0; age < ContentOrbitalLaserSettings.TOTAL_TICKS; age++) {
            assertTrue(activeLoops(audio, age) <= 4, "loop cap at " + age);
        }
    }

    @Test
    void lateJoinResumesTheCurrentPhase() {
        EffectAudioSpec audio = ContentOrbitalLaserAudio.plan(CENTER);
        EffectAudioPlan plan = EffectAudioPlan.from(
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            CENTER,
            0L,
            ContentOrbitalLaserSettings.TOTAL_TICKS,
            audio
        );

        assertCueAt(plan, phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT) + 5,
            ContentSounds.ORBITAL_LASER_DESCENT);
        assertCueAt(plan, phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT) + 5,
            ContentSounds.ORBITAL_LASER_DRONE);
        assertCueAt(plan, ContentOrbitalLaserSettings.TOTAL_TICKS - 25,
            ContentSounds.ORBITAL_LASER_AFTERMATH);
        assertTrue(plan.hasPlayableCues(
            phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT) + 5, 2
        ));
        assertFalse(plan.hasPlayableCues(
            ContentOrbitalLaserSettings.TOTAL_TICKS - 5, 2
        ));
    }

    @Test
    void gameplayAndPreviewCarryTheSameScore() {
        GameplayEffectSpec gameplay = ContentOrbitalLaserSpecs.gameplay(
            null, TARGET, 6, 42L
        );
        VisualEffectSpec preview = ContentOrbitalLaserSpecs.preview(TARGET, 6, 42L);

        assertFalse(gameplay.audio().isEmpty());
        assertEquals(gameplay.audio().cues(), preview.audio().cues());
        assertEquals(gameplay.audio().triggers(), preview.audio().triggers());
    }

    private static void assertCueAt(
        EffectAudioPlan plan,
        int age,
        Supplier<SoundEvent> sound
    ) {
        int index = plan.firstCueIndex(age, EffectAudioPlan.GRACE_TICKS);
        assertTrue(index < plan.cueCount(), "no cue at " + age);
        assertEquals(sound.get().getLocation(), plan.cues().get(index).soundId());
    }

    private static float gain(
        EffectAudioCue cue,
        EffectAudioAttenuation.Distance attenuation,
        Vec3 listener,
        int ageTicks
    ) {
        Vec3 source = cue.motion().resolve(cue.position(), Vec3.ZERO, ageTicks);
        return EffectAudioGain.distanceGain(
            source.distanceTo(listener), attenuation.maxDistance()
        );
    }

    private static int phaseStart(int phaseIndex) {
        return ContentOrbitalLaserSettings.phasePlan().phaseStartTick(phaseIndex);
    }

    private static int loopStop(EffectAudioSpec audio, Supplier<SoundEvent> sound) {
        EffectAudioCue cue = cue(audio, sound);
        assertTrue(cue.envelope().looping());
        return cue.envelope().stopTickOr(ContentOrbitalLaserSettings.TOTAL_TICKS);
    }

    private static int activeLoops(EffectAudioSpec audio, int age) {
        int loops = 0;
        for (EffectAudioCue cue : audio.cues()) {
            if (cue.envelope().looping()
                && cue.tick() <= age
                && age < cue.envelope().stopTickOr(ContentOrbitalLaserSettings.TOTAL_TICKS)) {
                loops++;
            }
        }
        return loops;
    }

    private static EffectAudioCue cue(EffectAudioSpec audio, Supplier<SoundEvent> sound) {
        ResourceLocation id = sound.get().getLocation();
        for (EffectAudioCue cue : audio.cues()) {
            if (cue.soundId().equals(id)) {
                return cue;
            }
        }
        throw new AssertionError("Missing cue " + id);
    }
}
