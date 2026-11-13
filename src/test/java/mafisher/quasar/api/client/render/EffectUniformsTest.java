package mafisher.quasar.api.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectUniformsTest {
    @Test
    void bindsEveryStandardGroup() {
        EffectUniforms uniforms = uniforms();
        RecordingSink sink = new RecordingSink();

        uniforms.bind(sink, EffectUniforms.Group.values());

        assertEquals(-6.0F, sink.vectors.get("EffectOrigin").x);
        assertEquals(-15.0F, sink.vectors.get("EffectOrigin").y);
        assertEquals(-24.0F, sink.vectors.get("EffectOrigin").z);
        assertEquals(1.0F, sink.vectors.get("EffectRight").x);
        assertEquals(0.0F, sink.vectors.get("EffectRight").y);
        assertEquals(1.0F, sink.vectors.get("EffectUp").y);
        assertEquals(1.0F, sink.vectors.get("EffectForward").z);
        assertEquals(10.0F, sink.floats.get("AgeTicks"));
        assertEquals(0.5F, sink.floats.get("Progress"));
        assertEquals(0.25F, sink.floats.get("Seed"));
        assertEquals(1.0F, sink.floats.get("Daylight"));
        assertEquals(2.0F, sink.floats.get("QualityLevel"));
        assertEquals(1.0F, sink.floats.get("LodLevel"));
        assertEquals(1920.0F, sink.floats.get("ViewportWidth"));
        assertTrue(sink.matrices.containsKey("ViewMat"));
        assertTrue(sink.matrices.containsKey("InvProjMat"));
    }

    @Test
    void bindsOnlyRequestedGroups() {
        RecordingSink sink = new RecordingSink();

        uniforms().bind(sink, EffectUniforms.Group.SPACE);

        assertEquals(java.util.Set.of("EffectOrigin", "EffectRight", "EffectUp", "EffectForward"),
            sink.vectors.keySet());
        assertTrue(sink.floats.isEmpty());
        assertTrue(sink.matrices.isEmpty());
    }

    @Test
    void rejectsEmptyGroupsAndNullGroups() {
        RecordingSink sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class, () -> uniforms().bind(sink));
        assertThrows(NullPointerException.class,
            () -> uniforms().bind(sink, EffectUniforms.Group.SPACE, null));
    }

    @Test
    void missingShaderUniformFailsAtBindTime() {
        ShaderInstance shader = mock(ShaderInstance.class);
        when(shader.getName()).thenReturn("test:effect");
        when(shader.getUniform("ViewMat")).thenReturn(null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> uniforms().bind(shader, EffectUniforms.Group.CAMERA));

        assertTrue(failure.getMessage().contains("ViewMat"));
        assertTrue(failure.getMessage().contains("test:effect"));
    }

    @Test
    void lightClampsButRejectsNonFiniteValues() {
        assertEquals(1.0F, new EffectUniforms.Light(2.0F).daylight());
        assertEquals(0.0F, new EffectUniforms.Light(-1.0F).daylight());
        assertThrows(IllegalArgumentException.class, () -> new EffectUniforms.Light(Float.NaN));
    }

    @Test
    void effectAndQualityGroupsValidateTheirRanges() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectUniforms.Effect(0.0F, 1.1F, 0.5F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectUniforms.Effect(0.0F, 0.5F, 1.1F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectUniforms.Quality(-1, 0));
    }

    @Test
    void buildsFromALiveRenderContext() {
        EffectRenderContext base = TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), new Vec3(4.0, 5.0, 6.0),
            2.0F, 100, 0.5F);
        EffectRenderContext context = new EffectRenderContext(
            base.effect(), base.transform(), base.ageTicks(), base.progress(), base.partialTick(),
            base.lod(), base.quality(), base.environment(),
            new EffectCameraSnapshot(new Vec3(10.0, 20.0, 30.0), new Matrix4f(), new Matrix4f(),
                1920, 1080),
            1.5F
        );

        EffectUniforms uniforms = EffectUniforms.from(context);

        assertEquals(-6.0, uniforms.space().space().cameraRelativeOrigin().x(), 0.0);
        assertEquals(1.0F, uniforms.light().daylight());
        assertEquals(2, uniforms.quality().qualityLevel());
    }

    @Test
    void rejectsARenderContextWithoutACamera() {
        EffectRenderContext context = TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), Vec3.ZERO, 1.0F, 100, 0.5F);

        assertThrows(IllegalStateException.class, () -> EffectUniforms.from(context));
    }

    private static EffectUniforms uniforms() {
        Matrix4f identity = new Matrix4f();
        EffectUniforms.Camera camera = new EffectUniforms.Camera(
            new Vec3(10.0, 20.0, 30.0), identity, identity, 1920, 1080);
        EffectSpace space = EffectSpace.from(
            new EffectTransform(new EffectPoint(4.0, 5.0, 6.0), 0.0F, 0.0F, 0.0F),
            2.0F,
            new EffectPoint(10.0, 20.0, 30.0)
        );
        return new EffectUniforms(
            camera,
            new EffectUniforms.Space(space),
            new EffectUniforms.Effect(10.0F, 0.5F, 0.25F),
            new EffectUniforms.Light(2.0F),
            new EffectUniforms.Quality(2, 1)
        );
    }

    private static final class RecordingSink implements EffectUniforms.UniformSink {
        private final Map<String, Vector3> vectors = new LinkedHashMap<>();
        private final Map<String, Float> floats = new LinkedHashMap<>();
        private final Map<String, Matrix4f> matrices = new LinkedHashMap<>();

        @Override
        public void setFloat(String name, float value) {
            this.floats.put(name, value);
        }

        @Override
        public void setVec3(String name, float x, float y, float z) {
            this.vectors.put(name, new Vector3(x, y, z));
        }

        @Override
        public void setMat4(String name, Matrix4f value) {
            this.matrices.put(name, new Matrix4f(value));
        }
    }

    private record Vector3(float x, float y, float z) {
        private Vec3 toVec3() {
            return new Vec3(this.x, this.y, this.z);
        }
    }
}
