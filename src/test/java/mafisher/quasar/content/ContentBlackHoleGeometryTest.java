package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ContentBlackHoleGeometryTest {
    @Test
    void identityBasisIsTheWorldAlignedDiskFrame() {
        ContentBlackHoleGeometry.Basis basis = ContentBlackHoleGeometry.basis(0.0F, 0.0F, 0.0F);

        assertVector(1.0F, 0.0F, 0.0F, basis.right());
        assertVector(0.0F, 0.0F, 1.0F, basis.forward());
        assertVector(0.0F, 1.0F, 0.0F, basis.normal());
    }

    @Test
    void basisStaysOrthonormalAndFiniteAtArbitraryAngles() {
        ContentBlackHoleGeometry.Basis basis = ContentBlackHoleGeometry.basis(37.0F, 8.0F, -12.0F);

        assertUnit(basis.right());
        assertUnit(basis.forward());
        assertUnit(basis.normal());
        assertEquals(0.0F, basis.right().dot(basis.forward()), 1.0E-5F);
        assertEquals(0.0F, basis.right().dot(basis.normal()), 1.0E-5F);
        assertEquals(0.0F, basis.forward().dot(basis.normal()), 1.0E-5F);
    }

    @Test
    void rotationComposesYawPitchAndRollInTheEngineOrder() {
        Matrix4f expected = new Matrix4f()
            .rotateY((float)Math.toRadians(37.0))
            .rotateX((float)Math.toRadians(8.0))
            .rotateZ((float)Math.toRadians(-12.0));

        assertMatrix(expected, ContentBlackHoleGeometry.rotation(37.0F, 8.0F, -12.0F), 1.0E-5F);
    }

    @Test
    void basisFollowsTheEffectYawAndPitch() {
        ContentBlackHoleGeometry.Basis yawed = ContentBlackHoleGeometry.basis(90.0F, 0.0F, 0.0F);
        assertVector(0.0F, 0.0F, -1.0F, yawed.right());
        assertVector(1.0F, 0.0F, 0.0F, yawed.forward());
        assertVector(0.0F, 1.0F, 0.0F, yawed.normal());

        ContentBlackHoleGeometry.Basis pitched = ContentBlackHoleGeometry.basis(0.0F, 8.0F, 0.0F);
        assertVector(0.0F, (float)Math.cos(Math.toRadians(8.0)),
            (float)Math.sin(Math.toRadians(8.0)), pitched.normal());
    }

    @Test
    void maskFacingKeepsTheQuadAlignedWithTheCameraAxes() {
        Quaternionf camera = new Quaternionf().rotationYXZ(
            (float)(Math.PI - Math.toRadians(120.0)),
            (float)(-Math.toRadians(20.0)),
            (float)(-Math.toRadians(5.0))
        );
        float yaw = 40.0F;
        float pitch = 8.0F;
        float roll = -12.0F;

        Matrix4f composed = new Matrix4f(ContentBlackHoleGeometry.rotation(yaw, pitch, roll))
            .mul(ContentBlackHoleGeometry.maskFacing(yaw, pitch, roll, camera));

        // A screen-aligned mask means the effect rotation and the facing cancel out to exactly the camera basis.
        assertMatrix(new Matrix4f().rotation(camera), composed, 1.0E-5F);
    }

    @Test
    void stepBudgetKeepsATraversalFloorAndScalesWithQuality() {
        assertEquals(64, ContentBlackHoleGeometry.stepCount(0, 0));
        assertEquals(80, ContentBlackHoleGeometry.stepCount(0, 1));
        assertEquals(96, ContentBlackHoleGeometry.stepCount(0, 2));
        assertEquals(112, ContentBlackHoleGeometry.stepCount(0, 3));
        assertEquals(64, ContentBlackHoleGeometry.stepCount(1, 0));
        assertEquals(64, ContentBlackHoleGeometry.stepCount(8, 0));
        assertEquals(96, ContentBlackHoleGeometry.stepCount(2, 3));
        assertTrue(ContentBlackHoleGeometry.stepCount(2, 3)
            < ContentBlackHoleGeometry.stepCount(1, 3));
    }

    @Test
    void variantSeedPreservesTheLowPayloadAndSelectsThePalette() {
        long payload = 0x0123_4567_89AB_CDEFL;
        long goldSeed = ContentBlackHoleGeometry.withVariant(payload, ContentBlackHoleGeometry.VARIANT_GOLD);
        long neonSeed = ContentBlackHoleGeometry.withVariant(payload, ContentBlackHoleGeometry.VARIANT_NEON);

        assertEquals(ContentBlackHoleGeometry.VARIANT_GOLD, ContentBlackHoleGeometry.variant(goldSeed));
        assertEquals(ContentBlackHoleGeometry.VARIANT_NEON, ContentBlackHoleGeometry.variant(neonSeed));
        assertEquals(payload & 0x0FFF_FFFF_FFFF_FFFFL, goldSeed & 0x0FFF_FFFF_FFFF_FFFFL);
        assertEquals(payload & 0x0FFF_FFFF_FFFF_FFFFL, neonSeed & 0x0FFF_FFFF_FFFF_FFFFL);
        assertEquals(0.0F, ContentBlackHoleGeometry.chargeForSeed(goldSeed, 0.55F), 0.0001F);
        assertEquals(1.0F, ContentBlackHoleGeometry.chargeForSeed(neonSeed, 0.55F), 0.0001F);
        assertEquals(0.55F, ContentBlackHoleGeometry.chargeForSeed(payload, 0.55F), 0.0001F);
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleGeometry.withVariant(payload, 3));
    }

    @Test
    void fadeEnvelopeStartsAndEndsAtZero() {
        assertEquals(0.0F, ContentBlackHoleGeometry.fadeEnvelope(0.0F), 1.0E-6F);
        assertEquals(1.0F, ContentBlackHoleGeometry.fadeEnvelope(0.08F), 1.0E-6F);
        assertEquals(1.0F, ContentBlackHoleGeometry.fadeEnvelope(0.5F), 1.0E-6F);
        assertEquals(0.5F, ContentBlackHoleGeometry.fadeEnvelope(0.94F), 1.0E-6F);
        assertEquals(0.0F, ContentBlackHoleGeometry.fadeEnvelope(1.0F), 1.0E-6F);
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
