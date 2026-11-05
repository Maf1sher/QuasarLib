package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectDamageShapeTest {
    @Test
    void sphereUsesClosestPointOnTargetBox() {
        EffectDamageShape.Sphere sphere = new EffectDamageShape.Sphere(EffectPoint.ZERO, 1.0);

        assertTrue(sphere.intersects(new EffectBounds(0.9, -0.1, -0.1, 1.2, 0.1, 0.1)));
        assertFalse(sphere.intersects(new EffectBounds(1.1, -0.1, -0.1, 1.3, 0.1, 0.1)));
        assertTrue(sphere.bounds().contains(EffectPoint.ZERO));
    }

    @Test
    void cylinderChecksHorizontalRadiusAndVerticalExtent() {
        EffectDamageShape.Cylinder cylinder = new EffectDamageShape.Cylinder(EffectPoint.ZERO, 1.0, 2.0);

        assertTrue(cylinder.intersects(new EffectBounds(0.8, 1.0, 0.0, 1.1, 1.5, 0.2)));
        assertFalse(cylinder.intersects(new EffectBounds(1.1, 1.0, 0.0, 1.3, 1.5, 0.2)));
        assertFalse(cylinder.intersects(new EffectBounds(0.0, 2.1, 0.0, 0.2, 2.4, 0.2)));
    }

    @Test
    void beamIsFiniteAndHasAConfigurableRadius() {
        EffectDamageShape.Beam beam = new EffectDamageShape.Beam(
            EffectPoint.ZERO, new EffectPoint(4.0, 0.0, 0.0), 0.25
        );

        assertTrue(beam.intersects(new EffectBounds(1.5, 0.1, -0.1, 2.0, 0.4, 0.1)));
        assertFalse(beam.intersects(new EffectBounds(1.5, 0.5, -0.1, 2.0, 0.8, 0.1)));
        assertFalse(beam.intersects(new EffectBounds(5.0, -0.1, -0.1, 5.2, 0.1, 0.1)));
        assertFalse(beam.intersects(new EffectBounds(2.0, 0.24, 0.24, 2.1, 0.3, 0.3)));
    }

    @Test
    void validatesShapeDimensions() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDamageShape.Sphere(EffectPoint.ZERO, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDamageShape.Cylinder(EffectPoint.ZERO, 1.0, -1.0));
    }
}
