package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.effect.EffectForceField;
import mafisher.quasar.api.effect.EffectPoint;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectForceFieldTest {
    private static final EffectPoint ORIGIN = new EffectPoint(0.0, 0.0, 0.0);

    @Test
    void radialPullMovesTowardTheCenter() {
        EffectForceField field = EffectForceField.pull(ORIGIN, 10.0, 1.0, 0.0, 4);

        Vec3 delta = field.velocityDeltaAt(new Vec3(5.0, 0.0, 0.0));

        assertEquals(-1.0, delta.x, 1.0E-9);
        assertEquals(0.0, delta.y, 1.0E-9);
        assertEquals(0.0, delta.z, 1.0E-9);
    }

    @Test
    void radialPushMovesAwayFromTheCenter() {
        EffectForceField field = EffectForceField.push(ORIGIN, 10.0, 1.0, 0.0, 4);

        Vec3 delta = field.velocityDeltaAt(new Vec3(5.0, 0.0, 0.0));

        assertEquals(1.0, delta.x, 1.0E-9);
    }

    @Test
    void outsideTheRadiusTheFieldIsZero() {
        EffectForceField field = EffectForceField.pull(ORIGIN, 4.0, 8.0, 1.0, 4);

        assertEquals(Vec3.ZERO, field.velocityDeltaAt(new Vec3(8.0, 0.0, 0.0)));
    }

    @Test
    void shockwaveCombinesOutwardMotionAndLift() {
        EffectForceField field = new EffectForceField.Shockwave(ORIGIN, 10.0, 2.0, 1.0, 0.0, 4);

        Vec3 delta = field.velocityDeltaAt(new Vec3(2.0, 0.0, 0.0));

        assertEquals(2.0, delta.x, 1.0E-9);
        assertEquals(2.0, delta.y, 1.0E-9);
    }

    @Test
    void vortexAddsTangentialMotion() {
        EffectForceField field = new EffectForceField.Vortex(ORIGIN, 10.0, 1.0, 0.0, 0.0, 0.0, 4);

        Vec3 delta = field.velocityDeltaAt(new Vec3(1.0, 0.0, 0.0));

        assertEquals(0.0, delta.x, 1.0E-9);
        assertEquals(1.0, delta.z, 1.0E-9);
    }

    @Test
    void accretionPullsInwardAndSwirlsAroundItsAxis() {
        EffectForceField field = EffectForceField.accretion(
            ORIGIN, new Vec3(0.0, 1.0, 0.0), 10.0, 1.0, 1.0, 0.0, 0.0, 4
        );

        Vec3 delta = field.velocityDeltaAt(new Vec3(1.0, 0.0, 0.0));

        assertEquals(-1.0, delta.x, 1.0E-9);
        assertEquals(0.0, delta.y, 1.0E-9);
        assertEquals(1.0, delta.z, 1.0E-9);
        assertTrue(field.bounds().contains(ORIGIN));
    }

    @Test
    void accretionLiftsAlongItsAxis() {
        EffectForceField field = EffectForceField.accretion(
            ORIGIN, new Vec3(0.0, 1.0, 0.0), 10.0, 0.0, 0.0, 0.5, 0.0, 4
        );

        Vec3 delta = field.velocityDeltaAt(new Vec3(1.0, 0.0, 0.0));

        assertEquals(0.5, delta.y, 1.0E-9);
    }

    @Test
    void pressureActsOnlyInsideItsVolume() {
        EffectForceField field = new EffectForceField.Pressure(
            new EffectBounds(-5.0, -5.0, -5.0, 5.0, 5.0, 5.0),
            new Vec3(1.0, 0.0, 0.0),
            2.0,
            0.0,
            4
        );

        assertEquals(2.0, field.velocityDeltaAt(new Vec3(0.0, 0.0, 0.0)).x, 1.0E-9);
        assertEquals(Vec3.ZERO, field.velocityDeltaAt(new Vec3(10.0, 0.0, 0.0)));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
            () -> EffectForceField.pull(ORIGIN, 0.0, 1.0, 1.0, 4));
        assertThrows(IllegalArgumentException.class,
            () -> EffectForceField.pull(ORIGIN, 4.0, EffectForceField.MAX_STRENGTH + 1.0, 1.0, 4));
        assertThrows(IllegalArgumentException.class,
            () -> EffectForceField.pull(ORIGIN, 4.0, 1.0, 1.0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectForceField.Pressure(
                new EffectBounds(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0),
                Vec3.ZERO,
                1.0,
                0.0,
                1
            ));
        assertThrows(IllegalArgumentException.class,
            () -> EffectForceField.accretion(
                ORIGIN, Vec3.ZERO, 10.0, 1.0, 1.0, 0.0, 0.0, 4
            ));
        assertTrue(EffectForceField.pull(ORIGIN, 4.0, 1.0, 1.0, 4).bounds().contains(ORIGIN));
    }
}
