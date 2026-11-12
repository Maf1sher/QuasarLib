package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public effect audio surface. */
class PublicAudioApiVisibilityTest {
    @Test
    void exposesTheAudioSpecAndPlan() {
        EffectAudioSpec spec = EffectAudioSpec.builder(100)
            .cue(EffectAudioCue.at(10, Vec3.ZERO, ResourceLocation.withDefaultNamespace("test"), SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.distance(64.0F)))
            .build();
        EffectAudioPlan plan = EffectAudioPlan.from(
            UUID.randomUUID(),
            ResourceLocation.withDefaultNamespace("overworld"),
            Vec3.ZERO,
            0L,
            100,
            spec
        );

        assertEquals(1, plan.cueCount());
        assertEquals(EffectAudioAttenuation.VANILLA, new EffectAudioCue(
            0, Vec3.ZERO, ResourceLocation.withDefaultNamespace("test"), SoundSource.MASTER, 1.0F, 1.0F
        ).attenuation());
        assertTrue(EffectAudioEnvelope.loop(1, 2).looping());
        assertEquals(Vec3.ZERO, EffectAudioMotion.ANCHORED.resolve(Vec3.ZERO, Vec3.ZERO));
        assertEquals("impact", new EffectAudioTrigger(
            "impact", Vec3.ZERO, ResourceLocation.withDefaultNamespace("test"),
            SoundSource.MASTER, 1.0F, 1.0F
        ).id());
        assertNotNull(EffectAudioService.class);
    }
}
