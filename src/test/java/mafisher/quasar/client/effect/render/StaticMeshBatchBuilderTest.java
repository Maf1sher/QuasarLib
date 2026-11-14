package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class StaticMeshBatchBuilderTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/static.png"
    );
    private static final RenderType NORMAL = QuasarRenderTypes.worldVisual(TEXTURE);
    private static final RenderType BATCH = QuasarRenderTypes.instancedWorldVisual(TEXTURE);
    private static final EffectMesh MESH = EffectMeshes.horizontalQuad();

    @Test
    void groupsConsecutiveCompatiblePasses() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(Vec3.ZERO);

        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(10.0));
        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(20.0));

        assertEquals(1, builder.commandCount());
        StaticMeshBatchBuilder.Command command = builder.command(0);
        assertTrue(command.isBatched());
        assertEquals(2, command.instanceCount());
    }

    @Test
    void keepsDifferentKeysAndInterleavedRunsSeparate() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(Vec3.ZERO);

        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(10.0));
        builder.add(pass("halo", OverlayTexture.NO_OVERLAY), BATCH, transform(20.0));
        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(30.0));

        assertEquals(3, builder.commandCount());
        assertEquals("core", builder.command(0).id());
        assertEquals("halo", builder.command(1).id());
        assertEquals("core", builder.command(2).id());
    }

    @Test
    void splitsRunsAtTheInstanceLimit() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(2);
        builder.reset(Vec3.ZERO);

        for (int index = 0; index < 5; index++) {
            builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(index));
        }

        assertEquals(3, builder.commandCount());
        assertEquals(2, builder.command(0).instanceCount());
        assertEquals(2, builder.command(1).instanceCount());
        assertEquals(1, builder.command(2).instanceCount());
    }

    @Test
    void keepsUnbatchablePassesAsOrderedSingleCommands() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(Vec3.ZERO);
        EffectStaticMeshPass fallback = pass("fallback", OverlayTexture.NO_OVERLAY);

        builder.add(fallback, null, transform(10.0));

        assertEquals(1, builder.commandCount());
        assertFalse(builder.command(0).isBatched());
        assertSame(fallback, builder.command(0).singlePass());
        assertNull(builder.command(0).id());
    }

    @Test
    void composesTheSameCameraRelativeModelMatrixAsTheDynamicPath() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(new Vec3(4.0, 5.0, 6.0));
        Matrix4f local = new Matrix4f().scale(2.0F);
        EffectTransform transform = new EffectTransform(new EffectPoint(10.0, 20.0, 30.0),
            37.0F, 8.0F, -12.0F);

        builder.add(pass("core", OverlayTexture.NO_OVERLAY, local), BATCH, transform);

        Matrix4f expected = new Matrix4f();
        InstancedBatchBuilder.modelMatrixInto(transform, local, 4.0, 5.0, 6.0, expected,
            new Matrix4f());
        Matrix4f actual = builder.command(0).instances().getFirst().model();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), 1.0E-5F);
            }
        }
    }

    @Test
    void keepsTintAndOverlayDataPerInstance() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(Vec3.ZERO);
        int overlay = OverlayTexture.pack(100, 200);
        EffectStaticMeshPass pass = new EffectStaticMeshPass("core", NORMAL, MESH,
            new Matrix4f(), 0.2F, 0.3F, 0.4F, 0.5F, overlay, 0x00F000F0);

        builder.add(pass, BATCH, transform(10.0));

        InstancedBatchBuilder.Instance instance = builder.command(0).instances().getFirst();
        assertEquals(0.2F, instance.red());
        assertEquals(0.3F, instance.green());
        assertEquals(0.4F, instance.blue());
        assertEquals(0.5F, instance.alpha());
        assertEquals(100.0F / 32767.0F, instance.effectDataX(), 1.0E-7F);
        assertEquals(200.0F / 32767.0F, instance.effectDataY(), 1.0E-7F);
    }

    @Test
    void resetClearsThePreviousFrameWithoutLosingReusableInstances() {
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(8);
        builder.reset(Vec3.ZERO);
        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(10.0));
        InstancedBatchBuilder.Instance first = builder.command(0).instances().getFirst();

        builder.reset(new Vec3(1.0, 2.0, 3.0));
        assertTrue(builder.isEmpty());

        builder.add(pass("core", OverlayTexture.NO_OVERLAY), BATCH, transform(10.0));
        assertSame(first, builder.command(0).instances().getFirst());
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new StaticMeshBatchBuilder(0));
        StaticMeshBatchBuilder builder = new StaticMeshBatchBuilder(1);
        assertThrows(IllegalArgumentException.class,
            () -> builder.reset(new Vec3(Double.NaN, 0.0, 0.0)));
    }

    private static EffectStaticMeshPass pass(String id, int overlay) {
        return pass(id, overlay, new Matrix4f());
    }

    private static EffectStaticMeshPass pass(String id, int overlay, Matrix4f local) {
        return new EffectStaticMeshPass(id, NORMAL, MESH, local, 1.0F, 1.0F, 1.0F, 1.0F,
            overlay, EffectStaticMeshPass.FULLBRIGHT_LIGHT);
    }

    private static EffectTransform transform(double x) {
        return new EffectTransform(new EffectPoint(x, 0.0, 0.0), 0.0F, 0.0F, 0.0F);
    }
}
