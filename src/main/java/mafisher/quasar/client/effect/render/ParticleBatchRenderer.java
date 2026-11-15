package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.compat.SceneDepthFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL31C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws stateless particle fields with one instanced quad per field. The renderer
 * uploads no per-particle data: the vertex shader derives every particle from the
 * field seed, the effect age, and {@code gl_InstanceID}.
 */
@ApiStatus.Internal
@SuppressWarnings("PMD.TooManyMethods")
final class ParticleBatchRenderer {
    private static final ResourceLocation WHITE_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleBatchRenderer.class);

    private final GpuMeshCache meshes;
    private final ShaderUniformCache uniforms = new ShaderUniformCache();
    private final Set<String> warnedContracts = new HashSet<>();

    ParticleBatchRenderer(GpuMeshCache meshes) {
        this.meshes = meshes;
    }

    void draw(List<ParticleBatchBuilder.Draw> draws, Matrix4f modelView, Matrix4f projection,
        @Nullable EffectCameraSnapshot camera, @Nullable SceneDepthFrame depthFrame) {
        if (draws.isEmpty()) {
            return;
        }
        Matrix4f inverseView = new Matrix4f(modelView).invert();
        Matrix4f depthProjection = depthFrame == null ? projection : depthFrame.projection();
        Matrix4f inverseDepthProjection = new Matrix4f(depthProjection).invert();
        int depthTexture = depthFrame == null ? 0 : depthFrame.target().getDepthTextureId();
        int depthSampler = depthTexture > 0 ? depthTexture : whiteTextureId();
        float softDepth = depthTexture > 0 ? 1.0F : 0.0F;
        int viewportWidth = camera == null
            ? Minecraft.getInstance().getMainRenderTarget().width : camera.viewportWidth();
        int viewportHeight = camera == null
            ? Minecraft.getInstance().getMainRenderTarget().height : camera.viewportHeight();
        Matrix4f view = new Matrix4f(modelView);
        Minecraft minecraft = Minecraft.getInstance();
        for (ParticleBatchBuilder.Draw draw : draws) {
            RenderType renderType = EffectRenderTypes.particleWorld(
                draw.field().appearance().texture(), draw.field().appearance().blend()
            );
            GpuMeshCache.GpuMesh mesh = this.meshes.get(renderType, EffectMeshes.verticalPlane());
            if (mesh != null) {
                this.drawOne(draw, renderType, mesh, view, projection, inverseView,
                    inverseDepthProjection, depthSampler, softDepth, viewportWidth, viewportHeight,
                    minecraft);
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void drawOne(ParticleBatchBuilder.Draw draw, RenderType renderType,
        GpuMeshCache.GpuMesh mesh, Matrix4f view, Matrix4f projection, Matrix4f inverseView,
        Matrix4f inverseDepthProjection, int depthSampler, float softDepth, int viewportWidth,
        int viewportHeight, Minecraft minecraft) {
        ShaderInstance shader = null;
        boolean stateSetUp = false;
        float[] shaderColor = RenderSystem.getShaderColor();
        float red = shaderColor[0];
        float green = shaderColor[1];
        float blue = shaderColor[2];
        float alpha = shaderColor[3];
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            renderType.setupRenderState();
            stateSetUp = true;
            shader = RenderSystem.getShader();
            if (shader == null) {
                warnOnce("shader:" + draw.id(), "Particle pass " + draw.id() + " has no active shader");
                return;
            }
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection,
                minecraft.getWindow());
            bindUniforms(shader, draw, softDepth, inverseView, inverseDepthProjection,
                viewportWidth, viewportHeight);
            shader.setSampler("ParticleDepthSampler", depthSampler);
            shader.apply();
            mesh.bind();
            RenderSystem.AutoStorageIndexBuffer indices = mesh.bindSequentialIndices();
            GL31C.glDrawElementsInstanced(VertexFormat.Mode.QUADS.asGLMode, mesh.indexCount(),
                indices.type().asGLType, 0L, draw.count());
        } catch (RuntimeException exception) {
            warnOnce("bind:" + draw.id(), "Could not draw particle pass " + draw.id() + ": "
                + exception.getMessage());
        } finally {
            GpuMeshCache.GpuMesh.unbind();
            if (shader != null) {
                shader.clear();
            }
            if (stateSetUp) {
                renderType.clearRenderState();
            }
            RenderSystem.setShaderColor(red, green, blue, alpha);
        }
    }

    private void bindUniforms(ShaderInstance shader, ParticleBatchBuilder.Draw draw,
        float softDepth, Matrix4f inverseView, Matrix4f inverseDepthProjection, int viewportWidth,
        int viewportHeight) {
        EffectParticleField field = draw.field();
        setMat4(shader, "ParticleTransform", draw.model());
        setInt(shader, "ParticleSeedLow", (int)draw.seed());
        setInt(shader, "ParticleSeedHigh", (int)(draw.seed() >>> 32));
        setFloat(shader, "ParticleAge", draw.ageTicks());
        setFloat(shader, "ParticleLifetime", field.lifetimeTicks());
        setInt(shader, "ParticleMode", field.mode() == EffectParticleField.Mode.BURST ? 1 : 0);
        bindShape(shader, field.shape());
        bindMotion(shader, field.motion());
        bindAppearance(shader, field.appearance(), draw.scale());
        setFloat(shader, "SoftDepthEnabled", softDepth);
        setMat4(shader, "InvViewMat", inverseView);
        setMat4(shader, "InvProjMat", inverseDepthProjection);
        setFloat(shader, "ViewportWidth", viewportWidth);
        setFloat(shader, "ViewportHeight", viewportHeight);
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private void bindShape(ShaderInstance shader, EffectParticleShape shape) {
        int type;
        float radiusMin = 0.0F;
        float radiusMax = 0.0F;
        float flags = 0.0F;
        float ax = 0.0F;
        float ay = 0.0F;
        float az = 0.0F;
        float bx = 0.0F;
        float by = 0.0F;
        float bz = 0.0F;
        switch (shape) {
            case EffectParticleShape.Point point -> type = 0;
            case EffectParticleShape.Sphere sphere -> {
                type = 1;
                ax = (float)sphere.center().x;
                ay = (float)sphere.center().y;
                az = (float)sphere.center().z;
                radiusMin = (float)sphere.minRadius();
                radiusMax = (float)sphere.maxRadius();
                flags = sphere.surface() ? 1.0F : 0.0F;
            }
            case EffectParticleShape.Box box -> {
                type = 2;
                ax = (float)box.min().x;
                ay = (float)box.min().y;
                az = (float)box.min().z;
                bx = (float)box.max().x;
                by = (float)box.max().y;
                bz = (float)box.max().z;
            }
            case EffectParticleShape.Disk disk -> {
                type = 3;
                ax = (float)disk.center().x;
                ay = (float)disk.center().y;
                az = (float)disk.center().z;
                bx = (float)disk.normal().x;
                by = (float)disk.normal().y;
                bz = (float)disk.normal().z;
                radiusMin = (float)disk.minRadius();
                radiusMax = (float)disk.maxRadius();
            }
            case EffectParticleShape.Line line -> {
                type = 4;
                ax = (float)line.start().x;
                ay = (float)line.start().y;
                az = (float)line.start().z;
                bx = (float)line.end().x;
                by = (float)line.end().y;
                bz = (float)line.end().z;
                radiusMin = (float)line.radius();
            }
        }
        setInt(shader, "ParticleShape", type);
        setVec3(shader, "ParticleShapeA", ax, ay, az);
        setVec3(shader, "ParticleShapeB", bx, by, bz);
        setFloat(shader, "ParticleShapeR0", radiusMin);
        setFloat(shader, "ParticleShapeR1", radiusMax);
        setFloat(shader, "ParticleShapeFlags", flags);
    }

    private void bindMotion(ShaderInstance shader, EffectParticleMotion motion) {
        setInt(shader, "ParticleVelocityMode", motion.mode().ordinal());
        setVec3(shader, "ParticleDirection", (float)motion.direction().x,
            (float)motion.direction().y, (float)motion.direction().z);
        setVec2(shader, "ParticleSpeed", (float)motion.speed().min(),
            (float)motion.speed().max());
        setFloat(shader, "ParticleSpread", (float)motion.spread());
        setFloat(shader, "ParticleGravity", (float)motion.gravity());
        setFloat(shader, "ParticleDrag", (float)motion.drag());
        setFloat(shader, "ParticleTurbulence", (float)motion.turbulence());
    }

    private void bindAppearance(ShaderInstance shader, EffectParticleAppearance appearance,
        float scale) {
        setVec2(shader, "ParticleSize",
            (float)(appearance.size().start() * scale),
            (float)(appearance.size().end() * scale));
        setColor(shader, "ParticleColorStart", appearance.colorStart());
        setColor(shader, "ParticleColorEnd", appearance.colorEnd());
        setFloat(shader, "ParticleSoftness", (float)(appearance.softness() * scale));
        setFloat(shader, "ParticleAdditive",
            appearance.blend() == EffectParticleAppearance.Blend.ADDITIVE ? 1.0F : 0.0F);
    }

    private void setFloat(ShaderInstance shader, String name, float value) {
        uniform(shader, name).set(value);
    }

    private void setInt(ShaderInstance shader, String name, int value) {
        uniform(shader, name).set(value);
    }

    private void setVec2(ShaderInstance shader, String name, float x, float y) {
        uniform(shader, name).set(x, y);
    }

    private void setVec3(ShaderInstance shader, String name, float x, float y, float z) {
        uniform(shader, name).set(x, y, z);
    }

    private void setColor(ShaderInstance shader, String name, int argb) {
        uniform(shader, name).set(
            (argb >> 16 & 0xFF) / 255.0F,
            (argb >> 8 & 0xFF) / 255.0F,
            (argb & 0xFF) / 255.0F,
            (argb >>> 24 & 0xFF) / 255.0F
        );
    }

    private void setMat4(ShaderInstance shader, String name, Matrix4f value) {
        uniform(shader, name).set(value);
    }

    private AbstractUniform uniform(ShaderInstance shader, String name) {
        AbstractUniform uniform = this.uniforms.get(shader, name);
        if (uniform == null) {
            throw new IllegalStateException("Particle shader is missing uniform " + name);
        }
        return uniform;
    }

    private int whiteTextureId() {
        return Minecraft.getInstance().getTextureManager().getTexture(WHITE_TEXTURE).getId();
    }

    private void warnOnce(String key, String message) {
        if (this.warnedContracts.add(key)) {
            LOGGER.warn(message);
        }
    }

    /** Drops shader-keyed uniform handles after a resource reload. */
    void onResourceReload() {
        this.uniforms.clear();
        this.warnedContracts.clear();
    }

    /** Clears diagnostic state; the shared mesh cache is owned by the manager. */
    void close() {
        this.onResourceReload();
    }
}
