package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.audio.EffectAudioMotion;
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

    @Test
    void pathInterpolatesAndClampsTheEffectTickWindow() {
        Vec3 authored = new Vec3(10.0, 64.0, 5.0);
        EffectAudioMotion.Path path = new EffectAudioMotion.Path(
            new Vec3(0.0, -10.0, 0.0), new Vec3(0.0, 10.0, 0.0), 20, 60
        );

        assertEquals(54.0, path.resolve(authored, Vec3.ZERO, 0).y);
        assertEquals(64.0, path.resolve(authored, Vec3.ZERO, 40).y);
        assertEquals(74.0, path.resolve(authored, Vec3.ZERO, 60).y);
        assertEquals(74.0, path.resolve(authored, Vec3.ZERO, 100).y);
    }

    @Test
    void pathIgnoresEffectTranslation() {
        Vec3 authored = new Vec3(1.0, 2.0, 3.0);
        EffectAudioMotion.Path path = new EffectAudioMotion.Path(
            new Vec3(0.0, -10.0, 0.0), new Vec3(0.0, 10.0, 0.0), 0, 10
        );

        Vec3 resolved = path.resolve(authored, new Vec3(100.0, 0.0, 0.0), 5);

        assertEquals(new Vec3(1.0, 2.0, 3.0), resolved);
    }

    @Test
    void twoArgumentResolveUsesAgeZero() {
        Vec3 authored = new Vec3(1.0, 2.0, 3.0);
        EffectAudioMotion.Path path = new EffectAudioMotion.Path(
            new Vec3(0.0, -10.0, 0.0), Vec3.ZERO, 10, 20
        );

        assertEquals(authored.add(path.startOffset()), path.resolve(authored, Vec3.ZERO));
    }

    @Test
    void rejectsInvalidPathOffsetsAndWindows() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioMotion.Path(Vec3.ZERO, Vec3.ZERO, -1, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioMotion.Path(Vec3.ZERO, Vec3.ZERO, 10, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioMotion.Path(Vec3.ZERO, Vec3.ZERO, 10, 5));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioMotion.Path(new Vec3(Double.NaN, 0.0, 0.0), Vec3.ZERO, 0, 10));
        assertThrows(NullPointerException.class,
            () -> new EffectAudioMotion.Path(null, Vec3.ZERO, 0, 10));
    }
}
