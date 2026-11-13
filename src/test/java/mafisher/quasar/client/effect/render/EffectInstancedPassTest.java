package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectInstancedPassTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/instanced.png"
    );
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(TEXTURE);

    @Test
    void copiesTheLocalTransformAndExposesValues() {
        Matrix4f local = new Matrix4f().scale(2.0F);
        EffectInstancedPass pass = pass(local);

        assertNotSame(local, pass.localTransform());
        local.zero();
        assertEquals(2.0F, pass.localTransform().m00());
        assertEquals(2.0F, pass.localTransform().m11());
        assertEquals(2.0F, pass.localTransform().m22());
        assertEquals(0.2F, pass.red());
        assertEquals(0.8F, pass.effectDataX());
    }

    @Test
    void validatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> new EffectInstancedPass(
            " ", RENDER_TYPE, EffectMeshes.horizontalQuad(), new Matrix4f(), 1, 1, 1, 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new EffectInstancedPass(
            "pass", null, EffectMeshes.horizontalQuad(), new Matrix4f(), 1, 1, 1, 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new EffectInstancedPass(
            "pass", RENDER_TYPE, null, new Matrix4f(), 1, 1, 1, 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new EffectInstancedPass(
            "pass", RENDER_TYPE, EffectMeshes.horizontalQuad(), null, 1, 1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectInstancedPass(
            "pass", RENDER_TYPE, EffectMeshes.horizontalQuad(), new Matrix4f(), Float.NaN, 1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectInstancedPass(
            "pass", RENDER_TYPE, EffectMeshes.horizontalQuad(), new Matrix4f(), 1, 1, 1, 1, 0, Float.NaN));
    }

    @Test
    void composesWorldAndLocalTransforms() {
        Matrix4f model = InstancedBatchBuilder.modelMatrix(
            new EffectTransform(new EffectPoint(10.0, 20.0, 30.0), 90.0F, 0.0F, 0.0F),
            new Matrix4f().scale(2.0F)
        );

        var origin = model.transformPosition(new org.joml.Vector3f(0.0F, 0.0F, 0.0F));
        var axis = model.transformPosition(new org.joml.Vector3f(1.0F, 0.0F, 0.0F));

        assertEquals(10.0, origin.x, 1.0E-5);
        assertEquals(20.0, origin.y, 1.0E-5);
        assertEquals(30.0, origin.z, 1.0E-5);
        assertEquals(10.0, axis.x, 1.0E-5);
        assertEquals(20.0, axis.y, 1.0E-5);
        assertEquals(28.0, axis.z, 1.0E-5);
        assertEquals(2.0, axis.distance(origin), 1.0E-5);
        assertTrue(model.isFinite());
    }

    private static EffectInstancedPass pass(Matrix4f local) {
        return new EffectInstancedPass("pass", RENDER_TYPE, EffectMeshes.horizontalQuad(), local,
            0.2F, 0.4F, 0.6F, 0.8F, 0.8F, 0.1F);
    }
}
