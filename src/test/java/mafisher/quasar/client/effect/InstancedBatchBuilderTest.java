package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class InstancedBatchBuilderTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/instanced.png")
    );

    @Test
    void groupsPassesAcrossVisibleContexts() {
        InstancedBatchBuilder builder = new InstancedBatchBuilder(4);

        builder.add(context(1.0, 2.0, 3.0), List.of(pass("outer")));
        builder.add(context(4.0, 5.0, 6.0), List.of(pass("outer")));

        List<InstancedBatchBuilder.InstancedDraw> draws = builder.draws();
        assertEquals(1, draws.size());
        assertEquals(2, draws.getFirst().instances().size());
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
        assertEquals(2, draws.get(0).instances().size());
        assertEquals(2, draws.get(1).instances().size());
        assertEquals(1, draws.get(2).instances().size());
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

    private static EffectInstancedPass pass(String id) {
        return new EffectInstancedPass(id, RENDER_TYPE, EffectMeshes.horizontalQuad(), new Matrix4f(),
            1.0F, 1.0F, 1.0F, 1.0F, 0.5F, 0.25F);
    }

    private static EffectRenderContext context(double x, double y, double z) {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID,
            UUID.randomUUID(),
            DIMENSION,
            x,
            y,
            z,
            0.0F,
            0.0F,
            0.0F,
            2.0F,
            100,
            0L,
            1L
        );
        return new EffectRenderContext(new VisualEffectInstance(payload), payload.transform(), 0.0F, 0.0F, 0.0F);
    }
}
