package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws one static cached world mesh. GL resources live in the shared {@link GpuMeshCache}; this class only binds
 * render state and issues one draw.
 */
final class CachedMeshRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedMeshRenderer.class);

    private final GpuMeshCache meshes;
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f cameraRelativeView = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();

    CachedMeshRenderer(GpuMeshCache meshes) {
        this.meshes = Objects.requireNonNull(meshes, "meshes");
    }

    /**
     * Draws one pass in the camera-relative frame. The model matrix matches the instanced path so batching never
     * changes the transform.
     */
    void drawSingle(EffectStaticMeshPass pass, EffectTransform transform, Vec3 camera,
        Matrix4f viewMatrix, Matrix4f projection) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(viewMatrix, "viewMatrix");
        Objects.requireNonNull(projection, "projection");

        this.cameraRelativeView.set(viewMatrix).setTranslation(0.0F, 0.0F, 0.0F);
        EffectSpace.Basis.rotationInto(transform.yaw(), transform.pitch(), transform.roll(),
            this.rotation);
        this.modelView.set(this.cameraRelativeView)
            .translate(
                (float)(transform.position().x() - camera.x),
                (float)(transform.position().y() - camera.y),
                (float)(transform.position().z() - camera.z)
            )
            .mul(this.rotation)
            .mul(pass.localTransform());

        GpuMeshCache.GpuMesh mesh = this.meshes.get(pass.renderType(), pass.mesh(),
            pass.red(), pass.green(), pass.blue(), pass.alpha(), pass.overlay(), pass.light());
        if (mesh == null) {
            return;
        }

        RenderType renderType = pass.renderType();
        ShaderInstance shader = null;
        boolean renderStateSetUp = false;
        float[] shaderColor = RenderSystem.getShaderColor();
        float previousRed = shaderColor[0];
        float previousGreen = shaderColor[1];
        float previousBlue = shaderColor[2];
        float previousAlpha = shaderColor[3];
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            renderType.setupRenderState();
            renderStateSetUp = true;
            shader = RenderSystem.getShader();
            if (shader == null) {
                LOGGER.warn("Static mesh pass {} has no active shader", pass.id());
                return;
            }
            mesh.bind();
            mesh.drawWithShader(this.modelView, projection, shader);
        } finally {
            GpuMeshCache.GpuMesh.unbind();
            if (renderStateSetUp) {
                renderType.clearRenderState();
            }
            RenderSystem.setShaderColor(previousRed, previousGreen, previousBlue, previousAlpha);
        }
    }
}
