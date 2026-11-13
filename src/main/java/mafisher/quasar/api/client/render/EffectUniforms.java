package mafisher.quasar.api.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import java.util.EnumSet;
import java.util.Objects;
import mafisher.quasar.api.client.screen.ScreenEffectCameraState;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Validated standard uniform groups for effect shaders.
 *
 * <p>A shader binds only the groups it declares. Every uniform in a requested group is
 * mandatory: a missing declaration throws at bind time instead of silently writing a zero.
 * The five groups are the supported contract; effect-specific uniforms stay separate.</p>
 *
 * <p>{@link #from(EffectRenderContext)} and {@link #from(ScreenEffectOperationContext)}
 * snapshot the current frame once per bind, so callers do not hand-copy camera, space,
 * effect, light, or quality values.</p>
 */
public record EffectUniforms(
    Camera camera,
    Space space,
    Effect effect,
    Light light,
    Quality quality
) {
    public EffectUniforms {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(space, "space");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(quality, "quality");
    }

    /** Builds standard groups from a world-render context. */
    public static EffectUniforms from(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        EffectCameraSnapshot camera = context.camera();
        if (camera == null) {
            throw new IllegalStateException("EffectRenderContext has no camera snapshot");
        }
        return new EffectUniforms(
            Camera.from(camera),
            new Space(context.effectSpace()),
            new Effect(context.ageTicks(), context.progress(), normalizedSeed(context.seed())),
            new Light(context.daylight()),
            new Quality(context.quality().level(), context.lod().level())
        );
    }

    /** Builds standard groups from a screen-operation context. */
    public static EffectUniforms from(ScreenEffectOperationContext context) {
        Objects.requireNonNull(context, "context");
        EffectRenderContext renderContext = context.effect();
        return new EffectUniforms(
            Camera.from(context.camera()),
            new Space(context.effectSpace()),
            new Effect(renderContext.ageTicks(), renderContext.progress(),
                normalizedSeed(renderContext.seed())),
            new Light(context.daylight()),
            new Quality(renderContext.quality().level(), renderContext.lod().level())
        );
    }

    /** Binds every standard group. Use the selective overload for a shader that declares a subset. */
    public void bind(ShaderInstance shader) {
        this.bind(shader, Group.values());
    }

    /** Binds the requested groups, failing when any of their uniforms is not declared. */
    public void bind(ShaderInstance shader, Group... groups) {
        Objects.requireNonNull(shader, "shader");
        this.bind(new ShaderSink(shader), groups);
    }

    /** Binds every standard group through the context's cached uniform handles. */
    public void bind(ScreenEffectOperationContext context, ShaderInstance shader) {
        this.bind(context, shader, Group.values());
    }

    /** Binds the requested groups through the context's cached uniform handles. */
    public void bind(ScreenEffectOperationContext context, ShaderInstance shader, Group... groups) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shader, "shader");
        this.bind(new ContextSink(context, shader), groups);
    }

    /** Package-private seam used by headless tests. */
    void bind(UniformSink sink, Group... groups) {
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(groups, "groups");
        EnumSet<Group> requested = EnumSet.noneOf(Group.class);
        for (Group group : groups) {
            requested.add(Objects.requireNonNull(group, "group"));
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("At least one uniform group is required");
        }
        if (requested.contains(Group.CAMERA)) {
            bindCamera(sink, this.camera);
        }
        if (requested.contains(Group.SPACE)) {
            bindSpace(sink, this.space);
        }
        if (requested.contains(Group.EFFECT)) {
            bindEffect(sink, this.effect);
        }
        if (requested.contains(Group.LIGHT)) {
            sink.setFloat("Daylight", this.light.daylight());
        }
        if (requested.contains(Group.QUALITY)) {
            sink.setFloat("QualityLevel", this.quality.qualityLevel());
            sink.setFloat("LodLevel", this.quality.lodLevel());
        }
    }

    private static void bindCamera(UniformSink sink, Camera camera) {
        sink.setMat4("ViewMat", camera.modelViewMatrix());
        sink.setMat4("WorldProjMat", camera.projectionMatrix());
        sink.setMat4("InvProjMat", camera.inverseProjectionMatrix());
        sink.setMat4("InvViewMat", camera.inverseViewMatrix());
        sink.setFloat("ViewportWidth", camera.viewportWidth());
        sink.setFloat("ViewportHeight", camera.viewportHeight());
    }

    private static void bindSpace(UniformSink sink, Space space) {
        EffectSpace value = space.space();
        EffectPoint origin = value.cameraRelativeOrigin();
        float x = (float)origin.x();
        float y = (float)origin.y();
        float z = (float)origin.z();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Camera-relative effect origin must fit in a float uniform");
        }
        sink.setVec3("EffectOrigin", x, y, z);
        var basis = value.basis();
        var right = basis.right();
        var up = basis.up();
        var forward = basis.forward();
        sink.setVec3("EffectRight", right.x, right.y, right.z);
        sink.setVec3("EffectUp", up.x, up.y, up.z);
        sink.setVec3("EffectForward", forward.x, forward.y, forward.z);
    }

    private static void bindEffect(UniformSink sink, Effect effect) {
        sink.setFloat("AgeTicks", effect.ageTicks());
        sink.setFloat("Progress", effect.progress());
        sink.setFloat("Seed", effect.seed());
    }

    private static float normalizedSeed(long seed) {
        return (float)((seed & 0xFFFFL) / 65535.0);
    }

    /** A shader-declared group. Requesting a group makes all of its uniforms mandatory. */
    public enum Group {
        CAMERA,
        SPACE,
        EFFECT,
        LIGHT,
        QUALITY
    }

    /** Headless writer seam; production implementations validate against a shader. */
    interface UniformSink {
        void setFloat(String name, float value);

        void setVec3(String name, float x, float y, float z);

        void setMat4(String name, Matrix4f value);
    }

    private static final class ShaderSink implements UniformSink {
        private final ShaderInstance shader;

        private ShaderSink(ShaderInstance shader) {
            this.shader = shader;
        }

        @Override
        public void setFloat(String name, float value) {
            uniform(this.shader, name).set(value);
        }

        @Override
        public void setVec3(String name, float x, float y, float z) {
            uniform(this.shader, name).set(x, y, z);
        }

        @Override
        public void setMat4(String name, Matrix4f value) {
            uniform(this.shader, name).set(value);
        }
    }

    private static final class ContextSink implements UniformSink {
        private final ScreenEffectOperationContext context;
        private final ShaderInstance shader;

        private ContextSink(ScreenEffectOperationContext context, ShaderInstance shader) {
            this.context = context;
            this.shader = shader;
        }

        @Override
        public void setFloat(String name, float value) {
            require(this.shader, name);
            this.context.setUniform(this.shader, name, value);
        }

        @Override
        public void setVec3(String name, float x, float y, float z) {
            require(this.shader, name);
            this.context.setUniform(this.shader, name, x, y, z);
        }

        @Override
        public void setMat4(String name, Matrix4f value) {
            require(this.shader, name);
            this.context.setUniform(this.shader, name, value);
        }
    }

    private static Uniform uniform(ShaderInstance shader, String name) {
        Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            throw missing(shader, name);
        }
        return uniform;
    }

    private static void require(ShaderInstance shader, String name) {
        if (shader.getUniform(name) == null) {
            throw missing(shader, name);
        }
    }

    private static IllegalStateException missing(ShaderInstance shader, String name) {
        return new IllegalStateException(
            "Shader " + shader.getName() + " is missing mandatory effect uniform '" + name + "'");
    }

    /**
     * Immutable camera group.
     *
     * <p>The model-view and projection matrices are copied on construction and access.
     * The inverse matrices are derived lazily.</p>
     */
    public record Camera(
        Vec3 position,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix,
        int viewportWidth,
        int viewportHeight
    ) {
        public Camera {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
            Objects.requireNonNull(projectionMatrix, "projectionMatrix");
            if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                throw new IllegalArgumentException("Camera position must be finite");
            }
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                throw new IllegalArgumentException("Viewport dimensions must be positive");
            }
            modelViewMatrix = new Matrix4f(modelViewMatrix);
            projectionMatrix = new Matrix4f(projectionMatrix);
            requireInvertible(modelViewMatrix, "modelViewMatrix");
            requireInvertible(projectionMatrix, "projectionMatrix");
        }

        public static Camera from(ScreenEffectCameraState camera) {
            Objects.requireNonNull(camera, "camera");
            return new Camera(camera.position(), camera.modelViewMatrix(), camera.projectionMatrix(),
                camera.viewportWidth(), camera.viewportHeight());
        }

        public static Camera from(EffectCameraSnapshot camera) {
            Objects.requireNonNull(camera, "camera");
            return new Camera(camera.position(), camera.modelViewMatrix(), camera.projectionMatrix(),
                camera.viewportWidth(), camera.viewportHeight());
        }

        @Override
        public Matrix4f modelViewMatrix() {
            return new Matrix4f(this.modelViewMatrix);
        }

        @Override
        public Matrix4f projectionMatrix() {
            return new Matrix4f(this.projectionMatrix);
        }

        public Matrix4f inverseViewMatrix() {
            return new Matrix4f(this.modelViewMatrix).invert();
        }

        public Matrix4f inverseProjectionMatrix() {
            return new Matrix4f(this.projectionMatrix).invert();
        }

        private static void requireInvertible(Matrix4f matrix, String name) {
            float determinant = matrix.determinant();
            if (!Float.isFinite(determinant) || determinant == 0.0F) {
                throw new IllegalArgumentException(name + " must be finite and invertible");
            }
        }
    }

    /** Immutable space group. */
    public record Space(EffectSpace space) {
        public Space {
            Objects.requireNonNull(space, "space");
        }
    }

    /** Immutable effect group. */
    public record Effect(float ageTicks, float progress, float seed) {
        public Effect {
            if (!Float.isFinite(ageTicks) || ageTicks < 0.0F) {
                throw new IllegalArgumentException("Effect age must be finite and non-negative");
            }
            if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                throw new IllegalArgumentException("Effect progress must be finite and in [0, 1]");
            }
            if (!Float.isFinite(seed) || seed < 0.0F || seed > 1.0F) {
                throw new IllegalArgumentException("Effect seed must be finite and in [0, 1]");
            }
        }
    }

    /** Immutable light group. Values are clamped to {@code [0, 1]}. */
    public record Light(float daylight) {
        public Light {
            if (!Float.isFinite(daylight)) {
                throw new IllegalArgumentException("Daylight must be finite");
            }
            daylight = Math.max(0.0F, Math.min(1.0F, daylight));
        }
    }

    /** Immutable quality group. */
    public record Quality(int qualityLevel, int lodLevel) {
        public Quality {
            if (qualityLevel < 0 || lodLevel < 0) {
                throw new IllegalArgumentException("Quality and LOD levels must be non-negative");
            }
        }
    }
}
