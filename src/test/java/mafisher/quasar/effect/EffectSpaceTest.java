package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.EffectTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EffectSpaceTest {
    private static final EffectPoint CAMERA = new EffectPoint(10.0, 20.0, 30.0);

    @Test
    void identityBasisIsTheWorldAlignedFrame() {
        EffectSpace.Basis basis = EffectSpace.Basis.fromEuler(0.0F, 0.0F, 0.0F);

        assertVector(1.0F, 0.0F, 0.0F, basis.right());
        assertVector(0.0F, 1.0F, 0.0F, basis.up());
        assertVector(0.0F, 0.0F, 1.0F, basis.forward());
    }

    @Test
    void basisStaysOrthonormalAtArbitraryAngles() {
        EffectSpace.Basis basis = EffectSpace.Basis.fromEuler(37.0F, 8.0F, -12.0F);

        assertUnit(basis.right());
        assertUnit(basis.up());
        assertUnit(basis.forward());
        assertEquals(0.0F, basis.right().dot(basis.up()), 1.0E-5F);
        assertEquals(0.0F, basis.right().dot(basis.forward()), 1.0E-5F);
        assertEquals(0.0F, basis.up().dot(basis.forward()), 1.0E-5F);
    }

    @Test
    void rotationComposesYawPitchAndRollInTheEngineOrder() {
        Matrix4f expected = new Matrix4f()
            .rotateY((float)Math.toRadians(37.0))
            .rotateX((float)Math.toRadians(8.0))
            .rotateZ((float)Math.toRadians(-12.0));

        assertMatrix(expected, EffectSpace.Basis.fromEuler(37.0F, 8.0F, -12.0F).rotation(), 1.0E-5F);
    }

    @Test
    void basisFollowsTheEffectYawAndPitch() {
        EffectSpace.Basis yawed = EffectSpace.Basis.fromEuler(90.0F, 0.0F, 0.0F);
        assertVector(0.0F, 0.0F, -1.0F, yawed.right());
        assertVector(1.0F, 0.0F, 0.0F, yawed.forward());
        assertVector(0.0F, 1.0F, 0.0F, yawed.up());

        EffectSpace.Basis pitched = EffectSpace.Basis.fromEuler(0.0F, 8.0F, 0.0F);
        assertVector(0.0F, (float)Math.cos(Math.toRadians(8.0)),
            (float)Math.sin(Math.toRadians(8.0)), pitched.up());
    }

    @Test
    void cameraRelativeOriginUsesDoublePrecisionBeforeNarrowing() {
        EffectTransform transform = new EffectTransform(
            new EffectPoint(30_000_010.0, 64.0, -30_000_020.0), 0.0F, 0.0F, 0.0F);
        EffectPoint camera = new EffectPoint(30_000_000.0, 64.0, -30_000_000.0);

        EffectSpace space = EffectSpace.from(transform, 4.0F, camera);

        assertEquals(10.0, space.cameraRelativeOrigin().x(), 0.0);
        assertEquals(0.0, space.cameraRelativeOrigin().y(), 0.0);
        assertEquals(-20.0, space.cameraRelativeOrigin().z(), 0.0);
        assertEquals(4.0F, space.scale());
    }

    @Test
    void pointAndDirectionConversionsRoundTrip() {
        EffectSpace space = EffectSpace.from(new EffectTransform(
            new EffectPoint(4.0, 5.0, 6.0), 37.0F, 8.0F, -12.0F), 2.0F, CAMERA);
        EffectPoint world = new EffectPoint(9.0, 11.0, -3.0);

        EffectPoint relative = space.toCameraRelative(world);
        EffectPoint roundTrip = space.toWorld(relative);

        assertEquals(world.x(), roundTrip.x(), 1.0E-9);
        assertEquals(world.y(), roundTrip.y(), 1.0E-9);
        assertEquals(world.z(), roundTrip.z(), 1.0E-9);

        Vector3f local = new Vector3f(0.3F, -0.4F, 1.2F);
        Vector3f worldDirection = space.localDirectionToWorld(local);
        Vector3f localAgain = space.worldDirectionToLocal(worldDirection);
        assertEquals(local.x, localAgain.x, 1.0E-5F);
        assertEquals(local.y, localAgain.y, 1.0E-5F);
        assertEquals(local.z, localAgain.z, 1.0E-5F);
    }

    @Test
    void rejectsInvalidScaleAndNonFiniteAngles() {
        EffectTransform transform = new EffectTransform(EffectPoint.ZERO, 0.0F, 0.0F, 0.0F);

        assertThrows(IllegalArgumentException.class,
            () -> EffectSpace.from(transform, 0.0F, CAMERA));
        assertThrows(IllegalArgumentException.class,
            () -> EffectSpace.from(transform, Float.NaN, CAMERA));
        assertThrows(IllegalArgumentException.class,
            () -> EffectSpace.Basis.fromEuler(Float.NaN, 0.0F, 0.0F));
    }

    private static void assertUnit(Vector3f value) {
        assertEquals(1.0F, value.length(), 1.0E-5F);
    }

    private static void assertVector(float x, float y, float z, Vector3f value) {
        assertEquals(x, value.x, 1.0E-5F);
        assertEquals(y, value.y, 1.0E-5F);
        assertEquals(z, value.z, 1.0E-5F);
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual, float epsilon) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), epsilon,
                    "matrix element [" + row + ", " + column + "]");
            }
        }
    }
}
