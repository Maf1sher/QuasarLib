package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class InstancedBatchBuilderTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/instanced.png")
    );
    private static final RenderType UNSORTED_RENDER_TYPE = QuasarRenderTypes.worldVisual(
        EffectShader.create(ResourceLocation.fromNamespaceAndPath("test", "shader/unsorted"),
            DefaultVertexFormat.BLOCK),
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/unsorted.png"),
        EffectRenderTypeOptions.WORLD_DEPTH_WRITING
    );

    @Test
    void groupsPassesAcrossVisibleContexts() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(4);

        builder.add(context(1.0, 2.0, 3.0), List.of(pass("outer")));
        builder.add(context(4.0, 5.0, 6.0), List.of(pass("outer")));

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(1, draws.size());
        assertEquals(2, draws.getFirst().instanceCount());
        assertEquals("outer", draws.getFirst().key().id());
    }

    @Test
    void splitsBatchesAtTheInstanceLimit() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(2);
        for (int index = 0; index < 5; index++) {
            builder.add(context(index, 0.0, 0.0), List.of(pass("outer")));
        }

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(3, draws.size());
        assertEquals(2, draws.get(0).instanceCount());
        assertEquals(2, draws.get(1).instanceCount());
        assertEquals(1, draws.get(2).instanceCount());
    }

    @Test
    void keepsDifferentPassIdsSeparate() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);
        builder.add(context(0.0, 0.0, 0.0), List.of(pass("outer"), pass("inner")));

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(2, draws.size());
        assertEquals("outer", draws.get(0).key().id());
        assertEquals("inner", draws.get(1).key().id());
    }

    @Test
    void rejectsDuplicateIdsWithinOneContext() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);

        assertThrows(IllegalStateException.class,
            () -> builder.add(context(0.0, 0.0, 0.0), List.of(pass("outer"), pass("outer"))));
    }

    @Test
    void emptyInputProducesNoDraws() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);

        builder.add(context(0.0, 0.0, 0.0), List.of());

        assertTrue(builder.isEmpty());
        assertTrue(builder.draws().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new InstancedBatchBuilder(0));
    }

    @Test
    void resetClearsPreviousFrameAndMovesTheCamera() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);
        builder.add(context(1.0, 0.0, 0.0), List.of(pass("outer")));

        builder.reset(10.0, 0.0, 0.0);

        assertTrue(builder.isEmpty());
        builder.add(context(1.0, 0.0, 0.0), List.of(pass("outer")));
        assertEquals(81.0, builder.draws().getFirst().instance(0).cameraDistanceSquared(), 0.0);
    }

    @Test
    void drawIntoDoesNotAccumulateAcrossFrames() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);
        builder.add(context(1.0, 0.0, 0.0), List.of(pass("outer")));
        List<InstancedBatchBuilder.InstancedDraw> output = new java.util.ArrayList<>();

        builder.drawInto(output);
        builder.drawInto(output);

        assertEquals(1, output.size());
    }

    @Test
    void sortsTranslucentBatchesBackToFrontFromTheCamera() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);
        builder.add(context(1.0, 0.0, 0.0), List.of(pass("outer")));
        builder.add(context(5.0, 0.0, 0.0), List.of(pass("outer")));
        builder.add(context(3.0, 0.0, 0.0), List.of(pass("outer")));

        InstancedBatchBuilder.InstancedDraw draw = builder.draws().getFirst();
        assertEquals(5.0, draw.instance(0).model().m30());
        assertEquals(3.0, draw.instance(1).model().m30());
        assertEquals(1.0, draw.instance(2).model().m30());
    }

    @Test
    void composesModelMatricesRelativeToTheCamera() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8, 4.0, 0.0, 0.0);
        builder.add(context(10.0, 0.0, 0.0), List.of(pass("outer")));

        assertEquals(6.0, builder.draws().getFirst().instance(0).model().m30());
    }

    @Test
    void keepsBackToFrontOrderBeyondOneHundredTwentyEightInstances() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(512);
        for (int index = 0; index < 256; index++) {
            builder.add(context(index, 0.0, 0.0), List.of(pass("outer")));
        }

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(1, draws.size());
        InstancedBatchBuilder.InstancedDraw draw = draws.getFirst();
        assertEquals(256, draw.instanceCount());
        assertEquals(255.0, draw.instance(0).model().m30());
        assertEquals(0.0, draw.instance(255).model().m30());
    }

    @Test
    void largeBatchesSplitWithoutBreakingSortOrder() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(200);
        for (int index = 0; index < 256; index++) {
            builder.add(context(index, 0.0, 0.0), List.of(pass("outer")));
        }

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(2, draws.size());
        assertEquals(255.0, draws.get(0).instance(0).model().m30());
        assertEquals(56.0, draws.get(0).instance(199).model().m30());
        assertEquals(55.0, draws.get(1).instance(0).model().m30());
        assertEquals(0.0, draws.get(1).instance(55).model().m30());
    }

    @Test
    void chunkSplittingKeepsBackToFrontOrder() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(2);
        for (int index = 1; index <= 4; index++) {
            builder.add(context(index, 0.0, 0.0), List.of(pass("outer")));
        }

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(2, draws.size());
        assertEquals(4.0, draws.get(0).instance(0).model().m30());
        assertEquals(3.0, draws.get(0).instance(1).model().m30());
        assertEquals(2.0, draws.get(1).instance(0).model().m30());
        assertEquals(1.0, draws.get(1).instance(1).model().m30());
    }

    @Test
    void keepsUnsortedBatchesInInsertionOrder() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(8);
        builder.add(context(1.0, 0.0, 0.0), List.of(pass("opaque", UNSORTED_RENDER_TYPE)));
        builder.add(context(5.0, 0.0, 0.0), List.of(pass("opaque", UNSORTED_RENDER_TYPE)));
        builder.add(context(3.0, 0.0, 0.0), List.of(pass("opaque", UNSORTED_RENDER_TYPE)));

        InstancedBatchBuilder.InstancedDraw draw = builder.draws().getFirst();
        assertEquals(1.0, draw.instance(0).model().m30());
        assertEquals(5.0, draw.instance(1).model().m30());
        assertEquals(3.0, draw.instance(2).model().m30());
    }

    private static EffectInstancedPass pass(String id) {
        return pass(id, RENDER_TYPE);
    }

    private static EffectInstancedPass pass(String id, RenderType renderType) {
        return new EffectInstancedPass(id, renderType, EffectMeshes.horizontalQuad(), new Matrix4f(),
            1.0F, 1.0F, 1.0F, 1.0F, 0.5F, 0.25F);
    }

    private static EffectRenderContext context(double x, double y, double z) {
        return TestVisualEffects.context(
            TestVisualEffects.payload(
                EFFECT_ID,
                UUID.randomUUID(),
                new Vec3(x, y, z),
                2.0F,
                100,
                1L
            ),
            0.0F,
            0.0F
        );
    }
}
