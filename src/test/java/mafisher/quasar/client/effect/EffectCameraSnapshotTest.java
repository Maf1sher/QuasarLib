package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectCameraSnapshotTest {
    @Test
    void copiesMatricesAndDerivesInverses() {
        Matrix4f view = new Matrix4f().translate(1.0F, 2.0F, 3.0F);
        Matrix4f projection = new Matrix4f().scale(2.0F);
        EffectCameraSnapshot snapshot = new EffectCameraSnapshot(
            new Vec3(1.0, 2.0, 3.0), view, projection, 1920, 1080);

        view.identity();
        projection.identity();

        Matrix4f inverseView = snapshot.inverseViewMatrix();
        assertEquals(-1.0F, inverseView.m30(), 1.0E-5F);
        assertEquals(-2.0F, inverseView.m31(), 1.0E-5F);
        assertEquals(-3.0F, inverseView.m32(), 1.0E-5F);
        assertEquals(0.5F, snapshot.inverseProjectionMatrix().m00(), 1.0E-5F);
    }

    @Test
    void rejectsInvalidViewportAndDegenerateMatrices() {
        Matrix4f identity = new Matrix4f();
        assertThrows(IllegalArgumentException.class,
            () -> new EffectCameraSnapshot(Vec3.ZERO, identity, identity, 0, 1080));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectCameraSnapshot(Vec3.ZERO, new Matrix4f().zero(), identity, 1920, 1080));
    }

    @Test
    void reportsDegenerateMatricesBeforeConstruction() {
        assertTrue(EffectCameraSnapshot.isUsable(new Matrix4f(), new Matrix4f()));
        assertFalse(EffectCameraSnapshot.isUsable(new Matrix4f().zero(), new Matrix4f()));
        assertFalse(EffectCameraSnapshot.isUsable(new Matrix4f(), new Matrix4f().zero()));
    }
}
