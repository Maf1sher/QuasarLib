package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioEnvelope;
import mafisher.quasar.effect.audio.EffectAudioMotion;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.audio.EffectAudioTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioNbtTest {
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    @Test
    void roundTripsEveryAttenuationVariant() {
        EffectAudioSpec spec = EffectAudioSpec.builder(100)
            .cue(0, new Vec3(1.0, 2.0, 3.0), SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.VANILLA)
            .cue(10, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.5F, 1.2F,
                EffectAudioAttenuation.distance(64.0F))
            .cue(20, new Vec3(-1.0, 4.0, 8.0), SOUND, SoundSource.AMBIENT, 0.8F, 0.9F,
                EffectAudioAttenuation.directional(new Vec3(0.0, -2.0, 0.0), 30.0F, 90.0F, 0.25F, 48.0F))
            .build();

        CompoundTag parent = new CompoundTag();
        EffectAudioNbt.saveInto(parent, spec);
        EffectAudioSpec loaded = EffectAudioNbt.loadFrom(parent);

        assertEquals(3, loaded.cueCount());
        assertEquals(1.0, loaded.cues().get(0).position().x);
        assertEquals(EffectAudioAttenuation.VANILLA, loaded.cues().get(0).attenuation());
        assertEquals(64.0F, ((EffectAudioAttenuation.Distance)loaded.cues().get(1).attenuation()).maxDistance());
        EffectAudioAttenuation.Directional directional =
            (EffectAudioAttenuation.Directional)loaded.cues().get(2).attenuation();
        assertEquals(-1.0, directional.direction().y, 1.0E-9);
        assertEquals(0.25F, directional.outsideGain());
    }

    @Test
    void roundTripsLoopsMotionAndTriggers() {
        EffectAudioSpec spec = EffectAudioSpec.builder(200)
            .cue(new EffectAudioCue(
                10,
                new Vec3(1.0, 2.0, 3.0),
                SOUND,
                SoundSource.AMBIENT,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F),
                EffectAudioEnvelope.loopUntil(150, 10, 20),
                EffectAudioMotion.FOLLOW_EFFECT
            ))
            .trigger(new EffectAudioTrigger(
                "impact", new Vec3(4.0, 5.0, 6.0), SOUND, SoundSource.PLAYERS, 0.8F, 1.2F,
                EffectAudioAttenuation.distance(32.0F)
            ))
            .build();

        CompoundTag parent = new CompoundTag();
        EffectAudioNbt.saveInto(parent, spec);
        EffectAudioSpec loaded = EffectAudioNbt.loadFrom(parent);

        assertEquals(1, loaded.cueCount());
        assertEquals(EffectAudioEnvelope.loopUntil(150, 10, 20), loaded.cues().getFirst().envelope());
        assertEquals(EffectAudioMotion.FOLLOW_EFFECT, loaded.cues().getFirst().motion());
        assertEquals(1, loaded.triggerCount());
        assertEquals("impact", loaded.triggerOrNull("impact").id());
        assertEquals(4.0, loaded.triggerOrNull("impact").position().x);
    }

    @Test
    void oldCueDataDefaultsToOneShotAndAnchored() {
        CompoundTag parent = new CompoundTag();
        CompoundTag audio = new CompoundTag();
        CompoundTag cue = new CompoundTag();
        cue.putInt("tick", 0);
        cue.putString("sound", SOUND.toString());
        cue.putString("source", SoundSource.MASTER.name());
        cue.putFloat("volume", 1.0F);
        cue.putFloat("pitch", 1.0F);
        CompoundTag attenuation = new CompoundTag();
        attenuation.putString("type", "vanilla");
        cue.put("attenuation", attenuation);
        audio.put("cues", listOf(cue));
        parent.put(EffectAudioNbt.KEY, audio);

        EffectAudioSpec loaded = EffectAudioNbt.loadFrom(parent);

        assertEquals(EffectAudioEnvelope.ONE_SHOT, loaded.cues().getFirst().envelope());
        assertEquals(EffectAudioMotion.ANCHORED, loaded.cues().getFirst().motion());
    }

    @Test
    void absentKeyIsNone() {
        assertSame(EffectAudioSpec.NONE, EffectAudioNbt.loadFrom(new CompoundTag()));
    }

    @Test
    void emptySpecIsNotStored() {
        CompoundTag parent = new CompoundTag();
        parent.put(EffectAudioNbt.KEY, new CompoundTag());

        EffectAudioNbt.saveInto(parent, EffectAudioSpec.NONE);

        assertTrue(parent.isEmpty());
    }

    @Test
    void rejectsInvalidSoundIds() {
        CompoundTag parent = new CompoundTag();
        CompoundTag audio = new CompoundTag();
        CompoundTag cue = new CompoundTag();
        cue.putInt("tick", 0);
        cue.putString("sound", "not a resource location");
        cue.putString("source", SoundSource.MASTER.name());
        cue.putFloat("volume", 1.0F);
        cue.putFloat("pitch", 1.0F);
        cue.put("attenuation", new CompoundTag());
        audio.put("cues", listOf(cue));
        parent.put(EffectAudioNbt.KEY, audio);

        assertThrows(IllegalArgumentException.class, () -> EffectAudioNbt.loadFrom(parent));
    }

    private static net.minecraft.nbt.ListTag listOf(CompoundTag tag) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(tag);
        return list;
    }
}
