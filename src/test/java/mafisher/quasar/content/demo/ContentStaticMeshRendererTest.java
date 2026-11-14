package mafisher.quasar.content.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.testing.TestResourceManager;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentStaticMeshRendererTest {
    @Test
    void rendersOnlyAfterASuccessfulReload() {
        ContentStaticMeshRenderer renderer = new ContentStaticMeshRenderer();
        EffectRenderContext context = context();

        assertTrue(renderer.renderPasses(context).isEmpty());
        assertTrue(renderer.staticMeshPasses(context).isEmpty());

        renderer.onResourceReload(TestResourceManager.fromClasspath());

        List<EffectStaticMeshPass> passes = renderer.staticMeshPasses(context);
        assertEquals(1, passes.size());
        EffectStaticMeshPass pass = passes.getFirst();
        assertEquals("static-mesh-test", pass.id());
        assertTrue(pass.mesh().vertexCount() > 0);
        assertEquals(DefaultVertexFormat.BLOCK, pass.renderType().format());
        assertEquals(VertexFormat.Mode.QUADS, pass.renderType().mode());
    }

    @Test
    void failedReloadKeepsTheLastValidPass() {
        ContentStaticMeshRenderer renderer = new ContentStaticMeshRenderer();
        renderer.onResourceReload(TestResourceManager.fromClasspath());
        EffectStaticMeshPass first = renderer.staticMeshPasses(context()).getFirst();

        renderer.onResourceReload(TestResourceManager.missing());

        List<EffectStaticMeshPass> passes = renderer.staticMeshPasses(context());
        assertFalse(passes.isEmpty());
        assertSame(first.mesh(), passes.getFirst().mesh());
    }

    @Test
    void returnsFiniteConservativeBounds() {
        ContentStaticMeshRenderer renderer = new ContentStaticMeshRenderer();
        renderer.onResourceReload(TestResourceManager.fromClasspath());

        AABB bounds = renderer.cullingBounds(context());
        double radius = renderer.spatialRadius(context());

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxX));
        assertTrue(Double.isFinite(bounds.minY) && Double.isFinite(bounds.maxY));
        assertTrue(Double.isFinite(bounds.minZ) && Double.isFinite(bounds.maxZ));
        assertTrue(radius > 0.0);
        assertTrue(Double.isFinite(radius));
        assertTrue(bounds.maxX - bounds.minX > 5.0);
    }

    private static EffectRenderContext context() {
        return TestVisualEffects.context(
            ContentEffectIds.STATIC_MESH,
            UUID.randomUUID(),
            Vec3.ZERO,
            5.0F,
            200,
            0.5F
        );
    }
}
