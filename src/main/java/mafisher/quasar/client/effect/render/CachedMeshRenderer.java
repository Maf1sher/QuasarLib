package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mafisher.quasar.api.client.EffectStaticMeshPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws static cached world meshes declared by {@link StaticMeshVisualEffectRenderer}. GL resources live in the
 * shared {@link GpuMeshCache}; this class only binds render state and issues one draw per pass.
 */
final class CachedMeshRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedMeshRenderer.class);

    private final GpuMeshCache meshes;
    private final Set<String> passIds = new HashSet<>();
    private final Matrix4f modelView = new Matrix4f();

    CachedMeshRenderer(GpuMeshCache meshes) {
        this.meshes = meshes;
    }

    /**
     * Draws the passes with the current pose already positioned at the effect transform.
     *
     * @param viewMatrix the render-thread view matrix, without the camera translation applied by the pose stack
     */
    void draw(List<EffectStaticMeshPass> passes, PoseStack poseStack, Matrix4f viewMatrix, Matrix4f projection) {
        this.passIds.clear();
        for (EffectStaticMeshPass pass : passes) {
            if (!this.passIds.add(pass.id())) {
                throw new IllegalStateException("Duplicate static mesh pass ID '" + pass.id() + "'");
            }
        }
        float[] shaderColor = RenderSystem.getShaderColor();
        float previousRed = shaderColor[0];
        float previousGreen = shaderColor[1];
        float previousBlue = shaderColor[2];
        float previousAlpha = shaderColor[3];
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            for (EffectStaticMeshPass pass : passes) {
                GpuMeshCache.GpuMesh mesh = this.meshes.get(pass.renderType(), pass.mesh(),
                    pass.red(), pass.green(), pass.blue(), pass.alpha(), pass.overlay(), pass.light());
                if (mesh == null) {
                    continue;
                }

                RenderType renderType = pass.renderType();
                ShaderInstance shader = null;
                renderType.setupRenderState();
                try {
                    shader = RenderSystem.getShader();
                    if (shader == null) {
                        LOGGER.warn("Static mesh pass {} has no active shader", pass.id());
                        continue;
                    }
                    this.modelView.set(viewMatrix)
                        .mul(poseStack.last().pose())
                        .mul(pass.localTransform());
                    mesh.bind();
                    mesh.drawWithShader(this.modelView, projection, shader);
                } finally {
                    GpuMeshCache.GpuMesh.unbind();
                    renderType.clearRenderState();
                }
            }
        } finally {
            RenderSystem.setShaderColor(previousRed, previousGreen, previousBlue, previousAlpha);
        }
    }
}
