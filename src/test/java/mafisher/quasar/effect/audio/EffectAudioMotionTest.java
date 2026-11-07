package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioMotionTest {
    @Test
    void anchoredCuesIgnoreEffectMovement() {
        Vec3 authored = new Vec3(10.0, 64.0, 5.0);

        assertEquals(authored, EffectAudioMotion.ANCHORED.resolve(authored, new Vec3(100.0, 0.0, 0.0)));
    }

    @Test
    void followEffectCuesTranslateWithTheEffect() {
        Vec3 authored = new Vec3(10.0, 64.0, 5.0);

        Vec3 resolved = EffectAudioMotion.FOLLOW_EFFECT.resolve(authored, new Vec3(4.0, -1.0, 2.0));

        assertEquals(14.0, resolved.x);
        assertEquals(63.0, resolved.y);
        assertEquals(7.0, resolved.z);
    }

    @Test
    void followEffectWithZeroTranslationKeepsTheAuthoredPosition() {
        Vec3 authored = new Vec3(-3.0, 20.0, 8.0);

        assertEquals(authored, EffectAudioMotion.FOLLOW_EFFECT.resolve(authored, Vec3.ZERO));
    }
}
