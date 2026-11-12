package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectDebrisAttractor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectDebrisMathTest {
    @Test
    void infallStartsAtThePieceAndReachesTheHorizon() {
        EffectDebrisAttractor attractor = new EffectDebrisAttractor(
            Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 2.0, 1.0, 1.0
        );
        Vec3 start = new Vec3(10.0, 3.0, 0.0);

        assertTrue(EffectDebrisMath.infallPosition(start, attractor, 0.0).distanceTo(start) < 1.0E-9);
        Vec3 end = EffectDebrisMath.infallPosition(start, attractor, 1.0);
        assertEquals(2.0, end.length(), 1.0E-9);
    }

    @Test
    void straightFallStaysOnTheRadialLine() {
        EffectDebrisAttractor attractor = new EffectDebrisAttractor(
            new Vec3(1.0, 2.0, 3.0), new Vec3(0.0, 1.0, 0.0), 2.0, 1.35, 0.0
        );
        Vec3 start = new Vec3(11.0, 7.0, 1.0);
        Vec3 radial = start.subtract(attractor.center()).normalize();

        double previous = Double.MAX_VALUE;
        for (int step = 0; step <= 20; step++) {
            Vec3 position = EffectDebrisMath.infallPosition(
                start, attractor, step / 20.0
            );
            Vec3 offset = position.subtract(attractor.center());
            assertTrue(offset.normalize().dot(radial) > 1.0 - 1.0E-9, "step " + step);
            assertTrue(offset.length() <= previous + 1.0E-9, "step " + step);
            previous = offset.length();
        }
        assertEquals(2.0, previous, 1.0E-9);
    }

    @Test
    void rotationPreservesLengthAndMovestangentially() {
        Vec3 rotated = EffectDebrisMath.rotate(
            new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), Math.PI / 2.0
        );
        assertEquals(1.0, rotated.length(), 1.0E-9);
        assertEquals(0.0, rotated.x, 1.0E-9);
        assertEquals(-1.0, rotated.z, 1.0E-9);
    }
}
