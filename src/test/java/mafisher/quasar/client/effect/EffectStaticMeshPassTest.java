package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectStaticMeshPassTest {
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.worldVisual(
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/cached.png")
    );

    @Test
    void defaultsToNoOverlayAndFullbrightLight() {
        EffectStaticMeshPass pass = new EffectStaticMeshPass("core", RENDER_TYPE,
            EffectMeshes.horizontalQuad(), new Matrix4f(), 1.0F, 0.5F, 0.25F, 0.75F);

        assertEquals(OverlayTexture.NO_OVERLAY, pass.overlay());
        assertEquals(EffectStaticMeshPass.FULLBRIGHT_LIGHT, pass.light());
    }

    @Test
    void keepsItsOwnLocalTransform() {
        Matrix4f local = new Matrix4f().translate(1.0F, 2.0F, 3.0F);

        EffectStaticMeshPass pass = new EffectStaticMeshPass("core", RENDER_TYPE,
            EffectMeshes.horizontalQuad(), local, 1.0F, 1.0F, 1.0F, 1.0F);
        local.translate(10.0F, 0.0F, 0.0F);

        assertNotSame(local, pass.localTransform());
        assertEquals(1.0F, pass.localTransform().m30());
    }

    @Test
    void rejectsInvalidPasses() {
        assertThrows(IllegalArgumentException.class, () -> new EffectStaticMeshPass(" ", RENDER_TYPE,
            EffectMeshes.horizontalQuad(), new Matrix4f(), 1.0F, 1.0F, 1.0F, 1.0F));
        assertThrows(NullPointerException.class, () -> new EffectStaticMeshPass("core", null,
            EffectMeshes.horizontalQuad(), new Matrix4f(), 1.0F, 1.0F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new EffectStaticMeshPass("core", RENDER_TYPE,
            EffectMeshes.horizontalQuad(), new Matrix4f(), Float.NaN, 1.0F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new EffectStaticMeshPass("core", RENDER_TYPE,
            EffectMeshes.horizontalQuad(), new Matrix4f(), 1.0F, 1.0F, 1.0F, Float.POSITIVE_INFINITY));
    }
}
